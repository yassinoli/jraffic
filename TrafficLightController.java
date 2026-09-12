import java.util.Map;
import java.util.HashMap;
import java.util.List;


public class TrafficLightController {
    private final Map<Direction, TrafficLight> lights;
    private Map<Direction, List<Vehicle>> laneVehicles = new HashMap<>();
    private final Map<Direction, Integer> laneCapacities = new HashMap<>();

    private final Direction[] phases = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private int currentPhase = 0;
    private long phaseStartTime;
    private boolean inTransition = false;

    private static final long MIN_GREEN_DURATION_MS = 2500;  
    private static final long GREEN_DURATION_MS = 4000;      
    private static final long MAX_GREEN_DURATION_MS = 8000;
    private static final long TRANSITION_DURATION_MS = 1500;  
    private static final double URGENT_PRESSURE = 0.75;

    public TrafficLightController(Map<Direction, TrafficLight> lights) {
        this.lights = lights;
        this.phaseStartTime = System.currentTimeMillis();
        this.inTransition = false;
        for (Direction direction : Direction.values()) {
            laneVehicles.put(direction, java.util.Collections.emptyList());
            laneCapacities.put(direction, 1);
        }
        setAllRed();
        lights.get(phases[currentPhase]).setGreen(true);
    }

    public void setLaneVehicles(Map<Direction, List<Vehicle>> laneVehicles) {
        this.laneVehicles = laneVehicles;
    }

    public void setLaneCapacity(int capacity) {
        int safeCapacity = Math.max(1, capacity);
        for (Direction direction : Direction.values()) {
            laneCapacities.put(direction, safeCapacity);
        }
    }

    public void setLaneCapacity(Direction direction, int capacity) {
        laneCapacities.put(direction, Math.max(1, capacity));
    }

    public void update() {
        long now = System.currentTimeMillis();
        long elapsed = now - phaseStartTime;

        if (inTransition) {
            if (elapsed >= TRANSITION_DURATION_MS) {
                currentPhase = chooseNextPhase();
                setAllRed();
                lights.get(phases[currentPhase]).setGreen(true);
                phaseStartTime = now;
                inTransition = false;
            }
        } else {
            // Green phase
            Direction greenDirection = phases[currentPhase];
            boolean canChange = elapsed >= MIN_GREEN_DURATION_MS;
            boolean normalPhaseDone = elapsed >= GREEN_DURATION_MS;
            boolean maxPhaseDone = elapsed >= MAX_GREEN_DURATION_MS;
            boolean hasWaitingTrafficElsewhere = hasQueuedLaneOtherThan(greenDirection);
            boolean shouldServeUrgentLane = hasUrgentLaneOtherThan(greenDirection)
                    && getQueuedCount(greenDirection) == 0;
            boolean currentStillUrgent = isUrgent(greenDirection) && !hasMoreUrgentLane(greenDirection);

            if (canChange && hasWaitingTrafficElsewhere
                    && (maxPhaseDone || shouldServeUrgentLane || (normalPhaseDone && !currentStillUrgent))) {
                setAllRed();
                phaseStartTime = now;
                inTransition = true;
            }
        }
    }

    public boolean isGreen(Direction dir) {
        TrafficLight light = lights.get(dir);
        return light != null && light.isGreen();
    }

    private void setAllRed() {
        for (TrafficLight light : lights.values()) {
            light.setGreen(false);
        }
    }

    private int chooseNextPhase() {
 
        for (int offset = 1; offset <= phases.length; offset++) {
            int candidate = (currentPhase + offset) % phases.length;
            if (getQueuedCount(phases[candidate]) > 0) {
                return candidate;
            }
        }
        return (currentPhase + 1) % phases.length;
    }

    private boolean hasUrgentLaneOtherThan(Direction direction) {
        for (Direction candidate : phases) {
            if (candidate != direction && isUrgent(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasQueuedLaneOtherThan(Direction direction) {
        for (Direction candidate : phases) {
            if (candidate != direction && getQueuedCount(candidate) > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMoreUrgentLane(Direction direction) {
        double currentPressure = getPressure(direction);
        for (Direction candidate : phases) {
            if (candidate != direction && getPressure(candidate) > currentPressure + 0.20) {
                return true;
            }
        }
        return false;
    }

    private boolean isUrgent(Direction direction) {
        return getPressure(direction) >= URGENT_PRESSURE;
    }

    private double getPressure(Direction direction) {
        int capacity = laneCapacities.getOrDefault(direction, 1);
        return (double) getQueuedCount(direction) / capacity;
    }

    private int getQueuedCount(Direction direction) {
        List<Vehicle> vehicles = laneVehicles.get(direction);
        if (vehicles == null) return 0;

        int count = 0;
        for (Vehicle vehicle : vehicles) {
            if (!vehicle.hasCrossedIntersection() && !vehicle.isOffScreen()) {
                count++;
            }
        }
        return count;
    }

    public Direction getCurrentGreenDirection() {
        if (inTransition) return null;
        return phases[currentPhase];
    }

    public String getCurrentPhaseLabel() {
        if (inTransition) return "ALL RED";
        return phases[currentPhase].toString();
    }

    public boolean isInTransition() {
        return inTransition;
    }

    public long getPhaseStartTime() {
        return phaseStartTime;
    }

    public double getLanePressure(Direction direction) {
        return getPressure(direction);
    }
}
