// Node.java
package stefano_ruggiero_assegnamento_3;

import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Nodo partecipante all’algoritmo di snapshot distribuito Chandy-Lamport.
 * Gestisce invio/ricezione di TASK, MARKER e cattura lo stato locale e dei canali.
 */
public class Node {

    private static final DateTimeFormatter TSTAMP =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int id;                        // ID di questo nodo
    private final int nodes;                     // numero totale di nodi
    private final int basePort;                  // porta base per connessioni
    private ServerSocket serverSocket;           // socket per accettare peer

    private final Map<Integer,ObjectOutputStream> outs   = new ConcurrentHashMap<>();
    private final ExecutorService pool                  = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler    = Executors.newSingleThreadScheduledExecutor();
    private final Random rnd                             = new Random();
    private final Object sendLock                        = new Object();

    private int localCounter       = 0;    // contatore locale di TASK
    private volatile boolean recording = false;
    private String currentSnapshotId    = null;
    private final Set<Integer> markerReceived           = ConcurrentHashMap.newKeySet();
    private final Map<Integer,List<Message>> channelBuf = new ConcurrentHashMap<>();

    private int snapshotCount         = 0;
    private final int targetSnapshots;   // se >0, termina dopo questo num di snapshot

    /**
     * Costruisce un nuovo nodo.
     *
     * @param id              ID del nodo
     * @param nodes           numero totale dei nodi
     * @param basePort        porta base per le connessioni (basePort + id)
     * @param targetSnapshots se >0, shutdown dopo questo numero di snapshot
     */
    public Node(int id, int nodes, int basePort, int targetSnapshots) {
        this.id              = id;
        this.nodes           = nodes;
        this.basePort        = basePort;
        this.targetSnapshots = targetSnapshots;
    }

    /**
     * Avvia il nodo:
     * <ol>
     *   <li>ServerSocket per accettare connessioni</li>
     *   <li>Connessione verso gli altri peer</li>
     *   <li>Generazione continua di TASK</li>
     *   <li>Pianificazione di snapshot casuali</li>
     * </ol>
     *
     * @throws IOException se non riesce ad aprire il ServerSocket
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(basePort + id);
        pool.execute(() -> {
            try {
                while (true) {
                    Socket s = serverSocket.accept();
                    initPeer(-1, s);
                }
            } catch (IOException e) {
                log("ERROR", "Server accept failed: " + e.getMessage());
            }
        });

        for (int peer = 0; peer < nodes; peer++) {
            if (peer == id) continue;
            final int dest = peer;
            pool.execute(() -> {
                while (true) {
                    try {
                        initPeer(dest, new Socket("localhost", basePort + dest));
                        return;
                    } catch (IOException e) {
                        sleep(250);
                    }
                }
            });
        }

        pool.execute(this::taskGenerator);
        scheduleNextSnapshot();
    }

    /**
     * Inizializza la comunicazione con un peer.
     *
     * @param dest ID del peer (–1 se non ancora identificato)
     * @param s    socket già connessa
     * @throws IOException in caso di errore di I/O
     */
    private void initPeer(int dest, Socket s) throws IOException {
        ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
        ObjectInputStream  in  = new ObjectInputStream(s.getInputStream());
        if (dest >= 0) {
            outs.put(dest, out);
            sendLocked(dest, new Message(Message.Type.TASK, id, "HELLO", null));
        }
        pool.execute(() -> {
            int peerId = dest;
            try {
                while (true) {
                    Message m = (Message) in.readObject();
                    if (peerId < 0) {
                        peerId = m.getSenderId();
                        outs.putIfAbsent(peerId, out);
                    }
                    handleIncoming(peerId, m);
                }
            } catch (Exception e) {
                log("ERROR", "Connection to peer " + peerId + " broken: " + e.getMessage());
            }
        });
    }

    /**
     * Gestisce un messaggio ricevuto da un peer.
     *
     * @param peerId ID del peer mittente
     * @param m      messaggio ricevuto
     */
    private synchronized void handleIncoming(int peerId, Message m) {
        if (m.getType() == Message.Type.MARKER) {
            onMarker(peerId, m.getSnapshotId());
        } else {
            if (recording && !markerReceived.contains(peerId)) {
                channelBuf.computeIfAbsent(peerId, k -> new ArrayList<>()).add(m);
            }
            localCounter++;
            log("TASK", String.format("da %d %s (ctr=%d)", peerId, m, localCounter));
        }
    }

    /**
     * Gestisce la ricezione di un MARKER.
     *
     * @param from   ID del peer che ha inviato il marker
     * @param snapId ID dello snapshot
     */
    private synchronized void onMarker(int from, String snapId) {
        log("MARK", String.format("da %d snap=%s", from, snapId));
        if (!recording) {
            log("SNAP", "reagisce a snapshot " + snapId);
            recording = true;
            currentSnapshotId = snapId;
            markerReceived.clear();
            channelBuf.clear();
            log("SNAP", "stato locale ctr=" + localCounter);
            broadcastMarker(snapId);
        }
        if (!snapId.equals(currentSnapshotId)) return;
        markerReceived.add(from);
        if (markerReceived.size() == nodes - 1) {
            finishSnapshot();
        }
    }

    /**
     * Completa lo snapshot corrente:
     * stampa i buffer dei canali, resetta lo stato di recording
     * e, se raggiunto il target, effettua lo shutdown.
     */
    private void finishSnapshot() {
        log("SNAP", "=== GLOBAL SNAPSHOT " + currentSnapshotId + " COMPLETATO ===");
        for (int peer = 0; peer < nodes; peer++) {
            if (peer == id) continue;
            List<Message> buf = channelBuf.getOrDefault(peer, Collections.emptyList());
            log("SNAP", String.format("channel[%d] = %s", peer, buf));
        }
        recording = false;
        currentSnapshotId = null;
        markerReceived.clear();
        channelBuf.clear();

        snapshotCount++;
        if (targetSnapshots > 0 && snapshotCount >= targetSnapshots) {
            log("CTRL", "raggiunti " + snapshotCount + " snapshot, terminazione automatica");
            shutdown();
            System.exit(0);
        }
    }

    /**
     * Invia un messaggio al peer in modo thread-safe.
     *
     * @param dest ID del peer destinatario
     * @param m    messaggio da inviare
     * @throws IOException se l’output stream non è disponibile
     */
    private void sendLocked(int dest, Message m) throws IOException {
        synchronized (sendLock) {
            ObjectOutputStream o = outs.get(dest);
            if (o != null) { o.writeObject(m); o.flush(); }
        }
    }

    /**
     * Invia a tutti i peer un MARKER per iniziare lo snapshot.
     *
     * @param snapId ID dello snapshot
     */
    private void broadcastMarker(String snapId) {
        synchronized (sendLock) {
            Message m = new Message(Message.Type.MARKER, id, "", snapId);
            outs.values().forEach(o -> {
                try { o.writeObject(m); o.flush(); }
                catch (IOException e) { log("ERROR", "Broadcast marker failed: " + e.getMessage()); }
            });
        }
    }

    /**
     * Generatore continuo di TASK che invia messaggi
     * a peer scelti casualmente ogni 500-1000 ms.
     */
    private void taskGenerator() {
        while (true) {
            sleep(500 + rnd.nextInt(500));
            int dest = rnd.nextInt(nodes);
            if (dest != id) {
                try {
                    sendLocked(dest, new Message(
                        Message.Type.TASK, id, "task-" + UUID.randomUUID(), null));
                } catch (IOException e) {
                    log("ERROR", "Task send failed: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Pianifica il prossimo snapshot dopo un ritardo casuale
     * compreso tra 2 e 10 secondi.
     */
    private void scheduleNextSnapshot() {
        int delay = 2 + rnd.nextInt(8);
        scheduler.schedule(() -> {
            String snapId = UUID.randomUUID().toString();
            log("SNAP", "avvia snapshot " + snapId);
            initiateSnapshot(snapId);
            scheduleNextSnapshot();
        }, delay, TimeUnit.SECONDS);
    }

    /**
     * Avvia manualmente uno snapshot, se non già in corso.
     *
     * @param snapId ID univoco dello snapshot
     */
    public synchronized void initiateSnapshot(String snapId) {
        if (recording) return;
        recording = true;
        currentSnapshotId = snapId;
        markerReceived.clear();
        channelBuf.clear();
        log("SNAP", "stato locale ctr=" + localCounter);
        broadcastMarker(snapId);
    }

    /**
     * Chiude socket, stream, scheduler e thread pool.
     */
    public void shutdown() {
        log("CTRL", "shutdown iniziato");
        try { serverSocket.close(); } catch (Exception ignored) {}
        outs.values().forEach(o -> {
            try { o.close(); } catch (Exception ignored) {}
        });
        scheduler.shutdownNow();
        pool.shutdownNow();
        log("CTRL", "thread pool e scheduler terminati");
    }

    /**
     * Stampa un log formattato con timestamp, livello e ID nodo.
     *
     * @param level livello di log (TASK, MARK, SNAP, ERROR, CTRL)
     * @param msg   messaggio di dettaglio
     */
    private void log(String level, String msg) {
        String time = LocalTime.now().format(TSTAMP);
        System.out.printf("%s [%s] Nodo %d: %s%n", time, level, id, msg);
    }

    /**
     * Esegue Thread.sleep ignorando InterruptedException.
     *
     * @param ms millisecondi di pausa
     */
    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
