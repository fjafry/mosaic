package org.eclipse.mosaic.app.perception;

import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.SimplePerceptionConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.VehicleObject;
import org.eclipse.mosaic.fed.application.app.api.MosaicApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.application.ApplicationInteraction;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.CartesianPolygon;
import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.objects.traffic.SumoTraciResult;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;

import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CriticalityMetrics extends ConfigurableApplication<MetricsConfig, VehicleOperatingSystem>
        implements VehicleApplication, MosaicApplication {

    public CriticalityMetrics() {
        super(MetricsConfig.class, "MetricsConfig");
    }

    private double ttc = 0.0;
    private double minTTC = Double.MAX_VALUE;
    private long minTTCtime = 0;
    private boolean ttcIncreased = false;
    private MetricsConfig config;

    // trigger is the time when emergency maneuver is called
    // reaction is the actual time when the driver reacts (trigger + reaction time)
    private double ActualTTCAtReaction = 0.0;
    private double ActualTTCAtTrigger = 0.0;
    private double PlainTTCAtTrigger = 0.0;
    private long emergencyTriggeredAt = 0;

    private double distance = Double.MAX_VALUE;
    private double minDistance = Double.MAX_VALUE;
    private double distanceAtTrigger = 0.0;
    private double distanceAtReaction = 0.0;

    private boolean collisionDetected = false;
    private long collisionOccuredAt = 0;

    /**
     * The angle used by the perception module. [degree]
     */
    private static final double VIEWING_ANGLE = 360d;
    /**
     * The distance used by the perception module. [m]
     */
    private static final double VIEWING_RANGE = 400d;

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
        getLog().info("minTTC: {} at time: {}", minTTC, minTTCtime);
        getLog().info("Minimum distance: {}", minDistance);
        getLog().info("Emergency triggered at: {}", emergencyTriggeredAt);
        getLog().info("Distance at trigger: {}", distanceAtTrigger);
        getLog().info("Actual TTC at trigger: {}", ActualTTCAtTrigger);
        getLog().info("Plain TTC at trigger: {}", PlainTTCAtTrigger);
        getLog().info("Actual TTC at reaction: {}", ActualTTCAtReaction);
        getLog().info("Distance at reaction: {}", distanceAtReaction);
        getLog().info("Collision detected: {}", collisionDetected);
        getLog().info("Collision occured at: {}", collisionOccuredAt);
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
        List<VehicleObject> targetVehicles = perceiveVehicles();
        if (targetVehicles.size() <= 1) {
            return;
        }
        if (!collisionDetected) {
            calculateMinDistance(targetVehicles);
            calculateTTC(targetVehicles);
        }
    }

    @Override
    public void processEvent(Event event) throws Exception {
        Object resource = event.getResource();
        if (resource instanceof String) {
            String resourceString = (String) resource;
            if (resourceString.equals("StoreTTC")) {
                if (!collisionDetected) {
                    ActualTTCAtReaction = ttc;
                    distanceAtReaction = distance;
                }
            }
        }
    }

    @Override
    public void onInteractionReceived(ApplicationInteraction applicationInteraction) {
        if (applicationInteraction instanceof MetricsInteraction) {
            final MetricsInteraction metricsInteraction = (MetricsInteraction) applicationInteraction;
            getLog().infoSimTime(this, "MosaicInteractionHandlingApp received MetricsInteraction: {}",
                    metricsInteraction.toString());
            if (metricsInteraction.getVehId().equals(config.vruVehicleId)) {
                return;
            }
            emergencyTriggeredAt = metricsInteraction.getTriggerTime();
            if (!collisionDetected) {
                ActualTTCAtTrigger = ttc;
                PlainTTCAtTrigger = metricsInteraction.getPlainTTC();
                distanceAtTrigger = distance;
            }
            this.getOs().getEventManager()
                    .newEvent(getOs().getSimulationTime() + (long) (metricsInteraction.getReactionTime() * TIME.SECOND),
                            this)
                    .withResource("StoreTTC")
                    .schedule();
        }
    }

    @Override
    public void onSumoTraciResponded(SumoTraciResult sumoTraciResult) {
    }

    private List<VehicleObject> perceiveVehicles() {
        List<VehicleObject> perceivedVehicles = getOs().getPerceptionModule().getPerceivedVehicles();

        List<VehicleObject> targetVehicles = perceivedVehicles.stream()
                .filter(v -> (v.getId().equals(config.driverVehicleId)) || (v.getId().equals(config.vruVehicleId)))
                .collect(Collectors.toList());

        return targetVehicles;
    }

    private void calculateTTC(List<VehicleObject> targetVehicles) {
        VehicleObject ego = targetVehicles.stream().filter(v -> v.getId().equals(config.driverVehicleId)).findFirst()
                .get();
        VehicleObject vru = targetVehicles.stream().filter(v -> v.getId().equals(config.vruVehicleId)).findFirst()
                .get();
        Vector3d r1 = getVectorFromCartesianPoint(ego.getPosition().toCartesian());
        Vector3d r2 = getVectorFromCartesianPoint(vru.getPosition().toCartesian());
        Vector3d v1 = getVectorFromSpeedAndHeading(ego.getSpeed(), ego.getHeading());
        Vector3d v2 = getVectorFromSpeedAndHeading(vru.getSpeed(), vru.getHeading());

        Vector3d d_rel = r1.subtract(r2);
        Vector3d v_rel = v2.subtract(v1);
        if (v_rel.magnitude() == 0) {
            return;
        }

        double v_rel_magnitude = v_rel.magnitude();
        double d_rel_magnitude = d_rel.magnitude();
        ttc = d_rel_magnitude / v_rel_magnitude;

        boolean vruLeftCA = vru.getPosition().toCartesian().getY()
                - vru.getLength() > ego.getPosition().toCartesian().getY() + ego.getWidth() / 2;

        if (ttc > 0 && ttc < minTTC && distance == minDistance && !vruLeftCA && ego.getSpeed() > 0 && !ttcIncreased) {
            minTTC = ttc;
            minTTCtime = getOs().getSimulationTime();
        } else if (ttc > minTTC && !ttcIncreased) {
            ttcIncreased = true;
            getLog().infoSimTime(this, "minTTC set to {}", minTTC);
        }
        getLog().debugSimTime(this, "Time: {}, speed: {}, Relative speed: {}, Relative distance: {}, TTC: {}",
                getOs().getSimulationTime(),
                ego.getSpeed(), v_rel_magnitude, d_rel_magnitude, ttc);
    }

    private Vector3d getVectorFromSpeedAndHeading(double speed, double heading) {
        // heading is in degrees from true north clockwise
        double y = speed * Math.cos(Math.toRadians(heading));
        double x = speed * Math.sin(Math.toRadians(heading));
        return new Vector3d(x, y, 0);
    }

    private Vector3d getVectorFromCartesianPoint(CartesianPoint point) {
        return new Vector3d(point.getX(), point.getY(), 0);
    }

    private void calculateMinDistance(List<VehicleObject> targetVehicles) {
        VehicleObject ego = targetVehicles.stream().filter(v -> v.getId().equals(config.driverVehicleId)).findFirst()
                .get();
        VehicleObject vru = targetVehicles.stream().filter(v -> v.getId().equals(config.vruVehicleId)).findFirst()
                .get();
        List<Vector3d> egoCornersVector3d = ego.getBoundingBox().getAllCorners();
        List<Vector3d> vruCornersVector3d = vru.getBoundingBox().getAllCorners();
        List<CartesianPoint> egoCornersCartesianPoints = egoCornersVector3d.stream().map(v -> v.toCartesian())
                .collect(Collectors.toList());
        List<CartesianPoint> vruCornersCartesianPoints = vruCornersVector3d.stream().map(v -> v.toCartesian())
                .collect(Collectors.toList());
        CartesianPolygon egoPolygon = new CartesianPolygon(egoCornersCartesianPoints);
        CartesianPolygon vruPolygon = new CartesianPolygon(vruCornersCartesianPoints);
        double currentDistance = Double.MAX_VALUE;
        if (egoPolygon.isIntersectingPolygon(vruPolygon)) {
            currentDistance = 0;
        } else {
            for (int i = 0; i < 4; i++) {
                CartesianPoint ego1 = egoCornersCartesianPoints.get(i);
                CartesianPoint ego2 = egoCornersCartesianPoints.get((i + 1) % 4);
                for (int j = 0; j < 4; j++) {
                    CartesianPoint vru1 = vruCornersCartesianPoints.get(j);
                    CartesianPoint vru2 = vruCornersCartesianPoints.get((j + 1) % 4);
                    currentDistance = Math.min(currentDistance, getDistanceBetweenPointAndLine(ego1, vru1, vru2));
                    currentDistance = Math.min(currentDistance, getDistanceBetweenPointAndLine(ego2, vru1, vru2));
                    currentDistance = Math.min(currentDistance, getDistanceBetweenPointAndLine(vru1, ego1, ego2));
                    currentDistance = Math.min(currentDistance, getDistanceBetweenPointAndLine(vru2, ego1, ego2));
                }
            }
        }
        distance = currentDistance;
        getLog().debugSimTime(this, "Current Distance: {}", currentDistance);

        if (currentDistance < minDistance) {
            minDistance = currentDistance;
        }
        if (currentDistance == 0) {
            collisionDetected = true;
            collisionOccuredAt = getOs().getSimulationTime();
        }
    }

    double getDistanceBetweenPointAndLine(CartesianPoint point, CartesianPoint lineStart, CartesianPoint lineEnd) {
        double x = point.getX();
        double y = point.getY();
        double x1 = lineStart.getX();
        double y1 = lineStart.getY();
        double x2 = lineEnd.getX();
        double y2 = lineEnd.getY();
        double A = x - x1;
        double B = y - y1;
        double C = x2 - x1;
        double D = y2 - y1;
        double dot = A * C + B * D;
        double len_sq = C * C + D * D;
        double param = -1;
        if (len_sq != 0) {
            param = dot / len_sq;
        }
        double xx, yy;
        if (param < 0) {
            xx = x1;
            yy = y1;
        } else if (param > 1) {
            xx = x2;
            yy = y2;
        } else {
            xx = x1 + param * C;
            yy = y1 + param * D;
        }
        double dx = x - xx;
        double dy = y - yy;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
