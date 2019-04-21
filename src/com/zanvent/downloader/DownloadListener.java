package com.zanvent.downloader;

public interface DownloadListener {
    void onDownloadStarted(Downloader.DownloadStatus downloadStatus);
    void onDownloadFinished(Downloader.DownloadStatus downloadStatus);
    void onDownloadFailed(Downloader.DownloadStatus downloadStatus);
}
