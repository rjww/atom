/*
    :: src/GETClient.java

    Author   Robert Woods <hi@robertwoods.me>
    Source   https://github.com/rjww/atom

    The GET client, which retrieves an aggregated feed from the
    AggregationServer and either returns it (when called programatically) or
    prints it to the console (when called on the command line).
*/

package rjww.atom;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class GETClient {
    private LamportClock clock;
    private String host;
    private int port;

    public GETClient(String host, int port) {
        this.clock = new LamportClock();
        this.host = host;
        this.port = port;
    }

    // GET the aggregated feed from the AggregationServer, and print it to
    // stdout.
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java GETClient host:port");
            System.exit(1);
        }

        String[] tokens = args[0].split(":");
        String host = tokens[0];
        int port = Integer.parseInt(tokens[1]);

        GETClient client = new GETClient(host, port);

        try {
            HTTPResponse response = client.get();
            System.out.println(response.body);
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

    // GET the aggregated feed from the AggregationServer and return the
    // corresponding HTTPResponse object.
    public HTTPResponse get() throws Exception {
        Socket socket = new Socket(this.host, this.port);
        socket.setSoTimeout(Common.SOCKET_TIMEOUT);
        BufferedReader in = Common.getBufferedReader(socket);
        PrintWriter out = Common.getPrintWriter(socket);

        sendRequest(out);
        HTTPResponse response = receiveResponse(in);
        socket.close();

        return response;
    }

    // Do the GET operation for get().
    private void sendRequest(PrintWriter out) throws Exception {
        int localLamport = this.clock.update();

        out.println(new StringBuffer()
           .append("GET /feed HTTP/1.1\n")
           .append("User-Agent: ATOMClient/1/0\n")
           .append("Lamport: " + localLamport + "\n")
           .append("\n")
           .toString());
    }

    // Receive and parse an incoming response from the AggregationServer, and
    // return it so that it can in turn be returned by get().
    private HTTPResponse receiveResponse(BufferedReader in) throws Exception {
        HTTPResponse response = Common.readHTTPResponse(in);
        this.clock.update(response.lamportTime);
        return response;
    }
}
