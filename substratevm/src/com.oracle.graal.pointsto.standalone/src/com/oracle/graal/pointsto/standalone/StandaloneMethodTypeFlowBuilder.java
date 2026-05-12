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

package com.oracle.graal.pointsto.standalone;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.ConstantTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.standalone.meta.StandaloneConstantReflectionProvider;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Standalone-specific type-flow builder customizations.
 *
 * Unsupported guest metadata constants, such as Espresso static-field-base cookies, are modeled as
 * non-null type-only states of their declaring class instead of being materialized as precise image
 * heap constants. This keeps the guest implementation detail out of standalone's shadow heap while
 * preserving a conservative approximation for type propagation.
 */
public final class StandaloneMethodTypeFlowBuilder extends MethodTypeFlowBuilder {
    private final StandaloneConstantReflectionProvider standaloneConstantReflection;

    public StandaloneMethodTypeFlowBuilder(PointsToAnalysis bb, PointsToAnalysisMethod method, MethodFlowsGraph flowsGraph, MethodFlowsGraph.GraphKind graphKind) {
        super(bb, method, flowsGraph, graphKind);
        this.standaloneConstantReflection = (StandaloneConstantReflectionProvider) bb.getConstantReflectionProvider();
    }

    @Override
    protected ConstantTypeFlow createObjectConstantSource(AnalysisType stampedType, JavaConstant constantValue, BytecodePosition position) {
        AnalysisType unsupportedType = standaloneConstantReflection.getUnsupportedObjectType(constantValue);
        if (unsupportedType != null) {
            return super.createTypeOnlyObjectConstantSource(unsupportedType, position);
        }
        return super.createObjectConstantSource(stampedType, constantValue, position);
    }

    @Override
    protected void registerObjectConstantRoot(JavaConstant constantValue, AnalysisType stampedType, BytecodePosition position) {
        AnalysisType unsupportedType = standaloneConstantReflection.getUnsupportedObjectType(constantValue);
        if (unsupportedType != null) {
            unsupportedType.registerAsReachable(position);
            return;
        }
        super.registerObjectConstantRoot(constantValue, stampedType, position);
    }
}
