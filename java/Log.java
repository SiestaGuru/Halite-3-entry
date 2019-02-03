
import hlt.Game;

import java.io.FileWriter;
import java.io.IOException;

//Allows logging strings to different files

public class Log {
    public enum LogType{


        MAIN(0,"Main",true),
        EXCEPTIONS(1,"ExceptionDetails",true),
        INFO(2,"DetailedInfo",false),
        PLANS(3,"FuturePlans",true),
        TEMP(4,"Temp",false),
        MOVES(5,"Moves",false),
        TIMING(6,"Timing",true),
        TESTS(7,"Tests",false),
        ANALYSIS(8,"Analysis",true),
        COMPETITORS(9,"Competitors",false),
        OUTPUT(10,"Output",true),
        PREDICTION(11,"Prediction",true),
        IMAGING(12,"Imaging",false),
        AUTOTRAINING(13,"AutoTraining",false),
        AUTOTRAINING_SUMMARY(14,"TrainSummary",false),
        ;

        public String name;
        public int id;
        public boolean active;

        LogType( int id, String name, boolean active ){
            this.name = name;
            this.id = id;
            this.active = active;
        }
    }

    public final FileWriter file;
    private static int logcount = LogType.values().length;
    private static LogType[] types = new LogType[logcount];
    public static Log[] logs = new Log[logcount];




    public static boolean allowLogging; //will be set in mybot



    static {
        if(allowLogging) {
            Runtime.getRuntime().addShutdownHook(new AtExit());
        }
    }

    private static class AtExit extends Thread {
        @Override
        public void run() {
            flushLogs();
        }
    }

    private Log(final FileWriter f) {
        file = f;
    }

    static long time = System.currentTimeMillis();

    static void open() {
        for (LogType l : LogType.values()) {
            types[l.id] = l;
        }

        for(int i = 0; i < logcount; i++){
            openFile(types[i]);
        }
    }


    public static void openFile(LogType type){
        if(allowLogging && type.active) {
            String filename;
            if(type == LogType.ANALYSIS){
                filename = "Analysis/" + time + " - " + Game.myId.id + ".log";
            }
            else if(type == LogType.OUTPUT){
                filename = "GameOutput/" + time + " - " + Game.myId.id + ".log";
            }
            else if(type == LogType.AUTOTRAINING){
                filename = "AutoTraining/" + time + " - " + Game.myId.id + ".log";
            }
            else{
                filename = "logs/bot-" + Game.myId.id + "/" + time + " " + type.name + ".log";
            }

            try {
                FileWriter writer = new FileWriter(filename,true);

                logs[type.id] = new Log(writer);

            } catch (Exception ex) {
                //System.out.println("Logger error");
                ex.printStackTrace();

                allowLogging = false;
                return;
            }
        }
    }

    public static void log(final String message) {
        log(message,LogType.TEMP);
    }
    public static void log(final String message, LogType logtype) {
        if(allowLogging && logtype.active) {
            try {
                logs[logtype.id].file.append(message).append("\r\n");
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void log(final int message) {
        log(message,LogType.TEMP);
    }
    public static void log(final int message, LogType logtype) {
        if(allowLogging && logtype.active) {
            try {
                logs[logtype.id].file.append(message + "\r\n");
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
    }





    public static void flushLogs(){
        if(allowLogging) {
            for (int i = 0; i < logcount; i++) {
                if (types[i].active && logs[i] != null) {
                    try {
                        logs[i].file.flush();
                    } catch (final IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }


    private static int exceptionId = 0;

    public static void exception(Throwable ex) throws Throwable {
        exception(ex,LogType.EXCEPTIONS);
    }
    public static void exception(Throwable ex,LogType loc) throws Throwable{
        if(allowLogging) {
            log(exceptionId + ": " + ex.toString(), LogType.MAIN);


            String exString = exceptionId + ": " + ex.toString() + "  ";
            log(exceptionId + ": ", loc);
            log(ex.toString(), loc);

            StackTraceElement[] stacktrace = ex.getStackTrace();
            for (int i = 0; i < stacktrace.length; i++) {
                String s = stacktrace[i].getFileName() + " - " + stacktrace[i].getMethodName() + " line: " + stacktrace[i].getLineNumber();
                log(s, loc);
                exString += s;
            }

            if(MyBot.SERVER_RELEASE && !MyBot.FINALS_RELEASE){
                System.err.println(exString);

                System.out.println(exString);
                throw ex;
            }


            log("", loc);
            log("", loc);


            exceptionId++;
        }
    }



}
