package com.dogpound.railmap.road;

import com.dogpound.railmap.ohle.Reg;
import com.dogpound.railmap.sound.PackSounds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

/**
 * Pedestrian crossing unit (batch 5 #3): the push-button box with its WAIT lamp and the red / green man above, on a
 * pole at the kerb. PUSH registers a pedestrian demand at the nearest Traffic Signal Controller; WAIT stays lit until
 * the pedestrian stage comes. Green man = walk (every vehicle group is at red), flashing green man = clearing, red man
 * otherwise. The audible unit beeps fast while it is safe to cross and gives the slow locator tick the rest of the time.
 */
public class TilePedUnit extends RoadTile {
    static final Reg<TilePedUnit> REG = new Reg<>();
    private long ctlPos = Long.MIN_VALUE;
    private int presses;

    @Override public void onLoad() { REG.add(this); }
    @Override public void invalidate() { super.invalidate(); REG.remove(this); }
    @Override public void onChunkUnload() { REG.remove(this); }

    public TileTrafficController controller() {
        if (ctlPos != Long.MIN_VALUE && world.getTileEntity(net.minecraft.util.math.BlockPos.fromLong(ctlPos)) instanceof TileTrafficController c && !c.isInvalid()) return c;
        return null;
    }

    @Override
    protected void serverTick() {
        if (age % 100 == 1 || ctlPos == Long.MIN_VALUE && age % 20 == 1) {
            TileTrafficController c = TileTrafficController.nearest(world, pos);
            ctlPos = c == null ? Long.MIN_VALUE : c.getPos().toLong();
        }
        if (age % 2 != 0) return;
        TileTrafficController c = controller();
        int a = c == null ? TileTrafficController.Phasing.DARK : c.aspect(TileTrafficController.P);
        boolean flash = (world.getTotalWorldTime() / 8) % 2 == 0;
        boolean walk = a == TileTrafficController.Phasing.GREEN;
        put("gman", walk || a == TileTrafficController.Phasing.WALK_FLASH && flash);
        put("rman", a == TileTrafficController.Phasing.RED);
        put("wait", c != null && c.live() && c.demand(TileTrafficController.P));
        if (walk && age % 8 == 0) world.playSound(null, pos, PackSounds.ev("ui.beep_ok"), SoundCategory.BLOCKS, 0.35f, 1.9f);
        else if (a == TileTrafficController.Phasing.RED && age % 30 == 0) world.playSound(null, pos, PackSounds.ev("ui.click"), SoundCategory.BLOCKS, 0.15f, 0.7f);
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (!id.equals("ped")) return null;
        TileTrafficController c = controller();
        if (c == null) return "§7Nothing happens - no Traffic Signal Controller within " + TileTrafficController.RANGE + " blocks";
        if (!c.live()) return "§7The unit is dark - its controller has no supply";
        presses++;
        markDirty();
        boolean was = c.demand(TileTrafficController.P);
        c.pedCall();
        world.playSound(null, pos, PackSounds.ev("ui.beep_ok"), SoundCategory.BLOCKS, 0.5f, 1.4f);
        return was ? "§eWAIT - already called" : "§eWAIT - pedestrian demand registered";
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("presses", presses); }
    @Override protected void load(NBTTagCompound t) { presses = t.getInteger("presses"); }
}
