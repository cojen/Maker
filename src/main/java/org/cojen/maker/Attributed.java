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
import java.util.Objects;

/**
 * Defines an item which can have attributes.
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

    public void addAttribute(String name, Object value) {
        Objects.requireNonNull(name);
        addAttribute(defineAttribute(name, value));
    }

    private Attribute defineAttribute(String name, Object value) {
        if (value == null) {
            return new Attribute.Empty(mConstants, name);
        } else if (value instanceof byte[]) {
            return new Attribute.Bytes(mConstants, name, (byte[]) value);
        } else if (!value.getClass().isArray()) {
            return new Attribute.Constant(mConstants, name, defineConstant(value));
        } else if (value instanceof Object[]) {
            return defineCompositeAttribute(name, (Object[]) value);
        } else {
            throw new IllegalArgumentException();
        }
    }

    private Attribute defineCompositeAttribute(String name, Object[] values) {
        if (values.length == 0) {
            return defineAttribute(name, null);
        } else if (values.length == 1) {
            return defineAttribute(name, values[0]);
        } else {
            var entries = new Attribute[values.length];
            for (int i=0; i<values.length; i++) {
                entries[i] = defineAttribute(null, values[i]);
            }
            return new Attribute.Composite(mConstants, name, entries);
        }
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

    public void addSignature(Object... components) {
        ConstantPool.C_UTF8 sig = mConstants.addUTF8(fullSignature(components));
        addAttribute(new Attribute.Constant(mConstants, "Signature", sig));
    }

    static String fullSignature(Object... components) {
        if (components.length == 0) {
            throw new IllegalArgumentException();
        }

        String first = resolveComponent(components[0]);
        if (components.length == 1) {
            return first;
        }

        var b = new StringBuilder(first.length() + 16);

        for (int i=0; i < components.length; i++) {
            String component = i == 0 ? first : resolveComponent(components[i]);
            if (component.startsWith("<")) {
                i = appendTypeArgs(0, b, component, components, i);
            } else {
                b.append(component);
            }
        }

        return b.toString();
    }

    private static String resolveComponent(Object component) {
        if (component instanceof String) {
            return (String) component;
        }

        Type type;
        if (component instanceof Class) {
            type = Type.from((Class) component);
        } else if (component instanceof Typed) {
            type = ((Typed) component).type();
        } else {
            throw new IllegalArgumentException("Unsupported component type");
        }

        return type.descriptor();
    }

    /**
     * @param first must start with a '<' character
     * @return updated components array index
     */
    private static int appendTypeArgs(int depth, StringBuilder b, String first,
                                      Object[] components, int i)
    {
        boolean semi = false;
        int end = b.length() - 1;
        if (end >= 0 && b.charAt(end) == ';') {
            b.setLength(end);
            semi = true;
        }

        b.append(first);
 
        for (++i; i < components.length; i++) {
            String component = resolveComponent(components[i]);
            if (component.startsWith("<")) {
                i = appendTypeArgs(depth + 1, b, component, components, i);
            } else {
                b.append(component);
                if (component.contains(">")) {
                    break;
                }
            }
        }

        if (semi) {
            for (int j = b.length(); --j >= end; ) {
                if (b.charAt(j) == '>') {
                    if (depth <= 0) {
                        b.insert(j + 1, ';');
                        return i;
                    }
                    depth--;
                }
            }

            // Restore the semicolon if the angle bracket syntax is wrong.
            b.insert(end, ';');
        }

        return i;
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
