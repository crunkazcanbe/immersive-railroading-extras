package com.dogpound.railmap.api;

import net.minecraft.entity.Entity;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import train.common.api.AbstractTrains;
import train.common.api.Locomotive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Traincraft (her 1.12.2 fork, modid "tc") for the transport adapter API (#96). Only loaded by reflection from
 * {@link Transport} when Traincraft is installed, so the train.* classes are never touched without it.
 */
public final class TraincraftAdapter implements Transport.Adapter {
    @Override public String id() { return "tc"; }
    @Override public String modId() { return "tc"; }
    @Override public String label() { return "Traincraft"; }
    @Override public List<String> commands() { return Arrays.asList("destination <name>", "stop"); }

    @Override
    public List<Transport.Vehicle> vehicles(World w) {
        List<Transport.Vehicle> out = new ArrayList<>();
        for (Entity e : new ArrayList<>(w.loadedEntityList)) {
            if (!(e instanceof AbstractTrains t) || e.isDead) continue;
            Transport.Vehicle v = new Transport.Vehicle();
            v.adapter = "tc"; v.entityId = e.getEntityId(); v.id = "tc:" + e.getEntityId(); v.uuid = e.getUniqueID();
            v.kind = t instanceof Locomotive ? "train" : "car";
            v.name = t.trainName == null || t.trainName.isEmpty() ? (t.trainType == null ? "Traincraft" : t.trainType) : t.trainName;
            v.destination = t.getDestination();
            v.x = e.posX; v.y = e.posY; v.z = e.posZ;
            v.speedKmh = Math.sqrt(e.motionX * e.motionX + e.motionZ * e.motionZ) * 20 * 3.6;
            if (e instanceof IInventory inv && !(t instanceof Locomotive)) {
                int used = 0, cap = 0;
                for (int i = 0; i < inv.getSizeInventory(); i++) {
                    ItemStack s = inv.getStackInSlot(i);
                    cap += s.isEmpty() ? inv.getInventoryStackLimit() : s.getMaxStackSize();
                    if (s.isEmpty()) continue;
                    used += s.getCount();
                    if (s.getItem().getRegistryName() != null) v.cargoTypes.add(s.getItem().getRegistryName().toString());
                }
                v.cargo = used;
                v.fillPct = cap == 0 ? -1 : used * 100 / cap;
            } else Transport.cargoOf(e, v);
            v.status = v.speedKmh < 0.5 ? "stopped" : "moving";
            out.add(v);
        }
        return out;
    }

    @Override
    public String command(World w, Transport.Vehicle v, String cmd, String arg) {
        Entity e = w.getEntityByID(v.entityId);
        if (!(e instanceof AbstractTrains t)) return "refused: train unloaded";
        switch (cmd) {
            case "destination": t.destination = arg; return "ok: destination = " + (arg.isEmpty() ? "(none)" : arg);
            case "stop": e.motionX = 0; e.motionZ = 0; return "ok: stopped";
            default: return "refused: unknown command " + cmd;
        }
    }
}
