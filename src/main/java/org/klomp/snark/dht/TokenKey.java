package org.klomp.snark.dht;
/*
 *  GPLv2
 */

import org.klomp.snark.data.SHA1Hash;
import org.klomp.snark.data.DataHelper;

/**
 *  Used to index incoming Tokens
 *
 * @since 0.9.2
 * @author zzz
 */
class TokenKey extends SHA1Hash {

    public TokenKey(NID nID, InfoHash ih) {
        super(DataHelper.xor(nID.getData(), ih.getData()));
    }
}
