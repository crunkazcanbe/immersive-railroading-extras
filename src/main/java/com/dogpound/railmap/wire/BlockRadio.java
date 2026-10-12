package com.dogpound.railmap.wire;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/** Radio Transmitter: wire into it and every colour goes on the air. Radio Receiver: puts them back on its wires. */
public class BlockRadio extends Block {
    public final boolean receiver;
    private static final AxisAlignedBB BOX = new AxisAlignedBB(3 / 16.0, 0, 3 / 16.0, 13 / 16.0, 10 / 16.0, 13 / 16.0);

    public BlockRadio(boolean receiver) {
        super(Material.IRON);
        this.receiver = receiver;
        String id = receiver ? "radio_receiver" : "radio_transmitter";
        setRegistryName(RailMap.MODID, id);
        setTranslationKey(RailMap.MODID + "." + id);
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(1f); setSoundType(SoundType.METAL);
    }

    @Override public boolean hasTileEntity(IBlockState s) { return true; }
    @Override public TileEntity createTileEntity(World w, IBlockState s) { return new TileRadio(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState s) { return EnumBlockRenderType.INVISIBLE; }
    @Override @SuppressWarnings("deprecation") public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override @SuppressWarnings("deprecation") public boolean isFullCube(IBlockState s) { return false; }
    @Override @SuppressWarnings("deprecation") public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return BOX; }

    @Override
    public boolean onBlockActivated(World w, BlockPos p, IBlockState s, EntityPlayer pl, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
        if (!(w.getTileEntity(p) instanceof TileRadio t)) return false;
        net.minecraft.item.ItemStack h = pl.getHeldItem(hand);
        if (t.burnt && h.getItem() instanceof com.dogpound.railmap.grid.ItemScrewdriver) { if (!w.isRemote) pl.sendStatusMessage(new TextComponentString(t.repair()), true); return true; }
        if (ItemWire.isPower(h)) { if (!w.isRemote) pl.sendStatusMessage(new TextComponentString("§7Lay the 12 V power wire on the floor beside it - it plugs itself in. Supply: " + WireNet.power(w, p).what), true); return true; }
        if (!h.isEmpty()) return false;
        if (w.isRemote) return true;
        t.setChannel(((t.channel - 1 + (pl.isSneaking() ? -1 : 1)) % 32 + 32) % 32 + 1);
        StringBuilder on = new StringBuilder();
        int n = 0;
        if (!receiver) for (int c = 0; c < 16; c++) if (WireNet.source(w, p, c) != null) { n++; on.append(' ').append(WireNet.NAMES[c]); }
        String pwr = t.pw == 2 ? " §c· BURNT OUT (screwdriver)" : t.pw == 0 ? " §c· NO 12 V POWER" : " §a· 12 V ok";
        pl.sendStatusMessage(new TextComponentString("§b📡 " + (receiver ? "Receiver" : "Transmitter") + " on channel " + t.channel + pwr + "§b"
                + (receiver ? " · transmitters on it: " + WireNet.transmitters(w, t.channel).size() : " · sending " + n + " circuit(s):" + on)), true);
        return true;
    }

    @Override
    public void randomDisplayTick(IBlockState s, World w, BlockPos p, java.util.Random rnd) {
        if (!(w.getTileEntity(p) instanceof TileRadio t) || !t.burnt || rnd.nextInt(3) != 0) return;
        w.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_NORMAL, p.getX() + 0.3 + rnd.nextDouble() * 0.4, p.getY() + 0.7, p.getZ() + 0.3 + rnd.nextDouble() * 0.4, 0, 0.02, 0);
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) {
        t.add(receiver ? "§7Picks up every circuit from transmitters on its channel and puts them on its wires" : "§7Wire into it: every colour coming in goes on the air on its channel");
        t.add("§7Right-click: next channel · sneak: previous (1-32)");
        t.add("§cNeeds 12 V DC: lay 12 V DC Power Wire beside it from a 12 V supply");
    }
}
