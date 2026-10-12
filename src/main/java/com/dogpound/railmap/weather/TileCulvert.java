package com.dogpound.railmap.weather;

import com.dogpound.railmap.ohle.Reg;
import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Box culvert with headwall and trash screen (her ChatGPT list #64: "culverts and pumps remove water at different
 * rates", "drainage upgrades reduce future disruption"). Passive: takes 1.5 mm of water out of its cell every 5 s while
 * there is water to take. Storms wash debris onto the screen and it blocks up; clear the screen (click it) to get the
 * flow back. No power - the level board and flow flag are mechanical.
 */
public class TileCulvert extends SafetyTile {
    static final Reg<TileCulvert> REG = new Reg<>();
    private double blocked;

    @Override public void onLoad() { REG.add(this); }
    @Override public void invalidate() { super.invalidate(); REG.remove(this); }
    @Override public void onChunkUnload() { REG.remove(this); }

    double capacity() { return 1.5 * (1 - blocked); }
    // ---- drainage network monitoring (infra #43)
    public static java.util.Collection<TileCulvert> all(net.minecraft.world.World w) { return REG.all(w); }
    public double blocked() { return blocked; }
    public double flowMm() { return capacity(); }
    /** a possession gang cleared the trash screen (infra #45) */
    public void cleared(String who) { WeatherData.get(world).log(world, "culvert screen cleared at " + pos.getX() + "," + pos.getZ() + " by " + who + " (" + Math.round(blocked * 100) + "% blocked)"); blocked = 0; put("block", 0); markDirty(); }

    @Override public String wiredName() { return "Culvert " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return "screen " + Math.round(blocked * 100) + "% blocked, " + String.format(java.util.Locale.ROOT, "%.1f", capacity()) + " mm / 5 s"; }
    @Override public int controlValue(String id) { return ctl.get(id); }
    @Override public boolean powered() { return true; }                  // passive: nothing to power
    @Override public int wiredLamp() { return blocked > 0.5 ? 2 : 1; }
    @Override public String ptext(String key) { return ctl.text(key); }

    @Override
    protected void serverTick() {
        if (age % 100 != 41) return;
        WeatherData.Cell c = Weather.cell(world, pos);
        if (c.precip >= 2 && !c.snowing) blocked = Math.min(0.9, blocked + (c.precip == 3 ? 0.02 : 0.004));
        put("flow", c.water > 1 ? 1 : 0);
        put("level", (int) Math.min(100, c.water / 3));
        put("block", (int) Math.round(blocked * 100));
        markDirty();
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("screen")) {
            if (who == null) return "§cThe screen is cleared by hand";
            int was = (int) Math.round(blocked * 100);
            blocked = 0;
            put("block", 0);
            WeatherData.get(world).log(world, "culvert screen cleared at " + pos.getX() + "," + pos.getZ() + " by " + who.getName() + " (" + was + "% blocked)");
            return "§aScreen cleared (" + was + "% blocked) - full flow";
        }
        return "§7" + gearState();
    }

    @Override protected void save(NBTTagCompound t) { t.setDouble("blocked", blocked); }
    @Override protected void load(NBTTagCompound t) { blocked = t.getDouble("blocked"); }
}
