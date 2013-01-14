Cannon
======

Cannon is a distributed systems failure modeling tool. What happens when one node in your Dynamo system becomes
partially available? Or if your MySQL slave suddenly starts serving reads 10x slower? What strategies are ideal for
handling with these partial failure modes? Cannon aims to provide some level of insight into these tricky situations.


Running
-------

    mvn package
    java -cp target/cannon-0.1-SNAPSHOT.jar com.idlerice.cannon.Cannon <strategy>

Where strategy is one of "random", where client requests are sent to a random server, or "affinity", where a given 
thread has a long term affinity to a given server.

Presently Cannon demonstrates one server in a three server cluster that periodically freaks out and slows down by 10x. 
