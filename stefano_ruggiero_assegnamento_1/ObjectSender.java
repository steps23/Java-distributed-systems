package stefano_ruggiero_assegnamento_1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Random;

/**
 * La classe ObjectSender invia oggetti di tipo {@link Message}
 * tramite multicast (UDP) verso un gruppo specifico.
 * Può simulare la perdita di pacchetti con probabilità LP.
 */
public class ObjectSender {
    /**
     * Indirizzo del gruppo multicast.
     */
    private static final String MULTICAST_ADDRESS = "230.0.0.1";

    /**
     * Porta usata dal gruppo multicast.
     */
    private static final int MULTICAST_PORT = 4446;

    /**
     * Probabilità di simulare la perdita di un pacchetto (messaggio non inviato).
     */
    private static final double LP = 0.3;

    /**
     * Generatore di numeri casuali usato per verificare se perdere il pacchetto.
     */
    private Random random = new Random();

    /**
     * Invia un messaggio {@link Message} in multicast.
     * Se un numero casuale è minore di LP, il messaggio viene considerato perso (non inviato).
     *
     * @param msg il messaggio da inviare
     */
    public void send(Message msg) {
        if (random.nextDouble() < LP) {
            System.out.format("Simulazione: il messaggio (nodeId=%d, messageId=%d, resend=%b) NON è stato inviato (perso).%n",
                              msg.getNodeId(), msg.getMessageId(), msg.isResend());
            return;
        }

        MulticastSocket multicastSocket = null;
        try {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);

            NetworkInterface networkInterface = null;
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

            InetSocketAddress groupAddress = new InetSocketAddress(group, MULTICAST_PORT);
            multicastSocket = new MulticastSocket(null);
            multicastSocket.setReuseAddress(true);
            multicastSocket.bind(new InetSocketAddress("0.0.0.0", MULTICAST_PORT));
            multicastSocket.setNetworkInterface(networkInterface);
            multicastSocket.joinGroup(groupAddress, networkInterface);

            if (msg.isResend()) {
                System.out.format("Sender: reinvio messaggio -> nodeId=%d, messageId=%d, resend=%b%n",
                                  msg.getNodeId(), msg.getMessageId(), msg.isResend());
            } else {
                System.out.format("Sender: invio messaggio -> nodeId=%d, messageId=%d, resend=%b%n",
                                  msg.getNodeId(), msg.getMessageId(), msg.isResend());
            }

            byte[] data = serialize(msg);
            DatagramPacket packet = new DatagramPacket(data, data.length, group, MULTICAST_PORT);
            multicastSocket.send(packet);

            multicastSocket.leaveGroup(groupAddress, networkInterface);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (multicastSocket != null) {
                multicastSocket.close();
            }
        }
    }

    /**
     * Serializza un oggetto in un array di byte.
     *
     * @param obj oggetto da serializzare
     * @return array di byte rappresentante l'oggetto serializzato
     * @throws IOException se si verifica un errore di I/O durante la serializzazione
     */
    private byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(obj);
        oos.flush();
        oos.close();
        return bos.toByteArray();
    }

    /**
     * Metodo main di test.
     * Invia 5 messaggi di esempio con messageId da 1 a 5.
     *
     * @param args argomenti da linea di comando (non utilizzati)
     */
    public static void main(String[] args) {
        ObjectSender sender = new ObjectSender();
        for (int i = 1; i <= 5; i++) {
            Message msg = new Message(1234, i, false);
            sender.send(msg);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
