
public class DropPoint {

    public final int x;
    public final int y;
    public final int id;
    public final boolean isYard;
    public final int playerId;
    public final Tile tile;

    public int haliteNear = 0;


    public DropPoint(int id, int x, int y, boolean isYard, int playerId){
        this.x = x;
        this.y = y;
        this.id = id;
        this.isYard = isYard;
        this.playerId = playerId;
        this.tile = Map.tiles[x][y];
    }

}
