/*
   Port of I2PSnark CoordinatorListener (GPLv2+, I2PSnark authors).
*/

package org.klomp.snark;

public interface CoordinatorListener
{
  /**
   * Called when the PeerCoordinator notices a change in the state of a peer.
   */
  void peerChange(PeerCoordinator coordinator, Peer peer);

  /**
   * Called when the PeerCoordinator got the MetaInfo via magnet.
   * @since 0.8.4
   */
  void gotMetaInfo(PeerCoordinator coordinator, MetaInfo metainfo);

  /**
   * Is this number of uploaders over the per-torrent limit?
   */
  public boolean overUploadLimit(int uploaders);

  public void addMessage(String message);
}
