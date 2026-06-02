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
package com.oracle.svm.configure.test.config;

import java.io.Reader;
import java.io.StringReader;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.configure.test.AddExports;

@AddExports({"org.graalvm.nativeimage/org.graalvm.nativeimage.impl", "jdk.graal.compiler/jdk.graal.compiler.phases.common", "jdk.graal.compiler/jdk.graal.compiler.util",
                "jdk.graal.compiler/jdk.graal.compiler.util.json", "jdk.internal.vm.ci/jdk.vm.ci.meta"})
public class URLProtocolTraceProcessorTest {
    private static final String JAR_HANDLER = "sun.net.www.protocol.jar.Handler";

    @Test
    public void createURLStreamHandlerRegistersHandlerConstructor() throws Exception {
        Class<?> configurationSetClass = Class.forName("com.oracle.svm.configure.config.ConfigurationSet");
        Object configurationSet = configurationSetClass.getConstructor().newInstance();
        Object processor = newTraceProcessor();

        processor.getClass().getMethod("process", Reader.class, configurationSetClass).invoke(processor, new StringReader("""
                        [
                          {
                            "tracer": "reflect",
                            "function": "createURLStreamHandler",
                            "class": "java.net.URL$DefaultFactory",
                            "caller_class": "com.example.UrlAgentRepro",
                            "args": ["%s"]
                          }
                        ]
                        """.formatted(JAR_HANDLER)), configurationSet);

        Object reflectionConfiguration = configurationSetClass.getMethod("getReflectionConfiguration").invoke(configurationSet);
        Object handlerType = getConfigurationType(reflectionConfiguration, JAR_HANDLER);
        Assert.assertNotNull(handlerType);
        Object constructorInfo = getConstructorInfo(handlerType);
        Assert.assertEquals("DECLARED", constructorInfo.getClass().getMethod("getDeclaration").invoke(constructorInfo).toString());
        Assert.assertEquals("ACCESSED", constructorInfo.getClass().getMethod("getAccessibility").invoke(constructorInfo).toString());

        Object factoryType = getConfigurationType(reflectionConfiguration, "java.net.URL$DefaultFactory");
        Assert.assertNull(factoryType);
    }

    @Test
    public void appClassPathResourceURLDoesNotRegisterJarHandlerConstructor() throws Exception {
        Class<?> configurationSetClass = Class.forName("com.oracle.svm.configure.config.ConfigurationSet");
        Object configurationSet = configurationSetClass.getConstructor().newInstance();
        Object processor = newTraceProcessor();

        processor.getClass().getMethod("process", Reader.class, configurationSetClass).invoke(processor, new StringReader("""
                        [
                          {
                            "tracer": "reflect",
                            "function": "getResource",
                            "class": "jdk.internal.loader.ClassLoaders$AppClassLoader",
                            "caller_class": "java.lang.Class",
                            "result": true,
                            "args": ["probe.txt"]
                          },
                          {
                            "tracer": "reflect",
                            "function": "getResource",
                            "class": "jdk.internal.loader.ClassLoaders$PlatformClassLoader",
                            "caller_class": "java.lang.ClassLoader",
                            "result": true,
                            "args": ["probe.txt"]
                          },
                          {
                            "tracer": "reflect",
                            "function": "getEntry",
                            "class": "java.util.jar.JarFile",
                            "caller_class": "java.util.jar.JarFile",
                            "result": true,
                            "args": ["probe.txt"]
                          },
                          {
                            "tracer": "reflect",
                            "function": "createURLStreamHandler",
                            "class": "java.net.URL$DefaultFactory",
                            "caller_class": "java.net.URL",
                            "args": ["%s"]
                          }
                        ]
                        """.formatted(JAR_HANDLER)), configurationSet);

        Object reflectionConfiguration = configurationSetClass.getMethod("getReflectionConfiguration").invoke(configurationSet);
        Assert.assertNull(getConfigurationType(reflectionConfiguration, JAR_HANDLER));
    }

    @Test
    public void appClassPathResourceEnumerationURLDoesNotRegisterJarHandlerConstructor() throws Exception {
        Class<?> configurationSetClass = Class.forName("com.oracle.svm.configure.config.ConfigurationSet");
        Object configurationSet = configurationSetClass.getConstructor().newInstance();
        Object processor = newTraceProcessor();

        processor.getClass().getMethod("process", Reader.class, configurationSetClass).invoke(processor, new StringReader("""
                        [
                          {
                            "tracer": "reflect",
                            "function": "getResources",
                            "class": "jdk.internal.loader.ClassLoaders$AppClassLoader",
                            "caller_class": "java.lang.ClassLoader",
                            "result": true,
                            "args": ["META-INF/MANIFEST.MF"]
                          },
                          {
                            "tracer": "reflect",
                            "function": "getResources",
                            "class": "jdk.internal.loader.ClassLoaders$PlatformClassLoader",
                            "caller_class": "java.lang.ClassLoader",
                            "result": true,
                            "args": ["META-INF/MANIFEST.MF"]
                          },
                          {
                            "tracer": "reflect",
                            "function": "createURLStreamHandler",
                            "class": "java.net.URL$DefaultFactory",
                            "caller_class": "java.net.URL",
                            "args": ["%s"]
                          }
                        ]
                        """.formatted(JAR_HANDLER)), configurationSet);

        Object reflectionConfiguration = configurationSetClass.getMethod("getReflectionConfiguration").invoke(configurationSet);
        Assert.assertNull(getConfigurationType(reflectionConfiguration, JAR_HANDLER));
    }

    @Test
    public void appClassPathResourceURLWithoutClasspathJarAccessDoesNotHideExplicitJarURL() throws Exception {
        Class<?> configurationSetClass = Class.forName("com.oracle.svm.configure.config.ConfigurationSet");
        Object configurationSet = configurationSetClass.getConstructor().newInstance();
        Object processor = newTraceProcessor();

        processor.getClass().getMethod("process", Reader.class, configurationSetClass).invoke(processor, new StringReader("""
                        [
                          {
                            "tracer": "reflect",
                            "function": "getResource",
                            "class": "jdk.internal.loader.ClassLoaders$AppClassLoader",
                            "caller_class": "java.lang.Class",
                            "result": true,
                            "args": ["probe.txt"]
                          },
                          {
                            "tracer": "reflect",
                            "function": "getResource",
                            "class": "jdk.internal.loader.ClassLoaders$PlatformClassLoader",
                            "caller_class": "java.lang.ClassLoader",
                            "result": true,
                            "args": ["probe.txt"]
                          },
                          {
                            "tracer": "reflect",
                            "function": "createURLStreamHandler",
                            "class": "java.net.URL$DefaultFactory",
                            "caller_class": "java.net.URL",
                            "args": ["%s"]
                          }
                        ]
                        """.formatted(JAR_HANDLER)), configurationSet);

        Object reflectionConfiguration = configurationSetClass.getMethod("getReflectionConfiguration").invoke(configurationSet);
        Assert.assertNotNull(getConfigurationType(reflectionConfiguration, JAR_HANDLER));
    }

    @Test
    public void urlStreamHandlerProviderLookupDoesNotHideExplicitJarURL() throws Exception {
        Class<?> configurationSetClass = Class.forName("com.oracle.svm.configure.config.ConfigurationSet");
        Object configurationSet = configurationSetClass.getConstructor().newInstance();
        Object processor = newTraceProcessor();

        processor.getClass().getMethod("process", Reader.class, configurationSetClass).invoke(processor, new StringReader("""
                        [
                          {
                            "tracer": "reflect",
                            "function": "getResources",
                            "class": "jdk.internal.loader.ClassLoaders$AppClassLoader",
                            "caller_class": "java.util.ServiceLoader",
                            "result": true,
                            "args": ["META-INF/services/java.net.spi.URLStreamHandlerProvider"]
                          },
                          {
                            "tracer": "reflect",
                            "function": "getResources",
                            "class": "jdk.internal.loader.ClassLoaders$PlatformClassLoader",
                            "caller_class": "java.util.ServiceLoader",
                            "result": true,
                            "args": ["META-INF/services/java.net.spi.URLStreamHandlerProvider"]
                          },
                          {
                            "tracer": "reflect",
                            "function": "createURLStreamHandler",
                            "class": "java.net.URL$DefaultFactory",
                            "caller_class": "java.net.URL",
                            "args": ["%s"]
                          }
                        ]
                        """.formatted(JAR_HANDLER)), configurationSet);

        Object reflectionConfiguration = configurationSetClass.getMethod("getReflectionConfiguration").invoke(configurationSet);
        Assert.assertNotNull(getConfigurationType(reflectionConfiguration, JAR_HANDLER));
    }

    @Test
    public void appClassPathResourceURLDoesNotHideLaterExplicitJarURL() throws Exception {
        Class<?> configurationSetClass = Class.forName("com.oracle.svm.configure.config.ConfigurationSet");
        Object configurationSet = configurationSetClass.getConstructor().newInstance();
        Object processor = newTraceProcessor();

        processor.getClass().getMethod("process", Reader.class, configurationSetClass).invoke(processor, new StringReader("""
                        [
                          {
                            "tracer": "reflect",
                            "function": "getResource",
                            "class": "jdk.internal.loader.ClassLoaders$AppClassLoader",
                            "caller_class": "java.lang.Class",
                            "result": true,
                            "args": ["probe.txt"]
                          },
                          {
                            "tracer": "reflect",
                            "function": "getEntry",
                            "class": "java.util.jar.JarFile",
                            "caller_class": "java.util.jar.JarFile",
                            "result": true,
                            "args": ["probe.txt"]
                          },
                          {
                            "tracer": "reflect",
                            "function": "forName",
                            "class": "java.lang.Class",
                            "caller_class": "com.example.UrlAgentRepro",
                            "args": ["java.lang.String"]
                          },
                          {
                            "tracer": "reflect",
                            "function": "createURLStreamHandler",
                            "class": "java.net.URL$DefaultFactory",
                            "caller_class": "java.net.URL",
                            "args": ["%s"]
                          }
                        ]
                        """.formatted(JAR_HANDLER)), configurationSet);

        Object reflectionConfiguration = configurationSetClass.getMethod("getReflectionConfiguration").invoke(configurationSet);
        Assert.assertNotNull(getConfigurationType(reflectionConfiguration, JAR_HANDLER));
    }

    private static Object newTraceProcessor() throws Exception {
        Class<?> configurationFilterClass = Class.forName("com.oracle.svm.configure.filters.ConfigurationFilter");
        Class<?> accessAdvisorClass = Class.forName("com.oracle.svm.configure.trace.AccessAdvisor");
        Object advisor = accessAdvisorClass.getConstructor(boolean.class, configurationFilterClass, configurationFilterClass, String.class).newInstance(false, null, null, null);
        return Class.forName("com.oracle.svm.configure.trace.TraceProcessor").getConstructor(accessAdvisorClass).newInstance(advisor);
    }

    private static Object getConfigurationType(Object reflectionConfiguration, String className) throws Exception {
        Class<?> conditionClass = Class.forName("com.oracle.svm.configure.UnresolvedAccessCondition");
        Object unconditional = conditionClass.getMethod("unconditional").invoke(null);
        Class<?> descriptorInterface = Class.forName("com.oracle.svm.configure.ConfigurationTypeDescriptor");
        Object descriptor = Class.forName("com.oracle.svm.configure.NamedConfigurationTypeDescriptor").getMethod("fromReflectionName", String.class).invoke(null, className);
        return reflectionConfiguration.getClass().getMethod("get", conditionClass, descriptorInterface).invoke(reflectionConfiguration, unconditional, descriptor);
    }

    private static Object getConstructorInfo(Object configurationType) throws Exception {
        Class<?> configurationMethodClass = Class.forName("com.oracle.svm.configure.config.ConfigurationMethod");
        Object constructorMethod = configurationMethodClass.getConstructor(String.class, String.class).newInstance("<init>", "()V");
        Class<?> configurationTypeClass = Class.forName("com.oracle.svm.configure.config.ConfigurationType");
        return Class.forName("com.oracle.svm.configure.config.ConfigurationType$TestBackdoor")
                        .getMethod("getMethodInfoIfPresent", configurationTypeClass, configurationMethodClass)
                        .invoke(null, configurationType, constructorMethod);
    }
}
