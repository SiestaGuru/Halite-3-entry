
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;

/*
WeirdAlgo is the most powerful of the side-algorithms. It essentially works like a decently strong bot on its own
and with hindsight, might've formed a better basis for the entire bot.

It works by:
Iterate over a bunch of precomputed paths originating from a ships starting tile   (short paths which dont return to previously visited tiles)
For every path, determine the best spots we would want to mine on, simply by just taking the tile with the highest gains along that path
Compare the final results of these paths in terms of halite gains, and distance to our goal/dropoff

This ends up being a really fast/efficient way of determining optimal mining over medium-length times.
It's orders of magnitude faster than bruteforcing all different routes, while achieving very similar results.
The main differences are:
- This already pre-filters a lot of nonsense backtracking paths that will very rarely be chosen
- It uses a very cheap method and less computationally complex method of finding the right places to stand still on
- It caps the amount of non-standstill moves since it's rarely useful to be checking further (given a goal to aim for)

Unfortunately, as is, it still lacks something in terms of multiple ship coordination and misses a lot of the finer details
 and the long term tweaks within the main algorithm.


 */
public class WeirdAlgo {

    public static int TURN_DEPTH;
    public static int MAX_PATH_LENGTH;

    public static Tile[][] recommendations;
    public static Tile[][] lastTurnPath;

    static boolean isActive;
    static Tile[] emptyrecs;
    static ArrayList<WeirdAlgoPathSuggestion> emptySuggestions = new ArrayList<>();

//    static boolean[][] reserved;
    static float[] consistencyBonuses;

    public static void SetRecommendations(){

        if(emptyrecs == null || emptyrecs.length < Plan.SEARCH_DEPTH ){
            emptyrecs = new Tile[Plan.SEARCH_DEPTH];
        }
//        reserved = new boolean[Map.width][Map.height];

        for(CheapShip s : Map.staticMyShips){
//            reserved[s.x][s.y] = true;

            if(lastTurnPath[s.id] == null){
                lastTurnPath[s.id] = new Tile[0];
            }
            recommendations[s.id] = null;
        }


        StringBuilder results = new StringBuilder();

        ArrayList<WeirdAlgoPathSuggestion> suggestions = new ArrayList<>();
        int[][] stoodstillOn = new int[Map.width][Map.height];
        boolean[][] t0MoveReserved = new boolean[Map.width][Map.height];




        for(CheapShip s : Map.staticMyShips){
//            reserved[s.x][s.y] = false;
//            //if(MyBot.EXPERIMENTAL_MODE){// || MyBot.myId == 0) {
//               // HandwavyWeights.WeirdAlgoWeight = 50f;
//                recommendations[s.id] = GetRecommendedTilesForShip(s);
//           // }else{
//            //    recommendations[s.id] = GetRecommendedTilesForShipOld(s);
//          //  }
//            for(int i = 0; i < Math.min(HandwavyWeights.ReserveDepth,recommendations[s.id].length); i++ ){
//                Tile t = recommendations[s.id][i];
//                if(t != null){
//                    reserved[t.x][t.y] = true;
//                }
//            }

            suggestions.addAll(GetRecommendedTilesForShip(s));

            if(!s.CanMove(Map.currentMap)){
                t0MoveReserved[s.x][s.y] = true;
            }
        }

        Collections.sort(suggestions);





        suggestionloop:
        for(WeirdAlgoPathSuggestion w : suggestions){
            if(recommendations[w.s.id] == null){

                for(int i = 0; i < w.path.length;i++){
                    if( w.standstill[i] > 0 &&   stoodstillOn[w.path[i].x][w.path[i].y]  >= HandwavyWeights.WeirdAlgoMaxGathersByOtherShips) {
                        continue suggestionloop;
                    }
                }

                if(w.standstill[0] == 0){
                    if(t0MoveReserved[w.path[1].x][w.path[1].y]){
                        continue suggestionloop;
                    }
                    t0MoveReserved[w.path[1].x][w.path[1].y] = true;
                }else if(w.s.CanMove(Map.currentMap)) {
                    if(t0MoveReserved[w.path[0].x][w.path[0].y]){
                        continue suggestionloop;//TODO: Maybe it's not nice to force other units to move
                    }
                    t0MoveReserved[w.path[0].x][w.path[0].y] = true;
                }

                Tile[] recs = new Tile[Math.max(TURN_DEPTH,Plan.SEARCH_DEPTH)];
                int pathIterator = 0;
                for (int i = 0; i < Math.min(recs.length,w.maxDepth); i++) {

                    if (w.standstill[pathIterator]-- == 0) {
                        if(++pathIterator >= w.path.length) break;
                    }
                    recs[i] = w.path[pathIterator];
                }
                recommendations[w.s.id] = recs;

                for(int i =0; i < w.path.length; i++){
                    stoodstillOn[w.path[i].x][w.path[i].y] += w.standstill[i];
                }

            }
        }



        for(CheapShip s : Map.staticMyShips){
            if(recommendations[s.id] == null){

                recommendations[s.id] = emptyrecs;
            }

            if(recommendations[s.id][0] != null) {
                results.append(s.id).append(":").append(recommendations[s.id][0].toString()).append(", ");
            }else{
                results.append(s.id).append(":null, ");
            }
        }






//        Log.log("Path Recommendations: " + results.toString(), Log.LogType.MAIN);


        if(MyBot.DO_GAME_OUTPUT){
            Move[][] moves = new Move[TURN_DEPTH][Map.staticMyShipCount];

            for(int i =0; i < Map.staticMyShipCount; i++){

                int index = Map.currentMap.myShips[i].id;

                if(recommendations[index] != null){
                    Tile prevTile= Map.staticShipsById[index].GetTile();

                    for(int j = 0; j < Math.min(TURN_DEPTH,recommendations[index].length);j++){
                        Tile newTile = recommendations[index][j];
                        if(newTile != null) {
                            Move m = new Move(prevTile, newTile, Map.currentMap.myShips[i]);
                            moves[j][i] = m;
                            prevTile = newTile;
                        }
                    }

                }
            }

            String s = "plan:" + 1 +  ";" + TURN_DEPTH + ";" + 0 + ":";
            for(int i = 0; i < TURN_DEPTH; i++){
                for(Move m : moves[i]) {
                    if(m != null ) {
                        s += m.from.x + "," + m.from.y + "," + m.to.x + "," + m.to.y + "," + m.ship.id + "/";
                    }
                }
                s += ";";
            }
            GameOutput.info.add(s);
        }

    }


    public static Tile[] GetRecommendedTilesForShipOld(CheapShip s){


        if(s.halite > 950 || !isActive){
            if(emptyrecs == null || emptyrecs.length < Plan.SEARCH_DEPTH ){
                emptyrecs = new Tile[Plan.SEARCH_DEPTH];
            }
            return emptyrecs;
        }

        Tile[] recs = new Tile[Math.max(TURN_DEPTH,Plan.SEARCH_DEPTH)];

        Tile startTile = s.GetTile();

        Tile[] recommendedPath = null;
        int[] recommendedStandstill = null;
        int besthalite = -10000;
        int[] standstillon = new int[7];
        int bestDropoffDist = 10000;

        outerloop:
        for(int stepsCount = 1; stepsCount < MAX_PATH_LENGTH; stepsCount++){

            for(Tile[] path : startTile.paths[stepsCount]) {
                int turnsLeft = TURN_DEPTH - stepsCount;

                for(int i =0; i <= stepsCount; i++){
                    standstillon[i] = 0;
                }

                while(turnsLeft-- > 0){
                    int bestIncrease = 0;
                    int increaseTile = 1;
                    for(int i =0; i <= stepsCount; i++){
                        int increase =  MyBot.gainIfStandFor1More[path[i].haliteStartTurnCappedTo5000][standstillon[i]];
                        if(increase > bestIncrease){
                            bestIncrease = increase;
                            increaseTile = i;
                        }
                    }
                    standstillon[increaseTile]++;
                }

                int totalHalite =0;
                for(int i =0; i <= stepsCount; i++){
                    totalHalite += MyBot.collectIfStandForX[path[i].haliteStartTurnCappedTo5000][standstillon[i]];
                }

                if(totalHalite > besthalite){
                    if(totalHalite + s.halite >= 1000){
                        //If we're already full, prioritize the dropoff distance of the final tile
                        int dist = path[path.length - 1].turnsFromDropoff;
                        if(dist <= bestDropoffDist){
                            if(dist < bestDropoffDist || startTile.haliteStartTurn > 50 || standstillon[0] == 0) { //the last two are to avoid the 'always stand still on the first turn' problem
                                besthalite = 999 - s.halite; //not 1000 to allow other records to surpass this one
                                bestDropoffDist = dist;
                                recommendedPath = path.clone();
                                recommendedStandstill = standstillon.clone();
                            }
                        }
                    }else {
                        besthalite = totalHalite;
                        recommendedPath = path.clone();
                        recommendedStandstill = standstillon.clone();
                    }

                }
            }
        }



        if(recommendedPath != null && recommendedStandstill != null) {
            int pathIterator = 0;
            for (int i = 0; i < TURN_DEPTH; i++) {

                if (recommendedStandstill[pathIterator]-- == 0) {
                    if(++pathIterator > recommendedPath.length) break;
                }
                recs[i] = recommendedPath[pathIterator];
            }
        }


        return recs;
    }

    public static ArrayList<WeirdAlgoPathSuggestion> GetRecommendedTilesForShip(CheapShip s){


        if(!isActive){
            return emptySuggestions;
//            if(emptyrecs == null || emptyrecs.length < Plan.SEARCH_DEPTH ){
//                emptyrecs = new Tile[Plan.SEARCH_DEPTH];
//            }
//            return emptyrecs;
        }

        Tile goal;
        int maxDepthAllowed = TURN_DEPTH;
        int maxPathLengthAllowed = MAX_PATH_LENGTH;
        float distanceUrgency;

        int maxIndexLastTurnPath = lastTurnPath[s.id].length - 1;
        Tile[] lastPath = lastTurnPath[s.id];


        int firsttepAllowStandstill = 0;
        if(s.halite > 990){
            firsttepAllowStandstill = 1;
        }

        if(Plan.dropOffRunner == s.id){
            goal = Plan.dropOffSpot;
            maxPathLengthAllowed = 2;
            maxDepthAllowed = 2;

            distanceUrgency = HandwavyWeights.UrgencyRunner;
        }
        else{
            goal = Plan.goals[Map.myIndexOfIds[s.id]];

            if((goal != null && goal.turnsFromDropoff == 0 && goal.DistManhattan(s.GetTile()) <= 5 && s.halite > 900)){
                maxPathLengthAllowed = 2;
                maxDepthAllowed = 1;
                distanceUrgency = HandwavyWeights.UrgencyFastReturn;
            }
            else if(s.halite > HandwavyWeights.LimitHalite1 ){
                maxPathLengthAllowed = Math.min(maxPathLengthAllowed,s.GetTile().turnsFromDropoff);
                maxDepthAllowed = maxPathLengthAllowed;
                distanceUrgency = HandwavyWeights.UrgencyReturn;


            }
            else if(goal != null){
                if(goal.goalIsAboutDenying){
                    maxDepthAllowed = Math.max(HandwavyWeights.DepthDeny,maxPathLengthAllowed);
                    distanceUrgency = HandwavyWeights.UrgencyDenyGoal;
                }else {
                    maxDepthAllowed = Math.max(HandwavyWeights.DepthGoal,maxPathLengthAllowed);
                    distanceUrgency = HandwavyWeights.UrgencyGoal;
                }
            }else{
                distanceUrgency = HandwavyWeights.UrgencyNoGoal;
            }

        }




        Tile startTile = s.GetTile();
        maxPathLengthAllowed = Math.min(maxPathLengthAllowed,6);
       // Tile[] recommendedPath = null;
       // int[] recommendedStandstill = null;
//        float bestscore = -10000;
        int[] standstillon = new int[7];
     //   int bestDropoffDist = 10000;


        for(Tile t : s.GetTile().tilesInWalkDistance[2]){
            t.bestScoreWeirdAlgo = -10000000f;
            t.bestPathWeirdAlgo = null;
            t.bestStandstillWeirdAlgo = null;
        }



        outerloop:
        for(int stepsCount = 1; stepsCount < maxPathLengthAllowed; stepsCount++){

            pathloop:
            for(Tile[] path : startTile.paths[stepsCount]) {
                float closestDist = 100;

                for(int i =0; i <= stepsCount; i++){
//                    if(reserved[path[i].x][path[i].y]){
//                        continue pathloop;
//                    }
                    standstillon[i] = 0;
                }

                int turnsLeft = maxDepthAllowed - stepsCount;


                int totalExpectedHalite = s.halite;

                while (turnsLeft-- > 0 && totalExpectedHalite < HandwavyWeights.LimitHalite2) {
                    float bestIncrease = 1;
                    int increaseTile = 1;
                    for (int i = firsttepAllowStandstill; i <= stepsCount; i++) {
                        float inspire = (1f + HandwavyWeights.WeirdAlgoInspireMult * Plan.inspireOdds[i][path[i].x][path[i].y]);
                        float increase = MyBot.gainIfStandFor1More[path[i].haliteStartTurnCappedTo5000][standstillon[i]] * inspire; //TODO: inspire
                        if (increase > bestIncrease) {
                            bestIncrease = increase;
                            increaseTile = i;
                        }
                    }
                    standstillon[increaseTile]++;
                    totalExpectedHalite += bestIncrease;
                }

                float score = 0;

                //Removing burn after gains (if done before, the  totalExpectedHalite < x  calc gets weird)
                for(int i =0; i <= stepsCount; i++){
                    totalExpectedHalite += MyBot.collectIfStandForX[path[i].haliteStartTurnCappedTo5000][0];

                    if(i < maxIndexLastTurnPath &&  (path[i].equals(lastPath[i])  || path[i].equals(lastPath[i+1]))){
                        score += consistencyBonuses[i];
                    }
                }

                totalExpectedHalite = Math.min(1000,totalExpectedHalite);



//                int totalHalite = s.halite;
//                for(int i =0; i <= stepsCount; i++){
//                    totalHalite = Math.min(1000, totalHalite + collectIfStandForX[Math.min(5000,path[i].haliteStartTurn)][standstillon[i]]);
//                }


                score +=  totalExpectedHalite;


                if(totalExpectedHalite >= HandwavyWeights.LimitHalite3) {
                    if (goal != null && goal.turnsFromDropoff == 0) {
                        for(int i =0; i <= stepsCount; i++) {
                            closestDist = Math.min(closestDist, path[stepsCount].ComplexDist(goal) + path[1].DistManhattan(goal));
                        }
                    }else{
                        for(int i =0; i <= stepsCount; i++) {
                            closestDist = Math.min(closestDist, path[stepsCount].complexDropoffDist  + path[1].complexDropoffDist);
                        }
                    }
                }else if(goal != null){
                    for(int i =0; i <= stepsCount; i++) {
                        closestDist = Math.min(closestDist, path[stepsCount].ComplexDist(goal)  + path[1].DistManhattan(goal));
                    }
                } else{
                    for(int i =0; i <= stepsCount; i++) {
                        closestDist = Math.min(closestDist, path[stepsCount].complexDropoffDist  + path[1].complexDropoffDist);
                    }
                }
                score -= closestDist * distanceUrgency * 0.5f;


                if(standstillon[0] == 0){
                    score -= Plan.eLocOdds[0][path[1].x][path[1].y] * HandwavyWeights.WeirdAlgoEOdds;
                } else{
                    score -= Plan.eLocOdds[0][path[0].x][path[0].y] * HandwavyWeights.WeirdAlgoEOdds;
                }


                if(stepsCount > 1){
                    if (score > path[2].bestScoreWeirdAlgo) {
                        path[2].bestScoreWeirdAlgo = score;
                        path[2].bestPathWeirdAlgo = path.clone();
                        path[2].bestStandstillWeirdAlgo = standstillon.clone();
                    }
                }else {
                    if (score > path[1].bestScoreWeirdAlgo) {
                        path[1].bestScoreWeirdAlgo = score;
                        path[1].bestPathWeirdAlgo = path.clone();
                        path[1].bestStandstillWeirdAlgo = standstillon.clone();
                    }
                }


//                if(score > bestscore){
//                    bestscore = score;
//                    recommendedPath = path.clone();
//                    recommendedStandstill = standstillon.clone();
//                }
            }
        }


        ArrayList<WeirdAlgoPathSuggestion> results = new ArrayList<>();

        for(Tile t : s.GetTile().tilesInWalkDistance[2]) {
            if(t.bestPathWeirdAlgo != null){
                WeirdAlgoPathSuggestion w = new WeirdAlgoPathSuggestion();
                w.path = t.bestPathWeirdAlgo;
                w.standstill = t.bestStandstillWeirdAlgo;
                w.score = t.bestScoreWeirdAlgo;
                w.s = s;
                w.maxDepth = maxDepthAllowed;
                results.add(w);
            }

        }
        return results;

        //Tile[] recs = new Tile[Math.max(TURN_DEPTH,Plan.SEARCH_DEPTH)];
//        if(recommendedPath != null && recommendedStandstill != null) {
//            int pathIterator = 0;
//            for (int i = 0; i < Math.min(recs.length,maxDepthAllowed); i++) {
//
//                if (recommendedStandstill[pathIterator]-- == 0) {
//                    if(++pathIterator >= recommendedPath.length) break;
//                }
//                recs[i] = recommendedPath[pathIterator];
//            }
//        }
//        return recs;
    }


    public static void GetAll6Paths() {
        MAX_PATH_LENGTH  = HandwavyWeights.PathLength;

        if(Map.width == 64){
            MAX_PATH_LENGTH = Math.min(MAX_PATH_LENGTH,4);
        }

        TURN_DEPTH = HandwavyWeights.TurnDepth;

        if(recommendations == null){

            recommendations = new Tile[2000][];
            lastTurnPath = new Tile[2000][];

            consistencyBonuses = new float[Math.max(7,MAX_PATH_LENGTH)];
            consistencyBonuses[0] = HandwavyWeights.PathConsistency0;
            consistencyBonuses[1] = HandwavyWeights.PathConsistency1;
            consistencyBonuses[2] = HandwavyWeights.PathConsistency2;
            consistencyBonuses[3] = HandwavyWeights.PathConsistency3;
            consistencyBonuses[4] = HandwavyWeights.PathConsistency4;
            consistencyBonuses[5] = HandwavyWeights.PathConsistency5;
            consistencyBonuses[6] = HandwavyWeights.PathConsistency6;
        }


        if(HandwavyWeights.ActivateWeirdAlgo == 0 ){//|| (MyBot.playerCount == 4 && Map.width == 64)){
            isActive = false;
            return; //too many timeouts online
        }
        else{
            isActive = true;
        }







//        if(Map.width < 56){
           // MAX_PATH_LENGTH  = 7;

//        }
//        else if(Map.width == 64 && MyBot.playerCount == 4){
//            MAX_PATH_LENGTH = 5;
//        }
//        else{
//            MAX_PATH_LENGTH  = 6;
//        }

        Stopwatch.Start();
//        ArrayDeque<Tile[]> tilelist = new ArrayDeque<>(1000000);

        for (Tile t0 : Map.tileList) {
            t0.paths[1] = new ArrayDeque<>(4);
            t0.paths[2] = new ArrayDeque<>(12);
            if(MAX_PATH_LENGTH >= 4) {
                t0.paths[3] = new ArrayDeque<>(36);
                if (MAX_PATH_LENGTH >= 5) {
                    t0.paths[4] = new ArrayDeque<>(100);
                    if (MAX_PATH_LENGTH >= 6) {
                        t0.paths[5] = new ArrayDeque<>(284);
                        if (MAX_PATH_LENGTH >= 7) {
                            t0.paths[6] = new ArrayDeque<>(780);
                        }
                    }
                }
            }

            for (Tile t1 : t0.neighbours) {
                t0.paths[1].add(new Tile[]{t0,t1});

                for (Tile t2 : t1.neighbours) {
                    if(!t2.equals(t0)) {
                        t0.paths[2].add(new Tile[]{t0,t1,t2});
                        if(MAX_PATH_LENGTH >= 4) {
                            for (Tile t3 : t2.neighbours) {
                                if (!t3.equals(t1)) {
                                    t0.paths[3].add(new Tile[]{t0, t1, t2, t3});
                                    if (MAX_PATH_LENGTH >= 5) {
                                        for (Tile t4 : t3.neighbours) {
                                            if (!t4.equals(t2) && !t4.equals(t0)) {
                                                t0.paths[4].add(new Tile[]{t0, t1, t2, t3, t4});
                                                if (MAX_PATH_LENGTH >= 6) {
                                                    for (Tile t5 : t4.neighbours) {
                                                        if (!t5.equals(t3) && !t5.equals(t1)) {
                                                            t0.paths[5].add(new Tile[]{t0, t1, t2, t3, t4, t5});
                                                            if (MAX_PATH_LENGTH >= 7) {
                                                                for (Tile t6 : t5.neighbours) {
                                                                    if (!t6.equals(t4) && !t6.equals(t2) && !t6.equals(t0)) {
                                                                        t0.paths[6].add(new Tile[]{t0, t1, t2, t3, t4, t5, t6});
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Stopwatch.Stop("Weird algo init");
    }




}
