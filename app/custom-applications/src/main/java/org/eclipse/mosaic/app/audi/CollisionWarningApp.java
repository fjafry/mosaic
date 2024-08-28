package org.eclipse.mosaic.app.audi;

import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Vector;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.json.stream.JsonParser;
import javax.swing.plaf.ActionMapUIResource;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.enums.DriveDirection;
import org.eclipse.mosaic.lib.enums.VehicleClass;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.GeoRectangle;
import org.eclipse.mosaic.lib.geo.MutableGeoPoint;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.objects.v2x.etsi.Cam;
import org.eclipse.mosaic.lib.objects.v2x.etsi.cam.AwarenessData;
import org.eclipse.mosaic.lib.objects.v2x.etsi.cam.VehicleAwarenessData;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.SerializationUtils;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;

/**
 * This is a simple application that shows sending a CAM (Cooperative Awareness Message) with an additional information (user tagged value)
 * by using the {@link org.eclipse.mosaic.fed.applicationNT.ambassador.simulationUnit.operatingSystem.OperatingSystem#setUserTaggedValue(byte[])) method.
 * In this way an additional byte field can be sent via CAM, nevertheless this is often connected with some serious work.
 * You may also want to safely serialize / deserialize objects.
 *
 * The CAMs will be sent by an ad hoc module so that only vehicles with an enabled ad hoc module can receive it.
 *
 * @author lukas.schotte@audi.de
 **/
public class CollisionWarningApp extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication, CommunicationApplication {
	
	final static int STORED_SIMULATION_STEPS = 3;			// limits how many simulation steps of the past are stored. depends on relevance and performance
	final static int CAM_FREQUENCY = 10;					// frequency of the CAM - changes the frequency of the processEvent() method 
	final static double TIME_TO_FORECAST_IN_SECONDS = 0.3;
	final static float SPEED_TO_SLOW_DOWN_IF_COLLOISION_IS_FORECASTED = 25 / 3.6f;	// speed which guarantees that the forecasted collision is prevented 
	final static int REACTION_TIME_OF_DRIVER_IN_MILLISECONDS = 500;
	final static int SUMO_UPDATING_TIME_IN_MS = 10; // to be defined in "sumo" folder - sumo_config.json
	//public static final SerializationUtils<TaggedValueMessage> DEFAULT_OBJECT_SERIALIZATION = new SerializationUtils<TaggedValueMessage>();
	
	private HashMap<String, StoredVehicleDataOfConnectedCar> LDM;  		//create LocalDynamicMap with all connected Cars to this Car
	private List<Thread> timerForReactionTimeOfDriver;
	private Thread timer;
	private boolean isTimerAlreadyActive;
	
	private boolean isSpeedAlreadyResetted;
	private CollisionWarningApp app = this;
	
	// Database JSON
	JsonObjectBuilder JSONbuilder;
	JsonArrayBuilder JSONx, JSONy, JSONz;
	int JSONcounter;
	private AllRoutesDatabase allRoutesDatabase;
	boolean isRouteDatabaseread;
	
	//Database
	RouteByVehicleIdDatabase routeByVehicleIdDatabase;
	
	public class RouteVehicleData {

	    private String vehicleId;
	    private String routeId;
	    
	    RouteVehicleData(String vehicleId, String routeId) {
	    	this.vehicleId = vehicleId;
	    	this.routeId = routeId;
	    }

		public String getVehicleId() {
			return vehicleId;
		}

		public String getRouteId() {
			return routeId;
		}
	}
	
	private class RouteByVehicleIdDatabase {						
		private List<RouteVehicleData> database;		
		
		RouteByVehicleIdDatabase() {
			database = new ArrayList<RouteVehicleData>();
		}
		
		private String getRouteIdByVehicleId(String vehicleId) {
			for (RouteVehicleData data : database) {
		        if (data.getVehicleId().equals(vehicleId)) {
		            return data.getRouteId();
		        }
		    }
		    return null;
		}
		
		public void add(String vehicleId, String routeId) {
			database.add(new RouteVehicleData(vehicleId, routeId));
		}
		
		public List<RouteVehicleData> getDatabase() {
			return database;
		}
	}
	
	private class AllRoutesDatabase {						
		private Queue<RouteDatabase> allRoutesData;		
		
		AllRoutesDatabase() {
			allRoutesData = new LinkedList<RouteDatabase>();
			// 20 = Anzahl der Routen + Puffer
			for (int i = 0; i < 20; i++) {
				allRoutesData.add(new RouteDatabase());
			}
		}
		
		RouteDatabase getRouteDataById(int routeId){
			int counter = 0;
			for(RouteDatabase db : allRoutesData) {
				if (counter == routeId) {
					return db;
				}
				counter++;
			}
			return null;
		}
	}
	
	private class RouteDatabase {						
		private Queue<Coordinates> routeData;		
		
		RouteDatabase() {
			routeData = new LinkedList<Coordinates>();
		}
		
		public void add(Coordinates c) {
			routeData.add(c);
		}
		
		public Queue<Coordinates> getRouteDatabase() {
			return routeData;
		}
		
		public Coordinates getPositionById(int id) {
			int i = 0;
			for (Coordinates c : routeData) {
				if (i == id) {
					return c;
				}
				i++;
			}
			return null;
		}
	}
	
	// Testing Variables -- BEGIN
	private Queue<Coordinates> forecastedCoords = new LinkedList<Coordinates>();
	// Testing Variables -- END
	
	public static class TaggedValueMessage implements Serializable {
        /**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		public int fooInt;
        public String fooString;

        @Override
        public String toString() {
            return "TaggedValueMessage: fooInt=" + fooInt + ", " + "fooString=" + fooString;
        }
    }
	
	//List of data of the last x simulation steps of all connected Cars
	//first element is the time wise oldest
	private class StoredVehicleDataOfConnectedCar {						
		private Queue<LDMVehicleData> storedVehicleDataOfConnectedCar;		
		
		StoredVehicleDataOfConnectedCar(LDMVehicleData vehicleData) {
			storedVehicleDataOfConnectedCar = new LinkedList<LDMVehicleData>();
			storedVehicleDataOfConnectedCar.add(vehicleData);
		}
		
		Queue<LDMVehicleData> getStoredVehicleDataOfConnectedCar(){
			return storedVehicleDataOfConnectedCar;
		}
	}
	
	// class to store all relevant data for the application and not the whole CAM 
	private class LDMVehicleData {
		private VehicleAwarenessData data;
		private Coordinates coordinates;
		
		public LDMVehicleData(VehicleAwarenessData data, double latitude, double longitude) {
			this.data = data;
			this.coordinates = new Coordinates(latitude, longitude);
		}
		
		double getLength() {
			return data.getLength();
		}
		
		double getHeading() {
			return data.getHeading();
		}
		
		double getSpeed() {
			return data.getSpeed();
		}
		
		double getAcceleration() {
			return data.getLongitudinalAcceleration();
		}
		
		double getLatitude() {
			return coordinates.getLatitude();
		}
		
		double getLongitude() {
			return coordinates.getLongitude();
		}

	}
	
	private class Coordinates {
		private double latitude, longitude;
		public Coordinates(double latitude, double longitude) {
			this.latitude = latitude;
			this.longitude = longitude;
		}
		double getLatitude() {
			return latitude;
		}
		
		double getLongitude() {
			return longitude;
		}
		public void setLatitude(double latitude) {
			this.latitude = latitude;
		}
		public void setLongitude(double longitude) {
			this.longitude = longitude;
		}
		
	}
	
	private class CartesianCoordinates {
		private double x, y, z;
		public CartesianCoordinates(double x, double y, double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
		public double getX() {
			return x;
		}
		public void setX(double x) {
			this.x = x;
		}
		public double getY() {
			return y;
		}
		public void setY(double y) {
			this.y = y;
		}
		public double getZ() {
			return z;
		}
		public void setZ(double z) {
			this.z = z;
		}
		
		
	}
	
	private void updateVehicleDataToLDM(String id, LDMVehicleData vehicleData) {		// creates new data table for a new Car OR update the data of an existing car
		if (!isVehicleAlreadyStoredInLDM(id)) {										
			LDM.put(id, new StoredVehicleDataOfConnectedCar(vehicleData));
		} else {
			if (LDM.get(id).getStoredVehicleDataOfConnectedCar().size() < STORED_SIMULATION_STEPS) {	// simply adds car if LDM hasn't reached capacity (STORED_SIMULATION_STEPS)
				LDM.get(id).getStoredVehicleDataOfConnectedCar().offer(vehicleData);			
			} else {
				LDM.get(id).getStoredVehicleDataOfConnectedCar().poll();								// deletes oldest data in LDM
				LDM.get(id).getStoredVehicleDataOfConnectedCar().offer(vehicleData);							// adds car to LDM 
			}
		}
	}
	
	private boolean isVehicleAlreadyStoredInLDM(String id) {
		return LDM.containsKey(id);
	}
	
	// creates Rectangle around the position point of vehicle, so it's possible to detect overlap with other safe zone rectangles of other vehicles
	// to forecast a collision 
	private GeoRectangle createSafeZoneAroundVehicle(double lengthOfVehicleInMeters, Coordinates position) {
		
			
			// multpy by 0.7 because of edges of cars in diagonal are longer than 0.5 length -> so something larger
			// velocity / 50 -> the faster the vehicle the larger the "Streuung" thereby the safety zone tolerance increases
			//double safetyDistance = lengthOfVehicleInMeters * (0.7 + velocity / 50);
			double safetyDistanceLat = transformMetersInLat(2 * lengthOfVehicleInMeters * 0.7);
			double safetyDistanceLong = transformMetersInLong(2 * lengthOfVehicleInMeters * 0.7, position.getLatitude());			
			
			//(int x, int y, int width, int height)
			//Constructs a new Rectangle whose upper-left corner is specified as (x,y)
			// multiply by 1.000.000 because Rectangle only works with Integer and the values after the comma don't get deleted in the casting process
			//getLog().infoSimTime(this, "lengthInGeoCoordinatesLength: {}, transformMetersInGeographicalCoordinatesLength(lengthOfVehicleInMeters): {}", 
			//		lengthInGeoCoordinatesLength, transformMetersInGeographicalCoordinatesLength(lengthOfVehicleInMeters));
			GeoRectangle safeZone = new GeoRectangle(new MutableGeoPoint(position.getLatitude(), position.getLongitude()), 
					new MutableGeoPoint(position.getLatitude() + safetyDistanceLat, position.getLongitude() + safetyDistanceLong));
			
			return safeZone;
		
	}
	
	private VehicleAwarenessData getVehicleAwarenessDataOfThisVehicle() {
		VehicleData data = this.getOperatingSystem().getVehicleData();
		return new VehicleAwarenessData(VehicleClass.Car, data.getSpeed(), data.getHeading(), this.getOperatingSystem().getInitialVehicleType().getLength(), 
				0, DriveDirection.FORWARD, 0, data.getLongitudinalAcceleration());
	}
	
		private double calculateDistanceBetweenTwoCoordinatesInMeters(Coordinates c1, Coordinates c2) {
		double lat1 = c1.getLatitude() * Math.PI / 180;
		double lon1 = c1.getLongitude() * Math.PI / 180;
		double lat2 = c2.getLatitude() * Math.PI / 180;
		double lon2 = c2.getLongitude() * Math.PI / 180;
		
		// Quelle der Formel: https://www.kompf.de/gps/distcalc.html
		double dist = 1000 * 6378.388 * Math.acos(Math.sin(lat1) * Math.sin(lat2) + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon2 - lon1));
		
		return dist;
	}
	
	private double calculateDistanceForAcceleratedMovement(double currentSpeed, double timeInSecounds, double acceleration) {
		return 0.5 * acceleration * Math.pow(timeInSecounds, 2) + currentSpeed * timeInSecounds;
	}
	
	private double transformMetersInLat(double distanceInMeters) {
		double distanceLat;
		
		distanceLat = distanceInMeters / 111120;   // one � = 111120m
		
		return distanceLat;
	}
	
	private double transformLatInMeters(double distanceLat) {
		double distanceInMeters;
		
		distanceInMeters = distanceLat * 111120;   // one � = 111120m
		
		return distanceInMeters;
	}
	
	private double transformMetersInLong(double distanceInMeters, double latitude) {
		double distanceLong;
		
		distanceLong = distanceInMeters / 111120 / Math.cos(latitude * Math.PI / 180);   // one � = 111120m * cos(lat)
		
		return distanceLong;
	}
	
	private double transformLongInMeters(double distanceLong, double latitude) {
		double distanceInMeters;
		
		distanceInMeters = distanceLong * 111120 * (Math.cos(latitude * Math.PI / 180));   // one � = 111120m * cos(lat)
		
		return distanceInMeters;
	}
	
	private void resetSpeedToNormal() {
		// slow down vehicle in 1000ms to given speed
		this.getOperatingSystem().resetSpeed();
	}
	
	private class TimerForReactionTimeOfDriver implements Runnable {

		@Override
		public void run() {
			try {
				if (!isTimerAlreadyActive) {
				getLog().infoSimTime(app, "Timer 1");
				Thread.sleep(REACTION_TIME_OF_DRIVER_IN_MILLISECONDS);
				getLog().infoSimTime(app, "Timer 2");
				initializeBrake();
				getLog().infoSimTime(app, "Timer 3");
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
		}
		
	}
	
	private void initializeBrake() {
		// slow down vehicle in 1000ms to given speed
		getLog().infoSimTime(this, "initializeBrake");
		this.getOperatingSystem().changeSpeedWithInterval(SPEED_TO_SLOW_DOWN_IF_COLLOISION_IS_FORECASTED, 1000);
	}

	@Override
    public void onStartup() {
		LDM =  new HashMap<String, StoredVehicleDataOfConnectedCar>();
		timerForReactionTimeOfDriver = new LinkedList<Thread>();
		timer = new Thread(new TimerForReactionTimeOfDriver());
		isSpeedAlreadyResetted = true;
		isTimerAlreadyActive = false;
		
		JSONcounter = 0;
		JSONx = Json.createArrayBuilder();
        JSONy = Json.createArrayBuilder();
        JSONz = Json.createArrayBuilder();
		
		/* GeoCircle transmissionArea = new GeoCircle(GeoPoint.latlon(52.5, 13.2), 3000);

		MessageRouting routing = getOs().getCellModule().createMessageRouting().geoBroadCast(transmissionArea);

		getOs().getCellModule().sendV2XMessage(new MyV2XMessage(routing)); */

        getOperatingSystem().getAdHocModule().enable(new AdHocModuleConfiguration()
                .addRadio().channel(AdHocChannel.CCH).distance(500).create()
        );
        getLog().infoSimTime(this, "Set up");
        //sendCam(); //Don't do this here! Sending CAMs only makes sense when we have access to vehicle info of sender, which is not ready at the set up stage.

        final Event event = new Event(getOperatingSystem().getSimulationTime() + 1 * TIME.SECOND / CAM_FREQUENCY, this);
        getOperatingSystem().getEventManager().addEvent(event);
        
        // read Data from JSON Database externally stored
        isRouteDatabaseread = false;
        
        allRoutesDatabase = new AllRoutesDatabase();
        
        routeByVehicleIdDatabase = new RouteByVehicleIdDatabase();
    }

    /** Sending CAM and scheduling next events every second **/
    @Override
    public void processEvent(Event event) throws Exception {
        sendCam();
        /*
        updateVehicleDataToLDM(this.getOperatingSystem().getId(), new LDMVehicleData(getVehicleAwarenessDataOfThisVehicle(), 
        		this.getOperatingSystem().getPosition().getLatitude(), this.getOperatingSystem().getPosition().getLongitude()));
        
        testPositionForecasting();
         */
        final Event nextEvent = new Event(getOperatingSystem().getSimulationTime() + 1 * TIME.SECOND / CAM_FREQUENCY, this);
        getOperatingSystem().getEventManager().addEvent(nextEvent);
    }

    public void sendCam() {
        //getLog().infoSimTime(this, "Sending Cam");
        //getLog().infoSimTime(this, "LDM: {}", LDM);
        getOs().getAdHocModule().sendCam();
    }

	@Override
	public void onShutdown() {
		//writeRoutePositionDatabase();
		getLog().info("Bye");
		
	}

	@Override
	public void onAcknowledgementReceived(ReceivedAcknowledgement arg0) {
		// to implement		
		
	}

	@Override
	public void onCamBuilding(CamBuilder arg0) {
		byte[] b;
		b= getOs().getVehicleData().getRouteId().getBytes();
		arg0.userTaggedValue(b);
		// to implement		
	}
	
	/*
    public byte[] beforeGetAndResetUserTaggedValue() {
        // this method will be triggered from the operating system (may a CAM or DENM will be prepared to send)
        // create a new object
        TaggedValueMessage exampleContent = new TaggedValueMessage();
        exampleContent.fooInt = 5;
        exampleContent.fooString = "Hello from " + (getOs().getVehicleData() != null ? getOs().getVehicleData().getName() : "unknown vehicle");

        byte[] byteArray;
        try {
            byteArray = DEFAULT_OBJECT_SERIALIZATION.toBytes(exampleContent);
        } catch (IOException ex) {
            getLog().error("Error during a serialization.", ex);
            return null;
        }
        // set the user tagged value in the operating system
        //getOs().assembleCamMessage(camBuilder.userTaggedValue(byteArray));
        
        // read the previous set user tagged value
        //String s = Arrays.toString(((Cam) getOperatingSystem()).getUserTaggedValue());
        
        // log it out
        //getLog().infoSimTime(this, "user-tagged value was read out of the memory: {}", s);
        
        return byteArray;
    }
    */
	
	@Override
	public void onMessageReceived(ReceivedV2xMessage receivedV2XMessage) {
		
		
		//getLog().infoSimTime(this, "V2X Message received");
		V2xMessage msg = receivedV2XMessage.getMessage();
		
		
		  if (msg instanceof Cam) { 
			  try { 
				  // Add RouteId to every Vehicle
				  byte[] b = ((Cam) msg).getUserTaggedValue();
				  String vehicleId = ((Cam) msg).getUnitID();
				  String routeId = new String(b, StandardCharsets.UTF_8);
				  
				  if (routeByVehicleIdDatabase.getRouteIdByVehicleId(vehicleId) == null) {
					  routeByVehicleIdDatabase.add(vehicleId, routeId);
				  }
			  }
			  catch (Exception e) { 
				  getLog().error("An error occurred", e); 
			  }
		  } else { 
			  getLog().infoSimTime(this, "Arrived message was not a CAM, but a {} msg from", msg.getSimpleClassName());
		  }
		 
		
		
		updateVehicleDataToLDM(((Cam) msg).getUnitID(), new LDMVehicleData((VehicleAwarenessData)((Cam) msg).getAwarenessData(), 
				((Cam) msg).getPosition().getLatitude(), ((Cam) msg).getPosition().getLongitude()));
		
	}

	@Override
	public void onMessageTransmitted(V2xMessageTransmission arg0) {
		// to implement		
	}

	@Override
	public void onVehicleUpdated(VehicleData arg0, VehicleData arg1) {
		//addRoutePositionToDatabase();
		
		/*getLog().infoSimTime(this, "onVehicleUpdated");
		try {
			getLog().infoSimTime(this, "onVehicleUpdated TRY");
			getLog().infoSimTime(this, " getRouteId: {}", getOs().getVehicleData().getRouteId());
			//NullPointerException lol wtf
		} catch (Exception e) {
			getLog().infoSimTime(this, "onVehicleUpdated CATCH");
			
			e.printStackTrace();
		}
		*/
		
		
		updateVehicleDataToLDM(getOs().getId(), new LDMVehicleData(new VehicleAwarenessData(null, getOs().getVehicleData().getSpeed(), 
				getOs().getVehicleData().getHeading(), 4.0, 2.0, null, 0, getOs().getVehicleData().getLongitudinalAcceleration()), 
				getOs().getVehicleData().getPosition().getLatitude(), getOs().getVehicleData().getPosition().getLongitude()));
		
		if(!isRouteDatabaseread) {
			readAllRoutesFromDatabase();
			isRouteDatabaseread = true;
			routeByVehicleIdDatabase.add(getOs().getId(), getOs().getVehicleData().getRouteId());
			//timer.start();
		} else {
			//identifyRoutePosition(1, getOs().getId());
			
		}
		getLog().infoSimTime(this, "Speed: {}, Acceleration: {}", getOs().getVehicleData().getSpeed(), getOs().getVehicleData().getLongitudinalAcceleration());
		if (getOs().getVehicleData().getSpeed() < 7) {
			resetSpeedToNormal();
		}
		//addRoutePositionToDatabase();		// muss nur einmal gemacht werden, also beim 1. Durchlauf, um alle Routen Positionen in externe Dateien zu schreiben
		
		//checkIfCollisionWillOccur();
		
		for (String s : checkIfCollisionWillOccurWithOtherVehicle()) {
			getLog().infoSimTime(this, "{} collides with {}", getOs().getId(), s);
		}
		// sofern eine Kollision detektiert wird, bremst das Fahrzeug ab
		// sobald keine Kollision mehr detektiert wird, beschleunigt das Fahrzeug wieder auf seine normale Geschwindigkeit
		
		if (checkIfCollisionWillOccurWithOtherVehicle().size() > 0 && !isTimerAlreadyActive) {
			//Geschwindigkeit minimieren
			Thread timerThread = new Thread(new TimerForReactionTimeOfDriver());
			timerForReactionTimeOfDriver.add(timerThread);
			getLog().infoSimTime(this, "Thread Count: {}", timerForReactionTimeOfDriver.size());
			timerThread.start();
			isTimerAlreadyActive = true;
			isSpeedAlreadyResetted = false;
		} else if (!isSpeedAlreadyResetted) {
			resetSpeedToNormal();
			isSpeedAlreadyResetted = true;
		} 
		if (checkIfCollisionWillOccurWithOtherVehicle().size() == 0) {
			isTimerAlreadyActive = false;
		}
		
	}
	
	private List<String> checkIfCollisionWillOccurWithOtherVehicle() {
		
		List<String> collidedVehicles = new LinkedList<String>();
		
		if (routeByVehicleIdDatabase.getRouteIdByVehicleId(getOs().getId()) == null) {
			  routeByVehicleIdDatabase.add(getOs().getId(), getOs().getVehicleData().getRouteId());
		} else {
			
			Coordinates thisVehPosInForecastedTime = calculatePositionInGivenTime(TIME_TO_FORECAST_IN_SECONDS, routeByVehicleIdDatabase.getRouteIdByVehicleId(getOs().getId()), getOs().getId());
			
			try {
			
				GeoRectangle thisVehicle = createSafeZoneAroundVehicle(LDM.get(this.getOperatingSystem().getId()).
						getStoredVehicleDataOfConnectedCar().peek().getLength(), thisVehPosInForecastedTime);
				
				double thisVehicleDeltaLat = thisVehicle.getB().getLatitude() - thisVehicle.getA().getLatitude();
				double thisVehicleDeltaLong = thisVehicle.getB().getLongitude() - thisVehicle.getA().getLongitude();
				
				for (RouteVehicleData data : routeByVehicleIdDatabase.getDatabase()) {
					if(!data.getVehicleId().equals(getOs().getId())) {
						Coordinates otherVehPosInForecastedTime = calculatePositionInGivenTime(TIME_TO_FORECAST_IN_SECONDS, data.getRouteId(), data.getVehicleId());
						
						// createSafeZone
						GeoRectangle otherVehicle = createSafeZoneAroundVehicle(LDM.get(data.getVehicleId()).getStoredVehicleDataOfConnectedCar().peek().getLength(),
								otherVehPosInForecastedTime);
						double otherVehicleDeltaLat = otherVehicle.getB().getLatitude() - otherVehicle.getA().getLatitude();
						double otherVehicleDeltaLong = otherVehicle.getB().getLongitude() - otherVehicle.getA().getLongitude();
							
						int isCollision = 0;
							
						if (thisVehicle.contains(otherVehicle.getA())) {
							isCollision++;
						} else if (thisVehicle.contains(new MutableGeoPoint(otherVehicle.getA().getLatitude() + otherVehicleDeltaLat, otherVehicle.getA().getLongitude()))) {
							isCollision++;
						} else if (thisVehicle.contains(new MutableGeoPoint(otherVehicle.getA().getLatitude() + otherVehicleDeltaLat, otherVehicle.getA().getLongitude() + otherVehicleDeltaLong))) {
							isCollision++;
						} else if (thisVehicle.contains(new MutableGeoPoint(otherVehicle.getA().getLatitude(), otherVehicle.getA().getLongitude() + otherVehicleDeltaLong))) {
							isCollision++;
						} else if (otherVehicle.contains(thisVehicle.getA())) {
							isCollision++;
						} else if (otherVehicle.contains(new MutableGeoPoint(thisVehicle.getA().getLatitude() + thisVehicleDeltaLat, thisVehicle.getA().getLongitude()))) {
							isCollision++;
						} else if (otherVehicle.contains(new MutableGeoPoint(thisVehicle.getA().getLatitude() + thisVehicleDeltaLat, thisVehicle.getA().getLongitude() + thisVehicleDeltaLong))) {
							isCollision++;
						} else if (otherVehicle.contains(new MutableGeoPoint(thisVehicle.getA().getLatitude(), thisVehicle.getA().getLongitude() + thisVehicleDeltaLong))) {
							isCollision++;
						}
							
						if (isCollision > 0) {
							collidedVehicles.add(data.getVehicleId());
						} 
					}
				}
			} catch (Exception e) {
				
			}
		}
		
		return collidedVehicles;
		
	}
	
	private Coordinates calculatePositionInGivenTime(double timeToForecastInSeconds, String routeId, String vehicleId) {
		
		int routeIdInt = Integer.parseInt(routeId);

		RouteDatabase db = allRoutesDatabase.getRouteDataById(routeIdInt);
		
		// zwecks Notbremsung muss die Vorhersage der Route angepasst werden,
		// da das Fzg nicht mehr so schnell f�hrt und damit nicht die vorgegebenen Positionspunkt einnimmt
		int currentPositionId = identifyRoutePosition(routeId, vehicleId);
		Coordinates currentPosition = db.getPositionById(currentPositionId);
		Coordinates nextPosition = db.getPositionById(currentPositionId + 1);
		double factorBrakingInfluenceInForecast = 1;
		if (currentPositionId > -1) {
			double distanceDriven = calculateDistanceBetweenTwoCoordinatesInMeters(currentPosition, nextPosition);
			//getLog().infoSimTime(this, "distanceDriven {}", distanceDriven);
			double supposedSpeed = distanceDriven * 1000 / (SUMO_UPDATING_TIME_IN_MS);
			//getLog().infoSimTime(this, "supposedSpeed {}", supposedSpeed);
			double currentSpeed = LDM.get(vehicleId).getStoredVehicleDataOfConnectedCar().peek().getSpeed();
			//getLog().infoSimTime(this, "currentSpeed {}", currentSpeed);
			//getLog().infoSimTime(this, "v_neu/v_normal: {}", currentSpeed/supposedSpeed);
			
			factorBrakingInfluenceInForecast = currentSpeed/supposedSpeed;
		}
		int positionIdInToTimeToForecast = identifyRoutePosition(routeId, vehicleId) + (int) (factorBrakingInfluenceInForecast * timeToForecastInSeconds * 1000 / SUMO_UPDATING_TIME_IN_MS);
		/*getLog().infoSimTime(this, "{} - positionIdInToTimeToForecast {}, factorBrakingInfluenceInForecast {}", vehicleId, positionIdInToTimeToForecast,
				factorBrakingInfluenceInForecast);
				*/
		return db.getPositionById(positionIdInToTimeToForecast);
	}
	
	
	private int identifyRoutePosition(String routeId, String vehicleId) {
		int routeIdInt = Integer.parseInt(routeId);
		Coordinates temp = null;
		int counter = 0;
		double heading = LDM.get(vehicleId).getStoredVehicleDataOfConnectedCar().peek().getHeading();
		double lat = LDM.get(vehicleId).getStoredVehicleDataOfConnectedCar().peek().getLatitude();
		double lon = LDM.get(vehicleId).getStoredVehicleDataOfConnectedCar().peek().getLongitude();
		for (Coordinates c : allRoutesDatabase.getRouteDataById(routeIdInt).getRouteDatabase()) {
			if (counter > 0) {
				/* getLog().infoSimTime(this, "counter: {}, Heading: {}, temp.getLatitude(): {}, lat: {}, c.getLatitude(): {}, temp.getLongitude(): {}, lon: {}, c.getLongitude(): {}",
						counter, heading, temp.getLatitude(), lat, c.getLatitude(), temp.getLongitude(), lon, c.getLongitude());
				*/
				if (0 <= heading && heading < 90) {
					if (temp.getLatitude() <= lat && lat < c.getLatitude() && temp.getLongitude() <= lon
							&& lon < c.getLongitude()) {
//						getLog().infoSimTime(this, "RoutePosition: {}, distance temp-current: {}, distance next-current: {}, distance temp-next: {}", counter,
//								calculateDistanceBetweenTwoCoordinatesInMeters(temp, new Coordinates(lat,lon)), calculateDistanceBetweenTwoCoordinatesInMeters(new Coordinates(lat,lon), c),
//								calculateDistanceBetweenTwoCoordinatesInMeters(temp, c));
						return counter;
					}
				} else if (90 <= heading && heading < 180) {
					if (temp.getLatitude() >= lat && lat > c.getLatitude() && temp.getLongitude() <= lon
							&& lon < c.getLongitude()) {
//						getLog().infoSimTime(this, "RoutePosition: {}", counter);
//						getLog().infoSimTime(this, "RoutePosition: {}, distance temp-current: {}, distance next-current: {}, distance temp-next: {}", counter,
//								calculateDistanceBetweenTwoCoordinatesInMeters(temp, new Coordinates(lat,lon)), calculateDistanceBetweenTwoCoordinatesInMeters(new Coordinates(lat,lon), c),
//								calculateDistanceBetweenTwoCoordinatesInMeters(temp, c));
						return counter;
					}	
				} else if (180 <= heading && heading < 270) {
					if (temp.getLatitude() >= lat && lat > c.getLatitude() && temp.getLongitude() >= lon
							&& lon > c.getLongitude()) {
//						getLog().infoSimTime(this, "RoutePosition: {}", counter);
//						getLog().infoSimTime(this, "RoutePosition: {}, distance temp-current: {}, distance next-current: {}, distance temp-next: {}", counter,
//								calculateDistanceBetweenTwoCoordinatesInMeters(temp, new Coordinates(lat,lon)), calculateDistanceBetweenTwoCoordinatesInMeters(new Coordinates(lat,lon), c),
//								calculateDistanceBetweenTwoCoordinatesInMeters(temp, c));
						return counter;
					}	
				} else if (270 <= heading && heading < 360) {
					if (temp.getLatitude() <= lat && lat < c.getLatitude() && temp.getLongitude() >= lon
							&& lon > c.getLongitude()) {
//						getLog().infoSimTime(this, "RoutePosition: {}", counter);
//						getLog().infoSimTime(this, "RoutePosition: {}, distance temp-current: {}, distance next-current: {}, distance temp-next: {}", counter,
//								calculateDistanceBetweenTwoCoordinatesInMeters(temp, new Coordinates(lat,lon)), calculateDistanceBetweenTwoCoordinatesInMeters(new Coordinates(lat,lon), c),
//								calculateDistanceBetweenTwoCoordinatesInMeters(temp, c));
						return counter;
					}	
				}
			}
			temp = c;
			counter++;
		}
		return -1;
	}
	
	private void addRoutePositionToDatabase() {		
		try {
			JSONx.add(JSONcounter, getOs().getVehicleData().getPosition().getLatitude());
			JSONy.add(JSONcounter, getOs().getVehicleData().getPosition().getLongitude());
			//JSONz.add(JSONcounter, getOs().getVehicleData().getProjectedPosition().getZ());
		} catch(Exception e) {
			// catch
		}
        JSONcounter++;
	}

	public void writeRoutePositionDatabase() {
		JSONbuilder = Json.createObjectBuilder();
		JSONbuilder.add("routeId", getOs().getVehicleData().getRouteId());

        JSONbuilder.add("x", JSONx);
        JSONbuilder.add("y", JSONy);
        //JSONbuilder.add("z", JSONz);
        JsonObject jo = JSONbuilder.build();
		try {
			// Noch allgemeinen Path mit \\xxx\\xx.json implementen
            FileWriter fw = new FileWriter("C:\\Users\\User\\OneDrive\\Dokumente\\Duales Studium\\Bachelorarbeit\\Simulator\\eclipse-mosaic-20.0\\scenarios\\mapKreuzung\\routesDatabase\\Route" +
            		getOs().getVehicleData().getRouteId() + ".json");
            JsonWriter jsonWriter = Json.createWriter(fw);
            jsonWriter.writeObject(jo);
            jsonWriter.close();
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
	
	public void readAllRoutesFromDatabase() {
		for (int i = 0; i < 20; i++) {
			try {
				InputStream is = new FileInputStream("C:\\Users\\User\\OneDrive\\Dokumente\\Duales Studium\\Bachelorarbeit\\Simulator\\eclipse-mosaic-20.0\\scenarios\\mapKreuzung\\routesDatabase\\Route" +
	            		i + ".json");
				JsonReader rdr = Json.createReader(is);
				
				JsonObject obj = rdr.readObject();
				JsonArray latArray = obj.getJsonArray("y");
				JsonArray longArray = obj.getJsonArray("x");
				//JsonArray zArray = obj.getJsonArray("z");
				
				for (int x = 0; x < longArray.size(); x++) {
					Coordinates c = new Coordinates(Double.parseDouble(latArray.get(x).toString()), 
							Double.parseDouble(longArray.get(x).toString()));
					allRoutesDatabase.getRouteDataById(i).add(c);
				}
			} catch (Exception e) {
				
			}
		}
	}
	
	
	
	
	
	
	
	
	
	
	
	
}
