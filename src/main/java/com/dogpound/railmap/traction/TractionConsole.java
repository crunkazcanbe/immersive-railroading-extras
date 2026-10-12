package com.dogpound.railmap.traction;

import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import com.dogpound.railmap.server.RailProps;
import com.dogpound.railmap.steam.Stock;
import com.dogpound.railmap.wire.IWiredGear;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.*;

/** the Control Center's DEPOT > Traction page rows: TU battery / hydrogen unit · TP charging + hydrogen plant */
public final class TractionConsole {
    private TractionConsole() {}

    public static void fill(World w, List<String[]> out) {
        try {
            for (LocomotiveDiesel l : cam72cam.mod.world.World.get(w).getEntities(LocomotiveDiesel.class)) {
                int k = Traction.kind(l);
                if (l.isDead() || k == Traction.NONE) continue;
                RailProps.Props pr = RailProps.get(w).of(l.getUUID());
                TractionData.Unit u = Traction.unit(l);
                String src = switch (pr.source) { case 1 -> "WIRE"; case 2 -> k == Traction.H2 ? "FUEL CELL" : "BATTERY"; case 3 -> "CHARGER"; case 4 -> "FLAT"; default -> "idle"; };
                out.add(new String[]{"TU", Stock.name(l), Traction.KIND[k], Math.round(pr.battery * 100) + "%", String.format(Locale.ROOT, "%.0f", Traction.capKwh(l, k)),
                        k == Traction.H2 ? String.format(Locale.ROOT, "%.0f/%.0f", Math.max(0, u.h2kg), Traction.h2CapKg(l)) : "", String.format(Locale.ROOT, "%.0f", u.rangeKm), src,
                        String.format(Locale.ROOT, "%.0f", u.kwNow), String.valueOf(pr.kwRegen), String.format(Locale.ROOT, "%.0f/%.0f/%.0f", u.kwhOut, u.kwhRegen, u.kwhCharged)});
            }
            for (TileEntity t : new ArrayList<>(w.loadedTileEntityList))
                if ((t instanceof TileChargePost || t instanceof H2Station.Part) && !t.isInvalid()) {
                    IWiredGear g = (IWiredGear) t;
                    out.add(new String[]{"TP", g.wiredName(), t.getPos().getX() + "," + t.getPos().getZ(), g.gearState()});
                }
        } catch (LinkageError | RuntimeException ignored) { }
    }
}
