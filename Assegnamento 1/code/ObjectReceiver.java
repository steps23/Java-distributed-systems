package stefano_ruggiero_assegnamento_1;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * La classe ObjectReceiver si occupa di ricevere messaggi multicast (UDP)
 * contenenti oggetti serializzati (istanze di {@link Message}).
 *
 * Funzionalità principali:
 * - Tiene traccia, per ogni mittente (nodeId), del messageId atteso successivo
 * - Rileva eventuali gap (mancanza di messaggi) e invia richieste di reinvio
 * - Quando riceve un messaggio di tipo "resend" con senderId uguale a localNodeId,
 *   invoca il {@link ResendListener} per gestirlo.
 */
public class ObjectReceiver {
    /**
     * Indirizzo del gruppo multicast.
     */
    private static final String MULTICAST_ADDRESS = "230.0.0.1";

    /**
     * Porta usata dal gruppo multicast.
     */
    private static final int MULTICAST_PORT = 4446;

    /**
     * Dimensione del buffer per ricevere i pacchetti UDP.
     */
    private static final int BUFFER_SIZE = 1024;

    /**
     * Indica se il receiver è attivo. Viene impostato a false per interrompere la ricezione.
     */
    private volatile boolean running = true;

    /**
     * Socket multicast per la ricezione dei pacchetti.
     */
    private MulticastSocket multicastSocket;

    /**
     * Mappa che registra, per ciascun nodo mittente, il prossimo messageId atteso.
     * Chiave: nodeId del mittente, Valore: messageId atteso.
     */
    private Map<Integer, Integer> expectedMap = new HashMap<>();

    /**
     * Mappa che memorizza, per ciascun nodo mittente, gli ID mancanti già notificati
     * (per evitare duplicati).
     * Chiave: nodeId del mittente, Valore: insieme di messageId mancanti notificati.
     */
    private Map<Integer, Set<Integer>> notifiedGaps = new HashMap<>();

    /**
     * ID del nodo locale, su cui gira questa istanza di ObjectReceiver.
     */
    private int localNodeId;

    /**
     * Listener incaricato di gestire le richieste di reinvio.
     */
    private ResendListener listener;

    /**
     * Costruisce un ObjectReceiver associandolo a un determinato nodo locale.
     *
     * @param localNodeId ID del nodo che riceve i messaggi
     * @param listener    callback {@link ResendListener} per gestire le richieste di reinvio
     */
    public ObjectReceiver(int localNodeId, ResendListener listener) {
        this.localNodeId = localNodeId;
        this.listener = listener;
    }

    /**
     * Avvia la ricezione dei pacchetti multicast.
     * Rimane in esecuzione finché {@link #running} non è impostato a false.
     *
     * Esegue:
     * - Selezione dell'interfaccia di rete adatta
     * - Creazione e join del gruppo multicast
     * - Ricezione e deserializzazione degli oggetti di tipo {@link Message}
     * - Gestione dei messaggi di reinvio (resend)
     * - Rilevamento e notifica dei messaggi mancanti
     */
    public void startReceiving() {
        InetAddress group;
        InetSocketAddress groupAddress = null;
        NetworkInterface networkInterface = null;

        try {
            group = InetAddress.getByName(MULTICAST_ADDRESS);

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isLoopback() && ni.supportsMulticast()) {
                    for (InetAddress addr : java.util.Collections.list(ni.getInetAddresses())) {
                        if (addr instanceof java.net.Inet4Address) {
                            networkInterface = ni;
                            break;
                        }
                    }
                    if (networkInterface != null) {
                        break;
                    }
                }
            }

            if (networkInterface == null) {
                System.err.println("Nessuna interfaccia multicast disponibile.");
                return;
            }

            groupAddress = new InetSocketAddress(group, MULTICAST_PORT);
            multicastSocket = new MulticastSocket(null);
            multicastSocket.setReuseAddress(true);
            multicastSocket.bind(new InetSocketAddress("0.0.0.0", MULTICAST_PORT));
            multicastSocket.setNetworkInterface(networkInterface);

            multicastSocket.joinGroup(groupAddress, networkInterface);
            System.out.println("Receiver (nodo " + localNodeId
                    + ") in ascolto sul gruppo " + MULTICAST_ADDRESS + ":" + MULTICAST_PORT);

            while (running) {
                try {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    multicastSocket.receive(packet);

                    Object receivedObj = deserialize(packet.getData());
                    if (receivedObj instanceof Message) {
                        Message msg = (Message) receivedObj;
                        int senderId = msg.getNodeId();
                        int receivedId = msg.getMessageId();

                        if (msg.isResend()) {
                            System.out.println("Nodo " + localNodeId
                                    + " ha ricevuto messaggio RESEND da nodo " + senderId
                                    + " per id " + receivedId);

                            if (senderId == localNodeId && listener != null) {
                                listener.onResendRequest(senderId, receivedId);
                            }
                        } else {
                            int expected = expectedMap.getOrDefault(senderId, 1);

                            if (receivedId > expected) {
                                System.out.println("Nodo " + localNodeId + " rileva gap: atteso "
                                        + expected + ", ricevuto " + receivedId
                                        + " da nodo " + senderId);

                                notifiedGaps.putIfAbsent(senderId, new HashSet<>());
                                for (int missing = expected; missing < receivedId; missing++) {
                                    if (!notifiedGaps.get(senderId).contains(missing)) {
                                        Message lossNotification = new Message(senderId, missing, true);
                                        ObjectSender sender = new ObjectSender();
                                        sender.send(lossNotification);
                                        System.out.println("Nodo " + localNodeId
                                                + " invia notifica perdita: nodo " + senderId
                                                + ", id " + missing);
                                        notifiedGaps.get(senderId).add(missing);
                                    }
                                }
                                expectedMap.put(senderId, receivedId + 1);
                            } else if (receivedId == expected) {
                                expectedMap.put(senderId, expected + 1);
                            } else {
                                System.out.println("Nodo " + localNodeId
                                        + " riceve messaggio duplicato o fuori ordine da nodo "
                                        + senderId + ": id " + receivedId
                                        + ", atteso " + expected);
                            }
                            System.out.println("Nodo " + localNodeId
                                    + " ha ricevuto messaggio da nodo " + senderId
                                    + ": id " + receivedId);
                        }
                    }
                } catch (IOException e) {
                    if (!running) {
                        break;
                    }
                    e.printStackTrace();
                }
            }

            if (multicastSocket != null && !multicastSocket.isClosed()
                    && groupAddress != null && networkInterface != null) {
                try {
                    multicastSocket.leaveGroup(groupAddress, networkInterface);
                } catch (IOException e) {
                    // Ignora eventuali errori in chiusura
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (multicastSocket != null && !multicastSocket.isClosed()) {
                multicastSocket.close();
            }
        }
    }

    /**
     * Deserializza un oggetto da un array di byte.
     *
     * @param data array di byte con l'oggetto serializzato
     * @return l'oggetto deserializzato
     * @throws IOException se avviene un errore di I/O
     * @throws ClassNotFoundException se la classe dell'oggetto non è trovata
     */
    private Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bis);
        Object obj = ois.readObject();
        ois.close();
        return obj;
    }

    /**
     * Ferma la ricezione dei messaggi e chiude la socket multicast.
     * Imposta {@link #running} a false e forza l'uscita dal ciclo principale.
     */
    public void stop() {
        running = false;
        if (multicastSocket != null && !multicastSocket.isClosed()) {
            multicastSocket.close();
        }
    }

    /**
     * Esegue un test locale se invocato come main, creando un receiver con localNodeId = 9999
     * e senza {@link ResendListener}.
     *
     * @param args argomenti da linea di comando (non utilizzati)
     */
    public static void main(String[] args) {
        ObjectReceiver receiver = new ObjectReceiver(9999, null);
        receiver.startReceiving();
    }
}
