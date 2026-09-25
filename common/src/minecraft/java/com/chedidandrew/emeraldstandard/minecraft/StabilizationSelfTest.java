package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine;
import java.nio.file.Files;
import net.minecraft.server.level.ServerLevel;

/** Opt-in native checks; no user's village or installed profile is modified. */
final class StabilizationSelfTest {
    static void verify(ServerLevel level) {
        verifyCore(level);
        WalkwayConnectionsSelfTest.verify(level);
        VillageExpansionSelfTest.verify(level);
        DebugPerformanceSelfTest.verify(level);
        System.out.println("PASS stabilization native: material identity, bounded JVM profile, walkway queue, occupied terrain and debug tick boundaries");
    }

    static void verifyCore(ServerLevel level) {
        try {
            verifyPathInvalidation(level);
            verifyTemplateCache(level);
            long geometryNanos = 0, materialNanos = 0;
            for (var dialect : VillageArchitecture.BiomeDialect.values()) {
                for (String palette : new String[]{"balanced", "timber_forward", "masonry_forward"}) {
                    long start = System.nanoTime();
                    var blueprint = AuthoredVillageStructures.plan(VillageProsperityEngine.ProjectType.COTTAGE,
                            "cottage_hearth_01", AuthoredVillageStructures.LATEST_TEMPLATE_REVISION,
                            palette, "restrained", VillageArchitecture.Character.RUSTIC, dialect);
                    geometryNanos += System.nanoTime() - start;
                    start = System.nanoTime();
                    var direct = AuthoredVillageStructures.planMaterials(AuthoredVillageStructures.LATEST_TEMPLATE_REVISION,
                            palette, VillageArchitecture.Character.RUSTIC, dialect);
                    materialNanos += System.nanoTime() - start;
                    if (!blueprint.materials().equals(direct)) throw new IllegalStateException("Road palette changed: " + dialect + "/" + palette);
                }
            }
            System.out.printf("PASS material lookup equivalence: 15 palettes; full geometry %.3fms, materials only %.3fms (diagnostic, not a performance assertion)%n",
                    geometryNanos / 1_000_000.0, materialNanos / 1_000_000.0);
            var directory = Files.createTempDirectory("tes-profile-smoke-");
            try (var profile = DebugJvmProfile.start(directory)) {
                if (!profile.state().equals("RUNNING")) throw new IllegalStateException("Profile did not start");
            }
            if (Files.size(directory.resolve("runtime-profile.jfr")) == 0)
                throw new IllegalStateException("Profile did not finalize");
            Files.delete(directory.resolve("runtime-profile.jfr")); Files.delete(directory);
        } catch (Exception failure) { throw new IllegalStateException("Stabilization regression", failure); }
    }

    static void verifyPathInvalidation(ServerLevel level) {
        var pos = new net.minecraft.core.BlockPos(8192, level.getMaxY()-32, 8192);
        level.getChunk(pos);
        var before = level.getBlockState(pos);
        try {
            var cache = level.getPathTypeCache();
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 18);
            if (cache.getOrCompute(level,pos) != net.minecraft.world.level.pathfinder.PathType.OPEN)
                throw new IllegalStateException("Air path classification stale");
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 18);
            if (cache.getOrCompute(level,pos) != net.minecraft.world.level.pathfinder.PathType.BLOCKED)
                throw new IllegalStateException("Construction retained cached air in non-ticking chunk");
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 18);
            if (cache.getOrCompute(level,pos) != net.minecraft.world.level.pathfinder.PathType.OPEN)
                throw new IllegalStateException("Clearing retained cached wall in non-ticking chunk");
            System.out.println("PASS path cache: loaded non-ticking chunk follows stone/air changes");
        } finally { level.setBlock(pos,before,18); }
    }

    private static void verifyTemplateCache(ServerLevel level) throws Exception {
        var v = new com.chedidandrew.emeraldstandard.core.EconomyState().village(new java.util.UUID(0, 491));
        v.architectureCharacter="rustic"; v.architectureDialect="plains";
        var p=new com.chedidandrew.emeraldstandard.core.EconomyState.VillageProject();
        p.projectId=1; p.type=VillageProsperityEngine.ProjectType.COTTAGE;
        p.designSchema="blueprint_v2"; p.designTemplateId="cottage_hearth_01"; p.designTemplateRevision=10;
        p.designPaletteId="balanced"; p.designDressingId="restrained";
        var origin=new net.minecraft.core.BlockPos(8192,200,8192);
        var geometry=VillageProsperityManager.class.getDeclaredMethod("blueprintPlacementPlan",ServerLevel.class,
                net.minecraft.core.BlockPos.class,v.getClass(),p.getClass()); geometry.setAccessible(true);
        var template=VillageProsperityManager.class.getDeclaredMethod("projectTemplate",ServerLevel.class,
                net.minecraft.core.BlockPos.class,v.getClass(),p.getClass()); template.setAccessible(true);
        Object first=geometry.invoke(null,level,origin,v,p);
        if(first!=geometry.invoke(null,level,origin.east(80),v,p)) throw new IllegalStateException("Relative geometry not reused across plots");
        p.designRotation=1;
        if(first==geometry.invoke(null,level,origin,v,p)) throw new IllegalStateException("Rotation omitted from geometry key");
        p.designRotation=0; p.designMirrored=true;
        if(first==geometry.invoke(null,level,origin,v,p)) throw new IllegalStateException("Mirroring omitted from geometry key");
        p.designMirrored=false; v.architectureDialect="desert";
        if(first==geometry.invoke(null,level,origin,v,p)) throw new IllegalStateException("Village style omitted from geometry key");
        v.architectureDialect="plains";
        Object cells=template.invoke(null,level,origin,v,p);
        if(cells!=template.invoke(null,level,origin,v,p)) throw new IllegalStateException("Normal construction rebuilt a frozen template");
        p.designPlanHash="deliberately-invalid-frozen-hash";
        try { template.invoke(null,level,origin,v,p); throw new IllegalStateException("Cached template bypassed frozen-hash validation"); }
        catch(java.lang.reflect.InvocationTargetException expected) {
            if(!expected.getCause().getClass().getSimpleName().equals("BlueprintPlanMismatchException")) throw expected;
        }
        System.out.println("PASS template reuse: plot independence, normal mode, rotation/mirror/style keys and frozen-hash rejection");
    }
}
