package com.dogpound.railmap.cabinet;

import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.controls.ControlState;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.controls.Layout;
import com.dogpound.railmap.controls.Surface;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.grid.ItemPadlock;
import com.dogpound.railmap.grid.ItemScrewdriver;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.grid.Permits;
import com.dogpound.railmap.grid.TileGrid;
import com.dogpound.railmap.item.ItemSignalWrench;
import com.dogpound.railmap.sound.PackSounds;
import com.dogpound.railmap.wire.IWiredGear;
import com.dogpound.railmap.wire.IWiredMachine;
import com.dogpound.railmap.wire.ItemWire;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Machine cabinets whose doors really swing open and whose insides really work (requested feature). tools/cabkit.py writes assets/irextras/cabinets/<block>.json
 * (doors, hinges, functions) and the inside's live controls into the block's normal controls layout:
 * <ul>
 * <li>m_cab_door&lt;n&gt; - door zone: right-click opens / closes it (padlock it shut with the Lockout Padlock)</li>
 * <li>m_cab_mcb_&lt;fn&gt; - MCB (LEVER: 0 ON, 1 OFF, 2 tripped)</li>
 * <li>m_cab_fuse_&lt;fn&gt; - fuse carrier (0 fitted, 1 pulled out, 2 blown): pull it, fit a Cartridge Fuse</li>
 * <li>cab_k / cab_kl - interface relays' flags and coil LEDs; cab_run cab_err cab_i cab_q - the PLC's LEDs</li>
 * </ul>
 * A function (fn) is live only with its MCB ON and its fuse fitted and healthy: ctl = the 12 V control supply (the
 * terminal goes dead, the machine fails safe, the console's WIRED page shows the fault), main = the mains / grid supply,
 * scr = the screens, out = a grid machine's DC output / battery. The state lives in the machine's own control state,
 * so it saves and syncs with it; old saves have none of these keys = everything ON and fitted.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class Cabinet {
    private Cabinet() {}

    public static final String DOOR = "m_cab_door", MCB = "m_cab_mcb_", FUSE = "m_cab_fuse_";
    static final DamageSource ZAP = new DamageSource("irextras.zap").setDamageBypassesArmor();

    // ---- spec ---------------------------------------------------------------------------------------------------
    public static final class Door {
        public String id, model;
        public double hx, hz, angle;
    }

    public static final class Spec {
        public String block, title;
        public boolean hv;
        public final List<Door> doors = new ArrayList<>();
        public final List<String[]> fns = new ArrayList<>();       // {id, label}
        public final List<String> relays = new ArrayList<>();
        public String label(String fn) { for (String[] f : fns) if (f[0].equals(fn)) return f[1]; return fn; }
        public int index(String fn) { for (int i = 0; i < fns.size(); i++) if (fns.get(i)[0].equals(fn)) return i + 1; return 0; }
    }

    private static final Map<String, Optional<Spec>> SPECS = new HashMap<>();

    public static synchronized Spec of(String block) {
        return SPECS.computeIfAbsent(block, Cabinet::load).orElse(null);
    }

    public static Spec of(TileEntity t) {
        return t == null || t.getBlockType() == null || t.getBlockType().getRegistryName() == null || !RailMap.MODID.equals(t.getBlockType().getRegistryName().getNamespace())
                ? null : of(t.getBlockType().getRegistryName().getPath());
    }

    private static Optional<Spec> load(String block) {
        try (InputStream in = Cabinet.class.getResourceAsStream("/assets/" + RailMap.MODID + "/cabinets/" + block + ".json")) {
            if (in == null) return Optional.empty();
            JsonObject o = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Spec s = new Spec();
            s.block = block;
            s.title = o.has("title") ? o.get("title").getAsString() : block;
            s.hv = o.has("hv") && o.get("hv").getAsBoolean();
            for (JsonElement e : o.getAsJsonArray("doors")) {
                JsonObject d = e.getAsJsonObject();
                Door door = new Door();
                door.id = d.get("id").getAsString();
                door.model = d.get("model").getAsString();
                JsonArray h = d.getAsJsonArray("hinge");
                door.hx = h.get(0).getAsDouble(); door.hz = h.get(1).getAsDouble();
                door.angle = d.get("angle").getAsDouble();
                s.doors.add(door);
            }
            for (JsonElement e : o.getAsJsonArray("fns")) s.fns.add(new String[]{e.getAsJsonObject().get("id").getAsString(), e.getAsJsonObject().get("label").getAsString()});
            if (o.has("relays")) for (JsonElement e : o.getAsJsonArray("relays")) s.relays.add(e.getAsString());
            return Optional.of(s);
        } catch (Exception ex) {
            RailMap.LOG.warn("[IR Extras] bad cabinet spec {}: {}", block, ex.toString());
            return Optional.empty();
        }
    }

    // ---- state: kept in the machine's own control state (TileGrid: its panel map) --------------------------------
    public static int get(IControlled m, String k) { return m instanceof TileGrid g ? g.pv(k) : m.controls().get(k); }

    static boolean put(IControlled m, String k, int v) {
        if (get(m, k) == v && (m instanceof TileGrid || m.controls().vals.containsKey(k))) return false;
        if (m instanceof TileGrid g) g.cabPut(k, v); else m.controls().set(k, v);
        return true;
    }

    static String text(IControlled m, String k) { return m instanceof TileGrid g ? g.ptext(k) : m.controls().text(k); }

    static void text(IControlled m, String k, String v) { if (m instanceof TileGrid g) g.cabText(k, v); else m.controls().texts.put(k, v); }

    /** is this function's MCB on and its fuse fitted + healthy (true for machines without a cabinet) */
    public static boolean ok(ControlState s, String fn) { return s.get(MCB + fn) == 0 && s.get(FUSE + fn) == 0; }

    public static boolean ok(IControlled m, String fn) { return get(m, MCB + fn) == 0 && get(m, FUSE + fn) == 0; }

    /** a display may show text: its cabinet's screen circuit is live (the cabinet's own PLC display runs off the 12 V) */
    public static boolean screenOk(ControlState s, String key) { return key.startsWith("cab_") || ok(s, "scr"); }

    public static boolean isCabinetId(String id) { return id.startsWith("m_cab_") || id.startsWith("cab_"); }

    static boolean open(IControlled m, Door d) { return get(m, d.id) == 1; }

    static Door door(Spec s, String id) { for (Door d : s.doors) if (d.id.equals(id)) return d; return null; }

    // ---- clicks -------------------------------------------------------------------------------------------------
    /**
     * the control the player is aiming at on this machine, seeing through open doors (a door's zone is where it stood
     * when shut: skippedDoor[0] gets it, skipD[0] its distance); dist[0] = distance of the result, px
     */
    static Panel.C aim(IControlled m, Spec s, Vec3d eye, Vec3d look, Panel.C[] skippedDoor, double[] skipD, double[] dist) {
        Layout l = m.layout();
        if (l.isEmpty()) return null;
        TileEntity t = (TileEntity) m;
        double ex = eye.x - t.getPos().getX() - 0.5, ey = eye.y - t.getPos().getY(), ez = eye.z - t.getPos().getZ() - 0.5, dx = look.x, dy = look.y, dz = look.z;
        EnumFacing f = m.controlFacing();
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        for (int i = 0; i < turns; i++) { double nx = ez, nz = -ex; ex = nx; ez = nz; nx = dz; nz = -dx; dx = nx; dz = nz; }
        double[] eyeM = {(ex + 0.5) * 16, ey * 16, (ez + 0.5) * 16}, lookM = {dx, dy, dz};
        Panel.C best = null;
        double bestD = Double.MAX_VALUE;
        for (Surface sf : l.surfaces) {
            double[] d = new double[1];
            Panel.C c = sf.hit(eyeM, lookM, d);
            if (c == null) continue;
            if (c.id.startsWith(DOOR)) { Door dr = door(s, c.id); if (dr != null && open(m, dr)) { if (d[0] < skipD[0]) { skippedDoor[0] = c; skipD[0] = d[0]; } continue; } }
            if (d[0] < bestD) { best = c; bestD = d[0]; }
        }
        dist[0] = bestD;
        return best;
    }

    /**
     * Right-click on a cabinet machine (or on the block behind a tall one's upper half): doors, MCBs, fuses are worked
     * here; through an open door the click reaches whatever is behind it. Anything else is left to GearEvents /
     * StationControls exactly as before.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void click(PlayerInteractEvent.RightClickBlock e) {
        World w = e.getWorld();
        TileEntity at = w.getTileEntity(e.getPos());
        EntityPlayer p = e.getEntityPlayer();
        ItemStack held = e.getItemStack();
        if (held.getItem() instanceof ItemWire || held.getItem() instanceof ItemScrewdriver || held.getItem() instanceof ItemSignalWrench) return;
        Vec3d eye = p.getPositionEyes(1f), look = p.getLook(1f);
        List<IControlled> cands = new ArrayList<>();
        double limit = Double.MAX_VALUE;
        if (at instanceof IControlled m) { if (of(at) == null) return; cands.add(m); }
        else {
            if (e.getHitVec() != null) limit = eye.distanceTo(e.getHitVec()) * 16 + 0.5;   // must be in front of the block hit
            for (TileEntity t : w.loadedTileEntityList)
                if (t instanceof IControlled m && !t.isInvalid() && t.getDistanceSq(eye.x, eye.y, eye.z) <= 64 && of(t) != null) cands.add(m);
        }
        IControlled bm = null;
        Panel.C bc = null, bskip = null;
        double bd = limit;
        for (IControlled m : cands) {
            Panel.C[] skipped = new Panel.C[1];
            double[] sd = {Double.MAX_VALUE}, d = new double[1];
            Panel.C c = aim(m, of((TileEntity) m), eye, look, skipped, sd, d);
            if (c == null && skipped[0] != null) { c = skipped[0]; d[0] = sd[0]; }       // the open door's frame: shut it
            if (c != null && d[0] < bd) { bd = d[0]; bm = m; bc = c; bskip = skipped[0]; }
        }
        if (bc == null) return;
        boolean mine = isCabinetId(bc.id);
        if (!mine && bskip == null) return;                                      // an ordinary control: worked as before
        if (p.isSneaking() && !(mine && bc.id.startsWith(DOOR))) return;
        e.setCanceled(true);
        e.setCancellationResult(EnumActionResult.SUCCESS);
        if (w.isRemote) return;
        String r = mine ? press(bm, of((TileEntity) bm), bc, p, held) : IControlled.operate(bm, bc.id, p);
        if (r != null && !r.isEmpty()) p.sendStatusMessage(new TextComponentString(r), true);
    }

    /** server: work a cabinet part by hand */
    static String press(IControlled m, Spec s, Panel.C c, EntityPlayer p, ItemStack held) {
        TileEntity t = (TileEntity) m;
        World w = t.getWorld();
        BlockPos pos = t.getPos();
        String id = c.id, r;
        if (id.startsWith(DOOR)) {
            Door d = door(s, id);
            if (d == null) return null;
            String lk = text(m, id + "_lk");
            if (!lk.isEmpty()) {
                if (p.isSneaking() && held.isEmpty() && lk.equals(p.getName())) {
                    text(m, id + "_lk", "");
                    if (!p.addItemStackToInventory(new ItemStack(com.dogpound.railmap.proxy.CommonProxy.padlock))) p.dropItem(new ItemStack(com.dogpound.railmap.proxy.CommonProxy.padlock), false);
                    PackSounds.play(w, pos, "ctl.key", 0.7f, 1.2f);
                    m.controlsChanged();
                    return "§aPadlock removed from the " + s.title + " door";
                }
                return "§c🔒 Padlocked shut by " + lk + (lk.equals(p.getName()) ? " §7(sneak + right-click empty-handed to remove it)" : "");
            }
            if (p.isSneaking()) return null;
            if (held.getItem() instanceof ItemPadlock) {
                if (open(m, d)) return "§eShut the door first, then padlock it";
                text(m, id + "_lk", p.getName());
                if (!p.capabilities.isCreativeMode) held.shrink(1);
                PackSounds.play(w, pos, "ctl.key", 0.8f, 0.9f);
                m.controlsChanged();
                Recorder.event(w, s.title + " door padlocked by " + p.getName());
                return "§a🔒 Door padlocked - only " + p.getName() + " can take it off";
            }
            boolean nowOpen = !open(m, d);
            put(m, id, nowOpen ? 1 : 0);
            PackSounds.play(w, pos, nowOpen ? "machine.door_open" : "machine.door_close", 0.8f, PackSounds.vary(w));
            r = nowOpen ? "§7" + s.title + ": door open" : "§7" + s.title + ": door shut";
            if (nowOpen && s.hv && energized(m)) {
                AlarmManager.get(w).raise(w, "cabdoor:" + pos.toLong(), AlarmManager.Cat.POWER, AlarmManager.Sev.MAJOR,
                        s.title + " HV door opened while LIVE", pos, true);
                if (RailMapConfig.doorShock && !p.capabilities.isCreativeMode) {
                    p.attackEntityFrom(ZAP, 6f);
                    p.setFire(1);
                    w.playSound(null, pos, ModSounds.ARC_FLASH, SoundCategory.BLOCKS, 1f, 1f);
                    Recorder.event(w, p.getName() + " shocked opening the live " + s.title);
                    r = "§c§l⚡ ZAP! §r§cThat cabinet is LIVE high voltage - isolate it before opening";
                } else r = "§6⚠ " + s.title + " is LIVE: HV door open alarm raised";
            }
        } else if (id.startsWith(MCB)) {
            String fn = id.substring(MCB.length());
            int v = get(m, id);
            int nv = v == 0 ? 1 : 0;                                                    // tripped (2) resets to OFF first
            put(m, id, nv);
            w.playSound(null, pos, ModSounds.CTL_TOGGLE, SoundCategory.BLOCKS, 0.7f, nv == 0 ? 1.15f : 0.9f);
            r = (nv == 0 ? "§aF" : "§cF") + s.index(fn) + " " + s.label(fn) + " MCB " + (nv == 0 ? "ON" : v == 2 ? "reset to OFF" : "OFF - " + what(fn) + " isolated");
            Recorder.event(w, s.title + " F" + s.index(fn) + " " + s.label(fn) + " MCB " + (nv == 0 ? "ON" : "OFF") + " by " + p.getName());
        } else if (id.startsWith(FUSE)) {
            String fn = id.substring(FUSE.length());
            int v = get(m, id);
            String nm = "FU" + s.index(fn) + " " + s.label(fn);
            if (v != 1) {
                put(m, id, 1);
                ItemStack f = ItemFuse.stack(v == 2);
                if (!p.addItemStackToInventory(f)) p.dropItem(f, false);
                w.playSound(null, pos, ModSounds.CTL_TOGGLE, SoundCategory.BLOCKS, 0.6f, 1.4f);
                r = "§e" + nm + " fuse pulled" + (v == 2 ? " §c(it's blown)" : "") + "§e - " + what(fn) + " isolated";
                Recorder.event(w, s.title + " " + nm + " fuse withdrawn by " + p.getName());
            } else if (held.getItem() instanceof ItemFuse) {
                boolean blown = held.getMetadata() == 1;
                put(m, id, blown ? 2 : 0);
                if (!p.capabilities.isCreativeMode) held.shrink(1);
                w.playSound(null, pos, ModSounds.CTL_TOGGLE, SoundCategory.BLOCKS, 0.6f, 1.0f);
                r = blown ? "§c" + nm + ": you fitted a BLOWN fuse - " + what(fn) + " still dead" : "§a" + nm + " fuse fitted - " + what(fn) + " restored";
                Recorder.event(w, s.title + " " + nm + " fuse fitted by " + p.getName());
            } else r = "§7" + nm + ": carrier empty - hold a Cartridge Fuse and right-click to fit one";
        } else if (id.equals("cab_lt")) {
            LAMPTEST.put(w.provider.getDimension() + ":" + pos.toLong(), w.getTotalWorldTime() + 60);
            w.playSound(null, pos, ModSounds.BUTTON, SoundCategory.BLOCKS, 0.5f, 1.3f);
            r = "§7Cabinet lamp test";
        } else return "§7" + c.label;                                                  // relays, LEDs: read only
        update(m, s);
        m.controlsChanged();
        return r;
    }

    static String what(String fn) {
        switch (fn) {
            case "ctl": return "12 V controls";
            case "main": return "mains supply";
            case "scr": return "screens";
            case "out": return "output";
            default: return fn;
        }
    }

    // ---- what each function really does --------------------------------------------------------------------------
    private static final Map<Class<?>, Optional<Field>> MAINS = new HashMap<>();

    /** the machine's mains supply object (depot.Mains or infra.Supply), whatever its base class calls it */
    static Object mains(TileEntity t) {
        // ponytail: reflection on the field named "mains" (5 base classes have one); an interface if more appear
        Optional<Field> f = MAINS.computeIfAbsent(t.getClass(), k -> {
            for (Class<?> c = k; c != null && c != TileEntity.class; c = c.getSuperclass()) {
                try { Field x = c.getDeclaredField("mains"); x.setAccessible(true); return Optional.of(x); } catch (NoSuchFieldException ignored) { }
            }
            return Optional.empty();
        });
        try { return f.isPresent() ? f.get().get(t) : null; } catch (IllegalAccessException ex) { return null; }
    }

    static boolean mainsOk(TileEntity t) {
        Object o = mains(t);
        if (o instanceof com.dogpound.railmap.depot.Mains x) return x.ok();
        if (o instanceof com.dogpound.railmap.infra.Supply x) return x.ok;
        if (t instanceof TileGrid) { GridData.Net n = GridData.get(t.getWorld()).netOf(t.getPos()); return n != null && n.problem.isEmpty(); }
        return true;
    }

    static boolean energized(IControlled m) {
        TileEntity t = (TileEntity) m;
        if (t instanceof TileGrid) return Permits.liveNow(t.getWorld(), t.getPos());
        return mainsOk(t);
    }

    /** push the MCB / fuse positions into the machine (server, on every change and twice a second) */
    static void apply(IControlled m, Spec s) {
        TileEntity t = (TileEntity) m;
        if (m instanceof IWiredGear g) g.term().isolated = !ok(m, "ctl");
        Object o = mains(t);
        boolean mainOff = !ok(m, "main");
        if (o instanceof com.dogpound.railmap.depot.Mains x) x.isolated = mainOff;
        else if (o instanceof com.dogpound.railmap.infra.Supply x) x.isolated = mainOff;
        if (t instanceof TileGrid g && !ok(m, "out")) {
            GridData d = GridData.get(t.getWorld());
            GridData.Node n = d.node(t.getPos());
            if (n != null) {
                if (g.kind() == GridKind.BATTERY) { if (n.batOn) { n.batOn = false; d.touch(); } }
                else if (g.kind().electronic() && n.dcOn) { n.dcOn = false; d.touch(); }
            }
        }
    }

    // ---- the inside's lamps, relays and PLC + alarms ---------------------------------------------------------------
    private static final Map<String, Set<String>> FAULTS = new HashMap<>();
    /** cabinet lamp test running until (world tick), per dim:pos */
    private static final Map<String, Long> LAMPTEST = new HashMap<>();

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || e.world.getTotalWorldTime() % 10 != 3) return;
        for (TileEntity t : new ArrayList<>(e.world.loadedTileEntityList)) {
            if (!(t instanceof IControlled m) || t.isInvalid()) continue;
            Spec s = of(t);
            if (s != null) update(m, s);
        }
    }

    static void update(IControlled m, Spec s) {
        TileEntity t = (TileEntity) m;
        World w = t.getWorld();
        BlockPos pos = t.getPos();
        apply(m, s);
        boolean ch = false;
        // a fault blows the fuse that protects it: burnt-out electronics take the 12 V control fuse with them
        boolean burnt = m instanceof IWiredGear g ? g.term().burnt : t instanceof TileGrid tg && tg.isBurnt();
        String fuseFn = has(s, "ctl") ? "ctl" : s.fns.isEmpty() ? null : s.fns.get(0)[0];
        if (burnt && fuseFn != null && get(m, FUSE + fuseFn) == 0) {
            ch |= put(m, FUSE + fuseFn, 2);
            w.playSound(null, pos, ModSounds.FUSE_BLOW, SoundCategory.BLOCKS, 1f, 1f);
            apply(m, s);
        }
        boolean k1 = m instanceof IWiredGear g ? g.term().powered() : ok(m, "ctl") && !burnt;
        boolean k2 = ok(m, "main") && mainsOk(t);
        boolean k3 = m instanceof IWiredMachine wm && wm.wiredLamp() == 3 || burnt;
        boolean closed = true;
        for (Door d : s.doors) closed &= !open(m, d);
        boolean fusesOk = true, mcbsOn = true;
        Set<String> faults = new TreeSet<>();
        for (String[] f : s.fns) {
            int fv = get(m, FUSE + f[0]), mv = get(m, MCB + f[0]);
            fusesOk &= fv == 0;
            mcbsOn &= mv == 0;
            ch |= put(m, "cab_fb_" + f[0], fv == 2 ? 1 : 0);
            if (fv == 2) faults.add("FU" + s.index(f[0]) + " " + f[1] + " fuse BLOWN");
            else if (fv == 1) faults.add("FU" + s.index(f[0]) + " " + f[1] + " fuse withdrawn");
            if (mv != 0) faults.add("F" + s.index(f[0]) + " " + f[1] + " MCB " + (mv == 2 ? "TRIPPED" : "OFF"));
        }
        boolean[] k = {k1, k2, k3, closed};
        boolean clicked = false;
        for (int i = 0; i < 4; i++) {
            if (put(m, "cab_k" + (i + 1), k[i] ? 1 : 0)) { ch = true; clicked = true; }
            ch |= put(m, "cab_kl" + (i + 1), k[i] ? 1 : 0);
        }
        if (clicked) w.playSound(null, pos, ModSounds.RELAY, SoundCategory.BLOCKS, 0.5f, 1.1f + w.rand.nextFloat() * 0.2f);
        ch |= put(m, "cab_run", k1 ? 1 : 0);
        ch |= put(m, "cab_err", k1 && (k3 || !faults.isEmpty()) ? 1 : 0);
        boolean[] in = {k1, k2, s.doors.size() > 0 && !open(m, s.doors.get(0)), s.doors.size() > 1 ? !open(m, s.doors.get(1)) : closed, fusesOk, mcbsOn};
        for (int i = 0; i < in.length; i++) ch |= put(m, "cab_i" + i, in[i] ? 1 : 0);
        boolean[] q = {k1 && !k3 && faults.isEmpty(), k3 || !faults.isEmpty(), ok(m, "scr"), ok(m, "main") && ok(m, "out")};
        for (int i = 0; i < q.length; i++) ch |= put(m, "cab_q" + i, q[i] ? 1 : 0);
        ch |= put(m, "cab_light", !closed && k1 ? 1 : 0);                           // the door switch lights the inside
        String plc = !k1 ? "" : burnt ? "ERR BURNT OUT" : !faults.isEmpty() ? "ERR " + faults.iterator().next().split(" ")[0] : k3 ? "ALARM" : !closed ? "RUN  DOOR OPEN" : "RUN  OK";
        if (!plc.equals(text(m, "cab_plc"))) { text(m, "cab_plc", plc); ch = true; }
        String key0 = w.provider.getDimension() + ":" + pos.toLong();
        Long lt = LAMPTEST.get(key0);
        if (lt != null) {                                                              // lamp test: every lamp in the cabinet lit
            boolean on = w.getTotalWorldTime() < lt;
            if (!on) LAMPTEST.remove(key0);
            if (on && k1) for (Panel.C c : m.layout().all.cs) if (c.t == Panel.T.LAMP && c.id.startsWith("cab_")) ch |= put(m, c.id, 1);
        }
        if (ch) m.controlsChanged();
        // standing alarms for what is isolated, raised / cleared on change
        String key = w.provider.getDimension() + ":" + pos.toLong();
        Set<String> was = FAULTS.getOrDefault(key, Collections.emptySet());
        if (!was.equals(faults)) {
            AlarmManager am = AlarmManager.get(w);
            for (String f : was) if (!faults.contains(f)) am.clear("cab:" + pos.toLong() + ":" + f);
            for (String f : faults) if (!was.contains(f))
                am.raise(w, "cab:" + pos.toLong() + ":" + f, AlarmManager.Cat.WIRED, f.contains("BLOWN") || f.contains("TRIPPED") ? AlarmManager.Sev.MAJOR : AlarmManager.Sev.MINOR,
                        s.title + ": " + f, pos, true);
            if (faults.isEmpty()) FAULTS.remove(key); else FAULTS.put(key, faults);
        }
        if (s.hv && (closed || !energized(m))) AlarmManager.get(w).clear("cabdoor:" + pos.toLong());
    }

    static boolean has(Spec s, String fn) { for (String[] f : s.fns) if (f[0].equals(fn)) return true; return false; }

    // ---- /irextras cabinet ... ------------------------------------------------------------------------------------
    public static boolean command(World w, ICommandSender sender, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("cabinet")) return false;
        if (a.length >= 2 && a[1].equalsIgnoreCase("list")) {
            int n = 0;
            for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) {
                Spec s = of(t);
                if (s == null || !(t instanceof IControlled m)) continue;
                n++;
                say(sender, "§e" + s.title + " §7@ " + t.getPos().getX() + " " + t.getPos().getY() + " " + t.getPos().getZ() + " - " + summary(m, s));
            }
            say(sender, "§7" + n + " cabinet(s) loaded");
            return true;
        }
        if (a.length >= 2 && a[1].equalsIgnoreCase("specs")) {
            int n = 0;
            for (net.minecraft.block.Block b : net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS)
                if (b.getRegistryName() != null && RailMap.MODID.equals(b.getRegistryName().getNamespace()) && of(b.getRegistryName().getPath()) != null) {
                    Spec s = of(b.getRegistryName().getPath());
                    say(sender, "§e" + s.block + "§7: " + s.doors.size() + " door(s), " + s.fns.size() + " MCB+fuse ways" + (s.hv ? ", §cHV" : ""));
                    n++;
                }
            say(sender, "§7" + n + " machine types have opening cabinets");
            return true;
        }
        if (a.length < 5) {
            say(sender, "§7/irextras cabinet list | specs | <x y z> status|selftest|door <n>|mcb <fn>|fuse <fn>|blow <fn>|reset");
            return true;
        }
        BlockPos pos;
        try { pos = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3])); }
        catch (NumberFormatException ex) { say(sender, "§cx y z must be numbers"); return true; }
        TileEntity t = w.getTileEntity(pos);
        Spec s = of(t);
        if (s == null || !(t instanceof IControlled m)) { say(sender, "§cNo cabinet machine there"); return true; }
        String op = a[4].toLowerCase(Locale.ROOT), arg = a.length > 5 ? a[5] : "";
        switch (op) {
            case "status" -> say(sender, "§e" + s.title + "§7: " + summary(m, s));
            case "door" -> { Door d = arg.isEmpty() ? s.doors.get(0) : door(s, DOOR + arg); if (d == null) { say(sender, "§cNo door " + arg); return true; } put(m, d.id, open(m, d) ? 0 : 1); say(sender, "§7door " + d.id + (open(m, d) ? " OPEN" : " shut")); }
            case "mcb" -> { put(m, MCB + arg, get(m, MCB + arg) == 0 ? 1 : 0); say(sender, "§7F " + arg + " " + (ok(m, arg) ? "ON" : "OFF")); }
            case "fuse" -> { put(m, FUSE + arg, get(m, FUSE + arg) == 1 ? 0 : 1); say(sender, "§7FU " + arg + (get(m, FUSE + arg) == 1 ? " pulled" : " fitted")); }
            case "blow" -> { put(m, FUSE + arg, 2); w.playSound(null, pos, ModSounds.FUSE_BLOW, SoundCategory.BLOCKS, 1f, 1f); say(sender, "§cFU " + arg + " blown (fault injected)"); }
            case "reset" -> { reset(m, s); say(sender, "§aAll doors shut, MCBs ON, fuses fitted"); }
            case "selftest" -> selftest(m, s, sender);
            default -> say(sender, "§cUnknown: " + op);
        }
        update(m, s);
        m.controlsChanged();
        return true;
    }

    static void reset(IControlled m, Spec s) {
        for (Door d : s.doors) { put(m, d.id, 0); text(m, d.id + "_lk", ""); }
        for (String[] f : s.fns) { put(m, MCB + f[0], 0); put(m, FUSE + f[0], 0); }
        apply(m, s);
    }

    static String summary(IControlled m, Spec s) {
        StringBuilder b = new StringBuilder();
        for (Door d : s.doors) b.append(d.id.substring(DOOR.length() - 4)).append(open(m, d) ? "=OPEN " : "=shut ").append(text(m, d.id + "_lk").isEmpty() ? "" : "(locked) ");
        for (String[] f : s.fns) {
            int mv = get(m, MCB + f[0]), fv = get(m, FUSE + f[0]);
            b.append(ok(m, f[0]) ? "§a" : "§c").append(f[1]).append(mv == 0 ? "" : mv == 2 ? " MCB TRIP" : " MCB OFF").append(fv == 0 ? "" : fv == 1 ? " FUSE OUT" : " FUSE BLOWN").append(ok(m, f[0]) ? " ok" : "").append("§7 ");
        }
        for (int i = 1; i <= 4; i++) b.append("K").append(i).append(get(m, "cab_k" + i) == 1 ? "↑ " : "↓ ");
        if (m instanceof IWiredGear g) b.append("· 12 V ").append(g.term().powered() ? "§aon" : "§coff");
        return b.toString();
    }

    /** proves every part really does what it says: each MCB / fuse isolates its function and comes back */
    static void selftest(IControlled m, Spec s, ICommandSender sender) {
        TileEntity t = (TileEntity) m;
        int pass = 0, fail = 0;
        Map<String, Integer> saved = new HashMap<>();
        for (Door d : s.doors) saved.put(d.id, get(m, d.id));
        for (String[] f : s.fns) { saved.put(MCB + f[0], get(m, MCB + f[0])); saved.put(FUSE + f[0], get(m, FUSE + f[0])); }
        GridData.Node gn = t instanceof TileGrid ? GridData.get(t.getWorld()).node(t.getPos()) : null;
        boolean dcOn = gn != null && gn.dcOn, batOn = gn != null && gn.batOn;          // the test opens the output: put it back after
        reset(m, s);
        for (Door d : s.doors) {
            put(m, d.id, 1); update(m, s);
            boolean a = open(m, d) && get(m, "cab_k4") == 0;
            put(m, d.id, 0); update(m, s);
            boolean b = !open(m, d);
            if (a && b) pass++; else { fail++; say(sender, "§cFAIL door " + d.id + " (door relay K4 did not drop)"); }
        }
        for (String[] f : s.fns) {
            for (String part : new String[]{MCB, FUSE}) {
                put(m, part + f[0], FUSE.equals(part) ? 2 : 1);
                apply(m, s);
                boolean isolated = !ok(m, f[0]) && isolatedReally(m, f[0], false);
                put(m, part + f[0], 0);
                apply(m, s);
                boolean back = ok(m, f[0]) && isolatedReally(m, f[0], true);
                if (isolated && back) pass++;
                else { fail++; say(sender, "§cFAIL " + (MCB.equals(part) ? "F" : "FU") + s.index(f[0]) + " " + f[1] + (isolated ? " did not come back" : " did not isolate " + what(f[0]))); }
            }
        }
        for (Map.Entry<String, Integer> e : saved.entrySet()) put(m, e.getKey(), e.getValue());
        if (gn != null && ok(m, "out")) { gn.dcOn = dcOn; gn.batOn = batOn; GridData.get(t.getWorld()).touch(); }
        apply(m, s);
        update(m, s);
        say(sender, (fail == 0 ? "§a" : "§c") + s.title + " cabinet self-test: " + pass + " passed, " + fail + " failed");
    }

    /** does the machine itself see the function in the expected state (expectOn) */
    static boolean isolatedReally(IControlled m, String fn, boolean expectOn) {
        TileEntity t = (TileEntity) m;
        switch (fn) {
            case "ctl":
                if (m instanceof IWiredGear g) return g.term().isolated != expectOn;
                return ok(m, "ctl") == expectOn;
            case "main": {
                Object o = mains(t);
                if (o instanceof com.dogpound.railmap.depot.Mains x) return x.isolated != expectOn;
                if (o instanceof com.dogpound.railmap.infra.Supply x) return x.isolated != expectOn;
                return true;
            }
            case "scr": return ok(m, "scr") == expectOn && (expectOn || screensDark(m));
            case "out": {
                GridData.Node n = t instanceof TileGrid ? GridData.get(t.getWorld()).node(t.getPos()) : null;
                if (n == null || expectOn) return ok(m, fn) == expectOn;
                return !(((TileGrid) t).kind() == GridKind.BATTERY ? n.batOn : n.dcOn);
            }
            default: return ok(m, fn) == expectOn;
        }
    }

    /** every display on the machine is blank */
    static boolean screensDark(IControlled m) {
        for (Panel.C c : m.layout().all.cs) if (c.t == Panel.T.DIGITS && "%s".equals(c.fmt) && !c.id.startsWith("cab_") && !m.ptext(c.id).isEmpty()) return false;
        return true;
    }

    private static void say(ICommandSender s, String msg) { s.sendMessage(new TextComponentString(msg)); }
}
