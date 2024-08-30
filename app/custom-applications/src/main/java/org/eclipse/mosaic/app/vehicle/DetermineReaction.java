package org.eclipse.mosaic.app.vehicle;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.eclipse.mosaic.fed.application.ambassador.util.UnitLogger;
import org.eclipse.mosaic.fed.application.app.api.Application;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;

public class DetermineReaction {
    private boolean emergencyBrakeTrigger = false;
    private VehicleOperatingSystem os;
    private UnitLogger log;
    private double emergencyBrakeMinTTC = 3.0;

    public DetermineReaction(VehicleOperatingSystem os, UnitLogger log, double emergencyBrakeMinTTC) {
        this.os = os;
        this.log = log;
        this.emergencyBrakeMinTTC = emergencyBrakeMinTTC;
    }

    private double calculateTTC(VehicleData egoVehicle, CartesianPoint targetPoint) {
        double ttc = 0.0;
        double distance = egoVehicle.getPosition().toCartesian().distanceTo(targetPoint);
        double egoSpeed = egoVehicle.getSpeed();
        if (egoSpeed > 0) {
            ttc = distance / egoSpeed;
        }
        return ttc;
    }

    public void checkEmergencyBrake(CartesianPoint otherPosition) {
        double ttc = calculateTTC(os.getVehicleData(), otherPosition);
        if (ttc <= emergencyBrakeMinTTC) {
            // should apply emergency brake
            List<? extends Application> applications = os.getApplications();
            for (Application application : applications) {
                String appName = application.getClass().getSimpleName();
                if (appName.equals("EmergencyManeuver")) {
                    EmergencyBrakeTrigger trigger = new EmergencyBrakeTrigger(ttc, otherPosition);
                    os.getEventManager()
                            .newEvent(os.getSimulationTime() + 1, application)
                            .withResource(trigger)
                            .schedule();
                }
            }
        }
    }

}
