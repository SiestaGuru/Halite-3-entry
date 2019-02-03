import java.util.ArrayList;
import java.util.Collections;


//Mining algorithm replacement that didn't end up performing that well, so not used in the final version
//Works somewhat similar to the solojourney algorithm in plan, except it does it simultaneously for all ships and with a much
//simpler evaluator.

public class SimulatenousJourneys {

    public static Tile[][] Do(int searchDepth){
        Tile[][] results = new Tile[searchDepth][Map.staticMyShipCount];

        if(HandwavyWeights.ActivateSimulJourney == 0){
            return results;
        }

        int max_turns = HandwavyWeights.ExperimentalSimulMaxTurns;
        int width = HandwavyWeights.ExperimentalSimulWidth;


        Stopwatch.Start();
        ArrayList<SimulatenousMoveSet> journeys = new ArrayList<>();

        int[] startHalite = new int[Map.staticMyShipCount];
        Tile[] goals = new Tile[Map.staticMyShipCount];

        for(CheapShip s : Map.staticMyShips){
            startHalite[Map.myIndexOfIds[s.id]] = s.halite;
            goals[Map.myIndexOfIds[s.id]] = Plan.goals[s.id];

            SimulatenousMoveSet ms = new SimulatenousMoveSet();
            ms.shipIndex = Map.myIndexOfIds[s.id];
            ms.moves = new Tile[max_turns];
            ms.moves[0] = s.GetTile(); //first step won't be counted
            journeys.add(ms);


        }

        int[] stoodStillOn = new int[Map.width * Map.height];
        int maxInspire = Plan.inspireMultiplier.length - 1;
        int maxEOdds = Plan.eLocOdds.length;

        for(int turn =1; turn < max_turns; turn++){
            ArrayList<SimulatenousMoveSet> upcomingJourneys = new ArrayList<>();
            int previousTurn = turn - 1;

            for(SimulatenousMoveSet ms : journeys){
                Tile tile =ms.moves[previousTurn];

                for(Tile t : tile.neighboursAndSelf){
                    //TODO: exclude moving back and forth
                    SimulatenousMoveSet msNew = new SimulatenousMoveSet();
                    msNew.shipIndex = ms.shipIndex;
                    msNew.moves = ms.moves.clone();
                    msNew.moves[turn] = t;
                    upcomingJourneys.add(msNew);

                    if(goals[ms.shipIndex] != null) {
                        if(goals[ms.shipIndex].turnsFromDropoff == 0){
                            msNew.minGoalDist = Math.min(t.turnsFromDropoff,Math.min(msNew.minGoalDist, t.DistManhattan(goals[ms.shipIndex])));
                        }else {
                            msNew.minGoalDist = Math.min(msNew.minGoalDist, t.DistManhattan(goals[ms.shipIndex]));
                        }
                        if(msNew.minGoalDist == 0 && msNew.reachedGoal > turn){
                            msNew.reachedGoal = turn;
                        }
                    }
                }
            }


            for(SimulatenousMoveSet ms : upcomingJourneys){
                //Essentially a stripped down version of the main algos evaluator
                int halite = startHalite[ms.shipIndex];
                int turnInHalite = 0;
                float score = -halite; //Final halite will be rewarded. Reducing score here by starting halite to not prioritize ships that are already full

                for(int i=1; i <= turn; i++){
                    int toX = ms.moves[i].x;
                    int toY = ms.moves[i].y;

                    if(ms.moves[i] == ms.moves[i-1]){
                        if(halite < 1000) { //shouldn't be increasing stoodstillcount. it'll think we'll burn less
                            halite = Math.min(1000, halite + (int) (MyBot.gainIfStandFor1More[ms.moves[i].haliteStartTurnCappedTo5000][stoodStillOn[ms.moves[i].tileIndex]++] * Plan.inspireMultiplier[Math.min(turn - 1, maxInspire)][toX][toY])); //TODO: optimize inspire multiplier map using tileindex
                        }
                    }else{
                        halite -= MyBot.burnIfMoveAfterX[ms.moves[i-1].haliteStartTurnCappedTo5000][stoodStillOn[ms.moves[i-1].tileIndex]];
                    }
                    if(halite < 0){
                        ms.bad = true;
                        ms.score = -100000f;
                        break;
                    }
                    if(ms.moves[i].turnsFromDropoff == 0){
                        turnInHalite += halite;
                        halite = 0;
                    }

                    if(turn <= maxEOdds) {
                        score -= Plan.eLocOdds[turn-1][toX][toY] * HandwavyWeights.ExperimentalEOdds;
                    }
                }

                score += halite + turnInHalite * HandwavyWeights.ExperimentalSimulTurnInHalite;
                score -= ms.minGoalDist * HandwavyWeights.ExperimentalSimulGoal;
                score -= ms.reachedGoal * HandwavyWeights.ExperimentalSimulReachedGoal;

                ms.score = score;

                for(int i=1; i <= turn; i++){
                    stoodStillOn[ms.moves[i].tileIndex] = 0; //Erase standStillInfo
                }
            }
            Collections.sort(upcomingJourneys);
            boolean[] doneShip = new boolean[Map.staticMyShipCount];
            int[] visitedTiles = new int[Map.width * Map.height];

            for(SimulatenousMoveSet ms : upcomingJourneys){
                if(!ms.bad) {
                    //second evaluation step
                    for (int i = 1; i <= turn; i++) {
                        ms.score -= visitedTiles[ms.moves[i].tileIndex] * HandwavyWeights.ExperimentalSimulVisited;
                    }

                    if (!doneShip[ms.shipIndex]) {
                        for (int i = 1; i <= turn; i++) {
                            if (ms.moves[i].turnsFromDropoff > 1) {
                                visitedTiles[ms.moves[i].tileIndex]++;
                            }
                        }
                        doneShip[ms.shipIndex] = true;
                    }
                }
            }

            Collections.sort(upcomingJourneys);
            int[] shipPathsUsed = new int[Map.staticMyShipCount];

            journeys.clear();

            if(turn == max_turns -1){
                for(Tile t : Map.tileList){
                    t.reservedSimulAlgo = false;
                }
                for(SimulatenousMoveSet ms : upcomingJourneys){
                    if(!ms.bad && !ms.moves[1].reservedSimulAlgo) {
                        if (shipPathsUsed[ms.shipIndex]++ < 1) {
                            journeys.add(ms);
                            ms.moves[1].reservedSimulAlgo = true;
                        }
                    }
                }
            }else{
                for(SimulatenousMoveSet ms : upcomingJourneys){
                    if(!ms.bad && shipPathsUsed[ms.shipIndex]++ < width){
                        journeys.add(ms);
                    }
                }
            }
        }

        for(SimulatenousMoveSet ms : journeys){
            for(int i=0; i < searchDepth; i++){
                results[i][ms.shipIndex] = ms.moves[i+1];
            }
        }
        Stopwatch.Stop("Simul journey");

        if(MyBot.DO_GAME_OUTPUT){
            String s = "plan:" + 0 +  ";" + (max_turns-1) + ";" + 0 + ":";
            for(int i=1; i < max_turns; i++){
                for(SimulatenousMoveSet ms : journeys){
                    s +=  ms.moves[i-1].x + "," + ms.moves[i-1].y + "," + ms.moves[i].x + "," + ms.moves[i].y + "," +  Map.staticMyShips.get(ms.shipIndex).id + "/";
                }
                s += ";";
            }
            GameOutput.info.add(s);

        }

        return results;

    }



}
