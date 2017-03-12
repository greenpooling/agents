package io.devenney.carpooling;

import jade.content.ContentElement;
import jade.content.lang.Codec.CodecException;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.OntologyException;
import jade.content.onto.UngroundedException;
import jade.content.onto.basic.Action;
import jade.content.onto.basic.Result;
import jade.core.AID;
import jade.core.Agent;
import jade.core.ContainerID;
import jade.domain.FIPANames;
import jade.domain.JADEAgentManagement.CreateAgent;
import jade.domain.JADEAgentManagement.JADEManagementOntology;
import jade.domain.JADEAgentManagement.QueryPlatformLocationsAction;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREInitiator;
import jade.util.leap.List;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.postgresql.PGConnection;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.Random;

public class AgentFactory extends Agent {
	// The Agent responsible for agent spawning based on Database state and events
  protected final Logger logger = LogManager.getLogger(getClass().getName());

	private ArrayList<ContainerID> containers = new ArrayList<ContainerID>();
	private ArrayList<String> createdAgents = new ArrayList<String>();

	//PostgreSQL Listener
	private class Listener extends Thread {

		private Connection conn;
		private org.postgresql.PGConnection pgconn;

		//Connection which watches for messages on the "watchers" channel
		Listener(Connection conn) throws SQLException {
			this.conn = conn;
			this.pgconn = (org.postgresql.PGConnection)conn;
			Statement stmt = conn.createStatement();
			stmt.execute("LISTEN watchers");
			stmt.close();
		}

		public void run() {
			while (true) {

        getContainers();

				try {
					//Get "watchers" notifications
					org.postgresql.PGNotification notifications[] = pgconn.getNotifications();

					if (notifications != null) {
						for (int i=0; i<notifications.length; i++) {
							//Determine table of notification
							switch (notifications[i].getParameter().split(",")[0]) {
							//Users
							case "users":
								Statement userStmt = conn.createStatement();
								ResultSet users = userStmt.executeQuery("SELECT * FROM users WHERE id=" + notifications[i].getParameter().charAt(notifications[i].getParameter().length()-1) + ";");
								while (users.next()) {
									Properties userProps = new Properties();
									userProps.put("forename", users.getString(2));
									userProps.put("surname", users.getString(3));
									userProps.put("department", users.getString(4));
									createAgent("user"+users.getString(1), 0, userProps);
								} users.close();
								userStmt.close();
								break;
							//Carpools
							case "carpools":
								Statement carpoolsStmt = conn.createStatement();
								ResultSet carpools = carpoolsStmt.executeQuery("SELECT * FROM carpools WHERE id=" + notifications[i].getParameter().split(",")[1] + ";");
								while (carpools.next()) {
									Properties carpoolProps = new Properties();
									carpoolProps.put("capacity", carpools.getString(2));
									carpoolProps.put("origin", carpools.getString(3));
									carpoolProps.put("destination", carpools.getString(4));
									carpoolProps.put("date", carpools.getString(5));
									carpoolProps.put("tdepart", carpools.getString(6));
									carpoolProps.put("tarrive", carpools.getString(7));
									carpoolProps.put("organiser", carpools.getString(8));
									carpoolProps.put("state", carpools.getString(9));
									carpoolProps.put("roundtrip", carpools.getString(11));
									createAgent("route"+carpools.getString(1), 1, carpoolProps);
								} carpools.close();
								carpoolsStmt.close();
								break;
							}
						}

            Thread.sleep(1000);

					}

					// Wait a while before checking again.
					Thread.sleep(500);
				} catch (SQLException sqle) {
					//Database issue, generally no free connections or connectivity issue
					sqle.printStackTrace();
				} catch (InterruptedException ie) {
					//Interrupted during sleep
					ie.printStackTrace();
				}
			}
		}
	}

	//Setup function - automatically runs when AgentFactory is created
	protected void setup() {

		//Get all alive containers at launch
		getContainers();

		//Database properties
		String url = System.getenv("DB_CONN");

    Properties props = new Properties();

		props.setProperty("user", System.getenv("DB_USER"));
    props.setProperty("password", System.getenv("DB_PASS"));

		// Respawn existing agents from DB
		try {
			Connection conn = DriverManager.getConnection(url,props);
			PGConnection pgconn = (org.postgresql.PGConnection)conn;
			Statement stmt = conn.createStatement();

			//Users
			ResultSet userResult = stmt.executeQuery("SELECT * FROM users" + ";");
			while (userResult.next()) {
				Properties userProps = new Properties();
				userProps.put("forename", userResult.getString(2));
				userProps.put("surname", userResult.getString(3));
				userProps.put("department", userResult.getString(4));
				createAgent("user"+userResult.getString(1), 0, userProps);
			} userResult.close();

			//Carpools
			ResultSet carpoolResult = stmt.executeQuery("SELECT * FROM carpools;");
			while (carpoolResult.next()) {
				Properties carpoolProps = new Properties();
				Integer id = Integer.valueOf(carpoolResult.getString(1));

				carpoolProps.put("capacity", carpoolResult.getString(2));
				carpoolProps.put("origin", carpoolResult.getString(3));
				carpoolProps.put("destination", carpoolResult.getString(4));
				carpoolProps.put("date", carpoolResult.getString(5));
				carpoolProps.put("tdepart", carpoolResult.getString(6));
				carpoolProps.put("tarrive", carpoolResult.getString(7));
				carpoolProps.put("organiser", carpoolResult.getString(8));
				carpoolProps.put("state", carpoolResult.getString(9));
				try {
					carpoolProps.put("roundtrip", carpoolResult.getString(10));
				} catch (Exception e) {
					carpoolProps.put("roundtrip", true);
				}

				createAgent("route"+id, 1, carpoolProps);
			} carpoolResult.close();

			stmt.close();			
		} catch (SQLException e) {
			e.printStackTrace();
		}

		//JDBC Listen for notify
		try {
			Connection lConn = DriverManager.getConnection(url,props);

			//Spawn a listener thread
			Listener listener = new Listener(lConn);
			listener.start();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	// Function to create an AMS Message from a CreateAgent object
	private void agentManagementMessage(AID ams, CreateAgent ca) {

		//AMS Action
		Action actExpr = new Action(getAMS(), ca);

		//Agent creation message must be a REQUEST
		ACLMessage request = new ACLMessage(ACLMessage.REQUEST);

		request.addReceiver(ams);

		//Define the conversation lanaguage and ontology
		getContentManager().registerLanguage(new SLCodec(), FIPANames.ContentLanguage.FIPA_SL);
		getContentManager().registerOntology(JADEManagementOntology.getInstance());
		request.setOntology(JADEManagementOntology.getInstance().getName());
		request.setLanguage(FIPANames.ContentLanguage.FIPA_SL);
		request.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);

		//Ask the AMS to spawn the agent
		try {
			getContentManager().fillContent(request, actExpr);
			addBehaviour(new AchieveREInitiator(this, request) {
				//Creation successful
				protected void handleInform(ACLMessage inform) {
					//TODO(brendan): Log something.
				}

				//Creation failed
				protected void handleFailure(ACLMessage failure) {
					//TODO(brendan): Determine failure cause and retry.
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Function to get all containers attached to Main-Container
	private void getContainers() {
		QueryPlatformLocationsAction query = new QueryPlatformLocationsAction();
		Action action = new Action(this.getAID(), query);

		// Message to query the AMS must be a REQUEST
		ACLMessage message = new ACLMessage(ACLMessage.REQUEST);
		message.addReceiver(this.getAMS());

		// Establish conversation language and ontology
		message.setLanguage(FIPANames.ContentLanguage.FIPA_SL);
		message.setOntology(JADEManagementOntology.getInstance().getName());
		getContentManager().registerLanguage(new SLCodec(), FIPANames.ContentLanguage.FIPA_SL);
		getContentManager().registerOntology(JADEManagementOntology.getInstance());

		// Try to add the Platform Locations query to the message
		try {
			getContentManager().fillContent(message, action);
		} catch (CodecException e) {
			// Conversation language exception
			e.printStackTrace();
		} catch (OntologyException e) {
			// Conversation ontology exception
			e.printStackTrace();
		}
		this.send(message);

		// Get the response from the AMS
		ACLMessage receivedMessage = this.blockingReceive(MessageTemplate.MatchSender(this.getAMS()));
		ContentElement content;

		// Try to extract the message content and parse it into a list of ContainerIDs
		try {
			content = this.getContentManager().extractContent(receivedMessage);
			Result result = (Result) content;
			List listOfPlatforms = (List) result.getValue();

			Iterator iter = listOfPlatforms.iterator();
			while (iter.hasNext()) {
				ContainerID next = (ContainerID) iter.next();
				if (!containers.contains(next)) {
          logger.info("Adding container " + next + " to container list.");
          containers.add(next);
        }
			}
		} catch (UngroundedException e) {
			// TODO(brendand): RTFM. What even is this?
			e.printStackTrace();
		} catch (CodecException e) {
			// Invalid conversation language.
			e.printStackTrace();
		} catch (OntologyException e) {
			// Invalid conversation ontology.
			e.printStackTrace();
		}
	}

	// Agent creation using properties from DB (setup query or notification)
	private void createAgent(String nickname, int agentType, Properties props) {

		// Specification of the Agent to create
		CreateAgent ca = new CreateAgent();

		// TODO(brendand): Load balancing.
		// Spawn the agent in a random container.
		Random rng = new Random();
		int rand = rng.nextInt(containers.size());
		ca.setContainer(new ContainerID(containers.get(rand).getName(), null));

		//Set the agent's nickname (will become this.getLocalName() within Agent)
		ca.setAgentName(nickname);

		//Determine the agent type (Carpooler or Route)
		switch (agentType) {
		case 0:
			ca.setClassName(CarpoolerAgent.class.getName());
			ca.addArguments(props.getProperty("forename"));
			ca.addArguments(props.getProperty("surname"));
			ca.addArguments(props.getProperty("department"));
			break;
		case 1:
			ca.setClassName(RouteAgent.class.getName());
			ca.addArguments(props.getProperty("capacity"));
			ca.addArguments(props.getProperty("origin"));
			ca.addArguments(props.getProperty("destination"));
			ca.addArguments(props.getProperty("date"));
			ca.addArguments(props.getProperty("tdepart"));
			ca.addArguments(props.getProperty("tarrive"));
			ca.addArguments(props.getProperty("organiser"));
			ca.addArguments(props.getProperty("state"));
			break;
		default:
			break;
		}

    logger.info("Created agent " + nickname + " in container " + ca.getContainer());

		//Send the CreateAgent specification to the AMS
		agentManagementMessage(getAMS(), ca);
	}
}
