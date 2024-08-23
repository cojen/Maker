/*
 *  Copyright 2021 Cojen.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.maker;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class CandidateTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(CandidateTest.class.getName());
    }

    @Test
    public void arrayCompare() {
        var obj = BaseType.from(Object.class);
        var str = BaseType.from(String.class);
        var num = BaseType.from(Number.class);

        var objArray = BaseType.from(Object[].class);
        var strArray = BaseType.from(String[].class);
        var numArray = BaseType.from(Number[].class);

        assertEquals(-1, Candidate.compare(strArray, objArray, obj));
        assertEquals(1, Candidate.compare(strArray, obj, objArray));

        assertEquals(0, Candidate.compare(strArray, str, num));
        assertEquals(0, Candidate.compare(objArray, strArray, numArray));
    }
}
