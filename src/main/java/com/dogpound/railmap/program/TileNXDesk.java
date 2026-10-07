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

public class TileNXDesk extends TileRailDisplay {
    private long entrance = 0;
    private long entranceAt;
    private long[] routeStarts = new long[0];
    private long[] routeEnds = new long[0];
    private int alarmFlash;

    @Override
    public void update() {
        super.update();
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
        } else {
            String r = Interlocking.setRoute(world, BlockPos.fromLong(entrance), signal);
            entrance = 0;
            msg = r == null ? "Route set" : r;
        }
        world.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK, net.minecraft.util.SoundCategory.BLOCKS, 0.6f, 1.0f);
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
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        entrance = tag.getLong("nxEntrance");
        entranceAt = tag.getLong("nxEntranceAt");
        routeStarts = fromTagList(tag.getTagList("nxStarts", 4));
        routeEnds = fromTagList(tag.getTagList("nxEnds", 4));
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
