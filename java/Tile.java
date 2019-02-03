
import java.util.ArrayDeque;
import java.util.ArrayList;

//There's a unique tile object available for every spot on the map, these never change
//I realized the memory impact of adding some variables here was minimal, while allowing easy access. I may've gone overboard a little..

//Some convenience variables/functions are used a lot, in particular the variables that carry a list of all adjacent tiles / all tiles within a distance X

public class Tile implements Comparable<Tile> {
    //TODO: add an array index int (may already have one? I forgot) so we can have 1 dimensional datastructures representing something on the map
    //will be cheaper to use in many cases.

    public final int x;
    public final int y;
    public final byte byteX;
    public final byte byteY;
    public final int tileIndex;

    public short haliteStartTurn;
    public int haliteStartTurnCappedTo5000;
    public int haliteStartTurnInt;

    public int movesSum2;
    public int movesSum3;
    public int movesSum4;

    public float haliteStartInRange1Avg;
    public float haliteStartInRange2Avg;
    public float haliteStartInRange3Avg;
    public float haliteStartInRange4Avg;
    public float haliteStartInRange5Avg;


    public float myShipsStartInRange1;
    public float myShipsStartInRange2;
    public float myShipsStartInRange3;
    public float myShipsStartInRange4;
    public float myShipsStartInRange5;

    public float myShipsStartInRange1Avg;
    public float myShipsStartInRange2Avg;
    public float myShipsStartInRange3Avg;
    public float myShipsStartInRange4Avg;
    public float myShipsStartInRange5Avg;

    public float enemyShipsStartInRange1;
    public float enemyShipsStartInRange2;
    public float enemyShipsStartInRange3;
    public float enemyShipsStartInRange4;
    public float enemyShipsStartInRange5;

    public float enemyShipsStartInRange1Avg;
    public float enemyShipsStartInRange2Avg;
    public float enemyShipsStartInRange3Avg;
    public float enemyShipsStartInRange4Avg;
    public float enemyShipsStartInRange5Avg;
    public float controlDanger; //starts as 0, increases with totalenemycontrol
    public float control; //my control - enemy control

    public boolean inControlZone;
    public int murderSpot; //amount of murders (deliberately move onto enemy) that can take place here
    public float likelyMurderScore; //takes halite etc into account
    public int enemyTerrain; //positive: on the start map, this tile is closer to enemy shipyard by this amount compared to my dropoffs. if negative, its in my terrain

    public int lastKnownStartHalite;


    public int turnsToReachEnemyShips;
    public int turnsToReachMyShips;

    public boolean didEvalCalsThisTurn = false;
    public float evalScoreDependOnTurnAndHaliteImportance;
    public float evalScoreFlat;
    public float followPathval;
    public float crossScore;
    public float runToEnemyDropoffScore;
    public float runToMyDropoffScore;
    public float standOnTileScore;


    public float tempLure;
    public float tempLureNext;


    public Move alreadyGoingThere;
    public boolean movingHere = false;

    public float weightForEShipOnTileWith100Halite;
    public float weightForEShipOnTileWithout100Halite;


    public ArrayDeque<Tile[]>[] paths = new ArrayDeque[7];



    public float[] inspireMultiplier;
    public float[] inspireOdds;

//    public final int block_id_first;
//    public final int block_id_second;




    //The following variables are all temporary and used for various algorithms, they may be overwritten regularly and can't be relied on to have consistent data
    public float desirability; //this var is used in several locations
    public float score;
    public float obtainableHalite;
    public float nearbyMultiplier;
    public float antiInspire;
    public int minDist;
    public int neighboursTaken;
    public int isTaken;
    public int nearbyTaken;
    public float likelyhood;
    public int[] visitedMonteCarlo;

    public float bestScoreWeirdAlgo;
    public Tile[] bestPathWeirdAlgo;
    public int[] bestStandstillWeirdAlgo;

    public int GoalHaliteNear;
    public int GoalShipsNear;


    public boolean hasFriendlyDropoff;
    public int turnsFromDropoff = 1000000, forecastTurnsFromDropoff;
    public float complexDropoffDist = 1000000,complexForecastTurns;
    public int turnsFromEnemyDropoff;
    public int turnsFromTempGoal;
    public int annoyCount;
    public int annoyPlayerId;
    public boolean annoyEmergency;
    public boolean alreadySetAsAnnoyGoal;
    public boolean alreadySetInspire;
    public int annoyHaliteNear;
    public boolean reservedSimulAlgo;
    public boolean borderTile;

    public boolean goalIsAboutDenying = false;

    public boolean isCenterTile = false; //center as in: one of the areas that's equidistant to all players in 4 p, and the spots right between the dropoffs in 2p


    public DropPoint[] closestDropoffPlayer;
    public float dropDistFactor; //just for performance

    public Tile north;
    public Tile south;
    public Tile west;
    public Tile east;

    public float distFromCenter;
    public float distFromCenterProportion;

    public static final int MAX_TILES_DIST_SUPPORTED = 8;
    public static final int MAX_WALK_DIST_SUPPORTED = 8;
    public ArrayList<Tile>[] tilesInDistance = new ArrayList[MAX_TILES_DIST_SUPPORTED];
    public ArrayList<Tile>[] tilesInWalkDistance = new ArrayList[MAX_WALK_DIST_SUPPORTED];

    public ArrayList<Tile> neighbours = new ArrayList<Tile>(4);
    public ArrayList<Tile> neighboursAndSelf = new ArrayList<Tile>(5);


   public Tile(byte x, byte y){
       this.x = x;
       this.y = y;
       this.byteX = x;
       this.byteY = y;


       //TODO: put this back on if we use this again
//       this.block_id_first = Map.MAP_FIRST_LOC[x][y];
//       this.block_id_second = Map.MAP_SECOND_LOC[x][y];

       tileIndex = x + y * Map.width;
   }

    public Tile West(){
       return west;
    }
    public Tile East(){
        return east;
    }
    public Tile North(){
        return north;
    }
    public Tile South(){
        return south;
    }

    public Tile WestInit(){
        if (x ==0){
            return Map.tiles[Map.width-1][y];
        }
        else{
            return Map.tiles[x-1][y];
        }
    }

    public Tile EastInit(){
        if (x + 1 >= Map.width ){
            return Map.tiles[0][y];
        }
        else{
            return Map.tiles[x+1][y];
        }
    }


    public Tile SouthInit(){
        if (y + 1 >= Map.height ){
            return Map.tiles[x][0];
        }
        else{
            return Map.tiles[x][y+1];
        }
    }

    public Tile NorthInit(){
        if (y == 0 ){
            return Map.tiles[x][Map.height -1];
        }
        else{
            return Map.tiles[x][y-1];
        }
    }


    public int DistManhattan(Tile t){
        return Math.min(Math.abs(x-t.x), Map.width-Math.abs(x-t.x))  +  Math.min(Math.abs(y-t.y), Map.height-Math.abs(y-t.y));
    }
    public int DistManhattan(CheapShip s){
        return DistManhattan(s.GetTile());
    }
    public double DistStraight(Tile t){
        double dx =  Math.min(Math.abs(t.x-x), Map.width-Math.abs(t.x-x));
        double dy = Math.min(Math.abs(t.y-y), Map.height-Math.abs(t.y-y));
        return Math.sqrt(dx * dx + dy * dy);
    }
    public int dx(Tile t){
        return Math.min(Math.abs(x-t.x), Map.width-Math.abs(x-t.x));
    }
    public int dy(Tile t){
        return Math.min(Math.abs(y-t.y), Map.height-Math.abs(y-t.y));
    }


    //Basically manhattan dist, but takes into account that tiles with more paths to the final location are generally better
    //Standing 1x and 5y away from the goal has more possible paths than standing 0x and 6y away, even though both have
    //the same dist
    public float ComplexDist(Tile t){
        int deltaX = Math.min(Math.abs(x-t.x), Map.width-Math.abs(x-t.x));
        int deltaY = Math.min(Math.abs(y-t.y), Map.height-Math.abs(y-t.y));
        float mindif = Math.min(Math.min(deltaX,deltaY), HandwavyWeights.ComplexDistMax) / HandwavyWeights.ComplexDistDivisor;
        return deltaX + deltaY - mindif;
    }



    public int DistManhattan(int x, int y){
        return Math.min(Math.abs(this.x-x), Map.width-Math.abs(this.x-x))  +  Math.min(Math.abs(this.y-y), Map.height-Math.abs(this.y-y));
    }
    public double DistStraight(int x, int y){
        double dx =  Math.min(Math.abs(this.x-x), Map.width-Math.abs(this.x-x));
        double dy = Math.min(Math.abs(this.y-y), Map.height-Math.abs(this.y-y));
        return Math.sqrt(dx * dx + dy * dy);
    }

    //Careful: may produce double tiles id dist is too high
    //TODO: maybe precalc this and cache it?
    public ArrayList<Tile> GetTilesWithinManhattan(int distance){
        ArrayList<Tile> tilesNearby = new ArrayList<>();
        for(int x1 = x - distance; x1 <= x + distance; x1++){
            for(int y1 = y - distance; y1 <= y + distance; y1++){
                tilesNearby.add(Map.currentMap.GetTile(x1,y1));
            }
        }
        return tilesNearby;
    }


    public Tile GetReflected(){
        return Map.tiles[(Map.width-1) -x][y];
    }

    public boolean IsWestOf(Tile t){
        return (t.y == y) && ((t.x - x == 1) || (t.x == 0 && x == Map.width -1));
    }
    public boolean IsEastOf(Tile t){
        return (t.y == y) && ((x - t.x == 1) || (x == 0 && t.x == Map.width -1));
    }

    public boolean IsNorthOf(Tile t){
        return (t.x == x) && ((t.y - y == 1) || (t.y == 0 && y == Map.height -1));
    }
    public boolean IsSouthOf(Tile t){
        return (t.x == x) && ((y - t.y == 1) || (y == 0 && t.y == Map.height -1));
    }


    public void TestDirs(){
        for(int x = 0; x < Map.width; x++){
            for(int y = 0; y < Map.height; y++){
                Tile t = Map.currentMap.GetTile(x,y);

                if(!t.IsWestOf( t.East())  ) {
                    Log.log("error 1 " + t + t.East());
                }
                if(t.IsNorthOf( t.East())  ){
                    Log.log("error 2"+ t + t.East());
                }
                if(t.IsSouthOf( t.East())  ){
                    Log.log("error 3"+ t + t.East());
                }
                if(t.IsEastOf( t.East())  ){
                    Log.log("error 4"+ t + t.East());
                }


                if(t.IsWestOf( t.North())  ){
                    Log.log("error 5"+ t+ t.North());
                }
                if(t.IsNorthOf( t.North())  ){
                    Log.log("error 6"+ t  + t.North());
                }
                if(!t.IsSouthOf( t.North())  ){
                    Log.log("error 7"+ t+ t.North());
                }
                if(t.IsEastOf( t.North())  ){
                    Log.log("error 8"+ t+ t.North());
                }

                if(t.IsWestOf( t.South())  ){
                    Log.log("error 9"+ t + t.South());
                }
                if(!t.IsNorthOf( t.South())  ){
                    Log.log("error 10"+ t + t.South());
                }
                if(t.IsSouthOf( t.South())  ){
                    Log.log("error 11"+ t + t.South());
                }
                if(t.IsEastOf( t.South())  ){
                    Log.log("error 12"+ t+ t.South());
                }


                if(t.IsWestOf( t.West())  ){
                    Log.log("error 13"+ t + t.West());
                }
                if(t.IsNorthOf( t.West())  ){
                    Log.log("error 14"+ t + t.West());
                }
                if(t.IsSouthOf( t.West())  ){
                    Log.log("error 15"+ t + t.West());
                }
                if(!t.IsEastOf( t.West())  ){
                    Log.log("error 16"+ t + t.West());
                }


            }
        }

    }



    public ArrayList<Tile> GetNeighboursAndSelf(){
        return neighboursAndSelf;
//        ArrayList<Tile> tiles = new ArrayList<>();
//        tiles.add(this);
//        tiles.add(West());
//        tiles.add(East());
//        tiles.add(South());
//        tiles.add(North());
//
//        return tiles;
    }

    public ArrayList<Tile> GetNeighbours(){

        return neighbours;
//        ArrayList<Tile> tiles = new ArrayList<>();
//        tiles.add(West());
//        tiles.add(East());
//        tiles.add(South());
//        tiles.add(North());
//
//        return tiles;


    }


    public String toString(){
        return "(" + x + "," + y + ")";
    }


    @Override
    //Designed to only care about the position, for deeper equality, do something else. Reason: hashmaps etc
    public int hashCode() {
        return tileIndex;
    }
    @Override
    public boolean equals(Object t) {
        return t == this || (t != null && t instanceof Tile && ((Tile) t).tileIndex == tileIndex);
    }

    public boolean equals(Tile t){
        return t != null && t.tileIndex == tileIndex;
    }


    public boolean equals(int x, int y) {
        return this.x == x && this.y == y;
    }






    @Override
    public int compareTo(Tile other) {
        //Better finalScore/mindist combinations appear higher in the list
        return Float.compare(other.desirability,desirability);
    }
}