/*
 *  Copyright 2022 Cojen.org
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

import java.lang.module.ModuleDescriptor;

import java.io.IOException;

import java.util.List;
import java.util.Set;

/**
 * 
 *
 * @author Brian S O'Neill
 */
class ModuleAttribute extends Attribute {
    static ModuleAttribute make(Attributed a, String name, ModuleDescriptor desc) {
        if (!(a instanceof TheClassMaker) || !"Module".equals(name)) {
            throw new IllegalArgumentException();
        }

        var cm = (TheClassMaker) a;

        if (!"module-info".equals(cm.name())) {
            throw new IllegalStateException();
        }

        cm.toModule();

        ConstantPool cp = cm.mConstants;
        var attr = new ModuleAttribute(cp, name);

        try {
            attr.encode(cp, desc);
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        Set<String> packages = desc.packages();
        if (!packages.isEmpty()) {
            cm.addAttribute(new Packages(cp, packages));
        }

        String mainClass = desc.mainClass().orElse(null);
        if (mainClass != null) {
            cm.addAttribute(new Attribute.Constant(cp, "ModuleMainClass", cp.addClass(mainClass)));
        }

        return attr;
    }

    private final BytesOut mBytes;

    private ModuleAttribute(ConstantPool cp, String name) {
        super(cp, name);
        mBytes = new BytesOut(null, 100);
    }

    @Override
    int length() {
        return mBytes.size();
    }

    @Override
    void writeDataTo(BytesOut out) throws IOException {
        out.write(mBytes);
    }

    private void encode(ConstantPool cp, ModuleDescriptor desc) throws IOException {
        // module_name_index
        mBytes.writeShort(cp.addModule(desc.name()).mIndex);

        // module_flags
        {
            int flags = 0;

            for (ModuleDescriptor.Modifier mod : desc.modifiers()) {
                switch (mod) {
                case OPEN:      flags |= 0x0020; break;
                case SYNTHETIC: flags |= 0x1000; break;
                case MANDATED:  flags |= 0x8000; break;
                }
            }

            mBytes.writeShort(flags);
        }

        // module_version_index
        {
            String version = desc.rawVersion().orElse(null);
            mBytes.writeShort(version == null ? 0 : cp.addUTF8(version).mIndex);
        }

        // requires
        {
            Set<ModuleDescriptor.Requires> set = desc.requires();
            // requires_index
            mBytes.writeShort(set.size());

            for (ModuleDescriptor.Requires requires : set) {
                mBytes.writeShort(cp.addModule(requires.name()).mIndex);

                int flags = 0;

                for (ModuleDescriptor.Requires.Modifier mod : requires.modifiers()) {
                    switch (mod) {
                    case TRANSITIVE: flags |= 0x0020; break;
                    case STATIC:     flags |= 0x0040; break;
                    case SYNTHETIC:  flags |= 0x1000; break;
                    case MANDATED:   flags |= 0x8000; break;
                    }
                }

                // requires_flags
                mBytes.writeShort(flags);

                String version = requires.rawCompiledVersion().orElse(null);
                // requires_version_index
                mBytes.writeShort(version == null ? 0 : cp.addUTF8(version).mIndex);
            }
        }

        // exports
        {
            Set<ModuleDescriptor.Exports> set = desc.exports();
            // exports_count
            mBytes.writeShort(set.size());

            for (ModuleDescriptor.Exports exports : set) {
                // exports_index
                mBytes.writeShort(cp.addPackage(exports.source()).mIndex);

                int flags = 0;

                for (ModuleDescriptor.Exports.Modifier mod : exports.modifiers()) {
                    switch (mod) {
                    case SYNTHETIC:  flags |= 0x1000; break;
                    case MANDATED:   flags |= 0x8000; break;
                    }
                }

                // exports_flags
                mBytes.writeShort(flags);

                Set<String> targets = exports.targets();
                // exports_to_count
                mBytes.writeShort(targets.size());
                for (String target : targets) {
                    // exports_to_index
                    mBytes.writeShort(cp.addModule(target).mIndex);
                }
            }
        }

        // opens
        {
            Set<ModuleDescriptor.Opens> set = desc.opens();
            // opens_count
            mBytes.writeShort(set.size());

            for (ModuleDescriptor.Opens opens : set) {
                // opens_index
                mBytes.writeShort(cp.addPackage(opens.source()).mIndex);

                int flags = 0;

                for (ModuleDescriptor.Opens.Modifier mod : opens.modifiers()) {
                    switch (mod) {
                    case SYNTHETIC:  flags |= 0x1000; break;
                    case MANDATED:   flags |= 0x8000; break;
                    }
                }

                // opens_flags
                mBytes.writeShort(flags);

                Set<String> targets = opens.targets();
                // opens_to_count
                mBytes.writeShort(targets.size());
                for (String target : targets) {
                    // opens_to_index
                    mBytes.writeShort(cp.addModule(target).mIndex);
                }
            }
        }

        // uses
        {
            Set<String> set = desc.uses();
            // uses_count
            mBytes.writeShort(set.size());
            for (String use : set) {
                // uses_index
                mBytes.writeShort(cp.addClass(use).mIndex);
            }
        }

        // provides
        {
            Set<ModuleDescriptor.Provides> set = desc.provides();
            // provides_count
            mBytes.writeShort(set.size());
            for (ModuleDescriptor.Provides provides : set) {
                // provides_index
                mBytes.writeShort(cp.addClass(provides.service()).mIndex);
                List<String> providers = provides.providers();
                // provides_with_count
                mBytes.writeShort(providers.size());
                for (String provider : providers) {
                    // provides_with_index
                    mBytes.writeShort(cp.addClass(provider).mIndex);
                }
            }
        }
    }

    static class Packages extends Attribute {
        private final ConstantPool.C_String[] mPackages;

        Packages(ConstantPool cp, Set<String> packages) {
            super(cp, "ModulePackages");
            mPackages = new ConstantPool.C_String[packages.size()];
            int i = 0;
            for (String p : packages) {
                mPackages[i++] = cp.addPackage(p);
            }
        }

        @Override
        int length() {
            return 2 + mPackages.length * 2;
        }

        @Override
        void writeDataTo(BytesOut out) throws IOException {
            out.writeShort(mPackages.length);
            for (ConstantPool.C_String p : mPackages) {
                out.writeShort(p.mIndex);
            }
        }
    }
}
