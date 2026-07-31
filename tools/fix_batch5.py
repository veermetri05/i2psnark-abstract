import io

BASE = "C:/Users/VeerMetri/Documents/projects/i2psnark-abstract/src/main/java/org/klomp/snark/"

def load(name):
    return io.open(BASE + name, encoding="utf-8").read()

def save(name, s):
    io.open(BASE + name, "w", encoding="utf-8", newline="\n").write(s)

# 1. ExtensionHandler import
p = "ExtensionHandler.java"
s = load(p)
s = s.replace("import java.io.InputStream;", "import java.io.ByteArrayInputStream;\nimport java.io.InputStream;")
s = s.replace("I2PSnarkUtil.MAX_CONNECTIONS", "ClientContext.MAX_CONNECTIONS")
save(p, s)

# 2. DataHelper: fromLong8 / toLong8 / read
p = "data/DataHelper.java"
s = load(p)
s = s.rstrip()[:-1]
s += """
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
"""
save(p, s)
print("done")
