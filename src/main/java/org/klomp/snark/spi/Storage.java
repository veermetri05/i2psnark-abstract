package org.klomp.snark.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 *  Abstract file storage — replaces direct {@code java.io.File} access
 *  and I2P's {@code SecureFileOutputStream}.
 *
 *  Names are relative paths under the application's data/config
 *  directory (e.g. {@code "libretorrent.config.d/i2psnark.dht.dat"}).
 *  Implementations decide where files actually live (filesystem,
 *  app-private storage, ...) and must create missing parent
 *  directories on {@link #create(String)}.
 *
 *  @since 0.1.0
 */
public interface Storage {

    /**
     *  @return the config/data directory path (informational; may be
     *          an opaque label for non-filesystem implementations)
     */
    String getConfigDir();

    /** @return true if the named file exists */
    boolean exists(String name);

    /**
     *  Open an existing file for reading.
     *  @throws IOException if the file does not exist or cannot be read
     */
    InputStream open(String name) throws IOException;

    /**
     *  Create (or truncate) a file for writing.
     *  @throws IOException if the file cannot be created
     */
    OutputStream create(String name) throws IOException;

    /** Delete a file. @return true if it was deleted */
    boolean delete(String name);
}
