package com.dogpound.railmap.program;

import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.auto.Interlocking;
import com.dogpound.railmap.settings.ISettingsHolder;
import com.dogpound.railmap.settings.Setting;
import com.dogpound.railmap.settings.SettingsStore;
import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class TileControlDesk extends TileEntity implements ITickable, ISettingsHolder {
    private static final String[] DOES = {"Nothing", "Toggle channel", "Pulse channel (1 s)", "Set route", "Cancel route",
            "Throw switch Straight", "Throw switch Turn", "Switch to automatic", "Hold signal at Stop", "Release signal"};
    private static final String[] COLOURS = {"Red", "Green", "Yellow", "Blue", "White", "Black"};
    private static final String[] SCREENS = {"Clock + alarms", "Channel lamps 1-16", "Last actions"};

    private final SettingsStore cfg = new SettingsStore(this);
    private int ticks;
    private int lamps;
    private int ch16;
    private int alarmCount;
    private final int[] pulseUntil = new int[13];
    private final List<String> log = new ArrayList<>();

    @Override
    public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote) sync();
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (++ticks % 10 != 0) return;
        int now = (int) world.getWorldTime();
        for (int i = 1; i <= 12; i++) {
            if (pulseUntil[i] > 0 && now >= pulseUntil[i]) {
                pulseUntil[i] = 0;
                ProgramData d = ProgramData.get(world);
                d.setChannel(cfg.num("b" + i + "ch"), false);
            }
        }
        int nl = 0, nc = 0;
        ProgramData d = ProgramData.get(world);
        for (int i = 1; i <= 12; i++) if (lamp(i)) nl |= 1 << (i - 1);
        for (int c = 1; c <= 16; c++) if (d.ch(c)) nc |= 1 << (c - 1);
        int na = d.alarms.size();
        if (nl != lamps || nc != ch16 || na != alarmCount) {
            lamps = nl;
            ch16 = nc;
            alarmCount = na;
            sync();
        }
    }

    public void press(int i, EntityPlayer p) {
        if (world == null || world.isRemote || i < 1 || i > 12) return;
        String do_ = cfg.text("b" + i + "do");
        int ch = cfg.num("b" + i + "ch");
        BlockPos a = parse(cfg.text("b" + i + "a"));
        BlockPos b = parse(cfg.text("b" + i + "b"));
        String res;
        ProgramData d = ProgramData.get(world);
        switch (do_) {
            case "Toggle channel":
                d.setChannel(ch, !d.ch(ch));
                res = "channel " + ch + " now " + (d.ch(ch) ? "ON" : "OFF");
                break;
            case "Pulse channel (1 s)":
                d.setChannel(ch, true);
                pulseUntil[i] = (int) world.getWorldTime() + 20;
                res = "channel " + ch + " pulsed";
                break;
            case "Set route":
                if (a == null || b == null) res = "Set Place A in the desk's settings (sneak-right-click with the Signal Wrench)";
                else {
                    String msg = Interlocking.setRoute(world, a, b);
                    res = msg == null ? "route set" : msg;
                }
                break;
            case "Cancel route":
                if (a == null) res = "Set Place A in the desk's settings (sneak-right-click with the Signal Wrench)";
                else {
                    Interlocking.cancelRoute(world, a);
                    res = "route cancelled";
                }
                break;
            case "Throw switch Straight":
            case "Throw switch Turn":
                if (a == null) res = "Set Place A in the desk's settings (sneak-right-click with the Signal Wrench)";
                else {
                    Vec3i v = new Vec3i(a.getX(), a.getY(), a.getZ());
                    boolean ok = Interlocking.claim(world, v, do_.endsWith("Turn") ? SwitchState.TURN : SwitchState.STRAIGHT, "desk@" + pos.toLong());
                    Interlocking.release(world, v, "desk@" + pos.toLong());
                    res = ok ? "switch " + (do_.endsWith("Turn") ? "turn" : "straight") : "switch refused (occupied or locked)";
                }
                break;
            case "Switch to automatic":
                if (a == null) res = "Set Place A in the desk's settings (sneak-right-click with the Signal Wrench)";
                else {
                    Interlocking.setAuto(world, a);
                    res = "switch set to automatic";
                }
                break;
            case "Hold signal at Stop":
            case "Release signal":
                if (a == null) res = "Set Place A in the desk's settings (sneak-right-click with the Signal Wrench)";
                else {
                    TileSignalMast m = ProgramData.mast(world, a);
                    if (m == null) res = "no signal at Place A";
                    else {
                        m.setRelayControl(do_.startsWith("Hold"), null);
                        res = do_.startsWith("Hold") ? "signal held at Stop" : "signal released";
                    }
                }
                break;
            default:
                res = "no action";
        }
        world.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK, SoundCategory.BLOCKS, 0.5f, 1f);
        log.add(0, stamp() + " B" + i + " " + cfg.text("b" + i + "label") + ": " + res);
        while (log.size() > 6) log.remove(log.size() - 1);
        p.sendMessage(new TextComponentString("§7" + res));
        sync();
    }

    public boolean lamp(int i) {
        if (world == null || world.isRemote || i < 1 || i > 12) return false;
        String do_ = cfg.text("b" + i + "do");
        int ch = cfg.num("b" + i + "ch");
        BlockPos a = parse(cfg.text("b" + i + "a"));
        ProgramData d = ProgramData.get(world);
        switch (do_) {
            case "Toggle channel":
            case "Pulse channel (1 s)":
                return d.ch(ch);
            case "Set route":
                if (a == null) return false;
                for (Interlocking.Route r : Interlocking.routes(world)) if (r.start.equals(a)) return true;
                return false;
            case "Throw switch Straight":
                return a != null && Interlocking.stateAt(world, a) == SwitchState.STRAIGHT;
            case "Throw switch Turn":
                return a != null && Interlocking.stateAt(world, a) == SwitchState.TURN;
            case "Hold signal at Stop":
            case "Release signal":
                if (a == null) return false;
                TileSignalMast m = ProgramData.mast(world, a);
                return m != null && m.relayControlled();
            default:
                return false;
        }
    }

    public String screen(int which) {
        String mode = which == 1 ? cfg.text("s1") : cfg.text("s2");
        List<String> l = new ArrayList<>();
        if (mode.equals("Clock + alarms")) {
            l.add(stamp());
            l.add("ALARMS " + alarmCount);
        } else if (mode.equals("Channel lamps 1-16")) {
            for (int row = 0; row < 2; row++) {
                StringBuilder sb = new StringBuilder();
                for (int c = 1; c <= 8; c++) {
                    int n = row * 8 + c;
                    if (c > 1) sb.append(' ');
                    sb.append(n).append(':').append((ch16 & (1 << (n - 1))) != 0 ? '#' : '.');
                }
                l.add(sb.toString());
            }
        } else {
            for (int i = 0; i < 4 && i < log.size(); i++) l.add(log.get(i));
        }
        return String.join("\n", l);
    }

    public String label(int i) { return cfg.text("b" + i + "label"); }
    public String colour(int i) { return cfg.text("b" + i + "col"); }
    public int lampBits() { return lamps; }

    private String stamp() {
        long t = (world.getWorldTime() + 6000) % 24000;
        return String.format("%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
    }

    private static BlockPos parse(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        String[] p = s.trim().split("[,\\s]+");
        if (p.length < 3) return null;
        try {
            return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void sync() {
        markDirty();
        if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound t = new NBTTagCompound();
        writeSync(t);
        return t;
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 1, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        if (pkt.getNbtCompound() != null) readSync(pkt.getNbtCompound());
    }

    private void writeSync(NBTTagCompound t) {
        cfg.write(t);
        t.setInteger("lamps", lamps);
        t.setInteger("ch16", ch16);
        t.setInteger("alarms", alarmCount);
        NBTTagList l = new NBTTagList();
        for (String s : log) l.appendTag(new NBTTagString(s));
        t.setTag("log", l);
    }

    private void readSync(NBTTagCompound t) {
        cfg.read(t);
        lamps = t.getInteger("lamps");
        ch16 = t.getInteger("ch16");
        alarmCount = t.getInteger("alarms");
        log.clear();
        NBTTagList l = t.getTagList("log", 8);
        for (int i = 0; i < l.tagCount(); i++) log.add(l.getStringTagAt(i));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        writeSync(t);
        for (int i = 1; i <= 12; i++) t.setInteger("pu" + i, pulseUntil[i]);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        readSync(t);
        for (int i = 1; i <= 12; i++) pulseUntil[i] = t.getInteger("pu" + i);
    }

    @Override
    public String settingsTitle() { return "Control Desk"; }

    @Override
    public List<Setting> settingDefs() {
        List<Setting> l = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            String pg = "Button " + i;
            l.add(Setting.text(pg, "b" + i + "label", "Label", "", "B" + i, 10));
            l.add(Setting.choice(pg, "b" + i + "do", "Does", "", DOES[0], DOES));
            l.add(Setting.num(pg, "b" + i + "ch", "Channel", "", 1, 1, 64, 1, ""));
            l.add(Setting.text(pg, "b" + i + "a", "Place A (x y z)", "Signal or switch", "", 24));
            l.add(Setting.text(pg, "b" + i + "b", "Place B (x y z)", "Route end signal", "", 24));
            l.add(Setting.choice(pg, "b" + i + "col", "Button colour", "", COLOURS[0], COLOURS));
        }
        l.add(Setting.choice("Screens", "s1", "Left screen shows", "", SCREENS[0], SCREENS));
        l.add(Setting.choice("Screens", "s2", "Right screen shows", "", SCREENS[2], SCREENS));
        return l;
    }

    @Override
    public SettingsStore settings() { return cfg; }

    @Override
    public void onSettingsChanged(String key) { markDirty(); }
}
