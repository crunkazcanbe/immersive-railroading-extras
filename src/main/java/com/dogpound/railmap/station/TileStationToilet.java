package com.dogpound.railmap.station;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.depot.DepotClock;
import com.dogpound.railmap.depot.DepotTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

/**
 * Accessible Toilet (her ChatGPT list #81 + #85): a wide cubicle with a powered door, grab rails, the red emergency
 * cord and a cleaning log. It gets dirtier every game hour and every use (FLUSH / WASH); under 20 % it is closed for
 * cleaning (out of service) until staff press CLEANED (logged with the time). The emergency CORD raises an alarm.
 * Counts for accessibility (accessible toilet) and facilities; running cost for water and cleaning. 12 V.
 */
public class TileStationToilet extends DepotTile implements IStationFacility {
    private float clean = 100;
    private String lastClean = "";
    private int uses;
    private long hourSeen;

    @Override public String wiredName() { return "Accessible toilet"; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : Math.round(clean) + "% clean · last cleaned " + (lastClean.isEmpty() ? "never" : lastClean); }
    @Override public String facilityKind() { return "Accessible toilet"; }
    @Override public boolean facilityWorking() { return powered() && clean >= 20 && ctl.get("k_lock") == 0; }
    @Override public int facilityPoints() { return 6; }
    @Override public int accessPoints() { return 15; }
    @Override public long dailyCost() { return 250; }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "flush" -> {
                if (!facilityWorking()) return "§cClosed for cleaning";
                clean = Math.max(0, clean - 2); uses++;
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("station.flush"), SoundCategory.BLOCKS, 0.8f, 1.0f);
                return "§7Flushed";
            }
            case "wash" -> { if (!facilityWorking()) return "§cClosed for cleaning"; clean = Math.max(0, clean - 0.5f); world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("station.tap"), SoundCategory.BLOCKS, 0.6f, 1.0f); return "§7Hands washed"; }
            case "cord" -> {
                AlarmManager.get(world).raise(world, "wc:" + pos.toLong(), AlarmManager.Cat.GENERAL, AlarmManager.Sev.MAJOR, "ACCESSIBLE TOILET EMERGENCY CORD pulled at " + pos.getX() + " " + pos.getY() + " " + pos.getZ(), pos, false);
                return "§cEmergency cord pulled - staff alerted";
            }
            case "cleaned" -> { clean = 100; lastClean = DepotClock.stamp(world) + (who == null ? "" : " " + who.getName()); AlarmManager.get(world).clear("wc:" + pos.toLong()); return "§aCleaned and logged: " + lastClean; }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        long h = world.getTotalWorldTime() / 1000;
        if (h != hourSeen) { if (hourSeen != 0) clean = Math.max(0, clean - 1.5f); hourSeen = h; }
        put("pwr", powered()); put("vacant", facilityWorking()); put("closed", !facilityWorking());
        put("cleang", Math.round(clean)); put("uses", uses);
        text("logt", lastClean.isEmpty() ? "NOT YET CLEANED" : "CLEANED " + cut(lastClean.toUpperCase(java.util.Locale.ROOT), 18));
    }

    @Override protected void saveExtra(NBTTagCompound t) { t.setFloat("clean", clean); t.setString("lc", lastClean); t.setInteger("uses", uses); }
    @Override protected void loadExtra(NBTTagCompound t) { clean = t.hasKey("clean") ? t.getFloat("clean") : 100; lastClean = t.getString("lc"); uses = t.getInteger("uses"); }
}
