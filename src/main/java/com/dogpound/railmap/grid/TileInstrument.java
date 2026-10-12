package com.dogpound.railmap.grid;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;

/**
 * A control-room panel instrument (requested feature). Linked with the Signal Wrench to any
 * grid machine or cable; once a second the server reads that point of the grid and the client draws the needle /
 * screen / lamp (InstrumentRenderer). For gauges wired in through Instrument Wire, see the Gauge Panel.
 */
public class TileInstrument extends TileEntity implements ITickable, com.dogpound.railmap.settings.ISettingsHolder {
    private BlockPos target;
    /** what it shows (synced) */
    public final Reading r = new Reading();
    /** wire colour run into the hole at the bottom of the case (-1 = none: read the Signal Wrench link instead) */
    public int wire = -1;
    /** Control Button: which of the machine's panel controls it copies */
    public String ctl = "";
    /** when it was last pressed (the cap goes in for a moment) */
    public long pressedAt;
    /** fried by the wrong voltage on its 12 V feed: dead until fixed with a screwdriver */
    public boolean burnt;

    /** a screwdriver on a burnt-out instrument: new board in */
    public String repair() {
        if (!burnt) return null;
        burnt = false; markDirty(); update0();
        return "§aRepaired - new electronics in the " + kind().label + (r.pw == 2 ? " §c(still on the wrong voltage - it'll burn again!)" : "");
    }
    private double peak = -1;
    private final com.dogpound.railmap.settings.SettingsStore cfg = new com.dogpound.railmap.settings.SettingsStore(this);

    public void setWire(int c) { wire = c; peak = -1; markDirty(); update0(); IBlockState st = world.getBlockState(pos); world.notifyBlockUpdate(pos, st, st, 3); }
    public void resetPeak() { peak = -1; update0(); }
    public boolean alarmOut() { return r.alarm && cfg.bool("redstone"); }

    private BlockPos src() { return wire >= 0 ? com.dogpound.railmap.wire.WireNet.source(world, pos, wire) : target; }

    /** Control Button: press the machine's own control, exactly as its panel button would */
    public String press(String who) {
        if (r.pw == 2) return "§cBurnt out - fix it with a screwdriver (and check its supply is 12 V DC)";
        if (r.pw == 0) return "§cNo power - run 12 V DC Power Wire to it from a 12 V DC supply";
        BlockPos s = src();
        if (!(s != null && world.getTileEntity(s) instanceof com.dogpound.railmap.wire.IWiredMachine m)) return "§cNothing on the wire - land the wire on a machine's control terminal (right-click the machine with it)";
        String id = r.readButton(world, s, ctl);
        String res = m.wiredPress(id, who);
        update0();
        return res == null || res.isEmpty() ? "§a" + r.line1 + " pressed" : res;
    }

    /** Control Button: copy the next / previous control on the machine's panel */
    public String cycle(int dir) {
        BlockPos s = src();
        if (s == null) return "§cWire it to a machine first";
        ctl = Reading.nextButton(world, s, ctl, dir);
        markDirty(); update0();
        return "§aThis button now does: §f" + r.line1 + " §7(" + r.line2 + ")";
    }

    public BlockInstrument.Inst kind() {
        IBlockState s = world == null ? null : world.getBlockState(pos);
        return s != null && s.getBlock() instanceof BlockInstrument b ? b.inst : BlockInstrument.Inst.VOLT;
    }

    public String link(BlockPos t) {
        target = t == null ? null : t.toImmutable();
        markDirty();
        update0();
        return target == null ? "§7Instrument unlinked" : "§aInstrument linked to " + t.getX() + ", " + t.getY() + ", " + t.getZ();
    }

    public BlockPos target() { return target; }

    @Override
    public void update() {
        if (world == null || world.isRemote || world.getTotalWorldTime() % 20 != (pos.hashCode() & 15)) return;
        update0();
    }

    private void update0() {
        if (world == null || world.isRemote) return;
        String before = r.key();
        boolean wasAlarm = r.alarm;
        com.dogpound.railmap.wire.WireNet.Supply sup = burnt ? null : com.dogpound.railmap.wire.WireNet.power(world, pos);
        if (sup != null && sup.state == 2) { burnt = true; com.dogpound.railmap.wire.WireNet.burnOut(world, pos, kind().label, sup.what); }
        if (burnt || sup.state == 0) {
            r.unpowered(burnt ? 2 : 0, burnt ? "fix with a screwdriver" : sup.what);
            if (wasAlarm) world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
            if (!before.equals(r.key())) { markDirty(); IBlockState st = world.getBlockState(pos); world.notifyBlockUpdate(pos, st, st, 3); }
            return;
        }
        r.pw = 1;
        BlockPos src = target;
        if (wire >= 0) src = com.dogpound.railmap.wire.WireNet.source(world, pos, wire);
        if (wire >= 0 && src == null) r.notLinked("nothing on the", com.dogpound.railmap.wire.WireNet.NAMES[wire] + " wire");
        else if (kind() == BlockInstrument.Inst.BUTTON) { String c = r.readButton(world, src, ctl); if (!c.equals(ctl)) { ctl = c; markDirty(); } }
        else r.read(world, src, kind());
        String range = cfg.text("range");
        r.full *= range.startsWith("Half") ? 0.5 : range.startsWith("Double") ? 2 : 1;
        double frac = r.value / Math.max(1e-9, r.full);
        int hi = cfg.num("hi"), lo = cfg.num("lo");
        r.hiFrac = hi > 0 ? hi / 100.0 : 0;
        r.loFrac = lo > 0 ? lo / 100.0 : 0;
        if (cfg.bool("peak") && src != null) { peak = Math.max(peak, frac); r.peakFrac = peak; } else r.peakFrac = -1;
        r.alarm = src != null && "LIVE".equals(r.status) && (hi > 0 && frac > hi / 100.0 || lo > 0 && frac < lo / 100.0);
        r.damp = cfg.text("damping").startsWith("Slow") ? 0.2 : cfg.text("damping").startsWith("Fast") ? 1.5 : 0.6;
        if (wasAlarm != r.alarm) world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
        if (!before.equals(r.key())) {
            markDirty();
            IBlockState st = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, st, st, 3);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        if (target != null) t.setLong("target", target.toLong());
        r.write(t, "");
        t.setInteger("wire", wire);
        t.setString("ctl", ctl); t.setBoolean("burnt", burnt);
        cfg.write(t);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        target = t.hasKey("target") ? BlockPos.fromLong(t.getLong("target")) : null;
        r.read(t, "");
        wire = t.hasKey("wire") ? t.getInteger("wire") : -1;
        ctl = t.getString("ctl"); burnt = t.getBoolean("burnt");
        cfg.read(t);
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() { return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager m, net.minecraft.network.play.server.SPacketUpdateTileEntity p) { readFromNBT(p.getNbtCompound()); }

    // ---- Settings Console (sneak-right-click with the Signal Wrench) ----
    @Override public String settingsTitle() { return kind().label; }
    @Override public com.dogpound.railmap.settings.SettingsStore settings() { return cfg; }
    @Override public void onSettingsChanged(String key) { if (key.equals("peak")) peak = -1; markDirty(); update0(); }

    @Override
    public java.util.List<com.dogpound.railmap.settings.Setting> settingDefs() {
        java.util.List<com.dogpound.railmap.settings.Setting> l = new java.util.ArrayList<>();
        boolean analogue = kind().analogue();
        if (analogue) {
            l.add(com.dogpound.railmap.settings.Setting.num("Alarms", "hi", "High alarm", "Red pointer on the dial: the lamp flashes and redstone comes on above it (% of full scale, 0 = off)", 0, 0, 125, 5, "%"));
            l.add(com.dogpound.railmap.settings.Setting.num("Alarms", "lo", "Low alarm", "Blue pointer: alarm below it (% of full scale, 0 = off)", 0, 0, 100, 5, "%"));
            l.add(com.dogpound.railmap.settings.Setting.bool("Alarms", "redstone", "Redstone on alarm", "Gives a redstone signal while the reading is outside the setpoints", true));
            l.add(com.dogpound.railmap.settings.Setting.choice("Needle", "damping", "Damping", "How fast the needle swings to a new reading", "Normal", "Fast", "Normal", "Slow"));
            l.add(com.dogpound.railmap.settings.Setting.bool("Needle", "peak", "Peak-hold pointer", "A red drag pointer stays at the highest reading (right-click the meter to reset it)", false));
            l.add(com.dogpound.railmap.settings.Setting.choice("Needle", "range", "Full scale", "Auto follows the rating; Half reads small loads finer, Double for heavy ones", "Auto", "Half", "Auto", "Double"));
        }
        l.add(com.dogpound.railmap.settings.Setting.info("Status", "Input", wire >= 0 ? com.dogpound.railmap.wire.WireNet.NAMES[wire] + " wire (hole at the bottom of the case)" : target != null ? "Signal Wrench link" : "not connected"));
        l.add(com.dogpound.railmap.settings.Setting.info("Status", "Reading", r.line2 + " · " + r.status));
        l.add(com.dogpound.railmap.settings.Setting.info("Status", "12 V supply", burnt ? "BURNT OUT - screwdriver to fix" : com.dogpound.railmap.wire.WireNet.power(world, pos).what));
        return l;
    }
}
