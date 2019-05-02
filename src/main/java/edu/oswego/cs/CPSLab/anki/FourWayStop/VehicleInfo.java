package edu.oswego.cs.CPSLab.anki.FourWayStop;

import java.time.Instant;
import java.util.Queue;
import java.io.Serializable;

/**
 * A class to exchange information about cyber-physical vehicles.
 * @author Benjamin Groman <bgroman@oswego.edu>
 */
public class VehicleInfo implements Serializable{
	private static final long serialVersionUID = 436L;
	public int locationID;
	public float offsetFromRoadCenter;
	public String model;
	public int speed;
	public Instant timestamp;
	public String MACid;
	public int roadPieceID;
	public boolean isClear;
	public boolean isMaster;
	public Queue<VehicleInfo> otherVehicles;//should be null if not master




	public static class IntersectionMessage implements Comparable{

		public String model;
		public Instant timestamp;
		public IntersectionMessage(String model, String timeStamp) {

			timestamp = Instant.parse(timeStamp);
			this.model = model;
		}

		@Override
		public int compareTo(Object o) {
			IntersectionMessage otherMsg = (IntersectionMessage)o;

			return this.timestamp.compareTo(otherMsg.timestamp);

		}

		public String toString(){

			return this.model +  "=-=-=-=-=" + this.timestamp.toString() + "EndOfCar";

		}
	}
}