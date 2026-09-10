package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;

/** Saved entity tags own the theatre, never the world's blocks or a villager's profession. */
final class VillageConstructionActivity {
    static final String JOB = "tes_worksite:";
    static final String PROP = "tes_worksite_prop";
    private static final String ROLE = "tes_worksite_role:";
    private static final String DELIVERY = "tes_material_delivery";
    private static final String CARRIER = "tes_carrier:";
    private static final Map<ServerLevel, List<Entity>> CARGO = new IdentityHashMap<>();
    private static final Map<Villager, net.minecraft.world.entity.ai.memory.WalkTarget> OWNED_WALKS = new IdentityHashMap<>();
    record Site(String tag, UUID village, long project, BlockPos origin, BlockPos corner, boolean working) {
        Site(String tag,UUID village,long project,BlockPos origin,BlockPos corner) { this(tag,village,project,origin,corner,true); }
    }
    private VillageConstructionActivity() { }
    static void reset() { CARGO.clear(); OWNED_WALKS.clear(); DevelopmentEntities.reset(); }

    static void tick(ServerLevel level, EconomyService economy, boolean enabled) {
        if (level.getGameTime() % 10 == 0) moveCargo(level, CARGO.getOrDefault(level, List.of()));
        if (level.getGameTime() % 80 != 0) return;
        List<Site> sites = new ArrayList<>();
        if (enabled) {
            String dimension = level.dimension().identifier().toString();
            for (var snapshot : economy.villageSnapshots()) {
                var village = snapshot.village();
                if (!dimension.equals(village.dimensionKey)) continue;
                for (var project : village.projects) {
                    if (project.originPos == 0 || !com.chedidandrew.emeraldstandard.core.VillageConstructionPolicy.eligible(village,project)) continue;
                    BlockPos origin = BlockPos.of(project.originPos);
                    BlockPos corner = project.boundsMinPos == 0 ? origin : BlockPos.of(project.boundsMinPos);
                    sites.add(new Site(JOB + village.villageId + ":" + project.projectId + ":" + project.originPos,
                            village.villageId, project.projectId, origin, corner,
                            !ConstructionWorkStatus.waiting(village.villageId,project.projectId)
                                    && project.retryAfterGameTick <= level.getGameTime()));
                }
            }
            if (EmeraldConfig.current().villageBanksEnabled()
                    && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                for (var entry : economy.pendingBankConstructionsSnapshot().entrySet()) {
                    var bank = entry.getValue();
                    var owner = bank.villageId()==null?null:economy.villageSnapshot(bank.villageId());
                    if (owner != null && !com.chedidandrew.emeraldstandard.core.VillageConstructionPolicy.villageEligible(owner.village())) continue;
                    BlockPos origin = BlockPos.of(bank.origin());
                    sites.add(new Site(JOB + "bank:" + entry.getKey() + ":" + bank.origin(),
                            bank.villageId(), entry.getKey(), origin, origin.offset(-5, 0, -5),
                            !ConstructionDiagnostics.waiting("bank:"+entry.getKey())));
                }
            }
        }
        update(level, sites);
    }

    static void update(ServerLevel level, List<Site> sites) {
        List<Entity> entities = DevelopmentEntities.snapshot(level);
        Set<String> active = new HashSet<>();
        sites.forEach(s -> active.add(s.tag));
        // Also runs after chunks reload. Unrelated displays and all real blocks are untouched.
        for (Entity entity : entities) {
            List<String> jobs = entity.entityTags().stream().filter(t -> t.startsWith(JOB)).toList();
            for (String tag : jobs) if (!active.contains(tag)) entity.removeTag(tag);
            if (entity.entityTags().stream().noneMatch(t -> t.startsWith(JOB))) {
                entity.removeTag(DELIVERY);
                if (entity instanceof Villager worker) releaseWalk(worker);
            }
            if (entity instanceof Display && entity.entityTags().contains(PROP)
                    && jobs.stream().noneMatch(active::contains)) entity.discard();
        }
        List<Villager> villagers = entities.stream().filter(e -> e instanceof Villager)
                .map(e -> (Villager) e).filter(Entity::isAlive).toList();
        for (Site site : sites) {
            if (!level.hasChunk(site.origin.getX() >> 4, site.origin.getZ() >> 4)) continue;
            List<Villager> assigned = villagers.stream().filter(v -> v.entityTags().contains(site.tag))
                    .sorted(Comparator.comparing(Entity::getUUID)).toList();
            List<Villager> workers = new ArrayList<>();
            for (Villager worker : assigned) {
                if (workers.size() < 2 && available(worker)
                        && worker.blockPosition().distSqr(site.origin) < 96 * 96) workers.add(worker);
                else { worker.removeTag(site.tag); worker.removeTag(DELIVERY); releaseWalk(worker); }
            }
            for (Villager candidate : level.getEntitiesOfClass(Villager.class,
                    new net.minecraft.world.phys.AABB(site.origin).inflate(48)).stream().filter(v -> site.working && available(v)
                    && v.entityTags().stream().noneMatch(t -> t.startsWith(JOB))
                    && (site.village == null || v.entityTags().contains("the_emerald_standard_village_" + site.village))
                    && v.blockPosition().distSqr(site.origin) < 48 * 48)
                    .sorted(Comparator.comparingDouble((Villager v) -> v.blockPosition().distSqr(site.origin))
                            .thenComparing(Entity::getUUID)).toList()) {
                if (workers.size() == 2) break;
                candidate.addTag(site.tag); workers.add(candidate);
            }
            BlockPos scaffold = ground(level, site.corner.offset(-2, 0, 1), site);
            BlockPos materials = ground(level, site.corner.offset(-2, 0, 4), site);
            syncProp(level, entities, site, "scaffold_base", scaffold, Blocks.SCAFFOLDING.defaultBlockState());
            syncProp(level, entities, site, "scaffold_top", scaffold == null ? null : scaffold.above(),
                    Blocks.SCAFFOLDING.defaultBlockState());
            syncProp(level, entities, site, "materials", materials, Blocks.OAK_PLANKS.defaultBlockState());
            if (!site.working || scaffold == null || materials == null) {
                for (Villager worker : workers) { releaseWalk(worker); worker.removeTag(DELIVERY); }
                continue;
            }
            if (scaffold == null || materials == null) continue;
            BlockPos delivery = materials.west(), work = scaffold.west();
            for (int i = 0; i < workers.size(); i++) {
                Villager worker = workers.get(i);
                if (!available(worker)) { releaseWalk(worker); continue; }
                // A completed trip picks up/sets down a display-only load, not real inventory.
                if (worker.blockPosition().distSqr(delivery) <= 2) worker.addTag(DELIVERY);
                if (worker.blockPosition().distSqr(work) <= 2) worker.removeTag(DELIVERY);
                BlockPos target = worker.entityTags().contains(DELIVERY) ? work : delivery;
                var memory = net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET;
                var current = worker.getBrain().getMemory(memory).orElse(null);
                if (walkable(level,target) && (current == null || current == OWNED_WALKS.get(worker))) {
                    var walk = new net.minecraft.world.entity.ai.memory.WalkTarget(target,.55f,0);
                    OWNED_WALKS.put(worker,walk); worker.getBrain().setMemory(memory,walk);
                }
                worker.getLookControl().setLookAt(site.origin.getX() + .5,
                        site.origin.getY() + 2, site.origin.getZ() + .5);
                if (worker.entityTags().contains(DELIVERY) && entities.stream().noneMatch(e -> e.isAlive()
                        && e.entityTags().contains(PROP) && e.entityTags().contains(CARRIER + worker.getUUID()))) {
                    var load = new WorkDisplay(level, Blocks.OAK_PLANKS.defaultBlockState(), .35f);
                    load.addTag(PROP); load.addTag(site.tag); load.addTag(CARRIER + worker.getUUID());
                    positionCargo(load, worker); level.addFreshEntity(load); entities.add(load); DevelopmentEntities.loaded(load,level);
                }
            }
        }
        List<Entity> cargo = entities.stream().filter(e -> e instanceof Display && e.isAlive()
                && e.entityTags().contains(PROP) && e.entityTags().stream().anyMatch(t -> t.startsWith(CARRIER))).toList();
        moveCargo(level, cargo);
        CARGO.put(level, cargo);
    }

    private static void moveCargo(ServerLevel level, List<Entity> loads) {
        for (Entity load : loads) {
            if (!load.isAlive()) continue;
            String carrier = load.entityTags().stream().filter(t -> t.startsWith(CARRIER)).findFirst().orElse("");
            Entity entity;
            try { entity = level.getEntity(UUID.fromString(carrier.substring(CARRIER.length()))); }
            catch (IllegalArgumentException | IndexOutOfBoundsException malformed) { load.discard(); continue; }
            if (!(entity instanceof Villager worker) || !available(worker) || !worker.entityTags().contains(DELIVERY)
                    || load.entityTags().stream().filter(t -> t.startsWith(JOB)).noneMatch(worker.entityTags()::contains))
                load.discard();
            else positionCargo(load, worker);
        }
    }

    private static void positionCargo(Entity load, Villager worker) {
        double angle = Math.toRadians(worker.getYRot());
        load.setPos(worker.getX() - Math.sin(angle) * .45 - .175, worker.getY() + .9,
                worker.getZ() + Math.cos(angle) * .45 - .175);
    }

    private static boolean available(Villager worker) {
        return worker.isAlive() && !worker.isBaby() && !BankerAccess.isBanker(worker)
                && !worker.isSleeping() && worker.getTradingPlayer() == null
                && !worker.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.PANIC)
                && !worker.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.RAID)
                && !worker.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.PRE_RAID)
                && !worker.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.HIDE)
                && worker.getTarget() == null && worker.getLastHurtByMob() == null;
    }

    private static void releaseWalk(Villager worker) {
        var owned = OWNED_WALKS.remove(worker);
        var memory = net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET;
        if (owned != null && worker.getBrain().getMemory(memory).orElse(null) == owned) {
            worker.getBrain().eraseMemory(memory); worker.getNavigation().stop();
        }
    }

    static void unloaded(Entity entity) { if(entity instanceof Villager worker) releaseWalk(worker); }

    private static boolean walkable(ServerLevel level, BlockPos pos) {
        return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                && level.getFluidState(pos.below()).isEmpty();
    }

    private static BlockPos ground(ServerLevel level, BlockPos column, Site site) {
        if (!level.hasChunk(column.getX() >> 4, column.getZ() >> 4)) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
        if (Math.abs(y - site.origin.getY()) > 4 || !walkable(level, pos)) return null;
        BlockState support = level.getBlockState(pos.below());
        if (support.hasBlockEntity() || !(VillageSitePreparation.dryNaturalGround(support)
                || support.is(Blocks.DIRT_PATH) || support.is(Blocks.GRAVEL))) return null;
        for (int dy = 0; dy < 2; dy++) if (!VillageDevelopmentProtection.mayPlace(level, site.village,
                site.project, pos.above(dy), level.getBlockState(pos.above(dy)), Blocks.SCAFFOLDING.defaultBlockState())) return null;
        return pos;
    }

    private static void syncProp(ServerLevel level, List<Entity> entities, Site site,
            String role, BlockPos pos, BlockState state) {
        List<Entity> existing = entities.stream().filter(e -> e instanceof Display && e.isAlive()
                && e.entityTags().contains(PROP) && e.entityTags().contains(site.tag)
                && e.entityTags().contains(ROLE + role)).toList();
        Entity keep = null;
        for (Entity entity : existing) {
            if (keep == null && pos != null && entity.blockPosition().equals(pos)
                    && level.getBlockState(pos).isAir()) keep = entity;
            else entity.discard();
        }
        if (keep != null || pos == null || !level.getBlockState(pos).isAir()) return;
        var prop = new WorkDisplay(level, state);
        prop.addTag(PROP); prop.addTag(site.tag); prop.addTag(ROLE + role);
        prop.setPos(pos.getX(), pos.getY(), pos.getZ());
        level.addFreshEntity(prop);
        DevelopmentEntities.loaded(prop,level);
        entities.add(prop);
    }

    /** Serializes as a vanilla block_display, so no new entity registration or client renderer. */
    private static final class WorkDisplay extends Display.BlockDisplay {
        WorkDisplay(ServerLevel level, BlockState state) {
            this(level, state, 1);
        }
        WorkDisplay(ServerLevel level, BlockState state, float scale) {
            super(EntityTypes.BLOCK_DISPLAY, level);
            CompoundTag data = new CompoundTag();
            data.put("block_state", NbtUtils.writeBlockState(state));
            CompoundTag transform = new CompoundTag();
            ListTag size = new ListTag();
            for (int axis = 0; axis < 3; axis++) size.add(FloatTag.valueOf(scale));
            transform.put("scale", size); data.put("transformation", transform);
            transform.put("translation", vector(0, 0, 0));
            transform.put("left_rotation", vector(0, 0, 0, 1));
            transform.put("right_rotation", vector(0, 0, 0, 1));
            data.putInt("teleport_duration", 10);
            readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data));
        }
        private static ListTag vector(float... values) {
            ListTag result = new ListTag();
            for (float value : values) result.add(FloatTag.valueOf(value));
            return result;
        }
    }
}
