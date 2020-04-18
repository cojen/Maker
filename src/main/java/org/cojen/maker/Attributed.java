/*
 *  Copyright (C) 2019 Cojen.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.maker;

import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines an entity which can have attributes.
 *
 * @author Brian S O'Neill
 */
abstract class Attributed {
    final ConstantPool mConstants;

    List<Attribute> mAttributes;

    Attributed(ConstantPool cp) {
        mConstants = cp;
    }

    void addAttribute(Attribute attr) {
        if (mAttributes == null) {
            mAttributes = new ArrayList<>(4);
        }
        mAttributes.add(attr);
    }

    void writeAttributesTo(DataOutput dout) throws IOException {
        if (mAttributes == null) {
            dout.writeShort(0);
        } else {
            TheClassMaker.checkSize(mAttributes, 65535, "Attribute");
            dout.writeShort(mAttributes.size());
            for (Attribute attr : mAttributes) {
                attr.writeTo(dout);
            }
        }
    }
}
