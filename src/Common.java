/*
    :: src/Common.java

    Author   Robert Woods <hi@robertwoods.me>
    Source   https://github.com/rjww/atom

    A set of shared static variables and utility functions.
*/

package rjww.atom;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.Socket;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

public class Common {
    public static final String AGGREGATION_CACHE_BACKUP_PATH = "./data/AggregationServer/cache.lock";
    public static final int AGGREGATION_JANITOR_CLEANUP_INTERVAL = 1000;
    public static final int AGGREGATION_JANITOR_EXPIRATION_THRESHOLD = 15000;
    public static final int AGGREGATION_SERVER_DEFAULT_PORT = 4567;
    public static final int CONTENT_HEARTBEAT_INTERVAL = 1000;
    public static final String CONTENT_SERVER_BACKUP_PATH = "./data/ContentServer/records/";
    public static final int SOCKET_TIMEOUT = 5000;

    // Construct a BufferedReader from the input stream of a supplied socket.
    public static BufferedReader getBufferedReader(Socket socket) throws Exception {
        return new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    // Construct a BufferedReader from a supplied file.
    public static BufferedReader getBufferedReader(File file) throws Exception {
        return new BufferedReader(new FileReader(file));
    }

    // Construct an ObjectInputStream from the input stream of a supplied file.
    public static ObjectInputStream getObjectInputStream(File file) throws Exception {
        return new ObjectInputStream(new FileInputStream(file));
    }

    // Construct an ObjectOutputStream from the output stream of a supplied file.
    public static ObjectOutputStream getObjectOutputStream(File file) throws Exception {
        return new ObjectOutputStream(new FileOutputStream(file));
    }

    // Construct a PrintWriter from the output stream of a supplied socket.
    public static PrintWriter getPrintWriter(Socket socket) throws Exception {
        return new PrintWriter(socket.getOutputStream(), true);
    }

    // Get the base filename from the path of a supplied file, sans extension.
    public static String getFilenameWithoutExtension(File file) {
        Pattern pattern = Pattern.compile("/([^/]+)\\..*?\\z");
        Matcher matcher = pattern.matcher(file.getPath());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return file.getPath();
    }

    // Read a supplied BufferedReader into an HTTPRequest object.
    public static HTTPRequest readHTTPRequest(BufferedReader in) throws Exception {
        HTTPRequest request = new HTTPRequest();
        String inputLine;

        inputLine = in.readLine();
        Matcher matcher = Pattern.compile("\\A(\\w+) /(\\w+)")
                                 .matcher(inputLine);
        if (matcher.find()) {
            request.method = matcher.group(1);
            request.resource = matcher.group(2);
        }

        while ((inputLine = in.readLine()) != null && !inputLine.equals("")) {
            String[] tokens = inputLine.split(": ");
            if (tokens[0].equalsIgnoreCase("uuid"))
                request.uuid = UUID.fromString(tokens[1]);
            if (tokens[0].equalsIgnoreCase("lamport"))
                request.lamportTime = Integer.parseInt(tokens[1]);
        }

        request.body = "";
        while ((inputLine = in.readLine()) != null && !inputLine.equals("")) {
            request.body += inputLine + "\n";
        }

        return request;
    }

    // Read a supplied BufferedReader into an HTTPResponse object.
    public static HTTPResponse readHTTPResponse(BufferedReader in) throws Exception {
        HTTPResponse response = new HTTPResponse();
        String inputLine;

        inputLine = in.readLine();
        Matcher matcher = Pattern.compile("\\A\\S+ (\\d+)")
                                 .matcher(inputLine);
        if (matcher.find()) {
            response.statusCode = Integer.parseInt(matcher.group(1));
        }

        while ((inputLine = in.readLine()) != null && !inputLine.equals("")) {
            String[] tokens = inputLine.split(": ");
            if (tokens[0].equalsIgnoreCase("lamport"))
                response.lamportTime = Integer.parseInt(tokens[1]);
        }

        response.body = "";
        while ((inputLine = in.readLine()) != null && !inputLine.equals("")) {
            response.body += inputLine + "\n";
        }

        return response;
    }

    // Marshal a supplied AtomFeed into an XML string.
    public static String marshalXML(AtomFeed feed) throws Exception {
        StringWriter writer = new StringWriter();

        try {
            JAXBContext context = JAXBContext.newInstance(AtomFeed.class);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(feed, writer);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return writer.toString();
    }

    // Unmarshal a supplied XML string into an AtomFeed object.
    public static AtomFeed unmarshalXML(String xmlString) throws Exception {
        AtomFeed feed = null;
        JAXBContext context = JAXBContext.newInstance(AtomFeed.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        feed = (AtomFeed) unmarshaller.unmarshal(new StringReader(xmlString));
        return feed;
    }
}
