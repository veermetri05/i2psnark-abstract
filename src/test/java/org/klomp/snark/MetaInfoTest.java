package org.klomp.snark;

import org.junit.Test;
import static org.junit.Assert.*;

import org.klomp.snark.bencode.BDecoder;
import org.klomp.snark.bencode.BEncoder;
import org.klomp.snark.bencode.BEValue;
import org.klomp.snark.data.DataHelper;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 *  MetaInfo parsing tests (the torrent dict used by the loopback test).
 */
public class MetaInfoTest {

    /** Build a minimal single-file torrent info dict. */
    public static byte[] buildInfoDict(String name, long length, int pieceLength) throws Exception {
        byte[] pieceHash = new byte[20];
        for (int i = 0; i < 20; i++)
            pieceHash[i] = (byte) i;

        Map<String, Object> info = new HashMap<String, Object>();
        info.put("name", name);
        info.put("piece length", Integer.valueOf(pieceLength));
        info.put("length", Long.valueOf(length));
        info.put("pieces", pieceHash);
        return BEncoder.bencode(info);
    }

    /** SHA-1 of the info dict = the torrent infohash. */
    public static byte[] infoHash(byte[] infoDict) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return md.digest(infoDict);
    }

    @Test
    public void testParseInfoDict() throws Exception {
        byte[] info = buildInfoDict("test.bin", 12345, 16384);
        Map<String, BEValue> top = new HashMap<String, BEValue>();
        top.put("info", new BDecoder(new java.io.ByteArrayInputStream(info)).bdecodeMap());

        MetaInfo meta = new MetaInfo(top);
        assertEquals("test.bin", meta.getName());
        assertEquals(12345L, meta.getTotalLength());
        assertArrayEquals(infoHash(info), meta.getInfoHash());
        assertNotNull(meta.getInfoBytes());
        assertEquals(info.length, meta.getInfoBytesLength());
    }

    @Test
    public void testHexRoundTrip() {
        byte[] data = new byte[256];
        for (int i = 0; i < 256; i++)
            data[i] = (byte) i;
        String hex = DataHelper.toString(data);
        assertEquals(512, hex.length());
        assertArrayEquals(data, DataHelper.fromHex(hex));
    }
}
