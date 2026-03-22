// NodeMain.java
package stefano_ruggiero_assegnamento_3;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Classe principale di avvio. Configura il nodo
 * e gestisce Ctrl-C e il comando "quit" da console.
 */
public class NodeMain {

    /**
     * Costruttore di default (richiesto dal Javadoc che segnala
     * warning se non esiste un costruttore esplicito).
     */
    public NodeMain() { /* non utilizzato */ }

    /**
     * Entry point.
     *
     * @param args array di stringhe:
     *             args[0] = ID del nodo (intero)
     *             args[1] = numero totale di nodi (intero)
     *             args[2] = porta base (intero)
     *             args[3] = targetSnapshots (intero, opzionale)
     * @throws Exception in caso di errori di rete o I/O
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) {
            System.err.println("Usage: java NodeMain <id> <nodes> <basePort> [targetSnapshots]");
            System.exit(1);
        }
        int id       = Integer.parseInt(args[0]);
        int nodes    = Integer.parseInt(args[1]);
        int basePort = Integer.parseInt(args[2]);
        int target   = (args.length == 4) ? Integer.parseInt(args[3]) : -1;

        Node node = new Node(id, nodes, basePort, target);
        node.start();

        // Hook per Ctrl-C
        Runtime.getRuntime().addShutdownHook(new Thread(node::shutdown));

        // Listener console: digitando "quit" si chiude il nodo
        new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().equalsIgnoreCase("quit")) {
                        node.shutdown();
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }, "ConsoleListener").start();
    }
}
