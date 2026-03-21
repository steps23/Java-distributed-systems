package stefano_ruggiero_assegnamento_1;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * La classe ObjectClient si connette a un server tramite TCP per l'handshake
 * e invia messaggi in multicast (UDP) utilizzando {@link ObjectSender}.
 * Inoltre, gestisce le richieste di reinvio (resend) tramite l'interfaccia {@link ResendListener}.
 *
 * Funzionalità:
 * - Connessione al server e scambio iniziale di messaggi
 * - Invio di messaggi originali incrementando un contatore (nextMsgId)
 * - Gestione di richieste di reinvio di messaggi persi
 * - Segnalazione al server del completamento dell'invio (messageId == 0)
 */
public class ObjectClient implements ResendListener {
    /**
     * Porta su cui è in ascolto il server TCP.
     */
    private static final int SERVER_PORT = 4444;

    /**
     * Host del server TCP.
     */
    private static final String SERVER_HOST = "localhost";

    /**
     * Valore limite per l'incremento di nextMsgId,
     * dopo il quale il client considera terminato il suo compito.
     */
    private static final int TARGET_ID = 100;

    /**
     * Identificativo univoco di questo client.
     */
    private final int nodeId;

    /**
     * Contatore per assegnare gli ID ai nuovi messaggi
     * (incrementato a ogni invio di messaggio originale).
     */
    private int nextMsgId = 1;

    /**
     * Mappa che memorizza i messaggi inviati,
     * in modo da poterli reinviare in caso di richiesta.
     * La chiave è il messageId, il valore è il {@link Message} corrispondente.
     */
    private Map<Integer, Message> sentMessages = new HashMap<>();

    /**
     * Insieme di messageId già gestiti per richieste di reinvio, 
     * così da non reinviare più volte lo stesso messaggio in risposta a richieste duplicate.
     */
    private Set<Integer> resendHandled = new HashSet<>();

    /**
     * Costruisce un nuovo ObjectClient associato a un determinato nodeId.
     *
     * @param nodeId identificativo univoco di questo client
     */
    public ObjectClient(int nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * Avvia il processo di connessione al server, invio di messaggi multicast
     * e gestione di richieste di reinvio.
     * Al termine, invia un messaggio di completamento (messageId == 0) al server.
     */
    public void start() {
        Socket socket = null;
        ObjectOutputStream os = null;
        ObjectInputStream is = null;

        try {
            // Connessione TCP al server
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            os = new ObjectOutputStream(socket.getOutputStream());
            is = new ObjectInputStream(socket.getInputStream());
            System.out.println("Client " + nodeId + " connesso al server. Handshake in corso...");

            // Invia un messaggio di handshake al server
            Message greeting = new Message(this.nodeId, nextMsgId, false);
            sentMessages.put(greeting.getMessageId(), greeting);
            os.writeObject(greeting);
            os.flush();
            System.out.format("Client %d ha inviato messaggio di handshake: id %d%n",
                    nodeId, nextMsgId);
            nextMsgId++;

            // Attende una risposta di conferma dal server
            Object response = is.readObject();
            if (response instanceof Message) {
                Message msg = (Message) response;
                System.out.format(
                    "Client %d ha ricevuto dal server: nodeId=%d, messageId=%d, resend=%b%n",
                    nodeId, msg.getNodeId(), msg.getMessageId(), msg.isResend()
                );
            }

            // Avvia il receiver multicast per gestire le richieste di reinvio
            ObjectReceiver receiver = new ObjectReceiver(this.nodeId, this);
            Thread receiverThread = new Thread(receiver::startReceiving);
            receiverThread.start();

            // Crea un sender per inviare i messaggi in multicast
            ObjectSender sender = new ObjectSender();

            // Invio di messaggi originali finché nextMsgId non supera TARGET_ID
            while (nextMsgId <= TARGET_ID) {
                Message multicastMsg = new Message(this.nodeId, nextMsgId, false);
                sentMessages.put(multicastMsg.getMessageId(), multicastMsg);
                sender.send(multicastMsg);
                System.out.format("Client %d ha inviato messaggio originale: id %d%n",
                        nodeId, nextMsgId);
                nextMsgId++;

                // Pausa prima di inviare il messaggio successivo
                Thread.sleep(200);
            }

            // Quando ha finito, ferma il receiver multicast e attende la terminazione del thread
            receiver.stop();
            receiverThread.join();

            // Invia al server un messaggio di completamento (messageId == 0)
            Message completionMsg = new Message(this.nodeId, 0, false);
            os.writeObject(completionMsg);
            os.flush();
            System.out.println("Client " + nodeId
                    + " ha inviato messaggio di completamento al server.");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Chiusura delle risorse
            try {
                if (is != null) {
                    is.close();
                }
                if (os != null) {
                    os.close();
                }
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Client " + nodeId + " ha terminato il lavoro.");
    }

    /**
     * Invocato quando viene rilevata una richiesta di reinvio per un messaggio
     * inviato da questo client (senderId corrisponde a nodeId).
     *
     * @param senderId     ID del mittente originale del messaggio
     * @param lostMessageId ID del messaggio da reinviare
     */
    @Override
    public void onResendRequest(int senderId, int lostMessageId) {
        if (senderId == this.nodeId) {
            // Evita reinvii multipli dello stesso messaggio
            if (resendHandled.contains(lostMessageId)) {
                return;
            }
            resendHandled.add(lostMessageId);

            // Recupera il messaggio originale
            Message original = sentMessages.get(lostMessageId);
            if (original != null) {
                System.out.println("Client " + nodeId
                        + " riceve richiesta di reinvio per il messaggio con id " + lostMessageId);

                // Crea un nuovo messaggio con lo stesso ID ma resend = true
                Message resendMsg = new Message(this.nodeId, lostMessageId, true);
                ObjectSender sender = new ObjectSender();
                sender.send(resendMsg);
                System.out.println("Client " + nodeId
                        + " ha reinviato il messaggio con id " + lostMessageId);
            } else {
                System.out.println("Client " + nodeId
                        + " non ha trovato il messaggio con id " + lostMessageId
                        + " per il reinvio.");
            }
        }
    }

    /**
     * Punto di ingresso dell'applicazione: crea un client con un nodeId casuale e avvia il processo.
     *
     * @param args argomenti da linea di comando (non utilizzati)
     */
    public static void main(String[] args) {
        Random random = new Random();
        int nodeId = random.nextInt(10000);
        new ObjectClient(nodeId).start();
    }
}
