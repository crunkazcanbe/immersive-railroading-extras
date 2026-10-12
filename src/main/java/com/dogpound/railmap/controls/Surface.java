package com.dogpound.railmap.controls;

import com.dogpound.railmap.grid.Panel;

/**
 * One face of a machine that carries live controls (requested feature). The Panel's u/v/face are in the surface's own frame, which is the model frame turned so that `side` becomes
 * the front (north): x = 16 - u, y = v, plane z = face, controls standing toward -z. `tilt` leans the surface back about
 * the line y = pivot on the plane (desks, lever frames).
 */
public final class Surface {
    public final String side;          // north (front), south, east, west, up
    public final double tilt, pivot;
    public final boolean plate;        // draw a backing plate (else the model's own housing is behind the controls)
    public final Panel panel;

    public Surface(String side, double tilt, double pivot, boolean plate, Panel panel) {
        this.side = side; this.tilt = tilt; this.pivot = pivot; this.plate = plate; this.panel = panel;
    }

    /** degrees about Y that turn the front onto `side` (model frame, block centre) */
    public double yaw() {
        switch (side) {
            case "east": return -90;
            case "west": return 90;
            case "south": return 180;
            default: return 0;
        }
    }

    public boolean top() { return side.equals("up"); }

    /** a point / direction of the model frame (px) into this surface's frame; dir = true ignores translation */
    public double[] toSurface(double[] p, boolean dir) {
        double x = p[0], y = p[1], z = p[2];
        if (!dir) { x -= 8; y -= 8; z -= 8; }
        if (top()) {                                     // inverse of rotate +90 about X
            double ny = y * 0 + z * 1, nz = -y * 1 + z * 0;
            y = ny; z = nz;
        } else {
            double a = Math.toRadians(-yaw()), c = Math.cos(a), s = Math.sin(a);   // inverse rotation about Y
            double nx = x * c + z * s, nz = -x * s + z * c;
            x = nx; z = nz;
        }
        if (!dir) { x += 8; y += 8; z += 8; }
        if (tilt != 0) {                                 // inverse of the lean about (y = pivot, z = face)
            double a = Math.toRadians(-tilt), c = Math.cos(a), s = Math.sin(a);
            double oy = dir ? 0 : pivot, oz = dir ? 0 : panel.face;
            double yy = y - oy, zz = z - oz;
            double ny = yy * c - zz * s, nz = yy * s + zz * c;
            y = ny + oy; z = nz + oz;
        }
        return new double[]{x, y, z};
    }

    /** the clickable control a ray (model frame, px) hits on this surface, or null; [0] distance in px */
    public Panel.C hit(double[] eye, double[] look, double[] dist) {
        double[] e = toSurface(eye, false), d = toSurface(look, true);
        if (Math.abs(d[2]) < 1e-6) return null;
        double t = (panel.face - e[2]) / d[2];
        if (t < 0 || t > 6 * 16) return null;
        double mx = e[0] + d[0] * t, my = e[1] + d[1] * t;
        Panel.C c = panel.hitAt(16 - mx, my);
        if (c != null) dist[0] = t;
        return c;
    }
}
