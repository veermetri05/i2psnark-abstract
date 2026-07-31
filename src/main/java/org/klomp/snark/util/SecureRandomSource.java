package org.klomp.snark.util;

import java.security.SecureRandom;

import org.klomp.snark.spi.RandomSource;

/**
 *  Default {@link RandomSource} backed by {@link SecureRandom}.
 */
public class SecureRandomSource extends RandomSource {

    private static final long serialVersionUID = 1L;

    private final SecureRandom _random = new SecureRandom();

    @Override
    public void nextBytes(byte[] buf, int off, int len) {
        byte[] tmp = new byte[len];
        _random.nextBytes(tmp);
        System.arraycopy(tmp, 0, buf, off, len);
    }

    @Override
    protected int next(int bits) {
        return _random.nextInt() >>> (32 - bits);
    }
}
