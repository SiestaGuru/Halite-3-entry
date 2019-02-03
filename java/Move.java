
public class Move implements Comparable<Move> {
    public static final int likelyVisitedSize = 5000;

    public final Tile from;
    public final Tile to;

    public final CheapShip ship;
    public boolean IgnoreInEval;

    public float score;

    public Move(Tile from, Tile to,CheapShip s){
        this.from = from;
        this.to = to;
        this.ship = s;
    }



    public String toString(){

        if(from.x != to.x || from.y != to.y) {

            return "(" + from.x + "," + from.y + ")->(" + to.x + "," + to.y + ")";
        }else{
            return "[" + from.x +"," + from.y + "]";
        }
    }

    public boolean isStandStill(){
        return from.x == to.x && from.y == to.y;
    }

    @Override
    //Designed to not care about the ship, its purely about the move itself
    public int hashCode() {
        return from.x + from.y * 65 + to.x * 4225 + to.y * 274625;
    }

    @Override
    public boolean equals(Object m) {
        return m == this ||  (m!= null && m instanceof Move && ((Move) m).from.x == from.x && ((Move) m).to.x == to.x && ((Move) m).from.y == from.y && ((Move) m).to.y == to.y && ship.id == ((Move) m).ship.id);
    }


    int GetLikelyIndex1(int turn){
        return (to.x * 3 + to.y * 7 + turn * 19 + ship.id * 269) % likelyVisitedSize;
    }
    int GetLikelyIndex2(int turn){
        return (to.x * 151 + to.y * 67 + turn * 23 + ship.id * 43) % likelyVisitedSize;
    }
    int GetLikelyIndex3(int turn){
        return (to.x * 29 + to.y * 179 + turn * 139 + ship.id * 53) % likelyVisitedSize;
    }


    @Override
    public int compareTo(Move other) {
        //Higher scores appear first in a list
        return Float.compare(other.score,this.score);
    }






    public static long GetMoveLongformat(Move m){
        return GetMoveLongformat(m.from,m.to,m.ship.id,m.ship.halite);
    }
    public static long GetMoveLongformat(Tile from, Tile to, int shipid, int shiphalite){
        long l1 = ((long)from.y) << 6;
        long l2 = ((long)to.x) << 12;
        long l3 = ((long)to.y) << 18;
        long l4 =((long)shiphalite) << 24;
        long l5 = ((long)shipid) << 34;

        return  from.x +  (((long)from.y) << 6) +  (((long)to.x) << 12) + (((long)to.y) << 18)+ (((long)shiphalite) << 24) + (((long)shipid) << 34);
    }

    public static Move MoveFromLong(long val){
        long fromx =  val & BITS_FIRST_6;
        long fromy =  val >> 6 & BITS_FIRST_6;
        long tox =  val >> 12 & BITS_FIRST_6;
        long toy =  val >> 18 & BITS_FIRST_6;
        long halite =  val >> 24 & BITS_FIRST_10;
        long id = val >> 34;
        return new Move(Map.tiles[(int)fromx][(int)fromy],Map.tiles[(int)tox][(int)toy],CheapShip.MakeShip((int)id,(short)halite,(byte)fromx,(byte)fromy));
    }

    public static Tile GetFromTileFromLong(long val){
        return Map.tiles[(int)(val & BITS_FIRST_6)][(int)(val >> 6 & BITS_FIRST_6)];
    }
    public static Tile GetToTileFromLong(long val){
        return Map.tiles[(int)(val >> 12 & BITS_FIRST_6)][(int)(val >> 18 & BITS_FIRST_6)];
    }
    public static short GetShipHaliteFromLong(long val){
        return (short) (val >> 24 & BITS_FIRST_10);
    }
    public static boolean IsLongStandstill(long val){
        if((val & BITS_FIRST_6) != (val >> 12 & BITS_FIRST_6)) return false;
        if((val >> 6 & BITS_FIRST_6) != (val >> 18 & BITS_FIRST_6)) return false;
        return true;
    }

    public static void TestMoveCast(){
        Move m = new Move(Map.tiles[0][63],Map.tiles[63][0],CheapShip.MakeShip(100,(short)0,(byte)0,(byte)63));
        Move m2 = new Move(Map.tiles[63][0],Map.tiles[0][63],CheapShip.MakeShip(33,(short)1000,(byte)63,(byte)0));
        Move m3 = new Move(Map.tiles[63][30],Map.tiles[30][63],CheapShip.MakeShip(0,(short)537,(byte)63,(byte)30));
        Move m4 = new Move(Map.tiles[63][63],Map.tiles[63][63],CheapShip.MakeShip(57,(short)442,(byte)63,(byte)63));

        long l1 = GetMoveLongformat(m);
        long l2 = GetMoveLongformat(m2);
        long l3 = GetMoveLongformat(m3);
        long l4 = GetMoveLongformat(m4);

        Move n = MoveFromLong(l1);
        Move n2 = MoveFromLong(l2);
        Move n3 = MoveFromLong(l3);
        Move n4 = MoveFromLong(l4);

        short hal1 = GetShipHaliteFromLong(l1);
        short hal2 = GetShipHaliteFromLong(l2);
        short hal3 = GetShipHaliteFromLong(l3);
        short hal4 = GetShipHaliteFromLong(l4);




        if(!n.equals(m) ||!n2.equals(m2)  || !n3.equals(m3) || !n4.equals(m4) ){
            Log.log("error");
        }

    }

    private static final long BITS_FIRST_6 = 63;
    private static final long BITS_FIRST_10 = 1023;

}
