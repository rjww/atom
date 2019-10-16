/*
    :: src/AggregationServer.java

    Author   Robert Woods <hi@robertwoods.me>
    Source   https://github.com/rjww/atom

    Coordination program for all aggregation server functions. Initializes all
    shared data and hands them off to worker threads. Listens for incoming
    requests on the ServerSocket, and dispatches each to an AggregationWorker in
    a separate thread. Also initializes the AggregationJanitor, which is
    responsible for periodically cleaning the shared cache.
*/

package rjww.atom;

import java.io.File;
import java.net.ServerSocket;
import java.net.SocketException;
import java.nio.file.Files;

public class AggregationServer implements Runnable {
    private AggregationCache cache;
    private AggregationJanitor janitor;
    private ServerSocket socket;

    // Construct the AggregationServer with a supplied port and initialize all
    // shared data.
    public AggregationServer(int port) {
        try {
            this.cache = new AggregationCache();
            this.janitor = new AggregationJanitor(this.cache);
            this.socket = new ServerSocket(port);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Call the primary constructor with the default port.
    public AggregationServer() {
        this(Common.AGGREGATION_SERVER_DEFAULT_PORT);
    }

    // Initialize the AggregationJanitor and set it running, then listen for
    // incoming requests on the ServerSocket and hand each one off to a
    // separate AggregationWorker thread.
    public void run() {
        new Thread(this.janitor).start();

        try {
            while (true) {
                new Thread(new AggregationWorker(this.cache, this.socket.accept())).start();
            }
        }
        catch (SocketException e) {
            this.janitor.stop();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Trigger a shutdown by attempting to close the ServerSocket. The resulting
    // SocketException is caught and handled in run().
    public synchronized void stop() {
        try {
            this.socket.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Get the current Lamport time from the cache. Used for testing.
    public int lamportTime() {
        synchronized (this.cache) {
            return this.cache.clock.peek();
        }
    }

    // Remove the backup cache from the file system.
    public void cleanBackup() {
        synchronized (this.cache) {
            try {
                Files.delete(this.cache.backupFile.toPath());
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Construct and run the AggregationServer from the command line. The port
    // can optionally be specified; if left unspecified, the default constructor
    // will be called, which uses the default port as defined in Common.
    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("Usage: java AggregationServer[, port]");
            System.exit(1);
        }

        AggregationServer server;

        if (args.length == 1) {
            int port = Integer.parseInt(args[0]);
            server = new AggregationServer(port);
        }
        else {
            server = new AggregationServer();
        }

        server.run();
    }
}
