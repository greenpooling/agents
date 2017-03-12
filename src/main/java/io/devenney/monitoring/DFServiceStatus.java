package monitoring;

public class DFServiceStatus {

	// Singleton which holds the current DFService health status
	
	private static DFServiceStatus instance;
	private int status = OKAY;
	
	// STATUSES
	private static final int OKAY = 0;
	private static final int SLOW = 1;
	private static final int UNSTABLE = 2;
	
	protected DFServiceStatus() {
		
	}
	
	// Singleton
	public static DFServiceStatus getInstance() {
		if (instance == null) {
			instance = new DFServiceStatus();
		}
		
		return instance;
	}
	
	// Setter
	public void setStatus(int status) {
		this.status = status;
	}
	
	// Getter
	public int getStatus() {
		return this.status;
	}
	
}
