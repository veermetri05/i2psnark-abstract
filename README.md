# i2psnark-abstract

**Implementation-free core of I2PSnark** — the BitTorrent protocol logic
(BEP 9/10 metadata download + the I2P DHT) extracted behind a small SPI,
with **zero** I2P dependencies and **zero** UI code. Runs on the JVM
(desktop) and Android. Pure Java 8, no runtime dependencies.

The security guarantee is architectural: like I2PSnark itself, this code
was born on I2P and contains no clearnet code — there is nothing to leak
and no `#if` guards to get wrong. The abstraction exists so the protocol
logic is reusable without dragging in `net.i2p.*`.

## What's inside

```
org.klomp.snark.spi        — the SPI (all I2P dependencies abstracted)
    Log, LogFactory, Logs      logging (replaceable; console by default)
    Clock, RandomSource,       time/randomness/timers
    Scheduler, Cancellable
    Storage                    file persistence (DHT routing table)
    PeerIdentity               opaque peer address (was: Destination)
    PeerIdentityFactory        deserialize identities
    Session                    local identity + hash lookup (was: I2PSession)
    Stream, StreamConnector    byte streams (was: I2PSocket(Manager))
    StreamServer               inbound acceptor (future full client)
    DatagramTransport          DHT datagrams (was: I2P datagram API)
    DatagramListener
    Tracker                    HTTP tracker abstraction (future work)
    EventBus                   typed listener registry — no polling
    Environment                bundles clock/random/scheduler/storage/logs
                               (was: I2PAppContext)

org.klomp.snark.data        — value types: Hash, SHA1Hash, ByteArray,
                              SimpleDataStructure, Base64, DataHelper,
                              SHA256, DataFormatException
org.klomp.snark.util        — default impls: SystemClock, SecureRandomSource,
                              DefaultScheduler, ConsoleLogFactory,
                              DefaultEnvironment, LHMCache
org.klomp.snark.bencode     — BDecoder/BEncoder/BEValue (pure, unchanged)
org.klomp.snark.kademlia    — KBucket(Set)/KBucketTrimmer routing table
                              (pure port of net.i2p.kademlia)
org.klomp.snark.dht         — the I2P DHT: DHT interface, KRPC, DHTNodes,
                              DHTTracker, NID/NodeInfo/Token/MsgID/...,
                              PersistDHT, DHTEventListener
org.klomp.snark.event       — event model: Event, SimpleEventBus,
                              MetadataEvent/Listener, DhtEvent/Listener,
                              ConnectionEvent/Listener, LogEvent/Listener,
                              BusLogFactory
org.klomp.snark             — MetaInfo (pure torrent parsing),
                              MetadataDownloader (BEP 9/10 client)
```

## The SPI in one picture

| I2P class (gone)            | SPI replacement                  |
|-----------------------------|----------------------------------|
| `I2PAppContext`             | `Environment` + statics          |
| `Destination`               | `PeerIdentity`                   |
| `I2PSocket`                 | `Stream`                         |
| `I2PSocketManager`          | `StreamConnector`                |
| `I2PServerSocket`           | `StreamServer`                   |
| `I2PSession` (datagrams)    | `DatagramTransport`              |
| `session.lookupDest()`      | `Session.lookup(hash, timeout)`  |
| `I2PDatagramMaker/Dissector`| inside the `DatagramTransport` impl |
| `java.io.File` (DHT save)   | `Storage`                        |
| `net.i2p.util.Log`          | `Log` via `Logs.setFactory()`    |
| `SimpleTimer2`              | `Scheduler`                      |
| `net.i2p.util.RandomSource` | `RandomSource`                   |
| `net.i2p.util.Clock`        | `Clock`                          |
| `net.i2p.data.Hash`         | `org.klomp.snark.data.Hash`      |

## Events, not polling

Every component pushes state out through the `EventBus`:

* **`MetadataEvent`** — one per phase change of a metadata download:
  `CONNECTING → CONNECTED → HANDSHAKE → EXTENSION_HANDSHAKE →
  DOWNLOADING (per chunk, with percent/bytes) → VERIFYING → COMPLETE`,
  plus `PEER_FAILED` / `FAILED`. Carries peer identity, progress,
  metadata size, duration, and the parsed `MetaInfo` on completion —
  enough for a live progress bar, status line and detail screen.
* **`DhtEvent`** — DHT health: `STARTED/STOPPED`, `NODE_COUNT` (routing
  table size changes), `NEW_NODE`, `LOOKUP_START/COMPLETE`,
  `BOOTSTRAP_START/COMPLETE`, and infohash discovery
  (`ANNOUNCE_PEER`, `GET_PEERS`, `FIND_NODE`).
* **`ConnectionEvent`** — connection lifecycle, posted by the host
  app's transport layer (CONNECTING/CONNECTED/DISCONNECTED/RECONNECTING).
* **`LogEvent`** — install `BusLogFactory` and every log record (DHT,
  downloader, ...) lands on the bus for an in-app Event Log screen.

Listeners register per type: `bus.register(MetadataListener.class, ui)`.
Dispatch is asynchronous on a daemon thread; failing listeners are
isolated.

## Building

```
./gradlew build                 # compile + tests (13 tests, incl. a full
                                # BEP 9/10 loopback download against a
                                # fake in-memory peer — no I2P needed)
./gradlew publishToMavenLocal   # org.klomp:i2psnark-abstract:0.1.0
```

## Using it

### 1. Provide the SPI (this is the only "implementation" work)

One class in your app wires everything:

```java
// Desktop (i2p_trials / crawler):
Environment env = Environment.basic(new FileStorage(dataDir));
EventBus bus = new SimpleEventBus("crawler");
Logs.setFactory(new BusLogFactory(bus, new ConsoleLogFactory()));
Clock.setInstance(...); RandomSource.setInstance(...); Scheduler.setInstance(...); // optional

// I2P session (your transport adapter — wraps net.i2p.*):
class MyDatagramTransport implements DatagramTransport { ... }
class MyStreamConnector  implements StreamConnector  { ... }
class MyIdentityFactory  implements PeerIdentityFactory { ... } // wrap Destination
```

### 2. Run the DHT

```java
KRPC krpc = new KRPC(env, identityFactory, "appname", datagramTransport);
krpc.setEventBus(bus);                       // → DhtEvents
krpc.setDHTEventListener(myCrawlerListener); // → DHTEventListener (crawler API)
Collection<Hash> peers = krpc.getPeersAndAnnounce(infohash, 30, 120_000, 8, 30_000, false, false);
```

### 3. Download metadata

```java
PeerIdentity dest = streamConnector.lookup(hash.getData(), 10_000);
MetaInfo meta = MetadataDownloader.download(streamConnector, dest, infohash, listener);
// or try several peers:
MetaInfo meta = MetadataDownloader.downloadFromPeers(streamConnector, peers, infohash, listener);
// or fan events to a global bus:
MetadataDownloader.download(connector, dest, ih, MetadataDownloader.toBus(bus));
```

### Android notes

* Java 8 target — consume as a source dependency or via
  `mavenLocal()`; no desugaring needed.
* Install `Logs.setFactory(new BusLogFactory(bus, androidLogger))` for
  the triplex behavior (bus → Log screen, logcat).
* The old Android `MetadataDownloader.ProgressListener` maps to
  `MetadataListener.Adapter` (percent + status string).
* KRPC's Android-specific triplex logging was removed from the core —
  it is replaced by the `Logs` registry, which the app configures once.

### Desktop notes

* `i2p_trials` already lists `mavenLocal()`: add
  `implementation "org.klomp:i2psnark-abstract:0.1.0"` and delete the
  duplicated `org.klomp.snark.*` sources / the `lib/i2psnark.jar`
  dependency for these classes.
* KRPC no longer takes `I2PSession`; wrap your session in a
  `DatagramTransport` (the old `I2CPTransport`/`SAMTransport` classes
  become adapters).

## Scope notes

* **In scope:** metadata download (BEP 3/9/10), the full I2P DHT
  (KRPC + routing table + persistence + HTTP bootstrap), torrent
  parsing, events.
* **Not yet (future SPI surface):** serving torrents (needs
  `StreamServer` + `Storage` + `Tracker` usage), BEP 6/52, µTP,
  web seeds — none of which matter for metadata-only apps, and all
  of which can be added on top of this SPI without touching I2P.

## License

GPLv2 — ported from I2PSnark (`i2p.i2p/apps/i2psnark`, GPLv2) and
I2P core value types / kademlia (public domain).
