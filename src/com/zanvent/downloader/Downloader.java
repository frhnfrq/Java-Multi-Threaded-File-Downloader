package com.zanvent.downloader;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Downloader implements Runnable {
    private DownloadManager mDownloadManager;
    private URL mUrl;
    private HttpURLConnection mConnection;
    private long mFileSize;
    private String mFileName;
    private boolean mAcceptsRange;
    private boolean mRan = false;
    private boolean mFinished = false;
    private boolean mSuccess;
    private volatile boolean mInterrupt = false;
    private String mProcessID;
    private DownloadStatus mDownloadStatus;
    private ExecutorService mExecutorService;
    private ArrayList<Future<Boolean>> mDownloadTaskResults;
    private ArrayList<Range> mRanges;
    private ArrayList<File> mFileParts;
    private Util.LockBasedLong mDownloadedSize;

    public Downloader(DownloadManager downloadManager, URL url) {
        mDownloadManager = downloadManager;
        mUrl = url;
        mProcessID = Util.createProcessID();
        mDownloadTaskResults = new ArrayList<>();
        mRanges = new ArrayList<>();
        mFileParts = new ArrayList<>();
        mDownloadedSize = new Util.LockBasedLong();
        mDownloadStatus = new DownloadStatus();
        mDownloadStatus.setProcessID(mProcessID);
    }

    @Override
    public void run() {
        mRan = true; // keeps track of download status, if the download ever started
        try {
            mConnection = (HttpURLConnection) mUrl.openConnection();
            mFileSize = getFileSize(mConnection);
            mFileName = getFilename(mConnection);
//            System.out.println("Filename is " + mFileName);
            mAcceptsRange = acceptsRange(mConnection);

            mDownloadStatus.setFileName(mFileName);
            mDownloadStatus.setFileSize(mFileSize);

            mDownloadManager.getDownloadListener().onDownloadStarted(mDownloadStatus);

            if (mFileName != null && mFileSize > 0) {  // valid URL
                if (mAcceptsRange) // "Range: bytes min-max" in the header, download it in multiple connections
                    mSuccess = initiateDownloader(mUrl, mFileName, DownloadManager.Config.connections);
                else    // download in single connection
                    mSuccess = initiateDownloader(mUrl, mFileName, 1);
            } else { // invalid URL
                mSuccess = false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            mSuccess = false;
        }
        mFinished = true;
        mConnection.disconnect();

        if (mSuccess) {
//            System.out.println(Thread.currentThread().getName() + " download completed. Download size " + mDownloadedSize.get() + " and file size " + mFileSize);
            mDownloadManager.getDownloadListener().onDownloadFinished(mDownloadStatus);
        } else {
//            System.out.println(Thread.currentThread().getName() + " download failed");
            mDownloadManager.getDownloadListener().onDownloadFailed(mDownloadStatus);
        }
    }


    private boolean initiateDownloader(URL url, String filename, int connections) {

        File file = new File(DownloadManager.Config.path, filename); // which will be created after download finishes
        file.getParentFile().mkdirs();

        if (connections > 1) { // download in multiple connections
            mExecutorService = Executors.newFixedThreadPool(connections);
            mRanges = Util.range(mFileSize, connections); // mRanges of bytes for each connection eg. min and max

            for (int i = 1; i <= connections; i++) {
                File f = new File(DownloadManager.Config.path, filename + "_part" + i); // temp file containing parts of the file from each connection
                mFileParts.add(f); // keep track of the temp files
                DownloaderTask downloaderTask = new DownloaderTask(url, f, i, mRanges.get(i - 1));
                Future<Boolean> future = mExecutorService.submit(downloaderTask);
                mDownloadTaskResults.add(future); // list of Future<Boolean> is needed to check if the file downloaded successfully or not
            }

            boolean success = true;
            for (Future<Boolean> future : mDownloadTaskResults) {
                try {
                    success &= future.get(); // this is a blocking method. if even one connection failed to download successfully boolean mSuccess will be false
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }

            if (success) { // if the file was downloaded successfully, merge it into a single file
                try {
                    OutputStream outputStream = new FileOutputStream(file, true);
                    for (File fp : mFileParts) {
                        InputStream inputStream = new FileInputStream(fp);
                        IOUtils.copyLarge(inputStream, outputStream);
                        inputStream.close();
                        fp.delete(); // delete the temp file
                    }
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    success = false;
                }
            } else { // delete the temp files if the download was failed
                for (File fp : mFileParts) {
                    fp.delete();
                }
            }

            mExecutorService.shutdown();

            return success;

        } else { // download in single connection and the download will be done on the current thread
            DownloaderTask downloaderTask = new DownloaderTask(url, file, 1, new Range(0, mFileSize));
            boolean success;
            try {
                success = downloaderTask.call();
            } catch (Exception e) {
                e.printStackTrace();
                success = false;
            }
            if (!success) // if the download failed, delete the file
                if (file.exists())
                    file.delete();

            return success;
        }
    }


    private class DownloaderTask implements Callable<Boolean> {

        private URL url;
        private File file;
        private int part;
        private Range range;

        private int retry = 0;

        public DownloaderTask(URL url, File file, int part, Range range) {
            this.url = url;
            this.file = file;
            this.part = part;
            this.range = range;
        }

        @Override
        public Boolean call() throws Exception {

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            if (file.exists()) {
//                System.out.println("File existed for " + Thread.currentThread().getName() + " with a size of " + file.length());
                if (file.length() >= (range.max - range.min)) {
                    return true;
                } else if (retry == 0) { // Downloader was restarted, add the downloaded bytes count to the mDownloadedSize field
                    mDownloadedSize.add(file.length());
                }
                range.min += file.length(); // update the min range
            } else {
//                System.out.println("File didn't exist for " + Thread.currentThread().getName());
            }

//            System.out.println("Range for " + Thread.currentThread().getName() + " " + range.min + " " + range.max);

            conn.setRequestProperty("Range", "bytes=" + range.min + "-" + range.max);

            try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream out = new FileOutputStream(file, true)) {


                int byteSize = 1024 * 50;
                byte[] dataBuffer = new byte[byteSize];
                int bytesRead;
                long bytesReadSize = 0; // TEMP
                long lastTime = System.currentTimeMillis();
                while ((bytesRead = in.read(dataBuffer, 0, byteSize)) != -1) {
                    if (isInterrupted()) {
                        return false;
                    }
                    mDownloadedSize.add(bytesRead);
                    bytesReadSize += bytesRead;
                    out.write(dataBuffer, 0, bytesRead);
                    if ((System.currentTimeMillis() - lastTime) > 2000) {
                        lastTime = System.currentTimeMillis();
//                        System.out.println("Thread " + Thread.currentThread().getName() + " " + ((((float) bytesReadSize) / 1024) / 1024) / 2);
                        bytesReadSize = 0;
                    }
                }
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                if (retry < 3) {
                    retry++;
//                    System.out.println("Retrying for " + Thread.currentThread().getName());
                    return call();
                } else {
//                    System.out.println("Failed to retry for " + Thread.currentThread().getName());
                    interrupt();
                }
                return false;
            } finally {
                conn.disconnect();
            }
        }
    }

    private boolean isInterrupted() {
        return mInterrupt;
    }

    public void interrupt() {
        mInterrupt = true;
    }

    private long getFileSize(HttpURLConnection conn) {
        return conn.getContentLengthLong();
    }

    private String getFilename(HttpURLConnection conn) {

        String filename = FilenameUtils.getName(conn.getURL().getPath());

        if (conn.getHeaderField("Content-Disposition") != null) {
            String contentDisposition = conn.getHeaderField("Content-Disposition");
            String reg = "(?<=filename=)([^&]*)\"";

            Pattern p = Pattern.compile(reg);

            Matcher m = p.matcher(contentDisposition);
            if (m.find()) {
                String result = m.group();
                return result.substring(1, result.length() - 1);
            }
        }

        if (filename != null && !filename.isEmpty()) {
            return filename;
        } else {
            return null;
        }
    }

    private boolean acceptsRange(HttpURLConnection conn) {
        if (conn.getHeaderField("Accept-Ranges") != null) {
            if (conn.getHeaderField("Accept-Ranges").equals("bytes")) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public static class Range {
        long min;
        long max;

        Range() {

        }

        Range(long min, long max) {
            this.min = min;
            this.max = max;
        }
    }

    public long getFileSize() {
        return mFileSize;
    }

    public String getFileName() {
        return mFileName;
    }

    public boolean hasRan() {
        return mRan;
    }

    public boolean isFinished() {
        return mFinished;
    }

    public boolean isSuccess() {
        return mSuccess;
    }

    public long getDownloadedSize() {
        return mDownloadedSize.get();
    }

    public String getProcessID() {
        return mProcessID;
    }

    public DownloadStatus getDownloadStatus() {
        mDownloadStatus.setDownloadedSize(getDownloadedSize());
        mDownloadStatus.setFinished(isFinished());
        mDownloadStatus.setRan(hasRan());
        mDownloadStatus.setSuccess(isSuccess());
        return mDownloadStatus;
    }

    public static class DownloadStatus {
        private String processID;
        private String fileName;
        private long fileSize;
        private long downloadedSize;
        private boolean ran, finished, success;

        public DownloadStatus() {

        }

        public DownloadStatus(String processID, String fileName, long fileSize, long downloadedSize, boolean ran, boolean finished, boolean success) {
            this.processID = processID;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.downloadedSize = downloadedSize;
            this.ran = ran;
            this.finished = finished;
            this.success = success;
        }

        public void setProcessID(String processID) {
            this.processID = processID;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public void setFileSize(long fileSize) {
            this.fileSize = fileSize;
        }

        public void setDownloadedSize(long downloadedSize) {
            this.downloadedSize = downloadedSize;
        }

        public void setRan(boolean ran) {
            this.ran = ran;
        }

        public void setFinished(boolean finished) {
            this.finished = finished;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getProcessID() {
            return processID;
        }

        public String getFileName() {
            return fileName;
        }

        public long getFileSize() {
            return fileSize;
        }

        public long getDownloadedSize() {
            return downloadedSize;
        }

        public boolean hasRan() {
            return ran;
        }

        public boolean isFinished() {
            return finished;
        }

        public boolean isSuccess() {
            return success;
        }
    }
}
