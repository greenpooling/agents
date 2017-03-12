# Multi-Agent Carpooling System

## Building

    mvn package

## Running

Export DB configuration:

    export DB_CONN="jdbc:postgresql://localhost:5432/carpooling"
    export DB_USER=postgres
    export DB_PASS=postgres

Run the main container (includes AgentFactory):

    mvn -Pjade-main exec:java

Run as many agent containers as desired:

    mvn -Pjade-agent exec:java
