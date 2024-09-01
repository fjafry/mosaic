package org.eclipse.mosaic.app.vehicle;

import org.eclipse.mosaic.app.vehicleconfig.VehicleConfig;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.app.perception.MetricsInteraction;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This class implements an application for vehicles.
 * In case the vehicle sensors detect an obstacle the vehicle will perform an
 * emergency brake.
 * If the emergency brake endures a specified minimum time duration a DENMessage
 * is sent out.
 */
public class EmergencyManeuver extends AbstractApplication<VehicleOperatingSystem>
        implements VehicleApplication {

    // Keep status of the emergency brake performed on obstacle
    private boolean emergencyBrake = false;
    private double acceleration;
    private double deceleration;
    private double reactionTime;
    private double targetSpeed;

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Startup of emergency maneuver app");
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
            // TODO: consider position of obstacle and ttc to determine type of emergency
            // maneuver
            if (!emergencyBrake) {
                emergencyBrake = true;
                getLog().infoSimTime(this, "emergency brake caused by detected obstacle");

                double currentSpeed = getOs().getVehicleData().getSpeed();
                double duration;
                if (currentSpeed > targetSpeed) {
                    duration = (currentSpeed - targetSpeed) / deceleration;
                } else {
                    duration = (targetSpeed - currentSpeed) / acceleration;
                }
                this.getOs().getEventManager()
                        .newEvent(getOs().getSimulationTime() + (long) (reactionTime * TIME.SECOND), this)
                        .withResource("ChangeSpeed. Duration: " + duration)
                        .schedule();
                EmergencyBrakeTrigger trigger = (EmergencyBrakeTrigger) resource;
                final MetricsInteraction metricsInteraction = new MetricsInteraction(
                        getOs().getSimulationTime(),
                        null, getOs().getId(), trigger.ttc, reactionTime, getOs().getSimulationTime());
                getOs().sendInteractionToRti(metricsInteraction);
            }
        } else if (resource instanceof String) {
            String resourceString = (String) resource;
            if (resourceString.contains("ChangeSpeed")) {
                getLog().infoSimTime(this, "Performing emergency brake after reaction time");

                // Define the regex pattern to match a double value
                Pattern pattern = Pattern.compile("\\d+\\.\\d+");
                Matcher matcher = pattern.matcher(resourceString);
                matcher.find();

                double duration = Double.parseDouble(matcher.group());
                getOs().changeSpeedWithInterval(targetSpeed, (long) (duration * TIME.SECOND));
            }
        } else if (resource instanceof VehicleConfig) {
            VehicleConfig config = (VehicleConfig) resource;
            getLog().infoSimTime(this, "Vehicle config read from json file");
            getLog().info("Configs acceleration equals {}", config.acceleration);
            getLog().info("Configs deceleration equals {}", config.deceleration);
            getLog().info("Configs reactionTime equals {}", config.reactionTime);
            getLog().info("Configs targetSpeed equals {}", config.targetSpeed);

            acceleration = config.acceleration;
            deceleration = config.deceleration;
            reactionTime = config.reactionTime;
            targetSpeed = config.targetSpeed;
        }

    }
}
