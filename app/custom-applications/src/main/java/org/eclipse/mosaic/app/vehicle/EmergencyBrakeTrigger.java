package org.eclipse.mosaic.app.vehicle;

import org.eclipse.mosaic.lib.geo.GeoPoint;

public class EmergencyBrakeTrigger {
    public double ttc;
    public GeoPoint targetPosition;

    public EmergencyBrakeTrigger(double ttc, GeoPoint targetPosition) {
        this.ttc = ttc;
        this.targetPosition = targetPosition;
    }
}
