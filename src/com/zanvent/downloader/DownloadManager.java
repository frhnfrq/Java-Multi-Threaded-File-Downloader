package com.zanvent.downloader;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadManager {
    private HashMap<String, Downloader> mDownloadProcesses;
    private int mConcurrentDownload;
    private ExecutorService mExecutorService;
    private DownloadListener mDownloadListener;

    public DownloadManager(int concurrentDownload) {
        mConcurrentDownload = concurrentDownload;
        mExecutorService = Executors.newFixedThreadPool(concurrentDownload);
        mDownloadProcesses = new HashMap<>();
    }

    public String download(URL url) {
        Downloader downloader = new Downloader(this, url);
        mDownloadProcesses.put(downloader.getProcessID(), downloader);
        mExecutorService.execute(downloader);
        return downloader.getProcessID();
    }

    public Downloader.DownloadStatus getDownloadStatus(String processID) {
        Downloader downloader = mDownloadProcesses.get(processID);
        if (downloader == null) {
            return null;
        }

        return downloader.getDownloadStatus();
    }

    public void cancelDownload(String processId) {
        Downloader downloader = mDownloadProcesses.get(processId);
        downloader.interrupt();
    }

    public void setDownloadListener(DownloadListener downloadListener) {
        mDownloadListener = downloadListener;
    }

    public DownloadListener getDownloadListener() {
        return mDownloadListener;
    }

    public ArrayList<Downloader> getDownloadList() {
        return new ArrayList<>(mDownloadProcesses.values());
    }

    public static class Config {
        public static String path = "H:\\transfer";
        public static int connections = 8;
    }

}
