package hlt;


public enum Direction {
    NORTH('n'),
    EAST('e'),
    SOUTH('s'),
    WEST('w'),
    STILL('o');

    public final char charValue;




    Direction(final char charValue) {
        this.charValue = charValue;
    }
}
