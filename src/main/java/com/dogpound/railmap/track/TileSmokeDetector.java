package com.dogpound.railmap.track;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;
import java.util.Map;

/**
 * Tunnel smoke + CO detector (her ChatGPT list #16): an aspirating smoke detector and CO cell in a stainless case on the
 * tunnel wall. It looks for fire around it (fire, lava, burning mobs / players / items within 10 blocks) and reads the
 * tunnel control panel's air (smoke, CO). Over the threshold it goes into ALARM, which puts the tunnel into emergency.
 * TEST puts test smoke into it (it really alarms - ISOLATE it first if you don't want the tunnel to go into emergency).
 * No 12 V = no detection (FAULT on the panel).
 */
public class TileSmokeDetector extends TrackGear {
    private double fire, obsc, coPpm;
    private int testSecs;
    private boolean alarm;

    public String id() { return "SD" + Math.floorMod(pos.getX() * 3 + pos.getZ() * 11 + pos.getY(), 100); }
    @Override public String wiredName() { return "Tunnel smoke detector " + id(); }
    public boolean alarm() { return alarm && powered(); }
    public boolean isolated() { return ctl.get("s_iso") == 1; }
    public double fireLoad() { return powered() ? fire : 0; }

    @Override public String gearState() { return !powered() ? "FAULT - no 12 V, not detecting" : (alarm ? "ALARM" : "normal") + (isolated() ? " (ISOLATED)" : "") + String.format(Locale.ROOT, " · %.1f %%/m · CO %.0f ppm", obsc, coPpm); }
    @Override public int wiredLamp() { return !powered() || alarm ? 3 : isolated() ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("alarm", alarm); m.put("obscuration", Math.round(obsc * 10) / 10.0); m.put("coPpm", Math.round(coPpm)); m.put("isolated", isolated()); }

    @Override
    protected void tickGear() {
        if (age % 20 != 0) return;
        fire = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        {
            for (int dx = -8; dx <= 8; dx++) for (int dy = -4; dy <= 3; dy++) for (int dz = -8; dz <= 8; dz++) {
                m.setPos(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                Material mt = world.getBlockState(m).getMaterial();
                if (mt == Material.FIRE) fire += 1;
                else if (mt == Material.LAVA) fire += 0.5;
            }
            for (Entity e : world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(pos).grow(10, 5, 10))) if (e.isBurning()) fire += 0.7;
        }
        TileTunnelControl c = TileTunnelControl.of(world, pos);
        double zoneSmoke = c == null ? 0 : c.smokeAt(pos);                 // next-48 #29: the air in its own section
        coPpm = c == null ? 0 : c.coAt(pos);
        if (testSecs > 0) { testSecs--; fire = Math.max(fire, 2); }
        obsc = Math.min(20, fire * 2.2 + zoneSmoke);
        double thr = new double[]{4.0, 2.0, 1.0}[Math.min(2, ctl.get("s_sens"))];
        boolean was = alarm;
        alarm = powered() && obsc >= thr;
        if (alarm && !was) {
            Recorder.event(world, "SMOKE DETECTOR " + id() + " ALARM" + (testSecs > 0 ? " (TEST)" : "") + String.format(Locale.ROOT, " %.1f %%/m", obsc) + (isolated() ? " - isolated" : ""));
            world.playSound(null, pos, com.dogpound.railmap.ModSounds.FIRE_BELL, SoundCategory.BLOCKS, 0.8f, 1.0f);
            if (c == null && !isolated()) AlarmManager.get(world).raise(world, "sd:" + pos.toLong(), AlarmManager.Cat.FIRE, AlarmManager.Sev.CRITICAL, "SMOKE DETECTOR " + id() + " ALARM at " + Rails.xz(pos) + " (no tunnel control panel covers it)", pos, true);
        }
        if (!alarm) AlarmManager.get(world).clear("sd:" + pos.toLong());
        put("alarm", alarm);
        put("fault", !powered() || isolated());
        put("pwr", powered());
        put("g_obs", (int) Math.round(Math.min(10, obsc) * 10));
        text("obsc", String.format(Locale.ROOT, "%.1f %%/M", obsc));
        text("co", String.format(Locale.ROOT, "CO %.0f PPM", coPpm));
        text("id", id() + (isolated() ? " ISOL" : ""));
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("test")) { testSecs = 12; return isolated() ? "§eTest smoke in (detector ISOLATED - panel won't go to emergency)" : "§cTest smoke in - NOT isolated: this will start a tunnel emergency!"; }
        if (id.equals("lamp")) return lampTest();
        return null;
    }

    @Override protected void save(NBTTagCompound t) { t.setBoolean("alarm", alarm); }
    @Override protected void load(NBTTagCompound t) { alarm = t.getBoolean("alarm"); }
}
