/*
 Licensed to Diennea S.r.l. under one
 or more contributor license agreements. See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership. Diennea S.r.l. licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.

 */
package herddb.utils;

import java.io.InputStream;

/**
 * A very simple InputStream which wraps a byte buffer
 *
 * @author enrico.olivelli
 */
public final class SimpleByteArrayInputStream extends InputStream {

    private final byte[] buf;
    private final int size;
    private int pos;

    public SimpleByteArrayInputStream(byte buf[]) {
        this.buf = buf;
        this.pos = 0;
        this.size = buf.length;
    }

    public SimpleByteArrayInputStream(byte buf[], int offset, int length) {
        this.buf = buf;
        this.pos = offset;
        this.size = length;
    }

    @Override
    public int read() {
        return (pos < size) ? (buf[pos++] & 0xff) : -1;
    }

    @Override
    public int read(byte b[], int off, int len) {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }

        if (pos >= size) {
            return -1;
        }

        int avail = size - pos;
        if (len > avail) {
            len = avail;
        }
        if (len <= 0) {
            return 0;
        }
        System.arraycopy(buf, pos, b, off, len);
        pos += len;
        return len;
    }

    @Override
    public int available() {
        return size - pos;
    }

}
