package com.dogpound.railmap.signal;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The grey box beside the track — and the place you wire redstone into.
 * <p>
 * Link signals to it with the {@link com.dogpound.railmap.item.ItemSignalWrench Signal Wrench}:
 * right-click a signal, then right-click this box. One box can hold as many signals as you
 * like, which is the whole point — a junction's worth of heads on one lever.
 * <p>
 * What the redstone does depends on the box's mode, cycled by right-clicking it bare-handed:
 * <ul>
 *   <li><b>Hold</b> (default) — power the box and every linked signal goes to Stop and stays
 *       there. Cut the power and they go back to reading the track. A dispatcher's lever.</li>
 *   <li><b>Drive</b> — the linked signals stop watching the track and show what the input
 *       strength says: 0 Stop, 1-5 Restricting, 6-10 Approach, 11+ Clear.</li>
 *   <li><b>Report</b> — the box ignores its input and instead <em>emits</em> the state of what
 *       is linked: 15 when every linked signal is clear, 0 when any of them is at Stop. Feed
 *       that into your own contraption.</li>
 * </ul>
 * In every mode the box also emits the report value on its comparator output, so you can read
 * the railroad without giving up the control modes.
 */
public class TileRelayCase extends TileEntity implements ITickable, com.dogpound.railmap.settings.ISettingsHolder {
    public enum Mode {
        HOLD("Hold at Stop when powered"),
        DRIVE("Aspect follows input strength"),
        REPORT("Output only — reports linked signals");

        public final String label;

        Mode(String label) { this.label = label; }

        public Mode next() {
            Mode[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    /** How far a signal may be from its box. Generous: interlockings are spread out. */
    public static final int LINK_RANGE = 64;

    private final List<BlockPos> linked = new ArrayList<>();
    private Mode mode = Mode.HOLD;
    private int lastPower = -1;
    private int ticks;

    public Mode mode() { return mode; }
    public List<BlockPos> linked() { return linked; }
    public int linkCount() { return linked.size(); }

    public void cycleMode() {
        mode = mode.next();
        cfg.put("mode", mode.label);
        lastPower = -1;         // re-apply under the new rules on the next tick
        markDirty();
        sync();
    }

    /** Returns a line for the player: linked, already linked (so unlinked), or out of range. */
    public String toggleLink(BlockPos target) {
        if (target.distanceSq(pos) > (double) LINK_RANGE * LINK_RANGE) {
            return "That signal is more than " + LINK_RANGE + " blocks away";
        }
        for (int i = 0; i < linked.size(); i++) {
            if (linked.get(i).equals(target)) {
                linked.remove(i);
                markDirty();
                sync();
                applyNow();
                return "Unlinked — " + linked.size() + " signal(s) left on this box";
            }
        }
        if (!isSignal(target)) { return "That is not a signal"; }
        linked.add(target);
        markDirty();
        sync();
        applyNow();
        return "Linked — this box now controls " + linked.size() + " signal(s)";
    }

    private boolean isSignal(BlockPos p) {
        if (!world.isBlockLoaded(p)) return false;
        TileEntity te = world.getTileEntity(p);
        return te instanceof TileSignalMast || te instanceof TileSignalBridge;
    }

    @Override
    public void update() {
        if (world.isRemote || ++ticks % Math.max(1, cfg.num("every")) != 0) return;
        int power = world.getRedstonePowerFromNeighbors(pos);
        if (cfg.bool("invert")) power = 15 - power;
        if (power < cfg.num("deadband")) power = 0;
        lastInput = power;
        if (power != lastPower) {
            lastPower = power;
            apply(power);
            world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
        }
    }

    private void applyNow() {
        if (world != null && !world.isRemote) {
            lastPower = -1;
        }
    }

    /** Push the box's decision out to every linked signal, dropping any that have gone. */
    private void apply(int power) {
        boolean dropped = false;
        for (int i = linked.size() - 1; i >= 0; i--) {
            BlockPos p = linked.get(i);
            if (!world.isBlockLoaded(p)) continue;      // not loaded is not the same as gone
            TileEntity te = world.getTileEntity(p);
            if (te instanceof TileSignalMast mast) {
                switch (mode) {
                    case HOLD   -> mast.setRelayControl(power > 0, null);
                    case DRIVE  -> mast.setRelayControl(false, aspectFor(power, cfg.num("restrict"), cfg.num("approach"), cfg.num("clear")));
                    case REPORT -> mast.setRelayControl(false, null);
                }
            } else if (te == null) {
                linked.remove(i);
                dropped = true;
            }
        }
        if (dropped) { markDirty(); sync(); }
    }

    static Aspect aspectFor(int power) { return aspectFor(power, 1, 6, 11); }

    /** input strength -> aspect, with the thresholds from the Settings Console */
    static Aspect aspectFor(int power, int restrict, int approach, int clear) {
        if (power >= clear) return Aspect.CLEAR;
        if (power >= approach) return Aspect.APPROACH;
        if (power >= restrict && power > 0) return Aspect.RESTRICTING;
        return Aspect.STOP;
    }

    /** What the box reports: 15 all clear, 0 if anything linked is at Stop, else the worst. */
    public int redstoneOutput() {
        int v = rawOutput();
        String how = cfg.text("output");
        if (how.startsWith("Off")) return 0;
        if (how.startsWith("Inverted")) v = 15 - v;
        else if (how.startsWith("All-or")) v = v >= 15 ? 15 : 0;
        return Math.max(0, Math.min(15, v * cfg.num("outMax") / 15));
    }

    private int rawOutput() {
        if (linked.isEmpty()) return 0;
        int worst = 15;
        for (BlockPos p : linked) {
            if (!world.isBlockLoaded(p)) continue;
            TileEntity te = world.getTileEntity(p);
            int v = 15;
            if (te instanceof TileSignalMast mast) v = mast.redstoneOutput();
            else if (te instanceof TileSignalBridge b) v = b.redstoneOutput();
            worst = Math.min(worst, v);
        }
        return worst;
    }

    public String statusLine() {
        return "Relay case — " + mode.label + " · " + linked.size() + " signal(s) linked"
                + (linked.isEmpty() ? " · link one with the Signal Wrench" : "");
    }

    // ---- Settings Console (sneak-right-click with the Signal Wrench) ----
    private final com.dogpound.railmap.settings.SettingsStore cfg = new com.dogpound.railmap.settings.SettingsStore(this);
    private int lastInput;

    @Override public String settingsTitle() { return "Relay Case"; }

    @Override
    public List<com.dogpound.railmap.settings.Setting> settingDefs() {
        List<com.dogpound.railmap.settings.Setting> l = new ArrayList<>();
        String[] modes = new String[Mode.values().length];
        for (int i = 0; i < modes.length; i++) modes[i] = Mode.values()[i].label;
        l.add(com.dogpound.railmap.settings.Setting.choice("Control", "mode", "What the redstone does", "Hold = lever puts every linked signal at Stop. Drive = strength picks the aspect. Report = output only", Mode.HOLD.label, modes));
        l.add(com.dogpound.railmap.settings.Setting.bool("Control", "invert", "Invert the input", "Powered counts as off and off counts as powered (fail-safe wiring)", false));
        l.add(com.dogpound.railmap.settings.Setting.num("Control", "deadband", "Ignore weak signals below", "Stray redstone under this strength counts as no power", 1, 1, 15, 1, ""));
        l.add(com.dogpound.railmap.settings.Setting.num("Control", "every", "Check the input every", "Slower = ignores quick flickers", 4, 1, 40, 1, "ticks"));
        l.add(com.dogpound.railmap.settings.Setting.bool("Control", "release", "Release signals when broken", "Breaking the box lets its signals go back to reading the track", true));
        l.add(com.dogpound.railmap.settings.Setting.num("Drive levels", "restrict", "Restricting from", "Input strength that shows Restricting (Drive mode)", 1, 1, 15, 1, ""));
        l.add(com.dogpound.railmap.settings.Setting.num("Drive levels", "approach", "Approach from", "Input strength that shows Approach", 6, 1, 15, 1, ""));
        l.add(com.dogpound.railmap.settings.Setting.num("Drive levels", "clear", "Clear from", "Input strength that shows Clear", 11, 1, 15, 1, ""));
        l.add(com.dogpound.railmap.settings.Setting.choice("Output", "output", "Comparator output", "What the box tells your redstone about its signals",
                "Worst signal (15 = all clear)", "Worst signal (15 = all clear)", "Inverted (15 = something at Stop)", "All-or-nothing (15 only when all clear)", "Off"));
        l.add(com.dogpound.railmap.settings.Setting.num("Output", "outMax", "Output strength", "Scale the output down for short redstone runs", 15, 1, 15, 1, ""));
        if (world != null) {
            l.add(com.dogpound.railmap.settings.Setting.info("Status", "Linked signals", linked.size() + (linked.isEmpty() ? " (link with the Signal Wrench)" : "")));
            l.add(com.dogpound.railmap.settings.Setting.info("Status", "Input now", String.valueOf(lastInput)));
            l.add(com.dogpound.railmap.settings.Setting.info("Status", "Output now", String.valueOf(redstoneOutput())));
            for (int i = 0; i < Math.min(8, linked.size()); i++) {
                BlockPos p = linked.get(i);
                l.add(com.dogpound.railmap.settings.Setting.info("Status", "Signal " + (i + 1), p.getX() + ", " + p.getY() + ", " + p.getZ()));
            }
        }
        return l;
    }

    @Override public com.dogpound.railmap.settings.SettingsStore settings() { cfg.put("mode", mode.label); return cfg; }

    @Override
    public void onSettingsChanged(String key) {
        if ("mode".equals(key)) for (Mode m : Mode.values()) if (m.label.equals(cfg.text("mode"))) mode = m;
        lastPower = -1;
        markDirty();
        sync();
        if (world != null && !world.isRemote) world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
    }

    private void sync() {
        if (world == null || world.isRemote) return;
        IBlockState s = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, s, s, 3);
    }

    /** Release everything this box was holding, so breaking it never strands a signal at red. */
    @Override
    public void invalidate() {
        if (world != null && !world.isRemote && cfg.bool("release")) {
            for (BlockPos p : linked) {
                if (!world.isBlockLoaded(p)) continue;
                TileEntity te = world.getTileEntity(p);
                if (te instanceof TileSignalMast mast) mast.setRelayControl(false, null);
            }
        }
        super.invalidate();
    }

    // ---- persistence ---------------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        long[] arr = new long[linked.size()];
        for (int i = 0; i < linked.size(); i++) arr[i] = linked.get(i).toLong();
        // NBT has no long[] in 1.12, so store as pairs of ints.
        int[] packed = new int[arr.length * 2];
        for (int i = 0; i < arr.length; i++) {
            packed[i * 2] = (int) (arr[i] >> 32);
            packed[i * 2 + 1] = (int) arr[i];
        }
        t.setIntArray("links", packed);
        t.setByte("mode", (byte) mode.ordinal());
        cfg.write(t);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        linked.clear();
        int[] packed = t.getIntArray("links");
        for (int i = 0; i + 1 < packed.length; i += 2) {
            long v = ((long) packed[i] << 32) | (packed[i + 1] & 0xffffffffL);
            linked.add(BlockPos.fromLong(v));
        }
        Mode[] all = Mode.values();
        int m = t.getByte("mode");
        mode = m >= 0 && m < all.length ? all[m] : Mode.HOLD;
        cfg.read(t);
        lastPower = -1;
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
}
