public class AnnoyPair  implements Comparable<AnnoyPair> {
    public final float priority;
    public final CheapShip ship;
    public final Tile tile;

    public AnnoyPair(CheapShip s, Tile t, float priority){
        this.ship = s;
        this.priority = priority;
        this.tile = t;
    }
    @Override
    public int compareTo(AnnoyPair other) {
        //Higher scores appear first in a list
        return Float.compare(other.priority,this.priority);
    }
}
