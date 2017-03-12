package io.devenney.carpooling;

import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

public class CarpoolerAgent extends Agent {
	// The agent representing a single user in the system.
	// Ultimately will respond to reputation and user tag queries.

	Properties carpoolDetails = new Properties();
	private Agent myAgent = this;
	private ArrayList<HashMap> routes = new ArrayList<HashMap>();
	
	// Runs on CarpoolerAgent creation
	protected void setup() {
		
		//Behaviour to receive arbitrary messages
		addBehaviour(new TickerBehaviour(this, 1000) {

			protected void onTick() {
				ACLMessage msg = receive();
				// If the message has content
				if (msg != null) {
					// TODO(brendan): Move reputation here
					// TODO(brendan): Handle user tags
				}
				// Otherwise wat, take it back!
				else {
					block();
				}
			}
		});
		
	}
}
