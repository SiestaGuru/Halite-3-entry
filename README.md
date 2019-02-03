# Halite-3-entry

This is my entry for the Halite 3 competition, it ended up reaching second place in the finals. You can read more about the game here: https://halite.io/

Disclaimer: This code is optimized for the game first, performance second, programming speed third. It's not at all optimized for readability or maintainability, so it'll be hard to read and probably impossible to comprehend.

There's some information about the structure of my bot, and about my approach in the comments inside.

A quick summary:

This bot tries to find possible future states resulting from movesets. It uses various methods to come to moveset plans, simulates them out to find out what happens, and then uses massive evaluator functions to determine how good each of these states are. The state with the highest score ends up deciding what moves will be picked. Ship and dropoff building are done separately.
The giant evaluators include a lot of data, including the results of a large amount of side algorithms. Some of these algorithms are even capable of being used as bots on their own, but are only used as weights inside of the main algorithm that change the values of the movesets/states.
You could say the bot works like some kind of ensemble method, where lots of different approaches are used and eventually merged together to come to the best moves.

A lot of parameters (on the order of 1k) are used as part of this decision making. Tuning of these parameters was doing by partially randomizing these variables in all games run in batch mode. By running a lot of games and collecting the values of these parameters, as well as data about the game such as: the amount of players, the amount of points I got, how efficient my ships were and whether I won or not; I was able to do a statistical analysis on this data to help me tune all my parameters. I used a homemade external tool for this, which allowed me to both plot data and to use automated statistical analysis methods. For more info, see the code file called HandwavyWeights.

Besides that, I also made a custom replay viewer based on data written to file during bot execution. Besides being able to replay the games, this tool allowed me to easily see indepth information of my bot. For example, I made a view that made it show future predictions of the inspire mechanic.

Neither of these two tools have been included here. While they were extremely useful, they're terrible. 

Performace was a massive issue for me throughout development, in particular GC performance. A bit 'stop the world' GC happening during execution of my bot, could make me go over the time limit of 2 seconds, making me instantly lose. This was a major pain since my entire approach revolved around being able to get a good amount of moveset-simulations in, and these simulations both cost time and required memory allocations to work. These memory allocations increased the frequency and duration of GC events, making a reduction in allocations a top priority for me throughout the competition. Considering how much my bot actually does per turn, I'm happy with the final results, even if it required lots of strange hacks and weird code to make possible.  For some detail on the two most important weird hacks, see the code file called Map.






Acknowledgements:

Thanks to the Two Sigma, the Halite devs and Janzert for the fun and well-run competition. And thanks to the players Fohristiwhirl and Mlomb for the extremely useful tools they've made, I owe a lot of my success to them. Also, I'd like to thank all those active on discord for making the competition a lot more enjoyable and for all the interesting discussions we've had (many of which have been useful)


