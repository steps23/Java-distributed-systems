package stefano_ruggiero_assegnamento_4;

import java.awt.*;
import javax.swing.*;

/**
 * Panel Swing che disegna la simulazione:
 * - ostacoli
 * - muri esterni
 * - uscite
 * - attrattori
 * - persone (colori in base allo stato)
 */
public class EvacuationPanel extends JPanel {
    /** Simulation reference for rendering */
    private final EvacuationSimulation sim;
    /** Scale factor for drawing */
    static final int SCALE = 12;
    /** Extra offset in pixels around drawing */
    static final int EXTRA_X = 90;

    /**
     * Costruttore: riceve riferimento alla simulazione.
     * @param sim simulazione da renderizzare
     */
    public EvacuationPanel(EvacuationSimulation sim) {
        this.sim = sim;
        setPreferredSize(new Dimension(
            EvacuationSimulation.WIDTH * SCALE + 2 * EXTRA_X,
            (EvacuationSimulation.HEIGHT + 15) * SCALE + 2 * EXTRA_X
        ));
        setBackground(Color.WHITE);
    }

    /**
     * Disegna ogni tick:
     * - ostacoli (grigio)
     * - muri (nero)
     * - uscite (verde)
     * - attrattori piccoli e grande
     * - persone con colore in base allo stato
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int offsetX = EXTRA_X;
        int offsetY = EXTRA_X / 2;
        Graphics2D g2 = (Graphics2D) g;

        // Ostacoli
        g.setColor(Color.LIGHT_GRAY);
        for (Obstacle o : sim.obstacles) {
            g.fillRect(offsetX + o.x * SCALE, offsetY + o.y * SCALE,
                       o.width * SCALE, o.height * SCALE);
        }

        // Muri esterni
        g.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(5));
        int W = EvacuationSimulation.WIDTH * SCALE;
        int H = EvacuationSimulation.HEIGHT * SCALE;
        g2.drawRect(offsetX, offsetY, W - 1, H - 1);

        // Uscite
        g.setColor(Color.GREEN.darker());
        for (Exit e : sim.exits) {
            g.fillRect(offsetX + e.x * SCALE, offsetY + e.y * SCALE,
                       e.width * SCALE, e.height * SCALE);
        }

        // Attrattori piccoli (blu)
        g.setColor(Color.BLUE);
        int r = 7;
        g.fillOval(offsetX + EvacuationSimulation.H_left.x * SCALE - r,
                   offsetY + EvacuationSimulation.H_left.y * SCALE - r, r*2, r*2);
        g.fillOval(offsetX + EvacuationSimulation.H_right.x * SCALE - r,
                   offsetY + EvacuationSimulation.H_right.y * SCALE - r, r*2, r*2);

        // Nuovi attrattori sotto le porte
        int yAttr = (int) EvacuationSimulation.H_final.y;
        int xL = sim.exits.get(0).x - 2;
        int xR = sim.exits.get(1).x + sim.exits.get(1).width + 2;
        g.fillOval(offsetX + xL * SCALE - r, offsetY + yAttr * SCALE - r, r*2, r*2);
        g.fillOval(offsetX + xR * SCALE - r, offsetY + yAttr * SCALE - r, r*2, r*2);

        // Attrattore finale H (blu grande)
        int ax = offsetX + EvacuationSimulation.H_final.x * SCALE;
        int ay = offsetY + EvacuationSimulation.H_final.y * SCALE;
        int rf = EvacuationSimulation.H_RADIUS * SCALE;
        g.fillOval(ax - rf, ay - rf, rf*2, rf*2);

        // Persone: colore in base allo stato
        for (Person p : sim.people) {
            if (p.evacuated) g.setColor(Color.GRAY);
            else if (p.waitingInQueue) g.setColor(Color.ORANGE);
            else g.setColor(Color.RED);
            int px = offsetX + (int)(p.x * SCALE);
            int py = offsetY + (int)(p.y * SCALE);
            g.fillOval(px - 4, py - 4, 9, 9);
        }
    }
}
