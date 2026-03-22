package stefano_ruggiero_assegnamento_4;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Rappresenta una singola persona nella simulazione di evacuazione.
 * Implementa comportamenti di flocking, evitamento ostacoli e gestione dei waypoint.
 */
public class Person {
    /** X coordinate of person */
    public double x;
    /** Y coordinate of person */
    public double y;
    /** Velocity in X direction */
    public double vx = 0;
    /** Velocity in Y direction */
    public double vy = 0;
    /** Whether the person has reached the final attraction area */
    public boolean evacuated = false;
    /** Whether the person has passed through the exit */
    public boolean hasExited = false;
    /** Whether the person is currently crossing an exit */
    public boolean crossingExit = false;
    /** Whether the person is waiting in the exit queue */
    public boolean waitingInQueue = false;
    /** Current phase: 0 = towards exit, 1 = towards final point H */
    public int phase = 0;
    /** Assigned exit for this person */
    public Exit myExit;
    /** Current attraction point (center of exit or final H) */
    public Point myAttractionPoint;
    /** Counter to detect if the person is stuck */
    public int stuckCounter = 0;

    // --- Flocking behavior parameters ---
    private static final double NEIGHBOR_DIST = 6.0;
    private static final double COHESION_WEIGHT = 1.0;
    private static final double SEPARATION_WEIGHT = 1.5;
    private static final double ALIGNMENT_WEIGHT = 1.0;

    /** Index of the current waypoint in the path */
    public int waypointIdx = 0;
    /** List of waypoints for the evacuation path */
    public List<double[]> waypoints = new ArrayList<>();

    /**
     * Costruttore: inizializza la posizione della persona.
     * @param x coordinata X iniziale
     * @param y coordinata Y iniziale
     */
    public Person(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Imposta i waypoint per il percorso verso l’uscita e il punto H finale.
     * Deve essere chiamato dopo aver assegnato myExit e myAttractionPoint.
     */
    public void setupWaypoints() {
        waypoints.clear();
        // 1. Centro della porta
        waypoints.add(new double[] { myAttractionPoint.x, myAttractionPoint.y });
        // 2. Punto subito fuori dalla porta
        int verso = (myExit.x < 10) ? -1 : 1;
        double outX = myAttractionPoint.x + verso * 3;
        double outY = myAttractionPoint.y;
        waypoints.add(new double[] { outX, outY });
        // 3. Waypoint intermedio al livello di H
        waypoints.add(new double[] { outX, EvacuationSimulation.H_final.y });
        // 4. Punto finale H
        waypoints.add(new double[] { EvacuationSimulation.H_final.x, EvacuationSimulation.H_final.y });
        waypointIdx = 0;
    }

    /**
     * Ritorna il rettangolo di collisione per il calcolo delle intersezioni.
     * @return rettangolo di collisione
     */
    public Rectangle getRect() {
        return new Rectangle((int) x - 1, (int) y - 1, 3, 3);
    }

    /**
     * Cohesion: steer towards average position of neighbors.
     */
    private double[] cohesion(List<Person> neighbors) {
        if (neighbors.isEmpty()) return new double[]{0, 0};
        double sumX = 0, sumY = 0;
        for (Person other : neighbors) {
            sumX += other.x;
            sumY += other.y;
        }
        double avgX = sumX / neighbors.size();
        double avgY = sumY / neighbors.size();
        double vx = avgX - this.x;
        double vy = avgY - this.y;
        double mag = Math.hypot(vx, vy);
        if (mag > 0) {
            vx /= mag;
            vy /= mag;
        }
        return new double[]{vx, vy};
    }

    /**
     * Separation: steer away from close neighbors.
     */
    private double[] separation(List<Person> neighbors) {
        double steerX = 0, steerY = 0;
        for (Person other : neighbors) {
            double d = distance(other);
            if (d > 0) {
                steerX += (this.x - other.x) / d;
                steerY += (this.y - other.y) / d;
            }
        }
        if (!neighbors.isEmpty()) {
            steerX /= neighbors.size();
            steerY /= neighbors.size();
        }
        double mag = Math.hypot(steerX, steerY);
        if (mag > 0) {
            steerX /= mag;
            steerY /= mag;
        }
        return new double[]{steerX, steerY};
    }

    /**
     * Alignment: steer towards average heading of neighbors.
     */
    private double[] alignment(List<Person> neighbors) {
        if (neighbors.isEmpty()) return new double[]{0, 0};
        double sumVx = 0, sumVy = 0;
        for (Person other : neighbors) {
            sumVx += other.vx;
            sumVy += other.vy;
        }
        double avgVx = sumVx / neighbors.size();
        double avgVy = sumVy / neighbors.size();
        double steerX = avgVx - this.vx;
        double steerY = avgVy - this.vy;
        double mag = Math.hypot(steerX, steerY);
        if (mag > 0) {
            steerX /= mag;
            steerY /= mag;
        }
        return new double[]{steerX, steerY};
    }

    /**
     * Aggiorna la posizione e lo stato della persona:
     * - calcola vettore verso waypoint
     * - aggiunge evitamento ostacoli e comportamenti di flocking
     * - normalizza e applica il passo
     * - gestisce cambio waypoint e flag di evacuazione
     *
     * @param neighbors lista dei vicini entro NEIGHBOR_DIST
     * @param obstacles lista di ostacoli presenti
     * @param exits     lista delle uscite
     * @param goal      punto di attrazione attuale (porta o H)
     */
    public void update(List<Person> neighbors, List<Obstacle> obstacles,
                       List<Exit> exits, Point goal) {
        if (evacuated) return;

        double[] wp = waypoints.get(waypointIdx);
        double dx = wp[0] - x;
        double dy = wp[1] - y;

        double[] avoid = avoidObstacles(obstacles);
        dx += avoid[0];
        dy += avoid[1];

        double[] sep = separation(neighbors);
        double[] ali = alignment(neighbors);
        double[] coh = cohesion(neighbors);
        dx += SEPARATION_WEIGHT * sep[0] + ALIGNMENT_WEIGHT * ali[0] + COHESION_WEIGHT * coh[0];
        dy += SEPARATION_WEIGHT * sep[1] + ALIGNMENT_WEIGHT * ali[1] + COHESION_WEIGHT * coh[1];

        double dist = Math.hypot(dx, dy);
        double step = 0.5;

        if (dist < 1.2 && waypointIdx < waypoints.size() - 1) {
            waypointIdx++;
        }

        if (dist > 1e-4) {
            vx = step * dx / dist;
            vy = step * dy / dist;

            Point2D ahead = new Point2D.Double(x + vx * 2, y + vy * 2);
            for (Obstacle o : obstacles) {
                if (o.getRect().contains(ahead)) {
                    double angle = Math.toRadians(100);
                    double cos = Math.cos(angle), sin = Math.sin(angle);
                    double newVx = vx * cos - vy * sin;
                    double newVy = vx * sin + vy * cos;
                    vx = newVx;
                    vy = newVy;
                    break;
                }
            }

            x += vx;
            y += vy;
        }

        if (waypointIdx == waypoints.size() - 1) {
            double dH = Math.hypot(x - EvacuationSimulation.H_final.x,
                                  y - EvacuationSimulation.H_final.y);
            if (dH <= EvacuationSimulation.H_RADIUS + 1.0) {
                evacuated = true;
            }
        }
    }

    /**
     * Calcola la distanza Euclidea tra questa persona e un’altra.
     * @param other la persona di cui calcolare la distanza
     * @return distanza Euclidea
     */
    public double distance(Person other) {
        return Math.hypot(this.x - other.x, this.y - other.y);
    }

    /**
     * Calcola un vettore di repulsione da ostacoli vicini
     * usando un campo inversamente proporzionale al quadrato della distanza.
     */
    private double[] avoidObstacles(List<Obstacle> obstacles) {
        double ax = 0, ay = 0;
        double desiredSeparation = 6.0;
        for (Obstacle o : obstacles) {
            Rectangle r = o.getRect();
            double closestX = Math.max(r.x, Math.min(x, r.x + r.width));
            double closestY = Math.max(r.y, Math.min(y, r.y + r.height));
            double dx = x - closestX;
            double dy = y - closestY;
            double dist = Math.hypot(dx, dy);
            if (dist < desiredSeparation && dist > 0) {
                double strength = 1.0 / (dist * dist);
                ax += (dx / dist) * strength;
                ay += (dy / dist) * strength;
            }
        }
        return new double[]{ax, ay};
    }
}
