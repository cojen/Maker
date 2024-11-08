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
 * 
 *
 * @author Brian S O'Neill
 */
class Opcodes {
    // Note: The commented out opcodes are generated programmatically, except for the extra dup
    // variants, which are never used by TheMethodMaker.

    static final byte
        NOP = (byte) 0,
        ACONST_NULL = (byte) 1,
        //ICONST_M1 = (byte) 2,
        ICONST_0 = (byte) 3,
        //ICONST_1 = (byte) 4,
        //ICONST_2 = (byte) 5,
        //ICONST_3 = (byte) 6,
        //ICONST_4 = (byte) 7,
        //ICONST_5 = (byte) 8,
        LCONST_0 = (byte) 9,
        //LCONST_1 = (byte) 10,
        FCONST_0 = (byte) 11,
        FCONST_1 = (byte) 12,
        FCONST_2 = (byte) 13,
        DCONST_0 = (byte) 14,
        DCONST_1 = (byte) 15,
        BIPUSH = (byte) 16,
        SIPUSH = (byte) 17,
        LDC = (byte) 18,
        LDC_W = (byte) 19,
        LDC2_W = (byte) 20,
        ILOAD = (byte) 21,
        LLOAD = (byte) 22,
        FLOAD = (byte) 23,
        DLOAD = (byte) 24,
        ALOAD = (byte) 25,
        ILOAD_0 = (byte) 26,
        //ILOAD_1 = (byte) 27,
        //ILOAD_2 = (byte) 28,
        //ILOAD_3 = (byte) 29,
        LLOAD_0 = (byte) 30,
        //LLOAD_1 = (byte) 31,
        //LLOAD_2 = (byte) 32,
        //LLOAD_3 = (byte) 33,
        FLOAD_0 = (byte) 34,
        //FLOAD_1 = (byte) 35,
        //FLOAD_2 = (byte) 36,
        //FLOAD_3 = (byte) 37,
        DLOAD_0 = (byte) 38,
        //DLOAD_1 = (byte) 39,
        //DLOAD_2 = (byte) 40,
        //DLOAD_3 = (byte) 41,
        ALOAD_0 = (byte) 42,
        //ALOAD_1 = (byte) 43,
        //ALOAD_2 = (byte) 44,
        //ALOAD_3 = (byte) 45,
        IALOAD = (byte) 46,
        LALOAD = (byte) 47,
        FALOAD = (byte) 48,
        DALOAD = (byte) 49,
        AALOAD = (byte) 50,
        BALOAD = (byte) 51,
        CALOAD = (byte) 52,
        SALOAD = (byte) 53,
        ISTORE = (byte) 54,
        LSTORE = (byte) 55,
        FSTORE = (byte) 56,
        DSTORE = (byte) 57,
        ASTORE = (byte) 58,
        ISTORE_0 = (byte) 59,
        //ISTORE_1 = (byte) 60,
        //ISTORE_2 = (byte) 61,
        //ISTORE_3 = (byte) 62,
        LSTORE_0 = (byte) 63,
        //LSTORE_1 = (byte) 64,
        //LSTORE_2 = (byte) 65,
        //LSTORE_3 = (byte) 66,
        FSTORE_0 = (byte) 67,
        //FSTORE_1 = (byte) 68,
        //FSTORE_2 = (byte) 69,
        //FSTORE_3 = (byte) 70,
        DSTORE_0 = (byte) 71,
        //DSTORE_1 = (byte) 72,
        //DSTORE_2 = (byte) 73,
        //DSTORE_3 = (byte) 74,
        ASTORE_0 = (byte) 75,
        //ASTORE_1 = (byte) 76,
        //ASTORE_2 = (byte) 77,
        //ASTORE_3 = (byte) 78,
        IASTORE = (byte) 79,
        //LASTORE = (byte) 80,
        //FASTORE = (byte) 81,
        //DASTORE = (byte) 82,
        //AASTORE = (byte) 83,
        //BASTORE = (byte) 84,
        //CASTORE = (byte) 85,
        //SASTORE = (byte) 86,
        POP = (byte) 87,
        POP2 = (byte) 88,
        DUP = (byte) 89,
        //DUP_X1 = (byte) 90,
        //DUP_X2 = (byte) 91,
        //DUP2 = (byte) 92,
        //DUP2_X1 = (byte) 93,
        //DUP2_X2 = (byte) 94,
        SWAP = (byte) 95,
        IADD = (byte) 96,
        //LADD = (byte) 97,
        //FADD = (byte) 98,
        //DADD = (byte) 99,
        ISUB = (byte) 100,
        //LSUB = (byte) 101,
        //FSUB = (byte) 102,
        //DSUB = (byte) 103,
        IMUL = (byte) 104,
        //LMUL = (byte) 105,
        //FMUL = (byte) 106,
        //DMUL = (byte) 107,
        IDIV = (byte) 108,
        //LDIV = (byte) 109,
        //FDIV = (byte) 110,
        //DDIV = (byte) 111,
        IREM = (byte) 112,
        //LREM = (byte) 113,
        //FREM = (byte) 114,
        //DREM = (byte) 115,
        INEG = (byte) 116,
        //LNEG = (byte) 117,
        //FNEG = (byte) 118,
        //DNEG = (byte) 119,
        ISHL = (byte) 120,
        //LSHL = (byte) 121,
        ISHR = (byte) 122,
        //LSHR = (byte) 123,
        IUSHR = (byte) 124,
        //LUSHR = (byte) 125,
        IAND = (byte) 126,
        //LAND = (byte) 127,
        IOR = (byte) 128,
        //LOR = (byte) 129,
        IXOR = (byte) 130,
        //LXOR = (byte) 131,
        IINC = (byte) 132,
        I2L = (byte) 133,
        I2F = (byte) 134,
        I2D = (byte) 135,
        L2I = (byte) 136,
        L2F = (byte) 137,
        L2D = (byte) 138,
        F2I = (byte) 139,
        F2L = (byte) 140,
        F2D = (byte) 141,
        D2I = (byte) 142,
        D2L = (byte) 143,
        D2F = (byte) 144,
        I2B = (byte) 145,
        I2C = (byte) 146,
        I2S = (byte) 147,
        LCMP = (byte) 148,
        FCMPL = (byte) 149,
        FCMPG = (byte) 150,
        DCMPL = (byte) 151,
        DCMPG = (byte) 152,
        IFEQ = (byte) 153,
        IFNE = (byte) 154,
        IFLT = (byte) 155,
        IFGE = (byte) 156,
        IFGT = (byte) 157,
        IFLE = (byte) 158,
        IF_ICMPEQ = (byte) 159,
        IF_ICMPNE = (byte) 160,
        IF_ICMPLT = (byte) 161,
        IF_ICMPGE = (byte) 162,
        IF_ICMPGT = (byte) 163,
        IF_ICMPLE = (byte) 164,
        IF_ACMPEQ = (byte) 165,
        //IF_ACMPNE = (byte) 166,
        GOTO = (byte) 167,
        TABLESWITCH = (byte) 170,
        LOOKUPSWITCH = (byte) 171,
        IRETURN = (byte) 172,
        LRETURN = (byte) 173,
        FRETURN = (byte) 174,
        DRETURN = (byte) 175,
        ARETURN = (byte) 176,
        RETURN = (byte) 177,
        GETSTATIC = (byte) 178,
        PUTSTATIC = (byte) 179,
        GETFIELD = (byte) 180,
        PUTFIELD = (byte) 181,
        INVOKEVIRTUAL = (byte) 182,
        INVOKESPECIAL = (byte) 183,
        INVOKESTATIC = (byte) 184,
        INVOKEINTERFACE = (byte) 185,
        INVOKEDYNAMIC = (byte) 186,
        NEW = (byte) 187,
        NEWARRAY = (byte) 188,
        ANEWARRAY = (byte) 189,
        ARRAYLENGTH = (byte) 190,
        ATHROW = (byte) 191,
        CHECKCAST = (byte) 192,
        INSTANCEOF = (byte) 193,
        MONITORENTER = (byte) 194,
        MONITOREXIT = (byte) 195,
        WIDE = (byte) 196,
        MULTIANEWARRAY = (byte) 197,
        IFNULL = (byte) 198,
        IFNONNULL = (byte) 199,
        GOTO_W = (byte) 200;
}
