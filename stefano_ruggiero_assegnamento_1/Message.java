package stefano_ruggiero_assegnamento_1;


import java.io.Serializable;

/**
 * La classe Message rappresenta un messaggio serializzabile,
 * contenente un identificativo del nodo mittente, un messageId
 * e un flag booleano che indica se si tratta di un reinvio.
 */
public final class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Identificativo del nodo che ha creato o inviato il messaggio.
     */
    private final int nodeId;

    /**
     * Identificativo univoco del messaggio.
     */
    private int messageId;

    /**
     * Indica se si tratta di un messaggio di reinvio.
     */
    private boolean resend;

    /**
     * Costruttore della classe Message.
     *
     * @param nodeId     ID del nodo mittente
     * @param messageId  ID del messaggio
     * @param resend     true se è un messaggio di reinvio, false altrimenti
     */
    public Message(final int nodeId, final int messageId, final boolean resend) {
        this.nodeId = nodeId;
        this.messageId = messageId;
        this.resend = resend;
    }

    /**
     * Restituisce l'ID del nodo mittente.
     *
     * @return l'ID del nodo che ha inviato il messaggio
     */
    public int getNodeId() {
        return nodeId;
    }

    /**
     * Restituisce l'ID del messaggio.
     *
     * @return il messageId
     */
    public int getMessageId() {
        return messageId;
    }

    /**
     * Indica se si tratta di un messaggio di reinvio.
     *
     * @return true se è un reinvio, false altrimenti
     */
    public boolean isResend() {
        return resend;
    }

    /**
     * Imposta un nuovo messageId.
     *
     * @param messageId nuovo valore per l'ID del messaggio
     */
    public void setMessageId(final int messageId) {
        this.messageId = messageId;
    }

    /**
     * Imposta il flag di reinvio.
     *
     * @param resend true se si tratta di un messaggio di reinvio, false altrimenti
     */
    public void setResend(final boolean resend) {
        this.resend = resend;
    }

    /**
     * Restituisce una rappresentazione in forma di stringa del messaggio.
     *
     * @return la descrizione del messaggio
     */
    @Override
    public String toString() {
        return "Message{"
                + "nodeId=" + nodeId
                + ", messageId=" + messageId
                + ", resend=" + resend
                + '}';
    }
}
