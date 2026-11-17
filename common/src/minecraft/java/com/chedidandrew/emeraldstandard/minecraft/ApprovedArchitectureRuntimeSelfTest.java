package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Only runs in the disposable dedicated-server fixture. Uses actual managers and saved plans. */
final class ApprovedArchitectureRuntimeSelfTest {
    static List<Runnable> checks(ServerLevel level) {
        var checks=new ArrayList<Runnable>();
        for(var style:VillageArchitecture.BiomeDialect.values()) {
            checks.add(()->ordinary(level,style,ApprovedArchitectureCatalog.REVISION));
            checks.add(()->ordinary(level,style,20,3,false,false,style.ordinal()%4,style.ordinal()%2==1,null,2));
            checks.add(()->ordinary(level,style,20,3,false,false,style.ordinal()%4,style.ordinal()%2==1,
                    "biome_"+style.id()+"_produce_stall"));
            for(int variant=0;variant<3;variant++) for(int fit=0;fit<=3;fit++) {
                int version=ApprovedBankStructures.currentVersion(variant,fit);
                checks.add(()->bank(level,style,version));
            }
        }
        checks.add(()->ordinary(level,VillageArchitecture.BiomeDialect.TAIGA,12));
        checks.add(()->ordinary(level,VillageArchitecture.BiomeDialect.PLAINS,13));
        for(int rotation=0;rotation<4;rotation++) for(boolean mirrored:List.of(false,true)) {
            final int turn=rotation;
            checks.add(()->ordinary(level,VillageArchitecture.BiomeDialect.PLAINS,14,3,false,false,turn,mirrored));
            checks.add(()->ordinary(level,VillageArchitecture.BiomeDialect.TAIGA,16,0,false,false,turn,mirrored));
            checks.add(()->ordinary(level,VillageArchitecture.BiomeDialect.PLAINS,17,0,false,false,turn,mirrored));
            checks.add(()->ordinary(level,VillageArchitecture.BiomeDialect.SNOWY,19,3,false,false,turn,mirrored));
            checks.add(()->ordinary(level,VillageArchitecture.BiomeDialect.SNOWY,20,3,false,false,turn,mirrored));
        }
        for(int fit=1;fit<=3;fit++) {
            final int outdoorLevel=fit;
            checks.add(()->ordinary(level,VillageArchitecture.BiomeDialect.TAIGA,13,outdoorLevel));
            checks.add(()->bank(level,VillageArchitecture.BiomeDialect.TAIGA,17+outdoorLevel*16));
            checks.add(()->ordinary(level,VillageArchitecture.BiomeDialect.PLAINS,14,outdoorLevel));
            checks.add(()->ordinary(level,VillageArchitecture.BiomeDialect.SNOWY,19,outdoorLevel));
            checks.add(()->ordinary(level,VillageArchitecture.BiomeDialect.SNOWY,20,outdoorLevel));
            for(int variant=0;variant<3;variant++) {
                final int choice=variant;
                checks.add(()->bank(level,VillageArchitecture.BiomeDialect.PLAINS,ApprovedBankStructures.versionForRevision(14,choice,outdoorLevel)));
            }
        }
        for(int version=13;version<=15;version++) {
            final int savedVersion=version;
            checks.add(()->bank(level,VillageArchitecture.BiomeDialect.TAIGA,savedVersion));
        }
        checks.add(()->ConstructionFinishSelfTest.verify(level));
        checks.add(()->ConstructionSupportRecoverySelfTest.verify(level));
        checks.add(()->ConstructionTerrainCompletionSelfTest.verify(level));
        checks.add(()->CoveredPathConstructionSelfTest.verify(level));
        checks.add(()->StructureIdentifySelfTest.verify(level));
        return List.copyOf(checks);
    }
    private static void ordinary(ServerLevel level,VillageArchitecture.BiomeDialect style,int revision) {
        ordinary(level,style,revision,0);
    }
    private static void ordinary(ServerLevel level,VillageArchitecture.BiomeDialect style,int revision,int outdoorLevel) {
        ordinary(level,style,revision,outdoorLevel,false,false);
    }
    static void mineCapture(ServerLevel level) {
        ordinary(level,VillageArchitecture.BiomeDialect.PLAINS,13,3,true,false);
    }
    static void yardSlope(ServerLevel level) {
        ordinary(level,VillageArchitecture.BiomeDialect.PLAINS,13,0,false,true);
    }
    private static void ordinary(ServerLevel level,VillageArchitecture.BiomeDialect style,int revision,int outdoorLevel,boolean mine,boolean terrain) {
        ordinary(level,style,revision,outdoorLevel,mine,terrain,style.ordinal()%4,style.ordinal()%2==1);
    }
    private static void ordinary(ServerLevel level,VillageArchitecture.BiomeDialect style,int revision,int outdoorLevel,boolean mine,boolean terrain,int rotation,boolean mirrored) {
        ordinary(level,style,revision,outdoorLevel,mine,terrain,rotation,mirrored,null);
    }
    private static void ordinary(ServerLevel level,VillageArchitecture.BiomeDialect style,int revision,int outdoorLevel,boolean mine,boolean terrain,int rotation,boolean mirrored,String selectedId) {
        ordinary(level,style,revision,outdoorLevel,mine,terrain,rotation,mirrored,selectedId,0);
    }
    private static void ordinary(ServerLevel level,VillageArchitecture.BiomeDialect style,int revision,int outdoorLevel,boolean mine,boolean terrain,int rotation,boolean mirrored,String selectedId,int terrainDrop) {
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();ServerPlayer observer=null;
        BlockPos origin=new BlockPos(1808+style.ordinal()*96,level.getMaxY()-64,1808);
        try {
            var state=EconomyState.fresh(1234,System.currentTimeMillis(),0);
            var v=state.village(mine?UUID.fromString("30bbb084-0e75-39e4-8bc6-a591308bc5ef"):UUID.randomUUID());v.dimensionKey="minecraft:overworld";v.centerPos=origin.asLong();
            v.architectureDialect=style.id();v.architectureCharacter="rustic";v.developmentTier=5;
            v.population=v.observedPopulation=30;v.housingCapacity=50;v.foodSupply=v.materialSupply=2000;
            v.treasury=1000;v.prosperity=v.safety=100;
            var p=new EconomyState.VillageProject();p.projectId=1;
            p.type=style==VillageArchitecture.BiomeDialect.TAIGA?VillageProsperityEngine.ProjectType.INN:VillageProsperityEngine.ProjectType.COTTAGE;
            p.designSchema="blueprint_v2";p.designTemplateRevision=revision;
            p.designTemplateId="biome_"+style.id()+(p.type==VillageProsperityEngine.ProjectType.INN?"_inn_coachhouse_02":"_cottage_hearth_01");
            p.designPaletteId="balanced";p.designDressingId="prosperous";p.designSeed=1234;
            p.designStage=2;p.designRotation=rotation;p.designMirrored=mirrored;
            p.designOutdoorLevel=outdoorLevel;
            if(selectedId!=null) {
                p.designTemplateId=selectedId;
                p.type=VillageArchitecture.requireBlueprint(selectedId,revision).type();
            }
            if(mine) {
                p.type=VillageProsperityEngine.ProjectType.MINE_ENTRANCE;p.designTemplateId="biome_plains_mine_headframe_01";
                p.designSeed=4518264180380142599L;p.designStage=1;p.designRotation=1;p.designMirrored=true;
            }
            p.designSignature=VillageArchitecture.blueprintSignature(p.type,p.designTemplateId,p.designTemplateRevision,p.designPaletteId,p.designDressingId,p.designMirrored);
            p.originPos=origin.asLong();p.economicComplete=true;p.economicProgress=1;
            p.sitePreparationComplete=true;p.constructionStarted=true;
            p.entranceApproachComplete=true;p.entranceApproachVersion=EconomyState.ENTRANCE_APPROACH_VERSION;
            p.trailAnchorSet=true;p.trailAnchorPos=origin.west(25).asLong();p.trailMaterializedComplete=true;
            List<?> canonical=(List<?>)invoke("projectTemplate",level,origin,v,p);
            p.trailTotalBlocks=((List<?>)invoke("managedProjectTrail",origin,v,p)).size();
            p.trailMaterializedBlocks=p.trailTotalBlocks;
            p.totalBlocks=canonical.size();p.constructionOrderCuts.addAll(List.of(0,p.totalBlocks));
            var descriptor=VillageArchitecture.requireBlueprint(p.designTemplateId,p.designTemplateRevision);
            int radius=Math.max(descriptor.width(),descriptor.depth());
            for(int x=-6;x<=radius+6;x++) for(int z=-6;z<=radius+6;z++)
                for(int y=-6;y<0;y++) set(level,before,origin.offset(x,y,z),
                        (y>=-terrainDrop?Blocks.AIR:y==-terrainDrop-1?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState());
            if(terrain) {
                for(int x=descriptor.width()/2;x<=radius+6;x++)for(int z=-6;z<=radius+6;z++)
                    set(level,before,origin.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState());
                var fitted=OutdoorTerrainFitting.freeze(level,origin,p);
                require(fitted!=null&&!fitted.isEmpty(),"Sloped-yard fixture did not freeze terrain columns");
                p.outdoorTerrainOffsets.putAll(fitted);
                canonical=(List<?>)invoke("projectTemplate",level,origin,v,p);
                p.totalBlocks=canonical.size();p.constructionOrderCuts.clear();p.constructionOrderCuts.addAll(List.of(0,p.totalBlocks));
            }
            for(Object c:canonical) {
                BlockPos at=target(origin,c);
                if(at.getY()>=origin.getY()) set(level,before,at,Blocks.AIR.defaultBlockState());
            }
            p.sitePreparationPlan=(SitePreparationPlan)invoke("prepareProjectSitePlan",level,origin,v,p,canonical);
            require(p.sitePreparationPlan!=null,"Approved ordinary terrain preflight rejected "+style);
            if(terrainDrop>0) require(p.sitePreparationPlan.cells().stream().filter(c->c.after().contains("_stairs[")).count()==terrainDrop*3,
                    "Actual reviewed exit lost its three-wide raised flight: "+style);
            p.sitePreparationComplete=false;
            Object bounds=invoke("bounds",origin,canonical);
            p.boundsMinPos=((BlockPos)method(bounds,"minimum")).asLong();p.boundsMaxPos=((BlockPos)method(bounds,"maximum")).asLong();
            p.designPlanHash=(String)invoke("blueprintPlanHash",v,p,invoke("blueprintPlacementPlan",level,origin,v,p));p.designPlanHashVersion=1;
            v.projects.add(p);v.projectSerial=1;
            var directory=Files.createTempDirectory("tes-approved-building-");state.save(directory.resolve("the_emerald_standard.properties"));
            var economy=new EconomyService();economy.configureVillageProsperity(true,true);economy.start(directory,1234,0);economy.configureForcedVillageDevelopment(true);
            observer=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"ReleaseFixture"),ClientInformation.createDefault());
            observer.setPos(origin.getX()+80,origin.getY(),origin.getZ());level.players().add(observer);
            var props=new Properties();props.setProperty(EmeraldConfig.FORCED_DEVELOPMENT_KEY,"true");var config=EmeraldConfig.parse(props);
            var budgetType=Class.forName(VillageProsperityManager.class.getName()+"$MaterializationBudget");
            var budget=budgetType.getDeclaredConstructor(int.class,int.class);budget.setAccessible(true);
            boolean restarted=false;
            for(int pulse=1;pulse<=20000;pulse++) {
                ForcedDevelopmentRuntime.reset();ForcedDevelopmentRuntime.claim(level.getServer());
                invoke("materializeDevelopment",level,economy,config,pulse*20L,budget.newInstance(64,1),economy.developmentVillageSnapshot(v.villageId));
                var current=economy.developmentVillageSnapshot(v.villageId).village().projects.getFirst();
                if(!restarted&&current.materializedBlocks>64&&!current.materializedComplete) {
                    restarted=true;
                    var reload=new EconomyService();reload.configureVillageProsperity(true,true);reload.start(directory,1234,0);reload.configureForcedVillageDevelopment(true);economy=reload;
                    var saved=economy.developmentVillageSnapshot(v.villageId).village().projects.getFirst();
                    require(current.designPlanHash.equals(saved.designPlanHash)&&saved.designOutdoorLevel==outdoorLevel
                            &&current.outdoorTerrainOffsets.equals(saved.outdoorTerrainOffsets),"Restart changed frozen geometry/fit/terrain");
                }
                if(current.materializedComplete) break;
                if(pulse==20000) {
                    List<?> ordered=(List<?>)invoke("constructionTemplate",canonical,current);
                    Object cell=ordered.get(current.materializedBlocks);BlockPos at=target(origin,cell);
                    throw new IllegalStateException("Release building stalled "+current.materializedBlocks+"/"+current.totalBlocks
                            +" at "+at+" expected "+field(cell,"state")+" actual "+level.getBlockState(at)
                            +" role "+field(cell,"role")+" phase "+field(cell,"constructionPhase")
                            +" "+ConstructionDiagnostics.recent(v.villageId+"/1",pulse*20L));
                }
            }
            int waivedStoragePlants=0;
            for(Object c:canonical) {
                BlockState expected=(BlockState)field(c,"state");if(expected.isAir()) continue;
                if(field(c,"role").toString().endsWith("SUPPORT")) continue; // Stable natural substrate satisfies adaptive footing.
                BlockPos at=(BlockPos)invoke("placementTarget",level,origin,c);
                // A potted flower on a storage barrel is optional, not structural authority.
                // The production attachment guard deliberately refuses block-entity bearings.
                // Prove that exact waiver rather than dropping all cosmetic assertions.
                if(expected.getBlock() instanceof FlowerPotBlock&&field(c,"role").toString().equals("COSMETIC")
                        &&level.getBlockState(at).isAir()&&level.getBlockState(at.below()).is(Blocks.BARREL)
                        &&!ConstructionOwnership.owned(level,at,expected)
                        &&!(boolean)invoke("cosmeticPlacementSupported",level,origin,at,c)) {
                    waivedStoragePlants++;continue;
                }
                if((boolean)field(c,"nativePathGround")) {
                    require(expected.is(Blocks.DIRT_PATH)
                            &&VillageProsperityManager.naturalSoilEquivalent(level.getBlockState(at),
                                    (BlockState)invoke("constructionState",c)),
                            "Missing stable covered ground "+p.designTemplateId+" "+at);
                    continue; // Only the independently tested immutable native-cover annotation.
                }
                if(VillageProsperityManager.naturalSoilEquivalent(level.getBlockState(at),expected)) continue; // Native grass spreads or decays.
                require(level.getBlockState(at).is(expected.getBlock()),"Missing approved block "+p.designTemplateId+" "+at+" expected "+expected+" actual "+level.getBlockState(at)+" role="+field(c,"role")
                        +" below="+level.getBlockState(at.below())+" above="+level.getBlockState(at.above())+" authoredTarget="+target(origin,c));
                if(VillageProsperityManager.isManagedConnectionBlock(expected))
                    require(Block.updateFromNeighbourShapes(level.getBlockState(at),level,at).equals(level.getBlockState(at)),
                            "Approved yard/building handed over stale connections at "+at);
                if(expected.is(Blocks.SPRUCE_LOG)) require(level.getBlockState(at).equals(expected),"Log axis changed during construction "+at);
                if(expected.getBlock() instanceof LadderBlock) require(level.getBlockState(at).canSurvive(level,at),"Ladder lost its bearing");
            }
            var reload=new EconomyService();reload.start(directory,1234,0);
            require(reload.developmentVillageSnapshot(v.villageId).village().projects.getFirst().materializedComplete,"Handover not persisted");
            var data=ApprovedVillageStructures.data(p.designTemplateId,p.designTemplateRevision,p.designOutdoorLevel);var at=mine?data.entrance():data.door();
            if(selectedId!=null)require(data.cells().get(at).getBlock() instanceof FenceGateBlock,"Open stall did not generate its primary gate");
            int doorX=p.designMirrored?data.identity().width()-1-at.getX():at.getX();
            BlockPos primaryDoor=origin.offset((BlockPos)invoke("rotateRelative",doorX,at.getY(),at.getZ(),invoke("projectSize",p),p.designRotation));
            if(!mine) {
                BlockPos streetStart=null;
                if(terrainDrop>0) {
                    Direction out=Direction.NORTH;
                    for(int turn=0;turn<rotation;turn++)out=out.getClockWise();
                    streetStart=VillageProsperityManager.projectEntrance(origin,p).relative(out,terrainDrop+2).below(terrainDrop);
                    require(EntranceStaircase.obstruction(level,p.sitePreparationPlan)==null,"raised flight damaged before handover");
                }
                VillageWalkingSelfTest.building(level,BlockPos.of(p.boundsMinPos),BlockPos.of(p.boundsMaxPos),"approved "+style,primaryDoor,streetStart);
            }
            else require(level.getBlockState(primaryDoor).getCollisionShape(level,primaryDoor).isEmpty()
                    &&level.getBlockState(primaryDoor.above()).getCollisionShape(level,primaryDoor.above()).isEmpty(),
                    "Open mine entrance lost its two-block passage");
            System.out.println("PASS live approved "+p.designTemplateId+"@"+revision+" fit="+outdoorLevel+": "+canonical.size()+" operations, rotation/mirror, partial restart and durable handover; guarded storage plants="+waivedStoragePlants);
        } catch(Exception failure) {throw new IllegalStateException("Approved ordinary runtime "+style,failure);}
        finally {if(observer!=null){level.players().remove(observer);observer.discard();}before.forEach((at,s)->level.setBlock(at,s,18));ConstructionDiagnostics.reset();}
    }
    private static void bank(ServerLevel level,VillageArchitecture.BiomeDialect style,int version) {
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();ServerPlayer observer=null;
        BlockPos origin=new BlockPos(1808+style.ordinal()*96,level.getMaxY()-64,2000);long key=8800+style.ordinal();
        try {
            var directory=Files.createTempDirectory("tes-approved-bank-");var economy=new EconomyService();economy.start(directory,1234,0);economy.configureForcedVillageDevelopment(true);
            BankStyleLedger.get(level).remember(origin.asLong(),style.id());
            var state=EconomyState.fresh(1234,System.currentTimeMillis(),0);var v=state.village(UUID.randomUUID());
            v.dimensionKey="minecraft:overworld";v.centerPos=origin.asLong();v.architectureDialect=style.id();v.developmentTier=5;v.population=v.observedPopulation=30;v.housingCapacity=50;v.foodSupply=v.materialSupply=2000;v.treasury=1000;v.prosperity=v.safety=100;
            state.save(directory.resolve("the_emerald_standard.properties"));economy=new EconomyService();economy.start(directory,1234,0);economy.configureForcedVillageDevelopment(true);
            observer=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"BankReleaseFixture"),ClientInformation.createDefault());
            observer.setPos(origin.getX()+80,origin.getY(),origin.getZ());level.players().add(observer);
            var nativePlan=ApprovedBankStructures.plan(style,version);
            for(int variant=0;variant<3;variant++) for(int fit=0;fit<=3;fit++)
                if(version==ApprovedBankStructures.currentVersion(variant,fit))
                    require(nativePlan.cells().values().stream().filter(s->s.is(Blocks.CAMPFIRE)
                            &&s.getValue(CampfireBlock.SIGNAL_FIRE)).count()==1,"Current Bank route lost its smoke marker");
            for(int x=nativePlan.minX()-5;x<=nativePlan.maxX()+5;x++) for(int z=nativePlan.minZ()-10;z<=nativePlan.maxZ()+5;z++)
                for(int y=-6;y<0;y++) set(level,before,origin.offset(x,y,z),(y==-1?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState());
            var prepare=VillageBankManager.class.getDeclaredMethod("prepareApprovedBank",ServerLevel.class,EconomyService.class,BlockPos.class,UUID.class,long.class,int.class);prepare.setAccessible(true);
            var plan=(BankConstruction)prepare.invoke(null,level,economy,origin,v.villageId,key,version);
            require(plan!=null&&economy.reserveBankConstruction(key,plan),"Bank parcel reservation rejected "+style);
            for(var cell:plan.cells()) before.putIfAbsent(BlockPos.of(cell.position()),level.getBlockState(BlockPos.of(cell.position())));
            require(VillageBankManager.approvedBankLots(level,economy).size()==1,"Pending Bank yard not protected");
            for(int pulse=0;pulse<plan.cells().size()+40&&economy.pendingBankConstructionsSnapshot().containsKey(key);pulse++) {
                VillageBankManager.advanceBankConstruction(level,economy,key,plan);
                if(pulse==2) {
                    // Slow live construction allows shaded soil to decay between pulses. The
                    // old tight-loop fixture never allowed this normal world change to occur.
                    for(var cell:plan.cells()) if(cell.after().startsWith("minecraft:grass_block")
                            &&level.getBlockState(BlockPos.of(cell.position())).is(Blocks.GRASS_BLOCK))
                        level.setBlock(BlockPos.of(cell.position()),Blocks.DIRT.defaultBlockState(),18);
                    var reload=new EconomyService();reload.start(directory,1234,0);reload.configureForcedVillageDevelopment(true);economy=reload;require(plan.cells().equals(economy.pendingBankConstructionsSnapshot().get(key).cells()),"Bank plan changed on restart");
                }
            }
            require(economy.hasGeneratedBankRegion(key),"Bank did not finish "+style+" "+ConstructionDiagnostics.recent("bank:"+key,level.getGameTime()));
            for(var cell:plan.cells()) {
                BlockPos at=BlockPos.of(cell.position()); BlockState actual=level.getBlockState(at);
                if(VillageProsperityManager.isManagedConnectionBlock(actual))
                    require(Block.updateFromNeighbourShapes(actual,level,at).equals(actual),"Approved Bank handed over stale connections at "+at
                            +" actual="+actual+" expected="+Block.updateFromNeighbourShapes(actual,level,at)
                            +" above="+level.getBlockState(at.above())+" north="+level.getBlockState(at.north())
                            +" east="+level.getBlockState(at.east())+" south="+level.getBlockState(at.south())+" west="+level.getBlockState(at.west()));
            }
            require(VillageBankManager.bankOperationProblem(level,economy,key).isEmpty(),"Approved Bank incorrectly damaged: "+VillageBankManager.bankOperationProblem(level,economy,key));
            BlockPos floor=nativePlan.air().stream().filter(at->nativePlan.air().contains(at.above()))
                    .filter(at->{var bearing=nativePlan.cells().get(at.below());return bearing!=null
                            &&bearing.isFaceSturdy(level,origin.offset(at.below()),net.minecraft.core.Direction.UP);})
                    .min(Comparator.comparingDouble(at->at.distSqr(new BlockPos(6,1,8)))).orElseThrow().below();
            BlockPos actualFloor=origin.offset(floor);var intactFloor=level.getBlockState(actualFloor);
            level.setBlock(actualFloor,Blocks.AIR.defaultBlockState(),18);
            require(VillageBankManager.bankOperationProblem(level,economy,key).contains("floor"),"Missing circulation floor remained operational "+style);
            level.setBlock(actualFloor,intactFloor,18);
            require(VillageBankManager.bankOperationProblem(level,economy,key).isEmpty(),"Restored Bank floor did not resume service "+style);
            BlockPos start=new BankWalkways.Bank(key,plan.bankerAnchor(),version,style).start();
            require(BankWalkways.candidates(level,economy,v).stream().anyMatch(bank->bank.key()==key),
                    "Approved Bank walkway missing at "+start+" ground="+level.getBlockState(start)
                            +" feet="+level.getBlockState(start.above())+" head="+level.getBlockState(start.above(2))
                            +" active="+VillageBankManager.bankWorkActive(level,economy,v.villageId,start,256));
            for(var cell:nativePlan.cells().entrySet()) require(VillageBankManager.bankConstructionCellMatches(level,
                    origin.offset(cell.getKey()),level.getBlockState(origin.offset(cell.getKey())),cell.getValue()),"Bank lost authored cell "+cell);
            VillageWalkingSelfTest.building(level,origin.offset(nativePlan.minX(),-3,nativePlan.minZ()),
                    origin.offset(nativePlan.maxX(),nativePlan.height()+3,nativePlan.maxZ()),"approved Bank "+style,
                    origin.offset(nativePlan.entrance()),origin.offset(nativePlan.walkwayExit()));
            var reload=new EconomyService();reload.start(directory,1234,0);require(reload.generatedBankStructureVersion(key)==version,"Bank version not persisted");
            verifyCompletedBankConnections(level, reload, origin, BlockPos.of(plan.bankerAnchor()), v.villageId, key, nativePlan);
            for(var cell:nativePlan.cells().entrySet()) if(cell.getValue().is(Blocks.CAMPFIRE)&&cell.getValue().getValue(CampfireBlock.SIGNAL_FIRE)) {
                BlockPos at=origin.offset(cell.getKey());BlockState fire=level.getBlockState(at);
                require(CampfireBlock.isLitCampfire(fire)&&fire.getValue(CampfireBlock.SIGNAL_FIRE)
                        &&!fire.getValue(CampfireBlock.WATERLOGGED)&&level.getBlockState(at.below()).is(Blocks.HAY_BLOCK)
                        &&level.getBlockState(at.above()).isAir()
                        &&CampfireBlock.isSmokeyPos(level,at.above())
                        &&level.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.CampfireBlockEntity,
                        "Completed/reloaded Bank lost its native smoke emitter");
                for(var side:net.minecraft.core.Direction.Plane.HORIZONTAL) for(int dy=-1;dy<=0;dy++) {
                    var relative=cell.getKey().above(dy).relative(side);
                    var expected=nativePlan.cells().get(relative);
                    if(expected!=null&&expected.is(Blocks.SPRUCE_TRAPDOOR)) {
                        var actual=level.getBlockState(origin.offset(relative));
                        require(actual.is(Blocks.SPRUCE_TRAPDOOR)&&actual.getValue(TrapDoorBlock.OPEN)
                                &&actual.getValue(TrapDoorBlock.FACING)==expected.getValue(TrapDoorBlock.FACING)
                                &&!actual.getValue(TrapDoorBlock.WATERLOGGED),
                                "Completed/reloaded Bank lost its skinny upright shroud");
                    }
                }
            }
            System.out.println("PASS live approved Bank "+style+" v"+version+": full terrain reservation, progressive build, restart, operational desk and road connection");
        } catch(Exception failure){throw new IllegalStateException("Approved Bank runtime "+style,failure);}
        finally {
            if(observer!=null){level.players().remove(observer);observer.discard();}
            level.getEntitiesOfClass(Villager.class,new AABB(origin).inflate(64),v->BankerAccess.isBankerForRegion(v,key)).forEach(Villager::discard);
            before.forEach((at,s)->level.setBlock(at,s,18));ConstructionDiagnostics.reset();
        }
    }
    private static void verifyCompletedBankConnections(ServerLevel level,EconomyService economy,BlockPos origin,
            BlockPos anchor,UUID village,long key,ApprovedBankStructures.Plan plan)throws Exception {
        BlockPos relative=plan.cells().entrySet().stream().filter(e->e.getValue().getBlock() instanceof FenceBlock
                ||e.getValue().getBlock() instanceof IronBarsBlock)
                .filter(e->!Block.updateFromNeighbourShapes(e.getValue().getBlock().defaultBlockState(),level,origin.offset(e.getKey()))
                        .equals(e.getValue().getBlock().defaultBlockState()))
                .map(Map.Entry::getKey).findFirst().orElseThrow();
        BlockPos at=origin.offset(relative); BlockState original=level.getBlockState(at);
        BlockState stale=original.getBlock().defaultBlockState();
        var method=VillageBankManager.class.getDeclaredMethod("maintainManagedBank",ServerLevel.class,EconomyService.class,
                BlockPos.class,UUID.class,long.class,VillageBankPlacementPolicy.UpgradeAttemptGate.class);
        method.setAccessible(true);
        try {
            level.setBlock(at,stale,18);
            try(var guard=VillageDevelopmentProtection.register(context->!context.position().equals(at))) {
                method.invoke(null,level,economy,anchor,village,key,new VillageBankPlacementPolicy.UpgradeAttemptGate());
                require(level.getBlockState(at).equals(stale),"Completed Bank repair bypassed protection");
            }
            method.invoke(null,level,economy,anchor,village,key,new VillageBankPlacementPolicy.UpgradeAttemptGate());
            require(level.getBlockState(at).equals(Block.updateFromNeighbourShapes(stale,level,at)),
                    "Completed Bank connections did not refresh after reload");
            level.setBlock(at,Blocks.AIR.defaultBlockState(),18);
            method.invoke(null,level,economy,anchor,village,key,new VillageBankPlacementPolicy.UpgradeAttemptGate());
            require(level.getBlockState(at).isAir(),"Completed Bank maintenance rebuilt a removed connector");
            level.setBlock(at,Blocks.STONE.defaultBlockState(),18);
            method.invoke(null,level,economy,anchor,village,key,new VillageBankPlacementPolicy.UpgradeAttemptGate());
            require(level.getBlockState(at).is(Blocks.STONE),"Completed Bank maintenance replaced a player alteration");
        } finally {level.setBlock(at,original,18);}
    }
    private static Object invoke(String name,Object...args)throws Exception{return ConstructionSupportRecoverySelfTest.invoke(name,args);}
    private static Object field(Object o,String name)throws Exception{return ConstructionSupportRecoverySelfTest.field(o,name);}
    private static Object method(Object o,String name)throws Exception{var m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(o);}
    private static BlockPos target(BlockPos origin,Object c)throws Exception{return origin.offset((int)field(c,"dx"),(int)field(c,"dy"),(int)field(c,"dz"));}
    private static void set(ServerLevel level,Map<BlockPos,BlockState> before,BlockPos at,BlockState state){level.getChunk(at);before.putIfAbsent(at,level.getBlockState(at));level.setBlock(at,state,18);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
