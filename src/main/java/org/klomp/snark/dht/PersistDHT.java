package org.klomp.snark.dht;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import org.klomp.snark.data.DataFormatException;
import org.klomp.snark.spi.Log;
import org.klomp.snark.spi.Logs;
import org.klomp.snark.spi.Storage;

/**
 *  Retrieve / Store the local DHT in a file.
 *  Port of I2PSnark's PersistDHT, abstracted over {@link Storage}.
 *
 *  @since 0.9.2
 */
abstract class PersistDHT {

    private static final long MAX_AGE = 60*60*1000;

    /**
     *  @param backupFile may be null
     *  @since 0.9.6
     */
    public static synchronized void loadDHT(KRPC krpc, Storage storage, String file, String backupFile) {
        if (storage.exists(file))
            loadDHT(krpc, storage, file);
        else if (backupFile != null && storage.exists(backupFile))
            loadDHT(krpc, storage, backupFile);
    }

    public static synchronized void loadDHT(KRPC krpc, Storage storage, String file) {
        Log log = Logs.getLog(PersistDHT.class);
        int count = 0;
        BufferedReader br = null;
        try {
            InputStream is = storage.open(file);
            br = new BufferedReader(new InputStreamReader(is, "ISO-8859-1"));
            String line = null;
            while ( (line = br.readLine()) != null) {
                if (line.startsWith("#"))
                    continue;
                try {
                    krpc.heardAbout(new NodeInfo(line, krpc.getIdentityFactory()));
                    count++;
                    // TODO limit number? this will flush the router's SDS caches
                } catch (IllegalArgumentException iae) {
                    if (log.shouldLog(Log.WARN))
                        log.warn("Error reading DHT entry", iae);
                } catch (DataFormatException dfe) {
                    if (log.shouldLog(Log.WARN))
                        log.warn("Error reading DHT entry", dfe);
                }
            }
        } catch (IOException ioe) {
            if (log.shouldLog(Log.WARN) && storage.exists(file))
                log.warn("Error reading the DHT File", ioe);
        } finally {
            if (br != null) try { br.close(); } catch (IOException ioe) {}
        }
        if (log.shouldLog(Log.INFO))
            log.info("Loaded " + count + " nodes from " + file);
    }

    /**
     *  @param saveAll if true, don't check last seen time
     */
    public static synchronized void saveDHT(DHTNodes nodes, boolean saveAll, Storage storage, String file) {
        if (nodes.size() <= 0)
            return;
        Log log = Logs.getLog(PersistDHT.class);
        int count = 0;
        long maxAge = saveAll ? 0 : org.klomp.snark.spi.Clock.getInstance().now() - MAX_AGE;
        PrintWriter out = null;
        try {
            OutputStream os = storage.create(file);
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, "ISO-8859-1")));
            out.println("# DHT nodes, format is NID:Hash:Destination:port");
            for (NodeInfo ni : nodes.values()) {
                 if (ni.lastSeen() < maxAge)
                     continue;
                 // DHTNodes shouldn't contain us, if that changes check here
                 out.println(ni.toPersistentString());
                 count++;
            }
            if (out.checkError())
                throw new IOException("Failed write to " + file);
        } catch (IOException ioe) {
            if (log.shouldLog(Log.WARN))
                log.warn("Error writing the DHT File", ioe);
        } finally {
            if (out != null) out.close();
        }
        if (log.shouldLog(Log.INFO))
            log.info("Stored " + count + " nodes to " + file);
    }
}
