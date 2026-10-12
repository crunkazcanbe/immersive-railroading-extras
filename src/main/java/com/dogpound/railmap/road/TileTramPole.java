package com.dogpound.railmap.road;

import com.dogpound.railmap.ohle.Ohle;
import com.dogpound.railmap.safety.SafetyTile;
import com.dogpound.railmap.signal.BlockCatenary;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/**
 * Tramway OHLE street pole (batch 5 #4): the street-side pole with its bracket arm that carries the tram's contact wire
 * over the road (hang Contact Wire from the arm, feed it with a Track Feeder / 600 V DC as for any overhead line), and
 * the tramway section indicator real tramways have at section ends: LINE LIVE (green) / DEAD (flashing red) for the
 * overhead line section on the wire within 3 blocks of the arm, from the OHLE section model (feeders, insulators,
 * isolators, earths, faults). Trams in tram mode also stop short of a dead section by themselves. 12 V for the lamps.
 */
public class TileTramPole extends SafetyTile {
    private String state = "";

    @Override public String wiredName() { return "Tramway pole " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return !powered() ? "NO 12 V - indicator dark" : state.isEmpty() ? "no contact wire at the arm" : state; }

    @Override
    protected void serverTick() {
        if (age % 20 != 11) return;
        Ohle.Section sec = null;
        search:
        for (int dy = -1; dy <= 6; dy++)
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos c = pos.add(dx, dy, dz);
                    if (world.isBlockLoaded(c) && world.getBlockState(c).getBlock() instanceof BlockCatenary k && k.kind() == BlockCatenary.Kind.WIRE) { sec = Ohle.section(world, c); break search; }
                }
        boolean live = sec != null && !sec.faulted && !sec.earthed && (sec.legacy() || sec.energised);
        state = sec == null ? "" : sec.name() + " " + sec.state();
        put("live", live);
        put("dead", sec != null && !live);
        text("sec", sec == null ? "NO WIRE" : sec.state().toUpperCase(java.util.Locale.ROOT));
    }

    @Override public String controlPress(String id, EntityPlayer who) { return "§7" + gearState(); }
    @Override protected void save(NBTTagCompound t) {}
    @Override protected void load(NBTTagCompound t) {}
}
