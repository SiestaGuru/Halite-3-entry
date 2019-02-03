import hlt.Constants;
import hlt.Game;

import java.util.ArrayDeque;

//Some extra stats that are collected to help the statistical analysis of games.
//These stats (mostly?) aren't used in-game

public class IndepthShipStats {

    public static int[][] haliteOnTurn;
    public static int[] born;
    public static int[] lastAlive;
    public static ArrayDeque<int[]>[]  shipTurnInEvents;


    public static int[] totalHaliteTurnedInOnTurn;

    public static float AverageReturnHalite;
    public static float AverageReturnTime;
    public static float AverageLifeSpan;
    public static float AverageEffectiveness;
    public static float ProportionAlive;
    public static int TurnInEvents;
    public static int shipsLivedTillEnd;
    public static int shipsDiedBeforeEnd;
    public static int shipsLived;
    public static float TurnInsPerShipTurn;
    public static float TurnInsPerShip;


    public static float ProportionStandStill;
    public static float ProportionMoved;
    public static int StandStill;
    public static int Moved;
    public static int Actions;
    public static int TotalHaliteStoodOn;
    public static int TotalHaliteMovedFrom;
    public static int TotalHaliteStoodOnWhileInspired;
    public static int TurnsInspiredOnStandstill;

    public static int ForcedStandStill;
    public static int GatherLarge;
    public static int GatherSmall;
    public static int MoveEarly; //With low halite, likely trying to find halite long dist
    public static int MoveMedium; //With medium halite, likely trying to gather
    public static int MoveLate; //With high halite, likely moving back to base

    public static int lastSpawnedShipTurn;
    public static int lastSpawnedShipPoints;
    public static int lastSpawnedShipShipcount;


    public static float ProportionForcedStandStill;
    public static float ProportionGatherLarge;
    public static float ProportionGatherSmall;
    public static float ProportionMoveEarly;
    public static float ProportionMoveMedium;
    public static float ProportionMoveLate;

    public static float AverageHaliteStandOn;
    public static float AverageHaliteMoveFrom;
    public static float AverageHaliteStandOnWhileInspired;
    public static float ProportionStandstillInspired;




    public static void Init(){
        haliteOnTurn = new int[2000][Constants.MAX_TURNS + 1];
        born = new int[2000];
        lastAlive = new int[2000];
        shipTurnInEvents = new ArrayDeque[2000];

    }



    public static void Update(){
        for(CheapShip s : Map.staticMyShips){
            if(s != null){

                if(born[s.id] == 0){
                    born[s.id] = MyBot.turn;
                    lastSpawnedShipTurn = MyBot.turn;
                    lastSpawnedShipShipcount = Map.staticMyShipCount;
                    lastSpawnedShipPoints = Game.me.halite;
                }
                lastAlive[s.id] = MyBot.turn;

                haliteOnTurn[s.id][MyBot.turn] = s.halite;

                if(MyBot.turn > 0){
                    if(s.halite == 0 && haliteOnTurn[s.id][MyBot.turn-1] > 0){

                        if(shipTurnInEvents[s.id] == null){
                            shipTurnInEvents[s.id] = new ArrayDeque<>();
                        }
                        shipTurnInEvents[s.id].add(new int[]{MyBot.turn,haliteOnTurn[s.id][MyBot.turn-1]});
                        TurnInEvents++;
                    }
                }
            }
        }

        if(Plan.lastTurnBestPlan != null) {

            for (Move m : Plan.lastTurnBestPlan.movesPerTurn[0]) {
                if(m != null) {
                    Actions++;

                    CheapShip curShip = Map.staticShipsById[m.ship.id];
                    boolean isStandstill;
                    if(curShip == null){
                        isStandstill = m.isStandStill(); //check what we planned to do if we can't see what happened due to collisions etc.
                    }
                    else{
                        isStandstill =  m.from.x == curShip.x && m.from.y == curShip.y; //check based on what actually happened if possible (especially important if loading replays)
                    }


                    if (isStandstill) {
                        StandStill++;
                        if (m.ship.halite < MyBot.moveCosts[Map.staticHaliteMapLastTurn[m.from.x][m.from.y]]) {
                            ForcedStandStill++;
                        } else if (Map.staticHaliteMapLastTurn[m.from.x][m.from.y] > 150) {
                            GatherLarge++;
                        } else {
                            GatherSmall++;
                        }
                        TotalHaliteStoodOn += Map.staticHaliteMapLastTurn[m.from.x][m.from.y];

                        if(Plan.inspireOdds[0][m.from.x][m.from.y] >= 1){ //should be last turns inspire odds
                            TotalHaliteStoodOnWhileInspired += Map.staticHaliteMapLastTurn[m.from.x][m.from.y];
                            TurnsInspiredOnStandstill++;
                        }

                    } else {
                        Moved++;

                        if (m.ship.halite > 850) {
                            MoveLate++;
                        } else if (m.ship.halite > 100) {
                            MoveMedium++;
                        } else {
                            MoveEarly++;
                        }
                        TotalHaliteMovedFrom += Map.staticHaliteMapLastTurn[m.from.x][m.from.y];
                    }
                }
            }
        }
    }

    public static void Analyze(){

        int totalLifeSpan = 0;
        int totalShipTurnInEvents = 0;
        int totalShipHaliteTurnedIn = 0;
        int shipsProductiveLifeSpan = 0;


        for(int i =0 ; i < 2000; i++){
            if(born[i] > 0){
                int lifespan = lastAlive[i] - born[i];
                totalLifeSpan += lifespan;

                if(shipTurnInEvents[i] != null) {
                    totalShipTurnInEvents += shipTurnInEvents[i].size();
                    int lastTurnIn = 0;

                    for (int[] event : shipTurnInEvents[i]) {
                        totalShipHaliteTurnedIn += event[1];
                        lastTurnIn = event[0];
                    }
                    shipsProductiveLifeSpan += lastTurnIn;
                }


                shipsLived++;
                if(lastAlive[i] > Constants.MAX_TURNS - 20){
                    shipsLivedTillEnd++;
                }else{
                    shipsDiedBeforeEnd++;
                }
            }
        }


        AverageReturnTime = ((float)shipsProductiveLifeSpan / (float)totalShipTurnInEvents);
        AverageReturnHalite = ((float)totalShipHaliteTurnedIn) / ((float)totalShipTurnInEvents);
        AverageLifeSpan = ((float)totalLifeSpan) / ((float)shipsLived);
        AverageEffectiveness = ((float)totalShipHaliteTurnedIn) / ((float)totalLifeSpan);
        ProportionAlive =  ((float)shipsLivedTillEnd) / ((float)shipsLived);

        ProportionForcedStandStill = ((float)ForcedStandStill) / ((float)Actions);
        ProportionGatherLarge = ((float)GatherLarge) / ((float)Actions);
        ProportionGatherSmall = ((float)GatherSmall) / ((float)Actions);
        ProportionMoveEarly = ((float)MoveEarly ) / ((float)Actions);
        ProportionMoveMedium = ((float)MoveMedium) / ((float)Actions);
        ProportionMoveLate = ((float)MoveLate) / ((float)Actions);

        ProportionStandStill = ((float)StandStill) / ((float)Actions);
        ProportionMoved = ((float)Moved) / ((float)Actions);


        TurnInsPerShip = ((float)totalShipTurnInEvents / (float)shipsLived);
        TurnInsPerShipTurn = ((float)totalShipTurnInEvents / (float)totalLifeSpan);


        AverageHaliteStandOn = ((float)TotalHaliteStoodOn / (float)StandStill);
        AverageHaliteMoveFrom = ((float)TotalHaliteMovedFrom / (float)Moved);
        AverageHaliteStandOnWhileInspired = ((float)TotalHaliteStoodOnWhileInspired / (float)TurnsInspiredOnStandstill);
        ProportionStandstillInspired =  ((float)TurnsInspiredOnStandstill / (float)StandStill);

//        Log.log("Analyzing ships indepth", Log.LogType.MAIN);


    }

    public static float AvgEffectivenesst50;
    public static float AvgEffectivenesst100;
    public static float AvgEffectivenesst150;
    public static float AvgEffectivenesst200;
    public static float AvgEffectivenesst250;
    public static float AvgEffectivenesst300;
    public static float AvgEffectivenesst350;
    public static float AvgEffectivenesst400;
    public static float AvgEffectivenesst450;
    public static float Gathered50;
    public static float Gathered100;
    public static float Gathered150;
    public static float Gathered200;
    public static float Gathered250;
    public static float Gathered300;
    public static float Gathered350;
    public static float Gathered400;
    public static float Gathered450;

    public static void NoteTurnStats(){
        if(MyBot.turn == 50) {
            Analyze();
            AvgEffectivenesst50 = AverageEffectiveness;
            Gathered50 = MyBot.me.totalGathered;
        } else if(MyBot.turn == 100) {
            Analyze();
            AvgEffectivenesst100 = AverageEffectiveness;
            Gathered100 = MyBot.me.totalGathered;
        } else if(MyBot.turn == 150){
            Analyze();
            AvgEffectivenesst150 = AverageEffectiveness;
            Gathered150 = MyBot.me.totalGathered;
        } else if(MyBot.turn == 200){
            Analyze();
            AvgEffectivenesst200 = AverageEffectiveness;
            Gathered200 = MyBot.me.totalGathered;
        } else if(MyBot.turn == 250){
            Analyze();
            AvgEffectivenesst250 = AverageEffectiveness;
            Gathered250 = MyBot.me.totalGathered;
        }else if(MyBot.turn == 300){
            Analyze();
            AvgEffectivenesst300 = AverageEffectiveness;
            Gathered300 = MyBot.me.totalGathered;
        }else if(MyBot.turn == 350){
            Analyze();
            AvgEffectivenesst350 = AverageEffectiveness;
            Gathered350 = MyBot.me.totalGathered;

        }else if(MyBot.turn == 400){
            Analyze();
            AvgEffectivenesst400 = AverageEffectiveness;
            Gathered400 = MyBot.me.totalGathered;

        }else if(MyBot.turn == 450){
            Analyze();
            AvgEffectivenesst450 = AverageEffectiveness;
            Gathered450 = MyBot.me.totalGathered;
        }

    }


}
