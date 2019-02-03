//For easy performance tracking
public class Stopwatch {

    private static long StopWatchNoChannel = 0;
    private static final long[] StopWatchChannels = new long[2000];
    private static final long[] AccumulatedCount = new long[2000];
    private static final long[] timesHit = new long[2000];

    private static final long[] totalSum = new long[2000];

    public static void Start(int channel){
        if(MyBot.SERVER_RELEASE)return;
        StopWatchChannels[channel] = System.nanoTime();

    }

    public static void Stop(int channel,String logAs){
        if(MyBot.SERVER_RELEASE)return;

        long dif = System.nanoTime() - StopWatchChannels[channel];
        Log.log(logAs + ":  " + ((double)dif) / 1000000.0 +  " ms", Log.LogType.TIMING );
    }


    public static void Start(){
        if(MyBot.SERVER_RELEASE)return;

        StopWatchNoChannel = System.nanoTime();
    }

    public static void Stop(String logAs){
        if(MyBot.SERVER_RELEASE)return;

        long dif = System.nanoTime() - StopWatchNoChannel;
        Log.log(logAs + ":  " + ((double)dif) / 1000000.0 +  " ms" , Log.LogType.TIMING);
        StopWatchNoChannel = System.nanoTime();
    }


    public static void Begin(int channel){
        if(MyBot.SERVER_RELEASE)return;

        AccumulatedCount[channel] = 0;
        StopWatchChannels[channel] = System.nanoTime();
        timesHit[channel] = 0;
    }
    public static void StopAccumulate(int channel){
        if(MyBot.SERVER_RELEASE)return;

        long time = (System.nanoTime() - StopWatchChannels[channel]);
        AccumulatedCount[channel] +=  time;
        timesHit[channel]++;
        totalSum[channel] += time;

    }
    public static void PrintAccumulate(int channel,String logAs){
        if(MyBot.SERVER_RELEASE)return;

        double ms = ((double)AccumulatedCount[channel]) / 1000000.0;
        Log.log(logAs + ":  " + ms +  " ms   done: "  +  timesHit[channel]  +  "  avg ms: "  + ms / ((double)timesHit[channel])   + " total: " + totalSum[channel] , Log.LogType.TIMING);
    }

    public static void ErrorLogPrint(int channel, String logAs){
        if(MyBot.SERVER_RELEASE)return;

        if(MyBot.SERVER_RELEASE  && !MyBot.FINALS_RELEASE) {
            long time = (System.nanoTime() - StopWatchChannels[channel]);
            AccumulatedCount[channel] +=  time;
            timesHit[channel]++;
            totalSum[channel] += time;

            double ms = ((double) AccumulatedCount[channel]) / 1000000.0;
            System.err.println(logAs + ":  " + time/ 1000000.0 + " ms avg: " + ms / ((double) timesHit[channel]) + " total: " + totalSum[channel]);
        }else{
            StopAccumulate(channel);
            PrintAccumulate(channel,logAs);
        }
    }








}
