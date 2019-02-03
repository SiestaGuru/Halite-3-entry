import hlt.Constants;
import hlt.Game;

import java.util.ArrayList;


/*
This class contains various methods that deal with predicting enemy behavior

It makes both a soft, odds based prediction, as well as a most likely move

Predictions are done by learning from the enemies past behavior.
It does this by executing a bunch of simple algorithms, each of which can do a prediction.
It also checks what situation enemy ships are in (out of a few simple options)
On every turn, the system tracks how accurate these algorithms were at predicting the behavior of the ships of some player in some situation.

The predictions per algorithm are weighted by how accurate they are and blended together to form a final prediction

Results: These predictions end up decently accurate, certainly much more so than not doing anything, but I've not experimented
much with other approaches.
 */

public class EnemyPrediction {

    public static final int SituationCount = 21;
    public static final int AlgorithmCount = 19;

    public static final int SITUATION_fullShipDefault = 0;
    public static final int SITUATION_fullShipNextToOpponent = 1;
    public static final int SITUATION_emptyShipDefault = 2;
    public static final int SITUATION_emptyShipBigTile = 3;
    public static final int SITUATION_emptyShipNextToOpponent = 4;
    public static final int SITUATION_emptyShipNearOpponent = 5;
    public static final int SITUATION_emptyShipNothingAround = 6;
    public static final int SITUATION_normalShipDefault = 7;
    public static final int SITUATION_normalShipBigTile = 8;
    public static final int SITUATION_normalShipNextToOpponent = 9;
    public static final int SITUATION_normalShipNearOpponent = 10;
    public static final int SITUATION_normalShipNothingAround = 11;
    public static final int SITUATION_fullShipNearOpponent = 12;
    public static final int SITUATION_emptyShipNothingHere = 13;
    public static final int SITUATION_normalShipNothingHere = 14;
    public static final int SITUATION_nonFullNextToMassive = 15;
    public static final int SITUATION_nonFullNextToOccupiedMassive = 16;
    public static final int SITUATION_standingOnMassiveFull = 17;
    public static final int SITUATION_standingOnMassiveNonFull = 18;
    public static final int SITUATION_emptyShipNextToOpponentTheirControl = 19;
    public static final int SITUATION_normalShipNextToOpponentTheirControl = 20;


    public static final int ALGORITHM_BASIC_PEACEFUL = 0;
    public static final int ALGORITHM_STAND_STILL = 1;

    public static final int ALGORITHM_RETURN_HOME = 2;
    public static final int ALGORITHM_ALWAYS_KILL_NEARBY = 3;
    public static final int ALGORITHM_CHASE_NEARBY = 4;
    public static final int ALGORITHM_MOVE_TO_HIGHEST_NEIGHBOUR = 5;
    public static final int ALGORITHM_MOVE_TO_LOWEST_NEIGHBOUR = 6;
    public static final int ALGORITHM_MOVE_ACCORDING_TO_MEANINGFUL = 7;
    public static final int ALGORITHM_SIMPLE_BURN_OPTIMIZATION = 8;
    public static final int ALGORITHM_MOVE_TO_HIGHEST_NEIGHBOUR_SLIGHLTY_SMARTER = 9;
    public static final int ALGORITHM_MOVE_ACCORDING_TO_MEDIUM_LURE = 10;
    public static final int ALGORITHM_RUN_FROM_HOME = 11;
    public static final int ALGORITHM_KILL_MOREHALITE = 12;
    public static final int ALGORITHM_REPEATER = 13;
    public static final int ALGORITHM_BASIC_NEVER_MOVE_TO_SHIP = 14;
    public static final int ALGORITHM_MOVEMENT_DIRECTION = 15;
    public static final int ALGORITHM_ANOTHER_MINER = 16;
    public static final int ALGORITHM_ANOTHER_MINER_2 = 17;
    public static final int ALGORITHM_ANOTHER_MINER_3 = 18;




    public static ArrayList<Move>[][][] lastTurn0Predictions = new ArrayList[4][SituationCount][AlgorithmCount];
    public static float[][][] predictions = new float[4][SituationCount][AlgorithmCount]; //really an int, just making it float to save on conversions
    public static float[][][] succesfulPredictions = new float[4][SituationCount][AlgorithmCount]; //really an int, just making it float to save on conversions

    public static int[] stoodStillCounter = new int[5000];


    static {


        //Set some decentish defaults for rarer situations, so we don't have to train from nothing every single time. Just based on some self games
        if(MyBot.playerCount == 2) {
            int player = MyBot.enemy1.id;

            succesfulPredictions[player][SITUATION_emptyShipNextToOpponent][ALGORITHM_ALWAYS_KILL_NEARBY] = 9f;
            succesfulPredictions[player][SITUATION_emptyShipNextToOpponent][ALGORITHM_KILL_MOREHALITE] = 10f;
            succesfulPredictions[player][SITUATION_emptyShipNextToOpponent][ALGORITHM_CHASE_NEARBY] = 7f;
            succesfulPredictions[player][SITUATION_emptyShipNextToOpponent][ALGORITHM_BASIC_PEACEFUL] = 2f;

            succesfulPredictions[player][SITUATION_emptyShipNextToOpponentTheirControl][ALGORITHM_ALWAYS_KILL_NEARBY] = 15f;
            succesfulPredictions[player][SITUATION_emptyShipNextToOpponentTheirControl][ALGORITHM_KILL_MOREHALITE] = 10f;
            succesfulPredictions[player][SITUATION_emptyShipNextToOpponentTheirControl][ALGORITHM_BASIC_PEACEFUL] = 2f;

            succesfulPredictions[player][SITUATION_emptyShipNearOpponent][ALGORITHM_CHASE_NEARBY] = 7f;
            succesfulPredictions[player][SITUATION_emptyShipNearOpponent][ALGORITHM_BASIC_PEACEFUL] = 3f;


            succesfulPredictions[player][SITUATION_normalShipNextToOpponent][ALGORITHM_ALWAYS_KILL_NEARBY] = 6f;
            succesfulPredictions[player][SITUATION_normalShipNextToOpponent][ALGORITHM_KILL_MOREHALITE] = 8f;
            succesfulPredictions[player][SITUATION_normalShipNextToOpponent][ALGORITHM_CHASE_NEARBY] = 5f;
            succesfulPredictions[player][SITUATION_normalShipNextToOpponent][ALGORITHM_BASIC_PEACEFUL] = 5f;

            succesfulPredictions[player][SITUATION_normalShipNextToOpponentTheirControl][ALGORITHM_ALWAYS_KILL_NEARBY] = 10f;
            succesfulPredictions[player][SITUATION_normalShipNextToOpponentTheirControl][ALGORITHM_KILL_MOREHALITE] = 10f;
            succesfulPredictions[player][SITUATION_normalShipNextToOpponentTheirControl][ALGORITHM_BASIC_PEACEFUL] = 3f;

            succesfulPredictions[player][SITUATION_normalShipNearOpponent][ALGORITHM_CHASE_NEARBY] = 5f;
            succesfulPredictions[player][SITUATION_normalShipNearOpponent][ALGORITHM_BASIC_PEACEFUL] = 6f;



            succesfulPredictions[player][SITUATION_fullShipNextToOpponent][ALGORITHM_KILL_MOREHALITE] = 3f;
            succesfulPredictions[player][SITUATION_fullShipNearOpponent][ALGORITHM_RETURN_HOME] = 7f;
            succesfulPredictions[player][SITUATION_fullShipNearOpponent][ALGORITHM_BASIC_PEACEFUL] = 5f;



        }else{
            for (int player = 0; player < MyBot.playerCount; player++) {
                succesfulPredictions[player][SITUATION_emptyShipNextToOpponent][ALGORITHM_ALWAYS_KILL_NEARBY] = 1f;
                succesfulPredictions[player][SITUATION_emptyShipNextToOpponent][ALGORITHM_KILL_MOREHALITE] = 3f;
                succesfulPredictions[player][SITUATION_emptyShipNextToOpponent][ALGORITHM_CHASE_NEARBY] = 2f;
                succesfulPredictions[player][SITUATION_emptyShipNextToOpponent][ALGORITHM_BASIC_PEACEFUL] = 7f;

                succesfulPredictions[player][SITUATION_emptyShipNextToOpponentTheirControl][ALGORITHM_ALWAYS_KILL_NEARBY] = 10f;
                succesfulPredictions[player][SITUATION_emptyShipNextToOpponentTheirControl][ALGORITHM_KILL_MOREHALITE] = 10f;
                succesfulPredictions[player][SITUATION_emptyShipNextToOpponentTheirControl][ALGORITHM_BASIC_PEACEFUL] = 5f;

                succesfulPredictions[player][SITUATION_emptyShipNearOpponent][ALGORITHM_CHASE_NEARBY] = 4f;
                succesfulPredictions[player][SITUATION_emptyShipNearOpponent][ALGORITHM_BASIC_PEACEFUL] = 6f;

                succesfulPredictions[player][SITUATION_normalShipNextToOpponent][ALGORITHM_KILL_MOREHALITE] = 3f;
                succesfulPredictions[player][SITUATION_normalShipNextToOpponent][ALGORITHM_BASIC_PEACEFUL] = 5f;

                succesfulPredictions[player][SITUATION_normalShipNextToOpponentTheirControl][ALGORITHM_ALWAYS_KILL_NEARBY] = 5f;
                succesfulPredictions[player][SITUATION_normalShipNextToOpponentTheirControl][ALGORITHM_KILL_MOREHALITE] = 5f;
                succesfulPredictions[player][SITUATION_normalShipNextToOpponentTheirControl][ALGORITHM_BASIC_PEACEFUL] = 5f;

                succesfulPredictions[player][SITUATION_normalShipNearOpponent][ALGORITHM_CHASE_NEARBY] = 5f;
                succesfulPredictions[player][SITUATION_normalShipNearOpponent][ALGORITHM_BASIC_PEACEFUL] = 6f;


                succesfulPredictions[player][SITUATION_fullShipNextToOpponent][ALGORITHM_KILL_MOREHALITE] = 2f;
                succesfulPredictions[player][SITUATION_fullShipNearOpponent][ALGORITHM_RETURN_HOME] = 8f;
                succesfulPredictions[player][SITUATION_fullShipNearOpponent][ALGORITHM_BASIC_PEACEFUL] = 5f;



            }
        }

        for(int i = 0; i < SituationCount; i++){
            for(int j = 0; j < AlgorithmCount; j++){
                for(int k = 0; k < 4; k++) {
                    lastTurn0Predictions[k][i][j] = new ArrayList<>();
                    if(succesfulPredictions[k][i][j] > 0f) {
                        predictions[k][i][j] = 10f;
                    }
                    else{
                        predictions[k][i][j] = 1f;
                    }
                }
            }
        }


    }
    public static boolean[][] DontEvenBother = new boolean[SituationCount][AlgorithmCount]; //TODO: do something like this. Dont have to kill for kill simulations when theres no one nearby, etc.


    public static boolean[] playerHasDeliberatelyKilled = new boolean[4];

    public static int[][] MostReliableAlgoForSituation = new int[4][SituationCount];


    public static Tile[] wasHere5TurnsAgo = new Tile[5000];

    public static Plan DoPredictions(int depth, int simEnemyturns){


        if(!Plan.DoEnemySimulation) {
            return new Plan(Map.currentMap, true);
        }
        int amountAdded = 0;

        simEnemyturns = Math.min(depth,simEnemyturns);

        for(CheapShip s : Map.allShipsOnTurn[Math.max(0,MyBot.turn - 5)]){
            wasHere5TurnsAgo[s.id] = s.GetTile();
        }


        Plan bestPlan;
        if(HandwavyWeights.DO_MY_SIM_ONCE_BEFORE_SIM == 1){
            //First do our moves once, helps determine enemy moves.
            bestPlan = Plan.GreedySearch(simEnemyturns,0,null,0,false);
        }else{
            bestPlan = new Plan(Map.currentMap,true);
        }


        int turnstoReachMax = 6;
        if(Plan.timeErrors > 40 || Map.relevantEnemyShipCount > 90){
            turnstoReachMax = 5;
            simEnemyturns = Math.max(1,simEnemyturns - 1);
        }


        for(CheapShip s : Map.staticEnemyShips){
            if( s.equals(Map.staticShipsMapLastTurn[s.x][s.y])){
                stoodStillCounter[s.id]++;
            }else{
                stoodStillCounter[s.id] = 0;
            }
        }

        mainloop:
        for(int turn = 0; turn < simEnemyturns; turn++){

            bestPlan.SetWipMap(bestPlan.CloneAndSim(0,turn,false,Map.currentMap,true,-1));
            boolean[][] occupied = new boolean[Map.width][Map.height];

            for(int shipIndex =0; shipIndex < Map.relevantEnemyShipCount; shipIndex++) {
                CheapShip s = bestPlan.WIPMAp.enemyShipsRelevant[shipIndex];
                if (s != null && s.halite < MyBot.moveCostsSafe[bestPlan.WIPMAp.GetHaliteAt(s)]) {
                    occupied[s.x][s.y] = true;
                }
            }

            for(int shipIndex =0; shipIndex < Map.relevantEnemyShipCount; shipIndex++){
                CheapShip s = bestPlan.WIPMAp.enemyShipsRelevant[shipIndex];
                if(s != null) {
                    boolean closeEnough = Plan.turnsToReachByShip[s.x][s.y] < turnstoReachMax; //for performance reasons, don't want all simulations to include 5 million ships which don't matter anyway
                    if((turn == 0  && Map.relevantEnemyShipCount < 40) || closeEnough) {
                        int playerid = Map.OwnerOfShip[s.id];
                        ArrayList<Move> possibilities = bestPlan.findAllMovesForEnemyShip(s,bestPlan.WIPMAp);
                        int situation = GetSituation(s, bestPlan.WIPMAp);

                        Move predictedMove = null;

                        if (possibilities.size() == 1) {
                            //Dont want to engage the prediction system when it can only do one thing. Makes predictions a bit easy
                            predictedMove = possibilities.get(0);
                        }
                        else if(stoodStillCounter[s.id] > 6 && s.GetTile().haliteStartTurn < 20){
                            //They're very likely waiting for something to happen
                            predictedMove = new Move(s.GetTile(),s.GetTile(),s);
                        }
                        else if(MyBot.turnsLeft < 25){
                            int algo = ALGORITHM_RETURN_HOME;
                            float bestscore = -10000000000f;
                            for (Move p : possibilities) {
                                if(!occupied[p.to.x][p.to.y]) {
                                    float score = EvaluateMoveUsingAlgo(p, bestPlan.WIPMAp, algo);
                                    if (score > bestscore) {
                                        bestscore = score;
                                        predictedMove = p;
                                    }
                                }
                            }
                        }
//                        else if(Plan.timeErrors > 150 || Map.relevantEnemyShipCount > 150) {
//                            //Let's not spend too much time on this
//                            int algo = MostReliableAlgoForSituation[playerid][situation];
//                            float bestscore = -10000000000f;
//                            for (Move p : possibilities) {
//                                if(!occupied[p.to.x][p.to.y]) {
//                                    float score = EvaluateMoveUsingAlgo(p, bestPlan.WIPMAp, algo);
//                                    if (score > bestscore) {
//                                        bestscore = score;
//                                        predictedMove = p;
//                                    }
//                                }
//                            }
//
//                        }
                        else if (HandwavyWeights.ActivateComplexPrediction == 1) {
                            Move[] movePredictions = new Move[AlgorithmCount];
                            for (int algo = 0; algo < AlgorithmCount; algo++) {
                                float bestscore = -10000000000f;
                                for (Move p : possibilities) {
                                    if(!occupied[p.to.x][p.to.y]) {
                                        float score = EvaluateMoveUsingAlgo(p, bestPlan.WIPMAp, algo);
                                        if (score > bestscore) {
                                            bestscore = score;
                                            movePredictions[algo] = p;
                                        }
                                    }
                                }
                                if (turn == 0) {
                                    lastTurn0Predictions[playerid][situation][algo].add(movePredictions[algo]);
                                }
                            }

                            double bestMoveScore = -100000000000.0;
                            predictedMove = movePredictions[MostReliableAlgoForSituation[playerid][situation]];

                            for (Move p : possibilities) {
                                double score = 0;

                                for (int algo = 0; algo < AlgorithmCount; algo++) {
                                    if(predictions[playerid][situation][algo] > HandwavyWeights.MIN_RESULTS_BEFORE_PREDICTION) {
                                        if (p.equals(movePredictions[algo])) {

                                            double accuracy = succesfulPredictions[playerid][situation][algo] / predictions[playerid][situation][algo];
                                            if (accuracy > HandwavyWeights.MIN_ACCURACY) {
                                                score += HandwavyWeights.FLAT_CONTRIBUTION_PREDICTION + accuracy * 1.0;
                                            }
                                            if (MostReliableAlgoForSituation[playerid][situation] == algo) {
                                                score += HandwavyWeights.BONUS_MOST_RELIABLE;
                                            }
                                        }
                                    }
                                }
                                if (score > bestMoveScore) {
                                    bestMoveScore = score;
                                    predictedMove = p;
                                }
                            }

                        } else {
                            if (turn == 0) {
                                Move[] movePredictions = new Move[AlgorithmCount];
                                for (int algo = 0; algo < AlgorithmCount; algo++) {
                                    float bestscore = -10000000000f;
                                    for (Move p : possibilities) {
                                        if(!occupied[p.to.x][p.to.y]) {
                                            float score = EvaluateMoveUsingAlgo(p, bestPlan.WIPMAp, algo);
                                            if (score > bestscore) {
                                                bestscore = score;
                                                movePredictions[algo] = p;
                                            }
                                        }
                                    }
                                    lastTurn0Predictions[playerid][situation][algo].add(movePredictions[algo]);
                                }
                                predictedMove = movePredictions[MostReliableAlgoForSituation[playerid][situation]];
                            } else {
                                int algo = MostReliableAlgoForSituation[playerid][situation];
                                float bestscore = -10000000000f;
                                for (Move p : possibilities) {
                                    if(!occupied[p.to.x][p.to.y]) {
                                        float score = EvaluateMoveUsingAlgo(p, bestPlan.WIPMAp, algo);
                                        if (score > bestscore) {
                                            bestscore = score;
                                            predictedMove = p;
                                        }
                                    }
                                }
                            }
                        }

                        if(closeEnough) {
                            if(predictedMove == null){
                                predictedMove = new Move(s.GetTile(),s.GetTile(),s);
                            }

                            amountAdded++;
                            bestPlan.enemyMovesPerTurn[turn][Map.enemyRelevantIndexOfIds[predictedMove.ship.id]] = predictedMove;
                            bestPlan.SetWipMap(bestPlan.CloneAndSim(0,turn + 1, false, Map.currentMap,true,-1));
                            occupied[predictedMove.to.x][predictedMove.to.y] = true;

                            if(amountAdded > 60){
                                break mainloop;
                            }
                        }
                    }

                }
            }

            if(MyBot.DO_GAME_OUTPUT && turn == 0){
                bestPlan.SetWipMap( bestPlan.CloneAndSim(0,turn + 1, true, Map.currentMap,true,-1));
                boolean[][] map = new boolean[Map.width][Map.height];

                for(int i = 0 ; i < bestPlan.enemyMovesPerTurn[0].length; i++){
                    if(bestPlan.enemyMovesPerTurn[turn][i] != null){
                        map[bestPlan.enemyMovesPerTurn[turn][i].to.x][bestPlan.enemyMovesPerTurn[turn][i].to.y] = true;
                    }
                }

                StringBuilder s = new StringBuilder();
                s.append("eprediction:");
                for(int y =0; y < Map.height; y++){
                    for(int x=0; x < Map.width; x++){
                        if(map[x][y]) {
                            s.append("1,");
                        }else{
                            s.append("0,");
                        }
                    }
                    s.append(";");
                }
                GameOutput.info.add(s.toString());
            }

            if(MyBot.ALLOW_LOGGING && Log.LogType.PREDICTION.active){
//                Log.log("Turn " + turn + " predictions: " , Log.LogType.PREDICTION);
                StringBuilder s = new StringBuilder();
                for(int i = 0 ; i < bestPlan.enemyMovesPerTurn[turn].length; i++){
                    if(bestPlan.enemyMovesPerTurn[turn][i] != null){
                        s.append(bestPlan.enemyMovesPerTurn[turn][i] + " ");
                    }
                }
//                Log.log(s.toString(), Log.LogType.PREDICTION);

            }

            //Now do our ships moves in this plan
            bestPlan.StripMyMoves();
            bestPlan = Plan.GreedySearch(simEnemyturns,0,bestPlan,turn,false);
        }

//        Log.log("amount of e moves: " + amountAdded, Log.LogType.MAIN);



        return bestPlan.ClearCopy();
    }


    public static float[][] LastTurnEOdds = null;
    public static float EoddsTotalPenalty;
    public static int EoddsTotalAttempts;


    public static float[][] GetT0EnemyOdds(){
        float[][] eOdds = new float[Map.width][Map.height];

        ArrayList<CheapShip> shipCollection;
        if(MyBot.CHECK_EODDS_ACCURACY){
            shipCollection = Map.staticEnemyShips;
        }else{
            shipCollection = Map.staticRelevantEnemyShips;
        }

        for(CheapShip s : shipCollection){
            if(s != null){
                Tile start = s.GetTile();

                if(MyBot.moveCosts[start.haliteStartTurn] > s.halite || (stoodStillCounter[s.id] > 6 && s.GetTile().haliteStartTurn < 20)  ||  s.GetTile().turnsFromDropoff <= 1){
                    eOdds[s.x][s.y] = 1f;
                }
                else{
                    int playerid = Map.OwnerOfShip[s.id];
                    int situation = GetSituation(s, Map.currentMap);

                    for (Tile t : start.neighboursAndSelf) {
                        t.likelyhood = 0.5f; //bit of a default val
                    }

                    for (int algo = 0; algo < AlgorithmCount; algo++) {
                        float accuracy = succesfulPredictions[playerid][situation][algo] / predictions[playerid][situation][algo];

                        if(accuracy > 0.2f) {
                            float totalScore = 0;


                            for (Tile t : start.neighboursAndSelf) {
                                t.desirability = Math.max(0f,EvaluateMoveUsingAlgo(new Move(start, t, s), Map.currentMap, algo));
                                totalScore += t.desirability;
                            }

                            if (totalScore != 0) {
                                for (Tile t : start.neighboursAndSelf) {
                                    t.likelyhood += (t.desirability / totalScore) * accuracy;
                                }
                            }
                        }
                    }

                    float totalPredictionSum = 0f;

                    for (Tile t : start.neighboursAndSelf) {
                        totalPredictionSum +=  t.likelyhood;
                    }

                    if(totalPredictionSum != 0) {
                        for (Tile t : start.neighboursAndSelf) {
                            eOdds[t.x][t.y] += (1f - eOdds[t.x][t.y]) *  Math.max(HandwavyWeights.MinOdds,(t.likelyhood / totalPredictionSum));
                        }
                    }else{
                        for (Tile t : start.neighboursAndSelf) {
                            eOdds[t.x][t.y] +=  (1f - eOdds[t.x][t.y]) * 0.2f;
                        }
                    }
                }
            }
        }


        if(MyBot.CHECK_EODDS_ACCURACY){

            if(LastTurnEOdds != null){

                for(int x=0; x < Map.width; x++){
                    for(int y=0; y < Map.height; y++) {
                        CheapShip s = Map.staticShipsMap[x][y];
                        float prediction = LastTurnEOdds[x][y];
                        if(s != null && !Map.DoIOwnShip[s.id]){
                            if(prediction == 0){
                                EoddsTotalPenalty += 8f; //Big penalty when we didn't even see a move coming
                            }
                            else{
                                EoddsTotalPenalty +=  Math.max(0f,1f - prediction) * 5f;
                            }
                            EoddsTotalAttempts++;
                        }else{
                            EoddsTotalPenalty += prediction;
                            if(prediction > 0){
                                EoddsTotalAttempts++;
                            }
                        }
                    }
                }
//                Log.log("EOddsAccuracy: " +  (EoddsTotalPenalty / EoddsTotalAttempts), Log.LogType.MAIN );



            }

            LastTurnEOdds = eOdds;
        }



        return eOdds;
    }

    public static float[][] GetT0EnemyOddsOldFashioned(){
        float[][] eOdds = new float[Map.width][Map.height];

        CheapShip[][] shiparray = new CheapShip[Map.width][Map.height];

        for(CheapShip s : Map.staticMyShips){
            shiparray[s.x][s.y] = s;
        }
        for(CheapShip s : Map.staticEnemyShips){
            shiparray[s.x][s.y] = s;
        }

//        Log.log("Time: " + System.currentTimeMillis(), Log.LogType.MAIN);

        for(CheapShip s : Map.staticEnemyShips) {
            //Let's be serious about enemy ship locs on the first turn. We can estimate it pretty well

            int pid = Game.ships.get(s.id).owner.id;

            if (Map.staticHaliteMap[s.x][s.y]  > s.halite * 10) {
                eOdds[s.x][s.y] = 1f;
            } else {
                int collect = (int) ((((float) Map.staticHaliteMap[s.x][s.y]) / ((float) Constants.EXTRACT_RATIO)) + 0.5);

                int inspireCount = 0;
                for(Tile t : s.GetTile().tilesInWalkDistance[4]){
                    if(shiparray[t.x][t.y] != null && Game.ships.get(shiparray[t.x][t.y].id).owner.id != pid ) {
                        inspireCount++;
                    }
                }
                if (inspireCount >= 2) {
                    collect *= 2f;
                }
                collect = Math.min(Constants.MAX_HALITE - s.halite, collect);
                float standstillOdds = Math.min(HandwavyWeights.MaxChanceStandstill, ((float) collect) / HandwavyWeights.EstGuaranteedCollect);
                //works a bit weird with two units close to each other, but oh well
                eOdds[s.x][s.y] += standstillOdds;
                //TODO: determine odds of next tile by amount of halite present
                for (Tile t : Map.tiles[s.x][s.y].GetNeighbours()) {
                    float wantToKill = 0f;
                    if(MyBot.playerCount == 2 || MyBot.turnsLeft < 100) {
                        if (shiparray[t.x][t.y] != null && Map.OwnerOfShip[shiparray[t.x][t.y].id] != pid) {
                            wantToKill = 0.3f;
                            wantToKill += Math.max(0,(shiparray[t.x][t.y].halite - s.halite) / 1000f);
                            wantToKill += Util.Clamp( s.GetTile().enemyShipsStartInRange4Avg - s.GetTile().myShipsStartInRange5Avg,0,0.3f); //give extra weight if they're surrounded by friends
                            wantToKill = Util.Clamp(wantToKill,0,0.7f);
                        }
                    }
                    eOdds[t.x][t.y] += Math.min(0.8f, Math.max(0.1f,(1f -standstillOdds)) / 4f + wantToKill); //yeah, the sum of the odds can exceed 100%, so what? what are ye gonna do, call the cops?

                }

            }
        }

        return eOdds;
    }


    final static float[][][] GetEnemyLocOdds(int searchdepth){
//        Log.log("Time: " + System.currentTimeMillis(), Log.LogType.MAIN);


        float[][][] locodds = new float[searchdepth][Map.width][Map.height];

        if(Plan.DoEnemySimulation){
            UpdateSuccessLastTurnPredictions();
            locodds[0] = GetT0EnemyOdds();
        }else {
            locodds[0] = GetT0EnemyOddsOldFashioned();
        }

//        Log.log("Time2: " + System.currentTimeMillis(), Log.LogType.MAIN);

        float spreadRemainder = 1.0f - 4 * HandwavyWeights.EstShipmoveRate;

        //TODO: performance, maybe indicate relevant tiles, so we dont have to go through all tiles here?
        for(int i = 1; i < searchdepth; i++){
            for(int x= 0;x < Map.width; x++) {
                for (int y = 0; y < Map.height; y++) {
                    locodds[i][x][y] += locodds[i-1][x][y] * spreadRemainder;
                    for(Tile t : Map.tiles[x][y].GetNeighbours()){
                        locodds[i][x][y] += locodds[i-1][t.x][t.y] * HandwavyWeights.EstShipmoveRate;
                    }
                }
            }
        }
//


        if(MyBot.DO_GAME_OUTPUT){

            StringBuilder s = new StringBuilder();

            s.append("elocodds:");
            for(int y =0; y < Map.height; y++){
                for(int x=0; x < Map.width; x++){
                    s.append( locodds[0][x][y] + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());
        }



//        String test = "E-locmap:  \r\n\r\n";
////        for(int turn = 0; turn < searchdepth; turn++) {
//            for (int y = 0; y < height; y++) {
//                for (int x = 0; x < width; x++) {
//                    test += " " + String.format("%.3f", locodds[1][x][y]);
//                }
//                test += "\r\n";
//            }
//            test += "\r\n";
//            test += "\r\n";
////        }
//        Log.log(test, Log.LogType.TEMP);



        return locodds;
    }

    public static int GetSituation(CheapShip s, Map map){
        boolean enemyAdjacent = false;
        boolean enemyNear = false;
        Tile tile = map.GetTile(s);


        if(tile.haliteStartTurn > 800){
            if(s.halite > 990){
                return SITUATION_standingOnMassiveFull;
            }else{
                return SITUATION_standingOnMassiveNonFull;
            }
        }

        for(Tile t  : tile.neighbours){
            if(t.haliteStartTurn > 800 && s.halite < 950){

                if(map.GetShipAt(t) != null && Map.OwnerOfShip[s.id] != Map.OwnerOfShip[map.GetShipAt(t).id]){
                    return SITUATION_nonFullNextToOccupiedMassive;
                }
                else{
                    return SITUATION_nonFullNextToMassive;
                }

            }
        }

        for(Tile t : tile.GetNeighbours()){
            CheapShip neighbour = map.GetShipAt(t);
            if(neighbour != null && Map.OwnerOfShip[s.id] != Map.OwnerOfShip[neighbour.id]){
                enemyAdjacent = true;
                break;
            }
        }
        for(Tile t : tile.tilesInWalkDistance[2]){
            CheapShip neighbour = map.GetShipAt(t);
            if(neighbour != null && Map.OwnerOfShip[s.id] != Map.OwnerOfShip[neighbour.id]){
                enemyNear = true;
                break;
            }
        }

        boolean enemyControl = tile.control < 2f;

        if(s.halite > 910){
            if(enemyAdjacent){
                return SITUATION_fullShipNextToOpponent;
            }else if(enemyNear){
                return SITUATION_fullShipNearOpponent;
            } else{
                return SITUATION_fullShipDefault;
            }

        } else if(s.halite < 100){
            if(enemyAdjacent){
                if(enemyControl){
                    return SITUATION_emptyShipNextToOpponentTheirControl;
                }else {
                    return SITUATION_emptyShipNextToOpponent;
                }
            }else if(enemyNear){
                return SITUATION_emptyShipNearOpponent;
            } else if(map.GetHaliteAt(tile) > 300) {
                return SITUATION_emptyShipBigTile;
            } else if(map.GetHaliteAt(tile) < 50){

                int halitesum = 0;
                for(Tile t : tile.GetNeighboursAndSelf()){
                    halitesum += map.GetHaliteAt(t);
                }
                if(halitesum < 150){
                    return SITUATION_emptyShipNothingAround;
                }else{
                    return SITUATION_emptyShipNothingHere;
                }
            }
            else{
                return SITUATION_emptyShipDefault;
            }
        } else{
            if(enemyAdjacent){
                if(enemyControl){
                    return SITUATION_normalShipNextToOpponentTheirControl;
                }else {
                    return SITUATION_normalShipNextToOpponent;
                }
            }else if(enemyNear){
                return SITUATION_normalShipNearOpponent;
            } else if(map.GetHaliteAt(tile) > 300) {
                return SITUATION_normalShipBigTile;
            } else if(map.GetHaliteAt(tile) < 50){
                int halitesum = 0;
                for(Tile t : tile.GetNeighboursAndSelf()){
                    halitesum += map.GetHaliteAt(t);
                }
                if(halitesum < 150){
                    return SITUATION_normalShipNothingAround;
                }else{
                    return SITUATION_normalShipNothingHere;
                }
            } else{
                return SITUATION_normalShipDefault;
            }
        }

    }

    public static void UpdateSuccessLastTurnPredictions(){

        for (int playerid = 0; playerid < MyBot.playerCount; playerid++) {
            if(playerid == MyBot.myId) continue;

            for (int situation = 0; situation < SituationCount; situation++) {
                for (int algo = 0; algo < AlgorithmCount; algo++) {
                    for (Move prediction : lastTurn0Predictions[playerid][situation][algo]) {
                        if(prediction != null) {
                            CheapShip shipAt = Map.currentMap.GetShipAt(prediction.to);

                            if (shipAt != null) {
                                if (shipAt.id == prediction.ship.id) {
                                    succesfulPredictions[playerid][situation][algo]++;
                                }
                            } else {
                                //checking whether it's likely it did the expected move and died
                                boolean found = false;
                                for(Tile t : prediction.from.neighboursAndSelf){
                                    if(Map.staticShipsMap[t.x][t.y] != null && Map.staticShipsMap[t.x][t.y].id == prediction.ship.id){
                                        found =true;
                                        break;
                                    }
                                }
                                if(!found){
                                    if (Map.staticHaliteMap[prediction.to.x][prediction.to.y] > Map.staticHaliteMapLastTurn[prediction.to.x][prediction.to.y]) {
                                        succesfulPredictions[playerid][situation][algo]++;

//                                        Log.log("Succesful kill prediction?? " + prediction, Log.LogType.MAIN);
                                    }
//                                    else {
//
//                                        Log.log("Unsuccesful kill prediction?? " + prediction, Log.LogType.MAIN);
//                                    }
                                }
                            }
                            predictions[playerid][situation][algo]++;
                        }
                    }
                }
            }

            for (int situation = 0; situation < SituationCount; situation++) {
                float mostReliablePredictionScore = -10000000000f;
                for (int algo = 0; algo < AlgorithmCount; algo++) {
                    if (predictions[playerid][situation][algo] > 5) {
                        float ratio = succesfulPredictions[playerid][situation][algo] / predictions[playerid][situation][algo];
                        if (ratio > mostReliablePredictionScore) {
                            mostReliablePredictionScore = ratio;
                            MostReliableAlgoForSituation[playerid][situation] = algo;
                        }
                    }
                }
            }

            for (int i = 0; i < SituationCount; i++) {
                for (int j = 0; j < AlgorithmCount; j++) {
                    lastTurn0Predictions[playerid][i][j].clear();
                }
            }

//            if(MyBot.ALLOW_LOGGING && Log.LogType.PREDICTION.active) {
//                String predictionOutput = "\r\n\r\nPrediction player: " + playerid;
//
//                for (int i = 0; i < SituationCount; i++) {
//                    predictionOutput += "\r\nSituation: " + i + "   ";
//                    for (int j = 0; j < AlgorithmCount; j++) {
//                        predictionOutput += j + ": " +  (succesfulPredictions[playerid][i][j] / predictions[playerid][i][j]) +";   ";
//                    }
//                }


//                Log.log(predictionOutput, Log.LogType.PREDICTION);
//            }
        }


        for(CheapShip s : Map.staticEnemyShipsLastTurn){
            int playerid =  Map.OwnerOfShip[s.id];
            if(!playerHasDeliberatelyKilled[playerid]) {
                Tile from = s.GetTile();
                Tile to = null;

                for (Tile t : from.neighboursAndSelf) {
                    if (Map.staticShipsMap[t.x][t.y] != null && Map.staticShipsMap[t.x][t.y].id == s.id) {
                        to = t;
                        break;
                    }
                }

                if (to == null) {
                    for (Tile t : from.neighboursAndSelf) {
                        if (Map.staticHaliteMap[t.x][t.y] > Map.staticHaliteMapLastTurn[t.x][t.y]) {
                            to = t;
                            break;
                        }
                    }
                }

                if (to != null && !from.equals(to)) {
                    CheapShip shipThereLastTurn = Map.staticShipsMapLastTurn[to.x][to.y];
                    if (shipThereLastTurn != null && Map.OwnerOfShip[shipThereLastTurn.id] != playerid) {
                        playerHasDeliberatelyKilled[playerid] = true;
//                        Log.log("Player " + playerid + " tried to commit murder!", Log.LogType.MAIN);
                    }

                }
            }
        }

    }


    public static void SetMurderSpots(){
        for(Tile t : Map.tileList){
            t.murderSpot = 0;
            t.likelyMurderScore = 0;
        }
        for(Competitor c : MyBot.players){
            if(!c.isMe && playerHasDeliberatelyKilled[c.id]){

                for(CheapShip s : c.ships){

                    for(Tile t : s.GetTile().neighbours){
                        CheapShip shipThere = Map.staticShipsMap[t.x][t.y];
                        if(shipThere != null && Map.OwnerOfShip[shipThere.id] != c.id){
                            t.murderSpot++;

                            float likelyHood = 0;

                            if(shipThere.halite > s.halite){
                                likelyHood += HandwavyWeights.MurderMoreHalite +   (shipThere.halite - s.halite) * HandwavyWeights.MurderHaliteDif;
                            }
                            if(t.controlDanger >0 ){
                                likelyHood += HandwavyWeights.MurderControlFlat + t.controlDanger * HandwavyWeights.MurderControl;
                            }

                            likelyHood += shipThere.halite * HandwavyWeights.MurderHalite;
                            likelyHood += t.haliteStartTurn * HandwavyWeights.MurderTileHalite;
                            likelyHood -= s.halite * HandwavyWeights.MurderHaliteEnemyHalite;

                            if(s.halite> 900 && t.controlDanger < 0.5f){
                                likelyHood -= HandwavyWeights.MurderFullEnemy;
                            }

                            if(MyBot.playerCount == 4){
                                likelyHood *= HandwavyWeights.Murder4p;
                            }

                            t.likelyMurderScore = Math.max(0,likelyHood);


                        }
                    }
                }
            }
        }

        if(MyBot.DO_GAME_OUTPUT){

            StringBuilder s = new StringBuilder();

            s.append("murder:");
            for(int y =0; y < Map.height; y++){
                for(int x=0; x < Map.width; x++){
                    s.append( Map.tiles[x][y].likelyMurderScore + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());
        }
    }

    //REMEMBER: ENEMY MOVES ARE NOT SIMMED BEFORE THIS EVALUATOR
    public static float EvaluateMoveUsingAlgo(Move m, Map map, int algorithm){
        float score = 0f;
        CheapShip shipAt = map.GetShipAt(m.to);

        switch (algorithm){
            case ALGORITHM_BASIC_PEACEFUL:
                if(m.isStandStill()){
                    score = map.GetHaliteAt(m.to) * HandwavyWeights.BASIC_STANDSTILL_HALITE;
                }else {
                    score = map.GetHaliteAt(m.to) * HandwavyWeights.BASIC_MOVE_HALITE;
                    score -= map.GetHaliteAt(m.from) *HandwavyWeights.BASIC_BURNHALITE;
                }

                score -= -Plan.distToMeaningfulHalite[m.to.x][m.to.y] * HandwavyWeights.BASIC_MEANINGFUL;
                score += Plan.medDistLure[m.to.x][m.to.y] * HandwavyWeights.BASIC_MEDLURE;

                //TODO: select the right players dropoff distance
                if(m.ship.halite > 900){
                    score +=  m.to.turnsFromEnemyDropoff * HandwavyWeights.TURNS_DROPOFF_FULL;
                    score -= map.GetHaliteAt(m.to) *HandwavyWeights.BASIC_BURNHALITE;
                }

                //TODO: probably something about inspire seeking


                break;
            case ALGORITHM_BASIC_NEVER_MOVE_TO_SHIP:
                if(m.isStandStill()) return EvaluateMoveUsingAlgo(m,map,ALGORITHM_BASIC_PEACEFUL);
                if(shipAt == null) return EvaluateMoveUsingAlgo(m,map,ALGORITHM_BASIC_PEACEFUL);
                return 0f;
            case ALGORITHM_STAND_STILL:
                if(m.isStandStill()){
                    score = 1f;
                }
                else{
                    score = 0f;
                }
                break;
            case ALGORITHM_RETURN_HOME:
                score = -Plan.turnsFromEnemyDropoff[m.to.x][m.to.y];
                break;
            case ALGORITHM_RUN_FROM_HOME:
                score = Plan.turnsFromEnemyDropoff[m.to.x][m.to.y];
                break;
            case ALGORITHM_ALWAYS_KILL_NEARBY:
                if(m.isStandStill()) return EvaluateMoveUsingAlgo(m,map,ALGORITHM_BASIC_PEACEFUL);
                if(shipAt == null) return EvaluateMoveUsingAlgo(m,map,ALGORITHM_BASIC_PEACEFUL);
                if(Map.OwnerOfShip[shipAt.id] == Map.OwnerOfShip[m.ship.id]){
                    return 0f;
                }else{
                    return 10000000f + shipAt.halite;
                }
            case ALGORITHM_KILL_MOREHALITE:
                if(m.isStandStill()) return EvaluateMoveUsingAlgo(m,map,ALGORITHM_BASIC_PEACEFUL);
                if(shipAt == null) return EvaluateMoveUsingAlgo(m,map,ALGORITHM_BASIC_PEACEFUL);
                if(Map.OwnerOfShip[shipAt.id] == Map.OwnerOfShip[m.ship.id]){
                    return 0f;
                }else{
                    if(shipAt.halite > m.ship.halite * 1.05) {
                        return 10000000f + shipAt.halite;
                    }
                    else{
                        return 0f;
                    }
                }

            case ALGORITHM_CHASE_NEARBY:
                if(m.isStandStill()) return EvaluateMoveUsingAlgo(m,map,ALGORITHM_BASIC_PEACEFUL);
                if(shipAt == null){
                    for(Tile t : m.to.tilesInWalkDistance[2]){
                        CheapShip shipNearby = map.GetShipAt(t);
                        if(shipNearby != null && Map.OwnerOfShip[m.ship.id] !=  Map.OwnerOfShip[shipNearby.id]){
                            return 100000f + shipNearby.halite - m.to.DistManhattan(t) * 100; //not as high of a desire as a direct kill
                        }
                    }
                    return EvaluateMoveUsingAlgo(m,map,ALGORITHM_BASIC_PEACEFUL);
                }
                if(Map.OwnerOfShip[shipAt.id] == Map.OwnerOfShip[m.ship.id]){
                    return 0f;
                }else{
                    //direct kill
                    return 10000000f + shipAt.halite;
                }

            case ALGORITHM_MOVE_TO_HIGHEST_NEIGHBOUR:
                score = map.GetHaliteAt(m.to);
                break;
            case ALGORITHM_MOVE_TO_HIGHEST_NEIGHBOUR_SLIGHLTY_SMARTER:
                if(m.isStandStill()){
                    score = map.GetHaliteAt(m.to) * 3.0f;
                }else {
                    score = map.GetHaliteAt(m.to);
                }
                break;
            case ALGORITHM_MOVE_TO_LOWEST_NEIGHBOUR:
                score = -map.GetHaliteAt(m.to);
                break;
            case ALGORITHM_MOVE_ACCORDING_TO_MEANINGFUL:
                score = -Plan.distToMeaningfulHalite[m.to.x][m.to.y] * 50 +  m.to.haliteStartTurn;
                break;
            case ALGORITHM_MOVE_ACCORDING_TO_MEDIUM_LURE:
                score = Plan.medDistLure[m.to.x][m.to.y]* 20  +  m.to.haliteStartTurn ;
                break;
            case ALGORITHM_SIMPLE_BURN_OPTIMIZATION:
                if(m.isStandStill()){
                    score = map.GetHaliteAt(m.to) * 1.5f;
                }else{
                    score = map.GetHaliteAt(m.to) - map.GetHaliteAt(m.from) * 0.1f;
                }
                break;
            case ALGORITHM_REPEATER:
                Tile prevTile =  m.to;

                if(m.to.IsSouthOf(m.from)){
                    prevTile = m.from.north;
                }else if(m.to.IsNorthOf(m.from)){
                    prevTile = m.from.south;
                }else if(m.to.IsWestOf(m.from)){
                    prevTile = m.from.east;
                }else if(m.to.IsEastOf(m.from)){
                    prevTile = m.from.west;
                }
                if(Map.staticShipsMapLastTurn[prevTile.x][prevTile.y] != null && Map.staticShipsMapLastTurn[prevTile.x][prevTile.y].id == m.ship.id){
                    score = 1;
                }else{
                    score = 0;
                }
                break;

            case ALGORITHM_MOVEMENT_DIRECTION:
                if(wasHere5TurnsAgo[m.ship.id] != null) {
                    score = (float)m.to.DistStraight(wasHere5TurnsAgo[m.ship.id]);
                }else{
                    return EvaluateMoveUsingAlgo(m,map,ALGORITHM_REPEATER);
                }
                break;
            case ALGORITHM_ANOTHER_MINER:
                int hal = map.GetHaliteAt(m.to);
                if(hal > Plan.AverageHalite * 0.5f) {
                    if (m.isStandStill()) {
                        score = hal * 2f;
                    } else {
                        score = hal * 1f;
                    }
                }else{
                    score = hal * -0.3f;
                }

                score += Plan.medDistLure[m.to.x][m.to.y] * 0.3f;

                //TODO: select the right players dropoff distance
                if(m.ship.halite > 920){
                    score +=  m.to.turnsFromEnemyDropoff * HandwavyWeights.TURNS_DROPOFF_FULL;
                    score -= map.GetHaliteAt(m.to) *HandwavyWeights.BASIC_BURNHALITE;
                }
                break;
            case ALGORITHM_ANOTHER_MINER_2:
                if (m.isStandStill()) {
                    score = map.GetHaliteAt(m.to) * 3f;
                } else {
                    score = map.GetHaliteAt(m.to) * 1f;
                }
                score += Plan.medDistLure[m.to.x][m.to.y] * 1f;
                break;
            case ALGORITHM_ANOTHER_MINER_3:
                score = MyBot.collectIfStandForX[ Math.min(5000,map.GetHaliteAt(m.to))][3];

                break;

        }

        return score;
    }







    //Realized halfway this is going to be way too expensive
//    static float[][][] monteCarloInspireOdds = null;
//    public static void MonteCarloPrediction(int depth) {
//
//            monteCarloInspireOdds = new float[depth][Map.width][Map.height];
//
//        if(Map.tiles[0][0].visitedMonteCarlo == null || Map.tiles[0][0].visitedMonteCarlo.length != depth) {
//            for (Tile t : Map.tileList) {
//                t.visitedMonteCarlo = new int[depth];
//            }
//        }
//
//
//        //int[][][] visited = new int[depth][Map.width][Map.height];
//
//        float[][][] oddsOfAtLeastOne = new float[depth][Map.width][Map.height];
//        int iterations = 1000;
//
//        ArrayList<Tile>[] relevantTiles = new ArrayList[depth];
//        for (int turn = 0; turn < depth; turn++) {
//            relevantTiles[turn] = new ArrayList<Tile>();
//
//
//
//
//        }
//
//        for (CheapShip s : Map.staticEnemyShips) {
//
//
//            for (int turn = 0; turn < depth; turn++) {
//                relevantTiles[turn].clear();
//            }
//
//            boolean returningHome = s.halite > 800 + MyBot.rand.nextInt(200);
//
//            for (int iteration = 0; iteration < iterations; iteration++) {
//                Tile curspot = s.GetTile();
//
//                for (int turn = 0; turn < depth; turn++) {
//                    float highest = -10000000f;
//                    Tile nexttile = curspot;
//
//                    for (Tile t : curspot.neighboursAndSelf) {
//                        float score;
//
//                        if (returningHome) {
//                            score = -t.turnsFromEnemyDropoff + MyBot.rand.nextFloat() * 2f;
//                        } else {
//
//                            score = Plan.longDistLure[t.x][t.y] * 0.5f;
//                            if (t == curspot) {
//                                score += 20 + t.haliteStartTurn * 3f;
//                            } else {
//                                score += t.haliteStartTurn;
//                            }
//
//                            score *= MyBot.rand.nextFloat();
//                            score += MyBot.rand.nextFloat() * 20f;
//                        }
//
//                        if (score > highest) {
//                            highest = score;
//                            nexttile = t;
//                        }
//
//                    }
//                    if(nexttile.visitedMonteCarlo[turn] == 0){
//                        relevantTiles[turn].add(nexttile);
//                    }
//                    nexttile.visitedMonteCarlo[turn]++;
//                }
//            }
//
//
//            for (int turn = 0; turn < depth; turn++) {
//                for(Tile t : relevantTiles[turn]){
//                    t.visitedMonteCarlo[turn]=0;
//                }
//            }
//
//
//
//        }
//
//        for (int turn = 0; turn < depth; turn++) {
//            for (int x = 0; x < Map.width; x++) {
//                for (int y = 0; y < Map.height; y++) {
//                    int count= visited[turn][x][y];
//                    if(count > 0){
//                        for(Tile t : Map.tiles[x][y].tilesInWalkDistance[4]){
//                            inspirevisited[turn][t.x][t.y] += count;
//                        }
//                    }
//
//                }
//            }
//        }
//        for (int turn = 0; turn < depth; turn++) {
//            for (int x = 0; x < Map.width; x++) {
//                for (int y = 0; y < Map.height; y++) {
//
//
//                    monteCarloInspireOdds[turn][x][y] = Math.min(1f,   visited[turn][x][y] / ( 41f * totalVisited[turn]));
//                }
//            }
//        }
//
//        if(MyBot.DO_GAME_OUTPUT){
//
//            StringBuilder s = new StringBuilder();
//
//            s.append("monteCarloOdds1:");
//            for(int y =0; y < Map.height; y++){
//                for(int x=0; x < Map.width; x++){
//                    s.append( monteCarloInspireOdds[1][x][y] + ",");
//                }
//                s.append(";");
//            }
//            GameOutput.info.add(s.toString());
//
//
//            s.append("monteCarloOdds5:");
//            for(int y =0; y < Map.height; y++){
//                for(int x=0; x < Map.width; x++){
//                    s.append( monteCarloInspireOdds[5][x][y] + ",");
//                }
//                s.append(";");
//            }
//            GameOutput.info.add(s.toString());
//        }
//
//    }

}
