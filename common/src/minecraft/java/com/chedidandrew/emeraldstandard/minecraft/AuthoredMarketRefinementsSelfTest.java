package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.ProjectType;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Materials;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Exact before/after market recipe assertions; never loads a world. */
final class AuthoredMarketRefinementsSelfTest {
    private AuthoredMarketRefinementsSelfTest() {
    }

    static void run() throws ReflectiveOperationException {
        Method author = AuthoredVillageStructures.class.getDeclaredMethod(
                "marketLane", Builder.class, Metadata.class, Materials.class);
        author.setAccessible(true);
        for (VillageArchitecture.BiomeDialect dialect : VillageArchitecture.BiomeDialect.values()) {
            Materials materials = AuthoredVillageStructures.plan(ProjectType.MARKET_SQUARE,
                    "market_lane_04", 2, VillageArchitecture.PALETTE_BALANCED,
                    VillageArchitecture.DRESSING_RESTRAINED, VillageArchitecture.Character.RUSTIC,
                    dialect).materials();
            for (int revision : new int[] {2, 3}) {
                Builder base = new Builder(Set.of());
                base.templateRevision = revision;
                Metadata metadata = new Metadata();
                metadata.templateRevision = revision;
                author.invoke(null, base, metadata, materials);
                List<Cell> before = base.values();
                AuthoredMarketRefinements.finishLane(base, metadata, materials);
                if (revision == 2) {
                    require(before.equals(base.values()), "Historical market roof must remain exact");
                    continue;
                }
                for (Cell old : before) {
                    boolean roofRecipe = old.phase() == Phase.ROOF
                            && (old.x() <= 6 || old.x() >= 10);
                    boolean postTip = (old.x() == 5 || old.x() == 11)
                            && old.y() >= 5 && old.y() <= 6 && old.phase() == Phase.FRAME;
                    boolean purlin = (old.x() == 1 || old.x() == 5 || old.x() == 11 || old.x() == 15)
                            && old.y() == 4 && old.phase() == Phase.FRAME;
                    if (!roofRecipe && !postTip && !purlin) {
                        require(old.equals(base.cellAt(new BlockPos(old.x(), old.y(), old.z()))),
                                "Market refinement changed a retained role fixture, floor, arch or frame: " + old);
                    }
                }
                for (int side : new int[] {0, 10}) {
                    for (int x = side; x <= side + 6; x++) {
                        for (int z = 0; z <= 12; z++) {
                            Cell roof = base.cellAt(new BlockPos(x, 5, z));
                            require(roof != null && roof.state().is(materials.roofSlab()),
                                    "Market canopy must be continuous across every stall");
                            for (int y = 6; y <= 8; y++) {
                                boolean pediment = z == 0 && x >= side + 2 && x <= side + 4
                                        && y <= 7;
                                require(base.isOccupied(new BlockPos(x, y, z)) == pediment,
                                        "Only the two small front pediments may rise above a stall canopy");
                            }
                        }
                    }
                    for (int x = side + 2; x <= side + 4; x++) {
                        Cell bearing = base.cellAt(new BlockPos(x, 5, 0));
                        require(bearing.state().getValue(SlabBlock.TYPE) == SlabType.DOUBLE,
                                "Merchant pediment must meet its full-depth canopy bearing");
                        require(base.cellAt(new BlockPos(x, 6, 0)).phase() == Phase.FRAME
                                        && base.cellAt(new BlockPos(x, 7, 0)).phase() == Phase.ROOF,
                                "Merchant pediment panel and weather cap must stay connected");
                    }
                }
                for (int x : new int[] {1, 5, 11, 15}) {
                    for (int z = 0; z <= 12; z++) {
                        Cell beam = base.cellAt(new BlockPos(x, 4, z));
                        require(beam != null && beam.state().is(materials.timber())
                                        && beam.state().getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Z,
                                "Market canopy lost a continuous longitudinal bearing");
                    }
                }
                for (int x = 7; x <= 9; x++) {
                    for (int z = 1; z <= 10; z++) {
                        require(!base.isOccupied(new BlockPos(x, 1, z))
                                        && !base.isOccupied(new BlockPos(x, 2, z)),
                                "Market's three-wide central street was obstructed");
                    }
                }
            }
            for (BlockPos conflict : List.of(
                    new BlockPos(5, 6, 1), new BlockPos(15, 4, 6), new BlockPos(2, 7, 0))) {
                Builder base = new Builder(Set.of());
                base.templateRevision = 3;
                Metadata metadata = new Metadata();
                metadata.templateRevision = 3;
                author.invoke(null, base, metadata, materials);
                base.force(Phase.FIXTURE, conflict.getX(), conflict.getY(), conflict.getZ(),
                        Blocks.CHEST.defaultBlockState());
                List<Cell> before = base.values();
                boolean rejected = false;
                try {
                    AuthoredMarketRefinements.finishLane(base, metadata, materials);
                } catch (IllegalStateException expected) {
                    rejected = true;
                }
                require(rejected && before.equals(base.values()),
                        "A changed post or occupied purlin must reject atomically before roof removal");
            }
        }
        System.out.println("PASS revision-3 market lane tests (five dialects, continuous roofs, "
                + "bearing beams, retained fixtures/arch/aisle, atomic conflicts and historical revision)");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
