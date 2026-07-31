package org.klomp.snark.util;

import org.klomp.snark.spi.Storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Storage implementation on the app's private files directory —
 * used for the DHT routing table persistence file.
 */
public class FileStorage implements Storage {

    private final File _dir;

    public FileStorage(File dir) {
        if (dir == null)
            throw new IllegalArgumentException("dir must not be null");
        _dir = dir;
    }

    @Override
    public String getConfigDir() {
        return _dir.getAbsolutePath();
    }

    private File file(String name) {
        return new File(_dir, name);
    }

    @Override
    public boolean exists(String name) {
        return file(name).exists();
    }

    @Override
    public InputStream open(String name) throws IOException {
        return new FileInputStream(file(name));
    }

    @Override
    public OutputStream create(String name) throws IOException {
        File f = file(name);
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IOException("Cannot create directory " + parent);
        return new FileOutputStream(f);
    }

    @Override
    public boolean delete(String name) {
        return file(name).delete();
    }
}
