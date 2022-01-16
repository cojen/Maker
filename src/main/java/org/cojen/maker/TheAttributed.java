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

import java.util.ArrayList;
import java.util.List;

/**
 * Defines an entity which can have attributes.
 *
 * @author Brian S O'Neill
 */
abstract class TheAttributed implements Attributed {
    ConstantPool mConstants;

    List<Attribute> mAttributes;

    private Attribute.Annotations[] mAnnotationSets;

    TheAttributed(ConstantPool cp) {
        mConstants = cp;
    }

    void addAttribute(Attribute attr) {
        if (mAttributes == null) {
            mAttributes = new ArrayList<>(4);
        }
        mAttributes.add(attr);
    }

    @Override
    public void addAttribute(String name, Object value) {
        Attribute attr;

        if (value == null) {
            attr = new Attribute.Empty(mConstants, name);
        } else if (value instanceof byte[]) {
            attr = new Attribute.Bytes(mConstants, name, (byte[]) value);
        } else if (!value.getClass().isArray()) {
            attr = new Attribute.Constant(mConstants, name, defineConstant(value));
        } else if (value instanceof Object[]) {
            var values = (Object[]) value;
            if (values.length > 65535) {
                throw new IllegalArgumentException();
            } else {
                var list = new Attribute.ConstantList(mConstants, name);
                for (Object v : values) {
                    list.addEntry(defineConstant(v));
                }
                attr = list;
            }
        } else {
            throw new IllegalArgumentException();
        }

        addAttribute(attr);
    }

    private ConstantPool.Constant defineConstant(Object value) {
        if (value instanceof String) {
            return mConstants.addUTF8((String) value);
        }

        if (value instanceof Class) {
            return mConstants.addClass(Type.from((Class) value));
        }

        if (value instanceof Typed) {
            return mConstants.addClass(((Typed) value).type());
        }

        if (value instanceof Number) {
            if (value instanceof Integer) {
                return mConstants.addInteger((int) value);
            }
            if (value instanceof Long) {
                return mConstants.addLong((long) value);
            }
            if (value instanceof Float) {
                return mConstants.addFloat((float) value);
            }
            if (value instanceof Double) {
                return mConstants.addDouble((double) value);
            }
        }

        throw new IllegalArgumentException();
    }

    TheAnnotationMaker addAnnotation(TheAnnotationMaker am, boolean visible) {
        if (mAnnotationSets == null) {
            mAnnotationSets = new Attribute.Annotations[2];
        }
        int which = visible ? 0 : 1;
        Attribute.Annotations annotations = mAnnotationSets[which];
        if (annotations == null) {
            annotations = new Attribute.Annotations(mConstants, visible);
            addAttribute(annotations);
            mAnnotationSets[which] = annotations;
        }
        annotations.add(am);
        return am;
    }

    void writeAttributesTo(BytesOut out) throws IOException {
        if (mAttributes == null) {
            out.writeShort(0);
        } else {
            TheClassMaker.checkSize(mAttributes, 65535, "Attribute");
            out.writeShort(mAttributes.size());
            for (Attribute attr : mAttributes) {
                attr.writeTo(out);
            }
        }
    }
}
