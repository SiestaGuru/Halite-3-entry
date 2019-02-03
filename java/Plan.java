import hlt.Constants;
import hlt.Game;

import java.util.*;


/*
This class does most of the main movement logic. Its entry entry points are Prep(), PrepAfterDropoffs() and FindBestPlan()
The most important methods in the final build are probably:  MoveEvaluate(), GetMinimal() and FindBestSoloJourney()

This movement system comes to move suggestions based on four main components:
- It precalculates just about anything that can be precalculated
- It generates possible movesets  (a plan) that includes a move for every ship on every turn within the current search depth
- It simulates these movesets to generate a final state
- The final state and the path used to get there are evaluated

Eventually a highest score plan is found, and the turn 0 moves of this plan will be turned into actual moves

The main evaluator is basically agnostic to the state finder and simulation systems used, so different move set finders can
and have been switched in and out regularly. These move set finders can also work together and build upon each other to some degree
by being able to adjust other plans found so far / take a set of moves as a guide.
The state finders generally make use of the evaluator functions to help determine which movesets are most likely to be worth following

The elementary components of move set finders / adjusters are found in:  FindBestSoloJourney(), GreedySearch(), MoveBasedGreedySearch(), MonteCarloSearch(), CollisionFixer()
The following more complex systems make use of elementary components to create/adjust plans:    FindBestSoloJourneysMix(), MajorFixemUpper(), GetGreedySpam()
The following are chains of these more complex systems that serve to reliably find a solution: GetMinimal(), GetMinimalExpanded(), GetOldFashioned(), GetNewStyle(), GetSoloEscalation(), GetFixemOnly(), GetSuggestionLed(), GetGreedySpam(),  GetHugeMonte()

In the final version,  GetMinimal() was selected as the best performing complex system on server.
With the final parameters, this only makes use of FindBestSoloJourneysMix(), FindBestSoloJourney() and  CollisionFixer()
Others options either had worse results in game (MonteCarloSearch) or had performance issues on the server (GetOldFashioned, MajorFixemUpper)

Evaluation of states is done through evaluators:  MoveEvaluate(), Evaluate(), FinalEvaluation() and EvaluateTile(),
these are aided by some evaluation done during precalculation (mostly in Prep(),PrepAfterDropoffs(), preCalcEvaluateStuff() and MyBot.PreCalc())
All of these evaluators are used extensively in the final version with MoveEvaluate() dominating most of the final behavior

The elementary move set finders evaluators are also agnostic to the evaluator used. This is used in MajorFixemUpper,
which uses different guiding evaluators to come up with movesets. Since MajorFixemUpper is not used in the final version, neither are these sideevaluators

 */

public class Plan implements Comparable<Plan>{



    //0 = base, 1= inspire, 2=solojourney, 3=first, 4=second, 5 = monte
    public static final int[] bestperType = new int[6];


    public static final int STYLE_COUNT = 24;
    public static final int STYLE_RANDOMIZE = -1;
    public static final int STYLE_DETERMINE_BASED_ON_PLAYERS = -2;
    public static final int STYLE_DETERMINE_BASED_ON_SIZE= -3;
    public static final int STYLE_RANDOMIZE_LIMITED = -4;

    //First results:
    //Done up to Greedy spam, Minimal_3 might be the best overall. Winning slightly over NEWSTYLE_4 and STYLE_OLDFASHIONED
    //STYLE_JOURNEY_ESCALATION_6 and STYLE_MINIMAL_5 were also decent options, winning on some measures
    //STYLE_EXTREMELY_MINIMAL did worst on pretty much all benchmarks, no surprise there. STYLE_FIXEM_ONLY is not doing well either
    //There's some player and rnaking system-based difference, on (percantage)points and the 'success' rankings, there's different results
    //It varies between players too, options STYLE_NEWSTYLE_3 and  STYLE_JOURNEY_ESCALATION_6 seem particularly good on 2p, while on 4p the Minimal variants and STYLE_OLDFASHIONED win out
    //There's map size differences too. It's probably heavily performance based. On 4p and large sizes, approaches that always get something decent out seem better. While on small 2p maps, the best final result may be best
    //Minimal 3 seems to get an advantage if performance is important

    //Second tests:
    //STYLE_MINIMAL_5  seems to win out, with STYLE_SUGGESTION_LED_SOLO_4 not far behind
    //Options: STYLE_MINIMAL_4,STYLE_MINIMAL_3,STYLE_JOURNEY_ESCALATION_3,STYLE_OLDSTYLE_3  arent doing bad either
    //This was a short think time though, so maybe others like STYLE_OLDFASHIONED, STYLE_NEWSTYLE_4, STYLE_JOURNEY_ESCALATION_3 would've still performed well


    public static final int STYLE_OLDFASHIONED = 0;
    public static final int STYLE_MINIMAL_3 = 1;
    public static final int STYLE_MINIMAL_4 = 2;
    public static final int STYLE_MINIMAL_5 = 3;
    public static final int STYLE_EXTREMELY_MINIMAL = 4;
    public static final int STYLE_NEWSTYLE_4 = 5;
    public static final int STYLE_FIXEM_ONLY = 6;
    public static final int STYLE_JOURNEY_ESCALATION_6 = 7;
    public static final int STYLE_GREEDY_SPAM = 8;
    public static final int STYLE_HUGE_MONTE = 9;
    public static final int STYLE_NEWSTYLE_3 = 10;
    public static final int STYLE_OLDSTYLE_3 = 11;
    public static final int STYLE_JOURNEY_ESCALATION_3 = 12;
    public static final int STYLE_SUGGESTION_LED_SOLO_4 = 13;
    public static final int STYLE_SUGGESTION_LED_SOLO_DEEP = 14;
    public static final int STYLE_MINIMAL_3_EXPANDED = 15;
    public static final int STYLE_SUGGESTION_LED_SOLO_5 = 16;
    public static final int STYLE_SUGGESTION_LED_SOLO_6 = 17;
    public static final int STYLE_MINIMAL_6 = 18;
    public static final int STYLE_MINIMAL_6_EXPANDED = 19;
    public static final int STYLE_MINIMAL_4_EXPANDED = 20;
    public static final int STYLE_MINIMAL_10 = 21;
    public static final int STYLE_MINIMAL_5_BROAD = 22;
    public static final int STYLE_NEWSTYLE_9 = 23;

    public static int SEARCH_DEPTH = 10;
    private static int GREEDY_STEPS_BEFORE_SWITCH_TO_LOW_ENERGY = 2;
    private static int SOLO_WIDTH = 20;
    private static int REDO_SOLO_MOVES = 0;

    private static final int GREEDY_EVALUATEFORWARDS = 2;
    //private static final int MINIMUM_TIME_LEFT_IN_MS = 1000;
    private static int MINIMUM_TIME_LEFT_IN_MS;
    private static int stopPrinting = 0;



    public static int[] ruleOfThreeUntil = new int[5000];
    public static int[] ruleOfTwoUntil = new int[5000];
    public static Tile[] ruleOfXTiles = new Tile[5000];


    public static  int[][] turnsFromDropoff;
    public static  int[][] turnsFromEnemyDropoff;
    public static  int[][] turnsFromDangerousEnemyDropoff;
    public static  int[][] turnsToReachByShip;
    public static  int[][] turnsToReachByEnemyShip;
    public static  int[][] distToMeaningfulHalite;
    public static  int[][] lureMap;
    public static float[][] controlEdgeMap;
    public static  float[][] longDistLure;
    public static  float[][] medDistLure;
    public static  float[][][] eLocOdds;
    public static  float[][][] inspireOdds;
    public static  float[][][] cumulativeInspireOdds;
    public static  float[][][] inspireMultiplier;
    public static boolean[][] poossibleCollisionOn;
    public static boolean[][] entrapmentMap;
    public static Tile[] goals = new Tile[0];
    public static Tile[] annoyGoals = new Tile[0];
    public static Tile[] annoyGoalsPreviousTurn = new Tile[0];
    public static Tile[][] simulMovePlan;

    public static HashSet<Move>[] banlist  = null;
    public static HashSet<Tile> allowCollisionsOn;
    public static HashMap<Move,Float>[] suggestionList = null;
    public static HashMap<CheapShip,Integer>[] prioChanges = null;

    public static Plan lastTurnBestPlan;
    public static int turnsLeft;
    public static int idCounter = 0;
//    public ArrayList<Integer> planChain = null;
    private static boolean PRINTER = false;
    public static boolean[][] crossMap;
    public static Move[][] futurePathingSuggestions;
    public static Move[][] lastTurnFuturePathing = new Move[5000][];


    public CheapShip soloShipJourney;
    public float suggestionScore;
    public float movesScore;
//    public final int id;

    public Map savepointMap; //Current WIP map
    public Map WIPMAp; //Current WIP map
    public Map finalMap; //Set after full simulation of the total moveset
    public float finalScore = -1000000000f;
    public float nonfinalScore  = -1000000000f;
    public float nonFinalPlusSuggestions  = -1000000000f;
//    public float finalPlusSuggestions  = -1000000000f;

    public float sortScore  = -1000000000f;
    public int evaluatingTurn = 0; //just the current turn in the greedy algorithm (counted as if we start algo at 0)
    public Move lastMove;
    public  Move[][] movesPerTurn;
    public  Move[][] enemyMovesPerTurn;

    public boolean[] usingOwnReferenceMoveset;
    public boolean[] usingOwnReferenceEnemyMoveset;



    public final int[] playerMoneyPerTurn;
//    public int swaps;
//    public int similarswaps;
    public boolean markedIlllegal;
    public int collisionCount;
    public boolean hasDoneFinalEvaluation = false;

    public static int dropOffRunner = -1;
    public static Tile dropOffSpot = null;
    public static Tile lastTurnDropOffSpot = null;

    public static float timeErrors = 0;
//    public static int illegalMoves = 0;
    public static int timesIntermediatePlanWasBetter = 0;


    public static double EffectiveHps = 0;

    public static Plan basePlan; //the plan with enemy moves
    public static boolean DoEnemySimulation;
    public static boolean ConsiderEnemyShipsInSimulation;
    public static float AverageHalite;

    public static boolean USE_NEW_EVALUATORS;

    static float GatherWeight;
    static float MiscWeight;
    static float WasteWeight;
    static float TurnInWeight;
    static float LureWeight;
    static float MovesWeight;
    static float InspireWeight;
    static float AggressionWeight;
    public static float[][][] maxEvalOnTile;
    public static int[][][] evalCountTile;
    private static ArrayList<CheapShipPair> mayWantToRecheckThese = new ArrayList<>(200);
    private static boolean phase1 = false;
    private static boolean REVERSE_ORDER = false;

    //Probabilistic data structure. We'll place moves in these using slightly different methods
    //Then, we pick the lowest hitcount retrieving the same way
//    public static int[] likelyVisited1;
//    public static int[] likelyVisited2;
//    public static int[] likelyVisited3;

//    public static boolean IMAGING;
//    public static int IMAGINGID = 0;
//    public static int[][][] VISITED_GREEDY;
//    public static int[][][] VISITED_SOLO;
//    public static float[][][] MAX_SCORE;

    public static void Prep(){



        turnsLeft =  Constants.MAX_TURNS -  MyBot.turn; //actually left right now, not at the end of the simulation
        Map.totalSimulationsDoneThisTurn = 0;
        //Roughly speaking, the compute time of the greedy algo is just  ships * depth * tries
        //The computer time of the solo algo is     ships * depth * width
        //Since we already have to account for ships*depth for the greedy, and want to use equal search depths for both (otherwise the eval algo becomes much worse)
        //it makes sense to keep the solo algos width mostly constantish across the ship board.
        //So, were only really focusing on making sure ships*depth is approx equal everywhere of course, we have less
        //flexibility in the amount of tries here, so there is a small decrease in width, in particular near the heavy end

        //right now, we seem to support about 200k simulations on the solo journey reliably, though with more ships, leaning more towards 150k
        DoEnemySimulation = HandwavyWeights.ActivateenemyPredictions == 1;
        ConsiderEnemyShipsInSimulation = DoEnemySimulation ||  (timeErrors < 80 && HandwavyWeights.AllowEShipsInSimulation == 1 );
//        if(MyBot.EXPERIMENTAL_MODE){
//            USE_NEW_EVALUATORS = true;
//            ConsiderEnemyShipsInSimulation = false;
//
//        }
        Map.carefulWereLeaking = 0;

        AverageHalite = ((float)Map.currentMap.haliteSum()) / (float)(Map.width * Map.height);

        if(!MyBot.THINK_QUICKLY){
            // MINIMUM_TIME_LEFT_IN_MS = 75;
            MINIMUM_TIME_LEFT_IN_MS = Math.min(MyBot.MS_BUFFER + 50,Math.max(MyBot.MS_BUFFER, (MyBot.MS_BUFFER-250) + Map.currentMap.myShipsCount * 5));

            if(MyBot.playerCount == 4){
                MINIMUM_TIME_LEFT_IN_MS += 40;
            }

            if(Map.width == 64){
                MINIMUM_TIME_LEFT_IN_MS += 70;
            } else if(Map.width == 56){
                MINIMUM_TIME_LEFT_IN_MS += 10;
            }


        }else{
            MINIMUM_TIME_LEFT_IN_MS = 1000;
        }


        EffectiveHps = Math.min(20f, MyBot.me.GetEffectiveHpsOverXturns(15));

        if(Test.IN_TEST_PROGRESS){
            SEARCH_DEPTH = 6;
        }
        else if(timeErrors > 20 && HandwavyWeights.PLAN_STYLE == STYLE_OLDFASHIONED){
            SEARCH_DEPTH = GetSearchDepth(STYLE_MINIMAL_4);
            SOLO_WIDTH = 5;
//            Log.log("SEVERE TIME ISSUES, REVERT TO SIMPLE", Log.LogType.PLANS);
        }
        else{
            SEARCH_DEPTH = GetSearchDepth(HandwavyWeights.PLAN_STYLE);

            if(Map.staticMyShipCount > 100){
                SEARCH_DEPTH = Math.max(2,SEARCH_DEPTH - 1);
            }
        }

//
//        alreadyGoingThere = new Move[Map.width][Map.height];
        alreadyGoingThereForCount = new Move[Map.width][Map.height];

        //inspireOdds = new float[SEARCH_DEPTH][][];
//        IMAGING = true;//false && MyBot.turn % 50 == 49;
//        if(IMAGING){
//            VISITED_GREEDY = new int[SEARCH_DEPTH][Map.width][Map.height];
//            MAX_SCORE = new float[SEARCH_DEPTH][Map.width][Map.height];
//            VISITED_SOLO = new int[SEARCH_DEPTH][Map.width][Map.height];
//        }


//        likelyVisited1 = new int[Move.likelyVisitedSize];
//        likelyVisited2 = new int[Move.likelyVisitedSize];
//        likelyVisited3 = new int[Move.likelyVisitedSize];



            Stopwatch.Start();
            eLocOdds = EnemyPrediction.GetEnemyLocOdds(SEARCH_DEPTH);
            Stopwatch.Stop("ELocMap");
            EnemyPrediction.SetMurderSpots();
            Stopwatch.Stop("Murderspots");


            turnsFromDropoff = SideAlgorithms.GetTurnsToDropOff();
        SideAlgorithms.SetForecastDropoffDist();
            Stopwatch.Stop("TurnsDropOff");
            turnsFromEnemyDropoff = SideAlgorithms.GetTurnsToEnemyDropOffs(); //TODO: cache
            Stopwatch.Stop("EnemyTurnsDropOff");

            controlEdgeMap = SideAlgorithms.GetControlEdgeAreas();

            Stopwatch.Stop("ControlEdgeMap");
        SideAlgorithms.SetBorders();
            lureMap = SideAlgorithms.GetShortDistLureHaliteMap();
            Stopwatch.Stop("LureMap");

            medDistLure = SideAlgorithms.GetMediumDistLureMap(turnsFromDropoff);
            Stopwatch.Stop("MedLure");
            distToMeaningfulHalite = SideAlgorithms.GetDistanceToMeaningfulSpotsMap();
            Stopwatch.Stop("MeaningfulHaliteV3");
        SideAlgorithms.SetMultipleMoveMaxSums();
            Stopwatch.Stop("MultipleMovesMax");
        SideAlgorithms.UpdateBrawlMap();
            Stopwatch.Stop("BrawlMap");
        SideAlgorithms.CalculateAntiInspire();
            Stopwatch.Stop("AntiInspire");


        if(HandwavyWeights.InspirenewVersion == 1) {
            inspireOdds = SideAlgorithms.InspireoddsNew(Math.min(20, Math.max(9, SEARCH_DEPTH + 1)));
        }else{
            inspireOdds = SideAlgorithms.Inspireodds(Math.min(20, Math.max(9, SEARCH_DEPTH + 1)));
        }




        if(inspireMultiplier == null || inspireMultiplier.length != inspireOdds.length) {
            inspireMultiplier = new float[inspireOdds.length][Map.width][Map.height];
            cumulativeInspireOdds = new float[inspireOdds.length][Map.width][Map.height];
        }
        if(Map.tileList.get(0).inspireMultiplier == null || Map.tileList.get(0).inspireMultiplier.length != inspireOdds.length){
            for(Tile t : Map.tileList){
                t.inspireMultiplier = new float[inspireOdds.length];
                t.inspireOdds = new float[inspireOdds.length];
            }
        }

        for(int t =0; t < inspireOdds.length; t++) {
            int max = Math.min(inspireOdds.length,t+4);

            for (int x = 0; x < Map.width; x++) {
                for (int y = 0; y < Map.height; y++) {
                    inspireMultiplier[t][x][y] = 1.0f +  2.0f * inspireOdds[t][x][y];
                    Map.tiles[x][y].inspireMultiplier[t] = inspireMultiplier[t][x][y];
                    Map.tiles[x][y].inspireOdds[t] = inspireOdds[t][x][y];
                    cumulativeInspireOdds[t][x][y] = 0;
                    for(int i = t; i < max; i++){
                        cumulativeInspireOdds[t][x][y] += inspireOdds[i][x][y];
                    }

                }
            }
        }


        Stopwatch.Stop("InspireMap");
        longDistLure = SideAlgorithms.GetLongDistLureMap(inspireOdds[Math.min(5,SEARCH_DEPTH -1)]); //take the inspire odds 5 turns into the future
        Stopwatch.Stop("LongLureMap");
        entrapmentMap = Annoyers.GetEntrapmentMap();
        Stopwatch.Stop("EntrapmentWeight");
        crossMap = SideAlgorithms.GetCrossMap();
        Stopwatch.Stop("CrossMap");
            if (MyBot.turn < 2 || MyBot.turn % 10 == 0) {
                turnsFromDangerousEnemyDropoff = SideAlgorithms.GetTurnsToDangerousEnemiesDropOff();
            }
            Stopwatch.Stop("Dangerous enemies");
            annoyGoals = Annoyers.AssignAnnoyers();
            Stopwatch.Stop("AnnoyTargetSeeking");



        idCounter = 0;

//        hasReachedGoal = new boolean[Map.staticMyShipCount];
        shipTurnIns = new int[Map.staticMyShipCount];
        shipTurnInsHalite = new int[Map.staticMyShipCount];
        if(banlist == null || banlist.length != SEARCH_DEPTH){
            allowCollisionsOn = new HashSet<>();

            banlist = new HashSet[SEARCH_DEPTH];
            suggestionList = new HashMap[SEARCH_DEPTH];
            prioChanges = new HashMap[SEARCH_DEPTH];
            for(int i = 0; i < SEARCH_DEPTH; i++){
                banlist[i] = new HashSet<>();
                suggestionList[i] = new HashMap();
                prioChanges[i] = new HashMap();
            }
        }else{
            allowCollisionsOn.clear();
            for(int i = 0; i < SEARCH_DEPTH; i++){
                banlist[i].clear();
                suggestionList[i].clear();
                prioChanges[i].clear();
            }
        }

        //TODO: consider whether it's worth it to directly integrate this into the simulation, instead of just doing it from the start
        for(CheapShip s: Map.staticMyShips){
            if(s != null && s.halite < HandwavyWeights.ActivateRuleOfXBelowShipHalite){
                Tile t = Map.tiles[s.x][s.y];

                if(t.haliteStartTurn > Math.min(HandwavyWeights.ActivateRuleOfXMinHalite,AverageHalite * HandwavyWeights.ActivateRuleOfXFactorAvg)){ //What's a good tile? Depends on the amount of halite left
                    boolean doRuleOfThree = true;
                    boolean doRuleOfTwo = true;

                    for(Tile t2 : t.tilesInWalkDistance[3]){

                        if(!t.equals(t2) && !Map.currentMap.IsShipAt(t2)){
                            if(t2.haliteStartTurn > t.haliteStartTurn * HandwavyWeights.ActivateRuleOfThreeTileBetterThanV2) {
                                doRuleOfThree = false;
                            }
                            if(t2.haliteStartTurn > t.haliteStartTurn * HandwavyWeights.ActivateRuleOfTwoTileBetterThan){
                                doRuleOfTwo = false;
                            }
                        }
                    }
                    if(doRuleOfThree){
                        ruleOfThreeUntil[s.id] = MyBot.turn + 3;

                    }
                    if(doRuleOfTwo){
                        ruleOfTwoUntil[s.id] = MyBot.turn + 2;

                    }
                    if(doRuleOfThree || doRuleOfTwo){
                        ruleOfXTiles[s.id] =t;
                    }
                }
            }
        }

        if(MyBot.turn <= 2){
            for(Tile t: Map.tileList){
                t.enemyTerrain = t.turnsFromDropoff - t.turnsFromEnemyDropoff;
            }
        }









        for(Tile t : Map.tileList){
            t.didEvalCalsThisTurn = false;

        }




        if(MyBot.DO_GAME_OUTPUT){
            maxEvalOnTile = new float[SEARCH_DEPTH][Map.width][Map.height];
            evalCountTile = new int[SEARCH_DEPTH][Map.width][Map.height];

            StringBuilder s = new StringBuilder();
            s.append("tilevaluations:");
            for(int y =0; y < Map.height; y++){
                for(int x=0; x < Map.width; x++){
                     EvaluateTile(Map.tiles[x][y]);
                    s.append( (Map.tiles[x][y].evalScoreDependOnTurnAndHaliteImportance+ Map.tiles[x][y].evalScoreFlat) + ",");
                }
                s.append(";");
            }
            GameOutput.info.add(s.toString());


        }

        if(USE_NEW_EVALUATORS){
            PrepNewEvaluator();
        }



    }


    public static void PrepAfterDropoffs(){
        //These things use the planned dropoff spot somewhere (or require data from algos that require this spot)
        Stopwatch.Start();
        if(!Test.IN_TEST_PROGRESS) { //ugh, too annoying to fix
            goals = Goals.GetShipGoals(turnsToReachByShip, longDistLure, goals, inspireOdds);
        }else{
            goals = new Tile[5000];
        }
        Stopwatch.Stop("GoalSeeking");
        WeirdAlgo.SetRecommendations();
        Stopwatch.Stop("WeirdAlgo");
        if(Map.staticMyShipCount * 4 + Map.staticEnemyShipCount2 < 200){
            Stopwatch.Start();
            futurePathingSuggestions = FuturePathing.GetMoveSuggestions();
            Stopwatch.Stop("Future path");
        }else {
            //Future path turned off for now, seems to be worse off

            futurePathingSuggestions = new Move[SEARCH_DEPTH][Map.staticMyShipCount];
        }

        simulMovePlan = SimulatenousJourneys.Do(SEARCH_DEPTH+1);

    }


    //Build multiple full plans, compare them, return the best
    public static Plan FindBestPlan(){

        if(MyBot.turn > MyBot.DIE_TURN && MyBot.DIE_TURN >= 0){
            return null;
        }
        if(Map.currentMap.myShipsCount ==0) return null;
        preCalcEvaluateStuff();
//        Log.log("turns left: " + turnsLeft, Log.LogType.TEMP);

        //Not yet sure what's the correct way of handling search depth yet. Might be better to stick to a constant one
        //to make results more predictable. On the other hand, being able to get high search depths early on is valuable
        //while later on, we'll probably still want to test a decent amount of routes.
        // Later on search depth might also be less important as more ships means lower predictability


        Stopwatch.Begin(9);
        Stopwatch.Begin(10);
        Stopwatch.Begin(11);
        Stopwatch.Begin(12);
        Stopwatch.Begin(13);
        Stopwatch.Begin(14);
        Stopwatch.Begin(15);
        Stopwatch.Begin(16);
        Stopwatch.Begin(17);
        Stopwatch.Begin(18);

        Stopwatch.Begin(19);
        Stopwatch.Begin(20);
        Stopwatch.Begin(21);

        Stopwatch.Begin(27);
        Stopwatch.Begin(28);
        Stopwatch.Begin(29);

        Stopwatch.Begin(36);
        Stopwatch.Begin(37);
        Stopwatch.Begin(38);
        Stopwatch.Begin(52);
        Stopwatch.Begin(56);
        Stopwatch.Begin(57);
        Stopwatch.Begin(58);
        Stopwatch.Begin(59);
        Stopwatch.Begin(60);
        Stopwatch.Begin(61);
        Stopwatch.Begin(62);
        Stopwatch.Begin(63);

        Stopwatch.Begin(1001);
        Stopwatch.Begin(1002);
        Stopwatch.Begin(1003);
        Stopwatch.Begin(1004);
        Stopwatch.Begin(1005);
        Stopwatch.Begin(1006);
        Stopwatch.Begin(1007);
        Stopwatch.Begin(1008);
        Stopwatch.Begin(1009);
        Stopwatch.Begin(1010);
        Stopwatch.Begin(1011);
        Stopwatch.Begin(1012);
        Stopwatch.Begin(1013);
        Stopwatch.Begin(1014);
        Stopwatch.Begin(1015);
        Stopwatch.Begin(1016);
        Stopwatch.Begin(1017);

        for(DropPoint d : Map.myDropoffs){
            allowCollisionsOn.add(Map.tiles[d.x][d.y]);
        }

        Stopwatch.Start();
        basePlan = null; //dont delete this
        basePlan = EnemyPrediction.DoPredictions( SEARCH_DEPTH,HandwavyWeights.PREDICT_ENEMY_TURNS);
        basePlan.StripMyMoves();
        Stopwatch.Stop("EPrediction");


        Plan bestPlan;


//        if(true){
//            basePlan = EnemyPrediction.DoPredictions( SEARCH_DEPTH,HandwavyWeights.PREDICT_ENEMY_TURNS);
//            basePlan.StripMyMoves();
//            Stopwatch.Start(71);
//            bestPlan = MonteCarloSearch(1);
//
//            Stopwatch.Stop(71,"monte carlo");
//        }
//        else
        if(timeErrors > 40 &&  HandwavyWeights.PLAN_STYLE == STYLE_OLDFASHIONED){
             bestPlan = GetPlanFromStyle(STYLE_MINIMAL_4);
        }

        else {
             bestPlan = GetPlanFromStyle(HandwavyWeights.PLAN_STYLE);
        }


        if(MyBot.ALLOW_LOGGING) {
            Stopwatch.PrintAccumulate(9, "Simulating");
            Stopwatch.PrintAccumulate(10, "Evaluating");
            Stopwatch.PrintAccumulate(11, "Finding Possibilities");
            Stopwatch.PrintAccumulate(12, "Plan copying");
            Stopwatch.PrintAccumulate(13, "CloneAndSim - Cloning Maps");
            Stopwatch.PrintAccumulate(14, "DoNothing");
            Stopwatch.PrintAccumulate(15, "step1");
            Stopwatch.PrintAccumulate(16, "step2");
            Stopwatch.PrintAccumulate(17, "step3");
            Stopwatch.PrintAccumulate(18, "queuemoves");


            Stopwatch.PrintAccumulate(19, "tiles");
            Stopwatch.PrintAccumulate(20, "ships");
            Stopwatch.PrintAccumulate(21, "something");


            Stopwatch.PrintAccumulate(27, "clinetileints");
            Stopwatch.PrintAccumulate(28, "initships");
            Stopwatch.PrintAccumulate(29, "initdropoffs");


            Stopwatch.PrintAccumulate(36, "Simulating - Init");
            Stopwatch.PrintAccumulate(37, "Simulating - Queue");
            Stopwatch.PrintAccumulate(38, "Simulating - Execute");
            Stopwatch.PrintAccumulate(52, "Simulating - Sorting plans");

            Stopwatch.PrintAccumulate(56, "Map creation - 1");
            Stopwatch.PrintAccumulate(57, "Map creation - 2");
            Stopwatch.PrintAccumulate(58, "Map creation - 3");
            Stopwatch.PrintAccumulate(59, "Map Cleanups");
            Stopwatch.PrintAccumulate(60, "Map creation - 4");
            Stopwatch.PrintAccumulate(61, "Map creation - 5");
            Stopwatch.PrintAccumulate(62, "Map creation - 6");
            Stopwatch.PrintAccumulate(63, "Map creation - 7");



            for(int i = 1001; i <= 1017; i++) {
                Stopwatch.PrintAccumulate(i, "Eval - " + i);
            }
        }


        lastTurnBestPlan = bestPlan;

        for(Move m: bestPlan.movesPerTurn[0]){
            if(m != null){
                if(!m.isStandStill()){
                    ruleOfThreeUntil[m.ship.id] = -10;
                    ruleOfTwoUntil[m.ship.id] = -10;
                }
            }
        }

        poossibleCollisionOn = new boolean[Map.width][Map.height];
        for(CheapShip s : Map.staticRelevantEnemyShips){

            if(s.halite > MyBot.moveCosts[Map.staticHaliteMap[s.x][s.y]]){
                poossibleCollisionOn[s.x][s.y] = true;
            }else{
                for(Tile t : Map.tiles[s.x][s.y].neighboursAndSelf){
                    poossibleCollisionOn[t.x][t.y] = true;
                }
            }



        }

        if(MyBot.DO_GAME_OUTPUT){

            float[] max = new float[SEARCH_DEPTH];
            float[] min =  new float[SEARCH_DEPTH];
            for(int t = 0 ; t < SEARCH_DEPTH; t++) {
                max[t] = -100000000000000f;
                min[t] = 100000000000000f;
                for (int y = 0; y < Map.height; y++) {
                    for (int x = 0; x < Map.width; x++) {
                        if (maxEvalOnTile[t][x][y] != 0) {
                            max[t] = Math.max(max[t], maxEvalOnTile[t][x][y]);
                            min[t] = Math.min(min[t], maxEvalOnTile[t][x][y]);
                        }
                    }
                }
            }

            for(int t = 0 ; t < SEARCH_DEPTH; t++) {
                StringBuilder s = new StringBuilder();


                if(max[t] - min[t] != 0) {
                    s.append("maxeval" + t + ":");
                    for (int y = 0; y < Map.height; y++) {
                        for (int x = 0; x < Map.width; x++) {
                            s.append((maxEvalOnTile[t][x][y] - min[t]) / (max[t] - min[t]) + ",");
                        }
                        s.append(";");
                    }
                    GameOutput.info.add(s.toString());

                }
                StringBuilder s2 = new StringBuilder();
                s2.append("evalcount"+t+":");
                for (int y = 0; y < Map.height; y++) {
                    for (int x = 0; x < Map.width; x++) {
                        s2.append(evalCountTile[t][x][y] + ",");
                    }
                    s2.append(";");
                }
                GameOutput.info.add(s2.toString());

            }


        }


        bestPlan.OutputToPlanChannel(0);

        return bestPlan;

    }



    public Plan(Map startMap, boolean setMoves){
        if(startMap != null) {
            if (DoEnemySimulation) {
                WIPMAp = new Map(startMap);
            }else{
                WIPMAp = new Map(startMap);
            }
        }
        if(setMoves) {
            usingOwnReferenceMoveset = new boolean[SEARCH_DEPTH];
            movesPerTurn = new Move[SEARCH_DEPTH][Map.staticMyShipCount];
            if (DoEnemySimulation) {
                usingOwnReferenceEnemyMoveset = new boolean[SEARCH_DEPTH];
                enemyMovesPerTurn = new Move[SEARCH_DEPTH][Map.relevantEnemyShipCount];
                for(int i = 0 ; i < SEARCH_DEPTH;i++){
                    usingOwnReferenceMoveset[i] = true;
                    usingOwnReferenceEnemyMoveset[i] = true;
                }
            } else {
                enemyMovesPerTurn = null;
                for(int i = 0 ; i < SEARCH_DEPTH;i++){
                    usingOwnReferenceMoveset[i] = true;
                }
            }
        }
        playerMoneyPerTurn = new int[Math.max(4,SEARCH_DEPTH)];
    }


    //'Fully' copies a plan
    public Plan GetDeepCopy(boolean cloneMaps){
        Stopwatch.Start(12);
        Plan p;

        if(cloneMaps) {
            p = new Plan(WIPMAp,  false);
        }else{
            p = new Plan(null, false);
        }

        p.evaluatingTurn = evaluatingTurn;
        p.suggestionScore = suggestionScore;

        if(cloneMaps && savepointMap != null){
            WIPMAp = new Map(savepointMap);//, (int)(Map.staticMyShipCount * 1.8 + Map.relevantEnemyShipCount * 1.5));
        }



        p.usingOwnReferenceMoveset = new boolean[SEARCH_DEPTH];
        p.movesPerTurn = new Move[SEARCH_DEPTH][];
        if(DoEnemySimulation) {
            p.usingOwnReferenceEnemyMoveset = new boolean[SEARCH_DEPTH];
            p.enemyMovesPerTurn = new Move[SEARCH_DEPTH][];
        }
        for(int turn = 0; turn < SEARCH_DEPTH; turn++){
            p.movesPerTurn[turn] = movesPerTurn[turn];
            usingOwnReferenceMoveset[turn] = false; //set the unique reference property of the objects were cloning FROM to show there might be dangling references
            if(DoEnemySimulation) {
                p.enemyMovesPerTurn[turn] = enemyMovesPerTurn[turn];
                usingOwnReferenceEnemyMoveset[turn] = false;
            }
        }
        p.movesScore = movesScore;
        Stopwatch.StopAccumulate(12);
        return p;
    }

    //Removes all excess, just saves the moveset
    public Plan ClearCopy(){
        Stopwatch.Start(12);
        Plan p = new Plan(null,false);

        p.suggestionScore = suggestionScore;

        p.usingOwnReferenceMoveset = new boolean[SEARCH_DEPTH];
        p.movesPerTurn = new Move[SEARCH_DEPTH][];
        if(DoEnemySimulation) {
            p.usingOwnReferenceEnemyMoveset = new boolean[SEARCH_DEPTH];
            p.enemyMovesPerTurn = new Move[SEARCH_DEPTH][];
        }
        for(int turn = 0; turn < SEARCH_DEPTH; turn++){
            p.movesPerTurn[turn] = movesPerTurn[turn];
            usingOwnReferenceMoveset[turn] = false; //set the unique reference property of the objects were cloning FROM to show there might be dangling references (to is also set to false)
            if(DoEnemySimulation) {
                p.enemyMovesPerTurn[turn] = enemyMovesPerTurn[turn];
                usingOwnReferenceEnemyMoveset[turn] = false;
            }
        }
        p.movesScore = movesScore;
        Stopwatch.StopAccumulate(12);
        return p;
    }


    public void SetMyMove(int turn, Move m){
        if(!usingOwnReferenceMoveset[turn]){
            movesPerTurn[turn] = movesPerTurn[turn].clone();
            usingOwnReferenceMoveset[turn] = true;
        }
        Move oldMove = movesPerTurn[turn][Map.myIndexOfIds[m.ship.id]];
        if(oldMove != null && !oldMove.IgnoreInEval){
            movesScore -= oldMove.score;
        }
        movesPerTurn[turn][Map.myIndexOfIds[m.ship.id]] = m;
        hasDoneFinalEvaluation = false;

        if(!m.IgnoreInEval){
            if(m.score == 0){
                MoveEvaluate(turn,m,Map.myIndexOfIds[m.ship.id],false);
            }
            movesScore += m.score;
        }

//        if(IMAGING && m.ship.id == IMAGINGID){
//            Log.log("Setting move t " + turn + "  " + m, Log.LogType.IMAGING);
//        }
    }
    public void SetEnemyMove(int turn, Move m){
        if(!usingOwnReferenceEnemyMoveset[turn]){
            enemyMovesPerTurn[turn] = enemyMovesPerTurn[turn].clone();
            usingOwnReferenceEnemyMoveset[turn] = true;
        }
        enemyMovesPerTurn[turn][Map.enemyRelevantIndexOfIds[m.ship.id]] = m;
        hasDoneFinalEvaluation = false;
    }

    private void RemoveMyMovesBy(int shipId) {
        //clear out all moves by this ship (from swapping)

        int index = Map.myIndexOfIds[shipId];
        if (index >= 0) {
            for (int turn = 0; turn < SEARCH_DEPTH; turn++) {
                Move oldmove = movesPerTurn[turn][index];
                if (oldmove != null) {


                    Float suggestion = suggestionList[turn].get(oldmove);
                    if (suggestion != null) suggestionScore -= suggestion;

                    if (!oldmove.IgnoreInEval) {
                        movesScore -= oldmove.score;
                    }

                    if (!usingOwnReferenceMoveset[turn]) {
                        movesPerTurn[turn] = movesPerTurn[turn].clone();
                        usingOwnReferenceMoveset[turn] = true;
                    }
                    movesPerTurn[turn][index] = null;
                }

            }
            markedIlllegal = false;
        }
        hasDoneFinalEvaluation = false;
    }
    public void StripMyMoves(){
        for(int turn =0; turn < usingOwnReferenceMoveset.length; turn++){
            usingOwnReferenceMoveset[turn] = true;
            movesPerTurn[turn] = new Move[Map.staticMyShipCount];
        }
        movesScore = 0;
        markedIlllegal = false;
        hasDoneFinalEvaluation = false;
    }

    public void SetWipMap(Map map){
        if(WIPMAp != null && savepointMap != WIPMAp && WIPMAp != finalMap){
            WIPMAp.CleanUp();
        }
        WIPMAp = map;
    }
    public void SetFinalMap(Map map){
        if(finalMap != null && finalMap != WIPMAp && savepointMap != finalMap){
            finalMap.CleanUp();
        }
        finalMap = map;
    }
    public void SetSavepointMap(Map map){
        if(savepointMap != null && savepointMap != WIPMAp && savepointMap != finalMap){
            savepointMap.CleanUp();
        }
        savepointMap = map;
    }

    private void CleanUpMaps(){
        boolean cleanedFinal = finalMap == null;
        boolean cleanedSave = savepointMap == null;

        if(WIPMAp != null){
            WIPMAp.CleanUp();
            if(WIPMAp == finalMap){
                cleanedFinal = true;
            }
            if(WIPMAp == savepointMap){
                cleanedSave = true;
            }
            WIPMAp = null;
        }

        if(!cleanedFinal){
            finalMap.CleanUp();
            if(finalMap == savepointMap){
                cleanedSave = true;
            }
            finalMap = null;
        }
        if(!cleanedSave){
            savepointMap.CleanUp();
            savepointMap = null;
        }
    }

    //clears some variables that can be set during the process, but which are not welcome
    private void ClearGunk(){
        CleanUpMaps();
        finalScore = 0;
        sortScore = 0;
        nonFinalPlusSuggestions = 0;
        nonfinalScore = 0;
        evaluatingTurn = 0;
        markedIlllegal = false;
        //Not sure what to do about playermoneyperturn and last move, weird variables
    }


    public ArrayList<Plan> findhighestPrioPossibilities(Map map, int onTurn) {
        //TODO: consider whether its better to use the top 3 ships or something

        Stopwatch.Start(11);
        ArrayList<Plan> possibilities = new ArrayList<>();

        CheapShip bestShip = null;
        float bestprio = -10000000f;

        for(CheapShip s : map.myShips){
            if(s != null && movesPerTurn[onTurn][Map.myIndexOfIds[s.id]] == null) {
                float prio = CheapPriorityEvalSorter(s,map);
                if (prio > bestprio) {
                    bestprio = prio;
                    bestShip = s;
                }
            }
        }

        if(bestShip != null){
            //We should've already handled the forced moves, so any ship left can in theory move in all possible directions (though some may lead to collisions)
            Tile shipTile = map.GetTile(bestShip);

            Move m = new Move(shipTile, shipTile,bestShip);
            if(!banlist[onTurn].contains(m)){
                possibilities.add(GetNewPlanAfterMove(m,onTurn));
            }

            for(Tile t : shipTile.GetNeighbours()){
                m = new Move(shipTile, t,bestShip);
                if(!banlist[onTurn].contains(m)){
                    possibilities.add(GetNewPlanAfterMove(m,onTurn));
                }
            }
        }

        //  Log.log("shipcount: " + shipcount + "  -  " + goodships + "  possible " + possibletiles + " -  " + goodtiles   + "  - "  +   possibilities.size(), Log.LogType.PLANS);

        Stopwatch.StopAccumulate(11);
        return  possibilities;

    }

    //dont use in greedy, unless we handle the swapping mechanism somehow
    public ArrayList<Plan> findAllStepsForShip(CheapShip s, Map map, int doMovesOnTurn) {
        Stopwatch.Start(11);
        ArrayList<Plan> possibilities = new ArrayList<>();

        Tile shipTile = Map.tiles[s.x][s.y];

        if( MyBot.moveCostsSafe[map.GetHaliteAt(s) + 1000]  > s.halite ){
            possibilities.add(GetNewPlanAfterMove(new Move(shipTile, shipTile,s),doMovesOnTurn));
        }else{
            possibilities.add(GetNewPlanAfterMove(new Move(shipTile, shipTile,s),doMovesOnTurn));
            for(Tile t : shipTile.GetNeighbours()){
                boolean addedSwap = false;
                CheapShip blockingShip = map.GetShipAt(t);

                if(blockingShip != null && s.id != blockingShip.id &&  Map.DoIOwnShip[blockingShip.id] && Map.myIndexOfIds[blockingShip.id] >= 0){
                    int cost = MyBot.moveCostsSafe[map.GetHaliteAt(t)  + 1000];
                    if(cost <= blockingShip.halite && movesPerTurn[doMovesOnTurn][Map.myIndexOfIds[blockingShip.id]] == null) {
                        possibilities.add(GetNewPlanAfterSwap(new Move(shipTile, t, s), blockingShip,doMovesOnTurn));
                        addedSwap = true;
                    }
                }

                if(!addedSwap || turnsLeft < 10){
                    possibilities.add(GetNewPlanAfterMove(new Move(shipTile, t,s),doMovesOnTurn));
                }
            }

        }
        Stopwatch.StopAccumulate(11);
        return possibilities;
    }

    public ArrayList<Plan> findSimulatedSteps(CheapShip s, boolean addToCheckAgain, double prioOfOriginal, CheapShip ressurectThisShipIfMoving, Map findPossibilitiesOn, Map simulateOn,int doMovesOnTurn) {

        ArrayList<Plan> possibilities = new ArrayList<>(5);

        Tile shipTile = Map.tiles[s.x][s.y];

        if( MyBot.moveCostsSafe[findPossibilitiesOn.GetHaliteAt(s) + 1000]  > s.halite ){
            //Log.log("need more pylons" + s.halite +   "   "  +  WIPMAp.GetHaliteAt(s), Log.LogType.PLANS );
            possibilities.add(GetSimulatedPlanAfterMove(new Move(shipTile, shipTile,s),ressurectThisShipIfMoving,simulateOn,doMovesOnTurn));
        }else{
            possibilities.add(GetSimulatedPlanAfterMove(new Move(shipTile, shipTile,s),ressurectThisShipIfMoving,simulateOn,doMovesOnTurn));

            for(Tile t : shipTile.GetNeighbours()){
                boolean addedSwap = false, banMove = false;
                CheapShip blockingShip = findPossibilitiesOn.GetShipAt(t);

                if(blockingShip != null && s.id != blockingShip.id &&  Map.DoIOwnShip[blockingShip.id] && Map.myIndexOfIds[blockingShip.id] >= 0){
                    if (addToCheckAgain && basePlan.CheapPriorityEvalSorter(blockingShip,findPossibilitiesOn) > prioOfOriginal) {
                        //The other ship has already moved. Let's try and reconsider the move order of these later if we have time
                        mayWantToRecheckThese.add(new CheapShipPair(s, blockingShip));
                    }
                    int cost = MyBot.moveCostsSafe[findPossibilitiesOn.GetHaliteAt(t)  + 1000];

                    if(cost <= blockingShip.halite) {
                        if (movesPerTurn[doMovesOnTurn][Map.myIndexOfIds[blockingShip.id]] == null){
                            possibilities.add(GetSimulatedPlanAfterSwap(new Move(shipTile, t, s), blockingShip, simulateOn, doMovesOnTurn, ressurectThisShipIfMoving));
                            addedSwap = true;
                        }
                    }
                    else{
                        banMove = true;
                    }
                }

                if(!banMove && (!addedSwap || turnsLeft < 10)){
                    Move m = new Move(shipTile, t,s);
                    if(!banlist[doMovesOnTurn].contains(m)) {
                        possibilities.add(GetSimulatedPlanAfterMove(m, ressurectThisShipIfMoving, simulateOn, doMovesOnTurn));
                    }
                }

            }
        }
        return possibilities;
    }

    public ArrayList<Move> findAllMovesForEnemyShip(CheapShip s, Map map) {
        ArrayList<Move> possibilities = new ArrayList<>();

        Tile shipTile = Map.tiles[s.x][s.y];
        if( map.GetHaliteAt(s) / Constants.MOVE_COST_RATIO     > s.halite ){
            //Log.log("need more pylons" + s.halite +   "   "  +  map.GetHaliteAt(s), Log.LogType.PLANS );
            possibilities.add(new Move(shipTile, shipTile,s));
        }else{
            possibilities.add(new Move(shipTile, shipTile,s));
            for(Tile t : shipTile.GetNeighbours()){
                CheapShip blockingShip = map.GetShipAt(t);

                if(blockingShip == null || Map.OwnerOfShip[blockingShip.id] != Map.OwnerOfShip[s.id]) {
                    //Ignoring swaps for now on enemy ships. Just stop same team moves from happening

                    possibilities.add(new Move(shipTile, t, s));
                }
            }
        }
        return possibilities;
    }
    public float CheapPriorityEvalSorter(CheapShip s, Map map){
        //Based only on lastmove, done during
        Tile shipTile = map.GetTile(s);
        float prio;
        if(turnsLeft < 15){
            prio = -turnsFromDropoff[s.x][s.y] * 100;
        }else {
            prio = map.GetHaliteAt(shipTile) * HandwavyWeights.PrioWeightTileHalite;

            prio += s.halite * HandwavyWeights.PrioWeightHalite;
            if (turnsFromDropoff[s.x][s.y] <= 1) {
                prio += HandwavyWeights.PrioBoostNextToDropoffV2;
                if (turnsFromDropoff[s.x][s.y] == 0) {
                    prio += 100000; //always make these go first
                }
            }
            for (Tile t : shipTile.GetNeighbours()) {
                if (map.IsEnemyShipAt(t)) {
                    prio += HandwavyWeights.PrioNextToEnemy;
                }
            }

            prio += shipTile.enemyShipsStartInRange4Avg * HandwavyWeights.PrioNearbyEnemyShips;
            prio += shipTile.myShipsStartInRange4Avg  * HandwavyWeights.PrioNearbyMyShips;

            if (prioChanges[evaluatingTurn] != null) {
                Integer i = prioChanges[evaluatingTurn].get(s);
                if (i != null) {
                    prio -= i;
                }
            }
        }
        if(REVERSE_ORDER){
            prio *= -1;
        }
        return prio;
    }

    //Some ships won't be able to move
    public void AddAllForcedMoves(Map map,int turn){

        for(CheapShip s : map.myShips){
            if(s != null) {
                Tile shipTile = map.GetTile(s);
                if (MyBot.moveCostsSafe[map.GetHaliteAt(s) + 1000] > s.halite) {
                    //Forced to stand still
                    SetMyMove(turn,new Move(shipTile, shipTile, s));
                }
            }
        }
        //TODO: Maybe include other types of forced moves. Might be good to have some ships force collisions etc.
    }


    public Plan GetNewPlanAfterMove(Move m,int doMovesOnTurn){
        Plan p = ClearCopy();
        p.SetMyMove(doMovesOnTurn,m);
        p.lastMove = m;
        return p;
    }
    public Plan GetNewPlanAfterSwap(Move m, CheapShip swappingwith,int doMovesOnTurn){
        Plan p = ClearCopy();
        Move reverse = new Move(m.to,m.from,swappingwith);
        reverse.IgnoreInEval = true; //this move should be judged on its own merit later, not included in the swap intiating move
        p.SetMyMove(doMovesOnTurn,m);
        p.SetMyMove(doMovesOnTurn,reverse);
        p.lastMove = m;
        return p;
    }

    public Plan GetSimulatedPlanAfterMove(Move m, CheapShip thisTurtleCrashedIntoMe, Map simulateOn,int doMovesOnTurn){
        Plan p = GetDeepCopy(false);
        p.SetMyMove(doMovesOnTurn,m);

        p.SetWipMap( new Map(simulateOn));
        p.WIPMAp.SimulateSingleMove(doMovesOnTurn,m,null, thisTurtleCrashedIntoMe);

        if(simulateOn.removeThese != null){
            for(Tile t : simulateOn.removeThese){
                if(p.WIPMAp.ignoreTileRemoval == null || !t.equals(p.WIPMAp.ignoreTileRemoval)) {
                    if (p.WIPMAp.shipMap[t.tileIndex] != null) {
                        int id = p.WIPMAp.shipMap[t.tileIndex].id;

                        if (Map.DoIOwnShip[id]) {
                            p.WIPMAp.myShips[Map.myIndexOfIds[id]] = null;
                        } else {
                            p.WIPMAp.enemyShipsRelevant[Map.enemyRelevantIndexOfIds[id]] = null;
                        }
                        p.WIPMAp.shipMap[t.tileIndex] = null;
                    }
                }
            }
        }

        p.lastMove = m;

        return p;
    }

    public Plan GetSimulatedPlanAfterSwap(Move m, CheapShip swappingwith, Map simulateOn,int doMovesOnTurn, CheapShip crashingIntoMe){
        Plan p = GetDeepCopy(false);

        Move reverse = new Move(m.to,m.from,swappingwith);
        reverse.IgnoreInEval = true; //this move should be judged on its own merit later, not included in the swap intiating move
//        DebugConfirm(m.ship.id,doMovesOnTurn);
        p.SetMyMove(doMovesOnTurn,m);
        p.SetMyMove(doMovesOnTurn,reverse);

//        p.movesPerTurn[doMovesOnTurn][Map.myIndexOfIds[m.ship.id]] = m;
//        DebugConfirm(swappingwith.id,doMovesOnTurn);
//        p.movesPerTurn[doMovesOnTurn][Map.myIndexOfIds[swappingwith.id]] = reverse;
//        DebugConfirm(-1,doMovesOnTurn);
//        p.swaps++;
//        if( Math.abs(m.ship.halite - swappingwith.halite) < 50){
//            p.similarswaps++;
//        }
        p.lastMove = m;

        p.SetWipMap(new Map(simulateOn));

        p.WIPMAp.SimulateSingleMove(evaluatingTurn,m,swappingwith,crashingIntoMe);
        p.WIPMAp.SimulateSingleMove(evaluatingTurn,reverse,m.ship,null);


        if(simulateOn.removeThese != null){
            for(Tile t : simulateOn.removeThese){
                if(p.WIPMAp.ignoreTileRemoval == null || !t.equals(p.WIPMAp.ignoreTileRemoval)) {
                    if (p.WIPMAp.shipMap[t.tileIndex] != null) {
                        int id = p.WIPMAp.shipMap[t.tileIndex].id;

                        if (Map.DoIOwnShip[id]) {
                            p.WIPMAp.myShips[Map.myIndexOfIds[id]] = null;
                        } else {
                            p.WIPMAp.enemyShipsRelevant[Map.enemyRelevantIndexOfIds[id]] = null;
                        }
                        p.WIPMAp.shipMap[t.tileIndex] = null;
                    }
                }
            }
        }

        return p;
    }



    public Map CloneAndSim(int startTurn, int endTurn, boolean simNonMovers, Map map, boolean deleteDeadShipsLastTurn, int doNotSimShipId){
        endTurn = Math.min(SEARCH_DEPTH, endTurn);

        Stopwatch.Start(13);


        map = new Map(map);//,(int)   (endTurn - startTurn) *  (int)(Map.staticMyShipCount * 1.6 ) +  (int)(Map.relevantEnemyShipCount * 1.5) );


        Stopwatch.StopAccumulate(13);
        Stopwatch.Start(9);
        Move[] moves;
        for(int turn = startTurn; turn <  endTurn; turn++){
//            Stopwatch.Start(36);
            map.NewSimInit(simNonMovers,doNotSimShipId);
//            Stopwatch.StopAccumulate(36);
//            Stopwatch.Start(37);
            moves = movesPerTurn[turn];
            for(int moveindex = 0; moveindex < Map.staticMyShipCount;moveindex++) {
                if (moves[moveindex] != null) {
                    map.QueueSimulatedMove(moves[moveindex]);
                }
            }

            if(Plan.DoEnemySimulation) {
                for(Move m : enemyMovesPerTurn[turn]){
                    if(m != null) {
                        map.QueueEnemySimulatedMove(m);
                    }
                }
            }
//            Stopwatch.StopAccumulate(37);
//            Stopwatch.Start(38);
            map.Simulate(turn, deleteDeadShipsLastTurn || turn < startTurn - 1); //the choice whether to delete dead ships only matters for the last sim turn

//            Stopwatch.StopAccumulate(38);
        }
        Stopwatch.StopAccumulate(9);

        //Log.log("Finish sum", Log.LogType.MAIN);
        return map;
    }

    @Override
    public int compareTo(Plan other) {
        //Higher scores appear first in a list
        return Float.compare(other.sortScore,this.sortScore);
    }



    private void DebugConfirm(int willmoveship,int onturn){
        HashSet<Integer>[] seen = new HashSet[SEARCH_DEPTH];

        for(int turn = 0; turn < SEARCH_DEPTH; turn++){
            seen[turn] = new HashSet<>();

            for(int moveindex =0; moveindex < Map.staticMyShipCount; moveindex++){
                if(movesPerTurn[turn][moveindex] != null) {
                    if(turn == onturn && movesPerTurn[turn][moveindex].ship.id == willmoveship){
                        String bug = "sdfs";
                    }
                    if (!seen[turn].contains(movesPerTurn[turn][moveindex].ship.id)){
                        seen[turn].add(movesPerTurn[turn][moveindex].ship.id);
                    }else{
                        String bug = "hoi";
                    }

                }
            }
        }

    }


    public String movesToString(){

        StringBuilder s = new StringBuilder();

        for(int i = 0 ; i < SEARCH_DEPTH; i++){
            s.append("Turn ").append(i).append("  -  ");
            for(Move m : movesPerTurn[i]){
                if(m != null) {
                    s.append(m.ship.id).append(": ").append(m.toString()).append(",  ");
                }
            }
            s.append("\r\n");
        }

        return s.toString();

    }





    //Runs a FindSoloJourney for every ship, then, for cases where two ships might be fighting over tiles, try running them
    //both in opposite order and see if that's better (the first ship will try claiming the best path available)
    public static Plan FindBestSoloJourneysMix(Plan startplan,boolean autoAccept, boolean allowSwitch, boolean doTimeErrors) {
        phase1 = true;
        mayWantToRecheckThese.clear();
        //Plan plan = new Plan(Map.currentMap,SEARCH_DEPTH,null);
        Plan plan = startplan.GetDeepCopy(false);

        if(doTimeErrors) {
            timeErrors -= 0.2f;
            timeErrors = Math.max(timeErrors, 0f);
        }

        if(autoAccept) {
            plan.finalScore = -10000000000000f;
        }
        else{
            plan.FinalEvaluation();
        }

        TreeSet<SortableShip> ships = new TreeSet<>();

        int triedtoadd = 0;
        for (CheapShip s : Map.currentMap.myShips) {
            if (s != null) {
                triedtoadd++;
                ships.add(new SortableShip(s, basePlan.CheapPriorityEvalSorter(s, Map.currentMap) + MyBot.rand.nextFloat() * 0.01f)); //randomness is a bandaid to deal with equal prio ships not getting added
            }
        }
//        Log.log("Have " + Map.currentMap.myShips.length + " to check  ( " + ships.size() + "/" + triedtoadd + ")", Log.LogType.PLANS);
        int checked = 0;
        boolean alreadyIncrementedTime = false;

        Iterator<SortableShip> iter = ships.iterator();
        while (iter.hasNext()) {

            int turnDepth = SEARCH_DEPTH;

            if (!MyBot.DETERMINISTIC_TIME_INDEPENDENT && !Test.IN_TEST_PROGRESS) {
                long timeleft = (MyBot.TIME_ALLOWED - (System.currentTimeMillis() - MyBot.startTurn)) - MINIMUM_TIME_LEFT_IN_MS;
                if (timeleft < 50) {
                    if(doTimeErrors) {
                        timeErrors += 10;
                    }
//                    Log.log("Aborting journey, no time left ", Log.LogType.PLANS);
                    return plan; //welp, it aint everything, but it's something
                } else if (timeleft < 100) {
                    //just finish it off
                    SOLO_WIDTH = Math.min(2, SOLO_WIDTH);
                    turnDepth = Math.min(4, turnDepth);
                    if (!alreadyIncrementedTime) {
                        if(doTimeErrors) {
                            timeErrors += 4;
                        }
                        alreadyIncrementedTime = true;
                    }
                } else if (timeleft < 300) {
                    SOLO_WIDTH = Math.min(5, SOLO_WIDTH);
                    turnDepth = Math.min(8, turnDepth);
                    if(doTimeErrors) {
                        timeErrors++;
                    }
                } else if (timeleft < 400) {
                    SOLO_WIDTH = Math.min(10, SOLO_WIDTH);
                    turnDepth = Math.min(8, turnDepth);
                } else if (timeleft < 700) {
                    SOLO_WIDTH = Math.min(20, SOLO_WIDTH);
                    turnDepth = Math.min(10, turnDepth);
                } else if (timeleft < 1000) {
                    SOLO_WIDTH = Math.min(30, SOLO_WIDTH);
                }
            }

            CheapShip ship = iter.next().ship;


            Plan workingWith;
            if (autoAccept) {
                workingWith = plan;
            } else {
                workingWith = plan.ClearCopy();
            }

            workingWith.RemoveMyMovesBy(ship.id);

            Plan result = FindBestSoloJourney(ship, workingWith, turnDepth, SOLO_WIDTH, 0, 0);
//            Log.log("Leaked1  "  + Map.leakedMaps, Log.LogType.PLANS);

            //Log.log("________________" + result.finalScore + "_______________", Log.LogType.PLANS);
            checked++;
            if(result != null) {
                if (autoAccept || result.finalScore > plan.finalScore){
                    if (plan != null) {
                        plan.CleanUpMaps();
                    }
                    plan = result;
                }else{
                    result.CleanUpMaps();
                }

            }
        }


        if(HandwavyWeights.DoGreedyMoveInMix == 1){
            Plan prepGreedyPlan = plan.ClearCopy();
            for(CheapShipPair p : mayWantToRecheckThese){
                prepGreedyPlan.RemoveMyMovesBy(p.s1.id);
                prepGreedyPlan.RemoveMyMovesBy(p.s2.id);
            }
            Plan greedyPlan = GreedySearch(SEARCH_DEPTH,0f,prepGreedyPlan,0,false);
            if(greedyPlan.nonfinalScore > plan.nonfinalScore){
                timesIntermediatePlanWasBetter++;
                plan.CleanUpMaps();
                plan = greedyPlan;
            }
            else{
                greedyPlan.CleanUpMaps();
            }
            prepGreedyPlan.CleanUpMaps();

//            Log.log("Intermediate results: " + timesIntermediatePlanWasBetter + " " + MyBot.turn, Log.LogType.PLANS);
        }

        //  Log.log("Initial solo journey plan (" + checked+ "): " + plan.finalResultToString(true), Log.LogType.PLANS);

        phase1 = false;
        if (allowSwitch) {
            int doneTooMany = 40;

            if (turnsLeft < HandwavyWeights.RunLeftTimerV3) {
                doneTooMany = 30;
            }

            while ((MyBot.TIME_ALLOWED - (System.currentTimeMillis() - MyBot.startTurn)) - MINIMUM_TIME_LEFT_IN_MS > 100 && mayWantToRecheckThese.size() > 0 && Map.carefulWereLeaking < 30) {

                if (doneTooMany-- <= 0) break;

                //Take the last ship first, want to reverse order now for better results
                CheapShipPair pair = mayWantToRecheckThese.remove(mayWantToRecheckThese.size() - 1);//mayWantToRecheckThese.remove(mayWantToRecheckThese.size() - 1);
                CheapShip s1 = Map.GetFreshShip(pair.s1.id);
                CheapShip s2 = Map.GetFreshShip(pair.s2.id);

                if (s1 == null || s2 == null) {
//                    Log.log("Couldnt get fresh ship", Log.LogType.MAIN);
                    continue;
                }
//            Log.log("Reconsidering: " + s1.id + " - " + s2.id, Log.LogType.PLANS);

                Plan newplan = plan.ClearCopy();


                newplan.RemoveMyMovesBy(s1.id);
                newplan.RemoveMyMovesBy(s2.id);

                //Now start the sim with the other ship moving first
                newplan = FindBestSoloJourney(s1, newplan, SEARCH_DEPTH, SOLO_WIDTH, 0, 0).ClearCopy();
//            Log.log("Leaked2  "  + Map.leakedMaps, Log.LogType.PLANS);

                newplan.RemoveMyMovesBy(s2.id);

                //Then have the other ship move
                Plan newplan2 = FindBestSoloJourney(s2, newplan, SEARCH_DEPTH, SOLO_WIDTH, 0, 0);
                newplan.CleanUpMaps();
//            Log.log("Leaked3  "  + Map.leakedMaps, Log.LogType.PLANS);


                if (newplan2.finalScore > plan.finalScore) {
                    // Log.log("Found a better plan! " + newplan.finalResultToString(true), Log.LogType.PLANS);
                    if (plan != null) {
                        plan.CleanUpMaps();
                    }
                    plan = newplan2;
                }else{
                    newplan2.CleanUpMaps();
                }

            }
        }


        //Reconsider ships that had early collision potential. In rare cases, we could be doing the entire thing again
//        while(MyBot.TIME_ALLOWED -  (System.currentTimeMillis() - MyBot.startTurn) > 800  && mayWantToRecheckThese.size() > 0) {
//            //Take the last ship first, want to reverse order now for better results
//            CheapShip ship = mayWantToRecheckThese.remove(mayWantToRecheckThese.size() - 1);
//            Log.log("Reconsidering: " + ship.id, Log.LogType.PLANS);
//
//            Plan newplan = plan.GetDeepCopy();
//
//            //clear out all moves by this ship
//            for (int turn = 0; turn < SEARCH_DEPTH; turn++) {
//                int size = newplan.movesPerTurn[turn].size();
//                for (int n = 0; n < size; n++) {
//                    if (newplan.movesPerTurn[turn].get(n).ship.id == ship.id) {
//                        newplan.movesPerTurn[turn].remove(n);
//                        break;
//                    }
//                }
//            }
//           // Log.log("_______________________________", Log.LogType.PLANS);
//
//            newplan = FindBestSoloJourney(ship, newplan, SEARCH_DEPTH, SOLO_WIDTH);
//         //   Log.log("________________" + newplan.finalScore + "_______________", Log.LogType.PLANS);
//
//
//            if (newplan.finalScore > plan.finalScore) {
//                Log.log("Found a better plan! " + newplan.finalScore);
//                plan = newplan;
//            }
//
//        }

        //make sure this is in the expected state
        plan.FinalEvaluation();
        return plan;
    }


    //For indepth testing
    public static boolean USE_OLD_METHOD = false;
    public static boolean USE_OLD_METHOD_FIRST = true;
    public static HashMap<Long,Float> scoreOnMoveSet = new HashMap<>();


    //This tries to find a good path for one ship, within an environment where other ships may or may not have already made moves
    //At the start, it takes all possible moves and turns them into plans
    //It then filters out the weakest plans till we have capitems items left
    //It keeps the remaining, and finds all possible moves from those remaining plans
    //Repeat these two steps till we've reached the turn we want to reach, then pick the best very best plan and return it
    private static Plan FindBestSoloJourney(CheapShip s, Plan startPlan, int turns, int capitems, int startturn, int EvaluatorType){
        startPlan = startPlan.ClearCopy();

        ArrayList<Plan> bestPlans = new ArrayList<>();

        if(startturn > 0){
            startPlan.SetWipMap(startPlan.CloneAndSim(0,startturn,true,Map.currentMap,true,-1));
            startPlan.SetSavepointMap(startPlan.WIPMAp);

        }else {
            startPlan.SetWipMap( new Map(Map.currentMap));
            startPlan.SetSavepointMap(startPlan.WIPMAp);
        }
        bestPlans.add(startPlan);
        int id = s.id;
        Plan best = startPlan;

        best.finalScore = -10000000000000f;
        double prio = startPlan.CheapPriorityEvalSorter(s,startPlan.savepointMap);

//        int didstuffcounter = 0;
//        int didstuffcounter2 = 0;
//        int deadcounter = 0;
//        int deadcounter2 = 0;
//        int lastshiphalite = 0;
//        int lasttilehalite = 0;


//        Log.log("checking ship: " + s.id, Log.LogType.PLANS);

//        if(IMAGING && id == IMAGINGID) {
//            Log.log("Solo journey start", Log.LogType.IMAGING);
//        }

        int turn = startturn;
        for(; turn < turns; turn++) {
//            Log.log("turn: " + turn, Log.LogType.PLANS);


//            if(IMAGING && id == IMAGINGID) {
//                Log.log("Solo journey turn  " + turn, Log.LogType.IMAGING);
//            }

            if (turn == turns - 1) {
                capitems = Math.min(20, capitems);
            }
            ArrayList<Plan> items = new ArrayList<>(bestPlans.size() * 5);



            for (Plan planLastTurn : bestPlans) {
                s = planLastTurn.WIPMAp.GetShipById(id);
//                didstuffcounter++;
//                if(IMAGING && id == IMAGINGID) {
//                    Log.log("Solo journey exploring possibility " + planLastTurn.movesToString(), Log.LogType.IMAGING);
//                }

                if (s == null) {  //I guess it died?
//                    deadcounter++;
//                    if(IMAGING && id == IMAGINGID) {
//                        Log.log("Dead? ", Log.LogType.IMAGING);
//                    }

                    if(MyBot.turnsLeft < HandwavyWeights.RunLeftTimerV3 || CountCollisions(planLastTurn) == 0) {
                        planLastTurn.FinalEvaluation();
                        if (!planLastTurn.markedIlllegal && planLastTurn.finalScore > best.finalScore) {
                            best.CleanUpMaps();
                            best = planLastTurn;
//                            deadcounter2++;
                        } else {
                            planLastTurn.CleanUpMaps();
                        }
                    }else{
                        planLastTurn.CleanUpMaps();
                    }
                } else {
                    planLastTurn.evaluatingTurn = turn;
                    if (ShouldQuit()) {
//                        Log.log("quitting, out of time", Log.LogType.PLANS);
                        best.FinalEvaluation();
                        return best;
                    }


//                    long presum = (int)planLastTurn.nonfinalScore * planLastTurn.savepointMap.haliteSum() * turn; //MyBot.rand.nextInt(10000);
//                    if(Test.IN_TEST_PROGRESS) {
//                        for (int testturn = 0; testturn < SEARCH_DEPTH; testturn++) {
//                            for (int index = 0; index < Map.staticMyShipCount; index++) {
//                                if (planLastTurn.movesPerTurn[testturn][index] != null) {
//                                    presum += planLastTurn.movesPerTurn[testturn][index].hashCode();
//                                }
//                            }
//                        }
//                    }
//                    if(presum == 1668499519){
//                        boolean b = true;
//                    }
//                    if(USE_OLD_METHOD) {
//
//                        Map baseMap = planLastTurn.savepointMap;
//                        ArrayList<Plan> possibilities = planLastTurn.findAllStepsForShip(s,baseMap,turn);
//                        for (Plan possibility : possibilities) {
//                            if (turn == 0 && phase1) {
//                                CheapShip alreadyThere = baseMap.GetShipAt(possibility.lastMove.to);
//                                if (alreadyThere != null && alreadyThere.id != id && Map.DoIOwnShip[alreadyThere.id] && basePlan.CheapPriorityEvalSorter(alreadyThere,baseMap) > prio) {
//                                    //The other ship has already moved. Let's try and reconsider the move order of these later if we have time
//                                    mayWantToRecheckThese.add(new CheapShipPair(s, alreadyThere));
//                                }
//                            }
//                            possibility.evaluatingTurn = turn;
//                            Float f = suggestionList[turn].get(possibility.lastMove);
//                            if (f != null) possibility.suggestionScore += f;
//                            possibility.WIPMAp = possibility.CloneAndSim(turn, Math.min(turn + 1, turns), false, baseMap,true);
//                            possibility.finalMap = possibility.WIPMAp;
//                            possibility.savepointMap = possibility.WIPMAp;
//                            possibility.playerMoneyPerTurn[turn] = possibility.finalMap.playerMoney;
//                            possibility.soloShipJourney = s;
//
//                            long halitesum =  possibility.WIPMAp.haliteSum();
//                            long movesum = 0;
//                            long sum = (int)planLastTurn.nonfinalScore * halitesum * turn; //MyBot.rand.nextInt(10000);
//                            if(Test.IN_TEST_PROGRESS) {
//
//                                for (int testturn = 0; testturn < SEARCH_DEPTH; testturn++) {
//                                    for (int index = 0; index < Map.staticMyShipCount; index++) {
//                                        if (possibility.movesPerTurn[testturn][index] != null) {
//                                            movesum += possibility.movesPerTurn[testturn][index].hashCode();
//                                        }
//                                    }
//                                }
//                            }
//                            sum += movesum;
//
//                            if(Test.IN_TEST_PROGRESS && (sum == 2053733867    || (turn == 5 && id == 3 && possibility.lastMove.from.equals(4,4) && possibility.lastMove.to.equals(4,0)))){
//                                Log.log("Old: " + possibility.lastMove + "  "  +  possibility.nonFinalPlusSuggestions, Log.LogType.TESTS);
//                            }
//
//                            possibility.EvaluatePicker(EvaluatorType);
//                            likelyVisited1[possibility.lastMove.GetLikelyIndex1(turn)]++;
//                            likelyVisited2[possibility.lastMove.GetLikelyIndex2(turn)]++;
//                            likelyVisited3[possibility.lastMove.GetLikelyIndex3(turn)]++;
//                            items.add(possibility);
//
//
//                            if(Test.IN_TEST_PROGRESS){
//                                if(USE_OLD_METHOD_FIRST) {
//                                    scoreOnMoveSet.put(sum, possibility.nonFinalPlusSuggestions);
//                                }else{
//                                    if(!scoreOnMoveSet.containsKey(sum)){
//                                        Log.log("Move not found at all!!  Turn: " + turn + "  ship: " +  id  + "  "  + possibility.lastMove  + " sum: " + sum + " presum: " + presum, Log.LogType.TESTS);
//                                    }else{
//                                        if(scoreOnMoveSet.get(sum) != possibility.nonFinalPlusSuggestions){
//                                            Log.log("Eval difference!! " + possibility.lastMove + " ship: " +  id + "  ON TURN:   " + turn + "   "  + scoreOnMoveSet.get(sum) + "!=" + possibility.nonFinalPlusSuggestions + " sum: " + sum + " presum: " + presum, Log.LogType.TESTS);
//                                        }
//                                    }
//                                }
//                            }
//
//
//                            // Log.log("Solo path: " + possibility.finalScore + "  "  +  possibility.lastMove, Log.LogType.PLANS);
//                        }
//
//
//                        //Will move all other ships that we already set moves for
//
//
//                    }else {

                        planLastTurn.SetWipMap(planLastTurn.CloneAndSim(turn, Math.min(turn + 1, turns), HandwavyWeights.SimStandstillSoloJourney == 1, planLastTurn.savepointMap, false,s.id));


                        CheapShip updatedShip = planLastTurn.WIPMAp.GetShipById(id);
                        CheapShip zombifyThisTurtle = null;
                        if (updatedShip == null) {
                            //We're dead on the new map because someone moved into us, but should still have an opportunity to move away.
                            //If we do,the other ship that crashed into us must now be dead. Let's ressurect it
                            zombifyThisTurtle = GetZombie(planLastTurn,turn,s);
                        } else {
                            //This should be the same object but on the new map
                            s = updatedShip;
                        }

                        for(Tile t : s.GetTile().neighboursAndSelf){
                            t.lastKnownStartHalite= planLastTurn.WIPMAp.GetHaliteAt(s.x,s.y);
                        }
//                        lastshiphalite = s.halite;
//                        lasttilehalite = planLastTurn.savepointMap.GetHaliteAt(s);

                        //Will find possible single step moves (found on the savepoint) including swaps, and simulate them out the wipmap we're working with
                        //possibilities will have the new simulated map put in wipmap
                        ArrayList<Plan> possibilities = planLastTurn.findSimulatedSteps(s, turn <= HandwavyWeights.AddToCheckAgain && phase1, prio, zombifyThisTurtle, planLastTurn.savepointMap, planLastTurn.WIPMAp, turn);
                        for (Plan possibility : possibilities) {
                            if (!possibility.markedIlllegal) {
//                                didstuffcounter2++;

                                Float f = suggestionList[turn].get(possibility.lastMove);
                                if (f != null) possibility.suggestionScore += f;

                                possibility.SetFinalMap(possibility.WIPMAp);
                                possibility.playerMoneyPerTurn[turn] = possibility.WIPMAp.playerMoney;
                                possibility.soloShipJourney = s;
                                possibility.evaluatingTurn = turn;

//                                long halitesum =  possibility.WIPMAp.haliteSum();
//                                long movesum = 0;
//                                long sum = (int)planLastTurn.nonfinalScore * halitesum * turn; //MyBot.rand.nextInt(10000);
//                                if(Test.IN_TEST_PROGRESS) {
//
//                                    for (int testturn = 0; testturn < SEARCH_DEPTH; testturn++) {
//                                        for (int index = 0; index < Map.staticMyShipCount; index++) {
//                                            if (possibility.movesPerTurn[testturn][index] != null) {
//                                                movesum += possibility.movesPerTurn[testturn][index].hashCode();
//                                            }
//                                        }
//                                    }
//                                }
//                                sum += movesum;
//                                if(Test.IN_TEST_PROGRESS && (sum == 2053733867    || (turn == 5 && id == 3 && possibility.lastMove.from.equals(4,4) && possibility.lastMove.to.equals(4,0)))){
//                                //if(Test.IN_TEST_PROGRESS && (sum == 1669336332    || (turn == 4 && id == 3 && possibility.lastMove.from.equals(3,4) && possibility.lastMove.to.equals(3,3)))){
//                                    Log.log("New: " + possibility.lastMove + "  "  +  possibility.nonFinalPlusSuggestions, Log.LogType.TESTS);
//                                }
                                possibility.EvaluatePicker(EvaluatorType);
                                possibility.SetSavepointMap(possibility.WIPMAp);

//                                likelyVisited1[possibility.lastMove.GetLikelyIndex1(turn)]++;
//                                likelyVisited2[possibility.lastMove.GetLikelyIndex2(turn)]++;
//                                likelyVisited3[possibility.lastMove.GetLikelyIndex3(turn)]++;
                                items.add(possibility);


//                                if(Test.IN_TEST_PROGRESS){
//                                    if(!USE_OLD_METHOD_FIRST) {
//                                        scoreOnMoveSet.put(sum, possibility.nonFinalPlusSuggestions);
//                                    }else{
//                                        if(!scoreOnMoveSet.containsKey(sum)){
//                                            Log.log("Move not found at all!!  Turn: " + turn + "  ship: " +  id  + "  "  + possibility.lastMove  + " sum: " + sum  + " presum: " + presum,  Log.LogType.TESTS);
//                                        }else{
//                                            if(scoreOnMoveSet.get(sum) != possibility.nonFinalPlusSuggestions){
//                                                Log.log("Eval difference!! " + possibility.lastMove + " ship: " +  id + "  ON TURN:   " + turn + "   "  + scoreOnMoveSet.get(sum) + "!=" + possibility.nonFinalPlusSuggestions + " sum: " + sum + " presum: " + presum, Log.LogType.TESTS);
//                                            }
//                                        }
//                                    }
//                                }


                            }
                            // Log.log("Solo path: " + possibility.finalScore + "  "  +  possibility.lastMove, Log.LogType.PLANS);
//                        }
                    }
                    for(Tile t : s.GetTile().neighboursAndSelf){
                        t.lastKnownStartHalite= -1;
                    }

                    planLastTurn.CleanUpMaps();


                }

            }



            if (items.size() <= capitems + 2) {
                bestPlans = items;
            } else {
                //In this section, we'll prune the options we have down to capitems
                //Try to gather good and diverse routes. First we'll start with good, then slowly prioritize diverse more
                int selectedItems = 0;
                int batchsize = 3; //How many items to take on every iteration
                bestPlans = new ArrayList<>();
                TreeSet<Plan> orderedPlans = null;
                TreeSet<Plan> remainingPlans = new TreeSet<>();
//                    Stopwatch.Start(52);
                for (Plan p : items) {
                    p.sortScore = p.nonFinalPlusSuggestions;
                }

                while (selectedItems < capitems) {
                    int plansSize = items.size();
                    if (plansSize == 0) break;

                    for (Plan p2 : bestPlans) {
                        for (Plan p : remainingPlans) {
                            //Note: Repeated checks will keep adding the finalScore. Not sure if that's desirable, but oh well. Don't think it hurts
                            //TODO: consider whether it's worth adding halite here in the difference comparison
                            double dif = p.soloShipJourney.GetTile().DistManhattan(p2.soloShipJourney);
                            //Log.log("Adding difference: " + dif, Log.LogType.PLANS);
                            p.sortScore += dif * HandwavyWeights.BeDifferentsoloJourney + MyBot.rand.nextFloat() * 0.01f;   //We sort both on the score, and the distance of the final tile to other tiles
                        }
                    }


                    Collections.sort(items);
                    //Select the best items, put them into the best list and take them out
                    int selectedThisIteration = 0;
                    int upTo = Math.min(batchsize, plansSize);
                    while (selectedThisIteration < upTo && selectedItems < capitems && !items.isEmpty()) {
                        selectedItems++;
                        selectedThisIteration++;
                        Plan item = items.remove(0);
                        bestPlans.add(item);

                    }
                    batchsize *= 1.5;
                }
                for (Plan p : items) {
                    p.CleanUpMaps();
//                    if(id == 0) {
                        //if(IMAGING && id == IMAGINGID) {
//                        Log.log("Filtered out: " + p.lastMove + " score: " + p.nonFinalPlusSuggestions + "  " + p.nonfinalScore, Log.LogType.PLANS);
//                    }
                }
                items.clear();
               // Log.log("Missed maps in this section: " + (needToClean - bestPlans.size()) + "  " + remainingPlans.size(), Log.LogType.PLANS);
//                for (Plan p : bestPlans) {
//                    if(id == 0){//   IMAGING && id == IMAGINGID) {
//                        Log.log("Kept: " + p.lastMove + " score: "  + p.nonFinalPlusSuggestions + "  " + p.nonfinalScore, Log.LogType.PLANS);
//                    }
//                }
            }
        }

//        if(IMAGING && id == IMAGINGID) {
//            Log.log(" Solo journey, looking at best ", Log.LogType.IMAGING);
//        }

        //TODO: optimize with something faster than a treeset
        for(Plan p : bestPlans){
            p.sortScore = p.nonfinalScore;
        }

        Collections.sort(bestPlans);
        int counter = 0;
        for(Plan p : bestPlans) {
            counter++;
            if(counter++ < Math.min(capitems,20)) { //Filter out the bottom scoring plans. They're extremely likely to be worthless and final evaluation is expensive
                p.FinalEvaluation();
                if (p.finalScore > best.finalScore && !p.markedIlllegal) {
                    best.CleanUpMaps();
                    best = p;

//                if(IMAGING && id == IMAGINGID) {
//                    Log.log("Best found  finalscore: "  + p.finalScore + " score:  " + p.nonfinalScore +  "  moves: " + p.movesToString(), Log.LogType.IMAGING);
//                }
                } else {
                    p.CleanUpMaps();
                }
            }else{
                p.CleanUpMaps();
            }
        }

        if(best.finalScore < -100000000000f){
            if(startturn > 0 && startPlan.WIPMAp != null){
                s = startPlan.WIPMAp.GetShipById(id);
                if(s == null){
                    s = Map.GetFreshShip(id);
                }
            }else{
                s = Map.GetFreshShip(id);
            }

//            if(!MyBot.RELEASE) {
////                Log.log("NO MOVES FOR SHIP SOLO JOURNEY " + s.id + " array size: " + bestPlans.size() + " adjacent ships ", Log.LogType.PLANS);
////                Log.log(didstuffcounter + " " + didstuffcounter2 + " " + deadcounter + " " + deadcounter2 + "  "  + lastshiphalite + " " + lasttilehalite, Log.LogType.PLANS);
//
//                for (Tile t : s.GetTile().neighboursAndSelf) {
//                    if (Map.currentMap.GetShipAt(t) != null) {
//                        Log.log(Map.currentMap.GetShipAt(t).toString() + " tilehalite: " + t.haliteStartTurn, Log.LogType.PLANS);
//                    } else {
//                        Log.log("no ship tilehalite: " + t.haliteStartTurn, Log.LogType.PLANS);
//                    }
//                }
//                Log.log(startPlan.movesToString(), Log.LogType.PLANS);
//
//            }


            //Well, guess we don't have anything?
            for(int i = startturn; i < SEARCH_DEPTH; i++){
               best.SetMyMove(i,new Move(s.GetTile(),s.GetTile(),s));
            }
        }

        Plan finalBest = best.ClearCopy();
        finalBest.finalScore = best.finalScore;
        finalBest.SetFinalMap(best.finalMap);
        best.CleanUpMaps();
        startPlan.CleanUpMaps();

        if(true){
            finalBest.CleanUpMaps();
        }


//        finalBest.lastMove = best.lastMove;
        return finalBest;


    }

    //Find the 'zombie' a ship that should still be alive, but was killed off in the simulation
    private static CheapShip GetZombie(Plan planLastTurn, int turn, CheapShip s){
        CheapShip zombifyThisTurtle = null;
        for (int moveindex = 0; moveindex < Map.staticMyShipCount; moveindex++) {
            Move m = planLastTurn.movesPerTurn[turn][moveindex];
            if (m != null && m.to.x == s.x && m.to.y == s.y && m.ship.id != s.id) {
                if(zombifyThisTurtle == null) {
                    zombifyThisTurtle = m.ship;
                }else{
                    return null; //This will be hit in 3+ collisions, let's not resurrect ships
                }
            }
        }
        if(DoEnemySimulation){
            for (int moveindex = 0; moveindex < Map.relevantEnemyShipCount; moveindex++) {
                Move m = planLastTurn.enemyMovesPerTurn[turn][moveindex];
                if (m != null && m.to.x == s.x && m.to.y == s.y && m.ship.id != s.id) {
                    if(zombifyThisTurtle == null) {
                        zombifyThisTurtle = m.ship;
                    }else{
                        return null;//This will be hit in 3+ collisions, let's not resurrect ships
                    }
                }
            }
        }
        return zombifyThisTurtle;
    }


    //Builds a single full plan from start to end
    //This works by iterating over the turns we want to make a plan for, then makes every ship decide in order what moves to take
    //for each of those turns. Ship order is determined by ship priority
    public static Plan GreedySearch(int turns, float evalRandomness, Plan startPlan, int startturn, boolean rewardUniqueness){
        Plan bestPlan = null;
        Map mapSavepoint;

        //Plan curPlan = new Plan(Map.currentMap,turns,null);
        if(startPlan == null || startturn == 0) {

            if(basePlan != null) {
                bestPlan = basePlan.ClearCopy();
            }
            else{
                bestPlan = new Plan(Map.currentMap,true);
            }
            mapSavepoint = new Map(Map.currentMap);//,Map.staticMyShipCount + Map.relevantEnemyShipCount);
            startturn = 0;
        }else{
            bestPlan = startPlan.ClearCopy();
            mapSavepoint = bestPlan.CloneAndSim(0,startturn,true,Map.currentMap,true,-1);
        }




        for(int turn = startturn; turn <  turns; turn++){

//            int found = 0;
//            for(CheapShip s : mapSavepoint.myShips ){
//                if( s!= null){
//                    found++;
//                }
//            }

//            Log.log("t " + turn  + "  myships: " + Map.staticMyShipCount + " remaining:  " + mapSavepoint.myShipsCount + " found " + found++, Log.LogType.PLANS);
            if(MyBot.SERVER_RELEASE && ShouldQuit()){
                return bestPlan;
            }

            bestPlan.evaluatingTurn = turn;

//            if(IMAGING){
//                Log.log("Starting greedy turn: " + turn  +  mapSavepoint.toStringShips(true), Log.LogType.IMAGING);
//            }

            bestPlan.AddAllForcedMoves(mapSavepoint,turn);
//            lastMapPerTurnDELETE[turn] = new Map(mapSavepoint);


            shiploop:
            while(true) { //every step of this loop, we'll select the move for one ship
                //Find all moves on the starting check point map. The moves found of other ships in this loop are not executed yet on that map
                ArrayList<Plan> possiblePlans = bestPlan.findhighestPrioPossibilities(mapSavepoint,turn);
                if(possiblePlans.size() == 0) {

                    break;
                }
                //To always overrule the previous plan
                bestPlan.nonFinalPlusSuggestions = -10000000000000f;
                boolean foundone = false;
                for (Plan p : possiblePlans) {  //Evaluate all the possible moves
                    //Simulate all ships we've found moves for from our starting check point.
                    p.SetFinalMap( p.CloneAndSim(turn, Math.min(turn + 1, turns),false,mapSavepoint,true,-1));
                    p.evaluatingTurn = turn;
                    Float f = suggestionList[turn].get(p.lastMove);
                    if(f != null) p.suggestionScore += f;
                    p.Evaluate(evalRandomness,rewardUniqueness);

//                    likelyVisited1[p.lastMove.GetLikelyIndex1(turn)]++;
//                    likelyVisited2[p.lastMove.GetLikelyIndex2(turn)]++;
//                    likelyVisited3[p.lastMove.GetLikelyIndex3(turn)]++;

//                    if(IMAGING && p.lastMove.ship.id == IMAGINGID){
////                        Log.log("Greedy Eval " + p.lastMove + " score: "  + p.nonFinalPlusSuggestions + "  " + p.nonfinalScore, Log.LogType.IMAGING);
//                        VISITED_GREEDY[turn][p.lastMove.to.x][p.lastMove.to.y]++;
//                        MAX_SCORE[turn][p.lastMove.to.x][p.lastMove.to.y] = Math.max(MAX_SCORE[turn][p.lastMove.to.x][p.lastMove.to.y],p.nonFinalPlusSuggestions);
//                    }


                    if (p.nonFinalPlusSuggestions >= bestPlan.nonFinalPlusSuggestions && !p.markedIlllegal) {

//                        if(IMAGING && p.lastMove.ship.id == IMAGINGID){
//                            Log.log("Best score yet", Log.LogType.IMAGING);
//                        }
                        foundone = true;
                        bestPlan.CleanUpMaps();
                        bestPlan = p;
                    }else{
                        p.CleanUpMaps();
                    }
                }
                if(!foundone){
                    Log.log("MAJOR ERROR IN GREEDY", Log.LogType.MAIN);
                    break;//shouldt happen, but you know..
                }
            }

            //Do a full, clean simulation from start to end to solve any weird issues. Might not be entirely needed
            mapSavepoint.CleanUp();
            mapSavepoint = bestPlan.CloneAndSim(0,Math.min(turn + 1, turns),true,Map.currentMap,true,-1);
            bestPlan.playerMoneyPerTurn[turn] = mapSavepoint.playerMoney;




//            Log.log("Best plan for turn: " + turn, Log.LogType.PLANS);
//            Log.log("", Log.LogType.PLANS);
//            Log.log(bestPlan.movesToString(), Log.LogType.PLANS);
//            Log.log("", Log.LogType.PLANS);
//            Log.log("", Log.LogType.PLANS);
        }


        bestPlan.SetFinalMap(mapSavepoint);



//        if(IMAGING && Map.myIndexOfIds[IMAGINGID] >= 0){
//            Log.log("Best path in greedy", Log.LogType.IMAGING);
//            for(int i=0;i < SEARCH_DEPTH; i++){
//                if(bestPlan.movesPerTurn[i][Map.myIndexOfIds[IMAGINGID]] != null) {
//                    Log.log(i + " "  + bestPlan.movesPerTurn[i][Map.myIndexOfIds[IMAGINGID]].toString(), Log.LogType.IMAGING);
//                }
//            }
//
//
//        }


//        Log.log("Final plan: ", Log.LogType.PLANS);
//        if(curPlan == null){
//            Log.log("NO PLAN", Log.LogType.PLANS);
//            return null;
//        }else {
//            Log.log(curPlan.movesToString(), Log.LogType.PLANS);
//            Log.log("", Log.LogType.PLANS);
//        }

      //  Log.flushLogs();

        return bestPlan;

    }

    //Like the greedy search, except it uses the moveevaluator for prioritization instead of ship priority
    public static Plan MoveBasedGreedySearch(){
        Plan plan =  basePlan.ClearCopy();
        plan.SetWipMap(new Map(Map.currentMap));

        for(int turn = 0; turn <  SEARCH_DEPTH; turn++){

            boolean[][] alreadyMovingTo = new boolean[Map.width][Map.height];
            boolean[] alreadyUsed = new boolean[Map.staticMyShipCount];
            int standingstill = 0;
            TreeSet<Move> sortedMoves = new TreeSet<Move>();




            for(CheapShip s : plan.WIPMAp.myShips){
                if(s != null) {
                    Tile from = Map.tiles[s.x][s.y];
                    if (MyBot.moveCostsSafe[plan.WIPMAp.GetHaliteAt(s) + 1000] > s.halite) {
                        plan.movesPerTurn[turn][Map.myIndexOfIds[s.id]] = new Move(from,from,s);
                        alreadyMovingTo[s.x][s.y] = true;
                        alreadyUsed[Map.myIndexOfIds[s.id]] = true;
                        standingstill++;
                    }else{
                        for(Tile t : from.neighboursAndSelf){
                            Move m = new Move(from,t,s);

                            plan.MoveEvaluate(turn,m,Map.myIndexOfIds[s.id],false);
                            sortedMoves.add(m);
                        }
                    }
                }
            }
           for(Move m : sortedMoves){
                if(!alreadyUsed[Map.myIndexOfIds[m.ship.id]] && (!alreadyMovingTo[m.to.x][m.to.y] || turnsLeft < HandwavyWeights.RunLeftTimerV3 && m.to.turnsFromDropoff == 0)){
                    plan.movesPerTurn[turn][Map.myIndexOfIds[m.ship.id]] = m;
                    alreadyMovingTo[m.to.x][m.to.y] = true;
                    alreadyUsed[Map.myIndexOfIds[m.ship.id]] = true;
                }
           }
           if(turn < SEARCH_DEPTH - 1) {
               plan.SetWipMap(plan.CloneAndSim(turn, turn + 1, true, plan.WIPMAp, true,-1));
           }
        }
        return plan;
    }

    public static Plan MoveBasedGreedySearch(Plan startPlan){
        Plan plan =  startPlan.ClearCopy();
        plan.SetWipMap(new Map(Map.currentMap));

        for(int turn = 0; turn <  SEARCH_DEPTH; turn++){

            boolean[][] alreadyMovingTo = new boolean[Map.width][Map.height];
            boolean[] alreadyUsed = new boolean[Map.staticMyShipCount];
            int standingstill = 0;
            TreeSet<Move> sortedMoves = new TreeSet<Move>();


            for(int i =0; i < Map.staticMyShipCount; i++){
                if(plan.movesPerTurn[turn][i] != null){
                    alreadyMovingTo[plan.movesPerTurn[turn][i].to.x][plan.movesPerTurn[turn][i].to.y] = true;
                }
            }


            for(int i =0; i < Map.staticMyShipCount; i++){
                if(plan.movesPerTurn[turn][i] == null) {
                    CheapShip s = Map.currentMap.myShips[i];
                    if (s != null) {
                        Tile from = Map.tiles[s.x][s.y];
                        if (MyBot.moveCostsSafe[plan.WIPMAp.GetHaliteAt(s) + 1000] > s.halite) {
                            plan.movesPerTurn[turn][Map.myIndexOfIds[s.id]] = new Move(from, from, s);
                            alreadyMovingTo[s.x][s.y] = true;
                            alreadyUsed[Map.myIndexOfIds[s.id]] = true;
                            standingstill++;
                        } else {
                            for (Tile t : from.neighboursAndSelf) {
                                Move m = new Move(from, t, s);

                                plan.MoveEvaluate(turn, m, Map.myIndexOfIds[s.id], false);
                                sortedMoves.add(m);
                            }
                        }
                    }
                }
            }
            for(Move m : sortedMoves){
                if(!alreadyUsed[Map.myIndexOfIds[m.ship.id]] && (!alreadyMovingTo[m.to.x][m.to.y] || turnsLeft < HandwavyWeights.RunLeftTimerV3 && m.to.turnsFromDropoff == 0)){
                    plan.movesPerTurn[turn][Map.myIndexOfIds[m.ship.id]] = m;
                    alreadyMovingTo[m.to.x][m.to.y] = true;
                    alreadyUsed[Map.myIndexOfIds[m.ship.id]] = true;
                }
            }
            if(turn < SEARCH_DEPTH - 1) {
                plan.SetWipMap(plan.CloneAndSim(turn, turn + 1, true, plan.WIPMAp, true,-1));
            }
        }
        return plan;
    }

    //Generates a bunch of essentially random paths, checks their value, then determines how good step 1 of those plans
    //was by averaging the results from these paths. It picks the best turn 1, then starts from there and tries to determine
    //the best turn 2 using the same process. Meanwhile, any good plans it stumbles upon during this process are remembered
    //This didn't end up being a very good approach, so it's not used in the final version
    public static Plan MonteCarloSearch(int stopTurn, double attemptsMod){
        Plan buildingplan = basePlan.ClearCopy();
        Plan bestplan = basePlan.ClearCopy();
        bestplan.finalScore = -10000000f;

        double reduce = 3.0 * Math.pow(0.985f,Map.staticMyShipCount) * Math.pow(0.7f,SEARCH_DEPTH);

        int[] attemptsAllowed = new int[]{(int)(600 * reduce * attemptsMod),(int)(250 * reduce  * attemptsMod), (int)(100 * reduce  * attemptsMod)};

        Move[][] buildingMoveSet = new Move[SEARCH_DEPTH][Map.staticMyShipCount];
        Map latestBaseMap = Map.currentMap;

        String typeOfBestMonteCarloPlan = "Stumbled upon";


        stopTurn = Math.min(SEARCH_DEPTH,stopTurn);

        for(int turn =0; turn < stopTurn; turn++){

            int attempts = attemptsAllowed[turn];

//            if(IMAGING){
//                Log.log("Starting monte carlo turn: " + turn, Log.LogType.IMAGING);
//            }


            short[][] baseHaliteMap = new short[Map.width][Map.height];
            for(int x =0; x< Map.width;x++){
                for(int y =0; y< Map.width;y++) {
                    baseHaliteMap[x][y] = latestBaseMap.GetHaliteAt(x,y);
                }
            }

            float[][] totalScores = new float[Map.staticMyShipCount][5];
            float[][] totalAttempts = new float[Map.staticMyShipCount][5];
            float[][] maxScores = new float[Map.staticMyShipCount][5];

            for(int i=0; i < Map.staticMyShipCount; i++) {
                for(int j=0; j < 5; j++) {
                    maxScores[i][j] = -1000000000f;//
                }
            }


            Plan buildingPlanClone = buildingplan.ClearCopy();

            for(int attempt =0; attempt < attempts; attempt++){
                if(ShouldQuit()) break;

                CheapShip[] attemptMyShips = latestBaseMap.myShips.clone();
//                short[][] attemptHaliteMap = new short[Map.width][];
//                for(int x =0; x< Map.width;x++){
//                    attemptHaliteMap[x] = baseHaliteMap[x].clone();
//                }


                int[] firstMoves = new int[Map.staticMyShipCount];
                Plan attemptPlan = buildingPlanClone; //a reference, will be continually overwritten. cloning here is too expensive



                for(int attemptTurn = turn; attemptTurn < SEARCH_DEPTH; attemptTurn++){

                    for(int shipindex =0; shipindex < Map.staticMyShipCount; shipindex++){
                        CheapShip s = attemptMyShips[shipindex];
                        if(s != null){
                            int cost = MyBot.moveCostsSafe[Map.staticHaliteMap[s.x][s.y] + 1000];
                            int bestMove = 0;
                            int newHalite;

                            Tile from = Map.tiles[s.x][s.y];
                            Tile to;

                            if(attemptMyShips[shipindex].halite >= cost){
                                int gain = Math.min(1000-s.halite,MyBot.standCollectSafe[Map.staticHaliteMap[s.x][s.y] + 1000]); //note: using static halite map, our path wont change the halite on spots. thats way too expensive to track to be worth it

                                float bestscore= gain * 1.6f;//The standstill finalScore
                                if(goals[s.id] != null){
                                    bestscore -= from.DistManhattan(goals[s.id]) * 50.0f;
                                } else if(s.halite > 950) {
                                    bestscore -= turnsFromDropoff[s.x][s.y] * 50.0f;
                                }
                                else{
                                    bestscore -= distToMeaningfulHalite[s.x][s.y] * 50.0f;
                                }


                                bestscore =  bestscore * MyBot.rand.nextFloat() * MyBot.rand.nextFloat()  + 40.0f * (MyBot.rand.nextFloat()) / (attemptTurn + 1f);

                                to = from;
                                newHalite = s.halite + gain;

                                for(Tile t : from.neighbours){
                                    float score =  (Math.min(1000-s.halite,MyBot.standCollectSafe[Map.staticHaliteMap[t.x][t.y] + 1000]) * 0.7f)- cost;
                                    if(goals[s.id] != null){
                                        score -= from.DistManhattan(goals[s.id]) * 50.0f;
                                    } else if(s.halite > 950) {
                                        score -= turnsFromDropoff[s.x][s.y] * 50.0f;
                                    }
                                    else{
                                        score -= distToMeaningfulHalite[s.x][s.y] * 50.0f;
                                    }


                                    float rand2 = (MyBot.rand.nextFloat()) / (attemptTurn + 1f);
                                    score =  bestscore * MyBot.rand.nextFloat() * MyBot.rand.nextFloat()  + 40.0f * (MyBot.rand.nextFloat()) / (attemptTurn + 1f);


                                    if(score > bestscore){
                                        bestscore = score;
                                        newHalite = s.halite - cost;
                                        if(t.IsNorthOf(from)){
                                            bestMove = 1;
                                        } else if(t.IsEastOf(from)){
                                            bestMove = 2;
                                        } else if(t.IsSouthOf(from)){
                                            bestMove = 3;
                                        } else{
                                            bestMove = 4;
                                        }
                                        to = t;
                                    }
                                }

//
//
//                                bestMove = MyBot.rand.nextInt(5);
//                                if(bestMove == 0){
//
//                                    to = from;
//                                    newHalite = s.halite + gain;
//                                    attemptHaliteMap[s.x][s.y] -= gain;
//                                } else if(bestMove == 1){
//                                    to = from.North();
//                                    newHalite = s.halite - cost;
//                                } else if(bestMove == 2){
//                                    to = from.East();
//                                    newHalite = s.halite - cost;
//                                } else if(bestMove == 3){
//                                    to = from.South();
//                                    newHalite = s.halite - cost;
//                                } else{
//                                    to = from.West();
//                                    newHalite = s.halite - cost;
//                                }
                            }else{
                                int gain = MyBot.standCollectSafe[Map.staticHaliteMap[s.x][s.y] + 1000];
                                to = from;
                                newHalite = s.halite + gain;
//                                attemptHaliteMap[s.x][s.y] -= gain;
                            }
                            attemptPlan.SetMyMove(attemptTurn,new Move(from,to,s));
                            attemptMyShips[shipindex] = CheapShip.MakeShip(s.id,(short)newHalite,to.byteX,to.byteY);
                            if(attemptTurn == turn){
                                firstMoves[shipindex] = bestMove;
                            }
                        }
                    }
                }

                attemptPlan.FinalEvaluation();


                if (attemptPlan.finalScore > bestplan.finalScore && !attemptPlan.markedIlllegal) {
                    bestplan.CleanUpMaps();
                    bestplan = attemptPlan.ClearCopy();
                }

                //Accepts illegal plans, since there's likely to be a bunch
                if (turn < Math.min(SEARCH_DEPTH, stopTurn + 1) - 1) {
                    for (int i = 0; i < Map.staticMyShipCount; i++) {
                        int firstmove = firstMoves[i];
                        totalAttempts[i][firstmove]++;
                        totalScores[i][firstmove] += attemptPlan.finalScore;

                        if (attemptPlan.finalScore > maxScores[i][firstmove]) {
                            maxScores[i][firstmove] = attemptPlan.finalScore;
                        }
                    }
                }

            }

            if(turn < Math.min(SEARCH_DEPTH,stopTurn + 1) - 1) {
                //Set forced moves
                for (int i = 0; i < Map.staticMyShipCount; i++) {
                    CheapShip ship = latestBaseMap.myShips[i];
                    if (ship != null) {
                        if(ship.halite <  MyBot.moveCosts[Map.currentMap.GetHaliteAt(ship.x,ship.y)]){
                            aleadygoingfor[ship.tileIndex] = true;
                            buildingMoveSet[turn][i] = new Move(Map.tilesById[ship.tileIndex], Map.tilesById[ship.tileIndex], ship);
                        }
                    }
                }
                for (int i = 0; i < Map.staticMyShipCount; i++) {
                    CheapShip ship = latestBaseMap.myShips[i];
                    if(ship != null && ship.halite >=  MyBot.moveCosts[Map.currentMap.GetHaliteAt(ship.x,ship.y)]) {
                        Tile from = Map.tiles[ship.x][ship.y];
                        Tile to = null;
                        float bestscore = -100000000000f;

                        for(int j =0; j < 5; j++){
                            float avg = (totalScores[i][j] / totalAttempts[i][j]); //TODO: move these down under the if check again
                            float score = avg + 1.0f * maxScores[i][j];
//                            if(IMAGING && ship.id == IMAGINGID){
//                                Log.log("Move: " + j + " attempts: " + totalAttempts[i][j] + " avg:  " +  avg + "  score:  " +  score, Log.LogType.IMAGING);
//                            }

                            if(totalAttempts[i][j] > 4) {

                                if (score > bestscore) {
                                    Tile towards = null;
                                    if (j == 0) {
                                        towards = from;
                                    } else if (j == 1) {
                                        towards = from.North();
                                    } else if (j == 2) {
                                        towards = from.East();
                                    } else if (j == 3) {
                                        towards = from.South();
                                    } else if (j == 4) {
                                        towards = from.West();
                                    }
                                    if(!aleadygoingfor[towards.tileIndex] || turnsLeft < HandwavyWeights.RunLeftTimerV3) {
                                        bestscore = score;
                                        to = towards;
                                    }
                                }
                            }
                        }
                        if( to != null) {
                            aleadygoingfor[to.tileIndex] = true;
                            buildingMoveSet[turn][i] = new Move(from, to, ship);
                        } else{
                            for(Tile t : from.GetNeighboursAndSelf()){
                                if(!aleadygoingfor[t.tileIndex]){
                                    aleadygoingfor[t.tileIndex] = true;
                                    buildingMoveSet[turn][i] = new Move(from, t, ship);
                                }
                            }

                        }

                    }
                }

                for(int i =0; i < Map.staticMyShipCount; i++){
                    aleadygoingfor[buildingMoveSet[turn][i].to.tileIndex] = false;
                }


                buildingplan.movesPerTurn[turn] = buildingMoveSet[turn];
                latestBaseMap.CleanUp();
                latestBaseMap = buildingplan.CloneAndSim(0,turn+1,false,Map.currentMap,true,-1);
            }


        }

//        if(IMAGING) {
//            Log.log("Build plan: ", Log.LogType.IMAGING);
//            for (int i = 0; i < SEARCH_DEPTH; i++) {
//                Move m = buildingplan.movesPerTurn[i][Map.myIndexOfIds[IMAGINGID]];
//                if( m != null){
//                    Log.log("Move: " + i + "   " + m, Log.LogType.IMAGING);
//                }
//            }
//        }

        //TODO: finish it off with greedies after every starting step of the building plan
        boolean bestPlanFoundFromGreedy = false;

//        Log.log("Build up plan " + buildingplan.movesToString(), Log.LogType.INFO);
//        Log.log("Best plan " + bestplan.movesToString(), Log.LogType.INFO);

        for(int turn =0; turn < stopTurn; turn++){
            if(ShouldQuit()) return bestplan;

            Plan copiedPlan = basePlan.ClearCopy();

            for(int turn2 =0; turn2 <= turn; turn2++){
                copiedPlan.movesPerTurn[turn2] = buildingplan.movesPerTurn[turn2];
            }

            Plan greedyResult = GreedySearch(SEARCH_DEPTH,0,copiedPlan,turn+1,false);
            greedyResult.FinalEvaluation();

//            Log.log("Greedy:  " + turn + "   " + greedyResult.movesToString(), Log.LogType.INFO);


            if(greedyResult.finalScore > bestplan.finalScore){
                bestplan = greedyResult;
                bestPlanFoundFromGreedy = true;
                typeOfBestMonteCarloPlan = "Greedy based on " + turn;
            }

            copiedPlan.CleanUpMaps();
            greedyResult.CleanUpMaps();
        }

        if(!bestPlanFoundFromGreedy){
            Plan bestFoundSoFar = bestplan;

            for(int turn =0; turn <stopTurn; turn++){
                if(ShouldQuit()) return bestplan;

                Plan copiedPlan = basePlan.ClearCopy();

                for(int turn2 =0; turn2 <= turn; turn2++){
                    copiedPlan.movesPerTurn[turn2] = bestFoundSoFar.movesPerTurn[turn2];
                }

                Plan greedyResult = GreedySearch(SEARCH_DEPTH,0,copiedPlan,turn,false);
                greedyResult.FinalEvaluation();

//                Log.log("Greedybest:  " + turn + "   " + greedyResult.movesToString(), Log.LogType.INFO);


                if(greedyResult.finalScore > bestplan.finalScore && !greedyResult.markedIlllegal){
                    bestplan = greedyResult;
                    typeOfBestMonteCarloPlan = "Greedy-best based on " + turn;
                }

                copiedPlan.CleanUpMaps();
                greedyResult.CleanUpMaps();
            }
        }


//        Log.log("Final:   "  +  typeOfBestMonteCarloPlan +    "  best:  "  + bestplan.movesToString(), Log.LogType.INFO);

        return bestplan;
    }



    //Just iterates over all ships and executes a cheap FindBestSoloJourney once for each to try to cheaply fix issues with movesets
    private static Plan MoveFixemUpper(Plan plan){

        plan = CollisionFixer(plan);
        plan.collisionCount = CountCollisions(plan);
        plan.FinalEvaluation();


        Stopwatch.Start(72);
        int fixe = 0;
        float originalScore = plan.finalScore;
        String fixed = "";

//        if(IMAGING){
//            Log.log("Move fixem upper", Log.LogType.IMAGING);
//        }

        for(CheapShip s : Map.staticMyShips){
            if(s != null) {
                if(ShouldQuit()) break;


//                if(IMAGING && s.id == IMAGINGID) {
//                    Log.log("Move fixem upper, trying to fix our ship", Log.LogType.IMAGING);
//                }
                Plan newplan = plan.ClearCopy();
                newplan.RemoveMyMovesBy(s.id);
                newplan = FindBestSoloJourney(s, newplan, SEARCH_DEPTH, 6, 0, 0);

                newplan.collisionCount = CountCollisions(newplan);

                if(newplan.collisionCount <= plan.collisionCount) {
                    if (newplan.finalScore > plan.finalScore) {
                        if (!newplan.markedIlllegal) {
                            plan.CleanUpMaps();
                            plan = newplan;
                            fixe++;
                            fixed += s.id + ", ";
                            // Log.log("Found a better plan by fixing ship " + s.id + "  score:  " + newplan.finalScore, Log.LogType.PLANS);
                        }
//                        else {
//                            Log.log("Illegal in fixem", Log.LogType.PLANS);
//                        }
                    }
                }
//                else{
//                    Log.log("Collision count too high to consider:  " + newplan.collisionCount);
//                }

            }
        }

//        if(fixe >0 ){
//            Log.log("Found a better plan by fixing " + fixe + " ships score:  " + originalScore + " -> " + plan.finalScore + "\r\n" + fixed, Log.LogType.PLANS);
//
//        }

        Stopwatch.Stop(72,"fixem");
        return plan;
    }


    //Tries to generate new paths for every ship using a different array of move evaluators
    //After generation, it evaluates these using the standard evaluator, to see if any of these happen to provide better results.
    //It works pretty well, but is too resource intensive for the server, so it's not used in the final version
    private static Plan MajorFixemUpper(Plan plan, int width){

        plan.CleanUpMaps();
        plan = CollisionFixer(plan);
        plan.collisionCount = CountCollisions(plan);

        plan.FinalEvaluation();


        Stopwatch.Start(73);
        int fixe = 0;
        float originalScore = plan.finalScore;

//        if(IMAGING){
//            Log.log("Major fixem upper", Log.LogType.IMAGING);
//        }

        TreeSet<SortableShip> ships = new TreeSet<>();
        for(CheapShip s : Map.currentMap.myShips){
            if(s != null) {
                ships.add(new SortableShip(s, plan.CheapPriorityEvalSorter(s,Map.currentMap) + MyBot.rand.nextFloat() * 0.01f)); //randomness is a bandaid to deal with equal prio ships not getting added
            }
        }
        int[] fixedPerType = new int[5];
        for(int evaluator = 0; evaluator < 6; evaluator++) {
            Iterator<SortableShip> iter = ships.iterator();
            while (iter.hasNext()) {

                CheapShip s = iter.next().ship;



//                if(IMAGING && s.id == IMAGINGID) {
//                    Log.log("Move fixem upper, trying to fix our ship using eval " + i, Log.LogType.IMAGING);
//                }

                if (s.halite > MyBot.moveCosts[Map.staticHaliteMap[s.x][s.y]]) {
                    Tile from = Map.tiles[s.x][s.y];
                    for (Tile t : from.neighboursAndSelf) {
                        if (ShouldQuitStricter() || Map.leakedMaps > 5000) return plan; //TODO: fix the map leak that's in here somewhere
                        Plan newplan = plan.ClearCopy();
                        Move m = new Move(from, t, s);


                        newplan.RemoveMyMovesBy(s.id);
                        newplan.SetMyMove(0, m);

                        newplan.MoveEvaluate(0,m,Map.myIndexOfIds[s.id],false);

//                        if(t.equals(from) && MyBot.turn > 10 && s.id == 0){
//                           Log.log("Is this a risky move? " + new Move(from, t, s), Log.LogType.PLANS);
//                        }

                        newplan = FindBestSoloJourney(s, newplan, SEARCH_DEPTH, width, 1, evaluator);

                        newplan.collisionCount = CountCollisions(newplan);

                        if (newplan.finalScore > plan.finalScore && newplan.collisionCount <= plan.collisionCount) {
                            plan.CleanUpMaps();
                            plan = newplan;
                            fixe++;
                            fixedPerType[evaluator]++;
//                            Log.log("new best plan movefixem (id:" + +s.id + ", eval: " +evaluator+ " )" + newplan.finalResultToString(true), Log.LogType.PLANS);
                        } else {
                            newplan.CleanUpMaps();
                        }
                    }
                }
            }
        }

//        if(fixe >0 ){
//            Log.log("MAJOR FIXER: Found a better plan by fixing " + fixe + " ships score:  " + originalScore + " -> " + plan.finalScore  +"  \r\nPer Type: " + fixedPerType[0] + ", " + fixedPerType[1] + ", " + fixedPerType[2] + ", " + fixedPerType[3] + ", " + fixedPerType[4]  , Log.LogType.PLANS);
//        } else{
//            Log.log("No changes in major fixem " , Log.LogType.PLANS);
//        }


        Stopwatch.Stop(73,"major fixem");
        return plan;



    }

    static Move[][] alreadyGoingThereForCount ;

    //Stops self-collisions from happening, first by attempting to use FindSoloJourney
    //But if it that isn't working, or if time is running out, self-collisions will be fixed by making ships stand still
    private static Plan CollisionFixer(Plan originalPlan){
        Stopwatch.Start(43);

        int safetycheck = 0;
        int corrections = 0;
        int realcorrections = 0;

        float originalscore = originalPlan.finalScore;
        Plan p = originalPlan.ClearCopy();

        while(true){
            boolean foundProblem = false;
            ArrayList<Move> redoThese = new ArrayList<>();

            for(Tile t : Map.tileList){
                t.alreadyGoingThere = null;
            }

            for(int i = 0 ; i < Map.staticMyShipCount; i++){
                Move m = p.movesPerTurn[0][i];
                if(m != null){
                    if (turnsLeft > HandwavyWeights.RunLeftTimerV3 || m.to.turnsFromDropoff > 0){
                        if (m.to.alreadyGoingThere != null) {
                            if (m.isStandStill()) {
                                redoThese.add(m.to.alreadyGoingThere);
                                m.to.alreadyGoingThere = m;

                                if(safetycheck > 10 && m.ship.CanMove(Map.currentMap) && !redoThese.contains(m)){
                                    redoThese.add(m);
                                }
                            } else {
                                redoThese.add(m);
                                if(safetycheck > 10 && m.to.alreadyGoingThere.ship.CanMove(Map.currentMap) && !redoThese.contains(m.to.alreadyGoingThere)){
                                    redoThese.add(m.to.alreadyGoingThere);
                                }

                            }
                            foundProblem = true;
                        } else {
                            m.to.alreadyGoingThere = m;
                        }
                    }
                }else{
                    CheapShip s = Map.currentMap.myShips[i];
                    Tile t = s.GetTile();
//                    Log.log("No move in collision fixer (" + safetycheck + ")  " + s, Log.LogType.PLANS);

                    if (turnsLeft > HandwavyWeights.RunLeftTimerV3 || turnsFromDropoff[t.x][t.y] > 0){
                        Move newmove = new Move(t,t,s);
                        if (t.alreadyGoingThere  != null) {
                            redoThese.add(t.alreadyGoingThere);
                            t.alreadyGoingThere = newmove;
                            foundProblem = true;
                        } else {
                            t.alreadyGoingThere =newmove;
                        }
                    }
                }
            }

            for(Move m : redoThese){
                Plan newplan = p.ClearCopy();
                newplan.RemoveMyMovesBy(m.ship.id);
                corrections++;
                banlist[0].clear();
                if(ShouldQuitLessStrict() || safetycheck > 15){
                    newplan.SetMyMove(0,new Move(m.from,m.from,m.ship));
                    p.CleanUpMaps();
                    p = newplan;
//                    Log.log("Aborting fixer because of time / too many attempts", Log.LogType.PLANS);
                }else {
                    banlist[0].add(m);
                    newplan = FindBestSoloJourney(m.ship, newplan, SEARCH_DEPTH, 10, 0, 0);

                    if (newplan.movesPerTurn[0][Map.myIndexOfIds[m.ship.id]] == null) {

                        newplan.movesPerTurn[0][Map.myIndexOfIds[m.ship.id]] = new Move(m.from, m.from, m.ship);

//                        Log.log("Collision correction, no move found??", Log.LogType.PLANS);
                        p.CleanUpMaps();
                        p = newplan;
                    } else if (newplan.finalScore > p.finalScore) {
//                        Log.log("Collision proper correction of move: " + m  + "  to: " +  newplan.movesPerTurn[0][Map.myIndexOfIds[m.ship.id]], Log.LogType.PLANS);

                        p.CleanUpMaps();
                        p = newplan;
                        realcorrections++;
                    }

                    else if(redoThese.size() == 1 || safetycheck > 5  ||  CountCollisions(newplan) < CountCollisions(p)){
                        p.CleanUpMaps();
                        p = newplan;

//                        Log.log("Found a worse scoring fix:" + newplan.finalScore + "  old " + p.finalScore + "  moves: " + newplan.movesToString(), Log.LogType.PLANS);
                    }
                    else{
                        newplan.CleanUpMaps();
                    }
                }
            }


            if(!foundProblem || safetycheck++ > 30 ){

//                if(safetycheck > 30){
//                    Log.log("Stopping because of safetycheck ", Log.LogType.PLANS);
//                    Log.log(p.movesToString(), Log.LogType.PLANS);

//                }

                break;
            }
//            else{
//                Log.log("Continuing collision fixer loop: " + safetycheck + "  " + redoThese.size(), Log.LogType.PLANS);
//            }
        }


//        if(corrections > 0){
//            Log.log("Corrected " + realcorrections + "/" + corrections + " collision attempts, safetycheck: " + safetycheck +  " score: " +  originalscore + " -> " + p.finalScore, Log.LogType.PLANS);
//        }
        ClearBansAndSuggestions();

        Stopwatch.Stop(43,"Collision fixer");


        int collisionsNew = CountCollisions(p);
        int collisionsOld = CountCollisions(originalPlan);
        if (collisionsNew < collisionsOld) {
            originalPlan.CleanUpMaps();
//            Log.log("Got an improved plan in cfixer (collision count)", Log.LogType.PLANS);
            return p;
        } else if (collisionsNew == collisionsOld && p.finalScore > originalPlan.finalScore) {
            originalPlan.CleanUpMaps();
//            Log.log("Got an improved plan in cfixer (score)", Log.LogType.PLANS);

            return p;
        }  else if(corrections > 0){
            originalPlan.CleanUpMaps();
//            Log.log("Got an improved plan in cfixer (corrections)", Log.LogType.PLANS);

            return p;
        } else {
            p.CleanUpMaps();
//            if(collisionsOld > 0) {
//                Log.log("Failed to improve plan in cfixer", Log.LogType.PLANS);
//            }
            return originalPlan;
        }



    }

    private static int CountCollisions(Plan p) {
        int collisions = 0;

        for(Move m : p.movesPerTurn[0]){
            if(m != null  && (turnsLeft > HandwavyWeights.RunLeftTimerV3 || turnsFromDropoff[m.to.x][m.to.y] > 0)){
                if(alreadyGoingThereForCount[m.to.x][m.to.y] != null){
                    collisions++;
                }else{
                    alreadyGoingThereForCount[m.to.x][m.to.y] = m;
                }
            }
        }

        for(Move m : p.movesPerTurn[0]){
            if(m != null){
                alreadyGoingThereForCount[m.to.x][m.to.y] = null;
            }
        }
        return collisions;
    }


    //Allows easy switching of approach by changing the plan style (since it's based on an int parameter, the stats analysis can also handle it)
    public static Plan GetPlanFromStyle(int style){
        switch (style){
            case STYLE_EXTREMELY_MINIMAL:
                return GetExtremelyMinimal();
            case STYLE_MINIMAL_3:
            case STYLE_MINIMAL_4:
            case STYLE_MINIMAL_5:
            case STYLE_MINIMAL_6:
            case STYLE_MINIMAL_10:
                return GetMinimal(HandwavyWeights.WIDTH_SOLO_MINIMAL);
            case STYLE_MINIMAL_5_BROAD:
                return GetMinimal(40);
            case STYLE_MINIMAL_3_EXPANDED:
            case STYLE_MINIMAL_4_EXPANDED:
            case STYLE_MINIMAL_6_EXPANDED:
                return GetMinimalExpanded();

            case STYLE_OLDSTYLE_3:
            case STYLE_OLDFASHIONED:
                return GetOldFashioned();
            case STYLE_NEWSTYLE_3:
            case STYLE_NEWSTYLE_4:
            case STYLE_NEWSTYLE_9:
                return GetNewStyle();
            case STYLE_FIXEM_ONLY:
                return GetFixemOnly();
            case STYLE_JOURNEY_ESCALATION_6:
            case STYLE_JOURNEY_ESCALATION_3:
                return GetSoloEscalation();
            case STYLE_SUGGESTION_LED_SOLO_DEEP:
            case STYLE_SUGGESTION_LED_SOLO_4:
            case STYLE_SUGGESTION_LED_SOLO_5:
            case STYLE_SUGGESTION_LED_SOLO_6:
                return GetSuggestionLed();
            case STYLE_GREEDY_SPAM:
                return GetGreedySpam(null);
            case STYLE_HUGE_MONTE:
                return GetHugeMonte();



        }
        return GetOldFashioned();
    }

    public static int GetSearchDepth(int style){
//        if(MyBot.EXPERIMENTAL_MODE){
//            return 1;
//        }
        switch (style){
            case STYLE_EXTREMELY_MINIMAL:
                return  1;
            case STYLE_FIXEM_ONLY:
                return 5;

            case STYLE_OLDSTYLE_3:
            case STYLE_NEWSTYLE_3:
            case STYLE_MINIMAL_3:
            case STYLE_MINIMAL_3_EXPANDED:
            case STYLE_JOURNEY_ESCALATION_3:
                return 3;
            case STYLE_MINIMAL_4:
            case STYLE_NEWSTYLE_4:
            case STYLE_SUGGESTION_LED_SOLO_4:
            case STYLE_MINIMAL_4_EXPANDED:
                return 4;
            case STYLE_MINIMAL_5:
            case STYLE_MINIMAL_5_BROAD:
            case STYLE_SUGGESTION_LED_SOLO_5:
                return 5;
            case STYLE_JOURNEY_ESCALATION_6:
            case STYLE_SUGGESTION_LED_SOLO_6:
            case STYLE_MINIMAL_6:
            case STYLE_MINIMAL_6_EXPANDED:
                return 6;
            case STYLE_MINIMAL_10:
                return 10;
            case STYLE_NEWSTYLE_9:
                return 9;
            case STYLE_GREEDY_SPAM:
                return 5;
            case STYLE_HUGE_MONTE:
                return 4;
            case STYLE_SUGGESTION_LED_SOLO_DEEP:
                return 9;
            case STYLE_OLDFASHIONED:
                int ships = Map.currentMap.myShipsCount;
                if(ships < 3){
                    SEARCH_DEPTH = HandwavyWeights.DEPTH_1;
                } else if(ships < 5){
                    SEARCH_DEPTH = HandwavyWeights.DEPTH_2;
                }  else if(ships < 7){
                    SEARCH_DEPTH = HandwavyWeights.DEPTH_3;
                }  else if(ships < 9){
                    SEARCH_DEPTH = HandwavyWeights.DEPTH_4;
                } else if(ships < 11){
                    SEARCH_DEPTH = HandwavyWeights.DEPTH_5;
                }  else if(ships < 13){
                    SEARCH_DEPTH = HandwavyWeights.DEPTH_6;
                } else if(ships < 15){
                    SEARCH_DEPTH = HandwavyWeights.DEPTH_7;
                } else if(ships < 19){
                    SEARCH_DEPTH = HandwavyWeights.DEPTH_8;
                } else if(ships < 23){
                    SEARCH_DEPTH = HandwavyWeights.DEPTH_9;
                } else if(ships < 30){
                    SEARCH_DEPTH = HandwavyWeights.DEPTH_10;
                }  else if(ships < 50){
                    SEARCH_DEPTH = HandwavyWeights.DEPTH_11;
                }  else if(ships < 75){
                    SEARCH_DEPTH = HandwavyWeights.DEPTH_12;
                }  else if(ships < 125){
                    SEARCH_DEPTH = HandwavyWeights.DEPTH_13;
                } else{
                    SEARCH_DEPTH = HandwavyWeights.DEPTH_14;
                }
                int maxSims = Math.max(7000,Math.min(MyBot.MAX_SIMULATIONS_ALLOWED / 4,    (int)( 80000.0 - Math.pow((double)ships,1.2) * 180.0))); //not the actual amount of sims, undershoots it by quite a bit. this is just the base level
                if(Map.width == 64){
                    maxSims *= 0.85;
                } else if(Map.width == 58){
                    maxSims *= 0.95;
                }
                if(MyBot.SERVER_RELEASE){
                    maxSims *= 0.6;
                    SEARCH_DEPTH -= 1;
                }
                SEARCH_DEPTH = Math.max(3,Math.min(SEARCH_DEPTH,maxSims / 1600));
                SOLO_WIDTH = Math.min(60, maxSims / Math.max(1,ships * SEARCH_DEPTH * 5));

                if(turnsLeft < HandwavyWeights.RunLeftTimerV3){
                    SEARCH_DEPTH = Math.min(SEARCH_DEPTH,3);
                    SOLO_WIDTH = Math.min(SOLO_WIDTH,40);
                }
                SOLO_WIDTH = (int)Math.min(SOLO_WIDTH, (Map.giantMapSize2 * Map.giantMapSize1) - 100f / 25f);
                SOLO_WIDTH = Math.max(7, SOLO_WIDTH);
                return SEARCH_DEPTH;
        }



        return 4;
    }


    //This is the plan obtaining approach used during the final version
    //This functions name may no longer be appropriate..
    public static Plan GetMinimal(int width){
        //if(timeErrors > 20){
            //Log.log("SEVERE TIME ISSUES, REVERT TO SIMPLE", Log.LogType.PLANS);
        //}


//        Log.log("Leaked before "  + Map.leakedMaps  + "  illegal: " + illegalMoves  +  " careful: " + Map.carefulWereLeaking, Log.LogType.PLANS);
        Plan plan;
        if(HandwavyWeights.MinimalPreMechanicV2 == 0) {
            plan = MoveBasedGreedySearch();
        }
        else if(HandwavyWeights.MinimalPreMechanicV2 == 1) {
            plan = GreedySearch(SEARCH_DEPTH,0,null,0,false);
        } else if(HandwavyWeights.MinimalPreMechanicV2 == 2) {
            SOLO_WIDTH = 4;
            plan = FindBestSoloJourneysMix(basePlan,true,true,true);
        } else if(HandwavyWeights.MinimalPreMechanicV2 == 3) {
            AddLastTurnSuggestions(10000);
            SOLO_WIDTH = 3;
            plan = FindBestSoloJourneysMix(basePlan,true,true,true);
        } else{
            SOLO_WIDTH = 12;
            plan = FindBestSoloJourneysMix(basePlan,true,false,true);
        }
        plan.FinalEvaluation();
        ClearBansAndSuggestions();
//       Log.log("Leaked after greedy "  + Map.leakedMaps  + "  illegal: " + illegalMoves  +  " careful: " + Map.carefulWereLeaking, Log.LogType.PLANS);


        AddLastTurnSuggestions(HandwavyWeights.PlanLastTurnSuggestions);
        Stopwatch.Start(81);

        if(Map.width == 64 && MyBot.playerCount == 4){
            width *= 0.6;
        }

        if(timeErrors > 150){
            width = Math.min(width,5);

        }
        else if(timeErrors > 90){
            width = Math.min(width,10);
        }
        else if(timeErrors > 90){
            width = Math.min(width,15);
        }
        else if(timeErrors > 60){
            width = Math.min(width,20);

        }
//        if(timeErrors > 150){
//            width = Math.min(width,5);
//
//        }
//        else if(timeErrors > 90){
//            width = Math.min(width,10);
//        }
//        else if(timeErrors > 90){
//            width = Math.min(width,15);
//        }
//        else if(timeErrors > 60){
//            width = Math.min(width,20);
//        }

        SOLO_WIDTH = width;





        Plan bestPlan = FindBestSoloJourneysMix(plan,false,true,true);
//        Log.log("Leaked after solo "  + Map.leakedMaps  + "  illegal: " + illegalMoves  +  " careful: " + Map.carefulWereLeaking, Log.LogType.PLANS);

        long timeleft = (MyBot.TIME_ALLOWED - (System.currentTimeMillis() - MyBot.startTurn)) - MINIMUM_TIME_LEFT_IN_MS;

        if(HandwavyWeights.MinimalPostMechanicV3 == 0) {
            bestPlan = CollisionFixer(bestPlan);
        }else if(HandwavyWeights.MinimalPostMechanicV3 == 1){
            if(timeleft > 500){
                REVERSE_ORDER = true;
                bestPlan = FindBestSoloJourneysMix(bestPlan,false,true,false);
                REVERSE_ORDER = false;
                timeleft = (MyBot.TIME_ALLOWED - (System.currentTimeMillis() - MyBot.startTurn)) - MINIMUM_TIME_LEFT_IN_MS;
            }

            bestPlan = CollisionFixer(bestPlan);
        }else if(HandwavyWeights.MinimalPostMechanicV3 == 2){
            if(timeleft > 400){
                REVERSE_ORDER = true;
                Plan newplan = FindBestSoloJourneysMix(bestPlan,true,true,false);
                REVERSE_ORDER = false;
                if(newplan.finalScore > bestPlan.finalScore){
                    bestPlan.CleanUpMaps();
                    bestPlan = newplan;
                }
                timeleft = (MyBot.TIME_ALLOWED - (System.currentTimeMillis() - MyBot.startTurn)) - MINIMUM_TIME_LEFT_IN_MS;
            }

            bestPlan = CollisionFixer(bestPlan);
        }

        else if(HandwavyWeights.MinimalPostMechanicV3 == 3){   //Best results locally, but causes performance issues / crashes on server
            bestPlan = MajorFixemUpper(bestPlan,20);
            bestPlan = CollisionFixer(bestPlan);
        }
        else if(HandwavyWeights.MinimalPostMechanicV3 == 4){
            if(timeleft > 400 && Map.carefulWereLeaking == 0) {
                SOLO_WIDTH = 40;
                Plan newplan  = FindBestSoloJourneysMix(bestPlan, false, false,false);
                if(newplan.finalScore > bestPlan.finalScore){
                    bestPlan.CleanUpMaps();
                    bestPlan = newplan;
                }
            }
            bestPlan = CollisionFixer(bestPlan);

        }
        else if(HandwavyWeights.MinimalPostMechanicV3 == 5){
            if(timeleft > 400 && Map.carefulWereLeaking == 0) {
                SOLO_WIDTH = 40;
                REVERSE_ORDER = true;
                Plan newplan  = FindBestSoloJourneysMix(bestPlan, false, false,false);
                REVERSE_ORDER = false;

                if(newplan.finalScore > bestPlan.finalScore){
                    bestPlan.CleanUpMaps();
                    bestPlan = newplan;
                }

            }
            bestPlan = CollisionFixer(bestPlan);

        }else{
            if(timeleft > 700){
                bestPlan = MajorFixemUpper(bestPlan,10);
            }

            timeleft = (MyBot.TIME_ALLOWED - (System.currentTimeMillis() - MyBot.startTurn)) - MINIMUM_TIME_LEFT_IN_MS;

            if(timeleft > 500){
                REVERSE_ORDER = true;
                Plan newplan = FindBestSoloJourneysMix(bestPlan,true,true,false);
                REVERSE_ORDER = false;
                if(newplan.finalScore > bestPlan.finalScore){
                    bestPlan.CleanUpMaps();
                    bestPlan = newplan;
                }
            }
            bestPlan = CollisionFixer(bestPlan);


        }



//        Log.log("Leaked after fixer "  + Map.leakedMaps  + "  illegal: " + illegalMoves  +  " careful: " + Map.carefulWereLeaking, Log.LogType.PLANS);

        Stopwatch.Stop(81,"Journey");
        return bestPlan;
    }

    //An older attempt at generating plans, no longer used
    public static Plan GetMinimalExpanded(){
        if(timeErrors > 40){
//            Log.log("SEVERE TIME ISSUES, REVERT TO SIMPLE", Log.LogType.PLANS);
            return GetMinimal(HandwavyWeights.WIDTH_SOLO_MINIMAL);
        }
        else {


            Plan plan = MoveBasedGreedySearch();
            plan.FinalEvaluation();

            AddLastTurnSuggestions(HandwavyWeights.PlanLastTurnSuggestions);
            Stopwatch.Start(81);

            SOLO_WIDTH = HandwavyWeights.WIDTH_SOLO_MINIMAL;
            Plan bestPlan = FindBestSoloJourneysMix(plan, false,true,true);
            bestPlan = MajorFixemUpper(bestPlan,20);
            Stopwatch.Stop(81, "Journey");
            ClearBansAndSuggestions();
            return bestPlan;
        }

       //
     //   bestPlan = CollisionFixer(bestPlan);

    }

    //An older attempt at generating plans, no longer used
    public static Plan GetGreedySpam(Plan bestPlan){

        if(bestPlan == null) {
            bestPlan = GreedySearch(SEARCH_DEPTH, 0f, null, 0, false);
        }

        int i = 0;
        //Do some random bans, and higher randomness on eval
        for (; i < 1000; i++) {
            if (ShouldQuit()) {
                break;
            }
            int totalbans = 0;
            int totalbans2 = 0;
            //Randomly ban some of the moves from the best plan, hope we stumble across something better
            float randomFactor = (9 - (i % 10)) * HandwavyWeights.RANDOMNESSCYCLE + (i * HandwavyWeights.ADVANCERANDOMNESS); //randomness goes up and down a bit. between 0.1 and 0.01. also increases slowly if we go enough iterations
            for (int turn = 0; turn < SEARCH_DEPTH; turn++) {
                for (int moveindex = 0; moveindex < Map.staticMyShipCount; moveindex++) {
                    Move m = bestPlan.movesPerTurn[turn][moveindex];

                    if (m != null) {
                        float turnBasedRandFactor = randomFactor;

                        if (turn == 0) {
                            //First step behavior is by far the most important
                            turnBasedRandFactor *= HandwavyWeights.TURNBASEDRANDFACTOR1;
                        } else if (turn == 1) {
                            turnBasedRandFactor *= HandwavyWeights.TURNBASEDRANDFACTOR2;
                        } else if (turn == 2) {
                            turnBasedRandFactor *= HandwavyWeights.TURNBASEDRANDFACTOR3;
                        } else {
                            turnBasedRandFactor *= HandwavyWeights.TURNBASEDRANDFACTOR4;
                        }

                        if (MyBot.rand.nextFloat() < turnBasedRandFactor) {
                            banlist[turn].add(m);
                            totalbans++;
                        } else if (m.isStandStill() && MyBot.rand.nextFloat() < turnBasedRandFactor) {
                            AddtoSuggestionList(m, turn, 200);
                        } else if (turn <= 0 && MyBot.rand.nextFloat() < turnBasedRandFactor && Map.currentMap.IsEnemyShipAt(m.to)) {
                            AddtoSuggestionList(m, turn, 100);
                        }

                        //Ban moves that were in the first plan, but not the best plan. These would be likely repeat-mistakes,
                        //as their presence in the first plan suggests our algo thinks these moves are great
                        //We'd like to keep working on our best plan, not keep trying to deal with the 'obvious' plan

                        if (MyBot.rand.nextFloat() < HandwavyWeights.BANODDS) {
                            banlist[turn].add(m);
                            totalbans++;
                            totalbans2++;
                        }
                        //Add a bit of extra desire to things already in the best plan
                        if (MyBot.rand.nextFloat() < HandwavyWeights.ADDDESIREODDS) {
                            suggestionList[turn].put(m, HandwavyWeights.ADDDESIRE + MyBot.rand.nextFloat() * HandwavyWeights.ADDDESIRERAND);
                        }
                    }
                }


                for (CheapShip s : Map.currentMap.myShips) {
                    if (s != null) {
                        prioChanges[turn].put(s, MyBot.rand.nextInt(HandwavyWeights.PRIORAND));
                    }
                }


                //Last turns algo probably had some sweet ideas. Let's try some suggestions sometimes
                if (MyBot.rand.nextFloat() < HandwavyWeights.CHANCEADDSUGGESTIONS && lastTurnBestPlan != null && turn + 1 < lastTurnBestPlan.movesPerTurn.length) {
                    for (int moveindex = 0; moveindex < lastTurnBestPlan.movesPerTurn[turn].length; moveindex++) {
                        suggestionList[turn].put(lastTurnBestPlan.movesPerTurn[turn][moveindex], MyBot.rand.nextFloat() * HandwavyWeights.SUGGESTIONSFROMLASTTURN);
                    }
                }

            }


            float randomFactor2 = HandwavyWeights.EVALEXTRARAND + (6 - (i % 7)) * HandwavyWeights.EVALEXTRARANDFACTOR;

            //Also include some randomness on the algorithm evaluator. At a different cycle than the other random factor
            Plan p = GreedySearch(SEARCH_DEPTH, randomFactor2, null, 0,true);
            p.FinalEvaluation();
            //Log.log("2nd stage search ( " + randomFactor + " , " + randomFactor2 +  " , "  + totalbans2 + "/" + totalbans  +  "), finalScore: " + p.finalScore + ",  time left: "  +  (MAX_TIME_TURN -  (System.currentTimeMillis() - MyBot.startTurn)) , Log.LogType.PLANS);
            if (!p.markedIlllegal &&  p.finalScore > bestPlan.finalScore - 500) {
                Plan n = MoveFixemUpper(p);
                p.CleanUpMaps();
                p = n;
            }

            if (p.finalScore > bestPlan.finalScore) {
                bestPlan.CleanUpMaps();
                bestPlan = p;
                //Log.log(" ", Log.LogType.PLANS);
//                Log.log("Overruled plan in 2nd!!!,  finalScore: " + bestPlan.finalScore, Log.LogType.PLANS);
                //Log.log(" ", Log.LogType.PLANS);
                //Log.log(bestPlan.movesToString(), Log.LogType.PLANS);
                //Log.log(" ", Log.LogType.PLANS);
            }
            ClearBansAndSuggestions();
        }

        return bestPlan;

    }

    //An older attempt at generating plans, no longer used
    public static Plan GetHugeMonte(){

        Plan plan = MonteCarloSearch(1,1.0);
        plan = MoveFixemUpper(plan);


        for(int i=0; i < 1000; i++){
            if(ShouldQuit())break;
            Plan newplan = MonteCarloSearch(1,1.5 *  (1.0 +  i / 100.0));
            newplan = MoveFixemUpper(plan);

            if(newplan.finalScore > plan.finalScore){
                plan = newplan;
            }
        }

        plan = CollisionFixer(plan);
        return plan;
    }

    //An older attempt at generating plans, no longer used
    public static Plan GetNewStyle(){

        Plan plan = MoveBasedGreedySearch();
        plan.FinalEvaluation();
//        Log.log("Move based plan: " + plan.finalResultToString(true), Log.LogType.PLANS);

        SOLO_WIDTH = 10;
        plan = FindBestSoloJourneysMix(plan,false,true,true);
        SOLO_WIDTH = 50;
        plan = FindBestSoloJourneysMix(plan,false,true,false);

//        Log.log("After journey: " + plan.finalResultToString(true), Log.LogType.PLANS);

        plan = MajorFixemUpper(plan,20);

//        Log.log("After major fixem fixes: " + plan.finalResultToString(true), Log.LogType.PLANS);


        Plan bestPlan = CollisionFixer(plan);

//        Log.log("After fixes: " + bestPlan.finalResultToString(true), Log.LogType.PLANS);
        return bestPlan;
    }

    //An older attempt at generating plans, no longer used
    public static Plan GetExtremelyMinimal(){

        Plan plan = MoveBasedGreedySearch();
        plan.FinalEvaluation();

//        Log.log("Move based plan: " + plan.finalResultToString(true), Log.LogType.PLANS);

        return plan;
    }

    //An older attempt at generating plans, no longer used
    public static Plan GetSoloEscalation(){
        SOLO_WIDTH = 5;
        Plan plan = FindBestSoloJourneysMix(basePlan,true,true,true);
        SOLO_WIDTH = 30;
        plan = FindBestSoloJourneysMix(plan,false,true,true);
        SOLO_WIDTH = 100;
        plan = FindBestSoloJourneysMix(plan,false,true,false);
        return plan;
    }

    //An older attempt at generating plans, no longer used
    public static Plan GetSuggestionLed(){
        AddLastTurnSuggestions(1000f);
        SOLO_WIDTH = 3;
        Plan plan = FindBestSoloJourneysMix(basePlan,true,true,true);
        ClearBansAndSuggestions();
        AddLastTurnSuggestions(HandwavyWeights.PlanLastTurnSuggestions);
        SOLO_WIDTH = 20;
        plan = FindBestSoloJourneysMix(plan,false,true,true);
        ClearBansAndSuggestions();
        plan = MajorFixemUpper(plan,20);

        return plan;
    }

    //An older attempt at generating plans, no longer used
    public static Plan GetFixemOnly(){
        Plan plan = MoveBasedGreedySearch();
        plan.FinalEvaluation();
        plan = MajorFixemUpper(plan,20);
        return plan;
    }

    //An older attempt at generating plans, no longer used
    public static Plan GetOldFashioned(){


//        Log.log("Leaked before "  + Map.leakedMaps  + "  illegal: " + illegalMoves, Log.LogType.PLANS);
        //First the standard, simple approach. Should frequently be the best and is a good starting point
        Plan firstPlan = GreedySearch(SEARCH_DEPTH, 0f,null,0,false);
        firstPlan.FinalEvaluation();

//        Log.log("Leaked 1 " + Map.leakedMaps + "  illegal: " + illegalMoves, Log.LogType.PLANS);
        Stopwatch.Stop("Greedy first");
        Log.log("\r\nFirst plan: \r\n" + firstPlan.finalScore, Log.LogType.PLANS);
        firstPlan = MoveFixemUpper(firstPlan);


        //PRINTER = false;
        Plan bestPlan = firstPlan;

//        Log.log("Leaked 2 " + Map.leakedMaps + "  illegal: " + illegalMoves, Log.LogType.PLANS);

        HashSet<Move>[] firstPlanMoves = new HashSet[SEARCH_DEPTH];
        for (int i = 0; i < SEARCH_DEPTH; i++) {
            firstPlanMoves[i] = new HashSet<>();
            for(int moveindex =0; moveindex < Map.staticMyShipCount;moveindex++){
                firstPlanMoves[i].add(basePlan.movesPerTurn[i][moveindex]);
            }
        }

        //Log.log("\r\nFirst plan: \r\n" + firstPlan.finalResultToString(true), Log.LogType.PLANS);

        Plan montePlan = null, inspiredPlan = null;

        Stopwatch.Start(7);
        //Plan sadadsa = new Plan(Map.currentMap,6);
        if(timeErrors < 20) {
            Stopwatch.Start(71);
            montePlan = MonteCarloSearch(1,1.0);
//            Log.log("Leaked 3 " + Map.leakedMaps + "  illegal: " + illegalMoves, Log.LogType.PLANS);
            Stopwatch.Stop(71, "Monte carlo");
            Log.log("\r\n\r\nMonte carlo: " + montePlan.finalScore, Log.LogType.PLANS);
            Log.log(montePlan.movesToString(), Log.LogType.PLANS);
            montePlan = MoveFixemUpper(montePlan);
//            Log.log("Leaked 4 " + Map.leakedMaps + "  illegal: " + illegalMoves, Log.LogType.PLANS);

            if (!montePlan.markedIlllegal && montePlan.finalScore > bestPlan.finalScore) {
                bestPlan = montePlan;
                Log.log(montePlan.movesToString(), Log.LogType.PLANS);
            }
        }

        if(timeErrors < 30) {
            AddLastTurnSuggestions(1000f);
            inspiredPlan = GreedySearch(SEARCH_DEPTH, 0f, null, 0, false);
            inspiredPlan.FinalEvaluation();
            Log.log("Inspired plan: " + bestPlan.finalScore, Log.LogType.PLANS);

//            Log.log("Leaked 5 " + Map.leakedMaps + "  illegal: " + illegalMoves, Log.LogType.PLANS);


            inspiredPlan = MoveFixemUpper(inspiredPlan);
            ClearBansAndSuggestions();

//            Log.log("Leaked 6 " + Map.leakedMaps + "  illegal: " + illegalMoves, Log.LogType.PLANS);

            if (!inspiredPlan.markedIlllegal && inspiredPlan.finalScore > bestPlan.finalScore) {
                bestPlan = inspiredPlan;
                Log.log(inspiredPlan.movesToString(), Log.LogType.PLANS);
            }
        }

        SOLO_WIDTH = 5;
        AddLastTurnSuggestions(HandwavyWeights.PlanLastTurnSuggestions);
        Stopwatch.Start(81);
        Plan soloJourneyPlan = FindBestSoloJourneysMix(bestPlan,false,true,true);

        //TODO: A move 'straightener', which looks at short move sequences and thinks of alternate ones. For example, if
        //we start with  move->stand still. Check whether  stand->move might've been better.
        //or, if we do move->move and end up on a diagonal tile, check whether move->move using the other tile might've been better
        //should be really cheap (requires only a single sim + evaluate on every try)
        //questionable whether itll do much better though



//        Log.log("Leaked-7  "  + Map.leakedMaps + "  illegal: " + illegalMoves, Log.LogType.PLANS);


        Stopwatch.Stop(81,"Solo Journey");

        if (!soloJourneyPlan.markedIlllegal && soloJourneyPlan.finalScore > bestPlan.finalScore) {
            bestPlan = soloJourneyPlan;
        }

//          Log.log("\r\n\r\nSolo journey: \r\n" + soloJourneyPlan.finalResultToString(true), Log.LogType.PLANS);
        Log.log("\r\n\r\nSolo journey: " + soloJourneyPlan.finalScore, Log.LogType.PLANS);
        Log.log("Time left after journey" + (MyBot.TIME_ALLOWED - (System.currentTimeMillis() - MyBot.startTurn)), Log.LogType.MAIN);

        ClearBansAndSuggestions();

        int ranFirstStage = 0;
        int ranSecondStage = 0;

//            firststageloop:
//            if (Map.currentMap.myShipsCount < HandwavyWeights.ELIM_FIRST_STAGE) { //not really worth doing these with many ships, might never get to 2nd stage
//                int tracker = 0;
//                //Now, do the same search, but try changing up the first move of every ship that can move, bit of randomness too
//                for (Move m : firstPlan.movesPerTurn[0]) {
//                    if (ShouldQuit()) {
//                        Log.log("Aborting at first stage, done:  " + tracker, Log.LogType.PLANS);
//                        break firststageloop;
//                    }
//
//                    if (!m.isStandStill() || (Map.currentMap.GetHaliteAt(m.from) * 0.1 < m.ship.halite)) {
//
//                        banlist[0].add(m);
//                        Plan p = GreedySearch(SEARCH_DEPTH, 0.1f,null);
//                        if (p != null) {
//                            ranFirstStage++;
//                            p.FinalEvaluation();
//                            //Log.log("First stage search complete, finalScore: " + p.finalScore + "  time left: " + (MAX_TIME_TURN - (System.currentTimeMillis() - MyBot.startTurn)), Log.LogType.PLANS);
//
//                            if (p.finalScore > bestPlan.finalScore) {
//                                bestperType[2]++;
//                                bestPlan = p;
//                                //Log.log(" ", Log.LogType.PLANS);
//                                Log.log("Overruled plan in first!!,  finalScore: " + bestPlan.finalScore, Log.LogType.PLANS);
//                                //Log.log(" ", Log.LogType.PLANS);
//                                //Log.log(bestPlan.movesToString(), Log.LogType.PLANS);
//                                //Log.log(" ", Log.LogType.PLANS);
//                            }
//                            tracker++;
//                        }
//                    }
//                    banlist[0].clear();
//                }
//            }


        int i = 0;
        //Do some random bans, and higher randomness on eval
        for (; i < 10; i++) {
            if (ShouldQuit()) {
                break;
            }

            int totalbans = 0;
            int totalbans2 = 0;

            //Randomly ban some of the moves from the best plan, hope we stumble across something better
            float randomFactor = (9 - (i % 10)) * HandwavyWeights.RANDOMNESSCYCLE + (i * HandwavyWeights.ADVANCERANDOMNESS); //randomness goes up and down a bit. between 0.1 and 0.01. also increases slowly if we go enough iterations
            for (int turn = 0; turn < SEARCH_DEPTH; turn++) {
                for (int moveindex = 0; moveindex < Map.staticMyShipCount; moveindex++) {
                    Move m = bestPlan.movesPerTurn[turn][moveindex];

                    if (m != null) {
                        float turnBasedRandFactor = randomFactor;

                        if (turn == 0) {
                            //First step behavior is by far the most important
                            turnBasedRandFactor *= HandwavyWeights.TURNBASEDRANDFACTOR1;
                        } else if (turn == 1) {
                            turnBasedRandFactor *= HandwavyWeights.TURNBASEDRANDFACTOR2;
                        } else if (turn == 2) {
                            turnBasedRandFactor *= HandwavyWeights.TURNBASEDRANDFACTOR3;
                        } else {
                            turnBasedRandFactor *= HandwavyWeights.TURNBASEDRANDFACTOR4;
                        }

                        if (MyBot.rand.nextFloat() < turnBasedRandFactor) {
                            banlist[turn].add(m);
                            totalbans++;
                        } else if (m.isStandStill() && MyBot.rand.nextFloat() < turnBasedRandFactor) {
                            AddtoSuggestionList(m, turn, 200);
                        } else if (turn <= 0 && MyBot.rand.nextFloat() < turnBasedRandFactor && Map.currentMap.IsEnemyShipAt(m.to)) {
                            AddtoSuggestionList(m, turn, 100);
                        }

                        //Ban moves that were in the first plan, but not the best plan. These would be likely repeat-mistakes,
                        //as their presence in the first plan suggests our algo thinks these moves are great
                        //We'd like to keep working on our best plan, not keep trying to deal with the 'obvious' plan
                        if (!firstPlanMoves[turn].contains(m)) {
                            if (MyBot.rand.nextFloat() < HandwavyWeights.BANODDS) {
                                banlist[turn].add(m);
                                totalbans++;
                                totalbans2++;
                            }
                        }

                        //Add a bit of extra desire to things already in the best plan
                        if (MyBot.rand.nextFloat() < HandwavyWeights.ADDDESIREODDS) {
                            suggestionList[turn].put(m, HandwavyWeights.ADDDESIRE + MyBot.rand.nextFloat() * HandwavyWeights.ADDDESIRERAND);
                        }

                    }
                }


                for (CheapShip s : Map.currentMap.myShips) {
                    if (s != null) {
                        prioChanges[turn].put(s, MyBot.rand.nextInt(HandwavyWeights.PRIORAND));
                    }
                }


                //Last turns algo probably had some sweet ideas. Let's try some suggestions sometimes
                if (MyBot.rand.nextFloat() < HandwavyWeights.CHANCEADDSUGGESTIONS && lastTurnBestPlan != null && turn + 1 < lastTurnBestPlan.movesPerTurn.length) {
                    for (int moveindex = 0; moveindex < lastTurnBestPlan.movesPerTurn[turn].length; moveindex++) {
                        suggestionList[turn].put(lastTurnBestPlan.movesPerTurn[turn][moveindex], MyBot.rand.nextFloat() * HandwavyWeights.SUGGESTIONSFROMLASTTURN);
                    }
                }

            }


            float randomFactor2 = HandwavyWeights.EVALEXTRARAND + (6 - (i % 7)) * HandwavyWeights.EVALEXTRARANDFACTOR;

            //Also include some randomness on the algorithm evaluator. At a different cycle than the other random factor
            Plan p = GreedySearch(SEARCH_DEPTH, randomFactor2, null, 0,true);
            p.FinalEvaluation();
            ranSecondStage++;

            //Log.log("2nd stage search ( " + randomFactor + " , " + randomFactor2 +  " , "  + totalbans2 + "/" + totalbans  +  "), finalScore: " + p.finalScore + ",  time left: "  +  (MAX_TIME_TURN -  (System.currentTimeMillis() - MyBot.startTurn)) , Log.LogType.PLANS);

            if (!p.markedIlllegal &&  p.finalScore > bestPlan.finalScore - 500) {
                p = MoveFixemUpper(p);
            }

            if (p.finalScore > bestPlan.finalScore) {
                bestPlan = p;
                //Log.log(" ", Log.LogType.PLANS);
                Log.log("Overruled plan in 2nd!!!,  finalScore: " + bestPlan.finalScore, Log.LogType.PLANS);
                //Log.log(" ", Log.LogType.PLANS);
                //Log.log(bestPlan.movesToString(), Log.LogType.PLANS);
                //Log.log(" ", Log.LogType.PLANS);
            }
            ClearBansAndSuggestions();
        }

//        Log.log("Leaked-8  "  + Map.leakedMaps + "  illegal: " + illegalMoves, Log.LogType.PLANS);

        if(bestPlan == firstPlan){
            bestperType[0]++;
        } else if(bestPlan == montePlan){
            bestperType[1]++;
        } else if(bestPlan == inspiredPlan){
            bestperType[2]++;
        } else if(bestPlan == soloJourneyPlan){
            bestperType[3]++;
        } else{
            bestperType[4]++;
        }

//        if(!MyBot.MEDIUM_CALCULATIONS) {
            bestPlan = MajorFixemUpper(bestPlan,5);
//            Log.log("Leaked-9 " + Map.leakedMaps + "  illegal: " + illegalMoves , Log.LogType.PLANS);
//        }
        //Final check.. probably not neccessary, but oh well
        bestPlan = CollisionFixer(bestPlan);


//            if(IMAGING) {
//                Log.log("!!!!!! FINAL RESULT !!!!!!", Log.LogType.IMAGING);
//
//                Log.log(bestPlan.finalResultToString(true), Log.LogType.IMAGING);
//
//                String str = "Greedy:  ";
//
//                for(int turn =0; turn < SEARCH_DEPTH; turn++) {
//                    str += " Turn: " + turn + " \r\n\r\n";
//
//                    for (int y = 0; y < Map.height; y++) {
//                        for (int x = 0; x < Map.width; x++) {
//                            str +=  String.format("%5d",VISITED_GREEDY[turn][x][y]) + " ";
//
//
//                        }
//                        str += "\r\n";
//                    }
//
//
//                }
//
//
//                str += "\r\n\r\nSolo:  ";
//
//                for(int turn =0; turn < SEARCH_DEPTH; turn++) {
//                    str += " Turn: " + turn + " \r\n\r\n";
//
//                    for (int y = 0; y < Map.height; y++) {
//                        for (int x = 0; x < Map.width; x++) {
//                            str +=  String.format("%5d",VISITED_SOLO[turn][x][y]) + " ";
//                        }
//                        str += "\r\n";
//                    }
//                }
//
//                str += "\r\n\r\nMax scores::  ";
//
//                for(int turn =0; turn < SEARCH_DEPTH; turn++) {
//                    str += " Turn: " + turn + " \r\n\r\n";
//
//                    for (int y = 0; y < Map.height; y++) {
//                        for (int x = 0; x < Map.width; x++) {
//                            str +=  String.format("%6d", ((int)MAX_SCORE[turn][x][y])) + " ";
//                        }
//                        str += "\r\n";
//                    }
//
//
//                }
//                str += "\r\n";
//                Log.log(str, Log.LogType.IMAGING);
//            }


        //TODO: move fixem upper if solo journey is not our best plan. Just get a cheap solo journey (width <= 5 maybe 1) for every ship, and see if it's better
        if(MyBot.ALLOW_LOGGING) {
//            if (MyBot.MONSTER_CALCULATION) {
//                SOLO_WIDTH = 5000;
//                MINIMUM_TIME_LEFT_IN_MS = -1000000;
//
//                Plan monsterPlan = FindBestSoloJourneysMix(basePlan, false);
//
//                Log.log("MONSTER SCORE: " + monsterPlan.finalScore + " vs, best: " + bestPlan.finalScore, Log.LogType.PLANS);
//                Log.log(monsterPlan.finalResultToString(true), Log.LogType.PLANS);
//            }


            Log.log("Ran " + ranFirstStage + " first stage ", Log.LogType.PLANS);
            Log.log("Ran " + i + " 2nd stage ", Log.LogType.PLANS);


            Log.log("\r\n\r\nFINISHED!!\r\nTried first: " + firstPlan + "  second: " + ranSecondStage, Log.LogType.PLANS);
            Log.log("Winner: \r\n" + bestPlan.finalResultToString(true), Log.LogType.PLANS);

            Log.log("\r\n\r\n Record: " + bestperType[0] + "," + bestperType[1] + ", " + bestperType[2] + ", " + bestperType[3] + ", " + bestperType[4] + ", " + bestperType[5], Log.LogType.PLANS);

        }
        return bestPlan;
    }


    private static void AddLastTurnSuggestions(float amount){
        if (lastTurnBestPlan != null) {

            int max = Math.min(SEARCH_DEPTH,lastTurnBestPlan.movesPerTurn.length-1);
            for (int turn = 0; turn < max - 1; turn++) {

                for(int moveindex =0; moveindex <  lastTurnBestPlan.movesPerTurn[turn+1].length;moveindex++){
                    if(lastTurnBestPlan.movesPerTurn[turn+1][moveindex] != null) {
                        suggestionList[turn].put(lastTurnBestPlan.movesPerTurn[turn + 1][moveindex], amount);
                    }
                }

            }
        }
    }

    private static void AddtoSuggestionList(Move m, int turn, float desire){
        if(suggestionList[turn].containsKey(m)){
            suggestionList[turn].put(m,Math.max(suggestionList[turn].get(m),desire)); //lets not overlap suggestions
        }else{
            suggestionList[turn].put(m,desire);

        }
    }

    public static void ClearBansAndSuggestions(){
        for(int i = 0; i < SEARCH_DEPTH; i++){
            banlist[i].clear();
            suggestionList[i].clear();
            prioChanges[i].clear();
        }
    }


    public static boolean twoplayers = false;
    public static boolean behind2p = false;
    public static boolean behind4p = false;
    public static boolean mapEmpty = false;
    public static boolean killTime = false;
    public static float collisionKnob;
    //For performance reasons. Done every turn
    public static void preCalcEvaluateStuff(){
        adjustedForAverageHalite  =  Math.min( HandwavyWeights.AverageHaliteNormalizeCap,Plan.AverageHalite / HandwavyWeights.AverageHaliteNormalizeVal); //if the average halite on the map drops low, many of the goal factors become too important
        collisionKnob = HandwavyWeights.CollisionKnob * HandwavyWeights.CollisionsKnobGameType[MyBot.GAMETYPE_PLAYERS] * HandwavyWeights.CollisionsKnobDensity[MyBot.GAMETYPE_DENSITY];

        twoplayers =  Game.players.size() == 2;
        mapEmpty = Map.curMapHaliteSum < MyBot.MapStartHalite * HandwavyWeights.MapEmpty || AverageHalite < HandwavyWeights.MapAverageHaliteEmpty;

        if(twoplayers){
            if(Map.staticEnemyShipCount2 > Map.staticMyShipCount + 2){
                behind2p = true;
            }
        }else{
            if(Map.staticEnemyShipCount2 / 3 > Map.staticMyShipCount + 2){
                behind4p = true;
            }

            if(AverageHalite < 10 && MyBot.turnsLeft > 20 && Map.staticMyShipCount > 25){
                killTime = true;
            }

        }

//        if (turnsLeft > 7) {


            float worth = HandwavyWeights.ShipCountStart[MyBot.GAMETYPE_PLAYERS] * (1f - MyBot.proportionGame) + HandwavyWeights.ShipCountEnd[MyBot.GAMETYPE_PLAYERS] * MyBot.proportionGame;
            //float worth = HandwavyWeights.ShipCountV3[MyBot.GAMETYPE_PLAYERS] + HandwavyWeights.ShipCountScaleTurns[MyBot.GAMETYPE_PLAYERS] * MyBot.turn;
            worth *= HandwavyWeights.ShipCountMapSizeMultiplier[MyBot.GAMETYPE_SIZE]  * HandwavyWeights.ShipCountDensityMultiplier[MyBot.GAMETYPE_DENSITY];
            if(behind2p){
                worth *= HandwavyWeights.MultiplierShipWorthIfBehind[0];
            } else if(behind4p){
                worth *= HandwavyWeights.MultiplierShipWorthIfBehind[1];
            }
            shipWorth = worth * collisionKnob;
//        } else {
//            shipWorth = HandwavyWeights.ShipCountEndGameV2[MyBot.GAMETYPE_PLAYERS] + HandwavyWeights.ShipCountScaleTurns[MyBot.GAMETYPE_PLAYERS];
//        }




        turnImportance = new float[SEARCH_DEPTH];
        turnImportanceFull = new float[SEARCH_DEPTH];

//        if(MyBot.EXPERIMENTAL_MODE) {
//            for (int i = 0; i < SEARCH_DEPTH; i++) {
//                turnImportanceFull[i] = 1.5f * (float) Math.pow(HandwavyWeights.TImportanceScaling, i); //the 1.5 is an artifact meant to mimic the old way of doing things
//            }
//        }else {
//
            for (int i = 4; i < SEARCH_DEPTH - 1; i++) {
                turnImportance[i] = HandwavyWeights.TBaseImportance  * HandwavyWeights.TOtherImportancePlayersMult[MyBot.GAMETYPE_PLAYERS];
                turnImportanceFull[i] = HandwavyWeights.TBaseImportanceFull * HandwavyWeights.TOtherImportanceFullSizeMult[MyBot.GAMETYPE_SIZE] * HandwavyWeights.TOtherImportanceFullPlayersMult[MyBot.GAMETYPE_PLAYERS];
            }
            turnImportance[SEARCH_DEPTH - 1] = HandwavyWeights.TFinalImportance  * HandwavyWeights.TOtherImportancePlayersMult[MyBot.GAMETYPE_PLAYERS];
            turnImportanceFull[SEARCH_DEPTH - 1] = HandwavyWeights.TFinalImportanceFull * HandwavyWeights.TOtherImportanceFullSizeMult[MyBot.GAMETYPE_SIZE]  * HandwavyWeights.TOtherImportanceFullPlayersMult[MyBot.GAMETYPE_PLAYERS];
            turnImportance[0] = HandwavyWeights.T0Importance  * HandwavyWeights.T0ImportancePlayersMult[MyBot.GAMETYPE_PLAYERS];
            turnImportanceFull[0] = HandwavyWeights.T0ImportanceFull * HandwavyWeights.T0ImportanceFullSizeMult[MyBot.GAMETYPE_SIZE]  * HandwavyWeights.T0ImportanceFullPlayersMult[MyBot.GAMETYPE_PLAYERS];
            if (SEARCH_DEPTH > 1) {
                turnImportance[1] = HandwavyWeights.T1Importance  * HandwavyWeights.T1ImportancePlayersMult[MyBot.GAMETYPE_PLAYERS];
                turnImportanceFull[1] = HandwavyWeights.T1ImportanceFull * HandwavyWeights.T1ImportanceFullSizeMult[MyBot.GAMETYPE_SIZE]  * HandwavyWeights.T1ImportanceFullPlayersMult[MyBot.GAMETYPE_PLAYERS];
                if (SEARCH_DEPTH > 2) {
                    turnImportance[2] = HandwavyWeights.T2Importance  * HandwavyWeights.T2ImportancePlayersMult[MyBot.GAMETYPE_PLAYERS];
                    turnImportanceFull[2] = HandwavyWeights.T2ImportanceFull * HandwavyWeights.T2ImportanceFullSizeMult[MyBot.GAMETYPE_SIZE]  * HandwavyWeights.T2ImportanceFullPlayersMult[MyBot.GAMETYPE_PLAYERS];
                    if (SEARCH_DEPTH > 3) {
                        turnImportance[3] = HandwavyWeights.T3Importance;
                        turnImportanceFull[3] = HandwavyWeights.T3ImportanceFull  * HandwavyWeights.TOtherImportancePlayersMult[MyBot.GAMETYPE_PLAYERS];
                        turnImportanceFull[2] = HandwavyWeights.T2ImportanceFull * HandwavyWeights.TOtherImportanceFullSizeMult[MyBot.GAMETYPE_SIZE]  * HandwavyWeights.TOtherImportanceFullPlayersMult[MyBot.GAMETYPE_PLAYERS];

                    }
                }
            }
//        }


        moveWorth = collisionKnob * HandwavyWeights.MoveCount[MyBot.GAMETYPE_PLAYERS] / SEARCH_DEPTH;

        for(int i = 0 ; i < 4001; i++){
            evalUsableHalite[i] = 0;

            if(i > AverageHalite){
                evalUsableHalite[i] += HandwavyWeights.AboveHaliteAvgBonus;
            }
            evalUsableHalite[i] += HandwavyWeights.RelativeHaliteAvgFactor * (i / AverageHalite);
            if (i > HandwavyWeights.BigTileV3) {
                evalUsableHalite[i] += HandwavyWeights.ExtraOnBigTileV3 + HandwavyWeights.HaliteBigTileMultiplierV4 * i;
            }
            if (i > Map.baseMeaningfulHalite) {
                evalUsableHalite[i] += HandwavyWeights.AboveMeaningfulHaliteStandstillBonus;
            }
            evalUsableHalite[i] += i * HandwavyWeights.HaliteMultiplierStandStillV2;

        }



        if (MyBot.turn < Constants.MAX_TURNS * 0.25) {
            playerMoneyMultiplier = HandwavyWeights.PlayerHaliteEarlyV3 + HandwavyWeights.PlayerHaliteUnBound ;
        } else if (MyBot.turn < Constants.MAX_TURNS * 0.55) {
            playerMoneyMultiplier =  HandwavyWeights.PlayerHaliteMediumV3 + HandwavyWeights.PlayerHaliteUnBound;
        } else if (MyBot.turn < Constants.MAX_TURNS * 0.75) {
            playerMoneyMultiplier =  HandwavyWeights.PlayerHaliteLateV3 + HandwavyWeights.PlayerHaliteUnBound;
        } else {
            playerMoneyMultiplier =  HandwavyWeights.PlayerHaliteVeryLateV3 + HandwavyWeights.PlayerHaliteUnBound;
        }

        playerMoneyDividedBy1000 = (Map.currentMap.playerMoney / 1000);

        if(MyBot.turn < 80) {
            boostForScore = HandwavyWeights.BoostFor1000Early;
        }else{
            boostForScore = HandwavyWeights.BoostFor1000;
        }

        shipHaliteMultiplier =HandwavyWeights.ShipHalite * HandwavyWeights.ShipHaliteScore + HandwavyWeights.ShipHalitePlayers[MyBot.GAMETYPE_PLAYERS] + HandwavyWeights.ShipHaliteSize[MyBot.GAMETYPE_SIZE] + HandwavyWeights.ShipHaliteDensity[MyBot.GAMETYPE_DENSITY];


        minHaliteForDropoffReturn = (int) Math.min(HandwavyWeights.DROP_DIST_MINDIST, Math.max(AverageHalite,40) * HandwavyWeights.DROP_DIST_MIN_AVG_BASED);


        float enemyShipWorth = ( HandwavyWeights.EnemyShipCountStart[MyBot.GAMETYPE_PLAYERS] * (1f-MyBot.proportionGame) +  HandwavyWeights.EnemyShipCountEnd[MyBot.GAMETYPE_PLAYERS]* MyBot.proportionGame);
        //enemyShipWorth = HandwavyWeights.EnemyShipCount[MyBot.GAMETYPE_PLAYERS] +  HandwavyWeights.EnemyShipCountScaleTurns[MyBot.GAMETYPE_PLAYERS] * MyBot.turn;
        if(Map.staticMyShipCount + HandwavyWeights.EnemyShipDifForBehind < Competitor.highestEnemyShipCount){
            enemyShipWorth *= HandwavyWeights.EnemyShipMultiplierBehind[MyBot.GAMETYPE_PLAYERS];
        }
        for(Tile t  : Map.tileList){
            float weightForenemyOnTile = (t.myShipsStartInRange4Avg -t.enemyShipsStartInRange4Avg) * HandwavyWeights.EnemyShipControlDifference[MyBot.GAMETYPE_PLAYERS];
            weightForenemyOnTile += HandwavyWeights.EnemyShipTileHalite[MyBot.GAMETYPE_PLAYERS] * t.haliteStartTurn;
            weightForenemyOnTile += enemyShipWorth;
            if (t.inControlZone) {
                weightForenemyOnTile += HandwavyWeights.EnemyShipCountControlZone[MyBot.GAMETYPE_PLAYERS];
            }

            if(t.turnsFromDropoff == 0){//Anti-blocking:
                weightForenemyOnTile += 10000;
            }

            if(t.turnsFromDropoff <= 5){
                t.weightForEShipOnTileWith100Halite = weightForenemyOnTile + HandwavyWeights.EnemyShipCloseToMyDropoffWithHalite[MyBot.GAMETYPE_PLAYERS];
            }else{
                t.weightForEShipOnTileWith100Halite = weightForenemyOnTile;
            }
            //REMEMBER: this value is subtracted from aggressivescore
            t.weightForEShipOnTileWithout100Halite = weightForenemyOnTile;


        }


        GatherWeight = HandwavyWeights.GatherScore  * HandwavyWeights.GatherScoreSizeMult[MyBot.GAMETYPE_SIZE]  * HandwavyWeights.GatherScorePlayersMult[MyBot.GAMETYPE_PLAYERS];
        MiscWeight = HandwavyWeights.MiscScore *  HandwavyWeights.MiscScoreSizeMult[MyBot.GAMETYPE_SIZE]   * HandwavyWeights.MiscScorePlayersMult[MyBot.GAMETYPE_PLAYERS];
        WasteWeight =  HandwavyWeights.WastePreventionScore *  HandwavyWeights.WastePreventionSizeMult[MyBot.GAMETYPE_SIZE]   * HandwavyWeights.WastePreventionPlayersMult[MyBot.GAMETYPE_PLAYERS];
        TurnInWeight = HandwavyWeights.TurnInScore  * HandwavyWeights.TurnInScoreSizeMult[MyBot.GAMETYPE_SIZE]   * HandwavyWeights.TurnInScorePlayersMult[MyBot.GAMETYPE_PLAYERS];
        LureWeight = HandwavyWeights.LuresScore  * HandwavyWeights.LuresScoreSizeMult[MyBot.GAMETYPE_SIZE]   * HandwavyWeights.LuresScorePlayersMult[MyBot.GAMETYPE_PLAYERS];
        MovesWeight = HandwavyWeights.IndividualMoveScore * HandwavyWeights.IndividualMoveScoreSizeMult[MyBot.GAMETYPE_SIZE]   * HandwavyWeights.IndividualMoveScorePlayersMult[MyBot.GAMETYPE_PLAYERS];
        InspireWeight =  HandwavyWeights.InspireScore * HandwavyWeights.InspireScoreSizeMult[MyBot.GAMETYPE_SIZE] * HandwavyWeights.InspireScorePlayersMult[MyBot.GAMETYPE_PLAYERS];

        AggressionWeight = HandwavyWeights.AggressiveScoreV2[MyBot.GAMETYPE] * collisionKnob;

        if (twoplayers) {
            if (Map.staticMyShipCount > Map.staticEnemyShipCount2 + 2) {
                AggressionWeight *= HandwavyWeights.MultiplierAggression2sIfAheadShipCountV2;
            }
        }
        if (turnsLeft < HandwavyWeights.EndGame)
        {
            if(twoplayers){
                AggressionWeight *= HandwavyWeights.MultiplierAggression2sEndGame;
            }else{
                AggressionWeight *= HandwavyWeights.MultiplierAggression4sEndGame;
            }
        }

    }




    //static boolean[][] aleadygoingfor = new boolean[64][64];
    static boolean[] aleadygoingfor = new boolean[64*64];
    static int[] shipTurnIns;
//    static boolean[] hasReachedGoal;
    static int[] shipTurnInsHalite;


    static float shipWorth;
    static float playerMoneyMultiplier;
    static float playerMoneyDividedBy1000;
    static float boostForScore;
    static float shipHaliteMultiplier;
    static int minHaliteForDropoffReturn;
    static float adjustedForAverageHalite = 1f;
    static int[] state = new int[5000];
    static final int STATE_NORMAL = 0;
    static final int STATE_RETURN = 1;
    public static float moveWorth;
    public static float[] evalUsableHalite = new float[4001];
    public static float[] turnImportance;
    public static float[] turnImportanceFull;

    public static void PrepNewEvaluator(){

        for(CheapShip s : Map.staticMyShips){

            if( s.halite > HandwavyWeights.LimitHalite4 || s.halite - s.GetTile().turnsFromDropoff * HandwavyWeights.HaliteTurnsDropoff > HandwavyWeights.LimitHalite5 || s.halite > HandwavyWeights.LimitHalite6 && s.GetTile().turnsFromDropoff < HandwavyWeights.HaliteMinDistDropoff){
                state[s.id] = STATE_RETURN;
            }else{
                state[s.id] = STATE_NORMAL;
            }

        }

    }


    public void FinalEvaluation(){
        //First, just a neat, no-nonsense, no-randomness evaluation.

        //finalMap = SimulatePlanFromStartTillEnd(SEARCH_DEPTH);

        if(!hasDoneFinalEvaluation) {


            ClearGunk();
            evaluatingTurn = SEARCH_DEPTH - 1;
            Map t1Map = CloneAndSim(0, 1, true, Map.currentMap, true,-1);

            SetFinalMap(CloneAndSim(1, SEARCH_DEPTH, true, t1Map, true,-1));
            if (t1Map.markedIllegal > 0) {
//                illegalMoves++;
                markedIlllegal = true;
            } else if (finalMap.markedIllegal > 3) {
//                illegalMoves++;
                markedIlllegal = true;
            }


            Evaluate(0, false);


            finalScore = nonfinalScore;

            //For the best final choice of a path through map-time, it helps to reward actual success a bit more.
            //We don't really need all the mechanics which helped smooth out the problems with greediness (instant gratification wins!)
//        Log.log("5: " + finalScore   + "  "  + finalMap.haliteSum() +  "   "  + finalMap.ships.size(), Log.LogType.PLANS);
//        for(CheapShip s : finalMap.ships){
//            Log.log(s.toString(), Log.LogType.PLANS);
//        }

            if(!USE_NEW_EVALUATORS) {
                int totalFinalShipHalite = 0;
                int totalHaliteWereStandingOn = 0;
                for (CheapShip s : finalMap.myShips) {
                    if (s != null) {
                        totalFinalShipHalite += s.halite;

                        totalHaliteWereStandingOn += Math.min(finalMap.GetHaliteAt(s), Constants.MAX_HALITE - s.halite);
                    }
                }
                finalScore += (totalFinalShipHalite + finalMap.playerMoney) * HandwavyWeights.FinalShipHaliteV3;
                finalScore += finalMap.playerMoney * HandwavyWeights.FinalPlayerHaliteV3;

                finalScore += totalHaliteWereStandingOn * HandwavyWeights.FinalStandOnHaliteV2;

                //Relatively minor aspects of the game not worth the performance cost of during actual evaluation, but which can be given some value here


                //Clean the surroundings of our myDropoffs, so we stop wasting halite while going back/forth

                if (!Test.IN_TEST_PROGRESS) {
                    for (DropPoint d : Map.myDropoffs) {
                        for (Tile t : Map.tiles[d.x][d.y].tilesInWalkDistance[3]) {
                            if (finalMap.GetHaliteAt(t) >= 10) {
                                finalScore -= HandwavyWeights.FinalHaliteNearDropExceeds10V3;
                            }
                        }
                        for (int x = d.x - 7; x <= d.x + 7; x++) {
                            if (finalMap.GetHaliteAt(finalMap.GetTile(x, d.y)) >= 10) {
                                finalScore -= HandwavyWeights.FinalHaliteDropCrossExceeds10V2;
                            }
                        }
                        for (int y = d.y - 7; y <= d.y + 7; y++) {
                            if (finalMap.GetHaliteAt(finalMap.GetTile(d.x, y)) >= 10) {
                                finalScore -= HandwavyWeights.FinalHaliteDropCrossExceeds10V2;
                            }
                        }
                    }
                }

                if (HandwavyWeights.ActivateFinalInspireAnalysis == 1  || (HandwavyWeights.ActivateFinalInspireAnalysis == 2 && MyBot.playerCount == 2)) {
                    float dealWithEnemyStuff = 0;
                    for (CheapShip s : t1Map.enemyShipsRelevant) {
                        if (s != null) {
                            Competitor owner = MyBot.players[Map.OwnerOfShip[s.id]];

                            if (MyBot.playerCount == 2) {
                                dealWithEnemyStuff -= owner.scaryFactor * HandwavyWeights.EnemyShipTimesScary2p;
                                dealWithEnemyStuff -= owner.scaryFactor * s.halite * HandwavyWeights.EnemyShipTimesScaryHal2p;

                                //TODO: might be worth just checking with our own ships instead of enemy ships too. Might be cheaper / better
                                int inspirecount = 0;
                                for (Tile t : t1Map.GetTile(s).tilesInWalkDistance[4]) {
                                    if (t1Map.shipMap[t.tileIndex] != null && Map.OwnerOfShip[t1Map.shipMap[t.tileIndex].id] != owner.id && ++inspirecount >= 2) {
                                        dealWithEnemyStuff -= owner.scaryFactor * HandwavyWeights.InspiredEnemyShipTimesScary[MyBot.GAMETYPE_PLAYERS];
                                        dealWithEnemyStuff -= owner.scaryFactor * s.usableHaliteUnderMe(t1Map) * HandwavyWeights.InspiredEnemyShipTimesScaryHal[MyBot.GAMETYPE_PLAYERS];
                                        dealWithEnemyStuff -= HandwavyWeights.InspiredEnemyShip[MyBot.GAMETYPE_PLAYERS];
                                        dealWithEnemyStuff -= s.usableHaliteUnderMe(t1Map) * HandwavyWeights.InspiredEnemyShipHal[MyBot.GAMETYPE_PLAYERS];
                                        break;
                                    }
                                }
                            } else {
                                dealWithEnemyStuff -= owner.scaryFactor * HandwavyWeights.EnemyShipTimesScary4p;
                                dealWithEnemyStuff -= owner.scaryFactor * s.halite * HandwavyWeights.EnemyShipTimesScaryHal4p;
                            }


                        }
                    }
                    finalScore += dealWithEnemyStuff * HandwavyWeights.metaNewInspireStuff;

                }
            }

//            finalPlusSuggestions = finalScore + suggestionScore;
            t1Map.CleanUp();

            hasDoneFinalEvaluation = true;
        }
    }

    public void Evaluate(float randomness, boolean rewardUniqueness) {
        //This function contains a lot of highly packed evaluation information for performance reasons.
        //Most of what's added to the evaluator is precomputed somewhere else. The biggest contributor to the score
        //is actually everything inside of MoveEvaluate(), which is added in here through movesScore near the end
        //preCalcEvaluateStuff(), EvaluateTile() and MyBot.PreCalc also contain significant amounts

        if(USE_NEW_EVALUATORS){
            EvaluateExperimental();
            return;
        }
        hasDoneFinalEvaluation = false;

        Stopwatch.Start(10);

        //Idea for later: make use of a distance map from dropoff points. Both a turn distance, and a cost distance. This can be used for cheap pathfinding
//        Stopwatch.Start(1001);

        float score = MyBot.rand.nextFloat() * randomness * HandwavyWeights.EVALRANDOMNESS;
        float gatherScore = 0, wastePreventionScore = 0, turnInHaliteScore = 0, miscScore = 0, lureScore = 0, aggressiveScore = 0, shipHaliteTotalScore = 0, moveScore = 0, totalFinalShipHalite = 0;

        turnInHaliteScore += finalMap.playerMoney * playerMoneyMultiplier;
        turnInHaliteScore += playerMoneyPerTurn[0] * HandwavyWeights.PlayerHaliteT1V2;
        turnInHaliteScore += playerMoneyPerTurn[1] * HandwavyWeights.PlayerHaliteT2V2;
        turnInHaliteScore += playerMoneyPerTurn[2] * HandwavyWeights.PlayerHaliteT3V2;
        turnInHaliteScore += playerMoneyPerTurn[3] * HandwavyWeights.PlayerHaliteT4V2;
        //Extra boost to getting us just a bit above some thousandfold, makes ships value going home a bit more if they can
        //Help contribute to getting enough cash to buy another ship
        if (finalMap.playerMoney / 1000 > playerMoneyDividedBy1000) {
            turnInHaliteScore += boostForScore;
        }

//        Stopwatch.StopAccumulate(1001);
//        Stopwatch.Start(1002);



        for (CheapShip s : finalMap.myShips) {
            if (s != null) {
                totalFinalShipHalite += s.halite;
//                CheapShip originalShip = Map.staticShipsById[s.id];
                //TODO: split it up into a final turn dropdist and a first turn dropdist
                //Give our ships value for existing. Prevents suicidal behavior, and determines whether we should trade ships with enemies
                wastePreventionScore += shipWorth;
                miscScore +=  Map.tilesById[s.tileIndex].standOnTileScore;
                // if (HandwavyWeights.ActivateGatherMechanic == 1) {
                //A cheap 'simulation' of how much we'd gather in continuing rounds.
                int gatherValue = Math.min(MyBot.gatherValues[finalMap.GetHaliteAt(s)], 1000 - s.halite);  //Let's not count gathering that exceeds what we can carry
                gatherScore += Math.max(0,  gatherValue     - Math.max(0, Map.staticShipsById[s.id].halite - s.halite)) * HandwavyWeights.GatherTotalV3; //Also filter out gathering we need to burn halite to get to
                // }
                //IDEA: Promote chasing/killing enemy ships if theyre closer to our myDropoffs than enemy myDropoffs. Especially if low halite. Not recommend on 4s tho
            }
        }

        score += (totalFinalShipHalite + finalMap.playerMoney) * shipHaliteMultiplier;
//        Stopwatch.StopAccumulate(1002);


//        Stopwatch.Start(1004);
        if(HandwavyWeights.ActivateCollisionMechanic == 1) {

            //Check for collisions
            for (int moveindex = 0; moveindex < Map.staticMyShipCount; moveindex++) {
                Move m = movesPerTurn[0][moveindex];
                if (m != null && !m.IgnoreInEval) { //TODO: determine whether it might be better to not exclude swapped moves here
                    if (m.to.movingHere) {
                        if (turnsLeft > 15 || m.to.turnsFromDropoff != 0) {
                            wastePreventionScore -= HandwavyWeights.SELF_COLLISION;
                        }
                    } else {
                        m.to.movingHere = true;
                    }
                }
            }
            for (int moveindex = 0; moveindex < Map.staticMyShipCount; moveindex++) {
                Move m = movesPerTurn[0][moveindex];
                if (m != null) {
                    m.to.movingHere = false;
                }
            }
        }
//        Stopwatch.StopAccumulate(1004);
//        Stopwatch.Start(1005);

        for (CheapShip s : finalMap.enemyShipsRelevant) {
            if (s != null) {
                if(s.halite > 100){
                    //These weights contain quite a bit of info, isn't pre-caching beautiful? Saves a surprising amount of computing power
                    aggressiveScore -= Map.tilesById[s.tileIndex].weightForEShipOnTileWith100Halite  + MyBot.EShipHaliteWeights[s.halite];
                }else{
                    aggressiveScore -= Map.tilesById[s.tileIndex].weightForEShipOnTileWithout100Halite + MyBot.EShipHaliteWeights[s.halite];
                }
                //TODO: scary factor of owner?
            }
        }


//        Stopwatch.StopAccumulate(1005);
//        Stopwatch.Start(1006);

        //TODO: optimize these, add missing size/player mults

        score += gatherScore * GatherWeight;
        score += miscScore * MiscWeight;
        score += wastePreventionScore * WasteWeight;
        score += turnInHaliteScore * TurnInWeight;
        score += lureScore * LureWeight;
        score += movesScore * MovesWeight;
        score += aggressiveScore * AggressionWeight;

//        if (score == Float.NaN) {
//            for (Log.LogType l : Log.LogType.values()) {
//                Log.log("NAN SCORE", l);
//            }
//        }

        this.nonfinalScore = score;
        this.nonFinalPlusSuggestions = score + suggestionScore;
//        Stopwatch.StopAccumulate(1006);

        Stopwatch.StopAccumulate(10);


        if(MyBot.DO_GAME_OUTPUT ){
            if(lastMove != null && lastMove.ship.id == 0) {
                maxEvalOnTile[evaluatingTurn][lastMove.to.x][lastMove.to.y] = Math.max(maxEvalOnTile[evaluatingTurn][lastMove.to.x][lastMove.to.y], nonFinalPlusSuggestions);
                evalCountTile[evaluatingTurn][lastMove.to.x][lastMove.to.y]++;
            }
        }
    }




    //One of the core methods of this bot. It evaluates moves in a plan
    //These evaluations are saved and reused, and almost everything that happens here is independent of the movement of other ships
    //The only exception is everything dealing with halite on a tile, which can change based on other ships
    //A lot of the code in this method uses data that is already heavily packed for performance reasons. Many of the things weighted
    //here represent the final conclusion of other algorithms, or which have been precalculated elsewhere (like the value of tiles).
    public void MoveEvaluate(int turn, Move m, int moveindex, boolean rewardUniqueness){
//        Stopwatch.Start(1008);
//        if(USE_NEW_EVALUATORS){
//             MoveEvaluateExperimental(turn,m,moveindex);
//             return;
//        }

        float score = 0, gatherScore = 0, wastePreventionScore = 0, turnInHaliteScore = 0,miscScore = 0,lureScore = 0,aggressiveScore = 0, inspireScore = 0;
        Tile from = m.from, to = m.to;
        int toX = to.x, toY = to.y, shipId = m.ship.id,shipHalite = m.ship.halite;
        int roomLeft = 1000 - shipHalite;
        float tImportance;
//        if(MyBot.EXPERIMENTAL_MODE) {
//            tImportance =1f;//turnImportance[turn];
//        }else{
            tImportance = turnImportance[turn];
//        }
        float iffyIfNotT0 = turn ==0 ? HandwavyWeights.ItIsT0BeNotAfraid[MyBot.GAMETYPE_PLAYERS] :HandwavyWeights.IffyIfNotT0V3[MyBot.GAMETYPE_PLAYERS];

        float halImportance = MyBot.shipHaliteImportance[shipHalite];
        float halImportanceTimesTurnImportance = tImportance * halImportance;


        CheapShip originalShip = Map.staticShipsById[shipId];

        int haliteFrom, haliteTo;
        if(from.lastKnownStartHalite >= 0 && turn > 0){
            haliteFrom = from.lastKnownStartHalite;
        }else{
            haliteFrom = from.haliteStartTurn; //TODO: try and fix so that we don't end up here / so that we can get an accurate value here
        }
        if(to.lastKnownStartHalite >= 0  && turn > 0){
            haliteTo = to.lastKnownStartHalite;
        }else{
            haliteTo = to.haliteStartTurn;
        }

        int usableHaliteUnderNextSpot = (int)(Math.min(roomLeft, haliteTo/4.0)*4.0);
        int expectGatherStandstill = Math.min(roomLeft, (int) (MyBot.standCollect[haliteFrom] *  to.inspireMultiplier[turn]));



        if(!to.didEvalCalsThisTurn){
            EvaluateTile(to);
        }


        wastePreventionScore += moveWorth;

        if (futurePathingSuggestions[turn][moveindex] != null && m.to.equals(futurePathingSuggestions[turn][moveindex].to)) { //See FuturePathing
            lureScore += to.followPathval * tImportance;
        }
        lureScore += longDistLure[toX][toY] * MyBot.longLureImportance[shipHalite] * halImportanceTimesTurnImportance; //see SideAlgorithms.GetLongDistLureMap()
        lureScore += medDistLure[toX][toY] * MyBot.medLureImportance[shipHalite] * halImportanceTimesTurnImportance;//see SideAlgorithms.GetMediumDistLureMap()
//        lureScore += lureMap[toX][toY] * MyBot.lureImportance[shipHalite] * halImportanceTimesTurnImportance; //see SideAlgorithms.GetShortDistLureHaliteMap()

//        Stopwatch.StopAccumulate(1008);
//        Stopwatch.Start(1009);
        float eOdds = eLocOdds[turn][toX][toY]; //see EnemyPrediction.GetEnemyLocOdds()


        int expectedShipHalite, expectGatherOnNextTile;
        if (toX == from.x && toY == from.y) {
            int expectedRemaining = haliteFrom - MyBot.standCollect[haliteFrom];

            expectGatherOnNextTile = Math.min(roomLeft - expectGatherStandstill, (int) (MyBot.standCollect[expectedRemaining] * to.inspireMultiplier[turn + 1])); //What we'll gather next turn, not this turn

            if (shipHalite >= 999) {
                //Counteract the money burnt stat, so full ships arent afraid to leave a big tile
                wastePreventionScore -= MyBot.moveCosts[haliteFrom] * HandwavyWeights.MoneyBurntV2;
                miscScore -= 500;
            } else {
                if (mapEmpty) {
                    gatherScore += Math.min(200, HandwavyWeights.MapEmptyBoostPerHaliteStandstillV2 * usableHaliteUnderNextSpot) * tImportance;
                } else {
                    score += MyBot.evalStandstill[shipHalite][Math.min(2999, haliteTo)] * tImportance; //See MyBot for logic, includes punishing for standing still on no/low halite
                }

                if (expectGatherStandstill > EffectiveHps * HandwavyWeights.AboveHpsFactor) {
                    //If we can collect more from this tile then we have been collecting recently, give a bonus
                    //Mostly for late game, where ships have a tendency to move around a bit too much
                    gatherScore += HandwavyWeights.AboveHpsBonusV2 * halImportanceTimesTurnImportance;
                }


                gatherScore += evalUsableHalite[usableHaliteUnderNextSpot] * halImportanceTimesTurnImportance; //Includes several bits, a flat score per halite and some stuff about the tile being better than average

                if (from.equals(ruleOfXTiles[shipId])) {
                    //Found during bruteforcing that on flat halite distribution terrain, the most efficient way to gather
                    //is to do:  gather->gather->gather->move->gather->gather->gather->move This tries to help us hit that minimum of 3 turns
                    //Whenever our environment resembles flat (we started on a decent tile, and our neighbours have similar halite amounts)
                    if (MyBot.turn + turn < ruleOfThreeUntil[shipId]) {
                        miscScore += HandwavyWeights.RuleOfThreeWeightV2;
                    } else if (MyBot.turn + turn < ruleOfTwoUntil[shipId]) {
                        //The rule of two is used when our tile is not the highest, but close to it. It's generally the strongest pattern then
                        miscScore += HandwavyWeights.RuleOfTwoWeightV2;
                    }
                }

            }
            if (shipHalite < 950) {
                if (inspireOdds[turn][toX][toY] > 0f) {

                    int inspireGains = (expectGatherStandstill - MyBot.standCollect[haliteFrom]) +   (int)(HandwavyWeights.InspireNextTurnWorth * Math.max(0,expectGatherOnNextTile - MyBot.standCollect[expectedRemaining]));

                    if(inspireGains > 0) { //Some extra bonuses to inspire seperate from the fact it's expected to give us more halite
                        inspireScore += HandwavyWeights.InspireMultV2 * HandwavyWeights.InspireHaliteMultV4[MyBot.GAMETYPE] * inspireGains * halImportanceTimesTurnImportance;
                        inspireScore += HandwavyWeights.InspireMultV2 * HandwavyWeights.InspireHaliteMultV4NoTImportance[MyBot.GAMETYPE] * inspireGains;
                        if (shipHalite < HandwavyWeights.LimitInspireGainsV3 && inspireGains > 20) {
                            inspireScore += HandwavyWeights.InspireMultV2 * (HandwavyWeights.InspireFlatV4NoTImportance[MyBot.GAMETYPE] + HandwavyWeights.InspireFlatV4[MyBot.GAMETYPE] * halImportanceTimesTurnImportance * adjustedForAverageHalite);
                        }
                    }
                }
//                else {
//                    if (inspireOdds[turn + 1][toX][toY] > 0.95f) {
//                        inspireScore += expectedRemaining * HandwavyWeights.InspireGuaranteedNextHalite + HandwavyWeights.InspireGuaranteedNextFlat * adjustedForAverageHalite;
//                    }
//                }
            }
            expectedShipHalite = Math.min(1000, shipHalite + expectGatherStandstill);
            if (!to.hasFriendlyDropoff && eOdds > 0) {

                float eLocTurnMultiplier;
                if(turn == 0){
                    eOdds *= HandwavyWeights.AvoidELocT0Mult;
                }else{
                    eOdds *= HandwavyWeights.AvoidELocTOtherMult;
                }
                if (to.turnsFromDropoff <= 1) {
                    eOdds *= 0.5f;
                }

                wastePreventionScore -= eOdds * HandwavyWeights.AvoidELocStand[MyBot.GAMETYPE_PLAYERS] * iffyIfNotT0  * collisionKnob;
                wastePreventionScore -= eOdds * HandwavyWeights.AvoidELocHalStand[MyBot.GAMETYPE_PLAYERS] * shipHalite * iffyIfNotT0  * collisionKnob;
                wastePreventionScore -= eOdds * to.controlDanger * HandwavyWeights.AvoidELocHalControlStand[MyBot.GAMETYPE_PLAYERS] * iffyIfNotT0  * collisionKnob;
                aggressiveScore -= eOdds * HandwavyWeights.AvoidEOddsStand[MyBot.GAMETYPE_PLAYERS];
                if (AverageHalite < 50) {
                    wastePreventionScore -= eOdds * HandwavyWeights.AvoidELocHalStandEndGame[MyBot.GAMETYPE_PLAYERS] * shipHalite * iffyIfNotT0  * collisionKnob;
                }

            }
//            if (eOdds > HandwavyWeights.ELocHigh[MyBot.GAMETYPE_PLAYERS]) {
//                wastePreventionScore -= HandwavyWeights.AvoidELocIfHigh[MyBot.GAMETYPE_PLAYERS] * iffyIfNotT0;
//            }

        } else {
            int expectedBurn =  MyBot.moveCostsSafe[haliteFrom + 1000];

            expectGatherOnNextTile = Math.min(roomLeft + expectedBurn, (int) (MyBot.standCollect[haliteTo] * inspireMultiplier[turn + 1][toX][toY])); //What we'll gather next turn, not this turn
            expectedShipHalite = Math.max(0, shipHalite - expectedBurn);


            wastePreventionScore -= expectedBurn * HandwavyWeights.MoneyBurntV2;

            if (turn > 0 && from.equals(movesPerTurn[turn - 1][moveindex])) {
                //Going back and forth is almost never desirable, maybe to avoid collisions sometimes ?
                //Mainly to prevent a massive proportion of explored paths from doing this
                //TODO: maybe just ban this behavior outright
                // wastePreventionScore -= haliteFrom * (HandwavyWeights.PunishBackAndForth + HandwavyWeights.PunishBackAndForthTImportance * tImportance);
                wastePreventionScore -= (HandwavyWeights.PunishBackAndForth + HandwavyWeights.PunishBackAndForthTImportance * tImportance);
            }

            if (shipHalite < 950) {
                gatherScore += usableHaliteUnderNextSpot * (HandwavyWeights.HaliteMultiplierMoveTo * halImportanceTimesTurnImportance + HandwavyWeights.HaliteMultiplierMoveToNoImp * tImportance);
                wastePreventionScore -= haliteFrom * (HandwavyWeights.HaliteMovepunisherV2 * halImportanceTimesTurnImportance + HandwavyWeights.HaliteMovepunisherNoImpV2 * tImportance);
                if (haliteFrom > 20) {
                    wastePreventionScore -= HandwavyWeights.FlatMovePunisherV2 * halImportanceTimesTurnImportance * adjustedForAverageHalite;
                    wastePreventionScore -= HandwavyWeights.FlatMovePunisherNoImp * tImportance * adjustedForAverageHalite;
                }



                int inspireGains = (int)(HandwavyWeights.InspireNextTurnWorth * Math.max(0,expectGatherOnNextTile - MyBot.standCollect[haliteTo]));

                if(inspireGains > 0) { //Some extra bonuses to inspire seperate from the fact it's expected to give us more halite
                    inspireScore += HandwavyWeights.InspireMultV2 * HandwavyWeights.InspireHaliteMultV4[MyBot.GAMETYPE] * inspireGains * halImportanceTimesTurnImportance;
                    inspireScore += HandwavyWeights.InspireMultV2 * HandwavyWeights.InspireHaliteMultV4NoTImportance[MyBot.GAMETYPE] * inspireGains;
                    if (shipHalite < HandwavyWeights.LimitInspireGainsV3 && inspireGains > 20) {
                        inspireScore += HandwavyWeights.InspireMultV2 * (HandwavyWeights.InspireFlatV4NoTImportance[MyBot.GAMETYPE] + HandwavyWeights.InspireFlatV4[MyBot.GAMETYPE] * halImportanceTimesTurnImportance * adjustedForAverageHalite);
                    }
                }

//                float inspireChanceNext = to.inspireOdds[turn + 1];
//                inspireScore += inspireChanceNext * HandwavyWeights.InspireMultV2 * HandwavyWeights.InspireHaliteMultV4[MyBot.GAMETYPE] * usableHaliteUnderNextSpot * tImportance;
//                inspireScore += inspireChanceNext * HandwavyWeights.InspireMultV2 * HandwavyWeights.InspireHaliteMultV4NoTImportance[MyBot.GAMETYPE] * usableHaliteUnderNextSpot;
//                if (shipHalite < HandwavyWeights.LimitInspireGainsV3 && usableHaliteUnderNextSpot > 20) {
//                    inspireScore += inspireChanceNext * HandwavyWeights.InspireFlatMult * HandwavyWeights.InspireFlatV4[MyBot.GAMETYPE] * tImportance * adjustedForAverageHalite;
//                    inspireScore += inspireChanceNext * HandwavyWeights.InspireFlatMult * HandwavyWeights.InspireFlatV4NoTImportance[MyBot.GAMETYPE] * adjustedForAverageHalite;
//                }
//                inspireScore -= to.inspireOdds[turn] * tImportance * (HandwavyWeights.InspireFlatMovePunish[MyBot.GAMETYPE_PLAYERS] * adjustedForAverageHalite + HandwavyWeights.InspireMultMovePunish[MyBot.GAMETYPE_PLAYERS] * usableHaliteUnderNextSpot);
//
//                if (inspireOdds[turn + 1][toX][toY] > 0.95f) {
//                    inspireScore += haliteTo * HandwavyWeights.InspireGuaranteedNextHalite + HandwavyWeights.InspireGuaranteedNextFlat * adjustedForAverageHalite;
//                }
            }

            if (shipHalite > 800 && haliteTo > 900 && to.turnsFromDropoff > 1) {
                //we'll just burn too much to be worth it, also risking getting blown up
                wastePreventionScore -= 20000;
            }

            //TODO: should be able to be optimized further
            if (!to.hasFriendlyDropoff) {
                if(eOdds > 0) {
                    if (to.turnsFromDropoff <= 1) {
                        eOdds *= 0.5f;
                    }
                    if (turn == 0) {
                        eOdds *= HandwavyWeights.AvoidELocT0Mult;
                    } else {
                        eOdds *= HandwavyWeights.AvoidELocTOtherMult;
                    }

                    wastePreventionScore -= eOdds * HandwavyWeights.AvoidELocMove[MyBot.GAMETYPE_PLAYERS] * iffyIfNotT0  * collisionKnob;
                    wastePreventionScore -= eOdds * HandwavyWeights.AvoidELocHalMove[MyBot.GAMETYPE_PLAYERS] * shipHalite * iffyIfNotT0  * collisionKnob;
                    wastePreventionScore -= eOdds * to.controlDanger * HandwavyWeights.AvoidELocHalControlMove[MyBot.GAMETYPE_PLAYERS] * iffyIfNotT0  * collisionKnob;
                    if (AverageHalite < 50) {
                        wastePreventionScore -= eOdds * HandwavyWeights.AvoidELocHalMoveEndGame[MyBot.GAMETYPE_PLAYERS] * shipHalite * iffyIfNotT0  * collisionKnob;
                    }

                    aggressiveScore -= eOdds * HandwavyWeights.AvoidEOddsMove[MyBot.GAMETYPE_PLAYERS];
                }
            }
            else{
                if(shipHalite > HandwavyWeights.MinHaliteTurnIn) {
                    turnInHaliteScore += shipHalite * HandwavyWeights.TurnInHaliteOnMove * tImportance;
                }
            }


//            if (eOdds > HandwavyWeights.ELocHigh[MyBot.GAMETYPE_PLAYERS]) {
//                wastePreventionScore -= HandwavyWeights.AvoidELocIfHigh[MyBot.GAMETYPE_PLAYERS] * iffyIfNotT0;
//            }
        }


//        Stopwatch.StopAccumulate(1009);
//        Stopwatch.Start(1010);
            gatherScore += HandwavyWeights.EnemyTerrainHaliteV2 * usableHaliteUnderNextSpot * Math.max(0, to.enemyTerrain);
            gatherScore += HandwavyWeights.ShipHaliteOnMoveV2 * expectedShipHalite; //yes, these two should be here, just named badly
            gatherScore += HandwavyWeights.ShipHaliteIfStandstillNextV2 * Math.min(1000, expectedShipHalite + expectGatherOnNextTile);



        miscScore += controlEdgeMap[toX][toY] *  usableHaliteUnderNextSpot * HandwavyWeights.ControlEdgeTileHalite * halImportanceTimesTurnImportance;
        gatherScore += MyBot.evalHalite[usableHaliteUnderNextSpot] * halImportanceTimesTurnImportance; //contains log curve etc.

        miscScore -= to.antiInspire * HandwavyWeights.AntiInspireMoveWeightV2[MyBot.GAMETYPE_PLAYERS];

            if (goals[shipId] != null) {
//                        if(hasReachedGoal[moveindex]){
//                            dist = 0;
//                            lureScore += HandwavyWeights.HasReachedGoalFlat;
//                            lureScore += (SEARCH_DEPTH - turn) * HandwavyWeights.HasReachedGoalTurn;
//                        }else {
//                            dist = goals[m.ship.id].DistManhattan(m.to.x, m.to.y);
//                            if(dist == 0){
//                                hasReachedGoal[moveindex] = true;
//                            }
//                        }

                if (goals[shipId].turnsFromDropoff == 0) {
                    lureScore += (7 - goals[shipId].ComplexDist(to)) * HandwavyWeights.DropoffGoalWeightV2 * adjustedForAverageHalite * HandwavyWeights.DropoffGoalWeightV2SizeMult[MyBot.GAMETYPE_SIZE];
                } else if (goals[shipId].goalIsAboutDenying && from.DistManhattan(goals[shipId]) > 3) {

                    lureScore += (7 - goals[shipId].ComplexDist(to)) * HandwavyWeights.GoalAboutDenyingWeight  * HandwavyWeights.GoalAboutDenyingWeightPlayersMult[MyBot.GAMETYPE_PLAYERS];
                } else {

                    float modifierChange = HandwavyWeights.GoalWeightHaliteMod * shipHalite + HandwavyWeights.GoalWeightOnHaliteMod * from.haliteStartTurn + HandwavyWeights.GoalWeightDistDropoff * from.turnsFromDropoff;
                    lureScore += (7 - goals[shipId].ComplexDist(to)) * (HandwavyWeights.GoalWeightV6 * HandwavyWeights.GoalWeightV6SizeMult[MyBot.GAMETYPE_SIZE] * HandwavyWeights.GoalWeightV6PlayersMult[MyBot.GAMETYPE_PLAYERS] + Math.max(0f, Goals.desire[shipId] * HandwavyWeights.GoalWeightDesireV2)) * Math.max(HandwavyWeights.GoalWeightMinModifier, 1f - modifierChange) * tImportance * adjustedForAverageHalite;

                }
            }


//        if(goals[shipId] == null || to.DistManhattan(goals[shipId]) > HandwavyWeights.WeirdAlgoDistFromGoalV2) {
            if (to.equals(WeirdAlgo.recommendations[shipId][turn])) {
                lureScore += HandwavyWeights.WeirdAlgoWeight * HandwavyWeights.WeirdAlgoWeightMultiplier[MyBot.GAMETYPE];
            }
//        }

        if (simulMovePlan[turn][moveindex] != null && m.to.equals(simulMovePlan[turn][moveindex])) {
            lureScore += HandwavyWeights.SimulWeight * HandwavyWeights.SimulWeightMultiplier[MyBot.GAMETYPE];
        }


        //Avoid the cross around dropoffs if not carrying halite a bit, can cause roadblocks
        if (shipHalite < HandwavyWeights.CrossAvoidMinHaliteV2) {
            miscScore -= to.crossScore  * adjustedForAverageHalite;
        }

        if (turnsFromEnemyDropoff[toX][toY] == 0 && (shipHalite > 0 || eOdds > 0 || (Map.enemyDropoffMap[toX][toY] != null && Map.enemyDropoffMap[toX][toY].isYard))) {
            miscScore -= 1000000;
        }

        if(shipHalite < 950) {
            lureScore -= distToMeaningfulHalite[toX][toY] * tImportance * MyBot.meaningFulHaliteMultiplier[shipHalite];
            //Turned off for now. It was bad and overly dominant.
//            inspireScore += cumulativeInspireOdds[turn][toX][toY] * (HandwavyWeights.cumulativeInspireFlat  * adjustedForAverageHalite + HandwavyWeights.cumulativeInspireHalite * haliteTo);

        }

        if(annoyGoals[moveindex] != null && turn == 0){
            boolean dangerzone = false;
            if (turnsFromDangerousEnemyDropoff[toX][toY] == 0) {
                for (Tile t : to.tilesInWalkDistance[2]) {
                    if (Map.currentMap.IsEnemyShipAt(t)) {
                        dangerzone = true;
                        break;
                    }
                }
            }
            if (dangerzone) {
                aggressiveScore -= 20000;
            } else {
                int mydist = annoyGoals[moveindex].DistManhattan(from);
                int dist = annoyGoals[moveindex].DistManhattan(to);

                aggressiveScore += ((mydist - dist) + 1) * HandwavyWeights.AnnoyWeight;
            }
        }
        else if (HandwavyWeights.ActivateEndGameDropoffBlocks == 0 || (shipHalite > HandwavyWeights.RunMinimumHaliteV2 ||   turnsLeft - turnsFromDangerousEnemyDropoff[toX][toY] < 0)) {
            //Run to home when time is up (the time is up aspect is done in evaluatetile)
            turnInHaliteScore += to.runToMyDropoffScore * tImportance;
        } else{
            //Block enemy dropoffs if we don't have enough halite
            aggressiveScore += to.runToEnemyDropoffScore * tImportance;
        }


//        Stopwatch.StopAccumulate(1010);
//        Stopwatch.Start(1011);

        if (shipHalite > minHaliteForDropoffReturn) {
            turnInHaliteScore +=  to.dropDistFactor * MyBot.shipReturnHaliteMultiplier[shipHalite]; //Lots of calcs behind this one
        }


        if (shipId == dropOffRunner) {
            miscScore += 10000 - dropOffSpot.ComplexDist(to) * HandwavyWeights.DropoffRunDesire;
        }


//        if(rewardUniqueness){  //reward moves we haven't tried before
//            int probableAttempts = Math.min(Math.min(likelyVisited1[m.GetLikelyIndex1(turn)],likelyVisited2[m.GetLikelyIndex2(turn)]),likelyVisited3[m.GetLikelyIndex3(turn)]);
//            miscScore -= probableAttempts * HandwavyWeights.ProbableAttemptsPunisher;
//            if(probableAttempts < 5){
//                miscScore += HandwavyWeights.ProbableAttemptsBoostIfFew;
//            }
//        }

//        Stopwatch.StopAccumulate(1011);
//        Stopwatch.Start(1012);

        //Some stuff that doesn't work at all on other turns / would be completely awful
        if(turn == 0){

            wastePreventionScore -= (to.murderSpot * (HandwavyWeights.MurderAvoidFlat[MyBot.GAMETYPE_PLAYERS] + HandwavyWeights.MurderAvoidHalite[MyBot.GAMETYPE_PLAYERS] * shipHalite)) * collisionKnob;
            wastePreventionScore -= to.likelyMurderScore * HandwavyWeights.MurderAvoidLikelyhood[MyBot.GAMETYPE_PLAYERS] * collisionKnob;

            if(shipHalite < HandwavyWeights.EntrapMaxMyHalite && entrapmentMap[toX][toY]){
                aggressiveScore += HandwavyWeights.EntrapmentWeight;
            }
            if (to.hasFriendlyDropoff) {
                if (turnsLeft < HandwavyWeights.RunLeftTimerV3) {
                    if (from.hasFriendlyDropoff) {
                        turnInHaliteScore += 100000;
                    } else {
                        turnInHaliteScore += 2000000;
                    }
                } else {
                    if (shipHalite > HandwavyWeights.MinHaliteTurnIn){
                        turnInHaliteScore += HandwavyWeights.StepOnDropoffFlatV2 + shipHalite * HandwavyWeights.StepOnDropoffV2;
                        if (MyBot.DoIWantToBuildShip && HandwavyWeights.ActivateAvoidDropoffWhenBuilding == 1 && Map.myDropoffMap[toX][toY].isYard) {
                            int crowdedness = 0;
                            for (Tile t : to.neighboursAndSelf) {
                                if (Map.currentMap.IsShipAt(t)) {
                                    crowdedness++;
                                }
                            }
                            if (crowdedness < 4) {
                                turnInHaliteScore += HandwavyWeights.StepOnDropoffIfWantToBuild;
                            }
                        }
                    }
                    else if (!MyBot.DoIWantToBuildShip && to.myShipsStartInRange4 <= 1 ) {
                        wastePreventionScore += HandwavyWeights.AvoidDropoffProbablyFine + shipHalite * HandwavyWeights.StepOnDropoffHalProbablyFine;
                    }
                    else if (shipHalite == 0) {
                        wastePreventionScore += HandwavyWeights.AvoidDropoffZero;
                    } else {
                        wastePreventionScore += HandwavyWeights.AvoidDropoffV2;
                    }
                }
            }


            //TODO: make use of the t1 enemy positions from the prediction system, not just the turn 0 positions
            CheapShip shipAlreadyAt = Map.currentMap.GetShipAt(to);

            if(shipAlreadyAt != null && !Map.DoIOwnShip[shipAlreadyAt.id]) {
                if(to.turnsFromDropoff > 1) {
                    if (twoplayers) {
                        aggressiveScore += HandwavyWeights.Step0KillDesire2pV2;
                        aggressiveScore += shipAlreadyAt.halite * HandwavyWeights.Step0KillDesireHalite2pV2;
                        if (turnsLeft < HandwavyWeights.BlowEUpLateTurnV2) {
                            aggressiveScore += shipAlreadyAt.halite * HandwavyWeights.BlowEUpLate2pV2;
                        }
                        wastePreventionScore -= shipHalite * HandwavyWeights.Step0AvoidRunUntoHal2p;
                        aggressiveScore += HandwavyWeights.Step0KillDesire2pControl * to.control;
                    } else {
                        aggressiveScore += HandwavyWeights.Step0KillDesire4pV2;
                        aggressiveScore += shipAlreadyAt.halite * HandwavyWeights.Step0KillDesireHalite4pV2;
                        if (turnsLeft < HandwavyWeights.BlowEUpLateTurnV2) {
                            aggressiveScore += shipAlreadyAt.halite * HandwavyWeights.BlowEUpLate4pV2;
                        }
                        wastePreventionScore -= shipHalite * HandwavyWeights.Step0AvoidRunUntoHal4p;
                        wastePreventionScore -= shipHalite * HandwavyWeights.Step0AvoidCurEnemySpot;

                        if (killTime && to.inControlZone && shipHalite < 100 && shipAlreadyAt.halite > 200) {
                            aggressiveScore += HandwavyWeights.Step0KillTimeFlat + HandwavyWeights.Step0KillTimeHalite * (shipAlreadyAt.halite - shipHalite);

                        }
                    }
                }
                else {
                    if (to.turnsFromDropoff == 0) {
                        score += 100000;
                    }
                }

            }

            if(!m.isStandStill()) {
                CheapShip shipLastTurn = Map.staticShipsMapLastTurn[toX][toY];
                if (shipLastTurn != null && shipLastTurn.id == shipId) {
                    wastePreventionScore -= HandwavyWeights.PenaltyMoveBackToPreviousTileV4 ;//dont want to run back and forth between spots unless theres a really good reason
                }

                CheapShip shipAtPos = Map.currentMap.GetShipAt(m.to);
                if (shipAtPos != null) {
                    if (Map.DoIOwnShip[shipAtPos.id]) {
                        //Detect and devalue swaps, they happen too often
                        Move m2 = movesPerTurn[0][Map.myIndexOfIds[shipAtPos.id]];
                        if (m2 != null) {
                            miscScore -= HandwavyWeights.SwapPunishmentV2  * adjustedForAverageHalite;
                            if (Math.abs(m2.ship.halite - m.ship.halite) < 50) {
                                miscScore -= HandwavyWeights.SwapPunishmentSimilarV2  * adjustedForAverageHalite;
                            }
                        }
                    }
                }
            }
        }
//        Stopwatch.StopAccumulate(1012);
//        Stopwatch.Start(1013);

        score += to.evalScoreDependOnTurnAndHaliteImportance * halImportanceTimesTurnImportance;
        score += to.evalScoreFlat;
        score += gatherScore * GatherWeight;
        score += miscScore * MiscWeight;
        score += wastePreventionScore * WasteWeight;
        score += turnInHaliteScore * TurnInWeight;
        score += lureScore * LureWeight;
        score += inspireScore * InspireWeight;
        score += aggressiveScore * AggressionWeight;
        m.score = score * turnImportanceFull[turn];
//        Stopwatch.StopAccumulate(1013);

    }

    public static void EvaluateTile(Tile t){
        //These calculations are all indepent of the ship involves, so are done here to prevent duplicate calculations
        //They are often turn dependent, so can't be saved forever
        float gatherScore =  GatherWeight;
        float dependentOnHaliteAndTurn = 0;
        dependentOnHaliteAndTurn += HandwavyWeights.HalAvgDist1WeightV2 * t.haliteStartInRange1Avg * gatherScore;
        dependentOnHaliteAndTurn += HandwavyWeights.HalAvgDist2WeightV2 * t.haliteStartInRange2Avg * gatherScore;
        dependentOnHaliteAndTurn += HandwavyWeights.HalAvgDist3WeightV2 * t.haliteStartInRange3Avg * gatherScore;
        dependentOnHaliteAndTurn += HandwavyWeights.HalAvgDist4WeightV2 * t.haliteStartInRange4Avg * gatherScore;
        dependentOnHaliteAndTurn += HandwavyWeights.HalAvgDist5WeightV2 * t.haliteStartInRange5Avg * gatherScore;

        dependentOnHaliteAndTurn += HandwavyWeights.MyShipRange1Weight[MyBot.GAMETYPE_PLAYERS] * t.myShipsStartInRange1Avg * gatherScore  * adjustedForAverageHalite;
        dependentOnHaliteAndTurn += HandwavyWeights.MyShipRange2Weight[MyBot.GAMETYPE_PLAYERS] * t.myShipsStartInRange2Avg * gatherScore  * adjustedForAverageHalite;
        dependentOnHaliteAndTurn += HandwavyWeights.MyShipRange3Weight[MyBot.GAMETYPE_PLAYERS] * t.myShipsStartInRange3Avg * gatherScore  * adjustedForAverageHalite;
        dependentOnHaliteAndTurn += HandwavyWeights.MyShipRange4Weight[MyBot.GAMETYPE_PLAYERS] * t.myShipsStartInRange4Avg * gatherScore  * adjustedForAverageHalite;
        dependentOnHaliteAndTurn += HandwavyWeights.MyShipRange5Weight[MyBot.GAMETYPE_PLAYERS] * t.myShipsStartInRange5Avg * gatherScore  * adjustedForAverageHalite;

        dependentOnHaliteAndTurn += HandwavyWeights.EnemyShipRange1Weight[MyBot.GAMETYPE_PLAYERS] * t.enemyShipsStartInRange1Avg * gatherScore  * adjustedForAverageHalite;
        dependentOnHaliteAndTurn += HandwavyWeights.EnemyShipRange2Weight[MyBot.GAMETYPE_PLAYERS] * t.enemyShipsStartInRange2Avg * gatherScore  * adjustedForAverageHalite;
        dependentOnHaliteAndTurn += HandwavyWeights.EnemyShipRange3Weight[MyBot.GAMETYPE_PLAYERS] * t.enemyShipsStartInRange3Avg * gatherScore  * adjustedForAverageHalite;
        dependentOnHaliteAndTurn += HandwavyWeights.EnemyShipRange4Weight[MyBot.GAMETYPE_PLAYERS] * t.enemyShipsStartInRange4Avg * gatherScore  * adjustedForAverageHalite;
        dependentOnHaliteAndTurn += HandwavyWeights.EnemyShipRange5Weight[MyBot.GAMETYPE_PLAYERS] * t.enemyShipsStartInRange5Avg * gatherScore  * adjustedForAverageHalite;
        dependentOnHaliteAndTurn += controlEdgeMap[t.x][t.y] * HandwavyWeights.ControlEdgeTileFlat * HandwavyWeights.MiscScore  * adjustedForAverageHalite;


        dependentOnHaliteAndTurn += HandwavyWeights.MaxSums2 * Math.min(HandwavyWeights.MaxSumsCap  * HandwavyWeights.MaxSumsCapPlayersMult[MyBot.GAMETYPE_PLAYERS],t.movesSum2) * gatherScore * HandwavyWeights.MaxSums2PlayersMult[MyBot.GAMETYPE_PLAYERS];
        dependentOnHaliteAndTurn += HandwavyWeights.MaxSums3 * Math.min(HandwavyWeights.MaxSumsCap * HandwavyWeights.MaxSumsCapPlayersMult[MyBot.GAMETYPE_PLAYERS],t.movesSum3) * gatherScore  * HandwavyWeights.MaxSums3PlayersMult[MyBot.GAMETYPE_PLAYERS];
        dependentOnHaliteAndTurn += HandwavyWeights.MaxSums4 * Math.min(HandwavyWeights.MaxSumsCap * HandwavyWeights.MaxSumsCapPlayersMult[MyBot.GAMETYPE_PLAYERS],t.movesSum4) * gatherScore  * HandwavyWeights.MaxSums4PlayersMult[MyBot.GAMETYPE_PLAYERS];




        t.evalScoreDependOnTurnAndHaliteImportance = dependentOnHaliteAndTurn;

        float flat = 0;
        flat += HandwavyWeights.EnemyTerrainDesire *  Util.SqrtCurve(t.enemyTerrain,-5,6) * gatherScore  * adjustedForAverageHalite;


//        if ( t.turnsFromEnemyDropoff <= 2 && turnsLeft > 25 && t.turnsFromDropoff > 4) {
//            flat -= (3 - t.turnsFromEnemyDropoff) * HandwavyWeights.PunishmentCloseToEnemyDropoff * HandwavyWeights.MiscScore;
//        }

        flat += t.control * HandwavyWeights.ControlZoneMultiplier[MyBot.GAMETYPE_PLAYERS]  * adjustedForAverageHalite ;


        if(t.borderTile){
            flat += HandwavyWeights.TileBorderFlat + HandwavyWeights.TileBorderHalite * t.haliteStartTurn;
        }

        t.evalScoreFlat = flat;

        t.followPathval = (float)( HandwavyWeights.FollowFuturePathV2 * Math.pow(HandwavyWeights.ReduceTrustFuturePathNearbyFriends3, t.myShipsStartInRange3Avg) * Math.pow(HandwavyWeights.ReduceTrustFuturePathNearbyFriends5, t.myShipsStartInRange5Avg));



        //Avoid the cross around dropoffs if not carrying halite a bit, can cause roadblocks
        t.crossScore = 0f;
        if (crossMap[t.x][t.y]) {
            //Only bother doing this if there's actually a ship nearby
            boolean foundBigShip = false;
            for(Tile t2 : t.tilesInWalkDistance[4]){
                CheapShip s = Map.currentMap.GetShipAt(t2);
                if(s != null && s.halite > 500 && Map.DoIOwnShip[s.id]){
                    if (MyBot.turnsLeft > HandwavyWeights.RunLeftTimerV3) {
                        t.crossScore = -HandwavyWeights.CrossAvoidStrengthV3  * adjustedForAverageHalite;
                    } else {
                        t.crossScore = -HandwavyWeights.CrossAvoidStrengthLastTurnsV3  * adjustedForAverageHalite;
                    }
                    break;
                }
            }
        }


        t.runToMyDropoffScore = 0;
        float timeleftToRun = turnsLeft - ((HandwavyWeights.RunLeftFactor * t.complexDropoffDist) + 1f);
        if (timeleftToRun < HandwavyWeights.RunLeftTimerV3) {
            t.runToMyDropoffScore += timeleftToRun * HandwavyWeights.RunCurV2;
            if(timeleftToRun < 1){
                t.runToMyDropoffScore -= 10000;
            }
        }



        int timeleftToRunToEnemy = turnsLeft - (Math.max(1, turnsFromDangerousEnemyDropoff[t.x][t.y]) + 1);
        if (timeleftToRunToEnemy < HandwavyWeights.RunLeftTimerEnemyV2  && timeleftToRunToEnemy >= 0) {
            if (turnsFromDangerousEnemyDropoff[t.x][t.y] > 0) {
                t.runToEnemyDropoffScore = timeleftToRunToEnemy * HandwavyWeights.RunEnemyCurV2;
            }else{
                t.runToEnemyDropoffScore = 0;
            }
        }else{
            t.runToEnemyDropoffScore = 0;
        }


        if(MyBot.turn < Constants.MAX_TURNS * 0.3f){
            t.standOnTileScore = (1f-  t.distFromCenterProportion) *  HandwavyWeights.TileWeightDistanceCenterEarly[MyBot.GAMETYPE_PLAYERS] * adjustedForAverageHalite;
        }else{
            t.standOnTileScore = (1f- t.distFromCenterProportion) *  HandwavyWeights.TileWeightDistanceCenterLate[MyBot.GAMETYPE_PLAYERS] * adjustedForAverageHalite;
        }

        t.didEvalCalsThisTurn = true;

    }


    //A secondary evaluator, not used for the main algorithms
    public void EvaluatorGoalSeeking(){
        float score = 0;
        for(int turn =0; turn < SEARCH_DEPTH; turn++) {
            for (int i = 0; i < Map.staticMyShipCount; i++) {
                Move m = movesPerTurn[turn][i];
                if(m != null){
                    score += 100;// dont want ship suiciding to be a legit strat here
                    if(goals[m.ship.id] != null){
                        score -= m.to.DistManhattan(goals[m.ship.id]);
                    }
                }
            }
        }
        //Not using suggestions here, point is to be different
        this.nonfinalScore = score;
        this.nonFinalPlusSuggestions = score;
    }
    //A secondary evaluator, not used for the main algorithms
    public void EvaluatorShipHalite(){
        float score = 0;
        for(CheapShip s : finalMap.myShips){
            if(s != null){
                score+= 100 + s.halite;  //+100 is to prevent suiciding
                score += Map.staticHaliteMap[s.x][s.y] * 0.5f;
            }
        }
        //Not using suggestions here, point is to be different
        score += finalMap.playerMoney;
        this.nonfinalScore = score;
        this.nonFinalPlusSuggestions = score;
    }
    //A secondary evaluator, not used for the main algorithms
    public void EvaluatorAwayFromStart(){
        float score = 0;
        for(CheapShip s : finalMap.myShips){
            if(s != null){
                CheapShip oldShip = Map.GetFreshShip(s.id);
                score +=  10 + Map.tiles[s.x][s.y].DistManhattan(oldShip.x,oldShip.y);
            }
        }
        this.nonfinalScore = score;
        this.nonFinalPlusSuggestions = score;
    }

    //A secondary evaluator, not used for the main algorithms
    public void EvaluatorDropoffFinder(){
        float score = 0;
        for(int turn =0; turn < SEARCH_DEPTH; turn++) {
            for (int i = 0; i < Map.staticMyShipCount; i++) {
                Move m = movesPerTurn[turn][i];
                if(m != null){
                    score += 5000f;// dont want ship suiciding to be a legit strat here
                    score += m.ship.halite;
                    score -= turnsFromDropoff[m.to.x][m.to.y] * 50f;
                }
            }
        }
        //Not using suggestions here, point is to be different
        score += finalMap.playerMoney * 1.5f;
        this.nonfinalScore = score;
        this.nonFinalPlusSuggestions = score;
    }
    //A secondary evaluator, not used for the main algorithms
    public void EvaluatorEnemyKiller(){
        float score = -finalMap.enemyShipsCount;
        //Not using suggestions here, point is to be different
        this.nonfinalScore = score;
        this.nonFinalPlusSuggestions = score ;
    }
    //A secondary evaluator, not used for the main algorithms
    public void EvaluatorMoveScoreOnly(){
        this.nonfinalScore = movesScore;
        this.nonFinalPlusSuggestions = movesScore;
    }


    public void EvaluatePicker(int i){
        if(i ==0){
            Evaluate(0,true);
        }  else if(i == 1){
            EvaluatorMoveScoreOnly();
        } else if(i == 2){
            EvaluatorShipHalite();
        } else if(i == 3){
            EvaluatorGoalSeeking();
        }else if(i == 4){
            EvaluatorAwayFromStart(); //EvaluatorDropoffFinder();
        }else if(i == 5){
            EvaluatorEnemyKiller();
        }
    }

    public void EvaluateExperimental(){
        float score = 0;

        score += movesScore;

        score -= CountCollisions(this) * 100000;

        this.nonfinalScore = score;
        this.nonFinalPlusSuggestions = score + suggestionScore;
    }

    public void MoveEvaluateExperimental(int turn, Move m, int moveindex){
        float score = 0;
        Tile from = m.from, to = m.to;
        int toX = to.x, toY = to.y, shipId = m.ship.id,shipHalite = m.ship.halite;
        int roomLeft = 1000 - shipHalite;
        int haliteFrom, haliteTo;
        if(from.lastKnownStartHalite >= 0 && turn > 0){
            haliteFrom = from.lastKnownStartHalite;
        }else{
            haliteFrom = from.haliteStartTurn; //TODO: try and fix so that we don't end up here / so that we can get an accurate value here
        }
        if(to.lastKnownStartHalite >= 0  && turn > 0){
            haliteTo = to.lastKnownStartHalite;
        }else{
            haliteTo = to.haliteStartTurn;
        }
        int usableHaliteUnderNextSpot =  Math.min(roomLeft, haliteTo);


        score += 10000;
//        if (to.equals(WeirdAlgo.recommendations[shipIndex][turn])) {
//            score += 100;
//        }

//        if (futurePathingSuggestions[turn][moveindex] != null && m.to.equals(futurePathingSuggestions[turn][moveindex].to)) {
//            score += 100;
//        }
        if (simulMovePlan[turn][moveindex] != null && m.to.equals(simulMovePlan[turn][moveindex])) {
            score += 100;
        }

//        switch(state[shipIndex]){
//
//            case STATE_RETURN:
//
//                if(goals[shipIndex] != null && goals[shipIndex].turnsFromDropoff == 0){
//                    score -= to.DistManhattan(goals[shipIndex]);
//                }
//                else{
//                    score -= to.turnsFromDropoff;
//                }
//
//
//                break;
//            case STATE_NORMAL:
//            default:
//
////                if (MyBot.myId == 0 &&   futurePathingSuggestions[turn][moveindex] != null && m.to.equals(futurePathingSuggestions[turn][moveindex].to)) {
////                    score += 100;
////                }
////                else
//
//                if (to.equals(WeirdAlgo.recommendations[shipIndex][turn])) {
//                    score += 100;
//                }
//
////                if(goals[shipIndex] != null){
////                    score -= to.DistManhattan(goals[shipIndex]);
////                }
//
//
//                break;
//        }


        m.score = score;
    }



    public static boolean ShouldQuitStricter(){
        boolean shouldI = Map.totalSimulationsDoneThisTurn > 60000 || Map.lastTurnBoardsCounter > 70000 || (!MyBot.DETERMINISTIC_TIME_INDEPENDENT && MyBot.TIME_ALLOWED -  (System.currentTimeMillis() - MyBot.startTurn) < MINIMUM_TIME_LEFT_IN_MS - 300);
//        if(shouldI && MyBot.SERVER_RELEASE  && !MyBot.FINALS_RELEASE){
//            System.err.println(" q" + (MyBot.TIME_ALLOWED -  (System.currentTimeMillis() - MyBot.startTurn)));
//        }

        return shouldI && !Test.IN_TEST_PROGRESS;
    }

    public static boolean ShouldQuit(){
        boolean shouldI = Map.totalSimulationsDoneThisTurn > 80000 || Map.lastTurnBoardsCounter > 90000 || (!MyBot.DETERMINISTIC_TIME_INDEPENDENT && MyBot.TIME_ALLOWED -  (System.currentTimeMillis() - MyBot.startTurn) < MINIMUM_TIME_LEFT_IN_MS - 20);
//        if(shouldI && MyBot.SERVER_RELEASE  && !MyBot.FINALS_RELEASE){
//            System.err.println(" q" + (MyBot.TIME_ALLOWED -  (System.currentTimeMillis() - MyBot.startTurn)));
//        }

        return shouldI && !Test.IN_TEST_PROGRESS;
    }

    public static boolean ShouldQuitLessStrict(){
        boolean shouldI = Map.totalSimulationsDoneThisTurn > 80000 || (!MyBot.DETERMINISTIC_TIME_INDEPENDENT && MyBot.TIME_ALLOWED -  (System.currentTimeMillis() - MyBot.startTurn) < MINIMUM_TIME_LEFT_IN_MS);
        if(shouldI && MyBot.SERVER_RELEASE  && !MyBot.FINALS_RELEASE){
            System.err.println(" q" + (MyBot.TIME_ALLOWED -  (System.currentTimeMillis() - MyBot.startTurn)));
        }

        return shouldI;
    }



    public String finalResultToString(boolean full){
        if(!MyBot.ALLOW_LOGGING || !Log.LogType.PLANS.active) return "";
        if(finalMap == null) return "bad result?";

        StringBuilder s = new StringBuilder();

//        s.append( "Id: " + id + "Score: " + finalScore + " (" + finalPlusSuggestions + ")  playerhalite: " + finalMap.playerMoney + "  shipmoney:  " + finalMap.shipHaliteSum() + "\r\n");
        for(CheapShip ship : finalMap.myShips){
            if(ship != null) {
                s.append(ship.toString());
            }
        }

//        s += " Planchain: ";
//        for(Integer i : planChain){
//            s += i + ", ";
//        }

        if(full) {

            s.append("\r\n\r\nMoves:\r\n");

            for (int i = 0; i < SEARCH_DEPTH; i++) {
                s.append("\r\nT: " + i + "   ");
                for(Move m : movesPerTurn[i]){
                    if(m!= null) {

                        s.append(m.ship.id +": " + m.toString() + ":  " + m.score + ", ");
                    }
                }
            }
        }

        return s.toString();

    }


    public void OutputToPlanChannel(int channel){
        if(MyBot.DO_GAME_OUTPUT  && false){ //turn this back on if not doing future pathing plan

            String s = "plan:" + channel +  ";" + SEARCH_DEPTH + ";" + finalScore + ":";
            for(int i = 0; i < SEARCH_DEPTH; i++){


                for(Move m : movesPerTurn[i]) {
                    if(m != null) {
                        s += m.from.x + "," + m.from.y + "," + m.to.x + "," + m.to.y + "," + m.ship.id + "/";
                    }
                }

                if(DoEnemySimulation) {
                    for(Move m : enemyMovesPerTurn[i]) {
                        if(m != null) {
                            s += m.from.x + "," + m.from.y + "," + m.to.x + "," + m.to.y + "," + m.ship.id + "/";
                        }
                    }
                }


                s += ";";
            }
            GameOutput.info.add(s);

        }
    }


}
