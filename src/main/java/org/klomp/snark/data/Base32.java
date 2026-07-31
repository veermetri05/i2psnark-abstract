package org.klomp.snark.data;

/**
 *  Base32 encode (RFC 4648, lowercase, no padding) — port of
 *  {@code net.i2p.data.Base32} (public domain, I2P). Used for
 *  .b32.i2p hostnames.
 */
public final class Base32 {

    private static final char[] ALPHABET = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h',
                                            'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p',
                                            'q', 'r', 's', 't', 'u', 'v', 'w', 'x',
                                            'y', 'z', '2', '3', '4', '5', '6', '7'};

    private static final byte[] EMASK = {(byte) 0x1f, (byte) 0x01, (byte) 0x03,
                                         (byte) 0x07, (byte) 0x0f};

    private Base32() {}

    /**
     *  Encode to lowercase base32, no trailing '='.
     *  @param source non-null
     */
    public static String encode(byte[] source) {
        StringBuilder buf = new StringBuilder((source.length + 7) * 8 / 5);
        encodeBytes(source, buf);
        return buf.toString();
    }

    private static void encodeBytes(byte[] source, StringBuilder out) {
        int usedbits = 0;
        for (int i = 0; i < source.length; ) {
            int fivebits;
            if (usedbits < 3) {
                fivebits = (source[i] >> (3 - usedbits)) & 0x1f;
                usedbits += 5;
            } else if (usedbits == 3) {
                fivebits = source[i++] & 0x1f;
                usedbits = 0;
            } else {
                fivebits = (source[i++] << (usedbits - 3)) & 0x1f;
                if (i < source.length) {
                    usedbits -= 3;
                    fivebits |= (source[i] >> (8 - usedbits)) & EMASK[usedbits];
                }
            }
            out.append(ALPHABET[fivebits]);
        }
    }
}
