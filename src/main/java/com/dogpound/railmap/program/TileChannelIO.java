package com.dogpound.railmap.program;

import com.dogpound.railmap.settings.ISettingsHolder;
import com.dogpound.railmap.settings.Setting;
import com.dogpound.railmap.settings.SettingsStore;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The Redstone Interface: joins redstone / Project Red bundled cables to the signal box's 64 wireless
 * channels. Plain redstone drives one channel (or is driven by one); bundled cables drive or are driven
 * by a run of 16 channels.
 */
@net.minecraftforge.fml.common.Optional.Interface(iface = "mrtjp.projectred.api.IBundledTile", modid = "projectred-core")
public class TileChannelIO extends TileEntity implements ITickable, ISettingsHolder, mrtjp.projectred.api.IBundledTile,
        com.dogpound.railmap.wire.IWiredGear, com.dogpound.railmap.controls.IControlled {
    // ---- 12 V terminal + working LEDs (requested feature): the cabinet runs on 12 V DC; the module LEDs show the real
    // channels: IN = the channels it reads into, OUT = the outputs it drives, BUS 1 = any of its channels on, BUS 2 =
    // bundled cable bus connected, PSU = 12 V in.
    private final com.dogpound.railmap.wire.Terminal term = new com.dogpound.railmap.wire.Terminal();
    private final com.dogpound.railmap.controls.ControlState ctl = new com.dogpound.railmap.controls.ControlState();
    @Override public com.dogpound.railmap.wire.Terminal term() { return term; }
    @Override public void gearChanged() { markDirty(); if (world != null && !world.isRemote) { net.minecraft.block.state.IBlockState gs = world.getBlockState(pos); world.notifyBlockUpdate(pos, gs, gs, 3); } }
    @Override public String wiredName() { return "Redstone interface"; }
    @Override public String gearState() { return term.powered() ? statusLine().replaceAll("§.", "") : "NO 12 V - interface dead"; }
    @Override public com.dogpound.railmap.controls.ControlState controls() { return ctl; }
    @Override public void controlsChanged() { gearChanged(); }
    @Override public com.dogpound.railmap.grid.Panel wiredPanel() { return layout().all; }
    @Override public String wiredPress(String id, String who) { return "§7The redstone interface has no controls - its LEDs show the channels"; }
    @Override public int wiredState(String id) { return controlValue(id); }

    private void leds(ProgramData d) {
        boolean pw = term.powered(), in = mode().equals("Redstone in -> channel") || bundledIn(), any = false;
        int first = bundledMode() ? base() : channel();
        boolean ch = com.dogpound.railmap.block.StationControls.put(ctl, "psu", pw);
        for (int i = 0; i < 4; i++) {
            boolean on = pw && in && (bundledMode() || i == 0) && d.ch(first + i);
            ch |= com.dogpound.railmap.block.StationControls.put(ctl, "in" + (i + 1), on);
        }
        for (int i = 0; i < 3; i++) {
            boolean on = pw && (mode().equals("Channel -> redstone out") ? i == 0 && out > 0 : bundledOutMode() && bundledOut[i] != 0);
            ch |= com.dogpound.railmap.block.StationControls.put(ctl, "out" + (i + 1), on);
        }
        for (int i = 0; i < (bundledMode() ? 16 : 1); i++) any |= d.ch(first + i);
        ch |= com.dogpound.railmap.block.StationControls.put(ctl, "bus1", pw && any)
                | com.dogpound.railmap.block.StationControls.put(ctl, "bus2", pw && bundledMode() && ProjectRedBridge.loaded());
        if (ch) controlsChanged();
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() { return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) { readFromNBT(pkt.getNbtCompound()); }

    public static final String[] MODES = {"Redstone in -> channel", "Channel -> redstone out", "Bundled in -> 16 channels",
            "Bundled out <- 16 channels", "Both ways (bundled)"};

    private final SettingsStore cfg = new SettingsStore(this);
    private int ticks;
    private int out;
    private byte[] bundledOut = new byte[16];

    private String mode() { return cfg.text("mode"); }
    private int channel() { return cfg.num("channel"); }
    private int base() { return cfg.num("base"); }
    private boolean invert() { return cfg.bool("invert"); }
    private int strength() { return cfg.num("strength"); }

    private boolean bundledMode() {
        String m = mode();
        return m.startsWith("Bundled") || m.startsWith("Both");
    }

    private boolean bundledIn() {
        String m = mode();
        return m.startsWith("Bundled in") || m.startsWith("Both");
    }

    private boolean bundledOutMode() {
        String m = mode();
        return m.startsWith("Bundled out") || m.startsWith("Both");
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (term.tick(world, pos, wiredName())) gearChanged();
        if (++ticks % 2 != 0) return;
        ProgramData d = ProgramData.get(world);
        if (ticks % 10 == 0) leds(d);
        if (!term.powered()) return;
        String m = mode();
        if (m.equals("Redstone in -> channel")) {
            boolean on = world.getRedstonePowerFromNeighbors(pos) > 0;
            if (invert()) on = !on;
            d.setChannel(channel(), on);
        } else if (m.equals("Channel -> redstone out")) {
            int n = d.ch(channel()) ? strength() : 0;
            if (invert()) n = n == 0 ? strength() : 0;
            if (n != out) {
                out = n;
                markDirty();
                world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
            }
        } else if (bundledMode()) {
            if (ProjectRedBridge.loaded()) {
                if (bundledIn()) {
                    boolean[] on = new boolean[16];
                    for (net.minecraft.util.EnumFacing f : net.minecraft.util.EnumFacing.VALUES) {
                        byte[] in = ProjectRedBridge.bundledInput(world, pos, f);
                        if (in == null) continue;
                        for (int i = 0; i < 16 && i < in.length; i++) if (in[i] != 0) on[i] = true;
                    }
                    for (int i = 0; i < 16; i++) d.setChannel(base() + i, on[i]);
                }
                if (bundledOutMode()) {
                    boolean changed = false;
                    for (int i = 0; i < 16; i++) {
                        byte v = d.ch(base() + i) ? (byte) 255 : 0;
                        if (v != bundledOut[i]) { bundledOut[i] = v; changed = true; }
                    }
                    if (changed) world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
                }
            }
        }
    }

    public int redstoneOut() { return out; }

    @net.minecraftforge.fml.common.Optional.Method(modid = "projectred-core")
    @Override
    public boolean canConnectBundled(int side) {
        return bundledMode();
    }

    @net.minecraftforge.fml.common.Optional.Method(modid = "projectred-core")
    @Override
    public byte[] getBundledSignal(int side) {
        return bundledOutMode() ? bundledOut : null;
    }

    // ---- Settings Console ------------------------------------------------------------------------------
    @Override public String settingsTitle() { return "Redstone Interface"; }

    @Override
    public List<Setting> settingDefs() {
        List<Setting> l = new ArrayList<>();
        l.add(Setting.choice("Interface", "mode", "What it does", "", MODES[0], MODES));
        l.add(Setting.num("Interface", "channel", "Channel", "Plain redstone modes", 1, 1, 64, 1, ""));
        l.add(Setting.num("Interface", "base", "First channel of the 16", "Bundled modes: colour i maps to channel base+i", 1, 1, 49, 1, ""));
        l.add(Setting.bool("Interface", "invert", "Invert", "", false));
        l.add(Setting.num("Interface", "strength", "Output strength", "Channel -> redstone out", 15, 1, 15, 1, ""));
        if (world != null && !world.isRemote) {
            ProgramData d = ProgramData.get(world);
            StringBuilder sb = new StringBuilder();
            if (mode().equals("Redstone in -> channel") || mode().equals("Channel -> redstone out")) {
                int c = channel();
                sb.append(c).append(":").append(d.ch(c) ? "ON" : "off");
            } else if (bundledMode()) {
                for (int i = 0; i < 16; i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(base() + i).append(':').append(d.ch(base() + i) ? "ON" : "off");
                }
            }
            l.add(Setting.info("Status", "Channels", sb.length() == 0 ? "none" : sb.toString()));
            com.dogpound.railmap.wire.IWiredGear.settings(l, "Status", this);
        }
        return l;
    }

    @Override public SettingsStore settings() { return cfg; }
    @Override public void onSettingsChanged(String key) { markDirty(); }

    public String statusLine() {
        String m = mode();
        if (m.equals("Redstone in -> channel")) return "§bRedstone in -> channel " + channel();
        if (m.equals("Channel -> redstone out")) return "§bChannel " + channel() + " -> redstone out (strength " + strength() + ")";
        if (m.startsWith("Bundled in")) return "§bBundled in -> channels " + base() + "-" + (base() + 15);
        if (m.startsWith("Bundled out")) return "§bChannels " + base() + "-" + (base() + 15) + " -> bundled out";
        return "§bBoth ways (bundled): channels " + base() + "-" + (base() + 15);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        cfg.write(t);
        term.write(t);
        ctl.write(t);
        t.setInteger("out", out);
        t.setByteArray("bundledOut", bundledOut);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        cfg.read(t);
        term.read(t);
        ctl.read(t);
        out = t.getInteger("out");
        byte[] b = t.getByteArray("bundledOut");
        if (b != null) {
            for (int i = 0; i < 16 && i < b.length; i++) bundledOut[i] = b[i];
        }
    }

    private static final class ProjectRedBridge {
        private static boolean loaded() {
            return net.minecraftforge.fml.common.Loader.isModLoaded("projectred-core");
        }

        static byte[] bundledInput(World w, BlockPos p, net.minecraft.util.EnumFacing f) {
            try {
                return mrtjp.projectred.api.ProjectRedAPI.transmissionAPI.getBundledInput(w, p, f);
            } catch (Throwable t) {
                return null;
            }
        }
    }
}
