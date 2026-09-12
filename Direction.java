
public enum Direction {
    NORTH, SOUTH, EAST, WEST;

    private static final java.util.Random rand = new java.util.Random();

    public static Direction random() {
        Direction[] values = values();
        return values[rand.nextInt(values.length)];
    }
}
