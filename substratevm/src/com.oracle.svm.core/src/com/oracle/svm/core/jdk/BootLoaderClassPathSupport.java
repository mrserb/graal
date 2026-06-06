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
package com.oracle.svm.core.jdk;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/// Accesses the runtime boot loader class path exposed via `ClassLoaders.bootLoader().ucp`.
/// This is used for resources and classes supplied with `-Xbootclasspath/a:` in
/// configurations where the boot loader is initialized at run time.
public final class BootLoaderClassPathSupport {
    /// Records the source path for boot-append packages only after a class in the package loads.
    private static final ConcurrentHashMap<String, String> loadedBootAppendPackageLocations = new ConcurrentHashMap<>();

    private BootLoaderClassPathSupport() {
    }

    /// Looks up a resource on the boot loader's runtime class path and returns its bytes.
    public static byte[] getResourceBytes(String resourceName) throws IOException {
        Target_jdk_internal_loader_BuiltinClassLoader bootLoader = Target_jdk_internal_loader_ClassLoaders.bootLoader();
        if (bootLoader == null || bootLoader.ucp == null) {
            return null;
        }
        Target_jdk_internal_loader_Resource resource = bootLoader.ucp.getResource(resourceName);
        if (resource == null) {
            return null;
        }
        return resource.getBytes();
    }

    /// Looks up a class on the boot loader's runtime class path and records its package source.
    public static byte[] getClassBytes(String internalClassName) throws IOException {
        Target_jdk_internal_loader_BuiltinClassLoader bootLoader = Target_jdk_internal_loader_ClassLoaders.bootLoader();
        if (bootLoader == null || bootLoader.ucp == null) {
            return null;
        }
        Target_jdk_internal_loader_Resource resource = bootLoader.ucp.getResource(internalClassName + ".class");
        if (resource == null) {
            return null;
        }
        recordPackageLocation(internalClassName, resource);
        return resource.getBytes();
    }

    /// Looks up the boot loader class path entry that provided `internalPackageName` (e.g. `org/foo/impl`).
    ///
    /// This is only for boot loader package discovery after a runtime-loaded boot class has made
    /// the package observable. It must not be used as a general class path package lookup.
    public static String getBootLoaderPackageLocation(String internalPackageName) {
        return loadedBootAppendPackageLocations.get(internalPackageName);
    }

    /// Records the package source for `internalClassName` from the class resource that was loaded.
    private static void recordPackageLocation(String internalClassName, Target_jdk_internal_loader_Resource resource) {
        int lastSlash = internalClassName.lastIndexOf('/');
        if (lastSlash == -1) {
            return;
        }
        String internalPackageName = internalClassName.substring(0, lastSlash);
        String location = getCodeSourcePath(resource);
        if (location != null) {
            loadedBootAppendPackageLocations.putIfAbsent(internalPackageName, location);
        }
    }

    /// Gets the file-system path for `resource`.
    private static String getCodeSourcePath(Target_jdk_internal_loader_Resource resource) {
        URL codeSourceURL = resource.getCodeSourceURL();
        if (codeSourceURL == null || !"file".equals(codeSourceURL.getProtocol())) {
            return null;
        }
        try {
            return Path.of(codeSourceURL.toURI()).toString();
        } catch (IllegalArgumentException | URISyntaxException e) {
            return null;
        }
    }
}
