package com.dogpound.railmap.ride;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * Haul Rope Spool: strings a line. Sneak + right-click the air to pick GONDOLA / AERIAL TRAM / FUNICULAR, then click
 * station A, the top section of each pylon in order (ropeways), and station B. A funicular follows its laid track from
 * A to B by itself (no pylons). Click a machine room, desk or tension weight after clicking one of a line's stations
 * with sneak to link that part to it by hand.
 */
public class ItemRopeSpool extends Item {
    public ItemRopeSpool() {
        setRegistryName(RailMap.MODID, "rope_spool");
        setTranslationKey(RailMap.MODID + ".rope_spool");
        setCreativeTab(RailMapTab.INSTANCE);
        setMaxStackSize(1);
    }

    static NBTTagCompound tag(ItemStack s) { if (!s.hasTagCompound()) s.setTagCompound(new NBTTagCompound()); return s.getTagCompound(); }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
        ItemStack s = p.getHeldItem(hand);
        if (!p.isSneaking()) return new ActionResult<>(EnumActionResult.PASS, s);
        if (!w.isRemote) {
            NBTTagCompound t = tag(s);
            int k = (t.getInteger("kind") + 1) % 3;
            t.setInteger("kind", k);
            t.removeTag("a"); t.removeTag("pyl");
            p.sendStatusMessage(new TextComponentString("§bHaul rope: " + RideGeom.KIND[k] + " §7- click station A to start"), true);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, s);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer p, World w, BlockPos pos, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
        if (w.isRemote) return EnumActionResult.SUCCESS;
        ItemStack s = p.getHeldItem(hand);
        NBTTagCompound t = tag(s);
        p.sendStatusMessage(new TextComponentString(use(w, p, pos, t)), true);
        return EnumActionResult.SUCCESS;
    }

    static String use(World w, EntityPlayer p, BlockPos pos, NBTTagCompound t) {
        RideData d = RideData.get(w);
        TileEntity te = w.getTileEntity(pos);
        int kind = t.getInteger("kind");
        if (te instanceof TileRideStation st) {
            RideData.Line on = d.byStation(pos);
            if (p.isSneaking() || on != null && !t.hasKey("a")) {
                if (on == null) return "§7This station has no rope yet";
                t.setInteger("sel", on.id);
                return "§b" + on.label() + " selected §7- click its machine room, desk or tension weight to link them";
            }
            if (!t.hasKey("a")) {
                t.setLong("a", pos.toLong());
                t.setTag("pyl", new NBTTagList());
                return "§bStation A set §7- " + (kind == RideGeom.FUNICULAR ? "now click the top station" : "now click each pylon's top section in order, then station B");
            }
            BlockPos a = BlockPos.fromLong(t.getLong("a"));
            if (a.equals(pos)) return "§7That is station A - click the other end";
            if (on != null || d.byStation(a) != null) return "§cA station can end only one line";
            List<BlockPos> pyl = new ArrayList<>();
            NBTTagList pl = t.getTagList("pyl", 4);
            for (int i = 0; i < pl.tagCount(); i++) pyl.add(BlockPos.fromLong(((NBTTagLong) pl.get(i)).getLong()));
            String r = build(w, d, kind, a, pyl, pos, p);
            t.removeTag("a"); t.removeTag("pyl");
            return r;
        }
        if (w.getBlockState(pos).getBlock() instanceof RideBlocks.Pylon) {
            if (!t.hasKey("a")) return "§7Click station A first";
            if (kind == RideGeom.FUNICULAR) return "§7A funicular runs on its track - no pylons";
            BlockPos top = new BlockPos(pos.getX(), RideBlocks.Pylon.top(w, pos), pos.getZ());
            NBTTagList pl = t.getTagList("pyl", 4);
            for (int i = 0; i < pl.tagCount(); i++) if (((NBTTagLong) pl.get(i)).getLong() == top.toLong()) return "§7That pylon is already on this rope";
            pl.appendTag(new NBTTagLong(top.toLong()));
            t.setTag("pyl", pl);
            return "§bPylon " + pl.tagCount() + " added (head " + (top.getY() + 2) + " m) §7- next pylon or station B";
        }
        if (te instanceof RideTile part && !(te instanceof TileRideStation)) {
            RideData.Line l = d.line(t.getInteger("sel"));
            if (l == null) return "§7Sneak-click one of the line's stations with the spool first";
            if (part instanceof TileRideTension && l.kind == RideGeom.FUNICULAR) return "§7A funicular's haul rope has no tension weight";
            return part.linkTo(l);
        }
        return "§7Click a ropeway station, a pylon or a machine room / desk / tension weight";
    }

    /** string the rope: A -> pylons -> B (ropeways), or along the track (funicular) */
    static String build(World w, RideData d, int kind, BlockPos a, List<BlockPos> pylons, BlockPos b, EntityPlayer p) {
        List<double[]> sup = new ArrayList<>();
        double sy = kind == RideGeom.FUNICULAR ? 0.25 : 3.1;
        sup.add(new double[]{a.getX() + 0.5, a.getY() + sy, a.getZ() + 0.5});
        if (kind == RideGeom.FUNICULAR) {
            List<BlockPos> path = trace(w, a, b);
            if (path == null) return "§cNo continuous funicular track from station A to station B (lay track blocks between them, rising at most one block per block)";
            for (BlockPos q : path) sup.add(new double[]{q.getX() + 0.5, RideBlocks.Track.railY(w, q), q.getZ() + 0.5});
        } else {
            for (BlockPos q : pylons) sup.add(new double[]{q.getX() + 0.5, q.getY() + 2.0, q.getZ() + 0.5});
        }
        sup.add(new double[]{b.getX() + 0.5, b.getY() + sy, b.getZ() + 0.5});
        double len = 0;
        for (int i = 0; i + 1 < sup.size(); i++) {
            double[] u = sup.get(i), v = sup.get(i + 1);
            double h = Math.hypot(v[0] - u[0], v[2] - u[2]);
            if (kind != RideGeom.FUNICULAR && h < 4) return "§cSpan " + (i + 1) + " is only " + Math.round(h) + " m - supports must be at least 4 m apart";
            if (kind != RideGeom.FUNICULAR && Math.abs(v[1] - u[1]) > h * 1.2) return "§cSpan " + (i + 1) + " is steeper than 50° - add a pylon";
            len += RideGeom.dist(u, v);
        }
        if (len > RideConfig.maxLength) return "§cLine " + Math.round(len) + " m is over the " + RideConfig.maxLength + " m limit (config)";
        if (len < 8) return "§cToo short";
        RideData.Line l = d.add(kind);
        l.supports.addAll(sup);
        l.stA = a.toLong();
        l.stB = b.toLong();
        for (BlockPos q : pylons) l.pylons.add(q.toLong());
        l.cabins = Math.max(2, Math.min(8, (int) (len * 2 / 40)));
        l.gate[0] = l.gate[1] = kind != RideGeom.GONDOLA;
        if (w.getTileEntity(a) instanceof TileRideStation s1) s1.lineId = l.id;
        if (w.getTileEntity(b) instanceof TileRideStation s2) s2.lineId = l.id;
        d.changed();
        com.dogpound.railmap.alarm.Recorder.event(w, l.label() + " rope strung by " + p.getName() + ", " + Math.round(len) + " m");
        return "§a" + l.label() + " strung: " + Math.round(len) + " m, " + (sup.size() - 2) + (kind == RideGeom.FUNICULAR ? " track points" : " pylons") + " §7- place its machine room, desk"
                + (kind == RideGeom.FUNICULAR ? "" : " and tension weight") + " near a station";
    }

    /** the funicular track from beside A to beside B (each step: a horizontal neighbour, level or one block up / down) */
    static List<BlockPos> trace(World w, BlockPos a, BlockPos b) {
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> path = new ArrayList<>();
        BlockPos cur = a;
        for (int step = 0; step < 4000; step++) {
            BlockPos best = null;
            double bd = Double.MAX_VALUE;
            for (EnumFacing f : EnumFacing.HORIZONTALS) for (int dy = -1; dy <= 1; dy++) {
                BlockPos q = cur.offset(f).up(dy);
                if (q.equals(b) && !path.isEmpty()) return path;
                if (seen.contains(q) || !(w.getBlockState(q).getBlock() instanceof RideBlocks.Track)) continue;
                double dd = q.distanceSq(b);
                if (dd < bd) { bd = dd; best = q; }
            }
            if (best == null) {
                for (EnumFacing f : EnumFacing.HORIZONTALS) for (int dy = -1; dy <= 1; dy++) if (cur.offset(f).up(dy).equals(b)) return path;
                return null;
            }
            seen.add(best);
            path.add(best);
            cur = best;
        }
        return null;
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        int k = s.hasTagCompound() ? s.getTagCompound().getInteger("kind") : 0;
        tip.add("§7Mode: §b" + RideGeom.KIND[k] + " §8(sneak + right-click air to change)");
        tip.add("§8Station A -> pylon tops in order -> station B. Funicular: A -> B along its track");
        if (s.hasTagCompound() && s.getTagCompound().hasKey("a")) tip.add("§eStringing: " + s.getTagCompound().getTagList("pyl", 4).tagCount() + " pylons so far");
    }
}
