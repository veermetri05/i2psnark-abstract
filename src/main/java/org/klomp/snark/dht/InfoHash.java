package org.klomp.snark.dht;
/*
 *  From zzzot, modded and relicensed to GPLv2
 */

import org.klomp.snark.data.SHA1Hash;
import org.klomp.snark.data.DataHelper;

/**
 *  A 20-byte SHA1 info hash
 *
 * @since 0.9.2
 * @author zzz
 */
class InfoHash extends SHA1Hash {

    public InfoHash(byte[] data) {
        super(data);
    }

    @Override
    public String toString() {
        if (_data == null) {
            return super.toString();
        } else {
            return "[InfoHash: " + DataHelper.toString(_data) + ']';
        }
    }
}
