package com.dogpound.railmap.station;

/**
 * A passenger facility at a station (her ChatGPT list #81 + #85): lifts, escalators, toilets, help points, vending
 * machines, kiosks, lighting, heating. Within 48 blocks of a station it counts towards the station's facilities score
 * (passenger satisfaction), its accessibility rating, and its daily running costs and income.
 */
public interface IStationFacility {
    /** what it is, for the audit ("Lift", "Toilet" ...) */
    String facilityKind();
    /** in service right now (powered, stocked, not broken) */
    boolean facilityWorking();
    /** satisfaction points it adds while working (a ticket machine is 15) */
    int facilityPoints();
    /** what it adds to the accessibility rating while working */
    default int accessPoints() { return 0; }
    /** gives step-free access between street and platform while working (a lift / ramp) */
    default boolean stepFree() { return false; }
    /** daily running cost, cents (power, water, cleaning, staff) */
    long dailyCost();
    /** income since it was last asked, cents (concession rent, sales) - and reset */
    default long takeIncome() { return 0; }
}
