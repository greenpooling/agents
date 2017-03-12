package io.devenney.carpooling;

import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import org.postgresql.PGConnection;

import java.io.BufferedReader;
import java.io.FileReader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.NumberFormat;
import java.util.Properties;

public class EvaluationAgent extends Agent {
	//Optionally injectable agent which provides system evaluation metrics

	// Runs on EvaluationAgent creation
	protected void setup() {
		addBehaviour(new TickerBehaviour(this, 1000) {

			protected void onTick() {
				// Get the taxi fare singleton
				TaxiCalculator tc = TaxiCalculator.getInstance();

	    	//Database properties
		    String url = System.getenv("DB_CONN");

		    Properties props = new Properties();

		    props.setProperty("user", System.getenv("DB_USER"));
		    props.setProperty("password", System.getenv("DB_PASS"));

				// Object to store cost of solo carpools
				BigDecimal onePerTaxi = new BigDecimal(0.0);
				// Object to store cost of system carpools
				BigDecimal carpoolCost = new BigDecimal(0.0);

				try {
					Connection conn = DriverManager.getConnection(url,props);
					PGConnection pgconn = (org.postgresql.PGConnection)conn;
					Statement stmt = conn.createStatement();

					ResultSet carpoolResult = stmt.executeQuery("SELECT * FROM carpools;");

					// For all entries in Carpools table
					while (carpoolResult.next()) {
						Properties carpoolProps = new Properties();
						Integer id = Integer.valueOf(carpoolResult.getString(1));

						carpoolProps.put("origin", carpoolResult.getString(3));
						carpoolProps.put("destination", carpoolResult.getString(4));
						carpoolProps.put("noMembers", carpoolResult.getString(10));

						// cost = taxiFare(origin, destination)
						BigDecimal cost = BigDecimal.valueOf(tc.getCost(carpoolProps.get("origin")
												  + "," + carpoolProps.get("destination")).get(1));

						// Check if the carpool is a round trip (doubles cost)
						try {
							carpoolProps.put("roundTrip", carpoolResult.getString(11));
						} catch (Exception e) {
							carpoolProps.put("roundTrip", 0);
						}

						// If the carpool has left SEEK_CARPOOL stage (otherwise validation hasn't occurred)
						if (Integer.valueOf(carpoolProps.get("noMembers").toString()) != 0) {
							switch(carpoolProps.get("roundTrip").toString()) {
							// Round trip
							case "1":
								carpoolCost = carpoolCost.add(cost.multiply(BigDecimal.valueOf(2)));
								onePerTaxi = onePerTaxi.add(cost.multiply(BigDecimal.valueOf(Integer.valueOf(carpoolProps.getProperty("noMembers").toString()))).multiply(BigDecimal.valueOf(2)));
								break;
							// One-way
							default:
								carpoolCost = carpoolCost.add(cost);
								onePerTaxi = onePerTaxi.add(cost.multiply(BigDecimal.valueOf(Integer.valueOf(carpoolProps.getProperty("noMembers").toString()))));
								break;
							}
						}
					} carpoolResult.close();
				} catch (Exception e) {
					e.printStackTrace();
				}

				// Print metrics
				// TODO(brendan): Log these to a file for headless execution
				System.out.printf("%s: %s\n", "Sum of carpool costs: ", currencyFormat(carpoolCost));
				System.out.printf("%s: %s\n", "Cost of one-per-taxi (solo commute)", currencyFormat(onePerTaxi));
				System.out.printf("%s: %s\n", "Savings:", currencyFormat(onePerTaxi.subtract(carpoolCost)));
			}
		});
	}

	// Format BigDecimal into printable currency String
	private static String currencyFormat(BigDecimal n) {
		return NumberFormat.getCurrencyInstance().format(n);
	}
}
