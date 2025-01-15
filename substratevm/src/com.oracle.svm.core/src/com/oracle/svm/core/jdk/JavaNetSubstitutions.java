/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.Hashtable;
import java.util.Set;

import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.RuntimeResourceSupport;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.resources.ResourceURLConnection;
import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.util.BasedOnJDKClass;
import com.oracle.svm.shared.util.LogUtils;

import sun.net.NetProperties;

@TargetClass(java.net.URL.class)
final class Target_java_net_URL {
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    @SuppressWarnings({"final", "unused"}) //
    // Checkstyle: stop
    private static Hashtable<?, ?> handlers = new Hashtable<>();
    // Checkstyle: resume

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    private static URLStreamHandlerFactory defaultFactory = new DefaultFactory();

    /**
     * Same as in the JDK except: it handles the resource protocol, it does not pull in the JAR
     * provider by default, and it handles error messages related to metadata better.
     */
    @BasedOnJDKClass(className = "java.net.URL$DefaultFactory")
    private static final class DefaultFactory implements URLStreamHandlerFactory {
        private static final String PROTOCOL_QUALIFIER = "sun.net.www.protocol.";
        private static final Set<String> REFLECTIVELY_ACCESSED_PROTOCOLS = Set.of("mailto", "jmod", "jrt", "ftp", "http", "https", "jar");

        @Override
        public URLStreamHandler createURLStreamHandler(String protocol) {
            // Avoid using reflection during bootstrap.
            switch (protocol) {
                case "file":
                    return new sun.net.www.protocol.file.Handler();
                case "resource":
                    return new URLStreamHandler() {
                        @Override
                        protected URLConnection openConnection(URL url) {
                            return new ResourceURLConnection(url);
                        }
                    };
            }
            String name = PROTOCOL_QUALIFIER + protocol + ".Handler";
            try {
                Object handler = Class.forName(name).getDeclaredConstructor().newInstance();
                return (URLStreamHandler) handler;
            } catch (ClassNotFoundException e) {
                if (REFLECTIVELY_ACCESSED_PROTOCOLS.contains(protocol)) {
                    throw new RuntimeException("Accessing a URL protocol that was not enabled. The URL protocol " + protocol +
                                    " is supported but not enabled by default. It must be enabled by adding the " + name + " to reachability metadata.");
                }
            } catch (Exception e) {
                // For compatibility, all Exceptions are ignored.
            }
            return null;
        }
    }
}

@TargetClass(className = "sun.net.spi.DefaultProxySelector")
final class Target_sun_net_spi_DefaultProxySelector {

    @Alias @InjectAccessors(DefaultProxySelectorSystemProxiesAccessor.class) //
    static boolean hasSystemProxies;

    @Alias
    static native boolean init();
}

final class DefaultProxySelectorSystemProxiesAccessor {
    static Boolean hasSystemProxies = null;

    static boolean get() {
        if (hasSystemProxies == null) {
            hasSystemProxies = ensureInitialized();
        }
        return hasSystemProxies;
    }

    static final SignedWord UNINITIALIZED = Word.signed(-2);
    static final SignedWord INITIALIZING = Word.signed(-1);

    static final CGlobalData<Pointer> initState = CGlobalDataFactory.createWord(UNINITIALIZED);

    /** Avoids calling init() more than once per process, which can leak resources with isolates. */
    static boolean ensureInitialized() {
        Boolean b = NetProperties.getBoolean("java.net.useSystemProxies");
        if (b != null && b) {
            // NOTE: System.loadLibrary("net") has already been called early on.
            while (true) {
                SignedWord value = initState.get().readWord(0);
                if (value.greaterOrEqual(0)) {
                    return value.notEqual(0);
                }
                if (initState.get().logicCompareAndSwapWord(0, UNINITIALIZED, INITIALIZING, LocationIdentity.ANY_LOCATION)) {
                    boolean result = Target_sun_net_spi_DefaultProxySelector.init();
                    initState.get().writeWord(0, Word.signed(result ? 1 : 0));
                }
            }
        }
        return false;
    }
}

@AutomaticallyRegisteredFeature
class JavaNetFeature implements InternalFeature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        for (String protocol : SubstrateOptions.EnableURLProtocols.getValue().values()) {
            try {
                var clazz = Class.forName("sun.net.www.protocol." + protocol + ".Handler");
                RuntimeReflection.register(clazz);
                RuntimeReflection.register(clazz.getConstructor());
            } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
                LogUtils.warning("Registering the " + protocol + " URL protocol failed. This protocol will not be available at runtime. The protocol was set with " +
                                SubstrateOptionsParser.commandArgument(SubstrateOptions.EnableURLProtocols, protocol) +
                                "Cause of the failure: " + e.getMessage());
            }
        }

        RuntimeResourceSupport.singleton().addResources(AccessCondition.typeReached(URL.class), "META-INF/services/java.net.spi.URLStreamHandlerProvider", "JavaNetFeature for URL");
    }
}

/** Dummy class to have a class with the file's name. */
public final class JavaNetSubstitutions {
}
