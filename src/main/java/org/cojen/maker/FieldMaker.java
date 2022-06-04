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

/**
 * Allows new fields to be defined within a class.
 *
 * @author Brian S O'Neill
 * @see ClassMaker#addField
 */
public interface FieldMaker extends Maker {
    /**
     * Switch this field to be public. Fields are package-private by default.
     *
     * @return this
     */
    FieldMaker public_();

    /**
     * Switch this field to be private. Fields are package-private by default.
     *
     * @return this
     */
    FieldMaker private_();

    /**
     * Switch this field to be protected. Fields are package-private by default.
     *
     * @return this
     */
    FieldMaker protected_();

    /**
     * Switch this field to be static. Fields are non-static by default.
     *
     * @return this
     */
    FieldMaker static_();

    /**
     * Switch this field to be final. Fields are non-final by default.
     *
     * @return this
     */
    FieldMaker final_();

    /**
     * Switch this field to be volatile. Fields are non-volatile by default.
     *
     * @return this
     */
    FieldMaker volatile_();

    /**
     * Switch this field to be transient. Fields are non-transient by default.
     *
     * @return this
     */
    FieldMaker transient_();

    /**
     * Indicate that this field is synthetic. Fields are non-synthetic by default.
     *
     * @return this
     */
    FieldMaker synthetic();

    /**
     * Indicate that this field is an enum constant. No checks or modifications are performed
     * to ensure that the enum field is defined correctly.
     *
     * @return this
     */
    FieldMaker enum_();

    /**
     * Set an initial constant value for this field. The allowed constants are the same as
     * those allowed by the {@link Variable#set Variable.set} method. Complex constants can be
     * assigned using a {@link ClassMaker#addClinit static initializer}.
     *
     * @return this
     * @throws IllegalStateException if not a static field
     * @throws IllegalArgumentException if the value type is unsupported, or if it's not
     * compatible with the field type
     */
    FieldMaker init(Object value);
}
