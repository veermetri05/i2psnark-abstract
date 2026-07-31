package org.klomp.snark.event;

import org.klomp.snark.spi.EventBus;

/**
 *  Base class for all events posted to an {@link EventBus}.
 *
 *  An event knows its listener interface ({@link #listenerType()})
 *  and how to deliver itself to one listener
 *  ({@link #dispatch(Object)}). The bus matches events to
 *  registered listeners by listener type.
 *
 *  @param <L> the listener interface this event dispatches to
 *  @since 0.1.0
 */
public abstract class Event<L> {

    private final long _time;

    protected Event() {
        _time = System.currentTimeMillis();
    }

    /** @return when the event occurred (ms since epoch) */
    public long getTime() {
        return _time;
    }

    /** @return the listener interface class this event dispatches to */
    public abstract Class<L> listenerType();

    /** Deliver this event to a single listener. */
    public abstract void dispatch(L listener);
}
