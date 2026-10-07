package com.dogpound.railmap.grid;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;

/**
 * The Power Cable item (requested feature).
 * Each stack carries a conductor spec (material × mm² × cores × insulation) in its NBT; every cable you lay from it is
 * that conductor (its resistance, current rating and voltage class drive the electrical solve). Sneak + right-click
 * the air to choose a different conductor.
 */
public class ItemPowerCable extends ItemBlock {
    public ItemPowerCable(Block b) {
        super(b);
        setHasSubtypes(false);
    }

    public static Elec.Spec spec(ItemStack s) {
        NBTTagCompound t = s.getTagCompound();
        return t == null || !t.hasKey("spec") ? Elec.Spec.LEGACY : Elec.Spec.parse(t.getString("spec"));
    }

    public static ItemStack with(ItemStack s, Elec.Spec sp) {
        if (!s.hasTagCompound()) s.setTagCompound(new NBTTagCompound());
        s.getTagCompound().setString("spec", sp.key());
        return s;
    }

    /** the ready-made cables in the creative tab */
    public static final Elec.Spec[] PRESETS = {
            new Elec.Spec(Elec.Material.COPPER, 2.5, Elec.Cores.THREE_N_PE, Elec.Insulation.LV),
            new Elec.Spec(Elec.Material.COPPER, 16, Elec.Cores.THREE_N_PE, Elec.Insulation.LV),
            new Elec.Spec(Elec.Material.COPPER, 50, Elec.Cores.DC2, Elec.Insulation.LV),
            new Elec.Spec(Elec.Material.COPPER, 25, Elec.Cores.MOTOR, Elec.Insulation.LV),
            Elec.Spec.LEGACY,
            new Elec.Spec(Elec.Material.ALUMINIUM, 300, Elec.Cores.THREE_N_PE, Elec.Insulation.MV),
            new Elec.Spec(Elec.Material.COPPER, 150, Elec.Cores.SINGLE, Elec.Insulation.HV),
            new Elec.Spec(Elec.Material.SUPERCONDUCTOR, 300, Elec.Cores.THREE_N_PE, Elec.Insulation.HV),
            new Elec.Spec(Elec.Material.COPPER, 300, Elec.Cores.BUSBAR, Elec.Insulation.LV),
    };

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;
        for (Elec.Spec sp : PRESETS) items.add(with(new ItemStack(this), sp));
    }

    @Override
    public String getItemStackDisplayName(ItemStack s) {
        Elec.Spec sp = spec(s);
        return String.format(Locale.ROOT, "%s Cable %s mm² (%s)", sp.mat == Elec.Material.SUPERCONDUCTOR ? "Superconducting" : sp.mat.label,
                Elec.fmt(sp.mm2), sp.cores == Elec.Cores.BUSBAR ? "busbar" : sp.ins.name());
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        Elec.Spec sp = spec(s);
        tip.add("§7" + sp.label());
        tip.add(String.format(Locale.ROOT, "§7Rated %.0f A · %.3f mΩ/m · insulation up to %s · max %d °C",
                sp.ampacity(), sp.ohmsPerM(20) * 1000, Elec.si(sp.ins.maxVolts, "V"), sp.ins.maxTemp));
        tip.add("§8Sneak + right-click the air: choose material, size, cores, insulation");
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
        ItemStack s = p.getHeldItem(hand);
        if (p.isSneaking()) {
            if (w.isRemote) com.dogpound.railmap.RailMap.proxy.openCableSpec(hand, spec(s));
            return new ActionResult<>(EnumActionResult.SUCCESS, s);
        }
        return super.onItemRightClick(w, p, hand);
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hx, float hy, float hz, IBlockState newState) {
        Elec.Spec sp = spec(stack);
        boolean ok = super.placeBlockAt(stack, player, world, pos, side, hx, hy, hz, BlockGrid.cableState(newState, sp));
        if (ok && !world.isRemote) {
            GridData d = GridData.get(world);
            GridData.Node n = d.node(pos);
            if (n == null) { d.add(pos, GridKind.CABLE); n = d.node(pos); }
            n.spec = sp == Elec.Spec.LEGACY ? "" : sp.key();
            n.temp = 20;
            d.touch();
        }
        return ok;
    }
}
