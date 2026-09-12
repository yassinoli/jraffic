import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;

public class Map extends JPanel {

    public Map() {
        setBackground(Color.BLACK);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.setColor(Color.WHITE);

        g.drawLine(400, 0, 400, 300);
        g.drawLine(600, 0, 600, 300);

        g.drawLine(400, 500, 400, 800);
        g.drawLine(600, 500, 600, 800);

        g.drawLine(0, 300, 300, 300);
        g.drawLine(0, 500, 300, 500);

        g.drawLine(700, 300, 1000, 300);
        g.drawLine(700, 500, 1000, 500);
    }
}