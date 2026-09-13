package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Loaded-only fence preparation precedes structural work. Crews never gate construction. */
final class ConstructionSitePresentation {
    private static final Map<ServerLevel, Set<String>> ACTIVE = new IdentityHashMap<>();
    private static final Map<ServerLevel, Set<String>> WORKING = new IdentityHashMap<>();
    private static final Map<ServerLevel, Integer> CURSOR = new IdentityHashMap<>();
    static void reset() { ACTIVE.clear(); WORKING.clear(); CURSOR.clear(); }
    static Boolean active(ServerLevel level, String tag) {
        var jobs = ACTIVE.get(level); return jobs == null ? null : jobs.contains(tag);
    }
    static Boolean working(ServerLevel level, String tag) {
        var jobs = WORKING.get(level); return jobs == null ? null : jobs.contains(tag);
    }
    static boolean fenceReady(ServerLevel level, String tag) {
        var entry = ConstructionSiteLedger.get(level).entries.get(tag);
        return entry != null && entry.fenceReady();
    }
    static int crewSize(int footprint) { return footprint < 90 ? 1 : footprint < 220 ? 2 : footprint < 450 ? 3 : 4; }
    static void update(ServerLevel level, List<VillageConstructionActivity.Site> sites) {
        Set<String> active = new HashSet<>(); sites.forEach(s -> active.add(s.tag())); ACTIVE.put(level, active);
        var ledger = ConstructionSiteLedger.get(level);
        for (var old : new ArrayList<>(ledger.entries.keySet())) if (!active.contains(old)) {
            var entry = ledger.entries.remove(old); ledger.setDirty();
            for (long packed : entry.fences()) {
                BlockPos pos = BlockPos.of(packed);
                if (loaded(level, pos) && level.getBlockEntity(pos) instanceof ConstructionFenceBlock.Receipt receipt
                        && receipt.job().equals(old)) receipt.restore(level);
            }
        }
        List<ConstructionBuilder> builders = DevelopmentEntities.snapshot(level).stream()
                .filter(e -> e instanceof ConstructionBuilder).map(e -> (ConstructionBuilder)e).toList();
        Set<String> working = new HashSet<>();
        sites.stream().filter(VillageConstructionActivity.Site::working).forEach(s -> working.add(s.tag()));
        WORKING.put(level, working);
        for (var worker : builders) {
            if (worker.job.isEmpty()) continue; // Egg/summon visitors do not belong to the automatic ledger.
            if (!active.contains(worker.job)) worker.depart(exit(level, worker.blockPosition()));
            else if (!working.contains(worker.job)) worker.allowedToWork = false;
        }
        // A global presentation work budget; all jobs remain known while loaded sites rotate fairly.
        List<VillageConstructionActivity.Site> loadedSites = sites.stream().filter(s -> loaded(level, s.origin())).toList();
        int cursor = Math.floorMod(CURSOR.getOrDefault(level, 0), Math.max(1, loadedSites.size()));
        int limit = Math.min(16, loadedSites.size());
        CURSOR.put(level, (cursor + limit) % Math.max(1, loadedSites.size()));
        for (int siteIndex = 0; siteIndex < limit; siteIndex++) {
            var site = loadedSites.get((cursor + siteIndex) % loadedSites.size());
            if (!loaded(level, site.origin())) continue;
            var entry = ledger.entry(site.tag());
            int placed = 0;
            boolean pending = false;
            for (BlockPos column : perimeter(site)) {
                if (entry.fenceReady()) break;
                if (!site.working()) break; // Pausing retains existing receipts without changing more terrain.
                if (placed >= 16) { pending = true; break; } // Bounded preparation batches.
                if (!loaded(level, column)) { pending = true; continue; }
                // Remember the column, not just its current ground height, after a player edit.
                if (entry.fences().stream().map(BlockPos::of).anyMatch(p ->
                        p.getX() == column.getX() && p.getZ() == column.getZ())) continue;
                BlockPos pos = ground(level, column, site.origin().getY(), true);
                if (pos == null || entry.fences().contains(pos.asLong()) || overlapsOther(site, sites, pos)) continue;
                if (!loaded(level,pos.north()) || !loaded(level,pos.south())
                        || !loaded(level,pos.east()) || !loaded(level,pos.west())) { pending = true; continue; }
                BlockState prior = level.getBlockState(pos);
                BlockState priorAbove = level.getBlockState(pos.above());
                BlockState barrier = ConstructionContent.fence.defaultBlockState()
                        .setValue(FenceBlock.NORTH, level.getBlockState(pos.north()).is(ConstructionContent.fence))
                        .setValue(FenceBlock.SOUTH, level.getBlockState(pos.south()).is(ConstructionContent.fence))
                        .setValue(FenceBlock.WEST, level.getBlockState(pos.west()).is(ConstructionContent.fence))
                        .setValue(FenceBlock.EAST, level.getBlockState(pos.east()).is(ConstructionContent.fence));
                if (!VillageDevelopmentProtection.mayPlace(level, site.village(), site.project(), pos, prior, barrier)
                        || (tallPlant(prior) && !VillageDevelopmentProtection.mayPlace(level, site.village(), site.project(),
                                pos.above(), priorAbove, Blocks.AIR.defaultBlockState()))) continue;
                if (!VillageConstructionOccupancy.mayChange(level,pos,prior,barrier) || !safeNeighborConnections(level,pos)) {
                    pending = true; continue;
                }
                // No dropped flowers or free fence items; the original state is saved in its block entity.
                if (tallPlant(prior)) level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                boolean changed = level.setBlock(pos, barrier, 3);
                if (changed && level.getBlockEntity(pos) instanceof ConstructionFenceBlock.Receipt receipt)
                    receipt.own(site.tag(), prior, tallPlant(prior) ? priorAbove : Blocks.AIR.defaultBlockState());
                else {
                    level.setBlock(pos, prior, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    if (tallPlant(prior)) level.setBlock(pos.above(), priorAbove, 3);
                    pending = true; continue;
                }
                entry.fences().add(pos.asLong()); ledger.setDirty(); placed++;
            }
            if (site.working() && !pending && !entry.fenceReady()) entry = ledger.fencePrepared(site.tag());
            List<BlockPos> spots = workSpots(level, site);
            List<BlockPos> reserved = new ArrayList<>();
            List<ConstructionBuilder> assigned = builders.stream().filter(b -> b.job.equals(site.tag()) && !b.leaving)
                    .sorted(Comparator.comparingInt(b -> entryIndex(ledger, site.tag(), b))).toList();
            int area = (site.maximum().getX() - site.corner().getX() + 1) * (site.maximum().getZ() - site.corner().getZ() + 1);
            for (int i = 0; i < assigned.size(); i++) {
                var worker = assigned.get(i);
                // A returning pre-upgrade/orphaned crew cannot enlarge a replacement crew.
                if (!entry.workers().contains(worker.getStringUUID()) && entry.workers().size() >= crewSize(area)
                        || entry.workers().indexOf(worker.getStringUUID()) >= crewSize(area)) {
                    worker.depart(exit(level, worker.blockPosition()));
                    if (entry.workers().remove(worker.getStringUUID())) ledger.setDirty();
                    continue;
                }
                if (!entry.workers().contains(worker.getStringUUID())) { entry.workers().add(worker.getStringUUID()); ledger.setDirty(); }
                BlockPos spot = chooseWorkSpot(level, worker, spots, reserved);
                if (spot != null) {
                    reserved.add(spot);
                    worker.assign(site.tag(), spot, workFocus(level, site, spot), site.working() && entry.fenceReady());
                } else {
                    worker.allowedToWork = false;
                    worker.depart(exit(level, worker.blockPosition()));
                }
            }
            // Finish the currently placeable barrier before planning a NEW arrival through
            // its gates. Existing workers still receive pause/evacuation updates above.
            if (!entry.fenceReady() || !site.working() || spots.isEmpty() || entry.workers().size() >= crewSize(area)) continue;
            List<BlockPos> arrivalSpots = spots.stream()
                    .filter(s -> separated(s, reserved) && stationClear(level, s, null)).toList();
            if (arrivalSpots.isEmpty()) continue;
            // One arrival per four seconds; every candidate must be safe, unseen and path-connected.
            for (int attempt = 0; attempt < 24; attempt++) {
                // A blocked first station must not prevent entry from every other side.
                BlockPos target = arrivalSpots.get(Math.floorMod(level.getGameTime() / 80 + attempt, arrivalSpots.size()));
                double angle = (attempt * 2.399963 + site.tag().hashCode() + level.getGameTime() * .001);
                int radius = 20 + attempt % 4 * 8;
                BlockPos candidate = ground(level, target.offset((int)(Math.cos(angle)*radius), 0, (int)(Math.sin(angle)*radius)), target.getY(), false);
                if (candidate == null || !unseen(level, Vec3.atCenterOf(candidate), 16)
                        || !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(candidate).inflate(1)).isEmpty()) continue;
                var worker = new ConstructionBuilder(ConstructionContent.builder, level);
                worker.setPos(candidate.getX()+.5, candidate.getY(), candidate.getZ()+.5);
                worker.setOnGround(true);
                var path = worker.getNavigation().createPath(target, 0);
                if (path == null || !path.canReach()) { worker.discard(); continue; }
                worker.assign(site.tag(), target, workFocus(level, site, target), true);
                if (level.addFreshEntity(worker)) {
                    DevelopmentEntities.loaded(worker, level); entry.workers().add(worker.getStringUUID()); ledger.setDirty();
                    worker.getNavigation().moveTo(path, .8);
                }
                break;
            }
        }
    }
    static void removed(ConstructionBuilder worker) {
        if (!(worker.level() instanceof ServerLevel level)) return;
        var ledger = ConstructionSiteLedger.get(level); var entry = ledger.entries.get(worker.job);
        if (entry != null && entry.workers().remove(worker.getStringUUID())) ledger.setDirty();
    }
    private static boolean safeNeighborConnections(ServerLevel level,BlockPos pos) {
        for (Direction direction:Direction.Plane.HORIZONTAL) {
            BlockPos neighbor=pos.relative(direction); BlockState state=level.getBlockState(neighbor);
            if (!state.is(ConstructionContent.fence)) continue;
            var toward= switch(direction.getOpposite()) {
                case NORTH -> FenceBlock.NORTH;
                case SOUTH -> FenceBlock.SOUTH;
                case EAST -> FenceBlock.EAST;
                default -> FenceBlock.WEST;
            };
            if (!VillageConstructionOccupancy.mayChange(level,neighbor,state,state.setValue(toward,true))) return false;
        }
        return true;
    }
    static List<BlockPos> perimeter(VillageConstructionActivity.Site site) {
        var result = new ArrayList<BlockPos>();
        int x0=site.corner().getX()-3, x1=site.maximum().getX()+3, z0=site.corner().getZ()-3, z1=site.maximum().getZ()+3;
        int cx=(x0+x1)/2, cz=(z0+z1)/2;
        // Three-wide gates on all sides; preserve a corridor aligned with the project's origin too.
        for (int x=x0;x<=x1;x++) if (Math.abs(x-cx)>1 && Math.abs(x-site.origin().getX())>1) {
            result.add(new BlockPos(x, site.origin().getY(), z0)); result.add(new BlockPos(x, site.origin().getY(), z1));
        }
        for (int z=z0+1;z<z1;z++) if (Math.abs(z-cz)>1 && Math.abs(z-site.origin().getZ())>1) {
            result.add(new BlockPos(x0, site.origin().getY(), z)); result.add(new BlockPos(x1, site.origin().getY(), z));
        }
        return result;
    }
    private static boolean overlapsOther(VillageConstructionActivity.Site site, List<VillageConstructionActivity.Site> all, BlockPos pos) {
        return all.stream().anyMatch(s -> s != site && pos.getX() >= s.corner().getX()-1 && pos.getX() <= s.maximum().getX()+1
                && pos.getZ() >= s.corner().getZ()-1 && pos.getZ() <= s.maximum().getZ()+1);
    }
    static boolean replaceablePlant(BlockState state) {
        return state.isAir() || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SNOW) || tallPlant(state)
                || (state.is(BlockTags.SMALL_FLOWERS) && !(state.getBlock() instanceof DoublePlantBlock));
    }
    static boolean tallPlant(BlockState state) {
        return (state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN))
                && state.getValue(DoublePlantBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
    }
    static boolean matchingUpper(BlockState lower, BlockState upper) {
        return tallPlant(lower) && upper.is(lower.getBlock())
                && upper.getValue(DoublePlantBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
    }
    static boolean loaded(ServerLevel level, BlockPos pos) { return level.hasChunk(pos.getX()>>4,pos.getZ()>>4); }
    static BlockPos ground(ServerLevel level, BlockPos column, int referenceY, boolean fence) {
        if (!loaded(level,column)) return null;
        for (int y=Math.min(level.getMaxY()-2,referenceY+8); y>=Math.max(level.getMinY()+1,referenceY-8); y--) {
            BlockPos p=new BlockPos(column.getX(),y,column.getZ());
            BlockState below=level.getBlockState(p.below()), here=level.getBlockState(p);
            BlockState above = level.getBlockState(p.above());
            boolean headroom = tallPlant(here) ? fence && matchingUpper(here, above) : above.isAir();
            if (!replaceablePlant(here) || !headroom || !below.isFaceSturdy(level,p.below(),Direction.UP)
                    || !below.getFluidState().isEmpty()) continue;
            if (fence && (!VillageSitePreparation.dryNaturalGround(below) || below.is(Blocks.FARMLAND))) continue;
            if (below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.CAMPFIRE) || below.is(Blocks.CACTUS)) continue;
            return p;
        }
        return null;
    }
    private static int entryIndex(ConstructionSiteLedger ledger, String tag, ConstructionBuilder worker) {
        int index = ledger.entry(tag).workers().indexOf(worker.getStringUUID());
        return index < 0 ? Integer.MAX_VALUE : index;
    }
    static boolean separated(BlockPos spot, List<BlockPos> reserved) {
        return reserved.stream().noneMatch(other -> {
            long dx = (long)spot.getX() - other.getX(), dz = (long)spot.getZ() - other.getZ();
            return dx * dx + dz * dz < 9;
        });
    }
    private static boolean stationClear(ServerLevel level, BlockPos spot, ConstructionBuilder worker) {
        return level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new AABB(spot).inflate(.25, 0, .25),
                e -> e != worker && e.isAlive() && !e.isSpectator()).isEmpty();
    }
    static BlockPos chooseWorkSpot(ServerLevel level, ConstructionBuilder worker, List<BlockPos> spots, List<BlockPos> reserved) {
        // Retain a valid assignment across update ticks and reloads. A failed route is briefly
        // excluded so another side can be tried instead of endlessly orbiting the same obstacle.
        if (!worker.needsNewWorkSpot() && spots.contains(worker.destination)
                && separated(worker.destination, reserved) && stationClear(level, worker.destination, worker))
            return worker.destination;
        int paths = 0;
        for (BlockPos spot : spots.stream().sorted(Comparator.comparingDouble(
                s -> worker.distanceToSqr(Vec3.atBottomCenterOf(s)))).toList()) {
            if (!separated(spot, reserved) || worker.avoids(spot) || !stationClear(level, spot, worker)) continue;
            if (++paths > 6) break;
            var path = worker.getNavigation().createPath(spot, 0);
            if (path != null && path.canReach()) return spot;
        }
        return null;
    }
    static List<BlockPos> workSpots(ServerLevel level, VillageConstructionActivity.Site site) {
        var result = new ArrayList<BlockPos>();
        int x0=site.corner().getX(), x1=site.maximum().getX(), z0=site.corner().getZ(), z1=site.maximum().getZ();
        // Work from stable exterior aisles, never occupy a future wall/room or share a station.
        // Multiple positions per side keep an inaccessible gate from collapsing the whole crew.
        for (int fraction : new int[] {2, 1, 3}) {
            int x=x0+(x1-x0)*fraction/4, z=z0+(z1-z0)*fraction/4;
            for (BlockPos column : List.of(new BlockPos(x,0,z0-1), new BlockPos(x,0,z1+1),
                    new BlockPos(x0-1,0,z), new BlockPos(x1+1,0,z))) {
                BlockPos spot=ground(level,column,site.origin().getY(),false);
                if (spot!=null && !result.contains(spot)) result.add(spot);
            }
        }
        return result;
    }
    static BlockPos workFocus(ServerLevel level, VillageConstructionActivity.Site site, BlockPos spot) {
        int x=Math.clamp(spot.getX(),site.corner().getX(),site.maximum().getX());
        int z=Math.clamp(spot.getZ(),site.corner().getZ(),site.maximum().getZ());
        // Face the nearby wall/foundation on this worker's side, not a shared point across the crew.
        for (int dy : new int[] {1,0,2,-1}) {
            BlockPos focus=new BlockPos(x,spot.getY()+dy,z);
            if (loaded(level,focus) && !level.getBlockState(focus).isAir()) return focus;
        }
        return new BlockPos(x,spot.getY(),z);
    }
    static boolean unseen(ServerLevel level, Vec3 position, double minimumDistance) {
        for (var player:level.players()) {
            if (player.isSpectator()) continue;
            Vec3 delta=position.subtract(player.getEyePosition()); double distance=delta.length();
            if (distance<minimumDistance) return false;
            if (distance>96) continue;
            // Ray checks must not pull an unloaded chunk into memory for a cosmetic arrival.
            for (int step=0;step<=Math.ceil(distance);step++)
                if (!loaded(level,BlockPos.containing(player.getEyePosition().add(delta.scale(step/Math.max(1,Math.ceil(distance))))))) return false;
            // Broad conservative view cone. Ray obstruction can also conceal an arrival.
            if (player.getViewVector(1).dot(delta.normalize()) > -.15
                    && level.clip(new ClipContext(player.getEyePosition(),position,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()==HitResult.Type.MISS) return false;
        }
        return true;
    }
    static BlockPos exit(ServerLevel level, BlockPos from) {
        for (int i=0;i<24;i++) {
            double angle=i*2.399963+level.getGameTime()*.001;
            BlockPos candidate=ground(level,from.offset((int)(Math.cos(angle)*28),0,(int)(Math.sin(angle)*28)),from.getY(),false);
            if(candidate!=null && unseen(level,Vec3.atCenterOf(candidate),16)) return candidate;
        }
        return from.offset(24,0,24);
    }
}
