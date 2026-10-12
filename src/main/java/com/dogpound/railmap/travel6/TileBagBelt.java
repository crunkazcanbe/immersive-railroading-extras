package com.dogpound.railmap.travel6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.depot.Mains;
import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Baggage belt conveyor (batch 6b #9): a powered rubber belt that carries whatever lies on it (bags, anything) away from
 * whoever laid it, from belt to belt. At the end it hands bags to a Baggage Hall (make-up cart) or a check-in desk's
 * hold; with nothing to take them they stop at the end and, after 10 s, the JAM lamp + a belt-jam alarm come on. Its
 * pushbutton station: START, STOP, the latching E-STOP (raises an alarm until it is pulled back), RUN / JAM lamps and a
 * motor current gauge (it rises with the load). 12 V control + mains (the drive motor) - no mains, no movement.
 */
public class TileBagBelt extends SafetyTile {
    private final Mains mains = new Mains();
    private int moved, jamTicks;
    private boolean jam, estopAlarm;

    @Override public String wiredName() { return "Baggage belt " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return !powered() ? "NO 12 V" : !mains.ok() ? "NO MAINS (" + mains.what + ")" : estop() ? "E-STOP" : running() ? (jam ? "JAMMED" : "RUNNING") + " · " + moved + " moved" : "STOPPED"; }

    boolean estop() { return ctl.get("m_estop") == 1; }
    public boolean running() { return powered() && mains.ok() && ctl.get("runs") == 1 && !estop(); }
    EnumFacing dir() { return controlFacing().getOpposite(); }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "start":
                if (!powered()) return "§cNo 12 V";
                if (estop()) return "§cE-STOP is in - pull it back first";
                ctl.set("runs", 1); return "§aBelt running";
            case "stop": ctl.set("runs", 0); return "§eBelt stopped";
            case "test": return powered() ? lampTest() : "§cNo 12 V";
            case "m_estop": {
                ctl.set(id, estop() ? 0 : 1);
                if (estop()) { ctl.set("runs", 0); return "§cE-STOP - belt stopped"; }
                return "§eE-STOP pulled back - press START";
            }
            default: return "§7" + gearState();
        }
    }

    @Override
    protected void serverTick() {
        if (mains.tick(world, pos, term, wiredName(), running() ? 6 : 0)) controlsChanged();
        if (estop() != estopAlarm) {
            estopAlarm = estop();
            String key = "travel6.belt.estop." + pos.toLong();
            if (estopAlarm) AlarmManager.get(world).raise(world, key, AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, "Baggage belt " + pos.getX() + "," + pos.getZ() + " E-STOP pressed", pos, true);
            else AlarmManager.get(world).clear(key);
            markDirty();
        }
        int load = 0;
        if (running()) {
            List<EntityItem> on = world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(pos).contract(0, 0.45, 0).offset(0, 0.25, 0));
            double v = TravelConfig.beltSpeed / 20.0;
            EnumFacing d = dir();
            BlockPos next = pos.offset(d);
            boolean nextBelt = world.getTileEntity(next) instanceof TileBagBelt;
            TileEntity recv = nextBelt ? null : world.getTileEntity(next);
            boolean stuck = false;
            for (EntityItem e : on) {
                if (e.isDead) continue;
                load++;
                double ox = e.posX - pos.getX() - 0.5, oz = e.posZ - pos.getZ() - 0.5;
                double along = ox * d.getXOffset() + oz * d.getZOffset();
                if (!nextBelt && along > 0.38) {                                  // at the end
                    ItemStack s = e.getItem();
                    ItemStack left = Baggage.accept(world, recv, s);
                    if (left.isEmpty()) { e.setDead(); moved++; markDirty(); continue; }
                    e.motionX = e.motionZ = 0; stuck = true;
                    continue;
                }
                e.motionX = d.getXOffset() * v - (d.getXOffset() == 0 ? ox * 0.2 : 0);   // runs along, kept to the middle
                e.motionZ = d.getZOffset() * v - (d.getZOffset() == 0 ? oz * 0.2 : 0);
                if (e.onGround) e.motionY = 0;
            }
            jamTicks = stuck ? jamTicks + 1 : 0;
        } else jamTicks = 0;
        boolean j = jamTicks > 200;
        if (j != jam) {
            jam = j;
            String key = "travel6.belt.jam." + pos.toLong();
            if (jam) AlarmManager.get(world).raise(world, key, AlarmManager.Cat.MACHINE, AlarmManager.Sev.INFO, "Baggage belt " + pos.getX() + "," + pos.getZ() + " jammed - nothing takes the bags at its end", pos, true);
            else AlarmManager.get(world).clear(key);
        }
        if (age % 5 != 0) return;
        put("runl", running());
        put("jaml", jam);
        put("pwr", powered());
        put("mainsl", mains.ok());
        put("amps", running() ? 30 + Math.min(70, load * 12) : 0);
        put("nmoved", moved);
    }

    @Override public void gearInfo(java.util.Map<String, Object> m) { m.put("mains", mains.what); }
    @Override protected void save(NBTTagCompound t) { mains.write(t); t.setInteger("moved", moved); t.setBoolean("ea", estopAlarm); }
    @Override protected void load(NBTTagCompound t) { mains.read(t); moved = t.getInteger("moved"); estopAlarm = t.getBoolean("ea"); }
}
