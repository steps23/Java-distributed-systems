Assegnamento 4 Sistemi Distribuiti — Stefano Ruggiero (mat. 364989)

- Panoramica

Questo progetto simula l’evacuazione di un edificio rettangolare dotato di due uscite, impiegando tecniche di Flocking Behavior abbinate a punti di attrazione esterni:

* 40 agenti (persone) che si muovono secondo le regole di separazione, allineamento e coesione, più una spinta verso il relativo punto di attrazione esterno.
* 22 ostacoli rettangolari, distribuiti in modo casuale con un gap minimo garantito per evitare sovrapposizioni.
* 2 uscite (sinistra e destra), ciascuna larga 2 celle e alta 8 celle, con capacità di transito di massimo 2 persone simultanee e gestione esplicita delle code.
* Punti di attrazione posizionati immediatamente all’esterno di ogni uscita: H₁ (H\_left) a sinistra, H₂ (H\_right) a destra, e un punto finale H sotto l’edificio.
* La simulazione termina quando tutte le persone raggiungono l’area di attrazione finale H.

- Compilazione: aprire un terminale nella radice del progetto e digitare --> javac -d out stefano_ruggiero_assegnamento_4/*.java


- Esecuzione : java -cp out stefano_ruggiero_assegnamento_4.EvacuationSimulation


- Parametri configurabili e Modificabili in `EvacuationSimulation.java` e `Person.java`:

* `WIDTH`, `HEIGHT`
* `NUM_PEOPLE`, `NUM_OBSTACLES`
* `EXIT_WIDTH`, `EXIT_HEIGHT`
* `H_RADIUS`, `TICK_LIMIT`
* Pesi flocking (`COHESION_WEIGHT`, `SEPARATION_WEIGHT`, `ALIGNMENT_WEIGHT`) e velocità (step in `update()`)

- Tutta la documentazione per dettagli aggiuntivi è presente nel javadoc presente nella cartella "docs". 