package com.dogpound.railmap.mail;

import com.dogpound.railmap.RailMap;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Registers the mail item on its own (no shared proxy lists touched). */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class MailRegistry {
    private MailRegistry() {}

    public static ItemMail mail;

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> e) {
        e.getRegistry().register(mail = new ItemMail());
    }
}
