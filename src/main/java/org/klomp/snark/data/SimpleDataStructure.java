package org.klomp.snark.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 *  A SimpleDataStructure contains only a single fixed-length byte array.
 *  Port of {@code net.i2p.data.SimpleDataStructure} (public domain, I2P).
 *
 *  Once non-null data is set, the data reference is immutable;
 *  subsequent attempts to set data throw a RuntimeException.
 */
public abstract class SimpleDataStructure {

    protected byte[] _data;

    /** A new instance with the data set to null */
    public SimpleDataStructure() {
    }

    /** @throws IllegalArgumentException if data is not the legal number of bytes (but null is ok) */
    public SimpleDataStructure(byte[] data) {
        setData(data);
    }

    /** The legal length of the byte array in this data structure */
    public abstract int length();

    /** Get the data reference (not a copy) @return the byte array, or null if unset */
    public byte[] getData() {
        return _data;
    }

    /** @return same thing as getData() */
    public byte[] toByteArray() {
        return _data;
    }

    /**
     * Sets the data.
     * @param data of correct length, or null
     * @throws IllegalArgumentException if data is not the legal number of bytes (but null is ok)
     * @throws RuntimeException if data already set
     */
    public void setData(byte[] data) {
        if (_data != null)
            throw new RuntimeException("Data already set");
        if (data != null && data.length != length())
            throw new IllegalArgumentException("Bad data length: " + data.length + "; required: " + length());
        _data = data;
    }

    /**
     * Sets the data.
     * @throws RuntimeException if data already set
     */
    public void readBytes(InputStream in) throws DataFormatException, IOException {
        if (_data != null)
            throw new RuntimeException("Data already set");
        int length = length();
        _data = new byte[length];
        // Throws on incomplete read
        int rv = 0;
        while (rv < length) {
            int read = in.read(_data, rv, length - rv);
            if (read < 0)
                throw new DataFormatException("Incomplete read");
            rv += read;
        }
    }

    public void writeBytes(OutputStream out) throws DataFormatException, IOException {
        if (_data == null)
            throw new DataFormatException("No data to write out");
        out.write(_data);
    }

    public String toBase64() {
        if (_data == null)
            return null;
        return Base64.encode(_data);
    }

    /**
     * Sets the data.
     * @throws DataFormatException if decoded data is not the legal number of bytes or on decoding error
     * @throws RuntimeException if data already set
     */
    public void fromBase64(String data) throws DataFormatException {
        if (data == null)
            throw new DataFormatException("Null data passed in");
        byte[] d = Base64.decode(data);
        if (d == null)
            throw new DataFormatException("Bad Base64 encoded data");
        if (d.length != length())
            throw new DataFormatException("Bad decoded data length, expected " + length() + " got " + d.length);
        setData(d);
    }

    /**
     * Does the same thing as setData() but null not allowed.
     * @throws DataFormatException if null or wrong length
     * @throws RuntimeException if data already set
     */
    public void fromByteArray(byte[] data) throws DataFormatException {
        if (data == null)
            throw new DataFormatException("Null data passed in");
        if (data.length != length())
            throw new DataFormatException("Bad data length: " + data.length + "; required: " + length());
        setData(data);
    }

    /** @return the SHA-256 hash of the data, or null if the data is null */
    public Hash calculateHash() {
        if (_data != null)
            return Hash.create(SHA256.hash(_data));
        return null;
    }

    @Override
    public boolean equals(Object object) {
        if (object == this)
            return true;
        if ((object == null) || !(object instanceof SimpleDataStructure))
            return false;
        return Arrays.equals(_data, ((SimpleDataStructure) object)._data);
    }

    /**
     * We assume the data has enough randomness in it, so use the first 4 bytes for speed.
     * If this is not the case, override in the extending class.
     */
    @Override
    public int hashCode() {
        if (_data == null)
            return 0;
        // use first 4 bytes
        return (_data[0] & 0xff) | ((_data[1] & 0xff) << 8) | ((_data[2] & 0xff) << 16) | ((_data[3] & 0xff) << 24);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(64);
        buf.append('[').append(getClass().getSimpleName()).append(": ");
        int length = length();
        if (_data == null) {
            buf.append("null");
        } else if (length <= 32) {
            buf.append(toBase64());
        } else {
            buf.append("size: ").append(Integer.toString(length));
        }
        buf.append(']');
        return buf.toString();
    }
}
