/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.graal.pointsto.standalone;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder.ObjectConstantHandler;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.standalone.meta.StandaloneConstantReflectionProvider;
import com.oracle.graal.pointsto.standalone.meta.StandaloneObjectConstantHandler;
import com.oracle.graal.pointsto.standalone.plugins.StandaloneGraphBuilderPhase;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.OriginalClassProvider;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

public class StandaloneHost extends HostVM {
    /**
     * Standalone-owned outcome of the build-time-initialization decision for one reachable type.
     */
    public enum ClassInitializationOutcome {
        PENDING,
        INITIALIZED,
        RUNTIME_ONLY,
        FAILED;

        /**
         * Returns whether shadow-heap snapshotting may use build-time static values for the type.
         */
        public boolean allowsStaticFieldSnapshotting() {
            return this == INITIALIZED;
        }
    }

    private final String imageName;
    private final boolean closedTypeWorld;
    private final StandaloneClassInitializationStrategy classInitializationStrategy;
    private final boolean printClassInitializationFailures;
    private final AtomicInteger classInitializationFailureCount = new AtomicInteger();
    private final ConcurrentMap<AnalysisType, String> firstClassInitializationFailureStackTraces = new ConcurrentHashMap<>();
    private final ConcurrentMap<AnalysisType, ClassInitializationOutcome> classInitializationOutcomes = new ConcurrentHashMap<>();
    private final ConcurrentMap<AnalysisType, AnalysisFuture<ClassInitializationOutcome>> classInitializationTasks = new ConcurrentHashMap<>();

    public StandaloneHost(OptionValues options, String imageName, StandaloneClassInitializationStrategy classInitializationStrategy, boolean closedTypeWorld) {
        super(options, /*- ClassLoader not supported. */ null);
        this.imageName = imageName;
        this.closedTypeWorld = closedTypeWorld;
        this.classInitializationStrategy = classInitializationStrategy;
        this.printClassInitializationFailures = StandaloneOptions.StandalonePrintClassInitializationFailures.getValue(options);
    }

    private boolean shouldInitializeAtBuildTime(AnalysisType type) {
        return classInitializationStrategy.shouldInitializeAtBuildTime(type);
    }

    /**
     * Initializes {@code type} only when the configured strategy currently allows eager
     * initialization for that type.
     */
    public void maybeInitializeAtBuildTime(AnalysisType type) {
        if (!shouldInitializeAtBuildTime(type)) {
            classInitializationOutcomes.putIfAbsent(type, ClassInitializationOutcome.RUNTIME_ONLY);
            return;
        }
        ClassInitializationOutcome knownOutcome = classInitializationOutcomes.get(type);
        if (knownOutcome == ClassInitializationOutcome.INITIALIZED || knownOutcome == ClassInitializationOutcome.FAILED) {
            return;
        }
        if (type.getWrapped().isInitialized()) {
            classInitializationOutcomes.put(type, ClassInitializationOutcome.INITIALIZED);
            return;
        }
        AnalysisFuture<ClassInitializationOutcome> newTask = new AnalysisFuture<>(() -> initializeAtBuildTime(type));
        AnalysisFuture<ClassInitializationOutcome> existingTask = classInitializationTasks.putIfAbsent(type, newTask);
        if (existingTask == null) {
            newTask.ensureDone();
        } else {
            existingTask.ensureDone();
        }
    }

    /**
     * Returns the current standalone-owned build-time-initialization outcome for {@code type}
     * without starting a new initialization attempt.
     */
    public ClassInitializationOutcome getClassInitializationOutcome(AnalysisType type) {
        ClassInitializationOutcome outcome = classInitializationOutcomes.get(type);
        if (outcome != null) {
            return outcome;
        }
        if (!shouldInitializeAtBuildTime(type)) {
            return ClassInitializationOutcome.RUNTIME_ONLY;
        }
        return ClassInitializationOutcome.PENDING;
    }

    /**
     * Returns the build-time-initialization outcome for {@code type}, waiting only for an already
     * started standalone initialization attempt to complete.
     */
    public ClassInitializationOutcome awaitClassInitializationOutcomeIfStarted(AnalysisType type) {
        AnalysisFuture<ClassInitializationOutcome> task = classInitializationTasks.get(type);
        if (task != null) {
            return task.ensureDone();
        }
        return getClassInitializationOutcome(type);
    }

    /**
     * Returns whether end-of-analysis reporting of class-initialization failures that fall back to
     * runtime handling is enabled.
     */
    public boolean shouldPrintClassInitializationFailures() {
        return printClassInitializationFailures;
    }

    /**
     * Returns the total number of build-time class-initialization attempts that failed and fell
     * back to runtime handling during the current analysis.
     */
    public int getClassInitializationFailureCount() {
        return classInitializationFailureCount.get();
    }

    /**
     * Returns the number of distinct classes whose first fallback-triggering initialization
     * failure was recorded during the current analysis.
     */
    public int getClassInitializationFailureTypeCount() {
        return firstClassInitializationFailureStackTraces.size();
    }

    /**
     * Formats the first recorded class-initialization failure for each class that fell back to
     * runtime handling.
     */
    public String formatClassInitializationFailures() {
        StringBuilder sb = new StringBuilder();
        firstClassInitializationFailureStackTraces.entrySet().stream()
                        .sorted((left, right) -> left.getKey().toJavaName().compareTo(right.getKey().toJavaName()))
                        .forEach(entry -> {
                            if (sb.length() > 0) {
                                sb.append(System.lineSeparator());
                            }
                            sb.append(entry.getKey().toJavaName()).append(':');
                            appendIndentedLines(sb, entry.getValue(), "    ");
                        });
        return sb.toString();
    }

    /**
     * Records the first class-initialization failure for each class that falls back to runtime
     * handling and keeps a total attempt count for the whole analysis.
     */
    public void recordClassInitializationFailure(AnalysisType type, Throwable failure) {
        classInitializationFailureCount.incrementAndGet();
        firstClassInitializationFailureStackTraces.putIfAbsent(type, printClassInitializationFailures ? formatThrowable(failure) : "");
        classInitializationOutcomes.put(type, ClassInitializationOutcome.FAILED);
    }

    /**
     * Performs the actual eager initialization request and records the resulting standalone-owned
     * outcome so later field readers can observe it without triggering initialization themselves.
     */
    private ClassInitializationOutcome initializeAtBuildTime(AnalysisType type) {
        try {
            if (!type.getWrapped().isInitialized()) {
                type.getWrapped().initialize();
            }
            classInitializationOutcomes.put(type, ClassInitializationOutcome.INITIALIZED);
            return ClassInitializationOutcome.INITIALIZED;
        } catch (Throwable failure) {
            /*
             * Standalone should fall back to runtime handling for guest classes whose eager
             * initialization is not executable on the current analysis thread.
             */
            recordClassInitializationFailure(type, failure);
            return ClassInitializationOutcome.FAILED;
        } finally {
            classInitializationTasks.remove(type);
        }
    }

    /**
     * Renders a throwable stack trace into stable text so the first failure for a class can be
     * reported after analysis has finished.
     */
    private static String formatThrowable(Throwable failure) {
        StringWriter buffer = new StringWriter();
        try (PrintWriter writer = new PrintWriter(buffer)) {
            failure.printStackTrace(writer);
        }
        return buffer.toString().stripTrailing();
    }

    /**
     * Appends a multi-line text block with a fixed indentation prefix on every line.
     */
    private static void appendIndentedLines(StringBuilder sb, String text, String indent) {
        for (String line : text.split("\\R", -1)) {
            sb.append(System.lineSeparator()).append(indent).append(line);
        }
    }

    @Override
    public boolean isInitialized(AnalysisType type) {
        return type.getWrapped().isInitialized();
    }

    @Override
    public void onTypeReachable(BigBang bb, AnalysisType type) {
        AnalysisError.guarantee(type.isReachable(), "Registering and initializing a type that was not yet marked as reachable: %s", type.toJavaName());
        maybeInitializeAtBuildTime(type);
    }

    @Override
    public ObjectConstantHandler createMethodTypeFlowBuilderObjectConstantHandler(PointsToAnalysis bb) {
        return new StandaloneObjectConstantHandler((StandaloneConstantReflectionProvider) bb.getConstantReflectionProvider());
    }

    @Override
    public boolean shouldStoreAnalyzedGraph(@SuppressWarnings("unused") BigBang bb, @SuppressWarnings("unused") AnalysisMethod method) {
        return false;
    }

    @Override
    public GraphBuilderPhase.Instance createGraphBuilderPhase(HostedProviders builderProviders, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                    IntrinsicContext initialIntrinsicContext) {
        return new StandaloneGraphBuilderPhase.Instance(builderProviders, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
    }

    @Override
    public String getImageName() {
        return imageName;
    }

    @Override
    public Comparator<? super ResolvedJavaType> getTypeComparator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<AnalysisMethod> handleForeignCall(ForeignCallDescriptor foreignCallDescriptor, ForeignCallsProvider foreignCallsProvider) {
        throw AnalysisError.shouldNotReachHere("StandaloneHost.handleForeignCall");
    }

    @Override
    public boolean isClosedTypeWorld() {
        return closedTypeWorld;
    }

    /**
     * Resolves the guest-side class loader name so standalone analysis reporting reflects the
     * analyzed application rather than the host VM loader graph.
     */
    @Override
    public String loaderName(AnalysisType type) {
        GuestAccess guestAccess = GuestAccess.get();
        JavaConstant classLoaderConstant = getClassLoader(guestAccess, type);
        if (classLoaderConstant.isNull()) {
            return "null";
        }
        String classLoaderName = getClassLoaderName(guestAccess, classLoaderConstant);
        if (classLoaderName != null) {
            return classLoaderName;
        }
        ResolvedJavaType classLoaderType = guestAccess.getProviders().getMetaAccess().lookupJavaType(classLoaderConstant);
        return classLoaderType.toJavaName();
    }

    /**
     * Reads the guest-side {@link ClassLoader} for the original application class represented by
     * {@code type}.
     */
    private static JavaConstant getClassLoader(GuestAccess guestAccess, AnalysisType type) {
        var original = OriginalClassProvider.getOriginalType(type);
        JavaConstant asConstant = guestAccess.getProviders().getConstantReflection().asJavaClass(original);
        return guestAccess.invoke(guestAccess.elements.java_lang_Class_getClassLoader, asConstant);
    }

    /**
     * Returns the guest-side class loader name when the loader exposes one.
     */
    private static String getClassLoaderName(GuestAccess guestAccess, JavaConstant classLoaderConstant) {
        JavaConstant classLoaderName = guestAccess.invoke(guestAccess.elements.java_lang_ClassLoader_getName, classLoaderConstant);
        if (classLoaderName.isNull()) {
            return null;
        }
        return guestAccess.getSnippetReflection().asObject(String.class, classLoaderName);
    }
}
