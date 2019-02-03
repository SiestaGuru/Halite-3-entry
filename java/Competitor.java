import hlt.Constants;
import hlt.Game;

import java.util.ArrayList;

//This class tracks some information per player
public class Competitor {

    public boolean isMe;
    public int id;

    public static Competitor mostDangerous = null;
    public static int myExpectedRank;
    public  boolean dangerousEnoughToAnnoy = false;

    public ArrayList<DropPoint> dropoffs;
    public ArrayList<CheapShip> ships;
    public ArrayList<CheapShip> shipsLastTurn;

    public int[] halite;
    public int[] haliteGathered;
    public int[] haliteBroughtHome;
    public int carryingHalite = 0;
    public int totalGathered = 0;
    public int totalMined = 0;
    public int totalInspire = 0;
    public int totalBurned = 0;
    public int totalDropped = 0; //TODO: implement
    public int shipsBuilt = 0;
    public int dropoffCount;
    public int shipCount = 0;
    public int currentPoints = 0;
    public int expectedPoints = 0;
    public int broughtHome;
    public int shipTurnsAlive;
    public int lastBuiltShip;

    public double scaryScore;
    public double scaryFactor;
    public int expectedRank;


    public int[][] collectedAt;

    public double gatherRate;

    public static int highestEnemyShipCount = 0;

    public Competitor(int id, boolean isMe){
        this.isMe = isMe;
        this.id = id;
        ships = new ArrayList<>();
        shipsLastTurn = new ArrayList<>();
        halite = new int[Constants.MAX_TURNS + 1];
        haliteGathered = new int[Constants.MAX_TURNS + 1];
        haliteBroughtHome = new int[Constants.MAX_TURNS + 1];
        halite[0] = 5000;
        dropoffCount = 0;
        broughtHome = 0;
    }

    public void init(){
        collectedAt = new int[Map.width][Map.height];
    }


    public void UpdateTurnInitial(){
        shipsLastTurn = ships;
        ships = new ArrayList<>();
        dropoffs = new ArrayList<>();


    }

    public void UpdateTurnSecond(){
        if(MyBot.turn > 0){
            carryingHalite = 0;
            shipCount = ships.size();
            currentPoints = halite[MyBot.turn -1];
            broughtHome = haliteBroughtHome[MyBot.turn -1];


            for (CheapShip s : ships){
                boolean found = false;
                carryingHalite += s.halite;
                shipTurnsAlive++;
                for(CheapShip old : shipsLastTurn){
                    if(s.id == old.id){
                        if(s.halite == 0 && old.halite != 0){
                            if(isMe){
                                if (Map.myDropoffMap[s.x][s.y] != null) {
                                    currentPoints += old.halite;
                                    broughtHome += old.halite;
                                }
                            }else {
                                if (Map.enemyDropoffMap[s.x][s.y] != null) {
                                    currentPoints += old.halite;
                                    broughtHome += old.halite;
                                }
                            }
                        }
                        else if(s.halite > old.halite){
                            int gained = s.halite - old.halite;
                            totalGathered += gained;
                            collectedAt[s.x][s.y] += gained;

                            int haliteChange = Map.staticHaliteMapLastTurn[s.x][s.y] - Map.staticHaliteMap[s.x][s.y];
                            totalMined += haliteChange;
                            totalInspire += (gained - haliteChange);



                        } else if(s.halite < old.halite){
                            totalBurned += (old.halite - s.halite);
                        }

                        found = true;
                        break;
                    }
                }
                if(!found){
                    shipsBuilt++;
                    currentPoints -= Constants.SHIP_COST;
                    lastBuiltShip = MyBot.turn;
                }
            }
            if(Game.players.get(id).dropoffs.size() > dropoffCount){
                //TODO: enemy shipyard prob bugged
                dropoffCount++;
                if(MyBot.turn > 20) {
                    currentPoints -= Constants.DROPOFF_COST;
                }
            }
            halite[MyBot.turn] = currentPoints;
            haliteGathered[MyBot.turn] = totalGathered;
            haliteBroughtHome[MyBot.turn] = broughtHome;


            if(MyBot.DO_GAME_OUTPUT){

                StringBuilder s = new StringBuilder();

                s.append("collectedat" + id + ":");
                for(int y =0; y < Map.height; y++){
                    for(int x=0; x < Map.width; x++){
                        s.append(collectedAt[x][y] + ",");
                    }
                    s.append(";");
                }
                GameOutput.info.add(s.toString());

            }
        }
    }

    public static void Analyze(){
        int bestId;
        double highestScore = -10000.0;

        double playerScore = 1f;
        highestEnemyShipCount = 0;

        for(int i = 0; i < MyBot.playerCount; i++){
            Competitor player = MyBot.players[i];

            double score = player.halite[MyBot.turn];
            score += player.carryingHalite * 0.9;
            //How much can they be expected to still gather and turnin at the current pace
            int turnsago = Math.max(MyBot.turn / 10, Math.min(15,MyBot.turn));

            //Two different methods of calculating expected income.
            //Based on the total income the last few turns
            double gatherRateLastTurns = ((player.haliteGathered[MyBot.turn] - player.haliteGathered[turnsago]) / Math.max(1.0,(double)turnsago));
            //Based on the income per ship per turn multiplied by ship count
            double gatherRateShipTurns =  player.shipCount * ((player.haliteBroughtHome[MyBot.turn] * 0.6 + player.haliteGathered[MyBot.turn] * 0.4) / Math.max(1.0,player.shipTurnsAlive));

            //mix em up, multiply by turns left
            double willgather = (gatherRateLastTurns * 0.4 + gatherRateShipTurns * 0.6)  * Math.max(0.0,MyBot.turnsLeft - 20.0);


            score += willgather *= 0.8f;

            double timefactor = 1.0 - (((double)MyBot.turnsLeft) / ((double)Constants.MAX_TURNS));

            score +=  player.shipCount * 1000.0 * timefactor;
            score += player.dropoffCount * 600.0 * timefactor;

            if(!player.isMe) {
                highestEnemyShipCount = Math.max(highestEnemyShipCount, player.shipCount);
            }

            if(MyBot.turn < Constants.MAX_TURNS * 0.2) {
                //Will prob still build a lot of ships
                player.expectedPoints = (int) (player.halite[MyBot.turn] + player.carryingHalite * 0.9 + willgather * 2.0);
            }
            else if(MyBot.turn < Constants.MAX_TURNS * 0.5) {
                //Will prob still build ships
                player.expectedPoints = (int) (player.halite[MyBot.turn] + player.carryingHalite * 0.9 + willgather * 1.4);
            }
            else if(MyBot.turn < Constants.MAX_TURNS * 0.75) {
                //fleet will remain roughly equal / gather rather starts decreasing enough to keep income similar
                player.expectedPoints = (int) (player.halite[MyBot.turn] + player.carryingHalite * 0.9 + willgather);
            } else{
                //gather rather per ship is reducing
                player.expectedPoints = (int) (player.halite[MyBot.turn] + player.carryingHalite * 0.9 + willgather * 0.7);
            }



            player.scaryScore = score;
            if(player.isMe){
                playerScore = score;
            }else{
                if(score > highestScore){
                    highestScore = score;
                    mostDangerous = player;
                }
            }
        }

        for(Competitor c : MyBot.players) {
            if (!c.isMe) {
                c.scaryFactor = c.scaryScore / Math.max(playerScore, 10);
            }
            c.expectedRank = 1;
            for(Competitor c2 : MyBot.players) {
                if(c2.id != c.id && c2.scaryScore > c.scaryScore){
                    c.expectedRank++;
                }
            }
        }
        myExpectedRank = MyBot.me.expectedRank;


        if(MyBot.ALLOW_LOGGING && Log.LogType.COMPETITORS.active) {
            String s = "\r\n";
            for (Competitor c2 : MyBot.players) {
                s += c2.id;
                if(c2.isMe){
                    s += "(me)    ";
                }else{
                    s += "(enemy) ";
                }
                s+= "Rank: " + c2.expectedRank + " scaryness: " + c2.scaryFactor + " finalScore: " + c2.halite[MyBot.turn] + " ships: " + c2.shipsBuilt + " myDropoffs: " + c2.dropoffCount + " burned: " + c2.totalBurned + " collected: " + c2.haliteGathered[MyBot.turn] + "\r\n";
            }
//            Log.log(s, Log.LogType.COMPETITORS);
        }
    }



    public double GetEffectiveHpsOverXturns(int turns){
        int startTurn = Math.max(0,MyBot.turn - turns);

        double collected = totalGathered - haliteGathered[startTurn];
        //TODO: do actual ship turns, not current shipcount


        return collected / Math.max(10,shipCount);


    }
}
