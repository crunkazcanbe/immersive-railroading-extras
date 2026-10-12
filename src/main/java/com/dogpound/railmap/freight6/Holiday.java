package com.dogpound.railmap.freight6;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.steam.Stock;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.time.LocalDate;
import java.util.*;

/**
 * Holiday trains (batch 6 #30), server side: fitting / removing the decoration kits, sending each player the decorated
 * cars near them, and the seasonal events - a Christmas train standing at a station with a Christmas Tree leaves a
 * present under it (once a game day), a ghost train standing by a Halloween Display at night fills its treat bowl.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class Holiday {
    private Holiday() {}

    /** fit (or with sneak, remove) a kit on the car `target` (null = the car the player is in / beside) */
    public static String apply(World w, EntityPlayer p, ItemStack kit, EntityRollingStock target) {
        EntityRollingStock s = target != null ? target : ItemHolidayKit.aimed(w, p);
        if (s == null) return "§7Stand in or beside an Immersive Railroading car";
        HolidayData d = HolidayData.get(w);
        UUID u = s.getUUID();
        int want = kit.getMetadata() == 0 ? HolidayData.XMAS : HolidayData.GHOST;
        if (p.isSneaking()) {
            Integer had = d.kits.remove(u);
            if (had == null) return "§7" + Stock.name(s) + " has no decorations";
            d.markDirty();
            if (!p.capabilities.isCreativeMode) p.inventory.addItemStackToInventory(new ItemStack(kit.getItem(), 1, had == HolidayData.XMAS ? 0 : 1));
            return "§e" + Stock.name(s) + ": decorations taken down";
        }
        if (d.kit(u) == want) return "§7" + Stock.name(s) + " is already dressed";
        Integer had = d.kits.put(u, want);
        d.markDirty();
        if (!p.capabilities.isCreativeMode) {
            kit.shrink(1);
            if (had != null) p.inventory.addItemStackToInventory(new ItemStack(kit.getItem(), 1, had == HolidayData.XMAS ? 0 : 1));
        }
        Recorder.event(w, Stock.name(s) + " dressed as a " + (want == HolidayData.XMAS ? "Christmas train" : "ghost train") + " by " + p.getName());
        w.playSound(null, p.getPosition(), com.dogpound.railmap.sound.PackSounds.ev(want == HolidayData.XMAS ? "station.chime_bell" : "alarm.gas_release"), net.minecraft.util.SoundCategory.PLAYERS, 0.6f, want == HolidayData.XMAS ? 1.2f : 0.5f);
        return "§a" + Stock.name(s) + (want == HolidayData.XMAS ? " is now a Christmas train" : " is now a ghost train");
    }

    /** a click with the kit right on a car (IR would otherwise take the click) */
    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getItemStack().getItem() instanceof ItemHolidayKit)) return;
        e.setCanceled(true);
        if (e.getWorld().isRemote) return;
        EntityRollingStock hit = null;
        try {
            for (EntityRollingStock s : cam72cam.mod.world.World.get(e.getWorld()).getEntities(EntityRollingStock.class)) if (s.internal == e.getTarget()) hit = s;
        } catch (RuntimeException | LinkageError ignored) { }
        e.getEntityPlayer().sendStatusMessage(new TextComponentString(apply(e.getWorld(), e.getEntityPlayer(), e.getItemStack(), hit)), true);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        int s = Season.now();
        if (s == Season.CHRISTMAS) e.player.sendMessage(new TextComponentString("§c🎄 §aSeason's greetings from the railway! §7Dress your trains with Christmas Lights kits; Christmas trains leave presents under station trees."));
        if (s == Season.HALLOWEEN) e.player.sendMessage(new TextComponentString("§6🎃 §5Ghost trains run at night... §7Dress a train with the Ghost Train kit; it fills the pumpkin displays' treat bowls."));
    }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || e.world.getTotalWorldTime() % 40 != 7) return;
        World w = e.world;
        HolidayData d = HolidayData.get(w);
        List<EntityRollingStock> dressed = new ArrayList<>();
        try {
            for (EntityRollingStock s : cam72cam.mod.world.World.get(w).getEntities(EntityRollingStock.class)) if (!s.isDead() && d.kit(s.getUUID()) != 0) dressed.add(s);
        } catch (RuntimeException | LinkageError ex) { return; }
        int season = Season.now();
        for (EntityPlayer p : w.playerEntities) {
            if (!(p instanceof EntityPlayerMP mp)) continue;
            PacketHoliday m = new PacketHoliday();
            m.season = season;
            for (EntityRollingStock s : dressed) {
                cam72cam.mod.math.Vec3d v = s.getPosition();
                if (p.getDistanceSq(v.x, v.y, v.z) < 192 * 192) m.kits.put(s.internal.getEntityId(), d.kit(s.getUUID()));
            }
            F6Net.NET.sendTo(m, mp);
        }
        if (season == Season.NONE || w.getTotalWorldTime() % 200 != 7) return;
        // ---- seasonal events: presents and treats from the decorated trains standing at stations
        for (EntityRollingStock s : dressed) {
            if (!Stock.standing(s)) continue;
            cam72cam.mod.math.Vec3d v = s.getPosition();
            BlockPos at = new BlockPos(v.x, v.y, v.z);
            int kit = d.kit(s.getUUID());
            if (kit == HolidayData.XMAS && season == Season.CHRISTMAS)
                for (TileXmasTree t : com.dogpound.railmap.stn.StnUtil.near(w, TileXmasTree.class, at, 20)) if (t.present(Stock.name(s))) { d.presents++; d.markDirty(); }
            if (kit == HolidayData.GHOST && season == Season.HALLOWEEN && !w.isDaytime())
                for (TileHalloweenDisplay t : com.dogpound.railmap.stn.StnUtil.near(w, TileHalloweenDisplay.class, at, 20)) if (t.treat(Stock.name(s))) { d.treats++; d.markDirty(); }
        }
    }

    public static String status(World w) {
        LocalDate t = LocalDate.now();
        int s = Season.now();
        return String.format(Locale.ROOT, "%s · today %s is %s · %d days to Christmas, %d to Halloween · %d dressed cars · %d presents, %d treats delivered",
                F6Config.holidays ? "holidays ON (" + F6Config.forceSeason + ")" : "holidays OFF", t, Season.NAMES[s], Season.daysTo(t, Season.CHRISTMAS), Season.daysTo(t, Season.HALLOWEEN),
                HolidayData.get(w).kits.size(), HolidayData.get(w).presents, HolidayData.get(w).treats);
    }
}
