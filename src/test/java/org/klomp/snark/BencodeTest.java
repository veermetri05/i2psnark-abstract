package org.klomp.snark;

import org.junit.Test;
import static org.junit.Assert.*;

import org.klomp.snark.bencode.BDecoder;
import org.klomp.snark.bencode.BEncoder;
import org.klomp.snark.bencode.BEValue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 *  Round-trip tests for the bencode codec.
 */
public class BencodeTest {

    @Test
    public void testRoundTrip() throws Exception {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("msg_type", Integer.valueOf(0));
        m.put("piece", Integer.valueOf(3));
        m.put("y", "q");
        m.put("t", new byte[] {1, 2, 3, 4});
        m.put("nested", new HashMap<String, Object>());

        byte[] enc = BEncoder.bencode(m);
        BEValue dec = new BDecoder(new ByteArrayInputStream(enc)).bdecodeMap();
        Map<String, BEValue> map = dec.getMap();
        assertEquals(0, map.get("msg_type").getInt());
        assertEquals(3, map.get("piece").getInt());
        assertEquals("q", map.get("y").getString());
        assertArrayEquals(new byte[] {1, 2, 3, 4}, map.get("t").getBytes());
        assertNotNull(map.get("nested").getMap());
    }

    @Test
    public void testByteStringWithPadding() throws Exception {
        // raw chunk data appended after a dict must survive
        byte[] raw = new byte[16384];
        for (int i = 0; i < raw.length; i++)
            raw[i] = (byte) (i * 31);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(BEncoder.bencode(new HashMap<String, Object>()));
        baos.write(raw);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BDecoder dec = new BDecoder(bais);
        BEValue bev = dec.bdecodeMap();
        assertNotNull(bev.getMap());
        // remaining bytes after the dict are the raw data
        assertEquals(raw.length, bais.available());
        byte[] rest = new byte[raw.length];
        bais.read(rest);
        assertArrayEquals(raw, rest);
    }

    @Test
    public void testEmptyDict() throws Exception {
        Map<String, Object> m = new HashMap<String, Object>();
        byte[] enc = BEncoder.bencode(m);
        assertArrayEquals("de".getBytes("US-ASCII"), enc);
        BEValue dec = new BDecoder(new ByteArrayInputStream(enc)).bdecodeMap();
        assertTrue(dec.getMap().isEmpty());
    }
}
