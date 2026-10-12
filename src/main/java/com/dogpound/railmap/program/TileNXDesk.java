package com.dogpound.railmap.program;

import com.dogpound.railmap.block.TileRailDisplay;
import com.dogpound.railmap.graph.RailNetwork;
import com.dogpound.railmap.auto.Interlocking;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class TileNXDesk extends TileRailDisplay implements com.dogpound.railmap.wire.IWiredGear, com.dogpound.railmap.controls.IControlled {
    // ---- 12 V terminal + the fascia function buttons (requested feature): ALL SIG ON cancels every route (signals back
    // to danger), EMERG REPL does that and bars new routes until pressed again, CANCEL drops a half-set entrance, LAMP
    // TEST lights every button for 3 s, DAY NIGHT dims the panel, ALARM ACK acknowledges the alarm list.
    private final com.dogpound.railmap.wire.Terminal term = new com.dogpound.railmap.wire.Terminal();
    private final com.dogpound.railmap.controls.ControlState ctl = new com.dogpound.railmap.controls.ControlState();
    @Override public com.dogpound.railmap.wire.Terminal term() { return term; }
    @Override public void gearChanged() { markDirty(); if (world != null && !world.isRemote) { net.minecraft.block.state.IBlockState gs = world.getBlockState(pos); world.notifyBlockUpdate(pos, gs, gs, 3); } }
    @Override public String wiredName() { return "NX panel"; }
    @Override public String gearState() { return !term.powered() ? "NO 12 V - function buttons dead" : emergency() ? "EMERGENCY REPLACEMENT" : routeStarts.length + " routes set"; }
    @Override public com.dogpound.railmap.controls.ControlState controls() { return ctl; }
    @Override public void controlsChanged() { gearChanged(); }
    @Override public com.dogpound.railmap.grid.Panel wiredPanel() { return layout().all; }
    @Override public String wiredPress(String id, String who) { return com.dogpound.railmap.controls.IControlled.operate(this, id, null); }
    @Override public int wiredState(String id) { return controlValue(id); }

    public boolean emergency() { return ctl.get("emergOn") == 1; }
    /** client: LAMP TEST held (lights every button) */
    public boolean lampTest() { return world != null && "lamptest".equals(ctl.pressId) && world.getTotalWorldTime() - ctl.pressAt < 60; }
    public boolean dimmed() { return ctl.get("dim") == 1; }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (!term.powered()) return "§cNo 12 V - the function buttons are dead";
        switch (id) {
            case "allsig": return "§eAll signals on - " + cancelAll() + " route(s) cancelled";
            case "emerg":
                ctl.set("emergOn", emergency() ? 0 : 1);
                return emergency() ? "§cEMERGENCY REPLACEMENT - " + cancelAll() + " route(s) cancelled, no new routes" : "§aEmergency replacement cleared - routes can be set";
            case "cancel": entrance = 0; return "§7Entrance cancelled";
            case "lamptest": return "§7Lamp test";
            case "night": ctl.set("dim", dimmed() ? 0 : 1); return dimmed() ? "§9Night - panel dimmed" : "§eDay - panel bright";
            case "ack": {
                java.util.List<String> a = ProgramData.get(world).alarms;
                ctl.set("acked", a.size());
                return a.isEmpty() ? "§7No alarms" : "§eAlarms acknowledged - latest: " + a.get(0);
            }
            default: return null;
        }
    }

    private int cancelAll() {
        int n = 0;
        for (long s : routeStarts) { Interlocking.cancelRoute(world, BlockPos.fromLong(s)); n++; }
        entrance = 0;
        return n;
    }

    private long entrance = 0;
    private long entranceAt;
    private long[] routeStarts = new long[0];
    private long[] routeEnds = new long[0];
    private int alarmFlash;

    @Override
    public void update() {
        super.update();
        if (world != null && !world.isRemote && term.tick(world, pos, wiredName())) gearChanged();
        if (world == null || world.isRemote || !isController()) return;
        if (world.getTotalWorldTime() % 10 != 0) return;
        boolean changed = false;
        if (entrance != 0 && world.getTotalWorldTime() - entranceAt > 200) {
            entrance = 0;
            changed = true;
        }
        List<Interlocking.Route> routes = Interlocking.routes(world);
        RailNetwork net = getNetwork();
        List<Long> starts = new ArrayList<>();
        List<Long> ends = new ArrayList<>();
        for (Interlocking.Route r : routes) {
            for (com.dogpound.railmap.graph.SignalNode sig : net.signals) {
                if (sig.pos.equals(r.start)) {
                    starts.add(r.start.toLong());
                    ends.add(r.end.toLong());
                    break;
                }
            }
        }
        long[] newStarts = new long[starts.size()];
        long[] newEnds = new long[ends.size()];
        for (int i = 0; i < starts.size(); i++) {
            newStarts[i] = starts.get(i);
            newEnds[i] = ends.get(i);
        }
        if (!java.util.Arrays.equals(routeStarts, newStarts) || !java.util.Arrays.equals(routeEnds, newEnds)) {
            routeStarts = newStarts;
            routeEnds = newEnds;
            changed = true;
        }
        if (changed) {
            markDirty();
            if (world != null) {
                net.minecraft.block.state.IBlockState s = world.getBlockState(pos);
                world.notifyBlockUpdate(pos, s, s, 3);
            }
        }
    }

    public String press(BlockPos signal, EntityPlayer p) {
        if (world == null || world.isRemote) return "";
        String msg;
        if (entrance == 0) {
            boolean hasRoute = false;
            for (long s : routeStarts) {
                if (BlockPos.fromLong(s).equals(signal)) {
                    hasRoute = true;
                    break;
                }
            }
            if (hasRoute) {
                Interlocking.cancelRoute(world, signal);
                msg = "Route cancelled";
            } else {
                entrance = signal.toLong();
                entranceAt = world.getTotalWorldTime();
                msg = "Entrance set - now press the exit signal";
            }
        } else if (BlockPos.fromLong(entrance).equals(signal)) {
            entrance = 0;
            msg = "Entrance cleared";
        } else if (emergency()) {
            entrance = 0;
            msg = "Emergency replacement in force - press EMERG REPL to set routes again";
        } else {
            String r = Interlocking.setRoute(world, BlockPos.fromLong(entrance), signal);
            entrance = 0;
            msg = r == null ? "Route set" : r;
        }
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.CTL_PUSH, net.minecraft.util.SoundCategory.BLOCKS, 0.6f, 1.0f);
        markDirty();
        net.minecraft.block.state.IBlockState s = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, s, s, 3);
        return msg;
    }

    public long entrance() {
        return entrance;
    }

    public long[] routeStarts() {
        return routeStarts;
    }

    public long[] routeEnds() {
        return routeEnds;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setLong("nxEntrance", entrance);
        tag.setLong("nxEntranceAt", entranceAt);
        tag.setTag("nxStarts", toTagList(routeStarts));
        tag.setTag("nxEnds", toTagList(routeEnds));
        term.write(tag);
        ctl.write(tag);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        entrance = tag.getLong("nxEntrance");
        entranceAt = tag.getLong("nxEntranceAt");
        routeStarts = fromTagList(tag.getTagList("nxStarts", 4));
        routeEnds = fromTagList(tag.getTagList("nxEnds", 4));
        term.read(tag);
        ctl.read(tag);
    }

    private static NBTTagList toTagList(long[] arr) {
        NBTTagList l = new NBTTagList();
        for (long v : arr) {
            l.appendTag(new NBTTagLong(v));
        }
        return l;
    }

    private static long[] fromTagList(NBTTagList l) {
        long[] arr = new long[l.tagCount()];
        for (int i = 0; i < l.tagCount(); i++) {
            arr[i] = l.getCompoundTagAt(i).getLong("");
        }
        return arr;
    }
}
