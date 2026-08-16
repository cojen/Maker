/*
 *  Copyright 2026 Cojen.org
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

import java.util.Arrays;

/**
 * A simple specialized BitSet which supports a fixed amount of negative indexes. These are
 * used for tracking unset strict fields within constructors.
 *
 * @author Brian S. O'Neill
 */
final class BitList implements Cloneable {
    private final int mNumNegative;

    private long[] mData;

    /**
     * @param numNegative fixed amount of negative indexes to support
     */    
    BitList(int numNegative) {
        mNumNegative = numNegative;
        mData = new long[(numNegative + 64) >> 6];
    }

    private BitList(BitList other) {
        mNumNegative = other.mNumNegative;
        mData = other.mData.clone();
    }

    public boolean get(int index) {
        index += mNumNegative;
        long[] data = mData;
        int slot = index >> 6;
        return slot < data.length && (data[slot] & (1L << index)) != 0;
    }

    public void set(int index) {
        index += mNumNegative;
        long[] data = mData;
        int slot = index >> 6;
        if (slot >= data.length) {
            mData = data = Arrays.copyOf(data, Math.max(slot + 1, data.length << 1));
        }
        data[slot] |= (1L << index);
    }

    /**
     * @throws IllegalArgumentException if the other list has a different amount of negative
     * indexes
     */
    public void and(BitList other) {
        if (mNumNegative != other.mNumNegative) {
            throw new IllegalArgumentException();
        }

        long[] thisData = mData;
        long[] otherData = other.mData;

        for (int i=0; i<thisData.length; i++) {
            if (i < otherData.length) {
                thisData[i] &= otherData[i];
            } else {
                thisData[i] = 0;
            }
        }
    }

    @Override
    public BitList clone() {
        return new BitList(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof BitList other)) {
            return false;
        }

        long[] thisData = mData;
        long[] otherData = other.mData;

        for (int i=0; i<Math.max(thisData.length, otherData.length); i++) {
            if (bits(thisData, i) != bits(otherData, i)) {
                return false;
            }
        }

        return true;
    }

    private static long bits(long[] data, int slot) {
        return slot < data.length ? data[slot] : 0;
    }
}
