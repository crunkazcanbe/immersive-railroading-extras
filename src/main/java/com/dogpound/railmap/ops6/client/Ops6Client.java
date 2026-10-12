package com.dogpound.railmap.ops6.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.ops6.MasterClocks;
import com.dogpound.railmap.ops6.Ops6Net;
import com.dogpound.railmap.ops6.Ops6Registry;
import com.dogpound.railmap.ops6.PacketOps6;
import com.dogpound.railmap.ops6.TileRailCrane;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** batch 6 E client: item models, the crane's moving parts, and the screens the server opens / feeds */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class Ops6Client {
    private Ops6Client() {}

    @SubscribeEvent
    public static void models(ModelRegistryEvent e) {
        for (Block b : Ops6Registry.BLOCKS)
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(b), 0, new ModelResourceLocation(b.getRegistryName(), "inventory"));
        if (Ops6Registry.beltpack != null) ModelLoader.setCustomModelResourceLocation(Ops6Registry.beltpack, 0, new ModelResourceLocation(RailMap.MODID + ":rco_beltpack", "inventory"));
        ClientRegistry.bindTileEntitySpecialRenderer(TileRailCrane.class, new CraneRenderer());
    }

    static void send(PacketOps6 p) { if (Ops6Net.NET != null) Ops6Net.NET.sendToServer(p); }

    public static void openBeltpack() { Minecraft.getMinecraft().displayGuiScreen(new GuiBeltpack()); }

    public static void receive(NBTTagCompound t) {
        Minecraft mc = Minecraft.getMinecraft();
        NBTTagCompound d = t.getCompoundTag("d");
        BlockPos pos = BlockPos.fromLong(t.getLong("pos"));
        switch (t.getString("op")) {
            case "open" -> mc.displayGuiScreen(new GuiOps6Screen(pos, d));
            case "screen" -> { if (mc.currentScreen instanceof GuiOps6Screen g && g.pos.equals(pos)) g.update(d); }
            case "rcostate" -> { if (mc.currentScreen instanceof GuiBeltpack g) g.update(d); }
            case "opensim" -> mc.displayGuiScreen(new GuiDriverSim(pos));
            case "simstate" -> { if (mc.currentScreen instanceof GuiDriverSim g && g.pos.equals(pos)) g.update(d); }
            case "clocks" -> MasterClocks.clientSync(d);
            default -> { }
        }
    }
}
