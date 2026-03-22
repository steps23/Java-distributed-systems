// File: Config.java
package stefano_ruggiero_assegnamento_2;

/**
 * Classe contenente le configurazioni statiche dell'applicazione.
 * Comprende parametri per barrier, mutua esclusione centralizzata,
 * failure detection tramite heartbeat e algoritmo di elezione Bully.
 */
public final class Config {
    /** Indirizzo IP multicast per UDP broadcast (barriera, heartbeat, elezione). */
    public static final String MULTICAST_ADDR        = "230.0.0.1";
    /** Porta UDP per invio/ricezione di messaggi di controllo. */
    public static final int    UDP_PORT              = 4446;
    /** Porta TCP per gestire REQUEST/GRANT/RELEASE (mutua esclusione). */
    public static final int    TCP_PORT              = 4444;

    /** Numero di nodi (escluso il coordinatore) da attendere nella barriera iniziale. */
    public static final int    EXPECTED_NODES        = 3;
    /** Numero di risorse che ogni nodo deve acquisire nel ciclo di sezione critica. */
    public static final int    RESOURCE_TARGET       = 25;

    /** Probabilità di crash simulato del coordinatore ad ogni heartbeat (0.0–1.0). */
    public static final double CRASH_PROBABILITY     = 0.35;
    /** Intervallo (ms) tra heartbeat consecutivi per failure detection. */
    public static final int    HEARTBEAT_INTERVAL_MS = 1000;
    /** Timeout (ms) senza heartbeat oltre cui si sospetta crash. */
    public static final int    HEARTBEAT_TIMEOUT_MS  = 3000;
    /** Timeout (ms) per attendere risposte OK durante il Bully election. */
    public static final int    ELECTION_TIMEOUT_MS   = 2000;

    /**
     * Costruttore privato per evitare istanziazione della classe di configurazione.
     */
    private Config() {
        // Non istanziare
    }
}
