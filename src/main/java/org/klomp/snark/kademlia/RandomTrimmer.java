package org.klomp.snark.kademlia;

import java.util.ArrayList;
import java.util.List;

import org.klomp.snark.spi.Environment;
import org.klomp.snark.data.SimpleDataStructure;

/**
 *  Removes a random element. Not resistant to flooding.
 *  @since 0.9.2 in i2psnark, moved to core in 0.9.10
 */
public class RandomTrimmer<T extends SimpleDataStructure> implements KBucketTrimmer<T> {
    protected final Environment _ctx;
    private final int _max;

    public RandomTrimmer(Environment ctx, int max) {
        _ctx = ctx;
        _max = max;
    }

    public boolean trim(KBucket<T> kbucket, T toAdd) {
        List<T> e = new ArrayList<T>(kbucket.getEntries());
        int sz = e.size();
        // concurrency
        if (sz < _max)
            return true;
        T toRemove = e.get(_ctx.random().nextInt(sz));
        kbucket.remove(toRemove);
        return true;
    }
}
