package com.zanvent.downloader;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class Util {

    private static AtomicLong processCounter = new AtomicLong(1);

    public static String createProcessID() {
        return String.valueOf(processCounter.getAndIncrement());
    }

    public static ArrayList<Downloader.Range> range(long length, long gap) {
        ArrayList<Downloader.Range> ranges = new ArrayList<>();
        long chunkSize = length / gap;
        for (int i = 1; i <= gap; i++) {
            Downloader.Range range = new Downloader.Range();
            range.min = (chunkSize * (i - 1));
            if (i != gap)
                range.max = (chunkSize * i) - 1;
            else
                range.max = length;

            ranges.add(range);
        }
        return ranges;
    }

    public static class LockBasedLong {
        private long i = 0;

        public synchronized void add(long n) {
            i += n;
        }

        public synchronized long get() {
            return i;
        }
    }


}
