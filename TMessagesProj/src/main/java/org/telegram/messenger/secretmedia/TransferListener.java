package org.telegram.messenger.secretmedia;

/**
 * Created by elanimus on 3/28/18.
 */

public interface TransferListener<S> {

    /**
     * Called when a transfer starts.
     *
     * @param source The source performing the transfer.
     * @param dataSpec Describes the data being transferred.
     */
    void onTransferStart(S source, DataSpec dataSpec);

    /**
     * Called incrementally during a transfer.
     *
     * @param source The source performing the transfer.
     * @param bytesTransferred The number of bytes transferred since the previous call to this
     *     method (or if the first call, since the transfer was started).
     */
    void onBytesTransferred(S source, int bytesTransferred);

    /**
     * Called when a transfer ends.
     *
     * @param source The source performing the transfer.
     */
    void onTransferEnd(S source);

}
