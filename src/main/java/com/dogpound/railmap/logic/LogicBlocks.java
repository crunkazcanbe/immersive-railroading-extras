package com.dogpound.railmap.logic;

import com.dogpound.railmap.RailMap;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** Railway logic modules (her ChatGPT list #99); CommonProxy registers them. */
public final class LogicBlocks {
    private LogicBlocks() {}

    public static List<Block> create() {
        return Arrays.asList(
                new BlockLogic(TileLogic.Kind.GATE, "Logic gate module: AND / OR / XOR / NAND / NOR / XNOR / NOT / BUFFER on three inputs"),
                new BlockLogic(TileLogic.Kind.TIMER, "Timer relay: on-delay, off-delay, pulse, flasher - preset in tenths of a second"),
                new BlockLogic(TileLogic.Kind.COUNTER, "Counter: up, down, up/down with preset, reset input, kept through power cuts"),
                new BlockLogic(TileLogic.Kind.COMPARE, "Comparator: A against B or a setpoint (>, >=, =, <=, <, <>, window) with hysteresis"),
                new BlockLogic(TileLogic.Kind.LATCH, "Latch + edge detector: SR, RS, toggle, rising / falling / any-edge pulse"),
                new BlockLogic(TileLogic.Kind.MATH, "Arithmetic: A + - x / B, min, max, average, difference - into a variable"),
                new BlockLogic(TileLogic.Kind.SEQUENCER, "Sequencer: a 2-8 state machine stepped by input or by time, one channel per state"));
    }

    public static void registerTiles() {
        GameRegistry.registerTileEntity(TileLogic.class, new ResourceLocation(RailMap.MODID, "logic_module"));
    }
}
