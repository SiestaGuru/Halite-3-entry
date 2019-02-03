
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.util.HashMap;


/*
This file contains all the magic values / parameters used throughout the code
Some of the parameters here have different values for playercount, map sizes or density. These are arrays instead of single variables.
A size 2 array deals with playercount, a size 4 array with density, a size 5 array with mapsize and a size 10 array with the combination of playercount and mapsize

In batch game runs, a selection of these is randomized. This creates rather different behavior for the bots present
At end of game, the randomized values are all written to file for further analysis using reflection (see AnalysisOutput)
along with important game statistics such as whether we won, how many points we got, how many ships we had by turn X, how many
inspire points, what the average collection rate was, etc.
Using this data, plus the randomized variables. I was able to quickly (hundreds of games) gather enough data to be able to
find meaningful correlations between variables and game success using an external stats analysis tool I wrote. This
tool would both analyze the data and do some basic stat analysis using different methods (slope, Pearson correlation, Spearman correlation,
Random Forest learning) as well as draw plots for me to analyze in more detail.
By combining different metrics such as: winchance, points, percentage of map turned into points, inspire gains, MU on server, average gather speed,
ships by turn 100 and ships lost  into success metrics, useful information about the optimal values for the parameters came rolling out.


There were significant issues with most of these metrics and methods. Some issues were:
- There's a lot of variance in halite. Detecting true correlations with lowish sample sizes was not easy
- The impact of most variables on most metrics was rather flat. Especially after maps started getting mined out completely
- Local improvements do not necessarily correlate with improvements on server
- Variables would sometimes have false correlation signals if I had changed them in batches that included actually impactful changes or if I happened to change them approximately at the same time as big code improvements
- Most variables do not have a monotonic relationship with success, but my analysis methods tended to assume there was such a relationship
- Some metrics used did not care about the points the enemy got, resulting in overly peaceful, cooperative behavior
- Some variables had so little of an impact on anything, they'd remain unchanged for ages
- The look elsewhere effect kept popping up. Frequently, completely non-functional variables would end up showing a high correlation with winchance etc.
- Since my games took very long to run, I was forced to use long-term data. This meant a lot of data was sourced from previous versions that worked differently and in which variables may've had different impacts
- Some metrics ended up being flawed, or had weird biases. "Points" was biased towards 4p 64x64 and made me too peaceful,  "halite per turn per ship" encouraged underspawning so much that it ended up being inversely correlated with actual success
- Since most of my variables were randomized during a run, the impact of any single variable changing was reduced. It was always likely another change was more dominant

These kinds of issues meant that what my stats tool said should be an improvement wasn't necessarily one, but they were good enough that they were right more often than not.
By just updating parameters in batches of 20-30, it ended up pretty likely that the entire batch ended up as an improvement, even if some individual tweaks may not have been.
Cycling through metrics also helped a ton, with different metrics being more/less vulnerable to the issues, none of them ended up being structural
problems that completely prevented meaningful improvements.
I came up with (partial) solutions to some of the problems too. For example, I would change the names of variables who's meaning significantly changed
to help prevent useless old data from impacting decision making. For many metrics, I also mainly used a version adjusted for time to help deal
with the impact of changes in my program

Despite all these problems, I found this approach of tuning parameters to be worth it.
Cycling between metrics and doing careful analysis of the graphs myself prevented these kinds of issues from becoming structurally impossible to deal with.
I managed to squeeze out improvements throughout the entire tournament, and credit these tweaks with about half my mu gains.
The main problem ended up being that it simply took too much time to tune everything well, both in terms of running time, and because it was quite labor intensive.

I considered doing parameter tuning automatically, with some form of genetic algorithm to make it a little less labor intensive,
but the game was too varied, my games ran too slowly and there were too many weird issues to really be able to meaningful trustworthy data out of such efforts.
By focusing on longterm data collection and analysis, I was also able to learn other interesting things from my data that helped out a lot. For example in what ways low density maps impact everything
and how they should be handled with (it's better to have: less dropoffs, less focus on inspire and banding together, more focus on raw mining and ship safety).
What also helped a lot was that I could use parameters to determine which algorithms to use, and could then easily determine which algorithms were actually the best.
 */


public class HandwavyWeights {



    public static int PLAN_STYLE = Plan.STYLE_RANDOMIZE;

    public static float IndividualMoveScore = 2.3f;//2.2f;//2.0f;// 1.5f;//0.95f;//0.9f;//0.86f;//0.9f;//0.887219091339754f;//0.9f;//0.7f;//0.55f;//0.62f;//0.59f;//0.57f;//0.6f;//0.62f;//0.68f;//0.7f;//0.9f;//0.85f;//0.95f;//1f;//1.05f;//1.03f;//0.96f;

    public static float GatherScore =  1.6f;//1.5f;//1.15f;//1.08937636616112f;//1.05f;//1.03f;//1.08f;//1.12f;//1.22f;//1.3f;//1.2f;//1f;//0.95f;//1f;//1.05f;//1.1f;//1.05f;//1.1f;//1.05f;//1.0f;//1.05f;//1.1f;//1.0f;//0.9f;//0.95f;//1f;
    public static float WastePreventionScore = 0.39f;//0.41f;//0.43f;//0.45f;//0.5f;//0.6f;//0.7f;//0.9f;//0.95f;//0.980089849356618f;//1.3f;//1.38f;//1.43f;//1.45f;//1.43f;//1.47f;//1.45f;//1.4f;//1.35f;//1.3f;//1.25f;//1.22f;//1.2f;//1.25f;//1.4f;//1.35f;//1.2f;//1.12f;//1.1f;//1f;//0.94f;//0.92f;// 1f;
    public static float MiscScore = 1.4f;//1.25f;//1.18f;//1.12f;//1.05f;//1.02680195823443f;//1f;//1f;//0.9f;//0.85f;//0.89f;//0.85f;//0.75f;// 0.67f;//0.73f;//0.75f;//0.8f;//1f;
    public static float TurnInScore = 0.96f;//0.92f;//0.95f;//1f;//0.9f;//0.85f;//0.78f;//0.7f;//0.65f;//0.7f;//0.750467994099265f;//1f;//1.09f;//1.06f;//1.03f;//0.95f;//1f;//1.1f;//1.3f;//1.42f;//1.4f;//1.2f;//1.3f;//1.2f;// 1.15f;//1.05f;
    public static float LuresScore = 1.05f;//1.1f;//1.35304319413129f;//1.45f;//1.37f;//1.33f;//1.32f;//1.25f;//1.35f;//1.4f;//1.45f;//1.5f;//1.43f;//1.45f;//1.4f;//1.35f;//1.3f;//1.1f;//1.05f;//1.1f;//1.25f;//1.33f;//1.2f;//1f;
    public static float ShipHaliteScore = 1.85f;//1.75f;//1.79540147614181f;//1.8f;//4.31491852591244f;//2.05f;//2.0f;//1.8f;//1.55f;// 1.5f;//1.46f;//1.43f;//1.38f;//1.32f;//1.25f;//1.3f;//1.2f;//1f;
    public static float metaNewInspireStuff = 1.4f;//1.7f;//2f;// 2.2f;//2.5f;//3f;//0.7f;
    public static float InspireScore =  0.9f;//0.55f;//0.585649208888831f;//0.5f;//2.31716356449201f;//0.83f;//0.88f;//0.9f;//0.92f;//0.95f;//1.0f;



    public static float EVALRANDOMNESS = 20f;//12f;//10f;//6f;//5f;//2.5//1;
   // public static float  MoneyBurntV2 = 0.07f;//0.15f;//0.3f
    public static float MoneyBurntV2 = 0.25f;//0.3f;//0.35f;//0.45f;//0.5f;//0.6f;//0.507816332078773f;//0.55f;//0.6f;//0.8f;//0.481604475975811f;//1.1f;//1f;//1.5f;//1.45f;//1.4f;//1.6f;//1.3f;//1.1f;//1f;//0.95f;//1f;//
   // public static int ThreshLure = 900;//870;//800;//820;//850;//800;//600; //700
    //public static float LureWeight = 0.18f;//0.15f;//0.06f;//0.035f;//0.02f;

    public static float Gather1V2 = 1.3f;//1.35f;//1.4f;// 1.6f;//1.9f;// 2.6f;//2.8f;//2.1f;//1.8f;//2.0f;//2.8f;//2.4f;//2.0f;//1.8f;//1.55f;//1.47f;//1.45f;//1.37f;//1.4f; //1.0f
    public static float Gather2V2 = 1.02f;//0.95f;//0.9f;
    public static float Gather3V2 = 0.75f;//0.8f;//0.85f;//0.75f;//0.7f;
    public static float Gather4V2 = 0.2f;//0.25f;//0.5f;
    public static float Gather5V2 = 0.13f;//0.15f;//0.3f;
    public static float GatherTotalV3 = 6.0f;//5.8f;//5.5f;//5f;//4.8f;//4.6f;//4.8f;//4.7f;//3.6f;//3.4f;//3.2f;//2.5f;//3.6f;//3.5f;//2.7f;//3f;//1.5f;//1.0f;//0.55f;//0.6f;//0.9f;//1.2f;//0.4f;//0.15f;//0.1f;// 0.25f;

    public static float RunLeftFactor = 1.3f;
    public static int RunLeftTimerV3 = 16;//15;//14;//16;//15;//13;//12;//11;//10;//8;//7;//6;
    public static int RunLeftTimerEnemyV2 = 16;//12;//8;//7;//6;
    public static int RunMinimumHaliteV2 = 30;//35;
    public static float RunCurV2 = 270f;//290f;//300f; //500f
    public static float RunEnemyCurV2 = 10000f;//240f;//260f; //500f

    public static float Importance1V3 = 0.45f;///0.5f;//0.55f;//0.62f;//0.75f;//0.8f;//0.86f;// 1.05f;//1.1f;//1.15f;//1.25f;//1.3f;//1.2f;//1.4f;//1.6f;//1.4f;//1.3f;//1f;//1.25f;//1.3f;//1.4f;//1.2f;//1.3f;
    public static float Importance2V3 = 0.45f;//0.5f;//0.49f;//0.52f;//0.65f;//0.7f;//0.85f;//0.9f;//1f;// 0.95f;//0.9f;//0.7f;//0.87f;//0.93f;//0.95f;//0.9f;//0.8f;
    public static float Importance3V3 = 0.45f;//0.5f;//0.54f;//0.52f;//0.45f;//0.4f;//0.35f;//0.26f;//0.3f;
    public static float Importance4V3 = 0.1f;//0.13f;//0.15f;//0.23f;//0.25f;//0.28f;//0.25f;//0.2f;//0.13f;//0.1f;
    public static float Importance5V3 = 0.015f;//0.02f;
   // public static float ImportanceOverallV3 =  0.02f;//0.015f;

    public static float PunishStandstillNoHaliteEmptyishV2 = 0;//150;//170;//150;//100;//200;//400;//800;
    public static float PunishStandstillLowHaliteEmptyishV2 = 0;//25;//100;//170;//20;
    public static float PunishStandstillNoHaliteNormalV2 = 0;//34;//30//40;//50;//110;//200;//400;//800;
    public static float PunishStandstillLowHaliteNormalV2 = 0;//35;//40;//100;//170;//20;

    public static float PunishStandstillNoHaliteFullishV2 = 120f;//105f;//85f;//80f;//90f;//105;//95;//90;//5;//80;//110;//200;//400;//800; //This is probably the best one of the bunch. Maybe reactivate this
    public static float PunishStandstillLowHaliteFullishV2 = 0f;//25f;//60;//25;//160;//100;//170;//20;

    public static int EmptyishStandstillV2 = 110;
    public static int FullishStandstillV2 = 840;//820;//800;//750;//700;//750;//800;


    public static int BigTileV3 = 65;//60;//70;//80;//100;// 120;//140;//80;
    public static float ExtraOnBigTileV3 = 0f;//85f;//60f;//55f;//65f;//55f;//45f;//30f;//25f;//17;//5;
    public static float HaliteBigTileMultiplierV4 = 0.1f;//1.4f;//1.45f;//1.7f;//1.5f;//1.4f;//0.9f;//0.7f;//0.5f;//0.95f;//1.2f;//0.7f;//0.6f;//0.35f;//0.1f;

    public static float HaliteMultiplierMoveTo = 1.7f;//1.8f;//2.00587278f;//1.9f;// 1.75f;//1.65f;//1.74423718261229f;//1.5f;//2.5f;//2.2f;//2.3f;//2.6f;//2.7f;//2.75f;//2.5f;//3f;//2.25f;//2.15f;//2f;//1.05f;//1.0f;//1.1f;//0.64f;//0.49f;//0.005f;
    public static float HaliteMultiplierMoveToNoImp =  0.03f;//0.033f;//0.03f;//0.02f;//0.018f;//0.017f;//0.015f;//0.01f;//0.07f;// 0.1f;//0.12f;//0.05f;//0.15 0.1f;//0.4f;//0.5f;//2.25f;//2.15f;//2f;//1.05f;//1.0f;//1.1f;//0.64f;//0.49f;//0.005f;
    public static float HaliteMultiplierStandStillV2 = 2.6f;//2.7f;//2.8f;//2.9f;//3.2f;//3.73259175739224f;//3.4f;//3.5f;//3.6f;//3.71615569698043f;//3.6f;//4.84153341252461f;//4f;//5.0f;//4.8f;//4.8f;//3.2f;//0.005f;
    public static float HaliteMovepunisherV2 = 0f;//0.8f;//0.9f;//1.3f;//0.65f;//0.8f;//0.75f;//0.6f;//0.5f;// 0.43f;//0.37f;//0.27f;//0.23f;//0.12f;//0.1f;//0.005f;
    public static float HaliteMovepunisherNoImpV2 =  3.5f;//3.4f;//3.2f;//3.1f;//2.9f;//3.10552970697506f;//3.0f;//3.3f;//3.1f;//2.99819994010674f;//3f;//2.7f;//2.4f;//2.25f;//2.2f;//2.1f;//2.0f;//1.90f;//1.5f;//2.05f;//2.2f;// 1.9f;// 1.7f;//1.6f;//1.4f;// 0.5f;//0.034f;// 0.05f;
    public static float FlatMovePunisherV2 = 0f;//26f;//24f;//20f;//15f;//25f;//26f;//24f;//22f;//20f;//25f;
    public static float FlatMovePunisherNoImp = 0f;//10f;//15f;//17f;//20f;//22f;// 25f;//30f;//25f;//45f;//40;//27f;//25f;//18f;
    public static float PunishBackAndForth =  0f;//40f;//90f;//100f;
    public static float PunishBackAndForthTImportance =  0f;//80f;//100f;

    public static float AboveHpsFactor = 1.15f;//1.25f;//1.3f;//1.35f;
    public static float AboveHpsBonusV2 = 25f;//35f;//40f;//32f;//25f;
    public static float AboveHaliteAvgBonus = 100f;//85f;//80f;//50f;//20f;
    public static float AboveMeaningfulHaliteStandstillBonus = 0f;//10f;//22f;//24f;//22f;//20f;// 2f;//15f;//17f;//22f;//24f;//25f;//25f;//22f;//21f;//14f;//25f;
    public static float RelativeHaliteAvgFactor = 0f;//41f;//44f;//47f;//50f;


    //TODO: might want to delete this functionality, doesnt seem the stats like it
//    public static float ProbableAttemptsPunisher = 2f;//10f;//20f;//30f;
//    public static float ProbableAttemptsBoostIfFew = 5f;//15f;//27f;//35f;//30f;//40f;


    public static int EmptyishV2 = 440;//380;//360;//240;//300;

    public static float MeaningfulHaliteEmptyishV2 =  0f;//3f;//5f;//10f;//22.018462315364f;//16f;//22f;//25.3716607731207f;//27.438432948572f;//82f;//75f;//70f;//60f;//140f;//120f;//110f;//70f;//43f;//40f;//35f;//30f;//40f;//55f;//35f;//15f;//25f;//20f;//14f;//11f;//9f;//7f;
    //public static float MeaningfulHaliteV3 = 14f;//15f;//7f;//4.2f;//3f;//0.8f;

    public static float MeaningfulHaliteV3 = 45f;//50f;//55f;//45f;//40f;//50f;//15f;//7f;//4.2f;//3f;//0.8f;
    public static float MeaningfulHalitePOW = 0.99f;//1.15f;//1.1f;//1f;
    public static float MeaningfulHaliteBASE = 260f;//230f;//240f;//250f;//300f;
    public static float MeaningfulHaliteMin = 0.6f;//0.55f;//0.53f;//0.5f;


    public static float StepOnDropoffV2 = 0.9f;//0.95f;//1.0f;//1.1f;//1.25f;//1.3f;//1.35f;//1.3f;//1.2f;//1.1f;//0.9f;//1.1f;//1.5f;//2.3f;//1.8f;//1f;//0.4f;
    public static float StepOnDropoffHalProbablyFine = 1.5f;//1.3f;
    public static float StepOnDropoffFlatV2 = 600f;//350f;//250f;
    public static float AvoidDropoffV2 = -3600;//-3300;//-3000;//-3500;//-2700;//-2400f;//-2100f;//-1300f;//-1000f;
    public static float AvoidDropoffZero = -6500f;//-6800f;//-6500;//-5000;
    public static float AvoidDropoffProbablyFine = -50f;//-6500;//-5000;
    public static float StepOnDropoffIfWantToBuild = -4400f;//-4200f;//-4000f;

    public static int MinHaliteTurnIn = 130;//125;// 130;//135;//140;//145;//165;//140;//120;//110;// 100;

    public static float ShipHalite = 5.3f;//5.5f;//5f;//4.7f;//4.6f;//4.7f;//4.5f;//4.3f;//4.1f;//3.8f;//4.0f;//2.8f;// 1.6f;//1.4f;//1.2f; //1.0f;




    //public static float PlayerHalite = 1.6f;//2.0f;
//    public static float PlayerHaliteV2 = 1.6f;//2.0f;

    public static float PlayerHaliteUnBound =  0.23f;//0.17f;//0.2f;//0.259747446984017f;//0.230755579984102f;//0.1f;//0f;
    public static float PlayerHaliteEarlyV3 = 2.2f;//2.3f;
    public static float PlayerHaliteMediumV3 = 2.1f;//1.9f;
    public static float PlayerHaliteLateV3 = 2f;//2.2f;//2.4f;//2.2f;//2.1f;//1.9f;//1.7f;
    public static float PlayerHaliteVeryLateV3 = 1.6f;//1.65f;//1.6f;//1.7f;
    public static float FinalShipHaliteV3 = 2.7f;//2.8f;// 2.6f;//2.2f;//1.9f;//2f;//1.8f;//1.7f;//1.5f;//1.3f;
    public static float FinalPlayerHaliteV3 = 0.23f;//0.29f;//0.32f;//0.35f;//0.4f;//0.65f;// 0.8f;//1.6f;//1.5f


    public static float BoostFor1000Early = 330f;//50f;//400f;//500f;//750f;//500f;// 1000f;//1150f;//1200f;//1400f;//800f;//600f;//420f;//400f;//250f;//50f;//100f;//90f;//80f; //the escalation is real
    public static float BoostFor1000 = 100f;//200f;//400f;//500f;//1200f;//1150f;//1200f;//1400f;//800f;//600f;//420f;//400f;//250f;//50f;//100f;//90f;//80f; //the escalation is real

    public static float PrioBoostNextToDropoffV2 = 650;//500;//270;//400;
    public static int PrioNextToEnemy = 100;//105;//100;//95;//90;//100;//120;//70;
    public static float PrioWeightHalite = 1.8f;//1.7f;//1.75f;//1.8f;//1.9f;//2.2f;//2.1f;//1.9f;//1.5f;//1.4f;//1.2f;//0.7f;//0.4f;
    public static float PrioWeightTileHalite = 0.1f;//0.2f;//0.4f;//1.7f;
    public static float PrioNearbyMyShips = 5f;
    public static float PrioNearbyEnemyShips = 5f;

    public static float FinalStandOnHaliteV2 = 0.45f;//0.35f;//0.32f;//0.24f;//0.18f;//0.16f;//0.14f;//0.11f;//0.12f;//0.13f;//0.2f;//0.04f;//0.05f;
    public static float FinalHaliteNearDropExceeds10V3 = 3.9f;//3.7f;//4.2f;//4.5f;//5f;
    public static float FinalHaliteDropCrossExceeds10V2 = 0f;//10f;

    public static int DEPTH_1 = 5;
    public static int DEPTH_2 = 5;
    public static int DEPTH_3 = 5;
    public static int DEPTH_4 = 5;
    public static int DEPTH_5 = 5;
    public static int DEPTH_6 = 5;
    public static int DEPTH_7 = 5;
    public static int DEPTH_8 = 4;
    public static int DEPTH_9 = 4;
    public static int DEPTH_10 = 4;//7;
    public static int DEPTH_11 = 4;//5;
    public static int DEPTH_12 = 3;//5
    public static int DEPTH_13 = 3;
    public static int DEPTH_14 = 3;

    public static int ELIM_FIRST_STAGE = 5;//14;//10;

    public static float ADVANCERANDOMNESS = 0.018f;//0.02f;//0.015f;// 0.009f;//0.0025f;//0.002f;//0.001f;
    public static float RANDOMNESSCYCLE = 0.13f;//0.15f;//0.12f;//0.08f;//0.1f;//0.05f;//0.02f;//0.01f;

    public static float TURNBASEDRANDFACTOR1 = 35f;//31f;//25f;//15f;//10f;//7f;
    public static float TURNBASEDRANDFACTOR2 = 9.5f;//8f;//4f;
    public static float TURNBASEDRANDFACTOR3 = 5.5f;//4.5f;//5f;//3f;
    public static float TURNBASEDRANDFACTOR4 = 5f;//4f;//3f;//2f;//1.5f;

    public static float BANODDS = 0.05f;//2f;//0.7f;//0.8f;

    public static float ADDDESIREODDS = 0.75f;//0.7f;//0.6f;//0.8f;
    public static float ADDDESIRE = 2.5f;//5.0f;
    public static float ADDDESIRERAND = 30f;// 28f;//16f;//10.0f;

    public static float SUGGESTIONSFROMLASTTURN = 40f;//25f;//12f;//20.0f;
    public static float CHANCEADDSUGGESTIONS = 0.3f;

    public static int PRIORAND = 360;//340;//200;

    public static float EVALEXTRARANDFACTOR = 1.6f;//1.2f;// 1.3f;
    public static float EVALEXTRARAND = 0.8f;

    public static int LureDistMod = 4;//13;

    public static float LongLureFlatHal = 1.25f;//1.1f;//1.2f;// 0.85f;// 0.3f;//0.5f;
    public static float LongLureDistHal = 1.15f;//1.1f;//0.7f;
    public static float LongLureSpread = 0.075f;//0.1f;//0.18f;//0.2f;  //CANT BE >= 0.2
    public static int LongLureSpreadTurns = 20;//15;//17;//27;//30;//29;//27;
    public static float[] LongLureTrustInspireV2 = new float[]{0.27f,1.02f};// 1f;//0.95f;//0.85f;//0.92f;//0.8f;//0.6f;
    public static float LongLureEdgeMapFactor = 3.1f;//2.8f;//2.5f;//2.7f;//2.9f;//3.1f;//3.3f;//3.1f;// 2.8f;//2.4f;//2f;//1.4f;//1f;
    public static float LongLureHalCenters = 0.45f;//0.5f;//0.55f;//0.6f;


    public static float MedLureFlatHal = 0.15f;//0.22f;//5f;
    public static float MedLureDistHal = 1.2f;//1.3f;//0.7f;//0.9f;//0.75f;//0.7f;
    public static float MedLureSpread = 0.17f;//0.15f;  //CANT BE >= 0.2
    public static int MedLureSpreadTurns = 10;//11;//13;//9;//10;//12;//13;//12;


    public static float DropoffBaseMultV2 =  0.7f;//0.632222744381416f;//0.6f;//0.1f;// 1.15f;//1.1f;//1.2f;//0.75f;
    public static int StopDropoffBuildingTurnsBeforeEnd = 90;//100;//150;//210;//190;// 150;
    public static float DropoffMapSizeScore = 52.5693610242709f;//55f;//45f;//25f;
    public static float DropoffPlayerCount = 0f;//8.23186863752653f;//13f;//15f;//20f;//22f;//27f;//25f;
    public static float DropoffFlatScore = 315.986642544518f;//320f;//330f;//270f;//
    public static float DropoffNoDropoffsMult = 1.85f;//1.75f;//1.65f;//1.58269573463003f;//1.03f;
    public static float DropoffOneDropoffMult = 1.2f;
    public static float DropoffNotEnoughHalite = 0.8f;
    public static float DropoffTooManyMultV2 = 0.35f;//0.15f;// 0.01f;//0.3f;
    public static float DropoffWayTooManyMultV2 = 0.00640207956752899f;//0.01f;// 0.01f;//0.3f;
    public static float DropoffWorstOpponentStr = 16f;//13.4351992769115f;//20f;//22f;//15f;
    public static float DropoffBestTileValueRatio = 14.6742148932588f;//12f;//15f;//23.5f;//23f;//22f;//15f;////9f;
    public static float DropoffSparseMapMult = 0.95f;//1.0f;//0.9f;//0.8f;//0.75f;
    public static float DropoffWorthMultV2 = 0.65f;//0.6f;//0.62f;//0.65f;//0.7f;// 0.6f;//1f;//0.7f;//1f;//1.05f;
    public static float DropoffShipsBelowVal =  80f;//70f;//63f;//60f;//52f;//49f;//46f;//42.9378987736959f;//30f;



    public static float MapHaliteLeftMinV3 = 36000f;//37000f;//42683.0218283543f;//65000f;//55000f;//50000f;//45000f;//20000f;//30000f;//45000f;//40000f;//36000f;//30000f;
//    public static float MyHpsFactor = 0.7f;//0.5f;
    public static float enemyHpsFactorV2 = 0.928110102701488f;//0.92f;//0.95f;////1.0f;//0.8f;//0.6f;//0.5f;

    public static float ProprtionMinEnemyNearish = 0.7f;
    public static float MinShipsInRegion = 0f;//1f;//2f;//4f;//3.5f;//4.5f;//4f;//4.5f;//5f;//6f;
    public static int MinStepsFromDropoff =  10;//11;//12;//13;//15;//15;//14;//11;//13;//14;//13;//11;//8;
    public static int MaxStepsFromDropoff = 64;// 33;//31;//29;//27;//24;//22;//20;//8;
    public static int MaxGainFromDistance = 10;//11;//12;// 18;//15;//8;
    public static int MaxGainFromDistance2 = 11;
    public static float DropoffWeightTooFarMult =  -70f;//-60f;//-50f;//-40f;//-25.6149222443946f;//-17f;
    public static float DropoffWeightContainsFriendlyShip = 0f;//50f;//583.601994946891f;//800;
    public static float DropoffWeightFriendlyShipHalite = 1.0f;//1.05f;//1.1f;//1.05f;//0.994706746376114f;//1.05f;//1;

    public static int MinStepsFromDropoffTiles = 11;//12;//13;//14;//23;//16;//23;// 19;//17;//19;//22;//20;//18;//17;//15;//14;
   // public static float DropoffBaseScore = 170f;//170f;//160f;//110f;//120f;

    public static float DropoffScoreTurnsFromEnemyNormalize = 303.883794087829f;//295f;//300f;
    public static float DropoffScoreTurnsFromEnemy = 13f;//11f;//10f;//9f;//8f;// 6f;//5f;//12f;//13f;//14f;//17f;//20f;
    public static float DropoffPunishmentCloseToEnemy = 720f;//700f;//650f;//630f;//578.444919354688f;//400f;
    public static int DropoffCloseToEnemy = 7;
    public static int DropoffCloseToEnemyTiles = 7;//8;//7;//9;


    public static float DropoffWeightShipHalNear = 1.89520712838463f;//1.8f;//1.8f;//1.2f;//1f;
    public static float DropoffWeightShipHalNearish = 0.53f;//0.55f;//0.515381491397247f;//0.42f;//0.45f;//0.4f;//0.3f;
    public static float DropoffWeightShipsNear = 118.267757530826f;//120f;//110f;//100f;
    public static float DropoffWeightShipsNearish = 77.5404505081144f;//75f;//55f;
    public static float DropoffWeightEnemyShipsNearish = 0f;
    public static float DropoffWeightDistV2 = 14.5268576469292f;//13.6f;//14f;//15f;//17f;//22f;//13f;//10f;
    public static float DropoffWeightMedLure = 2.439818657908f;//3.5f;//3f;//2.5f;//2.7f;//2.5f;//1.7f;//1.2f;
    public static float DropoffWeightLongLure = 5.45640658625986f;//5f;//3.7f;//4f;
    public static float DropoffWeightHalOnSpot = 3f;//3.7f;//3.5f;//2.5f;//1.5f;
    public static float DropoffWeightConsistentSelection = 677.912309934806f;//650f;//500f;
    public static float DropoffWeightHalLeftOnMap = 0.05f;//0.035f;//0.03f;//0.02f;//0.012f;
    public static float DropoffWeightHalNear = 0.289400918439621f;//0.25f;//0.15f;//0.13f;//0.1f;//0.07f;//0.05f;
    public static float DropoffWeightHalNearish =  0.07f;//0.06f;//0.055f;//0.04f;//0.03f;//0.0007f;//0.00585969504649395f;//0.005f;//0.01f;//0.023f;//0.015f;
    public static float DropoffMinSpotScore =  1300f;//1450f;//1500f;
    public static float DropoffMinHaliteNearSpot = 14000f;//13000f;//11000f;//9000f;//14000f;//15000f;//15500f;//16000f;//17000f;//16500f;//16000f;//15500f;//15000f;//14000f;//12000f;//18000f;//17000f;//15000f;//13000f;
    public static float DropoffMinHaliteNearSpotEarly = 15000f;//14500f;//14000f;//12000f;//10000f;//7000f;//15500f;//16000f;//17000f;//16500f;//16000f;//15500f;//15000f;//14000f;//12000f;//18000f;//17000f;//15000f;//13000f;
    public static float DropoffAbsoluteMin = 9000;//8500;//8000;//7700;//7366.87251753962f;//6000f;//7000f;//15500f;//16000f;//17000f;//16500f;//16000f;//15500f;//15000f;//14000f;//12000f;//18000f;//17000f;//15000f;//13000f;
    public static int DropoffAbsoluteMinShipsSavedTime = 3;
    public static int DropoffTooFewShips =  10;//11;//12;//13;//22;//21;//20;//22;
    public static float DropoffFactorIfTooFewShips = 0.498676299312271f;//0.5f;//0.4f;

    public static float DropoffWeightNearbyShipHalite =  0.038f;//0.04f;//0.045f;//0.05f;//0.0601754108389072f;//0.1f;
    public static float DropoffWeightDistanceSavings =  14f;//13f;//12f;//11f;//9f;//6.5f;//6.3f;//5.8999876190583f;//4f;//4.5f;//5f;//4f;//3f;
    public static int DropoffWeightMinDistanceSavings = 30;//28;//27;//34;//30;

    public static int DropoffminShips = 9;//10;//11;//12;//13;



    public static int DropoffNearTiles = 4;
    public static int DropoffNearishTiles = 7;//6;//7; //cant be higher than max supported tiles for nearbytiles

    public static float DropoffDenyMultiplier = 1f;
    public static float[] DropoffLowPrioZoneFlat = new float[]{-20f,-18f};
    public static float[] DropoffLowPrioZoneHalite = new float[]{-0.08f,-0.16f};
    public static float DropoffCentralFlat =  50f;
    public static float DropoffCentralHalite =  0.01f;


    public static float AlwaysAllowShipGameLength = 0.27f;//0.25f;//0.27f;//0.309268082125417f;//0.3f;//0.4f;//0.35f;//0.2f;//0.35f;//5f; //doesnt actually mean: always allow anymore. dont want to change name for statistics reasons though
    public static int StopTurnsBeforeEndMinShips = 150;
    public static int MinimalShips = 14;//15;//17;
    public static int MinHalLeftOnMapForShipV3 = 34000;//31389;//22000;//25000;//23000;//10000;//15000;//20000;
    public static int OverrideMaxAllowIfBelow = 4;//5;//6;//10;
    public static int OverrideTimeLimit = 1295;//1200;



    public static int AlwaysWorthShipCount = 14;//13;//16;//24;//18;//15;


    public static float PlayerHaliteT1V2 = 0.55f;//0.58f;//0.63f;//0.65f;//0.78f;//0.82f;//0.92f;//1.0f;// 0.8f;//0.6f;//0.55f;//0.4f;//5f;//0.95f;//0.92f;//0.86f;//0.8f;//0.6f;// 0.7f;//2.0f;
    public static float PlayerHaliteT2V2 = 0.55f;//0.58f;//0.53f;//0.47f;//0.45f;//0.52f;//0.5f;//0.45f;//0.4f;//2.0f;
    public static float PlayerHaliteT3V2 = 0.55f;//0.58f;//0.53f;//0.47f;//0.45f;//0.4f;//0.35f;//0.3f;//0.35f;//0.31f;//0.27f;//0.25f;//0.1f;//0.2f;//2.0f;
    public static float PlayerHaliteT4V2 = 0.33f;//0.35f;//0.4f;//0.35f;//0.3f;//0.22f;//0.25f;//2.0f;






    public static float Step0KillDesire2pV2 = 400f;//410f;//430f;//450f;//550f;//400f;// 250f;//0f;
    public static float Step0KillDesire2pControl = 85f;//90f;

    public static float Step0KillDesire4pV2 = -5500f;//-5000f;//-4500f;//-4000f;//-3000f;//-3500f;//-3000f;//-820f;//-700f;//-650f;//-700f;//-200f;
    public static float Step0KillDesireHalite2pV2 = 6.9f;//6.5f;//5.7f;//3f;//2.3f;//1.5f;
    public static float Step0KillDesireHalite4pV2 = 0.55f;//0.5f;//0.37f;//0.4f;
    public static float Step0AvoidRunUntoHal2p = 8f;//10f;
    public static float Step0AvoidRunUntoHal4p = 13f;//15f;//17f;//20f;
    public static float Step0AvoidCurEnemySpot = 5000f;//15f;//17f;//20f;
    public static float Step0KillTimeFlat = 2000f;
    public static float Step0KillTimeHalite = 10f;


    public static float EstGuaranteedCollect = 80f;
    public static float EstShipmoveRate = 0.13f;//0.1f;//0.07f;
    public static float TrustInInspirePredictionV3 = 1.3f;//1.2f;//1.1f;//1f;//0.77f;//0.8f;//0.92f;//0.95f;//1f;//1.05f;//0.95f;// 0.92f;//0.9f;
    public static float TrustInInspirePredictionNothingV2 =0.9f;// 0.6f;//0.82f;//0.85f;//0.9f;//0.85f;//0.8f;
    public static float TrustInInspirePredictionNothingNew = 0.8f;

    public static float MaxChanceStandstill = 0.6f;// 0.4f;//0.6f;
    public static float MinOdds = 0.1f;

    public static float DropoffRunDesire = 1050;//1100f;//700f;//1000f;//4500f;//3800f;//4000f;//3600f;//3500f;//2500f;//1700f;//1500f;//1300f;//1000f;



    public static int ShipHardCap = 105;//110;//150;

    public static float LongDistNerfValueAroundMyShips = 1.05f;//0.93f;//0.9f;//0.95f;//0.93f;//0.85f;//0.4f;
    public static int LongDistNerfRadiusV2 = 0;//1;
    public static int LongDistNerfRadiusEnemyV2 = 0;//1;


    public static float MedDistNerfValueAroundMyShips = 0.18f;//0.22f;//0.27f;//0.3f;//0.8f;
    public static float MedDistNerfValueAroundEnemyShips = 0.17f;//0.2f;
    public static int MedDistNerfRadius = 0;//2;

//    public static int TURNIN_THRESH = 710;//730;//740;//770;//760;
    public static int TURNIN_AFTER_THRESH = 270;// 250;//300;
    public static int DROP_DIST_CHECK = 8;//7;

    public static float behindIn2pWorthBuilding = 1.5f;//1.6f;//1.4f;//1.3f;
    public static int ShipsAheadToBeBehind = 1;


    //some of the floats here are really int-based, but this helps limiting conversions

    public static float DROP_DIST_CUR_TIER1_V3 = 450;//200;
    public static float DROP_DIST_CUR_MULT1_V3 = 0.11f;//0.09f;//0.06f;//0.045f;//0.04f;//0.025f;//0.03f;//0.01f;// 0.005f;
    public static float DROP_DIST_CUR_TIER2_V3 = 750;//500;
    public static float DROP_DIST_CUR_MULT2_V3 = 0.25f;//0.2f;// 0.15f;//0.1f;//0.025f;//0.03f;
    public static float DROP_DIST_CUR_TIER3_V3 = 920;//945;//940;//930;//920;//900;
    public static float DROP_DIST_CUR_MULT3_V3 = 1.5f;//1.1f;//1.0f//0.9f;
    public static float DROP_DIST_CUR_TIER4_V3 = 950;//980;//970;//965;
    public static float DROP_DIST_CUR_MULT4_V3 = 3.0f;//2.6f;//2.8f;//3.0f//0.9f;

    public static float DROP_DIST_FACTOR = 3000f;//3500f;//4000f;//5000f;
    public static float DROP_DIST_MAX_PERDISTFACTOR = 1350;//1400;//1450f;//1400f;//1250f;//1200f;//1000f;
    public static float DROP_DIST_MINDIST = 550f;//600f;//650f;//750f;//850f;// 680f;//700f;//760f;//800f;   //not actually min dist, is min halite. not sure how this naming happened
    public static float DROP_DIST_MIN_AVG_BASED = 7.5f;//8f;//8.5f;//9f;//9.5f;//10f;//11f;//12f;//15f;
    public static float DROP_DIST_BASE_DIST_CUR_V4 = 23f;//27f;//25f;//30;//35;//25;
    public static float DROP_DIST_DIST_POW = 0.98f;//0.95f;
    public static float DROP_DIST_EXPO_POW = 1.4f;//30;//35;//25;
    public static float DROP_DIST_WEIGHT = 1.3f;//1f;



    public static float GuessShipHalitePerTurnAverageV2 = 9.9f;//9.5f;//10f;//12f;//17;


    public static float TurnInSpeed = 370f;//400f;//450f;//390f;//400f;
    public static float TurnInSpeedHalite = 2.2f;//2.1f;//1.9f;//1.7f;// 1.45f;//1.7f;//1.4f;//1.2f;//1f;



    public static float TimeFactorHpsInitial = 0.85f;//0.875f;//0.95f;//0.95f;//0.9f;
    public static float TimeFactorHpsReducing = 0.25f;//0.255f;//0.24f;//0.26f;// 0.27f;//0.28f;//0.25f;//0.28f;//0.25f;//0.22f;//0.2f;

    public static float ExpectedShipValueCeaseGather = 28f;//25f;
    public static float MinGatherRate = 0.55f;// 0.52f;//0.55f;//0.6f;//0.7f;//0.65f;//0.62f;//0.6f;//0.55f;//0.65f;//0.7f;

    public static float EVNothingLeftToEat1 = 0.04f;//0.0451846585508303f;//0.045f;//0.05f;//0.07f;
    public static float EVNothingLeftToEat2 = 0.0937281231837915f;//0.07f;//0.08f;
    public static float EVNothingLeftToEat3 = 0.25f;//0.234219284857319f;//0.23f;//0.21f;//0.24f;
    public static float EVNothingLeftToEat4 = 0.25f;//0.261665013503283f;//0.35f;//0.31f;//0.33f;//0.35f;//0.38f;
    public static float EVNothingLeftToEat5 = 0.35f;//0.4f;//0.440829547103821f;//0.7f;//0.68f;//0.65f;
    public static float EVNothingLeftToEat6 = 0.4f;//0.45f;//0.5f;//0.539042532016413f;//0.88f;//0.93f;
    public static float EVNothingLeftToEat7 = 0.91040232508164f;//0.93f;//0.9f;//0.93f;//0.97f;

    public static float EVOvertakeV2 = 0.500566834154329f;//0.50f;//0.65f;//0.70f;
    public static float EVLessShips = 1.7f;//1.6f;//1.5f;//1.45f;//1.4f;//1.2f;//1.4f;//0.78f;//0.70f;// 0.65f;
    public static float EVLeadingByALittle = 1.16632365759356f;//1.2f;//1.1f;
    public static float EVLeadShipsAndPointsV2 = 1.6f;//1.5f;//1.4f;//1.35f;//1.25f;//1.2f;//1.12957789344632f;//1.15f;//1.1f;//1.08f;//1.05f;//0.95f;// 0.93f;//0.8f;
    public static float EVLeadShipsV2 = 1.3f;//1.2f;//1.1f;
    public static float EVTimeFactorMultiplier = 0.242398081975893f;//0.24f;//0.22f;//0.2f;//0.17f;//0.2f;// 0.3f;//0.325f;//0.275f;//0.25f;


    public static float EnemyShipTimesScary2p = 22f;//25f;//27f;
    public static float EnemyShipTimesScaryHal2p = 6f;//7f;//6.5f;//6.8f;//6.5f;//6f;
    public static float EnemyShipTimesScary4p = 12f;//14f;//18f;
    public static float EnemyShipTimesScaryHal4p = 0.6f;//0.65f;//0.6f;//0.55f;// 0.5f;//0.6f;//1.4f;//2.5f;//4f;


    public static float[] InspiredEnemyShip = new float[]{20f,5f};
   // public static float[] InspiredEnemyShipTimesScary = new float[]{30f,10f};
   public static float[] InspiredEnemyShipTimesScary = new float[]{25f,12f};

    public static float[] InspiredEnemyShipHal = new float[]{0.95f,0.5f};
    public static float[] InspiredEnemyShipTimesScaryHal = new float[]{5f,0.5f};

    public static float cumulativeInspireFlat = 30f;//28f;//26f;//25.0f;
    public static float cumulativeInspireHalite = 0.007f;//0.005f;//0.7f;//1.0f;


    public static float LureV3 = 0f;//0.1f;//0.2f;//0.3f;// 0.1f;//0.35f;//0.4f;//0.6f;//0.4f;//0.2f;
    public static float LongLureV2 = 1.9f;//1.8f;//1.6f;//1f;//0.5f;//0.2f;
    public static float MedLureV2 = 0f;//0.1f;//0.2f;//0.5f;//1.0f;//2.5f;//0.65f;//0.6f;//0.3f;//0.35f;

    public static float LureEmptyishV3 = 0.08f;//0.1f;//0f;//0.3f;//0.6f;//1.2f;//3f;//2f;//1.0f;//0.6f;//0.7f;//2f;//1.2f;//0.8f;//0.45f;
    public static float LongLureEmptyishV2 = 7f;//6.5f;//6f;//4f;//2.2f;//1.9f;//2.02679254054733f;//2f;//3.0946143235293f;//3f;//4f;//6f;//6.5f;//5.5f;//5f;//3f;//7f;5f;//3f;//2f;
    public static float MedLureEmptyishV2 = 0f;//0.5f;// 0f;//0.51f;//5f;// 6f;//2f;//1.2f;

    public static float LureFullishV3 = 0f;
    public static float LongLureFullishV2 = 0f;
    public static float MedLureFullishV2 = 0f;

    public static int FullishLuresV2 = 965;//960;//955;//950;
    public static int LimitInspireGainsV3 = 960;//950;

    public static int EmptyishLuresV2 = 460;//440;//420;

    public static int ActivateGatherMechanic = 1;
    public static int ActivateFinalInspireAnalysis = 0;
    public static int ActivateComplexPrediction = 1;
//    public static int ActivateChangeImportanceV3 = 1;
    public static int ActivateenemyPredictions = 1;
    public static int AllowEShipsInSimulation = 1;
    public static int ActivateCollisionMechanic = 1;
    public static int ActivateWeirdAlgo = 1;
    public static int ActivateFuturePathing = 1;
    public static int ActivateFuturePathingNonReturn = 0;
    public static int ActivateSimulJourney = 0;
    public static int ActivateAnnoyMechanic = 0;
    public static int ActivateEndGameDropoffBlocks = 1;
    public static int ActivateEntrapment = 0;
    public static int ActivateDenyGoals = 1;
    public static int ActivateReturnToFutureDropoff = 1;
    public static int ActivateAvoidDropoffWhenBuilding = 0;
    public static int ActivateIndependence = 0;//1;//0;
    public static int ActivateAntiInspire = 3; //2 is only 2 players, 3 only 4 players
    public static int ActivateForecastDistInDropoff = 1;

    public static float MinPropGameForAnnoying = 0.8f;

    public static int CrossAvoidMinHaliteV2 = 15;

    public static float CrossAvoidStrengthLastTurnsV3 = 70f;//70f;//45;//50;// 30;//40;
    public static float CrossAvoidStrengthV3 = 0f;//2f;//5f;//10f;//30f;//70f;//10;//15;//25;//30;//15;//5;

    public static float MapSparseFactor1 =  0.7f;//0.65f;//0.6f;//0.55f;//0.48f;//0.44f;//0.408475933783212f;//0.32f;//0.35f;//0.37f;//0.35f;//0.28f;//3f;
    public static float MapSparseFactor2 =  0.05f;//0.06f;//0.1f;//0.12f;//0.135352822090657f;//0.17f;//0.2f;//0.25f;
    public static float MapSparseMult2 = 0.43f;//0.391353269717699f;//0.53f;//0.5f;//0.4f;//0.35f;//0.3f;//0.28f;//0.31f;//0.35f;
    public static float MapSparseMult1 = 0.56f;//0.53f;//0.494979277311546f;//0.42f;//0.45f;//0.5f;// 0.52f;//0.5f;//0.45f;//0.4f;//6f;

    public static float MinMultiplierEV =  1.05f;//1f;//0.94f;//0.9f;//0.847328347346908f;//0.7f;//0.74f;//0.72f;//0.68f;//0.65f;//0.7f;
    public static float MaxMultiplierEV = 3.2f;//2.9f;//2.8f;//2.73f;//2.67354463668468f;//2f;//1.75f;//1.62f;//1.55f;//1.45f;//1.3f;//1.15f;//2f;

    public static float HasReachedGoalFlat = 0f;
    public static float HasReachedGoalTurn = 0f;
    public static float GoalWeightV6 = 1300f;//820f;//840f;//870f;//1024.04083254538f;//900f;//960f;//980f;//960f;//936.197719141985f;//943f;//800f;//700f;//650f;//500f;//600f;//440f;//400f;//340f;//280f;//220f;//300f;//220f;//300f;//350f;// 148f;//145f;//140f;//135f;//130f;//125f;// 110f;//100f;//90f;//110f;//80f;//60f;//90f;//110f;//130f;//160f;//154f;//148f;//120f;//90f;//70f;//45f;
    public static float GoalWeightDesireV2 = 0.008f;//0.01f;// 0.015f;
    public static float DropoffGoalWeightV2 = 231.381098538754f;//250f;//230f;//220f;//230f;//250f;//150f;//135f;//144f;//148f;
    public static float GoalAboutDenyingWeight = 780f;//750f;//700f;//580f;//550f;//500f;//700f;//1000f;
    public static float GoalWeightHaliteMod = 0.0025f;//0.0045f;//0.004f;//0.0025f;//0.002f;//0.0016f;// 0.0013f;//0.001f;
    public static float GoalWeightOnHaliteMod = 0.001f;
    public static float GoalWeightDistDropoff = 0f;//0.001f;
    public static float GoalWeightMinModifier= 0.43f;// 0.45f;//0.5f;// 0.6f;// 0.3f;//0.4f;//0.5f;


    public static float DesirabilityNeighboursTakenV3 = 1f;//0.91f;//0.95f;//0.85f;//0.75f;//0.7f;//0.9f;//0.68f;//0.7f;//0.9f;//0.7f;// 0.65f;
    public static float DesirabilityTaken2 = 0.28f;//0.25f;//0.2f;
//    public static float DesirabilityNearbyTaken = 53f;//50f;//100f;
//    public static float DesirabilityNearbyTakenEnd = 44f;//50f;//100f;
//    public static float DesirabilityNearbyTakenStart = 42f;//44f;//48f;//55f;//50f;//100f;

    public static float GoalNearbyEra0 = 3.53760844902206f;//0f;
    public static float GoalNearbyEra1 = 42f;//34f;//32.7934119393167f;//30f;//20f;
    public static float GoalNearbyEra2 = 55f;//44.3301255148905f;//50f;
    public static float GoalNearbyEra3 =  65f;//54.5492387529857f;//50f;
    public static float GoalNearbyEra4 = 54f;//48.2037884699188f;//45f;//50f;
    public static float GoalNearbyEra5 = 37f;//33f;//36.8472404256334f;//45f;//50f;
    public static float GoalNearbyEra6 = 32f;//34.8114654730102f;//50f;
    public static float GoalNearbyEra7 =  19f;//15.8131247452038f;//10f;

    public static float GoalNearby32324Mult =  0.1f;
    public static float Deny32324Mult =  1f;

    public static float[] GoalNearbyEraPlayers = new float[]{1.2f,1.25f};
    public static float[] GoalNearbyEraDensity =  new float[]{1f,1f,1.05f,1f};
    public static float[] GoalNearbyEraMapSize =  new float[]{1f,1f,0.99f,1f,1f};

    public static int MaxNearbyTaken = 4;//5;
    public static int NearbyRange = 4;

    public static float ShipTileDistReductionV4 = 2.1f;//2f;//1.8f;//1.5f;//1.4f;//1.25f;//1.2f;//1.16999034042439f;//0.9f;//1.2f;//1.35f;//1.3f;//1.2f;//1.5f;//1.3f;
    public static float ShipTileDistPowerEmptyishV2 =  0.92f;//0.94f;//0.952576958555679f;//0.995f;// 0.98f;// 0.97f;
    public static float ShipTileDistPowerNormalV2 = 0.64f;//0.67f;//0.7f;//0.72f;//0.772762378681628f;//0.99f;// 0.96f;//0.94f;//0.9f;//0.87f;//0.91f;////0.93f;
    public static float ShipTileDistPowerFullish = 0.88f;//0.85f;//0.87f;

    public static float ShipTileMultDist0 = 0.7f;//0.72f;//0.74f;//0.77f;//0.8f;//0.82f;//0.887941158790142f;//1.1f;//1.05f;//1.15f;
    public static float ShipTileMultDist1 = 0.9f;//0.938058131879952f;//0.99f;//1.0f;// 1.01f;  //stats want this to be less than 1, why?
    //public static float TilePrioDistV2 = 20f;//360f;//320f;//310f;//290f;//220f;//200f;
    public static float TilePrioDistPower = 0.999f;//0.992f;//0.99f;
    public static float MinTilePrio = 0.02f;//0.05f;
    public static float TileScoreLong = 4f;//3.5f;//3.3f;//3.2f;//2.85846627481339f;//2.8f;//2.5f;//2f;//0.8f;//1.5f;//0.5f;//1f;//5f;//8f;//16.5f;//15.5f;//16f;//18f;
    public static float[] GoalWeightInspireFlat = new float[]{66f,230f};//170f;//165f;//170f;//200f;//150f;//100f;
    public static float[] GoalWeightInspireHal = new float[]{2.9f,3.1f};//2.2f;//2.4f;//2.7f;//3f;
    public static float DistDropoffScore = 2.09384301593174f;//2.0f;//1.5f;
    public static float GoalWeightEdgeMapHaliteV2 = 5.00261348841909f;//6f;//4f;//1.7f;//1.4f;//1.3f;//1.2f;//1.0f;
    public static float GoalWeightEdgeMapFlatV2 = 22f;//20f;//18.9177795155098f;//15f;//30f;//20f;//35f;//30f;//20f;
    public static float GoalWeightUpcomingDropoffDistV2 = 350f;//410f;//430f;//470f;//500;//1698.03728839832f;//1800f;//2000f;//800f;//40f;// 50f;//3f;
    public static float GoalWeightSqrtHalite= 1.5f;//2.12725914045834f;//-2f;//0f;
    public static float GoalWeightLogHalite= 50f;//45f;//37.2353496982421f;//50f;
    public static float GoalWeightExpoHalite= -2f;//-1f;//1.5f;//3.17012647132829f;//0f;
    public static int GoalMaxDist= 30;//24;//22;//20;
    public static float GoalWeightForecastDist= 15f;//10f;

    public static float[] BrawlMin= new float[]{0.22f,0.4f};//0.43f;//0.6f;
    public static float[] GoalBrawl = new float[]{30f,0f};
    public static float[] DistEnemyDropoffScore = new float[]{-0.0159221292991153f,-0.008f};
    public static float TileScoreNeighboursV2 = 0f;//0.05f;//0.06f;//0.08f;//0.1f;//0.112134748196835f;//0.13f;//0.15f;//0.18f;//0.2f;//0.07f;//0.09f;//0.08f;//0.11f;//0.15f;
    public static float MaxHaliteForGoalV3 =  270f;//281.802510867919f;//330f;//340f;//230f;//250;//290;//270;//300;//450;// 250;//500;//550;//600;//270;//290;//320;//350f;//500;//520;//650;//600;//620;//640;//650;//700;//775;//820;//910;//875;//800f;//700f;
    public static int MaxDistForHighHaliteShip =  5;
    public static float MinHaliteForDropoffGoal = 615;//625;//635;//645;// 760;//770f;//780f;//800f;
    public static float GoalConsistencyBonus0V2 = 100f;// 118;//122;//129.237932054378f;//150f;//110f;//120f;//100f;
    public static float GoalConsistencyBonus1= 90f;//80f;//60f;//49.431629127774f;//50f;//70f;//60f;//50f;
    public static float GoalConsistencyBonusNear = 65f;//60f;//49.8406323899881f;//50f;//40f;//37f;//34f;//30f;//25f;//30f;
    public static float GoalWeightHaliteV2 = 4.8f;//4.5f;//3.9f;//7f;// 3.9f;//3.6f;//3.3f;//3f;// 2.5f;//1.5f;//1.4f;//1.2f;//1.5f;//2f;//1.5f;//4f;//15f;//12f;//5f;//4f;//2.4f;//.7f;
    public static float[] GoalTrustInspire = new float[]{0.65f,0.99f};
    public static int GoalUseInspireTurn = 1;//3;//5;//6;//5;//3;//1;//2;//4;


    public static float IndependenceHalite =  0.93f;//0.9f;
    public static float IndependenceMax3 =  0.65f;//0.6f;
    public static float IndependenceDropoffDist =  8f;//5f;
    public static float IndependenceEnemiesRange3 =  55f;//50f;
    public static float IndependenceFriendsRange3 =  55f;
    public static float IndependenceLongLure =  0.085f;//0.09f;
    public static float MaxIndependenceScoreForGoal =  440f;//460f;//490f;//520f;// 650f;//680f;//710f;


    public static float[] GoalShipTileInspireTrust =  new float[]{ 0.399177570332072f,0.98f};//0.6f;
    public static int GoalMinHaliteOnTile = 50;//40;
    public static float GoalTileDesireMultiplier = 1.0f;
    public static float GoalAverageHaliteNormalizeVal = 100f;//90f;//80f;//75f;//80f;//85.8244459311864f;//85f;//63.724853346572f;//105;//95f;//100f;
    public static float GoalAverageHaliteNormalizeCap =  1.2f;//1.15799832076251f;//1.1f;//1.15f;//1.1f;//1.02f;


    public static int BlowEUpLateTurnV2 = 50;//38;//36;//34;//32;//28;//25;
    public static float BlowEUpLate2pV2 = 12f;//11f;//10f;//8;
    public static float BlowEUpLate4pV2 = 14f;//13f;//12;//10;

    public static int BaseMeaningfulHalite = 280;//270;//250;
    public static int MinMeaningfulHalite = 80;//70;//60;

    public static int PenaltyMoveBackToPreviousTileV4 = 150;

    public static float AverageHaliteNormalizeVal =  85f;//68.2094995f;//77f;//80.246469625928f;//70f;//75f;//90f;//95f;//100f;
    public static float AverageHaliteNormalizeCap = 0.83f;//0.715388388679259f;//0.780395809909524f;//1.0f;


//    public static float TImportanceScaling = 0.34f;//0.35f;//0.38f;//0.4f;// 0.43f;//0.45f;//0.5f;//0.55f;//0.6f;

    public static int ComplexDistMax = 6;//3;
    public static float ComplexDistDivisor = 80f;//100f;//30f;


    public static float T0Importance = 1.45f;//1.52f;//1.56675734434612f;//1.55f;//1.6f;//1.65f;//1.7f;//1.9f;//2.1f;//2.3f;//2.6f;//2.05f;//2.1f;//2.3f;//2.5f;//2.9f;//2.7f;//2.25f;
    public static float T1Importance =   0.56f;//0.623489799383694f;//0.6f;//0.6f;
    public static float T2Importance = 0.31f;//0.297188288127025f;//0.35f;//0.23f;// 0.2f;//0.15f;//0.13f;//0.15f;//0.2f;//0.25f;//0.3f;//0.35f;
    public static float T3Importance =  0.28f;//0.25f;//0.2f;//0.18f;// 0.15f;//0.13f;//0.15f;//0.13f;//0.15f;//0.2f;//0.35f;
    public static float TFinalImportance =  0.13f;//0.15f;//0.13f;//0.15f;//0.2f;//0.55f;
    public static float TBaseImportance =  0.2f;//0.18f;//0.15f;//0.13f;//0.15f;//0.13f;//0.15f;//0.2f;//0.21f;//0.25f;//0.3f;

    public static float T0ImportanceFull = 1.25f;//1.19206842615396f;// 1.20f;//1.18f;//1.15f;//1.12f;//1.08f;//1f;
    public static float T1ImportanceFull = 0.48f;//0.52f;//0.57f;//0.594017724245807f;//0.72f;//0.72f;//0.75f;//0.8f;//0.83f;//0.92f;
    public static float T2ImportanceFull = 0.47f;//0.51f;//0.53f;//0.55f;//0.57f;//0.629774227765951f;//0.62f;//0.65f;//0.73f;//0.76f;//0.9f;
    public static float T3ImportanceFull =   0.43f;//0.47f;//0.53f;//f0.58f;//0.62f;//0.65f;
    public static float TFinalImportanceFull =  0.43f;//0.47f;// 0.51f;//0.57f;//0.55f;//0.62f;//0.65f;// 0.9f;
    public static float TBaseImportanceFull =   0.43f;//0.47f;//0.51f;//0.55f;//0.62f;//0.65f;//0.9f;



        public static float[] ItIsT0BeNotAfraid = new float[]{0.23f,0.15f};//0.36f;//0.4f;//0.5f;//0.55f;//0.6f;
    public static float[] IffyIfNotT0V3 = new float[]{0.20f,0.14f};// 0.5f;//0.55f;//0.6f;//0.55f;//0.4f;//0.35f;//0.25f;

    public static float MapEmpty = 0.08f;//0.09f;//0.11f;//0.15f;//0.28f;//0.25f;//0.2f;//0.15f;
    public static float MapEmptyBoostPerHaliteStandstillV2 = 0f;//13f;//15f;
    public static float MapAverageHaliteEmpty = 25f;

    public static float EstimatedShipValueModifier = 0.91f;//0.932f;//0.94f;//0.96f;//0.98f;//0.92f;//0.95f;//0.9f;//0.85f;//0.95f;//0.9f;
    public static float EstimatedShipValueFlat = -140f;//-120f;//-100f;//-65;//-60f;


    public static float SwapPunishmentV2 = 110;//105;//115f;//122f;//130f;//140f;//120f;
    public static float SwapPunishmentSimilarV2 = 280f;//300f;//250f;//240f;//210f;
    public static float PunishmentCloseToEnemyDropoff = 750f;//1000f;


    public static float PlanLastTurnSuggestions =  290f;//343.373537072094f;//310f;//298.62401526215f;//300f;//260f;//250f;//200f;//180f;//150f;//160f;//140f;//130f;//100f;//80f;//20f;// 40f;


    public static float OddsPathBase = 0.588307758079415f;//0.55f;//0.6f;//0.65f;  //Now positive (0.75 = 25% chance it's not there)
    public static float OddsNotPathTurnPow = 0.9f;

    public static float OddsPathBaseNew = 0.85f;
    public static float OddsPathBaseCouldBeNew = 0.6f;//0.5f;
    public static float OddsNotPathTurnPowNew = 0.85f;

    public static float OddsNotLikelyV3 = 0.23f;//0.2f;//0.37f;//0.4f;
    public static float OddsNotCouldbeV2 = 0.6f;//0.62f;//0.65f;//0.68f;//0.7f;//0.6f;//0.7f;
    public static float OddsNotMaybeV4 = 0.99f;//0.98f;////0.95f;


    public static float GoalFarTileMinimumScoreV2 = -12.9992236254596f;//-10;//0;//-300;//-30;//-100f;
    public static float GoalCloseTileMinimumScore = -70;//-60;//-50;//-70f;//-100f;
    public static float GoalCloseTileMinimumHalite = 8f;//10f;//24f;//20f;

    public static float GoalDropoffNotMadeYet = -1f;
    public static float GoalDropoffShipYard = -1f;//-2f;//-2.4f;//-2.7f;//-3f;
    public static float GoalDropoffNearbyHalite = 0.00015f;//0.0002f;//0.0003f;
    public static float GoalDropoffLongLure = 0.005f;
    public static float GoalDropoffNearbyHaliteMax = 4.2f;//4.5f;//5f;
    public static float GoalDropoffNearbyShips = 0.1f;//0.8f;//0.9f;//0.85f;//0.83f;//0.63f;//0.6f;//0.5f;
    public static float[] GoalDropoffNearbyEnemyDropoff = new float[]{0.2f,0.4f};
    public static int GoalDropoffNearbyEnemyDropoffDist = 2;//5;


    public static int AheadInShipThresh = 3;
    public static float MultiplierAggression2sIfAheadShipCountV2 = 1.4f;//1.3f;

    public static float MultiplierAggression2sEndGame = 1.05f;
    public static float MultiplierAggression4sEndGame = 1f;//1.05f;
    public static int EndGame = 50;


    public static int DO_MY_SIM_ONCE_BEFORE_SIM = 0;
    public static int PREDICT_ENEMY_TURNS = 1;//3;
    public static int MIN_RESULTS_BEFORE_PREDICTION = 8;
    public static float MIN_ACCURACY = 0.27f;//0.3f;//0.33f;//0.38f;//0.4f;
    public static float FLAT_CONTRIBUTION_PREDICTION = 0.15f;// 0.17f;//0.18f;//0.19f;//0.17f;//0.15f;//0.1f;
    public static float BONUS_MOST_RELIABLE = 0.95f;//1.0f;
    public static float BASIC_STANDSTILL_HALITE = 1.15f;//1.2f;//1.6f;//1.8f;//1.7f;//1.6f;//1.5f;//1.4f;//1.3f;//1.2f;//1.25f;
    public static float BASIC_MOVE_HALITE = 1.4f;//1.3f;//1.25f;
    public static float BASIC_BURNHALITE = 0.15f;//0.12f;//0.1f;
    public static float BASIC_BURNHALITE_RETURN = 0.15f;//0.12f;//0.1f;
    public static float BASIC_MEANINGFUL = 14f;//15f;//19f;//20f;
    public static float BASIC_MEDLURE = 1.5f;//1.7f;//2.0f;//2.8f;//2.6f;//2.7f;//2.8f;//3f;
    public static float TURNS_DROPOFF_FULL = -100f;//-50f;//-40f;//-34f;//-27f;//-30f;
    public static float TURNS_DROPOFF_NORMAL = 2.0f;//2.1f;//2.2f;//2.6f;//3f;//3.3f;//3.5f;//4f;

    public static float SELF_COLLISION = 33000f;//30000f;//25000f;//18000f;//15000f;

    public static float WorthMultiplierMult = 1.15f;//1.1f;//1.15f;

    public static float BeDifferentsoloJourney = 1.5f;


    public static float ShipHaliteOnMoveV2 = 1.55f;//1.5f;//1.3f;//1.611380615f;//1.4f;//1.89574188625919f;//2.5f;//1.8f;//1.85f;//1.5f;///1.2f;//0.6f;//0.5f;
    public static float ShipHaliteIfStandstillNextV2 = 1f;//0.5f;//0.11972545015171f;//0.1f;//0.15f;//0.2f;

    public static float HalAvgDist1WeightV2 = 0f;//0.2f;
    public static float HalAvgDist2WeightV2 = 0f;//0.16f;
    public static float HalAvgDist3WeightV2 = 0f;//0.1f;
    public static float HalAvgDist4WeightV2 = 0f;//0.07f;
    public static float HalAvgDist5WeightV2 = 0.18f;//0.14f;//0.1f;//0f;//0.05f;

    public static float[] MyShipRange1Weight = new float[]{0f,0f};//-8f;
    public static float[] MyShipRange2Weight = new float[]{3f,-1f};//-4f;
    public static float[] MyShipRange3Weight = new float[]{1.8f,0f};//-2f;
    public static float[] MyShipRange4Weight = new float[]{0f,0f};
    public static float[] MyShipRange5Weight = new float[]{-2f,2.5f};//-1f;

    public static float[] EnemyShipRange1Weight = new float[]{0f,-50f};
    public static float[] EnemyShipRange2Weight = new float[]{0.5f,-0.1f};//-0.1f;//0f;
    public static float[] EnemyShipRange3Weight = new float[]{0.6f,0.2f};//0f;//-2f;// -1f;
    public static float[] EnemyShipRange4Weight = new float[]{0f,0f};
    public static float[] EnemyShipRange5Weight = new float[]{1f,-0.1f};

    public static float MaxSums2 = 0f;//0.009f;//0.011f;//0.013f;//0.012f;
    public static float MaxSums3 = 0.015f;//0.012f;
    public static float MaxSums4 = 0.055f;//0.05f;//0.045f;//0.03f;//0.027f;//0.025f;//0.02f;//0.015f;//0.012f;
    public static int MaxSumsCap = 2100;//2000;//1700;//1650;//1600;//1500;//1250;//1200;

    public static float GoalMoveSums2 = 0.0104585147337809f;//0.01f;
    public static float GoalMoveSums3 = 0.0127536508393176f;//0.01f;
    public static float GoalMoveSums4 =  0.013f;//0.0107462329064793f;//0.01f;


    public static float[] InspireFlatMovePunish = new float[]{ 15.2683022462219f,27};//29;//31;//35;//50f;//40f;//35f;//20f;
    public static float[] InspireMultMovePunish = new float[]{1.9f,2.4f};//2.2f;//1.9f;//1.4f;//1.2f;
    public static float InspireMultV2 = 2.1f;//1.7f;//2.0f;//1.80879737564137f;//1.8f;//1.85f;//1.9f;//1.7f;//1.5f;//1.3f;//1.2f;//1.1f;
    public static float InspireFlatMult=1.1f;
    public static float InspireGuaranteedNextHalite = 1.7f;//2f;
    public static float InspireGuaranteedNextFlat = 0f;//11f;//14f;//12f;//10f;


    public static float ActivateRuleOfXFactorAvg = 0.73f;//0.7f;//0.76f;//0.8f;//1f;
    public static float ActivateRuleOfXMinHalite = 270f;//250f;
    public static int ActivateRuleOfXBelowShipHalite = 700;//710;//720;//730;//750;// 800;
    public static float ActivateRuleOfThreeTileBetterThanV2 = 1f;//0.65f;//0.67f;//0.7f;//0.75f;//0.8f;//0.9f;//0.85f;//1.0f;
    public static float ActivateRuleOfTwoTileBetterThan = 1.15f;//1.1f;//0.62f;//0.5f;//0.7f;//0.8f;///1.0f
    public static float RuleOfThreeWeightV2 = 33f;//30f;//50f;//100f;//50f;
    public static float RuleOfTwoWeightV2 = 21f;//25f;//18f;//15f;//30f;






    public static float EnemyTerrainDesire =  30f;//40f;//5f;//0.45f;//0.5f;
    public static float EnemyTerrainHaliteV2 = 0f;//0.05f;//0.1f;//0.065f;//0.053f;//0.05f;
    public static float EnemyTerrainGoalFlatV2 =  0.1f;//0.17f;//0.2f;//0.3f;//0.2f;//-1f;//-10f;
    public static float EnemyTerrainGoalHaliteV2 =  -0.0203560459551604f;//-0.018f;//-0.015f;//0.02f;//0.025f;//0.04f;//0.03f;// -1.2f;//-0.7f;//-0.8f;//-0.5f;  //positive is prefer enemy terrain.

    public static float BorderReachableFlat =  3.0f;//3.2f;//3.4f;//3.78339787597853f;//4.5f;//6;//8;//10;//20;
    public static float BorderReachableHalite =  0.2f;//0.2f;//0.117219344909359f;//0.09f;//0.12f;//0.1f;//0.07f;// 0.06f;// 0.05f;
    public static float[] GoalControlDanger =  new float[]{0.00f,0.15f};//0f;
    public static float[] GoalDenyScores =  new float[]{1.5f,0.9f,0.7f,0.6f,0.35f,1.5f,1.15f,1.1f,1.35f,1.25f};
    public static float MinDenyScore = 301.504230574632f;// 300f;
    public static float GoalCentralFlat =  1.38048315295888f;//0f;
    public static float GoalCentralHalite =  0.816881146189578f;//0.7f;//1f;
    public static float NearbyMultiplierEnemiesClose = 2.2f;//1.8f;
    public static float NearbyMultiplierAlone = 0.8f;

    public static float GoalEnemysNear5 = 5f;
    public static float GoalFriendsNear5 = 5f;

    public static int TilesDistanceLowPriorityZone = 9;//8;
    public static float[] LowPrioZoneFlat = new float[]{-20f,-18f};//-15f;//-20f;
    public static float[] LowPrioZoneHalite = new float[]{-0.08f,-0.16f};//-0.45f;//-0.5f;

    public static float AnnoyWeight = 200000f;
    public static float AnnoyFlatTileDesire = -40f;//-15f;

    public static float WeirdAlgoWeight =  75f;//67.7727720733646f;//70f;//65f;//60f;//64.3228440891942f;//65;//55f;// 55f;//35f;//25f;//10f;//45f;//40f;
    public static float[] WeirdAlgoWeightMultiplier =  new float[]{1f,1f,1f,1f,1f,1.1f,1f,1f,1f,1f};

    public static int WeirdAlgoSteps = 6;//7;//8;//10;
    public static int WeirdAlgoDistFromGoalV2 = 4;

    public static float DenyRelevanceEra0 = 0.9f;
    public static float DenyRelevanceEra1 = 0.85f;//0.9f;
    public static float DenyRelevanceEra2 = 2.1f;//2.06133228792142f;//1.6f;//1.1f;
    public static float DenyRelevanceEra3 = 1.42544349395621f;//1.4f;//1.2f;
    public static float DenyRelevanceEra4 = 1.35f;//1.33773680353255f;//1.3f;//1.2f;
    public static float DenyRelevanceEra5 = 0.9f;//1.05526790491435f;//1f;//0.95f;//0.9f;
    public static float DenyRelevanceEra6 = 0.37249537844098f;//0.4f;//0.45f;//0.5f;
    public static float DenyRelevanceEra7 =  0.0756306538502908f;//0.1f;




    public static float ControlEdgeTileFlat = 18f;//15f;//18f;//16f;//13f;//10f;
    public static float ControlEdgeTileHalite = 0f;//0.2f;//0.43f;//0.4f;//0.5f;


    public static float WeightSqrtHalite= -2.5f;//-2f;0f
    public static float WeightLogHalite= 10f;
    public static float WeightExpoHalite= 0f;

    public static float EntrapmentWeight = 4800f;//5200f;//5000f;
    public static float EntrapControlFactor = 1f;
    public static int EntrapMinEHalite = 360;//370;//400;// 500;
    public static int EntrapMaxMyHalite = 310;//300;
    public static int EntrapMinDist = 9;
    public static int EntrapMinShipDifference = 7;//5;

    public static int EnemyShipDifForBehind = 0;

    public static int WIDTH_SOLO_MINIMAL = 21;//23;//28;//25;//15;//20;//12;
    public static int MinimalPreMechanicV2 = 2;//0;//2;
    public static int MinimalPostMechanicV3 = 2;//6;//2;//3;//2;//5;//4;//2;//0;
    public static int AddToCheckAgain = 1; //till which turn will we accept swaps
    public static int SimStandstillSoloJourney = 0;
    public static int DoGreedyMoveInMix = 0;

    public static float UrgencyRunner = 125;//120;//100;//80;
    public static float UrgencyFastReturn = 125;//120;//100;//80;
    public static float UrgencyReturn = 34f;//30;//33;//44;//50;
    public static float UrgencyDenyGoal = 85;//80;//75;//65;//55;//40;//32;//30;// 26;//23;//22;//20;
    public static float UrgencyGoal = 60;//55;//50;//38;//35;//30;//28;//26;//24;//21;//18;//15;
    public static float UrgencyNoGoal = 5;///2;
    public static int DepthDeny = 9;//7;//8;
    public static int DepthGoal = 11;//9;//8;//9;//8;
    public static int TurnDepth = 19;//17;//15;//16;//15;//14;//13;//12;//9;  //cant be higher than collect array size in mybot
    public static int PathLength = 5;//6;//7;
    public static int LimitHalite1 = 790;//800;//820;//840;//860;//900;//920;
    public static int LimitHalite2 = 1045;//1035;//1025;//1010;//1000;//990;//970;
    public static int LimitHalite3 = 945;//940;//950;
    public static int LimitHalite4 = 940;///960;//
    public static int LimitHalite5 = 850;//840;//820;//800;
    public static int LimitHalite6 = 390;//400;
    public static float HaliteTurnsDropoff = 20;
    public static int HaliteMinDistDropoff = 5;//4;
    public static int ReserveDepth = 9;//17;//12;15;

    public static float WeirdAlgoInspireMult = 0.65f;//0.7f;//0.8f;//0.85f;//1f;//2f;
    public static float WeirdAlgoEOdds = 320;//300;//250;//175f;//150f;
    public static float PathConsistency0 = 28f;//31f;//33f;//35f;//30f;
    public static float PathConsistency2 = 17f;
    public static float PathConsistency3 = 17f;//15f;//13f;
    public static float PathConsistency4 = 10f;
    public static float PathConsistency5 = 2f;//7f;//9f;
    public static float PathConsistency6 = 2f;//7f;//8f;//13f;

    public static float TurnInHaliteOnMove = 5f;


    public static float FollowFuturePathV2 = 25f;//31.5064985862681f;//30f;//41f;//60f;//180f;//210f;//240f;//170f;//160f;//150f;
    public static float ReduceTrustFuturePathNearbyFriends3 = 0.995f;
    public static float ReduceTrustFuturePathNearbyFriends5 = 0.999f;//0.997f;//0.995f;
    public static float FuturePathTileHaliteFactorV2 =  14f;//14.5f;//15f;//15.5f;//16f;//14f;//15f;//10f;//10.5f;//10f;//10.5f;//10f;//9.5f;//10f;
    public static float FuturePathMeaningfulAlterationsFactorV2 = 0f;//32f;//30f;//16f;//15f;
    public static float FuturePathMeaningfulAlterationsFactorNonFinishV2 = 0f;//39f;//36f;//34f;//32f;//28f;//25f;//22f;// 20f;//17f;//15f;
    public static float FuturePathPathLengthNonFinishV2 = 9.35f;//9.5f;// 10f;//4.2f;//4f;
    public static float FuturePathTurnsDropoffNonFinishV2 = 20f;//19f;//18f;//16f;//15f;//100f;//20f;//0.11f;//0.1f;
    public static float FuturePathProportionAvgV2 = 0.52f;// 0.48f;//0.44f;//0.4f;//0.38f;//0.4f;//0.35f;//0.3f;
    public static float ThresholdMeaningfulAlterationV2 = 52f;//56f;//48f;//40f;
    public static int FuturePathMinHaliteFinishV2 = 900;//910;//930;//950;//900;//870;//850;
    public static float FuturePathMinHalitePerTurnV2 = 11.5f;//11f;//10.5f;//10f;//20f;//25f;
    public static int FuturePathMinHaliteNonFinish = 220;//210;//200;//190;//200;
    public static float FuturePathBaseDistMeaningfulV2 = 39;//41f;//43f;//45f;//47f;//50f;
    public static int FuturePathMaxQueue = 34;//27;//29;//25;//23;//25;
    public static int FuturePathMaxMoveDepth = 5;//7;//11;//10;

    public static float PrioFuturePathDistDropoff = -130f;//-120f;//-100f;//-85f;//-80f;//-70f;// -60f;//-50f;
    public static float PrioFuturePathHalite = -0.1f;
    public static float PrioFuturePathCrowdedEnemy = 5f;
    public static float PrioFuturePathCrowdedMy = 4.1f;//4.3f;//4.5f;
    public static float PrioFuturePathHaliteAround = 0.09f;//0.08f;// 0.07f;//0.08f;//0.1f;

    public static float RuleOf90 = 82f;//20f;//82f;//79.9714825275132f;//71.4810628032719f;//88f;//100f;
    public static float RuleOf120 = 0f;
    public static float RuleOf150 = 0f;
    public static float RuleOf180 = 0f;
    public static float RuleOf210 = 0f;

    public static int MinExpectedHaliteBeforeConsiderDropoff = 3500;
    public static int AllowRangeExpectedV2 = 6;

    public static float AvoidELocT0Mult = 1.7f;//1.5f;//1.6f;//1.5f;
    public static float AvoidELocTOtherMult = 0.9f;//0.73f;//0.7f;//0.65f;//0.6f;//0.5f;

    public static int WeirdAlgoMaxGathersByOtherShips = 3;//1;

    public static int InspireShape = 1;
    public static int InspirePathType = 1;
    public static int InspirenewVersion = 0;//1;//1;//0;//1;

    public static float SimulWeight = 25f;//40f;
    public static float[] SimulWeightMultiplier = new float[]{1f,1f,1f,1f,1f,1f,1f,1f,1f,1f};


    public static float ExperimentalSimulTurnInHalite = 2.2f;//
    public static float PathConsistency1 = 23f;//25f;//20f; 1.8f;//1.7f;//1.5f;
    public static float ExperimentalSimulGoal = 15f;//10f;
    public static float ExperimentalSimulReachedGoal = 23f;//20f;//15f;//11f;//10f;//5f;
    public static float ExperimentalSimulVisited = 2.6f;//3f;//4.2f;//4.5f;//5f;
    public static int ExperimentalSimulMaxTurns = 17;//19;//21;//27;
    public static int ExperimentalSimulWidth = 44;//50;
    public static float ExperimentalEOdds = 20f;//10f;


    public static int AntiInspire4pMinTurnV2 =  150;
    public static float AntiInspire4pMinPlayerDifV2 =  0.1f;
    public static float AntiInspire6ContributionV2 =  0.25f;
    public static float AntiInspire4ContributionV2 =  0.75f;
    public static float AntiInspire2ContributionV2 =  0.25f;
    public static float AntiInspireBaseDecreaseByV2 =  2.3f;


    public static float MurderMoreHalite = 0.35f;
    public static float MurderHaliteDif = 0.01f;
    public static float MurderControlFlat = 0.7f;//0.6f;//0.5f;
    public static float MurderControl = 0.4f;
    public static float MurderHalite = 0.0008f;
    public static float MurderHaliteEnemyHalite = 0.002f;
    public static float MurderFullEnemy = 0.5f;
    public static float MurderTileHalite = 0.0005f;
    public static float Murder4p = 0.5f;


    public static float TileBorderFlat = 20f;
    public static float TileBorderHalite = 0.5f;


    public static float CollisionKnob = 0.95f;
    public static float[] CollisionsKnobGameType   = new float[]{1f,1f,1f,1f,1f,0.85f,0.9f,0.95f,1.105f,1f};
    public static float[] CollisionsKnobDensity   = new float[]{1.05f,1f,1f,0.95f};


    public static float[] MurderAvoidFlat = new float[]{20f,1000f};
    public static float[] MurderAvoidHalite = new float[]{1f,10f};
    public static float[] MurderAvoidLikelyhood = new float[]{5f,1000f};


    public static float[] AntiInspireMoveWeightV2 = new float[]{2f,0f};
    public static float[] AntiInspireGoalWeightV2 = new float[]{6f,0f};


    public static float[] SimpleSpawnSpawns = new float[]{64f,75f,94f,104f,90f,44f,60f,71f,80f,113f};
    //public static float[] SimpleSpawnSpawns = new float[]{64f,75f,94f,104f,152f,44f,60f,71f,80f,113f};


    public static float[] ControlZoneMultiplier = new float[]{4.60554397253939f,0.95f};


    public static float[] MultiplierShipWorthIfBehind = new float[]{1.45f,1.1f};

    public static float[] MaxAllowShips = new float[]{0.62f,0.72f,0.73f,0.70f,0.75f,0.36f,0.45f,0.47f,0.57f,0.63f};

    public static float[] WorthMultiplier = new float[]{1.15f,1.3f,1.4f,1.48f,1.55f,0.95f,1.05f,1.05f,1.00f,1.1f};  //{1.15f,1.15f,1.15f,1.15f,1.15f,1.05f,0.88f,0.82f,0.85f,1.05f};
    public static float[] WorthMultiplierDensity = new float[]{0.95f,1.1f,1.2f,1.3f};
    public static float[] ShipsPerDropoffV2 = new float[]{17f,18f,20f,20f,20f,40f,24f,19f,19f,22f}; //should be an int[] but too lazy



    public static float InspireNextTurnWorth = 0.75f;//0.8f;



    //public static float[] InspireHaliteMultV4 = new float[]{0.15f,0.13f,0.13f,0.13f,0.13f,0.97f,0.89f,0.72f,0.65f,0.5f};//{0.15f,0.13f,0.13f,0.13f,0.13f,1f,0.92f,0.75f,0.72f,0.61f};
    public static float[] InspireHaliteMultV4 = new float[]{0.34f,0.3f,0.27f,0.3f,0.3f,0.97f,0.9f,1f,0.9f,0.95f};//{0.15f,0.13f,0.13f,0.13f,0.13f,1f,0.92f,0.75f,0.72f,0.61f};
    public static float[] InspireHaliteMultV4NoTImportance = new float[]{0.3f,0.3f,0.3f,0.3f,0.3f,1f,1f,0.9f,0.95f,0.95f};
   // public static float[] InspireHaliteMultV4NoTImportance = new float[]{0.15f,0.13f,0.13f,0.13f,0.13f,1f,0.96f,0.75f,0.65f,0.59f};
    public static float[] InspireFlatV4 = new float[]{2f,2.5f,2.5f,2.5f,2.5f,26f,26f,28f,26f,24f};
    //public static float[] InspireFlatV4 = new float[]{2f,2.5f,2.5f,2.5f,2.5f,27f,20f,14f,13f,9f};
    //public static float[] InspireFlatV4NoTImportance = new float[]{2f,2.5f,2.5f,2.5f,2.5f,27f,18f,14f,12f,11f};
    public static float[] InspireFlatV4NoTImportance = new float[]{2f,2.5f,2.5f,2.5f,2.5f,27f,40f,27f,27f,27f};


    //TODO: maybe seperate values depending on whether the opponent is aggressive. Currently buffing it up for server play
    public static float[] AvoidELocMove = new float[]{28f,3500f};
    public static float[] AvoidELocHalMove = new float[]{55f,220f};
    public static float[] AvoidELocHalControlMove = new float[]{65f,55f};
    public static float[] AvoidELocHalMoveEndGame = new float[]{20f,20f};

    public static float[] AvoidEOddsMove = new float[]{45,320f};

    public static float[] AvoidELocStand = new float[]{1.3f,400f};
    public static float[] AvoidELocHalStand = new float[]{65f,4f};
    public static float[] AvoidELocHalStandEndGame = new float[]{30f,30f};
    public static float[] AvoidELocHalControlStand = new float[]{80f,15f};
    public static float[] AvoidEOddsStand = new float[]{0f,30f};

//    public static float[] ELocHigh = new float[]{0.8f,0.8f};
//    public static float[] AvoidELocIfHigh = new float[]{50f,130f};

    public static float[] DropoffWorthMultiplier = new float[]{0.45f,0.46f,0.48f,0.48f,0.58f,0.34f,0.42f,0.33f,0.52f,0.57f}; //{0.37f,0.38f,0.4f,0.45f,0.5f,0.3f,0.55f,0.7f,0.6f,0.62f};
    public static float[] DropoffWorthDensity = new float[]{0.9f,1.15f,1.05f,1.1f};
    public static float[] DropoffWorthGametypeFlat = new float[]{0f,0f,0f,0f,-10f,0f,10f,0f,0f,25f};
    public static float[] DropoffWorthDensityFlat = new float[]{-5f,40f,10f,20f};


    public static float[] ShipCountStart = new float[]{85000f,90000f};
    public static float[] ShipCountEnd = new float[]{22000,70000f};

    public static float[] AggressiveScoreV2 = new float[]{0.65f,0.61f,0.57f,0.5f,0.5f,0.3f,0.3f,0.3f,0.3f,0.3f};

    public static float[] ShipCountMapSizeMultiplier = new float[]{0.90f,0.98f,1.05f,1.05f,1.25f};
    public static float[] ShipCountDensityMultiplier = new float[]{1.45f,1.1f,1.1f,1.15f};

    public static float[] EnemyShipCountStart = new float[]{15241.1492638483f,3000f};
    public static float[] EnemyShipCountEnd = new float[]{22000f,10000f};
//    public static float[] EnemyShipCountScaleTurns = new float[]{8f,15f};

    public static float[] EnemyShipCountControlZone = new float[]{1700f,100f};
    public static float[] EnemyShipHalite = new float[]{6.5f,1.3f}; //careful to not make this bigger than my own ship halite
    public static float[] EnemyShipTileHalite = new float[]{2.2f,1.9f};
    public static float[] EnemyShipCloseToMyDropoffWithHalite = new float[]{1500f,400f};
    public static float[] MoveCount = new float[]{2600f,6000f};
    public static float[] EnemyShipControlDifference = new float[]{14f,7f};

    public static float[] EnemyShipMultiplierBehind = new float[]{0.27f,0.8f};


    public static float[] ShipHalitePlayers = new float[]{0.93f,1.15f};
    public static float[] ShipHaliteSize = new float[]{0.8f,1.05f,1.05f,1.0f,1.1f};
    public static float[] ShipHaliteDensity = new float[]{0.92f,1.0f,0.92f,0.95f};
    public static float[] DropoffHardcap = new float[]{3,4,12,11,25};

    public static float[] LongDistNerfValueAroundEnemyShips = new float[]{0.40f,0.65f};

    public static float[] GoalWeightDistanceCenterEarlyV2 = new float[]{120f,200f};//new float[]{32f,85f};
    public static float[] TileWeightDistanceCenterEarly = new float[]{45f,30f};
    public static float[] GoalWeightDistanceCenterLateV2 =new float[]{10f,30f};
    public static float[] TileWeightDistanceCenterLate = new float[]{0f,10f};


    //Some variables the stats tool indicated were important across mapsizes, not always sure why / whether it's actually true
    public static float[] T0ImportanceFullSizeMult = new float[]{1.05f,1f,1f,1f,0.975f};
    public static float[] T1ImportanceFullSizeMult = new float[]{0.95f,0.95f,1f,0.95f,1f};
    public static float[] T2ImportanceFullSizeMult = new float[]{1f,1.1f,1.05f,1f,1f};
    public static float[] TOtherImportanceFullSizeMult = new float[]{1f,1f,1f,1f,1f};
    public static float[] GoalAverageHaliteNormalizeValSizeMult = new float[]{0.9f,1.1f,1.05f,1f,1.05f};
    public static float[] GoalWeightV6SizeMult = new float[]{1f,0.95f,1f,1f,1.1f};
    public static float[] DropoffGoalWeightV2SizeMult = new float[]{1.05f,1.05f,1f,1f,1.025f};
    public static float[] MinMultiplierEVSizeMult = new float[]{1.05f,0.95f,1f,1f,0.95f};
    public static float[] EVLessShipsSizeMult = new float[]{1.05f,1f,1f,1f,0.975f};
    public static float[] EVLeadShipsAndPointsV2SizeMult = new float[]{0.90f,1f,1f,1f,1.05f};
    public static float[] LongDistNerfValueAroundMyShipsSizeMult = new float[]{0.95f,1f,1f,1f,1.025f};
    public static float[] DropoffPunishmentCloseToEnemySizeMult = new float[]{0.95f,1.05f,1f,1f,1.025f};
    public static float[] MeaningfulHaliteV3SizeMult = new float[]{0.95f,1f,0.95f,1f,1f};
    public static float[] InspireScoreSizeMult = new float[]{0.9f,0.95f,1f,1f,1f};
    public static float[] TurnInScoreSizeMult = new float[]{1.05f,1f,1f,1f,0.95f};
    public static float[] IndividualMoveScoreSizeMult = new float[]{0.95f,1f,1f,1f,1.2f};
    public static float[] GatherScoreSizeMult = new float[]{0.96f,1f,0.95f,1f,0.975f};
    public static float[] LuresScoreSizeMult = new float[]{1f,1f,0.95f,1f,1.025f};
    public static float[] MiscScoreSizeMult = new float[]{1f,1f,1f,1f,1f};
    public static float[] WastePreventionSizeMult = new float[]{1f,1f,1f,1f,1.05f};



    public static float[] IndividualMoveScorePlayersMult = new float[]{0.95f,1f};
    public static float[] GatherScorePlayersMult = new float[]{1.05f,1f};
    public static float[] LuresScorePlayersMult = new float[]{1f,0.86f};
    public static float[] MiscScorePlayersMult = new float[]{1f,1f};
    public static float[] WastePreventionPlayersMult = new float[]{1f,1f};
    public static float[] GoalWeightV6PlayersMult = new float[]{1f,1f};
    public static float[] GoalAboutDenyingWeightPlayersMult = new float[]{1f,1.0f};
    public static float[] InspireScorePlayersMult = new float[]{0.6f,1.1f};
    public static float[] TurnInScorePlayersMult = new float[]{1.05f,1.05f};
    public static float[] DropoffWorthMultV2PlayersMult = new float[]{0.93f,1f};
    public static float[] GoalWeightUpcomingDropoffDistV2PlayersMult = new float[]{1.09f,1f};
    public static float[] T0ImportanceFullPlayersMult = new float[]{1.09660658532907f,1.02f};
    public static float[] T1ImportanceFullPlayersMult = new float[]{1.25f,1f};
    public static float[] T2ImportanceFullPlayersMult = new float[]{0.8f,1f};
    public static float[] TOtherImportanceFullPlayersMult = new float[]{0.96f,0.95f};
    public static float[] T0ImportancePlayersMult = new float[]{0.99f,1};
    public static float[] T1ImportancePlayersMult = new float[]{1.05328126602316f,1f};
    public static float[] T2ImportancePlayersMult = new float[]{0.983390273318943f,1f};
    public static float[] TOtherImportancePlayersMult = new float[]{0.989018160415515f,1f};
    public static float[] MaxSums2PlayersMult = new float[]{0.951455581485196f,1f};
    public static float[] MaxSums3PlayersMult = new float[]{0.7f,1f};
    public static float[] MaxSums4PlayersMult = new float[]{1.5f,1f};
    public static float[] MaxSumsCapPlayersMult = new float[]{1.2f,1f};


    public static float[] SpecialBunch1 = new float[]{0.0f,0.0f,0.0f,0.0f,0.0f,0.7f,1.7f,1.7f,0.1f,1.7f}; //{0.37f,0.38f,0.4f,0.45f,0.5f,0.3f,0.55f,0.7f,0.6f,0.62f};
    public static float[] SpecialBunchDensity = new float[]{0.95f,1f,1.05f,1.1f}; //{0.37f,0.38f,0.4f,0.45f,0.5f,0.3f,0.55f,0.7f,0.6f,0.62f};


    static HashMap<String,Float> FieldStepSizes = new HashMap<>();
    static HashMap<String,Float> OriginalValues = new HashMap<>();



    //An old and bad attempt and doing some automatic tweaking of variables
    //It's functional, but just doesn't deliver good results
    public static void AutoTrainingInit()  throws Throwable{

        if (MyBot.myId >= 2) return;// let's not train with these, biased since they never partake in 2s.
        // TODO: think of something clever that integrates these bots well, and allows bots to make use of some combined training files
        try {


            Field[] fields = HandwavyWeights.class.getDeclaredFields();
            for (Field f : fields) {
                if (f.getType().equals(float.class)) {
                    Float val = f.getFloat(null);
                    if (val != null) {
                        OriginalValues.put(f.getName(), val);

                        if (!FieldStepSizes.containsKey(f.getName())) {
                            float stepSize = Math.max(0.003f, Math.abs(val / 300f));

//                            if(MyBot.MINIMAL_CALCULATIONS){
//                                stepSize *= 0.6f;
//                            }
                            stepSize *= MyBot.AUTO_LEARNING_SPEED;

                            FieldStepSizes.put(f.getName(), stepSize);
                        }
                    }
                }
            }


            File folder = new File("C:\\Projects\\Halite\\AutoTraining");
            File[] files = folder.listFiles();
            long lastTime = Long.MIN_VALUE;
            File latestFile = null;
            for (File file : files) {
                if(file.length() > 100) {
                    String name = file.getName();
                    String[] parts = name.split("-");
                    if (parts.length > 1) {
                        String[] splitSecond = parts[1].trim().split("\\.");
                        if (Integer.parseInt(splitSecond[0].trim()) == MyBot.myId) {
                            long time = Long.parseLong(parts[0].trim());
                            if (time > lastTime) {
                                lastTime = time;
                                latestFile = file;
                            }
                        }
                    }
                }
            }

            if (latestFile != null) {
                BufferedReader br = new BufferedReader(new FileReader(latestFile));
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(":");

                    if(parts.length == 2) {
                        String variablename = parts[0].trim();
                        Field f = HandwavyWeights.class.getDeclaredField(variablename);

                        if (f != null) {
                            float variable = Float.parseFloat(parts[1].trim());

                            float trueOGVal = f.getFloat(null);


                            Log.log(f.getName() +": "   +   Math.min(Math.abs((variable - trueOGVal)), Math.abs((trueOGVal - variable)/  ((variable + trueOGVal) / 2f)))   +   "  abs: "    +  (variable - trueOGVal)  +    "   change: "  +  trueOGVal + " -> " + variable, Log.LogType.AUTOTRAINING_SUMMARY);

                            f.setFloat(null, variable);

                            OriginalValues.put(f.getName(), variable);
                        }
                    }
                    // process the line.
                }
            }
        } catch (Exception ex) {
            Log.exception(ex);
        }
    }


    public static void AutoTrainingFinish(float change) throws Throwable {
        try {

            for (String s : OriginalValues.keySet()) {
                Float stepsize = FieldStepSizes.get(s);
                if (stepsize != null) {


                    Field field = HandwavyWeights.class.getDeclaredField(s);
                    if (field != null) {
                        Float curVal = field.getFloat(null);
                        float originalVal = OriginalValues.get(s);
                        if(curVal > originalVal + 0.001f){
                            float newval = originalVal + stepsize * change;
                            Log.log(s + ":" + newval, Log.LogType.AUTOTRAINING);
                        } else if(curVal < originalVal - 0.001f){
                            float newval = originalVal + -1f * stepsize * change;
                            Log.log(s + ":" + newval, Log.LogType.AUTOTRAINING);
                        } else{
                            Log.log(s + ":" + originalVal, Log.LogType.AUTOTRAINING);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Log.exception(ex);
        }

    }


    //Randomize all variables in pre-defined ways
    //Sometimes a variable might be randomized multiple times, this is just to make it more likely that this
    //variable is randomized (it can also affect spread)
    public static void Randomize(){
        EVALRANDOMNESS = MultiplyByRange(EVALRANDOMNESS, 0.6f,1.5f);
        EVALRANDOMNESS = MultiplyByRange(EVALRANDOMNESS, 0.6f,1.5f);



        MoneyBurntV2 = AroundExpo(MoneyBurntV2, 0.3f);
        MoneyBurntV2 = MultiplyByRange(MoneyBurntV2,0.65f,1.4f);



        ShipCountStart = MultiplyByRange(ShipCountStart, 0.65f,1.4f);
        ShipCountStart = MultiplyByRange(ShipCountStart, 0.65f,1.4f);
        ShipCountStart = MultiplyByRange(ShipCountStart, 0.65f,1.4f);
        ShipCountEnd = MultiplyByRange(ShipCountEnd, 0.65f,1.4f);
        ShipCountEnd = MultiplyByRange(ShipCountEnd, 0.65f,1.4f);
        ShipCountEnd = MultiplyByRange(ShipCountEnd, 0.65f,1.4f);

        MoveCount = MultiplyByRange(MoveCount, 0.6f,2.5f);
        MoveCount = MultiplyByRange(MoveCount, 0.6f,2.5f);
        ShipCountMapSizeMultiplier = MultiplyByRange(ShipCountMapSizeMultiplier, 0.65f,1.4f);
        ShipCountDensityMultiplier = MultiplyByRange(ShipCountDensityMultiplier, 0.65f,1.4f);


       // ThreshLure = MultiplyByRange(ThreshLure, 0.6f,1.5f);
//        LureWeight = MultiplyByRange(LureWeight, 0.6f,1.5f);
//        LureWeight = MultiplyByRange(LureWeight, 0.6f,1.5f);

        Gather1V2 = MultiplyByRange(Gather1V2, 0.6f,1.5f);
        Gather1V2 = MultiplyByRange(Gather1V2, 0.6f,1.5f);
        Gather2V2 = MultiplyByRange(Gather2V2, 0.6f,1.5f);
        Gather3V2 = MultiplyByRange(Gather3V2, 0.6f,1.5f);
        Gather4V2 = MultiplyByRange(Gather4V2, 0.6f,1.5f);
        Gather5V2 = MultiplyByRange(Gather5V2, 0.6f,1.5f);
        GatherTotalV3 = MultiplyByRange(GatherTotalV3, 0.6f,1.5f);
        GatherTotalV3 = MultiplyByRange(GatherTotalV3, 0.6f,1.5f);


        RunLeftTimerV3 = Range(RunLeftTimerV3, 6,22);
        RunLeftTimerEnemyV2 = Range(RunLeftTimerEnemyV2, 4,15);
        RunCurV2 = MultiplyByRange(RunCurV2, 0.6f,1.7f);
        RunLeftFactor = Range(RunLeftFactor, 1f,1.4f);
        RunEnemyCurV2 = MultiplyByRange(RunEnemyCurV2, 0.6f,1.7f);




        Importance1V3 = MultiplyByRange(Importance1V3, 0.6f,1.4f);
        Importance1V3 = MultiplyByRange(Importance1V3, 0.6f,1.4f);
        Importance2V3 = MultiplyByRange(Importance2V3, 0.6f,1.4f);
        Importance2V3 = MultiplyByRange(Importance2V3, 0.6f,1.4f);
        Importance3V3 = MultiplyByRange(Importance3V3, 0.6f,1.4f);
        Importance4V3 = MultiplyByRange(Importance4V3, 0.6f,1.4f);
        Importance5V3 = MultiplyByRange(Importance5V3, 0.6f,1.7f);
        Importance5V3 = MultiplyByRange(Importance5V3, 0.6f,1.4f);

//        ImportanceOverallV3 = MultiplyByRange(ImportanceOverallV3, 0.6f,1.4f);
//        ImportanceOverallV3 = MultiplyByRange(ImportanceOverallV3, 0.6f,1.4f);


        PunishStandstillNoHaliteNormalV2 = Range(PunishStandstillNoHaliteNormalV2, 0,40);
        PunishStandstillNoHaliteNormalV2 = Range(PunishStandstillNoHaliteNormalV2, 0,300);
        PunishStandstillLowHaliteNormalV2 = Range(PunishStandstillLowHaliteNormalV2, 0,300);
        PunishStandstillLowHaliteNormalV2 = Range(PunishStandstillLowHaliteNormalV2, 40,200);

        PunishStandstillNoHaliteEmptyishV2 = Range(PunishStandstillNoHaliteEmptyishV2, 0,200);
        PunishStandstillNoHaliteEmptyishV2 = Range(PunishStandstillNoHaliteEmptyishV2, 0,50);
        PunishStandstillLowHaliteEmptyishV2 = Range(PunishStandstillLowHaliteEmptyishV2, 0,300);
        PunishStandstillLowHaliteEmptyishV2 = Range(PunishStandstillLowHaliteEmptyishV2, 40,200);

        PunishStandstillNoHaliteFullishV2 = Range(PunishStandstillNoHaliteFullishV2, 0,50);
        PunishStandstillNoHaliteFullishV2 = Range(PunishStandstillNoHaliteFullishV2, 0,300);
        PunishStandstillLowHaliteFullishV2 = Range(PunishStandstillLowHaliteFullishV2, 0,40);
        PunishStandstillLowHaliteFullishV2 = Range(PunishStandstillLowHaliteFullishV2, 0,200);




        BigTileV3 = Range(BigTileV3, 40,300);
        ExtraOnBigTileV3 = MultiplyByRange(ExtraOnBigTileV3, 0.5f,2f);
        HaliteBigTileMultiplierV4 = MultiplyByRange(HaliteBigTileMultiplierV4, 0.7f,1.4f);
        HaliteBigTileMultiplierV4 = MultiplyByRange(HaliteBigTileMultiplierV4, 0.7f,1.4f);


        HaliteMultiplierMoveTo = MultiplyByRange(HaliteMultiplierMoveTo, 0.7f,1.2f);
        HaliteMultiplierMoveTo = MultiplyByRange(HaliteMultiplierMoveTo, 0.7f,1.2f);
        HaliteMultiplierMoveToNoImp = MultiplyByRange(HaliteMultiplierMoveToNoImp, 0.5f,2f);
        HaliteMultiplierMoveToNoImp = MultiplyByRange(HaliteMultiplierMoveToNoImp, 0.5f,2f);
        HaliteMovepunisherV2 = MultiplyByRange(HaliteMovepunisherV2, 0.7f,1.6f);
        HaliteMovepunisherV2 = MultiplyByRange(HaliteMovepunisherV2, 0.7f,1.4f);
        HaliteMovepunisherV2 = MultiplyByRange(HaliteMovepunisherV2, 0.7f,1.4f);
        HaliteMultiplierStandStillV2 = MultiplyByRange(HaliteMultiplierStandStillV2, 0.5f,2f);



        EmptyishV2 = Range(EmptyishV2, 100,500);
        MeaningfulHaliteEmptyishV2 = MultiplyByRange(MeaningfulHaliteEmptyishV2, 0.3f,1.6f);
        MeaningfulHaliteEmptyishV2 = MultiplyByRange(MeaningfulHaliteEmptyishV2, 0.3f,1.6f);

        MeaningfulHaliteV3 = AroundExpo(MeaningfulHaliteV3,3f);
        MeaningfulHaliteV3 = MultiplyByRange(MeaningfulHaliteV3, 0.3f,3f);

        MeaningfulHalitePOW = MultiplyByRange(MeaningfulHalitePOW, 0.3f,3f);
        MeaningfulHaliteBASE = MultiplyByRange(MeaningfulHaliteBASE, 0.7f,1.3f);
        MeaningfulHaliteBASE = MultiplyByRange(MeaningfulHaliteBASE, 0.7f,1.3f);
        MeaningfulHaliteMin = MultiplyByRange(MeaningfulHaliteMin, 0.7f,1.3f);




        StepOnDropoffHalProbablyFine = MultiplyByRange(StepOnDropoffHalProbablyFine, 0.5f,2f);
        StepOnDropoffFlatV2 = MultiplyByRange(StepOnDropoffFlatV2, 0.5f,2f);
        AvoidDropoffV2 = MultiplyByRange(AvoidDropoffV2, 0.7f,1.3f);
        AvoidDropoffV2 = MultiplyByRange(AvoidDropoffV2, 0.7f,1.3f);

        AvoidDropoffZero = MultiplyByRange(AvoidDropoffZero, 0.7f,1.3f);
        AvoidDropoffZero = MultiplyByRange(AvoidDropoffZero, 0.7f,1.3f);

        StepOnDropoffIfWantToBuild = MultiplyByRange(StepOnDropoffIfWantToBuild, 0.7f,1.3f);
        StepOnDropoffIfWantToBuild = MultiplyByRange(StepOnDropoffIfWantToBuild, 0.7f,1.3f);

        MinHaliteTurnIn = MultiplyByRange(MinHaliteTurnIn, 0.7f,1.3f);
        MinHaliteTurnIn = MultiplyByRange(MinHaliteTurnIn, 0.7f,1.3f);


        BoostFor1000Early = MultiplyByRange(BoostFor1000Early, 0.5f,2f);
        BoostFor1000Early = MultiplyByRange(BoostFor1000Early, 0.5f,2f);

        BoostFor1000 = MultiplyByRange(BoostFor1000, 0.5f,2f);
        BoostFor1000 = MultiplyByRange(BoostFor1000, 0.5f,2f);

        PrioBoostNextToDropoffV2 = MultiplyByRange(PrioBoostNextToDropoffV2, 0.7f,1.3f);
        PrioBoostNextToDropoffV2 = MultiplyByRange(PrioBoostNextToDropoffV2, 0.7f,1.3f);
        PrioNextToEnemy = MultiplyByRange(PrioNextToEnemy, 0.7f,1.3f);
        PrioNextToEnemy = MultiplyByRange(PrioNextToEnemy, 0.7f,1.3f);
        PrioNearbyMyShips = MultiplyByRange(PrioNearbyMyShips, 0.7f,1.3f);
        PrioNearbyMyShips = MultiplyByRange(PrioNearbyMyShips, 0.7f,1.3f);
        PrioNearbyEnemyShips = MultiplyByRange(PrioNearbyEnemyShips, 0.7f,1.3f);
        PrioNearbyEnemyShips = MultiplyByRange(PrioNearbyEnemyShips, 0.7f,1.3f);


        PrioWeightHalite = Around(PrioWeightHalite, 0.7f);
        PrioWeightTileHalite = MultiplyByRange(PrioWeightTileHalite, 0.5f,2f);



        ShipHalite = MultiplyByRange(ShipHalite,0.7f,1.3f);
        FinalShipHaliteV3 = MultiplyByRange(FinalShipHaliteV3,0.3f,1.8f);
        FinalPlayerHaliteV3 = MultiplyByRange(FinalPlayerHaliteV3, 0.3f,1.8f);
        FinalStandOnHaliteV2 = MultiplyByRange(FinalStandOnHaliteV2, 0.3f,1.8f);
        FinalHaliteNearDropExceeds10V3 = MultiplyByRange(FinalHaliteNearDropExceeds10V3, 0.5f,2f);
        FinalHaliteDropCrossExceeds10V2 = MultiplyByRange(FinalHaliteDropCrossExceeds10V2, 0.5f,2f);


        ShipHalitePlayers = Range(ShipHalitePlayers,0.5f,2f);
        ShipHaliteSize = Range(ShipHaliteSize,0f,2.5f);
        ShipHaliteDensity = Range(ShipHaliteDensity,0.5f,2f);





        DEPTH_1 = MultiplyByRange(DEPTH_1, 0.7f,1.5f);
        DEPTH_2 = MultiplyByRange(DEPTH_2, 0.7f,1.5f);
        DEPTH_3 = MultiplyByRange(DEPTH_3, 0.7f,1.5f);
        DEPTH_4 = MultiplyByRange(DEPTH_4, 0.7f,1.5f);
        DEPTH_5 = MultiplyByRange(DEPTH_5, 0.7f,1.5f);
        DEPTH_6 = MultiplyByRange(DEPTH_6, 0.7f,1.5f);
        DEPTH_7 = MultiplyByRange(DEPTH_7, 0.7f,1.5f);
        DEPTH_8 = MultiplyByRange(DEPTH_8, 0.7f,1.5f);
        DEPTH_9 = MultiplyByRange(DEPTH_9, 0.7f,1.5f);
        DEPTH_10 = MultiplyByRange(DEPTH_10, 0.7f,1.5f);
        DEPTH_11 = MultiplyByRange(DEPTH_11, 0.7f,1.5f);
        DEPTH_12 = MultiplyByRange(DEPTH_12, 0.7f,1.5f);
        DEPTH_13 = MultiplyByRange(DEPTH_13, 0.7f,1.5f);
        DEPTH_14 = MultiplyByRange(DEPTH_14, 0.7f,1.5f);


//        DEPTH_1 = Range(DEPTH_1, 10,40);
//        DEPTH_2 = Range(DEPTH_2, 8,30);
//        DEPTH_3 = Range(DEPTH_3, 7,25);
//        DEPTH_4 = Range(DEPTH_4, 7,24);
//        DEPTH_5 = Range(DEPTH_5, 6,22);
//        DEPTH_6 = Range(DEPTH_6, 6,18);
//        DEPTH_7 = Range(DEPTH_7, 5,16);
//        DEPTH_8 = Range(DEPTH_8, 5,15);
//        DEPTH_9 = Range(DEPTH_9, 5,15);
//        DEPTH_10 = Range(DEPTH_10, 5,15);
//        DEPTH_11 = Range(DEPTH_11, 5,14);
//        DEPTH_12 = Range(DEPTH_12, 4,13);
//        DEPTH_13 = Range(DEPTH_13, 4,13);
//        DEPTH_14 = Range(DEPTH_14, 4,12);


        ELIM_FIRST_STAGE = Range(ELIM_FIRST_STAGE, 0,40);
        ELIM_FIRST_STAGE = Range(ELIM_FIRST_STAGE, 0,50);

        ADVANCERANDOMNESS = MultiplyByRange(ADVANCERANDOMNESS, 0.3f,2f);
        RANDOMNESSCYCLE = MultiplyByRange(RANDOMNESSCYCLE, 0.3f,2f);
        RANDOMNESSCYCLE = MultiplyByRange(RANDOMNESSCYCLE, 0.3f,2f);

        TURNBASEDRANDFACTOR1 = Range(TURNBASEDRANDFACTOR1, 5f,25f);
        TURNBASEDRANDFACTOR1 = Range(TURNBASEDRANDFACTOR1, 5f,40f);
        TURNBASEDRANDFACTOR2 = Range(TURNBASEDRANDFACTOR2, 0f,12f);
        TURNBASEDRANDFACTOR2 = Range(TURNBASEDRANDFACTOR2, 0f,15f);
        TURNBASEDRANDFACTOR3 = Range(TURNBASEDRANDFACTOR3, 0f,8f);
        TURNBASEDRANDFACTOR4 = Range(TURNBASEDRANDFACTOR4, 0f,6f);


        BANODDS = Range(BANODDS, 0f,1f);
        BANODDS = Range(BANODDS, 0f,1f);
        ADDDESIREODDS = Range(ADDDESIREODDS, 0f,1f);
        CHANCEADDSUGGESTIONS = Range(CHANCEADDSUGGESTIONS, 0f,1f);

        ADDDESIRE = Range(ADDDESIRE, 0f,10f);
        ADDDESIRERAND = Range(ADDDESIRERAND, 10f,40f);


        SUGGESTIONSFROMLASTTURN = Range(SUGGESTIONSFROMLASTTURN, 0f,40f);

        PRIORAND = Range(PRIORAND, 100,600);


        EVALEXTRARANDFACTOR = Range(EVALEXTRARANDFACTOR, 1,2);
        EVALEXTRARAND = Range(EVALEXTRARAND, 0,3);


        LureDistMod = Range(LureDistMod, 5,20);
        LureDistMod = Range(LureDistMod, 5,20);

        LongLureHalCenters = MultiplyByRange(LongLureHalCenters, 0.6f,1.8f);
        LongLureFlatHal = MultiplyByRange(LongLureFlatHal, 0.6f,1.8f);
        LongLureDistHal = Range(LongLureDistHal, 0f,1.2f);
        LongLureDistHal = Range(LongLureDistHal, 0.3f,2.0f);
        LongLureSpread = Range(LongLureSpread, 0.05f,0.19f);
        LongLureSpreadTurns = Range(LongLureSpreadTurns, 15,35);


        MedLureFlatHal = Range(MedLureFlatHal, 0.1f,0.9f);
        MedLureDistHal = Range(MedLureDistHal, 0f,1.2f);
        MedLureDistHal = Range(MedLureDistHal, 0.2f,1.8f);
        MedLureSpread = Range(MedLureSpread, 0.05f,0.2f);
        MedLureSpreadTurns = Range(MedLureSpreadTurns, 6,16);


        StopDropoffBuildingTurnsBeforeEnd = Range(StopDropoffBuildingTurnsBeforeEnd, 90,160);
        DropoffWorthMultiplier = MultiplyByRange(DropoffWorthMultiplier, 0.6f,1.8f);

        MapHaliteLeftMinV3 = Range(MapHaliteLeftMinV3, 0,80000f);
        enemyHpsFactorV2 = Range(enemyHpsFactorV2, 0.3f,1.5f);

        ShipsPerDropoffV2 = MultiplyByRange(ShipsPerDropoffV2, 0.6f,1.5f);
        ShipsPerDropoffV2 = MultiplyByRange(ShipsPerDropoffV2, 0.8f,1.2f);


        DropoffWeightTooFarMult = Around(DropoffWeightTooFarMult, 5f);


        MinStepsFromDropoff = Range(MinStepsFromDropoff, 10,19);
        MaxStepsFromDropoff = Around(MaxStepsFromDropoff, 7);
        MaxGainFromDistance = MultiplyByRange(MaxGainFromDistance, 0.6f,1.8f);
        MaxGainFromDistance2 = MultiplyByRange(MaxGainFromDistance2, 0.6f,1.8f);
        MinShipsInRegion = MultiplyByRange(MinShipsInRegion, 0.6f,1.8f);
        ProprtionMinEnemyNearish = MultiplyByRange(ProprtionMinEnemyNearish, 0.6f,1.8f);
        ProprtionMinEnemyNearish = MultiplyByRange(ProprtionMinEnemyNearish, 0.6f,1.8f);
        MinStepsFromDropoffTiles = MultiplyByRange(MinStepsFromDropoffTiles, 0.7f,1.4f);
        MinStepsFromDropoffTiles = MultiplyByRange(MinStepsFromDropoffTiles, 0.7f,1.4f);

        DropoffWeightContainsFriendlyShip = MultiplyByRange(DropoffWeightContainsFriendlyShip, 0.7f,1.4f);
        DropoffWeightContainsFriendlyShip = MultiplyByRange(DropoffWeightContainsFriendlyShip, 0.7f,1.4f);
        DropoffWeightFriendlyShipHalite = MultiplyByRange(DropoffWeightFriendlyShipHalite, 0.7f,1.4f);
        DropoffWeightFriendlyShipHalite = MultiplyByRange(DropoffWeightFriendlyShipHalite, 0.7f,1.4f);


        DropoffCloseToEnemyTiles = Range(DropoffCloseToEnemyTiles, 4,15);
        DropoffScoreTurnsFromEnemy = MultiplyByRange(DropoffScoreTurnsFromEnemy, 0.6f,1.8f);
        DropoffPunishmentCloseToEnemy = MultiplyByRange(DropoffPunishmentCloseToEnemy, 0.6f,1.8f);
        DropoffScoreTurnsFromEnemyNormalize = MultiplyByRange(DropoffScoreTurnsFromEnemyNormalize, 0.6f,1.8f);
        DropoffCloseToEnemy = Range(DropoffCloseToEnemy, 4,11);




        DropoffWeightShipHalNear = MultiplyByRange(DropoffWeightShipHalNear, 0.6f,1.8f);
        DropoffWeightShipHalNearish = MultiplyByRange(DropoffWeightShipHalNearish, 0.6f,1.8f);
        DropoffWeightShipsNear = MultiplyByRange(DropoffWeightShipsNear, 0.6f,1.8f);
        DropoffWeightShipsNearish = MultiplyByRange(DropoffWeightShipsNearish, 0.6f,1.8f);
        DropoffWeightEnemyShipsNearish = Range(DropoffWeightEnemyShipsNearish, -40f,20f);
        DropoffWeightDistV2 = Range(DropoffWeightDistV2, 5,45);
        DropoffWeightMedLure = Range(DropoffWeightMedLure, 0,5);
        DropoffWeightLongLure = Range(DropoffWeightLongLure, 0,5);
        DropoffWeightHalOnSpot = Range(DropoffWeightHalOnSpot, 0,5);
        DropoffWeightConsistentSelection = MultiplyByRange(DropoffWeightConsistentSelection, 0.6f,1.8f);
        DropoffWeightHalNear = MultiplyByRange(DropoffWeightHalNear, 0.3f,3f);
        DropoffWeightHalNearish = MultiplyByRange(DropoffWeightHalNearish, 0.3f,3f);



        AlwaysAllowShipGameLength = Range(AlwaysAllowShipGameLength, 0.1f,0.45f);

        StopTurnsBeforeEndMinShips = Range(StopTurnsBeforeEndMinShips, 50,250);


        DropoffDenyMultiplier = MultiplyByRange(DropoffDenyMultiplier, 0.6f,1.8f);
        DropoffDenyMultiplier = MultiplyByRange(DropoffDenyMultiplier, 0.6f,1.8f);
        DropoffLowPrioZoneFlat = MultiplyByRange(DropoffLowPrioZoneFlat, 0.6f,1.8f);
        DropoffLowPrioZoneFlat = MultiplyByRange(DropoffLowPrioZoneFlat, 0.6f,1.8f);
        DropoffLowPrioZoneHalite = MultiplyByRange(DropoffLowPrioZoneHalite, 0.6f,1.8f);
        DropoffLowPrioZoneHalite = MultiplyByRange(DropoffLowPrioZoneHalite, 0.6f,1.8f);
        DropoffCentralFlat = MultiplyByRange(DropoffCentralFlat, 0.6f,1.8f);
        DropoffCentralFlat = MultiplyByRange(DropoffCentralFlat, 0.6f,1.8f);
        DropoffCentralHalite = MultiplyByRange(DropoffCentralHalite, 0.6f,1.8f);
        DropoffCentralHalite = MultiplyByRange(DropoffCentralHalite, 0.6f,1.8f);



        MinimalShips = Range(MinimalShips, 0,30);

        MinHalLeftOnMapForShipV3 = Range(MinHalLeftOnMapForShipV3, 0,50000);
        MaxAllowShips = MultiplyByRange(MaxAllowShips, 0.8f,1.25f);
        MaxAllowShips = MultiplyByRange(MaxAllowShips, 0.8f,1.25f);
        MaxAllowShips = MultiplyByRange(MaxAllowShips, 0.9f,1.1f);

        OverrideMaxAllowIfBelow = Range(OverrideMaxAllowIfBelow, 0,15);
        OverrideTimeLimit = Range(OverrideTimeLimit, 40,120);


        AlwaysWorthShipCount = Range(AlwaysWorthShipCount, 7,30);
        WorthMultiplierMult = Range(WorthMultiplierMult, 0.85f,1.2f);
        WorthMultiplierMult = Range(WorthMultiplierMult, 0.85f,1.2f);
        WorthMultiplierMult = Range(WorthMultiplierMult, 0.85f,1.2f);
        WorthMultiplier = Range(WorthMultiplier, 0.85f,1.15f);
        WorthMultiplier = Range(WorthMultiplier, 0.8f,1.2f);
        WorthMultiplierDensity = MultiplyByRange(WorthMultiplierDensity, 0.8f,1.3f);
        WorthMultiplierDensity = MultiplyByRange(WorthMultiplierDensity, 0.7f,1.4f);


        PlayerHaliteT1V2 = MultiplyByRange(PlayerHaliteT1V2, 0.6f,1.5f);
        PlayerHaliteT2V2 = MultiplyByRange(PlayerHaliteT2V2, 0.6f,1.5f);
        PlayerHaliteT3V2 = MultiplyByRange(PlayerHaliteT3V2, 0.6f,1.5f);
        PlayerHaliteT4V2 = MultiplyByRange(PlayerHaliteT4V2, 0.6f,1.5f);

        PlayerHaliteUnBound = Range(PlayerHaliteUnBound, 0f,1f);
        PlayerHaliteEarlyV3 = MultiplyByRange(PlayerHaliteEarlyV3, 0.6f,1.5f);
        PlayerHaliteMediumV3 = MultiplyByRange(PlayerHaliteMediumV3, 0.6f,1.5f);
        PlayerHaliteLateV3 = MultiplyByRange(PlayerHaliteLateV3, 0.6f,1.5f);
        PlayerHaliteVeryLateV3 = MultiplyByRange(PlayerHaliteVeryLateV3, 0.6f,1.5f);


        DenyRelevanceEra0 = Around(DenyRelevanceEra0, 0.3f);
        DenyRelevanceEra1 = Around(DenyRelevanceEra1, 0.3f);
        DenyRelevanceEra2 = Around(DenyRelevanceEra2, 0.3f);
        DenyRelevanceEra3 = Around(DenyRelevanceEra3, 0.3f);
        DenyRelevanceEra4 = Around(DenyRelevanceEra4, 0.3f);
        DenyRelevanceEra5 = Around(DenyRelevanceEra5, 0.3f);
        DenyRelevanceEra6 = Around(DenyRelevanceEra6, 0.3f);
        DenyRelevanceEra7 = Around(DenyRelevanceEra7,0.3f);
        DenyRelevanceEra0 = MultiplyByRange(DenyRelevanceEra0, 0.6f,1.5f);
        DenyRelevanceEra1 = MultiplyByRange(DenyRelevanceEra1, 0.6f,1.5f);
        DenyRelevanceEra2 = MultiplyByRange(DenyRelevanceEra2, 0.6f,1.5f);
        DenyRelevanceEra3 = MultiplyByRange(DenyRelevanceEra3, 0.6f,1.5f);
        DenyRelevanceEra4 = MultiplyByRange(DenyRelevanceEra4, 0.6f,1.5f);
        DenyRelevanceEra5 = MultiplyByRange(DenyRelevanceEra5, 0.6f,1.5f);
        DenyRelevanceEra6 = MultiplyByRange(DenyRelevanceEra6, 0.6f,1.5f);
        DenyRelevanceEra7 = MultiplyByRange(DenyRelevanceEra7, 0.6f,1.5f);





        EstGuaranteedCollect = Range(EstGuaranteedCollect, 40f,400f);
        EstShipmoveRate = Range(EstShipmoveRate, 0f,0.15f);
        TrustInInspirePredictionV3 = MultiplyByRange(TrustInInspirePredictionV3, 0.8f,1.2f);
        TrustInInspirePredictionV3 = MultiplyByRange(TrustInInspirePredictionV3, 0.8f,1.2f);
        MinOdds = MultiplyByRange(MinOdds, 0.8f,1.2f);
        MinOdds = MultiplyByRange(MinOdds, 0.8f,1.2f);
        MinOdds = MultiplyByRange(MinOdds, 0.8f,1.2f);
        TrustInInspirePredictionNothingV2 = Range(TrustInInspirePredictionNothingV2, 0.7f,1.0f);
        MaxChanceStandstill = Range(MaxChanceStandstill, 0.3f,1.0f);

        InspireHaliteMultV4NoTImportance = MultiplyByRange(InspireHaliteMultV4NoTImportance, 0.5f,1.5f);
        InspireHaliteMultV4NoTImportance = MultiplyByRange(InspireHaliteMultV4NoTImportance, 0.8f,1.2f);
        InspireHaliteMultV4NoTImportance = MultiplyByRange(InspireHaliteMultV4NoTImportance, 0.8f,1.2f);
        InspireHaliteMultV4 = MultiplyByRange(InspireHaliteMultV4, 0.8f,1.2f);
        InspireHaliteMultV4 = MultiplyByRange(InspireHaliteMultV4, 0.8f,1.2f);
        InspireHaliteMultV4 = MultiplyByRange(InspireHaliteMultV4, 0.8f,1.2f);
        InspireFlatV4NoTImportance = MultiplyByRange(InspireFlatV4NoTImportance, 0.5f,1.5f);
        InspireFlatV4NoTImportance = MultiplyByRange(InspireFlatV4NoTImportance, 0.8f,1.2f);
        InspireFlatV4NoTImportance = MultiplyByRange(InspireFlatV4NoTImportance, 0.8f,1.2f);
        InspireFlatV4 = MultiplyByRange(InspireFlatV4, 0.8f,1.2f);
        InspireFlatV4 = MultiplyByRange(InspireFlatV4, 0.8f,1.2f);
        InspireFlatV4 = MultiplyByRange(InspireFlatV4, 0.8f,1.2f);


        InspireFlatMovePunish = MultiplyByRange(InspireFlatMovePunish, 0.8f,1.2f);
        InspireFlatMovePunish = MultiplyByRange(InspireFlatMovePunish, 0.8f,1.2f);
        InspireFlatMovePunish = MultiplyByRange(InspireFlatMovePunish, 0.8f,1.2f);
        InspireFlatMult = MultiplyByRange(InspireFlatMult, 0.8f,1.2f);
        InspireFlatMult = MultiplyByRange(InspireFlatMult, 0.8f,1.2f);
        InspireFlatMult = MultiplyByRange(InspireFlatMult, 0.8f,1.2f);
        InspireMultV2 = MultiplyByRange(InspireMultV2, 0.8f,1.2f);
        InspireMultV2 = MultiplyByRange(InspireMultV2, 0.8f,1.2f);
        InspireMultV2 = MultiplyByRange(InspireMultV2, 0.8f,1.2f);
        InspireMultMovePunish = MultiplyByRange(InspireMultMovePunish, 0.8f,1.2f);
        InspireMultMovePunish = MultiplyByRange(InspireMultMovePunish, 0.8f,1.2f);
        InspireMultMovePunish = MultiplyByRange(InspireMultMovePunish, 0.8f,1.2f);

        InspireGuaranteedNextHalite = MultiplyByRange(InspireGuaranteedNextHalite, 0.7f,1.3f);
        InspireGuaranteedNextHalite = MultiplyByRange(InspireGuaranteedNextHalite, 0.7f,1.3f);
        InspireGuaranteedNextFlat = MultiplyByRange(InspireGuaranteedNextFlat, 0.7f,1.3f);
        InspireGuaranteedNextFlat = MultiplyByRange(InspireGuaranteedNextFlat, 0.7f,1.3f);


        ActivateRuleOfXMinHalite = MultiplyByRange(ActivateRuleOfXMinHalite, 0.7f,1.3f);
        ActivateRuleOfXMinHalite = MultiplyByRange(ActivateRuleOfXMinHalite, 0.7f,1.3f);
        ActivateRuleOfXFactorAvg = MultiplyByRange(ActivateRuleOfXFactorAvg, 0.7f,1.3f);
        ActivateRuleOfXFactorAvg = MultiplyByRange(ActivateRuleOfXFactorAvg, 0.7f,1.3f);
        ActivateRuleOfThreeTileBetterThanV2 = MultiplyByRange(ActivateRuleOfThreeTileBetterThanV2, 0.7f,1.3f);
        ActivateRuleOfThreeTileBetterThanV2 = MultiplyByRange(ActivateRuleOfThreeTileBetterThanV2, 0.7f,1.3f);
        ActivateRuleOfTwoTileBetterThan = MultiplyByRange(ActivateRuleOfTwoTileBetterThan, 0.7f,1.3f);
        ActivateRuleOfTwoTileBetterThan = MultiplyByRange(ActivateRuleOfTwoTileBetterThan, 0.7f,1.3f);
        ActivateRuleOfXBelowShipHalite = MultiplyByRange(ActivateRuleOfXBelowShipHalite, 0.7f,1.3f);
        ActivateRuleOfXBelowShipHalite = MultiplyByRange(ActivateRuleOfXBelowShipHalite, 0.7f,1.3f);
        RuleOfThreeWeightV2 = MultiplyByRange(RuleOfThreeWeightV2, 0.65f,1.4f);
        RuleOfThreeWeightV2 = MultiplyByRange(RuleOfThreeWeightV2, 0.65f,1.4f);
        RuleOfThreeWeightV2 = MultiplyByRange(RuleOfThreeWeightV2, 0.65f,1.4f);
        RuleOfTwoWeightV2 = MultiplyByRange(RuleOfTwoWeightV2, 0.65f,1.4f);
        RuleOfTwoWeightV2 = MultiplyByRange(RuleOfTwoWeightV2, 0.65f,1.4f);
        RuleOfTwoWeightV2 = MultiplyByRange(RuleOfTwoWeightV2, 0.65f,1.4f);


        FollowFuturePathV2 = MultiplyByRange(FollowFuturePathV2, 0.65f,1.4f);
        FollowFuturePathV2 = MultiplyByRange(FollowFuturePathV2, 0.65f,1.4f);
        FollowFuturePathV2 = MultiplyByRange(FollowFuturePathV2, 0.65f,1.4f);



        AvoidELocMove = Around(AvoidELocMove, 50f);
        AvoidELocMove = MultiplyByRange(AvoidELocMove,0.65f,1.4f);

        AvoidELocHalMoveEndGame = MultiplyByRange(AvoidELocHalMoveEndGame, 0.65f,1.4f);
        AvoidELocHalMoveEndGame = MultiplyByRange(AvoidELocHalMoveEndGame, 0.65f,1.4f);
        AvoidELocHalMove = MultiplyByRange(AvoidELocHalMove, 0.65f,1.4f);
        AvoidELocHalMove = MultiplyByRange(AvoidELocHalMove, 0.65f,1.4f);
        AvoidELocHalControlMove = MultiplyByRange(AvoidELocHalControlMove, 0.65f,1.4f);
        AvoidELocHalControlMove = MultiplyByRange(AvoidELocHalControlMove, 0.65f,1.4f);
        AvoidEOddsMove = Range(AvoidEOddsMove, AvoidEOddsMove[MyBot.GAMETYPE_PLAYERS], AvoidEOddsMove[MyBot.GAMETYPE_PLAYERS] + 10f);
        AvoidEOddsMove = MultiplyByRange(AvoidEOddsMove, 0.5f,2f);


        AvoidELocStand = Around(AvoidELocStand, 50f);
        AvoidELocStand = MultiplyByRange(AvoidELocStand, 0.65f,1.4f);
        AvoidELocStand = MultiplyByRange(AvoidELocStand, 0.65f,1.4f);

        AvoidELocHalStandEndGame = MultiplyByRange(AvoidELocHalStandEndGame, 0.65f,1.4f);
        AvoidELocHalStandEndGame = MultiplyByRange(AvoidELocHalStandEndGame, 0.65f,1.4f);
        AvoidELocHalStand = MultiplyByRange(AvoidELocHalStand, 0.65f,1.4f);
        AvoidELocHalStand = MultiplyByRange(AvoidELocHalStand, 0.65f,1.4f);


        AvoidELocHalControlStand = MultiplyByRange(AvoidELocHalControlStand, 0.65f,1.4f);
        AvoidELocHalControlStand = MultiplyByRange(AvoidELocHalControlStand, 0.65f,1.4f);
        AvoidELocStand = Around(AvoidELocStand, 10f);

        AvoidEOddsStand = MultiplyByRange(AvoidEOddsStand, 0.5f,2f);

//        ELocHigh = Range(ELocHigh, 0.2f,0.9f);
//        AvoidELocIfHigh = Range(AvoidELocIfHigh, 0f,200f);




        Step0KillDesire2pControl = MultiplyByRange(Step0KillDesire2pControl, 0.5f,2f);
        Step0KillDesire2pControl = MultiplyByRange(Step0KillDesire2pControl, 0.5f,2f);
        Step0KillDesire2pV2 = MultiplyByRange(Step0KillDesire2pV2, 0.5f,2f);
        Step0KillDesire4pV2 = MultiplyByRange(Step0KillDesire4pV2, 0.6f,1.5f);
        Step0KillDesire4pV2 = MultiplyByRange(Step0KillDesire4pV2, 0.6f,1.5f);
        Step0KillDesire4pV2 = MultiplyByRange(Step0KillDesire4pV2, 0.6f,1.5f);
        Step0KillDesireHalite2pV2 = MultiplyByRange(Step0KillDesireHalite2pV2, 0.5f,2f);
        Step0KillDesireHalite4pV2 = MultiplyByRange(Step0KillDesireHalite4pV2, 0.5f,2f);
        Step0AvoidRunUntoHal4p = MultiplyByRange(Step0AvoidRunUntoHal4p, 0.5f,1.6f);
        Step0AvoidRunUntoHal4p = MultiplyByRange(Step0AvoidRunUntoHal4p, 0.5f,1.6f);
        Step0AvoidCurEnemySpot = MultiplyByRange(Step0AvoidCurEnemySpot, 0.5f,1.6f);
        Step0AvoidCurEnemySpot = MultiplyByRange(Step0AvoidCurEnemySpot, 0.5f,1.6f);
        Step0AvoidRunUntoHal2p = MultiplyByRange(Step0AvoidRunUntoHal2p, 0.5f,1.6f);
        Step0AvoidRunUntoHal2p = MultiplyByRange(Step0AvoidRunUntoHal2p, 0.5f,1.6f);


        ShipHardCap = Range(ShipHardCap, 80,140);



        LongDistNerfValueAroundMyShips = MultiplyByRange(LongDistNerfValueAroundMyShips,0.5f,1.5f);
        LongDistNerfValueAroundMyShips = MultiplyByRange(LongDistNerfValueAroundMyShips,0.5f,1.5f);
        LongDistNerfValueAroundEnemyShips = Around(LongDistNerfValueAroundEnemyShips, 0.3f);
        LongDistNerfRadiusV2 = Range(LongDistNerfRadiusV2, 0,1);


        MedDistNerfValueAroundMyShips = Range(MedDistNerfValueAroundMyShips, 0f,1f);
        MedDistNerfValueAroundEnemyShips = Range(MedDistNerfValueAroundEnemyShips, 0f,1f);
        MedDistNerfRadius = Range(MedDistNerfRadius, 0,4);


        LongDistNerfRadiusEnemyV2 = Range(LongDistNerfRadiusEnemyV2, 0,1);
        LongLureTrustInspireV2 = MultiplyByRange(LongLureTrustInspireV2, 0.7f,1.3f);




        GoalWeightDistanceCenterEarlyV2 = MultiplyByRange(GoalWeightDistanceCenterEarlyV2, 0.7f,1.3f);
        GoalWeightDistanceCenterEarlyV2 = MultiplyByRange(GoalWeightDistanceCenterEarlyV2, 0.7f,1.3f);
        TileWeightDistanceCenterEarly = MultiplyByRange(TileWeightDistanceCenterEarly, 0.7f,1.3f);
        TileWeightDistanceCenterEarly = MultiplyByRange(TileWeightDistanceCenterEarly, 0.7f,1.3f);
        TileWeightDistanceCenterLate = MultiplyByRange(TileWeightDistanceCenterLate, 0.7f,1.3f);
        TileWeightDistanceCenterLate = MultiplyByRange(TileWeightDistanceCenterLate, 0.7f,1.3f);
        GoalWeightDistanceCenterLateV2 = MultiplyByRange(GoalWeightDistanceCenterLateV2, 0.7f,1.3f);
        GoalWeightDistanceCenterLateV2 = MultiplyByRange(GoalWeightDistanceCenterLateV2, 0.7f,1.3f);




        ShipsAheadToBeBehind = Range(ShipsAheadToBeBehind, -5,5);
        behindIn2pWorthBuilding = Range(behindIn2pWorthBuilding, 1f,2f);

//        TURNIN_THRESH = Range(TURNIN_THRESH, 600,900);
        TURNIN_AFTER_THRESH = Range(TURNIN_AFTER_THRESH, 100,500);
        DROP_DIST_CHECK = Range(DROP_DIST_CHECK, 4,12);

        DROP_DIST_FACTOR = MultiplyByRange(DROP_DIST_FACTOR, 0.6f,1.7f);
        DROP_DIST_FACTOR = MultiplyByRange(DROP_DIST_FACTOR, 0.6f,1.7f);
        DROP_DIST_CUR_TIER1_V3 = Range(DROP_DIST_CUR_TIER1_V3, 0,500);
        DROP_DIST_CUR_TIER2_V3 = Range(DROP_DIST_CUR_TIER2_V3, 400,850);
        DROP_DIST_CUR_TIER3_V3 = Range(DROP_DIST_CUR_TIER3_V3, 800,970);
        DROP_DIST_CUR_TIER4_V3 = Range(DROP_DIST_CUR_TIER4_V3, 920,1000);

        DROP_DIST_BASE_DIST_CUR_V4 = MultiplyByRange(DROP_DIST_BASE_DIST_CUR_V4, 0.6f,1.7f);

        DROP_DIST_MAX_PERDISTFACTOR = MultiplyByRange(DROP_DIST_MAX_PERDISTFACTOR, 0.6f,1.7f);

        DROP_DIST_WEIGHT = MultiplyByRange(DROP_DIST_WEIGHT, 0.7f,1.3f);
        DROP_DIST_WEIGHT = MultiplyByRange(DROP_DIST_WEIGHT, 0.7f,1.3f);
        DROP_DIST_MIN_AVG_BASED = MultiplyByRange(DROP_DIST_MIN_AVG_BASED, 0.7f,1.3f);
        DROP_DIST_MINDIST = Range(DROP_DIST_MINDIST, 400f,920f);
        DROP_DIST_MINDIST = Range(DROP_DIST_MINDIST, 500f,920f);

        DROP_DIST_DIST_POW = Range(DROP_DIST_DIST_POW, 0.8f,1f);
        DROP_DIST_EXPO_POW = Range(DROP_DIST_EXPO_POW, 1f,2f);




        DROP_DIST_CUR_MULT1_V3 = MultiplyByRange(DROP_DIST_CUR_MULT1_V3, 0.5f,1.5f);
        DROP_DIST_CUR_MULT2_V3 = MultiplyByRange(DROP_DIST_CUR_MULT2_V3, 0.5f,1.5f);
        DROP_DIST_CUR_MULT3_V3 = MultiplyByRange(DROP_DIST_CUR_MULT3_V3, 0.5f,1.7f);
        DROP_DIST_CUR_MULT4_V3 = MultiplyByRange(DROP_DIST_CUR_MULT4_V3, 0.5f,1.7f);


        DropoffHardcap = Range(DropoffHardcap, 3,15);
        DropoffHardcap = MultiplyByRange(DropoffHardcap, 0.75f,1.25f);

        IndividualMoveScore = AroundExpo(IndividualMoveScore, 0.3f);
        IndividualMoveScore = AroundExpo(IndividualMoveScore, 0.3f);
        GatherScore = AroundExpo(GatherScore, 0.5f);
        WastePreventionScore = AroundExpo(WastePreventionScore, 0.5f);
        MiscScore = AroundExpo(MiscScore, 0.5f);
        TurnInScore = AroundExpo(TurnInScore, 0.5f);
        LuresScore = AroundExpo(LuresScore, 0.5f);
        ShipHaliteScore = AroundExpo(ShipHaliteScore, 0.5f);
        InspireScore = AroundExpo(InspireScore, 0.5f);
        InspireScore = AroundExpo(InspireScore, 0.5f);

        AggressiveScoreV2 = Range(AggressiveScoreV2,0.2f,1.6f);

        HaliteMovepunisherNoImpV2 = AroundExpo(HaliteMovepunisherNoImpV2, 0.9f);
        FlatMovePunisherV2 = MultiplyByRange(FlatMovePunisherV2, 0.75f,1.25f);
        FlatMovePunisherV2 = MultiplyByRange(FlatMovePunisherV2, 0.75f,1.25f);
        FlatMovePunisherV2 = MultiplyByRange(FlatMovePunisherV2, 0.75f,1.25f);
        FlatMovePunisherNoImp = MultiplyByRange(FlatMovePunisherNoImp, 0.3f,3f);
        PunishBackAndForth = MultiplyByRange(PunishBackAndForth, 0.3f,2f);
        PunishBackAndForth = MultiplyByRange(PunishBackAndForth, 0.75f,1.25f);
        PunishBackAndForth = MultiplyByRange(PunishBackAndForth, 0.75f,1.25f);
        PunishBackAndForthTImportance = MultiplyByRange(PunishBackAndForthTImportance, 0.3f,3f);



        GuessShipHalitePerTurnAverageV2 = MultiplyByRange(GuessShipHalitePerTurnAverageV2, 0.75f,1.25f);
        TimeFactorHpsInitial = MultiplyByRange(TimeFactorHpsInitial, 0.75f,1.25f);
        TimeFactorHpsReducing = MultiplyByRange(TimeFactorHpsReducing, 0.75f,1.25f);
        ExpectedShipValueCeaseGather = MultiplyByRange(ExpectedShipValueCeaseGather, 0.75f,1.25f);
        MinGatherRate = MultiplyByRange(MinGatherRate, 0.75f,1.25f);
        EVNothingLeftToEat1 = Range(EVNothingLeftToEat1, 0f,0.2f);
        EVNothingLeftToEat2 = Range(EVNothingLeftToEat2, 0f,0.4f);
        EVNothingLeftToEat3 = Range(EVNothingLeftToEat3, 0.1f,0.5f);
        EVNothingLeftToEat4 = Range(EVNothingLeftToEat4, 0.1f,0.7f);
        EVNothingLeftToEat5 = Range(EVNothingLeftToEat5, 0.3f,0.8f);
        EVNothingLeftToEat6 = Range(EVNothingLeftToEat6, 0.5f,1f);
        EVNothingLeftToEat7 = Range(EVNothingLeftToEat7, 0.6f,1f);





        EVOvertakeV2 = MultiplyByRange(EVOvertakeV2, 0.75f,1.25f);
        EVLessShips = MultiplyByRange(EVLessShips, 0.75f,1.25f);
        EVLeadingByALittle = MultiplyByRange(EVLeadingByALittle, 0.9f,1.2f);
        EVLeadShipsAndPointsV2 = MultiplyByRange(EVLeadShipsAndPointsV2, 0.75f,1.25f);
        EVLeadShipsV2 = MultiplyByRange(EVLeadShipsV2, 0.75f,1.25f);
        EVTimeFactorMultiplier = MultiplyByRange(EVTimeFactorMultiplier, 0.75f,1.25f);



        TurnInSpeed = MultiplyByRange(TurnInSpeed, 0.75f,1.25f);
        TurnInSpeed = MultiplyByRange(TurnInSpeed, 0.75f,1.25f);
        TurnInSpeed = MultiplyByRange(TurnInSpeed, 0.75f,1.25f);

        TurnInSpeedHalite = MultiplyByRange(TurnInSpeedHalite, 0.75f,1.25f);
        TurnInSpeedHalite = MultiplyByRange(TurnInSpeedHalite, 0.75f,1.25f);
        TurnInSpeedHalite = MultiplyByRange(TurnInSpeedHalite, 0.75f,1.25f);

        EnemyShipTimesScary2p = Range(EnemyShipTimesScary2p, 0,100);
        EnemyShipTimesScary4p = Range(EnemyShipTimesScary4p, 0,100);
        EnemyShipTimesScaryHal2p = MultiplyByRange(EnemyShipTimesScaryHal2p, 0.7f,1.3f);
        EnemyShipTimesScaryHal4p = MultiplyByRange(EnemyShipTimesScaryHal4p, 0.7f,1.3f);


        cumulativeInspireHalite = MultiplyByRange(cumulativeInspireHalite, 0.7f,1.3f);
        cumulativeInspireHalite = MultiplyByRange(cumulativeInspireHalite, 0.7f,1.3f);
        cumulativeInspireFlat = MultiplyByRange(cumulativeInspireFlat, 0.7f,1.3f);
        cumulativeInspireFlat = MultiplyByRange(cumulativeInspireFlat, 0.7f,1.3f);


        InspiredEnemyShipTimesScary = MultiplyByRange(InspiredEnemyShipTimesScary, 0.7f,1.3f);
        InspiredEnemyShipTimesScary = MultiplyByRange(InspiredEnemyShipTimesScary, 0.7f,1.3f);
        InspiredEnemyShipTimesScaryHal = MultiplyByRange(InspiredEnemyShipTimesScaryHal, 0.7f,1.3f);
        InspiredEnemyShipTimesScaryHal = Around(InspiredEnemyShipTimesScaryHal, 1.3f);
        InspiredEnemyShip = MultiplyByRange(InspiredEnemyShip, 0.7f,1.3f);
        InspiredEnemyShipHal = Around(InspiredEnemyShipHal, 2.5f);


        LureV3 = Range(LureV3, 0f,2);
        LureV3 = MultiplyByRange(LureV3, 0.3f,2f);

        LongLureV2 = Range(LongLureV2, 0f,3f);
        LongLureV2 = MultiplyByRange(LongLureV2, 0.3f,2f);

        MedLureV2 = Range(MedLureV2, 0f,10f);
        MedLureV2 = MultiplyByRange(MedLureV2, 0.3f,2f);

        LureEmptyishV3 = Range(LureEmptyishV3, 0f,4f);
        LureEmptyishV3 = Range(LureEmptyishV3, 0f,2f);

        LongLureEmptyishV2 = Range(LongLureEmptyishV2, 0f,10f);
        LongLureEmptyishV2 = MultiplyByRange(LongLureEmptyishV2, 0.3f,3f);

        MedLureEmptyishV2 = Range(MedLureEmptyishV2, 0f,4f);
        MedLureEmptyishV2 = Range(MedLureEmptyishV2, 0f,2f);
       // MedLureEmptyishV2 = MultiplyByRange(MedLureEmptyishV2, 0.3f,3f);

        EmptyishLuresV2 = Range(EmptyishLuresV2, 0,500);

        DropoffWeightHalLeftOnMap = MultiplyByRange(DropoffWeightHalLeftOnMap, 0.3f,3f);
        DropoffBaseMultV2 = MultiplyByRange(DropoffBaseMultV2, 0.3f,3f);


        metaNewInspireStuff = MultiplyByRange(metaNewInspireStuff, 0.3f,2f);


        ActivateGatherMechanic = Range(ActivateGatherMechanic, 0,1);
        ActivateFinalInspireAnalysis = Range(ActivateFinalInspireAnalysis, 0,2);
//        ActivateChangeImportanceV3 = Range(ActivateChangeImportanceV3, 0,1);
        ActivateenemyPredictions = Range(ActivateenemyPredictions, 0,1);
        AllowEShipsInSimulation = Range(AllowEShipsInSimulation, 0,1);
        ActivateCollisionMechanic = Range(ActivateCollisionMechanic, 0,1);
        ActivateComplexPrediction = Range(ActivateComplexPrediction, 0,1);
        ActivateWeirdAlgo = Range(ActivateWeirdAlgo, 0,1);
        ActivateFuturePathing = Range(ActivateFuturePathing, 0,1);
        ActivateFuturePathingNonReturn = Range(ActivateFuturePathingNonReturn, 0,1);
        ActivateSimulJourney = Range(ActivateSimulJourney, 0,1);
        ActivateAnnoyMechanic = Range(ActivateAnnoyMechanic, 0,1);
        ActivateEndGameDropoffBlocks = Range(ActivateEndGameDropoffBlocks, 0,1);
        ActivateEntrapment = Range(ActivateEntrapment, 0,1);
        ActivateDenyGoals = Range(ActivateDenyGoals, 0,1);
        ActivateReturnToFutureDropoff = Range(ActivateReturnToFutureDropoff, 0,1);
        ActivateAvoidDropoffWhenBuilding = Range(ActivateAvoidDropoffWhenBuilding, 0,1);
        ActivateIndependence = RangeAlways(ActivateIndependence, 0,1);
        ActivateAntiInspire = RangeAlways(ActivateAntiInspire, 0,3);
        ActivateForecastDistInDropoff = RangeAlways(ActivateForecastDistInDropoff, 0,3);



        LureFullishV3 = Range(LureFullishV3, -0.1f,0.5f);
        LongLureFullishV2 = Range(LongLureFullishV2, -0.1f,0.5f);
        MedLureFullishV2 = Range(MedLureFullishV2, -0.1f,0.5f);
        FullishLuresV2 = Range(FullishLuresV2, 900,990);

        LimitInspireGainsV3 = Range(LimitInspireGainsV3, 800,990);


        MinPropGameForAnnoying = Range(MinPropGameForAnnoying, 0f,0.95f);
        MinPropGameForAnnoying = Range(MinPropGameForAnnoying, 0f,0.95f);

        RunMinimumHaliteV2 = MultiplyByRange(RunMinimumHaliteV2, 0.3f,3f);


        CrossAvoidMinHaliteV2 = MultiplyByRange(CrossAvoidMinHaliteV2, 0.3f,3f);
        CrossAvoidStrengthV3 = MultiplyByRange(CrossAvoidStrengthV3, 0.6f,1.4f);
        CrossAvoidStrengthV3 = MultiplyByRange(CrossAvoidStrengthV3, 0.6f,1.4f);
        CrossAvoidStrengthLastTurnsV3 = MultiplyByRange(CrossAvoidStrengthLastTurnsV3, 0.3f,3f);


        MapSparseFactor1 = MultiplyByRange(MapSparseFactor1, 0.7f,1.3f);
        MapSparseFactor2 = MultiplyByRange(MapSparseFactor2, 0.7f,1.3f);
        MapSparseMult2 = MultiplyByRange(MapSparseMult2, 0.7f,1.3f);
        MapSparseMult1 = MultiplyByRange(MapSparseMult1, 0.7f,1.3f);
        MinMultiplierEV = MultiplyByRange(MinMultiplierEV, 0.8f,1.3f);
        MaxMultiplierEV = MultiplyByRange(MaxMultiplierEV, 0.8f,1.3f);



        GoalWeightV6 = MultiplyByRange(GoalWeightV6, 0.8f,1.4f);
        GoalWeightV6 = MultiplyByRange(GoalWeightV6, 0.6f,1.4f);

        GoalWeightDesireV2 = Range(GoalWeightDesireV2, 0f,0.05f);
        GoalWeightDesireV2 = MultiplyByRange(GoalWeightDesireV2, 0.7f,1.5f);
        GoalWeightDesireV2 = MultiplyByRange(GoalWeightDesireV2, 0.6f,1.4f);



        GoalAboutDenyingWeight = MultiplyByRange(GoalAboutDenyingWeight, 0.6f,1.4f);
        GoalAboutDenyingWeight = MultiplyByRange(GoalAboutDenyingWeight, 0.6f,1.4f);
        DropoffGoalWeightV2 = MultiplyByRange(DropoffGoalWeightV2, 0.6f,1.4f);
        DropoffGoalWeightV2 = MultiplyByRange(DropoffGoalWeightV2, 0.6f,1.4f);

        GoalWeightHaliteMod = Range(GoalWeightHaliteMod, -0.0005f,0.002f);
        GoalWeightHaliteMod = MultiplyByRange(GoalWeightHaliteMod, 0.6f,1.4f);

        GoalWeightOnHaliteMod = Range(GoalWeightOnHaliteMod, -0.0005f,0.002f);
        GoalWeightOnHaliteMod = MultiplyByRange(GoalWeightOnHaliteMod, 0.6f,1.4f);

        GoalWeightDistDropoff = Range(GoalWeightDistDropoff, -0.0005f,0.002f);
        GoalWeightDistDropoff = MultiplyByRange(GoalWeightDistDropoff, 0.6f,1.4f);

        GoalWeightMinModifier = Range(GoalWeightMinModifier, 0.3f,0.7f);
        GoalWeightMinModifier = MultiplyByRange(GoalWeightMinModifier, 0.6f,1.4f);





        HasReachedGoalFlat = Range(HasReachedGoalFlat, 0f,50f);
        HasReachedGoalTurn = Range(HasReachedGoalTurn, 0f,10f);

        ShipTileMultDist0 = Range(ShipTileMultDist0, 0.8f,1.5f);
        ShipTileMultDist1 = Range(ShipTileMultDist1, 0.8f,1.4f);

        DesirabilityNeighboursTakenV3 = Range(DesirabilityNeighboursTakenV3, 0.7f,1f);
        DesirabilityNeighboursTakenV3 = Range(DesirabilityNeighboursTakenV3, 0.7f,1f);
        DesirabilityTaken2 = MultiplyByRange(DesirabilityTaken2, 0.7f,1.3f);
        DesirabilityTaken2 = MultiplyByRange(DesirabilityTaken2, 0.7f,1.3f);
        ShipTileDistReductionV4 = MultiplyByRange(ShipTileDistReductionV4, 0.7f,1.3f);
        ShipTileDistReductionV4 = MultiplyByRange(ShipTileDistReductionV4, 0.7f,1.3f);
//        DesirabilityNearbyTakenEnd = MultiplyByRange(DesirabilityNearbyTakenEnd, 0.7f,1.3f);
//        DesirabilityNearbyTakenEnd = MultiplyByRange(DesirabilityNearbyTakenEnd, 0.7f,1.3f);
//        DesirabilityNearbyTakenStart = MultiplyByRange(DesirabilityNearbyTakenStart, 0.7f,1.3f);
//        DesirabilityNearbyTakenStart = MultiplyByRange(DesirabilityNearbyTakenStart, 0.7f,1.3f);

        GoalNearbyEra0 = Range(GoalNearbyEra0, 0f,50f);
        GoalNearbyEra0 = MultiplyByRange(GoalNearbyEra0, 0.7f,1.3f);
        GoalNearbyEra1 = Range(GoalNearbyEra1, 0f,50f);
        GoalNearbyEra1 = MultiplyByRange(GoalNearbyEra1, 0.7f,1.3f);
        GoalNearbyEra2 = Range(GoalNearbyEra2, 0f,50f);
        GoalNearbyEra2 = MultiplyByRange(GoalNearbyEra2, 0.7f,1.3f);
        GoalNearbyEra3 = Range(GoalNearbyEra3, 0f,50f);
        GoalNearbyEra3 = MultiplyByRange(GoalNearbyEra3, 0.7f,1.3f);
        GoalNearbyEra4 = Range(GoalNearbyEra4, 0f,50f);
        GoalNearbyEra4 = MultiplyByRange(GoalNearbyEra4, 0.7f,1.3f);
        GoalNearbyEra5 = Range(GoalNearbyEra5, 0f,50f);
        GoalNearbyEra5 = MultiplyByRange(GoalNearbyEra5, 0.7f,1.3f);
        GoalNearbyEra6 = Range(GoalNearbyEra6, 0f,50f);
        GoalNearbyEra6 = MultiplyByRange(GoalNearbyEra6, 0.7f,1.3f);
        GoalNearbyEra7 = Range(GoalNearbyEra7, 0f,50f);
        GoalNearbyEra7 = MultiplyByRange(GoalNearbyEra7, 0.7f,1.3f);


        GoalNearby32324Mult = MultiplyByRange(GoalNearby32324Mult, 0.7f,1.3f);
        GoalNearby32324Mult = MultiplyByRange(GoalNearby32324Mult, 0.7f,1.3f);
        GoalNearby32324Mult = MultiplyByRange(GoalNearby32324Mult, 0.7f,1.3f);
        Deny32324Mult = MultiplyByRange(Deny32324Mult, 0.7f,1.3f);
        Deny32324Mult = MultiplyByRange(Deny32324Mult, 0.7f,1.3f);
        Deny32324Mult = MultiplyByRange(Deny32324Mult, 0.7f,1.3f);



        GoalNearbyEraPlayers = MultiplyByRange(GoalNearbyEraPlayers, 0.7f,1.3f);
        GoalNearbyEraPlayers = MultiplyByRange(GoalNearbyEraPlayers, 0.7f,1.3f);
        GoalNearbyEraDensity = MultiplyByRange(GoalNearbyEraDensity, 0.7f,1.3f);
        GoalNearbyEraDensity = MultiplyByRange(GoalNearbyEraDensity, 0.7f,1.3f);
        GoalNearbyEraMapSize = MultiplyByRange(GoalNearbyEraMapSize, 0.7f,1.3f);


        MaxNearbyTaken  = Range(MaxNearbyTaken, 3,10);
        MaxNearbyTaken  = Range(MaxNearbyTaken, 3,10);
        NearbyRange  = Range(NearbyRange, 2,5);

        ShipTileDistPowerEmptyishV2 = Range(ShipTileDistPowerEmptyishV2, 0.9f,1f);
        ShipTileDistPowerNormalV2 = Range(ShipTileDistPowerNormalV2, 0.7f,1f);
        ShipTileDistPowerFullish = Range(ShipTileDistPowerFullish, 0.7f,1f);



        TilePrioDistPower = Range(TilePrioDistPower, 0.97f,1f);
        MinTilePrio = MultiplyByRange(MinTilePrio, 0.7f,1.3f);
        MinTilePrio = MultiplyByRange(MinTilePrio, 0.5f,1.5f);
//        TilePrioDistV2 = MultiplyByRange(TilePrioDistV2, 0.7f,1.3f);
//        TilePrioDistV2 = MultiplyByRange(TilePrioDistV2, 0.7f,1.3f);

        GoalWeightInspireFlat = MultiplyByRange(GoalWeightInspireFlat, 0.7f,1.5f);
        GoalWeightInspireFlat = MultiplyByRange(GoalWeightInspireFlat, 0.7f,1.5f);
        GoalWeightInspireHal = MultiplyByRange(GoalWeightInspireHal, 0.7f,1.5f);
        GoalWeightInspireHal = MultiplyByRange(GoalWeightInspireHal, 0.7f,1.5f);

        TileScoreLong = MultiplyByRange(TileScoreLong, 0.7f,1.5f);
        TileScoreLong = MultiplyByRange(TileScoreLong, 0.7f,1.5f);
        DistDropoffScore = MultiplyByRange(DistDropoffScore, 0.7f,2f);
        DistEnemyDropoffScore = Range(DistEnemyDropoffScore, -0.5f,0.5f);
        DistEnemyDropoffScore = Range(DistEnemyDropoffScore, -0.5f,0.5f);


        GoalBrawl = Around(GoalBrawl, 30f);
        GoalBrawl = Around(GoalBrawl, 30f);
        GoalBrawl = MultiplyByRange(GoalBrawl, 0.7f,1.3f);
        GoalBrawl = MultiplyByRange(GoalBrawl, 0.7f,1.3f);



        TileScoreNeighboursV2 = MultiplyByRange(TileScoreNeighboursV2, 0.7f,1.3f);
        TileScoreNeighboursV2 = MultiplyByRange(TileScoreNeighboursV2, 0.7f,1.3f);

        MaxHaliteForGoalV3 = MultiplyByRange(MaxHaliteForGoalV3, 0.7f,1.3f);
        MaxHaliteForGoalV3 = MultiplyByRange(MaxHaliteForGoalV3, 0.7f,1.3f);
        MaxHaliteForGoalV3 = MultiplyByRange(MaxHaliteForGoalV3, 0.7f,1.3f);



        IndependenceHalite = MultiplyByRange(IndependenceHalite, 0.7f,1.3f);
        IndependenceHalite = MultiplyByRange(IndependenceHalite, 0.7f,1.3f);
        IndependenceMax3 = MultiplyByRange(IndependenceMax3, 0.7f,1.3f);
        IndependenceMax3 = MultiplyByRange(IndependenceMax3, 0.7f,1.3f);
        IndependenceDropoffDist = Around(IndependenceDropoffDist, 10f);
        IndependenceEnemiesRange3 = MultiplyByRange(IndependenceEnemiesRange3, 0.7f,1.3f);
        IndependenceEnemiesRange3 = MultiplyByRange(IndependenceEnemiesRange3, 0.7f,1.3f);
        IndependenceFriendsRange3 = MultiplyByRange(IndependenceFriendsRange3, 0.7f,1.3f);
        IndependenceFriendsRange3 = MultiplyByRange(IndependenceFriendsRange3, 0.7f,1.3f);
        IndependenceLongLure = MultiplyByRange(IndependenceLongLure, 0.7f,1.3f);
        IndependenceLongLure = MultiplyByRange(IndependenceLongLure, 0.7f,1.3f);
        MaxIndependenceScoreForGoal = MultiplyByRange(MaxIndependenceScoreForGoal, 0.7f,1.3f);
        MaxIndependenceScoreForGoal = MultiplyByRange(MaxIndependenceScoreForGoal, 0.7f,1.3f);




        MinHaliteForDropoffGoal = MultiplyByRange(MinHaliteForDropoffGoal, 0.7f,1.3f);

        GoalConsistencyBonus0V2 = MultiplyByRange(GoalConsistencyBonus0V2, 0.7f,1.3f);
        GoalConsistencyBonus0V2 = MultiplyByRange(GoalConsistencyBonus0V2, 0.7f,1.3f);
        GoalConsistencyBonus0V2 = MultiplyByRange(GoalConsistencyBonus0V2, 0.7f,1.3f);
        GoalConsistencyBonus1 = MultiplyByRange(GoalConsistencyBonus1, 0.7f,1.3f);
        GoalConsistencyBonus1 = MultiplyByRange(GoalConsistencyBonus1, 0.7f,1.3f);
        GoalConsistencyBonus1 = MultiplyByRange(GoalConsistencyBonus1, 0.7f,1.3f);
        GoalConsistencyBonusNear = MultiplyByRange(GoalConsistencyBonusNear, 0.7f,1.3f);
        GoalConsistencyBonusNear = MultiplyByRange(GoalConsistencyBonusNear, 0.7f,1.3f);

        GoalMinHaliteOnTile = Range(GoalMinHaliteOnTile, 50,130);
        GoalMaxDist = Range(GoalMaxDist, 24,32);
        GoalMaxDist = Range(GoalMaxDist, 24,32);



        BlowEUpLateTurnV2 = MultiplyByRange(BlowEUpLateTurnV2, 0.7f,1.3f);
        BlowEUpLate2pV2 = MultiplyByRange(BlowEUpLate2pV2, 0.7f,1.3f);
        BlowEUpLate4pV2 = MultiplyByRange(BlowEUpLate4pV2, 0.7f,1.3f);

        BaseMeaningfulHalite = Range(BaseMeaningfulHalite, 100,400);
        MinMeaningfulHalite = Range(MinMeaningfulHalite, 10,100);

        GoalAverageHaliteNormalizeCap = MultiplyByRange(GoalAverageHaliteNormalizeCap, 0.7f,1.3f);
        GoalAverageHaliteNormalizeVal = MultiplyByRange(GoalAverageHaliteNormalizeVal, 0.7f,1.3f);
        GoalAverageHaliteNormalizeVal = MultiplyByRange(GoalAverageHaliteNormalizeVal, 0.7f,1.3f);
        GoalTileDesireMultiplier = MultiplyByRange(GoalTileDesireMultiplier, 0.6f,2f);
        GoalShipTileInspireTrust = MultiplyByRange(GoalShipTileInspireTrust, 0.5f,1.8f);
        PenaltyMoveBackToPreviousTileV4 = MultiplyByRange(PenaltyMoveBackToPreviousTileV4, 0.5f,2f);


        AverageHaliteNormalizeCap = MultiplyByRange(AverageHaliteNormalizeCap, 0.7f,1.3f);
        AverageHaliteNormalizeVal = MultiplyByRange(AverageHaliteNormalizeVal, 0.7f,1.3f);
        AverageHaliteNormalizeVal = MultiplyByRange(AverageHaliteNormalizeVal, 0.7f,1.3f);


        EmptyishStandstillV2 = Range(EmptyishStandstillV2, 0,500);
        FullishStandstillV2 = Around(FullishStandstillV2, 150);


//        TImportanceScaling = Range(TImportanceScaling, 0.4f,0.6f);
//        TImportanceScaling = Range(TImportanceScaling, 0.3f,0.75f);
//        TImportanceScaling = Range(TImportanceScaling, 0.35f,0.7f);


        ComplexDistMax = Range(ComplexDistMax, 2,11);
        ComplexDistDivisor = MultiplyByRange(ComplexDistDivisor, 0.5f,1.4f);
        ComplexDistDivisor = MultiplyByRange(ComplexDistDivisor, 0.5f,1.4f);
        ComplexDistDivisor = Range(ComplexDistDivisor, 5f,100f);




        T0Importance = MultiplyByRange(T0Importance, 0.5f,1.4f);
        T0Importance = MultiplyByRange(T0Importance, 0.8f,1.3f);

        T1Importance = MultiplyByRange(T1Importance, 0.5f,2f);
        T2Importance = MultiplyByRange(T2Importance, 0.5f,2f);
        T3Importance = MultiplyByRange(T3Importance, 0.5f,2f);
        TFinalImportance = MultiplyByRange(TFinalImportance, 0.5f,2f);
        TBaseImportance = MultiplyByRange(TBaseImportance, 0.5f,2f);

        T0ImportanceFull = MultiplyByRange(T0ImportanceFull, 0.5f,1.4f);
        T0ImportanceFull = MultiplyByRange(T0ImportanceFull, 0.8f,1.3f);

        T1ImportanceFull = MultiplyByRange(T1ImportanceFull, 0.5f,2f);
        T2ImportanceFull = MultiplyByRange(T2ImportanceFull, 0.5f,2f);
        T3ImportanceFull = MultiplyByRange(T3ImportanceFull, 0.5f,2f);
        TFinalImportanceFull = MultiplyByRange(TFinalImportanceFull, 0.5f,2f);
        TBaseImportanceFull = MultiplyByRange(TBaseImportanceFull, 0.5f,2f);


        IffyIfNotT0V3 = Range(IffyIfNotT0V3, 0f,1f);
        ItIsT0BeNotAfraid = Range(ItIsT0BeNotAfraid, 0f,1f);

        DropoffMapSizeScore = MultiplyByRange(DropoffMapSizeScore, 0.3f,3f);

        DropoffPlayerCount = MultiplyByRange(DropoffPlayerCount, 0.5f,2f);
        DropoffFlatScore = Range(DropoffFlatScore, -200,500);
        DropoffSparseMapMult = MultiplyByRange(DropoffSparseMapMult, 0.5f,2f);
        DropoffNoDropoffsMult = MultiplyByRange(DropoffNoDropoffsMult, 0.5f,2f);
        DropoffOneDropoffMult = MultiplyByRange(DropoffOneDropoffMult, 0.5f,2f);
        DropoffNotEnoughHalite = MultiplyByRange(DropoffNotEnoughHalite, 0.5f,2f);
        DropoffNotEnoughHalite = MultiplyByRange(DropoffNotEnoughHalite, 0.5f,2f);
        DropoffTooManyMultV2 = Range(DropoffTooManyMultV2, 0.1f,0.9f);
        DropoffWayTooManyMultV2 = MultiplyByRange(DropoffWayTooManyMultV2, 0.5f,2f);
        DropoffWorstOpponentStr = Range(DropoffWorstOpponentStr, -50f,50f);
        DropoffBestTileValueRatio = Range(DropoffBestTileValueRatio, 5f,50f);

        DropoffRunDesire = MultiplyByRange(DropoffRunDesire, 0.3f,3f);

        DropoffWorthMultV2 = MultiplyByRange(DropoffWorthMultV2, 0.8f,1.3f);
        DropoffWorthMultV2 = MultiplyByRange(DropoffWorthMultV2, 0.8f,1.3f);
        DropoffShipsBelowVal = MultiplyByRange(DropoffShipsBelowVal, 0.5f,2f);


        DropoffMinSpotScore = MultiplyByRange(DropoffMinSpotScore, 0.3f,3f);
        DropoffMinHaliteNearSpot = MultiplyByRange(DropoffMinHaliteNearSpot, 0.7f,1.2f);
        DropoffMinHaliteNearSpot = MultiplyByRange(DropoffMinHaliteNearSpot, 0.7f,1.2f);
        DropoffMinHaliteNearSpotEarly = MultiplyByRange(DropoffMinHaliteNearSpotEarly, 0.7f,1.2f);
        DropoffMinHaliteNearSpotEarly = MultiplyByRange(DropoffMinHaliteNearSpotEarly, 0.7f,1.2f);
        DropoffAbsoluteMin = MultiplyByRange(DropoffAbsoluteMin, 0.7f,1.2f);
        DropoffAbsoluteMin = MultiplyByRange(DropoffAbsoluteMin, 0.7f,1.2f);
        DropoffAbsoluteMin = MultiplyByRange(DropoffAbsoluteMin, 0.7f,1.2f);
        DropoffAbsoluteMinShipsSavedTime = MultiplyByRange(DropoffAbsoluteMinShipsSavedTime, 0.7f,1.2f);

        DropoffTooFewShips = Around(DropoffTooFewShips, 5);
        DropoffFactorIfTooFewShips = Range(DropoffFactorIfTooFewShips, 0.2f,0.8f);
        DropoffFactorIfTooFewShips = Range(DropoffFactorIfTooFewShips, 0.2f,0.8f);


        DropoffWeightMinDistanceSavings = MultiplyByRange(DropoffWeightMinDistanceSavings, 0.7f,1.2f);
        DropoffWeightMinDistanceSavings = MultiplyByRange(DropoffWeightMinDistanceSavings, 0.7f,1.2f);
        DropoffWeightDistanceSavings = MultiplyByRange(DropoffWeightDistanceSavings, 0.4f,1.6f);
        DropoffWeightDistanceSavings = MultiplyByRange(DropoffWeightDistanceSavings, 0.7f,1.2f);
        DropoffWeightDistanceSavings = MultiplyByRange(DropoffWeightDistanceSavings, 0.7f,1.2f);

        DropoffWeightNearbyShipHalite = MultiplyByRange(DropoffWeightNearbyShipHalite, 0.7f,1.2f);
        DropoffWeightNearbyShipHalite = MultiplyByRange(DropoffWeightNearbyShipHalite, 0.7f,1.2f);



        DropoffminShips = Range(DropoffminShips, 11,18);

        DropoffNearTiles = Range(DropoffNearTiles, 3,7);
        DropoffNearishTiles = Range(DropoffNearishTiles, 4,Tile.MAX_WALK_DIST_SUPPORTED -1);


        AboveHpsFactor = Range(AboveHpsFactor, 0.9f,2f);
        AboveHpsBonusV2 = MultiplyByRange(AboveHpsBonusV2, 0.3f,3f);
        AboveHaliteAvgBonus = MultiplyByRange(AboveHaliteAvgBonus, 0.3f,3f);
        AboveMeaningfulHaliteStandstillBonus = MultiplyByRange(AboveMeaningfulHaliteStandstillBonus, 0.3f,3f);
        RelativeHaliteAvgFactor = MultiplyByRange(RelativeHaliteAvgFactor, 0.3f,2f);

//        ProbableAttemptsPunisher = MultiplyByRange(ProbableAttemptsPunisher, 0.3f,3f);
//        ProbableAttemptsBoostIfFew = MultiplyByRange(ProbableAttemptsBoostIfFew, 0.3f,3f);

        GoalWeightHaliteV2 = MultiplyByRange(GoalWeightHaliteV2, 0.5f,1.6f);
        GoalWeightHaliteV2 = MultiplyByRange(GoalWeightHaliteV2, 0.5f,1.6f);
        GoalWeightHaliteV2 = MultiplyByRange(GoalWeightHaliteV2, 0.5f,1.6f);

        MapEmpty = MultiplyByRange(MapEmpty, 0.5f,1.6f);
        MapEmptyBoostPerHaliteStandstillV2 = Range(MapEmptyBoostPerHaliteStandstillV2, 0,20f);
        MapAverageHaliteEmpty = MultiplyByRange(MapAverageHaliteEmpty, 0.5f,1.6f);


        GoalTrustInspire = Range(GoalTrustInspire, 0.1f,1.1f);
        GoalUseInspireTurn = Range(GoalUseInspireTurn, 0,6);



        SwapPunishmentV2 = MultiplyByRange(SwapPunishmentV2, 0.5f,1.6f);
        SwapPunishmentSimilarV2 = MultiplyByRange(SwapPunishmentSimilarV2, 0.5f,1.6f);
        PunishmentCloseToEnemyDropoff = MultiplyByRange(PunishmentCloseToEnemyDropoff, 0.7f,1.3f);
        EstimatedShipValueModifier = MultiplyByRange(EstimatedShipValueModifier, 0.7f,1.3f);
        EstimatedShipValueFlat = Range(EstimatedShipValueFlat, -150,150f);

        PlanLastTurnSuggestions = MultiplyByRange(PlanLastTurnSuggestions, 0.7f,1.3f);
        PlanLastTurnSuggestions = MultiplyByRange(PlanLastTurnSuggestions, 0.7f,1.3f);
        PlanLastTurnSuggestions = MultiplyByRange(PlanLastTurnSuggestions, 0.7f,1.3f);



        OddsNotPathTurnPow = Range(OddsNotPathTurnPow, 0.8f,1f);
        OddsPathBase = MultiplyByRange(OddsPathBase, 0.5f,2f);
        OddsNotLikelyV3 = Range(OddsNotLikelyV3, 0.2f,0.8f);
        OddsNotCouldbeV2 = Range(OddsNotCouldbeV2, 0.5f,0.9f);
        OddsNotMaybeV4 = Range(OddsNotMaybeV4, 0.8f,1f);


        OddsNotPathTurnPowNew = Range(OddsNotPathTurnPowNew, 0.8f,1f);
        OddsPathBaseNew = MultiplyByRange(OddsPathBaseNew, 0.5f,1.5f);
        OddsPathBaseNew = MultiplyByRange(OddsPathBaseNew, 0.5f,1.5f);
        OddsPathBaseCouldBeNew = MultiplyByRange(OddsPathBaseCouldBeNew, 0.5f,1.5f);
        OddsPathBaseCouldBeNew = MultiplyByRange(OddsPathBaseCouldBeNew, 0.5f,1.5f);



        GoalCloseTileMinimumScore = Around(GoalCloseTileMinimumScore, 100f);
        GoalFarTileMinimumScoreV2 = Around(GoalFarTileMinimumScoreV2, 100f);
        GoalCloseTileMinimumHalite = Around(GoalCloseTileMinimumHalite, 20f);


        GoalDropoffNearbyEnemyDropoffDist = Range(GoalDropoffNearbyEnemyDropoffDist, 1,8);

        GoalDropoffNearbyEnemyDropoff = MultiplyByRange(GoalDropoffNearbyEnemyDropoff, 0.6f,1.5f);
        GoalDropoffNearbyEnemyDropoff = MultiplyByRange(GoalDropoffNearbyEnemyDropoff, 0.6f,1.5f);
        GoalDropoffNearbyShips = MultiplyByRange(GoalDropoffNearbyShips, 0.6f,1.5f);
        GoalDropoffNearbyHaliteMax = MultiplyByRange(GoalDropoffNearbyHaliteMax, 0.6f,1.5f);
        GoalDropoffNearbyHaliteMax = MultiplyByRange(GoalDropoffNearbyHaliteMax, 0.6f,1.5f);
        GoalDropoffNearbyHaliteMax = MultiplyByRange(GoalDropoffNearbyHaliteMax, 0.6f,1.5f);
        GoalDropoffShipYard = MultiplyByRange(GoalDropoffShipYard, 0.6f,1.5f);
        GoalDropoffShipYard = MultiplyByRange(GoalDropoffShipYard, 0.6f,1.5f);
        GoalDropoffShipYard = MultiplyByRange(GoalDropoffShipYard, 0.6f,1.5f);
        GoalDropoffNotMadeYet= MultiplyByRange(GoalDropoffNotMadeYet, 0.6f,1.5f);
        GoalDropoffNotMadeYet= MultiplyByRange(GoalDropoffNotMadeYet, 0.6f,1.5f);
        GoalDropoffNotMadeYet= MultiplyByRange(GoalDropoffNotMadeYet, 0.6f,1.5f);
        GoalDropoffNearbyHalite = MultiplyByRange(GoalDropoffNearbyHalite, 0.6f,1.5f);
        GoalDropoffNearbyHalite = MultiplyByRange(GoalDropoffNearbyHalite, 0.6f,1.5f);
        GoalDropoffNearbyHalite = MultiplyByRange(GoalDropoffNearbyHalite, 0.6f,1.5f);
        GoalDropoffLongLure = MultiplyByRange(GoalDropoffLongLure, 0.6f,1.5f);
        GoalDropoffLongLure = MultiplyByRange(GoalDropoffLongLure, 0.6f,1.5f);
        GoalDropoffLongLure = MultiplyByRange(GoalDropoffLongLure, 0.6f,1.5f);


        EnemyShipCountStart = MultiplyByRange(EnemyShipCountStart, 0.6f,1.5f);
        EnemyShipCountStart = MultiplyByRange(EnemyShipCountStart, 0.6f,1.5f);
        EnemyShipCountEnd = MultiplyByRange(EnemyShipCountEnd, 0.6f,1.5f);
        EnemyShipCountEnd = MultiplyByRange(EnemyShipCountEnd, 0.6f,1.5f);
        EnemyShipCountControlZone = MultiplyByRange(EnemyShipCountControlZone, 0.6f,1.5f);
        EnemyShipCountControlZone = MultiplyByRange(EnemyShipCountControlZone, 0.6f,1.5f);
        EnemyShipHalite = MultiplyByRange(EnemyShipHalite, 0.6f,1.5f);
        EnemyShipHalite = MultiplyByRange(EnemyShipHalite, 0.8f,1.3f);
        EnemyShipTileHalite = MultiplyByRange(EnemyShipTileHalite, 0.8f,1.3f);
        EnemyShipCloseToMyDropoffWithHalite = MultiplyByRange(EnemyShipCloseToMyDropoffWithHalite, 0.6f,1.5f);

        EnemyShipControlDifference = MultiplyByRange(EnemyShipControlDifference, 0.6f,1.5f);
        EnemyShipControlDifference = MultiplyByRange(EnemyShipControlDifference, 0.6f,1.5f);
        EnemyShipMultiplierBehind = MultiplyByRange(EnemyShipMultiplierBehind, 0.6f,1.5f);
        EnemyShipMultiplierBehind = MultiplyByRange(EnemyShipMultiplierBehind, 0.6f,1.5f);

        EnemyShipDifForBehind = Range(EnemyShipDifForBehind, -5,5);


        WIDTH_SOLO_MINIMAL = Around(WIDTH_SOLO_MINIMAL, 8);


        MinimalPreMechanicV2 = Range(MinimalPreMechanicV2, 0,4);
        MinimalPostMechanicV3 = Range(MinimalPostMechanicV3, 0,5);
        AddToCheckAgain = Range(AddToCheckAgain, 0,3);
        SimStandstillSoloJourney = Range(SimStandstillSoloJourney, 0,1);



        AheadInShipThresh = Range(AheadInShipThresh, 0,10);

        MultiplierAggression2sIfAheadShipCountV2 = Range(MultiplierAggression2sIfAheadShipCountV2, 1.0f,1.6f);
        MultiplierShipWorthIfBehind = Range(MultiplierShipWorthIfBehind, 1.0f,1.3f);
        ControlZoneMultiplier = Range(ControlZoneMultiplier, 1.0f,1.3f);


        MultiplierAggression2sEndGame = Range(MultiplierAggression2sEndGame, 1.0f,1.3f);
        MultiplierAggression4sEndGame = Range(MultiplierAggression4sEndGame, 1.0f,1.3f);
        EndGame = Range(EndGame, 10,80);

        DropoffWorthDensity = MultiplyByRange(DropoffWorthDensity, 0.6f,1.5f);
        DropoffWorthDensityFlat = Around(DropoffWorthDensityFlat, 100f);

        DropoffWorthGametypeFlat = Around(DropoffWorthGametypeFlat, 100f);






        DO_MY_SIM_ONCE_BEFORE_SIM = Range(DO_MY_SIM_ONCE_BEFORE_SIM, 0,1);
        PREDICT_ENEMY_TURNS = Range(PREDICT_ENEMY_TURNS, 2,4);
        MIN_RESULTS_BEFORE_PREDICTION = Range(MIN_RESULTS_BEFORE_PREDICTION, 3,15);
        MIN_ACCURACY = MultiplyByRange(MIN_ACCURACY, 0.6f,1.5f);
        FLAT_CONTRIBUTION_PREDICTION = MultiplyByRange(FLAT_CONTRIBUTION_PREDICTION, 0.6f,1.5f);
        BONUS_MOST_RELIABLE = MultiplyByRange(BONUS_MOST_RELIABLE, 0.6f,1.5f);
        BASIC_STANDSTILL_HALITE = MultiplyByRange(BASIC_STANDSTILL_HALITE, 0.6f,1.5f);
        BASIC_MOVE_HALITE = MultiplyByRange(BASIC_MOVE_HALITE, 0.6f,1.5f);
        BASIC_BURNHALITE = MultiplyByRange(BASIC_BURNHALITE, 0.6f,1.5f);
        BASIC_MEANINGFUL = MultiplyByRange(BASIC_MEANINGFUL, 0.6f,1.5f);
        BASIC_MEDLURE = MultiplyByRange(BASIC_MEDLURE, 0.6f,1.5f);
        TURNS_DROPOFF_FULL = MultiplyByRange(TURNS_DROPOFF_FULL, 0.6f,1.5f);
        TURNS_DROPOFF_NORMAL = MultiplyByRange(TURNS_DROPOFF_NORMAL, 0.6f,1.5f);


        SELF_COLLISION = MultiplyByRange(SELF_COLLISION, 0.6f,1.5f);
        BeDifferentsoloJourney = MultiplyByRange(BeDifferentsoloJourney, 0.3f,3f);


        ShipHaliteOnMoveV2 = MultiplyByRange(ShipHaliteOnMoveV2, 0.7f,1.4f);
        ShipHaliteOnMoveV2 = MultiplyByRange(ShipHaliteOnMoveV2, 0.7f,1.4f);
        ShipHaliteIfStandstillNextV2 = MultiplyByRange(ShipHaliteIfStandstillNextV2, 0.7f,1.4f);
        ShipHaliteIfStandstillNextV2 = MultiplyByRange(ShipHaliteIfStandstillNextV2, 0.7f,1.4f);


        HalAvgDist1WeightV2 = Range(HalAvgDist1WeightV2, 0f,0.5f);
        HalAvgDist1WeightV2 = Range(HalAvgDist1WeightV2, 0f,0.5f);
        HalAvgDist2WeightV2 = Range(HalAvgDist2WeightV2, 0f,0.5f);
        HalAvgDist2WeightV2 = Range(HalAvgDist2WeightV2, 0f,0.5f);
        HalAvgDist3WeightV2 = Range(HalAvgDist3WeightV2, 0f,0.5f);
        HalAvgDist3WeightV2 = Range(HalAvgDist3WeightV2, 0f,0.5f);
        HalAvgDist4WeightV2 = Range(HalAvgDist4WeightV2, 0f,0.5f);
        HalAvgDist4WeightV2 = Range(HalAvgDist4WeightV2, 0f,0.5f);
        HalAvgDist5WeightV2 = Range(HalAvgDist5WeightV2, 0f,0.5f);
        HalAvgDist5WeightV2 = Range(HalAvgDist5WeightV2, 0f,0.5f);


        MyShipRange1Weight = Around(MyShipRange1Weight, 30f);
        MyShipRange1Weight = Around(MyShipRange1Weight, 30f);
        MyShipRange2Weight = Around(MyShipRange2Weight,30f);
        MyShipRange2Weight = Around(MyShipRange2Weight,30f);
        MyShipRange3Weight = Around(MyShipRange3Weight, 30f);
        MyShipRange3Weight = Around(MyShipRange3Weight, 30f);
        MyShipRange4Weight = Around(MyShipRange4Weight, 30f);
        MyShipRange4Weight = Around(MyShipRange4Weight, 30f);
        MyShipRange5Weight = Around(MyShipRange5Weight,30f);
        MyShipRange5Weight = Around(MyShipRange5Weight,30f);


        EnemyShipRange1Weight = Around(EnemyShipRange1Weight, 30f);
        EnemyShipRange1Weight = Around(EnemyShipRange1Weight, 30f);
        EnemyShipRange2Weight = Around(EnemyShipRange2Weight, 30f);
        EnemyShipRange2Weight = Around(EnemyShipRange2Weight, 30f);
        EnemyShipRange3Weight = Around(EnemyShipRange3Weight, 30f);
        EnemyShipRange3Weight = Around(EnemyShipRange3Weight, 30f);
        EnemyShipRange4Weight = Around(EnemyShipRange4Weight, 30f);
        EnemyShipRange4Weight = Around(EnemyShipRange4Weight, 30f);
        EnemyShipRange5Weight = Around(EnemyShipRange5Weight, 30f);
        EnemyShipRange5Weight = Around(EnemyShipRange5Weight, 30f);


        MaxSums2 = MultiplyByRange(MaxSums2, 0.7f,1.4f);
        MaxSums2 = MultiplyByRange(MaxSums2, 0.7f,1.4f);
        MaxSums3 = MultiplyByRange(MaxSums3, 0.7f,1.4f);
        MaxSums3 = MultiplyByRange(MaxSums3, 0.7f,1.4f);
        MaxSums4 = MultiplyByRange(MaxSums4, 0.7f,1.4f);
        MaxSums4 = MultiplyByRange(MaxSums4, 0.7f,1.4f);
        MaxSumsCap = MultiplyByRange(MaxSumsCap, 0.7f,1.4f);
        MaxSumsCap = MultiplyByRange(MaxSumsCap, 0.7f,1.4f);


        EnemyTerrainDesire = MultiplyByRange(EnemyTerrainDesire, 0.7f,1.4f);
        EnemyTerrainDesire = MultiplyByRange(EnemyTerrainDesire, 0.7f,1.4f);
        EnemyTerrainHaliteV2 = MultiplyByRange(EnemyTerrainHaliteV2, 0.7f,1.4f);
        EnemyTerrainHaliteV2 = MultiplyByRange(EnemyTerrainHaliteV2, 0.7f,1.4f);

        EnemyTerrainGoalHaliteV2 = Range(EnemyTerrainGoalHaliteV2, -0.3f,0.3f);
        EnemyTerrainGoalFlatV2 = Range(EnemyTerrainGoalFlatV2, -4f,2f);
        EnemyTerrainGoalFlatV2 = MultiplyByRange(EnemyTerrainGoalFlatV2, 0.7f,1.4f);


        BorderReachableFlat = MultiplyByRange(BorderReachableFlat, 0.7f,1.4f);
        BorderReachableFlat = MultiplyByRange(BorderReachableFlat, 0.7f,1.4f);
        BorderReachableHalite = MultiplyByRange(BorderReachableHalite, 0.7f,1.4f);
        BorderReachableHalite = MultiplyByRange(BorderReachableHalite, 0.7f,1.4f);
        GoalControlDanger = Range(GoalControlDanger, -5f,5f);
        GoalDenyScores = MultiplyByRange(GoalDenyScores, 0.7f,1.4f);
        GoalDenyScores = MultiplyByRange(GoalDenyScores, 0.7f,1.4f);
        MinDenyScore = MultiplyByRange(MinDenyScore, 0.7f,1.4f);
        MinDenyScore = MultiplyByRange(MinDenyScore, 0.7f,1.4f);

        GoalCentralFlat = Range(GoalCentralFlat, 0f,20f);
        GoalCentralHalite = MultiplyByRange(GoalCentralHalite, 0.7f,1.4f);
        GoalCentralHalite = MultiplyByRange(GoalCentralHalite, 0.7f,1.4f);

        NearbyMultiplierEnemiesClose = MultiplyByRange(NearbyMultiplierEnemiesClose, 0.7f,1.4f);
        NearbyMultiplierEnemiesClose = MultiplyByRange(NearbyMultiplierEnemiesClose, 0.7f,1.4f);
        NearbyMultiplierAlone = MultiplyByRange(NearbyMultiplierAlone, 0.7f,1.4f);
        NearbyMultiplierAlone = MultiplyByRange(NearbyMultiplierAlone, 0.7f,1.4f);


        GoalEnemysNear5 = MultiplyByRange(GoalEnemysNear5, 0.7f,1.4f);
        GoalEnemysNear5 = MultiplyByRange(GoalEnemysNear5, 0.7f,1.4f);
        GoalFriendsNear5 = MultiplyByRange(GoalFriendsNear5, 0.7f,1.4f);
        GoalFriendsNear5 = MultiplyByRange(GoalFriendsNear5, 0.7f,1.4f);




        TilesDistanceLowPriorityZone = Around(TilesDistanceLowPriorityZone, 3);
        TilesDistanceLowPriorityZone = Around(TilesDistanceLowPriorityZone, 2);
        LowPrioZoneFlat = MultiplyByRange(LowPrioZoneFlat, 0.7f,1.4f);
        LowPrioZoneFlat = MultiplyByRange(LowPrioZoneFlat, 0.7f,1.4f);
        LowPrioZoneHalite = MultiplyByRange(LowPrioZoneHalite, 0.7f,1.4f);
        LowPrioZoneHalite = MultiplyByRange(LowPrioZoneHalite, 0.7f,1.4f);


        AnnoyFlatTileDesire = MultiplyByRange(AnnoyFlatTileDesire, 0.5f,1.4f);
        AnnoyFlatTileDesire = MultiplyByRange(AnnoyFlatTileDesire, 0.5f,1.4f);
        AnnoyWeight = MultiplyByRange(AnnoyWeight, 0.2f,1.4f);
        AnnoyWeight = MultiplyByRange(AnnoyWeight, 0.5f,1.4f);
        ControlEdgeTileFlat = MultiplyByRange(ControlEdgeTileFlat, 0.7f,1.4f);
        ControlEdgeTileFlat = MultiplyByRange(ControlEdgeTileFlat, 0.7f,1.4f);
        ControlEdgeTileHalite = MultiplyByRange(ControlEdgeTileHalite, 0.7f,1.4f);
        ControlEdgeTileHalite = MultiplyByRange(ControlEdgeTileHalite, 0.7f,1.4f);

        GoalWeightEdgeMapHaliteV2 = MultiplyByRange(GoalWeightEdgeMapHaliteV2, 0.7f,1.6f);
        GoalWeightEdgeMapHaliteV2 = MultiplyByRange(GoalWeightEdgeMapHaliteV2, 0.7f,1.4f);
        GoalWeightEdgeMapHaliteV2 = MultiplyByRange(GoalWeightEdgeMapHaliteV2, 0.7f,1.4f);
        GoalWeightEdgeMapFlatV2 = MultiplyByRange(GoalWeightEdgeMapFlatV2, 0.7f,1.6f);
        GoalWeightEdgeMapFlatV2 = MultiplyByRange(GoalWeightEdgeMapFlatV2, 0.7f,1.4f);
        GoalWeightEdgeMapFlatV2 = MultiplyByRange(GoalWeightEdgeMapFlatV2, 0.7f,1.4f);

        LongLureEdgeMapFactor = MultiplyByRange(LongLureEdgeMapFactor, 0.7f,1.4f);
        LongLureEdgeMapFactor = MultiplyByRange(LongLureEdgeMapFactor, 0.7f,1.4f);

        WeirdAlgoWeight = MultiplyByRange(WeirdAlgoWeight, 0.7f,1.4f);
        WeirdAlgoWeight = MultiplyByRange(WeirdAlgoWeight, 0.7f,1.4f);
        WeirdAlgoWeightMultiplier = MultiplyByRange(WeirdAlgoWeightMultiplier, 0.7f,1.4f);
        WeirdAlgoWeightMultiplier = MultiplyByRange(WeirdAlgoWeightMultiplier, 0.7f,1.4f);
        WeirdAlgoSteps = Range(WeirdAlgoSteps, 6,12);
        WeirdAlgoDistFromGoalV2 = Range(WeirdAlgoDistFromGoalV2, 3,9);




        BrawlMin = MultiplyByRange(BrawlMin, 0.7f,1.4f);
        BrawlMin = MultiplyByRange(BrawlMin, 0.7f,1.4f);

        GoalWeightUpcomingDropoffDistV2 = MultiplyByRange(GoalWeightUpcomingDropoffDistV2, 0.7f,1.4f);
        GoalWeightUpcomingDropoffDistV2 = MultiplyByRange(GoalWeightUpcomingDropoffDistV2, 0.7f,1.4f);
        GoalWeightSqrtHalite = Around(GoalWeightSqrtHalite, 20f);
        GoalWeightLogHalite = Around(GoalWeightLogHalite, 20f);
        GoalWeightExpoHalite = Around(GoalWeightExpoHalite, 20f);


        WeightSqrtHalite = Around(WeightSqrtHalite, 20f);
        WeightLogHalite = Around(WeightLogHalite, 20f);
        WeightExpoHalite = Around(WeightExpoHalite, 20f);



        EntrapmentWeight = MultiplyByRange(EntrapmentWeight, 0.7f,1.4f);
        EntrapControlFactor = MultiplyByRange(EntrapControlFactor, 0.7f,1.4f);
        EntrapMinEHalite = MultiplyByRange(EntrapMinEHalite, 0.7f,1.4f);
        EntrapMaxMyHalite = MultiplyByRange(EntrapMaxMyHalite, 0.7f,1.4f);
        EntrapMinDist = Range(EntrapMinDist, 2,7);
        EntrapMinShipDifference = Range(EntrapMinShipDifference, -5,8);


        ShipHardCap = Math.min(ShipHardCap,Map.shipArraySize);


        UrgencyRunner = MultiplyByRange(UrgencyRunner, 0.7f,1.4f);
        UrgencyFastReturn = MultiplyByRange(UrgencyFastReturn, 0.7f,1.4f);
        UrgencyReturn = MultiplyByRange(UrgencyReturn, 0.7f,1.4f);
        UrgencyDenyGoal = MultiplyByRange(UrgencyDenyGoal, 0.7f,1.4f);
        UrgencyDenyGoal = MultiplyByRange(UrgencyDenyGoal, 0.7f,1.4f);
        UrgencyGoal = MultiplyByRange(UrgencyGoal, 0.7f,1.4f);
        UrgencyGoal = MultiplyByRange(UrgencyGoal, 0.7f,1.4f);
        UrgencyNoGoal = MultiplyByRange(UrgencyNoGoal, 0.7f,1.4f);
        DepthDeny = Range(DepthDeny, 7,12);
        DepthGoal = Range(DepthGoal, 7,12);
        TurnDepth = Range(TurnDepth, 8,19);
        PathLength = Range(PathLength, 4,7);
        LimitHalite1 = Range(LimitHalite1, 700,1000);
        LimitHalite2 = Range(LimitHalite2, 900,1090);
        LimitHalite3 = Range(LimitHalite3, 850,1000);
        LimitHalite4 = Range(LimitHalite4, 850,1000);
        LimitHalite5 = Range(LimitHalite5, 750,950);
        LimitHalite6 = Range(LimitHalite6, 200,700);
        WeirdAlgoInspireMult = Range(WeirdAlgoInspireMult, 0.2f,2f);
        HaliteMinDistDropoff = Range(HaliteMinDistDropoff, 2,6);
        HaliteTurnsDropoff = Range(HaliteTurnsDropoff, 5,25);
        ReserveDepth = Range(ReserveDepth, 1,25);
        ReserveDepth = Range(ReserveDepth, 1,25);




        WeirdAlgoEOdds = MultiplyByRange(WeirdAlgoEOdds, 0.7f,1.4f);
        PathConsistency0 = MultiplyByRange(PathConsistency0, 0.7f,1.4f);
        PathConsistency0 = MultiplyByRange(PathConsistency0, 0.7f,1.4f);
        PathConsistency1 = MultiplyByRange(PathConsistency1, 0.7f,1.4f);
        PathConsistency1 = MultiplyByRange(PathConsistency1, 0.7f,1.4f);
        PathConsistency2 = MultiplyByRange(PathConsistency2, 0.7f,1.4f);
        PathConsistency2 = MultiplyByRange(PathConsistency2, 0.7f,1.4f);
        PathConsistency3 = MultiplyByRange(PathConsistency3, 0.7f,1.4f);
        PathConsistency3 = MultiplyByRange(PathConsistency3, 0.7f,1.4f);
        PathConsistency4 = MultiplyByRange(PathConsistency4, 0.7f,1.4f);
        PathConsistency4 = MultiplyByRange(PathConsistency4, 0.7f,1.4f);
        PathConsistency5 = MultiplyByRange(PathConsistency5, 0.7f,1.4f);
        PathConsistency5 = MultiplyByRange(PathConsistency5, 0.7f,1.4f);
        PathConsistency6 = MultiplyByRange(PathConsistency6, 0.7f,1.4f);
        PathConsistency6 = MultiplyByRange(PathConsistency6, 0.7f,1.4f);

        TurnInHaliteOnMove = MultiplyByRange(TurnInHaliteOnMove, 0.7f,1.4f);
        TurnInHaliteOnMove = MultiplyByRange(TurnInHaliteOnMove, 0.7f,1.4f);
        TurnInHaliteOnMove = MultiplyByRange(TurnInHaliteOnMove, 0.7f,1.4f);

        RuleOf90 = Range(RuleOf90, 0,50f);
        RuleOf90 = MultiplyByRange(RuleOf90, 0.65f,1.2f);
        RuleOf90 = MultiplyByRange(RuleOf90, 0.65f,1.2f);
        RuleOf120 = Range(RuleOf120, 0,50f);
        RuleOf120 = MultiplyByRange(RuleOf120, 0.65f,1.2f);

        RuleOf150 = Range(RuleOf150, 0,50f);
        RuleOf150 = MultiplyByRange(RuleOf150, 0.65f,1.2f);

        RuleOf180 = Range(RuleOf180, 0,50f);
        RuleOf180 = MultiplyByRange(RuleOf180, 0.65f,1.2f);

        RuleOf210 = Range(RuleOf210, 0,50f);
        RuleOf210 = MultiplyByRange(RuleOf210, 0.65f,1.2f);

        AvoidELocT0Mult = MultiplyByRange(AvoidELocT0Mult, 0.65f,1.3f);
        AvoidELocT0Mult = MultiplyByRange(AvoidELocT0Mult, 0.65f,1.3f);

        AvoidELocTOtherMult = MultiplyByRange(AvoidELocTOtherMult, 0.65f,1.3f);
        AvoidELocTOtherMult = MultiplyByRange(AvoidELocTOtherMult, 0.65f,1.3f);


        SimulWeight = MultiplyByRange(SimulWeight, 0.65f,1.3f);
        SimulWeight = MultiplyByRange(SimulWeight, 0.65f,1.3f);
        SimulWeightMultiplier = MultiplyByRange(SimulWeightMultiplier, 0.65f,1.3f);
        SimulWeightMultiplier = MultiplyByRange(SimulWeightMultiplier, 0.65f,1.3f);





        MinExpectedHaliteBeforeConsiderDropoff = Range(MinExpectedHaliteBeforeConsiderDropoff, 3000,4000);
        AllowRangeExpectedV2 = Range(AllowRangeExpectedV2, 5,12);
        WeirdAlgoMaxGathersByOtherShips = Range(WeirdAlgoMaxGathersByOtherShips, 1,5);
        InspireShape = Range(InspireShape, 0,3);
        InspireShape = Range(InspireShape, 0,3);
        InspireShape = Range(InspireShape, 0,3);
        InspirePathType = Range(InspirePathType, 0,1);
        InspirenewVersion = RangeAlways(InspirenewVersion, 0,1);


        T0ImportanceFullSizeMult = Around(T0ImportanceFullSizeMult, 0.2f);
        T1ImportanceFullSizeMult = Around(T1ImportanceFullSizeMult, 0.2f);
        T2ImportanceFullSizeMult = Around(T2ImportanceFullSizeMult, 0.2f);
        TOtherImportanceFullSizeMult = Around(TOtherImportanceFullSizeMult, 0.2f);
        GoalAverageHaliteNormalizeValSizeMult = Around(GoalAverageHaliteNormalizeValSizeMult, 0.2f);
        GoalWeightV6SizeMult = Around(GoalWeightV6SizeMult, 0.2f);
        DropoffGoalWeightV2SizeMult = Around(DropoffGoalWeightV2SizeMult, 0.2f);
        MinMultiplierEVSizeMult = Around(MinMultiplierEVSizeMult, 0.2f);
        EVLessShipsSizeMult = Around(EVLessShipsSizeMult, 0.2f);
        EVLeadShipsAndPointsV2SizeMult = Around(EVLeadShipsAndPointsV2SizeMult, 0.2f);
        LongDistNerfValueAroundMyShipsSizeMult = Around(LongDistNerfValueAroundMyShipsSizeMult, 0.2f);
        DropoffPunishmentCloseToEnemySizeMult = Around(DropoffPunishmentCloseToEnemySizeMult, 0.2f);
        MeaningfulHaliteV3SizeMult = Around(MeaningfulHaliteV3SizeMult, 0.2f);
        InspireScoreSizeMult = Around(InspireScoreSizeMult, 0.2f);
        TurnInScoreSizeMult = Around(TurnInScoreSizeMult, 0.2f);
        IndividualMoveScoreSizeMult = Around(IndividualMoveScoreSizeMult, 0.2f);
        GatherScoreSizeMult = Around(GatherScoreSizeMult, 0.2f);
        LuresScoreSizeMult = Around(LuresScoreSizeMult, 0.2f);
        MiscScoreSizeMult = Around(MiscScoreSizeMult, 0.2f);

        InspireScorePlayersMult = Around(InspireScorePlayersMult, 0.2f);
        TurnInScorePlayersMult = Around(TurnInScorePlayersMult, 0.2f);
        DropoffWorthMultV2PlayersMult = Around(DropoffWorthMultV2PlayersMult, 0.2f);
        GoalWeightUpcomingDropoffDistV2PlayersMult = Around(GoalWeightUpcomingDropoffDistV2PlayersMult, 0.2f);
        T0ImportanceFullPlayersMult = Around(T0ImportanceFullPlayersMult, 0.2f);
        T1ImportanceFullPlayersMult = Around(T1ImportanceFullPlayersMult, 0.2f);
        T2ImportanceFullPlayersMult = Around(T2ImportanceFullPlayersMult, 0.2f);
        TOtherImportanceFullPlayersMult = Around(TOtherImportanceFullPlayersMult, 0.2f);
        T0ImportancePlayersMult = Around(T0ImportancePlayersMult, 0.2f);
        T1ImportancePlayersMult = Around(T1ImportancePlayersMult, 0.2f);
        T2ImportancePlayersMult = Around(T2ImportancePlayersMult, 0.2f);
        TOtherImportancePlayersMult = Around(TOtherImportancePlayersMult, 0.2f);
        MaxSums2PlayersMult = Around(MaxSums2PlayersMult, 0.2f);
        MaxSums3PlayersMult = Around(MaxSums3PlayersMult, 0.2f);
        MaxSums4PlayersMult = Around(MaxSums4PlayersMult, 0.2f);
        MaxSumsCapPlayersMult = Around(MaxSumsCapPlayersMult, 0.2f);

        SpecialBunch1 = Around(SpecialBunch1, 1f);
        SpecialBunchDensity = Around(SpecialBunchDensity, 0.5f);

        ReduceTrustFuturePathNearbyFriends5 = Range(ReduceTrustFuturePathNearbyFriends5, 0.99f,1f);
        ReduceTrustFuturePathNearbyFriends5 = Range(ReduceTrustFuturePathNearbyFriends5, 0.99f,1f);
        ReduceTrustFuturePathNearbyFriends3 = Range(ReduceTrustFuturePathNearbyFriends3, 0.95f,1f);
        ReduceTrustFuturePathNearbyFriends3 = Range(ReduceTrustFuturePathNearbyFriends3, 0.95f,1f);

        IndividualMoveScorePlayersMult = Around(IndividualMoveScorePlayersMult, 0.2f);
        IndividualMoveScorePlayersMult = Around(IndividualMoveScorePlayersMult, 0.2f);
        GatherScorePlayersMult = Around(GatherScorePlayersMult, 0.2f);
        GatherScorePlayersMult = Around(GatherScorePlayersMult, 0.2f);
        LuresScorePlayersMult = Around(LuresScorePlayersMult, 0.2f);
        LuresScorePlayersMult = Around(LuresScorePlayersMult, 0.2f);
        MiscScorePlayersMult = Around(MiscScorePlayersMult, 0.2f);
        MiscScorePlayersMult = Around(MiscScorePlayersMult, 0.2f);
        WastePreventionPlayersMult = Around(WastePreventionPlayersMult, 0.2f);
        WastePreventionPlayersMult = Around(WastePreventionPlayersMult, 0.2f);



        FuturePathTileHaliteFactorV2 = MultiplyByRange(FuturePathTileHaliteFactorV2, 0.65f,1.4f);
        FuturePathMeaningfulAlterationsFactorV2 = MultiplyByRange(FuturePathMeaningfulAlterationsFactorV2, 0.65f,1.4f);
        FuturePathMeaningfulAlterationsFactorNonFinishV2 = MultiplyByRange(FuturePathMeaningfulAlterationsFactorNonFinishV2, 0.65f,1.4f);
        FuturePathMeaningfulAlterationsFactorNonFinishV2 = MultiplyByRange(FuturePathMeaningfulAlterationsFactorNonFinishV2, 0.65f,1.4f);
        FuturePathPathLengthNonFinishV2 = MultiplyByRange(FuturePathPathLengthNonFinishV2, 0.65f,1.4f);
        FuturePathTurnsDropoffNonFinishV2 = MultiplyByRange(FuturePathTurnsDropoffNonFinishV2, 0.65f,1.4f);
        FuturePathMinHaliteFinishV2 = Range(FuturePathMinHaliteFinishV2, 750,920);
        FuturePathProportionAvgV2 = MultiplyByRange(FuturePathProportionAvgV2, 0.65f,1.4f);
        FuturePathProportionAvgV2 = MultiplyByRange(FuturePathProportionAvgV2, 0.65f,1.4f);
        ThresholdMeaningfulAlterationV2 = MultiplyByRange(ThresholdMeaningfulAlterationV2, 0.65f,1.4f);
        FuturePathMinHalitePerTurnV2 = MultiplyByRange(FuturePathMinHalitePerTurnV2, 0.65f,1.4f);
        FuturePathMinHaliteNonFinish = MultiplyByRange(FuturePathMinHaliteNonFinish, 0.65f,1.4f);
        FuturePathBaseDistMeaningfulV2 = MultiplyByRange(FuturePathBaseDistMeaningfulV2, 0.65f,1.4f);
        FuturePathMaxQueue = MultiplyByRange(FuturePathMaxQueue, 0.65f,1.4f);
        FuturePathMaxQueue = MultiplyByRange(FuturePathMaxQueue, 0.65f,1.4f);
        FuturePathMaxMoveDepth = MultiplyByRange(FuturePathMaxMoveDepth, 0.65f,1.4f);
        FuturePathMaxMoveDepth = MultiplyByRange(FuturePathMaxMoveDepth, 0.65f,1.4f);


        PrioFuturePathDistDropoff = MultiplyByRange(PrioFuturePathDistDropoff, 0.65f,1.4f);
        PrioFuturePathDistDropoff = MultiplyByRange(PrioFuturePathDistDropoff, 0.65f,1.4f);
        PrioFuturePathHalite = Range(PrioFuturePathHalite, -0.3f,0.3f);
        PrioFuturePathCrowdedEnemy = Range(PrioFuturePathCrowdedEnemy, -10f,10f);
        PrioFuturePathCrowdedMy = Range(PrioFuturePathCrowdedMy, -10f,10f);
        PrioFuturePathHaliteAround = MultiplyByRange(PrioFuturePathHaliteAround, 0.65f,1.4f);
        PrioFuturePathHaliteAround = MultiplyByRange(PrioFuturePathHaliteAround, 0.65f,1.4f);



        AntiInspire4pMinTurnV2 = MultiplyByRange(AntiInspire4pMinTurnV2, 0.65f,1.4f);
        AntiInspire4pMinTurnV2 = MultiplyByRange(AntiInspire4pMinTurnV2, 0.65f,1.4f);
        AntiInspire4pMinPlayerDifV2 = MultiplyByRange(AntiInspire4pMinPlayerDifV2, 0.65f,1.4f);
        AntiInspire4pMinPlayerDifV2 = MultiplyByRange(AntiInspire4pMinPlayerDifV2, 0.65f,1.4f);
        AntiInspire6ContributionV2 = MultiplyByRange(AntiInspire6ContributionV2, 0.65f,1.4f);
        AntiInspire6ContributionV2 = MultiplyByRange(AntiInspire6ContributionV2, 0.65f,1.4f);
        AntiInspire4ContributionV2 = MultiplyByRange(AntiInspire4ContributionV2, 0.65f,1.4f);
        AntiInspire4ContributionV2 = MultiplyByRange(AntiInspire4ContributionV2, 0.65f,1.4f);
        AntiInspire2ContributionV2 = MultiplyByRange(AntiInspire2ContributionV2, 0.65f,1.4f);
        AntiInspire2ContributionV2 = MultiplyByRange(AntiInspire2ContributionV2, 0.65f,1.4f);
        AntiInspireBaseDecreaseByV2 = MultiplyByRange(AntiInspireBaseDecreaseByV2, 0.65f,1.4f);
        AntiInspireBaseDecreaseByV2 = MultiplyByRange(AntiInspireBaseDecreaseByV2, 0.65f,1.4f);
        AntiInspireMoveWeightV2 = MultiplyByRange(AntiInspireMoveWeightV2, 0.65f,1.4f);
        AntiInspireMoveWeightV2 = MultiplyByRange(AntiInspireMoveWeightV2, 0.65f,1.4f);
        AntiInspireGoalWeightV2 = MultiplyByRange(AntiInspireGoalWeightV2, 0.65f,1.4f);
        AntiInspireGoalWeightV2 = MultiplyByRange(AntiInspireGoalWeightV2, 0.65f,1.4f);
        AntiInspireGoalWeightV2 = MultiplyByRange(AntiInspireGoalWeightV2, 0.65f,1.4f);


        InspireNextTurnWorth = MultiplyByRange(InspireNextTurnWorth, 0.65f,1.4f);
        InspireNextTurnWorth = MultiplyByRange(InspireNextTurnWorth, 0.65f,1.4f);


        MurderMoreHalite = MultiplyByRange(MurderMoreHalite, 0.65f,1.4f);
        MurderHaliteDif = MultiplyByRange(MurderHaliteDif, 0.65f,1.4f);
        MurderControlFlat = MultiplyByRange(MurderControlFlat, 0.65f,1.4f);
        MurderControl = MultiplyByRange(MurderControl, 0.65f,1.4f);
        MurderHalite = MultiplyByRange(MurderHalite, 0.65f,1.4f);
        MurderHaliteEnemyHalite = MultiplyByRange(MurderHaliteEnemyHalite, 0.65f,1.4f);
        MurderFullEnemy = MultiplyByRange(MurderFullEnemy, 0.65f,1.4f);
        MurderTileHalite = MultiplyByRange(MurderTileHalite, 0.65f,1.4f);
        Murder4p = MultiplyByRange(Murder4p, 0.65f,1.4f);


        TileBorderFlat = MultiplyByRange(TileBorderFlat, 0.65f,1.4f);
        TileBorderHalite = MultiplyByRange(TileBorderHalite, 0.65f,1.4f);



        CollisionKnob = MultiplyByRange(CollisionKnob, 0.65f,1.4f);
        CollisionKnob = MultiplyByRange(CollisionKnob, 0.65f,1.4f);
        CollisionsKnobGameType = MultiplyByRange(CollisionsKnobGameType, 0.65f,1.4f);
        CollisionsKnobGameType = MultiplyByRange(CollisionsKnobGameType, 0.65f,1.4f);
        CollisionsKnobDensity = MultiplyByRange(CollisionsKnobDensity, 0.65f,1.4f);
        CollisionsKnobDensity = MultiplyByRange(CollisionsKnobDensity, 0.65f,1.4f);




        ExperimentalSimulTurnInHalite = MultiplyByRange(ExperimentalSimulTurnInHalite, 0.65f,1.4f);
        ExperimentalSimulTurnInHalite = MultiplyByRange(ExperimentalSimulTurnInHalite, 0.65f,1.4f);
        ExperimentalSimulGoal = MultiplyByRange(ExperimentalSimulGoal, 0.65f,1.4f);
        ExperimentalSimulGoal = MultiplyByRange(ExperimentalSimulGoal, 0.65f,1.4f);
        ExperimentalSimulReachedGoal = MultiplyByRange(ExperimentalSimulReachedGoal, 0.65f,1.4f);
        ExperimentalSimulReachedGoal = MultiplyByRange(ExperimentalSimulReachedGoal, 0.65f,1.4f);
        ExperimentalSimulVisited = MultiplyByRange(ExperimentalSimulVisited, 0.65f,1.4f);
        ExperimentalSimulVisited = MultiplyByRange(ExperimentalSimulVisited, 0.65f,1.4f);
        ExperimentalSimulMaxTurns = MultiplyByRange(ExperimentalSimulMaxTurns, 0.65f,1.4f);
        ExperimentalSimulMaxTurns = MultiplyByRange(ExperimentalSimulMaxTurns, 0.65f,1.4f);
        ExperimentalSimulWidth = MultiplyByRange(ExperimentalSimulWidth, 0.65f,1.4f);
        ExperimentalSimulWidth = MultiplyByRange(ExperimentalSimulWidth, 0.65f,1.4f);
        ExperimentalEOdds = MultiplyByRange(ExperimentalEOdds, 0.65f,1.4f);
        ExperimentalEOdds = MultiplyByRange(ExperimentalEOdds, 0.65f,1.4f);


        if(PLAN_STYLE == Plan.STYLE_RANDOMIZE){
             PLAN_STYLE = RangeAlways(PLAN_STYLE,0,Plan.STYLE_COUNT);
        } else if(PLAN_STYLE == Plan.STYLE_RANDOMIZE_LIMITED){
            int rand = MyBot.rand.nextInt(6);
            switch (rand){
                case  0:
                    PLAN_STYLE = Plan.STYLE_MINIMAL_4;
                    break;
                case  1:
                    PLAN_STYLE = Plan.STYLE_MINIMAL_5;
                    break;
                case  2:
                    PLAN_STYLE = Plan.STYLE_NEWSTYLE_4;
                    break;
                case  3:
                    PLAN_STYLE = Plan.STYLE_MINIMAL_3;
                    break;
                case  4:
                    PLAN_STYLE = Plan.STYLE_MINIMAL_4_EXPANDED;
                    break;
                default:
                    PLAN_STYLE = Plan.STYLE_MINIMAL_5_BROAD;
                    break;
            }


        }
    }

    public static void RandomizeExperimental(){
    }




    public static float Range(float StartVal, float start, float end){
        if(ShouldRandomize()){
            return (MyBot.rand.nextFloat() * (end-start)) + start;
        } else if(ShouldRandomizeSmall()){
            float val1 = (MyBot.rand.nextFloat() * (end-start)) + start;
            float dif = (StartVal - val1) * MyBot.SMALL_RANDOMIZE_FACTOR;
            return StartVal - dif;

        }
        return StartVal;
    }

    public static float[] Range(float[] StartVal, float start, float end){

        int type;
        if(StartVal.length == 10){
            type = MyBot.GAMETYPE;
        } else if(StartVal.length == 5){
            type = MyBot.GAMETYPE_SIZE;
        } else if(StartVal.length == 4){
            type = MyBot.GAMETYPE_DENSITY;
        }else{
            type = MyBot.GAMETYPE_PLAYERS;
        }
        if(ShouldRandomize()){

            StartVal[type] = (MyBot.rand.nextFloat() * (end-start)) + start;
        }else if(ShouldRandomizeSmall()){


            float val1 = (MyBot.rand.nextFloat() * (end-start)) + start;
            float dif = (StartVal[type] - val1) * MyBot.SMALL_RANDOMIZE_FACTOR;
            StartVal[type] =  StartVal[type] - dif;

        }
        return StartVal;
    }


    public static float[] MultiplyByRange(float[] StartVal, float lower, float upper){

        int type;
        if(StartVal.length == 10){
            type = MyBot.GAMETYPE;
        } else if(StartVal.length == 5){
            type = MyBot.GAMETYPE_SIZE;
        } else if(StartVal.length == 4){
            type = MyBot.GAMETYPE_DENSITY;
        }else{
            type = MyBot.GAMETYPE_PLAYERS;
        }
        if(ShouldRandomize()){
            float start = StartVal[type] * lower;
            float end = StartVal[type] * upper;
            StartVal[type] =  (MyBot.rand.nextFloat() * (end-start)) + start;
        }  else if(ShouldRandomizeSmall()){
            float start = StartVal[type] * lower;
            float end = StartVal[type] * upper;
            float val1 =  (MyBot.rand.nextFloat() * (end-start)) + start;
            float dif = (StartVal[type] - val1) * MyBot.SMALL_RANDOMIZE_FACTOR;
            StartVal[type] =  StartVal[type] - dif;

        }
        return StartVal;
    }

    public static float RangeAlways(float StartVal, float start, float end){

            return (MyBot.rand.nextFloat() * (end-start)) + start;

    }



    public static float MultiplyByRange(float StartVal, float lower, float upper){
        if(ShouldRandomize()){
            float start = StartVal * lower;
            float end = StartVal * upper;
            return (MyBot.rand.nextFloat() * (end-start)) + start;
        }
        else if(ShouldRandomizeSmall()){
            float start = StartVal * lower;
            float end = StartVal * upper;
            float val1 = (MyBot.rand.nextFloat() * (end-start)) + start;
            float dif = (StartVal - val1) * MyBot.SMALL_RANDOMIZE_FACTOR;
            return StartVal - dif;

        }
        return StartVal;
    }

    public static int MultiplyByRange(int StartVal, float lower, float upper){
        if(ShouldRandomize()){
            float start = StartVal * lower;
            float end = StartVal * upper;
            return (int)((MyBot.rand.nextFloat() * (end-start)) + start);
        } else if(ShouldRandomizeSmall()){
            float start = StartVal * lower;
            float end = StartVal * upper;
            float val1 = (MyBot.rand.nextFloat() * (end-start)) + start;
            float dif = (StartVal - val1) * MyBot.SMALL_RANDOMIZE_FACTOR;
            return (int)(StartVal - dif);

        }
        return StartVal;
    }

    public static float Around(float StartVal, float range){
        if(ShouldRandomize()){
            return   StartVal - range +  (MyBot.rand.nextFloat() * 2.0f * range);
        }else if(ShouldRandomizeSmall()) {

            float val1 = StartVal - range + (MyBot.rand.nextFloat() * 2.0f * range);
            float dif = (StartVal - val1) * MyBot.SMALL_RANDOMIZE_FACTOR;
            return   StartVal - dif;
        }
        return StartVal;
    }

    public static float[] Around(float[] StartVal, float range){

        int type;
        if(StartVal.length == 10){
            type = MyBot.GAMETYPE;
        } else if(StartVal.length == 5){
            type = MyBot.GAMETYPE_SIZE;
        } else if(StartVal.length == 4){
            type = MyBot.GAMETYPE_DENSITY;
        }else{
            type = MyBot.GAMETYPE_PLAYERS;
        }

        if(ShouldRandomize()){
            StartVal[type] =   StartVal[type] - range +  (MyBot.rand.nextFloat() * 2.0f * range);
        }else if(ShouldRandomizeSmall()){

            float val1 = StartVal[type] - range +  (MyBot.rand.nextFloat() * 2.0f * range);
            float dif = (StartVal[type] - val1) * MyBot.SMALL_RANDOMIZE_FACTOR;
            StartVal[type] =  StartVal[type] - dif;

        }
        return StartVal;
    }


    public static float AroundExpo(float StartVal, float range){
        if(ShouldRandomize()){
            float difFactor = MyBot.rand.nextFloat();
            return   StartVal - range * difFactor +  (MyBot.rand.nextFloat() * 2.0f * range * difFactor);
        }else if(ShouldRandomizeSmall()){

            float difFactor = MyBot.rand.nextFloat();
            float val1 = StartVal - range * difFactor +  (MyBot.rand.nextFloat() * 2.0f * range * difFactor);
            float dif = ((StartVal - val1) * MyBot.SMALL_RANDOMIZE_FACTOR);
            return StartVal - dif;
        }
        return StartVal;
    }

    public static int Range(int StartVal, int start, int end){
        if(ShouldRandomize()){
            return start + MyBot.rand.nextInt(end-start + 1);
        }  else if(ShouldRandomizeSmall()){
            int val1 = start + MyBot.rand.nextInt(end-start + 1);
            int dif = (int)((StartVal - val1) * MyBot.SMALL_RANDOMIZE_FACTOR);
            return StartVal - dif;

        }
        return StartVal;
    }
    public static int RangeAlways(int StartVal, int start, int end){
        return start + MyBot.rand.nextInt(end-start + 1);
    }

    public static int Around(int StartVal, int range){
        if(ShouldRandomize()){
            return   StartVal - range +  MyBot.rand.nextInt(range * 2);
        }else if(ShouldRandomizeSmall()){
            int val1 = StartVal - range +  MyBot.rand.nextInt(range * 2);
            int dif = (int)((StartVal - val1) * MyBot.SMALL_RANDOMIZE_FACTOR);
            return StartVal - dif;
        }
        return StartVal;
    }


    public static boolean ShouldRandomize(){
        return MyBot.rand.nextFloat() < MyBot.RANDOMIZE_AMOUNT;
    }
    public static boolean ShouldRandomizeSmall(){
        return MyBot.rand.nextFloat() < MyBot.SMALL_RANDOMIZE_AMOUNT;
    }



    public static void SpecialBunches(){
        float special1 = 1f + SpecialBunch1[MyBot.GAMETYPE] * SpecialBunchDensity[MyBot.GAMETYPE_DENSITY];

        HandwavyWeights.GoalNearbyEraPlayers[MyBot.GAMETYPE_PLAYERS] += 1f * special1;
        HandwavyWeights.TileScoreLong += 1.3f * special1;
        HandwavyWeights.GoalWeightHaliteV2 += 1.6f * special1;
        HandwavyWeights.LongLureEmptyishV2 += 1.5f * special1;
        HandwavyWeights.MyShipRange3Weight[MyBot.GAMETYPE_PLAYERS] += 5f * special1;
        HandwavyWeights.MyShipRange5Weight[MyBot.GAMETYPE_PLAYERS] += 2f * special1;

        if(special1 > 1){
            HandwavyWeights.WeirdAlgoMaxGathersByOtherShips = 4;
        }


    }
}
