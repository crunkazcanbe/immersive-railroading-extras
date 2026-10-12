package com.dogpound.railmap.ohle;

import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.server.RailProps;
import com.dogpound.railmap.safety.SafetyTile;
import com.dogpound.railmap.signal.BlockCatenary;
import com.dogpound.railmap.weather.Weather;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Motorised OHLE isolator (her ChatGPT list #31): the off-load switch on a mast beside a Section Insulator. CLOSED it
 * bridges the insulator (the two sections are one); OPEN it splits them. The motor needs 12 V and takes 4 s; with no
 * 12 V the handle cranks it by hand (4 turns). LOCAL: only the buttons here work; REMOTE: the Feeding Diagram / the
 * console / a wired desk work it; AUTO: remote + the feeder protection opens it by itself to cut a faulted section off
 * before reclosing. The earth switch (key) earths one side for a maintenance crew - only with the isolator open and
 * that side dead; while earthed the isolator can't close. Isolators are off-load switches: working one while a train
 * draws current through it draws an arc (flashover, the feeder trips). Linked automatically to the nearest Section
 * Insulator within 6 blocks, or wrench-pick an insulator and click the LINK lamp.
 */
public class TileIsolator extends SafetyTile {
    static final Reg<TileIsolator> REG = new Reg<>();

    public static Collection<TileIsolator> all(net.minecraft.world.World w) { return REG.all(w); }

    private long link, earthPos;
    private boolean closed = true, target = true;
    private int moving, cranks, ops;
    private String lastBy = "";

    public long link() { return link; }
    public long earthPos() { return earthed() ? earthPos : 0; }
    public boolean closed() { return closed; }
    public boolean earthed() { return ctl.get("k_earth") == 1; }
    public int mode() { return ctl.get("s_mode"); }          // 0 LOCAL, 1 REMOTE, 2 AUTO
    public boolean auto() { return mode() == 2; }
    public boolean moving() { return moving > 0; }

    @Override public void onLoad() { REG.add(this); }
    @Override public void invalidate() { super.invalidate(); REG.remove(this); Ohle.dirty(); }
    @Override public void onChunkUnload() { REG.remove(this); }

    @Override public String wiredName() { return "OHLE isolator " + Ohle.xz(pos); }
    @Override public String gearState() { return (moving > 0 ? (target ? "CLOSING" : "OPENING") : closed ? "CLOSED" : "OPEN") + (earthed() ? " + EARTHED side " + side() : "") + " · " + MODES[mode()] + (link == 0 ? " · NOT LINKED" : ""); }
    @Override public int wiredLamp() { return !powered() ? 3 : earthed() ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("closed", closed); m.put("earthed", earthed()); m.put("mode", MODES[mode()]); m.put("linked", link != 0); m.put("operations", ops); }

    static final String[] MODES = {"LOCAL", "REMOTE", "AUTO"};

    private String side() { return ctl.get("s_side") == 0 ? "A" : "B"; }

    @Override
    public int controlValue(String id) {
        if (id.equals("flag")) return closed ? 1 : 0;                        // the mechanical position flag needs no power
        return super.controlValue(id);
    }

    /** the wire blocks either side of the linked insulator, along the line: {A, B} */
    BlockPos[] sides() {
        if (link == 0) return new BlockPos[0];
        BlockPos ins = BlockPos.fromLong(link);
        net.minecraft.block.state.IBlockState st = world.getBlockState(ins);
        EnumFacing f = st.getPropertyKeys().contains(BlockCatenary.FACING) ? st.getValue(BlockCatenary.FACING) : EnumFacing.NORTH;
        BlockPos[] out = new BlockPos[2];
        for (int s = 0; s < 2; s++) {
            EnumFacing g = s == 0 ? f.getOpposite() : f;
            for (int dy = 0; dy >= -1 && out[s] == null; dy = dy == 0 ? 1 : dy == 1 ? -1 : -2) {
                BlockPos q = ins.offset(g).up(dy);
                if (Ohle.kindAt(world, q) == BlockCatenary.Kind.WIRE) out[s] = q;
            }
        }
        return out;
    }

    private void relink() {
        if (link != 0 && Ohle.kindAt(world, BlockPos.fromLong(link)) == BlockCatenary.Kind.SECTION_INSULATOR) return;
        long best = 0;
        double bd = Double.MAX_VALUE;
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -6; dz <= 6; dz++)
                for (int dy = -2; dy <= 10; dy++) {
                    BlockPos q = pos.add(dx, dy, dz);
                    if (Ohle.kindAt(world, q) != BlockCatenary.Kind.SECTION_INSULATOR) continue;
                    double d = q.distanceSq(pos);
                    if (d < bd) { bd = d; best = q.toLong(); }
                }
        if (best != link) { link = best; Ohle.dirty(); markDirty(); }
    }

    @Override
    protected void serverTick() {
        if (moving > 0) {
            moving--;
            put("amps", powered() ? 6 + world.rand.nextInt(3) : 0);
            if (moving == 0) finish();
        }
        if (age % 20 != 3) return;
        relink();
        BlockPos[] sd = sides();
        Ohle.Section a = sd.length > 0 && sd[0] != null ? Ohle.section(world, sd[0]) : null, b = sd.length > 1 && sd[1] != null ? Ohle.section(world, sd[1]) : null;
        put("lclosed", closed && moving == 0);
        put("lopen", !closed && moving == 0);
        put("lmove", moving > 0);
        put("learth", earthed());
        put("llink", link != 0);
        put("liveA", a != null && a.energised);
        put("liveB", b != null && b.energised);
        put("lfault", Weather.floodMm(world, pos) > 300 || link == 0);
        put("pwr", powered());
        put("ops", ops);
        if (moving == 0) put("amps", 0);
        text("state", moving > 0 ? (target ? "CLOSING" : "OPENING") : (closed ? "CLOSED" : "OPEN") + (earthed() ? " EARTH " + side() : ""));
        text("secA", a == null ? "A: NO WIRE" : "A " + a.state());
        text("secB", b == null ? "B: NO WIRE" : "B " + b.state());
    }

    private void finish() {
        closed = target;
        ops++;
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("grid.breaker_close"), net.minecraft.util.SoundCategory.BLOCKS, 1.0f, 0.9f);
        log((closed ? "CLOSED" : "OPENED") + " by " + lastBy);
        Ohle.dirty();
        markDirty();
    }

    /** the feeder protection's fault isolation: open now (AUTO, 12 V, not already moving) */
    boolean autoOpen(String why) {
        if (!auto() || !powered() || moving > 0 || !closed) return false;
        lastBy = why;
        target = false;
        moving = 80;
        return true;
    }

    /** from the feeding diagram / console / command (REMOTE or AUTO only) */
    public String remote(Boolean close, String who) {
        String r = drive(close == null ? !closed : close, who, true);
        controlsChanged();
        return r;
    }

    /** start the motor (or refuse, saying why) */
    private String drive(boolean close, String who, boolean remote) {
        if (remote && mode() == 0) return "§cIsolator in LOCAL - it can only be worked at the mast";
        if (!remote && mode() != 0) return "§cIsolator in " + MODES[mode()] + " - turn the selector to LOCAL to work it here";
        if (moving > 0) return "§7Already moving";
        if (close == closed) return "§7Already " + (closed ? "CLOSED" : "OPEN");
        if (close && earthed()) return "§cEarth switch is ON - take the earth off before closing";
        if (!powered()) return "§cNo 12 V for the motor - use the hand crank";
        if (Weather.floodMm(world, pos) > 300) return "§cMotor drowned - the isolator stands in flood water";
        if (underLoad()) return flashover(who);
        lastBy = who;
        target = close;
        moving = 80;
        return "§e" + (close ? "Closing" : "Opening") + " (4 s)";
    }

    /** working an off-load switch with a train drawing through it */
    private boolean underLoad() {
        BlockPos[] sd = sides();
        if (sd.length < 2 || sd[0] == null || sd[1] == null || !closed) return false;
        // with the isolator closed both sides are one section; does a train draw from wire that only this isolator feeds?
        Ohle.Section ea = Ohle.build(world, sd[0], true), eb = Ohle.build(world, sd[1], true);
        if (ea == null || eb == null) return false;
        Ohle.Section whole = Ohle.section(world, sd[0]);
        if (whole == null || !whole.energised) return false;
        boolean fedA = false, fedB = false;
        for (long f : whole.feeders) {
            BlockPos fp = BlockPos.fromLong(f);
            for (long w : ea.wires) if (BlockPos.fromLong(w).distanceSq(fp) < 150) { fedA = true; break; }
            for (long w : eb.wires) if (BlockPos.fromLong(w).distanceSq(fp) < 150) { fedB = true; break; }
        }
        if (fedA == fedB) return false;
        Ohle.Section unfed = fedA ? eb : ea;
        RailProps props = RailProps.get(world);
        for (LocomotiveDiesel l : cam72cam.mod.world.World.get(world).getEntities(LocomotiveDiesel.class)) {
            RailProps.Props pr = props.peek(l.getUUID());
            if (pr == null || pr.kwDraw <= 0) continue;
            BlockPos at = new BlockPos(l.getPosition().x, l.getPosition().y, l.getPosition().z);
            for (int dy = 2; dy <= 7; dy++)
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                        if (unfed.wires.contains(at.add(dx, dy, dz).toLong())) return true;
        }
        return false;
    }

    private String flashover(String who) {
        Ohle.arc(world, BlockPos.fromLong(link), 30);
        Ohle.fault(world, BlockPos.fromLong(link), "SHORT", "isolator " + Ohle.xz(pos) + " worked ON LOAD by " + who + " - arc across the contacts", false);
        log("FLASHOVER - worked on load by " + who);
        return "§c⚡ FLASHOVER - an isolator is an off-load switch: a train was drawing current through it";
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        boolean remote = who == null;
        String w = who(who);
        switch (id) {
            case "test": return lampTest();
            case "close": return drive(true, w, remote);
            case "open": return drive(false, w, remote);
            case "s_mode":
                if (remote) return "§cThe LOCAL / REMOTE / AUTO selector is at the mast";
                ctl.set("s_mode", (mode() + 1) % 3);
                log("control mode " + MODES[mode()] + " (" + w + ")");
                return "§eIsolator control: " + MODES[mode()];
            case "s_side":
                if (earthed()) return "§cTake the earth off before changing the side";
                ctl.set("s_side", 1 - ctl.get("s_side"));
                return "§eEarth switch side " + side();
            case "crank": {
                if (remote) return "§cThe hand crank is at the mast";
                if (mode() != 0) return "§cHand cranking only in LOCAL";
                if (closed && earthed()) return "§cEarthed";
                if (!closed && earthed()) return "§cEarth switch is ON - take the earth off before closing";
                if (underLoad()) return flashover(w);
                cranks++;
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("grid.relay_clunk"), net.minecraft.util.SoundCategory.BLOCKS, 0.8f, 1.0f);
                if (cranks < 4) return "§7Cranking... " + cranks + "/4 turns";
                cranks = 0;
                lastBy = w + " (hand crank)";
                target = !closed;
                finish();
                return "§e" + (closed ? "CLOSED" : "OPENED") + " by hand";
            }
            case "k_earth": {
                if (remote) return "§cThe earth switch is worked at the mast";
                if (earthed()) {
                    ctl.set("k_earth", 0);
                    earthPos = 0;
                    log("earth OFF by " + w);
                    Ohle.dirty();
                    return "§aEarth switch OFF";
                }
                if (closed || moving > 0) return "§cOpen the isolator first - the earth switch is interlocked with it";
                BlockPos[] sd = sides();
                BlockPos e = sd.length < 2 ? null : sd[ctl.get("s_side")];
                if (e == null) return "§cNo contact wire on side " + side() + " of the insulator";
                Ohle.Section s = Ohle.section(world, e);
                if (s != null && (s.energised || s.legacy() && !s.faulted && Ohle.kindAt(world, e) != null && com.dogpound.railmap.grid.GridTicker.whyDead(world, e, true).isEmpty() && !com.dogpound.railmap.grid.GridData.get(world).nodes().isEmpty())) {
                    Ohle.arc(world, e, 40);
                    if (who != null && !who.capabilities.isCreativeMode) who.attackEntityFrom(net.minecraft.util.DamageSource.LIGHTNING_BOLT, 6f);
                    Ohle.fault(world, e, "EARTH", "earth switch at " + Ohle.xz(pos) + " closed onto a LIVE section", false);
                    return "§c⚡ Side " + side() + " is LIVE - earthing it is a dead short. Open its feeder first";
                }
                ctl.set("k_earth", 1);
                earthPos = e.toLong();
                log("EARTHED side " + side() + " (" + (s == null ? "?" : s.name()) + ") by " + w + " - safe for work");
                Ohle.dirty();
                return "§eSide " + side() + " EARTHED - safe for a crew";
            }
            default: return "§7" + gearState();
        }
    }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        if (Ohle.kindAt(world, pick) != BlockCatenary.Kind.SECTION_INSULATOR) return "§cPick a Section Insulator with the wrench";
        link = pick.toLong();
        Ohle.dirty();
        markDirty();
        return "§aLinked to the section insulator at " + Ohle.xyz(pick);
    }

    private void log(String s) {
        OhleData.get(world).log(world, "isolator " + Ohle.xz(pos) + ": " + s);
        Recorder.event(world, "OHLE ISOLATOR " + Ohle.xz(pos) + ": " + s);
    }

    @Override protected void save(NBTTagCompound t) { t.setLong("link", link); t.setLong("earth", earthPos); t.setBoolean("closed", moving > 0 ? target : closed); t.setInteger("ops", ops); }
    @Override protected void load(NBTTagCompound t) { link = t.getLong("link"); earthPos = t.getLong("earth"); closed = !t.hasKey("closed") || t.getBoolean("closed"); target = closed; ops = t.getInteger("ops"); }

    static List<TileIsolator> sorted(net.minecraft.world.World w, BlockPos from) {
        List<TileIsolator> l = new java.util.ArrayList<>(all(w));
        l.sort(java.util.Comparator.comparingDouble(i -> i.getPos().distanceSq(from)));
        return l;
    }
}
