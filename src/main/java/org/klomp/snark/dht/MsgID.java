package org.klomp.snark.dht;
/*
 *  GPLv2
 */

import org.klomp.snark.spi.Environment;
import org.klomp.snark.data.ByteArray;

/**
 *  Used for both incoming and outgoing message IDs
 *
 * @since 0.9.2
 * @author zzz
 */
class MsgID extends ByteArray {

    /** BEP 5: 2 bytes, incremented */
    private static final int MY_TOK_LEN = 8;
    private static final int MAX_TOK_LEN = 16;

    /** outgoing - generate a random ID */
    public MsgID(Environment ctx) {
        super(null);
        byte[] data = new byte[MY_TOK_LEN];
        ctx.random().nextBytes(data);
        // assign directly: setData() validates against length(), which is
        // 0 while _data is null (ByteArray semantics) — same lesson as
        // the ByteArray(byte[]) ctor fix (session 1)
        _data = data;
        setValid(MY_TOK_LEN);
    }

    /** incoming  - save the ID (arbitrary length) */
    public MsgID(byte[] data) {
        super(data);
        // lets not get carried away
        if (data.length > MAX_TOK_LEN)
            throw new IllegalArgumentException();
    }
}
