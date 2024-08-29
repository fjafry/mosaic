package org.eclipse.mosaic.app.vehicle;

import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.MutableCartesianPoint;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/*
 * This application calculates the time to collision (TTC) of a vehicle with a conflict point.
 *  The TTC calculation is based on the method described here: https://taxonomy.connectedautomateddriving.eu/time-to-collision/
 */
public class CalculateRawTTC extends ConfigurableApplication<RawTTCconfig, VehicleOperatingSystem>
        implements VehicleApplication {

    public CalculateRawTTC() {
        super(RawTTCconfig.class, "RawTTCconfig");
    }

    private double minTTC = Double.MAX_VALUE;
    private double ttc = 0.0;
    private boolean vehicleStopped = false;

    private RawTTCconfig config;

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Started {} on {}.", this.getClass().getSimpleName(), getOs().getId());
        config = this.getConfiguration();
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "minTTC: {}", minTTC);

    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
        CartesianPoint targetPosition = new MutableCartesianPoint(config.xPosConflict, config.yPosConflict, 0);
        CartesianPoint currentPosition = updatedVehicleData.getPosition().toCartesian();
        double vehicleSpeed = updatedVehicleData.getSpeed();
        if (vehicleSpeed == 0) {
            vehicleStopped = true;
        }
        if (vehicleStopped || currentPosition.getX() >= targetPosition.getX()) {
            return;
        }
        calculateTTC(vehicleSpeed, currentPosition, targetPosition);
    }

    @Override
    public void processEvent(Event event) throws Exception {

    }

    private void calculateTTC(double vehicleSpeed, CartesianPoint currentPosition, CartesianPoint targetPosition) {

        double distance = currentPosition.distanceTo(targetPosition);
        if (vehicleSpeed > 0) {
            ttc = distance / vehicleSpeed;
        }
        if (ttc < minTTC) {
            minTTC = ttc;
        }
        getLog().debugSimTime(this, "TTC: {}", ttc);

    }
}
