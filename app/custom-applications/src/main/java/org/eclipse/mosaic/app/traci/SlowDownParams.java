package org.eclipse.mosaic.app.traci;

public class SlowDownParams {
    public double targetSpeed;
    public double duration;

    public SlowDownParams(double targetSpeed, double duration) {
        this.targetSpeed = targetSpeed;
        this.duration = duration;
    }
}
