package org.klomp.snark.util;

import java.io.File;

/**
 *  Restrictive file permissions — port of the upstream
 *  {@code net.i2p.util.SecureFile} / {@code SecureDirectory}
 *  {@code setPerms()} semantics that were lost when the abstract layer
 *  replaced SecureFile with plain {@link File}: torrent data files and
 *  partial-piece temp files get owner-only read/write (and owner-only
 *  execute for directories on non-Windows), so they are not
 *  world-readable on multi-user hosts.
 *
 *  No-op where the JVM or filesystem cannot set permission bits.
 *
 *  @since 0.1.0
 */
public final class FilePerms {

    private static final boolean isNotWindows = File.separatorChar != '\\';

    private FilePerms() {}

    /**
     *  Restrict to owner-only rw (plus owner-only x for directories on
     *  non-Windows). Never throws.
     *
     *  @param f the file or directory; must exist (no-op otherwise)
     */
    public static void setPerms(File f) {
        try {
            f.setReadable(false, false);
            f.setReadable(true, true);
            f.setWritable(false, false);
            f.setWritable(true, true);
            if (isNotWindows && f.isDirectory()) {
                f.setExecutable(false, false);
                f.setExecutable(true, true);
            }
        } catch (Throwable t) {
            // JVM or filesystem does not support permission bits
        }
    }
}
