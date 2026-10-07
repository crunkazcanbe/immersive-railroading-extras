package com.dogpound.railmap.signal;

import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.RailMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A signal mast: one to three heads on a post, governing the track it faces.
 * <p>
 * <b>Automatic (default).</b> Every second the {@link SignalEngine} follows the track ahead,
 * counts how many blocks are clear, and sets the aspect — real ABS. A block ends at an
 * insulated joint or the next facing signal.
 * <p>
 * <b>Redstone.</b> Every mast is also a redstone device, which is how you wire it into your own
 * logic or an interlocking:
 * <ul>
 *   <li><b>Output</b> — the mast powers the block it sits on: 15 = Clear, 7 = Approach,
 *       3 = Restricting, 0 = Stop. Put a comparator or a repeater next to the base.</li>
 *   <li><b>Input</b> — powering the mast HOLDS it at Stop, whatever the track says. That is
 *       exactly what a dispatcher's hold does on a real control point.</li>
 *   <li><b>{@link Mode#REDSTONE}</b> — the mast ignores the track entirely and takes its
 *       aspect from the input strength: 0 Stop, 1-5 Restricting, 6-10 Approach, 11+ Clear.</li>
 * </ul>
 */
public class TileSignalMast extends TileEntity implements ITickable, IScalable, com.dogpound.railmap.settings.ISettingsHolder {
    private final com.dogpound.railmap.settings.SettingsStore cfg = new com.dogpound.railmap.settings.SettingsStore(this);
    private int changes;

    @Override public String settingsTitle() { return style.label; }
    @Override public com.dogpound.railmap.settings.SettingsStore settings() { return cfg; }

    private static String[] modeLabels() { Mode[] v = Mode.values(); String[] o = new String[v.length]; for (int i = 0; i < v.length; i++) o[i] = v[i].label; return o; }
    private static String[] styleLabels() { SignalStyle[] v = SignalStyle.values(); String[] o = new String[v.length]; for (int i = 0; i < v.length; i++) o[i] = v[i].label; return o; }
    private static final Aspect[] MANUAL = { Aspect.STOP, Aspect.STOP_AND_PROCEED, Aspect.RESTRICTING, Aspect.APPROACH, Aspect.MEDIUM_APPROACH, Aspect.APPROACH_MEDIUM, Aspect.MEDIUM_CLEAR, Aspect.ADVANCE_APPROACH, Aspect.CLEAR, Aspect.DARK };
    private static String[] manualLabels() { String[] o = new String[MANUAL.length]; for (int i = 0; i < MANUAL.length; i++) o[i] = MANUAL[i].label; return o; }

    @Override
    public java.util.List<com.dogpound.railmap.settings.Setting> settingDefs() {
        // the mast's own fields are the truth: mirror them into the console every time
        cfg.put("mode", mode.label); cfg.put("style", style.label); cfg.put("heads", Integer.toString(heads));
        cfg.put("perm", Boolean.toString(permissive)); cfg.put("manual", manualAspect.label);
        java.util.List<com.dogpound.railmap.settings.Setting> l = new java.util.ArrayList<>();
        String c = "Control", a = "Appearance", r = "Redstone", st = "Status";
        l.add(com.dogpound.railmap.settings.Setting.text(c, "plate", "Number plate", "The signal's name/number (status + board)", "", 16));
        l.add(com.dogpound.railmap.settings.Setting.choice(c, "mode", "Control mode", "", Mode.AUTO.label, modeLabels()));
        l.add(com.dogpound.railmap.settings.Setting.choice(c, "manual", "Fixed aspect", "Shown in 'Fixed aspect' mode", Aspect.STOP.label, manualLabels()));
        l.add(com.dogpound.railmap.settings.Setting.bool(c, "perm", "Permissive (number plate)", "Stop becomes Stop-and-Proceed on automatic signals", false));
        l.add(com.dogpound.railmap.settings.Setting.bool(c, "rsHold", "Redstone holds it at Stop", "On automatic / CTC signals, a redstone input forces Stop", true));
        l.add(com.dogpound.railmap.settings.Setting.choice(c, "rsMap", "Redstone strength in redstone mode", "How redstone picks the aspect", "4 steps", "4 steps", "On = Clear, off = Stop", "On = Approach, off = Stop"));
        l.add(com.dogpound.railmap.settings.Setting.choice(a, "style", "Signal type", "", SignalStyle.COLOR_LIGHT.label, styleLabels()));
        l.add(com.dogpound.railmap.settings.Setting.num(a, "heads", "Heads", "Number of signal heads (up to the type's maximum)", 1, 1, 3, 1, ""));
        l.add(com.dogpound.railmap.settings.Setting.choice(r, "rsOut", "Redstone output", "", "By aspect (0-15)", "By aspect (0-15)", "15 when clear", "15 when at stop", "Off"));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Showing", aspect.label));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Blocks clear ahead", String.valueOf(clearBlocks)));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Block length", blockLength > 0 ? Math.round(blockLength) + " m" : "-"));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Track governed", governed == null ? "none found" : governed.getX() + ", " + governed.getY() + ", " + governed.getZ()));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Relay case", relayHold ? "HOLDING at Stop" : relayAspect != null ? "driving " + relayAspect.label : "not wired"));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "CTC route", mode == Mode.CTC ? (routeSet ? "SET" : "none") : "-"));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Aspect changes", String.valueOf(changes)));
        return l;
    }

    @Override
    public void onSettingsChanged(String key) {
        switch (key) {
            case "mode" -> { for (Mode m : Mode.values()) if (m.label.equals(cfg.text("mode"))) mode = m; }
            case "manual" -> { for (Aspect x : MANUAL) if (x.label.equals(cfg.text("manual"))) manualAspect = x; }
            case "perm" -> permissive = cfg.bool("perm");
            case "style" -> { for (SignalStyle x : SignalStyle.values()) if (x.label.equals(cfg.text("style"))) style = x; heads = Math.min(heads, style.maxHeads); }
            case "heads" -> heads = Math.max(1, Math.min(style.maxHeads, cfg.num("heads")));
            default -> { }
        }
        markDirty();
        sync();
        if (world != null) world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
    }

    /** Drawn size, 1 = normal; set with the Signal Wrench (sneak-right-click). */
    private float scale = 1f;

    @Override
    public float scale() {
        return scale;
    }

    @Override
    public void setScale(float s) {
        scale = IScalable.clamp(s);
        markDirty();
        sync();
    }

    /** How this mast decides what to show. */
    public enum Mode {
        AUTO("Automatic (track circuits)"),
        REDSTONE("Redstone controlled"),
        MANUAL("Fixed aspect"),
        /** Absolute signal at a control point: Stop until a dispatcher route is set through it. */
        CTC("Dispatcher controlled (CTC)");

        public final String label;

        Mode(String label) { this.label = label; }

        public Mode next() {
            Mode[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    private SignalStyle style = SignalStyle.COLOR_LIGHT;
    private int heads = 1;
    private Mode mode = Mode.AUTO;
    /** Permissive signals carry a number plate: a train may pass a red at restricted speed. */
    private boolean permissive;
    /** Aspect actually displayed. */
    private Aspect aspect = Aspect.DARK;
    /** Aspect chosen in MANUAL mode. */
    private Aspect manualAspect = Aspect.STOP;
    /** The track piece this mast governs; resolved once, then cached. */
    private BlockPos governed;
    private boolean governedResolved;
    /** A linked relay case is holding this signal at Stop. */
    private boolean relayHold;
    /** A linked relay case in Drive mode is dictating this aspect; null = not driven. */
    private Aspect relayAspect;
    /** CTC: a route is lined and locked through this signal, so it may show a proceed aspect. */
    private boolean routeSet;
    /** Set by the engine for the board readout: blocks clear ahead, and the distance walked. */
    private int clearBlocks;
    private double blockLength;
    private int ticks;

    // ---- configuration -------------------------------------------------------------------

    public SignalStyle style() { return style; }
    public int heads() { return heads; }
    public Mode mode() { return mode; }
    public boolean permissive() { return permissive; }
    public Aspect aspect() { return aspect; }
    public int clearBlocks() { return clearBlocks; }
    public double blockLength() { return blockLength; }

    public void configure(SignalStyle style, int heads) {
        this.style = style;
        this.heads = Math.max(1, Math.min(style.maxHeads, heads));
        markDirty();
        sync();
    }

    public boolean routeSet() { return routeSet; }

    /** Line (or drop) a CTC route through this signal. Only a CTC-mode signal cares. */
    public void setRoute(boolean set) {
        if (set == routeSet) return;
        routeSet = set;
        markDirty();
        sync();
    }

    public void cycleModeTo(Mode m) {
        if (mode == m) return;
        mode = m;
        markDirty();
        sync();
    }

    public void cycleMode() {
        mode = mode.next();
        markDirty();
        sync();
    }

    public void togglePermissive() {
        permissive = !permissive;
        markDirty();
        sync();
    }

    public Aspect manualAspect() {
        return manualAspect;
    }

    public void cycleManualAspect() {
        Aspect[] pick = { Aspect.STOP, Aspect.RESTRICTING, Aspect.APPROACH, Aspect.CLEAR };
        int i = 0;
        for (int k = 0; k < pick.length; k++) if (pick[k] == manualAspect) i = k + 1;
        manualAspect = pick[i % pick.length];
        markDirty();
        sync();
    }

    public EnumFacing facing() {
        IBlockState s = world.getBlockState(pos);
        return s.getBlock() instanceof BlockSignalMast ? s.getValue(BlockSignalMast.FACING) : EnumFacing.NORTH;
    }

    // ---- the track it governs ------------------------------------------------------------

    /**
     * Nearest IR track piece in front of the mast. A signal stands beside the track it governs
     * and faces the oncoming train, so we look along its facing.
     */
    public Vec3i governedRail() {
        if (!governedResolved && world != null && !world.isRemote) {
            governedResolved = true;
            governed = SignalEngine.findGovernedRail(world, pos, facing());
        }
        return governed == null ? null : new Vec3i(governed.getX(), governed.getY(), governed.getZ());
    }

    public void forgetGovernedRail() {
        governedResolved = false;
        governed = null;
    }

    // ---- ticking -------------------------------------------------------------------------

    @Override
    public void update() {
        if (world.isRemote || ++ticks % 20 != 0) return;
        // The engine drives AUTO masts in one pass; redstone/manual masts settle themselves.
        if (relayHold) { applyAspect(Aspect.STOP); return; }
        if (relayAspect != null) { applyAspect(relayAspect); return; }
        if (mode != Mode.AUTO && mode != Mode.CTC) applyAspect(computeNonAuto());
    }

    private Aspect computeNonAuto() {
        if (mode == Mode.MANUAL) return manualAspect;
        int power = world.getRedstonePowerFromNeighbors(pos);
        if (power <= 0) return Aspect.STOP;
        if ("On = Clear, off = Stop".equals(cfg.text("rsMap"))) return Aspect.CLEAR;
        if ("On = Approach, off = Stop".equals(cfg.text("rsMap"))) return Aspect.APPROACH;
        if (power <= 5) return Aspect.RESTRICTING;
        if (power <= 10) return Aspect.APPROACH;
        return Aspect.CLEAR;
    }

    /**
     * A {@link TileRelayCase} this signal is linked to setting its will. Hold wins over
     * everything; a driven aspect replaces what the track says; both off gives the signal
     * back to the track.
     */
    public void setRelayControl(boolean hold, Aspect driven) {
        boolean changed = hold != relayHold || driven != relayAspect;
        relayHold = hold;
        relayAspect = driven;
        if (!changed) return;
        if (relayHold) applyAspect(Aspect.STOP);
        else if (relayAspect != null) applyAspect(relayAspect);
        markDirty();
    }

    public boolean relayControlled() {
        return relayHold || relayAspect != null;
    }

    /** Called by {@link SignalEngine} for AUTO masts. */
    public void setComputed(Aspect a, int clear, double length) {
        clearBlocks = clear;
        blockLength = length;
        // A relay case outranks the track: that is the point of wiring one in.
        if (relayHold) { applyAspect(Aspect.STOP); return; }
        if (relayAspect != null) { applyAspect(relayAspect); return; }
        // A redstone input on an automatic signal is a dispatcher's HOLD: force it to danger.
        if (cfg.bool("rsHold") && world.getRedstonePowerFromNeighbors(pos) > 0) a = Aspect.STOP;
        else if (mode == Mode.CTC && !routeSet) a = Aspect.STOP;
        else if (a.isStop() && permissive) a = Aspect.STOP_AND_PROCEED;
        applyAspect(a);
    }

    private void applyAspect(Aspect a) {
        if (a == aspect) return;
        aspect = a;
        changes++;
        markDirty();
        sync();
        // Our own redstone output changed, so neighbours need to re-read us.
        world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
    }

    /** Redstone strength this mast emits: the more permissive the aspect, the stronger. */
    public int redstoneOutput() {
        switch (cfg.text("rsOut")) {
            case "15 when clear": return aspect == Aspect.CLEAR ? 15 : 0;
            case "15 when at stop": return aspect.isStop() ? 15 : 0;
            case "Off": return 0;
            default: break;
        }
        switch (aspect) {
            case CLEAR: return 15;
            case ADVANCE_APPROACH: return 12;
            case APPROACH_MEDIUM:
            case MEDIUM_CLEAR: return 10;
            case APPROACH:
            case MEDIUM_APPROACH: return 7;
            case RESTRICTING: return 3;
            default: return 0;
        }
    }

    private void sync() {
        if (world == null || world.isRemote) return;
        IBlockState s = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, s, s, 3);
    }

    // ---- lifecycle -----------------------------------------------------------------------

    @Override
    public void onLoad() {
        if (world != null && !world.isRemote) SignalRegistry.addMast(world.provider.getDimension(), this);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (world != null && !world.isRemote) SignalRegistry.removeMast(world.provider.getDimension(), pos);
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (world != null && !world.isRemote) SignalRegistry.removeMast(world.provider.getDimension(), pos);
    }

    // ---- persistence ---------------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        if (scale != 1f) t.setFloat("scale", scale);
        t.setByte("style", (byte) style.ordinal());
        t.setByte("heads", (byte) heads);
        t.setByte("mode", (byte) mode.ordinal());
        t.setBoolean("perm", permissive);
        t.setByte("aspect", (byte) aspect.ordinal());
        t.setByte("manual", (byte) manualAspect.ordinal());
        t.setInteger("clear", clearBlocks);
        t.setBoolean("route", routeSet);
        t.setInteger("changes", changes);
        cfg.write(t);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        scale = t.hasKey("scale") ? IScalable.clamp(t.getFloat("scale")) : 1f;
        style = SignalStyle.byOrdinal(t.getByte("style"));
        heads = Math.max(1, Math.min(style.maxHeads, t.getByte("heads")));
        Mode[] modes = Mode.values();
        int m = t.getByte("mode");
        mode = m >= 0 && m < modes.length ? modes[m] : Mode.AUTO;
        permissive = t.getBoolean("perm");
        Aspect[] all = Aspect.values();
        int a = t.getByte("aspect");
        aspect = a >= 0 && a < all.length ? all[a] : Aspect.DARK;
        int ma = t.getByte("manual");
        manualAspect = ma >= 0 && ma < all.length ? all[ma] : Aspect.STOP;
        clearBlocks = t.getInteger("clear");
        routeSet = t.getBoolean("route");
        changes = t.getInteger("changes");
        cfg.read(t);
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    /** Lamps glow, so the whole mast must stay rendered while any part of it is on screen. */
    @Override
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        return new net.minecraft.util.math.AxisAlignedBB(pos).grow(scale, 5 * scale, scale);
    }

    public String statusLine() {
        StringBuilder sb = new StringBuilder();
        if (!cfg.text("plate").trim().isEmpty()) sb.append("Signal ").append(cfg.text("plate").trim()).append(" · ");
        sb.append(style.label).append(" · ").append(heads).append(heads == 1 ? " head" : " heads");
        sb.append(" · ").append(mode.label);
        if (permissive) sb.append(" · permissive");
        if (relayHold) sb.append(" · HELD by a relay case");
        else if (relayAspect != null) sb.append(" · driven by a relay case");
        sb.append(" · showing ").append(aspect.label);
        if (mode == Mode.CTC) sb.append(routeSet ? " · ROUTE SET" : " · no route (set one from the board)");
        if (mode == Mode.AUTO || mode == Mode.CTC) {
            sb.append(" · ").append(clearBlocks).append(" block(s) clear");
            if (blockLength > 0) sb.append(" · block ").append(Math.round(blockLength)).append("m");
            if (governed == null) sb.append(" · NO TRACK FOUND");
        }
        return sb.toString();
    }

    static {
        RailMap.LOG.debug("[RailMap] signal mast class loaded");
    }
}
