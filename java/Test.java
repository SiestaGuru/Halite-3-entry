import hlt.Game;

import java.util.ArrayList;
import java.util.Random;

public class Test {

    public static boolean IN_TEST_PROGRESS = false;

    public static void DoTests() throws Throwable{
        //Lots of tests...
        PlanningTest();
    }



    //The one test here doesn't even work anymore. It was too cumbersome to maintain
    public static void PlanningTest() throws Throwable{
//
        Log.log("Starting Planning test" , Log.LogType.TESTS);


        MyBot.GAMETYPE_PLAYERS = 0;
        MyBot.GAMETYPE_DENSITY = 0;
        MyBot.GAMETYPE_SIZE = 0;
        MyBot.GAMETYPE = 0;

        Map.width = 5;
        Map.height = 5;
        Map.GenerateFirstMap(true);

        Map.currentMap.TestSetHalite();
        Map.currentMap.myShips = new CheapShip[4];
        Map.currentMap.shipMap = new CheapShip[5*5];
        //Map.currentMap.clearTheseTiles = new ArrayDeque<>();
        Map.currentMap.clearTheseArray = new Tile[Map.maxClearThese];



        CheapShip s1 =  CheapShip.MakeShip(1,(short)100,(byte)0,(byte)0);
        CheapShip s2 = CheapShip.MakeShip(2,(short)1000,(byte)3,(byte)0);
        CheapShip s3 = CheapShip.MakeShip(3,(short)0,(byte)3,(byte)3);
        CheapShip s4 = CheapShip.MakeShip(4,(short)70,(byte)0,(byte)3);
        Map.currentMap.PutMyShipWhereItBelongs(s1,0);
        Map.currentMap.PutMyShipWhereItBelongs(s2,1);
        Map.currentMap.PutMyShipWhereItBelongs(s3,2);
        Map.currentMap.PutMyShipWhereItBelongs(s4,3);

        Map.staticMyShipCount = 4;
        Map.myIndexOfIds[s1.id] = 0;
        Map.myIndexOfIds[s2.id] = 1;
        Map.myIndexOfIds[s3.id] = 2;
        Map.myIndexOfIds[s4.id] = 3;
        Map.DoIOwnShip[s1.id] = true;
        Map.DoIOwnShip[s2.id] = true;
        Map.DoIOwnShip[s3.id] = true;
        Map.DoIOwnShip[s4.id] = true;

        Map.staticMyShips = new ArrayList<>();
        Map.staticEnemyShips = new ArrayList<>();

        for(CheapShip s : Map.currentMap.myShips){
            Map.staticMyShips.add(s);
            Map.staticShipsById[s.id] = s;
        }


        DropPoint d = new DropPoint(5,3,4,true, Game.myId.id);
        Map.myDropoffMap = new DropPoint[Map.width][Map.height];
        Map.enemyDropoffMap = new DropPoint[Map.width][Map.height];
        Map.myDropoffMap[d.x][d.y] = d;
        Map.myDropoffs.add(d);



        Map m = new Map(Map.currentMap);//,0);



//        Plan.inspireOdds = new float[10][5][5];



        TestPlanEquality(m);


        //t1
        m.NewSimInit(true,-1);
        m.QueueSimulatedMove(new Move(m.GetTile(s1),m.GetTile(s1).West(),s1));
        m.QueueSimulatedMove(new Move(m.GetTile(s2),m.GetTile(s2),s2));
        m.QueueSimulatedMove(new Move(m.GetTile(s3),m.GetTile(s3),s3));
        m.Simulate(0,true);

        Log.log(m.toString(), Log.LogType.TESTS);
        Log.log(m.toStringShips(false), Log.LogType.TESTS);

        for(CheapShip s: m.myShips){
            if(s != null) {
                Log.log(s.toString(), Log.LogType.TESTS);
            }
        }

        s1 = m.GetShipById(s1.id);
        s2 = m.GetShipById(s2.id);
        s3 = m.GetShipById(s3.id);
        s4 = m.GetShipById(s4.id);

        IsTrue(s1.x == 4);
        IsTrue(s1.y == 0);

        IsTrue(s2.x == 3);
        IsTrue(s2.y == 0);

        IsTrue(s3.x == 3);
        IsTrue(s3.y == 3);

        IsTrue(s4.x == 0);
        IsTrue(s4.y == 3);

        IsTrue(s1.halite == 90);
        IsTrue(s2.halite == 1000);
        IsTrue(s3.halite == 3);
        IsTrue(s4.halite == 95);

        IsTrue( m.GetHaliteAt(0,0) == 100);
        IsTrue(m.GetHaliteAt(3,0) == 75);
        IsTrue(m.GetHaliteAt(3,3) == 7);
        IsTrue(m.GetHaliteAt(0,3) == 75);

        TestPlanEquality(m);

        m.NewSimInit(true,-1);

        m.QueueSimulatedMove(new Move(m.GetTile(s1),m.GetTile(s1).West(),s1));
        m.QueueSimulatedMove(new Move(m.GetTile(s2),m.GetTile(s2).North(),s2));
        m.QueueSimulatedMove(new Move(m.GetTile(s4),m.GetTile(s4),s4));
        m.Simulate(1,true);
        Log.log("-----------");
        Log.log(m.toString(), Log.LogType.TESTS);
        Log.log(m.toStringShips(false), Log.LogType.TESTS);

        for(CheapShip s: m.myShips){
            if(s != null) {
                Log.log(s.toString(), Log.LogType.TESTS);
            }
        }
        TestPlanEquality(m);
        m.NewSimInit(true,-1);
        m.Simulate(2,true);
        Log.log("-----------", Log.LogType.TESTS);
        Log.log(m.toString(), Log.LogType.TESTS);
        Log.log(m.toStringShips(false), Log.LogType.TESTS);

        for(CheapShip s: m.myShips){
            if(s != null) {
                Log.log(s.toString(), Log.LogType.TESTS);
            }
        }

        s1 = m.GetShipById(s1.id);
        s2 = m.GetShipById(s2.id);
        s3 = m.GetShipById(s3.id);
        s4 = m.GetShipById(s4.id);

        TestPlanEquality(m);

        m.NewSimInit(true,-1);
        m.QueueSimulatedMove(new Move(m.GetTile(s1),m.GetTile(s1).North(),s1));
        m.QueueSimulatedMove(new Move(m.GetTile(s2),m.GetTile(s2).South(),s2));
        m.QueueSimulatedMove(new Move(m.GetTile(s3),m.GetTile(s3).West(),s3));
        m.Simulate(3,true);
        Log.log("-----------", Log.LogType.TESTS);
        Log.log(m.toString(), Log.LogType.TESTS);
        Log.log(m.toStringShips(false), Log.LogType.TESTS);

        for(CheapShip s: m.myShips){
            if(s != null) {
                Log.log(s.toString(), Log.LogType.TESTS);
            }
        }

        TestPlanEquality(m);
        s1 = m.GetShipById(s1.id);
        s2 = m.GetShipById(s2.id);
        s3 = m.GetShipById(s3.id);
        s4 = m.GetShipById(s4.id);
        m.NewSimInit(true,-1);
        m.QueueSimulatedMove(new Move(m.GetTile(s1),m.GetTile(s1),s1));
        m.QueueSimulatedMove(new Move(m.GetTile(s2),m.GetTile(s2).North(),s2));
        m.QueueSimulatedMove(new Move(m.GetTile(s3),m.GetTile(s3).West(),s3));
        m.QueueSimulatedMove(new Move(m.GetTile(s4),m.GetTile(s4).East(),s4));

        m.Simulate(4,true);

        TestPlanEquality(m);

        Log.log("-----------", Log.LogType.TESTS);
        Log.log(m.toString(), Log.LogType.TESTS);
        Log.log(m.toStringShips(false), Log.LogType.TESTS);

        for(CheapShip s: m.myShips){
            if(s != null) {
                Log.log(s.toString(), Log.LogType.TESTS);
            }
        }




        Log.log("Success!" , Log.LogType.TESTS);






        Map.myIndexOfIds[s1.id] = -1;
        Map.myIndexOfIds[s2.id] = -1;
        Map.myIndexOfIds[s3.id] = -1;
        Map.myIndexOfIds[s4.id] = -1;

        Map.DoIOwnShip[s1.id] = false;
        Map.DoIOwnShip[s2.id] = false;
        Map.DoIOwnShip[s3.id] = false;
        Map.DoIOwnShip[s4.id] = false;

    }


    private static void TestPlanEquality(Map m) throws Exception{
        Plan basePlan = null;

        Plan.USE_OLD_METHOD_FIRST = false;
        Plan.scoreOnMoveSet.clear();

        //TODO: There still seems to be a miniscule difference in simulations:
        //Something about playermoney not updating exactly the same way in case of zombie-related moves on top of dropoffs
        //I don't really care enough right now

        Plan.USE_OLD_METHOD = false;
        Map.currentMap = new Map(m);//,100);
        MyBot.rand = new Random(1000);
        Plan.Prep();
        basePlan = EnemyPrediction.DoPredictions( Plan.SEARCH_DEPTH,HandwavyWeights.PREDICT_ENEMY_TURNS);
        Stopwatch.Stop("Enemy prediction");
        basePlan.StripMyMoves();
        Plan.basePlan = basePlan;
        Plan p2 = Plan.FindBestSoloJourneysMix(basePlan,true,true,false);

         Plan.USE_OLD_METHOD = true;
        Map.currentMap = new Map(m);//,100);
        MyBot.rand = new Random(1000);
        Plan.Prep();
        basePlan = EnemyPrediction.DoPredictions( Plan.SEARCH_DEPTH,HandwavyWeights.PREDICT_ENEMY_TURNS);
        Stopwatch.Stop("Enemy prediction");
        basePlan.StripMyMoves();
        Plan.basePlan = basePlan;
        Plan p1 = Plan.FindBestSoloJourneysMix(basePlan,true,true,false);





        Log.log("Simulated scores: " + p1.finalScore + "   " +  p2.finalScore, Log.LogType.TESTS);
        Log.log(p1.finalResultToString(true), Log.LogType.TESTS);
        Log.log(p2.finalResultToString(true), Log.LogType.TESTS);

        IsTrue(Math.abs(p1.finalScore -p2.finalScore) < 0.00001f);


    }


    private static void IsTrue(boolean b) throws Exception{
        if(!b) throw new Exception();
    }
    private static void IsFalse(boolean b) throws Exception{
        if(!b) throw new Exception();
    }


}
