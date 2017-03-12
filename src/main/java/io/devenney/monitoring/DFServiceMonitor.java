package monitoring;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.util.Logger;

public class DFServiceMonitor extends Agent {
	//DFService monitoring agent which watches response time
	
	private DFServiceStatus dfss;
	
	public DFServiceMonitor() {
		// Get the DFServiceStatus singleton
		dfss = DFServiceStatus.getInstance();
	}

	// Runs when DFServiceMonitor is created
	public void setup() {

		// Ticker behaviour to query the DFService
		addBehaviour(new TickerBehaviour(this, 2500) {
			protected void onTick() {
				// Establish constraints (as demanding as possible)
				SearchConstraints sc = new SearchConstraints();
				sc.setMaxResults(-1L);

				DFAgentDescription dfd = new DFAgentDescription();
				DFAgentDescription[] result = null;
				
				// Try to search the DF
				try {
					// Determine the response time
					long startTime = System.currentTimeMillis();
					result = DFService.search(getAgent(), dfd);
					long endTime = System.currentTimeMillis();
					
					long responseTime = endTime - startTime;
									
					// Set health status based on response time
					setStatus(responseTime);
				} catch (FIPAException e) {
					// Timeout searching DF
					// TODO(brendand): This should actually update the health status
					Logger.getJADELogger("Error searching DF");
				}
			}
		});
	}
	
	// Update the DFService health status contained in the singleton
	private void setStatus(long responseTime) {
		int status;
		
		//OK
		if (responseTime < 3000) {
			status = 0;
		//SLOW
		} else if (responseTime >= 3000 && responseTime < 20000) {
			status = 1;
		//UNSTABLE
		} else {
			status = 2;
		}
		
		dfss.setStatus(status);
	}
}
