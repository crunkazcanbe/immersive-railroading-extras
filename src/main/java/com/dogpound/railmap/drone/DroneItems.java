package com.dogpound.railmap.drone;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;

/** The drone's items: the airframe (goes in a dock's hangar), its 500 Wh flight battery, and the handheld controller. */
public final class DroneItems {
    private DroneItems() {}

    private static NBTTagCompound tag(ItemStack s) {
        if (!s.hasTagCompound()) s.setTagCompound(new NBTTagCompound());
        return s.getTagCompound();
    }

    /** the inspection quadcopter, folded: serial number and flying hours travel with it */
    public static final class Airframe extends Item {
        public Airframe() {
            setRegistryName(RailMap.MODID, "drone");
            setTranslationKey(RailMap.MODID + ".drone");
            setMaxStackSize(1);
            setCreativeTab(RailMapTab.INSTANCE);
        }

        public static String serial(ItemStack s) { return s.hasTagCompound() && !s.getTagCompound().getString("serial").isEmpty() ? s.getTagCompound().getString("serial") : ""; }
        public static void serial(ItemStack s, String n) { tag(s).setString("serial", n); }
        public static double hours(ItemStack s) { return s.hasTagCompound() ? s.getTagCompound().getDouble("hours") : 0; }
        public static void addHours(ItemStack s, double h) { tag(s).setDouble("hours", hours(s) + h); }

        @Override
        public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
            tip.add("§7Quadcopter: 4 x 21\" props, 30x zoom + thermal gimbal camera, LTE video, GNSS, obstacle radar");
            tip.add("§7Put it in a Drone Dock's hangar hatch; the dock launches it on its mission");
            String n = serial(s);
            tip.add("§8" + (n.isEmpty() ? "New - the dock gives it a serial" : "Serial " + n) + String.format(Locale.ROOT, " · %.1f flying hours", hours(s)));
        }
    }

    /** a 500 Wh smart flight battery: charge % and cycle count; capacity fades with cycles (fast charging ages it more) */
    public static final class Battery extends Item {
        public Battery() {
            setRegistryName(RailMap.MODID, "drone_battery");
            setTranslationKey(RailMap.MODID + ".drone_battery");
            setMaxStackSize(1);
            setCreativeTab(RailMapTab.INSTANCE);
        }

        public static float pct(ItemStack s) { return !s.hasTagCompound() || !s.getTagCompound().hasKey("pct") ? 100f : s.getTagCompound().getFloat("pct"); }
        public static void set(ItemStack s, float pct) { if (!s.isEmpty()) tag(s).setFloat("pct", Math.max(0, Math.min(100, pct))); }
        public static double cycles(ItemStack s) { return s.hasTagCompound() ? s.getTagCompound().getDouble("cyc") : 0; }
        public static void age(ItemStack s, double cyc) { tag(s).setDouble("cyc", cycles(s) + cyc); }
        /** usable capacity left, 1 = new; 80 % at about 700 cycles */
        public static double fade(ItemStack s) { return s.isEmpty() ? 1 : Math.max(0.6, 1 - cycles(s) / 3500.0); }
        public static boolean is(ItemStack s) { return !s.isEmpty() && s.getItem() instanceof Battery; }

        @Override public boolean showDurabilityBar(ItemStack s) { return pct(s) < 99.5f; }
        @Override public double getDurabilityForDisplay(ItemStack s) { return 1 - pct(s) / 100.0; }
        @Override public int getRGBDurabilityForDisplay(ItemStack s) { float p = pct(s); return p < 25 ? 0xFF4040 : p < 50 ? 0xFFC040 : 0x40E060; }

        @Override
        public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
            tip.add(String.format(Locale.ROOT, "§7Charge %.0f%% · %.0f cycles · capacity %.0f%% of 500 Wh", pct(s), cycles(s), fade(s) * 100));
            tip.add("§8Charges in a Drone Dock's battery bay (12 V + 120 / 240 V supply)");
        }
    }

    /** the handheld controller: use it on a dock to pair, in the air to launch / take control (fly where you look; sneak to hover), sneak-use to send it home */
    public static final class Controller extends Item {
        public Controller() {
            setRegistryName(RailMap.MODID, "drone_controller");
            setTranslationKey(RailMap.MODID + ".drone_controller");
            setMaxStackSize(1);
            setCreativeTab(RailMapTab.INSTANCE);
        }

        public static boolean holding(EntityPlayer p) { return p.getHeldItemMainhand().getItem() instanceof Controller || p.getHeldItemOffhand().getItem() instanceof Controller; }

        static BlockPos dock(ItemStack s) { return s.hasTagCompound() && s.getTagCompound().hasKey("dock") ? BlockPos.fromLong(s.getTagCompound().getLong("dock")) : null; }

        @Override
        public EnumActionResult onItemUse(EntityPlayer p, World w, BlockPos pos, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
            if (!(w.getTileEntity(pos) instanceof TileDroneDock k)) return EnumActionResult.PASS;
            if (w.isRemote) return EnumActionResult.SUCCESS;
            tag(p.getHeldItem(hand)).setLong("dock", pos.toLong());
            p.sendStatusMessage(new TextComponentString("§aController paired with dock " + k.serial() + " - use it in the air to launch / fly, sneak-use to send it home"), true);
            return EnumActionResult.SUCCESS;
        }

        @Override
        public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
            ItemStack s = p.getHeldItem(hand);
            if (w.isRemote) return new ActionResult<>(EnumActionResult.SUCCESS, s);
            BlockPos at = dock(s);
            TileDroneDock k = at != null && w.isBlockLoaded(at) && w.getTileEntity(at) instanceof TileDroneDock t ? t : null;
            String r;
            if (k == null) r = "§7Pair the controller first: use it on a Drone Dock";
            else {
                EntityDrone d = k.drone();
                if (p.isSneaking()) r = d == null ? "§7" + k.serial() + " isn't flying" : k.remote("rth", p);
                else if (d == null) r = k.remote("launch", p);
                else if (d.getDistanceSq(p) > Math.pow(DroneConfig.controllerRange, 2)) r = "§cOut of radio range (" + DroneConfig.controllerRange + " blocks)";
                else if (d.mode() == EntityDrone.MANUAL && p.getUniqueID().equals(d.controller)) { d.resumeMission(); r = "§eControl handed back - " + EntityDrone.MODES[d.mode()].toLowerCase(Locale.ROOT); }
                else if (d.mode() == EntityDrone.LAND || d.mode() == EntityDrone.LANDED_OUT) r = "§7" + Drones.statusLine(d);
                else { d.takeControl(p); r = "§aYou have control of " + d.droneName() + ": it flies where you look, sneak to hover, use again to hand back"; }
            }
            p.sendStatusMessage(new TextComponentString(r), true);
            return new ActionResult<>(EnumActionResult.SUCCESS, s);
        }

        @Override
        public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
            BlockPos d = dock(s);
            tip.add("§7Use on a Drone Dock to pair · use in the air: launch / take or hand back control");
            tip.add("§7Flying by hand it goes where you look (sneak = hover) · sneak-use = return home");
            tip.add("§8" + (d == null ? "Not paired" : "Paired with the dock at " + d.getX() + "," + d.getY() + "," + d.getZ()) + " · range " + DroneConfig.controllerRange + " blocks");
        }
    }
}
