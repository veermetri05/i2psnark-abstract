package org.klomp.snark.event;

import org.klomp.snark.spi.PeerIdentity;

/**
 *  Listener for {@link MetadataEvent}s — the primary way for UIs to
 *  show live metadata-download progress without polling.
 *
 *  All callbacks arrive on the event bus dispatch thread; keep them
 *  fast and non-blocking.
 *
 *  @since 0.1.0
 */
public interface MetadataListener {

    /** A metadata download event occurred (progress, phase change, result). */
    void onMetadataEvent(MetadataEvent event);

    /**
     *  Convenience adapter: report only percent + status text.
     *  Useful for simple status lines / notifications.
     */
    abstract class Adapter implements MetadataListener {
        @Override
        public void onMetadataEvent(MetadataEvent event) {
            onProgress(event.getPercent(), event.getMessage());
        }
        /** @param percent 0-100 overall progress for the current peer attempt */
        public abstract void onProgress(int percent, String status);
    }

    /**
     *  Convenience adapter: receive only the completion event.
     */
    abstract class CompletionAdapter implements MetadataListener {
        @Override
        public void onMetadataEvent(MetadataEvent event) {
            switch (event.getPhase()) {
                case COMPLETE:
                    onComplete(event);
                    break;
                case FAILED:
                    onFailed(event);
                    break;
                default:
                    break;
            }
        }
        public abstract void onComplete(MetadataEvent event);
        public abstract void onFailed(MetadataEvent event);
    }
}
