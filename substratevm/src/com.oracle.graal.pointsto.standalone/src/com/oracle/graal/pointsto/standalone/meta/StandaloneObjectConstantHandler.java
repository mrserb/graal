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

import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder.ObjectConstantHandler;
import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Standalone-owned object-constant policy used by the shared method type-flow builder.
 *
 * Unsupported guest metadata constants, such as Espresso static-field-base cookies, are exposed as
 * conservative type-only facts of their declaring class instead of being materialized precisely in
 * standalone's shadow heap.
 */
public final class StandaloneObjectConstantHandler implements ObjectConstantHandler {
    private final StandaloneConstantReflectionProvider standaloneConstantReflection;

    public StandaloneObjectConstantHandler(StandaloneConstantReflectionProvider standaloneConstantReflection) {
        this.standaloneConstantReflection = standaloneConstantReflection;
    }

    @Override
    public AnalysisType getTypeOnlyExposedType(JavaConstant constantValue) {
        return standaloneConstantReflection.getUnsupportedObjectType(constantValue);
    }
}
