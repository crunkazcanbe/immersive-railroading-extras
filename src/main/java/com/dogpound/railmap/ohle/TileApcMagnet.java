package com.dogpound.railmap.ohle;

import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Collection;

/**
 * Pantograph sign + track magnet (her ChatGPT list #32: "automatic lowering when entering a designated section"): the
 * lineside sign drivers read and the permanent magnet between the rails a loco's pick-up reads. LOWER PAN starts a
 * pantograph-down zone (low bridges, depots, wash plants, the end of the wire), RAISE PAN ends it, COAST opens the main
 * breaker for 4 s (an APC magnet). A loco in AUTO acts on it by itself; one in MANUAL only gets the warning. Magnets
 * work without power; the sign's lamp needs 12 V.
 */
public class TileApcMagnet extends SafetyTile {
    static final Reg<TileApcMagnet> REG = new Reg<>();

    public static Collection<TileApcMagnet> all(net.minecraft.world.World w) { return REG.all(w); }

    static final String[] MODES = {"LOWER PAN", "RAISE PAN", "COAST"};
    private int passes;

    @Override public void onLoad() { REG.add(this); }
    @Override public void invalidate() { super.invalidate(); REG.remove(this); }
    @Override public void onChunkUnload() { REG.remove(this); }

    public int mode() { return ctl.get("s_mode") % 3; }

    @Override public String wiredName() { return "Pantograph magnet " + Ohle.xz(pos); }
    @Override public String gearState() { return MODES[mode()] + " · " + passes + " trains"; }
    @Override public int wiredLamp() { return powered() ? 1 : 2; }
    @Override protected boolean keepTextUnpowered() { return true; }     // the sign is painted

    /** a loco's pick-up passed over the magnet: returns the cab message */
    String passed(LocomotiveDiesel loco, OhleData.Pan p, long now) {
        passes++;
        markDirty();
        if (p.manual) return "§e⚠ " + MODES[mode()] + " sign - your pantograph is in MANUAL (/irextras ohle pan up|down)";
        switch (mode()) {
            case 0: p.downZone = true; return "§e" + MODES[0] + " - pantograph lowered, coasting";
            case 1: p.downZone = false; return "§a" + MODES[1] + " - pantograph raising";
            default: p.vcb = false; p.vcbAt = now + 60; return "§eAPC magnet - main breaker open, coasting 4 s";
        }
    }

    @Override
    protected void serverTick() {
        if (age % 20 != 7) return;
        text("legend", MODES[mode()]);
        put("lit", powered());
        put("count", passes);
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("s_mode")) {
            if (who == null) return "§cSet the sign at the track";
            ctl.set("s_mode", (mode() + 1) % 3);
            return "§eSign + magnet: " + MODES[mode()];
        }
        return "§7" + gearState();
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("passes", passes); }
    @Override protected void load(NBTTagCompound t) { passes = t.getInteger("passes"); }
}
