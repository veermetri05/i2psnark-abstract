package org.klomp.snark.util;

import java.io.Serializable;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *  Count things. Port of {@code net.i2p.util.ObjectCounter} (GPLv2+, I2P).
 *
 *  @author zzz, welterde
 */
public class ObjectCounter<K> implements Serializable {
    /**
     * Serializable so it can be passed in an Android Bundle
     */
    private static final long serialVersionUID = 3160378641721937421L;

    private final ConcurrentHashMap<K, AtomicInteger> map;

    public ObjectCounter() {
        this.map = new ConcurrentHashMap<K, AtomicInteger>();
    }

    /**
     *  Add one.
     *  @return count after increment
     */
    public int increment(K h) {
        AtomicInteger i = this.map.putIfAbsent(h, new AtomicInteger(1));
        if (i != null)
            return i.incrementAndGet();
        return 1;
    }

    /**
     *  Set a high value
     */
    public void max(K h) {
        map.put(h, new AtomicInteger(Integer.MAX_VALUE / 2));
    }

    /**
     *  @return current count
     */
    public int count(K h) {
        AtomicInteger i = this.map.get(h);
        if (i != null)
            return i.get();
        return 0;
    }

    /**
     *  @return set of objects with counts &gt; 0
     */
    public Set<K> objects() {
        return this.map.keySet();
    }

    /**
     *  Start over. Reset the count for all keys to zero.
     */
    public void clear() {
        this.map.clear();
    }

    /**
     *  Reset the count for this key to zero
     */
    public void clear(K h) {
        this.map.remove(h);
    }
}
