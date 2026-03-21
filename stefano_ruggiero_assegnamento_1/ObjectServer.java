package stefano_ruggiero_assegnamento_1;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * La classe ObjectServer accetta connessioni TCP da un numero prefissato
 * di client e coordina lo scambio di alcuni messaggi. Al termine del lavoro
 * di tutti i client, il server chiude le connessioni e termina.
 */
public class ObjectServer {
    /**
     * Porta su cui il server rimane in ascolto per le connessioni TCP.
     */
    private static final int SPORT = 4444;

    /**
     * Numero di client richiesti. Il server attende che si connettano
     * e che inviino il loro messaggio di completamento.
     */
    private static final int REQUIRED_CLIENTS = 3;

    /**
     * Avvia il server, che rimane in ascolto finché non si connettono
     * tutti i client richiesti e non inviano il proprio messaggio di fine (messageId == 0).
     */
    public void start() {
        List<ClientHandler> clients = new ArrayList<>();
        try (ServerSocket server = new ServerSocket(SPORT)) {
            System.out.println("Server in attesa di " + REQUIRED_CLIENTS + " client...");

            CountDownLatch latch = new CountDownLatch(REQUIRED_CLIENTS);

            // Accetta connessioni finché non raggiunge il numero di client desiderato
            while (clients.size() < REQUIRED_CLIENTS) {
                Socket client = server.accept();
                System.out.println("Client connesso: " + client.getInetAddress());
                ClientHandler handler = new ClientHandler(client, latch);
                clients.add(handler);
                new Thread(handler).start();
            }

            // Invia a tutti i client un messaggio iniziale
            Message message = new Message(0, 100, false);
            System.out.println("Inviando il messaggio a tutti i client connessi.");
            for (ClientHandler ch : clients) {
                ch.sendMessage(message);
            }

            // Attende che tutti i client inviino il messaggio di completamento
            System.out.println("Attesa di messaggi di completamento dai client...");
            latch.await();
            System.out.println("Tutti i client hanno completato. Chiusura del sistema.");

            // Chiude le connessioni con i client
            for (ClientHandler ch : clients) {
                ch.close();
            }

            // Termina l'applicazione
            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Metodo main per avviare il server.
     *
     * @param args argomenti da linea di comando (non utilizzati)
     */
    public static void main(String[] args) {
        new ObjectServer().start();
    }

    /**
     * Classe interna che gestisce la comunicazione con un singolo client.
     */
    private static class ClientHandler implements Runnable {
        /**
         * Socket che collega questo handler al client.
         */
        private Socket socket;

        /**
         * Stream di output verso il client.
         */
        private ObjectOutputStream os;

        /**
         * Stream di input dal client.
         */
        private ObjectInputStream is;

        /**
         * Latch per sincronizzare la terminazione, viene decrementato
         * quando il client segnala di aver completato.
         */
        private CountDownLatch latch;

        /**
         * Costruttore del ClientHandler.
         *
         * @param socket socket TCP che connette al client
         * @param latch  latch condiviso per sincronizzare la fine dei lavori
         * @throws IOException se si verificano errori nell'ottenimento degli stream
         */
        public ClientHandler(Socket socket, CountDownLatch latch) throws IOException {
            this.socket = socket;
            this.latch = latch;
            this.os = new ObjectOutputStream(socket.getOutputStream());
            this.is = new ObjectInputStream(socket.getInputStream());
        }

        /**
         * Invia un messaggio {@link Message} al client.
         *
         * @param m messaggio da inviare
         */
        public void sendMessage(Message m) {
            try {
                os.writeObject(m);
                os.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Chiude la connessione con il client.
         */
        public void close() {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Legge messaggi dal client in un ciclo. Se riceve un messageId == 0,
         * decremente il latch (il client ha terminato) e termina.
         */
        @Override
        public void run() {
            try {
                while (true) {
                    Object o = is.readObject();
                    if (o instanceof Message) {
                        Message m = (Message) o;
                        System.out.println("Ricevuto dal client " + socket.getInetAddress() + ": " + m);

                        // Se messageId == 0, il client segnala il completamento
                        if (m.getMessageId() == 0) {
                            System.out.println("Client " + socket.getInetAddress() + " ha completato il lavoro.");
                            latch.countDown();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // Se la connessione si interrompe o c'è un errore, termina comunque
            }
        }
    }
}
