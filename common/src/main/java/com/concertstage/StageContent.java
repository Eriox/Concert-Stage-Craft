package com.concertstage;

import com.concertstage.block.AngledHolderBlock;
import com.concertstage.block.AngledHolderBlockEntity;
import com.concertstage.block.CurtainClothBlock;
import com.concertstage.block.CurtainRailBlock;
import com.concertstage.block.CurtainRailBlockEntity;
import com.concertstage.block.HoistBlock;
import com.concertstage.block.MotorTowerBlock;
import com.concertstage.block.MultiPartBlock;
import com.concertstage.block.MultiPartBlock.Placement;
import com.concertstage.block.ShapedBlock;
import com.concertstage.block.StageStairsBlock;
import com.concertstage.block.TowerBlockEntity;
import com.concertstage.block.tarp.StageTarpClothBlock;
import com.concertstage.block.tarp.StageTarpRailBlock;
import com.concertstage.block.tarp.StageTarpRailBlockEntity;
import com.concertstage.block.tarp.StageTarpTopBlock;
import com.concertstage.item.AngleAwareBlockItem;
import com.concertstage.stage.*;
import com.concertstage.item.AngleToolItem;
import com.concertstage.item.CurtainToolItem;
import com.concertstage.item.RemoteItem;
import com.concertstage.lift.LiftStructureEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Loader-agnostic content definitions. Forge and Fabric just register what is in these maps. */
public final class StageContent {
    public static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    public static final Map<String, Item> ITEMS = new LinkedHashMap<>();
    public static final Map<String, BlockEntityType<?>> BLOCK_ENTITIES = new LinkedHashMap<>();
    public static final Map<String, EntityType<?>> ENTITY_TYPES = new LinkedHashMap<>();

    public static final Block STAGE_RISER = block("stage_riser", new ShapedBlock(metal().noOcclusion(), Shapes.block()));
    public static final Block STAGE_STAIRS = block("stage_stairs", new StageStairsBlock(metal().noOcclusion()));
    public static final List<StageEquipmentBlock> STAGE_EQUIPMENT = createStageEquipment();
    public static final Block STAGE_MOUNT = blockNoItem("stage_mount", new StageMountBlock(metal().noOcclusion().dynamicShape().noLootTable()));
    public static final BlockEntityType<StageAttachmentBlockEntity> STAGE_ATTACHMENT_ENTITY = blockEntity("stage_attachment",
        BlockEntityType.Builder.of(StageAttachmentBlockEntity::new, stageEntityBlocks()).build(null));

    // ---- Trusses (one block wide, 16 px, since 1.2.9: outline and collision are the whole cell). Multi-block models: sizes are (x, y, z) cells ----
    public static final Block TRUSS_LADDER_L1 = block("truss_ladder_l1", truss(1, 1, 1, Placement.EXTEND, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_LADDER_L1_NOIR = block("truss_ladder_l1_noir", truss(1, 1, 1, Placement.EXTEND, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_LADDER_L2 = block("truss_ladder_l2", truss(2, 1, 1, Placement.EXTEND, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_LADDER_L2_NOIR = block("truss_ladder_l2_noir", truss(2, 1, 1, Placement.EXTEND, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_LADDER_V2 = block("truss_ladder_v2", truss(1, 2, 1, Placement.LOOK, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_LADDER_V2_NOIR = block("truss_ladder_v2_noir", truss(1, 2, 1, Placement.LOOK, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_JUNCTION_90 = block("truss_junction_90", truss(1, 1, 1, Placement.CORNER3, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_JUNCTION_90_NOIR = block("truss_junction_90_noir", truss(1, 1, 1, Placement.CORNER3, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_JUNCTION_T = block("truss_junction_t", truss(1, 1, 1, Placement.CORNER3, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_JUNCTION_T_NOIR = block("truss_junction_t_noir", truss(1, 1, 1, Placement.CORNER3, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_JUNCTION_CROSS = block("truss_junction_cross", truss(1, 1, 1, Placement.CORNER3, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_JUNCTION_CROSS_NOIR = block("truss_junction_cross_noir", truss(1, 1, 1, Placement.CORNER3, (lx, ly, lz) -> Shapes.block()));

    public static final Block TRUSS_H1_L1 = block("truss_h1_l1", truss(1, 1, 1, Placement.EXTEND,
        (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_H1_L2 = block("truss_h1_l2", truss(2, 1, 1, Placement.EXTEND,
        (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_H2_L2 = block("truss_h2_l2", truss(2, 2, 1, Placement.EXTEND,
        (lx, ly, lz) -> Shapes.block()));
    /** 1 x 2 m panel (one block long, two high): fills an odd gap in a run of 2 x 2 m panels, e.g. between two towers. */
    public static final Block TRUSS_H2_L1 = block("truss_h2_l1", truss(1, 2, 1, Placement.EXTEND,
        (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_V2_H2 = block("truss_v2_h2", truss(1, 2, 1, Placement.LOOK,
        (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_CORNER_3 = block("truss_corner_3", truss(1, 1, 1, Placement.CORNER3,
        (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_CORNER_3_H2 = block("truss_corner_3_h2", truss(1, 2, 1, Placement.CORNER3,
        (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_CORNER_6 = block("truss_corner_6", truss(1, 1, 1, Placement.LOOK,
        (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_CORNER_6_H2 = block("truss_corner_6_h2", truss(1, 2, 1, Placement.LOOK,
        (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_BASE_V2 = block("truss_base_v2", truss(1, 1, 1, Placement.LOOK,
        (lx, ly, lz) -> Shapes.block()));

    // ---- Black versions of every truss (same shapes, same placement, same texture atlas recoloured black; crafted with a black dye) ----
    public static final Block TRUSS_H1_L1_NOIR = block("truss_h1_l1_noir", truss(1, 1, 1, Placement.EXTEND, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_H1_L2_NOIR = block("truss_h1_l2_noir", truss(2, 1, 1, Placement.EXTEND, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_H2_L2_NOIR = block("truss_h2_l2_noir", truss(2, 2, 1, Placement.EXTEND, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_H2_L1_NOIR = block("truss_h2_l1_noir", truss(1, 2, 1, Placement.EXTEND, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_V2_H2_NOIR = block("truss_v2_h2_noir", truss(1, 2, 1, Placement.LOOK, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_CORNER_3_NOIR = block("truss_corner_3_noir", truss(1, 1, 1, Placement.CORNER3, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_CORNER_3_H2_NOIR = block("truss_corner_3_h2_noir", truss(1, 2, 1, Placement.CORNER3, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_CORNER_6_NOIR = block("truss_corner_6_noir", truss(1, 1, 1, Placement.LOOK, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_CORNER_6_H2_NOIR = block("truss_corner_6_h2_noir", truss(1, 2, 1, Placement.LOOK, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_BASE_V2_NOIR = block("truss_base_v2_noir", truss(1, 1, 1, Placement.LOOK, (lx, ly, lz) -> Shapes.block()));

    // ---- Small 1 x 2 truss (10 px section, tubes 2 px) for the chain hoist, aluminium and black. Plugs into any truss: flanges on the cell boundary ----
    private static final net.minecraft.world.phys.shapes.VoxelShape PETIT_SHAPE = Block.box(0, 2, 2, 16, 14, 14);
    /** 1.1.0-aurex.2: BoxTruss 60 of 1 m (aluminium / black), the same section as the 2 m one. */
    public static final Block TRUSS_H1_L1_PETIT = block("truss_h1_l1_petit", truss(1, 1, 1, Placement.EXTEND, (lx, ly, lz) -> PETIT_SHAPE));
    public static final Block TRUSS_H1_L1_PETIT_NOIR = block("truss_h1_l1_petit_noir", truss(1, 1, 1, Placement.EXTEND, (lx, ly, lz) -> PETIT_SHAPE));
    public static final Block TRUSS_H1_L2_PETIT = block("truss_h1_l2_petit", truss(2, 1, 1, Placement.EXTEND, (lx, ly, lz) -> PETIT_SHAPE));
    public static final Block TRUSS_H1_L2_PETIT_NOIR = block("truss_h1_l2_petit_noir", truss(2, 1, 1, Placement.EXTEND, (lx, ly, lz) -> PETIT_SHAPE));

    // ---- X30D triangular truss (1 x 2, runs along x like the others) and the 2 x 2 panel with an X30D stub, aluminium and black ----
    private static final net.minecraft.world.phys.shapes.VoxelShape X30D_SHAPE = Block.box(0, 2, 1.5, 16, 14, 14.5);
    public static final Block TRUSS_X30D_L1 = block("truss_x30d_l1", truss(1, 1, 1, Placement.EXTEND, (lx, ly, lz) -> X30D_SHAPE));
    public static final Block TRUSS_X30D_L1_NOIR = block("truss_x30d_l1_noir", truss(1, 1, 1, Placement.EXTEND, (lx, ly, lz) -> X30D_SHAPE));
    public static final Block TRUSS_X30D_L2 = block("truss_x30d_l2", truss(2, 1, 1, Placement.EXTEND, (lx, ly, lz) -> X30D_SHAPE));
    public static final Block TRUSS_X30D_L2_NOIR = block("truss_x30d_l2_noir", truss(2, 1, 1, Placement.EXTEND, (lx, ly, lz) -> X30D_SHAPE));
    public static final Block TRUSS_H2_L2_X30D = block("truss_h2_l2_x30d", truss(2, 2, 1, Placement.EXTEND, (lx, ly, lz) -> Shapes.block()));
    public static final Block TRUSS_H2_L2_X30D_NOIR = block("truss_h2_l2_x30d_noir", truss(2, 2, 1, Placement.EXTEND, (lx, ly, lz) -> Shapes.block()));

    // ---- Motorised truss towers: 4 heights x 4 joints. The joint slides along the mast and lifts what hangs on it ----
    public static final List<MotorTowerBlock> TOWERS = new ArrayList<>();

    static {
        for (int height : new int[] {10, 14, 18, 22}) {
            for (MotorTowerBlock.Carriage carriage : MotorTowerBlock.Carriage.values()) {
                MotorTowerBlock tower = new MotorTowerBlock(metal().noOcclusion(), height, carriage);
                block("motor_tower_" + height + "_" + carriage.id, tower);
                TOWERS.add(tower);
                MotorTowerBlock black = new MotorTowerBlock(metal().noOcclusion(), height, carriage, true);   // black version
                block("motor_tower_" + height + "_" + carriage.id + "_noir", black);
                TOWERS.add(black);
            }
        }
    }

    // ---- Trusses placed at an angle (with the angle tool): the cell that holds them, drawn by a block entity renderer ----
    public static final Block ANGLED_HOLDER = blockNoItem("angled_holder", new AngledHolderBlock(metal().noOcclusion().noLootTable()));
    public static final BlockEntityType<AngledHolderBlockEntity> ANGLED_HOLDER_ENTITY = blockEntity("angled_holder",
        BlockEntityType.Builder.of(AngledHolderBlockEntity::new, ANGLED_HOLDER).build(null));

    // ---- FOH ("regie") kit from 1.3.9: scaffold posts, nodes, ledgers, braced panels, lattice girders, guard rail bay, flight case ----
    // 1 cell = 16 px, tube lines through the middle of the cell. The lattice girders have the tube lines of the small truss
    // (4 / 12 px), so curtain rails and tarps go on them exactly like on a truss (see TrussGap).
    private static final VoxelShape REGIE_POST = Block.box(6.4, 0, 6.4, 9.6, 16, 9.6);
    private static final VoxelShape REGIE_BASE = Block.box(5.5, 0, 5.5, 10.5, 0.5, 10.5);
    private static final VoxelShape REGIE_GIRDER = Block.box(0, 3, 3, 16, 13, 13);
    public static final Block REGIE_POTEAU_H1 = block("regie_poteau_h1", regie(1, 1, 1, Placement.LOOK, false, (lx, ly, lz) -> REGIE_POST));
    public static final Block REGIE_POTEAU_PIED_H1 = block("regie_poteau_pied_h1", regie(1, 1, 1, Placement.LOOK, false,
        (lx, ly, lz) -> Shapes.or(REGIE_POST, REGIE_BASE)));
    public static final Block REGIE_NOEUD_ANGLE = block("regie_noeud_angle", regie(1, 1, 1, Placement.LOOK, false, (lx, ly, lz) -> regieNode(true, false, true, false)));
    public static final Block REGIE_NOEUD_T = block("regie_noeud_t", regie(1, 1, 1, Placement.LOOK, false, (lx, ly, lz) -> regieNode(true, true, true, false)));
    public static final Block REGIE_NOEUD_CROIX = block("regie_noeud_croix", regie(1, 1, 1, Placement.LOOK, false, (lx, ly, lz) -> regieNode(true, true, true, true)));
    public static final Block REGIE_LISSE_L1 = block("regie_lisse_l1", regie(1, 1, 1, Placement.EXTEND, true, (lx, ly, lz) -> Block.box(-2, 7.2, 7.2, 18, 8.8, 8.8)));
    /** Guard rail bay: 1 cell between two posts that are 2 cells apart. */
    public static final Block REGIE_BARRIERE_L1 = block("regie_barriere_l1", regie(1, 1, 1, Placement.EXTEND, true, (lx, ly, lz) -> Block.box(0, 0, 7.2, 16, 16, 8.8)));
    public static final Block REGIE_PANNEAU_L2H1 = block("regie_panneau_l2h1", regie(2, 1, 1, Placement.EXTEND, true, (lx, ly, lz) -> regiePanel(lx)));
    public static final Block REGIE_POUTRE_TREILLIS_L1 = block("regie_poutre_treillis_l1", regie(1, 1, 1, Placement.EXTEND, true, (lx, ly, lz) -> REGIE_GIRDER));
    public static final Block REGIE_POUTRE_TREILLIS_L2 = block("regie_poutre_treillis_l2", regie(2, 1, 1, Placement.EXTEND, true, (lx, ly, lz) -> REGIE_GIRDER));
    public static final Block REGIE_FLIGHT_CASE = block("regie_flight_case", regie(1, 1, 1, Placement.LOOK, false, (lx, ly, lz) -> Block.box(0.2, 0, 2.7, 15.8, 10, 13.3)));

    // ---- Circular truss (CSC RingTruss): rigging point block, ring drawn around it ----
    public static final Block RING_TRUSS = block("ring_truss", new com.concertstage.block.ring.RingTrussBlock(metal().noOcclusion()));
    /** 1.3.0 Dynamic Truss: curved trusses (same block entity as the ring: cells, lifts, fixtures). */
    public static final Block CURVED_TRITRUSS = block("curved_tritruss", new com.concertstage.block.ring.CurvedTrussBlock(metal().noOcclusion(), true));
    public static final Block CURVED_QUADTRUSS = block("curved_quadtruss", new com.concertstage.block.ring.CurvedTrussBlock(metal().noOcclusion(), false));
    public static final BlockEntityType<com.concertstage.block.ring.RingTrussBlockEntity> RING_TRUSS_ENTITY = blockEntity("ring_truss",
        BlockEntityType.Builder.of(com.concertstage.block.ring.RingTrussBlockEntity::new, RING_TRUSS, CURVED_TRITRUSS, CURVED_QUADTRUSS).build(null));
    public static final Block RING_TRUSS_CELL = blockNoItem("ring_truss_cell", new com.concertstage.block.ring.RingTrussCellBlock(metal().noOcclusion().dynamicShape().noLootTable()));
    public static final BlockEntityType<com.concertstage.block.ring.RingTrussCellBlockEntity> RING_TRUSS_CELL_ENTITY = blockEntity("ring_truss_cell",
        BlockEntityType.Builder.of(com.concertstage.block.ring.RingTrussCellBlockEntity::new, RING_TRUSS_CELL).build(null));
    // ---- Roof support frame (CSC RoofTruss): arc / gable / single slope in 1-block box truss, on the grid trusses; same collision cells as the ring ----
    public static final Block ROOF_TRUSS = block("roof_truss", new com.concertstage.block.roof.RoofTrussBlock(metal().noOcclusion()));
    /** Roof kits: the foot sets itself up as a whole roof (frames, ridge bar, purlins, sheet). CSC SkyRoof 16 x 12 m; CSC TitanRoof 18 x 12 m, heavier. */
    public static final Block ROOF_TRUSS_ST = block("roof_truss_st", new com.concertstage.block.roof.RoofTrussBlock(metal().noOcclusion(),
        com.concertstage.block.roof.RoofTrussBlockEntity.PRESET_ST));
    public static final Block ROOF_TRUSS_CT = block("roof_truss_ct", new com.concertstage.block.roof.RoofTrussBlock(metal().noOcclusion(),
        com.concertstage.block.roof.RoofTrussBlockEntity.PRESET_CT));
    public static final BlockEntityType<com.concertstage.block.roof.RoofTrussBlockEntity> ROOF_TRUSS_ENTITY = blockEntity("roof_truss",
        BlockEntityType.Builder.of(com.concertstage.block.roof.RoofTrussBlockEntity::new, ROOF_TRUSS, ROOF_TRUSS_ST, ROOF_TRUSS_CT).build(null));
    /** Any block set on a stage floor that is not at a block boundary, lowered onto the floor (see OffsetPlacement). */
    public static final Block OFFSET_BLOCK = blockNoItem("offset_block", new com.concertstage.block.offset.OffsetBlock(BlockBehaviour.Properties.of()
        .strength(1.0F).noOcclusion().dynamicShape()));
    public static final BlockEntityType<com.concertstage.block.offset.OffsetBlockEntity> OFFSET_BLOCK_ENTITY = blockEntity("offset_block",
        BlockEntityType.Builder.of(com.concertstage.block.offset.OffsetBlockEntity::new, OFFSET_BLOCK).build(null));
    /** FOH tarp roof (1.18.0): set on any FOH element, it covers the whole FOH structure. */
    public static final Item REGIE_ROOF_TARP = item("regie_roof_tarp", new com.concertstage.item.RegieRoofItem(new Item.Properties().stacksTo(16)));
    public static final Block REGIE_ROOF = blockNoItem("regie_roof", new com.concertstage.block.regie.RegieRoofBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.COLOR_BLACK).instabreak().sound(SoundType.WOOL).noOcclusion().noCollission()));   // aurex.2: one hit
    public static final BlockEntityType<com.concertstage.block.regie.RegieRoofBlockEntity> REGIE_ROOF_ENTITY = blockEntity("regie_roof",
        BlockEntityType.Builder.of(com.concertstage.block.regie.RegieRoofBlockEntity::new, REGIE_ROOF).build(null));
    public static final Item PLATFORM_ANGLE_TOOL = item("platform_angle_tool", new com.concertstage.item.PlatformAngleToolItem(new Item.Properties().stacksTo(1)));

    // ---- Crowd barriers: CSC CrowdGuard 120 (front of stage, 1 m sections: audience footplate, perforated panel, round top rail at 1.2 m,
    //      pit step) and CSC CityLine 200 (2 m galvanised crowd control barrier). Audience side towards the player who places them. ----
    public static final Block BARRIER_CROWDGUARD_1M = block("barrier_crowdguard_1m", barrier(1, 2, 2, (lx, ly, lz) -> crowdGuard(ly, lz, true, false)));
    public static final Block BARRIER_CROWDGUARD_GATE = block("barrier_crowdguard_gate", barrier(1, 2, 2, (lx, ly, lz) -> crowdGuard(ly, lz, false, false)));
    public static final Block BARRIER_CROWDGUARD_END = block("barrier_crowdguard_end", barrier(1, 2, 2, (lx, ly, lz) -> crowdGuard(ly, lz, true, true)));
    public static final Block BARRIER_CITYLINE_2M = block("barrier_cityline_2m", barrier(2, 2, 1,
        (lx, ly, lz) -> ly == 0 ? Shapes.or(Block.box(0, 0, 7, 16, 16, 9), Block.box(lx == 0 ? 3.4 : 11.4, 0, 2, lx == 0 ? 4.6 : 12.6, 0.6, 14)) : Block.box(0, 0, 7, 16, 8, 9)));

    // ---- Prolyte crash barriers (aluminium, 1 m sections, slatted footplate, perforated panel with the "A" bracing, 1.24 m) ----
    public static final Block BARRIER_ALUGUARD_1M = block("barrier_aluguard_1m", barrier(1, 2, 2, (lx, ly, lz) -> cell(lx, ly, lz,
        new double[]{0, 0, 0, 16, 0.8, 19.6}, new double[]{0, 0, 19.6, 16, 24, 21}, new double[]{0, 0, 26.8, 16, 0.9, 28.4})));
    /** aurex.2: 90° corner (2 x 2 cells) closing a square of AluGuard lines, public outside (tools/make_aluguard_corner.py). */
    public static final Block BARRIER_ALUGUARD_CORNER = block("barrier_aluguard_corner", barrier(2, 2, 2, (lx, ly, lz) -> cell(lx, ly, lz,
        new double[]{0, 0, 0, 32, 0.8, 19.6}, new double[]{12.4, 0, 19.6, 32, 0.8, 32},
        new double[]{0, 0, 19.6, 12.4, 24, 21}, new double[]{11, 0, 19.6, 12.4, 24, 32},
        new double[]{0, 0, 26.8, 11, 0.9, 28.6})));

    // ---- Festival stage stairs: 10 risers of 20 cm up to the 2.0 m deck of the pre-assembled festival stage (1 x 3 x 3 blocks).
    //      Clicked on the side of a festival stage, they are set against it, their top step at its rim. ----
    public static final Block FESTIVAL_STAIRS_H220 = block("festival_stairs_h220", new com.concertstage.block.FestivalStairsBlock(metal().noOcclusion()));

    // ---- CSC SiteFence: temporary fence panel 3 blocks x 2 (~3.5 m x 2 m) on rubber feet; the black fence screen fits on it ----
    public static final Item FENCE_SCREEN = item("fence_screen_black", new com.concertstage.item.FenceScreenItem(new Item.Properties()));
    public static final Block FENCE_SITE = block("fence_site_3m", new com.concertstage.block.FenceScreenBlock(metal().noOcclusion(), 3, 2, 1, false, StageContent::fenceShape));
    public static final Block FENCE_SITE_SCREEN = block("fence_site_3m_screen", new com.concertstage.block.FenceScreenBlock(metal().noOcclusion(), 3, 2, 1, true, StageContent::fenceShape));

    // ---- Stage tarp (PVC bâche): the item is the first top piece; the tarp is built from three blocks ----
    public static final Block STAGE_TARP_RAIL = block("stage_tarp_rail", new StageTarpRailBlock(tarp()));
    public static final Block STAGE_TARP_TOP = blockNoItem("stage_tarp_top", new StageTarpTopBlock(tarp().noLootTable()));
    public static final Block STAGE_TARP_CLOTH = blockNoItem("stage_tarp_cloth", new StageTarpClothBlock(tarp().noLootTable()));
    /** Compact FOH tarp (black, on the tube line): ties to ledgers, nodes, guard rails, girders, posts, panels, flight cases. */
    public static final Block REGIE_TARP_RAIL = block("regie_tarp_rail", new StageTarpRailBlock(tarp(), true));
    public static final Block REGIE_TARP_TOP = blockNoItem("regie_tarp_top", new StageTarpTopBlock(tarp().noLootTable()));
    public static final Block REGIE_TARP_CLOTH = blockNoItem("regie_tarp_cloth", new StageTarpClothBlock(tarp().noLootTable()));
    public static final BlockEntityType<StageTarpRailBlockEntity> STAGE_TARP_RAIL_ENTITY = blockEntity("stage_tarp_rail",
        BlockEntityType.Builder.of(StageTarpRailBlockEntity::new, STAGE_TARP_RAIL, REGIE_TARP_RAIL).build(null));

    // ---- Curtains ----
    public static final Block CURTAIN_RAIL = block("curtain_rail", new CurtainRailBlock(fabric().strength(2.5F, 6.0F).sound(SoundType.METAL)));
    public static final Block CURTAIN_CLOTH = blockNoItem("curtain_cloth", new CurtainClothBlock(fabric().noLootTable()));
    public static final BlockEntityType<CurtainRailBlockEntity> CURTAIN_RAIL_ENTITY = blockEntity("curtain_rail",
        BlockEntityType.Builder.of(CurtainRailBlockEntity::new, CURTAIN_RAIL).build(null));
    /** The chain hoist is a one cell motor, handled by the lift code like a tower: it is in {@link #TOWERS} for the block entity type. */
    public static final HoistBlock CHAIN_HOIST = block("palan_chaine", new HoistBlock(metal().noOcclusion()));
    static { TOWERS.add(CHAIN_HOIST); }

    public static final BlockEntityType<TowerBlockEntity> TOWER_ENTITY = blockEntity("motor_tower",
        BlockEntityType.Builder.of(TowerBlockEntity::new, TOWERS.toArray(new Block[0])).build(null));

    /** The part of a stage rig that is travelling up or down with the towers. */
    public static final EntityType<LiftStructureEntity> LIFT_STRUCTURE = entityType("lift_structure",
        EntityType.Builder.<LiftStructureEntity>of(LiftStructureEntity::new, MobCategory.MISC)
            .sized(0.5F, 0.5F).clientTrackingRange(12).updateInterval(Integer.MAX_VALUE).fireImmune().noSummon()
            .build("lift_structure"));

    // ---- Items ----
    public static final Item CURTAIN_TOOL = item("curtain_tool", new CurtainToolItem(new Item.Properties().stacksTo(1)));
    public static final Item LIFT_REMOTE = item("lift_remote", new RemoteItem(new Item.Properties().stacksTo(1)));
    public static final Item ANGLE_TOOL = item("angle_tool", new AngleToolItem(new Item.Properties().stacksTo(1)));
    public static final Item RING_TOOL = item("ring_truss_tool", new com.concertstage.item.RingToolItem(new Item.Properties().stacksTo(1)));
    public static final Item ROOF_TOOL = item("roof_truss_tool", new com.concertstage.item.RoofToolItem(new Item.Properties().stacksTo(1)));
    /** CSC Structure Manager (1.0.2): selects a whole stage roof and removes it in one action, after a confirmation. */
    public static final Item STRUCTURE_TOOL = item("structure_manager_tool", new com.concertstage.item.StructureManagerItem(new Item.Properties().stacksTo(1)));

    // ---- Sound (1.1.0): CSC Veyra / Noryx speaker arrays, CSC Audio Console, Array Tool and Audio Link Tool ----
    public static final Map<String, net.minecraft.sounds.SoundEvent> SOUND_EVENTS = new LinkedHashMap<>();
    /** Streamed sound of the speaker arrays (its file is a silent placeholder: the PCM comes from the console's track). */
    public static final net.minecraft.sounds.SoundEvent SPEAKER_STREAM = sound("speaker_stream");
    public static final Block SPEAKER = blockNoItem("csc_speaker", new com.concertstage.audio.SpeakerBlock(metal().noOcclusion().dynamicShape().noLootTable()));
    public static final BlockEntityType<com.concertstage.audio.SpeakerBlockEntity> SPEAKER_ENTITY = blockEntity("csc_speaker",
        BlockEntityType.Builder.of(com.concertstage.audio.SpeakerBlockEntity::new, SPEAKER).build(null));
    private static final Map<String, Item> SPEAKER_ITEMS = new LinkedHashMap<>();
    static {
        for (String id : com.concertstage.audio.SpeakerModels.ids())
            SPEAKER_ITEMS.put(id, item(id, new com.concertstage.audio.SpeakerItem(id, new Item.Properties().stacksTo(16))));
    }
    private static final Map<String, Item> FRAME_ITEMS = new LinkedHashMap<>();
    static {
        for (String id : com.concertstage.audio.SpeakerFrames.ALL.keySet())
            FRAME_ITEMS.put(id, item(id, new com.concertstage.audio.SpeakerFrameItem(id, new Item.Properties().stacksTo(16))));
    }
    public static Item frameItem(String id) { return FRAME_ITEMS.get(id); }
    public static final Block AUDIO_CONSOLE = block("csc_audio_console", new com.concertstage.audio.AudioConsoleBlock(metal().noOcclusion()));
    public static final BlockEntityType<com.concertstage.audio.AudioConsoleBlockEntity> AUDIO_CONSOLE_ENTITY = blockEntity("csc_audio_console",
        BlockEntityType.Builder.of(com.concertstage.audio.AudioConsoleBlockEntity::new, AUDIO_CONSOLE).build(null));
    public static final Item DELAY_PLANNER = item("csc_delay_planner",new com.concertstage.audio.DelayPlannerItem(new Item.Properties().stacksTo(1)));
    public static final Item ARRAY_TOOL = item("csc_array_tool", new com.concertstage.audio.ArrayToolItem(new Item.Properties().stacksTo(1)));
    public static final Item LINK_TOOL = item("csc_audio_link_tool", new com.concertstage.audio.LinkToolItem(new Item.Properties().stacksTo(1)));

    // ---- Lighting (1.2.0 Lightshow Revolution): CSC fixtures, the CSC LUMICORE X1 desk and the patch tool ----
    public static final Block VORTEX_X1 = block("csc_vortex_x1", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_vortex_x1", 0.21, 0.62));
    public static final Block LUMORA_W7 = block("csc_lumora_w7", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_lumora_w7", 0.2, 0.48));
    public static final Block LUMORA_W19 = block("csc_lumora_w19", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_lumora_w19", 0.26, 0.6));
    public static final Block PULSESTRIKE_M = block("csc_pulsestrike_m", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_pulsestrike_m", 0.24, 0.34));
    public static final Block PHANTOM_S1 = block("csc_phantom_s1", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_phantom_s1", 0.2495, 0.6750));
    public static final Block PROFILE_P1 = block("csc_profile_p1", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_profile_p1", 0.2310, 0.5900));
    public static final Block RAZOR_B1 = block("csc_razor_b1", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_razor_b1", 0.2130, 0.4700));
    public static final Block FLASHCORE_FX = block("csc_flashcore_fx", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_flashcore_fx", 0.2170, 0.2580));
    public static final Block PIXELBAR_B16 = block("csc_pixelbar_b16", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_pixelbar_b16", 0.5100, 0.2700));
    public static final Block PIXELTUBE_T1 = block("csc_pixeltube_t1", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_pixeltube_t1", 0.5350, 0.1480));
    public static final Block BLINDER_X2 = block("csc_blinder_x2", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_blinder_x2", 0.2535, 0.2920));
    public static final Block BLINDER_X4 = block("csc_blinder_x4", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_blinder_x4", 0.2560, 0.4350));
    public static final Block BLINDER_X8 = block("csc_blinder_x8", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_blinder_x8", 0.5110, 0.5350));
    public static final Block FOLLOWSPOT_F1 = block("csc_followspot_f1", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_followspot_f1", 0.5170, 1.6850));
    public static final Block PIXELWASH_W37 = block("csc_pixelwash_w37", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_pixelwash_w37", 0.2695, 0.6360));
    public static final Block LASERCORE_L1 = block("csc_lasercore_l1", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_lasercore_l1", 0.2190, 0.2560));
    public static final Block FLOODLIGHT_F8 = block("csc_floodlight_f8", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_floodlight_f8", 0.3930, 0.4250));
    // 1.2.1: the conventional halogen fixtures (one dimmer channel, manual tilt, gel)
    public static final Block PAR_P64 = block("csc_par_p64", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_par_p64", 0.1720, 0.3050));
    public static final Block FRESNEL_F1 = block("csc_fresnel_f1", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_fresnel_f1", 0.1880, 0.3540));
    public static final Block PC_C1 = block("csc_pc_c1", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_pc_c1", 0.1720, 0.3160));
    public static final Block ELLIPSOIDAL_E1 = block("csc_ellipsoidal_e1", new com.concertstage.light.FixtureBlock(metal().noOcclusion(), "csc_ellipsoidal_e1", 0.1770, 0.3420));
    public static final BlockEntityType<com.concertstage.light.FixtureBlockEntity> FIXTURE_ENTITY = blockEntity("csc_fixture",
        BlockEntityType.Builder.of(com.concertstage.light.FixtureBlockEntity::new, VORTEX_X1, LUMORA_W7, LUMORA_W19, PULSESTRIKE_M, PHANTOM_S1, PROFILE_P1, RAZOR_B1, FLASHCORE_FX, PIXELBAR_B16, PIXELTUBE_T1, BLINDER_X2, BLINDER_X4, BLINDER_X8, FOLLOWSPOT_F1, PIXELWASH_W37, LASERCORE_L1, FLOODLIGHT_F8, PAR_P64, FRESNEL_F1, PC_C1, ELLIPSOIDAL_E1).build(null));
    public static final Block LUMICORE = block("csc_lumicore_x1", new com.concertstage.light.LumicoreBlock(metal().noOcclusion()));
    // rev. 1.3.0-2: the Art-Net node is a desk of its own (same block entity type as the LUMICORE X1) that starts in External mode
    public static final Block ARTNET_CONSOLE = block("csc_artnet_node", new com.concertstage.light.ArtNetConsoleBlock(metal().noOcclusion()));
    public static final BlockEntityType<com.concertstage.light.LumicoreBlockEntity> LUMICORE_ENTITY = blockEntity("csc_lumicore_x1",
        BlockEntityType.Builder.of(com.concertstage.light.LumicoreBlockEntity::new, LUMICORE, ARTNET_CONSOLE).build(null));
    public static final Item CONFIG_CARD = item("csc_config_card", new com.concertstage.light.ConfigCardItem(new Item.Properties().stacksTo(1)));
    // rev. 1.3.0-3: points the selected fixtures at the spot clicked (or gives the DMX values for an external console)
    public static final Item AIM_TOOL = item("csc_focus_tool", new com.concertstage.light.AimToolItem(new Item.Properties().stacksTo(1)));
    // ---------------------------------------------------------------- CSC 1.3.0 LiveVision
    public static final Block LED_PANEL_P1 = block("csc_led_panel_p1", new com.concertstage.vision.LedPanelBlock(metal().noOcclusion().lightLevel(s -> 0)));
    public static final BlockEntityType<com.concertstage.vision.LedPanelBlockEntity> LED_PANEL_ENTITY = blockEntity("csc_led_panel_p1",
        BlockEntityType.Builder.of(com.concertstage.vision.LedPanelBlockEntity::new, LED_PANEL_P1).build(null));
    public static final Item LED_HANG_BAR = item("csc_led_hang_bar", new com.concertstage.vision.LedAccessoryItem(new Item.Properties(), 1, "csc_led_hang_bar"));
    public static final Item LED_GROUND_BASE = item("csc_led_ground_base", new com.concertstage.vision.LedAccessoryItem(new Item.Properties(), 2, "csc_led_ground_base"));
    public static final Item LED_WALL_MOUNT = item("csc_led_wall_mount", new com.concertstage.vision.LedAccessoryItem(new Item.Properties(), 4, "csc_led_wall_mount"));
    public static final Block VISION_PROCESSOR_VP1 = block("csc_vision_processor_vp1", new com.concertstage.vision.VisionProcessorBlock(metal().noOcclusion()));
    public static final BlockEntityType<com.concertstage.vision.VisionProcessorBlockEntity> VISION_PROCESSOR_ENTITY = blockEntity("csc_vision_processor_vp1",
        BlockEntityType.Builder.of(com.concertstage.vision.VisionProcessorBlockEntity::new, VISION_PROCESSOR_VP1).build(null));
    public static final Block CAM_PTZ_P1 = block("csc_cam_ptz_p1", new com.concertstage.vision.CameraBlock(metal().noOcclusion(), com.concertstage.vision.CameraKind.PTZ,
        new double[] {6.3, 0, 6.5, 9.7, 3.8, 9.9}, new double[] {6.3, 12.2, 6.5, 9.7, 16, 9.9}));
    public static final Block BROADCAST_B1 = block("csc_broadcast_b1", new com.concertstage.vision.CameraBlock(metal().noOcclusion(), com.concertstage.vision.CameraKind.BROADCAST,
        new double[] {2, 0, 2, 14, 16, 14}, null));
    public static final Block CRANE_C1 = block("csc_crane_c1", new com.concertstage.vision.CameraBlock(metal().noOcclusion(), com.concertstage.vision.CameraKind.CRANE,
        new double[] {0, 0, 0, 16, 16, 16}, null));
    public static final Block REMOTE_R1 = block("csc_remote_r1", new com.concertstage.vision.CameraBlock(metal().noOcclusion(), com.concertstage.vision.CameraKind.REMOTE,
        new double[] {6.5, 0, 5.5, 9.5, 4.9, 10.1}, new double[] {6.5, 11.1, 5.5, 9.5, 16, 10.1}));
    public static final BlockEntityType<com.concertstage.vision.CameraBlockEntity> CAMERA_ENTITY = blockEntity("csc_camera",
        BlockEntityType.Builder.of(com.concertstage.vision.CameraBlockEntity::new, CAM_PTZ_P1, BROADCAST_B1, CRANE_C1, REMOTE_R1).build(null));

    public static final Item LIGHT_PATCH_TOOL = item("csc_light_patch_tool", new com.concertstage.light.LightPatchToolItem(new Item.Properties().stacksTo(1)));
    /** Rev. 24: tilts the fixtures without tilt motor (blinders, floodlights, bars) on their bracket. */
    public static final Item FIXTURE_ANGLE_TOOL = item("csc_fixture_angle_tool", new com.concertstage.light.FixtureAngleToolItem(new Item.Properties().stacksTo(1)));

    /** Item of a speaker model ("csc_veyra_a1"...); the Veyra A1 for an unknown id. */
    public static Item speakerItem(String model) {
        Item i = SPEAKER_ITEMS.get(model);
        return i != null ? i : SPEAKER_ITEMS.get("csc_veyra_a1");
    }

    public static java.util.Collection<Item> speakerItems() {
        return SPEAKER_ITEMS.values();
    }

    private static net.minecraft.sounds.SoundEvent sound(String name) {
        net.minecraft.sounds.SoundEvent e = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(com.concertstage.ConcertStage.MOD_ID, name));
        SOUND_EVENTS.put(name, e);
        return e;
    }

    private StageContent() {}

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5F, 6.0F).sound(SoundType.METAL);
    }

    /** PVC tarp: walk-through like the curtain, a little harder to break. */
    private static BlockBehaviour.Properties tarp() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.WOOL)
            .noOcclusion().noCollission();
    }

    /** FOH kit piece of {@code sx x sy x sz} cells (main cell = local 0, 0, 0). */
    private static MultiPartBlock regie(int sx, int sy, int sz, Placement placement, boolean chain, MultiPartBlock.ShapeProvider shape) {
        return new com.concertstage.block.RegieBlock(metal().noOcclusion(), sx, sy, sz, placement, chain, shape);
    }

    /** Post with a ledger from its centre to the edge of the cell in each of the given directions (authored: +x east, +z south). */
    private static VoxelShape regieNode(boolean px, boolean nx, boolean pz, boolean nz) {
        VoxelShape s = REGIE_POST;
        if (px) s = Shapes.or(s, Block.box(8, 7.2, 7.2, 16, 8.8, 8.8));
        if (nx) s = Shapes.or(s, Block.box(0, 7.2, 7.2, 8, 8.8, 8.8));
        if (pz) s = Shapes.or(s, Block.box(7.2, 7.2, 8, 8.8, 8.8, 16));
        if (nz) s = Shapes.or(s, Block.box(7.2, 7.2, 0, 8.8, 8.8, 8));
        return s;
    }

    /** Braced panel, 2 cells wide: a post in the middle of each cell, ledgers between them (low, middle, high). */
    private static VoxelShape regiePanel(int lx) {
        double a = lx == 0 ? 8 : 0, b = lx == 0 ? 16 : 8;
        return Shapes.or(REGIE_POST, Block.box(a, 7.2, 7.4, b, 8.8, 8.6), Block.box(a, 0.3, 7.4, b, 1.5, 8.6), Block.box(a, 14.5, 7.4, b, 15.7, 8.6));
    }

    private static MultiPartBlock barrier(int sx, int sy, int sz, MultiPartBlock.ShapeProvider shape) {
        return new MultiPartBlock(metal().noOcclusion(), sx, sy, sz, 0, 0, 0, Placement.FRONT, false, shape);
    }

    /** Collision of one cell of a piece drawn in footprint pixels (16 px per cell, boxes {x0, y0, z0, x1, y1, z1}): the boxes clipped to the cell. */
    private static VoxelShape cell(int lx, int ly, int lz, double[]... boxes) {
        VoxelShape s = Shapes.empty();
        double ox = lx * 16, oy = ly * 16, oz = lz * 16;
        for (double[] b : boxes) {
            double x0 = Math.max(b[0], ox), y0 = Math.max(b[1], oy), z0 = Math.max(b[2], oz);
            double x1 = Math.min(b[3], ox + 16), y1 = Math.min(b[4], oy + 16), z1 = Math.min(b[5], oz + 16);
            if (x0 < x1 && y0 < y1 && z0 < z1) s = Shapes.or(s, Block.box(x0 - ox, y0 - oy, z0 - oz, x1 - ox, y1 - oy, z1 - oz));
        }
        return s;
    }

    /** Fence panel: mesh 2 blocks high, rubber feet at both ends. */
    private static VoxelShape fenceShape(int lx, int ly, int lz) {
        return cell(lx, ly, lz, new double[]{0, 0, 7.2, 48, 32, 8.8}, new double[]{0, 0, 3, 4.6, 2.4, 13}, new double[]{43.4, 0, 3, 48, 2.4, 13});
    }

    /** Front-of-stage barrier cell: footplate (audience side, lz 0 and the start of lz 1), panel 1.5 m high against climbing, pit step. */
    private static VoxelShape crowdGuard(int ly, int lz, boolean panel, boolean end) {
        if (lz == 0) {
            if (ly != 0) return Shapes.empty();
            VoxelShape s = Block.box(0, 0, 0, 16, 0.6, 16);
            return end ? Shapes.or(s, Block.box(0, 0, 1, 1.2, 8.4, 16)) : s;
        }
        VoxelShape frame = Shapes.or(Block.box(0, 0, 3.5, 1, ly == 0 ? 16 : 8, 5), Block.box(15, 0, 3.5, 16, ly == 0 ? 16 : 8, 5));
        if (ly == 0) {
            VoxelShape s = Shapes.or(Block.box(0, 0, 0, 16, 0.6, 4.5), Block.box(0.6, 6, 5.5, 15.4, 7, 10.5), frame);
            if (panel) s = Shapes.or(s, Block.box(0, 0, 3.5, 16, 16, 5));
            if (end) s = Shapes.or(s, Block.box(0, 0, 0, 1.2, 8.4, 3.5));
            return s;
        }
        return panel ? Block.box(0, 0, 3, 16, 8, 5.5) : frame;   // rail + anti-climb height (1.5 m)
    }

    /** Soft, walk-through cloth. */
    private static BlockBehaviour.Properties fabric() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.3F).sound(SoundType.WOOL)
            .noOcclusion().noCollission();
    }

    private static Block[] stageEntityBlocks(){List<Block> blocks=new ArrayList<>(STAGE_EQUIPMENT);blocks.add(STAGE_MOUNT);return blocks.toArray(new Block[0]);}
    private static List<StageEquipmentBlock> createStageEquipment() {
        List<StageEquipmentBlock> blocks = new ArrayList<>();
        for (StageSpec spec : StageSpec.all()) {
            if (spec.internal) continue;
            BlockBehaviour.Properties properties = metal().noOcclusion().dynamicShape();
            StageEquipmentBlock block = "joined".equals(spec.mode) ? new StageEquipmentBlock.Half(properties, spec)
                : "mounted".equals(spec.mode) ? new StageEquipmentBlock.Railing(properties, spec)
                : spec.count() > 16 ? new StageEquipmentBlock.Large(properties, spec)
                : new StageEquipmentBlock(properties, spec);
            block(spec.id, block); blocks.add(block);
        }
        return List.copyOf(blocks);
    }

    private static MultiPartBlock truss(int sx, int sy, int sz, Placement placement, MultiPartBlock.ShapeProvider shape) {
        return new MultiPartBlock(metal().noOcclusion(), sx, sy, sz, 0, 0, 0, placement, true, shape);
    }

    private static <T extends Block> T block(String name, T block) {
        BLOCKS.put(name, block);
        // trusses and towers use an item that can place them at an angle when the angle tool is held; the others are plain block items
        ITEMS.put(name, block instanceof StageEquipmentBlock equipment ? new StageEquipmentItem(equipment, new Item.Properties())
            : block instanceof StageTarpRailBlock rail && rail.isRegie() ? new com.concertstage.item.RegieTarpItem(block, new Item.Properties())
            : "festival_stairs_h220".equals(name) ? new com.concertstage.item.FestivalStairsItem(block, new Item.Properties())
            : block instanceof MultiPartBlock ? new AngleAwareBlockItem(block, new Item.Properties()) : new BlockItem(block, new Item.Properties()));
        return block;
    }

    private static <T extends Block> T blockNoItem(String name, T block) {
        BLOCKS.put(name, block);
        return block;
    }

    private static <T extends BlockEntity> BlockEntityType<T> blockEntity(String name, BlockEntityType<T> type) {
        BLOCK_ENTITIES.put(name, type);
        return type;
    }

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> entityType(String name, EntityType<T> type) {
        ENTITY_TYPES.put(name, type);
        return type;
    }

    private static <T extends Item> T item(String name, T item) {
        ITEMS.put(name, item);
        return item;
    }
}
