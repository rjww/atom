/*
    :: src/AggregationCache.java

    Author   Robert Woods <hi@robertwoods.me>
    Source   https//github.com/rjww/atom

    Cached records of the shared Lamport clock, as well as current content feeds
    PUT to the AggregationServer, along with Lamport and millis-since-epoch
    timestamps. Transient fields are also used to share state information
    between the server, its AggregationWorker instances, and the
    AggregationJanitor.
*/

package rjww.atom;

import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.UUID;

public class AggregationCache implements Serializable {
    public LamportClock clock;
    public HashMap<UUID,AtomFeed> feeds;
    public HashMap<UUID,Integer> lamports;
    public HashMap<UUID,Long> millis;
    public transient boolean isDirty;
    public transient AtomFeed aggregatedFeed;
    public transient File backupFile;

    // Attempt to restore the cache from backup if its backup file exists.
    // Initialize all transient values, and write to file.
    public AggregationCache() {
        this.backupFile = new File(Common.AGGREGATION_CACHE_BACKUP_PATH);

        try {
            if (this.backupFile.exists()) {
                readFromFile();
            }
            else {
                this.clock = new LamportClock();
                this.feeds = new HashMap<>();
                this.lamports = new HashMap<>();
                this.millis = new HashMap<>();
            }
            this.isDirty = true;
            this.aggregatedFeed = new AtomFeed();
            writeToFile();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Read non-transient fields from a backup file.
    public void readFromFile() throws Exception {
        ObjectInputStream in = Common.getObjectInputStream(this.backupFile);
        AggregationCache backup = (AggregationCache) in.readObject();
        this.clock = backup.clock;
        this.feeds = backup.feeds;
        this.lamports = backup.lamports;
        this.millis = backup.millis;
        in.close();
    }

    // Write all non-transient fields to a backup file. This operation involves
    // an indirect write to a temporary file, which is then moved to replace an
    // existing backup, so as to avoid a partial write if the server fails
    // mid-way.
    public void writeToFile() throws Exception {
        File tmp = new File(this.backupFile.getAbsolutePath() + ".tmp");
        ObjectOutputStream out = Common.getObjectOutputStream(tmp);
        out.writeObject(this);
        out.close();
        Files.move(tmp.toPath(), this.backupFile.toPath(),
                   StandardCopyOption.REPLACE_EXISTING);
    }
}
