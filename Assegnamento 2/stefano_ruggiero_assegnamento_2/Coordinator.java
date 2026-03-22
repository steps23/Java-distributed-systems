// File: Coordinator.java
package stefano_ruggiero_assegnamento_2;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Coordinatore centrale che gestisce:
 * <ul>
 *   <li>Barrier iniziale (REGISTER → START)</li>
 *   <li>Centralized queue-based mutual exclusion</li>
 *   <li>Failure detection con heartbeat e crash simulato</li>
 *   <li>Broadcast DONE a fine distribuzione risorse</li>
 * </ul>
 */
public class Coordinator {

    /**
     * Entry della coda di richieste REQUEST/RELEASE.
     */
    private static class RequestEntry {
        final Socket             client;
        final ObjectOutputStream out;
        final int                senderId;

        /**
         * Costruisce una entry per la coda.
         *
         * @param client   socket TCP del client
         * @param out      stream di output verso il client
         * @param senderId ID del nodo mittente
         */
        RequestEntry(Socket client, ObjectOutputStream out, int senderId) {
            this.client   = client;
            this.out      = out;
            this.senderId = senderId;
        }
    }

    private final BlockingQueue<RequestEntry> queue = new LinkedBlockingQueue<>();
    private final Random rand = new Random();
    private volatile boolean running = true;
    private int grantsCount = 0;
    private UDPMessenger udp;
    private ServerSocket server;

    /**
     * Punto di ingresso del coordinatore.
     *
     * @param args non utilizzati
     * @throws Exception in caso di errori di I/O o rete
     */
    public static void main(String[] args) throws Exception {
        new Coordinator().start();
    }

    /**
     * Avvia barrierPhase → startHeartbeat → startTCPServer.
     *
     * @throws Exception in caso di errori UDP o TCP
     */
    private void start() throws Exception {
        udp = new UDPMessenger();
        barrierPhase();
        startHeartbeat();
        startTCPServer();
    }

    /**
     * Barrier iniziale: attende REGISTER da tutti i nodi
     * e invia START a ciascuno.
     *
     * @throws IOException            se errore I/O UDP
     * @throws ClassNotFoundException mai sollevata qui
     */
    private void barrierPhase() throws IOException, ClassNotFoundException {
        System.out.println("Coordinator: in attesa di REGISTER da "
            + Config.EXPECTED_NODES + " nodi...");
        int registered = 0;
        while (registered < Config.EXPECTED_NODES) {
            Message m = udp.receive();
            if (m.getType() == Message.Type.REGISTER) {
                registered++;
                System.out.printf("  > ricevuto REGISTER da nodo %2d (%d/%d)%n",
                    m.getSenderId(), registered, Config.EXPECTED_NODES);
            }
        }
        System.out.println("Coordinator: tutti registrati, mando START");
        for (int i = 0; i < Config.EXPECTED_NODES; i++) {
            udp.send(new Message(Message.Type.START, 0, 0));
        }
    }

    /**
     * Invio periodico di HEARTBEAT. Con probabilità
     * CRASH_PROBABILITY simula crash.
     */
    private void startHeartbeat() {
        ScheduledExecutorService ses =
            Executors.newSingleThreadScheduledExecutor();
        ses.scheduleAtFixedRate(() -> {
            try {
                if (rand.nextDouble() < Config.CRASH_PROBABILITY) {
                    System.out.println("Coordinator: *** CRASH simulato ***");
                    running = false;
                    udp.close();
                    if (server != null && !server.isClosed()) server.close();
                    ses.shutdown();
                } else {
                    udp.send(new Message(Message.Type.HEARTBEAT, 0, 0));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, Config.HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Avvia il server TCP per REQUEST/RELEASE.
     *
     * @throws IOException se errore creazione server
     */
    private void startTCPServer() throws IOException {
        server = new ServerSocket(Config.TCP_PORT);
        System.out.println("Coordinator: TCP listening on port "
            + Config.TCP_PORT);
        try {
            while (running) {
                Socket client = server.accept();
                new Thread(() -> handleClient(client)).start();
            }
        } catch (SocketException se) {
            System.out.println("Coordinator: server socket chiuso per crash simulato");
        }
    }

    /**
     * Gestisce REQUEST e RELEASE da un client TCP.
     *
     * @param client socket TCP del client
     */
    private void handleClient(Socket client) {
        try (ObjectInputStream in = new ObjectInputStream(client.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream())) {
            while (running) {
                Message m = (Message) in.readObject();
                System.out.printf("[TCP RECV] %-7s from node %2d%n",
                    m.getType(), m.getSenderId());

                if (m.getType() == Message.Type.REQUEST) {
                    queue.put(new RequestEntry(client, out, m.getSenderId()));
                    processNext();
                } else if (m.getType() == Message.Type.RELEASE) {
                    grantsCount++;
                    System.out.printf("Coordinator: RELEASE da %2d (grants=%d)%n",
                        m.getSenderId(), grantsCount);
                    processNext();
                    if (grantsCount >= Config.EXPECTED_NODES * Config.RESOURCE_TARGET) {
                        udp.send(new Message(Message.Type.DONE, 0, 0));
                        System.out.println("Coordinator: DONE broadcast");
                    }
                }
            }
        } catch (Exception e) {
            // Client disconnesso o errore silenzioso
        }
    }

    /**
     * Estrae la prossima RequestEntry e invia GRANT.
     *
     * @throws IOException se errore I/O verso il client
     */
    private synchronized void processNext() throws IOException {
        RequestEntry e = queue.poll();
        if (e != null) {
            e.out.writeObject(new Message(Message.Type.GRANT, 0, 0));
            e.out.flush();
            System.out.printf("[TCP SEND] GRANT to node %2d%n", e.senderId);
        }
    }
}
