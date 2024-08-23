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

import java.lang.module.ModuleDescriptor;

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
        addAttribute(NamedAttribute.make(this, mConstants, name, value));
    }

    private static class NamedAttribute {
        // This code is in an inner class to avoid loading it eagerly.

        static Attribute make(Attributed a, ConstantPool cp, String name, Object value) {
            define: {
                if (a instanceof TheMethodMaker && ((TheMethodMaker) a).mClassMaker.isAnnotation()
                    && "AnnotationDefault".equals(name))
                {
                    return new Attribute.AnnotationDefault
                        (cp, TheAnnotationMaker.toElement(null, cp, value));
                } else if (value == null) {
                    return new Attribute.Empty(cp, name);
                } else if (value instanceof byte[] bytes) {
                    return new Attribute.Bytes(cp, name, bytes);
                } else if (!value.getClass().isArray()) {
                    final ConstantPool.Constant constant;
                    if (value instanceof String str) {
                        constant = cp.addUTF8(str);
                    } else if (value instanceof Class clazz) {
                        constant = cp.addClass(BaseType.from(clazz));
                    } else if (value instanceof Typed typed) {
                        constant = cp.addClass(typed.type());
                    } else if (value instanceof Number) {
                        if (value instanceof Integer) {
                            constant = cp.addInteger((int) value);
                        } else if (value instanceof Long) {
                            constant = cp.addLong((long) value);
                        } else if (value instanceof Float) {
                            constant = cp.addFloat((float) value);
                        } else if (value instanceof Double) {
                            constant = cp.addDouble((double) value);
                        } else {
                            break define;
                        }
                    } else if (value instanceof ModuleDescriptor md) {
                        return ModuleAttribute.make(a, name, md);
                    } else {
                        break define;
                    }

                    return new Attribute.Constant(cp, name, constant);
                } else if (value instanceof Object[] values) {
                    if (values.length == 0) {
                        return make(a, cp, name, null);
                    } else if (values.length == 1) {
                        return make(a, cp, name, values[0]);
                    } else {
                        var entries = new Attribute[values.length];
                        for (int i=0; i<values.length; i++) {
                            entries[i] = make(a, cp, null, values[i]);
                        }
                        return new Attribute.Composite(cp, name, entries);
                    }
                }
            }

            throw new IllegalArgumentException();
        }
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
        if (component instanceof String str) {
            return str;
        }

        BaseType type;
        if (component instanceof Class clazz) {
            type = BaseType.from(clazz);
        } else if (component instanceof Typed typed) {
            type = typed.type();
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

    int attributesLength() {
        int length = 2;
        if (mAttributes != null) {
            for (Attribute attr : mAttributes) {
                length += 6 + attr.length();
            }
        }
        return length;
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
