package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

final class VillageConstructionActivitySelfTest {
    static void verify(ServerLevel level) throws Exception {
        BlockPos origin = new BlockPos(2300, level.getMaxY() - 35, 2300);
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        List<Entity> cleanup = new ArrayList<>();
        UUID village = UUID.randomUUID();
        String tag = VillageConstructionActivity.JOB + village + ":1";
        var site = new VillageConstructionActivity.Site(tag,village,1,origin,origin,origin.offset(10,6,10),true);
        net.minecraft.server.level.ServerPlayer viewer=null;
        try {
            for (int x=-42;x<=52;x++) for (int z=-42;z<=52;z++) {
                level.getChunk(origin.offset(x,0,z));
                for (int y=-3;y<=3;y++) {
                    BlockPos pos=origin.offset(x,y,z); before.put(pos,level.getBlockState(pos));
                    level.setBlock(pos,(y < -1 ? Blocks.STONE : y==-1?Blocks.GRASS_BLOCK:Blocks.AIR).defaultBlockState(),18);
                }
            }
            // The first station has valid footing/headroom but sits atop an un-climbable pillar.
            // Arrival must try the other stations rather than repeatedly failing this one.
            BlockPos isolatedStation=origin.offset(5,4,-1);
            for(int y=0;y<4;y++) level.setBlock(origin.offset(5,y,-1),Blocks.STONE.defaultBlockState(),18);
            require(ConstructionSitePresentation.workSpots(level,site).getFirst().equals(isolatedStation),
                    "unreachable first-station fixture remains a valid ground candidate");
            var villager=VillageWalkingSelfTest.walker(level,origin); level.addFreshEntity(villager); cleanup.add(villager);
            var unrelated=new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY,level);
            unrelated.setPos(origin.getX(),origin.getY(),origin.getZ()); level.addFreshEntity(unrelated); cleanup.add(unrelated);
            var ring=ConstructionSitePresentation.perimeter(site);
            BlockPos flower=ring.get(0), edited=ring.get(1), broken=ring.get(2), claimed=ring.get(3);
            BlockPos grass=ring.get(4), fern=ring.get(5), upperEdit=ring.get(6), occupied=ring.get(7), upperClaim=ring.get(8);
            level.setBlock(flower,Blocks.DANDELION.defaultBlockState(),18);
            plantPair(level, grass, Blocks.TALL_GRASS);
            plantPair(level, fern, Blocks.LARGE_FERN);
            plantPair(level, upperEdit, Blocks.TALL_GRASS);
            plantPair(level, upperClaim, Blocks.LARGE_FERN);
            villager.setPos(occupied.getX()+.5,occupied.getY(),occupied.getZ()+.5);
            try(var guard=VillageDevelopmentProtection.register(c -> !c.position().equals(claimed)
                    && !c.position().equals(upperClaim.above()))) {
                VillageConstructionActivity.update(level,List.of(site));
                var first=ConstructionSiteLedger.get(level).entries.get(tag);
                require(!first.fenceReady() && first.fences().size()==16 && first.workers().isEmpty(),
                        "first bounded fence batch cannot start building or summon workers");
                VillageConstructionActivity.update(level,List.of(new VillageConstructionActivity.Site(
                        tag,village,1,origin,origin,origin.offset(10,6,10),false)));
                require(!first.fenceReady() && first.fences().size()==16,"pause freezes incomplete fence preparation");
                for(int i=0;i<12;i++) VillageConstructionActivity.update(level,List.of(site));
                require(!ConstructionSitePresentation.fenceReady(level,tag) && level.getBlockState(occupied).isAir(),
                        "occupied perimeter waits instead of declaring an unfinished fence ready");
                villager.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);
                for(int i=0;i<4;i++) VillageConstructionActivity.update(level,List.of(site));
            }
            require(ConstructionSitePresentation.fenceReady(level,tag) && level.getBlockState(occupied).is(ConstructionContent.fence),
                    "vacated perimeter finishes before authorizing structural work");
            require(level.getBlockState(claimed).isAir(),"protected perimeter skipped");
            require(ConstructionSitePresentation.matchingUpper(level.getBlockState(upperClaim),level.getBlockState(upperClaim.above())),
                    "protection on upper plant cell preserves the entire pair without stalling");
            require(level.getBlockState(flower).is(ConstructionContent.fence),"small flower replaced with real custom fence");
            require(level.getBlockState(grass).is(ConstructionContent.fence) && level.getBlockState(grass.above()).isAir()
                    && level.getBlockState(fern).is(ConstructionContent.fence) && level.getBlockState(fern.above()).isAir(),
                    "vanilla tall grass and large fern pairs no longer leave avoidable gaps");
            reloadReceipt(level,grass); reloadReceipt(level,fern);
            level.setBlock(upperEdit.above(),Blocks.CHEST.defaultBlockState(),18);
            for (BlockPos p:ring) if(level.getBlockState(p).is(ConstructionContent.fence)) {
                BlockState fence=level.getBlockState(p);
                require(fence.getValue(FenceBlock.NORTH)==level.getBlockState(p.north()).is(ConstructionContent.fence)
                        && fence.getValue(FenceBlock.SOUTH)==level.getBlockState(p.south()).is(ConstructionContent.fence)
                        && fence.getValue(FenceBlock.EAST)==level.getBlockState(p.east()).is(ConstructionContent.fence)
                        && fence.getValue(FenceBlock.WEST)==level.getBlockState(p.west()).is(ConstructionContent.fence),
                        "all real caution rails connect to their neighboring segments");
            }
            var receipt=(ConstructionFenceBlock.Receipt)level.getBlockEntity(flower);
            var saved=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());
            receipt.saveWithoutMetadata(saved);
            var reloadedReceipt=new ConstructionFenceBlock.Receipt(flower,level.getBlockState(flower));
            reloadedReceipt.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),saved.buildResult()));
            require(reloadedReceipt.job().equals(tag),"fence ownership survives chunk NBT reload");
            level.setBlockEntity(reloadedReceipt);
            List<ConstructionBuilder> crew=DevelopmentEntities.snapshot(level).stream().filter(e->e instanceof ConstructionBuilder)
                    .map(e->(ConstructionBuilder)e).filter(b->b.job.equals(tag)).toList(); cleanup.addAll(crew);
            require(crew.size()==2,"medium site gets two dedicated builders, actual="+crew.size());
            require(crew.stream().noneMatch(b->b.destination.equals(isolatedStation)),
                    "unreachable first station does not prevent arrivals at reachable sides");
            require(villager.entityTags().stream().noneMatch(t->t.startsWith(VillageConstructionActivity.JOB)),"real villagers are not recruited");
            require(ConstructionSitePresentation.crewSize(30)==1 && ConstructionSitePresentation.crewSize(300)==3
                    && ConstructionSitePresentation.crewSize(800)==4,"size-scaled crew bounds");
            var ledger=ConstructionSiteLedger.get(level);
            var encoded=ConstructionSiteLedger.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,ledger).getOrThrow();
            var decoded=ConstructionSiteLedger.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,encoded).getOrThrow();
            require(decoded.entries.get(tag).workers().size()==2 && decoded.entries.get(tag).fences().contains(flower.asLong())
                    && decoded.entries.get(tag).fenceReady(),"crew/fence readiness ledger survives reload");
            var oldEntry=new net.minecraft.nbt.CompoundTag();
            oldEntry.put("fences",new net.minecraft.nbt.ListTag()); oldEntry.put("workers",new net.minecraft.nbt.ListTag());
            require(!ConstructionSiteLedger.Entry.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,oldEntry).getOrThrow().fenceReady(),
                    "legacy ledgers safely resume fence preparation");
            var worker=crew.getFirst();
            require(level.getBlockState(flower).is(net.minecraft.tags.BlockTags.FENCES),"custom barrier is in the native fence pathfinding tag");
            require(net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeStatic(worker,flower)
                    ==net.minecraft.world.level.pathfinder.PathType.FENCE,"custom barrier is a forbidden fence navigation node");
            var workerSaved=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess()); worker.saveWithoutId(workerSaved);
            var reload=new ConstructionBuilder(ConstructionContent.builder,level);
            reload.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),workerSaved.buildResult()));
            require(reload.job.equals(tag) && reload.destination.equals(worker.destination),"builder assignment saved natively");
            verifyWorkwear(level,worker,workerSaved.buildResult());
            // Do not add the duplicate test entity: a real chunk reload preserves the same UUID.
            long originalTime=level.getGameTime();
            var clock=(ServerLevelData)level.getLevelData();
            try {
                for(int tick=0;tick<1200 && !worker.hammering() && worker.isAlive();tick++) {
                    clock.setGameTime(originalTime+tick+1);
                    // ServerLevel normally advances entity age before calling Entity.tick().
                    worker.tickCount++; worker.tick();
                }
            } finally { clock.setGameTime(originalTime); }
            require(worker.hammering(),"builder walks into the site then starts synced hammer animation: "
                    +worker.position()+" -> "+worker.destination+" allowed="+worker.allowedToWork+" alive="+worker.isAlive()+" path="+pathDescription(worker));
            for(BlockPos offset:List.of(new BlockPos(25,0,0),new BlockPos(-25,0,0),new BlockPos(0,0,25),new BlockPos(0,0,-25),
                    new BlockPos(25,0,25),new BlockPos(25,0,-25),new BlockPos(-25,0,25),new BlockPos(-25,0,-25))) {
                var approach=new ConstructionBuilder(ConstructionContent.builder,level); cleanup.add(approach);
                BlockPos start=origin.offset(5,0,5).offset(offset);
                approach.setPos(start.getX()+.5,start.getY(),start.getZ()+.5); approach.setOnGround(true);
                approach.assign(tag,worker.destination,origin,true); level.addFreshEntity(approach);
                try {
                    for(int tick=0;tick<1200 && !approach.hammering() && approach.isAlive();tick++) {
                        clock.setGameTime(originalTime+tick+1); approach.tickCount++; approach.tick();
                    }
                } finally { clock.setGameTime(originalTime); }
                require(approach.hammering(),"real gate approach from "+offset+": "+approach.position()+" path="+pathDescription(approach));
                approach.discard();
            }
            // Exercise the real manager across many old ten-second rotation boundaries.
            for (var b : crew) { b.setPos(b.destination.getX()+.5,b.destination.getY(),b.destination.getZ()+.5); b.setOnGround(true); }
            require(ConstructionSitePresentation.separated(crew.getFirst().destination,List.of(crew.getLast().destination)),
                    "workers receive distinct spaced stations");
            var stableTargets=crew.stream().map(b->b.destination).toList();
            int hammerTicks=0;
            int requests=crew.stream().mapToInt(b->b.pathRequests).sum();
            try {
                for(int tick=0;tick<480;tick++) {
                    clock.setGameTime(originalTime+tick+1);
                    if(tick%80==0) VillageConstructionActivity.update(level,List.of(site));
                    for(var b:crew) { b.tickCount++; b.tick(); if(b.hammering()) hammerTicks++; }
                }
            } finally { clock.setGameTime(originalTime); }
            require(crew.stream().map(b->b.destination).toList().equals(stableTargets),"working stations do not rotate with world time");
            require(hammerTicks>=800,"settled crew visibly hammers most of active time: "+hammerTicks);
            require(crew.stream().mapToInt(b->b.pathRequests).sum()-requests<8,"settled workers do not keep rebuilding paths");
            for(var b:crew) {
                require(!b.focus.equals(origin),"workers face local work rather than shared origin");
                require(b.destination.getX()<origin.getX() || b.destination.getX()>origin.getX()+10
                        || b.destination.getZ()<origin.getZ() || b.destination.getZ()>origin.getZ()+10,
                        "workstations remain outside future structure cells");
            }
            BlockPos failed=worker.destination;
            level.setBlock(failed,Blocks.STONE.defaultBlockState(),18);
            VillageConstructionActivity.update(level,List.of(site));
            require(!worker.destination.equals(failed) && !worker.leaving,"changed station selects a reachable alternative");
            level.setBlock(failed,Blocks.AIR.defaultBlockState(),18);
            worker.allowedToWork=false; worker.tick(); require(!worker.hammering(),"paused work stops animation");
            worker.setPos(origin.getX()+5.5,origin.getY(),origin.getZ()+5.5);
            var waitingSite=new VillageConstructionActivity.Site(tag,village,1,origin,origin,origin.offset(10,6,10),false);
            VillageConstructionActivity.update(level,List.of(waitingSite));
            require(!worker.leaving && ledger.entries.get(tag).workers().size() == 2
                    && level.getBlockState(flower).is(ConstructionContent.fence),
                    "pause retains crew identities and fence ownership");
            // Simulate the lifecycle index not containing an unloaded worker. The ledger must
            // still reserve its slot when the site resumes, even though it is not queryable.
            DevelopmentEntities.unloaded(crew.getLast(), level);
            VillageConstructionActivity.update(level, List.of(site));
            require(ledger.entries.get(tag).workers().size() == 2, "resume does not replace an unloaded worker");
            DevelopmentEntities.loaded(crew.getLast(), level);
            var surplus = new ConstructionBuilder(ConstructionContent.builder, level); cleanup.add(surplus);
            surplus.setPos(origin.getX()+2.5,origin.getY(),origin.getZ()+2.5);
            surplus.assign(tag, origin, origin, true); level.addFreshEntity(surplus); DevelopmentEntities.loaded(surplus, level);
            VillageConstructionActivity.update(level, List.of(site));
            require(surplus.leaving && ledger.entries.get(tag).workers().size() == 2,
                    "returning orphaned crew cannot exceed site quota");
            surplus.discard();
            VillageConstructionActivity.update(level, List.of(waitingSite));
            require(!worker.allowedToWork && (worker.destination.getX()<origin.getX() || worker.destination.getX()>origin.getX()+10
                    || worker.destination.getZ()<origin.getZ() || worker.destination.getZ()>origin.getZ()+10),
                    "waiting workers evacuate rather than deadlock occupied construction cells");
            // Short interruptions retain UUIDs; sustained pause sends the crew out. No
            // replacement may spawn while departing/unloaded workers still own their slots.
            try {
                for(int tick=0;tick<ConstructionBuilder.PAUSE_DEPARTURE_TICKS+2;tick++) {
                    clock.setGameTime(originalTime+tick+1);
                    for(var b:crew) { b.tickCount++; b.tick(); }
                }
            } finally { clock.setGameTime(originalTime); }
            require(crew.stream().allMatch(b->b.leaving && !b.hammering()),"sustained pause departs the crew");
            var departureSaved=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());
            worker.saveWithoutId(departureSaved);
            var departureReload=new ConstructionBuilder(ConstructionContent.builder,level);
            departureReload.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),departureSaved.buildResult()));
            require(departureReload.leaving && !departureReload.allowedToWork,"departure survives native reload without work authorization");
            int beforeResume=ledger.entries.get(tag).workers().size();
            VillageConstructionActivity.update(level,List.of(site));
            require(ledger.entries.get(tag).workers().size()==beforeResume
                    && crew.stream().allMatch(b->b.leaving),"resume does not duplicate or recall departing workers");
            for(int tick=0;tick<260;tick++) for(var b:crew) if(!b.isRemoved()) { b.tickCount++; b.tick(); }
            require(crew.stream().allMatch(Entity::isRemoved) && ledger.entries.get(tag).workers().isEmpty(),
                    "unseen departing crew releases slots only on removal");
            for(int i=0;i<4;i++) VillageConstructionActivity.update(level,List.of(site));
            var returning=DevelopmentEntities.snapshot(level).stream().filter(e->e instanceof ConstructionBuilder)
                    .map(e->(ConstructionBuilder)e).filter(b->b.job.equals(tag) && !b.leaving).toList();
            cleanup.addAll(returning);
            require(returning.size()==2 && ledger.entries.get(tag).workers().size()==2,"active site receives a bounded returning crew");
            level.setBlock(edited,Blocks.CHEST.defaultBlockState(),18);
            level.setBlock(broken,Blocks.AIR.defaultBlockState(),18);
            VillageConstructionActivity.update(level,List.of(site));
            require(level.getBlockState(broken).isAir(),"player-removed fence is not rebuilt");
            viewer=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(),"CrewViewFixture"),net.minecraft.server.level.ClientInformation.createDefault());
            viewer.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5); viewer.setYRot(0); viewer.setXRot(0); level.players().add(viewer);
            require(!ConstructionSitePresentation.unseen(level,viewer.getEyePosition().add(0,0,24),16),"visible forward arrival rejected");
            require(ConstructionSitePresentation.unseen(level,viewer.getEyePosition().add(0,0,-24),16),"distant behind-camera arrival accepted");
            require(!ConstructionSitePresentation.unseen(level,viewer.getEyePosition().add(0,0,-4),16),"nearby pop-in rejected even behind camera");
            var watched=new ConstructionBuilder(ConstructionContent.builder,level); cleanup.add(watched);
            watched.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5); watched.setOnGround(true);
            watched.assign(tag,origin,origin,true); level.addFreshEntity(watched);
            watched.depart(origin.offset(24,0,24));
            for(int tick=0;tick<260;tick++) {
                viewer.setPos(watched.getX(),watched.getY(),watched.getZ()+3);
                watched.tickCount++; watched.tick();
            }
            require(watched.isAlive() && watched.leaving && !watched.hammering(),"watched/nearby departure never pops out");
            level.players().remove(viewer); viewer=null;
            for(int tick=0;tick<260 && !watched.isRemoved();tick++) { watched.tickCount++; watched.tick(); }
            require(watched.isRemoved(),"departure completes once no player can see it");
            VillageConstructionActivity.update(level,List.of());
            require(level.getBlockState(flower).is(Blocks.DANDELION),"original flower replanted after completion/reload");
            require(ConstructionSitePresentation.matchingUpper(level.getBlockState(grass),level.getBlockState(grass.above()))
                    && ConstructionSitePresentation.matchingUpper(level.getBlockState(fern),level.getBlockState(fern.above())),
                    "both halves of tall grass and large fern restore after receipt reload");
            require(level.getBlockState(upperEdit).isAir() && level.getBlockState(upperEdit.above()).is(Blocks.CHEST),
                    "upper player edits survive cleanup without leaving half a plant");
            require(level.getBlockState(edited).is(Blocks.CHEST) && !unrelated.isRemoved(),"cleanup preserves player storage/unrelated displays");
            require(returning.stream().allMatch(b->b.leaving),"completion sends returning crew away");
            for(int tick=0;tick<300;tick++) for(var b:returning) if(!b.isRemoved()) { b.tickCount++; b.tick(); }
            require(returning.stream().allMatch(Entity::isRemoved),"departed unseen crews disappear without item drops");
            require(ledger.entries.get(tag)==null,"completed ledger entry retired");
            level.setBlock(edited,Blocks.AIR.defaultBlockState(),18);
            verifyPockets(level,origin,village);
            verifyUnloadedPerimeter(level,village);
            System.out.println("PASS VillageConstructionActivitySelfTest: fence-first batches, occupied perimeter wait/resume, tall-plant pair restoration, legacy/readiness reload, player edits, saved crew/receipts, real walking/animation, stable spaced stations, active hammer duty, bounded pathing, blocked station recovery, pause/depart/resume/reload, view cone, departure, size scaling, pocket styles");
        } finally {
            if(viewer!=null) level.players().remove(viewer);
            cleanup.forEach(Entity::discard); VillageConstructionActivity.update(level,List.of());
            before.forEach((pos,state)->level.setBlock(pos,state,18));
        }
    }
    private static void verifyUnloadedPerimeter(ServerLevel level,UUID village) {
        BlockPos origin=new BlockPos(1_000_008,level.getMaxY()-35,1_000_008);
        level.getChunk(origin); // Load only the fixture origin, not the full perimeter.
        String tag=VillageConstructionActivity.projectTag(village,77,origin.asLong());
        var site=new VillageConstructionActivity.Site(tag,village,77,origin,origin,origin.offset(10,6,10),true);
        var missing=ConstructionSitePresentation.perimeter(site).stream()
                .filter(p->!ConstructionSitePresentation.loaded(level,p)).toList();
        require(!missing.isEmpty(),"fixture straddles loaded/unloaded chunks");
        try {
            for(int i=0;i<4;i++) VillageConstructionActivity.update(level,List.of(site));
            require(!ConstructionSitePresentation.fenceReady(level,tag)
                    && missing.stream().noneMatch(p->ConstructionSitePresentation.loaded(level,p)),
                    "partly unloaded perimeter neither authorizes structural work nor force-loads chunks");
        } finally { VillageConstructionActivity.update(level,List.of()); }
    }
    /** Fixture-only driver of the real census/preparation path; explicitly loads test perimeter chunks. */
    static void prepareFences(ServerLevel level,com.chedidandrew.emeraldstandard.core.EconomyService economy,String tag) {
        var sites=VillageConstructionActivity.sites(level,economy,true);
        var site=sites.stream().filter(s->s.tag().equals(tag)).findFirst().orElseThrow();
        for(BlockPos p:ConstructionSitePresentation.perimeter(site))
            for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL) level.getChunk(p.relative(d));
        for(int i=0;i<64 && !ConstructionSitePresentation.fenceReady(level,tag);i++)
            VillageConstructionActivity.update(level,sites);
        require(ConstructionSitePresentation.fenceReady(level,tag),"fixture perimeter finished: "+tag);
    }
    static void cleanupCrews(ServerLevel level) {
        VillageConstructionActivity.update(level,List.of());
        DevelopmentEntities.snapshot(level).stream().filter(e->e instanceof ConstructionBuilder).forEach(Entity::discard);
    }
    private static void verifyWorkwear(ServerLevel level,ConstructionBuilder worker,net.minecraft.nbt.CompoundTag saved) {
        String local=net.minecraft.world.entity.npc.villager.VillagerType.byBiome(level.getBiome(worker.focus)).identifier().getPath();
        require(worker.clothing().equals(local),"automatic crew uses worksite's native biome clothing");
        for(String style:ConstructionBuilder.CLOTHING_STYLES) {
            var data=saved.copy(); data.putString("BuilderClothing",style);
            var copy=new ConstructionBuilder(ConstructionContent.builder,level);
            copy.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),data));
            copy.assign(worker.job,worker.destination,worker.focus,true);
            require(copy.clothing().equals(style),"saved outfit stays stable across worksite reassignment: "+style);
            var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());
            copy.saveWithoutId(output);
            var again=new ConstructionBuilder(ConstructionContent.builder,level);
            again.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),output.buildResult()));
            require(again.clothing().equals(style),"outfit NBT roundtrip: "+style);
        }
        for(String legacy:List.of("","unknown/skin")) {
            var data=saved.copy(); data.remove("BuilderClothing");
            if(!legacy.isEmpty())data.putString("BuilderClothing",legacy);
            var copy=new ConstructionBuilder(ConstructionContent.builder,level);
            copy.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),data));
            require(copy.clothing().equals("plains"),"legacy/invalid outfits start with safe texture fallback");
            copy.assign(worker.job,worker.destination,worker.focus,true);
            require(copy.clothing().equals(local),"old/invalid outfits adopt loaded local biome");
        }
        require(ConstructionBuilder.validClothing(null).equals("plains")
                && ConstructionBuilder.validClothing("../bad").equals("plains"),"invalid texture paths cannot escape clothing allowlist");
    }
    private static void plantPair(ServerLevel level,BlockPos pos,Block block) {
        level.setBlock(pos,block.defaultBlockState(),18);
        level.setBlock(pos.above(),block.defaultBlockState().setValue(DoublePlantBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),18);
    }
    private static void reloadReceipt(ServerLevel level,BlockPos pos) {
        var saved=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());
        level.getBlockEntity(pos).saveWithoutMetadata(saved);
        var receipt=new ConstructionFenceBlock.Receipt(pos,level.getBlockState(pos));
        receipt.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),saved.buildResult()));
        level.setBlockEntity(receipt);
    }
    private static void verifyPockets(ServerLevel level,BlockPos origin,UUID village) throws Exception {
        List<BlockPos> road=new ArrayList<>(); for(int x=0;x<14;x++) road.add(origin.offset(x,0,0));
        var roadPlan=VillageTerrainFinishing.road(level,road,origin.getY(),Set.of(),Blocks.STONE_BRICKS.defaultBlockState(),Blocks.STONE_BRICK_STAIRS.defaultBlockState(),village,1);
        require(roadPlan!=null,"road beside optional pocket");
        for(int style=0;style<3;style++) {
            var pocket=VillageTerrainFinishing.pocket(level,road,Set.of(),Set.of(),roadPlan,village,style);
            require(!pocket.isEmpty(),"optional neighborhood style "+style);
            require(pocket.equals(VillageTerrainFinishing.pocket(level,road,Set.of(),Set.of(),roadPlan,village,style)),"deterministic pocket");
        }
        try(var claim=VillageDevelopmentProtection.register(c->false)) {
            require(VillageTerrainFinishing.pocket(level,road,Set.of(),Set.of(),roadPlan,village,1).isEmpty(),"claimed pocket skipped");
        }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new IllegalStateException(message); }
    private static String pathDescription(ConstructionBuilder worker) {
        var path=worker.getNavigation().getPath(); if(path==null) return "none";
        var text=new StringBuilder("next="+path.getNextNodeIndex()+" ");
        for(int i=0;i<path.getNodeCount();i++) { var n=path.getNode(i); text.append(n.x).append(',').append(n.y).append(',').append(n.z).append(':').append(n.type).append(' '); }
        return text.toString();
    }
}
