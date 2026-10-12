package com.dogpound.railmap.grid;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;

/**
 * Permit to Work (her ChatGPT list #44): printed blank by a Permit Board. Right-click the HV equipment you need to
 * work on to make it out for that equipment (it shows the four-step checklist, live, every time you do); hand it in at
 * the board to have it ISSUED once every step is done; while it is in your inventory you may work inside the isolated
 * section. Hand it back at the board (CANCEL) when the job is finished.
 */
public class ItemPermit extends Item {
    public ItemPermit() {
        setRegistryName(RailMap.MODID, "work_permit");
        setTranslationKey(RailMap.MODID + ".work_permit");
        setCreativeTab(RailMapTab.INSTANCE);
        setMaxStackSize(1);
    }

    public static boolean issued(ItemStack s) { return s.getItem() instanceof ItemPermit && s.hasTagCompound() && s.getTagCompound().getInteger("id") > 0; }
    public static boolean bound(ItemStack s) { return s.getItem() instanceof ItemPermit && s.hasTagCompound() && s.getTagCompound().hasKey("eq"); }
    public static BlockPos equip(ItemStack s) { return bound(s) ? BlockPos.fromLong(s.getTagCompound().getLong("eq")) : null; }
    public static long bindAt(ItemStack s) { return bound(s) ? s.getTagCompound().getLong("bind") : -1; }

    /** the live registry entry an issued permit stands for (null = blank / cancelled / not ours) */
    public static Permits.Permit permit(Permits ps, ItemStack s) {
        if (!issued(s)) return null;
        Permits.Permit p = ps.byId(s.getTagCompound().getInteger("id"));
        return p != null && p.live ? p : null;
    }

    static void snapshot(ItemStack s, List<String> lines) {
        NBTTagList l = new NBTTagList();
        for (String x : lines) l.appendTag(new NBTTagString(x));
        s.getTagCompound().setTag("lines", l);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer p, World w, BlockPos pos, EnumFacing side, float hx, float hy, float hz, EnumHand hand) {
        if (!(w.getBlockState(pos).getBlock() instanceof BlockGrid)) return EnumActionResult.PASS;
        if (w.isRemote) return EnumActionResult.SUCCESS;
        ItemStack s = p.getHeldItem(hand);
        if (issued(s)) {
            Permits.Permit pm = permit(Permits.get(w), s);
            p.sendMessage(new TextComponentString(pm == null ? "§7This permit was cancelled - get a new form printed at the Permit Board"
                    : pm.area.contains(pos.toLong()) ? "§aPERMIT #" + pm.id + " covers this - safe to work on it" : "§cPERMIT #" + pm.id + " does NOT cover this - it is outside the isolated section"));
            return EnumActionResult.SUCCESS;
        }
        GridData.Node n = GridData.get(w).node(pos);
        if (n == null) return EnumActionResult.SUCCESS;
        if (!s.hasTagCompound()) s.setTagCompound(new NBTTagCompound());
        if (!s.getTagCompound().hasKey("eq") || s.getTagCompound().getLong("eq") != pos.toLong()) {
            s.getTagCompound().setLong("eq", pos.toLong());
            s.getTagCompound().setLong("bind", w.getTotalWorldTime());
            s.getTagCompound().setString("what", n.kind.label + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
            s.getTagCompound().setString("by", p.getName());
        }
        Permits.Check c = Permits.check(w, pos, s.getTagCompound().getLong("bind"));
        snapshot(s, c.lines);
        p.sendMessage(new TextComponentString("§b§lPERMIT TO WORK§r§7 - " + s.getTagCompound().getString("what") + (Permits.designated(w, pos) ? " §c(HV - permit required)" : " §7(LV - permit optional)")));
        for (String l : c.lines) p.sendMessage(new TextComponentString("  " + l));
        p.sendMessage(new TextComponentString(c.ok() ? "§aAll four steps done: hang the padlock keys in the Permit Board's key safe and press ISSUE with this form in your hand"
                : "§7Do the missing steps, then right-click it again to re-check"));
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        if (!s.hasTagCompound() || !s.getTagCompound().hasKey("eq")) {
            tip.add("§7Blank form: right-click the HV equipment to work on");
            tip.add("§8Isolate · earth · prove dead · lock off · ISSUE at the Permit Board");
            return;
        }
        NBTTagCompound t = s.getTagCompound();
        int id = t.getInteger("id");
        tip.add(id > 0 ? "§a§lISSUED §r§aPERMIT #" + id + "§7 to " + t.getString("by") : "§eNot yet issued");
        tip.add("§7Equipment: §f" + t.getString("what"));
        NBTTagList l = t.getTagList("lines", 8);
        for (int i = 0; i < l.tagCount(); i++) tip.add("  " + l.getStringTagAt(i));
        if (id > 0) tip.add("§8Hand it back at the board (CANCEL) when the work is done");
    }

    @Override public boolean hasEffect(ItemStack s) { return issued(s); }
}
