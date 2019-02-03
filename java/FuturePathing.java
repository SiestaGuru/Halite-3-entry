
import java.util.TreeSet;

/*
One of the side-algorithms thats effectively a bot on its own.

This algorithm works by having ships path through a 3D space towards dropoffs
The three dimensions are x,y and halite
Every move that changes the halite on a ship is seen as a move that moves the ship along the halite dimension
It then tries to path towards dropoffs with their regular x,y locations that have high halite coordinates
It paths by use of a depth/best first sort of approach. To save on memory allocations it doesn't keep track of a list of nodes

For ships that can't find enough halite in time to reach a dropoff while full, a secondary search can be done, which doesn't find dropoffs, but instead rewards halite gather efficiency

Ships are executed one by one and after their executing lay claim on the path they passed so other ships won't go there


Unfortunately, while the algorithm works fine and comes up with essentially optimal paths for single ships, it didn't end up
nearly as good as the main algorithm.
Main reasons seem to be:
- ship conflicts aren't handled well
- plans keep changing slightly so ships don't actually follow the plans consistently
- it ignores many aspects of the game, such as area control

 */

public class FuturePathing {



    public static boolean[][][] Reserved = null;
    public static float[][] MeaningfullyAltered = null;


    public static final int haveTriedLength = 30000;
    public static boolean[] haveAlreadyTried = new boolean[haveTriedLength];

    public static float[] dropOffMultiplierPerHalite = new float[1001];
    //public final static int MaxQueue = 50;
    public static int MaxQueue = 25;//20;//25;

    public final static long NOT_A_MOVE = -234235253;
    public static Move[][] lastTurnFuturePathing = new Move[5000][];

    public static boolean ALLOW_NON_FINISHER;



    public static Move[][] GetMoveSuggestions(){

        Move[][] result;
        if(MyBot.DO_GAME_OUTPUT){
            result = new Move[MaxQueue][Map.staticMyShipCount];
        }else {
            result = new Move[Plan.SEARCH_DEPTH][Map.staticMyShipCount];
        }

        if(HandwavyWeights.ActivateFuturePathing == 0 || Map.width == 64) return result;

        TreeSet<SortableShip> sortedShips = new TreeSet<>();

        for(CheapShip s : Map.staticMyShips){
            sortedShips.add( new SortableShip(s,GetShipPriority(s)));
        }

        FuturePathing.Init();
        for(SortableShip ss : sortedShips){
            CheapShip s = ss.ship;
            int index = Map.myIndexOfIds[s.id];
            Move[] moves = FuturePathing.FuturePathing(s,lastTurnFuturePathing[s.id],true);
            //Move[] moves = FuturePathing.FuturePathing(s,lastTurnFuturePathing[s.id],MyBot.turn < 80 || s.halite > 350);
            if(moves != null) {

                for(int i=0; i < Math.min(moves.length,result.length); i++){
                    result[i][index] = moves[i];
                }
            }
            lastTurnFuturePathing[s.id] = moves;
        }

        if(MyBot.DO_GAME_OUTPUT){
            String s = "plan:" + 0 +  ";" + MaxQueue + ";" + 0 + ":";
            for(int i = 0; i < MaxQueue; i++){
                for(Move m : result[i]) {
                    if(m != null) {
                        s += m.from.x + "," + m.from.y + "," + m.to.x + "," + m.to.y + "," + m.ship.id + "/";
                    }
                }
                s += ";";
            }
            GameOutput.info.add(s);
        }

        return result;
    }


    public static float GetShipPriority(CheapShip s){
        Tile t = s.GetTile();
        float score = t.turnsFromDropoff * HandwavyWeights.PrioFuturePathDistDropoff;
        score += s.halite * HandwavyWeights.PrioFuturePathHalite;
        score += t.myShipsStartInRange5Avg * HandwavyWeights.PrioFuturePathCrowdedEnemy;
        score += t.enemyShipsStartInRange5Avg * HandwavyWeights.PrioFuturePathCrowdedMy;
        score += t.haliteStartInRange5Avg * HandwavyWeights.PrioFuturePathHaliteAround;
        return score + MyBot.rand.nextFloat() * 0.001f; //to stop exact same valuations
    }


//

    public static void Init(){
        MaxQueue = HandwavyWeights.FuturePathMaxQueue;
        ALLOW_NON_FINISHER = HandwavyWeights.ActivateFuturePathingNonReturn == 1;
        if(Reserved == null){
            Reserved = new boolean[Map.width][Map.height][MaxQueue];
            MeaningfullyAltered = new float[Map.width][Map.height];
        }else{
            for(int x =0; x < Map.width; x++){
                for(int y =0; y < Map.height; y++) {
                    for(int t =0; t < MaxQueue; t++) {
                        Reserved[x][y][t] = false;
                    }
                    MeaningfullyAltered[x][y] = 0f;
                }
            }
        }

        for(CheapShip ship : Map.staticMyShips){
            //MeaningfullyAltered[ship.x][ship.y] = true;
            if(ship.halite < MyBot.moveCosts[Map.staticHaliteMap[ship.x][ship.y]]) {
                Reserved[ship.x][ship.y][0] = true;
            }
        }

        for(CheapShip ship : Map.staticEnemyShips){
            Reserved[ship.x][ship.y][0] = true;
            Reserved[ship.x][ship.y][1] = true;
            Reserved[ship.x][ship.y][2] = true;
            Reserved[ship.x][ship.y][3] = true;

            MeaningfullyAltered[ship.x][ship.y]+= 1f;

            for(Tile t : Map.tiles[ship.x][ship.y].neighbours){
                MeaningfullyAltered[t.x][t.y]+= 0.5f;

                if(ship.halite >= MyBot.moveCosts[Map.staticHaliteMap[ship.x][ship.y]]) {
                    Reserved[t.x][t.y][1] = true;
                }
            }
        }
    }
    static float[][] bestResults = new float[Map.width][Map.height];
   // static float[][][] bestResults = new float[Map.width][Map.height][MaxQueue];


    public static Move[] FuturePathing(CheapShip s, Move[] previouspath, boolean searchDropoffAlgo){

        //TODO: pack moves into ints/longs and use an old fashioned array to store the current moves etc, should significantly reduce allocations

        int maxTurnStandStill = HandwavyWeights.FuturePathMaxMoveDepth; //on later turns, the odds that we'll actually be able to get there, stand still and collect is minimal, so let's not take that into our calculations

        int QueueMaxSize = MaxQueue;
        int maxIterations = 40000 - Math.min(25000,Map.staticMyShipCount * 40  +  Map.width * Map.height);


//        if(MyBot.THINK_QUICKLY){
//            maxIterations *= 0.7;
//            QueueMaxSize *= 0.9;
//        }

//        if(!searchDropoffAlgo){
//            QueueMaxSize = Math.min(QueueMaxSize,maxTurnStandStill);
//        }


        long[] currentPath = new long[QueueMaxSize];
        int[] startHaliteOnPreviousTurn = new int[QueueMaxSize];
        long[] bestPath = null;

        boolean[] haveAlreadyTried = new boolean[30000];
        Tile standingOn = Map.tiles[s.x][s.y];
        int[][] haliteMap = new int[Map.width][Map.height];



        for(int x=0; x< Map.width;x++){
            for(int y=0; y< Map.height;y++) {
                haliteMap[x][y] = Map.staticHaliteMap[x][y];
                bestResults[x][y] = -100000f;
                if(!searchDropoffAlgo){
                    Tile goaltile = Plan.goals[s.id];
                    if(goaltile == null){
                        if(s.halite > 400) {
                            goaltile = Map.tiles[s.x][s.y].closestDropoffPlayer[MyBot.myId].tile;
                        }
                    }
                    if(goaltile == null){
                        Map.tiles[x][y].turnsFromTempGoal = 0;
                    }else{
                        Map.tiles[x][y].turnsFromTempGoal = goaltile.DistManhattan(x,y);
                    }
                }
            }
        }

        for(int i = 0 ; i < QueueMaxSize; i++){
            currentPath[i] = NOT_A_MOVE;
        }
        for(int i =0; i < haveTriedLength; i++){
            haveAlreadyTried[i] = false;
        }

        int iteration = 0;
        int currentPathLength = 0;
        int countMeaningfulAltered = 0;
        short currentlyCarryingHalite = s.halite;
        Tile banTile = null;


        float worthTileMoveTermsOfHalite, bestPathScore;

        if(searchDropoffAlgo){
            worthTileMoveTermsOfHalite = Math.max( HandwavyWeights.FuturePathTileHaliteFactorV2, Plan.AverageHalite * HandwavyWeights.FuturePathProportionAvgV2);
            bestPathScore = QueueMaxSize * -worthTileMoveTermsOfHalite +  HandwavyWeights.FuturePathMinHaliteFinishV2; //The bare minimum we'll accept. Reaching the goal at the the max queue size with the minimum halite
        }else{
            worthTileMoveTermsOfHalite = Math.max( HandwavyWeights.FuturePathPathLengthNonFinishV2, Plan.AverageHalite * HandwavyWeights.FuturePathProportionAvgV2);
            bestPathScore = -100000;//  s.halite - HandwavyWeights.FuturePathTurnsDropoffNonFinishV2 *  Map.width/2;
        }



        //Set the starting path equal to what we found last turn, up till the point where that path is still valid. Saves a lot on execution time when we can get in pretty deep
        //Turned off because of suspected bugs
//        if(previouspath != null && previouspath.length > 1){
////            ArrayDeque<Move> oldpath = previouspath.clone();
////            oldpath.removeFirst(); //first move of last turn, should've already been done
////
//
//            if(previouspath[1] != null &&  previouspath[1].from.equals(s.x,s.y)){
//                for(int pathindex =1; pathindex < Math.min(previouspath.length,QueueMaxSize); pathindex++){
//                    Move m = previouspath[pathindex];
//                    if(m != null) {
//                        if (Reserved[m.to.x][m.to.y][currentPathLength]) {
//                            break;
//                        }
//                        if (m.isStandStill()) {
//                            int collect = Math.min(1000 - currentlyCarryingHalite, MyBot.standCollectSafe[haliteMap[standingOn.x][standingOn.y] + 1000]);
//                            float inspireMult = 1f;
//                            if (currentPathLength <= Plan.SEARCH_DEPTH) {
//                                inspireMult = Plan.inspireMultiplier[currentPathLength][standingOn.x][standingOn.y];
//                            }
//                            currentlyCarryingHalite = (short) Math.min(1000, currentlyCarryingHalite + collect * inspireMult);
//                            startHaliteOnPreviousTurn[currentPathLength] = haliteMap[standingOn.x][standingOn.y];
//                            haliteMap[m.to.x][m.to.y] -= collect;
//
//
//                            countMeaningfulAltered += MeaningfullyAltered[standingOn.x][standingOn.y];
//
//
//                        } else {
//                            short moveCost = MyBot.moveCostsSafe[haliteMap[standingOn.x][standingOn.y] + 1000];
//                            if (moveCost > currentlyCarryingHalite) {
//                                break;
//                            }
//                            startHaliteOnPreviousTurn[currentPathLength] = haliteMap[standingOn.x][standingOn.y];
//                            currentlyCarryingHalite -= moveCost;
//
//                        }
//
//                        currentPath[currentPathLength++] = Move.GetMoveLongformat(standingOn, m.to, s.id,currentlyCarryingHalite);
////                        currentPath[currentPathLength++] = new Move(standingOn, m.to, CheapShip.MakeShip(s.id, currentlyCarryingHalite, standingOn.byteX, standingOn.byteY));
//
////                    currentPath.add();
////                    currentPathLength++;
//
//
//                        standingOn = m.to;
//
//                        if (!searchDropoffAlgo) {
//                            float score =  currentlyCarryingHalite +   10f * (currentlyCarryingHalite - s.halite) / Math.max(10f,currentPathLength) - standingOn.turnsFromTempGoal * HandwavyWeights.FuturePathTurnsDropoffNonFinishV2 - countMeaningfulAltered * HandwavyWeights.FuturePathMeaningfulAlterationsFactorNonFinishV2;
//                            if (score > bestPathScore) {
//                                bestPathScore = score;
//                                bestPath = currentPath.clone();
////                                Log.log("Start path, new best for nonfinisher: " + bestPathScore + " halite: " + s.halite + " turns: " + currentPathLength);
//                            }
//                        }
//                    }
//
//                }
//
//                if(searchDropoffAlgo && (standingOn.turnsFromDropoff == 0 && currentlyCarryingHalite >= HandwavyWeights.FuturePathMinHaliteFinishV2)) {
//                    bestPathScore = currentlyCarryingHalite - (standingOn.turnsFromDropoff + currentPathLength) * worthTileMoveTermsOfHalite - countMeaningfulAltered * HandwavyWeights.FuturePathMeaningfulAlterationsFactorV2;
//                    bestPath = currentPath.clone();
////                    Log.log("Starting path to final goal in " + currentPathLength + " steps, with " + currentlyCarryingHalite + " halite ", Log.LogType.MAIN);
//                }else if(!searchDropoffAlgo) {
////                     Log.log("Starting path for " + s.id + " not getting finished result  " + currentPathLength + " steps, with " + currentlyCarryingHalite + " halite ", Log.LogType.MAIN);
//                }
//
//            }
//            else{
////                Log.log(s.id +  "  Wasn't loyal to last turn, expected: " + previouspath[0] + "  reality: " + s, Log.LogType.MAIN);
////                String str = "Old path:    ";
////                for(Move m : previouspath){
////                    if(m != null) {
////                        str += m;
////                    }
////                }
////                Log.log(str, Log.LogType.MAIN);
//
//            }
//        }else{
////            Log.log("No path last turn for " + s.id, Log.LogType.MAIN);
//        }

//        if(s.id == 0){
//            Log.log("entering method", Log.LogType.MAIN);
//        }

        int standstillCounter = 0;


        while(true) {
            iteration++;

            Tile bestTile = null;
            float bestScore = -10000000f;

            if(!searchDropoffAlgo){
                bestScore = 5f;
            }

            float bestSuggestionScore = -10000000f;
            int collect,halitehere;
            short nextHalite = currentlyCarryingHalite;
            long bestMoveLong = NOT_A_MOVE;

            if(currentPathLength < QueueMaxSize) {
                halitehere  = haliteMap[standingOn.x][standingOn.y];
                short moveCost = MyBot.moveCostsSafe[halitehere + 1000];

                //This section sets the score for standing still
                //Can't stand still if we've already just undoed a standstill or if the tile is reserved by another ship (we'll make an exception if it's super early and we have no other options though)
                //Let's also not stand still if we can't even get 2 halite
                if( standingOn.equals(banTile) || currentPathLength > maxTurnStandStill || currentlyCarryingHalite > 998 || halitehere < 8 || standstillCounter > 5 ||  (Reserved[standingOn.x][standingOn.y][currentPathLength] && (currentPathLength >= 3 || moveCost < currentlyCarryingHalite)) ){
                    bestTile = null;
                    bestScore = -100000f;
                    collect = 0; //Compiler will complain otherwise, these shouldn't be hit
                }else {
                    collect = Math.min(1000-currentlyCarryingHalite,MyBot.standCollectSafe[halitehere + 1000]);
                    float inspireMult = Plan.inspireMultiplier[Math.min(currentPathLength,Plan.inspireMultiplier.length - 1)][standingOn.x][standingOn.y];
                    short hal = (short) Math.min(1000, currentlyCarryingHalite + collect * inspireMult);

                    float score, suggestionscore;

                    if(searchDropoffAlgo){
                        score = hal - (standingOn.turnsFromDropoff + currentPathLength) * worthTileMoveTermsOfHalite - countMeaningfulAltered * HandwavyWeights.FuturePathMeaningfulAlterationsFactorV2;
                        score -= MeaningfullyAltered[standingOn.x][standingOn.y] * HandwavyWeights.FuturePathMeaningfulAlterationsFactorV2;
                        if(  Math.min(1000 - hal, halitehere) > 80){
                            suggestionscore = score + halitehere * 0.2f - standingOn.turnsFromDropoff * 5f;
                        }else{
                            suggestionscore = score - halitehere * 0.01f - standingOn.turnsFromDropoff * 5f;
                        }
                    }else{
                        score =  hal - (standingOn.turnsFromTempGoal + currentPathLength) * HandwavyWeights.FuturePathTurnsDropoffNonFinishV2 - countMeaningfulAltered * HandwavyWeights.FuturePathMeaningfulAlterationsFactorNonFinishV2;
                        score -= MeaningfullyAltered[standingOn.x][standingOn.y] * HandwavyWeights.FuturePathMeaningfulAlterationsFactorNonFinishV2;
                        suggestionscore = score + halitehere * 0.15f;
                    }

                    long moveLong = Move.GetMoveLongformat(standingOn,standingOn,s.id,currentlyCarryingHalite);
                    if(score > bestResults[standingOn.x][standingOn.y] || (!haveAlreadyTried[(int)((moveLong * ( 1+ currentPathLength) + currentPathLength) % haveTriedLength)] &&  score > bestResults[standingOn.x][standingOn.y] - 20) ) {
                        bestSuggestionScore = suggestionscore;
                        bestScore = score;
                        bestTile = standingOn;
                        bestMoveLong = moveLong;
                        nextHalite = hal;
                    }

                }


                //This section checks the score for the possible moves
                if (moveCost <= currentlyCarryingHalite) {
                    short hal = (short) (currentlyCarryingHalite - moveCost);

                    for (Tile t : standingOn.neighbours) {
                        if(!t.equals(banTile) && !Reserved[t.x][t.y][currentPathLength]   && (t.turnsFromDropoff > 0 || currentlyCarryingHalite > HandwavyWeights.MinHaliteTurnIn)) {
                            float score, suggestionscore;
                            if(searchDropoffAlgo) {
                                score = hal - (t.turnsFromDropoff + currentPathLength) * worthTileMoveTermsOfHalite - countMeaningfulAltered * HandwavyWeights.FuturePathMeaningfulAlterationsFactorV2;
                                if(  Math.min(1000 - currentlyCarryingHalite, haliteMap[t.x][t.y]) > 80){
                                    suggestionscore = score + haliteMap[t.x][t.y] * 0.2f - standingOn.turnsFromDropoff * 5f;
                                }else{
                                    suggestionscore = score - haliteMap[t.x][t.y] * 0.01f - standingOn.turnsFromDropoff * 5f;
                                }
                            }else{
                                score =  hal - (standingOn.turnsFromTempGoal + currentPathLength) * HandwavyWeights.FuturePathTurnsDropoffNonFinishV2 - countMeaningfulAltered * HandwavyWeights.FuturePathMeaningfulAlterationsFactorNonFinishV2;
                                suggestionscore = score + haliteMap[t.x][t.y] * 0.2f;
                            }

                            //Don't care about meaningful alterations on tiles we're moving to. Only relevant for collection. Still have to track the total count though
                            long moveLong = Move.GetMoveLongformat(standingOn,t,s.id,currentlyCarryingHalite);

                            //if (score > bestScore && (score > bestResults[t.x][t.y][currentPathLength])){//  || (!haveAlreadyTried[(int)((moveLong * ( 1+ currentPathLength) + currentPathLength) % haveTriedLength)] &&  score > bestResults[standingOn.x][standingOn.y][currentPathLength] - 100))) {
                            if (suggestionscore > bestSuggestionScore && (score > bestResults[t.x][t.y] || (!haveAlreadyTried[(int)((moveLong * ( 1+ currentPathLength) + currentPathLength) % haveTriedLength)] &&  score > bestResults[standingOn.x][standingOn.y] - 20))) {
                                bestTile = t;
                                bestSuggestionScore = suggestionscore;
                                bestScore = score;
                                bestMoveLong = moveLong;
                                nextHalite = hal;
                            }
                        }
                    }
                }
            }else{
                //Compiler complains if these aren't set, they'll never be used though
                collect = 0;
                bestScore = 0f;
                halitehere = 0;
            }

            float assumedBestAchievableFromHere;
            if(bestTile != null) {
                //We'll assume the best we can manage is arriving at the goal with 990 halite, but that it'll take some turns to gather that halite (200 halite per turn max, with a min cost of 1 turn. A bit on the low end, but saves a lot of iterations)
                int halDifference = Math.max(0, 990 - nextHalite);

                if(currentPathLength >= maxTurnStandStill){
                    assumedBestAchievableFromHere = bestScore;
                }else {
                    assumedBestAchievableFromHere = bestScore + Math.max(0, halDifference - ((Math.min(150, halDifference) / 150)) * worthTileMoveTermsOfHalite);
                }
            }else{
                assumedBestAchievableFromHere = 0f;
            }

//            if(s.id ==0 && MyBot.turn > 0 && MyBot.turn < 50) {
//                Log.log("Assumed best: " + assumedBestAchievableFromHere + "  " + bestScore);
//            }

            if(bestTile != null && assumedBestAchievableFromHere > bestPathScore && iteration < maxIterations){
//                if(s.id ==0 && MyBot.turn > 0 && MyBot.turn < 50) {
//                    Log.log("Exploring: " + Move.MoveFromLong(bestMoveLong)  +  "  "  + bestScore + "  depth " + (currentPathLength + 1) + " curhalite: " + currentlyCarryingHalite + " next hal: " + nextHalite +  " storing halite: " + Move.GetShipHaliteFromLong(bestMoveLong), Log.LogType.MAIN);
//                }

                startHaliteOnPreviousTurn[currentPathLength] = halitehere;



                if(standingOn.equals(bestTile)){

                    haliteMap[bestTile.x][bestTile.y] -= collect;
                    standstillCounter++; //cheap hack to prevent a million standstills that will take thousands of iterations to resolve properly
                    countMeaningfulAltered += MeaningfullyAltered[bestTile.x][bestTile.y];

                }else{
                    standstillCounter = 0;
                }
                currentlyCarryingHalite = nextHalite;
                bestResults[bestTile.x][bestTile.y] = bestScore;
                haveAlreadyTried[(int)((bestMoveLong * ( 1+ currentPathLength) + currentPathLength) % haveTriedLength)] = true;
                currentPath[currentPathLength++] = bestMoveLong;
                standingOn = bestTile;

                if(searchDropoffAlgo){
                    if(bestTile.turnsFromDropoff == 0 && bestScore > bestPathScore){
                        boolean goodenough = false;

                        if(nextHalite > HandwavyWeights.FuturePathMinHaliteFinishV2 ||  currentPathLength > MyBot.turnsLeft - 2)
                        {
                            //New fastest path!
//                            Log.log("New best path " +  bestScore + " steps: " + currentPathLength + "  halite: " + nextHalite + " iteration: " + iteration, Log.LogType.MAIN);
                           int turnswasted = currentPathLength - s.GetTile().turnsFromDropoff;
                           if(turnswasted == 0){
                               goodenough = true;
                           }else{
                               float halitePerTurn =   ((float)(currentlyCarryingHalite - s.halite)) / turnswasted;
                               if(halitePerTurn > HandwavyWeights.FuturePathMinHalitePerTurnV2){
                                   goodenough = true;
                               }
                           }
                        }
                        if(goodenough){
                            bestPathScore = bestScore;
                            bestPath = currentPath.clone();

                        }else{
                            if(bestPath == null){
                                bestScore *= 0.7;
                                //This path itself is not good enough. But if we can't find a path above the minimum that
                                //beats this in score, it's probably going to be terrible too, so let's ignore those to save a lot of processing power.

                                if (bestScore > bestPathScore) {
                                    bestPathScore = bestScore;
                                }
                            }
                        }
                    }
                    else if(bestPath == null) {
                        bestScore -= bestTile.turnsFromDropoff * worthTileMoveTermsOfHalite;
                        bestScore *= 0.7;
                        if (bestScore > bestPathScore) {
                            bestPathScore = bestScore;
//                            Log.log("New end-missing path " +  bestScore + " steps: " + currentPathLength + "  halite: " + nextHalite + " iteration: " + iteration, Log.LogType.MAIN);

                        }
                    }

                }else{
                    if(bestScore > bestPathScore){
//                        Log.log(s.id + ": New fast halite grab path in " + currentPathLength + " steps, with " + nextHalite + " halite  meaningful: " +  countMeaningfulAltered  +  "   (iteration " + iteration +")", Log.LogType.MAIN);
                        bestPathScore = bestScore;
                        bestPath = currentPath.clone();
                    }

                }
            }else{
                //No move improvement found, undo the last move and try searching along a different path
                if(--currentPathLength < 0){
//                    Log.log(s.id + " exiting because no viable paths left ", Log.LogType.MAIN);
                    break;
                } else {
//                    if(s.id ==0 && MyBot.turn > 0 && MyBot.turn < 10) {
//                        Log.log("Erasing last: " + Move.MoveFromLong(currentPath[currentPathLength])   +   "  new depth: " + currentPathLength  + "  max achievable: " + assumedBestAchievableFromHere  + " halite: " + currentlyCarryingHalite + " moveCost " + MyBot.moveCostsSafe[halitehere + 1000], Log.LogType.MAIN);
//                    }

                    banTile = standingOn;
                    standingOn = Move.GetFromTileFromLong(currentPath[currentPathLength]);// lastMove.from;
                    currentlyCarryingHalite =  Move.GetShipHaliteFromLong(currentPath[currentPathLength]);//lastMove.ship.halite;
                    if(Move.IsLongStandstill(currentPath[currentPathLength])){
                        countMeaningfulAltered -= MeaningfullyAltered[standingOn.x][standingOn.y];

                        haliteMap[standingOn.x][standingOn.y] =  startHaliteOnPreviousTurn[currentPathLength];//     startHaliteOnPreviousTurn.removeLast();
                    }

                    currentPath[currentPathLength] = NOT_A_MOVE;

                }

            }
        }

//        if(searchDropoffAlgo) {
//            Log.log(s.id + " Finished dropoff algo loop " + iteration, Log.LogType.MAIN);
//        }else{
//            Log.log(s.id + " Finished high halite algo loop " + iteration, Log.LogType.MAIN);
//        }

        if(bestPath != null && bestPath.length > 0 && bestPath[0] != NOT_A_MOVE) {

            Move[] finalMoves = new Move[QueueMaxSize];

            for(int turn =0; turn < bestPath.length; turn++){
                if(bestPath[turn] != NOT_A_MOVE) {
                    Move m = Move.MoveFromLong(bestPath[turn]);
                    finalMoves[turn] = m;

                    Reserved[m.to.x][m.to.y][turn] = true;
                    if (m.isStandStill()) {//  && Map.staticHaliteMap[m.from.x][m.from.y] > HandwavyWeights.ThresholdMeaningfulAlterationV2){
                        MeaningfullyAltered[m.to.x][m.to.y]+=  Math.max(10f,HandwavyWeights.FuturePathBaseDistMeaningfulV2 - turn) / HandwavyWeights.FuturePathBaseDistMeaningfulV2;
                    }
                }
            }

//            String str = "Best path for " + s.id  + "  ("  + iteration  +") iterations :    ";
//            for(Move m : finalMoves){
//                if(m != null) {
//                    str += m;
//                }
//            }
//            Log.log(str, Log.LogType.MAIN);

            return finalMoves;
        } else if(searchDropoffAlgo && ALLOW_NON_FINISHER && s.halite < 950){
//            Log.log(s.id + " is resorting to non-finishing path ", Log.LogType.MAIN);
            return FuturePathing(s,previouspath,false);
           // return null;
        }else{
//            Log.log("Failed to find any path  ("  + iteration  + " ) iterations", Log.LogType.MAIN);
            return null;
        }


//        StringBuilder sb = new StringBuilder();
//
//        for(int y = 0; y < Map.height; y++){
//            for(int x = 0; x < Map.width; x++){
//                sb.append(String.format("%4f",bestResults[x][y]));
//            }
//            sb.append("\r\n");
//
//        }
//        Log.log(sb.toString(), Log.LogType.MAIN);



//        return bestPath;
    }


}
