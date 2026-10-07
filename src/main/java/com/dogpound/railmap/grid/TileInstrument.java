package com.dogpound.railmap.grid;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;

/**
 * A control-room panel instrument (requested feature). Linked with the Signal Wrench to any
 * grid machine or cable; once a second the server reads that point of the grid and the client draws the needle /
 * screen / lamp (InstrumentRenderer).
 */
public class TileInstrument extends TileEntity implements ITickable {
    private BlockPos target;
    // synced readings
    public double value, full = 1;        // analogue: reading and full scale (auto-ranged)
    public String unit = "", status = "", line1 = "", line2 = "", line3 = "";
    public int lamp;                      // 0 off, 1 live (green), 2 dead (red), 3 tripped (amber, flashing)
    /** client: where the needle is drawn now (eases toward value) */
    public float needle;
    public long needleAt;

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
        String before = value + full + unit + status + line1 + line2 + line3 + lamp;
        read();
        if (!before.equals(value + full + unit + status + line1 + line2 + line3 + lamp)) {
            markDirty();
            IBlockState st = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, st, st, 3);
        }
    }

    /** read the linked point of the grid in real units */
    private void read() {
        BlockInstrument.Inst k = kind();
        if (target == null) { value = 0; full = 1; unit = ""; status = "NOT LINKED"; line1 = "NOT LINKED"; line2 = "Signal Wrench:"; line3 = "machine -> me"; lamp = 0; return; }
        GridData d = GridData.get(world);
        GridData.Node n = d.node(target);
        GridData.Net net = d.netOf(target);
        if (n == null || net == null) { value = 0; status = "NO SIGNAL"; line1 = "NO SIGNAL"; line2 = line3 = ""; lamp = 2; return; }
        boolean live = net.problem.isEmpty();
        Elec.Level lv = net.level;
        double nominal = lv != null ? lv.volts : (net.traction ? 25_000 : 11_000);
        double pu = lv != null ? (n.pu > 0 ? n.pu : net.puSource) : net.volt;
        double volts = live ? nominal * pu : 0;
        double rating = 0;
        for (long c : net.ins) { GridData.Node cv = d.nodes().get(c); if (cv != null) rating += cv.kva * 1000.0; }
        if (rating <= 0) rating = (double) GridData.RATING * Elec.W_PER_FE_T * Math.max(1, net.transformers);
        double amps = n.kind == GridKind.CABLE || n.sided() ? n.amps : net.amps;
        double ampsFull = n.kind == GridKind.CABLE ? n.cable().ampacity() : (lv != null ? lv.amps(rating) : 500);
        lamp = n.kind == GridKind.BREAKER && n.breaker == 2 ? 3 : live ? 1 : 2;
        status = live ? "LIVE" : net.problem;
        switch (k) {
            case VOLT: value = volts; full = nominal * 1.25; unit = nominal >= 1000 ? "kV" : "V"; break;
            case AMP: value = amps; full = Math.max(1, ampsFull * 1.25); unit = "A"; break;
            case WATT: value = net.pW; full = Math.max(1, rating * 1.25); unit = rating >= 1e6 ? "MW" : "kW"; break;
            case VAR: value = net.qVar; full = Math.max(1, rating * 0.75); unit = rating >= 1e6 ? "Mvar" : "kvar"; break;
            case PF: value = live ? net.pf : 0; full = 1; unit = "PF"; break;
            case FREQ: {
                double hz = lv == null ? 50 : lv.hz;
                value = live && hz > 0 ? (net.hzPu != 1 ? hz * net.hzPu : hz * (1 - 0.002 * Math.min(1.5, net.pW / Math.max(1, rating)))) : 0;
                full = hz <= 0 ? 60 : hz * 1.1; unit = "Hz"; break;
            }
            case TEMP: value = n.kind == GridKind.CABLE ? n.temp : 25 + n.heat; full = 120; unit = "°C"; break;
            default: value = 0; full = 1; unit = ""; break;
        }
        line1 = (lv != null ? lv.label : net.legacy ? "legacy grid" : "no supply") + (live ? "" : " - DEAD");
        line2 = String.format(Locale.ROOT, "%s  %s", Elec.si(volts, "V"), Elec.si(amps, "A"));
        line3 = String.format(Locale.ROOT, "%s  PF %.2f", Elec.si(net.pW, "W"), net.pf);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        if (target != null) t.setLong("target", target.toLong());
        t.setDouble("value", value); t.setDouble("full", full); t.setString("unit", unit); t.setString("status", status);
        t.setString("l1", line1); t.setString("l2", line2); t.setString("l3", line3); t.setInteger("lamp", lamp);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        target = t.hasKey("target") ? BlockPos.fromLong(t.getLong("target")) : null;
        value = t.getDouble("value"); full = Math.max(1e-9, t.getDouble("full")); unit = t.getString("unit"); status = t.getString("status");
        line1 = t.getString("l1"); line2 = t.getString("l2"); line3 = t.getString("l3"); lamp = t.getInteger("lamp");
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() { return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager m, net.minecraft.network.play.server.SPacketUpdateTileEntity p) { readFromNBT(p.getNbtCompound()); }
}
