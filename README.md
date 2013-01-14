Cannon
======

Cannon is a distributed systems failure modeling tool. What happens when one node in your Dynamo system becomes
partially available? Or if your MySQL slave suddenly starts serving reads 10x slower? What strategies are ideal for
dealing with these partial failure modes? Cannon aims to provide some level of insight into these tricky situations.


Running
-------

    mvn package
    java -cp target/cannon-0.1-SNAPSHOT.jar com.idlerice.cannon.Cannon <strategy>