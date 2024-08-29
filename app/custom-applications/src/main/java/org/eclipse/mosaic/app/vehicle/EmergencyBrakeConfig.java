package org.eclipse.mosaic.app.vehicle;

/**
 * Default configuration for the {@link EmergencyBrakeApp}.
 */
public class EmergencyBrakeConfig {

    /**
     * The deceleration in m/s^2 with which the vehicle slows down in case an
     * obstacle is detected
     */
    public double deceleration = 5d;

    /**
     * The speed in m/s the vehicle is trying to reach during slow down in case an
     * obstacle is detected
     */
    public double targetSpeed = 2.0d;
}