package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Materials;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBlock;

final class AuthoredChimneyRefinementsSelfTest {
    private AuthoredChimneyRefinementsSelfTest() { }

    static void run() {
        Materials p = new Materials(VillageArchitecture.BiomeDialect.PLAINS,
                Blocks.STONE_BRICKS, Blocks.OAK_PLANKS, Blocks.OAK_PLANKS,
                Blocks.OAK_LOG, Blocks.OAK_STAIRS, Blocks.OAK_SLAB,
                Blocks.OAK_FENCE, Blocks.CHISELED_STONE_BRICKS, Blocks.OAK_DOOR,
                Blocks.STONE_BRICK_STAIRS, Blocks.BRICKS);
        for (int revision = 1; revision <= 5; revision++) {
            Builder b = stack(revision, p);
            Metadata m = metadata(revision);
            var before = b.values();
            AuthoredChimneyRefinements.refine(b, m, p);
            if (revision < 5) require(before.equals(b.values()), "Historical chimney changed");
            else {
                require(b.cellAt(new BlockPos(2, 6, 2)).state().is(Blocks.BRICKS), "Missing full collar");
                require(b.cellAt(new BlockPos(2, 7, 2)).state().is(Blocks.BRICK_WALL)
                        && b.cellAt(new BlockPos(2, 8, 2)).state().is(Blocks.BRICK_WALL), "Missing slim tip");
                require(b.cellAt(new BlockPos(2, 9, 2)) == null, "Tall stack not shortened");
                var finished = b.values();
                AuthoredChimneyRefinements.refine(b, m, p);
                require(finished.equals(b.values()), "Chimney pass is not idempotent");
            }
        }
        Builder capped = stack(5, p);
        capped.force(Phase.ROOF, 2, 13, 2, Blocks.STONE_BRICK_SLAB.defaultBlockState());
        var cappedBefore = capped.values();
        AuthoredChimneyRefinements.refine(capped, metadata(5), p);
        require(cappedBefore.equals(capped.values()), "Decorative capped chimney changed");
        Builder reserved = stack(5, p);
        Metadata reservedMetadata = metadata(5);
        reservedMetadata.reservedAir.add(new BlockPos(2, 9, 2));
        var reservedBefore = reserved.values();
        AuthoredChimneyRefinements.refine(reserved, reservedMetadata, p);
        require(reservedBefore.equals(reserved.values()), "Reserved column partially changed");
        Set<String> changed = new HashSet<>();
        int pots = 0;
        for (var descriptor : VillageArchitecture.activeBlueprints()) {
            for (var dialect : VillageArchitecture.BiomeDialect.values()) {
                var old = AuthoredVillageStructures.plan(descriptor.type(), descriptor.templateId(), 4,
                        VillageArchitecture.PALETTE_BALANCED, VillageArchitecture.DRESSING_PROSPEROUS,
                        VillageArchitecture.Character.RUSTIC, dialect);
                var now = AuthoredVillageStructures.plan(descriptor.type(), descriptor.templateId(), 5,
                        VillageArchitecture.PALETTE_BALANCED, VillageArchitecture.DRESSING_PROSPEROUS,
                        VillageArchitecture.Character.RUSTIC, dialect);
                Map<BlockPos, Cell> next = now.base().stream().collect(Collectors.toMap(
                        c -> new BlockPos(c.x(), c.y(), c.z()), Function.identity()));
                for (Cell prior : old.base()) {
                    Cell current = next.get(new BlockPos(prior.x(), prior.y(), prior.z()));
                    if (prior.equals(current)) continue;
                    require(prior.state().is(old.materials().chimney()), "Changed non-chimney cell in "
                            + descriptor.templateId() + ": " + prior + " -> " + current);
                    require(current == null || current.state().getBlock() instanceof WallBlock,
                            "Unexpected chimney replacement " + current);
                    if (current != null) pots++;
                    changed.add(descriptor.templateId());
                }
                require(old.stageOne().equals(now.stageOne()) && old.stageTwo().equals(now.stageTwo()),
                        "Chimney pass changed garden/furnishing stages: " + descriptor.templateId());
                if (descriptor.templateId().equals("cottage_hearth_01")) {
                    require(old.base().equals(now.base()), "Broad sculpted hearth chimney changed");
                }
            }
        }
        require(changed.size() >= 8, "Too few plain chimney masters refined: " + changed);
        System.out.println("PASS chimney refinement: " + changed.size() + " masters, " + pots
                + " wall-pot cells across five dialects; decorated hearth retained: " + changed.stream().sorted().toList());
        verifyShortStacks(p);
        verifyRevisionEight();
    }

    private static void verifyShortStacks(Materials p) {
        for (int exposed = 1; exposed <= 2; exposed++) {
            for (boolean cap : new boolean[] {false, true}) {
                Builder b = stack(8, p);
                int top = 5 + exposed;
                for (int y = top + 1; y <= 12; y++) b.remove(2, y, 2);
                if (cap) b.force(Phase.ROOF, 2, top + 1, 2, Blocks.STONE_BRICK_SLAB.defaultBlockState());
                AuthoredChimneyRefinements.refine(b, metadata(8), p);
                require(b.cellAt(new BlockPos(2, top, 2)).state().is(Blocks.BRICK_WALL),
                        "Short stack retains solid brick tip: exposed=" + exposed + ", cap=" + cap);
                require(b.cellAt(new BlockPos(2, 5, 2)).state().is(Blocks.BRICKS), "Roof mount changed");
                if (exposed == 2) require(b.cellAt(new BlockPos(2, 6, 2)).state().is(Blocks.BRICKS),
                        "Lower half lost its masonry collar");
                if (cap) require(b.cellAt(new BlockPos(2, top + 1, 2)).state().is(Blocks.STONE_BRICK_SLAB),
                        "Single-column decorative cap changed");
                var before = b.values();
                AuthoredChimneyRefinements.refine(b, metadata(8), p);
                require(before.equals(b.values()), "Short stack pass is not idempotent");
            }
        }
        Builder b = stack(8, p);
        for (int y = 8; y <= 12; y++) b.remove(2, y, 2);
        Metadata reserved = metadata(8);
        reserved.accessTargets.add(new BlockPos(2, 7, 2));
        var before = b.values();
        AuthoredChimneyRefinements.refine(b, reserved, p);
        require(before.equals(b.values()), "Short reserved stack partially changed");
    }

    private static void verifyRevisionEight() {
        Set<String> changed = new HashSet<>();
        int pots = 0;
        for (var descriptor : VillageArchitecture.activeBlueprints()) {
            for (var dialect : VillageArchitecture.BiomeDialect.values()) {
                var old = AuthoredVillageStructures.plan(descriptor.type(), descriptor.templateId(), 7,
                        VillageArchitecture.PALETTE_BALANCED, VillageArchitecture.DRESSING_PROSPEROUS,
                        VillageArchitecture.Character.RUSTIC, dialect, 42L);
                var now = AuthoredVillageStructures.plan(descriptor.type(), descriptor.templateId(), 8,
                        VillageArchitecture.PALETTE_BALANCED, VillageArchitecture.DRESSING_PROSPEROUS,
                        VillageArchitecture.Character.RUSTIC, dialect, 42L);
                Map<BlockPos, Cell> before = index(old.base()), after = index(now.base());
                require(before.keySet().equals(after.keySet()), "Short stack pass moved/removed blocks: " + descriptor.templateId());
                for (var pos : before.keySet()) {
                    Cell prior = before.get(pos), current = after.get(pos);
                    if (prior.equals(current)) continue;
                    require(prior.state().is(old.materials().chimney())
                                    && current.state().getBlock() instanceof WallBlock && prior.phase() == current.phase(),
                            "Non-chimney change: " + descriptor.templateId() + "/" + dialect + " " + prior + " -> " + current);
                    pots++;
                    changed.add(descriptor.templateId());
                }
                require(old.stageOne().equals(now.stageOne()) && old.stageTwo().equals(now.stageTwo()),
                        "Short stack pass changed furnishing stages: " + descriptor.templateId());
                // Independent surface census; the reviewed sculpted hearth is the sole exemption.
                if (dialect == VillageArchitecture.BiomeDialect.PLAINS) {
                    for (Cell c : now.base()) {
                        if (!c.state().is(Blocks.BRICKS) || c.y() < 6) continue;
                        BlockPos pos = new BlockPos(c.x(), c.y(), c.z());
                        if (after.containsKey(pos.above())) continue;
                        boolean single = true;
                        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                            if (after.containsKey(pos.relative(direction))) single = false;
                        }
                        if (single) require(descriptor.templateId().equals("cottage_hearth_01")
                                        && pos.equals(new BlockPos(9, 9, 7)),
                                "Unrefined plain full-brick tip " + descriptor.templateId() + " " + pos);
                    }
                }
            }
        }
        require(!changed.isEmpty(), "No remaining short/capped chimneys were refined");
        System.out.println("PASS revision-eight short/capped chimneys: " + changed.size() + " masters, " + pots
                + " wall-pot cells; all other cells and furnishing stages unchanged: " + changed.stream().sorted().toList());
    }

    private static Map<BlockPos, Cell> index(java.util.List<Cell> cells) {
        return cells.stream().collect(Collectors.toMap(c -> new BlockPos(c.x(), c.y(), c.z()), Function.identity()));
    }

    private static Builder stack(int revision, Materials p) {
        Builder b = new Builder(Set.of());
        b.templateRevision = revision;
        for (int y = 0; y <= 12; y++) b.force(Phase.ROOF, 2, y, 2, p.chimney().defaultBlockState());
        b.force(Phase.ROOF, 1, 5, 2, p.roofStairs().defaultBlockState());
        b.force(Phase.ROOF, 8, 18, 2, p.roofStairs().defaultBlockState()); // Distant peak must not affect sizing.
        return b;
    }

    private static Metadata metadata(int revision) {
        Metadata m = new Metadata();
        m.templateRevision = revision;
        m.integratedChimneys.add(new BlockPos(2, 0, 2));
        return m;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
