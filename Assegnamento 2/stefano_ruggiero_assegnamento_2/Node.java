// File: Node.java
package stefano_ruggiero_assegnamento_2;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.AsynchronousCloseException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Nodo partecipante alla rete distribuita:
 * <ul>
 *   <li>Barrier (REGISTER / START)</li>
 *   <li>Centralized queue-based mutual exclusion</li>
 *   <li>Failure detection tramite heartbeat UDP</li>
 *   <li>Bully election algorithm</li>
 *   <li>Coordinatore alternativo se necessario</li>
 * </ul>
 */
public class Node {

    /** Identificatore univoco del nodo. */
    private final int nodeId;
    /** Orologio logico di Lamport per ordinamento eventi. */
    private int lamport = 0;
    /** Contatore delle risorse acquisite. */
    private int acquired = 0;
    /** Timestamp dell’ultimo HEARTBEAT ricevuto. */
    private volatile long lastHeartbeat = System.currentTimeMillis();
    /** Insieme di risposte OK ricevute durante l’elezione Bully. */
    private final Set<Integer> okReplies =
        Collections.synchronizedSet(new HashSet<>());

    private UDPMessenger udp;
    private volatile boolean amICoordinator = false;
    private LinkedBlockingQueue<RequestEntry> queue;
    private ServerSocket myServer;
    private ScheduledExecutorService mainScheduler;
    private ScheduledExecutorService coordScheduler;

    /**
     * Entry della coda di richieste per il coordinatore alternativo.
     */
    private static class RequestEntry {
        final Socket             client;
        final ObjectOutputStream out;
        final int                senderId;

        /**
         * Costruisce una entry per la coda FIFO.
         *
         * @param client   socket del client
         * @param out      stream di output verso il client
         * @param senderId ID del nodo mittente
         */
        RequestEntry(Socket client, ObjectOutputStream out, int senderId) {
            this.client   = client;
            this.out      = out;
            this.senderId = senderId;
        }
    }

    /**
     * Costruisce un nuovo nodo.
     *
     * @param nodeId identificatore del nodo
     */
    public Node(int nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * Punto di ingresso per lanciare un nodo.
     *
     * @param args args[0] = nodeId
     * @throws Exception in caso di errori UDP o I/O
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java Node <nodeId>");
            System.exit(1);
        }
        int id = Integer.parseInt(args[0]);
        System.out.println("Node " + id + ": starting");
        new Node(id).start();
    }

    /**
     * Avvia:
     * <ol>
     *   <li>Barrier (REGISTER/START)</li>
     *   <li>Scheduling failure detection ed elezione</li>
     *   <li>Ciclo di mutua esclusione centralizzata</li>
     * </ol>
     *
     * @throws Exception se errore UDP o I/O
     */
    private void start() throws Exception {
        udp = new UDPMessenger();
        // Barrier iniziale
        udp.send(new Message(Message.Type.REGISTER, nodeId, lamport));
        System.out.printf("[Node %2d] UDP SEND  REGISTER%n", nodeId);
        while (true) {
            Message m = udp.receive();
            if (m.getType() == Message.Type.START) {
                System.out.printf("[Node %2d] UDP RECV  START%n", nodeId);
                break;
            }
        }

        // Scheduler per heartbeat timeout e listener UDP
        mainScheduler = Executors.newScheduledThreadPool(2);
        mainScheduler.scheduleAtFixedRate(
            this::checkCoordinator,
            Config.HEARTBEAT_TIMEOUT_MS,
            Config.HEARTBEAT_TIMEOUT_MS,
            TimeUnit.MILLISECONDS
        );
        mainScheduler.execute(this::listenUDP);

        // Ciclo di mutua esclusione
        resourceLoop();

        // Cleanup
        if (coordScheduler != null) coordScheduler.shutdownNow();
        mainScheduler.shutdownNow();
        udp.close();
        System.out.printf("Node %2d: acquired %d/%d → terminating%n",
                          nodeId, acquired, Config.RESOURCE_TARGET);
        System.exit(0);
    }

    /**
     * Listener UDP: gestisce HEARTBEAT, ELECTION, OK, VICTORY e DONE.
     */
    private void listenUDP() {
        try {
            while (true) {
                Message m = udp.receive();
                switch (m.getType()) {
                    case HEARTBEAT -> {
                        lastHeartbeat = System.currentTimeMillis();
                        System.out.printf("[Node %2d] UDP RECV  HEARTBEAT%n", nodeId);
                    }
                    case ELECTION -> {
                        System.out.printf("[Node %2d] UDP RECV  ELECTION from %2d%n",
                            nodeId, m.getSenderId());
                        if (m.getSenderId() < nodeId) {
                            udp.send(new Message(Message.Type.OK, nodeId, ++lamport));
                            System.out.printf("[Node %2d] UDP SEND  OK to %2d%n",
                                nodeId, m.getSenderId());
                            startElection();
                        }
                    }
                    case OK -> {
                        okReplies.add(m.getSenderId());
                        System.out.printf("[Node %2d] UDP RECV  OK from %2d%n",
                            nodeId, m.getSenderId());
                    }
                    case VICTORY -> {
                        System.out.printf("[Node %2d] UDP RECV  VICTORY from %2d%n",
                            nodeId, m.getSenderId());
                        if (m.getSenderId() == nodeId) becomeCoordinator();
                        lastHeartbeat = System.currentTimeMillis();
                        okReplies.clear();
                    }
                    case DONE -> {
                        System.out.printf("[Node %2d] UDP RECV  DONE, exiting%n", nodeId);
                        System.exit(0);
                    }
                    default -> { }
                }
            }
        } catch (SocketException | AsynchronousCloseException ignore) {
        } catch (Exception e) {
            System.err.printf("[Node %2d] listenUDP error:%n", nodeId);
            e.printStackTrace();
        }
    }

    /**
     * Verifica timeout dell’ultimo heartbeat e, se scaduto,
     * avvia l’algoritmo Bully per eleggere un nuovo coordinatore.
     */
    private void checkCoordinator() {
        if (!amICoordinator &&
            System.currentTimeMillis() - lastHeartbeat > Config.HEARTBEAT_TIMEOUT_MS) {
            System.out.printf("[Node %2d] timeout HEARTBEAT → starting election%n", nodeId);
            startElection();
        }
    }

    /**
     * Avvia Bully election: invia ELECTION, attende OK,
     * se non ne riceve allora invia VICTORY e diventa coordinatore.
     */
    private void startElection() {
        okReplies.clear();
        try {
            udp.send(new Message(Message.Type.ELECTION, nodeId, ++lamport));
            System.out.printf("[Node %2d] UDP SEND  ELECTION%n", nodeId);
        } catch (IOException ignore) {}
        try { Thread.sleep(Config.ELECTION_TIMEOUT_MS); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        if (okReplies.isEmpty()) {
            System.out.printf("[Node %2d] no OK → proclaiming self%n", nodeId);
            amICoordinator = true;
            try {
                udp.send(new Message(Message.Type.VICTORY, nodeId, ++lamport));
                System.out.printf("[Node %2d] UDP SEND  VICTORY%n", nodeId);
            } catch (IOException ignore) {}
            becomeCoordinator();
        }
    }

    /**

     * Diventa coordinatore alternativo: avvia heartbeat e server TCP.
     */
    private void becomeCoordinator() {
        System.out.printf("[Node %2d] becoming new coordinator%n", nodeId);
        queue = new LinkedBlockingQueue<>();
        coordScheduler = Executors.newSingleThreadScheduledExecutor();
        coordScheduler.scheduleAtFixedRate(() -> {
            try {
                udp.send(new Message(Message.Type.HEARTBEAT, nodeId, ++lamport));
                System.out.printf("[Node %2d] UDP SEND  HEARTBEAT%n", nodeId);
            } catch (IOException e) {
                coordScheduler.shutdownNow();
            }
        }, 0, Config.HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        new Thread(() -> {
            try (ServerSocket srv = new ServerSocket(Config.TCP_PORT)) {
                myServer = srv;
                System.out.printf("[Node %2d] NewCoord TCP listening on %d%n",
                    nodeId, Config.TCP_PORT);
                while (true) {
                    Socket client = srv.accept();
                    new Thread(() -> handleClient(client)).start();
                }
            } catch (IOException e) {
                System.out.printf("[Node %2d] NewCoord server closed%n", nodeId);
            }
        }).start();
    }

    /**
     * Gestisce REQUEST e RELEASE in qualità di coordinatore alternativo.
     *
     * @param client socket TCP del nodo richiedente
     */
    private void handleClient(Socket client) {
        try (ObjectInputStream in = new ObjectInputStream(client.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream())) {
            while (true) {
                Message m = (Message) in.readObject();
                System.out.printf("[Node %2d][NewCoord TCP RECV] %-7s from %2d%n",
                    nodeId, m.getType(), m.getSenderId());
                if (m.getType() == Message.Type.REQUEST) {
                    queue.put(new RequestEntry(client, out, m.getSenderId()));
                    processNext();
                } else if (m.getType() == Message.Type.RELEASE) {
                    System.out.printf("[Node %2d] NewCoord: RELEASE from %2d%n",
                        nodeId, m.getSenderId());
                    processNext();
                }
            }
        } catch (EOFException eof) {
        } catch (IOException | ClassNotFoundException e) {
            System.err.printf("[Node %2d] handleClient error:%n", nodeId);
            e.printStackTrace();
        }
    }

    /**
     * Estrae la prossima richiesta e invia GRANT al client.
     *
     * @throws IOException se errore I/O sul client
     */
    private synchronized void processNext() throws IOException {
        RequestEntry e = queue.poll();
        if (e != null) {
            e.out.writeObject(new Message(Message.Type.GRANT, nodeId, ++lamport));
            e.out.flush();
            System.out.printf("[Node %2d] NewCoord TCP SEND  GRANT to %2d%n",
                nodeId, e.senderId);
        }
    }

    /**
     * Ciclo di mutua esclusione: invia REQUEST, attende GRANT,
     * simula sezione critica, invia RELEASE e incrementa contatore.
     */
    private void resourceLoop() {
        while (acquired < Config.RESOURCE_TARGET) {
            boolean success = false;
            while (!success) {
                try (Socket tcp = new Socket("localhost", Config.TCP_PORT);
                     ObjectOutputStream out =
                         new ObjectOutputStream(tcp.getOutputStream());
                     ObjectInputStream in =
                         new ObjectInputStream(tcp.getInputStream())) {

                    System.out.printf("[Node %2d] TCP CONNECT → sending REQUEST%n", nodeId);
                    out.writeObject(new Message(Message.Type.REQUEST, nodeId, ++lamport));
                    out.flush();

                    Message m = (Message) in.readObject();
                    System.out.printf("[Node %2d] TCP RECV  %s%n",
                        nodeId, m.getType());

                    if (m.getType() == Message.Type.GRANT) {
                        Thread.sleep(100);
                        out.writeObject(new Message(Message.Type.RELEASE,
                            nodeId, ++lamport));
                        out.flush();
                        System.out.printf("[Node %2d] TCP SEND  RELEASE%n", nodeId);
                        success = true;
                    }
                } catch (Exception e) {
                    System.out.printf("[Node %2d] resourceLoop errore: %s → riprovo%n",
                        nodeId, e.getClass().getSimpleName());
                    try {
                        Thread.sleep(Config.ELECTION_TIMEOUT_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            acquired++;
            System.out.printf("[Node %2d] acquired %d/%d%n",
                nodeId, acquired, Config.RESOURCE_TARGET);
        }
    }
}
