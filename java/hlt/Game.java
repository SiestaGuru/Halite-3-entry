package hlt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class Game {
    public int turnNumber;
    public static PlayerId myId;
    public static final ArrayList<Player> players = new ArrayList<>();
    public static Player me;

    public static final Map<Integer, Ship> ships = new LinkedHashMap<>();


    // public final GameMap gameMap;

    public Game() {

        try {

//            Log.open(3);
//            Log.log("hiya!");
//            Log.flushLogs();

            //System.out.println(System.in.read());

           // System.out.println("nln");


            Constants.populateConstants(Input.readLine());

          //  System.out.println("afbvc");


         //   System.out.println("after reads");


            final Input input = Input.readInput();
            final int numPlayers = input.getInt();
            myId = new PlayerId(input.getInt());

           // System.out.println("before log");



          //  System.out.println("after log");

            for (int i = 0; i < numPlayers; ++i) {
                players.add(Player._generate());
            }
            me = players.get(myId.id);
            me.isMe = true;
            //gameMap = GameMap._generate();
            //Map.currentMap = Map.GenerateFirstMap();


        }catch (Exception ex){
            System.out.println(ex.toString());
            //ex.printStackTrace();
        }
    }

    public void ready(final String name) {
        System.out.println(name);
    }

    public void updateFrame() {




        ships.clear();

        for (int i = 0; i < players.size(); ++i) {
            final Input input = Input.readInput();

            final PlayerId currentPlayerId = new PlayerId(input.getInt());
            final int numShips = input.getInt();
            final int numDropoffs = input.getInt();
            final int halite = input.getInt();

            players.get(currentPlayerId.id)._update(numShips, numDropoffs, halite);
        }

        //gameMap._update();
//        Map.currentMap = new Map(true);

//        for (final Player player : players) {
//            for (final Ship ship : player.ships.values()) {
//                gameMap.at(ship).markUnsafe(ship);
//            }
//
//            gameMap.at(player.shipyard).structure = player.shipyard;
//
//            for (final Dropoff dropoff : player.myDropoffs.values()) {
//                gameMap.at(dropoff).structure = dropoff;
//            }
//        }
    }

    public void endTurn(final Collection<Command> commands) {

        StringBuilder bigstring = new StringBuilder();

        for (final Command command : commands) {
            bigstring.append(command.command).append(' ');
        }
        System.out.println(bigstring.toString());



//        for (final Command command : commands) {
//            System.out.print(command.command);
//            System.out.print(' ');
//        }
//        System.out.println();
    }
}
