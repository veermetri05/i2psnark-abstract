package org.klomp.snark.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 *  A thread-safe LRU cache with a maximum size — port of
 *  {@code net.i2p.util.LHMCache} (public domain, I2P).
 */
public class LHMCache<K, V> extends LinkedHashMap<K, V> {

    private static final long serialVersionUID = 1L;

    private final int _max;

    public LHMCache(int max) {
        super(64, 0.75f, true);
        _max = max;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > _max;
    }

    @Override
    public synchronized V get(Object key) {
        return super.get(key);
    }

    @Override
    public synchronized V put(K key, V value) {
        return super.put(key, value);
    }

    @Override
    public synchronized V remove(Object key) {
        return super.remove(key);
    }

    @Override
    public synchronized void clear() {
        super.clear();
    }

    @Override
    public synchronized int size() {
        return super.size();
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        return super.containsKey(key);
    }
}
