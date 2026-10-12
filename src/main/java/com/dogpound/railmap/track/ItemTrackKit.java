package com.dogpound.railmap.track;

import cam72cam.immersiverailroading.tile.TileRail;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.safety.SafetyTicker;
import com.dogpound.railmap.safety.TilePossessionSwitch;
import com.dogpound.railmap.server.TrainTracker;
import com.dogpound.railmap.track.TrackData.Metric;
import com.dogpound.railmap.track.TrackData.Seg;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Predicate;

/**
 * Track Maintenance Kit (her list #13 "grinders, tampers and ballast regulators restore condition" - the hand version a
 * track gang carries; the big machines are IR stock tagged TAMPER / GRINDER / REGULATOR). Sneak-right-click to pick the
 * job, right-click a rail to do it, with real materials:
 * <ul>
 *   <li>SPOT TAMP - packs the ballast under the sleepers: top, line and twist back toward new (no materials);</li>
 *   <li>RESLEEPER - 4 logs: replaces failed sleepers, pulls the gauge back;</li>
 *   <li>RE-RAIL - 3 iron ingots: new rail, head wear and surface defects gone;</li>
 *   <li>BALLAST - 4 gravel: dig out the fouled ballast, drop clean stone;</li>
 *   <li>REGAUGE - 2 iron nuggets: new fastenings, gauge pulled back to nominal.</li>
 * </ul>
 * Safety first: no work while a train is moving within 64 blocks unless a possession (Track Blocking Switch) covers you.
 */
public class ItemTrackKit extends Item {
    public static final String[] JOBS = {"SPOT TAMP", "RESLEEPER", "RE-RAIL", "BALLAST", "REGAUGE",
            "LAY BALLASTED", "LAY SLAB", "LAY HEAVY HAUL", "LAY METRO", "LAY HIGH SPEED"};   // batch-3 #10: rebuild a piece to a construction standard
    /** batch-3 #10: materials per started 8 m of track for each standard: {item id, count} pairs (meta 0 unless noted) */
    private static final Object[][] STD_MATS = {
            {Blocks.GRAVEL, 4, Blocks.LOG, 2},                                   // ballasted: stone + timber sleepers
            {Blocks.CONCRETE, 4, Items.IRON_INGOT, 1},                            // slab: concrete slab + embedded fastenings
            {Items.IRON_INGOT, 3, Blocks.GRAVEL, 4, Blocks.STONE, 2},             // heavy haul: head-hardened rail, deep ballast, heavy sleepers
            {Items.IRON_INGOT, 2, Items.SLIME_BALL, 1, Blocks.CONCRETE, 1},       // metro: resilient (rubber) baseplates on concrete
            {Items.IRON_INGOT, 2, Blocks.CONCRETE, 2, Items.REDSTONE, 1}};        // high speed: tight-tolerance rail, monitoring

    public ItemTrackKit() {
        setRegistryName(RailMap.MODID, "track_kit");
        setTranslationKey(RailMap.MODID + ".track_kit");
        setCreativeTab(RailMapTab.INSTANCE);
        setMaxStackSize(1);
    }

    private static int job(ItemStack s) { return s.getTagCompound() == null ? 0 : Math.floorMod(s.getTagCompound().getInteger("job"), JOBS.length); }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
        tip.add("§eJob: " + JOBS[job(stack)] + " §7(sneak-right-click to change)");
        tip.add("§8Spot tamp: free · resleeper: 4 logs · re-rail: 3 iron ingots · ballast: 4 gravel · regauge: 2 iron nuggets");
        tip.add("§8No work with trains moving within 64 blocks unless a possession covers you");
        tip.add("§8LAY <standard>: rebuild the piece to ballasted / slab / heavy haul / metro / high speed (materials per 8 m + coins)");
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer p, EnumHand hand) {
        ItemStack st = p.getHeldItem(hand);
        if (!p.isSneaking()) return new ActionResult<>(EnumActionResult.PASS, st);
        if (!world.isRemote) {
            NBTTagCompound t = st.getTagCompound() == null ? new NBTTagCompound() : st.getTagCompound();
            t.setInteger("job", (job(st) + 1) % JOBS.length);
            st.setTagCompound(t);
            p.sendStatusMessage(new TextComponentString("§eTrack gang job: " + JOBS[job(st)]), true);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, st);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer p, World world, BlockPos pos, EnumHand hand, EnumFacing f, float hx, float hy, float hz) {
        TileRail r = Rails.pieceAt(world, pos);
        if (r == null) return EnumActionResult.PASS;
        if (world.isRemote) return EnumActionResult.SUCCESS;
        ItemStack kit = p.getHeldItem(hand);
        if (p.isSneaking()) return onItemRightClick(world, p, hand).getType();
        String unsafe = unsafe(world, pos);
        if (unsafe != null) { p.sendStatusMessage(new TextComponentString("§c" + unsafe), true); return EnumActionResult.SUCCESS; }
        BlockPos key = Rails.pos(r);
        TrackData d = TrackData.get(world);
        Seg s = d.seg(key);
        int job = job(kit);
        String did;
        switch (job) {
            case 0 -> {
                for (Metric m : new Metric[]{Metric.TOP, Metric.LINE, Metric.TWIST}) s.set(m, m.fresh + (s.get(m) - m.fresh) * 0.45);
                did = "spot tamped";
            }
            case 1 -> {
                if (!take(p, i -> Block.getBlockFromItem(i.getItem()) == Blocks.LOG || Block.getBlockFromItem(i.getItem()) == Blocks.LOG2, 4)) return need(p, "4 logs (new sleepers)");
                s.set(Metric.SLEEPERS, Math.max(0, s.get(Metric.SLEEPERS) - 25));
                s.set(Metric.GAUGE, Math.max(0, s.get(Metric.GAUGE) * 0.5));
                did = "resleepered";
            }
            case 2 -> {
                if (!take(p, i -> i.getItem() == Items.IRON_INGOT, 3)) return need(p, "3 iron ingots (new rail)");
                s.set(Metric.RAIL, 0);
                s.set(Metric.SURFACE, 0);
                s.tonnes = 0;
                did = "re-railed";
            }
            case 3 -> {
                if (!take(p, i -> Block.getBlockFromItem(i.getItem()) == Blocks.GRAVEL, 4)) return need(p, "4 gravel (clean ballast)");
                s.set(Metric.FOULING, Math.max(Metric.FOULING.fresh, s.get(Metric.FOULING) - 30));
                s.set(Metric.TOP, Math.max(Metric.TOP.fresh, s.get(Metric.TOP) - 0.3));
                did = "ballast cleaned";
            }
            case 5, 6, 7, 8, 9 -> {
                String r2 = layStandard(p, world, r, key, s, TrackData.Standard.of(job - 5));
                if (r2 != null) { p.sendStatusMessage(new TextComponentString(r2), true); return EnumActionResult.SUCCESS; }
                did = "rebuilt to " + s.standard().label.toLowerCase(java.util.Locale.ROOT) + " standard";
            }
            default -> {
                if (!take(p, i -> i.getItem() == Items.IRON_NUGGET, 2)) return need(p, "2 iron nuggets (fastenings)");
                s.set(Metric.GAUGE, Math.max(0, s.get(Metric.GAUGE) - 8));
                did = "regauged";
            }
        }
        s.workedAt = world.getTotalWorldTime();
        s.lastWork = did.toUpperCase(java.util.Locale.ROOT) + " by " + p.getName();
        d.markDirty();
        world.playSound(null, pos, job == 2 ? com.dogpound.railmap.sound.PackSounds.ev("machine.air_tool") : SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.BLOCKS, 0.7f, 1.0f);
        Recorder.event(world, "TRACK GANG: " + Rails.xz(key) + " " + did + " by " + p.getName());
        com.dogpound.railmap.api.QuestEvents.fire(world, p, key, "track_work", "job", did, "tqi", s.tqi(), "tsr", s.tsr());
        p.sendStatusMessage(new TextComponentString("§a" + Rails.xz(key) + " " + did + " - TQI now " + s.tqi() + (s.tsr() > 0 ? " §c(TSR " + s.tsr() + " still)" : " §a(no restriction)")), true);
        return EnumActionResult.SUCCESS;
    }

    /** batch-3 #10: rebuild one piece to a standard (null = done; else why not, nothing taken) */
    private static String layStandard(EntityPlayer p, World w, TileRail r, BlockPos key, Seg s, TrackData.Standard st) {
        if (s.std == st.ordinal()) return "§7Already " + st.label.toLowerCase(java.util.Locale.ROOT) + " track - use the maintenance jobs";
        int len = Math.max(1, s.lengthM > 0 ? s.lengthM : Rails.lengthM(r));
        int lots = (len + 7) / 8;
        Object[] mats = STD_MATS[st.ordinal()];
        StringBuilder needs = new StringBuilder();
        for (int i = 0; i < mats.length; i += 2) {
            Item it = mats[i] instanceof Block b ? Item.getItemFromBlock(b) : (Item) mats[i];
            int n = (int) mats[i + 1] * lots;
            if (count(p, it) < n) needs.append(needs.length() == 0 ? "" : ", ").append(n).append(' ').append(new ItemStack(it).getDisplayName());
        }
        if (needs.length() > 0 && !p.capabilities.isCreativeMode) return "§c" + st.label + " track (" + len + " m) needs " + needs;
        long cents = (long) st.costPerM * len;
        if (TrackConfig.trackStandardCostsCoins && p instanceof net.minecraft.entity.player.EntityPlayerMP mp && com.dogpound.railmap.economy.Coins.available()
                && !com.dogpound.railmap.economy.Coins.charge(mp, cents, "track: " + st.label + " " + len + " m at " + Rails.xz(key)))
            return "§c" + st.label + " track costs " + com.dogpound.railmap.economy.Coins.fmt(cents) + " for " + len + " m - not enough in your account";
        for (int i = 0; i < mats.length; i += 2) {
            Item it = mats[i] instanceof Block b ? Item.getItemFromBlock(b) : (Item) mats[i];
            take(p, x -> x.getItem() == it, (int) mats[i + 1] * lots);
        }
        s.std = st.ordinal();
        for (Metric m : Metric.values()) s.set(m, m.fresh);                // new track to the new standard
        s.tonnes = 0;
        s.overloads = s.overspeeds = 0;
        s.lengthM = len;
        return null;
    }

    private static int count(EntityPlayer p, Item it) { int n = 0; for (ItemStack x : p.inventory.mainInventory) if (!x.isEmpty() && x.getItem() == it) n += x.getCount(); return n; }

    private static EnumActionResult need(EntityPlayer p, String what) { p.sendStatusMessage(new TextComponentString("§cThis job needs " + what), true); return EnumActionResult.SUCCESS; }

    /** a moving train nearby with no possession over the spot */
    static String unsafe(World w, BlockPos at) {
        for (TilePossessionSwitch ps : SafetyTicker.possessions(w))
            if (ps.blocking() && ps.getPos().distanceSq(at) <= (double) ps.zone() * ps.zone()) return null;
        for (TrainNode t : TrainTracker.latest(w)) {
            if (!t.moving()) continue;
            double dx = t.x - at.getX(), dz = t.z - at.getZ();
            if (dx * dx + dz * dz < 64 * 64 && Math.abs(t.y - at.getY()) < 12) return "STAND CLEAR - a train is moving within 64 blocks. Take a possession (Track Blocking Switch) to work on the line.";
        }
        return null;
    }

    private static boolean take(EntityPlayer p, Predicate<ItemStack> ok, int n) {
        if (p.capabilities.isCreativeMode) return true;
        int got = 0;
        for (ItemStack s : p.inventory.mainInventory) if (!s.isEmpty() && ok.test(s)) got += s.getCount();
        if (got < n) return false;
        for (ItemStack s : p.inventory.mainInventory) if (n > 0 && !s.isEmpty() && ok.test(s)) { int t = Math.min(n, s.getCount()); s.shrink(t); n -= t; }
        return true;
    }
}
