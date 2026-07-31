package org.klomp.snark.data;

import java.nio.charset.StandardCharsets;

/**
 *  Byte/string helpers — port of the subset of
 *  {@code net.i2p.data.DataHelper} used by the abstract layer
 *  (public domain, I2P).
 */
public final class DataHelper {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private DataHelper() {}

    // ── Strings ───────────────────────────────────────────────────────

    /** @return the ASCII bytes of the string */
    public static byte[] getASCII(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    /** @return the UTF-8 bytes of the string */
    public static byte[] getUTF8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** @return the string decoded as UTF-8 */
    public static String getUTF8(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }

    /** @return the string decoded as UTF-8 from data[off..off+len) */
    public static String getUTF8(byte[] data, int off, int len) {
        return new String(data, off, len, StandardCharsets.UTF_8);
    }

    // ── Hex ───────────────────────────────────────────────────────────

    /** @return the lowercase hex representation of the data (I2P DataHelper alias) */
    public static String toHexString(byte[] data) {
        return toString(data);
    }

    /** @return the lowercase hex representation of the data */
    public static String toString(byte[] data) {
        if (data == null)
            return "";
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int b = data[i] & 0xff;
            out[i * 2] = HEX[b >>> 4];
            out[i * 2 + 1] = HEX[b & 0x0f];
        }
        return new String(out);
    }

    /** @return the hex representation of data[off..off+len) */
    public static String toString(byte[] data, int off, int len) {
        char[] out = new char[len * 2];
        for (int i = 0; i < len; i++) {
            int b = data[off + i] & 0xff;
            out[i * 2] = HEX[b >>> 4];
            out[i * 2 + 1] = HEX[b & 0x0f];
        }
        return new String(out);
    }

    /** @return the bytes parsed from a hex string, or null on odd length / bad chars */
    public static byte[] fromHex(String s) {
        if (s == null || (s.length() & 1) != 0)
            return null;
        byte[] rv = new byte[s.length() / 2];
        for (int i = 0; i < rv.length; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0)
                return null;
            rv[i] = (byte) ((hi << 4) | lo);
        }
        return rv;
    }

    // ── Comparison ────────────────────────────────────────────────────

    /** @return true if the arrays are equal (null-safe, null != non-null) */
    public static boolean eq(byte[] a, byte[] b) {
        return java.util.Arrays.equals(a, b);
    }

    /** @return true if a[ao..ao+len) == b[bo..bo+len) */
    public static boolean eq(byte[] a, int ao, byte[] b, int bo, int len) {
        if (a == null || b == null || a.length < ao + len || b.length < bo + len)
            return false;
        for (int i = 0; i < len; i++) {
            if (a[ao + i] != b[bo + i])
                return false;
        }
        return true;
    }

    /** Lexicographic unsigned comparison — null sorts before non-null */
    public static int compareTo(byte[] a, byte[] b) {
        if (a == b)
            return 0;
        if (a == null)
            return -1;
        if (b == null)
            return 1;
        int min = Math.min(a.length, b.length);
        for (int i = 0; i < min; i++) {
            int x = a[i] & 0xff;
            int y = b[i] & 0xff;
            if (x != y)
                return x - y;
        }
        return a.length - b.length;
    }

    // ── Byte ops ──────────────────────────────────────────────────────

    /** @return a XOR b (must be same length) */
    public static byte[] xor(byte[] a, byte[] b) {
        byte[] rv = new byte[a.length];
        for (int i = 0; i < a.length; i++)
            rv[i] = (byte) (a[i] ^ b[i]);
        return rv;
    }

    /** @return a long parsed from len (1-8) bytes at off, big-endian */
    public static long fromLong(byte[] data, int off, int len) {
        if (data == null || len <= 0 || len > 8 || off + len > data.length)
            throw new IllegalArgumentException("Bad data");
        long rv = 0;
        for (int i = 0; i < len; i++)
            rv = (rv << 8) | (data[off + i] & 0xff);
        return rv;
    }

    /** Write the long into len (1-8) bytes at off, big-endian */
    public static void toLong(byte[] data, int off, int len, long value) {
        if (data == null || len <= 0 || len > 8 || off + len > data.length)
            throw new IllegalArgumentException("Bad data");
        for (int i = len - 1; i >= 0; i--) {
            data[off + i] = (byte) value;
            value >>>= 8;
        }
    }

    /**
     *  Split a string into at most max parts.
     *  Port of I2P's DataHelper.split().
     */
    public static String[] split(String s, String delimiter, int max) {
        if (s == null)
            return new String[0];
        java.util.List<String> parts = new java.util.ArrayList<String>(max);
        int start = 0;
        int idx;
        while (parts.size() < max - 1 && (idx = s.indexOf(delimiter, start)) >= 0) {
            parts.add(s.substring(start, idx));
            start = idx + delimiter.length();
        }
        parts.add(s.substring(start));
        return parts.toArray(new String[0]);
    }

    /**
     *  @return the collection's elements joined by ", " (via toString)
     *          — port of I2P's DataHelper.toString(Collection)
     */
    public static String toString(java.util.Collection<?> c) {
        if (c == null)
            return "null";
        if (c.isEmpty())
            return "[]";
        StringBuilder buf = new StringBuilder(64);
        buf.append('[');
        boolean first = true;
        for (Object o : c) {
            if (first) {
                first = false;
            } else {
                buf.append(", ");
            }
            buf.append(o);
        }
        buf.append(']');
        return buf.toString();
    }

    // ── Formatting ────────────────────────────────────────────────────

    /** Format milliseconds as a human duration, e.g. "3h 15m" */
    public static String formatDuration(long ms) {
        StringBuilder buf = new StringBuilder(32);
        if (ms < 0) {
            buf.append('-');
            ms = -ms;
        }
        long hours = ms / (60 * 60 * 1000);
        long minutes = (ms / (60 * 1000)) % 60;
        long seconds = (ms / 1000) % 60;
        if (hours > 0) {
            buf.append(hours).append('h');
            if (minutes > 0)
                buf.append(' ').append(minutes).append('m');
        } else if (minutes > 0) {
            buf.append(minutes).append('m');
            if (seconds > 0)
                buf.append(' ').append(seconds).append('s');
        } else {
            buf.append(seconds).append('s');
        }
        return buf.toString();
    }

    /** Format a byte count as e.g. "1.5K" (base 1024) */
    public static String formatSize2(long size) {
        if (size < 1024)
            return Long.toString(size);
        String[] units = {"K", "M", "G", "T", "P"};
        double d = size;
        int i = -1;
        while (d >= 1024 && i < units.length - 1) {
            d /= 1024;
            i++;
        }
        return String.format(java.util.Locale.US, "%.1f%s", d, units[i]);
    }

    /** Format a byte count as e.g. "123.4 KB" (base 1024) — the I2P "formatSize" form */
    public static String formatSize(long size) {
        if (size < 1024)
            return Long.toString(size) + " B";
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        double d = size;
        int i = -1;
        while (d >= 1024 && i < units.length - 1) {
            d /= 1024;
            i++;
        }
        return String.format(java.util.Locale.US, "%.1f %s", d, units[i]);
    }

    /**
     *  Deterministic byte-array hash, as in I2P's DataHelper —
     *  {@code rv = 31*rv + b} over the array.
     */
    public static int hashCode(byte[] data) {
        return hashCode(data, 0, data.length);
    }

    /** Deterministic byte-array hash over a sub-range. */
    public static int hashCode(byte[] data, int off, int len) {
        int rv = 0;
        for (int i = 0; i < len; i++)
            rv = 31 * rv + data[off + i];
        return rv;
    }

    /** Format a byte count as e.g. "1.5K" (base 1000) */
    public static String formatSize2Decimal(long size) {
        if (size < 1000)
            return Long.toString(size);
        String[] units = {"K", "M", "G", "T", "P"};
        double d = size;
        int i = -1;
        while (d >= 1000 && i < units.length - 1) {
            d /= 1000;
            i++;
        }
        return String.format(java.util.Locale.US, "%.1f%s", d, units[i]);
    }

  /**
   *  Skip n bytes on a stream, reading byte-by-byte if necessary —
   *  port of {@code net.i2p.data.DataHelper.skip} (public domain, I2P).
   *  @throws java.io.IOException
   */
  public static void skip(java.io.InputStream in, long n) throws java.io.IOException {
      if (n < 0)
          throw new IllegalArgumentException();
      if (n == 0)
          return;
      long read = 0;
      long nm1 = n - 1;
      if (nm1 > 0) {
          // skip all but the last byte
          read = in.skip(nm1);
          // if the stream didn't skip, read the bytes
          if (read < nm1) {
              byte[] buf = new byte[(int) Math.min(4096, n - read)];
              while (read < nm1) {
                  int sz = (int) Math.min(buf.length, nm1 - read);
                  int r = in.read(buf, 0, sz);
                  if (r < 0)
                      break;
                  read += r;
              }
          }
      }
      // skip/read the last byte
      if (read < n) {
          if (in.read() < 0)
              throw new java.io.EOFException("EOF while skipping");
      }
  }

  /** 8-byte big-endian long from an array — port of I2P's fromLong8 */
  public static long fromLong8(byte src[], int offset) {
      long rv = 0;
      int limit = offset + 8;
      for (int i = offset; i < limit; i++) {
          rv <<= 8;
          rv |= src[i] & 0xFF;
      }
      return rv;
  }

  /** 8-byte big-endian long into an array — port of I2P's toLong8 */
  public static void toLong8(byte target[], int offset, long value) {
      for (int i = offset + 7; i >= offset; i--) {
          target[i] = (byte) value;
          value >>= 8;
      }
  }

  /** Read fully, throwing EOF on short read — port of I2P's read(InputStream, byte[]) */
  public static int read(java.io.InputStream in, byte target[]) throws java.io.IOException {
      return read(in, target, 0, target.length);
  }

  /**
   *  WARNING - different than InputStream.read(target, offset, length)
   *  for a nonzero offset: reads exactly length bytes or throws EOF.
   *  Port of I2P's read(InputStream, byte[], int, int).
   */
  public static int read(java.io.InputStream in, byte target[], int offset, int length) throws java.io.IOException {
      int cur = 0;
      while (cur < length) {
          int numRead = in.read(target, offset + cur, length - cur);
          if (numRead == -1)
              throw new java.io.EOFException("EOF after reading " + cur + " bytes of " + length + " byte value");
          cur += numRead;
      }
      return cur;
  }
}
