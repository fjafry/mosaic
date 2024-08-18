package org.eclipse.mosaic.app.vehicle;

import org.eclipse.mosaic.lib.geo.CartesianPoint;

public class EmergencyBrakeTrigger {
    public double ttc;
    public CartesianPoint targetPosition;

    public EmergencyBrakeTrigger(double ttc, CartesianPoint targetPosition) {
        this.ttc = ttc;
        this.targetPosition = targetPosition;
    }
}
