package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.*;

/** Bounded district housing surveys and UUID census; never force-loads a chunk. */
final class VillagePopulationEnvironment {
    private static final LinkedHashMap<UUID,Scan> PENDING=new LinkedHashMap<>();
    private static final Map<UUID,String> STATUS=new HashMap<>();
    private static final Map<UUID,Long> LAST_ARRIVAL=new HashMap<>();
    private static final Map<UUID,Integer> NEXT_BED=new HashMap<>();
    static void reset() { PENDING.clear(); STATUS.clear(); LAST_ARRIVAL.clear(); NEXT_BED.clear(); }
    static String status(UUID id) { return STATUS.getOrDefault(id,"Housing survey pending"); }

    static void survey(ServerLevel level, EconomyService economy, EmeraldConfig config) {
        if (economy.isCatchingUp()) return;
        List<EconomyState.VillageRecord> villages=economy.villageSnapshots().stream()
                .map(EconomyService.VillageSnapshot::village)
                .filter(v -> v.dimensionKey.equals(level.dimension().identifier().toString())).toList();
        Map<UUID,UUID> recorded=new HashMap<>();
        for (var v:villages) for (UUID resident:v.residents.keySet()) recorded.putIfAbsent(resident,v.villageId);
        Map<UUID,List<EconomyService.ResidentObservation>> observations=new HashMap<>();
        for (Entity entity:DevelopmentEntities.snapshot(level)) {
            if (entity instanceof net.minecraft.world.entity.monster.zombie.ZombieVillager zombie && zombie.isAlive()) {
                UUID infectedOwner=recorded.get(zombie.getUUID());
                if(infectedOwner==null) infectedOwner=economy.canonicalVillageId(VillageProsperityManager.villageId(zombie));
                if(infectedOwner!=null && economy.hasVillage(infectedOwner))
                    economy.recordResidentStatus(infectedOwner,zombie.getUUID(),"minecraft:none",zombie.blockPosition().asLong(),
                            VillageProsperityEngine.ResidentStatus.INFECTED);
                continue;
            }
            if (!(entity instanceof Villager resident) || !resident.isAlive() || BankerAccess.isBanker(resident)) continue;
            var home=resident.getBrain().getMemory(MemoryModuleType.HOME)
                    .filter(h -> h.dimension().equals(level.dimension())).map(GlobalPos::pos).orElse(null);
            UUID id=recorded.get(resident.getUUID());
            if (id == null) id=economy.canonicalVillageId(VillageProsperityManager.villageId(resident));
            UUID homeOwner=home==null ? null : owner(home,villages);
            if(id!=null && homeOwner!=null && !id.equals(homeOwner) && loaded(level,home)
                    && level.getBlockState(home).getBlock() instanceof BedBlock
                    && economy.transferResidentHome(id,homeOwner,resident.getUUID(),home.asLong())) {
                id=homeOwner;recorded.put(resident.getUUID(),id);
            }
            if (id == null || !economy.hasVillage(id))
                id=owner(home == null ? resident.blockPosition() : home,villages);
            if (id == null) continue;
            VillageProsperityManager.assignVillage(resident,id);
            observations.computeIfAbsent(id,unused -> new ArrayList<>()).add(
                    new EconomyService.ResidentObservation(resident.getUUID(),
                            resident.getVillagerData().profession().unwrapKey().map(k -> k.identifier().toString()).orElse("minecraft:none"),
                            resident.blockPosition().asLong(),home == null ? 0 : home.asLong()));
        }
        for (var village:villages) {
            // Loaded UUIDs outside the rectangle still belong to their saved district.
            economy.observeDistrictResidents(village.villageId,observations.getOrDefault(village.villageId,List.of()));
            var b=VillageDistrictCoverage.of(village);
            boolean nearby=level.players().stream().anyMatch(p -> p.getX() >= b.minX()-128 && p.getX() <= b.maxX()+128
                    && p.getZ() >= b.minZ()-128 && p.getZ() <= b.maxZ()+128
                    || village.residents.values().stream().anyMatch(r -> r.homePos!=0
                        && Math.abs(p.getX()-BlockPos.of(r.homePos).getX())<=144
                        && Math.abs(p.getZ()-BlockPos.of(r.homePos).getZ())<=144));
            if (!nearby) continue;
            VillageFoodEnvironment.schedule(level,economy,village);
            if (!PENDING.containsKey(village.villageId) && PENDING.size()<128)
                PENDING.put(village.villageId,new Scan(level,village,villages));
        }
    }

    static UUID owner(BlockPos pos,List<EconomyState.VillageRecord> villages) {
        UUID owner=null; double best=Double.MAX_VALUE; boolean housingOwner=false;
        for (var v:villages) {
            if(v.organicTerritory && (!VillageTerritory.contains(v,pos.getX(),pos.getZ())
                    || !VillageTerritory.mayOwn(v,villages,VillageTerritory.parcel(pos.asLong())))) continue;
            var b=VillageDistrictCoverage.of(v);
            boolean registeredHome=v.residents.values().stream().anyMatch(r -> (r.homePos!=0 || r.immigrant)
                    && r.status!=VillageProsperityEngine.ResidentStatus.DEAD
                    && r.status!=VillageProsperityEngine.ResidentStatus.EMIGRATED
                    && Math.abs((long)pos.getX()-BlockPos.of(r.homePos).getX())<=16
                    && Math.abs((long)pos.getZ()-BlockPos.of(r.homePos).getZ())<=16);
            if(!registeredHome && (pos.getX()<b.minX() || pos.getX()>b.maxX() || pos.getZ()<b.minZ() || pos.getZ()>b.maxZ())) continue;
            boolean housing=registeredHome || v.projects.stream().anyMatch(p -> p.originPos!=0 && p.type.housingGain()>0
                    && p.boundsMinPos!=0 && p.boundsMaxPos!=0 && inside(pos,BlockPos.of(p.boundsMinPos),BlockPos.of(p.boundsMaxPos)));
            BlockPos center=BlockPos.of(v.centerPos);
            double dx=(double)pos.getX()-center.getX(),dz=(double)pos.getZ()-center.getZ(),distance=dx*dx+dz*dz;
            if(owner==null || housing && !housingOwner || housing==housingOwner
                    && (distance<best || distance==best && v.villageId.compareTo(owner)<0)) {
                owner=v.villageId;best=distance;housingOwner=housing;
            }
        }
        return owner;
    }
    private static boolean inside(BlockPos p,BlockPos low,BlockPos high) {
        return p.getX()>=low.getX() && p.getX()<=high.getX() && p.getY()>=low.getY() && p.getY()<=high.getY()
                && p.getZ()>=low.getZ() && p.getZ()<=high.getZ();
    }

    static void tick(MinecraftServer server,EconomyService economy,EmeraldConfig config) {
        if(PENDING.isEmpty() || economy.isCatchingUp()) return;
        var first=PENDING.entrySet().iterator().next();
        UUID id=first.getKey();Scan scan=first.getValue();PENDING.remove(id);
        if(scan.level.getServer()!=server || !economy.hasVillage(id))return;
        if(!scan.advance(BackgroundSurveyBudget.cells(server,2048))) {PENDING.put(id,scan);return;}
        economy.observeDistrictHousing(id,scan.completed);
        var current=economy.villageSnapshot(id);
        if(current!=null && config.villageVisualProgressionEnabled() && !config.forcedVillageDevelopment())
            attemptArrival(scan.level,economy,current.village());
    }

    static boolean loaded(ServerLevel level,BlockPos p) {
        return p.getY()>=level.getMinY() && p.getY()<=level.getMaxY()
                && level.hasChunk(p.getX()>>4,p.getZ()>>4);
    }
    static boolean bedHead(BlockState s) {
        return s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART)==BedPart.HEAD;
    }
    static boolean intactBed(ServerLevel level,BlockPos head) {
        if(!loaded(level,head))return false;
        BlockState state=level.getBlockState(head);
        if(!bedHead(state) || state.getValue(BedBlock.OCCUPIED))return false;
        BlockPos foot=head.relative(state.getValue(BedBlock.FACING).getOpposite());
        if(!loaded(level,foot))return false;
        BlockState other=level.getBlockState(foot);
        return other.is(state.getBlock()) && other.getValue(BedBlock.PART)==BedPart.FOOT
                && other.getValue(BedBlock.FACING)==state.getValue(BedBlock.FACING);
    }

    /** Threats require proximity to the actual landing, plus an unobstructed collision ray. */
    static boolean dangerous(ServerLevel level,BlockPos feet,Entity context) {
        return dangerous(level,feet,context,level.getEntitiesOfClass(
                Monster.class,new AABB(feet).inflate(12,8,12),Entity::isAlive));
    }

    static boolean dangerous(ServerLevel level,BlockPos feet,Entity context,List<Monster> threats) {
        Vec3 eye=Vec3.atBottomCenterOf(feet).add(0,1.6,0);
        for(Monster monster:threats) {
            if(!monster.isAlive())continue;
            if(monster.distanceToSqr(eye)>144)continue;
            Vec3 target=monster.getEyePosition();
            // No ray may cross an unloaded column, even at a district/chunk boundary.
            boolean complete=true;
            for(int i=0;i<=16;i++) if(!loaded(level,BlockPos.containing(eye.lerp(target,i/16.0)))) {complete=false;break;}
            if(!complete)return true;
            if(level.clip(new ClipContext(eye,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,context))
                    .getType()==HitResult.Type.MISS)return true;
        }
        return false;
    }

    static void attemptArrival(ServerLevel level,EconomyService economy,EconomyState.VillageRecord village) {
        UUID id=village.villageId;
        long now=level.getGameTime();
        if(!VillageMaterializationPolicy.settlerAttemptDue(now,LAST_ARRIVAL.get(id),
                EmeraldConfig.current().villageSettlerSpawnIntervalTicks()))return;
        LAST_ARRIVAL.put(id,now);
        if(village.pendingSettlers<=0) {STATUS.put(id,"No queued arrivals");return;}
        if(village.lifecycle!=VillageProsperityEngine.Lifecycle.ACTIVE
                && village.lifecycle!=VillageProsperityEngine.Lifecycle.RECOVERING
                && !village.districtFounding) {STATUS.put(id,"Arrivals paused by district conditions");return;}
        if(village.foodSupply<Math.max(12,(village.population+1)*6.0)) {
            STATUS.put(id,"Queued arrivals need food");return;
        }
        int physicalBeds=village.housingChunks.values().stream().mapToInt(List::size).sum();
        if(physicalBeds<=village.population) {STATUS.put(id,"Waiting for spare surveyed physical beds");return;}
        Set<Long> reserved=new HashSet<>();
        for(var r:village.residents.values()) if(r.status!=VillageProsperityEngine.ResidentStatus.DEAD
                && r.status!=VillageProsperityEngine.ResidentStatus.EMIGRATED) reserved.add(r.homePos);
        List<EconomyState.VillageRecord> districts=economy.villageSnapshots().stream()
                .map(EconomyService.VillageSnapshot::village).filter(v -> v.dimensionKey.equals(village.dimensionKey)).toList();
        int intact=0, reachable=0;
        List<Long> candidates=village.housingChunks.values().stream().flatMap(List::stream).distinct().toList();
        int start=Math.floorMod(NEXT_BED.getOrDefault(id,0),Math.max(1,candidates.size()));
        for(int step=0;step<Math.min(256,candidates.size());step++) {
            int index=(start+step)%candidates.size();
            NEXT_BED.put(id,index+1); // Unloaded/unsafe early homes cannot starve later loaded homes.
            long packed=candidates.get(index);
            if(reserved.contains(packed))continue;
            BlockPos bed=BlockPos.of(packed);
            if(!id.equals(owner(bed,districts)) || !intactBed(level,bed))continue;
            intact++;
            Villager settler=EntityTypes.VILLAGER.create(level,EntitySpawnReason.NATURAL);
            if(settler==null)return;
            BlockPos spawn=findArrival(level,bed,settler);
            if(spawn==null) {settler.discard();continue;}
            reachable++;
            var claim=level.getPoiManager().take(holder -> holder.is(PoiTypes.HOME),
                    (holder,pos)->pos.equals(bed),bed,1);
            if(claim.isEmpty()) {settler.discard();continue;}
            settler.teleportTo(spawn.getX()+.5,spawn.getY(),spawn.getZ()+.5);
            settler.setPersistenceRequired();
            settler.getBrain().setMemory(MemoryModuleType.HOME,GlobalPos.of(level.dimension(),bed));
            VillageProsperityManager.assignVillage(settler,id);
            if(!economy.claimSettlerArrival(id,settler.getUUID(),packed,spawn.asLong())) {
                settler.getBrain().eraseMemory(MemoryModuleType.HOME);
                level.getPoiManager().release(bed);settler.discard();
                STATUS.put(id,"Arrival claim refused: "+economy.lastError());return;
            }
            boolean added;
            try { added=level.addFreshEntity(settler); }
            catch (RuntimeException uncertain) {
                // An external entity hook can throw after insertion. Never replay an ambiguous
                // claim or discard a possibly inserted villager; its UUID census resolves it.
                STATUS.put(id,"Arrival insertion uncertain; saved identity retained for census");
                org.slf4j.LoggerFactory.getLogger("the_emerald_standard_population")
                        .warn("Settler insertion uncertain; retaining saved arrival identity",uncertain);
                return;
            }
            if(!added) {
                settler.getBrain().eraseMemory(MemoryModuleType.HOME);
                level.getPoiManager().release(bed);settler.discard();
                economy.cancelSettlerArrival(id,settler.getUUID());
            }
            STATUS.put(id,added ? "Arrival placed; awaiting UUID census" : "Arrival insertion failed; claim cancelled");
            if(added)DebugFlightRecorder.recordSettler(level,id,settler.getUUID(),spawn);
            return; // One arrival per completed survey/normal paced attempt, never an offline burst.
        }
        STATUS.put(id,"Queued: "+intact+" free intact loaded beds; "+reachable
                +" safe reachable landings; remaining beds occupied, unloaded, obstructed, dangerous or already claimed");
    }

    static BlockPos findArrival(ServerLevel level,BlockPos bed,Villager settler) {
        for(int radius=1;radius<=2;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            BlockPos p=bed.offset(dx,0,dz);
            if(!loaded(level,p) || !loaded(level,p.above()) || !loaded(level,p.below()))continue;
            if(!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()
                    || !level.getFluidState(p).isEmpty() || !level.getFluidState(p.above()).isEmpty()
                    || !level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP))continue;
            settler.teleportTo(p.getX()+.5,p.getY(),p.getZ()+.5);
            if(!level.noCollision(settler) || !level.getEntities(settler,settler.getBoundingBox(),Entity::isAlive).isEmpty())continue;
            if(level.getBlockState(p.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                    || level.getBlockState(p.below()).is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                    || level.getBlockState(p.below()).is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE))continue;
            // A not-yet-added entity has never had a physics tick; the verified support below
            // is needed before GroundPathNavigation will accept a path query.
            settler.setOnGround(true);
            var path=settler.getNavigation().createPath(bed,1);
            if(path!=null && path.canReach() && !dangerous(level,p,settler))return p;
        }
        return null;
    }

    static final class Scan {
        final ServerLevel level;
        final EconomyState.VillageRecord village;
        final List<EconomyState.VillageRecord> neighbors;
        final int minX,minZ,width,depth;
        final List<Long> additionalChunks=new ArrayList<>();
        long cursor;int section,cell;
        final List<Long> beds=new ArrayList<>();
        final Map<Long,List<Long>> completed=new LinkedHashMap<>();
        Scan(ServerLevel level,EconomyState.VillageRecord village,List<EconomyState.VillageRecord> neighbors) {
            this.level=level;this.village=village;this.neighbors=neighbors;
            var b=VillageDistrictCoverage.of(village);
            minX=b.minX()>>4;minZ=b.minZ()>>4;width=(b.maxX()>>4)-minX+1;depth=(b.maxZ()>>4)-minZ+1;
            Set<Long> extras=new LinkedHashSet<>();
            for(long previous:village.housingChunks.keySet()) {
                int cx=(int)previous,cz=(int)(previous>>32);
                if(cx<minX || cx>=minX+width || cz<minZ || cz>=minZ+depth)extras.add(previous);
            }
            for(var resident:village.residents.values()) {
                if(resident.homePos==0 && !resident.immigrant
                        || resident.status==VillageProsperityEngine.ResidentStatus.DEAD
                        || resident.status==VillageProsperityEngine.ResidentStatus.EMIGRATED)continue;
                BlockPos h=BlockPos.of(resident.homePos);
                for(int cx=(h.getX()-16)>>4;cx<=(h.getX()+16)>>4;cx++)
                    for(int cz=(h.getZ()-16)>>4;cz<=(h.getZ()+16)>>4;cz++) {
                        if(cx>=minX && cx<minX+width && cz>=minZ && cz<minZ+depth)continue;
                        if(extras.size()<4096)extras.add(((long)cx&0xffffffffL)|((long)cz<<32));
                    }
            }
            additionalChunks.addAll(extras);
        }
        boolean advance(int budget) {
            long rectangle=(long)width*depth,total=rectangle+additionalChunks.size();
            for(int work=0;cursor<total && work<budget;work++) {
                long extra=cursor<rectangle ? 0 : additionalChunks.get((int)(cursor-rectangle));
                int cx=cursor<rectangle ? minX+(int)(cursor%width) : (int)extra;
                int cz=cursor<rectangle ? minZ+(int)(cursor/width) : (int)(extra>>32);
                if(!level.hasChunk(cx,cz)) {next();continue;}
                var chunk=level.getChunk(cx,cz);
                if(section>=chunk.getSections().length) {
                    completed.put(((long)cx & 0xffffffffL)|((long)cz<<32),List.copyOf(beds));next();continue;
                }
                var s=chunk.getSection(section);
                if(cell==0 && (s.hasOnlyAir() || !s.maybeHas(VillagePopulationEnvironment::bedHead))) {section++;continue;}
                int x=cell&15,z=(cell>>4)&15,y=cell>>8;
                BlockState state=s.getBlockState(x,y,z);
                if(bedHead(state)) {
                    BlockPos p=new BlockPos(cx*16+x,level.getMinY()+section*16+y,cz*16+z);
                    if(village.villageId.equals(owner(p,neighbors)) && beds.size()<4096)beds.add(p.asLong());
                }
                if(++cell==4096){cell=0;section++;}
            }
            return cursor>=total;
        }
        void next(){cursor++;section=cell=0;beds.clear();}
    }
}
