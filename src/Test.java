/*
    :: src/Test.java

    Author   Robert Woods <hi@robertwoods.me>
    Source   https://github.com/rjww/atom

    The test harness for the Atom syndication project.
*/

package rjww.atom;

import java.io.File;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class Test {
    private static String host = "localhost";
    private static int port = 4444;
    private static String contentBackupPath = "./data/ContentServer/records/";
    private static String contentInputPath = "./data/ContentServer/input/";

    public static void main(String[] args) {
        try {
            int testDelay = 1000;

            testAggregationServerPersistence();
            Thread.sleep(testDelay);

            testContentServerPersistence();
            Thread.sleep(testDelay);

            testInitialContentServerPUT();
            Thread.sleep(testDelay);

            testSubsequentContentServerPUT();
            Thread.sleep(testDelay);

            testMultipleContentServerPUTs();
            Thread.sleep(testDelay);

            testAggregationJanitor();
            Thread.sleep(testDelay);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void testAggregationServerPersistence() {
        System.out.println("Testing that AggregationServer recovers from backup...");

        int lamportTime1, lamportTime2;

        {
            System.out.println("✔ Initializing first AggregationServer");
            AggregationServer aggregationServer = new AggregationServer(Test.port);
            new Thread(aggregationServer).start();

            System.out.println("✔ Making request to first AggregationServer to increment Lamport clock");
            try {
                GETClient client = new GETClient(Test.host, Test.port);
                client.get();
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            System.out.println("✔ Capturing Lamport time on first AggregationServer");
            lamportTime1 = aggregationServer.lamportTime();

            System.out.println("✔ Stopping first AggregationServer");
            aggregationServer.stop();
        }

        {
            System.out.println("✔ Initializing second AggregationServer with same backup file");
            AggregationServer aggregationServer = new AggregationServer(Test.port);
            new Thread(aggregationServer).start();

            System.out.println("✔ Captuing Lamport time on second AggregationServer");
            lamportTime2 = aggregationServer.lamportTime();

            System.out.println("✔ Stopping second AggregationServer");
            aggregationServer.stop();
            aggregationServer.cleanBackup();
        }

        if (lamportTime1 == lamportTime2) {
            System.out.println("✔ Lamport times match, indicating second server loaded from first server's backup");
        }
        else {
            System.out.println("✗ Lamport times don't match");
            System.exit(1);
        }
    }

    private static void testContentServerPersistence() {
        System.out.println("Testing that ContentServer recovers from backup...");

        System.out.println("✔ Initializing two ContentServers with the same backup file");
        File backupFile = new File(Test.contentBackupPath + "server.lock");
        ContentServer contentServer1 = new ContentServer(Test.host, Test.port, backupFile);
        ContentServer contentServer2 = new ContentServer(Test.host, Test.port, backupFile);

        UUID uuid1, uuid2;

        {
            System.out.println("✔ Initializing first ContentServer");
            ContentServer contentServer = new ContentServer(Test.host, Test.port, backupFile);

            System.out.println("✔ Capturing UUID on first ContentServer");
            uuid1 = contentServer.uuid();

            System.out.println("✔ Stopping first ContentServer");
            contentServer.stop();
        }

        {
            System.out.println("✔ Initializing second ContentServer with same backup file");
            ContentServer contentServer = new ContentServer(Test.host, Test.port, backupFile);

            System.out.println("✔ Captuing Lamport time on second AggregationServer");
            uuid2 = contentServer.uuid();

            System.out.println("✔ Stopping second ContentServer");
            contentServer.stop();
            contentServer.cleanBackup();
        }

        if (uuid1.equals(uuid2)) {
            System.out.println("✔ UUIDs match, indicating second server loaded from first server's backup");
        }
        else {
            System.out.println("✗ UUIDs don't match");
            System.exit(1);
        }
    }

    private static void testInitialContentServerPUT() {
        System.out.println("Testing initial ContentServer PUT to AggregationServer...");

        File backupFile = new File(Test.contentBackupPath + "server.lock");
        File inputFile = new File(Test.contentInputPath + "example1.txt");
        AtomFeed originalFeed = new AtomFeed(inputFile);

        AggregationServer aggregationServer = new AggregationServer(Test.port);
        ContentServer contentServer = new ContentServer(Test.host, Test.port, backupFile);
        GETClient client = new GETClient(Test.host, Test.port);

        new Thread(aggregationServer).start();

        try {
            HTTPResponse response = contentServer.put(inputFile);

            System.out.println("✔ Successfully connected to AggregationServer");
            System.out.println("✔ Response received");
            if (response.statusCode == 201)
                System.out.println("✔ Response status code is 201");
            else
                System.out.println("✗ Response status code is not 201");
        }
        catch (ConnectException e) {
            System.out.println("✗ Couldn't connect to AggregationServer");
            System.exit(1);
        }
        catch (SocketTimeoutException e) {
            System.out.println("✗ Timed out while waiting for response");
            System.exit(1);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        try {
            HTTPResponse response = client.get();
            AtomFeed newFeed = Common.unmarshalXML(response.body);

            List<String> originalIds = originalFeed.getEntries()
                .stream()
                .map(entry -> entry.getId())
                .collect(Collectors.toList());

            List<String> newIds = newFeed.getEntries()
                .stream()
                .map(entry -> entry.getId())
                .collect(Collectors.toList());

            if (originalIds.size() == newIds.size()) {
                System.out.println("✔ Aggregated feed has same entry count as ContentServer feed");
            }
            else {
                System.out.println("✗ Aggregated feed has different entry count to ContentServer feed");
                System.exit(1);
            }

            boolean entriesMatch = newIds.stream()
                .map(id -> originalIds.contains(id))
                .allMatch(val -> val == true);

            if (entriesMatch) {
                System.out.println("✔ Aggregated feed contains all entries from ContentServer feed");
            }
            else {
                System.out.println("✗ Aggregated feed doesn't contain all entries from ContentServer feed");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        aggregationServer.stop();
        aggregationServer.cleanBackup();
        contentServer.stop();
        contentServer.cleanBackup();
    }

    private static void testSubsequentContentServerPUT() {
        System.out.println("Testing subsequent ContentServer PUT to AggregationServer...");

        File backupFile = new File(Test.contentBackupPath + "server.lock");
        File inputFile = new File(Test.contentInputPath + "example1.txt");
        AtomFeed originalFeed = new AtomFeed(inputFile);

        AggregationServer aggregationServer = new AggregationServer(Test.port);
        ContentServer contentServer = new ContentServer(Test.host, Test.port, backupFile);
        GETClient client = new GETClient(Test.host, Test.port);

        new Thread(aggregationServer).start();

        try {
            contentServer.put(inputFile);
            HTTPResponse response = contentServer.put(inputFile);

            System.out.println("✔ Successfully connected to AggregationServer");
            System.out.println("✔ Response received");
            if (response.statusCode == 200)
                System.out.println("✔ Response status code is 200");
            else
                System.out.println("✗ Response status code is not 200");
        }
        catch (ConnectException e) {
            System.out.println("✗ Couldn't connect to AggregationServer");
            System.exit(1);
        }
        catch (SocketTimeoutException e) {
            System.out.println("✗ Timed out while waiting for response");
            System.exit(1);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        try {
            HTTPResponse response = client.get();
            AtomFeed newFeed = Common.unmarshalXML(response.body);

            List<String> originalIds = originalFeed.getEntries()
                .stream()
                .map(entry -> entry.getId())
                .collect(Collectors.toList());

            List<String> newIds = newFeed.getEntries()
                .stream()
                .map(entry -> entry.getId())
                .collect(Collectors.toList());

            if (originalIds.size() == newIds.size()) {
                System.out.println("✔ Aggregated feed has same entry count as ContentServer feed");
            }
            else {
                System.out.println("✗ Aggregated feed has different entry count to ContentServer feed");
                System.exit(1);
            }

            boolean entriesMatch = newIds.stream()
                .map(id -> originalIds.contains(id))
                .allMatch(val -> val == true);

            if (entriesMatch) {
                System.out.println("✔ Aggregated feed contains all entries from ContentServer feed");
            }
            else {
                System.out.println("✗ Aggregated feed doesn't contain all entries from ContentServer feed");
                System.exit(1);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        aggregationServer.stop();
        aggregationServer.cleanBackup();
        contentServer.stop();
        contentServer.cleanBackup();
    }

    private static void testMultipleContentServerPUTs() {
        System.out.println("Testing multiple ContentServer PUTs to AggregationServer...");

        File backupFile1 = new File(Test.contentBackupPath + "server1.lock");
        File backupFile2 = new File(Test.contentBackupPath + "server2.lock");
        File inputFile1 = new File(Test.contentInputPath + "example1.txt");
        File inputFile2 = new File(Test.contentInputPath + "example2.txt");
        AtomFeed originalFeed1 = new AtomFeed(inputFile1);
        AtomFeed originalFeed2 = new AtomFeed(inputFile2);

        AggregationServer aggregationServer = new AggregationServer(Test.port);
        ContentServer contentServer1 = new ContentServer(Test.host, Test.port, backupFile1);
        ContentServer contentServer2 = new ContentServer(Test.host, Test.port, backupFile2);
        GETClient client = new GETClient(Test.host, Test.port);

        new Thread(aggregationServer).start();

        try {
            HTTPResponse response1 = contentServer1.put(inputFile1);
            HTTPResponse response2 = contentServer2.put(inputFile2);

            System.out.println("✔ Successfully connected to AggregationServer");
            System.out.println("✔ Responses received");

            if (response1.statusCode == 201) {
                System.out.println("✔ Response 1 status code is 201");
            }
            else {
                System.out.println("✗ Response 1 status code is not 201");
                System.exit(1);
            }

            if (response2.statusCode == 201) {
                System.out.println("✔ Response 2 status code is 201");
            }
            else {
                System.out.println("✗ Response 2 status code is not 201");
                System.exit(1);
            }
        }
        catch (ConnectException e) {
            System.out.println("✗ Couldn't connect to AggregationServer");
            System.exit(1);
        }
        catch (SocketTimeoutException e) {
            System.out.println("✗ Timed out while waiting for responses");
            System.exit(1);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        try {
            HTTPResponse response = client.get();
            AtomFeed newFeed = Common.unmarshalXML(response.body);

            ArrayList<String> originalIds = new ArrayList<>();

            originalFeed1.getEntries()
                .stream()
                .forEach(entry -> originalIds.add(entry.getId()));

            originalFeed2.getEntries()
                .stream()
                .forEach(entry -> originalIds.add(entry.getId()));

            List<String> newIds = newFeed.getEntries()
                .stream()
                .map(entry -> entry.getId())
                .collect(Collectors.toList());

            if (originalIds.size() == newIds.size()) {
                System.out.println("✔ Aggregated feed has same entry count as both ContentServer feeds combined");
            }
            else {
                System.out.println("✗ Aggregated feed has different entry count to both ContentServer feeds combined");
                System.exit(1);
            }

            boolean entriesMatch = true;
            for (int i = 0; i < originalIds.size(); i++) {
                if (!originalIds.get(i).equals(newIds.get(i)))
                    entriesMatch = false;
            }

            if (entriesMatch) {
                System.out.println("✔ Aggregated feed contains all entries from both ContentServers, in order");
            }
            else {
                System.out.println("✗ Aggregated feed missing entries from one or both ContentServers, or entries out of order");
                System.exit(1);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        aggregationServer.stop();
        aggregationServer.cleanBackup();
        contentServer1.stop();
        contentServer1.cleanBackup();
        contentServer2.stop();
        contentServer2.cleanBackup();
    }

    private static void testAggregationJanitor() {
        System.out.println("Testing AggregationJanitor...");

        File backupFile1 = new File(Test.contentBackupPath + "server1.lock");
        File backupFile2 = new File(Test.contentBackupPath + "server2.lock");
        File inputFile1 = new File(Test.contentInputPath + "example1.txt");
        File inputFile2 = new File(Test.contentInputPath + "example2.txt");
        AtomFeed originalFeed1 = new AtomFeed(inputFile1);
        AtomFeed originalFeed2 = new AtomFeed(inputFile2);

        AggregationServer aggregationServer = new AggregationServer(Test.port);
        ContentServer contentServer1 = new ContentServer(Test.host, Test.port, backupFile1);
        ContentServer contentServer2 = new ContentServer(Test.host, Test.port, backupFile2);
        GETClient client = new GETClient(Test.host, Test.port);

        new Thread(aggregationServer).start();

        try {
            contentServer1.put(inputFile1);
            contentServer2.put(inputFile2);

            System.out.println("✔ Waiting to ensure AggregationJanitor doesn't prematurely remove feeds");
            Thread.sleep(Common.AGGREGATION_JANITOR_CLEANUP_INTERVAL + 2000);

            HTTPResponse response1 = client.get();
            AtomFeed newFeed1 = Common.unmarshalXML(response1.body);

            List<String> originalIds1 = originalFeed1.getEntries()
                .stream()
                .map(entry -> entry.getId())
                .collect(Collectors.toList());

            List<String> originalIds2 = originalFeed2.getEntries()
                .stream()
                .map(entry -> entry.getId())
                .collect(Collectors.toList());

            List<String> newIds1 = newFeed1.getEntries()
                .stream()
                .map(entry -> entry.getId())
                .collect(Collectors.toList());

            if (originalIds1.size() + originalIds2.size() == newIds1.size()) {
                System.out.println("✔ ContentHeartbeat kept records fresh on AggregationServer");
                System.out.println("✔ Initial aggregated feed has same entry count as both ContentServer feeds combined");
            }
            else {
                System.out.println("✗ Initial aggregated feed has different entry count to both ContentServer feeds combined");
                System.exit(1);
            }

            boolean entriesMatch1 = newIds1.stream()
                .map(id -> originalIds1.contains(id) || originalIds2.contains(id))
                .allMatch(val -> val == true);

            if (entriesMatch1) {
                System.out.println("✔ Initial aggregated feed contains all entries from both ContentServer feeds");
            }
            else {
                System.out.println("✗ Initial aggregated feed doesn't contain all entries from both ContentServer feeds");
                System.exit(1);
            }

            System.out.println("✔ Stopping ContentServer 1...");
            contentServer1.stop();
            contentServer1.cleanBackup();

            System.out.println("✔ Waiting for AggregationJanitor to remove ContentServer 1 entries...");
            Thread.sleep(Common.AGGREGATION_JANITOR_EXPIRATION_THRESHOLD + 2000);

            HTTPResponse response2 = client.get();
            AtomFeed newFeed2 = Common.unmarshalXML(response2.body);
            List<String> newIds2 = newFeed2.getEntries()
                .stream()
                .map(entry -> entry.getId())
                .collect(Collectors.toList());

            if (originalIds2.size() == newIds2.size()) {
                System.out.println("✔ New aggregated feed has same number of entries as ContentServer 2 feed");
            }
            else {
                System.out.println("✗ New aggregated feed and ContentServer 2 feed have different entry counts");
                System.exit(1);
            }

            boolean entriesMatch2 = newIds2.stream()
                .map(id -> !originalIds1.contains(id) && originalIds2.contains(id))
                .allMatch(val -> val == true);

            if (entriesMatch2) {
                System.out.println("✔ New aggregated feed contains all entries from ContentServer 2, none from ContentServer 1");
            }
            else {
                System.out.println("✗ New aggregated feed missing entries from ContentServer 2 or contains entries from ContentServer 1");
                System.exit(1);
            }

        }
        catch (Exception e) {
            e.printStackTrace();
        }

        aggregationServer.stop();
        aggregationServer.cleanBackup();
        contentServer2.stop();
        contentServer2.cleanBackup();
    }
}
