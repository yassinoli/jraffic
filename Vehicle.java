import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a vehicle in the traffic simulation.
 * Each vehicle has a direction it comes from, a turn type, and a color based on its route.
 */
public class Vehicle {
    // Vehicle dimensions
    public static final int WIDTH = 30;
    public static final int HEIGHT = 18;
    public static final double SPEED = 2.0;
    public static final int SAFETY_GAP = 25;

    // Position and movement
    private double x, y;
    private double angle; // in radians, 0 = right, PI/2 = down
    private final Direction origin;
    private final TurnType turnType;
    private final Color color;
    private boolean stopped;
    private boolean crossedIntersection;
    private boolean turning;
    private boolean turnComplete;
    private boolean offScreen;
    private final List<Point2D.Double> path = new ArrayList<>();
    private int waypointIndex;

    // Intersection boundaries (set by simulation)
    private static int intLeft, intRight, intTop, intBottom;
    private static int intCenterX, intCenterY;

    public static void setIntersectionBounds(int left, int top, int right, int bottom) {
        intLeft = left;
        intTop = top;
        intRight = right;
        intBottom = bottom;
        intCenterX = (left + right) / 2;
        intCenterY = (top + bottom) / 2;
    }

    public Vehicle(Direction origin, TurnType turnType, double x, double y) {
        this.origin = origin;
        this.turnType = turnType;
        this.x = x;
        this.y = y;
        this.stopped = false;
        this.crossedIntersection = false;
        this.turning = false;
        this.turnComplete = false;
        this.offScreen = false;
        this.waypointIndex = 0;

        // Set initial angle based on direction of travel
        switch (origin) {
            case NORTH: this.angle = Math.PI / 2; break;   // heading south (down)
            case SOUTH: this.angle = -Math.PI / 2; break;  // heading north (up)
            case EAST:  this.angle = Math.PI; break;        // heading west (left)
            case WEST:  this.angle = 0; break;              // heading east (right)
        }

        // Color based on turn type:
        // GREEN = straight, ORANGE = right turn, CYAN = left turn
        switch (turnType) {
            case STRAIGHT: this.color = new Color(46, 204, 113); break;  // green
            case RIGHT:    this.color = new Color(243, 156, 18); break;  // orange
            case LEFT:     this.color = new Color(52, 152, 219); break;  // blue
            default:       this.color = Color.WHITE; break;
        }

        buildRoutePath();
    }

    /**
     * Update vehicle position. Returns true if vehicle is still active.
     */
    public void update() {
        if (stopped || offScreen) return;

        followRoutePath();

        if (!crossedIntersection && isInsideIntersection()) {
            crossedIntersection = true;
        }

        if (crossedIntersection && turnType != TurnType.STRAIGHT && !turnComplete) {
            turning = true;
        }

        if (crossedIntersection && !isInsideIntersection()) {
            turning = false;
            turnComplete = true;
        }

        // Check if off screen
        if (x < -100 || x > 1100 || y < -100 || y > 900) {
            offScreen = true;
        }
    }

    private void buildRoutePath() {
        int laneW = SimulationPanel.LANE_WIDTH;
        double northSouthLeftLaneX = intCenterX - laneW / 2.0;
        double northSouthRightLaneX = intCenterX + laneW / 2.0;
        double eastWestTopLaneY = intCenterY - laneW / 2.0;
        double eastWestBottomLaneY = intCenterY + laneW / 2.0;

        switch (origin) {
            case NORTH:
                if (turnType == TurnType.STRAIGHT) {
                    path.add(new Point2D.Double(northSouthLeftLaneX, 900));
                } else if (turnType == TurnType.RIGHT) {
                    path.add(new Point2D.Double(northSouthLeftLaneX, eastWestTopLaneY));
                    path.add(new Point2D.Double(-100, eastWestTopLaneY));
                } else {
                    path.add(new Point2D.Double(northSouthLeftLaneX, eastWestBottomLaneY));
                    path.add(new Point2D.Double(1100, eastWestBottomLaneY));
                }
                break;
            case SOUTH:
                if (turnType == TurnType.STRAIGHT) {
                    path.add(new Point2D.Double(northSouthRightLaneX, -100));
                } else if (turnType == TurnType.RIGHT) {
                    path.add(new Point2D.Double(northSouthRightLaneX, eastWestBottomLaneY));
                    path.add(new Point2D.Double(1100, eastWestBottomLaneY));
                } else {
                    path.add(new Point2D.Double(northSouthRightLaneX, eastWestTopLaneY));
                    path.add(new Point2D.Double(-100, eastWestTopLaneY));
                }
                break;
            case EAST:
                if (turnType == TurnType.STRAIGHT) {
                    path.add(new Point2D.Double(-100, eastWestTopLaneY));
                } else if (turnType == TurnType.RIGHT) {
                    path.add(new Point2D.Double(northSouthRightLaneX, eastWestTopLaneY));
                    path.add(new Point2D.Double(northSouthRightLaneX, -100));
                } else {
                    path.add(new Point2D.Double(northSouthLeftLaneX, eastWestTopLaneY));
                    path.add(new Point2D.Double(northSouthLeftLaneX, 900));
                }
                break;
            case WEST:
                if (turnType == TurnType.STRAIGHT) {
                    path.add(new Point2D.Double(1100, eastWestBottomLaneY));
                } else if (turnType == TurnType.RIGHT) {
                    path.add(new Point2D.Double(northSouthLeftLaneX, eastWestBottomLaneY));
                    path.add(new Point2D.Double(northSouthLeftLaneX, 900));
                } else {
                    path.add(new Point2D.Double(northSouthRightLaneX, eastWestBottomLaneY));
                    path.add(new Point2D.Double(northSouthRightLaneX, -100));
                }
                break;
        }
    }

    private void followRoutePath() {
        if (waypointIndex >= path.size()) {
            x += SPEED * Math.cos(angle);
            y += SPEED * Math.sin(angle);
            return;
        }

        Point2D.Double target = path.get(waypointIndex);
        double dx = target.x - x;
        double dy = target.y - y;
        double distance = Math.sqrt(dx * dx + dy * dy);

        if (distance <= SPEED) {
            x = target.x;
            y = target.y;
            waypointIndex++;
            if (waypointIndex >= path.size()) {
                return;
            }
            target = path.get(waypointIndex);
            dx = target.x - x;
            dy = target.y - y;
            distance = Math.sqrt(dx * dx + dy * dy);
        }

        if (distance > 0) {
            angle = Math.atan2(dy, dx);
            x += SPEED * dx / distance;
            y += SPEED * dy / distance;
        }
    }

    private boolean isInsideIntersection() {
        return x > intLeft && x < intRight && y > intTop && y < intBottom;
    }

    /**
     * Get the stop line position for this vehicle's lane.
     */
    public double getStopLinePosition() {
        switch (origin) {
            case NORTH: return intTop - HEIGHT / 2.0;
            case SOUTH: return intBottom + HEIGHT / 2.0;
            case EAST:  return intRight + WIDTH / 2.0;
            case WEST:  return intLeft - WIDTH / 2.0;
        }
        return 0;
    }

    /**
     * Check if this vehicle should stop at the red light.
     */
    public boolean shouldStopAtLight() {
        if (crossedIntersection || turning || turnComplete) return false;
        return true;
    }

    /**
     * Get position along the direction of travel (for distance calculations).
     */
    public double getPositionAlongLane() {
        switch (origin) {
            case NORTH: return y;
            case SOUTH: return -y;
            case EAST:  return -x;
            case WEST:  return x;
        }
        return 0;
    }

    /**
     * Get the front edge position for distance calculations.
     */
    public double getFrontPosition() {
        switch (origin) {
            case NORTH: return y + HEIGHT / 2.0;
            case SOUTH: return y - HEIGHT / 2.0;
            case EAST:  return x - WIDTH / 2.0;
            case WEST:  return x + WIDTH / 2.0;
        }
        return 0;
    }

    /**
     * Get the back edge position for distance calculations.
     */
    public double getBackPosition() {
        switch (origin) {
            case NORTH: return y - HEIGHT / 2.0;
            case SOUTH: return y + HEIGHT / 2.0;
            case EAST:  return x + WIDTH / 2.0;
            case WEST:  return x - WIDTH / 2.0;
        }
        return 0;
    }

    /**
     * Calculate distance to another vehicle ahead in the same lane.
     */
    public double distanceTo(Vehicle other) {
        if (crossedIntersection || turning || turnComplete) return Double.MAX_VALUE;
        if (other.origin != this.origin) return Double.MAX_VALUE;
        if (other.turning || other.turnComplete) return Double.MAX_VALUE;

        switch (origin) {
            case NORTH: return other.y - this.y - WIDTH;
            case SOUTH: return this.y - other.y - WIDTH;
            case EAST:  return this.x - other.x - WIDTH;
            case WEST:  return other.x - this.x - WIDTH;
        }
        return Double.MAX_VALUE;
    }

    /**
     * Check if another vehicle is ahead of this one in the same lane.
     */
    public boolean isAhead(Vehicle other) {
        if (other.origin != this.origin) return false;
        switch (origin) {
            case NORTH: return other.y > this.y;
            case SOUTH: return other.y < this.y;
            case EAST:  return other.x < this.x;
            case WEST:  return other.x > this.x;
        }
        return false;
    }

    public void draw(Graphics2D g) {
        AffineTransform old = g.getTransform();

        g.translate(x, y);
        g.rotate(angle);

        // Car body
        g.setColor(color);
        g.fillRoundRect(-WIDTH / 2, -HEIGHT / 2, WIDTH, HEIGHT, 6, 6);

        // Car roof/cabin (darker shade)
        g.setColor(color.darker());
        g.fillRoundRect(-WIDTH / 6, -HEIGHT / 2 + 2, WIDTH / 3, HEIGHT - 4, 4, 4);

        // Windshield
        g.setColor(new Color(180, 220, 255, 200));
        g.fillRect(WIDTH / 6, -HEIGHT / 2 + 3, WIDTH / 5, HEIGHT - 6);

        // Headlights
        g.setColor(new Color(255, 255, 200));
        g.fillOval(WIDTH / 2 - 4, -HEIGHT / 2 + 2, 4, 4);
        g.fillOval(WIDTH / 2 - 4, HEIGHT / 2 - 6, 4, 4);

        // Tail lights
        g.setColor(Color.RED);
        g.fillOval(-WIDTH / 2, -HEIGHT / 2 + 2, 3, 3);
        g.fillOval(-WIDTH / 2, HEIGHT / 2 - 5, 3, 3);

        // Outline
        g.setColor(color.darker().darker());
        g.drawRoundRect(-WIDTH / 2, -HEIGHT / 2, WIDTH, HEIGHT, 6, 6);

        g.setTransform(old);
    }

    // Getters and setters
    public double getX() { return x; }
    public double getY() { return y; }
    public double getFrontX() { return x + Math.cos(angle) * WIDTH / 2.0; }
    public double getFrontY() { return y + Math.sin(angle) * WIDTH / 2.0; }
    public Direction getOrigin() { return origin; }
    public TurnType getTurnType() { return turnType; }
    public Color getColor() { return color; }
    public boolean isStopped() { return stopped; }
    public void setStopped(boolean stopped) { this.stopped = stopped; }
    public boolean hasCrossedIntersection() { return crossedIntersection; }
    public boolean isTurning() { return turning; }
    public boolean isTurnComplete() { return turnComplete; }
    public boolean isOffScreen() { return offScreen; }
    public boolean isInIntersection() { return (crossedIntersection || turning) && !turnComplete && !offScreen; }
}
