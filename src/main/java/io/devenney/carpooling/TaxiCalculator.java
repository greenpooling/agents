package io.devenney.carpooling;

import java.util.ArrayList;
import java.util.HashMap;

public class TaxiCalculator {
	
	// Singleton which can be used to calculate the taxi fare
	// between two campuses

	/************************************
	 * 
	 * 6PM-6AM: ADD £1 ALL COSTS
	 * 
	 *  Merchiston - Sighthill
	 * 		£16.93 - £21.13 - £39.38
	 * 	
	 * 	Sighthill - Craiglockhart
	 * 		£13.46 - £16.87 - £31.77
	 * 
	 * 	Merchiston - Craiglockhart
	 * 		£9.30 - £12.93 - £28.80
	 *
	 ************************************/

	private static TaxiCalculator instance;

	private HashMap<String, ArrayList<Float>> prices;

	protected TaxiCalculator() {
		// Populate the lists with fixed values
		prices = new HashMap<String, ArrayList<Float>>();

		ArrayList<Float> ms = new ArrayList<Float>();
		ms.add(16.93f);
		ms.add(21.13f);
		ms.add(39.38f);

		ArrayList<Float> sc = new ArrayList<Float>();
		sc.add(13.46f);
		sc.add(16.87f);
		sc.add(31.77f);

		ArrayList<Float> mc = new ArrayList<Float>();
		mc.add(9.30f);
		mc.add(12.93f);
		mc.add(28.80f);

		prices.put("1,2", ms);
		prices.put("2,1", ms);
		prices.put("2,3", sc);
		prices.put("3,2", sc);
		prices.put("1,3", mc);
		prices.put("3,1", mc);
	}

	// Singleton
	public synchronized static TaxiCalculator getInstance() {
		if (instance == null) {
			instance = new TaxiCalculator();
		}
		return instance;
	}

	// Return the arraylist associated with two endpoints
	public ArrayList<Float> getCost(String endpoints) {
		return prices.get(endpoints);
	}
}
