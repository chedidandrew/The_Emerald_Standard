package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.npc.villager.Villager;

/** Bounded fence preparation and visiting crews for authoritative construction jobs. */
final class VillageConstructionActivity {
    static final String JOB = "tes_worksite:";
    static final String PROP = "tes_worksite_prop";
    record Site(String tag, UUID village, long project, BlockPos origin, BlockPos corner, BlockPos maximum, boolean working) {
        Site(String tag, UUID village, long project, BlockPos origin, BlockPos corner) {
            this(tag,village,project,origin,corner,corner.offset(8,6,8),true);
        }
        Site(String tag, UUID village, long project, BlockPos origin, BlockPos corner, boolean working) {
            this(tag,village,project,origin,corner,corner.offset(8,6,8),working);
        }
    }
    private VillageConstructionActivity() { }
    static void reset() { ConstructionSitePresentation.reset(); DevelopmentEntities.reset(); }
    static String projectTag(UUID village, long project, long origin) {
        return JOB + village + ":" + project + ":" + origin;
    }
    static String bankTag(long key, long origin) { return JOB + "bank:" + key + ":" + origin; }
    static void tick(ServerLevel level, EconomyService economy, boolean enabled) {
        if (level.getGameTime() % 80 != 0) return;
        update(level, sites(level, economy, enabled));
    }
    static List<Site> sites(ServerLevel level, EconomyService economy, boolean enabled) {
        List<Site> sites = new ArrayList<>();
        {
            String dimension = level.dimension().identifier().toString();
            for (var project : economy.constructionSites(dimension)) {
                BlockPos origin = BlockPos.of(project.origin());
                BlockPos corner = project.min() == 0 ? origin : BlockPos.of(project.min());
                BlockPos maximum = project.max() == 0 ? origin.offset(8,6,8) : BlockPos.of(project.max());
                sites.add(new Site(projectTag(project.villageId(), project.projectId(), project.origin()),
                        project.villageId(), project.projectId(), origin, corner, maximum,
                        enabled && project.terrainReady() && project.working() && !ConstructionWorkStatus.waiting(project.villageId(),project.projectId())
                                && project.retryAfterTick() <= level.getGameTime()));
            }
            if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                for (var entry : economy.pendingBankConstructionsSnapshot().entrySet()) {
                    var bank = entry.getValue();
                    boolean working = enabled && EmeraldConfig.current().villageBanksEnabled()
                            && economy.constructionVillageEligible(bank.villageId());
                    BlockPos origin = BlockPos.of(bank.origin()), min = origin, max = origin;
                    for (var cell : bank.cells()) if (!cell.after().equals("minecraft:air")) {
                        BlockPos p = BlockPos.of(cell.position());
                        min = new BlockPos(Math.min(min.getX(),p.getX()),Math.min(min.getY(),p.getY()),Math.min(min.getZ(),p.getZ()));
                        max = new BlockPos(Math.max(max.getX(),p.getX()),Math.max(max.getY(),p.getY()),Math.max(max.getZ(),p.getZ()));
                    }
                    sites.add(new Site(bankTag(entry.getKey(), bank.origin()), bank.villageId(), entry.getKey(),
                            origin, min, max, working && !ConstructionDiagnostics.waiting("bank:"+entry.getKey())));
                }
            }
        }
        return sites;
    }
    static void update(ServerLevel level, List<Site> sites) {
        // Upgrade cleanup: only old tagged display props / assignments, never real villagers or blocks.
        for (var entity : DevelopmentEntities.snapshot(level)) {
            if (entity instanceof Display && entity.entityTags().contains(PROP)) entity.discard();
            if (entity instanceof Villager && entity.entityTags().stream().anyMatch(t -> t.startsWith(JOB))) {
                entity.entityTags().stream().filter(t -> t.startsWith(JOB) || t.equals("tes_material_delivery"))
                        .toList().forEach(entity::removeTag);
            }
        }
        ConstructionSitePresentation.update(level, sites);
    }
    static void unloaded(net.minecraft.world.entity.Entity entity) { }
}
