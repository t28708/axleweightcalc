package com.drive.axleweightcalc;

/**
 * Holds the four weight values extracted from a scale ticket PDF.
 * Any field that could not be parsed will be 0.
 */
public class ParsedTicket {

    public final int steerAxle;
    public final int driveAxle;
    public final int trailerAxle;
    public final int grossWeight;

    public ParsedTicket(int steerAxle, int driveAxle, int trailerAxle, int grossWeight) {
        this.steerAxle   = steerAxle;
        this.driveAxle   = driveAxle;
        this.trailerAxle = trailerAxle;
        this.grossWeight = grossWeight;
    }

    /**
     * Returns true when at least the three axle weights were successfully parsed.
     * (Gross can be derived, so we don't strictly require it.)
     */
    public boolean isValid() {
        return steerAxle > 0 && driveAxle > 0 && trailerAxle > 0;
    }

    /**
     * Returns true when ALL four values were found.
     */
    public boolean isComplete() {
        return isValid() && grossWeight > 0;
    }

    @Override
    public String toString() {
        return "ParsedTicket{steer=" + steerAxle
                + ", drive=" + driveAxle
                + ", trailer=" + trailerAxle
                + ", gross=" + grossWeight + "}";
    }
}
