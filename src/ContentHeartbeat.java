/*
    :: src/ContentHeartbeat.java

    Author   Robert Woods <hi@robertwoods.me>
    Source   https://github.com/rjww/atom

    Maintains a heartbeat with the AggregationServer on a ContentServer's
    behalf, by periodically sending an empty PUT request, and thus keeps the
    ContentServer's record fresh in the AggregationServer's cache. PUTs
    increment a shared Lamport clock on transmission, but are send-and-forget,
    so no response is read.
*/

package rjww.atom;

import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.util.UUID;

public class ContentHeartbeat implements Runnable {
    private ContentCache cache;
    private String host;
    private int port;
    private boolean isRunning;

    public ContentHeartbeat(ContentCache cache,
                            String host,
                            int port) {
        this.cache = cache;
        this.host = host;
        this.port = port;
        this.isRunning = true;
    }

    // While running, the ContentHeartbeat periodically opens a new socket with
    // the AggregationServer, and then constructs and sends an empty PUT request.
    public void run() {
        try {
            while (isRunning()) {
                Socket socket = new Socket(this.host, this.port);
                PrintWriter out = Common.getPrintWriter(socket);

                synchronized (this.cache) {
                    int localLamport = this.cache.clock.update();

                    out.println(new StringBuffer()
                       .append("PUT /heartbeat HTTP/1.1\n")
                       .append("User-Agent: ATOMClient/1/0\n")
                       .append("UUID: " + this.cache.uuid + "\n")
                       .append("Lamport: " + localLamport + "\n")
                       .toString());
                }

                socket.close();
                Thread.sleep(Common.CONTENT_HEARTBEAT_INTERVAL);
            }
        }
        catch (ConnectException e) {
            System.out.println("ContentHeartbeat: Unable to contact server.");
            System.exit(0);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Get the value of isRunning. Used in the main loop of run().
    private synchronized boolean isRunning() {
        return this.isRunning;
    }

    // Stop the ContentHeartbeat by setting isRunning to false.
    public synchronized void stop() {
        this.isRunning = false;
    }
}
