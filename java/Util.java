//Just a couple of functions that made little sense elsewhere
public class Util {

    //Returns values in range 0-1 corresponding to its position relative to min and max ( val<=min returns 0, val=>max returns 1)
    public static float LinearCurve(float val, float min, float max){
        val =  (val - min) / (max - min);
        return Clamp(val,0,1);
    }

    public static float ReverseLinearCurve(float val, float min, float max){
        val =  1f - ((val - min) / (max - min));
        return Clamp(val,0,1);
    }


    //Returns values in range 0-1
    //Just an exponential curve from x=0 to x=1, normalized for min/max
    public static float ExponentialCurve(float val, float min, float max){
        val =  (val - min) / (max - min);
        return Clamp(val * val,0,1);
    }

    //Returns values in range 0-1
    //The reverse of exponential curve (returns 1 if val=min, 0 if val=max)
    public static float ReverseExponentialCurve(float val, float min, float max){
        val =  1f - ( (val - min) / (max - min));
        return Clamp(val * val,0,1);
    }


    //Returns values in range 0-1
    //Just an sqrt curve from x=0 to x=1, normalized for min/max
    public static float SqrtCurve(float val, float min, float max){
        val =  (val - min) / (max - min);

        val = Math.max(val,0.0001f); //Prevent weird nan things
        return Clamp((float)Math.sqrt(val),0,1);
    }

    //Returns values in range 0-1
    //Just an sqrt curve from x=0 to x=1, normalized for min/max
    public static float ReverseSqrtCurve(float val, float min, float max){
        val =  1f - ((val - min) / (max - min));

        val = Math.max(val,0.0001f); //Prevent weird nan things
        return Clamp((float)Math.sqrt(val),0,1);
    }

    //Returns values in range 0-1
    //Results in a curve that has a 'plateau' towards max, and a steeply declining value towards min.
    //https://www.wolframalpha.com/input/?i=(log(+(+(x+-+0)+%2F+(7-+0)))+%2B+4)+%2F4++++++++++++++++++++from+x%3D0+to+x%3D7
    public static float LogCurve(float val, float min, float max){
        double dval =  ( (val - min) / (max - min));

        dval = Math.max(dval,0.0001f); //Prevent weird nan things

        return Clamp(((float)Math.log(dval) + 4f) / 4f,0,1);
    }

    //Returns values in range 0-1
    //Results in a curve that has a 'plateau' towards min, and a steeply declining value towards max.
    // https://www.wolframalpha.com/input/?i=(log(1+-+(+(x+-+0)+%2F+(7-+0)))+%2B+4)+%2F4++++++++++++++++++++from+x%3D0+to+x%3D7
    public static float ReverseLogCurve(float val, float min, float max){
        double dval =  1f - ( (val - min) / (max - min));

        dval = Math.max(dval,0.0001f); //Prevent weird nan things

        return Clamp(((float)Math.log(dval) + 4f) / 4f,0,1);
    }

    public static double Clamp(double val, double min, double max){
        return Math.min(max,Math.max(min,val));
    }

    public static float Clamp(float val, float min, float max){
        return Math.min(max,Math.max(min,val));
    }
    public static int Clamp(int val, int min, int max){
        return Math.min(max,Math.max(min,val));
    }
    public static short Clamp(short val, short min, short max){
        return (short)Math.min(max,Math.max(min,val));
    }
}
