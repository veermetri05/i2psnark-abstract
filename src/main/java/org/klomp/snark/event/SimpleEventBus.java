package org.klomp.snark.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ConcurrentHashMap;

import org.klomp.snark.spi.EventBus;

/**
 *  Default {@link EventBus} implementation.
 *
 *  Dispatch is asynchronous on a single daemon thread, so network
 *  threads never block on listener code. A throwing listener is
 *  isolated (logged, never propagated); other listeners still run.
 *
 *  @since 0.1.0
 */
public class SimpleEventBus implements EventBus {

    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Object>> _listeners;
    private final ExecutorService _dispatch;

    public SimpleEventBus() {
        this("i2psnark-events");
    }

    public SimpleEventBus(String name) {
        _listeners = new ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Object>>();
        ThreadFactory tf = new ThreadFactory() {
            private int _n;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, name + '-' + (++_n));
                t.setDaemon(true);
                return t;
            }
        };
        _dispatch = Executors.newSingleThreadExecutor(tf);
    }

    @Override
    public <L> void register(Class<L> listenerType, L listener) {
        if (listenerType == null || listener == null)
            throw new IllegalArgumentException("null type or listener");
        CopyOnWriteArrayList<Object> list = _listeners.get(listenerType);
        if (list == null) {
            CopyOnWriteArrayList<Object> created = new CopyOnWriteArrayList<Object>();
            list = _listeners.putIfAbsent(listenerType, created);
            if (list == null)
                list = created;
        }
        if (!list.contains(listener))
            list.add(listener);
    }

    @Override
    public <L> void unregister(Class<L> listenerType, L listener) {
        CopyOnWriteArrayList<Object> list = _listeners.get(listenerType);
        if (list != null)
            list.remove(listener);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <E> void post(E event) {
        if (!(event instanceof Event))
            return; // not one of ours
        final Event<Object> typed = (Event<Object>) event;
        final Class<Object> type = (Class<Object>) typed.listenerType();
        _dispatch.execute(new Runnable() {
            @Override
            public void run() {
                dispatch(typed, type);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void dispatch(Event<Object> event, Class<Object> type) {
        List<Object> list = _listeners.get(type);
        if (list == null)
            return;
        for (Object listener : list) {
            try {
                event.dispatch(listener);
            } catch (Throwable t) {
                System.err.println("Event listener error in " + type.getSimpleName() + ": " + t);
                t.printStackTrace();
            }
        }
    }

    /** Stop the dispatcher thread. Pending events are dropped. */
    public void shutdown() {
        _dispatch.shutdown();
    }
}
