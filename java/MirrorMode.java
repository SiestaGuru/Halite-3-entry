import hlt.Constants;
import hlt.Game;

/*
This class is just for a fun little mode that makes a player wait 1 turn and then perfectly mirror the enemy moves from then.
Turned off since it's awful
 */
public class MirrorMode {

    public static boolean StillViable = true;

    public static boolean DoWeBuildShipThisTurn = false;
    public static int TurnIntoDropoff = -1;

    public static Plan DoMirrorModeIfRunning(){
        DoWeBuildShipThisTurn = false;
        TurnIntoDropoff = -1;
        if(!StillViable) return null; // || MyBot.playerCount == 4

        if(MyBot.turn > 120){
//            Log.log("Quitting too late", Log.LogType.MAIN);
            return EmergencyQuit();
        }

        if(MyBot.turn > 20 && Map.staticMyShipCount < 4){
//            Log.log("Quitting too few ships", Log.LogType.MAIN);
            return EmergencyQuit();
        }
        if(MyBot.turn > 2  && Map.staticMyShipCount == 0) {
//            Log.log("Quitting opponent isnt building", Log.LogType.MAIN);
            return EmergencyQuit();
        }
        Tile buildDropoffOn = null;

        Competitor mirrorPlayer = null;

        if(MyBot.playerCount == 2){
            mirrorPlayer = MyBot.enemy1;
        }
        else{
            competitorloop:
            for(Competitor c: MyBot.players){
                if(!c.isMe){
                    for(DropPoint d : c.dropoffs){
                        if(d.isYard){
                            for(DropPoint d2 : Map.myDropoffs){
                                if(d2.tile.equals(d.tile.GetReflected())){
                                    mirrorPlayer = c;
                                    break competitorloop;
                                }
                            }
                        }
                    }
                }
            }
            if(mirrorPlayer == null){
//                Log.log("Quitting because no enemy found ???", Log.LogType.MAIN);
                return EmergencyQuit();
            }
        }


        if(mirrorPlayer.dropoffs.size() > Map.myDropoffs.size()){
            for(DropPoint d : mirrorPlayer.dropoffs){
                boolean found = false;
                for(DropPoint d2 : Map.myDropoffs){
                    if(d.tile.equals(d2.tile.GetReflected())){
                        found = true;
                        break;
                    }
                }

                if(!found){
                    buildDropoffOn = d.tile.GetReflected();
                    break;
                }
            }


        }

        int availableMoney = Game.me.halite;

         Plan p =  new Plan(Map.currentMap,true);
        for(CheapShip s : Map.staticMyShips){
            Tile oldTile = s.GetTile();
            Tile reflectedOld = oldTile.GetReflected();
            CheapShip eShipLastTurn = Map.staticShipsMapLastTurn[reflectedOld.x][reflectedOld.y];
            if(eShipLastTurn == null){
//                Log.log("Quitting cant find buddy", Log.LogType.MAIN);
                return EmergencyQuit();
            }
            Tile nextTile = null;
            for(Tile t : oldTile.GetReflected().neighboursAndSelf){
                if(Map.staticShipsMap[t.x][t.y] != null && Map.staticShipsMap[t.x][t.y].id == eShipLastTurn.id){
                    nextTile = t.GetReflected();
                    break;
                }
            }


            if(nextTile == null){
                if(oldTile.equals(buildDropoffOn)){
                    if(Game.me.halite + oldTile.haliteStartTurn + s.halite >= Constants.DROPOFF_COST){
                        TurnIntoDropoff = s.id;
                        availableMoney -=  (Constants.DROPOFF_COST - (oldTile.haliteStartTurn + s.halite));
                    }else{
//                        Log.log("Quitting dropoff too expensive", Log.LogType.MAIN);

                        return EmergencyQuit();
                    }
                }else {
//                    Log.log("Quitting no next tile found", Log.LogType.MAIN);

                    return EmergencyQuit();
                }
            }
            else{
                if(nextTile.DistManhattan(oldTile) > 1){
//                    Log.log("Quitting teleportationg", Log.LogType.MAIN);

                    return EmergencyQuit();

                }
                if(!oldTile.equals(nextTile)){
                    if(!s.CanMove(Map.currentMap)){
//                        Log.log("Quitting too expensive to walk", Log.LogType.MAIN);

                        return EmergencyQuit();

                    }
                }
                Move m = new Move(oldTile,nextTile,s);
                m.IgnoreInEval = true;
                p.SetMyMove(0,m);
            }


        }

        int trueEnemyCount = mirrorPlayer.shipCount;
        if(buildDropoffOn != null){
            trueEnemyCount++;
        }
        if(trueEnemyCount > Map.staticMyShipCount){
            if(availableMoney >= Constants.SHIP_COST) {
                DoWeBuildShipThisTurn = true;
            }
            else{
//                Log.log("Quitting cant build ship", Log.LogType.MAIN);

                return EmergencyQuit();
            }
        }

        return p;
    }


    public static Plan EmergencyQuit(){
        StillViable = false;
        DoWeBuildShipThisTurn = false;
        TurnIntoDropoff = -1;
        return null;
    }

}
