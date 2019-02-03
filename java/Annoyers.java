
import java.util.ArrayDeque;
import java.util.TreeSet;

/*
This class deals with two behaviors:
- Blocking dropoffs
- Blocking ships from crossing vertical lines they'll need to cross to get home, which can be done with one ship

The dropoff block system is only one of two used, see Plan.EvaluateTile() for the other lategame block

Both are relatively unfinished since they didn't really seem to be worth. Both are turned off in the final version

*/
public class Annoyers {


    public static boolean[][] GetEntrapmentMap(){
        boolean[][] entrapmentLocs = new boolean[Map.width][Map.height];


        if(HandwavyWeights.ActivateEntrapment == 0 || MyBot.playerCount == 4 || Map.staticMyShipCount < 15 || MyBot.enemy1.shipCount + HandwavyWeights.EntrapMinShipDifference >= MyBot.me.shipCount){
            return entrapmentLocs;
        }

        boolean[] columnsWithEDropoffs = new boolean[Map.width];

        for(DropPoint d : MyBot.enemy1.dropoffs){
            columnsWithEDropoffs[d.x] = true;
        }


        int[] leftMotionReq = new int[Map.width];
        int[] rightMotionReq = new int[Map.width];

        for(int startX = 0; startX < Map.width; startX++){

            for(int walkLeft = 0; walkLeft < Map.width; walkLeft++ ){
                int x =  (Map.width +  (startX - walkLeft)) % Map.width;
                if(columnsWithEDropoffs[x]){
                    leftMotionReq[startX] = walkLeft;
                }
            }
            for(int walkRight = 0; walkRight < Map.width; walkRight++ ){
                int x =  (Map.width +  (startX + walkRight)) % Map.width;
                if(columnsWithEDropoffs[x]){
                    rightMotionReq[startX] = walkRight;
                }
            }
        }

        for(CheapShip s : Map.staticRelevantEnemyShips){
            if(s.halite > HandwavyWeights.EntrapMinEHalite && s.GetTile().enemyShipsStartInRange5Avg <= s.GetTile().myShipsStartInRange5Avg * HandwavyWeights.EntrapControlFactor) {
                if (leftMotionReq[s.x] < rightMotionReq[s.x] - HandwavyWeights.EntrapMinDist  && leftMotionReq[s.x] > 1) {
                    entrapmentLocs[s.GetTile().west.x][s.y] = true;
                } else if (rightMotionReq[s.x] < leftMotionReq[s.x] - HandwavyWeights.EntrapMinDist && rightMotionReq[s.x] > 1) {
                    entrapmentLocs[s.GetTile().east.x][s.y] = true;
                }


                int dropoffDist = s.GetTile().turnsFromEnemyDropoff;

                if(dropoffDist < 5 && dropoffDist > 1) {
                    int countClosest = 0;
                    Tile best = null;

                    for (Tile t : s.GetTile().neighbours) {
                        if (t.turnsFromEnemyDropoff < dropoffDist){
                            countClosest++;
                            best = t;
                        }
                    }
                    if(countClosest == 1){ //If there's a sole spot that's closer to the dropoff then it's current spot, that's also a good option for a block
                        entrapmentLocs[best.x][best.y] = true;
                    }
                }

            }




        }


        if(MyBot.DO_GAME_OUTPUT) {
            StringBuilder s = new StringBuilder();

            s.append("entrapment:");
            for (int y = 0; y < Map.height; y++) {
                for (int x = 0; x < Map.width; x++) {
                    if(entrapmentLocs[x][y]){
                        s.append("1,");
                    }else{
                        s.append("0,");
                    }

                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());

        }

        return entrapmentLocs;
    }



    public static Tile[] AssignAnnoyers(){
        Tile[] annoyTargets = new Tile[Map.staticMyShipCount];

        if(HandwavyWeights.ActivateAnnoyMechanic == 0 || MyBot.proportionGame < HandwavyWeights.MinPropGameForAnnoying) return annoyTargets;


        ArrayDeque<Tile> relevantAnnoySpots = new ArrayDeque<>();

        for(Tile t : Map.tileList){
            t.annoyCount = 0;
            t.annoyEmergency = false;
        }
        int almostCertainLossKillPlayer = -1;
        int lowestPoints = 1000000;
        boolean oneICanBeat = MyBot.turn < 200 || MyBot.playerCount == 2;

        if(!oneICanBeat) {
            for (Competitor c : MyBot.players) {
                if (!c.isMe) {
                    if (c.expectedPoints < MyBot.me.expectedPoints * 1.1f || c.expectedPoints < MyBot.me.expectedPoints + 5000) {
                        oneICanBeat = true;
                    }
                    if (c.expectedPoints < lowestPoints) {
                        almostCertainLossKillPlayer = c.id;
                        lowestPoints = c.expectedPoints;
                    }
                }
            }
        }



        for(Competitor c : MyBot.players){
            if(c.dangerousEnoughToAnnoy  &&  (MyBot.turnsLeft < 100 ||  MyBot.me.shipCount > c.shipCount + 5)){
                boolean allowYardBlocks = !oneICanBeat && c.id == almostCertainLossKillPlayer;

                if(MyBot.turn -  c.lastBuiltShip > 15 || MyBot.turnsLeft < 40){
                    allowYardBlocks = true;
                }

                for(DropPoint d : c.dropoffs){
                    d.haliteNear = 0;
                    for(Tile t : d.tile.tilesInWalkDistance[7]){
                        d.haliteNear += t.haliteStartTurn;
                    }

                    if(!d.isYard || allowYardBlocks) {
                        relevantAnnoySpots.addAll(d.tile.tilesInDistance[1]);
                    }
                }

                for(CheapShip s : c.ships){
                    if(s.halite > 800 || (MyBot.turnsLeft < 30 && s.halite > 50)){
                        DropPoint closest = Map.tiles[s.x][s.y].closestDropoffPlayer[c.id];
                        if(!closest.isYard || allowYardBlocks) {
                            int closestDist = 10000;
                            Tile closestTile = closest.tile;
                            for (Tile t : closest.tile.tilesInDistance[1]) {
                                int dist = t.DistManhattan(s.x, s.y);
                                if (dist < closestDist) {
                                    closestDist = dist;
                                    closestTile = t;
                                }
                            }
                            if (closestDist < 10){
                                closestTile.annoyCount++;


                                //Extra boost for really close ships
                                if(closestDist == 1){
                                    if(closest.tile.DistManhattan(s.x,s.y) == 2 && s.halite > 400){
                                        closestTile.annoyEmergency = true;
                                    }
                                    closestTile.annoyCount += 2;

                                } else if(closestDist == 2){
                                    closestTile.annoyCount++;
                                }
                                closestTile.annoyPlayerId = closest.playerId;


                                closestTile.alreadySetAsAnnoyGoal = false;
                                closestTile.annoyHaliteNear = closest.haliteNear;
                            }
                        }
                    }
                }
            }
        }





        if(relevantAnnoySpots.size() > 0){
            TreeSet<AnnoyPair> pairs = new TreeSet<>();

            for(CheapShip s : Map.staticMyShips){

                Tile previousGoal = null;
                int previousId = Map.myIndexOfIdsLastTurn[s.id];
                if(previousId >= 0){
                    if(Plan.annoyGoalsPreviousTurn.length > previousId){
                        previousGoal =Plan.annoyGoalsPreviousTurn[previousId];

                    }
                }

                if(s.halite < 150 || (previousGoal != null  && s.halite < 300) || (!oneICanBeat && s.halite<250) || (MyBot.turnsLeft < 40 &&  Map.tiles[s.x][s.y].turnsFromEnemyDropoff < 3 && Map.tiles[s.x][s.y].turnsFromDropoff > 9)){

                    for(Tile t : relevantAnnoySpots) {
                        int dist = t.DistManhattan(s.x, s.y);
                        float score = -HandwavyWeights.AnnoyFlatTileDesire;// -50f;
                        score -= t.DistManhattan(s.x, s.y) * 2;

                        if (t.annoyEmergency && dist <= 1) {
                            score += 100f;
                        }
                        score += t.annoyCount * 4f;


                        if (t.annoyCount == 0) {
                            score -= 3f;
                        }

                        if (!oneICanBeat && t.annoyPlayerId == almostCertainLossKillPlayer) {
                            score += 100f;
                        }

                        score += Math.min(8, MyBot.me.shipCount - MyBot.players[t.annoyPlayerId].shipCount) * 2;


                        if (dist == 0 && t.annoyCount > 0) {
                            score += 5f;
                        }
                        if (previousGoal != null && t.DistManhattan(previousGoal) <= 3) {
                            score += 20f;
                        }

                        score -= s.halite * 0.02f;
                        score += Map.tiles[s.x][s.y].turnsFromDropoff * 0.2f;

                        if (MyBot.playerCount == 4) {
                            score -= 5f;
                        }
                        if (t.inControlZone) {
                            score += 15f;
                        }
                        if (t.enemyTerrain < 0) {
                            score += 13f;
                            score -= t.enemyTerrain;
                        }
                        score -= Math.max(t.enemyTerrain, 8) * 0.2;

                        score += t.annoyHaliteNear * 0.001f;
                        if (score > 0 || (MyBot.turnsLeft < 40 && score > -20) && dist < MyBot.turnsLeft) {
                            pairs.add(new AnnoyPair(s, t, score));
                        }
                    }
                }
            }

            int max = 10;
            if(MyBot.turnsLeft < 30 || !oneICanBeat){
                max = 30;
            }

            int counter = 0;
            for(AnnoyPair a : pairs){
                if(!a.tile.alreadySetAsAnnoyGoal && annoyTargets[Map.myIndexOfIds[a.ship.id]] == null){
                    if(counter++ >= max)break;
                    a.tile.alreadySetAsAnnoyGoal = true;
                    annoyTargets[Map.myIndexOfIds[a.ship.id]] = a.tile;

//                    Log.log("Set annoy target: " + a.ship + " -> " + a.tile, Log.LogType.MAIN);
                }
            }
        }


        return annoyTargets;
    }
}
