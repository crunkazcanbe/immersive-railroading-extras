package com.dogpound.railmap.track;

import cam72cam.immersiverailroading.tile.TileRail;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import com.dogpound.railmap.track.TrackData.Metric;
import com.dogpound.railmap.track.TrackData.Seg;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * Track Geometry Trolley (her list #13 "track inspection carts generate inspection reports"): the push trolley a track
 * inspector walks along the rails. Hold it and walk the line: every piece of track you pass is measured (top, line,
 * twist, gauge, rail wear, rail surface, sleepers, ballast) and logged, and the action bar shows the live readings.
 * Right-click a rail for a full spot reading; right-click the air to print the inspection report - into a written book
 * if you carry a Book (worst defects first, with the TSR each calls for), or into chat.
 */
public class ItemTrackTrolley extends Item {
    public ItemTrackTrolley() {
        setRegistryName(RailMap.MODID, "track_trolley");
        setTranslationKey(RailMap.MODID + ".track_trolley");
        setCreativeTab(RailMapTab.INSTANCE);
        setMaxStackSize(1);
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
        tip.add("§7Hold it and walk the track: every piece is measured and logged");
        tip.add("§8Right-click a rail: spot reading · right-click air: print the report (carry a Book)");
        NBTTagCompound t = stack.getTagCompound();
        if (t != null) tip.add("§b" + t.getTagList("log", Constants.NBT.TAG_LONG).tagCount() + " pieces logged");
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity e, int slot, boolean held) {
        if (world.isRemote || !held || !(e instanceof EntityPlayer p) || world.getTotalWorldTime() % 10 != 0) return;
        TileRail r = Rails.pieceAt(world, p.posX, p.posY - 0.2, p.posZ);
        if (r == null) return;
        BlockPos key = Rails.pos(r);
        TrackData d = TrackData.get(world);
        Seg s = d.seg(key);
        if (s.lengthM == 0) { s.lengthM = Rails.lengthM(r); s.radius = Rails.radius(r); s.gaugeMm = Rails.gaugeMm(r); }
        TrackCondition.inspect(s, p.getName() + " (trolley)", world.getTotalWorldTime());
        d.markDirty();
        NBTTagCompound t = stack.getTagCompound() == null ? new NBTTagCompound() : stack.getTagCompound();
        NBTTagList log = t.getTagList("log", Constants.NBT.TAG_LONG);
        boolean have = false;
        for (int i = 0; i < log.tagCount(); i++) if (((NBTTagLong) log.get(i)).getLong() == key.toLong()) { have = true; break; }
        if (!have && log.tagCount() < 96) log.appendTag(new NBTTagLong(key.toLong()));
        if (!have) com.dogpound.railmap.api.QuestEvents.fire(world, p, key, "track_inspected", "tqi", s.tqi(), "band", s.band(), "tsr", s.tsr(), "mode", "walk");
        t.setTag("log", log);
        stack.setTagCompound(t);
        int tsr = s.tsr();
        p.sendStatusMessage(new TextComponentString(String.format(Locale.ROOT, "§bTRC §7%s §f%s§7 · top %s · twist %s · gauge %+.0f · rail %s · TQI §f%d§7 · %s",
                Rails.xz(key), s.radius > 0 ? "R" + s.radius : "STR", TrackCondition.fmt(s.seen[Metric.TOP.ordinal()]), TrackCondition.fmt(s.seen[Metric.TWIST.ordinal()]),
                s.seen[Metric.GAUGE.ordinal()], TrackCondition.fmt(s.seen[Metric.RAIL.ordinal()]), s.tqi(), tsr > 0 ? "§cTSR " + tsr : "§aline speed")), true);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer p, World world, BlockPos pos, EnumHand hand, EnumFacing f, float hx, float hy, float hz) {
        TileRail r = Rails.pieceAt(world, pos);
        if (r == null) return EnumActionResult.PASS;
        if (world.isRemote) return EnumActionResult.SUCCESS;
        BlockPos key = Rails.pos(r);
        Seg s = TrackData.get(world).seg(key);
        TrackCondition.inspect(s, p.getName() + " (trolley)", world.getTotalWorldTime());
        TrackData.get(world).markDirty();
        p.sendMessage(new TextComponentString("§b== Track at " + Rails.xz(key) + " §7(" + (s.radius > 0 ? "curve R" + s.radius + " m" : "straight") + ", " + s.gaugeMm + " mm gauge, "
                + Math.round(s.tonnes) + " t carried, fastest " + s.maxKmh + " km/h) TQI " + s.tqi()));
        p.sendMessage(new TextComponentString(TrackCondition.report(s, false)));
        com.dogpound.railmap.api.QuestEvents.fire(world, p, key, "track_inspected", "tqi", s.tqi(), "band", s.band(), "tsr", s.tsr(), "mode", "spot");
        int tsr = s.tsr();
        p.sendMessage(new TextComponentString(tsr > 0 ? "§cTSR " + tsr + " km/h in force - " + s.worst().label + " past the " + (s.band() == 3 ? "IMMEDIATE ACTION" : "intervention") + " limit" : s.band() == 1 ? "§eAlert limit reached - plan maintenance" : "§aWithin limits"));
        if (!s.lastWork.isEmpty()) p.sendMessage(new TextComponentString("§7Last work: " + s.lastWork + " " + (world.getTotalWorldTime() - s.workedAt) / 1200 + " min ago"));
        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer p, EnumHand hand) {
        ItemStack st = p.getHeldItem(hand);
        if (world.isRemote) return new ActionResult<>(EnumActionResult.SUCCESS, st);
        NBTTagCompound t = st.getTagCompound();
        NBTTagList log = t == null ? new NBTTagList() : t.getTagList("log", Constants.NBT.TAG_LONG);
        if (log.tagCount() == 0) { p.sendStatusMessage(new TextComponentString("§7Nothing logged yet - walk the track holding the trolley"), true); return new ActionResult<>(EnumActionResult.SUCCESS, st); }
        TrackData d = TrackData.get(world);
        List<long[]> rows = new ArrayList<>();
        for (int i = 0; i < log.tagCount(); i++) { long k = ((NBTTagLong) log.get(i)).getLong(); Seg s = d.peek(BlockPos.fromLong(k)); if (s != null) rows.add(new long[]{k, s.tqi()}); }
        rows.sort(Comparator.comparingLong(a -> a[1]));
        List<String> pages = new ArrayList<>();
        int defects = 0, tsrs = 0;
        StringBuilder pg = new StringBuilder();
        for (long[] row : rows) {
            Seg s = d.peek(BlockPos.fromLong(row[0]));
            if (s.band() >= 2) defects++;
            if (s.tsr() > 0) tsrs++;
            Metric w = s.worst();
            String line = Rails.xz(BlockPos.fromLong(row[0])) + " TQI " + s.tqi() + "\n " + w.label + " " + TrackCondition.fmt(s.seen == null ? s.get(w) : s.seen[w.ordinal()]) + " " + w.unit + (s.tsr() > 0 ? "\n TSR " + s.tsr() + " km/h" : "") + "\n";
            if (pg.length() + line.length() > 230) { pages.add(pg.toString()); pg = new StringBuilder(); }
            pg.append(line);
        }
        if (pg.length() > 0) pages.add(pg.toString());
        String head = "TRACK INSPECTION\nREPORT\n\nInspector: " + p.getName() + "\nPieces: " + rows.size() + "\nOver intervention: " + defects + "\nTSRs: " + tsrs + "\n\nWorst first.\nLimits: UIC-style\nalert / intervention /\nimmediate action.";
        pages.add(0, head);
        ItemStack book = findBook(p);
        if (book != null) {
            book.shrink(1);
            ItemStack out = new ItemStack(Items.WRITTEN_BOOK);
            NBTTagCompound bt = new NBTTagCompound();
            bt.setString("title", "Track Report " + Rails.xz(BlockPos.fromLong(rows.isEmpty() ? 0 : rows.get(0)[0])));
            bt.setString("author", p.getName());
            NBTTagList pl = new NBTTagList();
            for (String s : pages) pl.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(s))));
            bt.setTag("pages", pl);
            out.setTagCompound(bt);
            if (!p.inventory.addItemStackToInventory(out)) p.dropItem(out, false);
            p.sendMessage(new TextComponentString("§aInspection report printed: " + rows.size() + " pieces, " + defects + " defects, " + tsrs + " TSRs"));
        } else {
            p.sendMessage(new TextComponentString("§b== Track inspection report (" + rows.size() + " pieces; carry a Book to print it) =="));
            for (int i = 0; i < Math.min(12, rows.size()); i++) {
                Seg s = d.peek(BlockPos.fromLong(rows.get(i)[0]));
                p.sendMessage(new TextComponentString("§f" + Rails.xz(BlockPos.fromLong(rows.get(i)[0])) + " §7TQI " + s.tqi() + " " + TrackCondition.report(s, false)));
            }
        }
        t.setTag("log", new NBTTagList());
        return new ActionResult<>(EnumActionResult.SUCCESS, st);
    }

    private static ItemStack findBook(EntityPlayer p) {
        for (ItemStack s : p.inventory.mainInventory) if (!s.isEmpty() && (s.getItem() == Items.BOOK || s.getItem() == Items.WRITABLE_BOOK)) return s;
        return null;
    }
}
