package com.dogpound.railmap.steam;

import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import com.dogpound.railmap.wire.IWiredGear;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.*;

/** the Control Center's DEPOT > Steam page rows: SB boiler · SP servicing plant · SL log */
public final class SteamConsole {
    private SteamConsole() {}

    public static void fill(World w, List<String[]> out) {
        try {
            SteamData d = SteamData.get(w);
            Set<UUID> live = new HashSet<>();
            for (LocomotiveSteam l : cam72cam.mod.world.World.get(w).getEntities(LocomotiveSteam.class)) {
                if (l.isDead()) continue;
                live.add(l.getUUID());
                SteamData.Boiler b = d.of(l.getUUID());
                out.add(new String[]{"SB", Stock.name(l), String.format(Locale.ROOT, "%.1f", l.getBoilerPressureBar()), l.getPercentLiquidFull() + "%",
                        String.format(Locale.ROOT, "%.2f", b.scaleMm), String.valueOf(Math.round(SteamTicker.hardness(d, l))), Math.round(b.ashKg * 100 / SteamConfig.ashPanKg) + "%",
                        "+" + Math.round(SteamTicker.loss(b) * 100) + "%", String.format(Locale.ROOT, "%.1f", b.steamH), String.valueOf(b.washouts),
                        b.leak ? "TUBES LEAKING" : b.scaleMm >= SteamConfig.washoutDueMm ? "WASHOUT DUE" : b.ashKg >= SteamConfig.ashPanKg ? "ASH PAN FULL" : "OK"});
            }
            for (TileEntity t : new ArrayList<>(w.loadedTileEntityList))
                if ((t instanceof TileCoalingTower || t instanceof TileCoalPit || t instanceof TileWaterColumn || t instanceof TileAshPit || t instanceof TileSandTower || t instanceof TileBoilerWashout) && !t.isInvalid()) {
                    IWiredGear g = (IWiredGear) t;
                    out.add(new String[]{"SP", g.wiredName(), t.getPos().getX() + "," + t.getPos().getZ(), g.gearState()});
                }
            int n = 0;
            for (String s : d.log) { if (n++ > 12) break; out.add(new String[]{"SL", s}); }
        } catch (LinkageError | RuntimeException ignored) { }
    }
}
