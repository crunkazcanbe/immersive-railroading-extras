package com.dogpound.railmap.weather;

import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Locale;

/**
 * Rail Temperature Monitor (her ChatGPT list #65): a probe magnet-clamped to the rail web with its solar-charged
 * transmitter box. Shows rail and air temperature against this track's stress-free temperature and the thresholds -
 * WATCH, 90 km/h, 50 km/h, BUCKLE risk - and the track's condition. Work done here is real:
 * <ul>
 *   <li>PATROL: a hot-weather patrol has walked the section: the heat restriction relaxes one step for a game hour;</li>
 *   <li>BALLAST: right-click with 16 gravel - the shoulders are made up and tamped: condition +25 %;</li>
 *   <li>REPAIR: a buckle cut out and re-stressed with 6 iron ingots: the line opens again.</li>
 * </ul>
 */
public class TileRailTempMonitor extends SafetyTile {
    @Override public String wiredName() { return "Rail temperature monitor " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { WeatherData.Cell c = Weather.cell(world, pos); return String.format(Locale.ROOT, "rail %.0f °C (SFT %.0f °C), track condition %.0f%%%s", c.rail, c.sft, c.condition * 100, c.buckled != 0 ? " · BUCKLED" : ""); }
    @Override public int wiredLamp() { WeatherData.Cell c = Weather.cell(world, pos); return !powered() || c.buckled != 0 ? 3 : c.rail >= Weather.heatLimit(c, world.getTotalWorldTime(), 1) ? 2 : 1; }

    @Override
    protected void serverTick() {
        if (age % 20 != 17) return;
        WeatherData.Cell c = Weather.cell(world, pos);
        long now = world.getTotalWorldTime();
        put("railg", (int) Math.max(0, Math.min(80, c.rail)));
        text("rail", String.format(Locale.ROOT, "RAIL %+.1f C", c.rail));
        text("air", String.format(Locale.ROOT, "AIR %+.1f C", c.air));
        text("sft", String.format(Locale.ROOT, "SFT %.0f C", c.sft));
        text("cond", String.format(Locale.ROOT, "COND %.0f%%", c.condition * 100));
        text("lim", String.format(Locale.ROOT, "%.0f/%.0f/%.0f C", Weather.heatLimit(c, now, 1), Weather.heatLimit(c, now, 2), Weather.heatLimit(c, now, 3)));
        put("lwatch", c.rail >= Weather.heatLimit(c, now, 0));
        put("lesr", c.rail >= Weather.heatLimit(c, now, 1));
        put("lbuckle", c.buckled != 0 || c.rail >= Weather.heatLimit(c, now, 3));
        put("lpatrol", now < c.patrolUntil);
        put("pwr", powered());
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        WeatherData d = WeatherData.get(world);
        WeatherData.Cell c = Weather.cell(world, pos);
        long now = world.getTotalWorldTime();
        switch (id) {
            case "test": return lampTest();
            case "patrol":
                if (who == null) return "§cA patrol is walked, not pressed from the office";
                c.patrolUntil = now + 1000;
                d.log(world, "hot-weather patrol by " + who.getName() + " in " + Weather.name(c) + " - restriction eased for 1 h");
                com.dogpound.railmap.alarm.Recorder.event(world, "HOT WEATHER PATROL " + Weather.name(c) + " by " + who.getName());
                return "§aPatrol booked: heat restriction eased one step for a game hour";
            case "ballast": {
                if (who == null) return "§cBallast work is done on site";
                if (!take(who, new ItemStack(Blocks.GRAVEL), 16)) return "§cBring 16 gravel to make up the ballast shoulders";
                c.condition = Math.min(1, c.condition + 0.25);
                d.log(world, "ballast shoulders made up in " + Weather.name(c) + " by " + who.getName() + " - condition " + Math.round(c.condition * 100) + "%");
                return "§aBallast made up and tamped: condition " + Math.round(c.condition * 100) + "%";
            }
            case "repair": {
                if (who == null) return "§cA buckle is repaired on site";
                if (c.buckled == 0) return "§7No buckle here";
                if (!take(who, new ItemStack(Items.IRON_INGOT), 6)) return "§cBring 6 iron ingots (closure rails) to cut out the buckle";
                c.buckled = 0;
                c.condition = Math.max(c.condition, 0.8);
                d.log(world, "buckle cut out and re-stressed in " + Weather.name(c) + " by " + who.getName() + " - line open");
                com.dogpound.railmap.alarm.Recorder.event(world, "BUCKLE REPAIRED " + Weather.name(c) + " by " + who.getName());
                return "§aBuckle cut out and re-stressed - the line is open";
            }
            default: return "§7" + gearState();
        }
    }

    /** take n of an item from the player's inventory (creative: free) */
    static boolean take(EntityPlayer p, ItemStack want, int n) {
        if (p.capabilities.isCreativeMode) return true;
        int have = 0;
        for (ItemStack s : p.inventory.mainInventory) if (s.getItem() == want.getItem()) have += s.getCount();
        if (have < n) return false;
        for (ItemStack s : p.inventory.mainInventory) {
            if (n <= 0) break;
            if (s.getItem() != want.getItem()) continue;
            int t = Math.min(n, s.getCount());
            s.shrink(t);
            n -= t;
        }
        return true;
    }

    @Override protected void save(NBTTagCompound t) {}
    @Override protected void load(NBTTagCompound t) {}
}
