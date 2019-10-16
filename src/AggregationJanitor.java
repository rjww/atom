/*
    :: src/AggregationJanitor.java

    Author   Robert Woods <hi@robertwoods.me>
    Source   https://github.com/rjww/atom

    Performs cleanup operations on the shared AggregationCache, removing records
    for ContentServers that haven't communicated with the AggregationServer
    within an interval defined in Common. Initialized in a separate thread by
    the AggregationServer on construction.
*/

package rjww.atom;

import java.util.ArrayList;
import java.util.UUID;

public class AggregationJanitor implements Runnable {
    private AggregationCache cache;
    private int cleanupInterval;
    private int expirationThreshold;
    private boolean isRunning;

    // Construct the AggregationJanitor with the supplied cache. The cleanup
    // interval and expiration threshold are also initalized, using values
    // defined in Common.
    public AggregationJanitor(AggregationCache cache) {
        this.cache = cache;
        this.cleanupInterval = Common.AGGREGATION_JANITOR_CLEANUP_INTERVAL;
        this.expirationThreshold = Common.AGGREGATION_JANITOR_EXPIRATION_THRESHOLD;
        this.isRunning = true;
    }

    // Runs on an infinite loop. On each iteration, the janitor sleeps for
    // cleanupInterval milliseconds, then performs a cleanup operation.
    public void run() {
        try {
            Thread.sleep(this.cleanupInterval);
            while (isRunning()) {
                performCleanup();
                Thread.sleep(this.cleanupInterval);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Indicates whether the AggregationJanitor is running. Used in main loop of
    // run(), allowing the thread to be stopped.
    private synchronized boolean isRunning() {
        return this.isRunning;
    }

    // Stop the AggregationJanitor by setting isRunning to false.
    public synchronized void stop() {
        this.isRunning = false;
    }

    // For each ContentServer record in the shared AggregationCache, determine
    // whether the time since the last PUT meets the expiration threshold. Any
    // expired records are then removed from the record, which is marked as
    // dirty and written to file.
    private void performCleanup() {
        synchronized (this.cache) {
            try {
                long now = System.currentTimeMillis();

                ArrayList<UUID> expiredUUIDs = new ArrayList<>();
                this.cache.millis.entrySet().stream()
                    .forEach(e -> {
                        if (now - e.getValue() >= this.expirationThreshold)
                            expiredUUIDs.add(e.getKey());
                    });

                if (!expiredUUIDs.isEmpty()) {
                    for (UUID uuid : expiredUUIDs) {
                        this.cache.feeds.remove(uuid);
                        this.cache.lamports.remove(uuid);
                        this.cache.millis.remove(uuid);
                    }
                    this.cache.isDirty = true;
                    this.cache.writeToFile();
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
