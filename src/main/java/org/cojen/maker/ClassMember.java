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

import java.io.IOException;

/**
 * Defines a member of a class -- fields and methods.
 *
 * @author Brian S O'Neill
 */
abstract class ClassMember extends TheAttributed {
    final ConstantPool.C_UTF8 mName;
    final ConstantPool.C_UTF8 mDescriptor;

    int mModifiers;

    ClassMember(ConstantPool cp, ConstantPool.C_UTF8 name, ConstantPool.C_UTF8 desc) {
        super(cp);
        mName = name;
        mDescriptor = desc;
    }

    ClassMember(ConstantPool cp, String name, String desc) {
        this(cp, cp.addUTF8(name), cp.addUTF8(desc));
    }

    final void writeTo(BytesOut out) throws IOException {
        out.writeShort(mModifiers);
        out.writeShort(mName.mIndex);
        out.writeShort(mDescriptor.mIndex);
        writeAttributesTo(out);
    }

    String getName() {
        return mName.mValue;
    }
}
