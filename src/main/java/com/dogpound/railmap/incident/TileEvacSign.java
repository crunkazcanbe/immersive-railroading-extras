package com.dogpound.railmap.incident;

import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.Map;

/**
 * Emergency Exit Sign + emergency lighting (her ChatGPT list #70): a maintained EXIT sign (lit all the time, the arrow
 * set with its ARROW switch) with two emergency spotlights and a sealed battery. On 12 V the battery charges; when the
 * 12 V goes (a mains failure) the sign and both spots run on the battery for about 20 minutes - and so do they during
 * an evacuation near it, with the sounder whooping. It really lights the area (block light). TEST is the monthly
 * functional test: 30 s on battery. Battery flat = FAULT lamp; it goes dark.
 */
public class TileEvacSign extends SafetyTile {
    private float batt = 100;
    private long testUntil;
    private int lightNow = -1;
    static final String[] ARROWS = {"EXIT", "< EXIT", "EXIT >", "EXIT ^", "EXIT V"};

    @Override public String wiredName() { return "Emergency exit sign"; }

    public boolean evac() { return IncidentOps.evacAt(world, pos, 16) != null; }

    /** what lights: 0 dark, 1 sign only, 2 sign + spots */
    public int lit() {
        boolean test = world.getTotalWorldTime() < testUntil;
        boolean onBatt = !powered() || test;
        if (onBatt && batt <= 0) return 0;
        return !powered() || test || evac() ? 2 : 1;
    }

    /** block light for BlockIncident */
    public int light() { return ctl.get("spotl") == 1 ? 15 : !ctl.text("exit").isEmpty() ? 7 : 0; }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager n, net.minecraft.network.play.server.SPacketUpdateTileEntity p) {
        int before = light();
        super.onDataPacket(n, p);
        if (world != null && light() != before) world.checkLight(pos);
    }

    @Override
    public String gearState() {
        return (powered() ? "maintained, charging" : batt > 0 ? "MAINS FAILED - on battery" : "DARK - battery flat") + String.format(" · battery %.0f %%", batt) + (evac() ? " · EVACUATION" : "");
    }

    @Override public int wiredLamp() { return batt < 20 ? 3 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("battery", Math.round(batt)); m.put("lit", lit()); }

    // lamps here run on the sign's own battery: they show without 12 V
    @Override public int controlValue(String id) { return ctl.get(id); }
    @Override protected boolean keepTextUnpowered() { return true; }

    @Override
    protected void serverTick() {
        boolean test = world.getTotalWorldTime() < testUntil;
        int l = lit();
        if (age % 20 == 0) {
            if (powered() && !test && l < 2) batt = Math.min(100, batt + 0.5f);
            else if (l > 0 && (!powered() || test)) batt = Math.max(0, batt - 100f / (20 * 60) * (l == 2 ? 1f : 0.5f));
        }
        if (l == 2 && evac() && age % 60 == 0) world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("alarm.evac"), SoundCategory.BLOCKS, 1.0f, 1.0f);
        put("spotl", l == 2);
        put("spotr", l == 2);
        put("charge", powered() && batt < 100 && !test);
        put("fault", batt < 20);
        put("battp", Math.round(batt));
        text("exit", l == 0 ? "" : ARROWS[Math.floorMod(ctl.get("s_arrow"), ARROWS.length)]);
        if (l != lightNow) { lightNow = l; world.checkLight(pos); }
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("test")) { testUntil = world.getTotalWorldTime() + 600; return "§7Functional test: 30 s on battery"; }
        return null;
    }

    @Override protected void save(NBTTagCompound t) { t.setFloat("batt", batt); }
    @Override protected void load(NBTTagCompound t) { batt = t.hasKey("batt") ? t.getFloat("batt") : 100; }
}
