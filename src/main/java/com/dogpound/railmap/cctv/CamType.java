package com.dogpound.railmap.cctv;

/**
 * The camera kinds. eye = the lens in the north-facing model (px); the view starts 2.4 px in front of it so the camera
 * never sees its own housing. pitch = default down-tilt, fov = vertical field of view at 1x.
 */
public enum CamType {
    DOME("cctv_dome", "Dome", 6, 70, 30, new double[]{8, 10, 6}),
    BULLET("cctv_bullet", "Bullet", 5, 58, 15, new double[]{8, 10, 1}),
    PTZ("cctv_ptz", "PTZ", 24, 55, 20, new double[]{8, 8.5, 3}),
    FISHEYE("cctv_fisheye", "Fisheye 360", 6, 140, 90, new double[]{8, 12.5, 8}),
    NIGHT("cctv_night", "Night / IR", 11, 58, 15, new double[]{8, 10, 1}),
    THERMAL("cctv_thermal", "Thermal", 9, 40, 10, new double[]{8, 9, 1.5}),
    PLATFORM("cctv_platform", "Platform", 7, 75, 25, new double[]{8, 13.5, 3}),
    DOORBELL("cctv_doorbell", "Doorbell", 4, 100, 8, new double[]{8, 10.5, 14.2}),
    TRAIN(null, "Train forward-facing", 6, 70, 4, null),
    /** an inspection drone's gimbal camera (drone.EntityDrone): flies, streams over LTE like a train camera */
    DRONE(null, "Inspection drone", 0, 62, 45, null),
    /** a yard gantry crane's spreader camera, looking straight down (freight.TileYardGantry, batch 5 #1) - keep last */
    CRANE(null, "Crane spreader camera", 0, 70, 89, null);

    public final String block, label;
    public final int watts;
    public final float fov, pitch;
    public final double[] eye;

    CamType(String block, String label, int watts, float fov, float pitch, double[] eye) {
        this.block = block; this.label = label; this.watts = watts; this.fov = fov; this.pitch = pitch; this.eye = eye;
    }

    public static CamType of(int i) { CamType[] v = values(); return v[Math.max(0, Math.min(v.length - 1, i))]; }

    /** PTZ pivots (model px): pan about the vertical through (8, 7), tilt about y = 8.5, z = 7 */
    public static final double PIV_X = 8, PIV_Y = 8.5, PIV_Z = 7;

    /** how the picture looks: 0 colour, 1 IR green monochrome, 2 thermal palette, 3 IR grey monochrome */
    public int look(boolean ir) {
        if (this == THERMAL) return 2;
        if (!ir) return 0;
        return this == NIGHT ? 1 : 3;
    }

    public boolean fisheye() { return this == FISHEYE; }
}
