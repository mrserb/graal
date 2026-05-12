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

package com.oracle.graal.pointsto.standalone.test;

import static org.junit.Assert.assertTrue;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.standalone.test.classes.SmallReachabilityCase;

/**
 * Profiles the approximate reachability impact of representative {@code java.base} areas.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class JdkReachabilityProfileTest extends StandaloneAnalysisTest {

    @Test
    public void testCollectionsProfile() {
        runProfile("collections", "collectionsProfile", range(0, 2_173));
    }

    @Test
    public void testConcurrencyProfile() {
        runProfile("concurrency", "concurrencyProfile", range(0, 2_104));
    }

    @Test
    public void testEncodingProfile() {
        runProfile("encoding", "encodingProfile", range(0, 2_342));
    }

    @Test
    public void testRegexProfile() {
        runProfile("regex", "regexProfile", range(0, 2_194));
    }

    @Test
    public void testTimeProfile() {
        runProfile("time", "timeProfile", range(0, 40));
    }

    @Test
    public void testFormattingProfile() {
        runProfile("formatting", "formattingProfile", range(0, 2_115));
    }

    @Test
    public void testMathProfile() {
        runProfile("math", "mathProfile", range(0, 2_407));
    }

    @Test
    public void testPlatformProfile() {
        runProfile("platform", "platformProfile", range(0, 5_511));
    }

    private void runProfile(String profileName, String entryMethodName, Range expectedRange) {
        runAnalysisMethod(SmallReachabilityCase.class, entryMethodName);
        long reachableMethods = universe().getMethods().stream().filter(AnalysisMethod::isReachable).count();
        assertTrue("Expected " + profileName + " to reach between " + expectedRange.min + " and " + expectedRange.max + " methods, but found " + reachableMethods,
                        reachableMethods >= expectedRange.min && reachableMethods <= expectedRange.max);
    }

    private static Range range(long min, long max) {
        return new Range(min, max);
    }

    private record Range(long min, long max) {
    }
}
