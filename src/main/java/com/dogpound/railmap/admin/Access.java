package com.dogpound.railmap.admin;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;

import java.util.Locale;
import java.util.UUID;

/**
 * Railway ownership and access control (her ChatGPT list #111). Who may work signals, dispatch trains, change
 * schedules, use depots and switch power:
 * <ol>
 *   <li>the server says so: Forge PermissionAPI nodes {@code irextras.rail.<signals|dispatch|schedules|depot|power>}
 *       (everyone by default) and {@code irextras.rail.admin} (ops) - her PridePerms answers these;</li>
 *   <li>on a company's territory (its claims, and the land round its stations and its lines' stations) the company
 *       decides: by role (Owner/Director everything, Dispatcher signals+dispatch+schedules+depots, Driver depots,
 *       Clerk schedules) changed per person by delegated grants / denials, or for another company's staff by an
 *       active track access agreement (#113) that grants that capability;</li>
 *   <li>command locking (#115): see {@link Locks}.</li>
 * </ol>
 * Unclaimed railway stays open, so an old world works exactly as before. Checked at every control desk click (a
 * right-click interceptor ahead of the controls framework) and in the board / console packets (small hooks).
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class Access {
    private Access() {}

    public enum Cap {
        SIGNALS("operate signals and points", true), DISPATCH("dispatch trains", true), SCHEDULES("change lines and schedules", false),
        DEPOT("use depots and maintenance facilities", false), POWER("switch substations and power", true), ADMIN("use railway administration", false);
        public final String what;
        /** commands of this kind take control of the area (#115) */
        public final boolean locks;
        Cap(String what, boolean locks) { this.what = what; this.locks = locks; }
        public int bit() { return 1 << ordinal(); }
        public String node() { return "irextras.rail." + name().toLowerCase(Locale.ROOT); }
        public static Cap of(String s) { try { return valueOf(s.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; } }
    }

    private static boolean registered;

    private static void register() {
        if (registered) return;
        registered = true;
        for (Cap c : Cap.values())
            PermissionAPI.registerNode(c.node(), c == Cap.ADMIN ? DefaultPermissionLevel.OP : DefaultPermissionLevel.ALL,
                    c == Cap.ADMIN ? "IR Extras railway administration: admin console, /irextras admin, bypasses company territory" : "IR Extras: " + c.what);
    }

    public static boolean node(EntityPlayer p, Cap c) {
        if (p == null || p instanceof FakePlayer) return true;
        register();
        try { return PermissionAPI.hasPermission(p, c.node()); } catch (RuntimeException e) { return c != Cap.ADMIN || p.canUseCommand(2, ""); }
    }

    public static boolean admin(EntityPlayer p) { return p == null || node(p, Cap.ADMIN); }

    /** what a company role may do by default */
    public static boolean roleAllows(Company.Role r, Cap c) {
        if (c == Cap.ADMIN) return false;
        switch (r) {
            case OWNER: case DIRECTOR: return true;
            case DISPATCHER: return c != Cap.POWER;
            case DRIVER: return c == Cap.DEPOT;
            case CLERK: return c == Cap.SCHEDULES;
            default: return false;
        }
    }

    /** what kind of railway equipment a tile is (null = not access-controlled: furniture, ticket machines, public emergency gear) */
    public static Cap capOf(TileEntity t) {
        if (t == null) return null;
        if (t instanceof TileAdminConsole) return Cap.ADMIN;
        String n = t.getClass().getName(), s = t.getClass().getSimpleName();
        if (!n.startsWith("com.dogpound.railmap.")) return null;
        if (s.contains("Emergency") || s.contains("CallPoint") || s.contains("Evac") || s.contains("Assembly") || s.contains("Ticket") || s.contains("Kiosk")
                || s.contains("Plunger") || s.contains("Wire") || s.contains("Tap")) return null;
        if (n.contains(".grid.") || n.contains(".ohle.") || s.equals("TilePowerPost")) return Cap.POWER;
        if (n.contains(".depot.") || s.equals("TileMaintenanceDepot") || s.equals("TileLocoDiag")) return Cap.DEPOT;
        if (s.equals("TileIncidentDesk") || s.equals("TileRadioDesk") || s.equals("TileYardPanel")) return Cap.DISPATCH;
        if (s.equals("TileStationMaster")) return Cap.SCHEDULES;
        if (s.equals("TileSignalBox") || s.equals("TileNXDesk") || s.equals("TileControlDesk") || s.equals("TilePointMachine")
                || n.contains(".signal.") || n.contains(".safety.")) return Cap.SIGNALS;
        return null;
    }

    // ---- territory --------------------------------------------------------------------------------------------------
    /** the company whose railway this is: an explicit claim first, else the nearest company station (or its lines' station) in reach */
    public static Company ownerAt(World w, BlockPos at) {
        if (at == null) return null;
        CompanyData cd = CompanyData.get(w);
        for (AdminData.Rect r : AdminData.get(w).claims)
            if (r.company > 0 && r.has(at.getX(), at.getZ())) { Company c = cd.byId(r.company); if (c != null) return c; }
        int R = AdminConfig.stationTerritory;
        if (R <= 0) return null;
        Company best = null;
        double bd = (double) R * R;
        RailwayData rd = RailwayData.get(w);
        for (Company c : cd.all()) {
            for (long s : c.stations) { double d = BlockPos.fromLong(s).distanceSq(at); if (d <= bd) { bd = d; best = c; } }
            for (String ln : c.lines) {
                RailwayData.Line l = rd.lines().get(ln);
                if (l != null) for (long s : l.stations) { double d = BlockPos.fromLong(s).distanceSq(at); if (d <= bd) { bd = d; best = c; } }
            }
        }
        return best;
    }

    /** may this person do `cap` on railway the company owns (on `line`, or anywhere when null)? */
    public static boolean mayAct(World w, UUID who, Company owner, Cap cap, String line) {
        AdminData ad = AdminData.get(w);
        int[] g = ad.grants.get(who);
        Company.Member m = owner.member(who);
        if (m != null) {
            if (g != null && (g[1] & cap.bit()) != 0) return false;
            return (g != null && (g[0] & cap.bit()) != 0) || roleAllows(m.role, cap);
        }
        Company mine = CompanyData.get(w).of(who);
        if (mine == null) return false;
        Company.Member mm = mine.member(who);
        boolean own = (g != null && (g[0] & cap.bit()) != 0) || (mm != null && roleAllows(mm.role, cap));
        if (g != null && (g[1] & cap.bit()) != 0) own = false;
        if (!own) return false;
        for (AdminData.Agreement a : ad.agreements)
            if (a.state == AdminData.State.ACTIVE && a.ownerCo == owner.id && a.operatorCo == mine.id && (a.caps & cap.bit()) != 0 && (line == null || a.covers(line))) return true;
        return false;
    }

    /**
     * The one check: null = go ahead (and, for commanding kinds, you now hold the area); else the refusal to show.
     * owner: the company that owns the thing (a train, a line), or null to look up who owns the place.
     */
    public static String check(EntityPlayer p, Cap cap, World w, BlockPos at, Company owner, String line, String desk) {
        if (p == null || p instanceof FakePlayer || w.isRemote) return null;
        boolean admin = admin(p);
        if (AdminConfig.accessControl && !admin) {
            if (!node(p, cap)) return "§c⛔ You don't have permission to " + cap.what + " on this server";
            if (cap == Cap.ADMIN) return "§c⛔ Railway administration is for server admins (irextras.rail.admin)";
            Company o = owner != null ? owner : ownerAt(w, at);
            if (o != null && !mayAct(w, p.getUniqueID(), o, cap, line))
                return "§c⛔ " + o.name + " runs this railway - to " + cap.what + " here you need a role there (" + need(cap) + "), a delegated grant, or an access agreement";
        }
        if (AdminConfig.commandLocking && cap.locks && at != null) return Locks.claim(w, p, at, desk, desk != null);
        return null;
    }

    private static String need(Cap c) {
        switch (c) {
            case POWER: return "Owner / Director";
            case DEPOT: return "Driver or above";
            case SCHEDULES: return "Clerk, Dispatcher or Director";
            default: return "Dispatcher or above";
        }
    }

    // ---- the desk click interceptor (ahead of GearEvents, which does not receive cancelled clicks) ---------------
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void click(PlayerInteractEvent.RightClickBlock e) {
        if (e.getWorld().isRemote) return;
        EntityPlayer p = e.getEntityPlayer();
        if (p.isSneaking() && e.getItemStack().getItem() instanceof ItemBlock) return;        // building against it
        TileEntity t = e.getWorld().getTileEntity(e.getPos());
        Cap c = capOf(t);
        if (c == null) return;
        String no = check(p, c, e.getWorld(), e.getPos(), null, null, DESKS.contains(t.getClass().getSimpleName()) ? deskName(t) : null);
        if (no == null) return;
        e.setCanceled(true);
        e.setCancellationResult(EnumActionResult.FAIL);
        p.sendStatusMessage(new TextComponentString(no), true);
    }

    /** control desks: working one takes control of its area (#115); other gear only respects whoever holds it */
    static final java.util.Set<String> DESKS = new java.util.HashSet<>(java.util.Arrays.asList("TileSignalBox", "TileNXDesk", "TileControlDesk", "TileIncidentDesk",
            "TileRadioDesk", "TileYardPanel", "TileFeedingDiagram", "TileDispatcherBoard"));

    static String deskName(TileEntity t) {
        if (t == null) return "Control Center console";
        String s = t.getClass().getSimpleName().replace("Tile", "");
        return s.replaceAll("([a-z])([A-Z])", "$1 $2") + " " + t.getPos().getX() + "," + t.getPos().getZ();
    }

    // ---- small hooks for the board / console packets ----------------------------------------------------------------
    private static boolean deny(EntityPlayerMP p, String no) {
        if (no == null) return false;
        p.sendStatusMessage(new TextComponentString(no), true);
        return true;
    }

    /** PacketRailOps: AUTO_SET/AUTO_OFF/SEND = dispatch the train; LINE_SAVE/LINE_DELETE = schedules; ROUTE_SET/CANCEL = signals */
    public static boolean denyRailOp(EntityPlayerMP p, byte op, String text, int entityId, long a) {
        World w = p.world;
        if (op <= 2) {
            Object[] tr = train(w, entityId);
            UUID u = (UUID) tr[0];
            String line = u == null ? null : lineOf(w, u);
            Company o = u == null ? null : CompanyData.get(w).ownerOfTrain(u, line);
            return deny(p, check(p, Cap.DISPATCH, w, (BlockPos) tr[1], o, line, "dispatcher board"));
        }
        if (op <= 4) {
            String ln = text == null ? "" : text.trim();
            Company o = CompanyData.get(w).ownerOfLine(ln);
            return deny(p, o == null ? check(p, Cap.SCHEDULES, w, null, null, ln, null) : check(p, Cap.SCHEDULES, w, null, o, ln, null));
        }
        return deny(p, check(p, Cap.SIGNALS, w, BlockPos.fromLong(a), null, null, "dispatcher board"));
    }

    /** PacketAction: THROW_SWITCH = signals at the switch, SET_STATION = schedules, TRAIN_CMD = dispatch the train, AUGMENT = dispatch */
    public static boolean denyAction(EntityPlayerMP p, byte action, BlockPos target, int entityId) {
        World w = p.world;
        if (action == 3) {
            Object[] tr = train(w, entityId);
            UUID u = (UUID) tr[0];
            String line = u == null ? null : lineOf(w, u);
            return deny(p, check(p, Cap.DISPATCH, w, (BlockPos) tr[1], u == null ? null : CompanyData.get(w).ownerOfTrain(u, line), line, "dispatcher board"));
        }
        Cap c = action == 0 ? Cap.SIGNALS : action == 1 ? Cap.SCHEDULES : Cap.DISPATCH;
        return deny(p, check(p, c, w, target, null, null, "dispatcher board"));
    }

    /** PacketWired: a remote press from the console's WIRED page works the gear at `target` */
    public static boolean denyRemote(EntityPlayerMP p, BlockPos target) {
        TileEntity t = p.world.isBlockLoaded(target) ? p.world.getTileEntity(target) : null;
        Cap c = capOf(t);
        return c != null && deny(p, check(p, c, p.world, target, null, null, "Control Center console"));
    }

    /** console tabs with no single place (DEPOT, OLE / power): the server permission only */
    public static boolean denyCap(EntityPlayerMP p, Cap c) {
        if (!AdminConfig.accessControl || admin(p) || node(p, c)) return false;
        return deny(p, "§c⛔ You don't have permission to " + c.what + " on this server");
    }

    static String lineOf(World w, UUID loco) {
        RailwayData.AutoTrain a = RailwayData.get(w).trains().get(loco);
        return a == null ? null : a.line;
    }

    /** [loco UUID or null, position or null] of a rolling stock entity id */
    static Object[] train(World w, int entityId) {
        try {
            cam72cam.immersiverailroading.entity.EntityRollingStock s = cam72cam.mod.world.World.get(w).getEntity(entityId, cam72cam.immersiverailroading.entity.EntityRollingStock.class);
            if (s == null) return new Object[]{null, null};
            BlockPos at = new BlockPos(s.getPosition().x, s.getPosition().y, s.getPosition().z);
            return new Object[]{s instanceof cam72cam.immersiverailroading.entity.Locomotive ? s.getUUID() : null, at};
        } catch (LinkageError | RuntimeException e) {
            return new Object[]{null, null};
        }
    }

    /** "SIGNALS,DEPOT" -> mask */
    public static int mask(String s) {
        int m = 0;
        for (String k : s.split("[,+ ]")) {
            if (k.equalsIgnoreCase("all")) return 0x1F;
            Cap c = Cap.of(k.trim());
            if (c != null) m |= c.bit();
        }
        return m;
    }

    public static String caps(int m) {
        StringBuilder b = new StringBuilder();
        for (Cap c : Cap.values()) if ((m & c.bit()) != 0) b.append(b.length() == 0 ? "" : "+").append(c.name());
        return b.length() == 0 ? "none" : b.toString();
    }
}
