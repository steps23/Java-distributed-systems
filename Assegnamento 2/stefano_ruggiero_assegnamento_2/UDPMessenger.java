// File: UDPMessenger.java
package stefano_ruggiero_assegnamento_2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 * Gestisce l’invio e la ricezione di {@link Message} tramite multicast UDP.
 * Utilizzato per barrier, heartbeat e Bully election.
 */
public class UDPMessenger implements AutoCloseable {

    /** Socket multicast per l’invio e la ricezione. */
    private final MulticastSocket socket;
    /** Indirizzo del gruppo multicast. */
    private final InetAddress     group;

    /**
     * Crea un messenger UDP che si unisce al gruppo multicast.
     *
     * @throws IOException se non è possibile configurare la socket
     */
    public UDPMessenger() throws IOException {
        group  = InetAddress.getByName(Config.MULTICAST_ADDR);
        socket = new MulticastSocket(Config.UDP_PORT);
        socket.setLoopbackMode(false);
        socket.joinGroup(group);
        System.out.println("[UDPMessenger] joined multicast "
            + Config.MULTICAST_ADDR + ":" + Config.UDP_PORT);
    }

    /**
     * Invia un {@link Message} serializzato al gruppo multicast.
     *
     * @param msg messaggio da inviare
     * @throws IOException se non è possibile serializzare o inviare il pacchetto
     */
    public void send(Message msg) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream    oos = new ObjectOutputStream(bos)) {
            oos.writeObject(msg);
            oos.flush();
            byte[] data = bos.toByteArray();
            DatagramPacket packet =
                new DatagramPacket(data, data.length, group, Config.UDP_PORT);
            socket.send(packet);
            System.out.printf("[UDP SEND] %-8s from %2d → %s:%d%n",
                msg.getType(), msg.getSenderId(),
                Config.MULTICAST_ADDR, Config.UDP_PORT);
        }
    }

    /**
     * Riceve un pacchetto UDP e lo deserializza in un {@link Message}.
     * Blocca finché non arriva un pacchetto.
     *
     * @return messaggio ricevuto
     * @throws IOException            se si verifica un errore di I/O
     * @throws ClassNotFoundException se la deserializzazione fallisce
     */
    public Message receive() throws IOException, ClassNotFoundException {
        byte[] buf = new byte[1024];
        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
        socket.receive(pkt);
        try (ObjectInputStream ois =
                 new ObjectInputStream(
                     new ByteArrayInputStream(pkt.getData(),0,pkt.getLength()))) {
            Message msg = (Message) ois.readObject();
            System.out.printf("[UDP RECV] %-8s from %2d ← %s:%d%n",
                msg.getType(), msg.getSenderId(),
                Config.MULTICAST_ADDR, Config.UDP_PORT);
            return msg;
        }
    }

    /**
     * Abbandona il gruppo multicast e chiude la socket.
     *
     * @throws IOException se si verifica un errore durante la chiusura
     */
    @Override
    public void close() throws IOException {
        socket.leaveGroup(group);
        socket.close();
        System.out.println("[UDPMessenger] closed");
    }
}
