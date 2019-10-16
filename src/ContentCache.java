/*
    :: src/ContentCache.java

    Author   Robert Woods <hi@robertwoods.me>
    Source   https://github.com/rjww/atom

    Cached records of the ContentServer's Lamport clock and UUID, with read-from
    and write-to file operations.
*/

package rjww.atom;

import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class ContentCache implements Serializable {
    public LamportClock clock;
    public UUID uuid;
    public transient File backupFile;

    // Construct the ContentCache by attempting to read from the backup file if
    // it exists. If it doesn't, initialize all member variables and write to
    // file.
    public ContentCache(File backupFile) {
        this.backupFile = backupFile;

        try {
            if (this.backupFile.exists()) {
                readFromFile();
            }
            else {
                this.clock = new LamportClock();
                this.uuid = UUID.randomUUID();
                writeToFile();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Read non-transient fields from a backup file.
    public void readFromFile() throws Exception {
        ObjectInputStream in = Common.getObjectInputStream(this.backupFile);
        ContentCache backup = (ContentCache) in.readObject();
        this.clock = backup.clock;
        this.uuid = backup.uuid;
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
