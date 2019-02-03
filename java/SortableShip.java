
public class SortableShip implements Comparable<SortableShip>{
    public final float priority;
    public final CheapShip ship;

    public SortableShip(CheapShip s, float priority){
        this.ship = s;
        this.priority = priority;
    }
    @Override
    public int compareTo(SortableShip other) {
        //Higher scores appear first in a list
        return Float.compare(other.priority,this.priority);
    }

}