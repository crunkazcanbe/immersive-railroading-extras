package com.dogpound.railmap.mail.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.mail.ItemMail;
import com.dogpound.railmap.mail.MailRegistry;
import com.dogpound.railmap.mail.TileSortingOffice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** mail item models, the letter screen (writing paper / reading a letter) and the sorting frame renderer (batch 5 #8) */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class MailClient {
    private MailClient() {}

    @SubscribeEvent
    public static void models(ModelRegistryEvent e) {
        if (MailRegistry.mail != null)
            for (int i = 0; i < ItemMail.VARIANTS.length; i++)
                ModelLoader.setCustomModelResourceLocation(MailRegistry.mail, i, new ModelResourceLocation(RailMap.MODID + ":" + ItemMail.VARIANTS[i], "inventory"));
        ClientRegistry.bindTileEntitySpecialRenderer(TileSortingOffice.class, new MailFrameRenderer());
    }

    @SubscribeEvent
    public static void use(PlayerInteractEvent.RightClickItem e) {
        if (!e.getWorld().isRemote || e.getEntityPlayer() != Minecraft.getMinecraft().player) return;
        ItemStack s = e.getItemStack();
        if (ItemMail.is(s, ItemMail.PAPER)) Minecraft.getMinecraft().displayGuiScreen(new GuiLetter(e.getHand(), null));
        else if (ItemMail.is(s, ItemMail.LETTER)) Minecraft.getMinecraft().displayGuiScreen(new GuiLetter(e.getHand(), s));
    }
}
