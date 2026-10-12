package com.dogpound.railmap.newrail6;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.dogpound.railmap.block.BlockStationDevice;
import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.infra.Supply;
import com.dogpound.railmap.sound.PackSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import java.util.*;

/**
 * Motorail end-loading ramp (batch 6 #18): a steel ramp at the end of a loading road, hydraulically levelled to the
 * wagon deck, with a hinged bridge plate that drops onto the first wagon, wheel chocks and a control pedestal. 12 V
 * controls, 240 / 480 V hydraulic power pack. The roadway is in front of the pedestal (vehicles stand within ~5 blocks
 * of the spot 4 blocks out); the track end is behind it. ENABLE key, PLATE up / down, CHOCKS, LOAD (drive the vehicle on
 * and chain it to the nearest empty wagon), UNLOAD (drive the nearest loaded wagon's vehicle off onto the roadway), ACK,
 * LAMP TEST, E-STOP. Interlocked: loading and unloading only with the plate down and the chocks off; while the plate is
 * down on a wagon, signals within 16 blocks are held at danger so nothing moves the train.
 */
public class TileMotorailRamp extends NR6Tile {
    static final String[] PAGES = {"OVERVIEW", "WAGONS", "EVENTS"};
    /** bridge plate 0 up .. 100 down on the wagon; deck levelling 0..100 */
    double plate, level;
    int moving;
    boolean chocks;

    public TileMotorailRamp() { super(new Supply(Elec.Level.AC240, Elec.Level.AC480)); }

    @Override public String wiredName() { return "Motorail ramp " + pos.getX() + "," + pos.getZ(); }

    EnumFacing front() { return world.getBlockState(pos).getBlock() instanceof BlockStationDevice ? world.getBlockState(pos).getValue(BlockStationDevice.FACING) : EnumFacing.NORTH; }

    boolean plateDown() { return plate >= 99.9; }

    @Override
    public String gearState() {
        return mains.status().replaceAll("§.", "") + " · plate " + (plateDown() ? "DOWN" : plate <= 0.1 ? "UP" : f0(plate) + "%") + (chocks ? " · chocks on" : "")
                + (Motorail.ivPresent() ? "" : " · Immersive Vehicles not installed");
    }

    @Override public int wiredLamp() { return !powered() ? 3 : !mains.ok ? 2 : 1; }

    @Override
    protected void serverTick() {
        if (age % 20 == 9) mainsTick(moving != 0 ? 7500 : 300);
        boolean estop = ctl.get("m_estop") == 1;
        if (moving != 0) {
            if (estop || !live()) { moving = 0; event(estop ? "PLATE STOPPED - E-STOP" : "PLATE STOPPED - " + mains.what); }
            else {
                plate = Math.max(0, Math.min(100, plate + moving * 1.0));
                level = Math.max(0, Math.min(100, level + moving * 1.2));
                if (age % 25 == 0) PackSounds.play(world, pos, "machine.pump", 0.5f, 1.1f);
                if (plate <= 0 || plate >= 100) { moving = 0; event(plate >= 100 ? "PLATE DOWN ON WAGON" : "PLATE UP"); }
            }
        }
        if (age % 5 != 0) return;
        boolean on = powered();
        NR6Data d = NR6Data.get(world);
        List<Entity> road = Motorail.ivPresent() && age % 20 == 0 ? Motorail.onRoad(world, pos, front()) : null;
        if (road != null) roadCount = road.size();
        if (age % 20 == 0) {
            EntityRollingStock e = Motorail.wagon(world, pos, false), l = Motorail.wagon(world, pos, true);
            empty = e == null ? "" : Ferry.label(e);
            loaded = l == null ? "" : Ferry.label(l) + ": " + d.loads.get(l.getUUID()).label;
        }
        put("l_pwr", on);
        put("l_main", on && mains.ok);
        put("l_plate", on && plateDown());
        put("l_up", on && plate <= 0.1);
        put("l_chock", on && chocks);
        put("l_veh", on && roadCount > 0);
        put("l_car", on && (!empty.isEmpty() || !loaded.isEmpty()));
        put("l_busy", on && moving != 0);
        put("l_flt", on && (!mains.ok || estop));
        put("l_iv", on && Motorail.ivPresent());
        put("g_plate", (int) Math.round(plate));
        put("g_level", (int) Math.round(level));
        switch (page(PAGES.length)) {
            case 0 -> screen("MOTORAIL RAMP " + (ctl.get("k_en") == 1 ? "IN USE" : "LOCKED"),
                    Motorail.ivPresent() ? "VEHICLES ON ROAD " + roadCount : "NO IMMERSIVE VEHICLES",
                    "PLATE " + (plateDown() ? "DOWN" : plate <= 0.1 ? "UP" : moving != 0 ? "MOVING" : f0(plate) + "%") + (chocks ? "  CHOCKS ON" : "  CHOCKS OFF"),
                    empty.isEmpty() ? "NO EMPTY WAGON" : "EMPTY " + empty,
                    loaded.isEmpty() ? "NO LOADED WAGON" : "LOADED " + loaded,
                    plateDown() ? "SIGNALS HELD" : "SIGNALS FREE");
            case 1 -> {
                String[] o = new String[6];
                o[0] = "VEHICLES ON WAGONS " + d.loads.size();
                int i = 1;
                for (NR6Data.Load ld : d.loads.values()) { if (i >= 6) break; o[i++] = ld.label; }
                if (i == 1) o[1] = "NONE";
                screen(o);
            }
            default -> screen(logLines("EVENTS", 6));
        }
    }

    private int roadCount;
    private String empty = "", loaded = "";

    @Override
    public void holds(Map<Long, String> hold, List<Object[]> zones) {
        if (plate <= 0.1) return;
        String why = "motorail ramp " + pos.getX() + "," + pos.getZ() + ": bridge plate down on the wagons";
        zones.add(new Object[]{pos.getX() + 0.5, pos.getZ() + 0.5, 16.0, why});
    }

    @Override
    public String controlPress(String id, EntityPlayer p) {
        if (id.equals("test")) return lampTest();
        if (id.startsWith("s_") || id.startsWith("k_") || id.startsWith("m_")) return null;
        if (!powered()) return "§cPedestal dark - no 12 V";
        if (id.equals("ack")) return "§7Acknowledged";
        if (ctl.get("k_en") != 1) return "§cTurn the ENABLE key first";
        if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
        switch (id) {
            case "plate" -> {
                if (!live()) return "§cPower pack has no mains: " + mains.what;
                if (chocks && plateDown()) return "§cTake the chocks off first";
                moving = plateDown() || moving > 0 ? -1 : 1;
                event(moving > 0 ? "PLATE LOWERING" : "PLATE RAISING");
                return moving > 0 ? "§eLowering the bridge plate onto the wagon" : "§eRaising the bridge plate";
            }
            case "chock" -> {
                chocks = !chocks;
                event(chocks ? "CHOCKS ON" : "CHOCKS OFF");
                return chocks ? "§eWheel chocks on (vehicles held on the ramp)" : "§aChocks off";
            }
            case "load", "unload" -> {
                if (!plateDown()) return "§cLower the bridge plate onto the wagon first";
                if (chocks) return "§cTake the chocks off first";
                String m = id.equals("load") ? Motorail.load(world, this) : Motorail.unload(world, this);
                event(m.replaceAll("§.", ""));
                return m;
            }
            default -> { return null; }
        }
    }

    @Override protected void storeMore(NBTTagCompound t) { t.setDouble("plate", plate); t.setDouble("level", level); t.setBoolean("chocks", chocks); }
    @Override protected void restoreMore(NBTTagCompound t) { plate = t.getDouble("plate"); level = t.getDouble("level"); chocks = t.getBoolean("chocks"); }
}
