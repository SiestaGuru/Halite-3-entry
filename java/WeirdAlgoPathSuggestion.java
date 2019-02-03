public class WeirdAlgoPathSuggestion implements Comparable<WeirdAlgoPathSuggestion> {
    public CheapShip s;
    public int[] standstill;
    public Tile[] path;
    public float score;
    public int maxDepth;


    @Override
    public int compareTo(WeirdAlgoPathSuggestion other) {
        //Higher scores appear first in a list
        return Float.compare(other.score,this.score);
    }
}
