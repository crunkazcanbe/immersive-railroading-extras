package com.dogpound.railmap.station;

import com.dogpound.railmap.depot.DepotTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Platform Lamp column (her ChatGPT list #85, lighting): a lantern on a column that really lights the platform (light
 * 15) - AUTO switches it on by its photocell (dusk to dawn, and in storms), ON / OFF by hand. Needs mains for the lamp
 * and 12 V for the control gear. Lit platforms count for the station's facilities; it costs its power.
 */
public class TilePlatformLamp extends DepotTile implements IStationFacility, com.dogpound.railmap.light.ILamp {
    private boolean lit;
    private boolean shown = true;                                   // night lighting: false while a failing lamp flickers off
    private double health;                                          // asset register (light.LampHealth): 0 ok, >0 flickering, >=1 failed
    private long lastLight = -10000;

    @Override public String wiredName() { return "Platform lamp"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return lit ? 3 : 0; }
    @Override public String gearState() { return lit ? "lit" : "off"; }
    @Override public String facilityKind() { return "Platform lighting"; }
    @Override public boolean facilityWorking() { return powered() && mainsOk(); }
    @Override public int facilityPoints() { return lit || !dark() ? 2 : 0; }
    @Override public long dailyCost() { return 25; }
    public boolean lit() { return lit; }

    boolean dark() { long t = world.getWorldTime() % 24000; return t > 12500 && t < 23500 || world.isThundering(); }

    @Override protected String press(String id, EntityPlayer who) { return null; }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        int mode = Math.floorMod(ctl.get("s_mode"), 3);
        int cmd = com.dogpound.railmap.light.LightingBus.command(world, pos);   // a lighting contactor / photocell controller on its wire
        boolean want = facilityWorking() && (mode == 1 || mode == 0 && (cmd >= 0 ? cmd == 1 : dark()) && !PlatformOps.lampOff(world, pos));   // night mode of a closed station (batch 3 #33)
        if (age % 200 == 0) health = com.dogpound.railmap.light.LampHealth.of(world, pos);
        boolean vis = com.dogpound.railmap.light.LampHealth.visible(health, want, world.rand);
        boolean steady = want != lit;
        if ((want != lit || vis != shown) && com.dogpound.railmap.light.LampHealth.mayChange(world.getTotalWorldTime(), lastLight, steady || health == 0)) {
            lit = want; shown = vis; lastLight = world.getTotalWorldTime(); world.checkLight(pos); controlsChanged();
        }
        put("pwr", powered()); put("mainsl", mains.ok()); put("litl", lit && shown); put("cell", dark());
    }

    @Override public boolean lampOn() { return lit && shown; }
    @Override public boolean lampFailed() { return health >= 1; }
    @Override public String lampName() { return "Platform lamp " + pos.getX() + "," + pos.getZ(); }
    @Override public double[][] haloPoints() { return new double[][]{{8, 24.1, 8, 6, 0xFFE8B0}}; }
    @Override public void onLoad() { super.onLoad(); if (world != null && world.isRemote) com.dogpound.railmap.light.LampRegistry.add(this); }
    @Override public void invalidate() { super.invalidate(); com.dogpound.railmap.light.LampRegistry.remove(this); }
    @Override public void onChunkUnload() { super.onChunkUnload(); com.dogpound.railmap.light.LampRegistry.remove(this); }

    @Override protected void saveExtra(NBTTagCompound t) { t.setBoolean("lit", lit); t.setBoolean("shown", shown); t.setFloat("health", (float) health); }
    @Override protected void loadExtra(NBTTagCompound t) {
        boolean was = lampOn();
        lit = t.getBoolean("lit"); shown = !t.hasKey("shown") || t.getBoolean("shown"); health = t.getFloat("health");
        if (world != null && world.isRemote && was != lampOn()) world.checkLight(pos);
    }
}
