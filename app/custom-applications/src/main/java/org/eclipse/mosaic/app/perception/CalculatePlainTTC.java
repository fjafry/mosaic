package org.eclipse.mosaic.app.perception;

import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.SimplePerceptionConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.VehicleObject;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;

import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CalculatePlainTTC extends ConfigurableApplication<TTCconfig, VehicleOperatingSystem>
        implements VehicleApplication {

    public CalculatePlainTTC() {
        super(TTCconfig.class, "PlainTTCconfig");
    }

    private double ttc = 0.0;
    private double minTTC = Double.MAX_VALUE;
    private TTCconfig config;

    /**
     * The angle used by the perception module. [degree]
     */
    private static final double VIEWING_ANGLE = 360d;
    /**
     * The distance used by the perception module. [m]
     */
    private static final double VIEWING_RANGE = 300d;

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Started {} on {}.", this.getClass().getSimpleName(), getOs().getId());

        enablePerceptionModule();
        config = this.getConfiguration();
    }

    private void enablePerceptionModule() {
        SimplePerceptionConfiguration perceptionModuleConfiguration = new SimplePerceptionConfiguration.Builder(
                VIEWING_ANGLE, VIEWING_RANGE)
                .build();
        getOs().getPerceptionModule().enable(perceptionModuleConfiguration);
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "minTTC: {}", minTTC);

    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
        List<VehicleObject> targetVehicles = perceiveVehicles();
        if (targetVehicles.isEmpty()) {
            return;
        }
        calculateTTC(updatedVehicleData, targetVehicles.get(0));
    }

    @Override
    public void processEvent(Event event) throws Exception {

    }

    private List<VehicleObject> perceiveVehicles() {
        List<VehicleObject> perceivedVehicles = getOs().getPerceptionModule().getPerceivedVehicles();
        List<VehicleObject> targetVehicles = perceivedVehicles.stream().filter(v -> config.vehicleId.equals(v.getId()))
                .collect(Collectors.toList());

        return targetVehicles;
    }

    private void calculateTTC(VehicleData egoVehicle, VehicleObject targetVehicle) {
        double distance = egoVehicle.getPosition().toCartesian().distanceTo(targetVehicle.getPosition().toCartesian());
        double egoSpeed = egoVehicle.getSpeed();
        if (egoSpeed > 0) {
            ttc = distance / egoSpeed;

            if (ttc < minTTC) {
                minTTC = ttc;
            }
        }
        getLog().debugSimTime(this, "TTC: {}", ttc);
    }
}
