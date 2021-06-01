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
abstract class Attributed {
    ConstantPool mConstants;

    List<Attribute> mAttributes;

    private Attribute.Annotations[] mAnnotationSets;

    Attributed(ConstantPool cp) {
        mConstants = cp;
    }

    void addAttribute(Attribute attr) {
        if (mAttributes == null) {
            mAttributes = new ArrayList<>(4);
        }
        mAttributes.add(attr);
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
