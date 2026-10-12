package com.dogpound.railmap.station;

import com.dogpound.railmap.depot.DepotTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Escalator section (her ChatGPT list #85): a flight of moving steps - place sections in a rising line, each one block
 * up and on from the last. Running UP (or DOWN on the direction key) the steps carry whoever stands on them along the
 * flight; stopped, it is a fixed stair. Comb plates, balustrade and handrail, and the STOP button at the landing.
 * Needs mains for the drive and 12 V for the controller; a facility (not step-free: wheelchairs take the lift).
 */
public class TileEscalator extends DepotTile implements IStationFacility {
    @Override public String wiredName() { return "Escalator"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return running() ? 25 : 0; }
    @Override public String gearState() { return running() ? (down() ? "running DOWN" : "running UP") : "stopped"; }
    @Override public String facilityKind() { return "Escalator"; }
    @Override public boolean facilityWorking() { return running(); }
    @Override public int facilityPoints() { return 6; }
    @Override public long dailyCost() { return 300; }

    public boolean running() { return powered() && mainsOk() && ctl.get("run") == 1 && ctl.get("m_estop") == 0; }
    public boolean down() { return ctl.get("k_dir") == 1; }

    @Override
    protected String press(String id, EntityPlayer who) {
        return switch (id) {
            case "start" -> { if (ctl.get("m_estop") == 1) yield "§cSTOP button is in"; if (!mainsOk()) yield "§cNo mains"; ctl.set("run", 1); yield "§aEscalator running " + (down() ? "DOWN" : "UP"); }
            case "stop" -> { ctl.set("run", 0); yield "§eEscalator stopped"; }
            default -> null;
        };
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("k_dir") && ctl.get("run") == 1) return "§cStop it before reversing";
        if (id.equals("m_estop")) { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); if (ctl.get(id) == 1) ctl.set("run", 0); return ctl.get(id) == 1 ? "§cEMERGENCY STOP" : "§aStop button reset"; }
        return null;
    }

    @Override
    protected void work() {
        if (age % 10 != 0) return;
        put("pwr", powered()); put("mainsl", mains.ok()); put("runl", running()); put("upl", running() && !down()); put("dnl", running() && down());
    }

    @Override protected void saveExtra(NBTTagCompound t) { }
    @Override protected void loadExtra(NBTTagCompound t) { }
}
