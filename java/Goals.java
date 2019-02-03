import hlt.Constants;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.TreeSet;


//Find long distance goals for ships
//These mostly help ships find their initial halite gather area after leaving a dropoff
//These end up being really important for the macro-level movement of ships and help area control and ship turtle flocking/souping
//Especially urgent goals (where were competing with enemies) are identified here too, ships prioritize moving towards them more
//For full ships, a preferred dropoff to return to is picked, which generally -but not always- ends up just being the closest dropoff
//imaginary future dropoffs are also supported here (they arent supported in other return to dropoff methods)



public class Goals {
    public static double[] desire;
    public static Tile[] previousGoals;

    private static float nearbyScore;

    public static Tile[] GetShipGoals(int[][] reachTilesIn, float[][] longDistLure, Tile[] previousGoals, float[][][] inspirePredictions){
        Goals.previousGoals = previousGoals;
        int highestid = 0;
        for(CheapShip s : Map.staticMyShips){
            highestid = Math.max(highestid,s.id);
        }
        float normalizedForAverageHaliteFactor  =  Math.min( HandwavyWeights.GoalAverageHaliteNormalizeCap,Plan.AverageHalite / (HandwavyWeights.GoalAverageHaliteNormalizeVal * HandwavyWeights.GoalAverageHaliteNormalizeValSizeMult[MyBot.GAMETYPE_SIZE])  ) ; //if the average halite on the map drops low, many of the goal factors become too important

      //  nearbyScore = (HandwavyWeights.DesirabilityNearbyTakenStart * (1f - MyBot.proportionGame)    +   HandwavyWeights.DesirabilityNearbyTakenEnd * MyBot.proportionGame)  * normalizedForAverageHaliteFactor ;


        Tile[] orders = new Tile[highestid + 1];
        desire = new double[highestid + 1];


        int inspireturn = Math.min(Plan.inspireOdds.length-1,HandwavyWeights.GoalUseInspireTurn);



        int dropoffSpotHaliteNear = 0;
        if(Plan.dropOffSpot != null){
            for(Tile t : Plan.dropOffSpot.tilesInWalkDistance[7]){
                dropoffSpotHaliteNear += t.haliteStartTurn;
            }
        }


        float era0 = 0;
        float era1 = 0.1f;
        float era2 = 0.2f;
        float era3 = 0.3f;
        float era4 = 0.45f;
        float era5 = 0.65f;
        float era6 = 0.9f;
        float era7 = 1f;

        float denyMultiplier = 1f;

        if(MyBot.proportionGame < era1){
            float prop = (MyBot.proportionGame - era0) / (era1 - era0);
            denyMultiplier = prop * HandwavyWeights.DenyRelevanceEra1 + (1f - prop) * HandwavyWeights.DenyRelevanceEra0;
            nearbyScore = prop * HandwavyWeights.GoalNearbyEra1  + (1f - prop) * HandwavyWeights.GoalNearbyEra0;
        }else if(MyBot.proportionGame < era2){
            float prop = (MyBot.proportionGame - era1) / (era2 - era1);
            denyMultiplier = prop * HandwavyWeights.DenyRelevanceEra2 + (1f - prop) * HandwavyWeights.DenyRelevanceEra1;
            nearbyScore = prop * HandwavyWeights.GoalNearbyEra2  + (1f - prop) * HandwavyWeights.GoalNearbyEra1;
        }else if(MyBot.proportionGame < era3){
            float prop = (MyBot.proportionGame - era2) / (era3 - era2);
            denyMultiplier = prop * HandwavyWeights.DenyRelevanceEra3 + (1f - prop) * HandwavyWeights.DenyRelevanceEra2;
            nearbyScore = prop * HandwavyWeights.GoalNearbyEra3  + (1f - prop) * HandwavyWeights.GoalNearbyEra2;
        }else if(MyBot.proportionGame < era4){
            float prop = (MyBot.proportionGame - era3) / (era4 - era3);
            denyMultiplier = prop * HandwavyWeights.DenyRelevanceEra4 + (1f - prop) * HandwavyWeights.DenyRelevanceEra3;
            nearbyScore = prop * HandwavyWeights.GoalNearbyEra4  + (1f - prop) * HandwavyWeights.GoalNearbyEra3;
        }else if(MyBot.proportionGame < era5){
            float prop = (MyBot.proportionGame - era4) / (era5 - era4);
            denyMultiplier = prop * HandwavyWeights.DenyRelevanceEra5 + (1f - prop) * HandwavyWeights.DenyRelevanceEra4;
            nearbyScore = prop * HandwavyWeights.GoalNearbyEra5  + (1f - prop) * HandwavyWeights.GoalNearbyEra4;
        }else if(MyBot.proportionGame < era6){
            float prop = (MyBot.proportionGame - era5) / (era6 - era5);
            denyMultiplier = prop * HandwavyWeights.DenyRelevanceEra6 + (1f - prop) * HandwavyWeights.DenyRelevanceEra5;
            nearbyScore = prop * HandwavyWeights.GoalNearbyEra6  + (1f - prop) * HandwavyWeights.GoalNearbyEra5;
        }else if(MyBot.proportionGame < era7){
            float prop = (MyBot.proportionGame - era6) / (era7 - era6);
            denyMultiplier = prop * HandwavyWeights.DenyRelevanceEra7 + (1f - prop) * HandwavyWeights.DenyRelevanceEra6;
            nearbyScore = prop * HandwavyWeights.GoalNearbyEra7  + (1f - prop) * HandwavyWeights.GoalNearbyEra6;
        }

        nearbyScore *= HandwavyWeights.GoalNearbyEraPlayers[MyBot.GAMETYPE_PLAYERS];
        nearbyScore *= HandwavyWeights.GoalNearbyEraDensity[MyBot.GAMETYPE_DENSITY];
        nearbyScore *= HandwavyWeights.GoalNearbyEraMapSize[MyBot.GAMETYPE_SIZE];
        //Log.log("Deny multiplier: " + denyMultiplier, Log.LogType.MAIN);

        if(MyBot.playerCount == 4 && Map.width == 32){
            nearbyScore *= HandwavyWeights.GoalNearby32324Mult; //special case, this map size doesn't benefit from this at all
            denyMultiplier *= HandwavyWeights.Deny32324Mult;
        }


        for(int x  = 0; x < Map.width; x++) {
            for (int y = 0; y < Map.height; y++) {
                Tile t = Map.tiles[x][y];

                float expectedhalite = t.haliteStartTurn;  //*   (1.0f +  (2.0f * Plan.inspireOdds[inspireturn][x][y] * HandwavyWeights.GoalTrustInspire[MyBot.GAMETYPE_PLAYERS]));
                t.obtainableHalite =  expectedhalite ;

                float nearbyHalite = 0;
                for(Tile t2 : t.tilesInWalkDistance[1]){
                    if(!t.equals(t2))
                    nearbyHalite += Map.staticHaliteMap[t2.x][t2.y] * 0.5f; //dont handwavy this, already accounted for by TileScoreNeighboursV2
                }
                for(Tile t2 : t.tilesInWalkDistance[2]){
                    if(!t.equals(t2))
                    nearbyHalite += Map.staticHaliteMap[t2.x][t2.y] * 0.5f;
                }
                nearbyHalite = Math.max(0f,Math.min(nearbyHalite, 1000f - expectedhalite));
                t.obtainableHalite += nearbyHalite * HandwavyWeights.TileScoreNeighboursV2;

                float denyRelevantScores = 0f;


                t.score = longDistLure[x][y] * HandwavyWeights.TileScoreLong;

                t.score += Plan.inspireOdds[inspireturn][x][y] * HandwavyWeights.GoalWeightInspireFlat[MyBot.GAMETYPE_PLAYERS] * normalizedForAverageHaliteFactor;
                t.score += Plan.inspireOdds[inspireturn][x][y] * HandwavyWeights.GoalWeightInspireHal[MyBot.GAMETYPE_PLAYERS] * t.haliteStartTurn;


                //These affect how much we should base the goal on areas of the map we'd rather take before the enemy does
                denyRelevantScores -= t.turnsFromDropoff * HandwavyWeights.DistDropoffScore  * normalizedForAverageHaliteFactor;
                denyRelevantScores -= t.turnsFromEnemyDropoff * HandwavyWeights.DistEnemyDropoffScore[MyBot.GAMETYPE_PLAYERS]  * normalizedForAverageHaliteFactor;

                denyRelevantScores += Math.max(0,t.enemyTerrain) * (HandwavyWeights.EnemyTerrainGoalFlatV2  * normalizedForAverageHaliteFactor + HandwavyWeights.EnemyTerrainGoalHaliteV2 *  Map.staticHaliteMap[x][y]) * MyBot.proportionGame;

                if(t.borderTile){
                    denyRelevantScores += HandwavyWeights.BorderReachableFlat  * normalizedForAverageHaliteFactor;
                    denyRelevantScores += HandwavyWeights.BorderReachableHalite * t.haliteStartTurn;
                }

                denyRelevantScores += expectedhalite * Plan.controlEdgeMap[x][y] * HandwavyWeights.GoalWeightEdgeMapHaliteV2 * MyBot.proportionGame;
                denyRelevantScores += Plan.controlEdgeMap[t.x][t.y] * HandwavyWeights.GoalWeightEdgeMapFlatV2 * MyBot.proportionGame  * normalizedForAverageHaliteFactor;

                if(MyBot.turn < Constants.MAX_TURNS * 0.3f){
                    denyRelevantScores += (1f- t.distFromCenterProportion) *  HandwavyWeights.GoalWeightDistanceCenterEarlyV2[MyBot.GAMETYPE_PLAYERS]  * normalizedForAverageHaliteFactor;
                }else{
                    denyRelevantScores += (1f- t.distFromCenterProportion) *  HandwavyWeights.GoalWeightDistanceCenterLateV2[MyBot.GAMETYPE_PLAYERS]  * normalizedForAverageHaliteFactor;
                }

                denyRelevantScores += t.controlDanger * HandwavyWeights.GoalControlDanger[MyBot.GAMETYPE_PLAYERS]  * normalizedForAverageHaliteFactor;

                if(t.isCenterTile) {
                    denyRelevantScores += HandwavyWeights.GoalCentralHalite * t.haliteStartTurn + HandwavyWeights.GoalCentralFlat;
                }

                if(t.turnsToReachEnemyShips - t.turnsToReachMyShips > HandwavyWeights.TilesDistanceLowPriorityZone){
                    t.score += HandwavyWeights.LowPrioZoneHalite[MyBot.GAMETYPE_PLAYERS] * t.haliteStartTurn + HandwavyWeights.LowPrioZoneFlat[MyBot.GAMETYPE_PLAYERS];
                }

                if(Plan.dropOffSpot != null){
                    if(Plan.dropOffSpot.turnsToReachEnemyShips < t.turnsToReachMyShips + 1 ||  t.isCenterTile ||  Plan.controlEdgeMap[t.x][t.y] > 0.1f){
                        denyRelevantScores +=  Util.ReverseLogCurve(t.DistManhattan(Plan.dropOffSpot),0,10) * HandwavyWeights.GoalWeightUpcomingDropoffDistV2 * HandwavyWeights.GoalWeightUpcomingDropoffDistV2PlayersMult[MyBot.GAMETYPE_PLAYERS];
                    }else{
                        t.score +=  Util.ReverseLogCurve(t.DistManhattan(Plan.dropOffSpot),0,8) * HandwavyWeights.GoalWeightUpcomingDropoffDistV2 * HandwavyWeights.GoalWeightUpcomingDropoffDistV2PlayersMult[MyBot.GAMETYPE_PLAYERS];
                    }
                }

                if(t.enemyShipsStartInRange5 > 1 || t.antiInspire > 0){
                    t.nearbyMultiplier = HandwavyWeights.NearbyMultiplierEnemiesClose;
                }else{
                    t.nearbyMultiplier = HandwavyWeights.NearbyMultiplierAlone;
                }

                denyRelevantScores += t.enemyShipsStartInRange5 * HandwavyWeights.GoalEnemysNear5;
                t.score += t.myShipsStartInRange5 * HandwavyWeights.GoalFriendsNear5;

                denyRelevantScores -=  t.antiInspire * HandwavyWeights.AntiInspireGoalWeightV2[MyBot.GAMETYPE_PLAYERS];
                denyRelevantScores += Math.max(0, SideAlgorithms.BrawlMap[t.x][t.y] - HandwavyWeights.BrawlMin[MyBot.GAMETYPE_PLAYERS]) * HandwavyWeights.GoalBrawl[MyBot.GAMETYPE_PLAYERS];
                denyRelevantScores *= HandwavyWeights.GoalDenyScores[MyBot.GAMETYPE];
                denyRelevantScores *= denyMultiplier;

                t.score += denyRelevantScores;

                t.goalIsAboutDenying =  HandwavyWeights.ActivateDenyGoals == 1 && ( denyRelevantScores > HandwavyWeights.MinDenyScore || t.isCenterTile);// || t.turnsToReachEnemyShips < t.turnsToReachMyShips;


                t.score +=   (10 - t.forecastTurnsFromDropoff) * HandwavyWeights.GoalWeightForecastDist;

                t.score +=  MyBot.haliteSqrtCurve[t.haliteStartTurn] * HandwavyWeights.GoalWeightSqrtHalite ;
                t.score +=  MyBot.haliteLogCurve[t.haliteStartTurn] * HandwavyWeights.GoalWeightLogHalite;
                t.score +=  MyBot.haliteExponentialCurve[t.haliteStartTurn] * HandwavyWeights.GoalWeightExpoHalite;


                t.score += t.movesSum2 * HandwavyWeights.GoalMoveSums2;
                t.score += t.movesSum3 * HandwavyWeights.GoalMoveSums3;
                t.score += t.movesSum4 * HandwavyWeights.GoalMoveSums4;

                if(t.turnsFromDropoff >  MyBot.turnsLeft - 8){
                    t.score -= 100000;
                }

                t.minDist = reachTilesIn[x][y];
                t.neighboursTaken = 0;
                t.nearbyTaken = 0;
                t.isTaken = 0;

            }
        }
        float totalDesirability = 0f;
        for(Tile t : Map.tileList) {
            UpdateTileDesirability(t);
            totalDesirability += t.desirability;
        }
        float avgDesirability = totalDesirability / ((float)Map.tileList.size());


        if(MyBot.DO_GAME_OUTPUT){
            StringBuilder s = new StringBuilder();
            s.append("goaltilescores:");
            for(int y =0; y < Map.height; y++){
                for(int x=0; x < Map.width; x++){
                    s.append( Map.tiles[x][y].score + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());


            StringBuilder s2 = new StringBuilder();
            s2.append("goaltiledesirability:");
            for(int y =0; y < Map.height; y++){
                for(int x=0; x < Map.width; x++){
                    s2.append( Map.tiles[x][y].desirability + ",");
                }
                s2.append(";");
            }
            GameOutput.info.add(s2.toString());

            StringBuilder s3 = new StringBuilder();
            s3.append("goaltileaboutdenying:");
            for(int y =0; y < Map.height; y++){
                for(int x=0; x < Map.width; x++){
                    if(Map.tiles[x][y].goalIsAboutDenying){
                        s3.append( "1,");
                    }else{
                        s3.append( "0,");
                    }

                }
                s3.append(";");
            }
            GameOutput.info.add(s3.toString());
        }



//        String str3 = "Desirability: \r\n";
//
//        Collections.sort(Map.tileList);
//
//        for(Tile t : Map.tileList){
//            str3 +=  t + ": " + t.desirability+ ", " + t.finalScore  +", " + longDistLure[t.x][t.y] + "   " ;
//        }
//        Log.log(str3, Log.LogType.MAIN);


        for(int i =0 ; i < Math.min(previousGoals.length,orders.length);i++){
            if(previousGoals[i] != null && previousGoals[i].turnsFromDropoff > 0) {

                CheapShip ship = Map.staticShipsById[i];
                if(ship != null) {
                    float score = GetShipToTileScore(ship,previousGoals[i]);
                    if(score >= HandwavyWeights.GoalFarTileMinimumScoreV2) {
                        orders[i] = previousGoals[i];
                        orders[i].isTaken++;
                        for (Tile t : orders[i].neighbours) {
                            t.neighboursTaken++;
                        }
                        for (Tile t : orders[i].tilesInWalkDistance[HandwavyWeights.NearbyRange]) {
                            t.nearbyTaken++;
                        }

//                        if(orders[i].DistManhattan(ship) > HandwavyWeights.MaxDistForHighHaliteShip) {
//                            Log.log("SET Questionable GOAL Step 0" + ship, Log.LogType.MAIN);
//                            if (ship.halite > HandwavyWeights.MaxHaliteForGoalV3) {
//                                Log.log("SET BAD GOAL Step 0" + ship, Log.LogType.MAIN);
//                            }
//                        }
                    }


                }


            }
        }
        ArrayList<Tile> dropoffTiles = new ArrayList<>();
        for(DropPoint d : Map.myDropoffs){
            dropoffTiles.add(d.tile);
        }
        if(Plan.dropOffSpot != null && HandwavyWeights.ActivateReturnToFutureDropoff == 1 && MyBot.expectedHaliteForDropoff >= Constants.DROPOFF_COST){
            dropoffTiles.add(Plan.dropOffSpot);
        }


        for(Tile t : dropoffTiles){
            t.GoalHaliteNear = 0;
            for(Tile t2 : t.tilesInWalkDistance[7]){
                t.GoalHaliteNear += t2.haliteStartTurn;
            }
            t.GoalShipsNear = 0;
            for(Tile t2 : t.tilesInWalkDistance[HandwavyWeights.NearbyRange]){
                if(Map.currentMap.IsShipAt(t2)) {
                    t.GoalShipsNear++;
                }
            }
        }



        ArrayList<CheapShip> tileSeekingShips = new ArrayList<>();
        ArrayList<CheapShip> tileSeekingShips2 = new ArrayList<>();

        for(CheapShip s : Map.staticMyShips){
            if(Plan.annoyGoals[Map.myIndexOfIds[s.id]] != null){
                orders[s.id] = Plan.annoyGoals[Map.myIndexOfIds[s.id]];
            }
           else if(s.halite >= HandwavyWeights.MinHaliteForDropoffGoal){

                Tile bestspot = null;
                float bestScore= -1000000f;


                for(Tile tile  : dropoffTiles){
                    float score =  0;

                    DropPoint d = Map.myDropoffMap[tile.x][tile.y];
                    if(d != null) {
                        //A real dropoff
                        if (d.isYard) {
                            score -= HandwavyWeights.GoalDropoffShipYard;
                        }
                    }
                    else{
                        //Imaginary future dropoff
                        score -= HandwavyWeights.GoalDropoffNotMadeYet;
                    }

                    score += Math.min(HandwavyWeights.GoalDropoffNearbyHaliteMax, tile.GoalHaliteNear * HandwavyWeights.GoalDropoffNearbyHalite);
                    score += HandwavyWeights.GoalDropoffLongLure * Plan.longDistLure[tile.x][tile.y];
                    score -= Map.tiles[s.x][s.y].DistManhattan(tile.x,tile.y);
                    score -=  Math.max(0, tile.GoalShipsNear - 3 )* HandwavyWeights.GoalDropoffNearbyShips;
                    score -= Math.max(HandwavyWeights.GoalDropoffNearbyEnemyDropoffDist - tile.turnsFromEnemyDropoff,0) * HandwavyWeights.GoalDropoffNearbyEnemyDropoff[MyBot.GAMETYPE_PLAYERS];
                    if(score > bestScore){
                        bestScore = score;
                        bestspot = tile;
                    }
                }

                orders[s.id] = bestspot;
                desire[s.id] = 100f;
            }
//            else{
//                desire[s.id] = HandwavyWeights.GoalFarTileMinimumScoreV2;
//                tileSeekingShips.add(s);
//
//            }
            else if(HandwavyWeights.ActivateIndependence == 0) {
                if (s.halite < HandwavyWeights.MaxHaliteForGoalV3) {
                    desire[s.id] = HandwavyWeights.GoalFarTileMinimumScoreV2;
                    tileSeekingShips.add(s);
                } else {
                    orders[s.id] = null;
                }
            }

            else{
                float independenceScore = 0;

                independenceScore += s.halite * HandwavyWeights.IndependenceHalite;
                independenceScore += s.GetTile().movesSum3  * HandwavyWeights.IndependenceMax3;
                independenceScore += s.GetTile().complexDropoffDist  * HandwavyWeights.IndependenceDropoffDist;
                independenceScore += s.GetTile().enemyShipsStartInRange3Avg  * HandwavyWeights.IndependenceEnemiesRange3;
                independenceScore += s.GetTile().myShipsStartInRange3Avg * HandwavyWeights.IndependenceFriendsRange3;
                independenceScore += Plan.longDistLure[s.x][s.y] * HandwavyWeights.IndependenceLongLure;

                if(independenceScore < HandwavyWeights.MaxIndependenceScoreForGoal || s.halite < HandwavyWeights.MaxHaliteForGoalV3){
                    desire[s.id] = HandwavyWeights.GoalFarTileMinimumScoreV2;
                    tileSeekingShips.add(s);
                }
                else {
                    orders[s.id] = null;
                }
            }
        }
        tileSeekingShips2.addAll(tileSeekingShips);


        TreeSet<Tile> sortedTiles = new TreeSet<>();

        sortedTiles.addAll(Map.tileList);




        TreeSet<GoalShipPair> shipGoalPairs = new TreeSet<>();
        int tilecounter =0;
        int maxtiles = tileSeekingShips2.size() * 5;
        long time = System.currentTimeMillis();

        for(Tile t : sortedTiles){
            if(tilecounter++ > maxtiles   ||   (System.currentTimeMillis() - time > 50 && !MyBot.DETERMINISTIC_TIME_INDEPENDENT)) break;
             for(CheapShip s : tileSeekingShips){
                float score = GetShipToTileScore(s,t) + MyBot.rand.nextFloat() * 0.0001f;
                if(score > HandwavyWeights.GoalFarTileMinimumScoreV2) {
                    shipGoalPairs.add(new GoalShipPair(s, t, score));
                }
            }
        }
        ArrayDeque<GoalShipPair> nextPass = new ArrayDeque();

        int firstpasscounter = 0;
        for(GoalShipPair pair : shipGoalPairs){
            float newscore = GetShipToTileScore(pair.s,pair.goal); //cant trust the old score, we might have had a ship move here meanwhile
            firstpasscounter++;
            if(pair.goal.equals(orders[pair.s.id])){
                desire[pair.s.id] = newscore;
                nextPass.add(pair);
            }
            else if(newscore > desire[pair.s.id] - 200  ) {
                if (newscore > desire[pair.s.id] ) {
                    if (orders[pair.s.id] != null) {
                        orders[pair.s.id].isTaken--;
                        for (Tile t : orders[pair.s.id].neighbours) {
                            t.neighboursTaken--;
                        }
                        for (Tile t : orders[pair.s.id].tilesInWalkDistance[HandwavyWeights.NearbyRange]) {
                            t.nearbyTaken--;
                        }
                    }
                    desire[pair.s.id] = newscore;
                    orders[pair.s.id] = pair.goal;

//                    if(orders[pair.s.id].DistManhattan(pair.s) > HandwavyWeights.MaxDistForHighHaliteShip) {
//                        Log.log("SET Questionable GOAL Step 1 " + pair.s, Log.LogType.MAIN);
//                        if (pair.s.halite > HandwavyWeights.MaxHaliteForGoalV3) {
//                            Log.log("SET BAD GOAL Step 1 " + pair.s, Log.LogType.MAIN);
//                        }
//                    }


                    pair.goal.isTaken++;
                    for (Tile t : pair.goal.neighbours) {
                        t.neighboursTaken++;
                    }
                    for (Tile t : pair.goal.tilesInWalkDistance[HandwavyWeights.NearbyRange]) {
                        t.nearbyTaken++;
                    }
                }
                nextPass.add(pair);
            }
        }

        int nextPassCounter = 0;
        for(GoalShipPair pair : nextPass){
            float newscore = GetShipToTileScore(pair.s,pair.goal); //cant trust the old score, we might have had a ship move here meanwhile
            if (newscore > desire[pair.s.id]) {
                if (orders[pair.s.id] != null) {
                    orders[pair.s.id].isTaken--;
                    for (Tile t : orders[pair.s.id].neighbours) {
                        t.neighboursTaken--;
                    }
                    for (Tile t : orders[pair.s.id].tilesInWalkDistance[HandwavyWeights.NearbyRange]) {
                        t.nearbyTaken--;
                    }
                }
                desire[pair.s.id] = newscore;
                orders[pair.s.id] = pair.goal;

//                if(orders[pair.s.id].DistManhattan(pair.s) > HandwavyWeights.MaxDistForHighHaliteShip) {
//                    Log.log("SET Questionable GOAL Step 2" + pair.s, Log.LogType.MAIN);
//                    if (pair.s.halite > HandwavyWeights.MaxHaliteForGoalV3) {
//                        Log.log("SET BAD GOAL Step 2" + pair.s, Log.LogType.MAIN);
//                    }
//                }

                pair.goal.isTaken++;
                for (Tile t : pair.goal.neighbours) {
                    t.neighboursTaken++;
                }
                for (Tile t : pair.goal.tilesInWalkDistance[HandwavyWeights.NearbyRange]) {
                    t.nearbyTaken++;
                }
            }
            nextPassCounter++;
        }

//        Log.log("First: " + firstpasscounter + " next: " + nextPassCounter, Log.LogType.MAIN);



        if(!MyBot.SERVER_RELEASE) {
//            String str2 = "ShipGoals: \r\n";
//            for (CheapShip s : Map.staticMyShips) {
//
//                if (orders[s.id] == null) {
//                    str2 += s.id + ": none, ";
//                } else {
//                    str2 += s.id + ": " + orders[s.id] + ", ";
//
//                }
//            }
//            Log.log(str2, Log.LogType.MAIN);


//            for (CheapShip s : Map.staticMyShips) {
//                if(s.halite > HandwavyWeights.MaxHaliteForGoalV3){
//                    if (orders[s.id] != null  && orders[s.id].turnsFromDropoff  > 0 && orders[s.id] != Plan.dropOffSpot ) {
//                        if (   orders[s.id].DistManhattan(s) > HandwavyWeights.MaxDistForHighHaliteShip) {
//                            Log.log("BAD GOAL FOR " + s + "  " + orders[s.id], Log.LogType.MAIN);
//                        }
//                    }
//                }
//            }
        }




        return orders;
    }

    private static float GetShipToTileScore(CheapShip s, Tile t){

        int dist = t.DistManhattan(s.x,s.y);

        if(dist > HandwavyWeights.GoalMaxDist){// ||  (s.halite > HandwavyWeights.MaxHaliteForGoalV3 && dist > HandwavyWeights.MaxDistForHighHaliteShip) ){
            return -10000000f;
        }

        float score = GetDesirability(t,s.roomLeft(),dist);


      //  score -=       (Math.max(0, t.haliteStartTurn - s.roomLeft())) * HandwavyWeights.GoalWeightHaliteV2; //connected to the halite part of the scoring system


       // score *=  1.0f + (inspire[Math.min(dist,inspire.length-1)][t.x][t.y] * 2.0 *  HandwavyWeights.GoalShipTileInspireTrust[MyBot.GAMETYPE_PLAYERS]);


        score -= dist * HandwavyWeights.ShipTileDistReductionV4;
        if (dist == 0) {
            score *= HandwavyWeights.ShipTileMultDist0;
        } else if(dist == 1){
            score *= HandwavyWeights.ShipTileMultDist1;
        }
        if(s.halite < 250) {
            score *= Math.pow(HandwavyWeights.ShipTileDistPowerEmptyishV2, dist);
        }
        else if(s.halite < 850){
            score *= Math.pow(HandwavyWeights.ShipTileDistPowerNormalV2, dist);

        } else{
            score *= Math.pow(HandwavyWeights.ShipTileDistPowerFullish, dist);

        }


        if(previousGoals.length >= s.id + 1&&  previousGoals[s.id] != null ){
            Tile p = previousGoals[s.id];
            if(t.equals(p)){
                score += HandwavyWeights.GoalConsistencyBonus0V2;
            }
            else if(t.DistManhattan(p) <= 1){
                score += HandwavyWeights.GoalConsistencyBonus1;
            }
            else if(t.DistManhattan(p) <= 3){
                score += HandwavyWeights.GoalConsistencyBonusNear;
            }
        }

        return score;

    }


    private static void UpdateTileDesirability(Tile t){

        t.desirability = GetDesirability(t,900, HandwavyWeights.GoalUseInspireTurn);
    }


    private static float GetDesirability(Tile t, int roomLeft, int dist){

        float desirability = t.score;
        desirability += (int)Math.min(t.obtainableHalite *  (1f + (t.inspireOdds[Math.min(dist,t.inspireOdds.length-1)])) ,roomLeft) * HandwavyWeights.GoalWeightHaliteV2;
        desirability += Math.min(HandwavyWeights.MaxNearbyTaken,t.nearbyTaken) * nearbyScore * t.nearbyMultiplier ;

        return  desirability*  (float)(Math.pow(HandwavyWeights.DesirabilityTaken2,t.isTaken) * Math.pow(HandwavyWeights.DesirabilityNeighboursTakenV3,t.neighboursTaken)) * HandwavyWeights.GoalTileDesireMultiplier ;

    }
}
