package com.dogpound.railmap.track;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;

import java.util.Locale;
import java.util.Map;

/**
 * Tunnel jet fan (her ChatGPT list #16): a reversible 37 kW axial jet fan hung from the tunnel roof. The tunnel control
 * panel commands speed and direction (it stops by itself if the panel stops talking to it for 5 s); the local
 * isolator on its side can take it out (OFF) or run it by hand (FWD / REV). The motor draws real power from a grid
 * Power Cable touching it (power rises with the cube of speed: half speed = an eighth of the power), the 12 V terminal
 * runs its starter and lamps. Spins up and down over a few seconds; you can see the air it moves.
 */
public class TileTunnelFan extends TrackGear {
    private int cmdSpeed, cmdDir = 1;
    private long cmdAt;
    private double speed;                    // -1..1 actual (sign = direction)
    private double kw;
    private String supply = "";
    private boolean noSupply;
    /** next-48 #29: the tunnel panel's UPS-backed emergency fan bus feeds it in an emergency when the grid can't */
    private boolean emergFeed, onEbus;
    void emergencyFeed(boolean on) { emergFeed = on; }
    public boolean onEmergencyBus() { return onEbus; }

    public String id() { return "JF" + Math.floorMod(pos.getX() * 7 + pos.getZ() * 5 + pos.getY(), 100); }
    @Override public String wiredName() { return "Tunnel jet fan " + id(); }

    /** the way FORWARD blows: along the fan's axis, which runs across its front (the side with the isolator box) */
    public EnumFacing axis() { return controlFacing().rotateYCCW(); }

    void command(int pct, int dir) { cmdSpeed = pct; cmdDir = dir; cmdAt = world.getTotalWorldTime(); }
    public boolean running() { return Math.abs(speed) > 0.05; }
    public double thrust() { return speed * Math.abs(speed); }
    public double kw() { return kw; }
    public boolean faulty() { return powered() && noSupply && want() != 0; }

    /** what it is asked to do now: -1..1 */
    private double want() {
        int local = ctl.get("s_local");                                 // 0 REMOTE, 1 OFF (isolated), 2 FWD, 3 REV
        if (local == 1) return 0;
        if (local == 2) return 1;
        if (local == 3) return -1;
        if (world.getTotalWorldTime() - cmdAt > 100) return 0;           // panel watchdog
        return cmdDir * cmdSpeed / 100.0;
    }

    @Override public String gearState() { return !powered() ? "NO 12 V - starter dead" : noSupply && want() != 0 ? "NO MOTOR SUPPLY - " + supply : running() ? String.format(Locale.ROOT, "%s %.0f%% · %.1f kW", speed > 0 ? "FORWARD" : "REVERSE", Math.abs(speed) * 100, kw) : "stopped"; }
    @Override public int wiredLamp() { return !powered() || faulty() ? 3 : running() ? 1 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("speedPct", Math.round(speed * 100)); m.put("kw", Math.round(kw * 10) / 10.0); m.put("supply", supply); }

    @Override
    protected void tickGear() {
        if (age % 20 != 0) return;
        double target = powered() ? want() : 0;
        double kwNeed = 37 * Math.pow(Math.abs(target), 3);
        noSupply = false;
        if (target != 0) {
            GridFeed.Result r = GridFeed.draw(world, pos, Math.max(0.5, kwNeed));
            supply = r.what;
            onEbus = !r.ok && emergFeed;
            if (onEbus) supply = "EMERGENCY FAN BUS (tunnel UPS)";
            else if (!r.ok) { noSupply = true; target = 0; }
        } else { supply = GridFeed.draw(world, pos, 0).what; onEbus = false; }
        // spin up / coast down (direction change: through zero)
        double step = 0.12;
        if (Math.signum(target) != Math.signum(speed) && Math.abs(speed) > 0.01) speed = Math.abs(speed) < step ? 0 : speed - Math.signum(speed) * step;
        else speed = speed + Math.max(-step, Math.min(step, target - speed));
        kw = 37 * Math.pow(Math.abs(speed), 3);
        if (running() && world instanceof WorldServer ws) {
            EnumFacing f = axis();
            double sgn = speed > 0 ? 1 : -1;                             // forward blows out of the axis end
            ws.spawnParticle(EnumParticleTypes.CLOUD, pos.getX() + 0.5 + f.getXOffset() * sgn * 1.6, pos.getY() + 0.5, pos.getZ() + 0.5 + f.getZOffset() * sgn * 1.6,
                    (int) (1 + Math.abs(speed) * 3), 0.2, 0.2, 0.2, 0.02);
        }
        put("run_f", speed > 0.05);
        put("run_r", speed < -0.05);
        put("fault", faulty());
        put("sup", !noSupply && !supply.startsWith("no grid") && !supply.startsWith("grid supply"));
        put("pwr", powered());
        put("g_rpm", (int) Math.round(Math.abs(speed) * 1480));
        text("kw", String.format(Locale.ROOT, "%.1f KW", kw));
        text("rpm", String.format(Locale.ROOT, "%s %4d RPM", speed > 0.05 ? "FWD" : speed < -0.05 ? "REV" : "STP", Math.round(Math.abs(speed) * 1480)));
        text("amps", String.format(Locale.ROOT, "%.0f A", kw * 1000 / (Math.sqrt(3) * 400 * 0.86)));
        text("id", id());
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("test")) return lampTest();
        if (id.equals("s_local")) {
            ctl.set("s_local", (ctl.get("s_local") + 1) % 4);
            return "§eLocal isolator: " + new String[]{"REMOTE (tunnel control)", "OFF - isolated", "HAND FORWARD", "HAND REVERSE"}[ctl.get("s_local")];
        }
        return null;
    }

    @Override protected void save(NBTTagCompound t) { t.setDouble("speed", speed); }
    @Override protected void load(NBTTagCompound t) { speed = t.getDouble("speed"); }
}
