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

    /**
     *  Decode lowercase base32 (no trailing '=' allowed) — port of
     *  {@code net.i2p.data.Base32#decode(String)} (public domain, I2P).
     *
     *  @param source non-null, lowercase, unpadded
     *  @return the decoded bytes, or null on invalid input
     */
    public static byte[] decode(String source) {
        if (source.length() <= 1)
            return new byte[source.length()];
        int len58 = source.length() * 5 / 8;
        byte[] outBuff = new byte[len58];
        int outBuffPosn = 0;

        int usedbits = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            int fivebits;
            if (c < '2' || c > 'z')
                fivebits = -1;
            else if (c <= '7')
                fivebits = c - '2' + 26;
            else if (c <= 'Z')
                fivebits = c - 'A';
            else if (c <= 'z')
                fivebits = c - 'a';
            else
                fivebits = -1;

            if (fivebits >= 0) {
                if (outBuffPosn >= len58)
                    return null;
                if (usedbits == 0) {
                    outBuff[outBuffPosn] = (byte) ((fivebits << 3) & 0xf8);
                    usedbits = 5;
                } else if (usedbits < 3) {
                    outBuff[outBuffPosn] |= (byte) ((fivebits << (3 - usedbits)) & DMASK[usedbits]);
                    usedbits += 5;
                } else if (usedbits == 3) {
                    outBuff[outBuffPosn++] |= (byte) fivebits;
                    usedbits = 0;
                } else {
                    outBuff[outBuffPosn++] |= (byte) ((fivebits >> (usedbits - 3)) & DMASK[usedbits]);
                    byte next = (byte) (fivebits << (11 - usedbits));
                    if (outBuffPosn < len58) {
                        outBuff[outBuffPosn] = next;
                        usedbits -= 3;
                    } else if (next != 0) {
                        // extra data at the end
                        return null;
                    }
                }
            } else {
                return null;
            }
        }
        return outBuff;
    }

    /** masks for the used-bits cases, indexed by usedbits (0-7) — as in I2P */
    private static final byte[] DMASK = {(byte) 0xf8, (byte) 0x7c, (byte) 0x3e,
                                          (byte) 0x1f, (byte) 0x0f, (byte) 0x07,
                                          (byte) 0x03, (byte) 0x01};
}
