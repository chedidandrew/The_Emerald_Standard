package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import java.lang.reflect.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;

/** Disposable-world regression for a migrated courtyard Inn reported stuck at 3603/4873. */
final class ConstructionSupportRecoverySelfTest {
    static void verify(ServerLevel level) {
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        ServerPlayer observer=null;
        try {
            var state=EconomyState.fresh(1234,System.currentTimeMillis(),0);
            var village = state.village(UUID.fromString("e4d5e4f1-9958-0e58-9268-ffa2dcaee4e2"));
            village.villageId = UUID.fromString("e4d5e4f1-9958-0e58-9268-ffa2dcaee4e2");
            village.architectureCharacter = "agrarian"; village.architectureDialect = "taiga";
            village.developmentTier = 5;
            var project = new EconomyState.VillageProject();
            project.projectId = 6; project.type = VillageProsperityEngine.ProjectType.INN;
            project.designSchema = "blueprint_v2"; project.designTemplateId = "inn_courtyard_05";
            project.designTemplateRevision = 10; project.designPaletteId = "timber_forward";
            project.designDressingId = "lived_in"; project.designSeed = 5500102569780973282L;
            project.designStage = 2; project.designRotation = 1; project.designMirrored = true;
            project.designSignature = 4324551043519814560L;
            project.originPos = 405994674171971L; project.totalBlocks = 4873; project.materializedBlocks = 3603;
            project.constructionOrderCuts.addAll(List.of(2941,4873));
            var origin = new BlockPos(1344,level.getMaxY()-40,1344);
            List<?> canonical = (List<?>) invoke("projectTemplate",level,origin,village,project);
            List<?> ordered = (List<?>) invoke("constructionTemplate",canonical,project);
            var cells = new ArrayList<SupportedConstructionOrder.Cell>();
            for(Object p:ordered) cells.add(new SupportedConstructionOrder.Cell(
                    new BlockPos((int)field(p,"dx"),(int)field(p,"dy"),(int)field(p,"dz")),
                    (BlockState)field(p,"state"),(int)field(p,"constructionPhase")));
            require(cells.size()==4873,"same frozen Inn operation count");
            require(cells.get(3603).pos().equals(new BlockPos(19,1,-4))
                    &&cells.get(3603).state().is(Blocks.SPRUCE_FENCE),"exact reported stuck fence");
            require((boolean)method(ordered.get(3603),"isCosmetic"),"reported fence is optional, not required lighting");
            require(cells.get(3481).pos().equals(new BlockPos(19,0,-4)),"skipped foot in consumed prefix");
            // Exact saved migration cut and blueprint; no player save is loaded or mutated.
            for(var c:cells) {
                for(int y=-5;y<0;y++) set(level,before,origin.offset(c.pos().getX(),y,c.pos().getZ()),Blocks.DIRT.defaultBlockState());
                set(level,before,origin.offset(c.pos()),Blocks.AIR.defaultBlockState());
            }
            for(int i=0;i<3603;i++) set(level,before,origin.offset(cells.get(i).pos()),cells.get(i).state());
            BlockPos missingFoot=origin.offset(19,0,-4), fence=missingFoot.above();
            set(level,before,missingFoot,Blocks.GRASS_BLOCK.defaultBlockState());
            require(!SupportedConstructionOrder.supportedNow(level,origin,cells.get(3603),
                    SupportedConstructionOrder.supportPalette(cells)),"grass is not the authored stone support");
            String reason=SupportedConstructionOrder.waitReason(level,origin,cells.get(3603),
                    SupportedConstructionOrder.supportPalette(cells));
            require(reason.contains("spruce_fence")&&reason.contains("grass_block")
                    &&reason.contains(missingFoot.toShortString()),"diagnostics identify exact missing support/current terrain");
            ConstructionDiagnostics.record("support-fixture","waiting_for_support",3603,4873,100,reason);
            require(ConstructionDiagnostics.report("support-fixture",110).get("reason").equals(reason)
                    &&ConstructionDiagnostics.report("support-fixture",2000).equals(Map.of("observed",false)),
                    "debug captures live reason without claiming stale observations");

            village.dimensionKey="minecraft:overworld"; village.centerPos=origin.asLong();
            village.population=village.observedPopulation=30; village.housingCapacity=50;
            village.foodSupply=village.materialSupply=2000; village.treasury=1000;
            village.prosperity=village.safety=100;
            project.originPos=origin.asLong(); project.economicComplete=true; project.economicProgress=1;
            project.sitePreparationComplete=true; project.constructionStarted=true;
            project.entranceApproachComplete=true; project.entranceApproachVersion=EconomyState.ENTRANCE_APPROACH_VERSION;
            project.trailAnchorSet=true; project.trailAnchorPos=origin.east(20).asLong();
            project.trailTotalBlocks=((List<?>)invoke("managedProjectTrail",origin,village,project)).size();
            project.trailMaterializedBlocks=project.trailTotalBlocks; project.trailMaterializedComplete=true;
            Object bounds=invoke("bounds",origin,canonical);
            project.boundsMinPos=((BlockPos)method(bounds,"minimum")).asLong();
            project.boundsMaxPos=((BlockPos)method(bounds,"maximum")).asLong();
            project.designPlanHash=(String)invoke("blueprintPlanHash",village,project,
                    invoke("blueprintPlacementPlan",level,origin,village,project));
            project.designPlanHashVersion=1; village.projects.add(project); village.projectSerial=6;
            var directory=Files.createTempDirectory("tes-support-recovery-");
            state.save(directory.resolve("the_emerald_standard.properties"));
            var economy=new EconomyService(); economy.configureEconomicClock(false,30);
            economy.configureVillageProsperity(true,true); economy.start(directory,1234,0);
            observer=new ServerPlayer(level.getServer(),level,
                    new GameProfile(UUID.randomUUID(),"SupportFixture"),ClientInformation.createDefault());
            observer.setPos(origin.getX()+100,origin.getY(),origin.getZ()); level.players().add(observer);
            var props=new Properties(); props.setProperty(EmeraldConfig.FORCED_DEVELOPMENT_KEY,"true");
            var config=EmeraldConfig.parse(props);
            var type=Class.forName(VillageProsperityManager.class.getName()+"$MaterializationBudget");
            var budget=type.getDeclaredConstructor(int.class,int.class); budget.setAccessible(true);
            boolean passedCursor=false;
            for(int pulse=1;pulse<=8000;pulse++) {
                ForcedDevelopmentRuntime.reset(); ForcedDevelopmentRuntime.claim(level.getServer());
                invoke("materializeDevelopment",level,economy,config,pulse*20L,budget.newInstance(64,1),
                        economy.developmentVillageSnapshot(village.villageId));
                var current=economy.developmentVillageSnapshot(village.villageId).village().projects.getFirst();
                passedCursor |= current.materializedBlocks>3603;
                if(pulse==4) require(passedCursor,"saved Inn resumes promptly instead of waiting for decorative support");
                if(current.materializedComplete) break;
                if(pulse==8000) throw new IllegalStateException("Inn not complete: "+current.materializedBlocks+" / "+current.totalBlocks
                        +" "+ConstructionDiagnostics.recent(village.villageId+"/6",pulse*20L));
            }
            require(level.getBlockState(missingFoot).is(Blocks.GRASS_BLOCK)&&level.getBlockState(fence).isAir(),
                    "no forced replacement of terrain or floating decorative post");
            var restarted=new EconomyService(); restarted.configureEconomicClock(false,30);
            restarted.configureVillageProsperity(true,true); restarted.start(directory,1234,0);
            require(restarted.developmentVillageSnapshot(village.villageId).village().projects.getFirst().materializedComplete,
                    "completion remains saved after restart");
            System.out.println("PASS construction support recovery: exact 3603/4873 migrated Inn completes, unsafe optional dressing waived, no terrain overwrite/floating post, persisted completion and detailed diagnostics");
        } catch(Exception e) { throw new IllegalStateException("Support recovery fixture",e); }
        finally {
            if(observer!=null) { level.players().remove(observer); observer.discard(); }
            before.forEach((p,s)->level.setBlock(p,s,18));
            ConstructionDiagnostics.reset();
        }
    }
    private static void set(ServerLevel level,Map<BlockPos,BlockState> before,BlockPos p,BlockState s) {
        level.getChunk(p); before.putIfAbsent(p,level.getBlockState(p)); level.setBlock(p,s,18);
    }
    private static Object method(Object o,String name) throws Exception {
        Method m=o.getClass().getDeclaredMethod(name); m.setAccessible(true); return m.invoke(o);
    }
    private static void require(boolean ok,String message) { if(!ok)throw new IllegalStateException(message); }
    static Object invoke(String name,Object...args) throws Exception {
        for(Method m:VillageProsperityManager.class.getDeclaredMethods()) if(m.getName().equals(name)&&m.getParameterCount()==args.length) {
            m.setAccessible(true); return m.invoke(null,args);
        }
        throw new NoSuchMethodException(name);
    }
    static Object field(Object o,String name) throws Exception {
        Field f=o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
}
