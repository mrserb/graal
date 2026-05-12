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

import java.lang.reflect.Method;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.shared.util.ReflectionUtil;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Encapsulates guest-provider-specific probes for object constants that standalone analysis must
 * treat as opaque metadata rather than as normal heap objects.
 *
 * The current guest-specific case is Espresso's static-field-base cookie, but the rest of the
 * standalone pipeline interacts with that case only through this standalone-owned descriptor.
 */
public final class StandaloneUnsupportedGuestObjectSupport {
    private static final String GET_TYPE_FOR_STATIC_BASE_METHOD_NAME = "getTypeForStaticBase";

    private final AnalysisUniverse universe;
    private final ConstantReflectionProvider original;
    private final Method getTypeForStaticBaseMethod;

    public StandaloneUnsupportedGuestObjectSupport(AnalysisUniverse universe, ConstantReflectionProvider original) {
        this.universe = universe;
        this.original = original;
        this.getTypeForStaticBaseMethod = ReflectionUtil.lookupPublicMethodInClassHierarchy(true, original.getClass(), GET_TYPE_FOR_STATIC_BASE_METHOD_NAME, JavaConstant.class);
    }

    /**
     * Returns the exposed analysis type for guest constants that standalone must treat as opaque
     * metadata instead of ordinary heap objects.
     *
     * This probe is best-effort on purpose. Unknown, null, primitive, or otherwise unsupported
     * guest constants are simply treated as ordinary constants.
     */
    public AnalysisType getExposedType(JavaConstant constant) {
        JavaConstant probe = unwrapHostedConstant(constant);
        if (probe == null || probe.isNull() || probe.getJavaKind() != JavaKind.Object) {
            return null;
        }
        /*
         * Espresso static-field-base cookies are metadata handles, not guest heap objects. Expose
         * only the declaring class they stand for so standalone can propagate a conservative type
         * without materializing the cookie in the shadow heap.
         */
        AnalysisType staticBaseType = getStaticFieldBaseType(probe);
        return staticBaseType;
    }

    private AnalysisType getStaticFieldBaseType(JavaConstant constant) {
        if (getTypeForStaticBaseMethod == null) {
            return null;
        }
        try {
            ResolvedJavaType type = ReflectionUtil.invokeMethod(getTypeForStaticBaseMethod, original, constant);
            if (type == null) {
                return null;
            }
            if (type instanceof AnalysisType analysisType) {
                return analysisType;
            }
            return universe.lookup(type);
        } catch (RuntimeException e) {
            /*
             * Some guest constants are not valid inputs for the optional provider probe and should
             * simply remain ordinary constants from standalone's point of view.
             */
            return null;
        }
    }

    private static JavaConstant unwrapHostedConstant(JavaConstant constant) {
        if (constant instanceof ImageHeapConstant imageHeapConstant && imageHeapConstant.getHostedObject() != null) {
            return imageHeapConstant.getHostedObject();
        }
        return constant;
    }
}
