import javax.swing.JFrame;
import java.awt.Color;

public class Main {

    public static void main(String[] args) {

        JFrame window = new JFrame("Jraffic");

        window.setSize(1000, 800);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        window.getContentPane().setBackground(Color.BLACK);

        Map map = new Map();
        window.add(map);

        window.setVisible(true);
    }
}