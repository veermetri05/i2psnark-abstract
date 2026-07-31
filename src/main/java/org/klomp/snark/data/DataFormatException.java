package org.klomp.snark.data;

/**
 *  Thrown on data format errors — port of
 *  {@code net.i2p.data.DataFormatException}.
 *
 *  Public domain (I2P).
 */
public class DataFormatException extends Exception {

    private static final long serialVersionUID = 1L;

    public DataFormatException() {
        super();
    }

    public DataFormatException(String message) {
        super(message);
    }

    public DataFormatException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataFormatException(Throwable cause) {
        super(cause);
    }
}
