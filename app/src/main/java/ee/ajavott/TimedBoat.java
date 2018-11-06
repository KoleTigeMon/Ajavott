package ee.ajavott;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Allan on 18.04.2015.
 */
public class TimedBoat {

    public static final Pattern BOAT_NUMBER_PATTERN = Pattern.compile("([\\d\\w]+)");

    private final String boat;
    private final String time;
    private final String selectedKp;
    private int retries = 50;
    private boolean isBadNoBoatNumber = false;
    private boolean isBadExistBoatNumber = false;
    private boolean cancelled = false;

    public TimedBoat(final String boat, final String time, final String selectedKp) {
        Matcher boatNumbermatcher = BOAT_NUMBER_PATTERN.matcher(boat);
        if(boatNumbermatcher.find()) {
            this.boat = boatNumbermatcher.group(1);
        } else {
            this.boat = boat;
        }
        this.time = time;
        this.selectedKp = selectedKp;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public int getRetries() {
        return this.retries;
    }

    public String getBoat() {
        return this.boat;
    }

    public String getTime() {
        return this.time;
    }

    public String getSelectedKP() {
        return this.selectedKp;
    }

    public String toString() {
        return this.boat + " " + this.time + " " + this.selectedKp;
    }

    public void setIsBadNoBoatNumber() {
        this.isBadNoBoatNumber = true;
    }
    public void setIsBadExistBoatNumber() {
        this.isBadExistBoatNumber = true;
    }

    public boolean isBadNoBoatNumber() {
        return this.isBadNoBoatNumber;
    }

    public boolean isBadExistBoatNumber() {
        return this.isBadExistBoatNumber;
    }


    public void setIsCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }
}
