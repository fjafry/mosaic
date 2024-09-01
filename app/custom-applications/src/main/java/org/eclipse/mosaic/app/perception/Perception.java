package org.eclipse.mosaic.app.perception;

import org.eclipse.mosaic.app.vehicle.DetermineReaction;
import org.eclipse.mosaic.app.vehicle.EmergencyBrakeTrigger;
import org.eclipse.mosaic.app.vehicleconfig.VehicleConfig;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.SimplePerceptionConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.errormodels.DistanceFilter;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.errormodels.PositionModifier;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.errormodels.BoundingBoxOcclusion;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.VehicleObject;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.Application;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This application showcases the functionalities of the
 * {@link org.eclipse.mosaic.fed.application.app.api.perception.PerceptionModule}
 * and its
 * {@link org.eclipse.mosaic.fed.application.ambassador.simulation.perception.errormodels.PerceptionModifier}s.
 */
public class Perception extends ConfigurableApplication<PerceptionConfig, VehicleOperatingSystem>
        implements VehicleApplication {

    public Perception() {
        super(PerceptionConfig.class, "PerceptionConfig");
    }

    private double emergencyBrakeMinTTC = 3.0;
    private String perceptionTargetId;
    private boolean emergencyBrakeTrigger = false;
    private int perceivedCounter = 0;
    private int perceptionSimSteps = 10;
    private double viewingAngle;
    private double viewingRange;

    private DetermineReaction determineReaction;

    /**
     * The angle used by the perception module. [degree]
     */
    private static final double VIEWING_ANGLE = 150d;
    /**
     * The distance used by the perception module. [m]
     */
    private static final double VIEWING_RANGE = 200d;

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Started {} on {}.", this.getClass().getSimpleName(), getOs().getId());
        PerceptionConfig config = this.getConfiguration();
        viewingAngle = config.viewingAngle == 0 ? VIEWING_ANGLE : config.viewingAngle;
        viewingRange = config.viewingRange == 0 ? VIEWING_RANGE : config.viewingRange;
        enablePerceptionModule();
    }

    private void enablePerceptionModule() {
        // filter to emulate occlusion
        BoundingBoxOcclusion boundingBoxOcclusion = new BoundingBoxOcclusion();
        // filter to reduce perception probability based on distance to ego vehicle
        DistanceFilter distanceFilter = new DistanceFilter(getRandom(), 0.0);
        // filter adding noise to longitudinal and lateral
        PositionModifier positionModifier = new PositionModifier(getRandom());

        SimplePerceptionConfiguration perceptionModuleConfiguration = new SimplePerceptionConfiguration.Builder(
                viewingAngle, viewingRange)
                .addModifiers(boundingBoxOcclusion, distanceFilter, positionModifier)
                .build();
        getOs().getPerceptionModule().enable(perceptionModuleConfiguration);
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Shutdown {} on {}.", this.getClass().getSimpleName(), getOs().getId());
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
        if (this.perceptionTargetId == null) {
            return;
        }
        List<VehicleObject> targetVehicles;
        if (perceptionSimSteps == 0) {
            return;
        }
        targetVehicles = perceiveVehicles();
        if (targetVehicles.isEmpty()) {
            if (perceivedCounter != 0) {
                perceivedCounter--;
            }
            return;
        } else {
            if (perceivedCounter < perceptionSimSteps) {
                perceivedCounter++;
            }
        }
        if (perceivedCounter == perceptionSimSteps && !emergencyBrakeTrigger) {
            checkEmergencyBrake(targetVehicles.get(0).getPosition().toCartesian());
        }
    }

    @Override
    public void processEvent(Event event) throws Exception {
        Object resource = event.getResource();
        if (resource instanceof VehicleConfig) {
            VehicleConfig config = (VehicleConfig) resource;
            getLog().infoSimTime(this, "Vehicle config read from json file");
            getLog().info("Configs emergencyBrakeMinTTC is {} s", config.emergencyBrakeMinTTC);
            getLog().info("Configs perception target is {} s", config.perceptionTargetId);
            getLog().info("Configs perception sim steps to consider {}", config.perceptionSimSteps);
            emergencyBrakeMinTTC = config.emergencyBrakeMinTTC == 0 ? this.emergencyBrakeMinTTC
                    : config.emergencyBrakeMinTTC;
            perceptionTargetId = config.perceptionTargetId;
            perceptionSimSteps = config.perceptionSimSteps;
            determineReaction = new DetermineReaction(this.getOs(), getLog(), emergencyBrakeMinTTC);
        }
    }

    /**
     * Perceives vehicles in viewing range
     */
    private List<VehicleObject> perceiveVehicles() {
        List<VehicleObject> perceivedVehicles = getOs().getPerceptionModule().getPerceivedVehicles();
        getLog().debugSimTime(this, "Perceived vehicles: {}",
                perceivedVehicles.stream().map(VehicleObject::getId).collect(Collectors.toList()));

        List<VehicleObject> targetVehicles = perceivedVehicles.stream()
                .filter(v -> perceptionTargetId.equals(v.getId()))
                .collect(Collectors.toList());

        return targetVehicles;
    }

    private double calculateTTC(VehicleData egoVehicle, CartesianPoint targetPoint) {
        double ttc = 0.0;
        double distance = egoVehicle.getPosition().toCartesian().distanceTo(targetPoint);
        double egoSpeed = egoVehicle.getSpeed();
        if (egoSpeed > 0) {
            ttc = distance / egoSpeed;
        }
        getLog().debugSimTime(this, "TTC: {}", ttc);
        return ttc;
    }

    private void checkEmergencyBrake(CartesianPoint otherPosition) {
        double ttc = calculateTTC(getOs().getVehicleData(), otherPosition);
        if (ttc <= emergencyBrakeMinTTC) {
            // should apply emergency brake
            emergencyBrakeTrigger = true;
            getLog().infoSimTime(this, "Should apply emergency break now. TTC value is {}", ttc);
            List<? extends Application> applications = getOs().getApplications();
            for (Application application : applications) {
                String appName = application.getClass().getSimpleName();
                if (appName.equals("EmergencyManeuver")) {
                    getLog().infoSimTime(this, "Found EmergencyManeuver, scheduling event");
                    EmergencyBrakeTrigger trigger = new EmergencyBrakeTrigger(ttc, otherPosition);
                    this.getOs().getEventManager()
                            .newEvent(getOs().getSimulationTime() + 1, application)
                            .withResource(trigger)
                            .schedule();
                }
            }
        }
    }
}
