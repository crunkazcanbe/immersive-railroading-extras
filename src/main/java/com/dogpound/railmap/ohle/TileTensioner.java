package com.dogpound.railmap.ohle;

import com.dogpound.railmap.safety.SafetyTile;
import com.dogpound.railmap.weather.Weather;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Collection;

/**
 * Balance-weight tensioner (her ChatGPT list #32: wire tension): a pulley wheel and a stack of cast-iron weights on the
 * mast at the end of a tension length, holding the contact wire at a constant 15 kN whatever the temperature. In heat
 * the wire grows and the weights sink; in cold they rise. Within its travel the tension holds; past either end
 * (weights grounded / at the pulley) or with the maintenance lock in, the wire behaves like a fixed termination: slack
 * and sagging in heat (poor contact at speed), tight in cold. Purely mechanical - the scale and dials need no power.
 * Feeds the section whose wire is within 4 blocks.
 */
public class TileTensioner extends SafetyTile {
    static final Reg<TileTensioner> REG = new Reg<>();

    public static Collection<TileTensioner> all(net.minecraft.world.World w) { return REG.all(w); }

    @Override public void onLoad() { REG.add(this); }
    @Override public void invalidate() { super.invalidate(); REG.remove(this); Ohle.dirty(); }
    @Override public void onChunkUnload() { REG.remove(this); }

    /** weight height on its scale, % of travel: 50 at 10 °C, sinking 1.4 % per °C warmer */
    public double travel() { return 50 - (Weather.airC(world, pos) - 10) * 1.4; }
    public boolean locked() { return ctl.get("k_lock") == 1; }
    public boolean holding() { double t = travel(); return !locked() && t > 2 && t < 98; }
    public double tensionN() { return 15000; }

    @Override public String wiredName() { return "Balance-weight tensioner " + Ohle.xz(pos); }
    @Override public String gearState() { double t = travel(); return locked() ? "LOCKED (fixed termination)" : t <= 2 ? "WEIGHTS GROUNDED - wire slack" : t >= 98 ? "WEIGHTS AT THE PULLEY - wire over-tight" : "holding 15 kN, weights at " + Math.round(t) + "%"; }
    @Override public int wiredLamp() { return holding() ? 1 : 2; }

    /** mechanical: the scale, the spring balance and the thermometer read without power */
    @Override public int controlValue(String id) { return ctl.get(id); }
    /** nothing on it needs power (self-tests and the WIRED page must not call it dead) */
    @Override public boolean powered() { return true; }
    @Override public String ptext(String key) { return ctl.text(key); }

    @Override
    protected void serverTick() {
        if (age % 40 != 5) return;
        double t = travel(), air = Weather.airC(world, pos);
        double kn = holding() ? 15.0 : Math.max(2, Math.min(24, 12 - 0.218 * (air - 10)));
        put("pos", (int) Math.round(Math.max(0, Math.min(100, t))));
        put("kn", (int) Math.round(kn));
        put("temp", (int) Math.round(air + 20));                // dial runs -20..+60
        put("flag", holding() ? 0 : 1);
        text("state", locked() ? "LOCKED" : t <= 2 ? "GROUNDED" : t >= 98 ? "AT PULLEY" : "FREE");
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("k_lock")) {
            if (who == null) return "§cThe lock is on the mast";
            ctl.set("k_lock", 1 - ctl.get("k_lock"));
            Ohle.dirty();
            OhleData.get(world).log(world, "tensioner " + Ohle.xz(pos) + (locked() ? " LOCKED" : " released") + " by " + who.getName());
            return locked() ? "§eWeights locked - the wire is now a fixed termination (sags in heat)" : "§aWeights free - constant tension";
        }
        return "§7" + gearState();
    }

    @Override protected void save(NBTTagCompound t) {}
    @Override protected void load(NBTTagCompound t) {}
}
