package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Replays the captured snowy tannery using its real 648-cell plan, never a player's world. */
final class DecorativeFloraConstructionSelfTest {
    static void verify(ServerLevel level) throws Exception {
        for(boolean forced:List.of(false,true)) run(level,forced);
    }
    private static void run(ServerLevel level,boolean forced) throws Exception {
        var entry=VanillaVillageBuildings.manifest().stream().filter(e->e.id().endsWith("/snowy_tannery_1")).findFirst().orElseThrow();
        VanillaConstructionPlan plan;
        try(var stream=level.getServer().getResourceManager().getResourceOrThrow(
                net.minecraft.resources.Identifier.parse(entry.id().replace("minecraft:","minecraft:structure/")+".nbt")).open()) {
            plan=VanillaVillageBuildings.convert(entry,stream.readAllBytes());
        }
        var origin=new BlockPos(4736,level.getMaxY()-48,4736);
        Map<BlockPos,BlockState> before=new LinkedHashMap<>(); ServerPlayer observer=null; String job=null;
        try {
            var state=EconomyState.fresh(1234,0,0);var v=state.village(UUID.fromString("a3d1065d-4267-3db0-bca6-831b61fb35c9"));
            v.dimensionKey="minecraft:overworld";v.centerPos=origin.asLong();v.architectureCharacter="formal";
            v.architectureDialect=v.naturalVillageStyle="snowy";v.developmentTier=1;
            v.population=v.observedPopulation=8;v.housingCapacity=30;v.foodSupply=v.materialSupply=2000;v.treasury=1000;v.prosperity=v.safety=100;
            var p=new EconomyState.VillageProject();p.projectId=v.projectSerial=16;p.type=VillageProsperityEngine.ProjectType.MARKET_SQUARE;
            p.vanillaPlan=plan;p.designSchema=VanillaConstructionPlan.SCHEMA;p.designRotation=1;p.originPos=origin.asLong();
            p.economicComplete=true;p.economicProgress=1;p.sitePreparationComplete=true;p.constructionStarted=true;
            p.entranceApproachComplete=true;p.entranceApproachVersion=EconomyState.ENTRANCE_APPROACH_VERSION;
            p.trailAnchorSet=true;p.trailAnchorPos=origin.north(24).asLong();p.trailMaterializedComplete=true;
            p.trailTotalBlocks=((List<?>)invoke("managedProjectTrail",origin,v,p)).size();
            p.trailMaterializedBlocks=p.trailTotalBlocks;
            List<?> canonical=(List<?>)invoke("projectTemplate",level,origin,v,p);p.totalBlocks=canonical.size();
            require(p.totalBlocks==648,"captured tannery operation count changed");p.constructionOrderCuts.addAll(List.of(0,648));
            List<?> ordered=(List<?>)invoke("constructionTemplate",canonical,p);
            Object flora=ordered.get(67);require(block(flora).is(Blocks.SHORT_GRASS),"captured cursor no longer targets grass");
            require((boolean)method(flora,"isDecorativeFlora"),"grass retains required runtime authority");
            for(int x=-12;x<=20;x++)for(int z=-12;z<=20;z++)for(int y=-9;y<plan.height()+3;y++)
                set(level,before,origin.offset(x,y,z),(y<0?Blocks.DIRT:Blocks.AIR).defaultBlockState());
            var bounds=invoke("bounds",origin,canonical);p.boundsMinPos=((BlockPos)method(bounds,"minimum")).asLong();p.boundsMaxPos=((BlockPos)method(bounds,"maximum")).asLong();
            p.materializedBlocks=67;v.projects.add(p);
            var dir=Files.createTempDirectory("tes-tannery-flora-");state.save(dir.resolve("the_emerald_standard.properties"));
            var economy=load(dir,forced);job=ConstructionOwnership.project(v.villageId,16,p.originPos);
            var ledger=ConstructionOwnership.get(level);ledger.begin(economy,job,v.villageId,16,p.originPos,false);
            for(int i=0;i<ordered.size();i++) {
                var c=ordered.get(i);var at=target(origin,c);var expected=block(c);set(level,before,at,expected);
                if(!expected.isAir())ledger.claim(job,at,expected,expected.hasBlockEntity());
            }
            // The native plant has no legal substrate after the exterior landing/terrain overlay.
            // Force that same survival failure without changing any saved blueprint cell.
            for(Object c:ordered)if(VillageProsperityManager.decorativeFlora(block(c))) {
                var at=target(origin,c);set(level,before,at,Blocks.AIR.defaultBlockState());
                set(level,before,at.below(),Blocks.STONE.defaultBlockState());
                require(!block(c).canSurvive(level,at),"fixture did not reproduce unsupported flora");
            }
            observer=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"TanneryFixture"),ClientInformation.createDefault());
            observer.setPos(origin.getX()+48,origin.getY(),origin.getZ());level.players().add(observer);
            VillageConstructionActivitySelfTest.prepareFences(level,economy,VillageConstructionActivity.projectTag(v.villageId,16,p.originPos));
            var budgetClass=Class.forName(VillageProsperityManager.class.getName()+"$MaterializationBudget");var budget=budgetClass.getDeclaredConstructor(int.class,int.class);budget.setAccessible(true);
            var props=new Properties();props.setProperty(EmeraldConfig.FORCED_DEVELOPMENT_KEY,Boolean.toString(forced));var config=EmeraldConfig.parse(props);
            for(int pulse=1;pulse<=250;pulse++) {
                ForcedDevelopmentRuntime.reset();ForcedDevelopmentRuntime.claim(level.getServer());
                invoke("materializeDevelopment",level,economy,config,pulse*20L,budget.newInstance(64,1),economy.developmentVillageSnapshot(v.villageId));
                var current=economy.developmentVillageSnapshot(v.villageId).village().projects.getFirst();
                if(current.materializedComplete)break;
                if(pulse==2)economy=load(dir,forced);
                if(pulse==250)throw new IllegalStateException("Captured tannery stalled "+current.materializedBlocks+" "+ConstructionDiagnostics.recent(v.villageId+"/16",pulse*20L));
            }
            var restored=read(dir,v.villageId);
            require(restored.materializedComplete&&restored.vanillaPlan.hash().equals(plan.hash())&&restored.totalBlocks==648,"handover changed plan identity/cells");
            for(Object c:ordered)if(!VillageProsperityManager.decorativeFlora(block(c))) {
                var at=target(origin,c);
                require((boolean)invoke("placementSatisfied",level,origin,at,level.getBlockState(at),c),"required geometry waived: "+at);
            }
            var audited=load(dir,forced);
            long auditTick=(120L-Math.floorMod(v.villageId.hashCode(),120))*20L;
            invoke("reconcileOneMaterializedProject",level,audited,audited.developmentVillageSnapshot(v.villageId).village(),config,auditTick,List.of());
            require(read(dir,v.villageId).materializedComplete,"flora loss restarted completed construction");
            var required=ordered.stream().filter(c->{try{return block(c).is(Blocks.SPRUCE_PLANKS)&&(int)field(c,"dy")>=2;}catch(Exception e){throw new IllegalStateException(e);}}).findFirst().orElseThrow();
            set(level,before,target(origin,required),Blocks.AIR.defaultBlockState());
            require(!(boolean)invoke("placementSatisfied",level,origin,target(origin,required),level.getBlockState(target(origin,required)),required),"fixture failed to remove required wall");
            var auditVillage=audited.developmentVillageSnapshot(v.villageId).village();
            require(auditVillage.villageId.equals(v.villageId)&&Math.floorMod((auditTick+2400L)/20L+auditVillage.villageId.hashCode(),120L)==0L,"fixture audit cadence incorrect");
            require((boolean)invoke("villageWorkAreaAvailable",level,auditVillage)&&VanillaVillageBuildings.canResolve(auditVillage.projects.getFirst().vanillaPlan),"fixture audit unavailable");
            for(Object c:(List<?>)invoke("projectTemplate",level,origin,auditVillage,auditVillage.projects.getFirst()))
                require((boolean)invoke("placementColumnLoaded",level,origin,c),"fixture audit has unloaded column "+target(origin,c));
            invoke("reconcileOneMaterializedProject",level,audited,audited.developmentVillageSnapshot(v.villageId).village(),config,auditTick+2400L,List.of());
            require(!read(dir,v.villageId).materializedComplete,"required building damage waived with flora");
            for(Block block:List.of(Blocks.WHEAT,Blocks.SWEET_BERRY_BUSH,Blocks.FARMLAND,Blocks.OAK_SAPLING,Blocks.OAK_LEAVES,Blocks.SNOW_BLOCK,Blocks.CHEST,Blocks.LANTERN,Blocks.COBBLESTONE))
                require(!VillageProsperityManager.decorativeFlora(block.defaultBlockState()),"productive/required block classified as flora "+block);
            System.out.println("PASS captured tannery 67/648: forced="+forced+", unsupported grass, unchanged frozen plan, reload, required geometry and completion audit");
        } finally {if(observer!=null){level.players().remove(observer);observer.discard();}if(job!=null)ConstructionOwnership.get(level).finish(job);
            VillageConstructionActivitySelfTest.cleanupCrews(level);before.forEach((at,s)->level.setBlock(at,s,18));ConstructionWorkStatus.reset();ConstructionDiagnostics.reset();}
    }
    private static EconomyService load(java.nio.file.Path dir,boolean forced)throws Exception {var e=new EconomyService();e.configureVillageProsperity(true,true);e.start(dir,1234,0);e.configureForcedVillageDevelopment(forced);return e;}
    // Read-only durability assertions must not start a second writer and reset journal baselines.
    private static EconomyState.VillageProject read(java.nio.file.Path dir,UUID id)throws Exception {
        return EconomyState.load(dir.resolve("the_emerald_standard.properties"),1234,System.currentTimeMillis(),0).village(id).projects.getFirst();
    }
    private static Object invoke(String n,Object...a)throws Exception{return ConstructionSupportRecoverySelfTest.invoke(n,a);}
    private static Object field(Object c,String n)throws Exception{return ConstructionSupportRecoverySelfTest.field(c,n);}
    private static Object method(Object c,String n)throws Exception{var m=c.getClass().getDeclaredMethod(n);m.setAccessible(true);return m.invoke(c);}
    private static BlockState block(Object c)throws Exception{return (BlockState)field(c,"state");}
    private static BlockPos target(BlockPos o,Object c)throws Exception{return o.offset((int)field(c,"dx"),(int)field(c,"dy"),(int)field(c,"dz"));}
    private static void set(ServerLevel l,Map<BlockPos,BlockState>b,BlockPos p,BlockState s){l.getChunk(p);b.putIfAbsent(p,l.getBlockState(p));l.setBlock(p,s,18);}
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
}
