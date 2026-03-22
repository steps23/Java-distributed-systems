package stefano_ruggiero_assegnamento_4;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.*;

/**
 * Classe principale che gestisce la simulazione di evacuazione.
 * Inizializza persone, ostacoli, uscite e controlla il loop di simulazione.
 */
public class EvacuationSimulation {
    /** Width of the simulation area in cells */
    public static final int WIDTH = 80;
    /** Height of the simulation area in cells */
    public static final int HEIGHT = 50;
    /** X coordinate of final attraction point H center */
    public static final double H_X = WIDTH / 2.0;
    /** Y coordinate of final attraction point H center */
    public static final double H_Y = HEIGHT + 12;
    /** Number of people in simulation */
    public static final int NUM_PEOPLE = 40;
    /** Number of obstacles in simulation */
    public static final int NUM_OBSTACLES = 22;
    /** Maximum number of ticks before stopping */
    public static final int TICK_LIMIT = 1500;
    /** Width of each exit in cells */
    public static final int EXIT_WIDTH = 2;
    /** Height of each exit in cells */
    public static final int EXIT_HEIGHT = 8;
    /** Radius of final attraction area H */
    public static final int H_RADIUS = 10;

    /** List of people agents */
    List<Person> people = new ArrayList<>();
    /** List of obstacles */
    List<Obstacle> obstacles = new ArrayList<>();
    /** List of exits */
    List<Exit> exits = new ArrayList<>();
    /** Counters for queue size at each exit */
    int[] exitQueue = new int[2];
    /** Current simulation tick */
    int tick = 0;

    /** Left local attraction point outside left exit */
    public static Point H_left;
    /** Right local attraction point outside right exit */
    public static Point H_right;
    /** Intermediate magenta point under left exit */
    public static Point magenta1;
    /** Intermediate magenta point under right exit */
    public static Point magenta2;
    /** Final attraction point H under building */
    public static final Point H_final = new Point(WIDTH / 2, HEIGHT + 12);

    /**
     * Avvio dell’applicazione Swing.
     * @param args argomenti da linea di comando (non usati)
     */
    public static void main(String[] args) {
        EvacuationSimulation sim = new EvacuationSimulation();
        JFrame frame = new JFrame("Evacuation Simulation");
        EvacuationPanel panel = new EvacuationPanel(sim);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(panel);
        frame.pack();
        frame.setResizable(false);
        frame.setVisible(true);
        sim.run(panel);
    }

    /**
     * Costruttore: genera ostacoli, uscite, attrattori e persone.
     */
    public EvacuationSimulation() {
        generateSpacedObstacles();
        exits.add(new Exit(0, HEIGHT / 4, EXIT_WIDTH, EXIT_HEIGHT));
        exits.add(new Exit(WIDTH - EXIT_WIDTH, 3 * HEIGHT / 4 - EXIT_HEIGHT, EXIT_WIDTH, EXIT_HEIGHT));
        H_left = new Point(exits.get(0).x - 2, exits.get(0).y + exits.get(0).height / 2);
        H_right = new Point(exits.get(1).x + exits.get(1).width + 2, exits.get(1).y + exits.get(1).height / 2);
        magenta1 = new Point(H_left.x, (int)H_Y - 10);
        magenta2 = new Point(H_right.x, (int)H_Y - 10);
        Random rand = new Random();
        for (int i = 0; i < NUM_PEOPLE; i++) {
            double px, py;
            Person p;
            do {
                px = rand.nextDouble() * (WIDTH - 20) + 10;
                py = rand.nextDouble() * (HEIGHT - 16) + 8;
                p = new Person(px, py);
            } while (collidesWithObstacle(p.getRect()));
            Exit assignedExit = exits.get(i % 2);
            p.myExit = assignedExit;
            p.myAttractionPoint = assignedExit.getCenter();
            p.setupWaypoints();
            people.add(p);
        }
    }

    /**
     * Ciclo principale della simulazione: aggiorna persone e GUI finché non evacuato o superato tick limit.
     * @param panel pannello su cui effettuare il repaint
     */
    public void run(EvacuationPanel panel) {
        System.out.println("Simulazione evacuazione building con flocking, attrattori locali alle uscite e code...");
        while (!allEvacuated() && tick < TICK_LIMIT) {
            tick++;
            for (Person p : people) {
                if (!p.evacuated) {
                    List<Person> neighbors = findNeighbors(p);
                    if (p.phase == 0) {
                        if (!p.waitingInQueue && readyForExit(p)) {
                            int exitIdx = exits.indexOf(p.myExit);
                            if (exitQueue[exitIdx] < 2) {
                                exitQueue[exitIdx]++;
                                p.crossingExit = true;
                            } else {
                                p.waitingInQueue = true;
                                continue;
                            }
                        }
                        p.update(neighbors, obstacles, exits, p.myAttractionPoint);
                        if (p.crossingExit && hasCrossedExit(p)) {
                            int exitIdx = exits.indexOf(p.myExit);
                            exitQueue[exitIdx] = Math.max(0, exitQueue[exitIdx] - 1);
                            p.crossingExit = false;
                            p.phase = 1;
                            p.hasExited = true;
                            p.setupWaypoints();
                        }
                    } else if (p.phase == 1) {
                        p.update(neighbors, obstacles, exits, H_final);
                    }
                }
            }
            checkEvacuations();
            panel.repaint();
            try { Thread.sleep(35); } catch (Exception e) {}

            if (tick % 10 == 0 || allEvacuated()) {
                System.out.printf("Tick %d: %d evacuati\n", tick, countEvacuated());
            }
        }
        System.out.println("Simulazione conclusa.");
    }

    /**
     * Genera ostacoli con un gap minimo tra di essi.
     */
    private void generateSpacedObstacles() {
        Random rand = new Random();
        final int MIN_GAP = 5;
        final int MAX_TRIES = 10000;
        int placed = 0, tries = 0;
        while (placed < NUM_OBSTACLES && tries < MAX_TRIES) {
            int w = rand.nextInt(7) + 4;
            int h = rand.nextInt(5) + 3;
            int x = rand.nextInt(WIDTH - w - 2 * MIN_GAP) + MIN_GAP;
            int y = rand.nextInt(HEIGHT - h - 2 * MIN_GAP) + MIN_GAP;
            Obstacle candidate = new Obstacle(x, y, w, h);
            Rectangle inflated = new Rectangle(x - MIN_GAP, y - MIN_GAP, w + 2*MIN_GAP, h + 2*MIN_GAP);
            boolean ok = true;
            for (Obstacle o : obstacles) {
                if (o.getRect().intersects(inflated)) { ok = false; break; }
            }
            if (ok) { obstacles.add(candidate); placed++; }
            tries++;
        }
        if (placed < NUM_OBSTACLES) {
            System.err.println("Warning: posizionati solo " + placed +
                               " ostacoli su " + NUM_OBSTACLES);
        }
    }

    /** Stampa le posizioni delle persone non evacuate. */
    private void printPeoplePositions() { /* ... */ }

    /** Verifica se tutte le persone sono state evacuate. */
    private boolean allEvacuated() { return countEvacuated() == people.size(); }

    /** Conta le persone evacuate. */
    private int countEvacuated() {
        int c = 0;
        for (Person p : people) if (p.evacuated) c++;
        return c;
    }

    /**
     * Trova i vicini entro NEIGHBOR_DIST.
     * @param p persona di riferimento
     * @return lista di vicini
     */
    private List<Person> findNeighbors(Person p) {
        List<Person> neighbors = new ArrayList<>();
        for (Person other : people) {
            if (other != p && !other.evacuated && p.distance(other) < 6) {
                neighbors.add(other);
            }
        }
        return neighbors;
    }

    /**
     * Verifica se la persona è nella zona dell’uscita.
     * @param p persona da verificare
     * @return true se pronta per entrare in coda
     */
    private boolean readyForExit(Person p) {
        Exit exit = p.myExit;
        Rectangle zone = new Rectangle(exit.x, exit.y, exit.width, exit.height);
        return !p.evacuated && !p.crossingExit && zone.intersects(p.getRect());
    }

    /**
     * Verifica se la persona ha oltrepassato l’uscita.
     * @param p persona da verificare
     * @return true se oltrepassato il bordo dell’edificio
     */
    private boolean hasCrossedExit(Person p) {
        if (p.myExit == exits.get(0)) return p.x < -0.1;
        else return p.x > WIDTH + 0.1;
    }

    /**
     * Marca le persone in area H_final come evacuate e resetta flag di coda.
     */
    private void checkEvacuations() {
        for (Person p : people) {
            if (!p.evacuated) {
                if (p.phase == 1 && distance(p.x, p.y, H_final.x, H_final.y) <= H_RADIUS) {
                    p.evacuated = true;
                    System.out.printf("Persona evacuata a tick %d in posizione (%.2f, %.2f)\n",
                                      tick, p.x, p.y);
                }
                if (p.waitingInQueue && !readyForExit(p)) {
                    p.waitingInQueue = false;
                }
            }
        }
    }

    /**
     * Controlla collisione rettangolare con ostacoli.
     * @param r rettangolo della persona
     * @return true se c’è intersezione
     */
    private boolean collidesWithObstacle(Rectangle r) {
        for (Obstacle o : obstacles) {
            if (o.getRect().intersects(r)) return true;
        }
        return false;
    }

    /**
     * Calcola la distanza Euclidea tra due punti.
     * @param x1 ascissa punto 1
     * @param y1 ordinata punto 1
     * @param x2 ascissa punto 2
     * @param y2 ordinata punto 2
     * @return distanza Euclidea
     */
    private static double distance(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2, dy = y1 - y2;
        return Math.sqrt(dx*dx + dy*dy);
    }
}
