public class SimulatenousMoveSet implements Comparable<SimulatenousMoveSet> {
    public Tile[] moves;
    public int minGoalDist = Map.width;
    public int reachedGoal = 50;
    public int shipIndex;
    public float score;
    public boolean bad;

    @Override
    public int compareTo(SimulatenousMoveSet other) {
        //Higher scores appear first in a list
        return Float.compare(other.score,this.score);
    }
}
