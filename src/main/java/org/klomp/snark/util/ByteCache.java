package org.klomp.snark.util;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.klomp.snark.data.ByteArray;

/**
 *  Cache frequently used fixed-size byte buffers to reduce memory
 *  churn — a self-contained port of {@code net.i2p.util.ByteCache}
 *  (public domain / GPLv2+, I2P) without the TryCache/statistics/
 *  timer machinery the full client does not need.
 *
 *  The ByteArray should be held onto as long as the data referenced
 *  in it is needed.
 */
public final class ByteCache {

    private static final Map<Integer, ByteCache> _caches = new ConcurrentHashMap<Integer, ByteCache>();

    /**
     *  Get a cache responsible for objects of the given size.
     *
     *  @param cacheSize how many objects to hold before discarding
     *                   released objects
     *  @param size how large the cached objects are
     */
    public static ByteCache getInstance(int cacheSize, int size) {
        Integer sz = Integer.valueOf(size);
        ByteCache cache;
        synchronized (_caches) {
            cache = _caches.get(sz);
            if (cache == null) {
                cache = new ByteCache(cacheSize, size);
                _caches.put(sz, cache);
            }
        }
        return cache;
    }

    /** Clear everything (memory pressure) */
    public static void clearAll() {
        for (ByteCache bc : _caches.values())
            bc.clear();
    }

    private final int _entrySize;
    private final int _maxEntries;
    private final Deque<ByteArray> _free = new ArrayDeque<ByteArray>();

    private ByteCache(int maxCachedEntries, int entrySize) {
        _maxEntries = maxCachedEntries;
        _entrySize = entrySize;
    }

    /** Get a buffer from the cache, or allocate a fresh one. */
    public ByteArray acquire() {
        synchronized (_free) {
            ByteArray ba = _free.poll();
            if (ba != null)
                return ba;
        }
        byte[] data = new byte[_entrySize];
        ByteArray rv = new ByteArray(data);
        rv.setValid(0);
        return rv;
    }

    /** Put this structure back onto the available cache for reuse */
    public void release(ByteArray entry) {
        release(entry, true);
    }

    public void release(ByteArray entry, boolean shouldZero) {
        if (entry == null || entry.getData() == null)
            return;
        if (entry.getData().length != _entrySize)
            return;
        entry.setValid(0);
        if (shouldZero)
            Arrays.fill(entry.getData(), (byte) 0x0);
        synchronized (_free) {
            if (_free.size() < _maxEntries)
                _free.push(entry);
        }
    }

    /** Drop all cached buffers */
    public void clear() {
        synchronized (_free) {
            _free.clear();
        }
    }
}
