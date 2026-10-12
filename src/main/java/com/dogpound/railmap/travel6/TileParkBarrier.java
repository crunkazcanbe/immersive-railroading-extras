package com.dogpound.railmap.travel6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.safety.SafetyTile;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Car park barrier (batch 6b #10): a post with a two-block boom that really blocks the lane (the boom's far half is an
 * invisible collision block it places and lifts). MODE key: ENTRY or EXIT. Drive up to it (riding anything - an
 * Immersive Vehicles car, a horse, a cart): ENTRY issues a parking ticket and lifts the boom - unless the pay
 * station's sign says FULL; EXIT takes a paid ticket and lifts it, or pays from your Pride Card on the spot (the park
 * & ride rate if you rode the railway), else PAY AT MACHINE. Road traffic is waved through. On foot: TICKET button.
 * HELP calls the control room (alarm). The STAFF key holds the boom up. No 12 V: the boom stands up (fail safe for
 * emergency vehicles); no mains (the boom motor, 230 V) the same.
 */
public class TileParkBarrier extends SafetyTile {
    private final com.dogpound.railmap.depot.Mains mains = new com.dogpound.railmap.depot.Mains();
    private int openFor, in, out;
    private String l1 = "", l2 = "";
    private long msgUntil;
    private final Map<UUID, Long> served = new HashMap<>();

    @Override public String wiredName() { return "Car park barrier " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return !powered() ? "NO 12 V - boom up" : !mains.ok() ? "NO MAINS - boom up (" + mains.what + ")" : (exit() ? "EXIT" : "ENTRY") + " · " + (isOpen() ? "UP" : "DOWN") + " · " + in + " in / " + out + " out"; }

    boolean exit() { return ctl.get("k_mode") == 1; }
    public boolean isOpen() { return world != null && (!powered() || !mains.ok() || openFor > 0 || ctl.get("k_up") == 1); }   // no 12 V or no mains: the spring lifts the boom

    EnumFacing facing() { return controlFacing(); }
    /** the cell the boom's far half reaches over (the model's -x side) */
    public BlockPos armPos() { return pos.offset(facing().rotateYCCW()); }

    TileParkPay pay() {
        TileParkPay best = null;
        double bd = 48 * 48;
        for (TileParkPay t : StnUtil.near(world, TileParkPay.class, pos, 48)) { double d = t.getPos().distanceSq(pos); if (d < bd) { bd = d; best = t; } }
        return best;
    }

    String garage() { TileParkPay p = pay(); return p == null ? "car park " + pos.getX() + "," + pos.getZ() : p.garage(); }

    void say(String a, String b) { l1 = a; l2 = b; msgUntil = world.getTotalWorldTime() + 80; }

    void lift() {
        boolean was = isOpen();
        openFor = 120;
        if (!was) { world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.clunk"), SoundCategory.BLOCKS, 0.6f, 0.8f); sync(); }
    }

    void sync() {
        IBlockState s = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, s, s, 3);
        markDirty();
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "k_mode": case "k_up": { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); sync(); return id.equals("k_up") ? (ctl.get(id) == 1 ? "§eSTAFF: boom held UP" : "§eSTAFF: boom on AUTO") : (exit() ? "§eMode EXIT" : "§eMode ENTRY"); }
            case "test": return powered() ? lampTest() : "§cNo 12 V";
            case "help": {
                AlarmManager.get(world).raise(world, "travel6.park.help." + pos.toLong(), AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR,
                        "Help requested at the " + (exit() ? "exit" : "entry") + " barrier, " + garage() + (who == null ? "" : " (" + who.getName() + ")"), pos, false);
                say("CALLING", "PLEASE WAIT");
                return "§eHelp called - the control room has been alerted";
            }
            case "tkt": case "pad": if (who == null) return "§7Drive up to the barrier"; break;
            default: return "§7" + gearState();
        }
        if (!powered()) return "§7The barrier is dead (no 12 V) - its boom stands up, drive through";
        return serve(who, true);
    }

    /** a driver (or someone at the button) at the barrier */
    String serve(EntityPlayer p, boolean button) {
        long now = world.getTotalWorldTime();
        TravelData d = TravelData.get(world);
        String g = garage();
        if (!exit()) {
            ItemStack has = Parking.ticketIn(p);
            TravelData.Park s = Parking.session(world, has);
            if (s != null && s.live() && s.garage.equals(g)) { lift(); say("WELCOME BACK", "TICKET #" + s.id); return "§7You already have ticket #" + s.id + " - drive in"; }
            TileParkPay pay = pay();
            if (pay != null && pay.full()) { say("CAR PARK FULL", "SORRY"); return "§cCar park FULL"; }
            TravelData.Park np = d.newPark(now);
            np.who = p.getUniqueID(); np.whoName = p.getName(); np.garage = g;
            ItemStack t = ItemTravel.parkTicket(np, g);
            if (!p.inventory.addItemStackToInventory(t)) p.dropItem(t, false);
            in++;
            lift();
            world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.5f, 1.4f);
            say("TAKE TICKET", "#" + np.id + " " + Recorder.clock(world, now));
            return "§aTicket #" + np.id + " - keep it, pay before you leave (" + Coins.fmt(TravelConfig.parkPerHour) + "/h, park & ride with a Pride Card)";
        }
        ItemStack t = Parking.ticketIn(p);
        TravelData.Park s = Parking.session(world, t);
        if (s == null || !s.live()) { say("NO TICKET", "PRESS HELP"); return "§cNo valid parking ticket - pay at the machine or press HELP"; }
        if (!Parking.paidUp(world, s)) {
            ItemStack cs = Cards.held(p);
            TravelData.Card c = d.cards.get(ItemTravel.cardId(cs));
            long due = Parking.due(world, s);
            String no = due == 0 ? Parking.pay(world, p, s, null, g) : c == null ? "PAY AT MACHINE" : Parking.pay(world, p, s, c, g);
            if (no != null) { say("PAY AT MACHINE", Coins.fmt(due)); return "§c" + no; }
            if (c != null) ItemTravel.sync(cs, c);
            say("PAID " + Coins.fmt(due), c == null ? "THANK YOU" : "PRIDE CARD");
        } else say("THANK YOU", "GOODBYE");
        s.out = now;
        d.markDirty();
        t.shrink(1);
        out++;
        lift();
        return "§aThank you" + (s.rail ? " - park & ride" : "") + ", goodbye";
    }

    @Override
    protected void serverTick() {
        if (mains.tick(world, pos, term, wiredName(), isOpen() ? 1 : 2)) sync();
        if (openFor > 0 && --openFor == 0) sync();
        if (age % 10 == 0) arm();
        if (age % 5 != 0) return;
        if (powered()) {
            long now = world.getTotalWorldTime();
            EnumFacing f = facing();
            AxisAlignedBB zone = new AxisAlignedBB(pos).union(new AxisAlignedBB(armPos())).union(new AxisAlignedBB(pos.offset(f, 2))).union(new AxisAlignedBB(armPos().offset(f, 2))).grow(0, 1, 0);
            for (Entity e : world.getEntitiesWithinAABB(Entity.class, zone)) {
                if (e instanceof com.dogpound.railmap.road.EntityRoadVehicle) { if (!isOpen()) { lift(); if (exit()) out++; else in++; } continue; }
                if (!(e instanceof EntityPlayer p) || !p.isRiding()) continue;
                Long last = served.get(p.getUniqueID());
                if (last != null && now - last < 100) continue;
                served.put(p.getUniqueID(), now);
                String r = serve(p, false);
                p.sendStatusMessage(new net.minecraft.util.text.TextComponentString(r), true);
            }
            served.values().removeIf(t -> now - t > 400);
        }
        boolean m = world.getTotalWorldTime() < msgUntil;
        TileParkPay pay = pay();
        put("go", isOpen());
        put("stop", !isOpen());
        put("fulll", !exit() && pay != null && pay.full());
        put("pwr", powered());
        put("mainsl", mains.ok());
        put("upl", ctl.get("k_up") == 1);
        put("nin", in);
        put("nout", out);
        text("l1", m ? l1 : exit() ? "EXIT - INSERT TICKET" : pay != null && pay.full() ? "CAR PARK FULL" : "PRESS FOR TICKET");
        text("l2", m ? l2 : exit() ? "OR TOUCH PRIDE CARD" : pay == null ? StnUtil.up(StnUtil.cut(garage(), 18)) : pay.free() + " SPACES");
    }

    /** the boom's far half: a collision block over the next cell while the boom is down */
    void arm() {
        BlockPos a = armPos();
        IBlockState s = world.getBlockState(a);
        boolean there = s.getBlock() == TravelRegistry.arm;
        if (!isOpen() && !there && s.getBlock().isReplaceable(world, a)) world.setBlockState(a, TravelRegistry.arm.getDefaultState().withProperty(BlockParkArm.FACING, facing()), 3);
        else if (isOpen() && there) world.setBlockToAir(a);
    }

    @Override protected void onPowerChange(boolean on) { sync(); }

    @Override public void gearInfo(Map<String, Object> m) { m.put("mains", mains.what); }
    @Override protected void save(NBTTagCompound t) { mains.write(t); t.setInteger("open", openFor); t.setInteger("in", in); t.setInteger("out", out); }
    @Override protected void load(NBTTagCompound t) { mains.read(t); openFor = t.getInteger("open"); in = t.getInteger("in"); out = t.getInteger("out"); }
}
