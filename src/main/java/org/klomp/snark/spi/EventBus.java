package org.klomp.snark.spi;

/**
 *  Typed event bus — the abstract layer's answer to polling.
 *
 *  Protocol components (MetadataDownloader, KRPC, ...) post events;
 *  UIs register typed listeners and get pushed updates. Events are
 *  dispatched asynchronously so network threads never block on UI
 *  code, and a failing listener never breaks the dispatcher.
 *
 *  Registration is typed by the listener interface, so a UI can
 *  register the same object for several event types:
 *  <pre>
 *      bus.register(MetadataListener.class, myListener);
 *      bus.register(DhtListener.class, myListener);
 *  </pre>
 *
 *  @since 0.1.0
 */
public interface EventBus {

    /**
     *  Register a listener for the given listener type.
     *  Idempotent for the same (type, listener) pair.
     */
    <L> void register(Class<L> listenerType, L listener);

    /**
     *  Unregister a listener. No-op if not registered.
     */
    <L> void unregister(Class<L> listenerType, L listener);

    /**
     *  Post an event to all registered listeners of the event's
     *  listener type. Returns immediately; dispatch happens
     *  asynchronously on the bus's dispatch thread.
     */
    <E> void post(E event);
}
