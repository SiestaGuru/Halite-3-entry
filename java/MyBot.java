// This Java API uses camelCase instead of the snake_case as documented in the API docs.
//     Otherwise the names of methods are consistent.

import hlt.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;



/*The center class of the bot
The main method in this class does several things:
-Control the initiation process
-Do the main control flow per turn
-Decide on ship spawning and dropoff building
-Collect the moves generated, sending them to the engine


Other than this file, the most important/interesting files are:
- Map:  For data, data parsing and simulations
- Plan: Comes up with and evaluates move plans. The core of my movement system
- SideAlgorithms: Miscellaneous algorithms, used mostly to impact move decisions
- Goals: Sets longer distance goals for ships
- EnemyPrediction: Some different approaches to guess what the enemy is going to do
- Annoyers: For dropoff blocking and cutting off enemy paths

- FuturePathing: An algorithm that tries to obtain optimal/near optimal long term move plans. Nearly a full-fledged bot, would probably rank top 50-150ish on its own.  Used as a weight
- WeirdAlgo: An algorithm that comes up with move suggestions. Basically a full-fledged bot, probably top 10-50 on its own. Used as a weight
- SimultaneousJourneys:   An unfinished algorithm that comes up with move suggestions. It sort of works as a bot, but a really bad one. Currently not used
- MirrorMode: A system to mimic the enemy moves a turn later until it desyncs. Didn't end up being very good

*/


public class MyBot {

    //These parameters control a lot of important aspects of the workflow
    public static final boolean FINALS_RELEASE = true;

    public static final boolean SERVER_RELEASE = true; //If true, sets several of the other parameters in this list. Used for uploaded versions (turns logging off etc.)
    public static boolean FAST_BATCH_USING_NOTIMEOUT = false; //If true, sets several of the other parameters in this list. Used for batch runs to collect data
    public static boolean EXPERIMENTAL_MODE = false; //Frequently used during development to try out some changes I wasn't confident in yet.
    public static int MS_BUFFER = 450;

    public static boolean RELEASE = true; //If true, sets several of the other parameters in this list
    public static boolean DO_GAME_OUTPUT = false; //If true, writes a lot of game data to a file for my custom game viewer. These files are huge, so this is usually turned off
    public static boolean RANDOMIZE_HANDWAVY = false; //Randomizes magic values
    public static boolean DO_POST_ANALYSIS = true; //Write game stats and paramater values to file, to be used by my statistics tool
    public static boolean CHECK_INSPIRE_ACCURACY = false;
    public static boolean CHECK_EODDS_ACCURACY = false;

    public static boolean ALLOW_LOGGING = true; //Remember: if false, also stops analysis and game output
    public static boolean THINK_QUICKLY = false;
    public static int PLAN_STYLE_CHOICE = Plan.STYLE_MINIMAL_4;//The plan style determines the algorithm(s) used for game state finding
    public static boolean DO_TESTS = false; //Tests break the game after finishing right now
    public static boolean ALLOW_KILLFILE = false;


    public static float RANDOMIZE_AMOUNT = 0.1f;//0.08f;
    public static float SMALL_RANDOMIZE_AMOUNT = 0.9f;
    public static float SMALL_RANDOMIZE_FACTOR = 0.1f;

    public static boolean DETERMINISTIC_TIME_INDEPENDENT = false;

    public static boolean SINGLE_PLAYER = false;
    public static int SPAWN_LIMIT = -1;//2;
    public static int DROPOFF_LIMIT = -1;
    public static int DIE_TURN = -1; //only worth setting if allow_killfile is off
    public static boolean SIMPLE_SPAWN = false;
    public static boolean MIRROR_MODE = false;

    public static boolean AUTO_LEARNING_LOAD = false;
    public static boolean AUTO_LEARNING_SAVE = false;
    public static float AUTO_LEARNING_SPEED = 1.4f;

    public static int MAX_SIMULATIONS_ALLOWED = 120000;

    public static boolean LIGHT_MODE = false; //will be set


//    public static boolean MONSTER_CALCULATION = false; //will timeout without --no-timeout
//    public static boolean MINIMAL_CALCULATIONS = false; //avoid most of the algorithm, just do something real quick for testing
//    public static boolean EXTREMELY_MINIMAL_CALCULATIONS = true; //just do the move greedy algo, dont look ahead at all
//    public static boolean MEDIUM_CALCULATIONS = false; //avoid most of the algorithm, just do something real quick for testing
//


    public static final short[] moveCosts = new short[40000]; //move cost per amount of halite. cheaper to precalc. just assuming for now 40k wont ever be reached
    public static final short[] moveCostsSafe = new short[40000]; //safe means, everything shifted by 1000 to support the rare negative halite val

    public static final short[] standCollect = new short[40000]; //collect amount if you stand still
    public static final short[] standCollectSafe = new short[40000];

    public static int SizeAfterXTurnArrays = 40;
    public static int[][] burnIfMoveAfterX = new int[5001][SizeAfterXTurnArrays];
    public static int[][] collectIfStandForX = new int[5001][SizeAfterXTurnArrays];
    public static int[][] gainIfStandFor1More = new int[5001][SizeAfterXTurnArrays];


    public static final int[] gatherValues = new int[40000]; //collect amount if you stand still for several turns

    public static final float[] haliteLogCurve = new float[40000];
    public static final float[] haliteExponentialCurve = new float[40000];
    public static final float[] haliteSqrtCurve = new float[40000];

    public static final float[] shipReturnHaliteMultiplier = new float[5000]; //performance precalc for in eval
    public static final float[] meaningFulHaliteMultiplier = new float[5000]; //performance precalc for in eval

    public static final float[] longLureImportance = new float[5000]; //performance precalc for in eval
    public static final float[] medLureImportance = new float[5000]; //performance precalc for in eval
    public static final float[] lureImportance = new float[5000]; //performance precalc for in eval
    public static final float[] shipHaliteImportance = new float[5000]; //performance precalc for in eval

    public static final float[] evalHalite = new float[70000];
    public static final float[][] evalStandstill = new float[1001][3000]; //performance precalc for in eval
    public static final float[] EShipHaliteWeights = new float[1001];


    public static final String versionName = "v4.5.10 (final)";
    public static final String analysisFormat = "0.1.2";


    public static Random rand;

    public static long startTurn = 0;
    public static int turn = 0;
    public static int turnsLeft = 0;
    public static float proportionGame = 0;
    public static int botTurnsAlive = 0;
    public static int totalMoneyCollected = 0;
    public static int moneyCollectedLastTurn = 0;
    public static int moneyCollectedLastTurn2 = 0;
    public static int moneyCollectedLastTurn3 = 0;
    public static int moneyCollectedLastTurn4 = 0;
    public static int moneyCollectedLastTurn5 = 0;


    public static float TotalSpent = 0;
    public static float TotalBurnt = 0;
    public static float TotalShipsBuilt = 0;
    public static float TotalShipsLost = 0; //excluding mass suicide at the end
    public static int MapStartHalite = 0;
    public static float MapStartDensity = 0;
    public static int killfilecheck = 0;

    public static int expectedHaliteForDropoff;

    public static final int TIME_ALLOWED = 2000;

    public static Competitor[] players;
    public static Competitor me;
    public static Competitor enemy1;
    public static Competitor enemy2;
    public static Competitor enemy3;
    public static int myId;
    public static int playerCount;


    public static int GAMETYPE = -1;
    public static int GAMETYPE_PLAYERS = -1;
    public static int GAMETYPE_SIZE = -1;
    public static int GAMETYPE_DENSITY = -1;

    public static int PointsT50,PointsT100,PointsT150,PointsT200,PointsT250,PointsT300,PointsT350,PointsT400,PointsT450;
    public static int ShipsT50,ShipsT100,ShipsT150,ShipsT200,ShipsT250,ShipsT300,ShipsT350,ShipsT400,ShipsT450;
    public static int DropoffsT50,DropoffsT100,DropoffsT150,DropoffsT200,DropoffsT250,DropoffsT300,DropoffsT350,DropoffsT400,DropoffsT450;
    public static double myActualHps,estimateEnemiesEatAtCurRate,estimateIllEatAtCurRate,myUsableHps,totalCollected;
    public static int myShipCount;



    public static boolean DoIWantToBuildShip;
    public static long rngSeed;


    public static void main(final String[] args) {
        try {
            if (SERVER_RELEASE) {
                RELEASE = true;
                FAST_BATCH_USING_NOTIMEOUT = false;
            }
            if (RELEASE) {
                ALLOW_LOGGING = false;
                THINK_QUICKLY = false;
                DIE_TURN = -1;
                DO_POST_ANALYSIS = false;
                RANDOMIZE_HANDWAVY = false;
                DO_TESTS = false;
                ALLOW_KILLFILE = false;
                PLAN_STYLE_CHOICE = Plan.STYLE_DETERMINE_BASED_ON_SIZE;
                DO_GAME_OUTPUT = false;
                DETERMINISTIC_TIME_INDEPENDENT = false;
                MAX_SIMULATIONS_ALLOWED = 80000;
                AUTO_LEARNING_LOAD = false;
                AUTO_LEARNING_SAVE = false;
                EXPERIMENTAL_MODE = false;
                MS_BUFFER = 320;//340;
                SPAWN_LIMIT = -1;
                DROPOFF_LIMIT = -1;
                SINGLE_PLAYER = false;
                SIMPLE_SPAWN = false;
                MIRROR_MODE = false;
                CHECK_INSPIRE_ACCURACY = false;
                CHECK_EODDS_ACCURACY = false;
            } else if (FAST_BATCH_USING_NOTIMEOUT) {
                ALLOW_LOGGING = true;
                RANDOMIZE_HANDWAVY = true;
                DO_POST_ANALYSIS = true;
                AUTO_LEARNING_LOAD = false;
                AUTO_LEARNING_SAVE = false;
                DO_GAME_OUTPUT = false;
                //EXPERIMENTAL_MODE = false;
                MS_BUFFER = 1000;
                PLAN_STYLE_CHOICE = Plan.STYLE_MINIMAL_3;
                for (Log.LogType l : Log.LogType.values()) {
                    l.active = false;
                }
                Log.LogType.ANALYSIS.active = true;
                Log.LogType.EXCEPTIONS.active = true;
                // SPAWN_LIMIT = -1;
                DROPOFF_LIMIT = -1;
                SINGLE_PLAYER = false;
                CHECK_INSPIRE_ACCURACY = false;
                CHECK_EODDS_ACCURACY = false;

            }
            if (SINGLE_PLAYER) {// SPAWN_LIMIT >= 0 ||  DROPOFF_LIMIT >= 0){
                DO_POST_ANALYSIS = false;
                RANDOMIZE_HANDWAVY = false;
            }

            if (!DO_GAME_OUTPUT) {
                Log.LogType.OUTPUT.active = false;
            }
            if (!AUTO_LEARNING_SAVE) {
                Log.LogType.AUTOTRAINING.active = false;
            }
            Log.allowLogging = ALLOW_LOGGING;
            HandwavyWeights.PLAN_STYLE = PLAN_STYLE_CHOICE;


            if (args.length > 1) {
                rngSeed = Integer.parseInt(args[1]);
            } else if (DETERMINISTIC_TIME_INDEPENDENT) {
                rngSeed = 100000;
            } else {
                rngSeed = System.nanoTime();
            }
            rand = new Random(rngSeed);

            // Log.log("Hello world");


            myActualHps = 0;
            estimateEnemiesEatAtCurRate = 0;
            estimateIllEatAtCurRate = 0;
            myUsableHps = 0;
            totalCollected = 0;
            int myShipCount = 0;


            // System.out.println("were here");

            Game game = new Game();
            Log.open();
            playerCount = Game.players.size();
            myId = Game.myId.id;
            players = new Competitor[playerCount];
            int counter = 1;
            for (int i = 0; i < playerCount; i++) {
                players[i] = new Competitor(i, i == myId);

                if (!players[i].isMe) {
                    if (counter == 1) {
                        enemy1 = players[i];
                    } else if (counter == 2) {
                        enemy2 = players[i];
                    } else if (counter == 3) {
                        enemy3 = players[i];
                    }

                    counter++;
                }

            }
            me = players[myId];

            if (SINGLE_PLAYER && myId == 0) {
                SINGLE_PLAYER = false;
            }


            CheapShip.GenerateRepo();

            if (DO_TESTS && ALLOW_LOGGING && Log.LogType.TESTS.active) {
                Test.IN_TEST_PROGRESS = true;
                try {
                    //Must be before creation of first map
                    Test.DoTests();

                } catch (Exception ex) {
                    for (Log.LogType l : Log.LogType.values()) {
                        if (l != Log.LogType.ANALYSIS) {
                            Log.log("Tests Failed!!!");
                        }
                    }

                    Log.exception(ex, Log.LogType.TESTS);
                }
                Test.IN_TEST_PROGRESS = false;
                Map.staticShipsById = new CheapShip[10000];
            }


            Map.GenerateFirstMap(false);

            MapStartHalite = Map.initialHalite;
            MapStartDensity = (float) MapStartHalite / (float) (Map.height * Map.width);


            if (Map.height == 32) {
                GAMETYPE = 0;
                GAMETYPE_SIZE = 0;
            } else if (Map.height == 40) {
                GAMETYPE = 1;
                GAMETYPE_SIZE = 1;

            } else if (Map.height == 48) {
                GAMETYPE = 2;
                GAMETYPE_SIZE = 2;

            } else if (Map.height == 56) {
                GAMETYPE = 3;
                GAMETYPE_SIZE = 3;

            } else if (Map.height == 64) {
                GAMETYPE = 4;
                GAMETYPE_SIZE = 4;
            } else {
                GAMETYPE = 3; //whats with the weird map man
                GAMETYPE_SIZE = 3;
            }
            if (playerCount == 4) {
                GAMETYPE += 5;
                GAMETYPE_PLAYERS = 1;
            } else {
                GAMETYPE_PLAYERS = 0;
            }
            if (MapStartDensity < 135) {
                GAMETYPE_DENSITY = 0;
            } else if (MapStartDensity < 170) {
                GAMETYPE_DENSITY = 1;
            } else if (MapStartDensity < 205) {
                GAMETYPE_DENSITY = 2;
            } else {
                GAMETYPE_DENSITY = 3;
            }

            if (RANDOMIZE_HANDWAVY) {
                if (AUTO_LEARNING_LOAD) {
                    HandwavyWeights.AutoTrainingInit();
                }
                if (EXPERIMENTAL_MODE) {
                    HandwavyWeights.RandomizeExperimental();
                } else {
                    HandwavyWeights.Randomize();
                }
            }

            if (HandwavyWeights.PLAN_STYLE == Plan.STYLE_RANDOMIZE || HandwavyWeights.PLAN_STYLE == Plan.STYLE_RANDOMIZE_LIMITED) {
                HandwavyWeights.PLAN_STYLE = Plan.STYLE_MINIMAL_5;
            } else if (HandwavyWeights.PLAN_STYLE == Plan.STYLE_DETERMINE_BASED_ON_PLAYERS) {
                if (playerCount == 2) {
                    HandwavyWeights.PLAN_STYLE = Plan.STYLE_JOURNEY_ESCALATION_6;
                } else {
                    HandwavyWeights.PLAN_STYLE = Plan.STYLE_MINIMAL_3;
                }
            } else if (HandwavyWeights.PLAN_STYLE == Plan.STYLE_DETERMINE_BASED_ON_SIZE) {
//
                if (Map.width <= 56) {
                    HandwavyWeights.PLAN_STYLE = Plan.STYLE_MINIMAL_5;
                }
                else if (playerCount == 4 && Map.width >= 64) {
                    HandwavyWeights.PLAN_STYLE = Plan.STYLE_MINIMAL_3;
                }
                else {
                    HandwavyWeights.PLAN_STYLE = Plan.STYLE_MINIMAL_4;
                }


            }
            HandwavyWeights.SpecialBunches();

            PreCalc(); //Must be done after randomize
            WeirdAlgo.GetAll6Paths();

            LIGHT_MODE = HandwavyWeights.PLAN_STYLE == Plan.STYLE_MINIMAL_3 || HandwavyWeights.PLAN_STYLE == Plan.STYLE_MINIMAL_4 || HandwavyWeights.PLAN_STYLE == Plan.STYLE_EXTREMELY_MINIMAL || THINK_QUICKLY;


            if(EXPERIMENTAL_MODE){
                EXPERIMENTAL_MODE = myId == 0;
            }


            if (EXPERIMENTAL_MODE) {
//            HandwavyWeights.WeirdAlgoMaxGathersByOtherShips = 4;
            }


            IndepthShipStats.Init();
            if(ALLOW_LOGGING) {
                Log.log(Map.currentMap.toString(), Log.LogType.MAIN);
                if (DO_GAME_OUTPUT) {
                    GameOutput.OutputInitialGameState();
                }
            }

            game.ready("Guru " + versionName + " - P" + Game.myId.id);

            Log.log("Successfully created bot! My Player ID is " + Game.myId + ". Bot rng seed is " + rngSeed + ".");

            //The main turn loop
            while (true) {
                final ArrayList<Command> commandQueue = new ArrayList<>();
                try {
                    turn = Input.readInput().getInt();
                    startTurn = System.currentTimeMillis();
                    proportionGame = ((float) turn) / Constants.MAX_TURNS;
                    game.turnNumber = turn;
                    //The entire bot is still build on top of the original java starter kit, which still does the game input parsing
                    //My system later reparses this, to create datastructures usable for my bot
                    //The starter kit should've been refactored out long ago, but it wasn't ever really an issue and would've taken valuable time
                    game.updateFrame();
                    turnsLeft = Constants.MAX_TURNS - turn;
                    GameOutput.Clear();
                    if (!RELEASE) {
                        for (Log.LogType l : Log.LogType.values()) {
                            if (l != Log.LogType.ANALYSIS && l != Log.LogType.OUTPUT & l != Log.LogType.AUTOTRAINING) {
                                Log.log("=============== TURN " + turn + " ================", l);
                            }
                        }
                    }
                } catch (OutOfMemoryError ex) {
                    Log.log("Oom during turn load", Log.LogType.MAIN);
                    Log.exception(ex);
                    Log.flushLogs();
                } catch (Exception ex) {
                    //END OF GAME
                    //An exception is always thrown at the end of game during input parsing.
                    //This is a horrible way to detect the end of game, but it works well enough and has the nice side effect
                    //of terminating the expected way on something really unexpected.
                    Log.log("Game over", Log.LogType.MAIN);
                    AnalysisOutput.GenerateOutput();
                    Log.flushLogs();
                    System.out.println(ex.toString());
                    return;
                }

                try {
                    Stopwatch.Begin(90);
                    Map.UpdateMap();
                    if (!SINGLE_PLAYER) {
                        Competitor.Analyze();
                        IndepthShipStats.NoteTurnStats();

                        if (DO_POST_ANALYSIS) {
                            //The fall through here is on purpose
                            switch (turn) {
                                case 50:
                                    PointsT50 = Map.currentMap.playerMoney;
                                    ShipsT50 = Map.currentMap.myShipsCount;
                                    DropoffsT50 = Map.myDropoffs.size();
                                case 100:
                                    PointsT100 = Map.currentMap.playerMoney;
                                    ShipsT100 = Map.currentMap.myShipsCount;
                                    DropoffsT100 = Map.myDropoffs.size();
                                case 150:
                                    PointsT150 = Map.currentMap.playerMoney;
                                    ShipsT150 = Map.currentMap.myShipsCount;
                                    DropoffsT150 = Map.myDropoffs.size();
                                case 200:
                                    PointsT200 = Map.currentMap.playerMoney;
                                    ShipsT200 = Map.currentMap.myShipsCount;
                                    DropoffsT200 = Map.myDropoffs.size();
                                case 250:
                                    PointsT250 = Map.currentMap.playerMoney;
                                    ShipsT250 = Map.currentMap.myShipsCount;
                                    DropoffsT250 = Map.myDropoffs.size();
                                case 300:
                                    PointsT300 = Map.currentMap.playerMoney;
                                    ShipsT300 = Map.currentMap.myShipsCount;
                                    DropoffsT300 = Map.myDropoffs.size();
                                case 350:
                                    PointsT350 = Map.currentMap.playerMoney;
                                    ShipsT350 = Map.currentMap.myShipsCount;
                                    DropoffsT350 = Map.myDropoffs.size();
                                case 400:
                                    PointsT400 = Map.currentMap.playerMoney;
                                    ShipsT400 = Map.currentMap.myShipsCount;
                                    DropoffsT400 = Map.myDropoffs.size();
                                case 450:
                                    PointsT450 = Map.currentMap.playerMoney;
                                    ShipsT450 = Map.currentMap.myShipsCount;
                                    DropoffsT450 = Map.myDropoffs.size();
                            }

                        }

                        int controlVal = Map.currentMap.playerMoney * 7 + Map.currentMap.shipHaliteSum() * 5 + Map.currentMap.shipCoordSum() * 3 + Map.currentMap.haliteSum() * 2;
                        Stopwatch.Stop(90, "Map Update + competitor");
//                      Stopwatch.Start(91);
                        IndepthShipStats.Update(); //needs to be done before prep
                        Plan.Prep();
//                      Stopwatch.ErrorLogPrint(91,"Prep:");
                        Stopwatch.Begin(92);


                        Plan mirrorPlan = null;
                        if (MIRROR_MODE) {//&& MyBot.playerCount == 2){// ){
                            mirrorPlan = MirrorMode.DoMirrorModeIfRunning();
                        }


                        int buildDropoffNowShipId = -1;

                        int maphaliteleft = Map.currentMap.haliteSum();
                        boolean buildingDropoff = false;
                        boolean cantBuildDrop = false;

                        int turnsleft = Constants.MAX_TURNS - game.turnNumber;


                        int turnsback = Math.max(turn / 10, Math.min(15, turn));
                        estimateEnemiesEatAtCurRate = 0;
                        estimateIllEatAtCurRate = 0;

                        double myShipExpectedValue = 0;

                        //closer to the end of the game, average gather rates drop
                        double timeFactor = HandwavyWeights.TimeFactorHpsInitial;

                        timeFactor -= (((double) turn) / ((double) Constants.MAX_TURNS)) * HandwavyWeights.TimeFactorHpsReducing;

                        for (Competitor c : players) {
                            double ratioCollected = Math.max(HandwavyWeights.MinGatherRate, (double) (c.totalGathered - c.totalBurned) / ((double) c.totalGathered));

                            double collectionComponent = ratioCollected * (c.haliteGathered[turn] - c.haliteGathered[turn - turnsback]);
                            double broughtHomeComponent = (c.haliteBroughtHome[turn] - c.haliteBroughtHome[turn - turnsback]);
                            double gatherRateShipTurnsComponent = Map.currentMap.myShipsCount * ((me.haliteBroughtHome[MyBot.turn] * 0.6 + me.haliteGathered[MyBot.turn] * 0.4) / Math.max(1.0, me.shipTurnsAlive));


                            double expectedShipValue = HandwavyWeights.EstimatedShipValueFlat + HandwavyWeights.EstimatedShipValueModifier * timeFactor * ((turnsleft - HandwavyWeights.ExpectedShipValueCeaseGather) * (collectionComponent * 0.33 + broughtHomeComponent * 0.33 + gatherRateShipTurnsComponent * 0.33) / (double) (myShipCount * turnsback));


                            if (expectedShipValue == Float.NaN) {
                                expectedShipValue = 10000;
                            }
                            if (c.isMe) {
                                myShipExpectedValue = expectedShipValue;
                                Log.log("Expected value of ship: " + myShipExpectedValue + " col: " + collectionComponent + " home: " + broughtHomeComponent + " gather " + gatherRateShipTurnsComponent, Log.LogType.MAIN);


                                estimateIllEatAtCurRate = expectedShipValue * myShipCount;
                            } else {
                                estimateEnemiesEatAtCurRate += expectedShipValue * c.shipCount;
                            }

                        }


                        estimateEnemiesEatAtCurRate = Map.currentMap.enemyShipsCount * HandwavyWeights.GuessShipHalitePerTurnAverageV2 * (Constants.MAX_TURNS - turn);
                        myActualHps = ((float) totalMoneyCollected / (float) botTurnsAlive);
                        myUsableHps = myActualHps;
                        myShipCount = Map.currentMap.myShipsCount;


                        if (turn < 40) {
                            myUsableHps = 40f;
                        }


                        Plan.dropOffRunner = -1;
                        Plan.dropOffSpot = null;
                        int estimatedCost = 0;

                        //This section determines whether we should build a dropoff
                        if (MirrorMode.TurnIntoDropoff >= 0) {
                            commandQueue.add(Command.transformShipIntoDropoffSite(Game.ships.get(MirrorMode.TurnIntoDropoff).id));
                            TotalSpent += Constants.DROPOFF_COST - Map.staticShipsById[MirrorMode.TurnIntoDropoff].GetTile().haliteStartTurn;
                            buildingDropoff = true;
                            Plan.dropOffSpot = Map.staticShipsById[MirrorMode.TurnIntoDropoff].GetTile();
                            buildDropoffNowShipId = MirrorMode.TurnIntoDropoff;
                        } else if (mirrorPlan != null) {

                        } else if (DROPOFF_LIMIT == -1 || DROPOFF_LIMIT > me.dropoffCount) {
                            //TODO: maybe integrate this into plans somehow?
                            if (turn > 40 && Map.currentMap.myShipsCount > HandwavyWeights.DropoffminShips &&
                                    turnsleft > HandwavyWeights.StopDropoffBuildingTurnsBeforeEnd &&
                                    Map.myDropoffs.size() < HandwavyWeights.DropoffHardcap[GAMETYPE_SIZE] && (Map.width != 32 || playerCount != 4 || Map.myDropoffs.size() < 2)) {


                                expectedHaliteForDropoff = Game.me.halite;

                                for (CheapShip s : Map.staticMyShips) {
                                    if (s.halite > 900 && s.GetTile().turnsFromDropoff < HandwavyWeights.AllowRangeExpectedV2) {
                                        expectedHaliteForDropoff += s.halite;
                                    }
                                }


                                Stopwatch.Start();
                                Tile bestDropOffTile = SideAlgorithms.GetBestDropoffSpot();
                                Stopwatch.Stop("dropoff search");

                                if (bestDropOffTile != null && expectedHaliteForDropoff + bestDropOffTile.haliteStartTurn > HandwavyWeights.MinExpectedHaliteBeforeConsiderDropoff) {
                                    boolean enoughLeftOnMap = maphaliteleft - (estimateEnemiesEatAtCurRate * HandwavyWeights.enemyHpsFactorV2 + estimateIllEatAtCurRate) > HandwavyWeights.MapHaliteLeftMinV3;
                                    boolean tooManyDropoffs = Map.myDropoffs.size() * HandwavyWeights.ShipsPerDropoffV2[MyBot.GAMETYPE] > Map.currentMap.myShipsCount + 2 && Map.currentMap.myShipsCount < 100;
                                    boolean wayTooManyDropoffs = Map.myDropoffs.size() * HandwavyWeights.ShipsPerDropoffV2[MyBot.GAMETYPE] > Map.currentMap.myShipsCount * 1.8;


                                    double value = HandwavyWeights.DropoffWorthDensity[MyBot.GAMETYPE_DENSITY] * HandwavyWeights.DropoffWorthMultiplier[MyBot.GAMETYPE] * (1.1 + (HandwavyWeights.DropoffBaseMultV2 / ((double) Map.myDropoffs.size()))) * myActualHps * Map.currentMap.myShipsCount * (turnsleft - 10.0);
                                    value += bestDropOffTile.desirability * HandwavyWeights.DropoffBestTileValueRatio;
                                    value += HandwavyWeights.DropoffFlatScore;
                                    value += (Map.width - 32) * HandwavyWeights.DropoffMapSizeScore;
                                    value += MyBot.playerCount * HandwavyWeights.DropoffPlayerCount;
                                    value += HandwavyWeights.DropoffWorthGametypeFlat[MyBot.GAMETYPE];
                                    value += HandwavyWeights.DropoffWorthDensityFlat[MyBot.GAMETYPE_DENSITY];

                                    double worstOpponentScary = 0;
                                    for (Competitor c : players) {
                                        if (!c.isMe) {
                                            worstOpponentScary = Math.max(worstOpponentScary, c.scaryFactor);
                                        }
                                    }
                                    value += worstOpponentScary * HandwavyWeights.DropoffWorstOpponentStr;

                                    if (Map.myDropoffs.size() < 2) {
                                        value *= HandwavyWeights.DropoffNoDropoffsMult;
                                    } else if (Map.myDropoffs.size() < 3) {
                                        value *= HandwavyWeights.DropoffOneDropoffMult;
                                    }

                                    if (!enoughLeftOnMap) {
                                        value *= HandwavyWeights.DropoffNotEnoughHalite;
                                    }
                                    if (tooManyDropoffs) {
//                                        Log.log("Too many dropoffs! " + Map.myDropoffs.size() + " " + Map.staticMyShipCount, Log.LogType.MAIN);
                                        value *= HandwavyWeights.DropoffTooManyMultV2;
                                    }
//                                    else {
//                                        Log.log("We can do dropoffs! " + Map.myDropoffs.size() + " " + Map.staticMyShipCount, Log.LogType.MAIN);
//                                    }

                                    if (wayTooManyDropoffs) {
                                        value *= HandwavyWeights.DropoffWayTooManyMultV2;
                                    }

                                    if (Map.currentMap.myShipsCount < HandwavyWeights.DropoffTooFewShips) {
                                        value *= HandwavyWeights.DropoffFactorIfTooFewShips;
                                    }


                                    value *= Math.min(1.0, Map.staticMyShipCount / HandwavyWeights.DropoffShipsBelowVal);

                                    value *= HandwavyWeights.DropoffWorthMultV2 * HandwavyWeights.DropoffWorthMultV2PlayersMult[MyBot.GAMETYPE_PLAYERS];

                                    if (value > Constants.DROPOFF_COST) {

                                        GameOutput.info.add("newdropoffloc:" + bestDropOffTile.x + "," + bestDropOffTile.y);

                                        // if( me.halite >= Constants.DROPOFF_COST) {
//                                        Log.log("WANT TO BUILD DROPOFF " + value, Log.LogType.MAIN);
//                            double bestDropOffBuildScore = HandwavyWeights.DropoffBaseScore;

                                        int halonspot = 0;
                                        int closestDist = 100000;


                                        for (CheapShip s : Map.staticMyShips) {
                                            if (s.x == bestDropOffTile.x && s.y == bestDropOffTile.y) {
                                                halonspot = Map.staticHaliteMap[s.x][s.y];
                                                if (s.halite + halonspot + Game.me.halite >= Constants.DROPOFF_COST) {
                                                    buildDropoffNowShipId = s.id;
                                                    Plan.dropOffRunner = -1;
                                                } else {
                                                    Plan.dropOffRunner = s.id;
                                                }
                                                break;
                                            } else {
                                                int dist = bestDropOffTile.DistManhattan(s.x, s.y);
                                                if (dist < closestDist) {
                                                    closestDist = dist;
                                                    Plan.dropOffRunner = s.id;
                                                }

                                            }
                                        }

                                        if (buildDropoffNowShipId >= 0) {
//                                            Log.log("BUILDING DROPOFF", Log.LogType.MAIN);
                                            commandQueue.add(Command.transformShipIntoDropoffSite(Game.ships.get(buildDropoffNowShipId).id));
                                            TotalSpent += Constants.DROPOFF_COST - halonspot;
                                            buildingDropoff = true;
                                            Plan.dropOffSpot = bestDropOffTile;
                                            estimatedCost = Math.max(0, Constants.DROPOFF_COST - halonspot);
                                        } else if (Plan.dropOffRunner >= 0) {
                                            GameOutput.info.add("dropoffrunner:" + Plan.dropOffRunner);
                                            estimatedCost = Math.max(0, Constants.DROPOFF_COST - halonspot);
                                            Plan.dropOffSpot = bestDropOffTile;
                                        }


//                            for (CheapShip s : Map.currentMap.myShips) {
//                                if (s != null && Map.myDropoffMap[s.x][s.y] == null) {
//                                    if (s.halite + Map.currentMap.GetHaliteIgnoreProposals(s.x, s.y) + Game.me.halite >= Constants.DROPOFF_COST) {
//                                        if (Plan.turnsFromDropoff[s.x][s.y] > HandwavyWeights.MinStepsFromDropoff) {
//                                            double finalScore = Plan.turnsFromDropoff[s.x][s.y] * HandwavyWeights.DropoffWeightDistV2 + Plan.medDistLure[s.x][s.y] * HandwavyWeights.DropoffWeightMedLure + Plan.longDistLure[s.x][s.y] * HandwavyWeights.DropoffWeightLongLure + Map.currentMap.GetHaliteIgnoreProposals(s.x, s.y) * HandwavyWeights.DropoffWeightHalOnSpot;
//
//                                            finalScore += maphaliteleft * HandwavyWeights.DropoffWeightHalLeftOnMap;
//
//                                            Log.log("Score: " + finalScore + "   " + s);
//
//
//                                            if (finalScore > bestDropOffBuildScore) {
//                                                bestDropOffBuildScore = finalScore;
//                                                bestshipId = s.id;
//                                                halonspot = Map.currentMap.GetHaliteIgnoreProposals(s.x, s.y);
//                                            }
//                                        }
//                                    }
//                                }
//                            }


//                    }else{
//                        Log.log("CASH", Log.LogType.MAIN);
//                    }
                                    }
//                                    else {
//                                        Log.log("NOT WORTH BUILDING DROPOFF  " + value, Log.LogType.MAIN);
//                                    }
                                }
//                                else {
//                                    Log.log("Not building dropoff cant find spot / not enough expected halite", Log.LogType.MAIN);
//                                }
                            }
//                            else {
//                                Log.log("Not building dropoff hard reason", Log.LogType.MAIN);
//
//                                if (maphaliteleft - (estimateEnemiesEatAtCurRate * HandwavyWeights.enemyHpsFactorV2 + estimateIllEatAtCurRate) < HandwavyWeights.MapHaliteLeftMinV3) {
//                                    Log.log("MAP TOO EMPTY FOR DROPOFF", Log.LogType.MAIN);
//                                }
//                            }
                        }

                        if (!buildingDropoff) {
                            for (CheapShip s : Map.staticMyShips) {
                                if (s.GetTile().haliteStartTurn > 5000) {
//                                    Log.log("Building dropoff on massive tile", Log.LogType.MAIN);
                                    buildDropoffNowShipId = s.id;
                                    commandQueue.add(Command.transformShipIntoDropoffSite(Game.ships.get(s.id).id));
                                    TotalSpent += Constants.DROPOFF_COST - s.GetTile().haliteStartTurn;
                                    buildingDropoff = true;
                                    Plan.dropOffSpot = s.GetTile();
                                    estimatedCost = Math.max(0, Constants.DROPOFF_COST - s.GetTile().haliteStartTurn);
                                    break;
                                }
                            }
                        }


                        DoIWantToBuildShip = false;


                        //This section determines whether we should build a ship
                        if (MirrorMode.DoWeBuildShipThisTurn) {
                            DoIWantToBuildShip = true;
                        } else if (mirrorPlan != null) {
                            DoIWantToBuildShip = false;
                        } else if (SPAWN_LIMIT == -1 || SPAWN_LIMIT > me.shipsBuilt) {


                            if ((Game.me.halite >= Constants.SHIP_COST && (!buildingDropoff || Game.me.halite - estimatedCost >= Constants.SHIP_COST)) && Map.currentMap.myShipsCount < HandwavyWeights.ShipHardCap) {

                                if (SIMPLE_SPAWN) {
                                    if (me.shipsBuilt <= HandwavyWeights.SimpleSpawnSpawns[GAMETYPE]) {
                                        DoIWantToBuildShip = true;
                                    }
                                } else {
                                    boolean shouldMatchEnemy = playerCount == 2 && turnsleft > 100 && Map.currentMap.enemyShipsCount > Map.currentMap.myShipsCount - 4;// &&  enemy1.currentPoints + enemy1.carryingHalite + 1500 < me.currentPoints + me.carryingHalite;


                                    if (playerCount == 4 && turnsleft > 100) {

                                        //First check if there's an enemy with higher (or equal) shipcount
                                        for (Competitor c : players) {
                                            if (!c.isMe) {
                                                if (c.shipCount > me.shipCount - 1) {
                                                    shouldMatchEnemy = true;
                                                    break;
                                                }
                                            }
                                        }
                                        if (shouldMatchEnemy) {
                                            //Then check to ensure there's no one close to us in points + collected
                                            for (Competitor c : players) {
                                                if (!c.isMe) {
                                                    if (c.currentPoints + c.carryingHalite + 4000 > me.currentPoints + me.carryingHalite) {
                                                        shouldMatchEnemy = false;
                                                    }
                                                }
                                            }
                                        }
                                    }


                                    if ((turn < Constants.MAX_TURNS * HandwavyWeights.MaxAllowShips[GAMETYPE] || (Map.currentMap.myShipsCount < HandwavyWeights.OverrideMaxAllowIfBelow && turnsLeft > HandwavyWeights.OverrideTimeLimit) || shouldMatchEnemy)) {
                                        //Make sure we're not building these when we want to make myDropoffs
                                        if (Plan.dropOffSpot == null || Game.me.halite >= (Constants.SHIP_COST + estimatedCost)) {
                                            if (maphaliteleft > Map.initialHalite * 0.1) {
                                                //assuming we've beat all the conditions, determine whether it's worth building ships.
                                                //calculate the average collection per ship
                                                double expectedValue = myShipExpectedValue;

                                                boolean forceBuyingEarly = game.turnNumber <= Constants.MAX_TURNS * HandwavyWeights.AlwaysAllowShipGameLength;
                                                boolean forceBuyingMinimalShips = game.turnNumber < (Constants.MAX_TURNS - HandwavyWeights.StopTurnsBeforeEndMinShips) && Map.currentMap.myShipsCount < HandwavyWeights.MinimalShips;

                                                double estimatedLeftToEat = maphaliteleft - (estimateEnemiesEatAtCurRate + estimateIllEatAtCurRate);
                                                double multiplier = HandwavyWeights.WorthMultiplierMult * HandwavyWeights.WorthMultiplier[GAMETYPE] * HandwavyWeights.WorthMultiplierDensity[GAMETYPE_DENSITY];


                                                if (estimatedLeftToEat < HandwavyWeights.MinHalLeftOnMapForShipV3 * 0.1 || Map.curMapHaliteSum < Map.initialHalite * 0.15) {
                                                    multiplier *= HandwavyWeights.EVNothingLeftToEat1;
                                                } else if (estimatedLeftToEat < HandwavyWeights.MinHalLeftOnMapForShipV3 * 0.3 || Map.curMapHaliteSum < Map.initialHalite * 0.25) {
                                                    multiplier *= HandwavyWeights.EVNothingLeftToEat2;
                                                } else if (estimatedLeftToEat < HandwavyWeights.MinHalLeftOnMapForShipV3 * 0.55 || Map.curMapHaliteSum < Map.initialHalite * 0.35) {
                                                    multiplier *= HandwavyWeights.EVNothingLeftToEat3;
                                                } else if (estimatedLeftToEat < HandwavyWeights.MinHalLeftOnMapForShipV3 * 0.75 || Map.curMapHaliteSum < Map.initialHalite * 0.45) {
                                                    multiplier *= HandwavyWeights.EVNothingLeftToEat4;
                                                } else if (estimatedLeftToEat < HandwavyWeights.MinHalLeftOnMapForShipV3 * 1.0 || Map.curMapHaliteSum < Map.initialHalite * 0.55) {
                                                    multiplier *= HandwavyWeights.EVNothingLeftToEat5;
                                                } else if (estimatedLeftToEat < HandwavyWeights.MinHalLeftOnMapForShipV3 * 1.5 || Map.curMapHaliteSum < Map.initialHalite * 0.62) {
                                                    multiplier *= HandwavyWeights.EVNothingLeftToEat6;
                                                } else if (estimatedLeftToEat < HandwavyWeights.MinHalLeftOnMapForShipV3 * 2.5 || Map.curMapHaliteSum < Map.initialHalite * 0.70) {
                                                    multiplier *= HandwavyWeights.EVNothingLeftToEat7;
                                                }


                                                multiplier *= (1.0 - HandwavyWeights.EVTimeFactorMultiplier * ((double) turn / (double) Constants.MAX_TURNS));


                                                if (maphaliteleft < Map.initialHalite * HandwavyWeights.MapSparseFactor1) {
                                                    if (maphaliteleft < Map.initialHalite * HandwavyWeights.MapSparseFactor2) {
                                                        multiplier *= HandwavyWeights.MapSparseMult2;
                                                    } else {
                                                        multiplier *= HandwavyWeights.MapSparseMult1;
                                                    }
                                                }

                                                if (shouldMatchEnemy) {
                                                    multiplier *= HandwavyWeights.behindIn2pWorthBuilding;
                                                    expectedValue += 1000;
                                                }

                                                if (turn > 150) {
                                                    for (Competitor c : players) {
                                                        if (!c.isMe) {
                                                            if (turnsleft < 200 && c.expectedPoints > me.expectedPoints * 0.95 && c.currentPoints < me.currentPoints * 0.8) {
                                                                //Please don't overtake us.
                                                                multiplier *= HandwavyWeights.EVOvertakeV2;
                                                            }
                                                            if (me.ships.size() < c.ships.size()) {
                                                                multiplier *= HandwavyWeights.EVLessShips * HandwavyWeights.EVLessShipsSizeMult[MyBot.GAMETYPE_SIZE];
                                                            } else if (me.ships.size() < c.ships.size() + 15) {
                                                                multiplier *= HandwavyWeights.EVLeadingByALittle;
                                                            } else {
                                                                if (me.halite[turn] > c.halite[turn]) {
                                                                    multiplier *= HandwavyWeights.EVLeadShipsAndPointsV2 * HandwavyWeights.EVLeadShipsAndPointsV2SizeMult[MyBot.GAMETYPE_SIZE];
                                                                } else {
                                                                    multiplier *= HandwavyWeights.EVLeadShipsV2;
                                                                }
                                                            }

                                                        }

                                                    }
                                                }

                                                //capping multiplier, its still supposed to be a decision of: will we actually have a net gain from building a ship
                                                expectedValue *= Math.max(HandwavyWeights.MinMultiplierEV * HandwavyWeights.MinMultiplierEVSizeMult[MyBot.GAMETYPE_SIZE], Math.min(multiplier, HandwavyWeights.MaxMultiplierEV));

//                                                Log.log("Expected value of ship: " + expectedValue + " multiplier " + multiplier, Log.LogType.MAIN);


                                                //Must be decently sure it's actually a good investment
                                                if (forceBuyingEarly || forceBuyingMinimalShips || myShipCount < HandwavyWeights.AlwaysWorthShipCount || expectedValue > Constants.SHIP_COST) {
                                                    DoIWantToBuildShip = true;

                                                }
//                                                else {
//                                                    Log.log("Not building ships, not worth it ", Log.LogType.MAIN);
//                                                }
                                            }
//                                            else {
//                                                Log.log("Not building ships, map too sparse ", Log.LogType.MAIN);
//                                            }
                                        }
//                                        else {
//                                            Log.log("Not building ships, saving for dropoff ", Log.LogType.MAIN);
//                                        }
                                    }
//                                    else {
//                                        Log.log("Not building ships, too late ", Log.LogType.MAIN);
//                                    }

                                }
                            }
                        }


                        Plan.lastTurnDropOffSpot = Plan.dropOffSpot;

                        Stopwatch.Stop(92, "Dropoffs + rand");

                        //This bit will calculate all moves
                        Plan.PrepAfterDropoffs();
                        Plan stronkPlan;
                        if (mirrorPlan != null) {
                            stronkPlan = mirrorPlan;
                        } else {
                            stronkPlan = Plan.FindBestPlan();
                        }


                        Stopwatch.Begin(94);

                        moneyCollectedLastTurn5 = moneyCollectedLastTurn4;
                        moneyCollectedLastTurn4 = moneyCollectedLastTurn3;
                        moneyCollectedLastTurn3 = moneyCollectedLastTurn2;
                        moneyCollectedLastTurn2 = moneyCollectedLastTurn;
                        moneyCollectedLastTurn = 0;

                        //This section turns planned moves into commands
                        if (stronkPlan != null && stronkPlan.movesPerTurn[0] != null) {
                            HashSet<Integer> ships = new HashSet<>();

                            for (int moveindex = 0; moveindex < Map.staticMyShipCount; moveindex++) {
                                Move m = stronkPlan.movesPerTurn[0][moveindex];

                                if (m != null && m.ship.id != buildDropoffNowShipId && Game.ships.get(m.ship.id).isMine) {
                                    if (m.from.x == m.to.x) {
                                        if (m.from.y == m.to.y) {
                                            //Stand still command
                                            //On second thought, not sending commands for ships that don't move is cheaper with no downsides
                                            // commandQueue.add(new Command("m " + m.ship.id + " o"));

                                            moneyCollectedLastTurn += (int) ((((double) Map.currentMap.GetHaliteAt(m.to)) / ((double) Constants.EXTRACT_RATIO)) + 0.5);
                                        } else if (m.to.IsNorthOf(m.from)) {
                                            commandQueue.add(new Command("m " + m.ship.id + " n"));

                                            TotalBurnt += Map.currentMap.GetHaliteAt(m.to) / Constants.MOVE_COST_RATIO;
                                        } else {
                                            commandQueue.add(new Command("m " + m.ship.id + " s"));
                                            TotalBurnt += Map.currentMap.GetHaliteAt(m.to) / Constants.MOVE_COST_RATIO;

                                        }
                                    } else {
                                        TotalBurnt += Map.currentMap.GetHaliteAt(m.to) / Constants.MOVE_COST_RATIO;
                                        if (m.to.IsWestOf(m.from)) {
                                            commandQueue.add(new Command("m " + m.ship.id + " w"));
                                        } else {
                                            commandQueue.add(new Command("m " + m.ship.id + " e"));
                                        }
                                    }
                                    ships.add(m.ship.id);
//                                    Log.log(m.ship + "   " + m, Log.LogType.MOVES);
                                }
//                                else {
//                                    Log.log("Null move for ship at index " + moveindex + " ship:  " + Map.currentMap.myShips[moveindex], Log.LogType.MAIN);
//                                }


                            }

                            for (CheapShip s : Map.currentMap.myShips) {
                                if (s != null && !ships.contains(s.id) && s.id != buildDropoffNowShipId) {
//                                    Log.log("WHAAAT?T? PLAN FAILED?AD?DS" + s, Log.LogType.MAIN);
                                    commandQueue.add(new Command("m " + s.id + " o"));

                                    Tile t = Map.currentMap.GetTile(s);
                                    stronkPlan.SetMyMove(0, new Move(t, t, s));
                                }
                            }
                        }
                        totalMoneyCollected += moneyCollectedLastTurn;
                        botTurnsAlive += Map.currentMap.myShipsCount;

//                        Log.log("Collected: " + totalMoneyCollected + "  In turns: " + botTurnsAlive + " LAst turn: " + moneyCollectedLastTurn + " BotHps " + myActualHps + " myships: " + Map.currentMap.myShipsCount + " total ships: " + Map.currentMap.allShipsCount, Log.LogType.MAIN);

                        if (DoIWantToBuildShip) {
                            boolean foundship = false;
                            if (stronkPlan != null && stronkPlan.movesPerTurn[0] != null) {

                                for (int moveindex = 0; moveindex < Map.staticMyShipCount; moveindex++) {
                                    Move m = stronkPlan.movesPerTurn[0][moveindex];
                                    if (m != null && m.to.x == Game.me.shipyard.position.x && m.to.y == Game.me.shipyard.position.y) {
                                        foundship = true;
                                        break;
                                    }
                                }
                            }
                            if (!foundship) {
                                commandQueue.add(Game.me.shipyard.spawn());
                                TotalSpent += Constants.SHIP_COST;
                                TotalShipsBuilt++;
                            }
                        }


                        if (turnsleft > 10) {
                            TotalShipsLost = TotalShipsBuilt - myShipCount;
                        }


//                        int controlVal2 = Map.currentMap.playerMoney * 7 + Map.currentMap.shipHaliteSum() * 5 + Map.currentMap.shipCoordSum() * 3 + Map.currentMap.haliteSum() * 2;
//                        if (controlVal != controlVal2) {
//                            for (Log.LogType l : Log.LogType.values()) {
//                                if (l != Log.LogType.ANALYSIS) {
//                                    Log.log("STOP MESSING WITH THE MAP", l);
//                                }
//                            }
//                        }
                    }
                } catch (OutOfMemoryError ex) {
                    Log.exception(ex);
                    Log.flushLogs();
                } catch (Exception ex) {

                    if (SERVER_RELEASE && !FINALS_RELEASE) {
                        System.err.print(ex.toString());
                        ex.printStackTrace();
                    }

                    Log.exception(ex);
                    Log.flushLogs();
                }

                try {

                    if (DO_GAME_OUTPUT && ALLOW_LOGGING) {
                        GameOutput.OutputGameState();
                    }

                    if (ALLOW_KILLFILE && turn - killfilecheck > 5 && (TIME_ALLOWED - (System.currentTimeMillis() - startTurn)) > 150) {
                        CheckKillFile();
                        killfilecheck = turn;
                    }


                    if (SERVER_RELEASE && !FINALS_RELEASE) {
                        System.err.println(" TURN: " + turn + "ms: " + (TIME_ALLOWED - (System.currentTimeMillis() - startTurn)) + " s:" + Map.totalSimulationsDoneThisTurn + " timeerrors: " + Plan.timeErrors);
                    }


                    Stopwatch.Stop(94, "Ships + move execution");
//                    Log.log("Failures: " + Map.mapCopyFailures + " Total: " + Map.mapCopyTotal, Log.LogType.MAIN);
//                    Log.log("Should have this much time left: " + (TIME_ALLOWED - (System.currentTimeMillis() - startTurn)) + "  commands: " + commandQueue.size(), Log.LogType.MAIN);
                    game.endTurn(commandQueue);

                    if (!FAST_BATCH_USING_NOTIMEOUT || turn % 10 == 0) {
                        Log.flushLogs();
                    }
                    //System.gc();
                } catch (OutOfMemoryError ex) {
                    Log.exception(ex);
                    Log.flushLogs();
                } catch (Exception ex) {
                    Log.exception(ex);
                }
            }

        } catch (Throwable exc) {
            return;
        }
    }


    //The killfile is a file that can stop the bot from execution any further
    //Useful if I want to abort long matches without killing the process (as that would mean no replay etc.)
    private static void CheckKillFile() throws Throwable {
        try {

            Stopwatch.Start();
            byte[] encoded = Files.readAllBytes(Paths.get("KILLFILE.txt"));
            String s = new String(encoded, "utf-8");
            int val = Integer.parseInt(s);
            Log.log("Setting kill to: " + val, Log.LogType.MAIN);
            DIE_TURN = val;
            Stopwatch.Stop("Killfile");
        } catch (Exception ex) {
            Log.exception(ex);
        }
    }


    //Calculate some things on turn 1 that'll be necessary throughout the execution of the program.
    //This saves a lot on performance where it matters, and makes some things more convenient (others less)
    private static void PreCalc() {
        for (int i = 0; i < 40000; i++) {
            //not entirely sure about the caps, but it helps prevent bugs too
            standCollect[i] = (short) Math.min(1000, (int) ((((double) i) / ((double) Constants.EXTRACT_RATIO)) + 0.5));
            moveCosts[i] = (short) Math.min(1000, i / Constants.MOVE_COST_RATIO);


            int gatherhalitet1 = MyBot.standCollect[i];
            int remaindert1 = i - gatherhalitet1;
            int gatherhalitet2 = MyBot.standCollect[remaindert1];
            int remaindert2 = remaindert1 - gatherhalitet2;
            int gatherhalitet3 = MyBot.standCollect[remaindert2];
            int remaindert3 = remaindert2 - gatherhalitet3;
            int gatherhalitet4 = MyBot.standCollect[remaindert3];
            int remaindert4 = remaindert3 - gatherhalitet4;
            int gatherhalitet5 = MyBot.standCollect[remaindert4];
            gatherValues[i] = (int) (gatherhalitet1 * HandwavyWeights.Gather1V2 + gatherhalitet2 * HandwavyWeights.Gather2V2 + gatherhalitet3 * HandwavyWeights.Gather3V2 + gatherhalitet4 * HandwavyWeights.Gather4V2 + gatherhalitet5 * HandwavyWeights.Gather5V2);


            standCollectSafe[i] = (short) Math.min(1000, (int) ((Math.max(0.0, i - 1000.0) / ((double) Constants.EXTRACT_RATIO)) + 0.5));
            moveCostsSafe[i] = (short) Math.min(1000, Math.max(0, i - 1000) / Constants.MOVE_COST_RATIO);


            haliteLogCurve[i] = Util.LogCurve(i, 0, 1000);
            haliteExponentialCurve[i] = Util.ExponentialCurve(i, 0, 1000);
            haliteSqrtCurve[i] = Util.SqrtCurve(i, 0, 1000);

            evalHalite[i] = haliteSqrtCurve[i] * HandwavyWeights.GoalWeightSqrtHalite + MyBot.haliteLogCurve[i] * HandwavyWeights.GoalWeightLogHalite + MyBot.haliteExponentialCurve[i] * HandwavyWeights.GoalWeightExpoHalite;
        }

        standCollect[1] = 1;
        standCollectSafe[1001] = 1;

        for (int halite = 0; halite <= 5000; halite++) {
            for (int turns = 0; turns < SizeAfterXTurnArrays; turns++) {
                int gained = 0;
                int remaining = halite;
                for (int turn = turns; turn > 0; turn--) {
                    int collect = MyBot.standCollect[remaining];
                    remaining -= collect;
                    gained += collect;
                }
                int burn = MyBot.moveCosts[remaining];
                gained -= burn;

                collectIfStandForX[halite][turns] = gained;
                burnIfMoveAfterX[halite][turns] = burn;
                if (turns > 0) {
                    gainIfStandFor1More[halite][turns - 1] = gained - collectIfStandForX[halite][turns - 1];
                }
            }

        }


        for (int i = 0; i < 5000; i++) {

            float expoFactor = (float) Math.pow(i, HandwavyWeights.DROP_DIST_EXPO_POW) / ((float) (Math.pow(1000.0f, HandwavyWeights.DROP_DIST_EXPO_POW)));
            float multFactor = Math.max(0, i - HandwavyWeights.DROP_DIST_MINDIST) / ((1000f - HandwavyWeights.DROP_DIST_MINDIST));

            shipReturnHaliteMultiplier[i] = HandwavyWeights.DROP_DIST_WEIGHT * Math.min(HandwavyWeights.DROP_DIST_MAX_PERDISTFACTOR, expoFactor * multFactor * HandwavyWeights.DROP_DIST_FACTOR);
            meaningFulHaliteMultiplier[i] = (float) (HandwavyWeights.MeaningfulHaliteV3 * HandwavyWeights.MeaningfulHaliteV3SizeMult[MyBot.GAMETYPE_SIZE] * Util.Clamp(Math.min(1f, Map.baseMeaningfulHalite / HandwavyWeights.MeaningfulHaliteBASE) * 1f - Math.pow(i / 1000f, HandwavyWeights.MeaningfulHalitePOW), HandwavyWeights.MeaningfulHaliteMin, 1.0));


            if (i < HandwavyWeights.EmptyishLuresV2) {

                medLureImportance[i] = HandwavyWeights.MedLureEmptyishV2;
                longLureImportance[i] = HandwavyWeights.LongLureEmptyishV2;
                lureImportance[i] = HandwavyWeights.LureEmptyishV3;
            } else if (i > HandwavyWeights.FullishLuresV2) {

                medLureImportance[i] = HandwavyWeights.MedLureFullishV2;
                longLureImportance[i] = HandwavyWeights.LongLureFullishV2;
                lureImportance[i] = HandwavyWeights.LureFullishV3;
            } else {
                medLureImportance[i] = HandwavyWeights.MedLureV2;
                longLureImportance[i] = HandwavyWeights.LongLureV2;
                lureImportance[i] = HandwavyWeights.LureV3;
            }


            if (i < 200) {
                shipHaliteImportance[i] = HandwavyWeights.Importance1V3;
            } else if (i < 400) {
                shipHaliteImportance[i] = HandwavyWeights.Importance2V3;
            } else if (i < 700) {
                shipHaliteImportance[i] = HandwavyWeights.Importance3V3;
            } else if (i < 900) {
                shipHaliteImportance[i] = HandwavyWeights.Importance4V3;
            } else if (i < 1000) {
                shipHaliteImportance[i] = HandwavyWeights.Importance5V3;
            } else {
                shipHaliteImportance[i] = 0f;
            }

        }

        for (int shipHalite = 0; shipHalite < 1001; shipHalite++) {

            EShipHaliteWeights[shipHalite] = shipHalite * HandwavyWeights.EnemyShipHalite[MyBot.GAMETYPE_PLAYERS];

            for (int haliteTo = 0; haliteTo < 3000; haliteTo++) {
                evalStandstill[shipHalite][haliteTo] = 0;
                if (shipHalite < HandwavyWeights.EmptyishStandstillV2) {
                    if (haliteTo < 10) {
                        evalStandstill[shipHalite][haliteTo] = -HandwavyWeights.WastePreventionScore * HandwavyWeights.PunishStandstillNoHaliteEmptyishV2;
                    } else if (haliteTo < 20) {
                        evalStandstill[shipHalite][haliteTo] = -HandwavyWeights.WastePreventionScore * HandwavyWeights.PunishStandstillLowHaliteEmptyishV2;
                    }
                } else if (shipHalite > HandwavyWeights.FullishStandstillV2) {
                    if (haliteTo < 10) {
                        evalStandstill[shipHalite][haliteTo] = -HandwavyWeights.WastePreventionScore * HandwavyWeights.PunishStandstillNoHaliteFullishV2;
                    } else if (haliteTo < 20) {
                        evalStandstill[shipHalite][haliteTo] = -HandwavyWeights.WastePreventionScore * HandwavyWeights.PunishStandstillLowHaliteFullishV2;
                    }
                } else {
                    if (haliteTo < 10) {
                        evalStandstill[shipHalite][haliteTo] = -HandwavyWeights.WastePreventionScore * HandwavyWeights.PunishStandstillNoHaliteNormalV2;
                    } else if (haliteTo < 20) {
                        evalStandstill[shipHalite][haliteTo] = -HandwavyWeights.WastePreventionScore * HandwavyWeights.PunishStandstillLowHaliteNormalV2;
                    }
                }

                if (shipHalite < 970 && haliteTo > 90) {
                    evalStandstill[shipHalite][haliteTo] += HandwavyWeights.RuleOf90;
                }
                if (shipHalite < 960 && haliteTo > 120) {
                    evalStandstill[shipHalite][haliteTo] += HandwavyWeights.RuleOf120;
                }
                if (shipHalite < 950 && haliteTo > 150) {
                    evalStandstill[shipHalite][haliteTo] += HandwavyWeights.RuleOf150;
                }
                if (shipHalite < 940 && haliteTo > 180) {
                    evalStandstill[shipHalite][haliteTo] += HandwavyWeights.RuleOf180;
                }
                if (shipHalite < 930 && haliteTo > 210) {
                    evalStandstill[shipHalite][haliteTo] += HandwavyWeights.RuleOf210;
                }


            }
        }


    }
}
