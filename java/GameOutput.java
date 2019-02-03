import hlt.Constants;

import java.util.ArrayList;

//Output some data for my custom replay viewer

public class GameOutput {

    public static ArrayList<String> info = new ArrayList<>();


    public static void OutputInitialGameState(){
        String output = "";

        output += "size:" + Map.width;
        output += "\nplayers:" + MyBot.playerCount;
        output += "\nduration:" + Constants.MAX_TURNS;

        Output(output,0);

    }

    public static void Clear(){
        info.clear();
    }

    public static void OutputGameState(){




        StringBuilder s = new StringBuilder();

        s.append("t:" + MyBot.turn);
        s.append("\nhalite:");
        for(int y = 0; y < Map.height; y++){
            for(int x = 0; x < Map.width; x++){
                s.append(Map.staticHaliteMap[x][y] + ",");
            }
            s.append(";");
        }

        s.append("\nships:");
        for(CheapShip ship : Map.staticAllShips){

            String goal;
            if(ship.id < Plan.goals.length) {
                Tile t = Plan.goals[ship.id];
                if (t != null) {
                    goal = "," + t.x + "," + t.y + "," + Goals.desire[ship.id] + ";";
                } else {
                    goal = ",-1,-1,-1;";
                }
            }else {
                goal = ",-1,-1,-1;";
            }

            s.append(ship.id + "," + ship.x + "," + ship.y + "," + ship.halite + "," +  Map.OwnerOfShip[ship.id] + goal);
        }


        for(String str : info){
            s.append("\r\n" + str);
        }

        Output(s.toString(),0);

    }

    public static void Output(String s, int attempt){
        try {
            Log.openFile(Log.LogType.OUTPUT);
            Log.log(s, Log.LogType.OUTPUT);
            Log.logs[Log.LogType.OUTPUT.id].file.flush();
            Log.logs[Log.LogType.OUTPUT.id].file.close();
            Log.logs[Log.LogType.OUTPUT.id] = null;
        }catch (Exception ex){

            if(attempt < 50){
                Output(s,attempt + 1);
            }else{
                Log.log("Couldnt write to file, in use", Log.LogType.MAIN);
            }

        }


    }




}
