public class GoalShipPair implements Comparable<GoalShipPair> {


    public CheapShip s;
    public Tile goal;
    public float score;
    public GoalShipPair(CheapShip s, Tile goal, float score){
        this.s = s;
        this.goal = goal;
        this.score = score;
    }

    @Override
    public int compareTo(GoalShipPair other) {
        //Higher scores appear first in a list
        return Float.compare(other.score,this.score);
    }
}
