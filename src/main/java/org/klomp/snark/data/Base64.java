package org.klomp.snark.data;

/**
 *  Base64 encode/decode (standard alphabet, with padding) — port of
 *  {@code net.i2p.data.Base64} (public domain, I2P).
 */
public final class Base64 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private Base64() {}

    /** @return encoded string, or "" if source is null */
    public static String encode(byte[] source) {
        return source != null ? encode(source, 0, source.length) : "";
    }

    /**
     *  Encode source[off..off+len)
     *  Output will be a multiple of 4 chars, including 0-2 trailing '='
     */
    public static String encode(byte[] source, int off, int len) {
        if (source == null)
            return "";
        StringBuilder out = new StringBuilder(((len + 2) / 3) * 4);
        int end = off + len;
        int i = off;
        while (i < end) {
            int rem = end - i;
            int b0 = source[i++] & 0xff;
            int b1 = i < end ? source[i++] & 0xff : 0;
            int b2 = i < end ? source[i++] & 0xff : 0;
            out.append(ALPHABET.charAt(b0 >>> 2));
            out.append(ALPHABET.charAt(((b0 & 0x03) << 4) | (b1 >>> 4)));
            out.append(rem >= 2 ? ALPHABET.charAt(((b1 & 0x0f) << 2) | (b2 >>> 6)) : '=');
            out.append(rem >= 3 ? ALPHABET.charAt(b2 & 0x3f) : '=');
        }
        return out.toString();
    }

    /** @return decoded bytes, or null on decode error */
    public static byte[] decode(String source) {
        if (source == null)
            return null;
        int len = source.length();
        if (len % 4 != 0)
            return null;
        // count padding
        int pad = 0;
        if (len > 0 && source.charAt(len - 1) == '=') pad++;
        if (len > 1 && source.charAt(len - 2) == '=') pad++;
        if (pad > 2)
            return null;
        int outLen = (len / 4) * 3 - pad;
        byte[] out = new byte[outLen];
        int o = 0;
        for (int i = 0; i < len; i += 4) {
            int c0 = decodeChar(source.charAt(i));
            int c1 = decodeChar(source.charAt(i + 1));
            int c2 = i + 2 < len && source.charAt(i + 2) != '=' ? decodeChar(source.charAt(i + 2)) : 0;
            int c3 = i + 3 < len && source.charAt(i + 3) != '=' ? decodeChar(source.charAt(i + 3)) : 0;
            if (c0 < 0 || c1 < 0 || c2 < 0 || c3 < 0)
                return null;
            int triple = (c0 << 18) | (c1 << 12) | (c2 << 6) | c3;
            if (o < outLen) out[o++] = (byte) (triple >>> 16);
            if (o < outLen) out[o++] = (byte) (triple >>> 8);
            if (o < outLen) out[o++] = (byte) triple;
        }
        return out;
    }

    private static int decodeChar(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '+') return 62;
        if (c == '/') return 63;
        return -1;
    }
}
