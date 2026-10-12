package com.dogpound.railmap.infra;

import com.dogpound.railmap.grid.Elec;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.List;
import java.util.Locale;

/**
 * OHLE Recording Station (#44): the rack where the catenary inspection vehicle's measurements land - a recorder chassis,
 * two monitors (height / stagger and wear traces), a printer and a telemetry receiver. It shows the newest run live:
 * car and speed, metres measured, height range, worst stagger and wear, exceedances. LIMITS picks the class the NEXT run
 * is judged by (NEW RUN arms it and closes the current one), END RUN closes it, ORDERS raises every exceedance of the
 * shown run as a work order at its place, PRINT hands over the run report. Runs on 120 / 240 V AC (a grid cable at that
 * level touching it, 300 W) and 12 V control power.
 */
public class TileOhleRecStation extends InfraTile {
    public TileOhleRecStation() { super(new Supply(Elec.Level.AC120, Elec.Level.AC240)); }

    @Override public String wiredName() { return "OHLE recording station " + pos.getX() + "," + pos.getZ(); }

    private InfraData.Run cur() { List<InfraData.Run> l = InfraData.get(world).runs; return l.isEmpty() ? null : l.get(0); }

    @Override
    public String gearState() {
        InfraData.Run r = cur();
        return mains.status().replaceAll("§.", "") + (r == null ? " · no runs yet" : String.format(Locale.ROOT, " · run #%d %s %.0f m %d exceedances%s", r.id, r.vehicle, r.metres, r.exceed, r.end == 0 ? " (recording)" : ""));
    }

    @Override public int wiredLamp() { InfraData.Run r = cur(); return !live() ? 3 : r != null && r.exceed > 0 ? 2 : 1; }

    @Override
    protected void serverTick() {
        if (age % 20 == 4) mainsTick(300);
        if (age % 10 != 4) return;
        boolean on = live();
        InfraData.Run r = cur();
        put("rec", on && r != null && r.end == 0);
        put("exc", on && r != null && r.exceed > 0);
        put("link", on && r != null && r.end == 0 && world.getTotalWorldTime() - r.start < 999999);
        put("sup", mains.ok); put("pwr", powered());
        text("cls", "LIMITS " + OhleRecording.CLASSES[Math.max(0, Math.min(1, ctl.get("s_cls")))]);
        if (!on) { text("run", mains.ok ? "" : "NO MAINS"); text("veh", ""); text("m", ""); text("h", ""); text("stg", ""); text("wear", ""); text("excd", ""); put("g_h", 0); put("g_s", 0); put("g_w", 0); return; }
        if (r == null) { text("run", "NO RUNS - TAG A CAR OHLE"); return; }
        text("run", String.format(Locale.ROOT, "RUN %d %s", r.id, r.end == 0 ? "REC" : "DONE"));
        text("veh", String.format(Locale.ROOT, "%s %.0f KM/H", r.vehicle.toUpperCase(Locale.ROOT), r.vMax));
        text("m", String.format(Locale.ROOT, "%.0f M MEASURED", r.metres));
        text("h", String.format(Locale.ROOT, "HT %.2f-%.2f M", r.hMin > 50 ? 0 : r.hMin, r.hMax));
        text("stg", String.format(Locale.ROOT, "STAGGER MAX %.2f M", r.stgMax));
        text("wear", String.format(Locale.ROOT, "WEAR MAX %.0f%%", r.wearMax * 100));
        text("excd", "EXCEED " + r.exceed + " " + r.cls);
        put("g_h", (int) Math.round(Math.max(0, Math.min(100, (r.hMax - 4.0) / 3.0 * 100))));
        put("g_s", (int) Math.round(Math.min(100, r.stgMax / 0.6 * 100)));
        put("g_w", (int) Math.round(Math.min(100, r.wearMax * 100)));
    }

    @Override
    public String controlPress(String id, EntityPlayer p) {
        InfraData.Run r = cur();
        switch (id) {
            case "test": return lampTest();
            case "s_cls": return null;
            case "new": {
                if (!live()) return "§cThe station is dead: " + (powered() ? mains.what : "no 12 V");
                if (r != null && r.end == 0) OhleRecording.end(world, r);
                OhleRecording.arm(world, ctl.get("s_cls"));
                return "§aNext run armed with " + OhleRecording.CLASSES[Math.max(0, Math.min(1, ctl.get("s_cls")))] + " limits - run a car tagged OHLE under the wire";
            }
            case "end":
                if (r == null || r.end != 0) return "§7No run recording";
                OhleRecording.end(world, r);
                return "§eRun #" + r.id + " closed";
            case "orders": {
                if (!live()) return "§cThe station is dead";
                if (r == null) return "§7No run";
                int n = OhleRecording.orders(world, r.id);
                return n == 0 ? "§aRun #" + r.id + ": no exceedances, nothing to order" : "§6" + n + " work order(s) raised from run #" + r.id;
            }
            case "print": {
                if (p == null) return "§7Printing is done at the station";
                if (!live()) return "§cThe station is dead";
                if (r == null) return "§7No run to print";
                net.minecraft.item.ItemStack b = com.dogpound.railmap.depot.Diagnostics.book("OHLE run " + r.id, "OHLE recording", OhleRecording.report(world, r));
                if (!p.inventory.addItemStackToInventory(b)) p.dropItem(b, false);
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.6f, 1f);
                return "§aRun #" + r.id + " report printed";
            }
            default: return "§7" + gearState();
        }
    }

    @Override protected void store(NBTTagCompound t) { }
    @Override protected void restore(NBTTagCompound t) { }
}
