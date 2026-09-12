/**
 * Represents the type of turn a vehicle will make at the intersection.
 */
public enum TurnType {
    STRAIGHT, LEFT, RIGHT;

    private static final java.util.Random rand = new java.util.Random();

    public static TurnType random() {
        TurnType[] values = values();
        return values[rand.nextInt(values.length)];
    }
}
