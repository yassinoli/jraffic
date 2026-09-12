import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Entry point for the Jraffic traffic simulation.
 *
 * Controls:
 *   ↑ Up     = spawn vehicle from south (heading north)
 *   ↓ Down   = spawn vehicle from north (heading south)
 *   → Right  = spawn vehicle from west  (heading east)
 *   ← Left   = spawn vehicle from east  (heading west)
 *   R        = spawn vehicle from random direction
 *   ESC      = exit simulation
 */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame window = new JFrame("Jraffic - Traffic Simulation");

            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setResizable(false);

            SimulationPanel simulation = new SimulationPanel();
            window.add(simulation);
            window.pack();

            window.setLocationRelativeTo(null); // center on screen
            window.setVisible(true);

            // Ensure the panel gets keyboard focus
            simulation.requestFocusInWindow();
        });
    }
}
