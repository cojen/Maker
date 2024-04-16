/*
 *  Copyright 2020 Cojen.org
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

import java.io.*;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Extra tests for the BytesOut class.
 *
 * @author Brian S O'Neill
 */
public class BytesOutTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(BytesOutTest.class.getName());
    }

    @Test
    public void nothing() throws Exception {
        var out = new BytesOut(null, 100);
        out.flush();
        assertEquals(0, out.toByteArray().length);
    }

    @Test
    public void utf() throws Exception {
        var out = new BytesOut(null, 100);
        var str = "A\u0123\u1234";
        out.writeUTF(str);
        byte[] result = out.toByteArray();

        var bout = new ByteArrayOutputStream();
        var dout = new DataOutputStream(bout);
        dout.writeUTF(str);
        byte[] expect = bout.toByteArray();

        assertArrayEquals(expect, result);
    }

    @Test
    public void writeOverflow() throws Exception {
        var bout = new ByteArrayOutputStream();
        var out = new BytesOut(bout, 8);
        out.flush();

        byte[] b1 = "hello".getBytes("UTF-8");
        out.write(b1, 0, b1.length);

        byte[] b2 = "world".getBytes("UTF-8");
        out.write(b2, 0, b2.length);

        byte[] b3 = "hello, world!!!".getBytes("UTF-8");
        out.write(b3, 0, b3.length);

        out.flush();
        byte[] result = bout.toByteArray();
        byte[] expect = "helloworldhello, world!!!".getBytes("UTF-8");
        assertArrayEquals(expect, result);
    }

    @Test
    public void writeOverflow2() throws Exception {
        var bout = new ByteArrayOutputStream();
        var out = new BytesOut(bout, 15);

        out.writeLong(Long.MAX_VALUE);
        out.writeLong(Long.MIN_VALUE);

        out.flush();
        byte[] result = bout.toByteArray();
        byte[] expect = {127, -1, -1, -1, -1, -1, -1, -1, -128, 0, 0, 0, 0, 0, 0, 0};
        assertArrayEquals(expect, result);
    }

    @Test
    public void writeOverflow3() throws Exception {
        var bout = new ByteArrayOutputStream();
        var out = new BytesOut(bout, 15);

        out.writeInt(10_000);
        var str = "hello, world!!!";
        out.writeUTF(str);

        out.flush();
        byte[] result = bout.toByteArray();

        var bout2 = new ByteArrayOutputStream();
        var dout = new DataOutputStream(bout2);
        dout.writeInt(10_000);
        dout.writeUTF(str);
        byte[] expect = bout2.toByteArray();
        assertArrayEquals(expect, result);
    }

    @Test
    public void writeOverflow4() throws Exception {
        var bout = new ByteArrayOutputStream();
        var out = new BytesOut(bout, 8);

        var str = "hello\u1000";
        out.writeUTF(str);

        out.flush();
        byte[] result = bout.toByteArray();

        var bout2 = new ByteArrayOutputStream();
        var dout = new DataOutputStream(bout2);
        dout.writeUTF(str);
        byte[] expect = bout2.toByteArray();
        assertArrayEquals(expect, result);
    }

    @Test
    public void writeExpand() throws Exception {
        var out = new BytesOut(null, 5);
        out.writeInt(1234567890);
        out.writeLong(1234567890123456789L);
        out.writeShort(12345);
        byte[] result = out.toByteArray();

        var bout = new ByteArrayOutputStream();
        var dout = new DataOutputStream(bout);
        dout.writeInt(1234567890);
        dout.writeLong(1234567890123456789L);
        dout.writeShort(12345);
        byte[] expect = bout.toByteArray();

        assertArrayEquals(expect, result);
    }

    @Test
    public void checkUTF() throws Exception {
        assertEquals(0, BytesOut.checkUTF(UsageTest.makeString(65535, 'a')));
        assertEquals(0, BytesOut.checkUTF(UsageTest.makeString(32767, '\u0100') + 'a'));
        assertEquals(0, BytesOut.checkUTF(UsageTest.makeString(21844, '\u1000') + "abc"));

        assertEquals(65536, BytesOut.checkUTF(UsageTest.makeString(65536, 'a')));
        assertEquals(65536, BytesOut.checkUTF(UsageTest.makeString(32767, '\u0100') + "ab"));
        assertEquals(65536, BytesOut.checkUTF(UsageTest.makeString(21845, '\u1000') + 'a'));
    }
}
