package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.ProjectType;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;

/** Production coverage and atomic preservation for the two exact workshop assemblies. */
final class AuthoredWorkshopContactRefinementsSelfTest {
    private AuthoredWorkshopContactRefinementsSelfTest() { }

    static void run() {
        int count = 0;
        for (BiomeDialect dialect : BiomeDialect.values()) {
            for (boolean basilica : new boolean[] {true, false}) {
                String id = basilica ? "warehouse_basilica_05" : "mine_adit_03";
                var plan = AuthoredVillageStructures.plan(
                        basilica ? ProjectType.WAREHOUSE : ProjectType.MINE_ENTRANCE, id, 4,
                        VillageArchitecture.PALETTE_BALANCED, VillageArchitecture.DRESSING_RESTRAINED,
                        VillageArchitecture.Character.RUSTIC, dialect);
                List<BlockPos> joints = new ArrayList<>();
                if (basilica) {
                    for (int z : new int[] {4, 8, 12}) {
                        joints.add(new BlockPos(7, 6, z));
                        joints.add(new BlockPos(17, 6, z));
                    }
                } else {
                    for (int z = 4; z <= 11; z++) joints.add(new BlockPos(3, 5, z));
                    for (int z = 6; z <= 11; z++) joints.add(new BlockPos(7, 5, z));
                }
                for (BlockPos pos : joints) {
                    Cell actual = plan.base().stream().filter(c -> position(c).equals(pos)).findFirst().orElse(null);
                    require(actual != null && actual.phase() == Phase.FRAME
                                    && actual.state().is(plan.materials().timber())
                                    && actual.state().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, pos),
                            "Missing production workshop joint: " + id + " / " + dialect + " " + pos);
                    require(!plan.metadata().reservedAir().contains(pos)
                                    && !plan.metadata().accessTargets().contains(pos),
                            "Workshop joint intersects a reserved route: " + pos);
                    count++;
                }
                for (int revision : new int[] {1, 2, 3, 4}) {
                    for (String conflict : List.of("none", "occupied", "reserved", "unknown_target", "missing_anchor")) {
                        Builder base = new Builder(Set.of());
                        base.templateRevision = revision;
                        Metadata metadata = new Metadata();
                        metadata.templateRevision = revision;
                        metadata.reservedAir.addAll(plan.metadata().reservedAir());
                        metadata.accessTargets.addAll(plan.metadata().accessTargets());
                        for (Cell cell : plan.base()) {
                            if (!joints.contains(position(cell))) {
                                base.put(cell.phase(), cell.x(), cell.y(), cell.z(), cell.state());
                            }
                        }
                        BlockPos first = joints.getFirst();
                        BlockPos target = basilica ? new BlockPos(6, 6, 4) : new BlockPos(3, 6, 4);
                        BlockPos anchor = basilica ? new BlockPos(8, 6, 4) : new BlockPos(2, 5, 4);
                        switch (conflict) {
                            case "occupied" -> base.force(Phase.FIXTURE, first.getX(), first.getY(), first.getZ(),
                                    Blocks.CHEST.defaultBlockState());
                            case "reserved" -> metadata.reservedAir.add(first);
                            case "unknown_target" -> base.force(Phase.ROOF, target.getX(), target.getY(), target.getZ(),
                                    Blocks.DIAMOND_BLOCK.defaultBlockState());
                            case "missing_anchor" -> base.remove(anchor.getX(), anchor.getY(), anchor.getZ());
                            default -> { }
                        }
                        List<Cell> before = base.values();
                        AuthoredWorkshopContactRefinements.refine(base, metadata, plan.materials(), id);
                        if (revision < 4 || !conflict.equals("none")) {
                            require(before.equals(base.values()), "Legacy or unsafe workshop parcel changed: "
                                    + id + " / " + revision + " / " + conflict);
                        } else {
                            require(base.values().size() == before.size() + joints.size(), "Incomplete workshop joint group");
                            for (Cell old : before) require(old.equals(base.cellAt(position(old))), "Existing cell changed: " + old);
                            List<Cell> once = base.values();
                            AuthoredWorkshopContactRefinements.refine(base, metadata, plan.materials(), id);
                            require(once.equals(base.values()), "Workshop refinement was not idempotent");
                        }
                    }
                }
            }
        }
        require(count == 100, "Workshop biome coverage changed");
        System.out.println("PASS 100 workshop joints: production coverage, original cells, frozen revisions and atomic guards");
    }

    private static BlockPos position(Cell cell) { return new BlockPos(cell.x(), cell.y(), cell.z()); }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
