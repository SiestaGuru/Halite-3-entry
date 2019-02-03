import hlt.Constants;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;

/*
A collection of small algorithms for misc purposes.
They're mostly about calculating something for every tile, like inspire odds on some turn, or distance to dropoffs.
 */
public class SideAlgorithms {
    private static int lastDropoffsCount = -1;
    private static int[][] turnsDropoff;

    public static int width;
    public static int height;



    final static int[][] GetTurnsToDropOff(){

        if(Map.myDropoffs.size() == lastDropoffsCount){
            return turnsDropoff;
        }
        lastDropoffsCount = Map.myDropoffs.size();
        if(turnsDropoff == null) {
            turnsDropoff = new int[width][height];
        }
        for(Tile t : Map.tileList){

            for(DropPoint d : Map.myDropoffs){
                t.turnsFromDropoff = Math.min(t.turnsFromDropoff,t.DistManhattan(d.tile));
                t.complexDropoffDist = Math.min(t.complexDropoffDist,t.ComplexDist(d.tile));
            }
            turnsDropoff[t.x][t.y] = t.turnsFromDropoff;
            t.hasFriendlyDropoff = t.turnsFromDropoff == 0;
        }
        return turnsDropoff;

    }

    final static void SetForecastDropoffDist(){
        if(Plan.dropOffSpot == null){
            for(Tile t : Map.tileList){
                t.forecastTurnsFromDropoff = t.turnsFromDropoff;
                t.complexForecastTurns = t.complexDropoffDist;
            }
        }
        else{
            for(Tile t : Map.tileList){
                t.forecastTurnsFromDropoff = Math.min(t.DistManhattan(Plan.dropOffSpot), t.turnsFromDropoff);
                t.complexForecastTurns = Math.min(t.ComplexDist(Plan.dropOffSpot), t.complexDropoffDist);
            }
        }

        if(HandwavyWeights.ActivateForecastDistInDropoff == 0) {
            for(Tile t : Map.tileList){
                t.dropDistFactor = (((HandwavyWeights.DROP_DIST_BASE_DIST_CUR_V4 - ((float) t.complexDropoffDist))) / HandwavyWeights.DROP_DIST_BASE_DIST_CUR_V4) * ((float) Math.pow(HandwavyWeights.DROP_DIST_DIST_POW, ((float) t.complexDropoffDist)));
            }
        }
        else{
            for(Tile t : Map.tileList){
                t.dropDistFactor = (((HandwavyWeights.DROP_DIST_BASE_DIST_CUR_V4 - ((float) t.complexForecastTurns))) / HandwavyWeights.DROP_DIST_BASE_DIST_CUR_V4) * ((float) Math.pow(HandwavyWeights.DROP_DIST_DIST_POW, ((float) t.complexForecastTurns)));
            }
        }
    }

    final static int[][] GetTurnsToEnemyDropOffs(){
        int[][] turns = new int[Map.width][Map.height];
        boolean[][] done = new boolean[Map.width][Map.height];


        ArrayList<Tile> tileQueue = new ArrayList<>();
        ArrayList<Tile> nextQueue = new ArrayList<>();

        for(DropPoint d : Map.enemyDropoffs){
            tileQueue.add(Map.tiles[d.x][d.y]);
            done[d.x][d.y] = true;
        }

        int stepsRequired = 1;

        while(!tileQueue.isEmpty()){
            for(Tile t : tileQueue){
                for(Tile n : t.GetNeighbours()){
                    if(!done[n.x][n.y]){
                        done[n.x][n.y] = true;
                        turns[n.x][n.y] = stepsRequired;
                        nextQueue.add(n);
                    }
                }
            }
            tileQueue = nextQueue;
            nextQueue = new ArrayList<>();
            stepsRequired++;
        }


        float centerX = (Map.width / 2) - 0.5f;
        float centerY = (Map.height / 2) - 0.5f;
        float maxDist =  (float) Math.sqrt(  centerX* centerX  +  centerY  * centerY);


        for(int x =0; x < Map.width; x++){
            for(int y =0; y < Map.width; y++){
                Map.tiles[x][y].turnsFromEnemyDropoff = turns[x][y];
            }
        }


        for(Tile t : Map.tileList){
            t.closestDropoffPlayer = new DropPoint[MyBot.playerCount];
            for(int playerid = 0; playerid < MyBot.playerCount; playerid++){
                int closest = 100000;
                for(DropPoint d : MyBot.players[playerid].dropoffs){
                    int dist = t.DistManhattan(d.x,d.y);
                    if(dist < closest){
                        closest = dist;
                        t.closestDropoffPlayer[playerid] = d;
                    }
                }
            }
        }




//
//        if(MyBot.turn % 5 == 0) {
//            Log.log("distmap");
//            String test = "";
//            for (int y = 0; y < height; y++) {
//                for (int x = 0; x < width; x++) {
//                    test += " " + String.format("%2d", turns[x][y]);
//                }
//                test += "\r\n";
//            }
//            Log.log(test, Log.LogType.TEMP);
//        }
        return turns;
    }

    final static int[][] GetTurnsToDangerousEnemiesDropOff(){

        ArrayList<DropPoint> dropPoints = new ArrayList<>();

        for(Competitor c : MyBot.players){
            c.dangerousEnoughToAnnoy = false;
        }


        if(Test.IN_TEST_PROGRESS){
            dropPoints.add(new DropPoint(7,0,4,true,1));
        }
        else if(MyBot.playerCount == 2){
            for(Competitor c : MyBot.players){
                if(!c.isMe){
                    dropPoints.addAll(c.dropoffs);
                    c.dangerousEnoughToAnnoy = MyBot.turn > 150;
                }
            }
        }else{
            boolean found = false;
            //First try disabling players who threaten to overtake us
            for(Competitor c : MyBot.players){
                if(!c.isMe){
                    if((c.currentPoints + c.carryingHalite) * 1.05 > MyBot.me.currentPoints && c.currentPoints < MyBot.me.currentPoints * 1.05) {
                        dropPoints.addAll(c.dropoffs);
                        found = true;
                        c.dangerousEnoughToAnnoy = MyBot.turnsLeft < 90;
                    }

                }
            }
            //Then just disable players ahead of us, just in case
            if(!found){
                for(Competitor c : MyBot.players){
                    if(!c.isMe){
                        if((c.currentPoints + c.carryingHalite) * 1.05  > MyBot.me.currentPoints ) {
                            dropPoints.addAll(c.dropoffs);
                            found = true;
                            c.dangerousEnoughToAnnoy = MyBot.turnsLeft < 50;
                        }
                    }
                }
            }
            //If no one is ahead, still send them anyway
            if(!found){
                for(Competitor c : MyBot.players){
                    if(!c.isMe){
                        dropPoints.addAll(c.dropoffs);
                        c.dangerousEnoughToAnnoy = MyBot.turnsLeft < 40;
                    }
                }
            }
        }




        int[][] turns = new int[Map.width][Map.height];
        boolean[][] done = new boolean[Map.width][Map.height];


        ArrayList<Tile> tileQueue = new ArrayList<>();
        ArrayList<Tile> nextQueue = new ArrayList<>();

        for(DropPoint d : dropPoints){
            tileQueue.add(Map.tiles[d.x][d.y]);
            done[d.x][d.y] = true;
        }

        int stepsRequired = 1;

        while(!tileQueue.isEmpty()){
            for(Tile t : tileQueue){
                for(Tile n : t.GetNeighbours()){
                    if(!done[n.x][n.y]){
                        done[n.x][n.y] = true;
                        turns[n.x][n.y] = stepsRequired;
                        nextQueue.add(n);
                    }
                }
            }
            tileQueue = nextQueue;
            nextQueue = new ArrayList<>();
            stepsRequired++;
        }

//
//        if(MyBot.turn % 5 == 0) {
//            Log.log("distmap");
//            String test = "";
//            for (int y = 0; y < height; y++) {
//                for (int x = 0; x < width; x++) {
//                    test += " " + String.format("%2d", turns[x][y]);
//                }
//                test += "\r\n";
//            }
//            Log.log(test, Log.LogType.MAIN);
//        }
        return turns;
    }


    //how fast is each tile reachable by at least one ship
    final static int[][] GetTurnsFromShips(){
        int[][] turns = new int[Map.width][Map.height];
        boolean[][] done = new boolean[Map.width][Map.height];


        ArrayList<Tile> tileQueue = new ArrayList<>();
        ArrayList<Tile> nextQueue = new ArrayList<>();

        for(CheapShip s : Map.staticMyShips){
            tileQueue.add(Map.tiles[s.x][s.y]);
            done[s.x][s.y] = true;
            s.GetTile().turnsToReachMyShips = 0;
        }

        int stepsRequired = 1;

        while(!tileQueue.isEmpty()){
            for(Tile t : tileQueue){
                t.turnsToReachMyShips = stepsRequired;
                for(Tile n : t.GetNeighbours()){
                    if(!done[n.x][n.y]){
                        done[n.x][n.y] = true;
                        turns[n.x][n.y] = stepsRequired;
                        nextQueue.add(n);
                    }
                }
            }
            tileQueue = nextQueue;
            nextQueue = new ArrayList<>();
            stepsRequired++;
        }

//
//        if(MyBot.turn % 5 == 0) {
//            Log.log("distmap ships");
//            String test = "";
//            for (int y = 0; y < height; y++) {
//                for (int x = 0; x < width; x++) {
//                    test += " " + String.format("%2d", turns[x][y]);
//                }
//                test += "\r\n";
//            }
//            Log.log(test, Log.LogType.TEMP);
//        }
        return turns;
    }

    //how fast is each tile reachable by at least one ship
    final static int[][] GetTurnsFromEnemyShips(){
        int[][] turns = new int[Map.width][Map.height];
        boolean[][] done = new boolean[Map.width][Map.height];


        ArrayList<Tile> tileQueue = new ArrayList<>();
        ArrayList<Tile> nextQueue = new ArrayList<>();

        for(CheapShip s : Map.staticEnemyShips){
            tileQueue.add(Map.tiles[s.x][s.y]);
            done[s.x][s.y] = true;
            s.GetTile().turnsToReachEnemyShips = 0;
        }

        int stepsRequired = 1;

        while(!tileQueue.isEmpty()){
            for(Tile t : tileQueue){
                t.turnsToReachEnemyShips = stepsRequired;
                for(Tile n : t.GetNeighbours()){
                    if(!done[n.x][n.y]){
                        done[n.x][n.y] = true;
                        turns[n.x][n.y] = stepsRequired;
                        nextQueue.add(n);
                    }
                }
            }
            tileQueue = nextQueue;
            nextQueue = new ArrayList<>();
            stepsRequired++;
        }

//
//        if(MyBot.turn % 5 == 0) {
//            Log.log("distmap ships");
//            String test = "";
//            for (int y = 0; y < height; y++) {
//                for (int x = 0; x < width; x++) {
//                    test += " " + String.format("%2d", turns[x][y]);
//                }
//                test += "\r\n";
//            }
//            Log.log(test, Log.LogType.TEMP);
//        }
        return turns;
    }


    //A map that assigns value to tiles that are close to tiles with halite.
    //Not really used anymore
    final static int[][] GetShortDistLureHaliteMap(){
        int[][] luremap = new int[Map.width][Map.height];

        ArrayDeque<Tile> queue = new ArrayDeque<>();
        ArrayDeque<Tile> cleanup = new ArrayDeque<>();


        ArrayList<Tile> orderedTiles = new ArrayList<>();
        for(int x= 0 ; x < Map.width; x++) {
            for (int y = 0; y < Map.height; y++) {
                Map.tiles[x][y].desirability = Map.tiles[x][y].haliteStartTurn;
                orderedTiles.add(Map.tiles[x][y]);
            }
        }
        Collections.sort(orderedTiles);

        int[][] dist = new int[Map.width][Map.height];

        for(Tile startTile : orderedTiles){
            queue.add(startTile);


            dist[startTile.x][startTile.y] = 1; //so it doesnt go back there

            int startworth = Math.min(700,Map.staticHaliteMap[startTile.x][startTile.y]); //capped so that big collision piles wont ruin everything

            if( Map.currentMap.IsShipAt(startTile)){
                startworth *= 0.25;
            }


            while(!queue.isEmpty()){
                Tile tile = queue.pop();
                cleanup.add(tile);

                int newdist = dist[tile.x][tile.y] + 1;


                for(Tile t : tile.GetNeighbours()){
                    if(dist[t.x][t.y] == 0){
                        int val = ((startworth - t.haliteStartTurn) / newdist) - newdist * HandwavyWeights.LureDistMod;

                        if(val > luremap[t.x][t.y]){
                            luremap[t.x][t.y] = val;
                            dist[t.x][t.y] = newdist;
                            queue.add(t);
                        }
                    }
                }
            }

            for(Tile t : cleanup){
                dist[t.x][t.y] = 0;
            }
            cleanup.clear();

        }

//        if(MyBot.turn % 5 == 1) {

        if(MyBot.DO_GAME_OUTPUT){

            StringBuilder s = new StringBuilder();

            s.append("lure:");
            for(int y =0; y < Map.height; y++){
                for(int x=0; x < Map.width; x++){
                    s.append(luremap[x][y] + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());
        }



        return luremap;
    }

    //Gives every tile a value, then 'smooths' it out over longer distances to get an approximate worth of the surrounding area of the tile
    final static float[][] GetLongDistLureMap(float[][] inspireMap){

        if(Constants.MAX_TURNS - MyBot.turn < 20){

            for(Tile t : Map.tileList){
                if(t.turnsFromDropoff == 0){
                    t.tempLure = 1000;
                }else{
                    t.tempLure = 0;
                }
            }

        }else {
            for (Tile t : Map.tileList) {
                t.tempLure = (((float) Math.min(t.haliteStartTurn,2000f)) * HandwavyWeights.LongLureFlatHal) + t.haliteStartTurn * HandwavyWeights.LongLureDistHal / Math.max(3.0f, (float) t.turnsFromDropoff);
                t.tempLure *= (1.0f + 2.0f * inspireMap[t.x][t.y] * HandwavyWeights.LongLureTrustInspireV2[MyBot.GAMETYPE_PLAYERS]);
            }


            //let's decrease the worth of tiles we already have an army on
//            for (CheapShip s : Map.staticMyShips) {
//                luremap[s.x][s.y] = 0;
//                for(Tile t : Map.tiles[s.x][s.y].tilesInWalkDistance[HandwavyWeights.LongDistNerfRadiusV2]){
//                    luremap[t.x][t.y] *= HandwavyWeights.LongDistNerfValueAroundMyShips * HandwavyWeights.LongDistNerfValueAroundMyShipsSizeMult[MyBot.GAMETYPE_SIZE];
//                }
//            }
//            //let's decrease the worth of tiles the enemy already has an army on
//            for (CheapShip s : Map.staticEnemyShips) {
//                luremap[s.x][s.y] = 0;
//                for(Tile t : Map.tiles[s.x][s.y].tilesInWalkDistance[HandwavyWeights.LongDistNerfRadiusEnemyV2]){
//                    luremap[t.x][t.y] *= HandwavyWeights.LongDistNerfValueAroundEnemyShips[MyBot.GAMETYPE_PLAYERS];
//                }
//            }

            //Let's add a bonus for edge zones we really want to control as well as the equidistant zones
            for (int x = 0; x < Map.width; x++) {
                for (int y = 0; y < Map.height; y++) {
                    Map.tiles[x][y].tempLure += Plan.controlEdgeMap[x][y] * Map.staticHaliteMap[x][y] * HandwavyWeights.LongLureEdgeMapFactor;
                    if (Map.tiles[x][y].isCenterTile) {
                        Map.tiles[x][y].tempLure += Map.staticHaliteMap[x][y] * HandwavyWeights.LongLureHalCenters;
                    }
                }
            }
        }

        float spreadRemainder = 1.0f - 4 * HandwavyWeights.LongLureSpread;

        for(int i = 0; i < HandwavyWeights.LongLureSpreadTurns; i++){
            for(Tile tile : Map.tileList){
                tile.tempLureNext = tile.tempLure * spreadRemainder;
                for(Tile t : tile.neighbours){
                    tile.tempLureNext += t.tempLure * HandwavyWeights.LongLureSpread;
                }
            }
            for(Tile t : Map.tileList){
                t.tempLure = t.tempLureNext;
            }
        }


        float total = 0;
        float max = 0;
        for(Tile t : Map.tileList){
            total += t.tempLure;
            max = Math.max(max,t.tempLure);
        }

        float avg = total / (Map.width * Map.height);
        float normalizeRatio =  (avg * 0.75f + max * 0.25f) / 250f; //We'll try to have 0-500 as the range for the luremap values


        float[][] finalluremap = new float[Map.width][Map.height];

        for(Tile t : Map.tileList){
            finalluremap[t.x][t.y] = t.tempLure / normalizeRatio;

        }



        if(MyBot.DO_GAME_OUTPUT){

            StringBuilder s = new StringBuilder();

            s.append("longlure:");
            for(int y =0; y < Map.height; y++){
                for(int x=0; x < Map.width; x++){
                    s.append(finalluremap[x][y] + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());
        }

// /
//        if(MyBot.turn % 5 == 2) {
//            String test = "Longdistluremap:  \r\n\r\n";
//            for(int y =0; y < height; y++){
//                for(int x=0; x < width; x++){
//                    test +=  " " + String.format("%3d",(int)luremap[x][y]);
//                }
//                test+="\r\n";
//            }
//            Log.log(test, Log.LogType.TEMP);
//        }
        return finalluremap;
    }

    //Gives every tile a value, then 'smooths' it out over longer distances to get an approximate worth of the surrounding area of the tile
    final static float[][] GetMediumDistLureMap(int[][] dropoffdist){
        float[][] luremap = new float[Map.width][Map.height];
        float[][] nextluremap = new float[Map.width][Map.height];

        if(Constants.MAX_TURNS - MyBot.turn < 20){
            for(DropPoint d : Map.myDropoffs){
                luremap[d.x][d.y] = 1000;
            }
        }else {
            for (int x = 0; x < Map.width; x++) {
                for (int y = 0; y < Map.height; y++) {
                    luremap[x][y] = ((float) Map.staticHaliteMap[x][y] * HandwavyWeights.MedLureFlatHal) + Map.staticHaliteMap[x][y] * HandwavyWeights.MedLureDistHal / Math.max(3.0f, (float) dropoffdist[x][y]);
                }
            }

            //let's decrease the worth of tiles we already have an army on
            for (CheapShip s : Map.staticMyShips) {
                luremap[s.x][s.y] = 0;
                for(Tile t : s.GetTile().tilesInDistance[HandwavyWeights.MedDistNerfRadius]){
                    luremap[t.x][t.y] *= HandwavyWeights.MedDistNerfValueAroundMyShips;
                }
            }
            //let's decrease the worth of tiles we already have an army on
            for (CheapShip s : Map.staticEnemyShips) {
                luremap[s.x][s.y] = 0;
                for(Tile t : s.GetTile().tilesInDistance[HandwavyWeights.MedDistNerfRadius]){
                    luremap[t.x][t.y] *= HandwavyWeights.MedDistNerfValueAroundEnemyShips;
                }
            }
        }



        float spreadRemainder = 1.0f - 4 * HandwavyWeights.MedLureSpread;

        for(int i = 0; i < HandwavyWeights.MedLureSpreadTurns; i++){

//            Log.log("\r\n\r\n " + i + "\r\n", Log.LogType.TEMP);

//            nextluremap = new float[width][height];

            for(int x= 0 ; x < Map.width; x++) {
                for (int y = 0; y < Map.height; y++) {
                    nextluremap[x][y] = luremap[x][y] * spreadRemainder;
                    for(Tile t : Map.tiles[x][y].GetNeighbours()){
                        nextluremap[x][y] += luremap[t.x][t.y] * HandwavyWeights.MedLureSpread;
                    }
                }
            }
//            luremap = nextluremap;
            for(int x= 0 ; x < Map.width; x++) {
                for (int y = 0; y < Map.height; y++) {
                    luremap[x][y]  = nextluremap[x][y];
                }
            }
        }

        float total = 0;
        float max = 0;
        for(int x= 0 ; x < Map.width; x++) {
            for (int y = 0; y < Map.height; y++) {
                total += luremap[x][y];
                max = Math.max(max,luremap[x][y]);
            }
        }
        float avg = total / (Map.width * Map.height);
        float normalizeRatio =  (avg * 0.75f + max * 0.25f) / 250f;  //We'll try to have 0-500 as the range for the luremap values

        for(int x= 0 ; x < Map.width; x++) {
            for (int y = 0; y < Map.height; y++) {
                luremap[x][y] /= normalizeRatio;
            }
        }

        if(MyBot.DO_GAME_OUTPUT){

            StringBuilder s = new StringBuilder();

            s.append("mediumlure:");
            for(int y =0; y < Map.height; y++){
                for(int x=0; x < Map.width; x++){
                    s.append(luremap[x][y] + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());
        }
//
//        if(MyBot.turn % 5 == 3) {
//            String test = "Longdistluremap:  \r\n\r\n";
//            for (int y = 0; y < height; y++) {
//                for (int x = 0; x < width; x++) {
//                    test += " " + String.format("%3d", (int) luremap[x][y]);
//                }
//                test += "\r\n";
//            }
//            Log.log(test, Log.LogType.TEMP);
//        }

        return luremap;
    }

    //Sets values that represent the maximum halite tile that can be found within x steps
    final static void SetMultipleMoveMaxSums(){

        for(Tile t : Map.tileList){

            int max2 = 0;
            int max3 = 0;
            int max4 = 0;
            for(Tile t2: t.neighbours){
                max2 = Math.max(max2,t2.haliteStartTurn);

                for(Tile t3: t2.neighbours){
                    if(!t3.equals(t)){
                        max3 = Math.max(max3,t2.haliteStartTurn + t3.haliteStartTurn);

                        for(Tile t4: t3.neighbours) {
                            if (!t4.equals(t2)) {
                                max4 = Math.max(max4,t2.haliteStartTurn + t3.haliteStartTurn + t4.haliteStartTurn);
                            }
                        }
                    }
                }
            }
            t.movesSum2 = max2 + t.haliteStartTurn;
            t.movesSum3 = max3 + t.haliteStartTurn;
            t.movesSum4 = max4 + t.haliteStartTurn;
        }
    }

    //Sets which tiles are 'central' tiles
    // In 4p these are tiles that are close to equidistant to the shipyards of all players
    //In 2p, its the tiles around the two spots right in between the starting shipyards
    final static void SetCentralTiles(){

        ArrayList<Tile> centerspots = new ArrayList<Tile>();

        centerspots.add(Map.tiles[width / 2][height / 2]);
        centerspots.add(Map.tiles[width / 2][(height / 2) -1]);
        centerspots.add(Map.tiles[(width / 2)-1][height / 2]);
        centerspots.add(Map.tiles[(width / 2)-1][(height / 2) -1]);

        centerspots.add(Map.tiles[0][height / 2]);
        centerspots.add(Map.tiles[0][(height / 2) -1]);
        centerspots.add(Map.tiles[width -1][height / 2]);
        centerspots.add(Map.tiles[width -1][(height / 2) -1]);


        if(MyBot.playerCount == 4){
            centerspots.add(Map.tiles[width / 2][0]);
            centerspots.add(Map.tiles[width / 2][height -1]);
            centerspots.add(Map.tiles[(width / 2)-1][0]);
            centerspots.add(Map.tiles[(width / 2)-1][height -1]);

            centerspots.add(Map.tiles[0][0]);
            centerspots.add(Map.tiles[0][height -1]);
            centerspots.add(Map.tiles[width -1][0]);
            centerspots.add(Map.tiles[width -1][height -1]);
        }

        for(Tile t : Map.tileList){
            for(Tile t2 : centerspots){
                if(t.DistManhattan(t2) <= 3){
                    t.isCenterTile = true;
                    break;
                }
            }

        }

//        StringBuilder s = new StringBuilder();
//
//        for(int y =0; y < height; y++){
//            for(int x=0; x < width; x++){
//                if(tiles[x][y].isCenterTile) {
//                    s.append("1 ");
//                }else{
//                    s.append("0 ");
//                }
//            }
//            s.append("\r\n");
//        }
//        Log.log(s.toString(), Log.LogType.MAIN);

    }


    //Sets the tile distance to tiles with a 'meaningful' amount of halite
    //The amount of halite a tile needs to be meaningful depends on the halite left on the map
    final static int[][] GetDistanceToMeaningfulSpotsMap(){
        int[][] turns = new int[width][height];
        boolean[][] done = new boolean[width][height];


        ArrayList<Tile> tileQueue = new ArrayList<>();
        ArrayList<Tile> nextQueue = new ArrayList<>();


        int meaningFulHalite = HandwavyWeights.BaseMeaningfulHalite;
        int found = 0;

        while(meaningFulHalite > HandwavyWeights.MinMeaningfulHalite) {
            for (int x = 0; x < Map.width; x++) {
                for (int y = 0; y < Map.height; y++) {
                    if (Map.staticHaliteMap[x][y] > meaningFulHalite) {
                        tileQueue.add(Map.tiles[x][y]);
                        done[x][y] = true;
                        found++;
                    }
                }

            }
            if(found > Math.max(Map.staticAllShips.size() * 1.2,  (width * height) / 35)) break;
            meaningFulHalite *= 0.8;
        }

        Map.baseMeaningfulHalite = meaningFulHalite;

        int stepsRequired = 1;

        while(!tileQueue.isEmpty()){
            for(Tile t : tileQueue){
                for(Tile n : t.GetNeighbours()){
                    if(!done[n.x][n.y]){
                        done[n.x][n.y] = true;
                        turns[n.x][n.y] = stepsRequired;
                        nextQueue.add(n);
                    }
                }
            }
            tileQueue = nextQueue;
            nextQueue = new ArrayList<>();
            stepsRequired++;
        }

        if(MyBot.DO_GAME_OUTPUT){

            StringBuilder s = new StringBuilder();

            s.append("meaningfuldist:");
            for(int y =0; y < height; y++){
                for(int x=0; x < width; x++){
                    s.append(turns[x][y] + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());
        }

//        if(MyBot.turn % 5 == 4) {
////
//        String test = "Turns to meaningful:  \r\n\r\n";
//        for(int y =0; y < height; y++){
//            for(int x=0; x < width; x++){
//                test +=  " " + String.format("%3d",(int)turns[x][y]);
//            }
//            test+="\r\n";
//        }
//        Log.log(test, Log.LogType.TEMP);
//        }

        return turns;
    }


    public static float BrawlMap[][];


    //This map tracks places that have had a lot of collisions recently
    public static void UpdateBrawlMap(){


        float[][] newBrawlMap = new float[Map.width][Map.height];
        if(BrawlMap != null){

            for(Tile t : Map.tileList){
                newBrawlMap[t.x][t.y] = BrawlMap[t.x][t.y] * 0.4f;
                for(Tile t2 : t.neighbours){
                    newBrawlMap[t.x][t.y] +=  BrawlMap[t2.x][t2.y] * 0.15f;
                }
                newBrawlMap[t.x][t.y]  =  Math.max(0, (newBrawlMap[t.x][t.y] * 0.97f) - 0.008f);
            }
        }

        BrawlMap = newBrawlMap;

        for(CheapShip s : Map.shipsDiedLastTurn){
            if(s.GetTile().turnsFromDropoff > 1 && s.GetTile().turnsFromEnemyDropoff > 1) {
                for (Tile t : s.GetTile().neighboursAndSelf) {
                    BrawlMap[t.x][t.y] += 1f;
                }
            }
        }

        if(MyBot.DO_GAME_OUTPUT){
            StringBuilder s = new StringBuilder();
            s.append("brawl:");
            for(int y =0; y < height; y++){
                for(int x=0; x < width; x++){
                    s.append( BrawlMap[x][y] + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());
        }

    }


    static int[][][] certainlyThere;
    static int[][][] likelyThere;
    static int[][][] couldbeThere;
    static int[][][] maaaybeThere;
    static int[][][] pathingLikelyThere;
    static int[][][] haliteMapInspire;
    static boolean[][][] alreadycontains;


    static float[][][][] inspireHistory = new float[501][][][];


    //Calculate the odds that a tile will be inspired for us in x turns
    final static float[][][] Inspireodds(int maxTurn){
        CheapShip[][] expectedShipLocs = new CheapShip[maxTurn][Map.staticEnemyShipCount2];

        for(int i =0; i < Map.staticEnemyShipCount2; i++){
            expectedShipLocs[0][i] = Map.staticEnemyShips.get(i);
        }


        if(haliteMapInspire == null || haliteMapInspire.length != maxTurn){
            haliteMapInspire =  new int[maxTurn][width][height];
            alreadycontains = new boolean[maxTurn][width][height];
            //alreadySet = new boolean[width][height];
        }
        float[][][] inspireOdds =new float[maxTurn][width][height];

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++) {
                for (int turn = 0; turn < maxTurn; turn++) {
                    haliteMapInspire[turn][x][y] = Map.staticHaliteMap[x][y];
                    alreadycontains[turn][x][y] = false;

                    certainlyThere[turn][x][y] = 0;
                    likelyThere[turn][x][y] = 0;
                    couldbeThere[turn][x][y] = 0;
                    maaaybeThere[turn][x][y] = 0;
                    pathingLikelyThere[turn][x][y] = 0;
                }
            }
        }


        if(HandwavyWeights.InspirePathType == 1) {
            //Essentially a mini simulation + greedy location algo
            //Not anywhere close to perfect, misses collisions etc. Don't want to spend too much performance on this though
            //TODO: probably integrate with enemy loc odds
            for (int index = 0; index < Map.staticEnemyShipCount2; index++) {
                Tile originalLoc = expectedShipLocs[0][index].GetTile();
                for (int turn = 1; turn < maxTurn; turn++) {
                    CheapShip s = expectedShipLocs[turn - 1][index];

                    if (s != null) {
                        Tile nextLoc = Map.tiles[s.x][s.y];
                        if (s.halite > 950) {
                            int closestDistance = Plan.turnsFromEnemyDropoff[s.x][s.y];
                            //Assume they're going home.
                            //Which home? No idea. Just go to whatever enemy dropoff is closest (TODO)
                            for (Tile t : nextLoc.GetNeighbours()) {
                                if (!alreadycontains[turn][t.x][t.y]) {
                                    int dist = Plan.turnsFromEnemyDropoff[t.x][t.y];
                                    if (dist < closestDistance) {
                                        closestDistance = dist;
                                        nextLoc = t;
                                    } else if (dist == closestDistance && t.haliteStartTurn < nextLoc.haliteStartTurn) {
                                        nextLoc = t;
                                    }
                                }
                            }
                        } else {

                            float bestscore = -1000000000f;

                            for (Tile t : nextLoc.neighboursAndSelf) {
                                int hal = Math.min(haliteMapInspire[turn - 1][t.x][t.y], Constants.MAX_HALITE - s.halite);
                                float score = hal + Plan.medDistLure[t.x][t.y] + 0.5f + Plan.longDistLure[t.x][t.y] * 0.1f - Plan.distToMeaningfulHalite[t.x][t.y] * 10f;
                                if (score > bestscore && !alreadycontains[turn][t.x][t.y]) {
                                    //TODO: maybe prevent colliding ships?
                                    bestscore = score;
                                    nextLoc = t;
                                }

                            }
                        }
                        CheapShip newShip;
                        if (nextLoc.x == s.x && nextLoc.y == s.y) {
                            int collect = MyBot.standCollectSafe[haliteMapInspire[turn - 1][nextLoc.x][nextLoc.y] + 1000];
                            newShip = CheapShip.MakeShip(s.id, (short) Math.min(1000, s.halite + collect), s.x, s.y);
                            // newShip = new CheapShip( s, (short)Math.min(1000, s.halite + collect));
                            haliteMapInspire[turn][nextLoc.x][nextLoc.y] -= collect;
                        } else {
                            //newShip = new CheapShip(s.id, (short)Math.max(0, s.halite - MyBot.moveCostsSafe[haliteMapInspire[turn - 1][nextLoc.x][nextLoc.y] + 1000]), nextLoc);
                            newShip = CheapShip.MakeShip(s.id, (short) Math.max(0, s.halite - MyBot.moveCostsSafe[haliteMapInspire[turn - 1][nextLoc.x][nextLoc.y] + 1000]), nextLoc.byteX, nextLoc.byteY);
                        }
                        alreadycontains[turn][nextLoc.x][nextLoc.y] = true;
                        expectedShipLocs[turn][index] = newShip;
                    }
                }
            }
        }

        for (int turn = 0; turn < maxTurn; turn++) {
            //  HashSet<Integer> alreadySetSpot = new HashSet<>();
            //boolean[][] alreadySet = new boolean[width][height];
            // for (CheapShip ship : expectedShipLocs[0]) {

            for (int index =0; index < Map.staticEnemyShipCount2; index++) {
                CheapShip ship = expectedShipLocs[0][index];
                if(ship != null) {
                    Tile start = Map.currentMap.GetTile(ship);

                    int distCertainly;
                    int distLikely;
                    int distCouldBe;
                    int distMaybe;
                    if(HandwavyWeights.InspireShape == 0) {
                        switch (turn) { //REMEMBER: could be must be >= likely and certainly (else cleaning will fail)
                            case 0:
                                distCertainly = 4;
                                distLikely = 4;
                                distCouldBe = 4;
                                distMaybe = 4;
                                break;
                            case 1:
                                distCertainly = 3;
                                distLikely = 4;
                                distCouldBe = 4;
                                distMaybe = 5;
                                break;
                            case 2:

                                distCertainly = 2;
                                distLikely = 3;
                                distCouldBe = 4;
                                distMaybe = 5;
                                break;
                            case 3:
                                distCertainly = 1;
                                distLikely = 3;
                                distCouldBe = 4;
                                distMaybe = 6;

                                break;
                            case 4:
                                distCertainly = 0;
                                distLikely = 2;
                                distCouldBe = 4;
                                distMaybe = 6;
                                break;
                            case 5:
                            case 6:
                                distCertainly = 0;
                                distLikely = 2;
                                distCouldBe = 4;
                                distMaybe = 7;
                                break;
                            default:

                                distCertainly = 0;
                                distLikely = 2;
                                distCouldBe = 3;
                                distMaybe = 7;
                                break;
                        }
                    }
                    else if(HandwavyWeights.InspireShape == 1) {
                        distCertainly = 4;
                        distLikely = 4;
                        distCouldBe = 4;
                        distMaybe = 4;
                    }
                    else if(HandwavyWeights.InspireShape == 2) {
                        distCertainly = Math.max(0,4-turn);
                        distLikely = Math.max(0,4-(int)(turn * 0.3));
                        distCouldBe = 4;
                        distMaybe = Math.min(7,4 + turn);
                    }
                    else{
                        switch (turn) { //REMEMBER: could be must be >= likely and certainly (else cleaning will fail)
                            case 0:
                                distCertainly = 4;
                                distLikely = 4;
                                distCouldBe = 4;
                                distMaybe = 4;
                                break;
                            case 1:
                            case 2:
                            case 3:
                                distCertainly = 3;
                                distLikely = 4;
                                distCouldBe = 4;
                                distMaybe = 4;
                                break;
                            case 4:
                            case 5:
                                distCertainly = 2;
                                distLikely = 3;
                                distCouldBe = 4;
                                distMaybe = 4;
                                break;
                            default:
                                distCertainly = 1;
                                distLikely = 3;
                                distCouldBe = 4;
                                distMaybe = 5;
                                break;
                        }
                    }


                    if (distCertainly > 0) {
                        for (Tile t : start.tilesInWalkDistance[distCertainly]) {
                            certainlyThere[turn][t.x][t.y]++;
                            t.alreadySetInspire = true;

//                            Integer spotid = t.x + width * (t.y + height * (turn + maxTurn * ship.id));
//                            if (alreadySetSpot.contains(spotid)) {
//                                pathingLikelyThere[turn][t.x][t.y]--;
//                            } else {
//                                alreadySetSpot.add(spotid);
//                            }
                        }
                    }




                    for (Tile t : start.tilesInWalkDistance[distLikely]) {
                        if(!t.alreadySetInspire){
                            t.alreadySetInspire = true;
                            likelyThere[turn][t.x][t.y]++;
                        }

//                        Integer spotid = t.x + width * (t.y + height * (turn + maxTurn * ship.id));
//                        if (!alreadySetSpot.contains(spotid)) {
//                            likelyThere[turn][t.x][t.y]++;
//                            alreadySetSpot.add(spotid);
//                            //alreadySet[t.x][t.y] = true;
//                        }
                    }

                    Tile pathingTile = null;
                    if (turn > 0) {
                        CheapShip movingShip = expectedShipLocs[turn][index];
                        if(movingShip != null){
                            pathingTile = Map.currentMap.GetTile(movingShip);
                            for (Tile t : pathingTile.tilesInWalkDistance[4]) {
                                if(!t.alreadySetInspire) {
                                    pathingLikelyThere[turn][t.x][t.y]++;
                                    t.alreadySetInspire = true;
                                }

                                //  Integer spotid =  t.x + width * (t.y  +  height * (turn + maxTurn * movingShip.id));
                                //alreadySetSpot.add(spotid);


//                            int uniqueid = t.x + t.y * width + ship.id * width * height;
//                            alreadySetSpot.add(uniqueid)
                            }
                        }
                    }

                    for (Tile t : start.tilesInWalkDistance[distCouldBe]) {
//                        Integer spotid = t.x + width * (t.y + height * (turn + maxTurn * ship.id));
//                        if (!alreadySetSpot.contains(spotid)) {
//                            couldbeThere[turn][t.x][t.y]++;
//                            //alreadySet[t.x][t.y] = true;
//                            alreadySetSpot.add(spotid);
//                        }

                        if(!t.alreadySetInspire){
                            t.alreadySetInspire = true;
                            couldbeThere[turn][t.x][t.y]++;
                        }

                    }
//                    if (Plan.timeErrors < 15) {
                    for (Tile t : start.tilesInWalkDistance[distMaybe]) {

                        if(!t.alreadySetInspire){
                            maaaybeThere[turn][t.x][t.y]++;
                        }

//                            Integer spotid = t.x + width * (t.y + height * (turn + maxTurn * ship.id));
//                            if (!alreadySetSpot.contains(spotid)) {
//                                maaaybeThere[turn][t.x][t.y]++;
//                            }
                    }
//                    }



                    for (Tile t : start.tilesInWalkDistance[distCouldBe]) {
                        t.alreadySetInspire = false;
                    }
                    if(pathingTile != null){
                        for (Tile t : pathingTile.tilesInWalkDistance[4]) {
                            t.alreadySetInspire = false;
                        }
                    }
                }




            }
        }







        for(int turn = 0; turn < maxTurn; turn++) {

            float pathnotodds = 1.0f - (float)(HandwavyWeights.OddsPathBase * Math.pow(HandwavyWeights.OddsNotPathTurnPow,turn));

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {

                    if (certainlyThere[turn][x][y] >= 2) {
                        inspireOdds[turn][x][y] = 1.0f;
                    } else if (certainlyThere[turn][x][y] == 1) {
                        float oddsOfNothing = (float)(Math.pow(pathnotodds, pathingLikelyThere[turn][x][y]) * Math.pow(HandwavyWeights.OddsNotLikelyV3, likelyThere[turn][x][y]) * Math.pow(HandwavyWeights.OddsNotCouldbeV2, couldbeThere[turn][x][y]) * Math.pow(HandwavyWeights.OddsNotMaybeV4, maaaybeThere[turn][x][y]));
                        inspireOdds[turn][x][y] = (1.0f - oddsOfNothing) * HandwavyWeights.TrustInInspirePredictionV3;
                    } else{


                        int pathing = pathingLikelyThere[turn][x][y];
                        int likely = likelyThere[turn][x][y];
                        int couldbe = couldbeThere[turn][x][y];
                        int maybe = maaaybeThere[turn][x][y];

                        if(pathing + likely + couldbe + maybe >= 2) {
                            //I cant help but feel this is a little too complicated
                            //Though I'm tempted to make all this here a one-liner
                            double oddsOf1p = 1.0 - Math.pow(pathnotodds, pathing);
                            double oddsOf1l = 1.0 - Math.pow(HandwavyWeights.OddsNotLikelyV3, likely);
                            double oddsOf1c = 1.0 - Math.pow(HandwavyWeights.OddsNotCouldbeV2, couldbe);
                            double oddsOf1m = 1.0 - Math.pow(HandwavyWeights.OddsNotMaybeV4, maybe);

                            double oddsNot2p = 1.0 - (oddsOf1p * (1.0 - Math.min(1.0, Math.pow(pathnotodds, pathing - 1))));
                            double oddsNot2l = 1.0 - (oddsOf1l * (1.0 - Math.min(1.0, Math.pow(HandwavyWeights.OddsNotLikelyV3, likely - 1))));
                            double oddsNot2c = 1.0 - (oddsOf1c * (1.0 - Math.min(1.0, Math.pow(HandwavyWeights.OddsNotCouldbeV2, couldbe - 1))));
                            double oddsNot2m = 1.0 - (oddsOf1m * (1.0 - Math.min(1.0, Math.pow(HandwavyWeights.OddsNotMaybeV4, maybe - 1))));

                            double oddsNotpl = 1.0 - (oddsOf1p * oddsOf1l);
                            double oddsNotpc = 1.0 - (oddsOf1p * oddsOf1c);
                            double oddsNotpm = 1.0 - (oddsOf1p * oddsOf1m);

                            double oddsNotlc = 1.0 - (oddsOf1l * oddsOf1c);
                            double oddsNotlm = 1.0 - (oddsOf1l * oddsOf1m);

                            double oddsNotcm = 1.0 - (oddsOf1c * oddsOf1m);

                            double oddsAtLeastOnecomboHappens = 1.0 - (oddsNot2p * oddsNot2l * oddsNot2c * oddsNot2m * oddsNotpl * oddsNotpc * oddsNotpm * oddsNotlc * oddsNotlm * oddsNotcm);

                            inspireOdds[turn][x][y] = (float) oddsAtLeastOnecomboHappens * HandwavyWeights.TrustInInspirePredictionNothingV2;
                        }
                    }

                }
            }
        }


        if(MyBot.DO_GAME_OUTPUT){

            StringBuilder s = new StringBuilder();

            s.append("inspireodds0:");
            for(int y =0; y < height; y++){
                for(int x=0; x < width; x++){
                    s.append( inspireOdds[0][x][y] + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());
        }

        if(MyBot.DO_GAME_OUTPUT){

            StringBuilder s = new StringBuilder();

            s.append("inspireodds1:");
            for(int y =0; y < height; y++){
                for(int x=0; x < width; x++){
                    s.append( inspireOdds[1][x][y] + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());
        }

        if(MyBot.DO_GAME_OUTPUT ){

            StringBuilder s = new StringBuilder();

            s.append("inspireoddslast:");
            for(int y =0; y < height; y++){
                for(int x=0; x < width; x++){
                    s.append( inspireOdds[maxTurn - 1][x][y] + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());
        }

//
//          String test = "Inspiremap t : " + turn +  " \r\n\r\n";
//            for (int y = 0; y < height; y++) {
//                for (int x = 0; x < width; x++) {
//                    test += " " + String.format("%.3f", inspireOdds[x][y]);
//                }
//                test += "\r\n";
//            }
//            test += "\r\n";
//            test += "\r\n";
//        Log.log(test, Log.LogType.TEMP);


        //TODO:delete this

        if(MyBot.CHECK_INSPIRE_ACCURACY){
            inspireHistory[MyBot.turn] = inspireOdds;
            CheckInspireAccuracy(inspireOdds[0]);
        }


        return inspireOdds;

    }
    //Calculate the odds that a tile will be inspired for us in x turns
    final static float[][][] InspireoddsNew(int maxTurn){



        float[][][] inspireOdds =new float[maxTurn][width][height];


        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++) {
                for (int turn = 0; turn < maxTurn; turn++) {
                    certainlyThere[turn][x][y] = 0;
                    likelyThere[turn][x][y] = 0;
                    pathingLikelyThere[turn][x][y] = 0;
                    couldbeThere[turn][x][y] = 0;
                }
            }
        }
        Tile[][][] expectedShipLocs = new Tile[maxTurn][Map.staticEnemyShipCount2][];

        for (int index =0; index < Map.staticEnemyShipCount2; index++) {
            CheapShip ship = Map.staticEnemyShips.get(index);
            if(ship != null) {
                Tile start =  Map.tiles[ship.x][ship.y];
                boolean canMovet0 = ship.CanMove(Map.currentMap);

                Tile usedToBeAt = null;
                for(CheapShip s : Map.allShipsOnTurn[Math.max(0,MyBot.turn - 3)]){
                    if(s.id == ship.id){
                        usedToBeAt = Map.tiles[s.x][s.y];
                        break;
                    }
                }
                Tile prediction = start;
                if(usedToBeAt != null){
                    prediction = Map.currentMap.GetTile( start.x +  start.dx(usedToBeAt), start.y +  start.dy(usedToBeAt));
                }
                int roomleft = ship.roomLeft();



                for (int turn = 1; turn < maxTurn; turn++) {
                    if(turn == 1 && !canMovet0){
                        expectedShipLocs[turn][index] = new Tile[]{start};
                    }
                    else{
                        int spots =  Math.min(4,2 +  (int)(turn * 0.5));
                        int maxDist;
                        if(canMovet0){
                            maxDist = (int)(turn * 0.5 + 0.5);
                        }else{
                            maxDist = (int)(turn * 0.5);
                        }
                        maxDist = Math.min(maxDist,start.tilesInWalkDistance.length - 1);

                        ArrayList<Tile> tileList = new ArrayList<>(start.tilesInWalkDistance[maxDist]);

                        if(ship.halite > 900 && start.haliteStartTurn < 100){
                            for (Tile t : tileList) {
                                float score = -t.turnsFromEnemyDropoff * 1.5f - t.ComplexDist(start) * 0.9f;
                                if(t.haliteStartTurn > 100){
                                    score += t.haliteStartTurn * 0.01f;
                                }else{
                                    score -= t.haliteStartTurn * 0.01f;
                                }
                                score -= t.DistManhattan(prediction) * 0.4f;
                                t.score = score;
                            }
                        }else {
                            for (Tile t : tileList) {
                                float score = Math.min(roomleft,t.haliteStartTurn * 0.8f);
                                score -= t.ComplexDist(start) * 360f;
                                score -= t.DistManhattan(prediction) * 30f;
                                t.score = score;
                            }
                        }

                        Collections.sort(tileList);

                        expectedShipLocs[turn][index] = new Tile[spots];
                        for(int i=0; i < spots;i++){
                            expectedShipLocs[turn][index][i] = tileList.get(i);
                        }


                    }
                }


            }

        }






        for (int turn = 0; turn < maxTurn; turn++) {
            //  HashSet<Integer> alreadySetSpot = new HashSet<>();
            //boolean[][] alreadySet = new boolean[width][height];
            // for (CheapShip ship : expectedShipLocs[0]) {

            for (int index =0; index < Map.staticEnemyShipCount2; index++) {
                CheapShip ship = Map.staticEnemyShips.get(index);

                if(ship != null) {
                    Tile start = Map.currentMap.GetTile(ship);

                    int distCertainly = 4 - turn;
                    if (distCertainly > 0) {
                        for (Tile t : start.tilesInWalkDistance[distCertainly]) {
                            certainlyThere[turn][t.x][t.y]++;
                            t.alreadySetInspire = true;
                        }
                    }

                    for (Tile t : start.tilesInWalkDistance[4]) {
                        if(!t.alreadySetInspire){
                            t.alreadySetInspire = true;
                            likelyThere[turn][t.x][t.y]++;
                        }
                    }

                    Tile[] pathingTiles = expectedShipLocs[turn][index];

                    if(pathingTiles != null) {
                        for (int i = 0; i < pathingTiles.length; i++) {
                            for (Tile t : pathingTiles[i].tilesInWalkDistance[3]) {
                                if (!t.alreadySetInspire) {
                                    pathingLikelyThere[turn][t.x][t.y]++;
                                    t.alreadySetInspire = true;
                                }
                            }

                        }

                        for (int i = 0; i < pathingTiles.length; i++) {
                            for (Tile t : pathingTiles[i].tilesInWalkDistance[4]) {
                                if (!t.alreadySetInspire) {
                                    couldbeThere[turn][t.x][t.y]++;
                                    t.alreadySetInspire = true;
                                }
                            }
                        }

                        for(int i =0; i < pathingTiles.length; i++){
                            for (Tile t : pathingTiles[i].tilesInWalkDistance[4]) {
                                t.alreadySetInspire = false;
                            }
                        }
                    }

                    for (Tile t : start.tilesInWalkDistance[4]) {
                        t.alreadySetInspire = false;
                    }

                }
            }
        }


        for(int turn = 0; turn < maxTurn; turn++) {
            float pathnotodds = 1.0f - (float)(HandwavyWeights.OddsPathBaseNew * Math.pow(HandwavyWeights.OddsNotPathTurnPowNew,turn));
            float pathnotoddsCouldBe = 1.0f - (float)(HandwavyWeights.OddsPathBaseCouldBeNew * Math.pow(HandwavyWeights.OddsNotPathTurnPowNew,turn));

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {

                    if (certainlyThere[turn][x][y] >= 2) {
                        inspireOdds[turn][x][y] = 1.0f;
                    } else if (certainlyThere[turn][x][y] == 1) {
                        float oddsOfNothing = (float)(Math.pow(pathnotodds, pathingLikelyThere[turn][x][y]) * Math.pow(pathnotoddsCouldBe, couldbeThere[turn][x][y]) * Math.pow(HandwavyWeights.OddsNotLikelyV3, likelyThere[turn][x][y]));
                        inspireOdds[turn][x][y] = (1.0f - oddsOfNothing) * HandwavyWeights.TrustInInspirePredictionV3;

                    } else{
                        int pathing = pathingLikelyThere[turn][x][y];
                        int likely = likelyThere[turn][x][y];
                        int couldbe = couldbeThere[turn][x][y];


                        if(pathing + likely + couldbe >= 2) {
                            //I cant help but feel this is a little too complicated
                            //Though I'm tempted to make all this here a one-liner
                            double oddsOf1p = 1.0 - Math.pow(pathnotodds, pathing);
                            double oddsOf1l = 1.0 - Math.pow(HandwavyWeights.OddsNotLikelyV3, likely);
                            double oddsOf1c = 1.0 - Math.pow(pathnotoddsCouldBe, couldbe);

                            double oddsNot2p = 1.0 - (oddsOf1p * (1.0 - Math.min(1.0, Math.pow(pathnotodds, pathing - 1))));
                            double oddsNot2l = 1.0 - (oddsOf1l * (1.0 - Math.min(1.0, Math.pow(HandwavyWeights.OddsNotLikelyV3, likely - 1))));
                            double oddsNot2c = 1.0 - (oddsOf1c * (1.0 - Math.min(1.0, Math.pow(pathnotoddsCouldBe, couldbe - 1))));
                            double oddsNotpl = 1.0 - (oddsOf1p * oddsOf1l);
                            double oddsNotpc = 1.0 - (oddsOf1p * oddsOf1c);
                            double oddsNotlc = 1.0 - (oddsOf1l * oddsOf1c);

                            double oddsAtLeastOnecomboHappens = 1.0 - (oddsNot2p * oddsNot2l * oddsNot2c *  oddsNotpl * oddsNotpc *  oddsNotlc);

                            inspireOdds[turn][x][y] = (float) oddsAtLeastOnecomboHappens * HandwavyWeights.TrustInInspirePredictionNothingV2;
                        }
                    }

                }
            }
        }


        if(MyBot.DO_GAME_OUTPUT){

            StringBuilder s = new StringBuilder();

            s.append("inspireodds0:");
            for(int y =0; y < height; y++){
                for(int x=0; x < width; x++){
                    s.append( inspireOdds[0][x][y] + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());
        }

        if(MyBot.DO_GAME_OUTPUT){

            StringBuilder s = new StringBuilder();

            s.append("inspireodds1:");
            for(int y =0; y < height; y++){
                for(int x=0; x < width; x++){
                    s.append( inspireOdds[1][x][y] + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());
        }

        if(MyBot.DO_GAME_OUTPUT ){

            StringBuilder s = new StringBuilder();

            s.append("inspireoddslast:");
            for(int y =0; y < height; y++){
                for(int x=0; x < width; x++){
                    s.append( inspireOdds[maxTurn - 1][x][y] + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());




        }

        if(MyBot.DO_GAME_OUTPUT ) {
            StringBuilder s = new StringBuilder();

            s.append("inspirepathpredictions:");
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    s.append(pathingLikelyThere[maxTurn - 1][x][y] + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());
        }

        if(MyBot.CHECK_INSPIRE_ACCURACY){
            inspireHistory[MyBot.turn] = inspireOdds;
            CheckInspireAccuracy(inspireOdds[0]);
        }

        return inspireOdds;

    }


    static float[] totalErrorPerTurn = new float[20];
    static float[] totalAbsDifPerTurn = new float[20];
    static  float[] totalSquaredErrorPerTurn = new float[20];
    static int[] totalGuesses = new int[20];


    //How good are our inspire predictions?
    public static void CheckInspireAccuracy(float[][] actualInspire){
        for(int turn=0; turn < MyBot.turn; turn++){
            if(inspireHistory[turn] != null){
                if( turn + (inspireHistory[turn].length - 1) < MyBot.turn){
                    inspireHistory[turn] = null;
                }
                else{
                    int turnsInFuture = MyBot.turn - turn;
                    float[][] predictions = inspireHistory[turn][turnsInFuture];


                    for(int x=0;x < Map.width;x++){
                        for(int y=0;y < Map.height;y++) {
                            float dif = predictions[x][y] - actualInspire[x][y];
                            totalAbsDifPerTurn[turnsInFuture] += dif;
                            totalErrorPerTurn[turnsInFuture] += Math.abs(dif);
                            totalSquaredErrorPerTurn[turnsInFuture] += dif * dif;
                        }
                    }
                    totalGuesses[turnsInFuture] += Map.height * Map.width;
                }
            }
        }

        Log.log("Inspire Accuracy: " , Log.LogType.MAIN);
        for(int i =0; i < 20; i++){
            if(totalGuesses[i] > 0){
                Log.log(i + " : " +  (totalErrorPerTurn[i] / totalGuesses[i]) + "   " + (totalSquaredErrorPerTurn[i] / totalGuesses[i]) + "  " + (totalAbsDifPerTurn[i] / totalGuesses[i]), Log.LogType.MAIN );
            }
        }
    }



    //Control edges are areas that are at roughly equal distance between my dropoffs and dropoffs of some enemy
    public static float[][] GetControlEdgeAreas(){
        float[][] slippingAwayZones = new float[Map.width][Map.height];


        float factor = 1f;
        if(MyBot.turn < 40 && Map.staticMyShipCount < 6) {
            factor = 0.5f;//let's focus hard on getting those early ships first
        }



        for(int x =0; x < Map.width; x++){
            for(int y =0; y < Map.height; y++) {
                Tile t = Map.tiles[x][y];
                float control = 0.6f * (  -t.enemyTerrain +  (t.turnsFromEnemyDropoff - t.turnsFromDropoff ));
                //This formula transforms it into roughly what we want. Probably a bit lengthy for what it does, but oh well

                //https://www.wolframalpha.com/input/?i=(-1+*+((x+-+3)+%2F+(%7Cx%7C+%2B+4)%5E1.2++++%2B++x*0.02+-+0.15))++%2B+0.45+%2B+%7Cx%7C+-+%7Cx%7C%5E1.035+++++++++++from+x+%3D+-15+to+x%3D7
                float adjustedcontrol = (float)Math.max(0.0, -((control - 3f) / (Math.pow(Math.abs(control) + 4.0,1.2)) + control*0.02 - 0.15) + 0.4 + Math.abs(control) - Math.pow(Math.abs(control),1.04));

                //This is more for the macro level changes. We shouldn't care as much about areas in far corners as right in the middle between players
                //Also affects the curve a bit though
                adjustedcontrol *=  (22f / (t.turnsFromDropoff + 10f));

                slippingAwayZones[x][y] = adjustedcontrol * factor;
            }
        }

        if(MyBot.DO_GAME_OUTPUT){
            StringBuilder s = new StringBuilder();
            s.append("edgemap:");
            for(int y =0; y < Map.height; y++){
                for(int x=0; x < Map.width; x++){
                    s.append(slippingAwayZones[x][y] + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());
        }

        return slippingAwayZones;

    }


    //Borders are areas roughly as far away from my ships as enemy ships
    //Probably needs a rework since it gets weird with single ships behind enemy lines
    public static void SetBorders(){
        for(Tile t : Map.tileList){
            t.borderTile = t.turnsToReachMyShips <= t.turnsToReachEnemyShips + 2 && t.turnsToReachMyShips >= t.turnsToReachEnemyShips * 0.8  && t.turnsToReachMyShips < 6;
        }

        if(MyBot.DO_GAME_OUTPUT){
            StringBuilder s = new StringBuilder();
            s.append("borders:");
            for(int y =0; y < height; y++){
                for(int x=0; x < width; x++){

                    if(Map.tiles[x][y].borderTile){
                        s.append( "1,");
                    }else{
                        s.append( "0,");
                    }
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());
        }
    }

    //Gives a score to tiles depending on the amount of enemies near of players we really don't want to give any points
    //Ths is supposed to make us avoid areas with lots of enemy units, while still going for the places where we can get
    //inspire at little cost
    final static void CalculateAntiInspire(){
        Competitor mainEnemy = null;
        if(HandwavyWeights.ActivateAntiInspire == 0 || (HandwavyWeights.ActivateAntiInspire == 2 && MyBot.playerCount == 4 ) || (HandwavyWeights.ActivateAntiInspire == 3 && MyBot.playerCount == 2 ) ) return;

        if(MyBot.playerCount == 2){
            mainEnemy = MyBot.enemy1;
        }else{
            if(MyBot.turn < HandwavyWeights.AntiInspire4pMinTurnV2) return;

            float bestScore = HandwavyWeights.AntiInspire4pMinPlayerDifV2;

            for(Competitor c : MyBot.players){
                if(!c.isMe){
                    float score =  (float)Math.abs(c.scaryFactor - 1f);
                    if(score < bestScore){
                        bestScore = score;
                        mainEnemy = c;
                    }
                }
            }
        }

        for(Tile t : Map.tileList){
            t.antiInspire = 0;
        }
        if(mainEnemy == null) return;


        for(CheapShip s : mainEnemy.ships){

            Tile tile = s.GetTile();

            for(Tile t : tile.tilesInWalkDistance[6]){
                t.antiInspire += HandwavyWeights.AntiInspire6ContributionV2;
            }
            for(Tile t : tile.tilesInWalkDistance[4]){
                t.antiInspire += HandwavyWeights.AntiInspire4ContributionV2;
            }
            for(Tile t : tile.tilesInWalkDistance[2]){
                t.antiInspire += HandwavyWeights.AntiInspire2ContributionV2;
            }

        }

        for(Tile t : Map.tileList){
            t.antiInspire = Math.max(0,t.antiInspire - HandwavyWeights.AntiInspireBaseDecreaseByV2);
        }


    }

    //Tiles that form the + sign around our myDropoffs
    //Not very useful
    final static boolean[][] GetCrossMap(){
        boolean[][] cross = new boolean[width][height];

        for(DropPoint d : Map.myDropoffs){
            ArrayList<Tile> spots = new ArrayList<>();
            Tile t = Map.tiles[d.x][d.y];

            spots.add(t.North());
            spots.add(t.North().North());
            spots.add(t.North().North().North());
            spots.add(t.North().North().North().North());

            spots.add(t.South());
            spots.add(t.South().South());
            spots.add(t.South().South().South().South());

            spots.add(t.East());
            spots.add(t.East().East());
            spots.add(t.East().East().East().East());

            spots.add(t.West());
            spots.add(t.West().West());
            spots.add(t.West().West().West().West());

            for(Tile t2 : spots){
                cross[t2.x][t2.y] = true;
            }

        }
        return cross;
    }

    //Find the best spot for a future dropoff if any reasonable spots exist
    final static Tile GetBestDropoffSpot(){
        int[][] shipsNear = new int[width][height];
        int[][] shipsNearish = new int[width][height];
        int[][] enemyShipsNearish = new int[width][height];
        int[][] haliteNear = new int[width][height];
        int[][] haliteNearish = new int[width][height];

        for(CheapShip s : Map.staticMyShips){
            if( s != null){
                for(Tile t : Map.currentMap.GetTile(s).tilesInWalkDistance[HandwavyWeights.DropoffNearTiles]){
                    shipsNear[t.x][t.y]++;
                    haliteNear[t.x][t.y] += s.halite;
                }
                for(Tile t : Map.currentMap.GetTile(s).tilesInWalkDistance[HandwavyWeights.DropoffNearishTiles]){
                    shipsNearish[t.x][t.y]++;
                    haliteNearish[t.x][t.y] += s.halite;

                }
            }
        }

        for(CheapShip s : Map.staticEnemyShips) {
            if (s != null) {
                for(Tile t : Map.currentMap.GetTile(s).tilesInDistance[4]){
                    enemyShipsNearish[t.x][t.y]++;
                }
            }
        }


        //just for display purposes. TODO: remove this at end of the competition
        double[][] scoreMap = new double[width][height];
        double bestscore = HandwavyWeights.DropoffMinSpotScore;
        Tile bestTile = null;

        for(int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int dist = Plan.turnsFromDropoff[x][y];
                if(Map.tiles[x][y].haliteStartTurn > 4000 || (  shipsNear[x][y] + shipsNearish[x][y] > Math.max(HandwavyWeights.MinShipsInRegion,enemyShipsNearish[x][y] * HandwavyWeights.ProprtionMinEnemyNearish)  && dist > HandwavyWeights.MinStepsFromDropoff && dist < HandwavyWeights.MaxStepsFromDropoff && Map.enemyDropoffMap[x][y] == null)){
                    double score = 0, denyScore = 0f;
                    Tile t = Map.tiles[x][y];

                    double nearbyHalite = 0;
                    double nearbyHaliteOnShips = 0;
                    for(Tile t2 : t.tilesInWalkDistance[5]){
                        if(Plan.turnsFromDropoff[t2.x][t2.y] >  HandwavyWeights.MinStepsFromDropoffTiles && Plan.turnsFromEnemyDropoff[x][y]  >  HandwavyWeights.DropoffCloseToEnemyTiles) {
                            score += t2.haliteStartTurn * HandwavyWeights.DropoffWeightHalNear;
                            nearbyHalite += t2.haliteStartTurn * 0.75; //(not 1, or wed get > 1 per tile since were double passing these tiles)
                        }else{
                            nearbyHalite += t2.haliteStartTurn * 0.5;
                        }
                    }
                    for(Tile t2 : t.tilesInWalkDistance[7]){
                        if(Plan.turnsFromDropoff[t2.x][t2.y] >  HandwavyWeights.MinStepsFromDropoffTiles && Plan.turnsFromEnemyDropoff[x][y]  >  HandwavyWeights.DropoffCloseToEnemyTiles ) {
                            score += t2.haliteStartTurn * HandwavyWeights.DropoffWeightHalNearish;
                            nearbyHalite += t2.haliteStartTurn * 0.25;
                        }else{
                            nearbyHalite += t2.haliteStartTurn * 0.1;
                        }
                    }

                    int distanceSavings = 0;
                    int shipsSavingDist = 0;
                    for(CheapShip s : Map.staticMyShips){
                        Tile st = s.GetTile();

                        int shipdist =  t.DistManhattan(st);
                        if(s.halite > 200) {
                            distanceSavings += Math.max(0, st.turnsFromDropoff - shipdist);
                        }

                        if(shipdist < st.turnsFromDropoff + 2){
                            shipsSavingDist++;
                        }


                        if(shipdist < Math.min(st.turnsFromDropoff,8)){
                            nearbyHaliteOnShips += s.halite;
                        }
                    }

                    if(nearbyHalite > HandwavyWeights.DropoffAbsoluteMin && shipsSavingDist >= HandwavyWeights.DropoffAbsoluteMinShipsSavedTime) {
                        if (nearbyHalite > HandwavyWeights.DropoffMinHaliteNearSpot || (MyBot.me.dropoffCount < 2 && nearbyHalite > HandwavyWeights.DropoffMinHaliteNearSpotEarly) || distanceSavings > HandwavyWeights.DropoffWeightMinDistanceSavings) {
                            score += shipsNear[x][y] * HandwavyWeights.DropoffWeightShipsNear + shipsNearish[x][y] * HandwavyWeights.DropoffWeightShipsNearish + enemyShipsNearish[x][y] * HandwavyWeights.DropoffWeightEnemyShipsNearish;

                            score += distanceSavings * HandwavyWeights.DropoffWeightDistanceSavings;
                            score += nearbyHaliteOnShips * HandwavyWeights.DropoffWeightNearbyShipHalite;
                            score += haliteNear[x][y] * HandwavyWeights.DropoffWeightShipHalNear + haliteNearish[x][y] * HandwavyWeights.DropoffWeightShipHalNearish;

                            score += Math.min(Plan.turnsFromEnemyDropoff[x][y] * HandwavyWeights.DropoffScoreTurnsFromEnemy, HandwavyWeights.DropoffScoreTurnsFromEnemyNormalize) - HandwavyWeights.DropoffScoreTurnsFromEnemyNormalize;

                            if (Plan.turnsFromEnemyDropoff[x][y] < HandwavyWeights.DropoffCloseToEnemy) {
                                score -= HandwavyWeights.DropoffPunishmentCloseToEnemy * HandwavyWeights.DropoffPunishmentCloseToEnemySizeMult[MyBot.GAMETYPE_SIZE];
                            }

                            score += Math.min(HandwavyWeights.MaxGainFromDistance2, dist) * HandwavyWeights.DropoffWeightDistV2;

                            score += Math.max(0, dist - HandwavyWeights.MaxGainFromDistance) * HandwavyWeights.DropoffWeightTooFarMult;

                            CheapShip friendlyShipHere = Map.currentMap.GetShipAt(t);
                            if (friendlyShipHere != null && Map.DoIOwnShip[friendlyShipHere.id]) {
                                score += HandwavyWeights.DropoffWeightContainsFriendlyShip + friendlyShipHere.halite * HandwavyWeights.DropoffWeightFriendlyShipHalite;
                            }

                            score += Plan.medDistLure[x][y] * HandwavyWeights.DropoffWeightMedLure;
                            score += Plan.longDistLure[x][y] * HandwavyWeights.DropoffWeightLongLure;
                            score += Map.staticHaliteMap[x][y] * HandwavyWeights.DropoffWeightHalOnSpot;

                            if (Map.tiles[x][y].haliteStartTurn > 4000) {
                                score += 5000;
                            }

                            if (t.equals(Plan.lastTurnDropOffSpot)) {
                                score += HandwavyWeights.DropoffWeightConsistentSelection;
                            }


                            if(t.turnsToReachEnemyShips - t.turnsToReachMyShips > HandwavyWeights.TilesDistanceLowPriorityZone){
                                denyScore += HandwavyWeights.DropoffLowPrioZoneHalite[MyBot.GAMETYPE_PLAYERS] * t.haliteStartTurn + HandwavyWeights.DropoffLowPrioZoneFlat[MyBot.GAMETYPE_PLAYERS];
                            }
                            if(t.isCenterTile) {
                                denyScore += HandwavyWeights.DropoffCentralFlat * haliteNear[x][y] + HandwavyWeights.DropoffCentralHalite;
                            }


                            score += denyScore * HandwavyWeights.DropoffDenyMultiplier;


                            if (Map.currentMap.IsEnemyShipAt(t)) {
                                score *= 0.5;
                            }


                            scoreMap[x][y] = score;
                            if (score > bestscore) {
                                bestscore = score;
                                bestTile = Map.tiles[x][y];
                                bestTile.desirability = (float) bestscore;
                            }


                        }
                    }



//                    double finalScore = Plan.turnsFromDropoff[s.x][s.y] * HandwavyWeights.DropoffWeightDistV2 +  + Map.currentMap.GetHaliteIgnoreProposals(s.x, s.y) * HandwavyWeights.DropoffWeightHalOnSpot;
//
//                    finalScore += maphaliteleft * HandwavyWeights.DropoffWeightHalLeftOnMap;


                }
            }
        }

        if(MyBot.DO_GAME_OUTPUT){
            StringBuilder s = new StringBuilder();
            s.append("dropoffscore:");
            for(int y =0; y < height; y++){
                for(int x=0; x < width; x++){
                    s.append( scoreMap[x][y] + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());
        }


        return bestTile;

    }


}
