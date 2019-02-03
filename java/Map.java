import hlt.*;

import java.lang.StringBuilder;
import java.util.*;


/*
This class is mainly responsible for two things:
- Parsing map data from the starter kit into more easily usable and better performing structures. See: UpdateMap(), GenerateFirstMap(), LoadShipsFromInput(), SetNearbyTiles() and Map(boolean fromInput)
- Doing and storing the results of simulations of move-sets. See: Simulate(), SimulateSingleMove() and Map(Map m)


For usability reasons, simulated maps contain both information about all the halite on the map in these imagined future
scenarios, as well as all the ships and their positions, including a map containing this information
For performance reasons, I had to use rather odd methods of storing this information.
For ships:
Instead of making a new map of ships every time, there's the giantShipMap array, which is essentially a pool of ship maps
of the correct size. Whenever a map is needed, it picks an unused shipmap from the array, and after use the shipmap in that
array is cleaned and made usable again. Careful: Ship map leaks or failures to clean these maps correctly leads to crashes and lots of issues.
See GetShipArrays() and CleanUp()


For halite:
Cloning the entire halite map for every imaginary map cost way too much. So, at first I looked at only cloning the rows on
which changes happened, while still using the unchanged rows of the map I was cloning from. Even that was still way too much.
Since the halite on most tiles is rarely changed, I ended up using a different shape of the halite arrays. Instead of having every row
in the array represent a row on the map, every row represents the halite on a grouping of tiles that are likely going to be changed.
This significantly reduced the amount of cloning necessary. See BuildOptimizedHaliteMap(), ProposeHaliteChange()


*/

public class Map {

    public static byte widthByte, heightByte;
    public static int width, height;

    public static Map currentMap;

    public static Tile[][] tiles;
    public static Tile[] tilesById;
    public static ArrayList<Tile> tileList = new ArrayList<>(); //will be weirdly most of the time

    public static short[][] staticHaliteMap,staticHaliteMapLastTurn; //only tracked for the startmap
    public static int[] staticHaliteMapById;
    public static CheapShip[] staticShipsById = new CheapShip[10000]; //only tracked for the startmap, too expensive for simulations
    public static CheapShip[][] staticShipsMap,staticShipsMapLastTurn; //only tracked for the startmap, too expensive for simulations
    public static ArrayList<CheapShip> staticAllShips,staticMyShips,staticEnemyShips,staticAllShipsLastTurn,staticEnemyShipsLastTurn,staticMyShipsLastTurn,staticRelevantEnemyShips; //only tracked for the startmap, too expensive for simulations
    public static ArrayList<CheapShip>[] allShipsOnTurn;

    public static boolean[] DoIOwnShip = new boolean[5000];
    public static int[] OwnerOfShip = new int[5000];
    public static int[] myIndexOfIds = new int[5000];
    public static int[] myIndexOfIdsLastTurn = new int[5000];
    public static int[] enemyRelevantIndexOfIds = new int[5000];
    public static int[] enemyIndexOfIdsLastTurn = new int[5000];
    public static float baseMeaningfulHalite =0;
    public static int curMapHaliteSum;


    public static int totalSimulationsDoneThisTurn = 0;

    public static DropPoint[][] myDropoffMap;  //TODO: if ever we add support of dropoff creation in plan, make this non-static
    public static DropPoint[] myDropoffMapById;
    public static DropPoint[][] enemyDropoffMap;  //TODO: if ever we add support of dropoff creation in plan, make this non-static
    public static DropPoint[] enemyDropoffMapById;
    public static ArrayList<DropPoint> myDropoffs;
    public static ArrayList<DropPoint> enemyDropoffs;
    public static Map[] lastTurnBoardsArray = new Map[120000];
    public static int lastTurnBoardsCounter = 0;


    public static int leakedMaps = 0;

    public static int carefulWereLeaking;

    public static int staticMyShipCount, staticEnemyShipCount2, relevantEnemyShipCount; //start of the turn counts
    public static int initialHalite;

    public static final ArrayList<CheapShip> shipsDiedLastTurn = new ArrayList<>();
    public static int mapCopyFailures = 0;
    public static int mapCopyTotal = 0;
    private static CheapShip[][][] giantShipMap;
    private static CheapShip[][][] giantMyShipArrays;
    private static CheapShip[][][] giantEnemyShipArrays;

    private static Tile[][][] giantClearThese;
    static final int giantMapSize1 = 22;
    static final int shipArraySize = 250;
    static final int maxClearThese = 1600;
    static int giantMapSize2;
    private static int iteratorBigMap2 = 0;
    private static int iteratorBigMap1 = 0;
    private static boolean[][] usingMap;

    private static int[][] HALITE_MAP_FIRST_LOC;
    private static int[][] HALITE_MAP_SECOND_LOC;
    private static int[] HALITE_MAP_FIRST_LOC_FROM_ID;
    private static int[] HALITE_MAP_SECOND_LOC_FROM_ID;


    CheapShip[] myShips, enemyShipsRelevant;
    int myShipsCount,enemyShipsCount;//, allShipsCount;
    int playerMoney;
    private boolean usingHaliteReference = true;
    private boolean[] usingHaliteLineCopy;
    private boolean alreadyCleaned;
    int markedIllegal;
    private short[][] haliteMap;
    Tile ignoreTileRemoval;
    Tile[] clearTheseArray;
    private int clearTheseCounter;
    private int myBoardId2 = 0;
    private int myBoardId1 = 0;
    CheapShip[] shipMap;

    //Awful bandaid, but no idea how to handle complex situations like swaps on spots that other ships are running into.
    //Fix whenever performance is really really needed or bugs really need to be weeded out
    ArrayList<Tile> removeThese;


    public Map(boolean fromInput){
        clearTheseArray = new Tile[maxClearThese];
        myDropoffs = new ArrayList<>();
        enemyDropoffs = new ArrayList<>();
        SetNearbyTiles(Math.min(width,Tile.MAX_TILES_DIST_SUPPORTED));

        if(fromInput) {
            for (int y = 0; y < height; y++) {
                Input input2 = Input.readInput();
                for (int x = 0; x < width; x++) {
                    staticHaliteMap[x][y] = (short)input2.getInt();
                    tiles[x][y].haliteStartTurn = staticHaliteMap[x][y];
                    tiles[x][y].haliteStartTurnCappedTo5000 = Math.min(5000,staticHaliteMap[x][y]);
                    tiles[x][y].haliteStartTurnInt = staticHaliteMap[x][y];
                    staticHaliteMapLastTurn[x][y] = staticHaliteMap[x][y];
                    initialHalite += staticHaliteMap[x][y];
                }
            }



            LoadShipsFromInput();
            playerMoney = Game.me.halite;
        }
        usingHaliteLineCopy = new boolean[width];
    }

    //Clone a map
    public Map(Map m){
       // Stopwatch.Start(21);
//        Stopwatch.Start(56);
        haliteMap = m.haliteMap; //shallow copy. when finalizing halite changes, well make a real one. Reason: performance. I was making so many deep copies that I was hitting the memory write bandwidth
        lastTurnBoardsArray[lastTurnBoardsCounter++] = this;
        leakedMaps++;
//        Stopwatch.StopAccumulate(56);
//        Stopwatch.Start(57);
        GetShipArrays();
//        Stopwatch.StopAccumulate(57);
        CheapShip s;
//        Stopwatch.Start(58);
        for(int i =0; i < staticMyShipCount;i++){
           s = m.myShips[i];
            if(s != null){
                shipMap[s.tileIndex] = s;
                myShips[i] = s;
                clearTheseArray[clearTheseCounter++] = tilesById[s.tileIndex];
            }
        }
//        Stopwatch.StopAccumulate(58);

        if(Plan.ConsiderEnemyShipsInSimulation) {
//            Stopwatch.Start(60);
            for (int i = 0; i < relevantEnemyShipCount; i++) {
                s = m.enemyShipsRelevant[i];
                if (s != null) {
                    shipMap[s.tileIndex] = s;
                    enemyShipsRelevant[i] = s;
                    clearTheseArray[clearTheseCounter++] = tilesById[s.tileIndex];
                }
            }
//            Stopwatch.StopAccumulate(60);
        }



        //Stopwatch.StopAccumulate(56);

        //Stopwatch.Start(58);
        playerMoney = m.playerMoney;
        myShipsCount = m.myShipsCount;
        enemyShipsCount = m.enemyShipsCount;
      //  Stopwatch.StopAccumulate(58);
       // Stopwatch.StopAccumulate(21);

    }
    private void GetShipArrays(){

        mapCopyTotal++;

        int failures = 0;
        while(failures++ < 700){
            iteratorBigMap2++;
            if(iteratorBigMap2 >= giantMapSize2){
                iteratorBigMap2 = 0;
                iteratorBigMap1++;
                if(iteratorBigMap1 >= giantMapSize1){
                    iteratorBigMap1 = 0;
                }
            }
            if(!usingMap[iteratorBigMap1][iteratorBigMap2]){
                myBoardId1 = iteratorBigMap1;
                myBoardId2 = iteratorBigMap2;
                shipMap = giantShipMap[iteratorBigMap1][iteratorBigMap2];
                myShips = giantMyShipArrays[iteratorBigMap1][iteratorBigMap2];
                enemyShipsRelevant = giantEnemyShipArrays[iteratorBigMap1][iteratorBigMap2];
                clearTheseArray = giantClearThese[iteratorBigMap1][iteratorBigMap2];

                usingMap[iteratorBigMap1][iteratorBigMap2] = true;

//                if(!MyBot.RELEASE) {
//                    for (int x = 0; x < width; x++) {
//                        for (int y = 0; y < height; y++) {
//                            if (shipMap[x][y] != null) {
//                                Log.log("ALERT, INCORRECT MAP CLEANUP " + shipMap[x][y], Log.LogType.MAIN);
//                            }
//                        }
//                    }
//                }
//                for(int i = 0; i < shipArraySize; i++){
//                    if(myShips[i] != null){
//                        String bugtest = "";
//                    }
//                    if(enemyShipsRelevant[i] != null){
//                        String bugtest = "";
//                    }
//                }


                return;
            }
        }

        if(MyBot.SERVER_RELEASE  && !MyBot.FINALS_RELEASE){
            System.err.println("LEAKING MAPS");
        }
        carefulWereLeaking++;
        //The big maps purpose is to prevent this simple lines here.. It's too expensive and makes the GC destroy me
       // shipMap = new CheapShip[width][height];
        shipMap = new CheapShip[width*height];

        myShips = new CheapShip[staticMyShipCount];
        enemyShipsRelevant = new CheapShip[relevantEnemyShipCount];
        clearTheseArray = new Tile[maxClearThese];
        mapCopyFailures++;
        myBoardId2 = -1;


//        while(usingMap[iteratorBigMap1][(++iteratorBigMap2) % giantMapSize2]){
//            if(failurecounter++ % 10 == 0){
//                iteratorBigMap1 = (iteratorBigMap1 + 1) % giantMapSize1;
//
//                if(failurecounter >= 30) {
//                    mapCopyFailures++;
//                    failurecounter = 0;
//                    shipMap = new CheapShip[width][height];
//                    myBoardId2 = -1;
//                    //  mapCopyFailures++;
//                    return;
//                }
//            }
//        }
//
//        usingMap[iteratorBigMap1][mapid] = true;
//        shipMap = giantShipMap[iteratorBigMap1][mapid];
//        myBoardId2 = mapid;



//            if(usingMapFromTurn[iteratorBigMap2] < MyBot.turn - 2){
//                //should be safe to reset and use, costs memory writes though, so having to use this is not optimal
//                giantShipMap[iteratorBigMap2] = new CheapShip[width][height];
//                usingMapFromTurn[iteratorBigMap2] = -1000;
//                break;
//            }
//            iteratorBigMap2++;
//            if(iteratorBigMap2 >= giantMapSize){
//                iteratorBigMap2 = 0;
//                mapResetsThisTurn++;
//                Log.log("Starting over from map 0 again: " + mapResetsThisTurn, Log.LogType.MAIN);
//                if(mapResetsThisTurn > 2){
//                    whateverJustLetItBreak = true;
//                    Log.log("BREAKING MAP REPOSITORY", Log.LogType.MAIN);
//                }
//            }
//            if(whateverJustLetItBreak){
//                //Just start using new maps instead of the big array
//                break;
//            }


//
//        if(usingMap[mapid]){
//            //All this logic to prevent doing this (costs a lot of memory bandwidth)
//
//            //iteratorBigMap2++;
//        } else{
//
//

//        }
    }

    final void CleanUp(){
        if(!alreadyCleaned) {
            alreadyCleaned = true; //might get weird threading issues otherwise
            if (myBoardId2 >= 0 && usingMap[myBoardId1][myBoardId2] && myShips != null) {
                leakedMaps--;
                //Stopwatch.Start(59);
                for (int i = 0; i <  myShips.length; i++) {
                    myShips[i] = null;
                }
                for (int i = 0; i < enemyShipsRelevant.length; i++) {
                    enemyShipsRelevant[i] = null;
                }

                for(int i =0; i < clearTheseCounter; i++){
                    //shipMap[clearTheseArray[i].x][clearTheseArray[i].y] = null;
                    shipMap[clearTheseArray[i].tileIndex] = null;
                }

                //These lines probably not neccessary, but to be sure
                giantShipMap[myBoardId1][myBoardId2] = shipMap;
                giantMyShipArrays[myBoardId1][myBoardId2] = myShips;
                giantEnemyShipArrays[myBoardId1][myBoardId2] = enemyShipsRelevant;

                usingMap[myBoardId1][myBoardId2] = false;

               // Stopwatch.StopAccumulate(59);
            }else{
                shipMap = null; //get rid of allocations here
            }
        }
    }



//    @Override
//    protected void finalize(){
//        CleanUp();
//        try {
//            super.finalize();
//        }catch (Throwable ex){
//            Log.log("Finalize threw throwable", Log.LogType.MAIN);
//        }
//    }

    static void GenerateFirstMap(boolean forTest) throws Throwable{

        if(!forTest) {
            Input input = Input.readInput();
            width = input.getInt();
            height = input.getInt();

            SideAlgorithms.width = width;
            SideAlgorithms.height = height;
        }
        widthByte = (byte) width;
        heightByte = (byte) height;

        tiles = new Tile[width][height];
        tilesById = new Tile[width*height];
        staticHaliteMap = new short[width][height];
        staticHaliteMapById = new int[width*height];
        staticHaliteMapLastTurn = new short[width][height];
        staticShipsMap = new CheapShip[width][height];
        staticShipsMapLastTurn = new CheapShip[width][height];
        staticAllShips = new ArrayList<>();
        staticEnemyShips = new ArrayList<>();
        staticRelevantEnemyShips = new ArrayList<>();
        staticMyShips = new ArrayList<>();

        HALITE_MAP_FIRST_LOC = new int[width][height];
        HALITE_MAP_SECOND_LOC = new int[width][height];
        HALITE_MAP_FIRST_LOC_FROM_ID = new int[width*height];
        HALITE_MAP_SECOND_LOC_FROM_ID = new int[width*height];

        for(int i = 0 ; i < myIndexOfIds.length;i++){
            myIndexOfIds[i] = -1;
            enemyRelevantIndexOfIds[i] = -1;
        }

        try {
            giantShipMap = new CheapShip[giantMapSize1][][];
            giantMyShipArrays = new CheapShip[giantMapSize1][][];
            giantEnemyShipArrays = new CheapShip[giantMapSize1][][];
            giantClearThese = new Tile[giantMapSize1][][];

            int wantToUseMemory = 1720 * 32 * 32;
            giantMapSize2 = Math.max(560, wantToUseMemory / (width * height)) / giantMapSize1;            //cant afford as many spots on 64x64 maps, it causes issues...

            if(forTest){
                giantMapSize2 *= 0.25;
            }

            usingMap = new boolean[giantMapSize1][giantMapSize2];
            for (int j = 0; j < giantMapSize1; j++) {
                //giantShipMap[j] = new CheapShip[giantMapSize2][][];
                giantShipMap[j] = new CheapShip[giantMapSize2][];
                giantMyShipArrays[j] = new CheapShip[giantMapSize2][];
                giantEnemyShipArrays[j] = new CheapShip[giantMapSize2][];
                giantClearThese[j] = new Tile[giantMapSize2][];
                for (int i = 0; i < giantMapSize2; i++) {
                    //giantShipMap[j][i] = new CheapShip[width][height];
                    giantShipMap[j][i] = new CheapShip[width*height];
                    giantMyShipArrays[j][i] = new CheapShip[shipArraySize];
                    giantEnemyShipArrays[j][i] = new CheapShip[shipArraySize];
                    giantClearThese[j][i] = new Tile[maxClearThese];
                    //usingMapFromTurn[i] = -1000;
                }
            }
        }catch (OutOfMemoryError ex){
            Log.exception(ex);
            Log.flushLogs();
        }

        if(!MyBot.FAST_BATCH_USING_NOTIMEOUT && !MyBot.EXPERIMENTAL_MODE && MyBot.SPAWN_LIMIT < 0) {
            int maxcount = 1000;
            int maxtime = 15000;
            //The intent is to provide the GC with a sense of pride and accomplishment
            //Without this section, there'll be a lot of crashes
            long time = System.currentTimeMillis();
            int[] innocentVariable = new int[10];
            for (int i = 0; i < maxcount; i++) {
                Stopwatch.Start();

                for (int j = 0; j < 100; j++) {
                    innocentVariable = new int[40];
                }

                try {
                    Thread.sleep(50);
                } catch (Exception ex) {
                }

                //So the point of this actually is is to put the giant arrays in the tenured section of the java memory
                //It needs a few gcs before it's put there, and this event of moving all this data seems to cause issues if
                //it happens while also executing turns. It's safe to do these gcs here because we have the time
                System.gc();
                Stopwatch.Stop("GC loop");

                if (System.currentTimeMillis() - time > maxtime) break;
                Log.flushLogs();
            }
            if (innocentVariable[0] == 0) { //to prevent JIT from removing this section
                innocentVariable = null;
            }
        }




        for (byte y = 0; y < heightByte; y++) {
            for (byte x = 0; x < widthByte; x++) {
                tiles[x][y] = new Tile(x, y);
                tileList.add(tiles[x][y]);
                tilesById[tiles[x][y].tileIndex] = tiles[x][y];
            }
        }

        SideAlgorithms.certainlyThere = new int[50][width][height];
        SideAlgorithms.likelyThere = new int[50][width][height];
        SideAlgorithms.couldbeThere = new int[50][width][height];
        SideAlgorithms.maaaybeThere = new int[50][width][height];
        SideAlgorithms.pathingLikelyThere = new int[50][width][height];


        Map.currentMap = new Map(!forTest);

        BuildOptimizedHaliteMap(Map.currentMap);

        for(int i = 0 ; i < MyBot.playerCount; i++){
            MyBot.players[i].init();
        }

    }



    static void UpdateMap(){
        final int updateCount = Input.readInput().getInt();

        for (int i = 0; i < updateCount; ++i) {
            final Input input = Input.readInput();
            final int x = input.getInt();
            final int y = input.getInt();

            staticHaliteMapLastTurn[x][y] = staticHaliteMap[x][y];
//          Log.log("Changing tile: x,y from " + currentMap.tiles[x][y]);
            staticHaliteMap[x][y] = (short)input.getInt();
            tiles[x][y].haliteStartTurn = staticHaliteMap[x][y];
            tiles[x][y].haliteStartTurnCappedTo5000 = Math.min(5000,tiles[x][y].haliteStartTurn);
//          Log.log("to: " + currentMap.tiles[x][y]);
        }


        for(Tile t : tileList){
            staticHaliteMapById[t.tileIndex] = staticHaliteMap[t.x][t.y];
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Tile t = tiles[x][y];
                t.haliteStartInRange1Avg = 0;
                t.haliteStartInRange2Avg = 0;
                t.haliteStartInRange3Avg = 0;
                t.haliteStartInRange4Avg = 0;
                t.haliteStartInRange5Avg = 0;
                for(Tile t2 : t.tilesInWalkDistance[1]){
                    t.haliteStartInRange1Avg += t2.haliteStartTurn;
                }
                for(Tile t2 : t.tilesInWalkDistance[2]){
                    t.haliteStartInRange2Avg += t2.haliteStartTurn;
                }
                for(Tile t2 : t.tilesInWalkDistance[3]){
                    t.haliteStartInRange3Avg += t2.haliteStartTurn;
                }
                for(Tile t2 : t.tilesInWalkDistance[4]){
                    t.haliteStartInRange4Avg += t2.haliteStartTurn;
                }
                for(Tile t2 : t.tilesInWalkDistance[5]){
                    t.haliteStartInRange5Avg += t2.haliteStartTurn;
                }
                t.haliteStartInRange1Avg = t.haliteStartInRange1Avg / ((float)t.tilesInWalkDistance[1].size());
                t.haliteStartInRange2Avg = t.haliteStartInRange2Avg / ((float)t.tilesInWalkDistance[2].size());
                t.haliteStartInRange3Avg = t.haliteStartInRange3Avg / ((float)t.tilesInWalkDistance[3].size());
                t.haliteStartInRange4Avg = t.haliteStartInRange4Avg / ((float)t.tilesInWalkDistance[4].size());
                t.haliteStartInRange5Avg = t.haliteStartInRange5Avg / ((float)t.tilesInWalkDistance[5].size());
            }

        }

        curMapHaliteSum = currentMap.haliteSum();
        currentMap.LoadShipsFromInput();
        currentMap.playerMoney = Game.me.halite;




        for(int i = 0; i < lastTurnBoardsCounter; i++){
            lastTurnBoardsArray[i].CleanUp();
            lastTurnBoardsArray[i] = null;
        }
        lastTurnBoardsCounter = 0;

        BuildOptimizedHaliteMap(Map.currentMap);

    }

    private void LoadShipsFromInput(){
        //Clean up some stuff first;

        shipsDiedLastTurn.clear();

        clearTheseArray = new Tile[2000];

        if(allShipsOnTurn == null){
            allShipsOnTurn = new ArrayList[Constants.MAX_TURNS+1];
        }

        clearTheseCounter = 0;

        staticAllShipsLastTurn = staticAllShips;
        staticEnemyShipsLastTurn = staticEnemyShips;
        staticMyShipsLastTurn = staticMyShips;
        enemyIndexOfIdsLastTurn = enemyRelevantIndexOfIds;
        myIndexOfIdsLastTurn = myIndexOfIds;

        enemyRelevantIndexOfIds = new int[5000];
        myIndexOfIds = new int[5000];

        for(int i = 0 ; i < MyBot.playerCount; i++){
            MyBot.players[i].UpdateTurnInitial();
        }


        staticShipsMapLastTurn = staticShipsMap;

        //clear up these arrays
        for(CheapShip s : staticAllShips){
            staticShipsById[s.id] = null;
        }
        for(CheapShip s : staticMyShips){
            myIndexOfIds[s.id] = -1;
        }
        for(CheapShip s : staticEnemyShips){
            enemyRelevantIndexOfIds[s.id] = -1;
        }

        staticShipsMap = new CheapShip[width][height];
        staticMyShips = new ArrayList<>();
        staticEnemyShips = new ArrayList<>();
        staticAllShips = new ArrayList<>();
        allShipsOnTurn[MyBot.turn] = staticAllShips;

        staticRelevantEnemyShips.clear();

         staticMyShipCount = Game.me.ships.size();
         staticEnemyShipCount2 = 0;
        for(Player p : Game.players){
            if(!p.isMe){
                staticEnemyShipCount2 += p.ships.size();
            }
        }
        myShips = new CheapShip[staticMyShipCount];



        myDropoffs.clear();
        enemyDropoffs.clear();
        shipMap = new CheapShip[width*height];
        myDropoffMap = new DropPoint[width][height];
        myDropoffMapById = new DropPoint[width*height];
        enemyDropoffMap = new DropPoint[width][height];
        enemyDropoffMapById = new DropPoint[width*height];

        for(Tile t : tileList){

            t.enemyShipsStartInRange1 = 0;
            t.enemyShipsStartInRange2 = 0;
            t.enemyShipsStartInRange3 = 0;
            t.enemyShipsStartInRange4 = 0;
            t.enemyShipsStartInRange5 = 0;

            t.enemyShipsStartInRange1Avg = 0;
            t.enemyShipsStartInRange2Avg = 0;
            t.enemyShipsStartInRange3Avg = 0;
            t.enemyShipsStartInRange4Avg = 0;
            t.enemyShipsStartInRange5Avg = 0;

            t.myShipsStartInRange1 = 0;
            t.myShipsStartInRange2 = 0;
            t.myShipsStartInRange3 = 0;
            t.myShipsStartInRange4 = 0;
            t.myShipsStartInRange5 = 0;

            t.myShipsStartInRange1Avg = 0;
            t.myShipsStartInRange2Avg = 0;
            t.myShipsStartInRange3Avg = 0;
            t.myShipsStartInRange4Avg = 0;
            t.myShipsStartInRange5Avg = 0;
        }

        int myShipIndex = 0;
        int enemyShipIndex = 0;

        for(Player p : Game.players){
            int pid = p.id.id;
            for(Ship s : p.ships.values()) {
                if((byte)s.position.x < 0 || (byte)s.position.y < 0 || s.id.id <0 || s.halite<0 ){
//                    Log.log("WHAT IS THIS?S? " + s.id.id, Log.LogType.MAIN);
                }else {
                    CheapShip cs = CheapShip.MakeShip(s.id.id, (short)s.halite, (byte) s.position.x, (byte) s.position.y);

                    PutShipOnItsTile(cs);
                    Tile t = tiles[cs.x][cs.y];
                    if (p.isMe) {
                        staticMyShips.add(cs);
                        DoIOwnShip[cs.id] = true;

                        myShips[myShipIndex] = cs;
                        myIndexOfIds[cs.id] = myShipIndex;
                        myShipIndex++;


                        for(Tile t2 : t.tilesInWalkDistance[1]){
                            t2.myShipsStartInRange1++;
                        }
                        for(Tile t2 : t.tilesInWalkDistance[2]){
                            t2.myShipsStartInRange2++;
                        }
                        for(Tile t2 : t.tilesInWalkDistance[3]){
                            t2.myShipsStartInRange3++;
                        }
                        for(Tile t2 : t.tilesInWalkDistance[4]){
                            t2.myShipsStartInRange4++;
                        }
                        for(Tile t2 : t.tilesInWalkDistance[5]){
                            t2.myShipsStartInRange5++;
                        }

                    } else {
                        staticEnemyShips.add(cs);
                        DoIOwnShip[cs.id] = false;


                        for(Tile t2 : t.tilesInWalkDistance[1]){
                            t2.enemyShipsStartInRange1++;
                        }
                        for(Tile t2 : t.tilesInWalkDistance[2]){
                            t2.enemyShipsStartInRange2++;
                        }
                        for(Tile t2 : t.tilesInWalkDistance[3]){
                            t2.enemyShipsStartInRange3++;
                        }
                        for(Tile t2 : t.tilesInWalkDistance[4]){
                            t2.enemyShipsStartInRange4++;
                        }
                        for(Tile t2 : t.tilesInWalkDistance[5]){
                            t2.enemyShipsStartInRange5++;
                        }
                    }
                    OwnerOfShip[cs.id] = p.id.id;

                    staticAllShips.add(cs);
                    MyBot.players[pid].ships.add(cs);

                    staticShipsById[cs.id] = cs;
                    staticShipsMap[cs.x][cs.y] = cs;
                }
            }

            if(p.isMe) {
                DropPoint shipyard = new DropPoint(p.shipyard.id.id,p.shipyard.position.x,p.shipyard.position.y, true, p.id.id);
                myDropoffs.add(shipyard);
                Tile shipYardPos = tiles[p.shipyard.position.x][p.shipyard.position.y];
                myDropoffMap[shipYardPos.x][shipYardPos.y] = shipyard;
                myDropoffMapById[shipYardPos.tileIndex] = shipyard;


                for (Dropoff d : p.dropoffs.values()) {
                    DropPoint dp = new DropPoint(d.id.id, d.position.x,d.position.y, false, d.owner.id);
                    myDropoffMap[dp.x][dp.y] = dp;
                    myDropoffMapById[dp.tile.tileIndex] = dp;
                    myDropoffs.add(dp);
                }

                MyBot.players[p.id.id].dropoffs.addAll(myDropoffs);
            }else{
                DropPoint shipyard = new DropPoint(p.shipyard.id.id, p.shipyard.position.x, p.shipyard.position.y, true, p.id.id);
                enemyDropoffs.add(shipyard);
                Tile shipYardPos = tiles[p.shipyard.position.x][p.shipyard.position.y];
                enemyDropoffMap[shipYardPos.x][shipYardPos.y] = shipyard;
                enemyDropoffMapById[shipYardPos.tileIndex] = shipyard;

                MyBot.players[p.id.id].dropoffs.add(shipyard);

                for (Dropoff d : p.dropoffs.values()) {
                    DropPoint dp = new DropPoint(d.id.id, d.position.x,d.position.y, false, d.owner.id);
                    enemyDropoffMap[dp.x][dp.y] = dp;
                    enemyDropoffMapById[dp.tile.tileIndex] = dp;
                    MyBot.players[p.id.id].dropoffs.add(dp);
                    enemyDropoffs.add(dp);

                }
            }
        }

        Stopwatch.Start();
        Plan.turnsToReachByShip = SideAlgorithms.GetTurnsFromShips();
        Stopwatch.Stop("TurnsFromShips");
        Stopwatch.Start();
        Plan.turnsToReachByEnemyShip = SideAlgorithms.GetTurnsFromEnemyShips();
        Stopwatch.Stop("TurnsFromEnemyShips");

        ArrayList<CheapShip> relevantEnemyShips = new ArrayList<>();

        int distanceAllowed = 4;

        for(CheapShip s : staticEnemyShips){
            if(Plan.turnsToReachByShip[s.x][s.y] <= distanceAllowed){
                relevantEnemyShips.add(s);
                staticRelevantEnemyShips.add(s);
            }
        }
        relevantEnemyShipCount = Math.min(shipArraySize, relevantEnemyShips.size());

        enemyShipsRelevant = new CheapShip[relevantEnemyShipCount];
        for(int i= 0 ; i < relevantEnemyShipCount; i++){
            enemyShipsRelevant[i] = relevantEnemyShips.get(i);
            enemyRelevantIndexOfIds[enemyShipsRelevant[i].id] = i;
        }



        for(Tile t : tileList){
            t.enemyShipsStartInRange1Avg = t.enemyShipsStartInRange1 / ((float)t.tilesInWalkDistance[1].size());
            t.enemyShipsStartInRange2Avg = t.enemyShipsStartInRange2 / ((float)t.tilesInWalkDistance[2].size());
            t.enemyShipsStartInRange3Avg = t.enemyShipsStartInRange3 / ((float)t.tilesInWalkDistance[3].size());
            t.enemyShipsStartInRange4Avg = t.enemyShipsStartInRange4 / ((float)t.tilesInWalkDistance[4].size());
            t.enemyShipsStartInRange5Avg = t.enemyShipsStartInRange5 /  ((float)t.tilesInWalkDistance[5].size());

            t.myShipsStartInRange1Avg = t.myShipsStartInRange1 / ((float)t.tilesInWalkDistance[1].size());
            t.myShipsStartInRange2Avg = t.myShipsStartInRange2 /  ((float)t.tilesInWalkDistance[2].size());
            t.myShipsStartInRange3Avg = t.myShipsStartInRange3 /  ((float)t.tilesInWalkDistance[3].size());
            t.myShipsStartInRange4Avg = t.myShipsStartInRange4 / ((float)t.tilesInWalkDistance[4].size());
            t.myShipsStartInRange5Avg = t.myShipsStartInRange5 /  ((float)t.tilesInWalkDistance[5].size());


            float totalControl = t.myShipsStartInRange1Avg + t.myShipsStartInRange2Avg * 1.5f + t.myShipsStartInRange3Avg * 2f + t.myShipsStartInRange4Avg * 2.5f + t.myShipsStartInRange5Avg  * 2.5f;
            float totalEnemyControl = t.enemyShipsStartInRange1Avg + t.enemyShipsStartInRange2Avg  * 1.5f  + t.enemyShipsStartInRange3Avg  * 2f+ t.enemyShipsStartInRange4Avg  * 2.5f + t.enemyShipsStartInRange5Avg  * 2.5f;

            if(t.turnsToReachMyShips < 10) {
                if (t.turnsFromDropoff < 5) {
                    for (DropPoint d : myDropoffs) {
                        int dist = d.tile.DistManhattan(t);
                        if (d.isYard) {
                            totalControl += Math.max(0, 5 - dist) * 0.4f;
                        }else{
                            totalControl += Math.max(0, 5 - dist) * 0.2f;
                        }
                    }
                }
                if (t.turnsFromEnemyDropoff < 5) {
                    for (DropPoint d : enemyDropoffs) {
                        int dist = d.tile.DistManhattan(t);
                        if (d.isYard) {
                            totalEnemyControl += Math.max(0, 5 - dist) * 0.5f;
                        }else{
                            totalEnemyControl += Math.max(0, 5 - dist) * 0.3f;
                        }
                    }
                }
            }


            if(totalControl >= 2f &&  (totalControl > totalEnemyControl * 1.4f || totalControl > totalEnemyControl && t.turnsFromDropoff * 1.2f < t.turnsFromEnemyDropoff) ){
                t.inControlZone = true;
            }else{
                t.inControlZone = false;
            }

            t.control = totalControl - totalEnemyControl;

            if(MyBot.playerCount == 2 || MyBot.turnsLeft < 50){
                t.controlDanger = Math.max(0f,(float)(totalEnemyControl - totalControl));
            }else if(MyBot.turnsLeft < 150){
                t.controlDanger = Math.max(0f,(float)(totalEnemyControl - totalControl) / 2f);
            }else{
                t.controlDanger = Math.max(0f,(float)(totalEnemyControl - totalControl) / 4f);
            }

        }

        if(MyBot.DO_GAME_OUTPUT){

            StringBuilder s = new StringBuilder();

            s.append("control:");
            for(int y =0; y < height; y++){
                for(int x=0; x < width; x++){
                    s.append(tiles[x][y].control + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());
        }

        for(CheapShip s : staticAllShipsLastTurn){
            if(staticShipsById[s.id] == null){
                shipsDiedLastTurn.add(s);
            }
        }




        myShipsCount = staticMyShipCount;
        enemyShipsCount = staticEnemyShipCount2;

        for(int i = 0 ; i < MyBot.playerCount; i++){
            MyBot.players[i].UpdateTurnSecond();
        }

    }


    private void RemoveShipFromTile(CheapShip s){
        shipMap[s.tileIndex]= null;
       // shipMap[s.x][s.y]= null;
    }


    private void PutShipOnItsTile(CheapShip s){
        shipMap[s.tileIndex]= s;
     //   shipMap[s.x][s.y]= s;

        clearTheseArray[clearTheseCounter++] = tiles[s.x][s.y];

       // clearTheseTiles.add(tiles[s.x][s.y]);
    }

     void PutMyShipWhereItBelongs(CheapShip s, int index){
        shipMap[s.tileIndex]= s;
        //shipMap[s.x][s.y]= s;
        myShips[index] = s;

         clearTheseArray[clearTheseCounter++] = tiles[s.x][s.y];

       //  clearTheseTiles.add(tiles[s.x][s.y]);
    }
    private void PutEnemyShipWhereItBelongs(CheapShip s, int index){
        shipMap[s.tileIndex]= s;
        //shipMap[s.x][s.y]= s;
        enemyShipsRelevant[index] = s;
        //clearTheseTiles.add(tiles[s.x][s.y]);
        clearTheseArray[clearTheseCounter++] = tiles[s.x][s.y];

    }

    CheapShip GetShipAt(Tile t){
       // return shipMap[t.x][t.y];
        return shipMap[t.tileIndex];
    }
    CheapShip GetShipById(int id){
        CheapShip s;

        int myIndex = myIndexOfIds[id];

        if(myIndex >= 0){
            s = myShips[myIndex];
        }else{
            int enemyIndex = enemyRelevantIndexOfIds[id];
            s = enemyShipsRelevant[enemyIndex];
        }
        return s;
    }



    final boolean IsEnemyShipAt(Tile t){
       // return shipMap[t.x][t.y] != null && !DoIOwnShip[shipMap[t.x][t.y].id];
        return shipMap[t.tileIndex] != null && !DoIOwnShip[shipMap[t.tileIndex].id];
    }

    final boolean IsShipAt(Tile t){
       // return  shipMap[t.x][t.y] != null;
        return  shipMap[t.tileIndex] != null;
    }

    final boolean IsFriendlyShipAt(Tile t){
       // CheapShip s = GetShipAt(t.x,t.y);
        CheapShip s = shipMap[t.tileIndex];
        return s != null && DoIOwnShip[s.id];
    }

    DropPoint GetDropAt(int x, int y){
        return myDropoffMap[x][y];
    }


    static CheapShip GetFreshShip(int id){
        return staticShipsById[id];
    }

    final boolean ContainsFriendlyShipOtherThan(Tile t,int id){
        CheapShip s = GetShipAt(t);
        return s != null && s.id != id && DoIOwnShip[s.id];
    }


    final short GetHaliteAt(CheapShip s){
        return haliteMap[ HALITE_MAP_FIRST_LOC[s.x][s.y]][HALITE_MAP_SECOND_LOC[s.x][s.y]];
        //return halite[s.x][s.y];
    }
    final short GetHaliteAt(Tile t){
       // return haliteMap[ HALITE_MAP_FIRST_LOC[t.x][t.y]][HALITE_MAP_SECOND_LOC[t.x][t.y]];
        return haliteMap[ HALITE_MAP_FIRST_LOC_FROM_ID[t.tileIndex]][HALITE_MAP_SECOND_LOC_FROM_ID[t.tileIndex]];

        //return halite[t.x][t.y];
    }
    final short GetHaliteAt(int x, int y){
        return haliteMap[ HALITE_MAP_FIRST_LOC[x][y]][HALITE_MAP_SECOND_LOC[x][y]];
        //return GetHaliteAt(GetTile(x,y));
    }
    //Faster than gethalite at, but doesnt work with non-finalized maps
//    final int GetHaliteIgnoreProposals(int x, int y){
//        return halite[x][y];
//    }


    private void ProposeHaliteChange(int x, int y, int change){

        int index1 = HALITE_MAP_FIRST_LOC[x][y];

        //This section is to try and keep new halite tile assignments to a minimum, they're pretty heavy,
        //and aren't necessary as long as we don't actually change anything
        //We'll maybe assign 10-20% of halite rows in a full simulation. In partial simulations, we may not even assign any
        //This extra logic on halite assignments seem to be worth that. Though the earlier strategy of keeping track of halite changes
        //wasn't worth it, as it impacted read speed too much
        if(usingHaliteReference){
            //Before we've made any attempt at changing halite, we don't set anything yet.
            //Don't want to write unnecessary memory
            usingHaliteReference = false;
            haliteMap = haliteMap.clone();
            haliteMap[index1] = haliteMap[index1].clone();
            usingHaliteLineCopy = new boolean[haliteMap.length];
            usingHaliteLineCopy[index1] = true;
        }
        else if(!usingHaliteLineCopy[index1]){
            haliteMap[index1] = haliteMap[index1].clone();
            usingHaliteLineCopy[index1] = true;
        }

        haliteMap[index1][HALITE_MAP_SECOND_LOC[x][y]] += change;
    }
    private void ProposeHaliteChange(int tileIndex, int change){

        int index1 = HALITE_MAP_FIRST_LOC_FROM_ID[tileIndex];

        //This section is to try and keep new halite tile assignments to a minimum, they're pretty heavy,
        //and aren't necessary as long as we don't actually change anything
        //We'll maybe assign 10-20% of halite rows in a full simulation. In partial simulations, we may not even assign any
        //This extra logic on halite assignments seem to be worth that. Though the earlier strategy of keeping track of halite changes
        //wasn't worth it, as it impacted read speed too much
        if(usingHaliteReference){
            //Before we've made any attempt at changing halite, we don't set anything yet.
            //Don't want to write unnecessary memory
            usingHaliteReference = false;
            haliteMap = haliteMap.clone();
            haliteMap[index1] = haliteMap[index1].clone();
            usingHaliteLineCopy = new boolean[haliteMap.length];
            usingHaliteLineCopy[index1] = true;
        }
        else if(!usingHaliteLineCopy[index1]){
            haliteMap[index1] = haliteMap[index1].clone();
            usingHaliteLineCopy[index1] = true;
        }

        haliteMap[index1][HALITE_MAP_SECOND_LOC_FROM_ID[tileIndex]] += change;
    }

    final void NewSimInit(boolean simulateUnmentioned,int doNotsimId){
        if(simulateUnmentioned) {
            for (CheapShip s : myShips) {
                if(s != null) {
                    //Assume standstill unless otherwise specified. Fixes reference problems etc.
                    if(s.id == doNotsimId){
                        s.moveToTileIndex = -1;
                    }else {
                        s.moveToTileIndex = s.tileIndex;
                    }
                }
            }
            if(Plan.DoEnemySimulation) {
                for (CheapShip s : enemyShipsRelevant) {
                    if (s != null) {
                        //Assume standstill unless otherwise specified. Fixes reference problems etc.
                        s.moveToTileIndex = s.tileIndex;
                    }
                }
            }

        }else{
            for(CheapShip s : myShips){
                if(s != null) {
                    //Assume no move unless otherwise specified. Necessary because a ship can live on multiple plans, and may have this overwritten on one
                    s.moveToTileIndex = -1;
                }
            }
            if(Plan.DoEnemySimulation) {
                for (CheapShip s : enemyShipsRelevant) {
                    if (s != null) {
                        //Assume no move unless otherwise specified. Necessary because a ship can live on multiple plans, and may have this overwritten on one
                        s.moveToTileIndex = -1;
                    }
                }
            }
        }
    }

    final void QueueSimulatedMove(Move m){
        CheapShip ship = GetShipById(m.ship.id);
        if(ship != null){
            if(DoIOwnShip[ship.id]) {
                if (!m.isStandStill()) {
                    RemoveShipFromTile(ship);
                }
                ship.moveToTileIndex = m.to.tileIndex;
            }
        }
    }
    final void QueueEnemySimulatedMove(Move m){

        CheapShip ship = GetShipById(m.ship.id);
        if(ship != null){
            if(!DoIOwnShip[ship.id]) {
                if (!m.isStandStill()) {
                    RemoveShipFromTile(ship);
                }
                ship.moveToTileIndex = m.to.tileIndex;
            }
        }

    }


    final void Simulate(int turn, boolean deleteDeadShips){
        totalSimulationsDoneThisTurn++;
        markedIllegal = 0;
        removeThese = new ArrayList<>();

        for(int index = 0; index < staticMyShipCount; index++){
            CheapShip oldShip =  myShips[index];
            if(oldShip != null && oldShip.moveToTileIndex >= 0){

                int fromIndex = oldShip.tileIndex;
                int toIndex = oldShip.moveToTileIndex;
                int fromHalite = haliteMap[ HALITE_MAP_FIRST_LOC_FROM_ID[fromIndex]][HALITE_MAP_SECOND_LOC_FROM_ID[fromIndex]];
                if (toIndex == fromIndex) {
                    //Standing still
                    int collect =  MyBot.standCollectSafe[fromHalite + 1000]; //+1000 is because the array is shifted by 1000 to prevent negative halite amounts from crashing the bot

                    if(collect > 0) {
                        ProposeHaliteChange(fromIndex, -collect);//TODO: check if this is bounded by the 1k max thing
                        CheapShip newShip = CheapShip.MakeShip(oldShip.id, (short) Math.min(oldShip.halite + (int)(collect * Plan.inspireMultiplier[turn][oldShip.x][oldShip.y]), Constants.MAX_HALITE), oldShip.x, oldShip.y);
                        PutMyShipWhereItBelongs(newShip,index);
                    }else{
                        PutMyShipWhereItBelongs(oldShip,index);
                    }
                    //Rather rare? If it happens at all? Probably not worth on for perf reasons
//                  if (myDropoffMap[s.x][s.y]  != null) {
//                      playerMoney += s.halite;
//                      s.halite = 0;
//                  }
                } else {
                    //Moving
                    int cost = MyBot.moveCostsSafe[fromHalite + 1000];
                    if(cost > oldShip.halite){
                        markedIllegal++;
                        cost = oldShip.halite;
                    }

                    CheapShip shipAtMoveToTile =  shipMap[toIndex];

                    if ( shipAtMoveToTile == null){
                        CheapShip newShip;
                        if (myDropoffMapById[toIndex]  != null) {
                            playerMoney += oldShip.halite - cost;
                            newShip = CheapShip.MakeShip(oldShip.id,(short)0, tilesById[toIndex].byteX, tilesById[toIndex].byteY);//  oldShip.moveToX,oldShip.moveToY);

                        }else{
                            newShip = CheapShip.MakeShip(oldShip.id, (short)(oldShip.halite - cost), tilesById[toIndex].byteX,tilesById[toIndex].byteY);
                        }
                        PutMyShipWhereItBelongs(newShip,index);
                    } else {
                        //Colliding with a ship

                        //moneyBurnt += cost; eh, not sure whats better for eval here. prob just ignore it
                        if (myDropoffMapById[toIndex]  != null) {
                            playerMoney += oldShip.halite + shipAtMoveToTile.halite -  cost;
                        } else {
                            ProposeHaliteChange(toIndex,oldShip.halite + shipAtMoveToTile.halite -  cost);
                        }
                        myShips[index] = null;
                        removeThese.add(tilesById[toIndex]);
                        //Prevent this ship from executing
                        shipAtMoveToTile.moveToTileIndex = -1;


                        myShipsCount--;
                        if(DoIOwnShip[shipAtMoveToTile.id]){
                            int indexOfShip = myIndexOfIds[shipAtMoveToTile.id];
                            if(indexOfShip >= 0) { //Rarely happens, not sure at all why... probably a big bug somewhere
                                if(myShips[indexOfShip] != null){ //This can be null in case of a triple collision
//                                    if((myShips[indexOfShip].x != toX || myShips[indexOfShip].y != toY) ){
//                                        String bugalert = "";
//                                    }
                                    myShips[indexOfShip] = null;
                                    myShipsCount--;
                                }
                            }

                        }else{
                            int indexOfShip = enemyRelevantIndexOfIds[shipAtMoveToTile.id];
                            if(indexOfShip >= 0) { //Rarely happens, not sure at all why... probably a big bug somewhere
                                if(enemyShipsRelevant[indexOfShip] != null){ //This can be null in case of a triple collision
//                                    if((enemyShipsRelevant[indexOfShip].x != toX || enemyShipsRelevant[indexOfShip].y != toY) ){
//                                        String bugalert = "";
//                                    }
                                    enemyShipsRelevant[indexOfShip] = null;
                                    enemyShipsCount--;
                                }
                            }
                        }
                    }
                }

            }
        }

        if(Plan.DoEnemySimulation && Plan.ConsiderEnemyShipsInSimulation) {
            for (int index = 0; index < relevantEnemyShipCount; index++) {
                CheapShip oldShip = enemyShipsRelevant[index];

                if (oldShip != null && oldShip.moveToTileIndex >= 0) {

                    int fromIndex = oldShip.tileIndex;
                    int toIndex = oldShip.moveToTileIndex;
                    int fromHalite = haliteMap[ HALITE_MAP_FIRST_LOC_FROM_ID[fromIndex]][HALITE_MAP_SECOND_LOC_FROM_ID[fromIndex]];


                    if (fromIndex == toIndex) {
                        int collect = MyBot.standCollectSafe[fromHalite + 1000]; //+1000 is because the array is shifted by 1000 to prevent negative halite amounts from crashing the bot
                        if(collect > 0) {
                            ProposeHaliteChange(fromIndex, -collect);//TODO: check if this is bounded by the 1k max thing
                            CheapShip newShip = CheapShip.MakeShip(oldShip.id, (short)Math.min(oldShip.halite + collect, Constants.MAX_HALITE), oldShip.x, oldShip.y); //TODO: maybe introduce enemy inspire?
                            PutEnemyShipWhereItBelongs(newShip,index);
                        }else{
                            PutEnemyShipWhereItBelongs(oldShip,index);
                        }
                    } else {

                        CheapShip shipAtMoveToTile = shipMap[ toIndex]; //GetShipAt(toX,toY);
                        if (shipAtMoveToTile == null) {
                            CheapShip newShip;
                            if (enemyDropoffMapById[toIndex] != null) {
                                newShip = CheapShip.MakeShip(oldShip.id, (short)0,tilesById[toIndex].byteX, tilesById[toIndex].byteY); //TODO:: enemy money?
                            } else {
                                newShip = CheapShip.MakeShip(oldShip.id, (short)(oldShip.halite - MyBot.moveCostsSafe[fromHalite + 1000]), tilesById[toIndex].byteX, tilesById[toIndex].byteY);
                            }
                            PutEnemyShipWhereItBelongs(newShip,index);

                        } else {
                            if (myDropoffMapById[toIndex] == null) {
                                int change = oldShip.halite + shipAtMoveToTile.halite - MyBot.moveCostsSafe[fromHalite + 1000];
                                ProposeHaliteChange(toIndex, change);
                            }
                            enemyShipsRelevant[index] = null;
                            enemyShipsCount--;
                            if (DoIOwnShip[shipAtMoveToTile.id]) {
                                int indexOfShip = myIndexOfIds[shipAtMoveToTile.id];
                                if(indexOfShip >= 0) { //Rarely happens, not sure at all why... probably a big bug somewhere
                                    if(myShips[indexOfShip] != null){
                                        myShips[indexOfShip] = null;
                                        myShipsCount--;
                                    }
                                }

                            } else{
                                int indexOfShip = enemyRelevantIndexOfIds[shipAtMoveToTile.id];
                                if(indexOfShip >= 0) { //Rarely happens, not sure at all why... probably a big bug somewhere
                                    if(enemyShipsRelevant[indexOfShip] != null){
                                        enemyShipsRelevant[indexOfShip] = null;
                                        enemyShipsCount--;
                                    }
                                }
                            }
                            removeThese.add(tilesById[toIndex]);
                            //Prevent this ship from executing
                            shipAtMoveToTile.moveToTileIndex = -1;
                        }
                    }

                }
            }
        }
        if(deleteDeadShips) {
            for (Tile t : removeThese) {
                shipMap[t.tileIndex]= null;
            }
        }
    }





    //dont use for enemy ships
    final void SimulateSingleMove(int turn, Move m, CheapShip swappingWith, CheapShip thisTurtleCrashedIntoMe){
        CheapShip ship = m.ship;
        int shipIndex = myIndexOfIds[ship.id];
        byte oldX = m.from.byteX;
        byte oldY = m.from.byteY;
        int oldTileIndex = m.from.tileIndex;

        if(m.isStandStill()){
            if(thisTurtleCrashedIntoMe == null) { //If they did, I believe we don't need to do anything
                int collect = MyBot.standCollectSafe[GetHaliteAt(oldX,oldY) + 1000]; //+1000 is because the array is shifted by 1000 to prevent negative halite amounts from crashing the bot
                if (collect > 0) {
                    ProposeHaliteChange(oldX, oldY, -collect);
                    CheapShip newShip = CheapShip.MakeShip(ship.id, (short)Math.min(ship.halite + collect * Plan.inspireMultiplier[turn][oldX][oldY], Constants.MAX_HALITE), oldX, oldY); //TODO: optimize inspire
                    shipMap[oldTileIndex] = newShip;
                    clearTheseArray[clearTheseCounter++] = tiles[oldX][oldY];
                    myShips[shipIndex] = newShip;
                }
            }
        }else {
            byte newX = m.to.byteX;
            byte newY = m.to.byteY;
            int newTileIndex = m.to.tileIndex;

            CheapShip shipAtMoveToTile = shipMap[newTileIndex];// GetShipAt(newX, newY);

            if(shipMap[oldTileIndex] != null && shipMap[oldTileIndex].id == ship.id){
                shipMap[oldTileIndex] = null;
            }
            if(thisTurtleCrashedIntoMe != null){ //This bit handles complex situations involving swaps, collisions etc. Would not recommend trying to understand it

                //The tile we came from is empty now, so we can resurrect the one who ran into us. If we're also swapping, our swap partner will run into this newly ressurected turtle
                //Ugh, this is way too involved for such an edge case...
                short halite = (short) (thisTurtleCrashedIntoMe.halite - MyBot.moveCostsSafe[GetHaliteAt(thisTurtleCrashedIntoMe.x, thisTurtleCrashedIntoMe.y) + 1000]);
                if(swappingWith == null) {
                    ignoreTileRemoval = tiles[oldX][oldY];
                }
                if (DoIOwnShip[thisTurtleCrashedIntoMe.id]) {
                    myShipsCount += 2;

                    if(myDropoffMap[oldX][oldY] != null){
                        halite = 0;
                    } else if(enemyDropoffMap[oldX][oldY] == null){
                        ProposeHaliteChange(oldX, oldY, Math.max(-GetHaliteAt(oldX,oldY), -(halite + ship.halite)));
                    }
                    CheapShip turtleZombie = CheapShip.MakeShip(thisTurtleCrashedIntoMe.id, halite, oldX, oldY);
                    PutMyShipWhereItBelongs(turtleZombie, myIndexOfIds[turtleZombie.id]);
                } else {
                    myShipsCount++;
                    enemyShipsCount++;
                    if(enemyDropoffMap[oldX][oldY] != null){
                        halite = 0;
                    }
                    else if(myDropoffMap[oldX][oldY] == null){
                        ProposeHaliteChange(oldX, oldY, Math.max(-GetHaliteAt(oldX,oldY), -(halite + ship.halite)));
                    }
                    CheapShip turtleZombie = CheapShip.MakeShip(thisTurtleCrashedIntoMe.id, halite, oldX, oldY);
                    PutEnemyShipWhereItBelongs(turtleZombie, enemyRelevantIndexOfIds[turtleZombie.id]);
                }
            }

            int cost = MyBot.moveCostsSafe[GetHaliteAt(oldX,oldY) + 1000]; //needs to be after ship resurrection or cost may not line up
            if(cost > m.ship.halite){
                markedIllegal++;
                cost = m.ship.halite;
            }

           if (shipAtMoveToTile == null || (swappingWith != null && swappingWith.id == shipAtMoveToTile.id)) {
                CheapShip newShip;
                if (myDropoffMap[newX][newY] != null) {
                    playerMoney += ship.halite - cost;
                    newShip = CheapShip.MakeShip(ship.id, (short)0, newX, newY);
                } else {
                    newShip = CheapShip.MakeShip(ship.id, (short)(ship.halite - cost), newX, newY);
                }
                myShips[shipIndex] = newShip;
                shipMap[newTileIndex] = newShip;
               clearTheseArray[clearTheseCounter++] = tiles[newX][newY];

           }
            else {
               shipMap[newTileIndex] = null;

               if (myDropoffMap[newX][newY] != null) {
                   playerMoney += shipAtMoveToTile.halite + ship.halite - cost;
               } else if (enemyDropoffMap[newX][newY] != null) { //TODO?
               } else {
                   ProposeHaliteChange(newX, newY, shipAtMoveToTile.halite + ship.halite - cost);
               }
               myShips[shipIndex] = null;
               myShipsCount--;

               if (DoIOwnShip[shipAtMoveToTile.id]) {
                   int indexOfShip = myIndexOfIds[shipAtMoveToTile.id];
                   if (indexOfShip >= 0) {
                       myShips[indexOfShip] = null;
                       myShipsCount--;
                   }
               } else {
                   int indexOfShip = enemyRelevantIndexOfIds[shipAtMoveToTile.id];
                   if (indexOfShip >= 0) {
                       enemyShipsRelevant[indexOfShip] = null;
                   }
                   enemyShipsCount--;
               }
           }
        }

    }



    public final String toString(){
        if(!Log.allowLogging)return "";
        StringBuilder s = new StringBuilder();

        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){
                s.append(String.format("%4d",GetHaliteAt(x,y)));
            }
            s.append("\r\n");

        }
        return s.toString();
    }


    final String compareMapToString(Map m){
        if(!Log.allowLogging)return "";
        StringBuilder s = new StringBuilder();

        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){
                s.append(String.format("%4d", GetHaliteAt(x,y)  - m.GetHaliteAt(x,y)));
            }
            s.append("\r\n");

        }
        return s.toString();

    }

    final int haliteSum(){
        int haliteSum = 0;
        for (int i = 0; i < haliteMap.length; i++) {
            for (int j = 0; j < haliteMap[i].length; j++) {
                haliteSum += haliteMap[i][j];
            }
        }
        return haliteSum;
    }

    //for debugging /equality tests
    final int shipCoordSum(){
        int sum = 0;
        for(CheapShip s : myShips){
            if(s != null) {
                sum += s.x + s.y;
            }
        }
        for(CheapShip s : enemyShipsRelevant){
            if(s != null) {
                sum += s.x + s.y;
            }
        }
        return sum;
    }

    final int shipHaliteSum(){
        int sum = 0;
        for(CheapShip s : myShips){
            if(s != null) {
                sum += s.halite;
            }
        }
        return sum;
    }



    final String toStringShips(boolean showplayer){
        StringBuilder s = new StringBuilder();

        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){
                Tile t = tiles[x][y];
                if(!IsShipAt(t)){
                    s.append("- ");
                }
                else if(showplayer){
                    try {
                        if (Map.myIndexOfIds[GetShipAt(t).id] >= 0) {
                            s.append(OwnerOfShip[Map.myIndexOfIds[GetShipAt(t).id]]).append(" ");
                        } else if (Map.enemyRelevantIndexOfIds[GetShipAt(t).id] >= 0) {
                            s.append(OwnerOfShip[Map.enemyRelevantIndexOfIds[GetShipAt(t).id]]).append(" ");
                        } else{
                            s.append("? ");
                        }
                    }catch ( Exception ex){
//                        Log.log("What is this ship here??? " + GetShipAt(t), Log.LogType.IMAGING);
                        s.append( "O ");
                    }
                } else{
                    s.append( "X ");
                }
            }
            s.append("\r\n");

        }
        return s.toString();
    }


    //Allows wrapping once, just use the array if no wrapping is neccessary
    final Tile GetTile(int x, int y){
        if(x < 0){
            x+= width;
        }else if(x >= width){
            x-= width;
        }
        if(y < 0){
            y+= width;
        }else if(y >= width){
            y-= width;
        }

        return tiles[x][y];
    }

    final Tile GetTile(Position p){
        return tiles[p.x][p.y];
    }
    final Tile GetTile(CheapShip s){
        return tiles[s.x][s.y];
    }

    //Allows full wrapping
    final Tile GetTileExtraWrap(int x, int y){
        while(x < 0){
            x+= width;
        }
        while(x >= width){
            x-= width;
        }
        while(y < 0){
            y+= width;
        }
        while(y >= width){
            y-= width;
        }

        return tiles[x][y];
    }



    //Sets the arrays that allow easy access to a tiles neighbours / tiles within distance X
     void SetNearbyTiles(int maxturn){

        float centerX = (width / 2) - 0.5f;
        float centerY = (height / 2) - 0.5f;
        float maxDist =  (float) Math.sqrt(  centerX* centerX  +  centerY  * centerY);


        for(int turn = 0; turn < maxturn; turn++) {
            for (int x = 0; x < width; x++) {
                int xminTurn = x - turn;
                int xplusTurn = x+turn;
                for (int y = 0; y < height; y++) {
                    Tile t = tiles[x][y];
                    t.distFromCenter = (float) Math.sqrt(  (centerX - x) * (centerX - x) +  (centerY - y) * (centerY - y));
                    t.distFromCenterProportion =  t.distFromCenter / maxDist;
                    ArrayList<Tile> list = new ArrayList<>( (turn * 2 + 1) *  (turn * 2 + 1) );
                    for(int y2 = y-turn; y2<= y+turn; y2++){
                        for(int x2 = xminTurn; x2<= xplusTurn; x2++){
                            list.add(GetTile(x2,y2));
                        }
                    }
                    t.tilesInDistance[turn] = list;
                }
            }

        }

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Tile t = tiles[x][y];

                t.south = t.SouthInit();
                t.north = t.NorthInit();
                t.east = t.EastInit();
                t.west = t.WestInit();

                t.neighbours.add(t.north);
                t.neighbours.add(t.east);
                t.neighbours.add(t.south);
                t.neighbours.add(t.west);




                t.tilesInWalkDistance[0] = new ArrayList<>();
                t.tilesInWalkDistance[1] = new ArrayList<>();
                t.tilesInWalkDistance[0].add(t);
                t.tilesInWalkDistance[1].add(t);
                t.tilesInWalkDistance[1].addAll(t.neighbours);
                t.neighboursAndSelf.addAll(t.tilesInWalkDistance[1]);
            }
        }

        for(int turn = 2; turn <  Tile.MAX_WALK_DIST_SUPPORTED; turn++) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    Tile t = tiles[x][y];
                    HashSet<Tile> newTiles = new HashSet<>();
                    for(Tile t2: t.neighbours){
                        newTiles.addAll(t2.tilesInWalkDistance[turn - 1]);
                    }
                    t.tilesInWalkDistance[turn] = new ArrayList<>(newTiles);
                }
            }
        }
        SideAlgorithms.SetCentralTiles();
    }



    //On every turn, the arrays storing halite on tiles are organized in a different way based on the proximity of the tiles
    //compared to ships. This helps avoid needless allocations
    //Yes.. this entire method is just a performance optimization..
    final static void BuildOptimizedHaliteMap(Map setHaliteOnMap) {
        int currentBucketSize;


        currentBucketSize = 4;

        int bucketNr = 0;
        int idInsideBucket = 0;

        boolean[][] hadTile = new boolean[width][height];

        ArrayList<Integer> bucketSizes = new ArrayList<>();

        //t0 moves are by far most likely to result in halite changes, use a smallish bucket size
        for (CheapShip s : Map.staticMyShips) {
            Tile t = tiles[s.x][s.y];
            if (!hadTile[t.x][t.y]) {
                hadTile[t.x][t.y] = true;
                HALITE_MAP_FIRST_LOC_FROM_ID[t.tileIndex] = bucketNr;
                HALITE_MAP_SECOND_LOC_FROM_ID[t.tileIndex] = idInsideBucket;
                HALITE_MAP_FIRST_LOC[t.x][t.y] = bucketNr;
                HALITE_MAP_SECOND_LOC[t.x][t.y] = idInsideBucket++;



                if (idInsideBucket >= currentBucketSize) {
                    bucketSizes.add(idInsideBucket);
                    idInsideBucket = 0;
                    bucketNr++;

                }
            }
        }


        if(idInsideBucket > 2) {
            bucketNr++;
            bucketSizes.add(idInsideBucket);
            idInsideBucket = 0;
        }

        if(Plan.DoEnemySimulation) {
            currentBucketSize = 16;
            for (CheapShip s : Map.staticRelevantEnemyShips) {
                Tile t = s.GetTile();
                if (!hadTile[t.x][t.y]) {
                    hadTile[t.x][t.y] = true;
                    HALITE_MAP_FIRST_LOC_FROM_ID[t.tileIndex] = bucketNr;
                    HALITE_MAP_SECOND_LOC_FROM_ID[t.tileIndex] = idInsideBucket;
                    HALITE_MAP_FIRST_LOC[t.x][t.y] = bucketNr;
                    HALITE_MAP_SECOND_LOC[t.x][t.y] = idInsideBucket++;
                    if (idInsideBucket >= currentBucketSize) {
                        bucketSizes.add(idInsideBucket);
                        idInsideBucket = 0;
                        bucketNr++;
                    }
                }
            }
        }

        currentBucketSize = 12;
        //Second steps are pretty likely too. Ordering by direction since it's unlikely two tiles adjacent to a ship will both be hit by the same ship in the same journey (And we want to have as many hits within the same buckets as possible)
        for(CheapShip s : Map.staticMyShips){
            Tile t = s.GetTile().North();
            if(!hadTile[t.x][t.y]){
                hadTile[t.x][t.y] = true;
                HALITE_MAP_FIRST_LOC_FROM_ID[t.tileIndex] = bucketNr;
                HALITE_MAP_SECOND_LOC_FROM_ID[t.tileIndex] = idInsideBucket;
                HALITE_MAP_FIRST_LOC[t.x][t.y] = bucketNr;
                HALITE_MAP_SECOND_LOC[t.x][t.y] = idInsideBucket++;
                if(idInsideBucket >= currentBucketSize){
                    bucketSizes.add(idInsideBucket);
                    idInsideBucket = 0;
                    bucketNr++;
                }
            }
        }
        for(CheapShip s : Map.staticMyShips){
            Tile t = s.GetTile().East();
            if(!hadTile[t.x][t.y]){
                hadTile[t.x][t.y] = true;
                HALITE_MAP_FIRST_LOC_FROM_ID[t.tileIndex] = bucketNr;
                HALITE_MAP_SECOND_LOC_FROM_ID[t.tileIndex] = idInsideBucket;
                HALITE_MAP_FIRST_LOC[t.x][t.y] = bucketNr;
                HALITE_MAP_SECOND_LOC[t.x][t.y] = idInsideBucket++;
                if(idInsideBucket >= currentBucketSize){
                    bucketSizes.add(idInsideBucket);
                    idInsideBucket = 0;
                    bucketNr++;
                }
            }
        }
        for(CheapShip s : Map.staticMyShips){
            Tile t = s.GetTile().South();
            if(!hadTile[t.x][t.y]){
                hadTile[t.x][t.y] = true;
                HALITE_MAP_FIRST_LOC_FROM_ID[t.tileIndex] = bucketNr;
                HALITE_MAP_SECOND_LOC_FROM_ID[t.tileIndex] = idInsideBucket;
                HALITE_MAP_FIRST_LOC[t.x][t.y] = bucketNr;
                HALITE_MAP_SECOND_LOC[t.x][t.y] = idInsideBucket++;
                if(idInsideBucket >= currentBucketSize){
                    bucketSizes.add(idInsideBucket);
                    idInsideBucket = 0;
                    bucketNr++;
                }
            }
        }
        for(CheapShip s : Map.staticMyShips){
            Tile t = s.GetTile().West();
            if(!hadTile[t.x][t.y]){
                hadTile[t.x][t.y] = true;
                HALITE_MAP_FIRST_LOC_FROM_ID[t.tileIndex] = bucketNr;
                HALITE_MAP_SECOND_LOC_FROM_ID[t.tileIndex] = idInsideBucket;
                HALITE_MAP_FIRST_LOC[t.x][t.y] = bucketNr;
                HALITE_MAP_SECOND_LOC[t.x][t.y] = idInsideBucket++;
                if(idInsideBucket >= currentBucketSize){
                    bucketSizes.add(idInsideBucket);
                    idInsideBucket = 0;
                    bucketNr++;
                }
            }
        }




        if(idInsideBucket > 4) {
            bucketNr++;
            bucketSizes.add(idInsideBucket);
            idInsideBucket = 0;
        }

        currentBucketSize = 32;

        if(Plan.DoEnemySimulation){
            for(CheapShip s : Map.staticRelevantEnemyShips) {
                for (Tile t2 : tiles[s.x][s.y].neighbours) {
                    if (!hadTile[t2.x][t2.y]) {
                        hadTile[t2.x][t2.y] = true;
                        HALITE_MAP_FIRST_LOC_FROM_ID[t2.tileIndex] = bucketNr;
                        HALITE_MAP_SECOND_LOC_FROM_ID[t2.tileIndex] = idInsideBucket;
                        HALITE_MAP_FIRST_LOC[t2.x][t2.y] = bucketNr;
                        HALITE_MAP_SECOND_LOC[t2.x][t2.y] = idInsideBucket++;

                        if (idInsideBucket >= currentBucketSize) {
                            bucketSizes.add(idInsideBucket);
                            idInsideBucket = 0;
                            bucketNr++;
                        }
                    }
                }
            }

            if(idInsideBucket > 5) {
                bucketNr++;
                bucketSizes.add(idInsideBucket);
                idInsideBucket = 0;
            }
        }


        int maxDist = Math.min(5,Plan.SEARCH_DEPTH);
        //Sometimes get hit
        for(CheapShip s : Map.staticMyShips){
            for(Tile t : tiles[s.x][s.y].tilesInWalkDistance[maxDist]){
                if(!hadTile[t.x][t.y]){
                    hadTile[t.x][t.y] = true;
                    HALITE_MAP_FIRST_LOC_FROM_ID[t.tileIndex] = bucketNr;
                    HALITE_MAP_SECOND_LOC_FROM_ID[t.tileIndex] = idInsideBucket;
                    HALITE_MAP_FIRST_LOC[t.x][t.y] = bucketNr;
                    HALITE_MAP_SECOND_LOC[t.x][t.y] = idInsideBucket++;
                    if(idInsideBucket >= currentBucketSize){
                        bucketSizes.add(idInsideBucket);
                        idInsideBucket = 0;
                        bucketNr++;

                    }
                }
            }
        }

        if(idInsideBucket > 12) {
            bucketNr++;
            bucketSizes.add(idInsideBucket);
            idInsideBucket = 0;
        }

        if(Plan.DoEnemySimulation && HandwavyWeights.PREDICT_ENEMY_TURNS > 1){
            currentBucketSize = 32;
            int dist = Math.min(3,HandwavyWeights.PREDICT_ENEMY_TURNS);
            for(CheapShip s : Map.staticRelevantEnemyShips){
                for(Tile t : tiles[s.x][s.y].tilesInWalkDistance[dist]){
                    if(!hadTile[t.x][t.y]){
                        hadTile[t.x][t.y] = true;
                        HALITE_MAP_FIRST_LOC_FROM_ID[t.tileIndex] = bucketNr;
                        HALITE_MAP_SECOND_LOC_FROM_ID[t.tileIndex] = idInsideBucket;
                        HALITE_MAP_FIRST_LOC[t.x][t.y] = bucketNr;
                        HALITE_MAP_SECOND_LOC[t.x][t.y] = idInsideBucket++;

                        if(idInsideBucket >= currentBucketSize){
                            bucketSizes.add(idInsideBucket);
                            idInsideBucket = 0;
                            bucketNr++;
                        }
                    }
                }
            }
        }


        //Finish off our latest bucket
        if(idInsideBucket > 0) {
            bucketNr++;
            bucketSizes.add(idInsideBucket);
            idInsideBucket = 0;
        }

        //Capture all tiles we haven't had yet in the latest, biggest bucket
        for(int x =0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if(!hadTile[x][y]){
                    HALITE_MAP_FIRST_LOC_FROM_ID[tiles[x][y].tileIndex] = bucketNr;
                    HALITE_MAP_SECOND_LOC_FROM_ID[tiles[x][y].tileIndex] = idInsideBucket;
                    HALITE_MAP_FIRST_LOC[x][y] = bucketNr;
                    HALITE_MAP_SECOND_LOC[x][y] = idInsideBucket++;
                }
            }
        }
        bucketSizes.add(idInsideBucket);

        setHaliteOnMap.haliteMap = new short[bucketNr+1][];

        for(int i =0; i < bucketSizes.size(); i++){
            setHaliteOnMap.haliteMap[i] = new short[bucketSizes.get(i)];
        }

        if(setHaliteOnMap == Map.currentMap) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    setHaliteOnMap.haliteMap[HALITE_MAP_FIRST_LOC[x][y]][HALITE_MAP_SECOND_LOC[x][y]] = Map.staticHaliteMap[x][y];
                }
            }
        }

//        Log.log("Buckets: " +  (bucketNr+1), Log.LogType.MAIN );
    }



    public String GetUniquenessString(){
        String s = "halite: " + haliteSum() + " bots: ";
        for(CheapShip ship : myShips){
            if(ship != null) {
                s += ship.toString();
            }
        }

        return s;
    }

    public void TestSetHalite(){
        Map.staticHaliteMap = new short[Map.width][Map.height];

        HALITE_MAP_FIRST_LOC = new int[width][height];
        HALITE_MAP_SECOND_LOC = new int[width][height];
        BuildOptimizedHaliteMap(this);

        for(int x =0; x < width; x++){
            for(int y =0; y < height; y++) {
                ProposeHaliteChange(x,y,100);
                Map.staticHaliteMap[x][y] = 100;
            }
        }
        ProposeHaliteChange(3,3,-90);
        Map.staticHaliteMap[3][3] = 10;

        Map.staticHaliteMap[3][4] = 0; //dropoff
        ProposeHaliteChange(3,4,-100);
    }



}