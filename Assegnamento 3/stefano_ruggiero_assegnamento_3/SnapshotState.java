// SnapshotState.java
package stefano_ruggiero_assegnamento_3;

import java.util.List;
import java.util.Map;

/**
 * Rappresenta lo stato catturato da un nodo per uno snapshot.
 * Contiene il valore del contatore locale e lo stato dei canali in ingresso.
 */
public class SnapshotState {
    /** ID dello snapshot. */
    private final String snapshotId;
    /** ID del nodo che ha catturato lo stato. */
    private final int nodeId;
    /** Valore del contatore locale al momento dello snapshot. */
    private final int localCounter;
    /** Buffer dei messaggi ricevuti dopo l’inizio dello snapshot per ciascun peer. */
    private final Map<Integer, List<Message>> channelStates;

    /**
     * Costruisce un nuovo SnapshotState.
     *
     * @param snapshotId    ID univoco dello snapshot
     * @param nodeId        ID del nodo che cattura lo stato
     * @param localCounter  contatore locale al momento dello snapshot
     * @param channelStates mappa ID peer → lista di messaggi registrati
     */
    public SnapshotState(String snapshotId,
                         int nodeId,
                         int localCounter,
                         Map<Integer, List<Message>> channelStates) {
        this.snapshotId   = snapshotId;
        this.nodeId       = nodeId;
        this.localCounter = localCounter;
        this.channelStates= channelStates;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SNAPSHOT ").append(snapshotId)
          .append(" @ nodo ").append(nodeId)
          .append("  counter=").append(localCounter).append('\n');
        channelStates.forEach((from, list) ->
          sb.append("  canale da ").append(from)
            .append(" → ").append(list).append('\n'));
        return sb.toString();
    }
}
