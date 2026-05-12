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

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import com.oracle.graal.pointsto.standalone.test.classes.SmallReachabilityCase;

/**
 * Repeated standalone-analysis run that increases the chance of hitting the Espresso build-time
 * heap TruffleSafepoint initialization failure.
 */
public class EspressoTruffleSafepointReproducerTest extends StandaloneAnalysisTest {
    private static final String VMACCESS_NAME_PROPERTY = "com.oracle.graal.pointsto.standalone.vmaccess.name";
    private static final int MAX_ATTEMPTS = 8;
    private static final String[] REPRODUCER_SEQUENCE = {
                    "collectionsProfile",
                    "encodingProfile",
                    "platformProfile"
    };

    @Test
    public void testRepeatedPlatformSequence() {
        assumeTrue("This reproducer is relevant only for Espresso VMAccess.", "espresso".equals(System.getProperty(VMACCESS_NAME_PROPERTY)));
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            for (String entryMethod : REPRODUCER_SEQUENCE) {
                runAnalysisMethod(SmallReachabilityCase.class, entryMethod);
            }
        }
        assertEquals("Expected the custom common-pool worker factory to eliminate downgraded Espresso class-initialization failures.", 0,
                        standaloneHost().getClassInitializationFailureCount());
    }
}
