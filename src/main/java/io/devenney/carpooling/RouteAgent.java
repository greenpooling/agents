package io.devenney.carpooling;

import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import org.postgresql.PGConnection;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import monitoring.DFServiceStatus;

public class RouteAgent extends Agent {

	// FSM STATES
	private int state;
	static final int SEEK_CARPOOL = 0;
	static final int AWAIT_OFFERS = 1;
	static final int AWAIT_CONFIRM = 2;
	static final int AWAIT_REQUESTS = 3;
	static final int AWAIT_ACCEPT = 4;
	static final int COMPLETE = 5;
	static final int CHECK_ACCEPT = 6;

	// CAMPUSES
	static final int MERCHISTON = 1;
	static final int SIGHTHILL = 2;
	static final int CRAIGLOCKHART = 3;

	// Carpool details
	Properties details = new Properties();
	private int id;
	private int noMembers = 0;

	// Communication details
	private DFAgentDescription dfd;
	private ServiceDescription sd;
	private Agent myAgent = this;
	private ArrayList<AID> carpoolers = new ArrayList<AID>();
	private int noAgents = 0;
	HashMap<AID, ACLMessage> proposalMap = new HashMap<AID, ACLMessage>();
	HashMap<AID, Float> costMap = new HashMap<AID, Float>();

	// Message details (shared between states)
	String content;
	Time earliest, latest;

	// DFService Monitor singleton
	DFServiceStatus dfss = DFServiceStatus.getInstance();

	public RouteAgent() { }

	// Turn a message type and content into an ACLMessage
	protected ACLMessage genMessage(int msgType, String content) {
		ACLMessage msg = new ACLMessage(msgType);
		msg.setContent(content);
		return msg;
	}

	// Run on RouteAgent creation
	protected void setup() {

		// Populate carpool properties with arguments in AMS creation request
		details.put("capacity", Integer.valueOf(this.getArguments()[0].toString()));
		details.put("origin", this.getArguments()[1].toString());
		details.put("destination", this.getArguments()[2].toString());
		details.put("date", this.getArguments()[3].toString());
		details.put("tdepart", this.getArguments()[4].toString());
		details.put("tarrive", this.getArguments()[5].toString());
		details.put("organiser", this.getArguments()[6].toString());
		details.put("state", this.getArguments()[7].toString());
		id = Integer.valueOf(this.getLocalName().substring(5, this.getLocalName().length()));
		updateState(Integer.valueOf(details.get("state").toString()));

		// Load the JDBC driver
		try {
			Class.forName("org.postgresql.Driver");
		} catch (ClassNotFoundException e1) {
			// Class org.postgresql.Driver not present
			e1.printStackTrace();
		}

		// Database connection properties
    String url = System.getenv("DB_CONN");

    Properties props = new Properties();

    props.setProperty("user", System.getenv("DB_USER"));
    props.setProperty("password", System.getenv("DB_PASS"));

		try {
			Connection conn = DriverManager.getConnection(url,props);
			PGConnection pgconn = (org.postgresql.PGConnection)conn;
			Statement stmt = conn.createStatement();

			// HashMap to represent ucintermediary table
			HashMap<Integer, ArrayList<Integer>> ucIntermediary = new HashMap<Integer,ArrayList<Integer>>();

			// Add the organiser to the list of carpoolers (ensures carpoolers.size() == noMembers)
			AID organiser = new AID("user"+details.getProperty("organiser"), AID.ISLOCALNAME);
			carpoolers.add(organiser);
			incMemberCount();

			// Load the other carpoolers
			ResultSet intermediary = stmt.executeQuery("SELECT * FROM ucintermediary;");
			while (intermediary.next()) {
				AID a = new AID("user"+intermediary.getString(2), AID.ISLOCALNAME);
				// If the carpool ID is our carpool ID
				// and we haven't already loaded the user
				if (intermediary.getString(3).equals(this.getLocalName().substring(5, this.getLocalName().length()))
						&& !carpoolers.contains(a)) {
					carpoolers.add(a);
					// Increment the member count
					incMemberCount();
				}
			} intermediary.close();
		} catch (SQLException sqle) {
			sqle.printStackTrace();
		}

		// Agent service listing properties
		dfd = new DFAgentDescription();
		sd = new ServiceDescription();
		dfd.setName(getAID());

		// TODO(brendan): Easy switch between architecture v1 and v2

		//sd.setType("carpool");
		sd.setType("carpool" + details.get("date"));
		//sd.setType("carpool" + details.get("date") + details.get("origin") + details.get("destination"));

		sd.setName(getLocalName());
		dfd.addServices(sd);

		// Register with the Yellow Pages
		try {
			DFService.register(this, dfd);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}

		// SEEK_CARPOOL state behaviour
		OneShotBehaviour seekCarpool = new OneShotBehaviour() {

			int exitValue = SEEK_CARPOOL;

			public void action() {


				dfd = new DFAgentDescription();
				sd = new ServiceDescription();

				//TODO(brendan): Easy switch between architecture v1 and v2

				//sd.setType("carpool");
				sd.setType("carpool" + details.get("date"));
				//sd.setType("carpool" + details.get("date") + details.get("origin") + details.get("destination"));

				dfd.addServices(sd);

				// Carpool request message
				content =  details.getProperty("date")+","
						+ details.getProperty("origin")+","
						+ details.getProperty("destination")+","
						+ details.getProperty("tdepart")+","
						+ details.getProperty("tarrive")+","
						+ carpoolers.get(0).getLocalName();

				ACLMessage msg = genMessage(ACLMessage.REQUEST, content);

				// Determine the health of the DFService to establish our search constraints
				boolean canSearch = false;
				SearchConstraints sc = new SearchConstraints();
				sc.setMaxResults(-1L);
				long timeout = 30000;

				switch (dfss.getStatus()) {
				//OK
				case 0:
					canSearch = true;
					break;
				case 1:
				//SLOW
					canSearch = true;
					timeout = 100000;
					break;
				//UNSTABLE
				case 2:
					break;
				}

				// If OK or SLOW
				if (canSearch) {
					try {
						long startTime = System.currentTimeMillis();

						// Search DF for agents matching our ServiceDescription
						DFAgentDescription[] result = DFService.searchUntilFound(myAgent, new AID("df", AID.ISLOCALNAME), dfd, sc, timeout);

						// Write timing info to file
						try {
							String filename = "timing.txt";
							FileWriter fw = new FileWriter(filename,true);
							fw.write("DFService.search (Agents: " + result.length + "):" + (System.currentTimeMillis() - startTime) +"\n");
							fw.close();
						} catch (IOException e) {
							e.printStackTrace();
						}

						// Add all found agents as receivers of our carpool request message
						noAgents = result.length;
						for (int i = 0; i < result.length; ++i) {
							msg.addReceiver(result[i].getName());
						}
						send(msg);

						// Everything went okay - transition to AWAIT_OFFERS
						exitValue = updateState(AWAIT_OFFERS);
					} catch (FIPAException fe) {
						fe.printStackTrace();
					}
				// Else we're not allowed to search the DFService
				} else {
					try {
						// Back off before rechecking DFService health
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}

			// Returns the ID of the state to transition into
			public int onEnd() {
				return exitValue;
			}
		};

		// AWAIT_REQUESTS state behaviour
		OneShotBehaviour awaitRequests = new OneShotBehaviour() {

			int exitValue = AWAIT_REQUESTS;

			public void action() {

				// Receive next message in queue
				ACLMessage request = receive();

				// If message exists and has content
				if (request != null && request.getContent() != null) {
					String[] args = request.getContent().split(",");

					boolean alreadyPresent = false;

					// Determine message type
					switch (request.getPerformative()) {
					// REQUEST (from SEEK_CARPOOL)
					case ACLMessage.REQUEST:
						// Ensure the sender isn't already in our carpool
						// This is logically impossible but users can do what they want
						for (AID a : carpoolers) {
							if (args[5].equals(a.getLocalName())) {
								alreadyPresent = true;
							}
						}

						// If the sender is not in our carpool
						if (!alreadyPresent) {
							ACLMessage response;

							// Time window values
							Time tDepart = Time.valueOf(args[3]);
							Time tArrive = Time.valueOf(args[4]);
							Time mArrive = Time.valueOf(details.getProperty("tarrive"));
							Time mDepart = Time.valueOf(details.getProperty("tdepart"));

							boolean feasible = true;

							String window;
							earliest = Time.valueOf("00:00:00");
							latest = Time.valueOf("00:00:00");

							// TODO(brendan): Optimise these checks. Time window check
							// seems much more computationally intensive.

							// Determine whether the time windows are feasible
							// and set the new time window to the largest window
							// which works for all members
							if (mArrive.before(tDepart) || mDepart.after(tArrive)) {
								feasible = false;
							} else {
								if (mArrive.before(tArrive)) {
									latest = mArrive;
								} else {
									latest = tArrive;
								}

								if (mDepart.before(tDepart)) {
									earliest = tDepart;
								} else {
									earliest = mDepart;
								}
							}

							// If origin and destination match, plus time windows are feasible
							if (details.get("origin").equals(args[1]) && details.get("destination").equals(args[2])
									&& feasible) {
								response = new ACLMessage(ACLMessage.PROPOSE);

								// Work out the new cost per member and set it as the message content
								TaxiCalculator tc = TaxiCalculator.getInstance();
								float cost = tc.getCost(details.get("origin") + "," + details.get("destination")).get(1)/(noMembers+1);

								content = cost + "," + earliest.toString() + "," + latest.toString();

								response.setContent(content);

							// Else we can't carpool with the sender
							} else {
								response = new ACLMessage(ACLMessage.REFUSE);
							}

							// Send the response
							response.addReceiver(request.getSender());
							send(response);
						// Sender is already a carpool member
						} else {
							ACLMessage invalid = new ACLMessage(ACLMessage.REFUSE);
							invalid.addReceiver(request.getSender());
							send(invalid);
						}
						break;
					// ACCEPT_PROPOSAL (from CHECK_ACCEPT)
					case ACLMessage.ACCEPT_PROPOSAL:

						// Let the sender know that we're in the correct state
						// and have added them to the carpool
						ACLMessage confirm = new ACLMessage(ACLMessage.CONFIRM);
						confirm.addReceiver(request.getSender());
						send(confirm);
						carpoolers.add(new AID(args[0], AID.ISLOCALNAME));
						incMemberCount();

						// Update the time window based on what we sent the sender in
						// our initial offer
						args = request.getContent().split(",");
						updateTimes(Time.valueOf(args[1]), Time.valueOf(args[2]));

						// If our carpool is still under capacity
						if (noMembers < (Integer)details.get("capacity")) {
							DBHandler dbh = DBHandler.getInstance();
							int senderId = Integer.valueOf(request.getSender().getLocalName().substring(5, request.getSender().getLocalName().length()));
							System.out.println(senderId);
							dbh.update("UPDATE ucintermediary SET cid="+id+"WHERE cid="+senderId+";");
							dbh.update("DELETE FROM carpools WHERE id="+senderId+";");

							// Transition back to AWAIT_REQUESTS
							exitValue = updateState(AWAIT_REQUESTS);
						// Otherwise we've reached capacity
						} else {
							// Deregister with the DF to minimise its load
							try {
								DFService.deregister(myAgent);
							} catch (FIPAException e) {
								e.printStackTrace();
							}

							// Transition to COMPLETE
							exitValue = updateState(COMPLETE);
						}
						break;
					// Message type we can't handle.
					// TODO(brendan): Respond with MISUNDERSTOOD
					default:
						break;
					}
				}
				// Otherwise we don't care about it - give it back
				else {
					block();
				}
			}

			// Returns the ID of the state to transition into
			public int onEnd() {
				return exitValue;
			}
		};

		// AWAIT_OFFERS state behaviour
		OneShotBehaviour awaitOffers = new OneShotBehaviour() {
			int exitValue = AWAIT_REQUESTS;

			public void action() {

				int noReplies = 0;

				ArrayList<AID> AIDs = new ArrayList<AID>();
				proposalMap = new HashMap<AID, ACLMessage>();
				costMap = new HashMap<AID, Float>();

				long startTime = System.currentTimeMillis();

				// While there are less responses than requests
				while (noReplies < noAgents) {

					// Define a timeout in case a receiver has transitioned to COMPLETE
					if (System.currentTimeMillis() - startTime > 10000) {
						Logger.getGlobal().info("Timeout in state AWAIT_OFFERS | Agent: " + myAgent.getLocalName());
						break;
					}

					ACLMessage reply = receive();

					// If the reply exists
					if (reply != null) {
						// And we haven't somehow replied to ourself
						if (reply.getSender() != myAgent.getAID()) {
							// Determine the message type
							switch (reply.getPerformative()) {
							// PROPOSAL - we can carpool
							case ACLMessage.PROPOSE:
								// Update proposal and cost maps for the user's attention
								proposalMap.put(reply.getSender(), reply);
								costMap.put(reply.getSender(), Float.valueOf(reply.getContent().split(",")[0]));
								break;
							// REFUSAL - we can't carpool
							case ACLMessage.REFUSE:
								break;
							// DEFAULT - a message type we weren't expecting
							// TODO(brendan): Respond with MISUNDERSTOOD
							default:
								break;
							}
						}
						noReplies++;
					// Otherwise we don't care about it - give it back
					} else {
						block();
					}
				}

				// If we received any proposals
				if (proposalMap.size() > 0) {
					// Write to DB for attention of end user
					setProposals(proposalMap, costMap);
					// Transition to CHECK_ACCEPT (waits for user response)
					exitValue = CHECK_ACCEPT;
				}
			}

			// Returns the ID of the state to transition into
			public int onEnd() {
				return exitValue;
			}
		};

		// CHECK_ACCEPT state behaviour
		OneShotBehaviour checkAccept = new OneShotBehaviour() {
			int exitValue = SEEK_CARPOOL;

			public void action() {

				try {
					Connection conn = DriverManager.getConnection(url,props);
					PGConnection pgconn = (org.postgresql.PGConnection)conn;
					Statement stmt = conn.createStatement();

					// Give the user some time to respond to proposals
					long startTime = System.currentTimeMillis();
					while(true) {
						if (System.currentTimeMillis() - startTime > 10000) {
							break;
						}
					}

					// Get relevant entries in the ucproposals table
					ResultSet proposals = stmt.executeQuery("SELECT * FROM proposals WHERE uid="+details.getProperty("organiser")+";");
					// Loop through each entry
					while (proposals.next()) {
						try {
							// Check if the user has flagged it as accepted
							if (proposals.getString(4).equals("1")) {
								// Determine which carpool has been accepted
								AID a = new AID("route"+proposals.getString(3), AID.ISLOCALNAME);
								ACLMessage confirm = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
								confirm.addReceiver(a);
								ACLMessage proposalMessage = proposalMap.get(a);

								// Reflect the proposal's adjusted time window
								String[] args = proposalMessage.getContent().split(",");
								earliest = Time.valueOf(args[1]);
								latest = Time.valueOf(args[2]);

								// Send confirmation to proposer
								content = carpoolers.get(0).getLocalName() + "," + earliest.toString() + "," + latest.toString();
								confirm.setContent(content);
								send(confirm);

								// Transition to AWAIT_CONFIRM
								exitValue = AWAIT_CONFIRM;

								break;
							// Else this proposal hasn't been flagged as accepted
							} else {
								// TODO(brendan): Remove from DB here to prevent
								// extremely unlikely desync in which the user accepts
								// a proposal we've already determined they didn't accept
							}
						} catch (NullPointerException npe) {
							// Client has written a null value rather than a zero to the DB
							// which we can still work with but throws a NPE
						}
					} proposals.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			// Returns the ID of the state to transition into
			public int onEnd() {
				// Remove the proposals from ucproposals table
				deleteProposals();
				return exitValue;
			}
		};

		// AWAIT_CONFIRM state behaviour
		OneShotBehaviour awaitConfirm = new OneShotBehaviour() {
			int exitValue = SEEK_CARPOOL;

			public void action() {
				long startTime = System.currentTimeMillis();
				while(true) {
					// Receive the next message in the queue
					ACLMessage reply = receive();
					// If the message exists
					if (reply != null) {
						// And it's a confirmation
						if (reply.getPerformative() == ACLMessage.CONFIRM) {
							// We've merged into the sender's carpool
							// so it's safe to terminate
							exitValue = updateState(COMPLETE);
							myAgent.doDelete();
						}
					}
					// Else we don't care about it - put it back
					else {
						block();
					}
					// Timeout
					if (System.currentTimeMillis() - startTime > 10000) {
						break;
					}
				}
			}

			// Returns the ID of the state to transition into
			public int onEnd() {
				return exitValue;
			}
		};

		// COMPLETE state behaviour
		TickerBehaviour complete = new TickerBehaviour(this, 1000) {
			protected void onTick() {
				// Receive next message in queue
				ACLMessage discard = receive();

				// If the message exists
				if (discard != null) {
					// We don't expect anything in state COMPLETE
					// so this was probably sent before we transitioned
					// and we should inform the sender as such
					ACLMessage respond = new ACLMessage(ACLMessage.NOT_UNDERSTOOD);
					respond.addReceiver(discard.getSender());
					send(respond);
				}
				// Otherwise we don't care about it - put it back
				else {
					block();
				}
			}
		};

		// LOAD state behaviour
		OneShotBehaviour load = new OneShotBehaviour() {
			int exitValue = SEEK_CARPOOL;

			public void action() {
				// Transition into whichever state was stored in the DB
				exitValue = state;
			}

			// Returns the ID of the state to transition into
			public int onEnd() {
				return exitValue;
			}
		};

		// Finite State Machine behaviour
		FSMBehaviour fsm = new FSMBehaviour();

		// Register states (first, intermediaries, last)
		fsm.registerFirstState(load, "Load");
		fsm.registerState(seekCarpool, "SeekCarpool");
		fsm.registerState(awaitOffers, "AwaitOffers");
		fsm.registerState(awaitConfirm, "AwaitConfirm");
		fsm.registerState(awaitRequests, "AwaitRequests");
		fsm.registerState(checkAccept, "CheckAccept");
		fsm.registerLastState(complete, "Complete");

		// Register transitions between states, as well as their values
		fsm.registerTransition("Load", "SeekCarpool", SEEK_CARPOOL);
		fsm.registerTransition("Load", "AwaitOffers", AWAIT_OFFERS);
		fsm.registerTransition("Load", "AwaitConfirm", AWAIT_CONFIRM);
		fsm.registerTransition("Load", "AwaitRequests", AWAIT_REQUESTS);
		fsm.registerTransition("Load", "Complete", COMPLETE);

		fsm.registerTransition("SeekCarpool", "SeekCarpool", SEEK_CARPOOL);
		fsm.registerTransition("SeekCarpool", "AwaitOffers", AWAIT_OFFERS);

		fsm.registerTransition("AwaitOffers", "AwaitRequests", AWAIT_REQUESTS);
		fsm.registerTransition("AwaitOffers", "CheckAccept", CHECK_ACCEPT);

		fsm.registerTransition("CheckAccept", "SeekCarpool", SEEK_CARPOOL);
		fsm.registerTransition("CheckAccept", "AwaitConfirm", AWAIT_CONFIRM);

		fsm.registerTransition("AwaitConfirm", "SeekCarpool", SEEK_CARPOOL);
		fsm.registerTransition("AwaitConfirm", "Complete", COMPLETE);

		fsm.registerTransition("AwaitRequests", "AwaitRequests", AWAIT_REQUESTS);
		fsm.registerTransition("AwaitRequests", "Complete", COMPLETE);

		fsm.registerTransition("AwaitAccept", "AwaitAccept", AWAIT_ACCEPT);
		fsm.registerTransition("AwaitAccept", "AwaitRequests", AWAIT_REQUESTS);
		fsm.registerTransition("AwaitAccept", "Complete", COMPLETE);

		// Add the behaviour
		addBehaviour(fsm);
	}

	// Update our global variable state and, conditionally, the database
	private int updateState(int state) {
		DBHandler dbh = DBHandler.getInstance();
		// If we're not in an unstable state
		if (state != 1 && state != 2 && state != 4)
			// Update our state in the database
			dbh.update("UPDATE carpools SET state="+state+" WHERE id="+id+";");
		this.state = state;
		return state;
	}

	// Update time windows in the global properties and database
	private void updateTimes(Time earliest, Time latest) {
		DBHandler dbh = DBHandler.getInstance();
		dbh.update("UPDATE carpools SET tdepart='"+earliest.toString()+"' WHERE id="+id+";");
		dbh.update("UPDATE carpools SET tarrive='"+latest.toString()+"' WHERE id="+id+";");
		details.setProperty("tdepart", earliest.toString());
		details.setProperty("tarrive", latest.toString());
	}

	// Increment the member count in the global variable and database
	private void incMemberCount() {
		DBHandler dbh = DBHandler.getInstance();
		noMembers++;
		dbh.update("UPDATE carpools SET dbgMemCount='"+noMembers+"' WHERE id="+id+";");
	}

	// Delete all of our proposals from the database so the user won't see them
	private void deleteProposals() {
		DBHandler dbh = DBHandler.getInstance();
		dbh.update("DELETE FROM proposals WHERE uid=" + details.get("organiser") + ";");
	}

	// Set proposals in the database based on a proposal and cost map
	private void setProposals(HashMap<AID, ACLMessage> proposals, HashMap<AID, Float> costs) {
		DBHandler dbh = DBHandler.getInstance();
		HashMap<Integer, Float> insert = new HashMap<Integer, Float>();
		HashMap<Integer, Integer> separation = new HashMap<Integer, Integer>();

		// Get the reputation associated with the proposal
		HashMap<AID, Integer> reputations = getReputation(proposalMap.keySet());

		// For each proposal
		for (AID a : proposalMap.keySet()) {
			// Create an entry in the insertion and separation maps
			insert.put(Integer.valueOf(a.getLocalName().substring(5, a.getLocalName().length())), costMap.get(a));
			separation.put(Integer.valueOf(a.getLocalName().substring(5, a.getLocalName().length())), reputations.get(a));
		}

		// For each proposal to be inserted
		for (int i : insert.keySet()) {
			// Insert it into the proposals table
			dbh.update("INSERT INTO proposals(cid, uid, cost, separation) VALUES("+i+", " + details.get("organiser") + ", " + insert.get(i) + ", "
					+ separation.get(i) + ");");
		}
	}

	// Get the reputation between our user and each entry in an AID set
	private HashMap<AID, Integer> getReputation(Set<AID> aids) {

		// Initialise the reputation map
		HashMap<AID, Integer> reputations = new HashMap<AID, Integer>();
		for (AID a : aids) {
			reputations.put(a, -1);
		}

		// DB connection properties
    String url = System.getenv("DB_CONN");

    Properties props = new Properties();

    props.setProperty("user", System.getenv("DB_USER"));
    props.setProperty("password", System.getenv("DB_PASS"));

		try {
			Connection conn = DriverManager.getConnection(url,props);
			PGConnection pgconn = (org.postgresql.PGConnection)conn;
			Statement stmt = conn.createStatement();

			HashMap<Integer, ArrayList<Integer>> relations = new HashMap<Integer, ArrayList<Integer>>();
			ArrayList<Integer> firstCarpools = new ArrayList<Integer>();

			// Get all carpools which involve us
			ResultSet intermediary = stmt.executeQuery("SELECT * FROM ucintermediary WHERE uid=" + details.get("organiser") + ";");
			while (intermediary.next()) {
				System.out.println("Added carpool to first: " + Integer.valueOf(intermediary.getString(3)));
				firstCarpools.add(Integer.valueOf(intermediary.getString(3)));
			} intermediary.close();

			// Add all users in our past carpools as first degree connections
			ArrayList<Integer> firstUsers = new ArrayList<Integer>();
			for (Integer cid : firstCarpools) {
				if (cid != id) {
					ResultSet intermediaryFirst = stmt.executeQuery("SELECT * FROM ucintermediary WHERE cid=" + cid + ";");
					while (intermediaryFirst.next()) {
						if (!intermediaryFirst.getString(2).equals(details.get("organiser"))
								&& !firstUsers.contains(Integer.valueOf(intermediaryFirst.getString(2)))) {
							firstUsers.add(Integer.valueOf(intermediaryFirst.getString(2)));
							System.out.println("Added user to first: " + Integer.valueOf(intermediaryFirst.getString(2)));
						}
					} intermediaryFirst.close();
				}
			}

			// Get all carpools involving our first degree connections
			ArrayList<Integer> secondCarpools = new ArrayList<Integer>();
			for (Integer i : firstUsers) {
				ResultSet carpoolsSecond = stmt.executeQuery("SELECT * FROM ucintermediary WHERE uid=" + i + ";");
				while (carpoolsSecond.next()) {
					if (!secondCarpools.contains(Integer.valueOf(carpoolsSecond.getString(3)))) {
						secondCarpools.add(Integer.valueOf(carpoolsSecond.getString(3)));
					}
				} carpoolsSecond.close();
			}

			// Add all users in our first degree connections' carpools as
			// second degree connections, assuming they're not already first
			// degree connections
			ArrayList<Integer> secondUsers = new ArrayList<Integer>();
			for (Integer cid : secondCarpools) {
				ResultSet usersSecond = stmt.executeQuery("SELECT * FROM ucintermediary WHERE cid=" + cid + ";");
				while (usersSecond.next()) {
					System.out.println("A: " + usersSecond.getString(2) + " | B: " + usersSecond.getString(3));
					if (!usersSecond.getString(2).equals(details.getProperty("organiser"))
							&& !secondUsers.contains(Integer.valueOf(usersSecond.getString(2)))
							&& !firstUsers.contains(Integer.valueOf(usersSecond.getString(2)))) {
						secondUsers.add(Integer.valueOf(usersSecond.getString(2)));
					}
				} usersSecond.close();
			}

			// TODO(brendan): Look at this - it needs optimisation and slight logical fixes.

			// For all agents in our proposals map
			for (AID a : reputations.keySet()) {
				int reputation = -1;

				// Check the current members of the proposer carpool
				ResultSet usersInProposal = stmt.executeQuery("SELECT * FROM ucintermediary WHERE cid=" + a.getLocalName().substring(5, a.getLocalName().length()));
				while (usersInProposal.next()) {

					// If the carpool member is a first degree connection
					if (firstUsers.contains(Integer.valueOf(usersInProposal.getString(2)))) {
						reputation = 1;
					// Else if the carpool member is a second degree connection
					} else if (secondUsers.contains(Integer.valueOf(usersInProposal.getString(2)))) {
						reputation = 2;
					// Else they are not a connection
					} else {
						System.out.println("CID: " + usersInProposal.getString(3) + " | UID: " + usersInProposal.getString(2));
					}
				}
				reputations.put(a, reputation);
			}
		} catch(Exception e) {
			e.printStackTrace();
		}

		return reputations;
	}

	// Runs when RouteAgent terminates
	protected void takeDown() {
		try {
			// Deregister with DF (may have already happened but is not guaranteed)
			DFService.deregister(myAgent);
		} catch (FIPAException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
