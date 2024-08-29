package org.eclipse.mosaic.app.vehicle;

import org.eclipse.mosaic.app.traci.SlowDownParams;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.Application;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This class implements an application for vehicles.
 * In case the vehicle sensors detect an obstacle the vehicle will perform an
 * emergency brake.
 * If the emergency brake endures a specified minimum time duration a DENMessage
 * is sent out.
 */
public class EmergencyBrake extends ConfigurableApplication<EmergencyBrakeConfig, VehicleOperatingSystem>
        implements VehicleApplication {

    // Keep status of the emergency brake performed on obstacle
    private boolean emergencyBrake = false;
    EmergencyBrakeConfig config;

    /**
     * Initializes an instance of the {@link EmergencyBrakeApp}.
     */
    public EmergencyBrake() {
        super(EmergencyBrakeConfig.class, "EmergencyBrakeConfig");
    }

    @Override
    public void onStartup() {
        config = this.getConfiguration();
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {

    }

    @Override
    public void onShutdown() {

    }

    @Override
    public void processEvent(Event event) throws Exception {
        Object resource = event.getResource();
        if (resource instanceof EmergencyBrakeTrigger) {
            // Initiate emergency brake if obstacle is detected
            if (!emergencyBrake) {
                emergencyBrake = true;
                getLog().infoSimTime(this, "Performing emergency brake caused by detected obstacle");
                double maxDecel = config.deceleration;
                double targetSpeed = config.targetSpeed;
                double currentSpeed = getOs().getVehicleData().getSpeed();
                double duration = (currentSpeed - targetSpeed) / maxDecel;
                List<? extends Application> applications = getOs().getApplications();
                for (Application application : applications) {
                    String appName = application.getClass().getSimpleName();
                    if (appName.equals("SlowDown")) {
                        getLog().infoSimTime(this, "Found SlowDown, scheduling event");
                        SlowDownParams params = new SlowDownParams(targetSpeed, duration);
                        this.getOs().getEventManager()
                                .newEvent(getOs().getSimulationTime() + 1, application)
                                .withResource(params)
                                .schedule();
                    }
                }

            }
        }

    }
}
