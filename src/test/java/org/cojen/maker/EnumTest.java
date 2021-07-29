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
public class EnumTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(EnumTest.class.getName());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void basic() throws Exception {
        // Defines an enum class. In addition to adding the enum modifier, a properly defined
        // enum must extend the Enum class, and it must also define a public "values" method
        // which returns an array of all the enum field instances.

        ClassMaker cm = ClassMaker.begin().public_().final_().enum_().extend(Enum.class);

        MethodMaker mm = cm.addConstructor(String.class, int.class).private_();
        mm.invokeSuperConstructor(mm.param(0), mm.param(1));

        FieldMaker fm = cm.addField(cm, "THING").public_().static_().final_().enum_();

        mm = cm.addClinit();
        var thingField = mm.field("THING");
        thingField.set(mm.new_(cm, "THING", 0));

        mm = cm.addMethod(cm.arrayType(1), "values").public_().static_();
        var valuesVar = mm.new_(cm.arrayType(1), 1);
        valuesVar.aset(0, mm.field("THING"));
        mm.return_(valuesVar);

        var clazz = (Class<Enum>) cm.finish();

        assertTrue(clazz.isEnum());

        var thing = Enum.valueOf(clazz, "THING");
        assertEquals("THING", thing.name());
        assertEquals(0, thing.ordinal());
    }
}
