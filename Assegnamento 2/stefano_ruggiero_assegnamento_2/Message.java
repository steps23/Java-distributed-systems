// File: Message.java
package stefano_ruggiero_assegnamento_2;

import java.io.Serializable;

/**
 * Rappresenta un messaggio serializzabile scambiato tra nodi e coordinatore.
 * Usato per barrier, mutua esclusione centralizzata, heartbeat,
 * algoritmo di elezione Bully e segnale di termine.
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Tipi di messaggi:
     * <ul>
     *   <li>{@link #REGISTER}</li>
     *   <li>{@link #START}</li>
     *   <li>{@link #REQUEST}</li>
     *   <li>{@link #GRANT}</li>
     *   <li>{@link #RELEASE}</li>
     *   <li>{@link #HEARTBEAT}</li>
     *   <li>{@link #ELECTION}</li>
     *   <li>{@link #OK}</li>
     *   <li>{@link #VICTORY}</li>
     *   <li>{@link #DONE}</li>
     * </ul>
     */
    public enum Type {
        /** Nodo si registra al coordinatore (barriera iniziale). */
        REGISTER,
        /** Coordinatore invia il segnale di avvio (barriera iniziale). */
        START,
        /** Nodo richiede accesso alla sezione critica (mutua esclusione). */
        REQUEST,
        /** Coordinatore concede accesso alla sezione critica. */
        GRANT,
        /** Nodo rilascia la risorsa (mutua esclusione). */
        RELEASE,
        /** Segnale di vita inviato periodicamente per failure detection. */
        HEARTBEAT,
        /** Inizio dell’elezione Bully (richiesta di elezione). */
        ELECTION,
        /** Risposta positiva (OK) durante l’elezione Bully. */
        OK,
        /** Annuncio di vittoria del nuovo coordinatore. */
        VICTORY,
        /** Segnale di completamento distribuzione risorse. */
        DONE
    }

    /** Tipo di questo messaggio. */
    private final Type type;
    /** ID del nodo mittente. */
    private final int  senderId;
    /** Timestamp logico di Lamport, per ordinamento eventi. */
    private final int  lamport;

    /**
     * Costruisce un nuovo messaggio.
     *
     * @param type     tipo di messaggio
     * @param senderId identificatore del nodo mittente
     * @param lamport  timestamp logico di Lamport
     */
    public Message(Type type, int senderId, int lamport) {
        this.type     = type;
        this.senderId = senderId;
        this.lamport  = lamport;
    }

    /**
     * Ritorna il tipo di messaggio.
     *
     * @return tipo di messaggio
     */
    public Type getType() {
        return type;
    }

    /**
     * Ritorna l’ID del nodo mittente.
     *
     * @return identificatore del mittente
     */
    public int getSenderId() {
        return senderId;
    }

    /**
     * Ritorna il timestamp logico di Lamport associato.
     *
     * @return timestamp di Lamport
     */
    public int getLamport() {
        return lamport;
    }
}
