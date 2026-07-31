package org.klomp.snark.data;

/**
 *  A generic byte array data structure — port of
 *  {@code net.i2p.data.ByteArray} (public domain, I2P).
 */
public class ByteArray extends SimpleDataStructure {

    /** valid data length; -1 if unset */
    private int _valid = -1;

    /**
     *  @param data may be null. Assigned directly: the base class
     *              {@code setData()} validation cannot run before
     *              {@link #length()} is meaningful.
     */
    public ByteArray(byte[] data) {
        if (data != null) {
            _data = data;
            _valid = data.length;
        }
    }

    @Override
    public int length() {
        return _data != null ? _data.length : 0;
    }

    /** @param len the number of bytes that are meaningful (may be &lt; data.length) */
    public void setValid(int len) {
        _valid = len;
    }

    /** @return the number of bytes that are meaningful, or -1 if unset */
    public int getValid() {
        return _valid;
    }

    @Override
    public boolean equals(Object object) {
        return super.equals(object);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
