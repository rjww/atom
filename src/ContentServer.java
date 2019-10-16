/*
    :: src/ContentServer.java

    Author   Robert Woods <hi@robertwoods.me>
    Source   https://github.com/rjww/atom

    Simulates a server hosting a particular Atom feed, which is send to the
    AggregationServer via a HTTP PUT request.
*/

package rjww.atom;

import java.io.BufferedReader;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContentServer implements Serializable {
    private ContentCache cache;
    private transient ContentHeartbeat heartbeat;
    private transient String host;
    private transient int port;
    private transient boolean isRunning;

    // Construct the ContentServer and initialize its heartbeat. The constructor
    // for ContentCache attempts a read-from-file, so the server will recover
    // from a failure if its backup file is present.
    public ContentServer(String host, int port, File backupFile) {
        this.cache = new ContentCache(backupFile);
        this.host = host;
        this.port = port;
        this.heartbeat = new ContentHeartbeat(this.cache, host, port);
        this.isRunning = false;
    }

    // Initialize a ContentServer and do a single PUT with the input file
    // supplied as a command-line argument. The server must be manually killed,
    // since its heartbeat thread will keep it alive indefinitely.
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java ContentServer host:port inputFilename");
            System.exit(1);
        }

        String[] tokens = args[0].split(":");
        String host = tokens[0];
        int port = Integer.parseInt(tokens[1]);
        String inputFilename = args[1];

        File inputFile = new File(inputFilename);
        File backupFile = new File(Common.CONTENT_SERVER_BACKUP_PATH +
                                   Common.getFilenameWithoutExtension(inputFile) +
                                   ".lock");

        ContentServer server = new ContentServer(host, port, backupFile);

        try {
            server.put(inputFile);
        }
        catch (ConnectException e) {
            System.out.println("ContentServer: Unable to connect to server.");
            System.exit(0);
        }
        catch (SocketTimeoutException e) {
            System.out.println("ContentServer: Server unresponsive.");
            System.exit(0);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Return the UUID from the ContentCache.
    public UUID uuid() {
        synchronized (this.cache) {
            return this.cache.uuid;
        }
    }

    // PUT the feed contained in the supplied input file to the
    // AggregationServer whose address was given on construction of the
    // ContentServer.
    public HTTPResponse put(File inputFile) throws Exception {
        if (!this.isRunning) {
            new Thread(this.heartbeat).start();
            this.isRunning = true;
        }

        Socket socket = new Socket(this.host, this.port);
        socket.setSoTimeout(Common.SOCKET_TIMEOUT);
        BufferedReader in = Common.getBufferedReader(socket);
        PrintWriter out = Common.getPrintWriter(socket);
        AtomFeed feed = new AtomFeed(inputFile);

        sendRequest(out, feed);
        HTTPResponse response = receiveResponse(in);
        socket.close();
        this.cache.writeToFile();

        return response;
    }

    // Send a stop message down to the ContentHeartbeat.
    public void stop() {
        this.heartbeat.stop();
        this.isRunning = false;
    }

    // Do the request send for put().
    private void sendRequest(PrintWriter out, AtomFeed feed) throws Exception {
        synchronized (this.cache) {
            // Update local Lamport clock for request transmission.
            int localLamport = this.cache.clock.update();

            // Build HTTP request body by marshalling the supplied Atom feed.
            String body = Common.marshalXML(feed);

            // Construct HTTP request and send to server.
            out.println(new StringBuffer()
               .append("PUT /feed HTTP/1.1\n")
               .append("User-Agent: ATOMClient/1/0\n")
               .append("Content-Type: application/xml\n")
               .append("Content-Length: " + body.getBytes().length + "\n")
               .append("UUID: " + uuid() + "\n")
               .append("Lamport: " + localLamport + "\n")
               .append("\n")
               .append(body)
               .toString());
        }
    }

    // Receive and parse an incoming response from the AggregationServer so that
    // it can be returned in put().
    private HTTPResponse receiveResponse(BufferedReader in) throws Exception {
        HTTPResponse response = Common.readHTTPResponse(in);

        synchronized (this.cache) {
            // Update local Lamport clock for response receipt.
            this.cache.clock.update(response.lamportTime);
        }

        // Return the response.
        return response;
    }

    // Remove the ContentCache backup file from the file system.
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
}
