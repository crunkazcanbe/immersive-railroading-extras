package com.dogpound.railmap.block;

import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.auto.Dispatcher;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.item.ItemTicket;
import com.dogpound.railmap.proxy.CommonProxy;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The station's ticket machine: sells tickets to every named station, and takes them back to
 * call a train.
 * <ul>
 *   <li><b>Buy</b> — right-click for the touch screen: pick a destination, one way or round
 *       trip, pay the fare, and it prints the ticket.</li>
 *   <li><b>Ride</b> — right-click it holding a ticket from this station. It punches the ticket
 *       and the dispatcher sends a driverless train: one already running a line through both
 *       stations, or the nearest train on call.</li>
 *   <li><b>Signals</b> — link platform signals to it with the Signal Wrench and they are held at
 *       Stop while a train is boarding here, so nothing runs through the platform while people
 *       are getting on.</li>
 *   <li><b>Redstone</b> — a short pulse when a ticket is accepted (open a fare gate) and a steady
 *       signal while a train is boarding (doors, lights, a departure bell).</li>
 * </ul>
 */
public class TileTicketMachine extends TileEntity implements ITickable {
    private final List<BlockPos> linked = new ArrayList<>();
    private long station = Long.MIN_VALUE;
    private String stationName = "";
    private int pulse;
    private boolean boarding;
    private int ticks;

    public long station() {
        return station;
    }

    public String stationName() {
        return stationName;
    }

    public int redstoneOutput() {
        return pulse > 0 || boarding ? 15 : 0;
    }

    @Override
    public void update() {
        if (world.isRemote) return;
        ticks++;
        if (pulse > 0 && --pulse == 0) world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
        if (ticks % 20 != 0) return;
        resolveStation();
        boolean now = station != Long.MIN_VALUE && Dispatcher.boardingAt(world, station);
        if (now != boarding) {
            boarding = now;
            world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
            // Only on a change, so a relay case wired to the same signal keeps its say otherwise.
            for (BlockPos p : linked) {
                if (world.isBlockLoaded(p) && world.getTileEntity(p) instanceof TileSignalMast m) {
                    m.setRelayControl(boarding, null);
                }
            }
        }
    }

    /** The nearest named station within reach — the one this machine sells from. */
    public void resolveStation() {
        StationData stations = StationData.get(world);
        long best = Long.MIN_VALUE;
        double bd = (double) RailMapConfig.stationReach * RailMapConfig.stationReach;
        for (Map.Entry<Long, String> e : stations.names().entrySet()) {
            double d = BlockPos.fromLong(e.getKey()).distanceSq(pos);
            if (d < bd) { bd = d; best = e.getKey(); }
        }
        if (best != station) {
            station = best;
            stationName = best == Long.MIN_VALUE ? "" : stations.names().get(best);
            sync();
        }
    }

    // ---- fares ---------------------------------------------------------------------------

    public static Item fareItem() {
        String id = RailMapConfig.fareItem == null ? "" : RailMapConfig.fareItem.trim();
        return id.isEmpty() ? null : Item.getByNameOrId(id);
    }

    /** Fare in fare items from here to {@code dest}; 0 when tickets are free. */
    public int fare(long dest, boolean round) {
        if (fareItem() == null) return 0;
        double d = Math.sqrt(BlockPos.fromLong(dest).distanceSq(BlockPos.fromLong(station)));
        int one = Math.max(1, (int) Math.ceil(d / RailMapConfig.fareBlocksPerItem));
        return round ? one * 2 : one;
    }

    /** Take the fare and print the ticket. Returns a line for the player. */
    public String buy(EntityPlayer player, long dest, boolean round) {
        resolveStation();
        if (station == Long.MIN_VALUE) return "This machine isn't at a station — name one nearby on the Dispatcher Board";
        StationData stations = StationData.get(world);
        String destName = stations.names().get(dest);
        if (destName == null || dest == station) return "Pick a destination";
        int fare = fare(dest, round);
        Item item = fareItem();
        if (fare > 0 && !player.capabilities.isCreativeMode) {
            if (count(player, item) < fare) {
                return "Not enough — this ticket costs " + fare + " " + new ItemStack(item).getDisplayName();
            }
            take(player, item, fare);
        }
        RailwayData data = RailwayData.get(world);
        ItemStack ticket = ItemTicket.make(CommonProxy.ticket, station, stationName, dest, destName, round,
                data.nextTicketSerial(), world.getWorldTime() / 24000);
        if (!player.inventory.addItemStackToInventory(ticket)) player.dropItem(ticket, false);
        world.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK, SoundCategory.BLOCKS, 0.6f, 1.6f);
        world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.5f, 0.7f);
        return "Printed: " + stationName + " → " + destName + (round ? " (round trip)" : "")
                + (fare > 0 ? " · paid " + fare : "");
    }

    /** A ticket fed into the slot. */
    public String insert(EntityPlayer player, ItemStack stack) {
        resolveStation();
        if (!ItemTicket.valid(stack)) return "That ticket is blank";
        if (ItemTicket.isSpent(stack)) return "That ticket has already been used";
        if (station == Long.MIN_VALUE) return "This machine isn't at a station";
        if (ItemTicket.from(stack) != station) {
            return "This ticket is for a trip from " + ItemTicket.fromName(stack) + " — use it there";
        }
        long to = ItemTicket.to(stack);
        ItemTicket.punch(stack);
        String reply = Dispatcher.ticketInserted(world, station, to, player.getName());
        pulse = 30;
        world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
        world.playSound(null, pos, SoundEvents.BLOCK_NOTE_CHIME, SoundCategory.BLOCKS, 0.9f, 1.3f);
        return reply;
    }

    private static int count(EntityPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) if (!s.isEmpty() && s.getItem() == item) n += s.getCount();
        return n;
    }

    private static void take(EntityPlayer p, Item item, int n) {
        for (ItemStack s : p.inventory.mainInventory) {
            if (n <= 0) break;
            if (s.isEmpty() || s.getItem() != item) continue;
            int k = Math.min(n, s.getCount());
            s.shrink(k);
            n -= k;
        }
        p.inventory.markDirty();
    }

    // ---- linked platform signals ---------------------------------------------------------

    public String toggleLink(BlockPos target) {
        if (target.distanceSq(pos) > 64 * 64) return "That signal is more than 64 blocks away";
        for (int i = 0; i < linked.size(); i++) {
            if (linked.get(i).equals(target)) {
                linked.remove(i);
                if (world.getTileEntity(target) instanceof TileSignalMast m) m.setRelayControl(false, null);
                markDirty();
                return "Signal unlinked from the ticket machine";
            }
        }
        if (!(world.getTileEntity(target) instanceof TileSignalMast)) return "Only signal masts can be linked";
        linked.add(target);
        markDirty();
        return "Platform signal linked — held at Stop while a train boards here (" + linked.size() + " linked)";
    }

    @Override
    public void invalidate() {
        if (world != null && !world.isRemote) {
            for (BlockPos p : linked) {
                if (world.isBlockLoaded(p) && world.getTileEntity(p) instanceof TileSignalMast m) m.setRelayControl(false, null);
            }
        }
        super.invalidate();
    }

    private void sync() {
        markDirty();
        IBlockState s = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, s, s, 3);
    }

    // ---- persistence ---------------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        List<Long> ls = new ArrayList<>();
        for (BlockPos p : linked) ls.add(p.toLong());
        int[] packed = new int[ls.size() * 2];
        for (int i = 0; i < ls.size(); i++) {
            packed[i * 2] = (int) (ls.get(i) >> 32);
            packed[i * 2 + 1] = (int) (long) ls.get(i);
        }
        t.setIntArray("links", packed);
        t.setLong("st", station);
        t.setString("sn", stationName);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        linked.clear();
        int[] packed = t.getIntArray("links");
        for (int i = 0; i + 1 < packed.length; i += 2) {
            linked.add(BlockPos.fromLong(((long) packed[i] << 32) | (packed[i + 1] & 0xffffffffL)));
        }
        station = t.hasKey("st") ? t.getLong("st") : Long.MIN_VALUE;
        stationName = t.getString("sn");
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
