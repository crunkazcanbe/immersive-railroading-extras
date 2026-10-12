package com.dogpound.railmap.track;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Locale;
import java.util.Map;

/**
 * Tunnel luminaire (her ChatGPT list #16 + Requested "tunnel lights that really emit light"): a sealed LED light on the
 * tunnel wall that lights the block around it for real. On 12 V it is on (the tunnel control panel can switch the
 * tunnel lights OFF / NORMAL / BOOST, and an emergency drives them to full); with no 12 V it drops to its emergency
 * battery (dimmer) for 3 hours, then goes dark. The battery recharges while powered. The green dot is the battery
 * healthy LED; TEST runs a battery-duration test (pretends the supply failed for 10 s).
 */
public class TileTunnelLight extends TrackGear implements TrackBlock.Lit, com.dogpound.railmap.light.ILamp {
    private static final int BATT_SECS = 3 * 3600;
    private int battery = BATT_SECS, level, testSecs;
    private double health;                                          // night lighting: asset register (light.LampHealth)
    private long lastLight = -10000;

    @Override public String wiredName() { return "Tunnel light " + Math.floorMod(pos.getX() * 5 + pos.getZ() * 3 + pos.getY(), 1000); }
    @Override public int light() { return level; }
    @Override public int lampCircuit() { return 0; }                // tunnel lights follow the tunnel control panel, not a lighting circuit
    @Override public boolean lampOn() { return level > 0; }
    @Override public boolean lampFailed() { return health >= 1; }
    @Override public String lampName() { return wiredName(); }
    private boolean mainsOk() { return powered() && testSecs == 0; }

    @Override public String gearState() { return level == 0 ? (powered() ? "switched off" : "DARK - no 12 V, battery flat") : mainsOk() ? "lit" : String.format(Locale.ROOT, "ON EMERGENCY BATTERY %d%%", 100 * battery / BATT_SECS); }
    @Override public int wiredLamp() { return !powered() ? 3 : battery < BATT_SECS / 2 ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("light", level); m.put("batteryPct", 100 * battery / BATT_SECS); }

    @Override
    protected void tickGear() {
        if (age % 20 != 0) return;
        if (testSecs > 0) testSecs--;
        int want;
        TileTunnelControl c = TileTunnelControl.of(world, pos);
        if (mainsOk()) {
            int mode = c == null ? 1 : c.lightMode();
            want = mode == 0 ? 0 : mode == 2 ? 15 : 13;
            battery = Math.min(BATT_SECS, battery + 3);
        } else if (battery > 0) { battery--; want = 8; }
        else want = 0;
        if (age % 200 == 0) health = com.dogpound.railmap.light.LampHealth.of(world, pos);
        boolean steady = (want > 0) != (level > 0) && health == 0;
        if (!com.dogpound.railmap.light.LampHealth.visible(health, want > 0, world.rand)) want = 0;   // a failing driver flickers, a failed one is dark
        if (want != level && com.dogpound.railmap.light.LampHealth.mayChange(world.getTotalWorldTime(), lastLight, steady || health == 0)) {
            level = want; lastLight = world.getTotalWorldTime(); world.checkLight(pos); controlsChanged();
        }
        put("lens", level > 0 ? (level >= 15 ? 2 : 1) : 0);
        put("batt", battery > BATT_SECS / 10 ? 1 : 0);
        put("chg", mainsOk() && battery < BATT_SECS);
        text("pct", (100 * battery / BATT_SECS) + "%");
    }

    /** the lens and the battery LED glow on battery too (controlValue would blank them with no 12 V) */
    @Override public int controlValue(String id) { return ctl.get(id); }
    @Override protected boolean keepTextUnpowered() { return true; }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("test")) { testSecs = 10; return "§eBattery test: mains cut for 10 s"; }
        return "§7" + gearState();
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("batt", battery); t.setInteger("lvl", level); }
    @Override protected void load(NBTTagCompound t) { battery = t.hasKey("batt") ? t.getInteger("batt") : BATT_SECS; int old = level; level = t.getInteger("lvl"); if (world != null && world.isRemote && old != level) world.checkLight(pos); }
}
