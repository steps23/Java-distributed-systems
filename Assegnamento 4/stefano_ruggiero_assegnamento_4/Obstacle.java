package stefano_ruggiero_assegnamento_4;

import java.awt.*;

/**
 * Rappresenta un ostacolo rettangolare nella simulazione.
 */
public class Obstacle {
    /** X coordinate of top-left corner */
    int x, /** Y coordinate of top-left corner */ y,
        /** Width in cells */ width,
        /** Height in cells */ height;

    /**
     * Costruttore: crea un ostacolo rettangolare.
     * @param x ascissa del vertice superiore sinistro in unità di cella
     * @param y ordinata del vertice superiore sinistro in unità di cella
     * @param w larghezza in unità di cella
     * @param h altezza in unità di cella
     */
    public Obstacle(int x, int y, int w, int h) {
        this.x = x; this.y = y;
        this.width = w; this.height = h;
    }

    /**
     * Ritorna il rettangolo di occupazione dell’ostacolo.
     * @return rettangolo di occupazione
     */
    public Rectangle getRect() {
        return new Rectangle(x, y, width, height);
    }
}
