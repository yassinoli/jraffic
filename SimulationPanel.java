import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Main simulation panel that orchestrates the entire traffic simulation.
 * Handles rendering of roads, traffic lights, vehicles, and the HUD.
 * Processes keyboard input for spawning vehicles and manages the game loop.
 */
public class SimulationPanel extends JPanel implements KeyListener {

    // ===== Layout constants =====
    public static final int WINDOW_WIDTH = 1000;
    public static final int WINDOW_HEIGHT = 800;
    public static final int LANE_WIDTH = 40;
    public static final int ROAD_WIDTH = LANE_WIDTH * 2; // 2 lanes per road

    // Intersection center
    public static final int CENTER_X = WINDOW_WIDTH / 2;
    public static final int CENTER_Y = WINDOW_HEIGHT / 2;

    // Intersection boundaries
    public static final int INT_LEFT = CENTER_X - ROAD_WIDTH / 2;
    public static final int INT_RIGHT = CENTER_X + ROAD_WIDTH / 2;
    public static final int INT_TOP = CENTER_Y - ROAD_WIDTH / 2;
    public static final int INT_BOTTOM = CENTER_Y + ROAD_WIDTH / 2;

    // Lane length from stop line to edge of screen (spawn point)
    public static final int LANE_LENGTH_VERTICAL = INT_TOP; // from top edge to intersection
    public static final int LANE_LENGTH_HORIZONTAL = INT_LEFT; // from left edge to intersection

    // Lane capacity: floor(lane_length / (vehicle_length + safety_gap))
    public static final int LANE_CAPACITY_VERTICAL =
            (int) Math.floor((double) LANE_LENGTH_VERTICAL / (Vehicle.WIDTH + Vehicle.SAFETY_GAP));
    public static final int LANE_CAPACITY_HORIZONTAL =
            (int) Math.floor((double) LANE_LENGTH_HORIZONTAL / (Vehicle.WIDTH + Vehicle.SAFETY_GAP));

    // Spawn cooldown to prevent spamming (milliseconds)
    private static final long SPAWN_COOLDOWN_MS = 500;

    // ===== Simulation state =====
    private final List<Vehicle> vehicles = new CopyOnWriteArrayList<>();
    private final Map<Direction, TrafficLight> trafficLights = new HashMap<>();
    private final TrafficLightController lightController;
    private final Map<Direction, Long> lastSpawnTime = new HashMap<>();
    private final Timer gameTimer;
    private boolean running = true;

    // Statistics
    private int totalSpawned = 0;
    private int totalExited = 0;

    // Road colors
    private static final Color ROAD_COLOR = new Color(55, 55, 60);
    private static final Color ROAD_BORDER_COLOR = new Color(90, 90, 95);
    private static final Color LANE_MARKING_COLOR = new Color(200, 200, 200);
    private static final Color GRASS_COLOR = new Color(34, 85, 34);
    private static final Color SIDEWALK_COLOR = new Color(140, 140, 130);
    private static final Color CROSSWALK_COLOR = new Color(220, 220, 220);

    public SimulationPanel() {
        setPreferredSize(new Dimension(WINDOW_WIDTH, WINDOW_HEIGHT));
        setBackground(GRASS_COLOR);
        setFocusable(true);
        addKeyListener(this);

        // Initialize intersection bounds for Vehicle class
        Vehicle.setIntersectionBounds(INT_LEFT, INT_TOP, INT_RIGHT, INT_BOTTOM);

        // Create traffic lights positioned at each lane's stop line
        // NORTH light: vehicles coming from top, stop line just above intersection
        trafficLights.put(Direction.NORTH,
                new TrafficLight(Direction.NORTH, INT_LEFT - 18, INT_TOP - 8));
        // SOUTH light: vehicles coming from bottom, stop line just below intersection
        trafficLights.put(Direction.SOUTH,
                new TrafficLight(Direction.SOUTH, INT_RIGHT + 18, INT_BOTTOM + 8));
        // EAST light: vehicles coming from right, stop line just right of intersection
        trafficLights.put(Direction.EAST,
                new TrafficLight(Direction.EAST, INT_RIGHT + 8, INT_TOP - 18));
        // WEST light: vehicles coming from left, stop line just left of intersection
        trafficLights.put(Direction.WEST,
                new TrafficLight(Direction.WEST, INT_LEFT - 8, INT_BOTTOM + 18));

        // Initialize traffic light controller
        lightController = new TrafficLightController(trafficLights);
        lightController.setLaneCapacity(Direction.NORTH, LANE_CAPACITY_VERTICAL);
        lightController.setLaneCapacity(Direction.SOUTH, LANE_CAPACITY_VERTICAL);
        lightController.setLaneCapacity(Direction.EAST, LANE_CAPACITY_HORIZONTAL);
        lightController.setLaneCapacity(Direction.WEST, LANE_CAPACITY_HORIZONTAL);

        // Initialize spawn times
        for (Direction d : Direction.values()) {
            lastSpawnTime.put(d, 0L);
        }

        // Game loop at ~60 FPS
        gameTimer = new Timer(16, e -> {
            if (running) {
                updateSimulation();
                repaint();
            }
        });
        gameTimer.start();
    }

    // ===== Simulation Update =====

    private void updateSimulation() {
        // Update traffic light controller with current lane occupancy
        Map<Direction, List<Vehicle>> laneVehicles = getLaneVehicleMap();
        lightController.setLaneVehicles(laneVehicles);
        lightController.update();

        // Update vehicles: stop/go logic
        for (Vehicle v : vehicles) {
            if (v.isOffScreen()) continue;

            boolean shouldStop = false;

            // 1. Check traffic light
            if (v.shouldStopAtLight() && !lightController.isGreen(v.getOrigin())) {
                double distToStop = distanceToStopLine(v);
                if (distToStop <= Vehicle.SAFETY_GAP + Vehicle.SPEED && distToStop >= -Vehicle.SPEED * 2) {
                    shouldStop = true;
                }
            }

            // 2. Check distance to vehicle ahead (safety distance)
            Vehicle ahead = findVehicleAhead(v);
            if (ahead != null) {
                double dist = v.distanceTo(ahead);
                if (dist <= Vehicle.SAFETY_GAP) {
                    shouldStop = true;
                }
            }

            v.setStopped(shouldStop);
            v.update();
        }

        // Remove off-screen vehicles
        List<Vehicle> toRemove = new ArrayList<>();
        for (Vehicle v : vehicles) {
            if (v.isOffScreen()) {
                toRemove.add(v);
            }
        }
        totalExited += toRemove.size();
        vehicles.removeAll(toRemove);
    }

    /**
     * Build a map from each direction to the list of vehicles in that lane.
     */
    private Map<Direction, List<Vehicle>> getLaneVehicleMap() {
        Map<Direction, List<Vehicle>> map = new HashMap<>();
        for (Direction d : Direction.values()) {
            map.put(d, new ArrayList<>());
        }
        for (Vehicle v : vehicles) {
            map.get(v.getOrigin()).add(v);
        }
        return map;
    }

    /**
     * Calculate the distance from a vehicle to the stop line of its lane.
     */
    private double distanceToStopLine(Vehicle v) {
        switch (v.getOrigin()) {
            case NORTH: return INT_TOP - v.getFrontY();
            case SOUTH: return v.getFrontY() - INT_BOTTOM;
            case EAST:  return v.getFrontX() - INT_RIGHT;
            case WEST:  return INT_LEFT - v.getFrontX();
        }
        return Double.MAX_VALUE;
    }

    /**
     * Find the nearest vehicle ahead in the same lane.
     */
    private Vehicle findVehicleAhead(Vehicle current) {
        Vehicle closest = null;
        double minDist = Double.MAX_VALUE;

        for (Vehicle other : vehicles) {
            if (other == current) continue;
            if (other.isOffScreen()) continue;

            if (current.isAhead(other)) {
                double d = current.distanceTo(other);
                if (d >= 0 && d < minDist) {
                    minDist = d;
                    closest = other;
                }
            }
        }
        return closest;
    }

    // ===== Vehicle Spawning =====

    /**
     * Spawn a vehicle from the given direction with a random turn type.
     * Respects spawn cooldown and lane capacity.
     */
    private void spawnVehicle(Direction origin) {
        long now = System.currentTimeMillis();

        // Check cooldown
        if (now - lastSpawnTime.get(origin) < SPAWN_COOLDOWN_MS) return;

        // Check lane capacity
        long laneCount = vehicles.stream()
                .filter(v -> v.getOrigin() == origin && !v.hasCrossedIntersection() && !v.isOffScreen())
                .count();
        int capacity = (origin == Direction.NORTH || origin == Direction.SOUTH)
                ? LANE_CAPACITY_VERTICAL : LANE_CAPACITY_HORIZONTAL;
        if (laneCount >= capacity) return;

        // Check safe distance from last vehicle in lane
        if (!isSafeToSpawn(origin)) return;

        TurnType turn = TurnType.random();
        double x, y;

        // Spawn at the edge of the screen in the correct incoming lane
        switch (origin) {
            case NORTH:
                // Coming from top, incoming lane is the left lane (going down)
                x = CENTER_X - LANE_WIDTH / 2.0;
                y = -Vehicle.HEIGHT;
                break;
            case SOUTH:
                // Coming from bottom, incoming lane is the right lane (going up)
                x = CENTER_X + LANE_WIDTH / 2.0;
                y = WINDOW_HEIGHT + Vehicle.HEIGHT;
                break;
            case EAST:
                // Coming from right, incoming lane is the top lane (going left)
                x = WINDOW_WIDTH + Vehicle.WIDTH;
                y = CENTER_Y - LANE_WIDTH / 2.0;
                break;
            case WEST:
                // Coming from left, incoming lane is the bottom lane (going right)
                x = -Vehicle.WIDTH;
                y = CENTER_Y + LANE_WIDTH / 2.0;
                break;
            default:
                return;
        }

        Vehicle v = new Vehicle(origin, turn, x, y);
        vehicles.add(v);
        lastSpawnTime.put(origin, now);
        totalSpawned++;
    }

    /**
     * Check if there's enough safe distance from the last spawned vehicle in this lane.
     */
    private boolean isSafeToSpawn(Direction origin) {
        double safeDistance = Vehicle.WIDTH + Vehicle.SAFETY_GAP + 10;
        for (Vehicle v : vehicles) {
            if (v.getOrigin() != origin || v.isOffScreen()) continue;
            switch (origin) {
                case NORTH:
                    if (v.getY() < safeDistance) return false;
                    break;
                case SOUTH:
                    if (v.getY() > WINDOW_HEIGHT - safeDistance) return false;
                    break;
                case EAST:
                    if (v.getX() > WINDOW_WIDTH - safeDistance) return false;
                    break;
                case WEST:
                    if (v.getX() < safeDistance) return false;
                    break;
            }
        }
        return true;
    }

    // ===== Rendering =====

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawBackground(g);
        drawRoads(g);
        drawIntersection(g);
        drawDirectionArrows(g);
        drawLaneMarkings(g);
        drawStopLines(g);
        drawTrafficLights(g);
        drawVehicles(g);
    }

    private void drawBackground(Graphics2D g) {
        // Grass background
        g.setColor(GRASS_COLOR);
        g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    private void drawRoads(Graphics2D g) {
        // Sidewalks (slightly wider than roads)
        int sw = 6;
        g.setColor(SIDEWALK_COLOR);
        // Vertical sidewalks
        g.fillRect(INT_LEFT - sw, 0, sw, INT_TOP);
        g.fillRect(INT_RIGHT, 0, sw, INT_TOP);
        g.fillRect(INT_LEFT - sw, INT_BOTTOM, sw, WINDOW_HEIGHT - INT_BOTTOM);
        g.fillRect(INT_RIGHT, INT_BOTTOM, sw, WINDOW_HEIGHT - INT_BOTTOM);
        // Horizontal sidewalks
        g.fillRect(0, INT_TOP - sw, INT_LEFT, sw);
        g.fillRect(0, INT_BOTTOM, INT_LEFT, sw);
        g.fillRect(INT_RIGHT, INT_TOP - sw, WINDOW_WIDTH - INT_RIGHT, sw);
        g.fillRect(INT_RIGHT, INT_BOTTOM, WINDOW_WIDTH - INT_RIGHT, sw);

        // Road surfaces
        g.setColor(ROAD_COLOR);
        // Vertical road (north-south)
        g.fillRect(INT_LEFT, 0, ROAD_WIDTH, WINDOW_HEIGHT);
        // Horizontal road (east-west)
        g.fillRect(0, INT_TOP, WINDOW_WIDTH, ROAD_WIDTH);

        // Road borders
        g.setColor(ROAD_BORDER_COLOR);
        g.setStroke(new BasicStroke(2));
        // Vertical road borders (only outside intersection)
        g.drawLine(INT_LEFT, 0, INT_LEFT, INT_TOP);
        g.drawLine(INT_RIGHT, 0, INT_RIGHT, INT_TOP);
        g.drawLine(INT_LEFT, INT_BOTTOM, INT_LEFT, WINDOW_HEIGHT);
        g.drawLine(INT_RIGHT, INT_BOTTOM, INT_RIGHT, WINDOW_HEIGHT);
        // Horizontal road borders
        g.drawLine(0, INT_TOP, INT_LEFT, INT_TOP);
        g.drawLine(0, INT_BOTTOM, INT_LEFT, INT_BOTTOM);
        g.drawLine(INT_RIGHT, INT_TOP, WINDOW_WIDTH, INT_TOP);
        g.drawLine(INT_RIGHT, INT_BOTTOM, WINDOW_WIDTH, INT_BOTTOM);
    }

    private void drawIntersection(Graphics2D g) {
        // Intersection box - slightly lighter
        g.setColor(new Color(65, 65, 70));
        g.fillRect(INT_LEFT, INT_TOP, ROAD_WIDTH, ROAD_WIDTH);

        // Crosswalk patterns
        g.setColor(new Color(CROSSWALK_COLOR.getRed(), CROSSWALK_COLOR.getGreen(),
                CROSSWALK_COLOR.getBlue(), 80));
        int cw = 5; // crosswalk stripe width
        int cs = 8; // crosswalk stripe spacing
        // North crosswalk
        for (int x = INT_LEFT; x < INT_RIGHT; x += cs) {
            g.fillRect(x, INT_TOP - 2, cw, 4);
        }
        // South crosswalk
        for (int x = INT_LEFT; x < INT_RIGHT; x += cs) {
            g.fillRect(x, INT_BOTTOM - 2, cw, 4);
        }
        // East crosswalk
        for (int y = INT_TOP; y < INT_BOTTOM; y += cs) {
            g.fillRect(INT_RIGHT - 2, y, 4, cw);
        }
        // West crosswalk
        for (int y = INT_TOP; y < INT_BOTTOM; y += cs) {
            g.fillRect(INT_LEFT - 2, y, 4, cw);
        }
    }

    private void drawLaneMarkings(Graphics2D g) {
        // Dashed center line to separate incoming/outgoing lanes
        g.setColor(LANE_MARKING_COLOR);
        float[] dash = {12f, 8f};
        g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));

        // Vertical center line (above intersection)
        g.drawLine(CENTER_X, 0, CENTER_X, INT_TOP - 4);
        // Vertical center line (below intersection)
        g.drawLine(CENTER_X, INT_BOTTOM + 4, CENTER_X, WINDOW_HEIGHT);
        // Horizontal center line (left of intersection)
        g.drawLine(0, CENTER_Y, INT_LEFT - 4, CENTER_Y);
        // Horizontal center line (right of intersection)
        g.drawLine(INT_RIGHT + 4, CENTER_Y, WINDOW_WIDTH, CENTER_Y);

        g.setStroke(new BasicStroke(1));
    }

    private void drawDirectionArrows(Graphics2D g) {
        g.setColor(new Color(150, 150, 150, 80));
        g.setStroke(new BasicStroke(2));
        int arrowSize = 8;

        // Vertical lanes - above intersection
        int laneLeftX = INT_LEFT + LANE_WIDTH / 2;  // incoming from north (going down)
        int laneRightX = CENTER_X + LANE_WIDTH / 2; // outgoing (going up)
        for (int y = 60; y < INT_TOP - 40; y += 80) {
            drawArrow(g, laneLeftX, y, laneLeftX, y + 20, arrowSize); // ↓
            drawArrow(g, laneRightX, y + 20, laneRightX, y, arrowSize); // ↑
        }

        // Vertical lanes - below intersection
        for (int y = INT_BOTTOM + 40; y < WINDOW_HEIGHT - 60; y += 80) {
            drawArrow(g, laneLeftX, y, laneLeftX, y + 20, arrowSize); // ↓
            drawArrow(g, laneRightX, y + 20, laneRightX, y, arrowSize); // ↑
        }

        // Horizontal lanes - left of intersection
        int laneTopY = INT_TOP + LANE_WIDTH / 2;     // outgoing (going left)
        int laneBottomY = CENTER_Y + LANE_WIDTH / 2; // incoming from west (going right)
        for (int x = 60; x < INT_LEFT - 40; x += 80) {
            drawArrow(g, x + 20, laneTopY, x, laneTopY, arrowSize); // ←
            drawArrow(g, x, laneBottomY, x + 20, laneBottomY, arrowSize); // →
        }

        // Horizontal lanes - right of intersection
        for (int x = INT_RIGHT + 40; x < WINDOW_WIDTH - 60; x += 80) {
            drawArrow(g, x + 20, laneTopY, x, laneTopY, arrowSize); // ←
            drawArrow(g, x, laneBottomY, x + 20, laneBottomY, arrowSize); // →
        }

        g.setStroke(new BasicStroke(1));
    }

    /**
     * Draw an arrow from (x1,y1) to (x2,y2) with an arrowhead.
     */
    private void drawArrow(Graphics2D g, int x1, int y1, int x2, int y2, int headSize) {
        g.drawLine(x1, y1, x2, y2);
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len == 0) return;
        double ux = dx / len;
        double uy = dy / len;
        // Arrowhead
        int ax1 = (int) (x2 - headSize * (ux + uy * 0.5));
        int ay1 = (int) (y2 - headSize * (uy - ux * 0.5));
        int ax2 = (int) (x2 - headSize * (ux - uy * 0.5));
        int ay2 = (int) (y2 - headSize * (uy + ux * 0.5));
        g.fillPolygon(new int[]{x2, ax1, ax2}, new int[]{y2, ay1, ay2}, 3);
    }

    private void drawStopLines(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3));

        // Stop lines at each entry to the intersection
        // North stop line (for vehicles going south)
        g.drawLine(INT_LEFT, INT_TOP, CENTER_X, INT_TOP);
        // South stop line (for vehicles going north)
        g.drawLine(CENTER_X, INT_BOTTOM, INT_RIGHT, INT_BOTTOM);
        // East stop line (for vehicles going west)
        g.drawLine(INT_RIGHT, INT_TOP, INT_RIGHT, CENTER_Y);
        // West stop line (for vehicles going east)
        g.drawLine(INT_LEFT, CENTER_Y, INT_LEFT, INT_BOTTOM);

        g.setStroke(new BasicStroke(1));
    }

    private void drawTrafficLights(Graphics2D g) {
        for (TrafficLight tl : trafficLights.values()) {
            tl.draw(g);
        }
    }

    private void drawVehicles(Graphics2D g) {
        for (Vehicle v : vehicles) {
            v.draw(g);
        }
    }


    // ===== Keyboard Input =====

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
                spawnVehicle(Direction.SOUTH); // UP arrow = vehicle comes from south
                break;
            case KeyEvent.VK_DOWN:
                spawnVehicle(Direction.NORTH); // DOWN arrow = vehicle comes from north
                break;
            case KeyEvent.VK_RIGHT:
                spawnVehicle(Direction.WEST); // RIGHT arrow = vehicle comes from west
                break;
            case KeyEvent.VK_LEFT:
                spawnVehicle(Direction.EAST); // LEFT arrow = vehicle comes from east
                break;
            case KeyEvent.VK_R:
                spawnVehicle(Direction.random());
                break;
            case KeyEvent.VK_ESCAPE:
                running = false;
                gameTimer.stop();
                System.exit(0);
                break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}
    @Override
    public void keyTyped(KeyEvent e) {}

    /**
     * Stop the simulation.
     */
    public void stopSimulation() {
        running = false;
        gameTimer.stop();
    }
}
