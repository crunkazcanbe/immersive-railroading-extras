package com.dogpound.railmap.track;

import cam72cam.immersiverailroading.tile.TileRail;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Surveyor's total station (her ChatGPT list #18): design the railway before you build it.
 * <ul>
 *   <li>hold it: the screen line measures from your last waypoint to where you stand - distance, rise, gradient
 *       (1 in N), and the curve radius through your last two waypoints and you, with the speed that curve allows;</li>
 *   <li>right-click the ground (or a rail): a waypoint at formation level on top of that block;
 *       sneak-right-click the air: take the last one back;</li>
 *   <li>right-click the air: the instrument's screen - plan, elevation profile against the real ground, cut / fill,
 *       gradients, radii, proposed speed limits, materials, the curve check against the nearest train;</li>
 *   <li>sneak-right-click the ground: set out the route - survey pegs every 20 m written with chainage and cut / fill,
 *       and a Construction Blueprint book (needs a Book; pegs use a stick each).</li>
 * </ul>
 * Every change is exported: the console's TRACK > Survey page and a GeoJSON file per route for the web map. Rename the
 * instrument in an anvil to name the route.
 */
public class ItemSurveyor extends Item {
    public ItemSurveyor() {
        setRegistryName(RailMap.MODID, "surveyor");
        setTranslationKey(RailMap.MODID + ".surveyor");
        setCreativeTab(RailMapTab.INSTANCE);
        setMaxStackSize(1);
    }

    public static NBTTagCompound srv(ItemStack st) {
        NBTTagCompound t = st.getTagCompound() == null ? new NBTTagCompound() : st.getTagCompound();
        if (!t.hasKey("srv")) t.setTag("srv", new NBTTagCompound());
        st.setTagCompound(t);
        return t.getCompoundTag("srv");
    }

    public static String routeName(ItemStack st) { return st.hasDisplayName() ? st.getDisplayName().toUpperCase(Locale.ROOT) : "ROUTE " + (st.getTagCompound() == null ? 0 : st.getTagCompound().getInteger("seed") % 1000); }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
        NBTTagCompound s = stack.getTagCompound() == null ? null : stack.getTagCompound().getCompoundTag("srv");
        int n = s == null ? 0 : Survey.points(s).size();
        tip.add("§b" + routeName(stack) + "§7: " + n + " waypoints" + (n > 1 ? String.format(Locale.ROOT, ", %.0f m", Survey.length(Survey.points(s))) : ""));
        tip.add("§7Right-click ground: waypoint · air: the instrument screen");
        tip.add("§8Sneak + air: undo · sneak + ground: set out pegs + blueprint book · rename it to name the route");
    }

    @Override
    public void onUpdate(ItemStack st, World world, Entity e, int slot, boolean held) {
        if (world.isRemote || !held || !(e instanceof EntityPlayer p) || world.getTotalWorldTime() % 10 != 0) return;
        if (st.getTagCompound() != null && !st.getTagCompound().hasKey("seed")) st.getTagCompound().setInteger("seed", world.rand.nextInt(100000));
        List<int[]> pts = Survey.points(srv(st));
        if (pts.isEmpty()) { p.sendStatusMessage(new TextComponentString("§bSURVEY §7right-click the ground to start a route"), true); return; }
        int[] last = pts.get(pts.size() - 1);
        double run = Math.hypot(p.posX - last[0] - 0.5, p.posZ - last[2] - 0.5), rise = Math.floor(p.posY) - last[1];
        double pm = SurveyMath.gradePermille(rise, run);
        String rs = "";
        if (pts.size() >= 2) {
            int[] a = pts.get(pts.size() - 2);
            double r = SurveyMath.radius(a[0], a[2], last[0], last[2], p.posX - 0.5, p.posZ - 0.5);
            rs = r <= 0 ? " · straight" : String.format(Locale.ROOT, " · R%.0f m (%s)", r, Survey.speedText(SurveyMath.curveKmh(r)));
        }
        p.sendStatusMessage(new TextComponentString(String.format(Locale.ROOT, "§bSURVEY §f%.1f m§7 · rise %+.0f m · grade §f%s%s§7 · total %.0f m", run, rise, SurveyMath.oneIn(pm), rs, Survey.length(pts) + run)), true);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer p, World world, BlockPos pos, EnumHand hand, EnumFacing f, float hx, float hy, float hz) {
        ItemStack st = p.getHeldItem(hand);
        if (world.isRemote) return EnumActionResult.SUCCESS;
        NBTTagCompound s = srv(st);
        if (p.isSneaking()) { setOut(world, p, st, s); return EnumActionResult.SUCCESS; }
        List<int[]> pts = Survey.points(s);
        if (pts.size() >= 64) { p.sendStatusMessage(new TextComponentString("§c64 waypoints is the most one route can hold - set it out, then start a new one"), true); return EnumActionResult.SUCCESS; }
        TileRail r = Rails.pieceAt(world, pos);
        int y = r != null ? pos.getY() : pos.getY() + 1;
        pts.add(new int[]{pos.getX(), y, pos.getZ()});
        Survey.setPoints(s, pts);
        Survey.profile(world, s);
        String file = Survey.export(world, routeName(st), s);
        String extra = "";
        if (pts.size() >= 2) {
            int[] a = pts.get(pts.size() - 2), b = pts.get(pts.size() - 1);
            double run = Math.hypot(b[0] - a[0], b[2] - a[2]);
            extra = String.format(Locale.ROOT, " · %.0f m at %s", run, SurveyMath.oneIn(SurveyMath.gradePermille(b[1] - a[1], run)));
        }
        p.sendStatusMessage(new TextComponentString("§aWaypoint " + pts.size() + " at " + pos.getX() + "," + y + "," + pos.getZ() + extra + (file == null ? "" : " §8(exported)")), true);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer p, EnumHand hand) {
        ItemStack st = p.getHeldItem(hand);
        if (p.isSneaking()) {
            if (!world.isRemote) {
                NBTTagCompound s = srv(st);
                List<int[]> pts = Survey.points(s);
                if (!pts.isEmpty()) { pts.remove(pts.size() - 1); Survey.setPoints(s, pts); Survey.profile(world, s); Survey.export(world, routeName(st), s); }
                p.sendStatusMessage(new TextComponentString(pts.isEmpty() ? "§7Route empty" : "§eLast waypoint removed - " + pts.size() + " left"), true);
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, st);
        }
        if (world.isRemote) com.dogpound.railmap.client.gui.GuiSurvey.open(st);
        return new ActionResult<>(EnumActionResult.SUCCESS, st);
    }

    /** pegs every 20 m (and at each waypoint) + the construction blueprint book */
    private void setOut(World world, EntityPlayer p, ItemStack st, NBTTagCompound s) {
        List<int[]> pts = Survey.points(s);
        if (pts.size() < 2) { p.sendStatusMessage(new TextComponentString("§7Survey at least two waypoints first"), true); return; }
        String name = routeName(st);
        Survey.profile(world, s);
        int[] g = s.getIntArray("gnd");
        double len = Survey.length(pts);
        Block peg = ForgeRegistries.BLOCKS.getValue(new net.minecraft.util.ResourceLocation(RailMap.MODID, "survey_peg"));
        int placed = 0, noStick = 0;
        double[] wch = Survey.chainages(pts), rad = Survey.radii(pts);
        List<Double> stations = new ArrayList<>();
        for (double c = 0; c <= len + 0.01; c += 20) stations.add(c);
        for (double c : wch) stations.add(c);
        List<String> table = new ArrayList<>();
        for (double c : stations) {
            double[] at = Survey.at(pts, c);
            int gi = (int) Math.min(g.length - 1, Math.round(c / Survey.STEP));
            int gy = g.length == 0 ? (int) Math.round(at[2]) : g[Math.max(0, gi)];
            double d = gy - at[2];
            String lev = Math.abs(d) < 0.5 ? "ON GRADE" : (d > 0 ? "CUT " : "FILL ") + String.format(Locale.ROOT, "%.1f", Math.abs(d));
            int seg = 1;
            while (seg < wch.length - 1 && wch[seg] < c) seg++;
            double pm = SurveyMath.gradePermille(pts.get(seg)[1] - pts.get(seg - 1)[1], wch[seg] - wch[seg - 1]);
            double rr = rad[Math.min(rad.length - 1, seg)];
            String info = SurveyMath.oneIn(pm) + (rr > 0 ? " R" + Math.round(rr) : "");
            table.add(String.format(Locale.ROOT, "%s %d,%d\n F%.0f G%d %s\n", Survey.chain(c), (int) at[0], (int) at[1], at[2], gy, lev));
            if (peg == null) continue;
            BlockPos pp = new BlockPos((int) Math.floor(at[0]), gy, (int) Math.floor(at[1]));
            if (!world.isAirBlock(pp) && !world.getBlockState(pp).getBlock().isReplaceable(world, pp)) continue;
            if (!p.capabilities.isCreativeMode && !takeStick(p)) { noStick++; continue; }
            world.setBlockState(pp, peg.getDefaultState().withProperty(com.dogpound.railmap.block.BlockStationDevice.FACING, p.getHorizontalFacing().getOpposite()));
            if (world.getTileEntity(pp) instanceof TileSurveyPeg tp) tp.write(name, "CH " + Survey.chain(c), lev, info);
            placed++;
        }
        // the blueprint
        double[] ew = Survey.earthworks(s);
        List<String> pages = new ArrayList<>();
        pages.add(String.format(Locale.ROOT, "CONSTRUCTION\nBLUEPRINT\n%s\n\nLength %.0f m\nWaypoints %d\nCut %.0f m3\nFill %.0f m3\nDeepest cut %.1f m\nHighest bank %.1f m\n%s", name, len, pts.size(), ew[0], ew[1], ew[2], ew[3],
                ew[2] > 15 ? "\nTUNNEL ADVISED" : ew[3] > 10 ? "\nVIADUCT ADVISED" : ""));
        pages.add(String.format(Locale.ROOT, "MATERIALS\n(single track)\n\nRail %.0f m\nSleepers %d\nFastenings %d\nBallast %.0f m3\nFormation %.1f m wide\n\nPegs set: %d%s", len * 2, (int) Math.ceil(len / 0.65), (int) Math.ceil(len / 0.65) * 4, len * 2.6, Survey.WIDTH, placed, noStick > 0 ? "\n(" + noStick + " short of sticks)" : ""));
        StringBuilder sec = new StringBuilder("SECTIONS\n");
        for (String l : Survey.sections(s)) { if (sec.length() + l.length() > 230) { pages.add(sec.toString()); sec = new StringBuilder(); } sec.append(l).append('\n'); }
        pages.add(sec.toString());
        StringBuilder cc = new StringBuilder("CURVE CHECK\n");
        Object[] train = Survey.designTrain(world, p.getPosition());
        if (train != null) cc.append(train[0]).append(String.format(Locale.ROOT, "\nlongest car %.1f m\n", (double) train[1]));
        for (String l : Survey.curveCheck(s, train)) { if (cc.length() + l.length() > 230) { pages.add(cc.toString()); cc = new StringBuilder(); } cc.append(l).append('\n'); }
        pages.add(cc.toString());
        Gis.Report gr = Gis.validate(world, s, 0, 0);                   // next-48 #26: the GIS check of this alignment
        Gis.get(world).verdicts.put(name, gr.verdict());
        Gis.get(world).markDirty();
        StringBuilder gp = new StringBuilder("GIS CHECK\n" + gr.verdict() + "\n");
        for (String l : gr.lines) { if (l.startsWith("PASS")) continue; if (gp.length() + l.length() > 230) { pages.add(gp.toString()); gp = new StringBuilder(); } gp.append(l).append('\n'); }
        pages.add(gp.toString());
        StringBuilder tb = new StringBuilder("SETTING OUT\n");
        for (String l : table) { if (tb.length() + l.length() > 230) { pages.add(tb.toString()); tb = new StringBuilder(); } tb.append(l); }
        if (tb.length() > 0) pages.add(tb.toString());
        while (pages.size() > 50) pages.remove(pages.size() - 1);
        ItemStack book = null;
        for (ItemStack b : p.inventory.mainInventory) if (!b.isEmpty() && (b.getItem() == Items.BOOK || b.getItem() == Items.WRITABLE_BOOK)) { book = b; break; }
        if (book != null || p.capabilities.isCreativeMode) {
            if (book != null) book.shrink(1);
            ItemStack out = new ItemStack(Items.WRITTEN_BOOK);
            NBTTagCompound bt = new NBTTagCompound();
            bt.setString("title", ("Blueprint " + name).substring(0, Math.min(32, ("Blueprint " + name).length())));
            bt.setString("author", p.getName());
            NBTTagList pl = new NBTTagList();
            for (String pg : pages) pl.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(pg))));
            bt.setTag("pages", pl);
            out.setTagCompound(bt);
            if (!p.inventory.addItemStackToInventory(out)) p.dropItem(out, false);
        }
        String file = Survey.export(world, name, s);
        p.sendMessage(new TextComponentString("§a" + name + " set out: " + placed + " pegs" + (noStick > 0 ? " §c(" + noStick + " skipped - no sticks)" : "") + (book == null && !p.capabilities.isCreativeMode ? " §7(carry a Book for the blueprint)" : " §a+ blueprint book")
                + (file == null ? "" : " §8· exported to " + file)));
    }

    private static boolean takeStick(EntityPlayer p) {
        for (ItemStack s : p.inventory.mainInventory) if (!s.isEmpty() && s.getItem() == Items.STICK) { s.shrink(1); return true; }
        return false;
    }
}
