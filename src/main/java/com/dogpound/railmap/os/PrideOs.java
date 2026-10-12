package com.dogpound.railmap.os;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.block.BlockOfficePart;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.wire.IWiredGear;
import net.minecraft.block.material.MapColor;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapData;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pride OS on the desk computers (requested feature). Sneak + right-click
 * a desk that has a modelled monitor with an empty hand: with 12 V (and mains, where the desk needs it) the computer
 * boots; without, NO POWER. Everything it keeps (notes, drawings, high scores, settings) lives in the desk tile's own
 * NBT under ForgeData/prideos, so no desk class changes and old saves load empty. Two players can use one desk: the
 * last save wins. Clicks on the desks' own buttons and screens are untouched (those are not sneaking).
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class PrideOs {
    private PrideOs() {}

    /** desks with a modelled monitor / screen / laptop (block registry paths) */
    public static final Set<String> DESKS = new HashSet<>(Arrays.asList(
            "freight_office", "lost_property", "parcels_office", "planning_desk", "driver_assessor", "diagnostic_terminal",
            "admin_console", "company_hq", "insurance_desk", "regulator_office", "loco_workshop", "crowd_monitor",
            "commodity_exchange", "stock_exchange", "container_terminal", "container_tracker", "crew_desk", "depot_office",
            "disruption_desk", "formation_desk", "incident_desk", "maint_terminal", "port_berth", "travel_centre",
            "station_master", "radio_desk"));

    static final String TAG = "prideos";
    static final int MAX_NOTES = 12, MAX_NOTE = 2000, MAX_DRAWS = 8, CANVAS = 32;
    /** players who typed the right PIN for a desk this session (uuid + pos), so their saves are accepted */
    private static final Set<String> unlocked = new HashSet<>();

    // ---- opening ------------------------------------------------------------------------------------------------------
    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.HIGH)   // before the desk-control click handlers, which would swallow it
    public static void click(PlayerInteractEvent.RightClickBlock e) {
        EntityPlayer p = e.getEntityPlayer();
        if (!p.isSneaking() || e.getHand() != EnumHand.MAIN_HAND || !p.getHeldItemMainhand().isEmpty()) return;
        TileEntity t = desk(e.getWorld(), e.getPos());
        if (t == null) return;
        e.setCanceled(true);
        e.setCancellationResult(EnumActionResult.SUCCESS);
        if (!e.getWorld().isRemote && p instanceof EntityPlayerMP) open((EntityPlayerMP) p, t);
    }

    /** the OS desk at pos (its side blocks lead to the desk), or null */
    public static TileEntity desk(World w, BlockPos pos) {
        if (w.getBlockState(pos).getBlock() instanceof BlockOfficePart) {
            BlockPos m = BlockOfficePart.master(w, pos);
            if (m != null) pos = m;
        }
        TileEntity t = w.getTileEntity(pos);
        if (t == null || w.getBlockState(pos).getBlock().getRegistryName() == null) return null;
        return DESKS.contains(w.getBlockState(pos).getBlock().getRegistryName().getPath()) ? t : null;
    }

    /** 12 V on the desk's terminal, and mains too where the desk runs on mains (its "mains" lamp state) */
    public static boolean powered(TileEntity t) {
        if (t instanceof IWiredGear && !((IWiredGear) t).term().powered()) return false;
        if (t instanceof com.dogpound.railmap.controls.IControlled) {
            com.dogpound.railmap.controls.ControlState s = ((com.dogpound.railmap.controls.IControlled) t).controls();
            if (s.vals.containsKey("mains") && s.get("mains") == 0) return false;
        }
        return true;
    }

    static void open(EntityPlayerMP p, TileEntity t) {
        if (!powered(t)) {
            RailMap.NETWORK.sendTo(new PacketOs(PacketOs.NOPOWER, t.getPos(), "", null), p);
            return;
        }
        NBTTagCompound os = data(t);
        if (!os.getCompoundTag("set").getString("pin").isEmpty() && !unlocked.contains(p.getUniqueID() + "@" + t.getPos().toLong())) {
            NBTTagCompound l = new NBTTagCompound();
            l.setString("name", os.getCompoundTag("set").getString("name"));
            RailMap.NETWORK.sendTo(new PacketOs(PacketOs.LOCKED, t.getPos(), "", l), p);
            return;
        }
        NBTTagCompound out = os.copy();
        NBTTagCompound set = out.getCompoundTag("set");
        set.setBoolean("hasPin", !set.getString("pin").isEmpty());
        set.removeTag("pin");                                     // the PIN never leaves the server
        out.setTag("set", set);
        out.setTag("status", status(t.getWorld()));
        out.setString("desk", t.getBlockType().getLocalizedName());
        RailMap.NETWORK.sendTo(new PacketOs(PacketOs.OPEN, t.getPos(), "", out), p);
    }

    // ---- the desk's saved state ---------------------------------------------------------------------------------------
    static NBTTagCompound data(TileEntity t) { return t.getTileData().getCompoundTag(TAG); }

    static void put(TileEntity t, String key, NBTBase v) {
        NBTTagCompound os = data(t);
        os.setTag(key, v);
        t.getTileData().setTag(TAG, os);
        t.markDirty();
    }

    static void fromClient(EntityPlayerMP p, PacketOs m) {
        if (p.getDistanceSq(m.pos) > 12 * 12 || !p.world.isBlockLoaded(m.pos)) return;
        TileEntity t = desk(p.world, m.pos);
        if (t == null) return;
        String who = p.getUniqueID() + "@" + m.pos.toLong();
        NBTTagCompound os = data(t);
        boolean locked = !os.getCompoundTag("set").getString("pin").isEmpty() && !unlocked.contains(who);
        switch (m.op) {
            case PacketOs.UNLOCK:
                if (m.data.getString("pin").equals(os.getCompoundTag("set").getString("pin"))) { unlocked.add(who); open(p, t); }
                else { p.sendStatusMessage(new TextComponentString("§cPride OS: wrong PIN"), true); RailMap.NETWORK.sendTo(new PacketOs(PacketOs.LOCKED, m.pos, "wrong", null), p); }
                return;
            case PacketOs.STATUS:
                if (!locked) RailMap.NETWORK.sendTo(new PacketOs(PacketOs.STATUS, m.pos, "", status(p.world)), p);
                return;
            default:
        }
        if (locked) return;
        switch (m.op) {
            case PacketOs.SAVE: save(t, m.key, m.data, who); break;
            case PacketOs.BOOK: say(p, book(p, t, m.data.getInteger("n"))); break;
            case PacketOs.MAP: say(p, map(p, t, m.data.getInteger("n"))); break;
            default:
        }
    }

    /** one section from a client; checked and capped (last write wins) */
    static void save(TileEntity t, String key, NBTTagCompound d, String who) {
        NBTTagCompound os = data(t);
        switch (key) {
            case "notes": {
                NBTTagList in = d.getTagList("v", 10), out = new NBTTagList();
                for (int i = 0; i < Math.min(MAX_NOTES, in.tagCount()); i++) {
                    NBTTagCompound n = in.getCompoundTagAt(i), c = new NBTTagCompound();
                    c.setString("t", cap(n.getString("t"), 40));
                    c.setString("b", cap(n.getString("b"), MAX_NOTE));
                    out.appendTag(c);
                }
                put(t, "notes", out);
                break;
            }
            case "draws": {
                NBTTagList in = d.getTagList("v", 10), out = new NBTTagList();
                for (int i = 0; i < Math.min(MAX_DRAWS, in.tagCount()); i++) {
                    NBTTagCompound n = in.getCompoundTagAt(i), c = new NBTTagCompound();
                    byte[] px = n.getByteArray("p");
                    c.setString("n", cap(n.getString("n"), 24));
                    c.setByteArray("p", px.length == CANVAS * CANVAS ? px : new byte[CANVAS * CANVAS]);
                    out.appendTag(c);
                }
                put(t, "draws", out);
                break;
            }
            case "hs": {                                            // keep the best of what is there and what came in
                NBTTagCompound hs = os.getCompoundTag("hs");
                for (String k : new String[]{"snake", "2048"}) hs.setInteger(k, Math.max(hs.getInteger(k), d.getInteger(k)));
                for (String k : new String[]{"mines", "sol"}) {
                    int a = hs.getInteger(k), b = d.getInteger(k);
                    if (b > 0 && (a == 0 || b < a)) hs.setInteger(k, b);
                }
                hs.setInteger("solWins", Math.max(hs.getInteger("solWins"), d.getInteger("solWins")));
                put(t, "hs", hs);
                break;
            }
            case "set": {
                NBTTagCompound old = os.getCompoundTag("set"), s = new NBTTagCompound();
                s.setInteger("wall", Math.floorMod(d.getInteger("wall"), 8));
                s.setInteger("theme", Math.floorMod(d.getInteger("theme"), 8));
                s.setString("name", cap(d.getString("name"), 24));
                String pin = d.hasKey("pin") ? d.getString("pin") : old.getString("pin");
                s.setString("pin", pin.matches("[0-9]{0,8}") ? pin : old.getString("pin"));
                s.setInteger("vol", Math.max(0, Math.min(100, d.hasKey("vol") ? d.getInteger("vol") : old.hasKey("vol") ? old.getInteger("vol") : 80)));
                if (!s.getString("pin").isEmpty()) unlocked.add(who);   // whoever sets the PIN stays in
                put(t, "set", s);
                break;
            }
            default:
        }
    }

    private static String cap(String s, int n) { return s.length() > n ? s.substring(0, n) : s; }

    private static void say(EntityPlayer p, String s) { if (s != null) p.sendStatusMessage(new TextComponentString(s), true); }

    // ---- railway status (the same data the Control Center reads) ------------------------------------------------------
    public static NBTTagCompound status(World w) {
        NBTTagCompound s = new NBTTagCompound();
        NBTTagList trains = new NBTTagList();
        int locos = 0, cars = 0;
        try {
            for (cam72cam.immersiverailroading.entity.EntityRollingStock rs : cam72cam.mod.world.World.get(w).getEntities(cam72cam.immersiverailroading.entity.EntityRollingStock.class)) {
                cars++;
                if (!(rs instanceof cam72cam.immersiverailroading.entity.Locomotive)) continue;
                locos++;
                float kmh = rs instanceof cam72cam.immersiverailroading.entity.EntityMoveableRollingStock && ((cam72cam.immersiverailroading.entity.EntityMoveableRollingStock) rs).getCurrentSpeed() != null
                        ? (float) ((cam72cam.immersiverailroading.entity.EntityMoveableRollingStock) rs).getCurrentSpeed().metric() : 0;
                int consist = rs instanceof cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock && ((cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock) rs).isCoupled()
                        ? ((cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock) rs).getTrain().size() : 1;
                if (trains.tagCount() < 40) trains.appendTag(new NBTTagString(String.format(java.util.Locale.ROOT, "%s|%.0f|%d|%d|%d",
                        rs.getDefinition() == null ? rs.getDefinitionID() : rs.getDefinition().name(), kmh, consist, (int) rs.getPosition().x, (int) rs.getPosition().z)));
            }
        } catch (Throwable ignored) {}                              // IR not there / mid-load: no trains
        s.setTag("trains", trains);
        s.setInteger("locos", locos);
        s.setInteger("cars", cars);

        NBTTagList al = new NBTTagList();
        int active = 0, unacked = 0;
        for (AlarmManager.Alarm a : AlarmManager.get(w).all()) {
            if (!a.active) continue;
            active++;
            if (!a.acked) unacked++;
            if (al.tagCount() < 30) al.appendTag(new NBTTagString(a.sev.name() + "|" + (a.acked ? "1" : "0") + "|" + a.text));
        }
        s.setTag("alarms", al);
        s.setInteger("active", active);
        s.setInteger("unacked", unacked);

        GridData g = GridData.get(w);
        int tripped = 0, open = 0;
        for (GridData.Node n : g.nodes().values()) { if (n.breaker == 2) tripped++; else if (n.breaker == 1) open++; }
        s.setInteger("gridNodes", g.nodes().size());
        s.setInteger("gridNets", g.nets().size());
        s.setInteger("gridTripped", tripped);
        s.setInteger("gridOpen", open);

        NBTTagList st = new NBTTagList();
        for (Map.Entry<Long, String> e : StationData.get(w).names().entrySet())
            if (st.tagCount() < 60) st.appendTag(new NBTTagString(e.getValue() + "|" + BlockPos.fromLong(e.getKey()).getX() + "|" + BlockPos.fromLong(e.getKey()).getZ()));
        s.setTag("stations", st);
        return s;
    }

    // ---- printing -----------------------------------------------------------------------------------------------------
    /** note n as a written book: takes a book (or book and quill) from you unless creative */
    static String book(EntityPlayerMP p, TileEntity t, int n) {
        NBTTagList notes = data(t).getTagList("notes", 10);
        if (n < 0 || n >= notes.tagCount()) return "§cPride OS: save the note first";
        if (!take(p, Items.WRITABLE_BOOK) && !take(p, Items.BOOK)) return "§cPride OS printer: put a book in your inventory";
        NBTTagCompound note = notes.getCompoundTagAt(n);
        String body = note.getString("b");
        NBTTagList pages = new NBTTagList();
        for (int i = 0; i < Math.max(1, body.length()); i += 240)
            pages.appendTag(new NBTTagString(net.minecraft.util.text.ITextComponent.Serializer.componentToJson(new TextComponentString(body.substring(i, Math.min(body.length(), i + 240))))));
        ItemStack b = new ItemStack(Items.WRITTEN_BOOK);
        NBTTagCompound tag = new NBTTagCompound();
        String title = note.getString("t").isEmpty() ? "Note " + (n + 1) : note.getString("t");
        tag.setString("title", title.length() > 32 ? title.substring(0, 32) : title);
        tag.setString("author", p.getName());
        tag.setTag("pages", pages);
        b.setTagCompound(tag);
        give(p, b);
        com.dogpound.railmap.sound.PackSounds.play(p.world, t.getPos(), "ui.paper", 0.7f, 1f);
        return "§aPride OS: printed \"" + title + "\"";
    }

    /** the 16 paint colours: map colours (shade 2 = the true colour), shared with the Paint app */
    public static final MapColor[] PALETTE = {MapColor.SNOW, MapColor.SILVER, MapColor.GRAY, MapColor.BLACK, MapColor.RED, MapColor.ADOBE,
            MapColor.YELLOW, MapColor.LIME, MapColor.GREEN, MapColor.CYAN, MapColor.LIGHT_BLUE, MapColor.BLUE, MapColor.PURPLE,
            MapColor.MAGENTA, MapColor.PINK, MapColor.BROWN};

    /** drawing n onto a new filled map (4x scale, locked away from any terrain): takes an empty map unless creative */
    static String map(EntityPlayerMP p, TileEntity t, int n) {
        NBTTagList draws = data(t).getTagList("draws", 10);
        if (n < 0 || n >= draws.tagCount()) return "§cPride OS: save the drawing first";
        if (!take(p, Items.MAP)) return "§cPride OS printer: put an empty map in your inventory";
        byte[] px = draws.getCompoundTagAt(n).getByteArray("p");
        World w = p.world;
        int id = w.getUniqueDataId("map");
        String key = "map_" + id;
        MapData md = new MapData(key);
        md.scale = 0;
        md.xCenter = 30_000_000;                                   // nowhere: the map never draws terrain over the picture
        md.zCenter = 30_000_000;
        md.dimension = Integer.MIN_VALUE + 7;
        md.trackingPosition = false;
        md.unlimitedTracking = false;
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            int c = px.length == CANVAS * CANVAS ? px[(y / 4) * CANVAS + x / 4] & 15 : 0;
            md.colors[y * 128 + x] = (byte) (PALETTE[c].colorIndex * 4 + 2);
        }
        md.markDirty();
        w.setData(key, md);
        ItemStack s = new ItemStack(Items.FILLED_MAP, 1, id);
        NBTTagCompound disp = new NBTTagCompound();
        disp.setString("Name", "§d" + (draws.getCompoundTagAt(n).getString("n").isEmpty() ? "Pride Paint " + (n + 1) : draws.getCompoundTagAt(n).getString("n")));
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("display", disp);
        s.setTagCompound(tag);
        give(p, s);
        com.dogpound.railmap.sound.PackSounds.play(w, t.getPos(), "ui.paper", 0.7f, 0.8f);
        return "§aPride OS: printed the drawing to a map";
    }

    private static boolean take(EntityPlayer p, Item item) {
        if (p.capabilities.isCreativeMode) return true;
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (s.getItem() == item) { s.shrink(1); return true; }
        }
        return false;
    }

    private static void give(EntityPlayer p, ItemStack s) { if (!p.inventory.addItemStackToInventory(s)) p.dropItem(s, false); }

    // ---- /irextras os ... --------------------------------------------------------------------------------------------
    public static final String USAGE = "os selftest";

    public static boolean command(World w, ICommandSender sender, String[] args) {
        if (args.length < 1 || !args[0].equalsIgnoreCase("os")) return false;
        if (args.length > 1 && args[1].equalsIgnoreCase("selftest")) {
            List<String> r = OsLogic.selftest();
            int fail = 0;
            for (String l : r) { sender.sendMessage(new TextComponentString(l)); if (l.contains("FAIL")) fail++; }
            sender.sendMessage(new TextComponentString((fail == 0 ? "§aPride OS self-test: all " : "§cPride OS self-test: " + fail + " of ") + r.size() + " checks " + (fail == 0 ? "passed" : "failed")));
        } else sender.sendMessage(new TextComponentString("§7/irextras os selftest - Pride OS game, calculator and notes checks. Open Pride OS: sneak + right-click a desk computer with an empty hand."));
        return true;
    }
}
