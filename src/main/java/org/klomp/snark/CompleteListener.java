/*
   Port of I2PSnark CompleteListener (GPLv2+, I2PSnark authors).
   ut_comment methods dropped: the abstract layer has no comments feature.
*/

package org.klomp.snark;

public interface CompleteListener {
    public void torrentComplete(Snark snark);
    public void updateStatus(Snark snark);

    /**
     * We transitioned from magnet mode, we have now initialized our
     * metainfo and storage. The listener should now call getMetaInfo()
     * and save the data to disk.
     *
     * @return the new name for the torrent or null on error
     * @since 0.8.4
     */
    public String gotMetaInfo(Snark snark);

    /**
     * @since 0.9
     */
    public void fatal(Snark snark, String error);

    /**
     * @since 0.9.2
     */
    public void addMessage(Snark snark, String message);

    /**
     * @since 0.9.4
     */
    public void gotPiece(Snark snark);

    /** not really listeners but the easiest way to get back to an optional SnarkManager */
    public long getSavedTorrentTime(Snark snark);
    public BitField getSavedTorrentBitField(Snark snark);
    /**
     * @since 0.9.15
     */
    public boolean getSavedPreserveNamesSetting(Snark snark);
    /**
     * @since 0.9.15
     */
    public long getSavedUploaded(Snark snark);

    /**
     * @since 0.9.42
     */
    public boolean shouldAutoStart();

    /**
     * @since 0.9.62
     */
    public BandwidthListener getBandwidthListener();
}
