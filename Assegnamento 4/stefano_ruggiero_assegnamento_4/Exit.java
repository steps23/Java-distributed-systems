package stefano_ruggiero_assegnamento_4;

import java.awt.*;

/**
 * Rappresenta un’uscita dell’edificio.
 * Fornisce dimensioni e centro per attrazione.
 */
public class Exit {
    /** X coordinate of top-left corner */
    public int x;
    /** Y coordinate of top-left corner */
    public int y;
    /** Width in cells */
    public int width;
    /** Height in cells */
    public int height;

    /**
     * Costruttore: definisce posizione e dimensioni dell’uscita.
     * @param x ascissa del vertice superiore sinistro in unità di cella
     * @param y ordinata del vertice superiore sinistro in unità di cella
     * @param width larghezza in unità di cella
     * @param height altezza in unità di cella
     */
    public Exit(int x, int y, int width, int height) {
        this.x = x; this.y = y;
        this.width = width; this.height = height;
    }

    /**
     * Ritorna il rettangolo dell’uscita per il calcolo delle intersezioni.
     * @return rettangolo dell’uscita
     */
    public Rectangle getRect() {
        return new Rectangle(x, y, width, height);
    }

    /**
     * Ritorna il punto centrale dell’uscita.
     * @return punto centrale
     */
    public Point getCenter() {
        return new Point(x + width / 2, y + height / 2);
    }
}
