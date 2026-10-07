package com.dogpound.railmap.signs;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.server.RailProps;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;

/**
 * Works out what each Pride Rail car shows (the whole consist follows its locomotive): automatic = line + next stop
 * from IR Extras' autopilot, or the custom text set with /irextras sign or the Control Center, or off.
 */
public class SignServer {
    private static final boolean IR = Loader.isModLoaded("immersiverailroading");

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !IR || e.world.getTotalWorldTime() % 30 != 3) return;
        if (e.world.playerEntities.isEmpty()) return;
        try {
            PacketSigns p = build(e.world);
            if (p.entries.isEmpty()) return;
            for (net.minecraft.entity.player.EntityPlayer pl : e.world.playerEntities)
                if (pl instanceof EntityPlayerMP mp) RailMap.NETWORK.sendTo(p, mp);
        } catch (LinkageError | RuntimeException ex) {
            RailMap.LOG.debug("[IR Extras] signs: {}", ex.toString());
        }
    }

    public static boolean ours(EntityRollingStock s) {
        String id = s.getDefinitionID();
        return id != null && id.contains("/pride_");
    }

    /** the locomotive that owns this car's consist (itself if it is one / nothing else is coupled) */
    public static EntityRollingStock head(EntityRollingStock s) {
        if (s instanceof EntityCoupleableRollingStock c) {
            List<EntityCoupleableRollingStock> train = c.getTrain();
            for (EntityCoupleableRollingStock t : train) if (t instanceof Locomotive) return t;
        }
        return s;
    }

    private static PacketSigns build(net.minecraft.world.World world) {
        PacketSigns p = new PacketSigns();
        RailProps props = RailProps.get(world);
        RailwayData data = RailwayData.get(world);
        for (EntityRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityRollingStock.class)) {
            if (s.isDead() || !ours(s)) continue;
            EntityRollingStock h = head(s);
            RailProps.Props pr = props.peek(h.getUUID());
            PacketSigns.Entry en = new PacketSigns.Entry();
            en.entityId = s.internal.getEntityId();
            en.title = h.getDefinition() == null ? "" : h.getDefinition().name().replaceAll(" \\(.*", "");
            en.mode = pr == null ? 0 : pr.signMode;
            en.color = 0xFF9A1F;
            RailwayData.AutoTrain auto = data.trains().get(h.getUUID());
            if (auto != null) {
                en.line = auto.line == null ? "" : auto.line;
                RailwayData.Line l = data.lines().get(en.line);
                if (l != null) en.color = l.color;
                Autopilot.Run r = Autopilot.run(auto.loco);
                en.next = r == null || r.nextStopName == null ? "" : r.nextStopName;
            }
            if (en.mode == 1) en.dest = pr.signText;
            else if (en.mode == 2) en.dest = "";
            else en.dest = !en.next.isEmpty() ? (en.line.isEmpty() ? "" : en.line + "  ") + "for " + en.next
                    : (auto != null ? en.line : "Pride Rail  " + en.title);
            p.entries.add(en);
        }
        return p;
    }
}
