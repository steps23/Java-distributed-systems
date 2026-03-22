// Message.java
package stefano_ruggiero_assegnamento_3;

import java.io.Serializable;
import java.util.Objects;

/**
 * Rappresenta un messaggio scambiato tra nodi.
 * Può essere di tipo TASK (contenente un payload di lavoro)
 * oppure MARKER (utilizzato per l’algoritmo di snapshot).
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Tipi di messaggio: TASK o MARKER.
     */
    public enum Type {
        /** Messaggio che trasporta un task. */
        TASK,
        /** Messaggio che segnala l’inizio di uno snapshot. */
        MARKER
    }

    /** Tipo del messaggio (TASK o MARKER). */
    private final Type type;
    /** ID del nodo mittente. */
    private final int senderId;
    /** Payload del TASK (identifier del task); per MARKER è vuoto. */
    private final String payload;
    /** ID dello snapshot per i MARKER; null per i TASK. */
    private final String snapshotId;

    /**
     * Costruisce un nuovo Message.
     *
     * @param type       tipo di messaggio (TASK o MARKER)
     * @param senderId   ID del nodo mittente
     * @param payload    contenuto del task (solo per TASK)
     * @param snapshotId ID dello snapshot (solo per MARKER)
     */
    public Message(Type type, int senderId, String payload, String snapshotId) {
        this.type       = type;
        this.senderId   = senderId;
        this.payload    = payload;
        this.snapshotId = snapshotId;
    }

    /**
     * Restituisce il tipo del messaggio.
     *
     * @return TASK o MARKER
     */
    public Type getType() {
        return type;
    }

    /**
     * Restituisce l’ID del nodo mittente.
     *
     * @return ID del nodo
     */
    public int getSenderId() {
        return senderId;
    }

    /**
     * Restituisce il payload del TASK.
     *
     * @return stringa del task, o "" se MARKER
     */
    public String getPayload() {
        return payload;
    }

    /**
     * Restituisce l’ID dello snapshot per i MARKER.
     *
     * @return UUID dello snapshot, o null se TASK
     */
    public String getSnapshotId() {
        return snapshotId;
    }

    @Override
    public String toString() {
        if (type == Type.TASK) {
            return String.format("TASK %-36s", payload);
        } else {
            return String.format("MARKER snap=%s", snapshotId);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, senderId, payload, snapshotId);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Message)) return false;
        Message m = (Message) o;
        return type == m.type
            && senderId == m.senderId
            && Objects.equals(payload, m.payload)
            && Objects.equals(snapshotId, m.snapshotId);
    }
}
