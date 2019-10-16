/*
    :: src/AggregationWorker.java

    Author   Robert Woods <hi@robertwoods.me>
    Source   https://github.com/rjww/atom

    Handles and services an individual request to the AggregationServer. Updates
    all cached feeds PUT to the server by ContentServers, registers empty PUTs
    from ContentHeartbeats to the cache, and collects and transmits the
    aggregated feed in response to GETClient requests.
*/

package rjww.atom;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.bind.JAXBException;

public class AggregationWorker implements Runnable {
    private AggregationCache cache;
    private Socket socket;

    public AggregationWorker(AggregationCache cache, Socket socket) {
        this.cache = cache;
        this.socket = socket;
    }

    // The AggregationWorker runs by handling a single request on the socket
    // passed to it by the AggregationServer.
    public void run() {
        try (
            BufferedReader in = Common.getBufferedReader(this.socket);
            PrintWriter out = Common.getPrintWriter(this.socket);
        ) {
            this.socket.setSoTimeout(Common.SOCKET_TIMEOUT);
            handleRequest(in, out);
        }
        catch (SocketTimeoutException e) {
            System.out.println("AggregationWorker: Client unresponsive.");
            System.exit(0);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Dispatch on request method and, if a PUT, on target resource. Anything
    // that isn't a GET or a PUT is responded to with a 400 Bad Request.
    private void handleRequest(BufferedReader in,
                               PrintWriter out) throws Exception {
        HTTPRequest request = Common.readHTTPRequest(in);
        String method = request.method.toLowerCase();
        String resource = request.resource.toLowerCase();

        if (method.equals("get"))
            sendAggregatedFeed(request, out);

        if (method.equals("put") && resource.equals("feed"))
            updateContentCache(request, out);

        if (method.equals("put") && resource.equals("heartbeat"))
            registerHeartbeat(request, out);

        // Send 400 Bad Request response to the client.
        else
            sendResponse(400, out);
    }

    // Service a GET request by sending the aggregated feed. If the content
    // cache is dirty (i.e. if it has been updated since the last GET), then an
    // aggregate operation is performed first.
    private void sendAggregatedFeed(HTTPRequest request,
                                    PrintWriter out) throws Exception {
        synchronized (this.cache) {
            // Update shared Lamport clock for request receipt.
            this.cache.clock.update(request.lamportTime);

            // If cache is dirty, aggregate the feed by clearing all entries
            // from the shared feed, then accessing each content feed using the
            // set of stored UUIDs streamed in Lamport-time order.
            if (this.cache.isDirty) {
                this.cache.aggregatedFeed.clearEntries();

                ArrayList<UUID> sortedUUIDs = new ArrayList<>();
                this.cache.lamports.entrySet().stream()
                    .sorted(HashMap.Entry.comparingByValue())
                    .forEach(e -> sortedUUIDs.add(e.getKey()));

                for (UUID uuid : sortedUUIDs) {
                    AtomFeed feed = this.cache.feeds.get(uuid);
                    if (feed != null) {
                        for (AtomEntry entry : feed.getEntries()) {
                            this.cache.aggregatedFeed.addEntry(entry);
                        }
                    }
                }

                this.cache.isDirty = false;
                this.cache.writeToFile();
            }
        }

        // Send 200 OK response to the client with the aggregated feed in the
        // response body.
        sendResponse(200, out, Common.marshalXML(this.cache.aggregatedFeed));
    }

    // Service a PUT from a ContentServer by updating its record in the shared
    // cache.
    private void updateContentCache(HTTPRequest request,
                                    PrintWriter out) throws Exception {
        try {
            AtomFeed feed = Common.unmarshalXML(request.body);
            boolean isNewFeed;

            synchronized (this.cache) {
                // Update shared Lamport clock for request receipt.
                this.cache.clock.update(request.lamportTime);

                // Note whether an older copy of the feed was already cached.
                isNewFeed = !this.cache.feeds.containsKey(request.uuid);

                // Update cached records and write to file.
                this.cache.feeds.put(request.uuid, feed);
                this.cache.lamports.put(request.uuid, this.cache.clock.peek());
                this.cache.millis.put(request.uuid, System.currentTimeMillis());
                this.cache.isDirty = true;
                this.cache.writeToFile();
            }

            if (isNewFeed)
                sendResponse(201, out);
            else
                sendResponse(200, out);
        }
        // A JAXBException indicates that the feed sent by the client was
        // malformed and couldn't be unmarshalled. This is handled by responding
        // with a 500 Internal Server Error.
        catch (JAXBException e) {
            sendResponse(500, out);
        }
    }

    // Service a PUT by a ContentHeartbeat by updating the time-since-epoch (in
    // millis) since last contact in the shared cache. The AggregationJanitor
    // uses this time when determining whether a cached record should be cleaned
    // up, so registering the heartbeat prevents that from happening.
    private void registerHeartbeat(HTTPRequest request,
                                   PrintWriter out) throws Exception {
        synchronized (this.cache) {
            // Update shared Lamport clock for request receipt.
            this.cache.clock.update(request.lamportTime);

            // Update cached timestamp and write to file.
            this.cache.millis.put(request.uuid, System.currentTimeMillis());
            this.cache.writeToFile();
        }

        // Send 204 No Content response to the client.
        sendResponse(204, out);
    }

    // Send the appropriate response to the client by status code, including the
    // supplied body string (which can be empty).
    private void sendResponse(int statusCode, PrintWriter out, String body) throws Exception {
        String startLine;

        // Determine HTTP response start-line from the supplied status code.
        switch(statusCode) {
            case 200:
                startLine = "HTTP/1.1 200 OK\n";
                break;
            case 201:
                startLine = "HTTP/1.1 201 Created\n";
                break;
            case 204:
                startLine = "HTTP/1.1 204 No Content\n";
                break;
            case 500:
                startLine = "HTTP/1.1 500 Internal Server Error\n";
                break;
            default:
                startLine = "HTTP/1.1 400 Bad Request\n";
        }

        // Update shared Lamport clock for response transmission.
        int localLamport;
        synchronized (this.cache) {
            localLamport = this.cache.clock.update();
            this.cache.writeToFile();
        }

        // Construct HTTP response and send to client.
        out.println(new StringBuffer()
           .append(startLine)
           .append("Server: AggregationServer\n")
           .append("Lamport: " + localLamport + "\n")
           .append("\n")
           .append(body)
           .toString());
    }

    // Send a response without a body by dispatching to sendResponse with an
    // empty body string.
    private void sendResponse(int statusCode, PrintWriter out) throws Exception {
        sendResponse(statusCode, out, "");
    }
}
