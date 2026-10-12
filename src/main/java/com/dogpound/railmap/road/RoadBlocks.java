package com.dogpound.railmap.road;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import com.dogpound.railmap.depot.DepotBlock;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** road traffic (batch 5 #3) and tram (#4) blocks; CommonProxy registers them */
public final class RoadBlocks {
    private RoadBlocks() {}

    /** the cabinet: its item also carries the road vehicles' body models (RoadClient) */
    public static Block controller;

    public static List<Block> create() {
        Block road = new Block(Material.ROCK) {{ setSoundType(SoundType.STONE); }}.setHardness(1.5f).setResistance(10f).setCreativeTab(RailMapTab.INSTANCE);   // street track: asphalt with grooved rails (also IR's bed fill)
        road.setRegistryName(RailMap.MODID, "tram_road").setTranslationKey(RailMap.MODID + ".tram_road");
        return Arrays.asList(
                controller = new DepotBlock("traffic_controller", TileTrafficController::new, new double[]{1, 0, 3, 15, 16, 15},
                        "Traffic signal controller: runs the signals, ped crossings and tram signals within 48 blocks",
                        "Stages A-D + PED + TRAM, vehicle-actuated / fixed / manual / flash, tram priority, timing screen. 12 V + mains"),
                new DepotBlock("traffic_signal", TileTrafficSignal::new, new double[]{5, 0, 6, 11, 16, 11},
                        "Traffic signal head (red / amber / green): place it at the stop line facing the drivers",
                        "GROUP selector on the back = phase group A-D of the nearest controller; road vehicles stop at it"),
                new DepotBlock("ped_unit", TilePedUnit::new, new double[]{5, 0, 7, 11, 16, 11},
                        "Pedestrian crossing: push button, WAIT lamp, red / green man, audible beeps",
                        "PUSH calls the pedestrian stage at the nearest controller"),
                new DepotBlock("tram_signal", TileTramSignal::new, new double[]{5, 0, 7, 11, 16, 11},
                        "Tram signal (white bars): STOP / GO / GO LEFT / GO RIGHT / PREPARE, A = call registered",
                        "Back: ROUTE + MODE (CONTROLLER tram stage / SECTION occupancy). Tram autopilots obey it"),
                new NoCollision("tram_loop", TileTramLoop::new, new double[]{0, 0, 0, 16, 1, 16},
                        "Detection loop in the road: TRAM CALL / TRAM CANCEL (priority check-in / out) or VEH A-D",
                        "Calls the nearest Traffic Signal Controller when a tram or road vehicle crosses it"),
                new DepotBlock("tram_stop", TileTramStop::new, new double[]{0, 0, 8, 16, 16, 16},
                        "Tram stop: shelter, real-time display, help point, stop flag - a station trams call at",
                        "STOP key: ALL STOP / REQUEST. Place beside the tram track. 12 V + mains for display + lights") {
                    @Override
                    public void breakBlock(World world, BlockPos pos, IBlockState state) {
                        if (!world.isRemote) com.dogpound.railmap.server.StationData.get(world).setName(pos, "");
                        super.breakBlock(world, pos, state);
                    }
                },
                new DepotBlock("ticket_validator", TileTicketValidator::new, new double[]{5, 0, 6, 11, 16, 11},
                        "Ticket validator: tap a ticket or pass on the reader at a tram stop",
                        "Punches tickets valid from / to the stop, checks pass zones. 12 V"),
                new DepotBlock("tram_edge", TileTramEdge::new, new double[]{0, 0, 0, 16, 1.2, 16},
                        "Lit platform edge: yellow line, tactile paving, LED edge lights",
                        "The LEDs flash while a tram is due at the nearest tram stop"),
                new DepotBlock("tram_pole", TileTramPole::new, new double[]{6, 0, 6, 10, 16, 10},
                        "Tramway OHLE pole: bracket arm for the contact wire, LINE LIVE / DEAD section indicator",
                        "Shows the overhead line section at its arm. 12 V"),
                road);
    }

    public static void registerTiles() {
        reg(TileTrafficController.class, "traffic_controller");
        reg(TileTrafficSignal.class, "traffic_signal");
        reg(TilePedUnit.class, "ped_unit");
        reg(TileTramSignal.class, "tram_signal");
        reg(TileTramLoop.class, "tram_loop");
        reg(TileTramStop.class, "tram_stop");
        reg(TileTicketValidator.class, "ticket_validator");
        reg(TileTramEdge.class, "tram_edge");
        reg(TileTramPole.class, "tram_pole");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) {
        GameRegistry.registerTileEntity(c, new net.minecraft.util.ResourceLocation(RailMap.MODID, id));
    }

    /** a loop is sawn into the road: vehicles and people go straight over it */
    static final class NoCollision extends DepotBlock {
        NoCollision(String id, java.util.function.Supplier<net.minecraft.tileentity.TileEntity> tile, double[] box, String... tips) { super(id, tile, box, tips); }
        @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return NULL_AABB; }
    }
}
