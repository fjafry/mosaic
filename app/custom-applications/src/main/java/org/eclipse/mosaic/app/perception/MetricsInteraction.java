package org.eclipse.mosaic.app.perception;

import org.eclipse.mosaic.interactions.application.ApplicationInteraction;

import java.util.Objects;

public class MetricsInteraction extends ApplicationInteraction {

    private double plainTTC;
    private double reactionTime;
    private long triggerTime;
    private String vehId;

    public MetricsInteraction(long time, String unitId, String id, double plainTTC, double reactionTime,
            long triggerTime) {
        super(time, unitId);
        this.plainTTC = plainTTC;
        this.reactionTime = reactionTime;
        this.triggerTime = triggerTime;
        this.vehId = id;
    }

    public String getVehId() {
        return this.vehId;
    }

    public double getPlainTTC() {
        return this.plainTTC;
    }

    public double getReactionTime() {
        return this.reactionTime;
    }

    public long getTriggerTime() {
        return this.triggerTime;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 13 * hash + Objects.hashCode(this.plainTTC) + Objects.hashCode(this.reactionTime)
                + Objects.hashCode(this.triggerTime) + Objects.hashCode(this.vehId);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MetricsInteraction other = (MetricsInteraction) obj;
        return Objects.equals(this.plainTTC, other.plainTTC) && Objects.equals(this.reactionTime, other.reactionTime)
                && Objects.equals(this.triggerTime, other.triggerTime) && Objects.equals(this.vehId, other.vehId);
    }

    @Override
    public String toString() {
        return "MetricsInteraction{" + "vehicleId=" + this.vehId + ", plainTTC=" + this.plainTTC + ", reactionTime="
                + this.reactionTime
                + ", triggerTime=" + this.triggerTime + '}';
    }

}
