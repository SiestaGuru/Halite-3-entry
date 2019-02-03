import hlt.Constants;
import hlt.Game;
import hlt.Player;

import java.lang.reflect.Field;

//This class writes important game statistics and the names+values of all my magic values to a file to be used in statistical analyses
//See HandwavyWeights for more info

public class AnalysisOutput {

    public static void GenerateOutput() throws Throwable{
        try {
            IndepthShipStats.Analyze();
        } catch (Exception ex2) {
            Log.exception(ex2);
        }
        double rank = 1;
        int totalEnemyScore = 0;
        double totalEnemyScorePerPlayer = 0;
        double totalEnemyShipsBuiltPerPlayer = 0;
        double totalEnemyGatheredPerPlayer = 0;
        double totalEnemyDroppedPerPlayer = 0;
        if (Game.players.size() == 4) {
            rank = 0.75;
            for (Player p : Game.players) {
                if (p.halite > Game.me.halite) {
                    rank += 0.5;
                }

                if (!p.isMe) {
                    totalEnemyScore += p.halite;
                }
            }
            //With 4 players:   1 = 0.75,  2 = 1.25 3 = 1.75 4 = 2.25   gives a better idea how valuable ranks are
        } else {
            for (Player p : Game.players) {
                if (p.halite > Game.me.halite) {
                    rank++;
                }
                if (!p.isMe) {
                    totalEnemyScore += p.halite;
                }
            }
        }

        Log.log("Rank: " + rank + " score: " + Game.me.halite + " enemy score: " + totalEnemyScore / (MyBot.playerCount - 1), Log.LogType.MAIN);

        if (MyBot.DO_POST_ANALYSIS) {
            Log.log("DOING POST ANALYSIS", Log.LogType.MAIN);
            for (Competitor c : MyBot.players) {
                if (!c.isMe) {
                    totalEnemyShipsBuiltPerPlayer += c.shipsBuilt;
                    totalEnemyGatheredPerPlayer += c.totalGathered;
                    totalEnemyDroppedPerPlayer += c.totalDropped;
                }
            }
            totalEnemyShipsBuiltPerPlayer /= (double) (MyBot.playerCount - 1);
            totalEnemyScorePerPlayer = totalEnemyScore / (double) (MyBot.playerCount - 1);
            totalEnemyGatheredPerPlayer /= (double) (MyBot.playerCount - 1);
            totalEnemyDroppedPerPlayer /= (double) (MyBot.playerCount - 1);

            Analyze("format", MyBot.versionName);
            Analyze("version", MyBot.analysisFormat);

            //Important stats
            Analyze("Rank", rank);
            Analyze("Players", Game.players.size());
            Analyze("MapSize", Map.width);
            Analyze("Turns", Constants.MAX_TURNS);
            Analyze("Points", Game.me.halite);
            Analyze("StartTimeMs", MyBot.startTurn);
            Analyze("TimeError", Plan.timeErrors);


            //Settings
            Analyze("DIE_TURN", MyBot.DIE_TURN);

            Analyze("SPAWN_LIMIT", MyBot.SPAWN_LIMIT);
            Analyze("DROPOFF_LIMIT", MyBot.DROPOFF_LIMIT);

            //TODO: move this to analysis tool
            if (MyBot.DIE_TURN >= 0 || MyBot.TotalShipsBuilt < 12) {
                Analyze("BADGAME", "1");
            } else {
                Analyze("BADGAME", "0");
            }


            if (MyBot.THINK_QUICKLY) {
                Analyze("THINK_QUICKLY", "1");
            } else {
                Analyze("THINK_QUICKLY", "0");
            }
            if (MyBot.RANDOMIZE_HANDWAVY) {
                Analyze("RANDOMIZE_HANDWAVY", "1");
            } else {
                Analyze("RANDOMIZE_HANDWAVY", "0");
            }

            if (MyBot.EXPERIMENTAL_MODE) {
                Analyze("EXPERIMENTAL_MODE", "1");
            } else {
                Analyze("EXPERIMENTAL_MODE", "0");
            }

            if (HandwavyWeights.PLAN_STYLE == Plan.STYLE_MINIMAL_3 || HandwavyWeights.PLAN_STYLE == Plan.STYLE_MINIMAL_4 || HandwavyWeights.PLAN_STYLE == Plan.STYLE_MINIMAL_5) {
                Analyze("MINIMAL_CALCULATIONS", "1");
            } else {
                Analyze("MINIMAL_CALCULATIONS", "0");
            }


            Analyze("STYLE_CHOICE", MyBot.PLAN_STYLE_CHOICE);
            Analyze("RANDOMIZE_AMOUNT", MyBot.RANDOMIZE_AMOUNT);
            Analyze("GAMETYPE", MyBot.GAMETYPE);
            Analyze("RngSeed", MyBot.rngSeed);

            Analyze("ShipTurns", MyBot.botTurnsAlive);
            Analyze("CollectedPerShipPerTurn", ((float) MyBot.totalMoneyCollected) / ((float) MyBot.botTurnsAlive));
            Analyze("CollectedPerShipPerTurnReal", ((float) MyBot.me.totalGathered) / ((float) MyBot.botTurnsAlive));
            Analyze("PointsPerShipPerTurn", ((float) Game.me.halite) / ((float) MyBot.botTurnsAlive));

            Analyze("HaliterPerShipPerTurn", MyBot.myActualHps);
            Analyze("PointsPerTurn", ((float) Game.me.halite) / ((float) Constants.MAX_TURNS));
            Analyze("TotalSpent", MyBot.TotalSpent);
            Analyze("TotalShipsBuilt", MyBot.TotalShipsBuilt);
            Analyze("TotalShipsLost", MyBot.TotalShipsLost);
            Analyze("MapStartHalite", MyBot.MapStartHalite);
            Analyze("myShipCount", MyBot.myShipCount);
            Analyze("totalCollected",MyBot.totalMoneyCollected);
            Analyze("Dropoffs", Map.myDropoffs.size());
            Analyze("MapHaliteLeft", Map.currentMap.haliteSum());
            Analyze("ShipHaliteLeft", Map.currentMap.shipHaliteSum());
            Analyze("EShips", Map.currentMap.enemyShipsCount);
            Analyze("ShipsOverIncome", ((float) MyBot.TotalShipsBuilt) / ((float) MyBot.totalMoneyCollected));
            Analyze("DropsOverIncome", ((float) Map.myDropoffs.size()) / ((float) MyBot.totalMoneyCollected));
            Analyze("Turn", MyBot.turn); //not always the same as game length (if we crash)
            Analyze("PercentGameCompleted", ((double) MyBot.turn) / ((double) Constants.MAX_TURNS));
            Analyze("TotalEnemyScore", totalEnemyScore);
            Analyze("EnemyShipsbuiltPerPlayer", totalEnemyShipsBuiltPerPlayer);
            Analyze("TotalEnemyScorePerPlayer", totalEnemyScorePerPlayer);
            Analyze("TotalEnemyGatheredPerPlayer", totalEnemyGatheredPerPlayer);
            Analyze("TotalEnemyDroppedPerPlayer", totalEnemyDroppedPerPlayer);

            Analyze("LastShipSpawn", IndepthShipStats.lastSpawnedShipTurn);
            Analyze("ExpectedPointsPerShipAfterLastSpawn", (float) (Game.me.halite - IndepthShipStats.lastSpawnedShipPoints) / ((float) IndepthShipStats.lastSpawnedShipShipcount));


            Analyze("Inspire", MyBot.me.totalInspire);
            Analyze("Mined", MyBot.me.totalMined);

            Analyze("InspirePerShipTurn", ((float) MyBot.me.totalInspire) / ((float) MyBot.botTurnsAlive));
            Analyze("MinedPerShipTurn", ((float) MyBot.me.totalMined) / ((float) MyBot.botTurnsAlive));


            Analyze("TurnInEvents", IndepthShipStats.TurnInEvents);
            Analyze("AverageReturnTime", IndepthShipStats.AverageReturnTime);
            Analyze("TurnInsPerShip", IndepthShipStats.TurnInsPerShip);
            Analyze("TurnInsPerShipTurn", IndepthShipStats.TurnInsPerShipTurn);
            Analyze("AverageReturnHalite", IndepthShipStats.AverageReturnHalite);
            Analyze("AverageEffectiveness", IndepthShipStats.AverageEffectiveness);

            Analyze("AverageLifeSpan", IndepthShipStats.AverageLifeSpan);
            Analyze("ProportionAlive", IndepthShipStats.ProportionAlive);
            Analyze("shipsLivedTillEnd", IndepthShipStats.shipsLivedTillEnd);
            Analyze("shipsDiedBeforeEnd", IndepthShipStats.shipsDiedBeforeEnd);
            Analyze("shipsLived", IndepthShipStats.shipsLived);

            Analyze("StandStill", IndepthShipStats.StandStill);
            Analyze("Moved", IndepthShipStats.Moved);
            Analyze("Actions", IndepthShipStats.Actions);
            Analyze("ForcedStandStill", IndepthShipStats.ForcedStandStill);
            Analyze("GatherLarge", IndepthShipStats.GatherLarge);
            Analyze("GatherSmall", IndepthShipStats.GatherSmall);
            Analyze("MoveEarly", IndepthShipStats.MoveEarly);
            Analyze("MoveMedium", IndepthShipStats.MoveMedium);
            Analyze("MoveLate", IndepthShipStats.MoveLate);
            Analyze("ProportionStandStill", IndepthShipStats.ProportionStandStill);
            Analyze("ProportionMoved", IndepthShipStats.ProportionMoved);
            Analyze("ProportionForcedStandStill", IndepthShipStats.ProportionForcedStandStill);
            Analyze("ProportionGatherLarge", IndepthShipStats.ProportionGatherLarge);
            Analyze("ProportionGatherSmall", IndepthShipStats.ProportionGatherSmall);
            Analyze("ProportionMoveEarly", IndepthShipStats.ProportionMoveEarly);
            Analyze("ProportionMoveMedium", IndepthShipStats.ProportionMoveMedium);
            Analyze("ProportionMoveLate", IndepthShipStats.ProportionMoveLate);

            Analyze("AverageHaliteStandOn", IndepthShipStats.AverageHaliteStandOn);
            Analyze("AverageHaliteMoveFrom", IndepthShipStats.AverageHaliteMoveFrom);
            Analyze("AverageHaliteStandOnWhileInspired", IndepthShipStats.AverageHaliteStandOnWhileInspired);
            Analyze("ProportionStandstillInspired", IndepthShipStats.ProportionStandstillInspired);
            Analyze("TotalHaliteStoodOn", IndepthShipStats.TotalHaliteStoodOn);
            Analyze("TotalHaliteMovedFrom", IndepthShipStats.TotalHaliteMovedFrom);
            Analyze("TotalHaliteStoodOnWhileInspired", IndepthShipStats.TotalHaliteStoodOnWhileInspired);
            Analyze("TurnsInspiredOnStandstill", IndepthShipStats.TurnsInspiredOnStandstill);


            Analyze("PointsT50", MyBot.PointsT50);
            Analyze("PointsT100", MyBot.PointsT100);
            Analyze("PointsT150", MyBot.PointsT150);
            Analyze("PointsT200", MyBot.PointsT200);
            Analyze("PointsT250",MyBot. PointsT250);
            Analyze("PointsT300", MyBot.PointsT300);
            Analyze("PointsT350",MyBot. PointsT350);
            Analyze("PointsT400", MyBot.PointsT400);
            Analyze("PointsT450", MyBot.PointsT450);
            Analyze("ShipsT50", MyBot.ShipsT50);
            Analyze("ShipsT100", MyBot.ShipsT100);
            Analyze("ShipsT150", MyBot.ShipsT150);
            Analyze("ShipsT200", MyBot.ShipsT200);
            Analyze("ShipsT250", MyBot.ShipsT250);
            Analyze("ShipsT300", MyBot.ShipsT300);
            Analyze("ShipsT350", MyBot.ShipsT350);
            Analyze("ShipsT400", MyBot.ShipsT400);
            Analyze("ShipsT450", MyBot.ShipsT450);
            Analyze("DropoffsT50", MyBot.DropoffsT50);
            Analyze("DropoffsT100", MyBot.DropoffsT100);
            Analyze("DropoffsT150", MyBot.DropoffsT150);
            Analyze("DropoffsT200", MyBot.DropoffsT200);
            Analyze("DropoffsT250", MyBot.DropoffsT250);
            Analyze("DropoffsT300", MyBot.DropoffsT300);
            Analyze("DropoffsT350", MyBot.DropoffsT350);
            Analyze("DropoffsT400", MyBot.DropoffsT400);
            Analyze("DropoffsT450", MyBot.DropoffsT450);

            Analyze("AvgEffectivenessT50", IndepthShipStats.AvgEffectivenesst50);
            Analyze("AvgEffectivenessT100", IndepthShipStats.AvgEffectivenesst100);
            Analyze("AvgEffectivenessT150", IndepthShipStats.AvgEffectivenesst150);
            Analyze("AvgEffectivenessT200", IndepthShipStats.AvgEffectivenesst200);
            Analyze("AvgEffectivenessT250", IndepthShipStats.AvgEffectivenesst250);
            Analyze("AvgEffectivenessT300", IndepthShipStats.AvgEffectivenesst300);
            Analyze("AvgEffectivenessT350", IndepthShipStats.AvgEffectivenesst350);
            Analyze("AvgEffectivenessT400", IndepthShipStats.AvgEffectivenesst400);
            Analyze("AvgEffectivenessT450", IndepthShipStats.AvgEffectivenesst450);

            Analyze("GatheredT50", IndepthShipStats.Gathered50);
            Analyze("GatheredT100", IndepthShipStats.Gathered100);
            Analyze("GatheredT150", IndepthShipStats.Gathered150);
            Analyze("GatheredT200", IndepthShipStats.Gathered200);
            Analyze("GatheredT250", IndepthShipStats.Gathered250);
            Analyze("GatheredT300", IndepthShipStats.Gathered300);
            Analyze("GatheredT350", IndepthShipStats.Gathered350);
            Analyze("GatheredT400", IndepthShipStats.Gathered400);
            Analyze("GatheredT450", IndepthShipStats.Gathered450);

            //The variables we want to find best vals for, load using reflection from the handwavy class:
            try {
                Field[] fields = HandwavyWeights.class.getDeclaredFields();
                for (Field f : fields) {

                    if (MyBot.EXPERIMENTAL_MODE) {
                        if (!f.getName().contains("Experimental")) {
                            continue;
                        }
                    }

                    if (f.getType().equals(int.class)) {
                        Analyze(f.getName(), f.getInt(null));
                    } else if (f.getType().equals(double.class)) {
                        Analyze(f.getName(), f.getDouble(null));
                    } else if (f.getType().equals(float.class)) {
                        Analyze(f.getName(), f.getFloat(null));
                    } else if (f.getType().equals(String.class)) {
                        Analyze(f.getName(), (String) f.get(null));
                    } else if (f.getType().equals(float[].class)) {
                        Analyze(f.getName(), (float[]) f.get(null));
                    }
                }
            } catch (Exception exe) {
                Log.exception(exe);
            }

        } else {
            Log.log("NO ANALYSIS", Log.LogType.MAIN);
        }

        if (MyBot.AUTO_LEARNING_SAVE && MyBot.RANDOMIZE_HANDWAVY) {
            float amount;
            //With 4 players:   1 = 0.75,  2 = 1.25 3 = 1.75 4 = 2.25   gives a better idea how valuable ranks are
            if (rank == 0.75) {
                amount = 1.0f;
            } else if (rank == 1) {
                amount = 0.8f;
            } else if (rank == 1.25) {
                amount = 0.3f;
            } else if (rank == 1.75) {
                amount = -0.3f;
            } else if (rank == 2) {
                amount = -0.8f;
            } else if (rank == 2.25) {
                amount = -1.0f;
            } else {
                amount = 0f;
                Log.log("Weird rank?!?!?!", Log.LogType.MAIN);
            }
            HandwavyWeights.AutoTrainingFinish(amount);

        }
    }

    public static void Analyze(String label, float value) {
        Log.log(label + ":" + value, Log.LogType.ANALYSIS);
        if (MyBot.DO_GAME_OUTPUT) {
            Log.log(label + ":" + value, Log.LogType.OUTPUT);
        }
    }

    public static void Analyze(String label, int value) {
        Log.log(label + ":" + value, Log.LogType.ANALYSIS);
        if (MyBot.DO_GAME_OUTPUT) {
            Log.log(label + ":" + value, Log.LogType.OUTPUT);
        }
    }

    public static void Analyze(String label, double value) {
        Log.log(label + ":" + value, Log.LogType.ANALYSIS);
        if (MyBot.DO_GAME_OUTPUT) {
            Log.log(label + ":" + value, Log.LogType.OUTPUT);
        }
    }

    public static void Analyze(String label, String value) {
        Log.log(label + ":" + value, Log.LogType.ANALYSIS);
        if (MyBot.DO_GAME_OUTPUT) {
            Log.log(label + ":" + value, Log.LogType.OUTPUT);
        }
    }


    public static void Analyze(String label, float[] value) {

        int type;
        if (value.length == 10) {
            type = MyBot.GAMETYPE;
        } else if (value.length == 5) {
            type = MyBot.GAMETYPE_SIZE;
        } else if (value.length == 4) {
            type = MyBot.GAMETYPE_DENSITY;
        } else {
            type = MyBot.GAMETYPE_PLAYERS;
        }

        Log.log(label + "_" + type + ":" + value[type], Log.LogType.ANALYSIS);
        if (MyBot.DO_GAME_OUTPUT) {
            Log.log(label + "_" + type + ":" + value[type], Log.LogType.OUTPUT);
        }
    }

}
