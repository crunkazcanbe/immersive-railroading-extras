package com.dogpound.railmap.grid;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/** One block class for the whole power grid (same 1.12 'building' trick as BlockCatenary for per-kind states). */
public class BlockGrid extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    public static final PropertyInteger STATE = PropertyInteger.create("state", 0, 2);   // breaker: closed / open / tripped; others: off / on
    public static final PropertyBool N = PropertyBool.create("north"), S = PropertyBool.create("south"), E = PropertyBool.create("east"),
            W = PropertyBool.create("west"), U = PropertyBool.create("up"), D = PropertyBool.create("down");
    /** what a power cable looks like on the ground (requested feature) */
    public enum Look implements net.minecraft.util.IStringSerializable {
        MV, LV, HV, SC, BUS;
        @Override public String getName() { return name().toLowerCase(java.util.Locale.ROOT); }
        public static Look of(Elec.Spec s) {
            if (s.cores == Elec.Cores.BUSBAR) return BUS;
            if (s.mat == Elec.Material.SUPERCONDUCTOR) return SC;
            return s.ins == Elec.Insulation.LV ? LV : s.ins == Elec.Insulation.HV ? HV : MV;
        }
    }
    public enum Gauge implements net.minecraft.util.IStringSerializable {
        MED, THIN, THICK;
        @Override public String getName() { return name().toLowerCase(java.util.Locale.ROOT); }
        public static Gauge of(Elec.Spec s) { return s.mm2 <= 10 ? THIN : s.mm2 <= 95 ? MED : THICK; }
    }
    public static final net.minecraft.block.properties.PropertyEnum<Look> LOOK = net.minecraft.block.properties.PropertyEnum.create("look", Look.class);
    public static final net.minecraft.block.properties.PropertyEnum<Gauge> GAUGE = net.minecraft.block.properties.PropertyEnum.create("gauge", Gauge.class);

    public static IBlockState cableState(IBlockState base, Elec.Spec s) { return base.withProperty(LOOK, Look.of(s)).withProperty(GAUGE, Gauge.of(s)); }

    private static GridKind building;
    public final GridKind kind;

    private static Material prep(GridKind k) { building = k; return Material.IRON; }

    public BlockGrid(GridKind kind) {
        super(prep(kind));
        this.kind = kind;
        setRegistryName(RailMap.MODID, kind.id);
        setTranslationKey(RailMap.MODID + "." + kind.id);
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(3f);
        IBlockState st = blockState.getBaseState();
        if (kind == GridKind.CABLE) st = st.withProperty(N, false).withProperty(S, false).withProperty(E, false).withProperty(W, false).withProperty(U, false).withProperty(D, false)
                .withProperty(LOOK, Look.MV).withProperty(GAUGE, Gauge.MED);
        else st = st.withProperty(FACING, EnumFacing.NORTH).withProperty(STATE, 0)
                .withProperty(N, false).withProperty(S, false).withProperty(E, false).withProperty(W, false);
        setDefaultState(st);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        // machines also carry N/S/E/W: a cable-entry port is drawn on every side something really connects (actual state only)
        return building == GridKind.CABLE ? new BlockStateContainer(this, N, S, E, W, U, D, LOOK, GAUGE) : new BlockStateContainer(this, FACING, STATE, N, S, E, W);
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tip, ITooltipFlag flag) {
        tip.add("§7" + kind.tip);
        if (kind == GridKind.CABLE) tip.add("§8Intake → Transformer → Rectifier / Traction → Breaker → Feeder");
        else tip.add("§8Sneak + Signal Wrench: settings · right-click: status");
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess w, BlockPos p) {
        if (kind != GridKind.CABLE) {
            EnumFacing front = Panel.of(kind) != null ? state.getValue(FACING) : null;   // nothing plugs into the control panel
            return state.withProperty(N, front != EnumFacing.NORTH && link(w, p, EnumFacing.NORTH)).withProperty(S, front != EnumFacing.SOUTH && link(w, p, EnumFacing.SOUTH))
                    .withProperty(E, front != EnumFacing.EAST && link(w, p, EnumFacing.EAST)).withProperty(W, front != EnumFacing.WEST && link(w, p, EnumFacing.WEST));
        }
        return state.withProperty(N, conn(w, p, EnumFacing.NORTH)).withProperty(S, conn(w, p, EnumFacing.SOUTH)).withProperty(E, conn(w, p, EnumFacing.EAST))
                .withProperty(W, conn(w, p, EnumFacing.WEST)).withProperty(U, conn(w, p, EnumFacing.UP)).withProperty(D, conn(w, p, EnumFacing.DOWN));
    }

    /** does something on this side really hook into this machine? our cable / grid parts, any mod's power cable or
     *  machine (Forge Energy) where this machine takes or gives power, any mod's pipe where it burns fuel or steam */
    private boolean link(IBlockAccess w, BlockPos p, EnumFacing side) {
        BlockPos n = p.offset(side);
        if (w.getBlockState(n).getBlock() instanceof BlockGrid) return true;
        TileEntity te = w.getTileEntity(n);
        if (te == null) return false;
        EnumFacing back = side.getOpposite();
        try {
            boolean fe = kind == GridKind.INTAKE || kind == GridKind.BATTERY || kind == GridKind.FEEDER || kind.generator();
            if (fe && te.hasCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY, back)) return true;
            if (kind.buttons() && te.hasCapability(net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, back)) return true;
            // mods that only expose power on the server (Electrical Age's converter, IC2-style): treat as linked by class name
            String c = te.getClass().getName().toLowerCase();
            if (fe && (c.contains("energyconverter") || c.contains("cable") || c.contains("conduit") || c.contains("wire"))) return true;
        } catch (RuntimeException ignored) { }
        return false;
    }

    /** a cable reaches toward a neighbour unless that neighbour is a machine showing us its control panel
     *  (real cables come in through glands at the back / sides / top / bottom, never through the panel) */
    private static boolean conn(IBlockAccess w, BlockPos p, EnumFacing d) {
        IBlockState s = w.getBlockState(p.offset(d));
        if (!(s.getBlock() instanceof BlockGrid b)) return false;
        return !panelFacing(b.kind, s, d.getOpposite());
    }

    /** is `side` the control-panel face of this grid block? */
    static boolean panelFacing(GridKind k, IBlockState s, EnumFacing side) {
        return k != GridKind.CABLE && Panel.of(k) != null && s.getPropertyKeys().contains(FACING) && s.getValue(FACING) == side;
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hx, float hy, float hz, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        return kind == GridKind.CABLE ? getDefaultState() : getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override public IBlockState getStateFromMeta(int meta) {
        if (kind == GridKind.CABLE) {                                  // meta = look * 3 + gauge; old cables (0) = MV medium
            int m = Math.min(14, meta);
            return getDefaultState().withProperty(LOOK, Look.values()[m / 3]).withProperty(GAUGE, Gauge.values()[m % 3]);
        }
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3)).withProperty(STATE, Math.min(2, meta >> 2));
    }

    @Override public int getMetaFromState(IBlockState s) {
        return kind == GridKind.CABLE ? s.getValue(LOOK).ordinal() * 3 + s.getValue(GAUGE).ordinal() : s.getValue(FACING).getHorizontalIndex() | (s.getValue(STATE) << 2);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return kind.machine(); }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileGrid(kind); }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            GridData d = GridData.get(world);
            d.add(pos, kind);
            if (kind.converter()) d.node(pos).realSides = true;   // new ones: real sides
        }
    }

    /** a cable being broken: its conductor, so the drop is the same cable (the node is gone by getDrops time) */
    private static final java.util.Map<BlockPos, String> DROPPING = new java.util.HashMap<>();

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            GridData d = GridData.get(world);
            GridData.Node n = d.node(pos);
            if (kind == GridKind.CABLE && n != null) DROPPING.put(pos.toImmutable(), n.spec);
            d.remove(pos);
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void getDrops(net.minecraft.util.NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        if (kind != GridKind.CABLE) { super.getDrops(drops, world, pos, state, fortune); return; }
        String sp = DROPPING.remove(pos);
        ItemStack s = new ItemStack(net.minecraft.item.Item.getItemFromBlock(this));
        if (sp != null && !sp.isEmpty()) ItemPowerCable.with(s, Elec.Spec.parse(sp));
        drops.add(s);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing f,
                                    float hx, float hy, float hz) {
        if (player.isSneaking() && player.getHeldItem(hand).isEmpty() && world.getTileEntity(pos) instanceof TileGrid lt && !lt.loto().isEmpty()) {
            if (!world.isRemote) ItemPadlock.remove(lt, player);
            return true;
        }
        if (world.isRemote || player.isSneaking()) return !player.isSneaking();
        if (world.getTileEntity(pos) instanceof TileGrid t) {
            Panel.C c = panelHit(kind, state.getValue(FACING), pos, player);
            com.dogpound.railmap.RailMap.LOG.debug("[IR Extras] {} clicked: face {} -> control {}", kind, f, c == null ? null : c.id);
            player.sendStatusMessage(new TextComponentString(c != null ? t.press(c.id, player) : t.click(player)), true);
        }
        else if (kind == GridKind.CABLE) {
            GridData d = GridData.get(world);
            GridData.Net n = d.netOf(pos);
            GridData.Node c = d.node(pos);
            Elec.Spec sp = c == null ? Elec.Spec.LEGACY : c.cable();
            String el = "";
            if (n != null && n.level != null && c != null)
                el = String.format(java.util.Locale.ROOT, " · %s · %.0f A of %.0f · %.0f °C · %s", Elec.si(n.level.volts * c.pu, "V"), c.amps, sp.ampacity(), c.temp,
                        c.temp > sp.ins.maxTemp ? "§cOVERHEATING§7" : "ok");
            player.sendStatusMessage(new TextComponentString(n == null ? "§7" + sp.label() + " - not part of a network" :
                    "§7" + sp.label() + " · network " + n.id + el + " · " + (n.problem.isEmpty() ? "§alive" : "§c" + n.problem)), true);
        }
        return true;
    }

    /**
     * Which real control the player is pointing at. The panel is defined in face pixels (u from your left, v up) on a
     * plane that may stand proud of the block or sit recessed in it (switchgear), so the player's line of sight is
     * intersected with THAT plane, in the model's own (north-facing) space - exact even at an angle.
     */
    static Panel.C panelHit(GridKind kind, EnumFacing facing, BlockPos pos, EntityPlayer player) {
        Panel panel = Panel.of(kind);
        if (panel == null) return null;
        net.minecraft.util.math.Vec3d eye = player.getPositionEyes(1f), look = player.getLook(1f);
        double ex = eye.x - pos.getX() - 0.5, ey = eye.y - pos.getY(), ez = eye.z - pos.getZ() - 0.5, dx = look.x, dy = look.y, dz = look.z;
        int turns = facing.getHorizontalIndex() == 2 ? 0 : facing.getHorizontalIndex() == 3 ? 1 : facing.getHorizontalIndex() == 0 ? 2 : 3;   // N 0, E 90, S 180, W 270
        for (int i = 0; i < turns; i++) {                                    // undo the blockstate's clockwise turns
            double nx = ez, nz = -ex; ex = nx; ez = nz;
            nx = dz; nz = -dx; dx = nx; dz = nz;
        }
        double planeZ = panel.face / 16.0 - 0.5;                              // model z of the control faces, centred
        if (Math.abs(dz) < 1e-6) return null;
        double t = (planeZ - ez) / dz;
        if (t < 0 || t > 6) return null;
        double mx = (ex + dx * t + 0.5) * 16, my = (ey + dy * t) * 16;
        return Panel.hit(kind, 16 - mx, my);                                  // model +x is the viewer's left
    }

    // ---- shape -------------------------------------------------------------------------------------------------
    private static final AxisAlignedBB CABLE_BOX = new AxisAlignedBB(0.3, 0, 0.3, 0.7, 0.4, 0.7);

    @Override @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess s, BlockPos p) {
        switch (kind) {
            case CABLE: return CABLE_BOX;
            case FEEDER: case METER: return new AxisAlignedBB(0.2, 0, 0.2, 0.8, 1, 0.8);
            default: return FULL_BLOCK_AABB;
        }
    }

    @Override @SuppressWarnings("deprecation") public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override @SuppressWarnings("deprecation") public boolean isFullCube(IBlockState s) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT; }

    @Override @SuppressWarnings("deprecation")
    public boolean hasComparatorInputOverride(IBlockState s) { return kind == GridKind.METER || kind == GridKind.BATTERY || kind == GridKind.INTAKE; }

    @Override @SuppressWarnings("deprecation")
    public int getComparatorInputOverride(IBlockState s, World world, BlockPos pos) {
        return world.getTileEntity(pos) instanceof TileGrid t ? t.comparator() : 0;
    }
}
