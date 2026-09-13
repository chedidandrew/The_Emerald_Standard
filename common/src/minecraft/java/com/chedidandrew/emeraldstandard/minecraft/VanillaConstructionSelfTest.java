package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Uses production placement, support ordering and saved cursors in a disposable smoke world. */
final class VanillaConstructionSelfTest {
    static void verify(ServerLevel level) {
        for (String style : List.of("plains","desert","savanna","taiga","snowy"))
            require(NaturalVillageIdentity.hasDefaultFamily(level.getServer().getResourceManager(),style),
                    "Default source-family gate rejected " + style);
        for (int i=0;i<VanillaVillageBuildings.manifest().size();i++) VanillaVillageBuildings.tick(level.getServer());
        require(VanillaBuildingCatalog.plans().size()==VanillaVillageBuildings.manifest().size(),
                "Runtime resource admission rejected default buildings: " + VanillaVillageBuildings.diagnostics());
        String[] names = VanillaVillageBuildings.manifest().stream()
                .map(e -> e.id().substring(e.id().lastIndexOf('/')+1)).toArray(String[]::new);
        List<String> failures = new ArrayList<>();
        for (int i=0; i<names.length; i++) {
            if (!names[i].matches(System.getProperty("the_emerald_standard.vanillaFixtureFilter",".*"))) continue;
            try { verifyOne(level,names[i],i%4); }
            catch (Exception error) {
                failures.add(names[i] + ": " + error);
                System.out.println("FAIL vanilla native " + failures.getLast());
            }
        }
        if (!failures.isEmpty()) throw new IllegalStateException("Vanilla construction failures: " + failures);
        System.out.println("PASS vanilla construction: selected catalog fixtures, native survival, bed access, empty containers and saved progress");
    }

    private static void verifyOne(ServerLevel level,String name,int rotation) throws Exception {
        var entry = VanillaVillageBuildings.manifest().stream().filter(e -> e.id().endsWith("/"+name)).findFirst().orElseThrow();
        VanillaConstructionPlan plan;
        try (var stream = level.getServer().getResourceManager().getResourceOrThrow(
                net.minecraft.resources.Identifier.parse(entry.id().replace("minecraft:", "minecraft:structure/") + ".nbt")).open()) {
            plan = VanillaVillageBuildings.convert(entry, stream.readAllBytes());
        }
        BlockPos origin = new BlockPos(3456,level.getMaxY()-48,3456);
        Map<BlockPos,BlockState> before = new LinkedHashMap<>();
        ServerPlayer observer = null;
        try {
            var state = EconomyState.fresh(7788,0,0);
            var village = state.village(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            village.architectureCharacter = "agrarian"; village.architectureDialect = village.naturalVillageStyle = plan.style();
            village.dimensionKey = "minecraft:overworld"; village.centerPos = origin.asLong();
            village.population = village.observedPopulation = 20; village.housingCapacity = 30;
            village.foodSupply = village.materialSupply = 2000; village.treasury = 1000;
            village.prosperity = village.safety = 100;
            var project = new EconomyState.VillageProject();
            project.projectId = village.projectSerial = 1;
            project.type = switch(plan.role()) {
                case "residence" -> VillageProsperityEngine.ProjectType.HOUSE;
                case "food" -> VillageProsperityEngine.ProjectType.GRANARY;
                case "craft" -> VillageProsperityEngine.ProjectType.SMITHY;
                default -> VillageProsperityEngine.ProjectType.MARKET_SQUARE;
            };
            project.vanillaPlan = plan; project.designSchema = VanillaConstructionPlan.SCHEMA;
            project.designRotation = rotation; project.originPos = origin.asLong();
            project.economicComplete = true; project.economicProgress = 1;
            project.sitePreparationComplete = true; project.constructionStarted = true;
            project.entranceApproachComplete = true; project.entranceApproachVersion = EconomyState.ENTRANCE_APPROACH_VERSION;
            project.trailAnchorSet = true; project.trailAnchorPos = origin.north(24).asLong();
            project.trailTotalBlocks = ((List<?>)invoke("managedProjectTrail",origin,village,project)).size();
            project.trailMaterializedBlocks = project.trailTotalBlocks; project.trailMaterializedComplete = true;
            List<?> canonical = (List<?>)invoke("projectTemplate",level,origin,village,project);
            project.totalBlocks = canonical.size();
            var bounds = invoke("bounds",origin,canonical);
            project.boundsMinPos = ((BlockPos)access(bounds,"minimum")).asLong();
            project.boundsMaxPos = ((BlockPos)access(bounds,"maximum")).asLong();
            village.projects.add(project);
            int side = Math.max(plan.width(),plan.depth())+4;
            for(int x=-2;x<side;x++) for(int z=-3;z<side;z++) {
                for(int y=-9;y<plan.height()+2;y++)
                    set(level,before,origin.offset(x,y,z),y<0 ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            require(invoke("mayUseProjectSite",level,village.villageId,project.projectId,origin,canonical,
                    invoke("excavationFloor",origin,project)) == VillageMaterializationPolicy.SiteAvailability.AVAILABLE,
                    name+" rejected a protected, loaded, flat natural site");
            project.sitePreparationPlan = (SitePreparationPlan)invoke("prepareProjectSitePlan",level,origin,village,project,canonical);
            require(project.sitePreparationPlan != null,name+" could not survey shallow foundation");
            project.sitePreparationComplete = project.sitePreparationPlan.cells().isEmpty();
            var directory = Files.createTempDirectory("tes-vanilla-native-");
            state.save(directory.resolve("the_emerald_standard.properties"));
            var economy = new EconomyService(); economy.configureEconomicClock(false,30);
            economy.configureVillageProsperity(true,true); economy.start(directory,7788,0);
            observer = new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"VanillaFixture"),ClientInformation.createDefault());
            observer.setPos(origin.getX()+48,origin.getY(),origin.getZ()); level.players().add(observer);
            var budgetType = Class.forName(VillageProsperityManager.class.getName()+"$MaterializationBudget");
            var budget = budgetType.getDeclaredConstructor(int.class,int.class); budget.setAccessible(true);
            var props = new Properties(); props.setProperty(EmeraldConfig.FORCED_DEVELOPMENT_KEY,"true");
            var config = EmeraldConfig.parse(props);
            for(int pulse=1;pulse<=250;pulse++) {
                if (pulse==3 && name.endsWith("_small_house_1")) {
                    economy = new EconomyService(); economy.configureEconomicClock(false,30);
                    economy.configureVillageProsperity(true,true); economy.start(directory,7788,0);
                    require(economy.developmentVillageSnapshot(village.villageId).village().projects.getFirst()
                            .vanillaPlan.hash().equals(plan.hash()),name+" rerolled its interrupted plan");
                }
                var pulseConfig = name.endsWith("_small_house_1") && pulse%3==0
                        ? EmeraldConfig.parse(new Properties()) : config;
                ForcedDevelopmentRuntime.reset(); ForcedDevelopmentRuntime.claim(level.getServer());
                invoke("materializeDevelopment",level,economy,pulseConfig,pulse*20L,budget.newInstance(64,1),
                        economy.developmentVillageSnapshot(village.villageId));
                var current = economy.developmentVillageSnapshot(village.villageId).village().projects.getFirst();
                if(current.materializedComplete) break;
                if(pulse==250) {
                    var ordered = (List<?>)invoke("constructionTemplate",canonical,current);
                    var cell = ordered.get(Math.min(current.materializedBlocks, ordered.size()-1));
                    BlockPos at = origin.offset((int)field(cell,"dx"),(int)field(cell,"dy"),(int)field(cell,"dz"));
                    throw new IllegalStateException(name+" stalled at "+current.materializedBlocks+"/"+current.totalBlocks
                            +" next="+at.toShortString()+" expected="+field(cell,"state")+" actual="+level.getBlockState(at)
                            +" "+ConstructionDiagnostics.recent(village.villageId+"/1",pulse*20L));
                }
            }
            var restart = new EconomyService(); restart.configureEconomicClock(false,30);
            restart.configureVillageProsperity(true,true); restart.start(directory,7788,0);
            var restored = restart.developmentVillageSnapshot(village.villageId).village().projects.getFirst();
            require(restored.materializedComplete && restored.vanillaPlan.hash().equals(plan.hash()),name+" lost completion or frozen cells");
            for(var cell:canonical) {
                BlockPos at = origin.offset((int)field(cell,"dx"),(int)field(cell,"dy"),(int)field(cell,"dz"));
                require(level.getBlockState(at).canSurvive(level,at),name+" has an unsupported block "+level.getBlockState(at)+" below="+level.getBlockState(at.below())+" above="+level.getBlockState(at.above())+" at "+at.toShortString());
                if (level.getBlockEntity(at) instanceof net.minecraft.world.Container container)
                    require(container.isEmpty(),name+" imported free container contents");
            }
            // These fixtures synchronously reuse non-ticking chunks; discard navigation
            // cache entries left by the previous fixture before testing the new building.
            before.keySet().forEach(level.getPathTypeCache()::invalidate);
            if (plan.beds()>0) {
                BlockPos entryPos = (BlockPos)invoke("projectEntrance",origin,project);
                // The real external path starts here; provide its two landing cells in this fixture.
                set(level,before,entryPos,Blocks.DIRT_PATH.defaultBlockState());
                var inward = switch(rotation) {
                    case 1 -> net.minecraft.core.Direction.WEST; case 2 -> net.minecraft.core.Direction.NORTH;
                    case 3 -> net.minecraft.core.Direction.EAST; default -> net.minecraft.core.Direction.SOUTH;
                };
                set(level,before,entryPos.relative(inward),Blocks.DIRT_PATH.defaultBlockState());
                var visitor = VillageWalkingSelfTest.walker(level,entryPos.above());
                visitor.setPos(entryPos.getX()+0.5,entryPos.getY()+1,entryPos.getZ()+0.5);
                visitor.setOnGround(true);
                ((net.minecraft.world.entity.ai.navigation.GroundPathNavigation)visitor.getNavigation()).setCanOpenDoors(true);
                for (var cell:canonical) {
                    var value=(BlockState)field(cell,"state");
                    if (!(value.getBlock() instanceof BedBlock) || value.getValue(BedBlock.PART)
                            != net.minecraft.world.level.block.state.properties.BedPart.HEAD) continue;
                    BlockPos bed=origin.offset((int)field(cell,"dx"),(int)field(cell,"dy"),(int)field(cell,"dz"));
                    // Navigate to a standing cell beside the bed: GroundPathNavigation projects
                    // a solid bed target upward, which is not the villager's sleeping approach.
                    BlockPos accessible = null;

                    // SleepInBed accepts a standing position within two blocks of the head.
                    // Cramped vanilla bedrooms often approach diagonally beside the foot.
                    for (BlockPos candidate : BlockPos.betweenClosed(bed.offset(-1,-1,-1),bed.offset(1,1,1))) {
                        BlockPos beside = candidate.immutable();
                        if (!bed.closerToCenterThan(new net.minecraft.world.phys.Vec3(
                                beside.getX()+.5,beside.getY(),beside.getZ()+.5),2)
                                || !(level.getBlockState(beside).isAir() || level.getBlockState(beside).getBlock() instanceof CarpetBlock)
                                || !level.getBlockState(beside.above()).isAir()) continue;
                        var path = visitor.getNavigation().createPath(beside,0);
                        if (path != null && path.canReach()) { accessible = beside; break; }
                    }
                    require(accessible != null,name+" has no reachable standing cell beside bed "+bed.toShortString()+" from "+entryPos);
                    if (name.endsWith("_small_house_1"))
                        VillageWalkingSelfTest.walk(level,visitor,accessible,name+" to bed");
                }
                visitor.discard();
            }
            System.out.println("PASS vanilla native "+name+" rotation "+rotation+" operations "+project.totalBlocks);
        } finally {
            if(observer!=null) { level.players().remove(observer); observer.discard(); }
            VillageConstructionActivitySelfTest.cleanupCrews(level);
            before.forEach((pos,value)->level.setBlock(pos,value,18));
            ConstructionDiagnostics.reset();
        }
    }
    private static void set(ServerLevel level,Map<BlockPos,BlockState> before,BlockPos p,BlockState s) {
        level.getChunk(p); before.putIfAbsent(p,level.getBlockState(p)); level.setBlock(p,s,18);
    }
    private static Object invoke(String name,Object...args) throws Exception {
        return ConstructionSupportRecoverySelfTest.invoke(name,args);
    }
    private static Object field(Object o,String name) throws Exception {
        return ConstructionSupportRecoverySelfTest.field(o,name);
    }
    private static Object access(Object o,String name) throws Exception {
        var m=o.getClass().getDeclaredMethod(name); m.setAccessible(true); return m.invoke(o);
    }
    private static void require(boolean ok,String message) { if(!ok)throw new IllegalStateException(message); }
}
