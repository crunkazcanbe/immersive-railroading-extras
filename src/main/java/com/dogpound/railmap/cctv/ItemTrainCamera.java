package com.dogpound.railmap.cctv;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;

/**
 * Forward-facing train camera: stand by an Immersive Railroading locomotive and use it to bolt the camera on (it then
 * streams the view ahead - the end the reverser points at - over its LTE modem to every CCTV network). Sneak-use by a
 * loco to take its camera off again.
 */
public class ItemTrainCamera extends Item {
    public ItemTrainCamera() {
        setRegistryName(RailMap.MODID, "cctv_train_camera");
        setTranslationKey(RailMap.MODID + ".cctv_train_camera");
        setMaxStackSize(16);
        setCreativeTab(RailMapTab.INSTANCE);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
        ItemStack s = p.getHeldItem(hand);
        if (w.isRemote) return new ActionResult<>(EnumActionResult.SUCCESS, s);
        EntityRollingStock rs = TrainCams.nearLoco(w, p);
        if (rs == null) { p.sendStatusMessage(new TextComponentString("§7Stand next to a locomotive (within 6 blocks) to fit the camera"), true); return new ActionResult<>(EnumActionResult.FAIL, s); }
        CctvData d = CctvData.get(w);
        String id = rs.getUUID().toString();
        CctvData.Cam c = d.byTrain(id);
        if (p.isSneaking()) {
            if (c == null) { p.sendStatusMessage(new TextComponentString("§7That loco has no camera"), true); return new ActionResult<>(EnumActionResult.FAIL, s); }
            d.cams.remove(c.no);
            d.changed();
            if (!p.addItemStackToInventory(new ItemStack(this))) p.dropItem(new ItemStack(this), false);
            p.sendStatusMessage(new TextComponentString("§eCamera CAM " + c.no + " taken off " + TrainCams.name(rs)), true);
            return new ActionResult<>(EnumActionResult.SUCCESS, s);
        }
        if (c != null) { p.sendStatusMessage(new TextComponentString("§7" + TrainCams.name(rs) + " already has CAM " + c.no), true); return new ActionResult<>(EnumActionResult.FAIL, s); }
        c = new CctvData.Cam();
        c.no = d.allocCam();
        c.type = CamType.TRAIN.ordinal();
        c.train = id;
        c.dim = w.provider.getDimension();
        c.name = ("TRAIN " + TrainCams.name(rs)).toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9 ./:+-]", "");
        if (c.name.length() > 24) c.name = c.name.substring(0, 24);
        c.fov = CamType.TRAIN.fov;
        c.status = CctvData.ONLINE;
        d.cams.put(c.no, c);
        d.changed();
        if (!p.capabilities.isCreativeMode) s.shrink(1);
        p.sendStatusMessage(new TextComponentString("§aForward-facing camera fitted: CAM " + c.no + " on " + TrainCams.name(rs)), true);
        return new ActionResult<>(EnumActionResult.SUCCESS, s);
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        tip.add("§7Use beside an IR locomotive to fit it; sneak-use to take it off");
        tip.add("§8Streams the view ahead (reverser end) to every CCTV network over LTE; powered by the loco");
    }
}
