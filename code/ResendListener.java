package stefano_ruggiero_assegnamento_1;

/***************************************************************
 * Interfaccia che consente la gestione di richieste di reinvio
 * di messaggi in caso di perdita di pacchetti.
 *
 * @author 
 ***************************************************************/
public interface ResendListener {

    /**
     * Metodo chiamato quando viene rilevata una richiesta di reinvio per un messaggio perso.
     *
     * @param senderId       l'ID del nodo che ha originariamente inviato il messaggio
     * @param lostMessageId  l'ID del messaggio che risulta mancante
     */
    void onResendRequest(int senderId, int lostMessageId);
}
