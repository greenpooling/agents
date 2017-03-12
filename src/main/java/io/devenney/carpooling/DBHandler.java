package io.devenney.carpooling;

import org.postgresql.PGConnection;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class DBHandler {
	// Handler to make DB queries modular and extensible

	private Properties props;
	private String url = System.getenv("DB_CONN");
	private static DBHandler instance = null;

	// Constructor establishes connection properties
	protected DBHandler() {

  	// DB connection properties
	  props = new Properties();
    String url = System.getenv("DB_CONN");
    props.setProperty("user", System.getenv("DB_USER"));
    props.setProperty("password", System.getenv("DB_PASS"));
  }

	// Singleton
	public static DBHandler getInstance() {
		if (instance == null) {
			instance = new DBHandler();
		}
		return instance;
	}

	// Function to execute an arbitrary query.
	// Care should be taken to sanitise if any call is ever added which reflects user input.
	public void update(String query) {
		try {
			Class.forName("org.postgresql.Driver");
			Connection conn = DriverManager.getConnection(url,props);
			PGConnection pgconn = (org.postgresql.PGConnection)conn;
			Statement stmt = conn.createStatement();
			stmt.execute(query);
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// org.postgresql.Driver is missing
			e.printStackTrace();
		}
	}

}
