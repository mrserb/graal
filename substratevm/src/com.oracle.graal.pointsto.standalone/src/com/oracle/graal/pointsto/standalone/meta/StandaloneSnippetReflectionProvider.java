/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.meta;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Standalone equivalent of the hosted snippet reflection provider.
 *
 * The provider redirects newly created object constants through the shadow heap so heap scanning
 * and verification keep seeing {@link ImageHeapConstant} wrappers instead of raw hosted constants.
 */
public final class StandaloneSnippetReflectionProvider implements SnippetReflectionProvider {
    /**
     * Shadow-heap access used to wrap newly created object constants. It is installed after both
     * the snippet reflection provider and the heap scanner have been created.
     */
    private ImageHeapScanner heapScanner;
    /**
     * Guest-aware snippet reflection implementation supplied by the selected VMAccess backend.
     */
    private final SnippetReflectionProvider original;
    /**
     * Exposes {@link WordTypes} for injected node intrinsic parameters used during graph building.
     */
    private final WordTypes wordTypes;

    /**
     * Creates a standalone snippet reflection provider that keeps object constants shadow-heap
     * aware while delegating guest conversions to the original VMAccess-backed provider.
     */
    public StandaloneSnippetReflectionProvider(ImageHeapScanner heapScanner, SnippetReflectionProvider original, WordTypes wordTypes) {
        this.heapScanner = heapScanner;
        this.original = original;
        this.wordTypes = wordTypes;
    }

    /**
     * Installs the heap scanner after both components have been constructed.
     */
    public void setHeapScanner(ImageHeapScanner heapScanner) {
        this.heapScanner = heapScanner;
    }

    @Override
    public JavaConstant forObject(Object object) {
        VMError.guarantee(heapScanner != null, "Heap scanner not installed yet.");
        return heapScanner.createImageHeapConstant(object, ObjectScanner.OtherReason.UNKNOWN);
    }

    @Override
    public JavaConstant forBoxed(JavaKind kind, Object value) {
        if (kind == JavaKind.Object) {
            return forObject(value);
        }
        return original.forBoxed(kind, value);
    }

    /**
     * Converts constants back to hosted objects by first unwrapping standalone image-heap wrappers
     * and then delegating the actual guest-to-host conversion to the VMAccess-backed provider.
     */
    @Override
    public <T> T asObject(Class<T> type, JavaConstant constant) {
        JavaConstant unwrapped = constant;
        if (constant instanceof ImageHeapConstant imageHeapConstant) {
            unwrapped = imageHeapConstant.getHostedObject();
            if (unwrapped == null) {
                return null;
            }
        }
        return original.asObject(type, unwrapped);
    }

    @Override
    public <T> T getInjectedNodeIntrinsicParameter(Class<T> type) {
        if (type.equals(WordTypes.class)) {
            return type.cast(wordTypes);
        }
        return original.getInjectedNodeIntrinsicParameter(type);
    }

    @Override
    public Class<?> originalClass(ResolvedJavaType type) {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public Executable originalMethod(ResolvedJavaMethod method) {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public Field originalField(ResolvedJavaField field) {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }
}
