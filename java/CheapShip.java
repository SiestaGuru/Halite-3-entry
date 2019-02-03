import hlt.Constants;
import hlt.Game;
import hlt.Ship;

import java.util.HashMap;
import java.util.HashSet;

public class CheapShip {

    //This ship object does not keep track of owner, that's done through the owner array in Map
    public final int id;
    public final short halite;
    public final byte x;
    public final byte y;
    public final int tileIndex;

    //this variable is only temporarily used in simulations and
    public int moveToTileIndex = -1;



    public static CheapShip[] CheapShipRepository = null;
    public static final int CheapShipRepositorySize = 1048576;


    private CheapShip(int id, short halite, byte x, byte y){
        this.id = id;
        this.halite = halite;
        this.x = x;
        this.y = y;
        tileIndex = x + Map.width * y;
    }



    static CheapShip MakeShip(int id, short halite, byte x, byte y){
        //This is to reuse ship objects where possible. GC goes mad with all these new ship objects
        //Have to trade CPU time though
        //It's kind of silly that this makes sense to do
        int hash = (100000 + halite + x * 5000 + y * 320000 + id) % CheapShipRepositorySize;
        CheapShip ship = CheapShipRepository[hash];
        if(ship.halite == halite && ship.id == id && ship.x == x && ship.y == y) {
            return ship;
        }
        ship = new CheapShip(id, halite, x, y);
        CheapShipRepository[hash] = ship;
        return ship;
    }
    static void GenerateRepo(){
        //big array is big
        CheapShipRepository = new CheapShip[CheapShipRepositorySize];
        //Add a reference to a fake ship throughout the array so the elements are never null (allows avoiding a null check)
        CheapShip fakeShip = new CheapShip((short)-12345,(short)-123456,(byte)0,(byte)0);
        for(int i = 0; i < CheapShip.CheapShipRepositorySize;i++){
            CheapShip.CheapShipRepository[i] = fakeShip;
        }
    }

    public Tile GetTile(){
        return Map.tilesById[tileIndex];
    }


    final boolean CanMove(Map m){
        return halite >= MyBot.moveCosts[  m.GetHaliteAt(x,y)];
    }

    final boolean IsFull(){
        return halite >= Constants.MAX_HALITE;
    }

    //If this is a fake ship, might get weird results
    final Ship GetGameShip(){
        return Game.ships.get(id);
    }

    public String toString(){

        return "(" + id+ ": " + halite + ";" + x + "," + y + ")";

    }


    final int roomLeft(){
        return Constants.MAX_HALITE - halite;
    }
    final int usableHaliteUnderMe(Map map){
        return Math.min(Constants.MAX_HALITE - halite,map.GetHaliteAt(x,y));
    }
    final int haliteUnderMe(Map map){
        return map.GetHaliteAt(x,y);
    }

    final boolean isMine(){
        return Game.ships.get(id).isMine;
    }



    @Override
    public int hashCode() {
        return id;
    }

    //Just tells us whether they have the same ship id, not location etc.
    @Override
    public boolean equals(Object t){
        return t == this || (t != null && t instanceof CheapShip && ((CheapShip) t).id == id);
    }
}
