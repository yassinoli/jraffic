import java.awt.Color;
import java.awt.Graphics2D;

public class TrafficLight {
    private boolean green;
    private final Direction direction;
    private final int x, y;

    public TrafficLight(Direction direction, int x, int y) {
        this.direction = direction;
        this.x = x;
        this.y = y;
        this.green = false;
    }

    public boolean isGreen() {
        return green;
    }

    public void setGreen(boolean green) {
        this.green = green;
    }

    public Direction getDirection() {
        return direction;
    }

    public void draw(Graphics2D g) {
        int size = 14;
        g.setColor(Color.DARK_GRAY);
        g.fillRoundRect(x - size / 2 - 3, y - size / 2 - 3, size + 6, size + 6, 5, 5);
        g.setColor(green ? new Color(0, 220, 0) : new Color(220, 0, 0));
        g.fillOval(x - size / 2, y - size / 2, size, size);
        g.setColor(green ? new Color(0, 255, 0, 60) : new Color(255, 0, 0, 60));
        g.fillOval(x - size / 2 - 4, y - size / 2 - 4, size + 8, size + 8);
    }
}
