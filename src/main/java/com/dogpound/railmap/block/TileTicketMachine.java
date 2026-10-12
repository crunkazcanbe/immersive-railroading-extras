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
public class TileTicketMachine extends TileEntity implements ITickable, com.dogpound.railmap.settings.ISettingsHolder, com.dogpound.railmap.wire.IWiredGear, com.dogpound.railmap.controls.IControlled {
    // ---- working controls (requested feature): key a destination number on the keypad (1 = nearest station, as on the
    // touch screen's list), SINGLE / RETURN on the screen, OK or ENTER prints the ticket; MENU opens the full touch
    // screen, CALL TRAIN feeds the ticket in your hand, C / CLEAR / CANCEL / coin return clear the entry, HELP explains.
    // Lamps: in-service beacon and card reader on 12 V while selling, collect-ticket light after printing.
    private final com.dogpound.railmap.controls.ControlState ctl = new com.dogpound.railmap.controls.ControlState();
    private long collectUntil;
    @Override public com.dogpound.railmap.controls.ControlState controls() { return ctl; }
    @Override public void controlsChanged() { sync(); }
    @Override public com.dogpound.railmap.grid.Panel wiredPanel() { return layout().all; }
    @Override public String wiredPress(String id, String who) { return com.dogpound.railmap.controls.IControlled.operate(this, id, null); }
    @Override public int wiredState(String id) { return controlValue(id); }

    /** destinations in the touch screen's order: nearest first */
    private List<Long> dests() {
        List<Long> l = new ArrayList<>();
        if (station == Long.MIN_VALUE) return l;
        for (Long k : StationData.get(world).names().keySet()) if (k != station) l.add(k);
        BlockPos here = BlockPos.fromLong(station);
        l.sort((a, b) -> Double.compare(BlockPos.fromLong(a).distanceSq(here), BlockPos.fromLong(b).distanceSq(here)));
        return l;
    }

    /** the keyed destination, or null */
    private Long picked() {
        List<Long> d = dests();
        int n = ctl.get("num");
        return n >= 1 && n <= d.size() ? d.get(n - 1) : null;
    }

    /** lamps and the selection line on the screen; true if anything changed */
    private boolean showSelection() {
        boolean pw = term.powered(), selling = pw && open() && station != Long.MIN_VALUE, ret = ctl.get("ret") == 1;
        String line;
        if (!pw) line = "";
        else if (station == Long.MIN_VALUE) line = "NO STATION NEARBY";
        else if (!open()) line = "CLOSED";
        else {
            Long d = picked();
            int n = ctl.get("num");
            line = d != null ? n + " " + StationData.get(world).names().get(d) + (ret ? " RTN " : " SGL ") + fare(d, ret)
                    : n > 0 ? n + " - NO SUCH STOP" : "KEY 1-" + dests().size() + (ret ? " RETURN" : " SINGLE");
        }
        return StationControls.put(ctl, "service", selling) | StationControls.put(ctl, "card", selling)
                | StationControls.put(ctl, "collect", pw && world.getTotalWorldTime() < collectUntil)
                | StationControls.text(ctl, "sel", line.toUpperCase(java.util.Locale.ROOT));
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        String r = press(id, who);
        showSelection();
        return r;
    }

    private String press(String id, EntityPlayer who) {
        if (!term.powered()) return "§cThe screen is dark - no 12 V";
        if (id.length() == 2 && id.charAt(0) == 'k' && Character.isDigit(id.charAt(1))) {
            ctl.set("num", (ctl.get("num") * 10 + (id.charAt(1) - '0')) % 100);
            return "§7" + ctl.get("num");
        }
        switch (id) {
            case "single": ctl.set("ret", 0); return "§7Single ticket";
            case "return": ctl.set("ret", 1); return roundTrips() ? "§7Return ticket" : "§cThis machine only sells singles";
            case "kc": case "clear": ctl.set("num", 0); return "§7Cleared";
            case "cancel": case "coinret": ctl.set("num", 0); ctl.set("ret", 0); return "§7Cancelled - nothing taken";
            case "kok": case "enter": {
                if (who == null) return "§7Tickets are bought at the machine, in person";
                Long d = picked();
                if (d == null) return "§eKey the number of a destination first (1 = nearest; MENU lists them)";
                int before = sold;
                String r = buy(who, d, ctl.get("ret") == 1);
                if (sold > before) { collectUntil = world.getTotalWorldTime() + 100; ctl.set("num", 0); }
                return "§e" + r + com.dogpound.railmap.station.Pis.ticketNote(world, stationName);   // next-48 #30: service warnings from the PIS feed
            }
            case "menu":
                if (!(who instanceof net.minecraft.entity.player.EntityPlayerMP mp)) return "§7The touch screen is used in person";
                resolveStation();
                com.dogpound.railmap.RailMap.NETWORK.sendTo(com.dogpound.railmap.network.PacketTicketMenu.build(this, mp), mp);
                return "";
            case "call":
                if (who == null || !(who.getHeldItemMainhand().getItem() instanceof ItemTicket)) return "§eHold your ticket and press CALL TRAIN";
                return "§e" + insert(who, who.getHeldItemMainhand());
            case "help":
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.6f, 1.0f);
                return "§bHelp: key a destination number (1 = nearest, MENU lists them), SINGLE or RETURN, then OK. Hold a ticket + CALL TRAIN to call one.";
            default: return null;
        }
    }

    // ---- 12 V terminal (requested feature): needs 12 V DC instrument power; with none it is screen dark, no sales
    private final com.dogpound.railmap.wire.Terminal term = new com.dogpound.railmap.wire.Terminal();
    @Override public com.dogpound.railmap.wire.Terminal term() { return term; }
    @Override public void gearChanged() { markDirty(); if (world != null && !world.isRemote) { net.minecraft.block.state.IBlockState gs = world.getBlockState(pos); world.notifyBlockUpdate(pos, gs, gs, 3); } }
    @Override public String wiredName() { return "Ticket machine"; }
    @Override public String gearState() { return term.powered() ? sold + " tickets sold" + (stationName.isEmpty() ? "" : " · " + stationName) : "NO 12 V - screen dark, no sales"; }

    private final com.dogpound.railmap.settings.SettingsStore cfg = new com.dogpound.railmap.settings.SettingsStore(this);
    private int sold, punched;
    private long revenue;

    @Override public String settingsTitle() { return "Ticket Machine"; }
    @Override public com.dogpound.railmap.settings.SettingsStore settings() { return cfg; }
    @Override public void onSettingsChanged(String key) { sync(); }

    @Override
    public java.util.List<com.dogpound.railmap.settings.Setting> settingDefs() {
        java.util.List<com.dogpound.railmap.settings.Setting> l = new java.util.ArrayList<>();
        String f = "Fares", h = "Service", b = "Boarding", st = "Status";
        l.add(com.dogpound.railmap.settings.Setting.bool(f, "free", "Free travel", "Print tickets without taking anything", false));
        l.add(com.dogpound.railmap.settings.Setting.text(f, "item", "Pay with (item id)", "Blank = the mod config's fare item, e.g. minecraft:iron_nugget or realmcoin:coin", "", 64));
        l.add(com.dogpound.railmap.settings.Setting.num(f, "per", "Blocks per fare item", "Longer trips cost more: one item per this many blocks", RailMapConfig.fareBlocksPerItem, 10, 5000, 10, "blocks"));
        l.add(com.dogpound.railmap.settings.Setting.num(f, "min", "Minimum fare", "", 1, 0, 64, 1, "items"));
        l.add(com.dogpound.railmap.settings.Setting.num(f, "max", "Maximum fare", "0 = no cap", 0, 0, 640, 5, "items"));
        l.add(com.dogpound.railmap.settings.Setting.choice(f, "scheme", "Fare scheme", "Distance: by blocks; Zones: by fare zones crossed (set on each Station Master's Desk); Flat: the minimum fare everywhere", "Distance", "Distance", "Zones", "Flat"));
        l.add(com.dogpound.railmap.settings.Setting.num(f, "perZone", "Each extra zone costs", "Zones scheme: on top of the minimum fare", 1, 0, 64, 1, "items"));
        l.add(com.dogpound.railmap.settings.Setting.bool(f, "peak", "Peak pricing", "+25% from 07:00-09:00 and 17:00-19:00 (game clock)", false));
        l.add(com.dogpound.railmap.settings.Setting.bool(f, "roundTrips", "Sell round trips", "", true));
        l.add(com.dogpound.railmap.settings.Setting.num(f, "roundPct", "Round trip price", "Percent of two singles", 100, 50, 200, 5, "%"));
        l.add(com.dogpound.railmap.settings.Setting.bool(h, "hours", "Opening hours", "Only sell between the hours below (game clock)", false));
        l.add(com.dogpound.railmap.settings.Setting.num(h, "openAt", "Opens at", "", 6, 0, 23, 1, ":00"));
        l.add(com.dogpound.railmap.settings.Setting.num(h, "closeAt", "Closes at", "", 22, 0, 23, 1, ":00"));
        l.add(com.dogpound.railmap.settings.Setting.text(h, "closedMsg", "Closed message", "", "This machine is closed. Come back in the morning!", 80));
        l.add(com.dogpound.railmap.settings.Setting.bool(b, "holdSignals", "Hold linked signals while boarding", "Signals you linked with the wrench stay at Stop while a train boards here", true));
        l.add(com.dogpound.railmap.settings.Setting.num(b, "pulse", "Redstone pulse on ticket", "", 30, 2, 200, 2, "ticks"));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Station", stationName.isEmpty() ? "none in range" : stationName));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Tickets sold", String.valueOf(sold)));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Tickets used here", String.valueOf(punched)));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Fares taken", revenue + " items"));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Linked signals", String.valueOf(linked.size())));
        com.dogpound.railmap.wire.IWiredGear.settings(l, st, this);
        return l;
    }

    private boolean open() {
        if (!cfg.bool("hours") || world == null) return true;
        int h = (int) ((world.getWorldTime() % 24000) / 1000 + 6) % 24, a = cfg.num("openAt"), c = cfg.num("closeAt");
        return a <= c ? h >= a && h < c : h >= a || h < c;
    }

    public boolean roundTrips() { return cfg.bool("roundTrips"); }

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
        if (world != null && !world.isRemote) { if (term.tick(world, pos, wiredName())) { showSelection(); gearChanged(); } if (!term.powered()) return; }
        if (world.isRemote) return;
        ticks++;
        if (pulse > 0 && --pulse == 0) world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
        if (ticks % 10 == 0 && showSelection()) sync();
        if (ticks % 20 != 0) return;
        resolveStation();
        boolean now = station != Long.MIN_VALUE && Dispatcher.boardingAt(world, station);
        if (now != boarding) {
            boarding = now;
            world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
            // Only on a change, so a relay case wired to the same signal keeps its say otherwise.
            if (cfg.bool("holdSignals")) for (BlockPos p : linked) {
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

    /** this machine's fare item (Settings Console), else the config one; null = free */
    public Item fareItemHere() {
        if (cfg.bool("free")) return null;
        String id = cfg.text("item").trim();
        if (!id.isEmpty()) { Item it = Item.getByNameOrId(id); if (it != null) return it; }
        return fareItem();
    }

    /** Fare in fare items from here to {@code dest}; 0 when tickets are free. */
    public int fare(long dest, boolean round) {
        if (fareItemHere() == null) return 0;
        double d = Math.sqrt(BlockPos.fromLong(dest).distanceSq(BlockPos.fromLong(station)));
        int one = switch (cfg.text("scheme")) {
            case "Zones" -> cfg.num("min") + (com.dogpound.railmap.economy.PassengerData.get(world).zonesBetween(station, dest) - 1) * cfg.num("perZone");
            case "Flat" -> Math.max(1, cfg.num("min"));
            default -> Math.max(cfg.num("min"), (int) Math.ceil(d / Math.max(1, cfg.num("per"))));
        };
        if (cfg.bool("peak") && com.dogpound.railmap.economy.PassengerData.peak(com.dogpound.railmap.economy.PassengerData.hour(world))) one = (int) Math.ceil(one * 1.25);
        if (cfg.num("max") > 0) one = Math.min(one, cfg.num("max"));
        return round ? (int) Math.ceil(one * 2 * cfg.num("roundPct") / 100.0) : one;
    }

    /** Take the fare and print the ticket. Returns a line for the player. */
    public String buy(EntityPlayer player, long dest, boolean round) {
        if (!term.powered()) return "The screen is dark - the machine has no 12 V power";
        resolveStation();
        if (station == Long.MIN_VALUE) return "This machine isn't at a station — name one nearby on the Dispatcher Board";
        StationData stations = StationData.get(world);
        String destName = stations.names().get(dest);
        if (destName == null || dest == station) return "Pick a destination";
        if (!open()) return cfg.text("closedMsg");
        if (round && !cfg.bool("roundTrips")) return "This machine only sells one-way tickets";
        int fare = fare(dest, round);
        Item item = fareItemHere();
        if (fare > 0 && !player.capabilities.isCreativeMode) {
            if (count(player, item) < fare) {
                return "Not enough — this ticket costs " + fare + " " + new ItemStack(item).getDisplayName();
            }
            take(player, item, fare);
            revenue += fare;
            com.dogpound.railmap.economy.PassengerData.get(world).ticketSold(world, station, dest, fare, item);   // the operators get the fare
        }
        sold++;
        RailwayData data = RailwayData.get(world);
        ItemStack ticket = ItemTicket.make(CommonProxy.ticket, station, stationName, dest, destName, round,
                data.nextTicketSerial(), world.getWorldTime() / 24000);
        if (!player.inventory.addItemStackToInventory(ticket)) player.dropItem(ticket, false);
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.CTL_PUSH, SoundCategory.BLOCKS, 0.6f, 1.0f);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.paper"), SoundCategory.BLOCKS, 0.6f, 1.0f);
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
        punched++;
        String reply = Dispatcher.ticketInserted(world, station, to, player.getName());
        pulse = Math.max(2, cfg.num("pulse"));
        world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.9f, 1.0f);
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
        t.setInteger("sold", sold);
        t.setInteger("punched", punched);
        t.setLong("revenue", revenue);
        term.write(t);
        cfg.write(t);
        ctl.write(t);
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
        sold = t.getInteger("sold");
        punched = t.getInteger("punched");
        revenue = t.getLong("revenue");
        term.read(t);
        cfg.read(t);
        ctl.read(t);
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
