package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.ProjectType;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Blueprint;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Materials;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Exact before/after structural joint tests, plus actual production-plan coverage in five biomes. */
final class AuthoredStructuralContactRefinementsSelfTest {
    private static final List<Case> CASES = List.of(
            new Case("mine_headframe_01", "mineHeadframeLandmark", ProjectType.MINE_ENTRANCE,
                    positions(new int[][] {{5, 4, 5}, {9, 4, 5}, {6, 3, 5}, {8, 3, 5},
                            {9, 4, 9}, {6, 3, 9}, {8, 3, 9}})),
            new Case("granary_loft_01", "granaryRaisedBarn", ProjectType.GRANARY,
                    positions(new int[][] {{3, 3, 2}, {9, 3, 2}, {3, 3, 9}, {9, 3, 9}})),
            new Case("house_towercourt_06", "houseTowercourt", ProjectType.HOUSE,
                    List.of(new BlockPos(-1, 8, 15))),
            new Case("guard_bastion_02", "guardBastion", ProjectType.GUARD_POST,
                    List.of(new BlockPos(-1, 9, 2), new BlockPos(15, 9, 2))),
            new Case("granary_windmill_02", "granaryWindmill", ProjectType.GRANARY,
                    List.of(new BlockPos(18, 5, 11))),
            new Case("inn_gallery_01", "innBalcony", ProjectType.INN,
                    List.of(new BlockPos(8, 11, 4))));

    private AuthoredStructuralContactRefinementsSelfTest() {
    }

    static void run() throws ReflectiveOperationException {
        int actualJoints = 0;
        for (BiomeDialect dialect : BiomeDialect.values()) {
            for (Case sample : CASES) {
                Blueprint production = AuthoredVillageStructures.plan(sample.type(), sample.id(), 4,
                        VillageArchitecture.PALETTE_BALANCED, VillageArchitecture.DRESSING_RESTRAINED,
                        VillageArchitecture.Character.RUSTIC, dialect, 0L);
                Materials p = production.materials();
                Method author = AuthoredVillageStructures.class.getDeclaredMethod(
                        sample.author(), Builder.class, Metadata.class, Materials.class);
                author.setAccessible(true);
                for (int revision : new int[] {1, 2, 3, 4}) {
                    Builder stage = new Builder(Set.of());
                    stage.templateRevision = revision;
                    Metadata metadata = metadata(revision);
                    if (revision < 4) {
                        author.invoke(null, stage, metadata, p);
                    } else {
                        // The contact hook follows existing roof/presentation composition;
                        // the untouched raw author is intentionally not the hook's input.
                        // Actual output coverage below independently requires every final joint.
                        for (Cell cell : production.base()) {
                            if (!sample.bearings().contains(position(cell))) {
                                stage.put(cell.phase(), cell.x(), cell.y(), cell.z(), cell.state());
                            }
                        }
                        metadata.reservedAir.addAll(production.metadata().reservedAir());
                        metadata.accessTargets.addAll(production.metadata().accessTargets());
                    }
                    List<Cell> before = stage.values();
                    AuthoredStructuralContactRefinements.refine(stage, metadata, p, sample.id(), List.of());
                    if (revision < 4) {
                        require(before.equals(stage.values()), "Historical structural recipe changed: "
                                + sample.id() + "@" + revision);
                        continue;
                    }
                    require(stage.values().size() == before.size() + sample.bearings().size(),
                            "Exact authored bearings did not all install: " + sample.id() + " / " + dialect
                                    + " added=" + (stage.values().size() - before.size()) + " joints="
                                    + sample.bearings().stream().map(pos -> pos + "=" + stage.cellAt(pos)
                                            + " below=" + stage.cellAt(pos.below())
                                            + " above=" + stage.cellAt(pos.above())).toList());
                    for (Cell old : before) {
                        require(old.equals(stage.cellAt(position(old))),
                                "Bearing changed an existing roof, fixture, floor or route cell: " + old);
                    }
                    List<Cell> once = stage.values();
                    AuthoredStructuralContactRefinements.refine(stage, metadata, p, sample.id(), List.of());
                    require(once.equals(stage.values()), "Structural finishing was not idempotent");
                }
                Map<BlockPos, Cell> complete = new HashMap<>();
                production.base().forEach(c -> complete.put(position(c), c));
                production.stageOne().forEach(c -> complete.put(position(c), c));
                production.stageTwo().forEach(c -> complete.put(position(c), c));
                for (BlockPos pos : sample.bearings()) {
                    Cell joint = complete.get(pos);
                    require(joint != null && joint.phase() == Phase.FRAME,
                            "Production stage hook omitted a bearing: " + sample.id() + " / " + dialect + " " + pos);
                    require(joint.state().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, pos)
                                    || joint.state().getBlock() instanceof StairBlock
                                    && joint.state().getValue(StairBlock.HALF) == Half.TOP
                                    && joint.state().isFaceSturdy(EmptyBlockGetter.INSTANCE, pos, Direction.UP),
                            "Bearing has no solid rendered upper contact: " + joint);
                    require(pos.getY() >= 3 && !production.metadata().reservedAir().contains(pos)
                                    && !production.metadata().accessTargets().contains(pos),
                            "Structural bearing obstructed reserved movement or interaction");
                    actualJoints++;
                }
            }
        }
        courtyardCaps();
        guardedParcel();
        System.out.println("PASS revision-4 structural contacts: " + actualJoints
                + " actual joints across five biomes; original cells/frozen revisions 1-3 retained, "
                + "upper bearings, reserved routes and atomic unknown/occupied support checks");
    }

    private static void courtyardCaps() {
        for (BiomeDialect dialect : BiomeDialect.values()) {
            Blueprint actual = AuthoredVillageStructures.plan(ProjectType.INN, "inn_courtyard_05", 4,
                    VillageArchitecture.PALETTE_BALANCED, VillageArchitecture.DRESSING_RESTRAINED,
                    VillageArchitecture.Character.RUSTIC, dialect);
            Materials p = actual.materials();
            Map<BlockPos, Cell> base = new HashMap<>();
            actual.base().forEach(c -> base.put(position(c), c));
            for (int[] patch : new int[][] {{7, 6}, {7, 14}, {17, 14}}) {
                for (int x = patch[0]; x <= patch[0] + 1; x++) {
                    for (int z = patch[1]; z <= patch[1] + 1; z++) {
                        Cell cap = base.get(new BlockPos(x, 10, z));
                        require(cap != null && cap.phase() == Phase.ROOF
                                        && cap.state().equals(p.roofSlab().defaultBlockState()
                                                .setValue(SlabBlock.TYPE, SlabType.DOUBLE))
                                        && !base.containsKey(new BlockPos(x, 11, z)),
                                "Courtyard crossing cap remains detached: " + dialect + " " + x + "," + z);
                    }
                }
            }
            for (int revision : new int[] {1, 2, 3, 4}) {
                for (boolean occupied : new boolean[] {false, true}) {
                    Builder stage = new Builder(Set.of());
                    stage.templateRevision = revision;
                    Metadata metadata = metadata(revision);
                    stage.put(Phase.ROOF, 7, 9, 6, p.roofStairs().defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.WEST));
                    for (int x = 7; x <= 8; x++) {
                        for (int z = 6; z <= 7; z++) {
                            stage.put(Phase.ROOF, x, 11, z, p.roofSlab().defaultBlockState()
                                    .setValue(SlabBlock.TYPE, SlabType.DOUBLE));
                        }
                    }
                    if (occupied) {
                        stage.put(Phase.FIXTURE, 8, 10, 7, Blocks.CHEST.defaultBlockState());
                    }
                    List<Cell> before = stage.values();
                    AuthoredStructuralContactRefinements.refine(stage, metadata, p, "inn_courtyard_05", List.of());
                    if (revision < 4 || occupied) {
                        require(before.equals(stage.values()), "Old or occupied cap parcel changed partially");
                    } else {
                        require(stage.values().size() == before.size()
                                        && stage.cellAt(new BlockPos(7, 9, 6)).equals(before.getFirst()),
                                "Cap lowering changed its original bearing or increased roof bulk");
                        for (int x = 7; x <= 8; x++) {
                            for (int z = 6; z <= 7; z++) {
                                require(stage.cellAt(new BlockPos(x, 10, z)) != null
                                                && stage.cellAt(new BlockPos(x, 11, z)) == null,
                                        "Cap patch did not move down as a complete group");
                            }
                        }
                    }
                }
            }
        }
        System.out.println("PASS courtyard roof crossings: 3 exact four-cell caps lowered in five biomes, "
                + "unchanged roof volume, immutable legacy/occupied parcels");
    }

    private static void guardedParcel() throws ReflectiveOperationException {
        Case sample = CASES.get(2);
        Materials p = AuthoredVillageStructures.plan(sample.type(), sample.id(), 4,
                VillageArchitecture.PALETTE_BALANCED, VillageArchitecture.DRESSING_RESTRAINED,
                VillageArchitecture.Character.RUSTIC, BiomeDialect.PLAINS).materials();
        Method author = AuthoredVillageStructures.class.getDeclaredMethod(
                sample.author(), Builder.class, Metadata.class, Materials.class);
        author.setAccessible(true);
        BlockPos bearing = sample.bearings().getFirst();
        for (String conflict : List.of("occupied", "reserved", "unknown_target", "missing_anchor", "prior_stage")) {
            Builder stage = new Builder(conflict.equals("prior_stage") ? Set.of(bearing) : Set.of());
            stage.templateRevision = 4;
            Metadata metadata = metadata(4);
            author.invoke(null, stage, metadata, p);
            switch (conflict) {
                case "occupied" -> stage.force(Phase.FIXTURE, bearing.getX(), bearing.getY(), bearing.getZ(),
                        Blocks.CHEST.defaultBlockState());
                case "reserved" -> metadata.reservedAir.add(bearing);
                case "unknown_target" -> stage.force(Phase.ROOF, -1, 9, 15, Blocks.DIAMOND_BLOCK.defaultBlockState());
                case "missing_anchor" -> stage.remove(0, 8, 15);
                default -> { }
            }
            List<Cell> before = stage.values();
            AuthoredStructuralContactRefinements.refine(stage, metadata, p, sample.id(), List.of());
            require(before.equals(stage.values()), "Unsafe structural parcel was not skipped intact: " + conflict);
        }
    }

    private static Metadata metadata(int revision) {
        Metadata metadata = new Metadata();
        metadata.templateRevision = revision;
        return metadata;
    }

    private static List<BlockPos> positions(int[][] xyz) {
        List<BlockPos> result = new ArrayList<>();
        for (int[] pos : xyz) {
            result.add(new BlockPos(pos[0], pos[1], pos[2]));
        }
        return List.copyOf(result);
    }

    private static BlockPos position(Cell cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Case(String id, String author, ProjectType type, List<BlockPos> bearings) {
    }
}
