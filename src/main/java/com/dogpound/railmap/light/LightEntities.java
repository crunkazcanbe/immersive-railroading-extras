package com.dogpound.railmap.light;

import com.dogpound.railmap.RailMap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

/** registers the client-only train light point, so mods that look entities up by registry name never meet an unknown one */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class LightEntities {
    private LightEntities() {}

    @SubscribeEvent
    public static void register(RegistryEvent.Register<EntityEntry> e) {
        e.getRegistry().register(EntityEntryBuilder.create().entity(EntityTrainLight.class)
                .id(new ResourceLocation(RailMap.MODID, "train_light"), 940).name(RailMap.MODID + ".train_light")
                .tracker(16, 1000, false).build());
    }
}
