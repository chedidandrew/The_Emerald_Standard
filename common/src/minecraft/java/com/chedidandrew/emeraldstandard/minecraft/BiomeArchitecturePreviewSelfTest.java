package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;

/** Native blueprint admission, deliberately independent of normal project selection. */
public final class BiomeArchitecturePreviewSelfTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var desk = AuthoredVillageStructuresSelfTest.class.getDeclaredMethod("registerExactDeskFixture");
        desk.setAccessible(true); desk.invoke(null);
        String galleryBefore=System.getProperty(StructureGallery.ENABLE_PROPERTY);
        System.setProperty(StructureGallery.ENABLE_PROPERTY,"true");
        try {
        verifyChairGeometry();
        verifyRoofJoinRejections();
        verifyDoorwayRejections();
        verifyFloorBearingRejection();
        if (BiomeArchitecturePreview.samples().size()!=13) throw new AssertionError("Preview scope changed");
        for(var sample:BiomeArchitecturePreview.samples()) {
            var plan=BiomeArchitecturePreview.plan(sample);
            if(!plan.equals(BiomeArchitecturePreview.plan(sample))) throw new AssertionError("Nondeterministic sample");
            long beds=plan.cells().values().stream().filter(s->s.getBlock() instanceof BedBlock
                    &&s.getValue(BedBlock.PART)==BedPart.HEAD).count();
            long expectedBeds=sample.style()==VillageArchitecture.BiomeDialect.PLAINS
                    ? PlainsLegacyArchitecturePreview.source(sample.role()).cells().values().stream()
                            .filter(s->s.getBlock() instanceof BedBlock&&s.getValue(BedBlock.PART)==BedPart.HEAD).count()
                    : sample.role().equals("HOUSE")?2:sample.role().equals("INN")?4:0;
            if(beds!=expectedBeds)
                throw new AssertionError("Unexpected beds in "+sample.id());
            if(sample.style()==VillageArchitecture.BiomeDialect.PLAINS) verifyLegacyCopy(plan);
            else {
                verifyFurnitureRejections(plan);
                if(plan.cells().values().stream().noneMatch(s->s.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock))
                    throw new AssertionError("Missing deliberate rug in "+sample.id());
                if(plan.cells().values().stream().noneMatch(s->s.getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock))
                    throw new AssertionError("Missing planting in "+sample.id());
            }
            plan.reverseView();
            if(sample.style()==VillageArchitecture.BiomeDialect.DESERT && plan.cells().values().stream()
                    .anyMatch(s->s.is(Blocks.ACACIA_STAIRS)||s.is(Blocks.ACACIA_PLANKS)||s.is(Blocks.ACACIA_LOG)))
                throw new AssertionError("Orange timber recolor leaked into desert architecture");
            System.out.println("PASS preview "+sample.id()+": "+plan.cells().size()
                    +" cells; "+plan.access().size()+" reachable declared approaches; "+beds+" beds; lighting admitted");
        }
        verifyFullCatalog();
        PreviewDoorwayAudit.report();
        if(VillageArchitecture.activeBlueprints().size()!=52
                ||VillageArchitecture.activeBlueprints().stream().anyMatch(b->b.templateRevision()!=11)
                ||VillageBankManager.galleryBankStructureVersion()!=12)
            throw new AssertionError("Preview altered the production catalog");
        System.out.println("PASS production catalog unchanged; 52 revision-11 designs and version-12 Bank");
        } finally {
            if(galleryBefore==null) System.clearProperty(StructureGallery.ENABLE_PROPERTY);
            else System.setProperty(StructureGallery.ENABLE_PROPERTY,galleryBefore);
        }
    }
    private static void verifyChairGeometry() {
        for(var toward:net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var seat=Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING,toward.getOpposite());
            var boxes=seat.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,net.minecraft.core.BlockPos.ZERO).toAabbs();
            var high=boxes.stream().filter(box->box.minY>=0.49).findFirst().orElseThrow();
            double highX=(high.minX+high.maxX)/2-0.5,highZ=(high.minZ+high.maxZ)/2-0.5;
            if(highX*toward.getStepX()+highZ*toward.getStepZ()>=0)
                throw new AssertionError("Native stair back is toward the table: "+toward);
            var at=net.minecraft.core.BlockPos.ZERO.above();
            var table=at.relative(toward);
            var cells=new java.util.HashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
            cells.put(at,seat); cells.put(table,Blocks.OAK_FENCE.defaultBlockState());
            cells.put(table.above(),Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
            if(PreviewSeatingAudit.validate(cells,java.util.Set.of(at))!=1) throw new AssertionError("Missing seat target");
            cells.put(at,seat.setValue(net.minecraft.world.level.block.StairBlock.FACING,toward));
            try { PreviewSeatingAudit.validate(cells,java.util.Set.of(at)); throw new AssertionError("Outward chair admitted"); }
            catch(IllegalStateException expected) { }
        }
        System.out.println("PASS native chair-back geometry and negative admission in all four directions");
    }
    private static void verifyDoorwayRejections() {
        var sample=new BiomeArchitecturePreview.Sample(VillageArchitecture.BiomeDialect.TAIGA,"HOUSE","doorway_test",9,5);
        var cells=new java.util.LinkedHashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(int x=0;x<=2;x++) for(int z=0;z<=3;z++)
            cells.put(new net.minecraft.core.BlockPos(x,0,z),Blocks.SPRUCE_PLANKS.defaultBlockState());
        var at=new net.minecraft.core.BlockPos(1,1,1);
        for(var facing:net.minecraft.core.Direction.Plane.HORIZONTAL)
            for(var hinge:net.minecraft.world.level.block.state.properties.DoorHingeSide.values()) {
                var lower=Blocks.SPRUCE_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING,facing)
                        .setValue(net.minecraft.world.level.block.DoorBlock.HINGE,hinge);
                cells.put(at,lower);
                cells.put(at.above(),lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
                var plan=new BiomeArchitecturePreview.Plan(sample,java.util.Map.copyOf(cells),java.util.Set.of(),at,5);
                if(PreviewDoorwayAudit.validate(plan)!=1) throw new AssertionError("Doorway count");
                for(var obstacle:java.util.List.of(Blocks.SPRUCE_LOG,Blocks.POTTED_FERN,Blocks.SPRUCE_FENCE,Blocks.STONE_BRICKS)) {
                    var blocked=new java.util.LinkedHashMap<>(cells);
                    blocked.put(at.relative(facing),obstacle.defaultBlockState());
                    expectDoorwayFailure(new BiomeArchitecturePreview.Plan(sample,java.util.Map.copyOf(blocked),java.util.Set.of(),at,5),"blocked door");
                }
                var lowCanopy=new java.util.LinkedHashMap<>(cells);
                lowCanopy.put(at.relative(facing).above(),Blocks.SPRUCE_SLAB.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,net.minecraft.world.level.block.state.properties.SlabType.TOP));
                expectDoorwayFailure(new BiomeArchitecturePreview.Plan(sample,java.util.Map.copyOf(lowCanopy),java.util.Set.of(),at,5),"blocked door");
                var orphan=new java.util.LinkedHashMap<>(cells); orphan.remove(at);
                expectDoorwayFailure(new BiomeArchitecturePreview.Plan(sample,java.util.Map.copyOf(orphan),java.util.Set.of(),at,5),"orphan door upper");
                var missing=new java.util.LinkedHashMap<>(cells); missing.remove(at.above());
                expectDoorwayFailure(new BiomeArchitecturePreview.Plan(sample,java.util.Map.copyOf(missing),java.util.Set.of(),at,5),"incomplete doorway");
            }
        // Clear space on both sides alone does not prove a secondary door is connected.
        for(int x=6;x<=8;x++) for(int z=0;z<=3;z++)
            cells.put(new net.minecraft.core.BlockPos(x,0,z),Blocks.SPRUCE_PLANKS.defaultBlockState());
        var remote=new net.minecraft.core.BlockPos(7,1,1);
        cells.put(remote,cells.get(at)); cells.put(remote.above(),cells.get(at.above()));
        expectDoorwayFailure(new BiomeArchitecturePreview.Plan(sample,java.util.Map.copyOf(cells),java.util.Set.of(),at,5),"disconnected doorway");
        System.out.println("PASS 57 doorway negatives and eight open-door positives: four directions, both hinges, plants/logs/fences/walls, low canopies, missing halves and disconnected rooms");
    }
    private static void expectDoorwayFailure(BiomeArchitecturePreview.Plan plan,String reason) {
        try { PreviewDoorwayAudit.validate(plan); throw new AssertionError("Invalid doorway admitted: "+reason); }
        catch(IllegalStateException expected) { if(!expected.getMessage().contains(reason)) throw expected; }
    }
    private static void verifyRoofJoinRejections() {
        for(var style:VillageArchitecture.BiomeDialect.values()) {
            if(style==VillageArchitecture.BiomeDialect.PLAINS) continue;
            for(var form:PreviewExteriorPrograms.Form.values()) for(int wallTop:new int[]{4,5,6}) {
                var sample=new BiomeArchitecturePreview.Sample(style,"HOUSE","join_test",18,16);
                var b=new BiomeArchitecturePreview.Builder(sample);
                b.room(0,17,2,15,wallTop);
                var occupied=new java.util.HashMap<>(b.cells);
                PreviewExteriorPrograms.shell(b,form,0,17,2,15,wallTop+2,1,wallTop);
                PreviewRoofEnvelope.seal(b);
                PreviewRoofEnvelope.validate(b.cells,b.roofJoins.keySet());
                occupied.forEach((pos,state)-> {
                    if(!state.equals(b.cells.get(pos))) throw new AssertionError("Roof touched occupied-storey cell: "+pos);
                });
                var at=b.roofJoins.keySet().iterator().next();
                var cells=new java.util.HashMap<>(b.cells);
                cells.remove(at);
                expectRoofJoinFailure(cells,b.roofJoins.keySet());
                cells.put(at,b.p.roofSlab().defaultBlockState());
                expectRoofJoinFailure(cells,b.roofJoins.keySet());
                cells.put(at,Blocks.GLASS.defaultBlockState());
                PreviewRoofEnvelope.validate(cells,b.roofJoins.keySet());
            }
        }
        System.out.println("PASS 120 regional roof-envelope cases; missing cells and half-slab air bands rejected; glazed joins admitted");
    }
    private static void expectRoofJoinFailure(java.util.Map<net.minecraft.core.BlockPos,
            net.minecraft.world.level.block.state.BlockState> cells,java.util.Set<net.minecraft.core.BlockPos> joins) {
        try { PreviewRoofEnvelope.validate(cells,joins); throw new AssertionError("Roof gap admitted"); }
        catch(IllegalStateException expected) { if(!expected.getMessage().contains("roof-to-wall join")) throw expected; }
    }
    private static void verifyFullCatalog() {
        var samples=BiomeArchitectureCatalogPreview.samples();
        if(samples.size()!=375||samples.stream().map(BiomeArchitecturePreview.Sample::id).distinct().count()!=375)
            throw new AssertionError("Full review requires 52 designs, Bank and 22 compact designs in each of five styles");
        verifyTierPolicy(samples);
        var failures=new java.util.ArrayList<String>();
        for(var style:VillageArchitecture.BiomeDialect.values()) {
            var region=samples.stream().filter(s->s.style()==style).toList();
            if(region.size()!=75) throw new AssertionError("Incomplete style: "+style);
            var unique=new java.util.HashSet<java.util.Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>>();
            var programs=new java.util.HashSet<PreviewExteriorPrograms.Program>();
            var silhouettes=new java.util.HashMap<String,String>();
            int chairs=0,roofJoins=0,rooms=0,upper=0;
            var interiors=new StringBuilder();
            for(var sample:region) {
                try {
                var plan=BiomeArchitecturePreview.plan(sample);
                boolean compact=PreviewCompactBuildings.isCompact(sample);
                var joins=compact?java.util.Set.<net.minecraft.core.BlockPos>of():BiomeArchitectureCatalogPreview.roofJoins(sample);
                PreviewRoofEnvelope.validate(plan.cells(),joins);
                roofJoins+=joins.size();
                if(!plan.equals(BiomeArchitecturePreview.plan(sample))) throw new AssertionError("Unstable catalog plan");
                if(!unique.add(plan.cells())) throw new AssertionError("Duplicate design geometry: "+sample.id());
                chairs+=PreviewSeatingAudit.validate(plan.cells(),PreviewSeatingAudit.lowStairs(plan.cells()));
                if(sample.width()>50||sample.depth()>74) throw new AssertionError("Catalog exceeds review plot");
                var layout=PreviewRoomLayout.layout(plan); rooms+=layout.partitions(); upper+=layout.upperLevels();
                if(!PreviewRoomLayout.reachable(plan.cells(),plan.entrance()).containsAll(layout.upperTargets()))
                    throw new AssertionError("Unreachable upper storey");
                if(compact) {
                    BiomeArchitecturePreview.validate(plan);
                    long beds=plan.cells().values().stream().filter(s->s.getBlock() instanceof BedBlock
                            &&s.getValue(BedBlock.PART)==BedPart.HEAD).count();
                    if(beds!=PreviewCompactBuildings.spec(sample).beds()) throw new AssertionError("Compact bed capacity "+beds);
                    String previous=silhouettes.putIfAbsent("compact:"+exteriorSilhouette(plan),sample.id());
                    if(previous!=null) throw new AssertionError("Repeated compact exterior silhouette: "+previous+" / "+sample.id());
                    verifyUpperLevelRejections(plan);
                } else if(style==VillageArchitecture.BiomeDialect.PLAINS) {
                    var source=sample.role().equals("BANK")?PlainsLegacyArchitecturePreview.source("BANK")
                            :PlainsLegacyArchitecturePreview.source(sample.role(),BiomeArchitectureCatalogPreview.masterId(sample));
                    verifyLegacyCopy(plan,source);
                } else {
                    if(!sample.role().equals("BANK")&&!programs.add(PreviewExteriorPrograms.program(BiomeArchitectureCatalogPreview.masterId(sample))))
                        throw new AssertionError("Repeated exterior program: "+sample.id());
                    if(!sample.role().equals("BANK")) {
                        String previous=silhouettes.putIfAbsent(exteriorSilhouette(plan),sample.id());
                        if(previous!=null) throw new AssertionError("Repeated normalized exterior silhouette: "+previous+" / "+sample.id());
                    }
                    BiomeArchitecturePreview.validate(plan);
                    long beds=plan.cells().values().stream().filter(s->s.getBlock() instanceof BedBlock
                            &&s.getValue(BedBlock.PART)==BedPart.HEAD).count();
                    long expected=sample.role().equals("COTTAGE")?2:sample.role().equals("HOUSE")?4:sample.role().equals("INN")?8:0;
                    if(beds!=expected) throw new AssertionError("Catalog bed capacity: "+sample.id()+" "+beds+" expected "+expected);
                }
                if(style==VillageArchitecture.BiomeDialect.TAIGA) {
                    long moss=plan.cells().values().stream().filter(s->s.is(Blocks.MOSSY_COBBLESTONE)).count();
                    long plain=plan.cells().values().stream().filter(s->s.is(Blocks.COBBLESTONE)).count();
                    if(moss>3||plain<=moss) throw new AssertionError("Excess Taiga moss: "+sample.id());
                    if(PreviewDoorwayAudit.baseline(plan).entrySet().stream().anyMatch(e->e.getKey().getY()>=4
                            &&(e.getValue().is(Blocks.SPRUCE_STAIRS)||e.getValue().is(Blocks.SPRUCE_SLAB)
                                ||e.getValue().is(Blocks.SPRUCE_PLANKS)&&!layout.floors().contains(e.getKey()))))
                        throw new AssertionError("Non-log timber roofing in Taiga: "+sample.id());
                    long courses=plan.cells().entrySet().stream().filter(e->e.getKey().getY()>=4&&e.getValue().is(Blocks.SPRUCE_LOG)
                            &&e.getValue().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)
                                    !=net.minecraft.core.Direction.Axis.Y).count();
                    if(courses<8) throw new AssertionError("Missing horizontal bark-on roof courses: "+sample.id());
                }
                if(style==VillageArchitecture.BiomeDialect.DESERT&&plan.cells().values().stream()
                        .anyMatch(s->s.is(Blocks.ACACIA_STAIRS)||s.is(Blocks.ACACIA_PLANKS)||s.is(Blocks.ACACIA_LOG)))
                    throw new AssertionError("Desert timber recolor: "+sample.id());
                for(var cell:plan.cells().entrySet()) if(cell.getValue().is(Blocks.SNOW)) {
                    var below=plan.cells().get(cell.getKey().below());
                    if(below==null||!below.isFaceSturdy(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
                            cell.getKey().below(),net.minecraft.core.Direction.UP))
                        throw new AssertionError("Snow decoration lost its final roof bearing: "+sample.id()+" "+cell.getKey());
                }
                System.out.println("PASS catalog "+sample.id()+": "+plan.cells().size()+" cells; "+plan.access().size()+" approaches");
                } catch(RuntimeException|AssertionError error) {
                    failures.add(sample.id()+": "+error.getMessage());
                    System.err.println("CATALOG FAILURE "+failures.getLast());
                }
            }
            if(style!=VillageArchitecture.BiomeDialect.PLAINS&&programs.size()!=52)
                failures.add("Incomplete unique exterior programs: "+style+" "+programs.size());
            System.out.println("PASS style seating audit "+style+": "+chairs+" unambiguous table-facing ground seats");
            // Revision 8 explicitly redesigns interiors. Do not re-bless the retired revision-7
            // interior hashes: enforce usable rooms/levels, beds, doors, seating and supports.
            if(rooms<12||upper<8) failures.add("Insufficient purposeful rooms/upper levels: "+style+" "+rooms+"/"+upper);
            System.out.println("ROOM COMPOSITION "+style+": "+rooms+" partitioned rooms; "+upper+" accessible upper levels; "+roofJoins+" closed roof joins");
        }
        if(!failures.isEmpty()) throw new AssertionError("Catalog admission failures ("+failures.size()+"): "+failures);
    }
    private static void verifyTierPolicy(java.util.List<BiomeArchitecturePreview.Sample> samples) {
        for(var style:VillageArchitecture.BiomeDialect.values()) for(String role:samples.stream().map(BiomeArchitecturePreview.Sample::role).distinct().toList()) {
            var first=PreviewArchitectureTiers.eligible(samples,style,role,1);
            var fifth=PreviewArchitectureTiers.eligible(samples,style,role,5);
            if(first.size()<2||first.stream().anyMatch(s->PreviewArchitectureTiers.size(s).minimumTier!=1)
                    ||!fifth.containsAll(first)||fifth.size()!=samples.stream().filter(s->s.style()==style&&s.role().equals(role)).count())
                throw new AssertionError("Tier progression missing compact designs: "+style+" "+role);
            var selected=PreviewArchitectureTiers.select(samples,style,role,1,12345);
            if(!first.contains(selected)||!selected.equals(PreviewArchitectureTiers.select(samples,style,role,1,12345)))
                throw new AssertionError("Unstable staged tier selection");
        }
        System.out.println("PASS staged tiers: two compact designs per role/style at tier 1; all sizes retained at tier 5; seeded selection deterministic");
    }
    private static void verifyUpperLevelRejections(BiomeArchitecturePreview.Plan plan) {
        if(PreviewRoomLayout.layout(plan).upperLevels()==0) return;
        var targets=PreviewRoomLayout.layout(plan).upperTargets();
        var rung=plan.cells().entrySet().stream().filter(e->e.getValue().is(Blocks.LADDER)&&e.getKey().getY()==2)
                .map(java.util.Map.Entry::getKey).findFirst().orElseThrow();
        for(boolean solid:new boolean[]{false,true}) {
            var broken=new java.util.HashMap<>(plan.cells());
            if(solid) broken.put(rung.above(2),Blocks.COBBLESTONE.defaultBlockState()); else broken.remove(rung);
            if(PreviewRoomLayout.reachable(broken,plan.entrance()).containsAll(targets))
                throw new AssertionError("Disconnected upper floor admitted in "+plan.sample().id());
        }
        var target=targets.iterator().next(); var blocked=new java.util.HashMap<>(plan.cells());
        blocked.put(target.above(),Blocks.COBBLESTONE.defaultBlockState());
        if(PreviewRoomLayout.reachable(blocked,plan.entrance()).contains(target)) throw new AssertionError("Low upper headroom admitted");
    }
    private static void verifyFloorBearingRejection() {
        var sample=new BiomeArchitecturePreview.Sample(VillageArchitecture.BiomeDialect.TAIGA,"HOUSE","floor_bearing_negative",7,7);
        var ground=net.minecraft.core.BlockPos.ZERO; var floor=ground.above(4);
        var p=new BiomeArchitecturePreview.Plan(sample,java.util.Map.of(ground,Blocks.COBBLESTONE.defaultBlockState(),
                floor,Blocks.SPRUCE_PLANKS.defaultBlockState()),java.util.Set.of(),ground.above(),6);
        PreviewRoomLayout.register(p,0,0,java.util.Set.of(floor),java.util.Set.of());
        try { PreviewRoomLayout.validate(p); throw new AssertionError("Floating upper floor admitted"); }
        catch(IllegalStateException expected) { if(!expected.getMessage().contains("unanchored upper-floor plate")) throw expected; }
        System.out.println("PASS elevated floor plates require structural connection to ground; floating islands rejected");
    }
    private static void appendInteriorSnapshot(StringBuilder target,BiomeArchitecturePreview.Plan plan) {
        target.append(plan.sample().id()).append('\n');
        PreviewDoorwayAudit.baseline(plan).entrySet().stream().filter(entry->entry.getKey().getY()<=4)
                .sorted(java.util.Comparator.comparingInt((java.util.Map.Entry<net.minecraft.core.BlockPos,
                        net.minecraft.world.level.block.state.BlockState> entry)->entry.getKey().getX())
                        .thenComparingInt(entry->entry.getKey().getY()).thenComparingInt(entry->entry.getKey().getZ()))
                .forEach(entry->target.append(entry.getKey().getX()).append(',').append(entry.getKey().getY())
                        .append(',').append(entry.getKey().getZ()).append(':').append(entry.getValue()).append('\n'));
    }
    private static String snapshotHash(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }
    /** Palette/furnishing-independent top-height and footprint projection, normalized in X/Z.
     * A differently colored or simply resized copy of one shell must not qualify as variety. */
    private static String exteriorSilhouette(BiomeArchitecturePreview.Plan plan) {
        int width=plan.sample().width(),depth=plan.sample().depth();
        int[][] heights=new int[width][depth];
        boolean[][] floors=new boolean[width][depth];
        for(var entry:plan.cells().entrySet()) {
            var pos=entry.getKey(); var state=entry.getValue();
            if(pos.getY()==0) floors[pos.getX()][pos.getZ()]=true;
            if(pos.getY()<4||state.is(Blocks.SNOW)||state.is(Blocks.LANTERN)||state.is(Blocks.IRON_CHAIN)
                    ||state.getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock) continue;
            if(!state.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,pos).isEmpty())
                heights[pos.getX()][pos.getZ()]=Math.max(heights[pos.getX()][pos.getZ()],pos.getY());
        }
        var result=new StringBuilder();
        for(int z=0;z<32;z++) for(int x=0;x<32;x++) {
            int px=Math.min(width-1,x*width/32),pz=Math.min(depth-1,z*depth/32);
            result.append((char)('A'+heights[px][pz])).append(floors[px][pz]?'1':'0');
        }
        return result.toString();
    }
    private static void verifyFurnitureRejections(BiomeArchitecturePreview.Plan plan) {
        var at=new net.minecraft.core.BlockPos(plan.entrance().getX(),1,plan.entrance().getZ()+1);
        for(var state:java.util.List.of(Blocks.SANDSTONE_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                        net.minecraft.world.level.block.state.properties.SlabType.TOP),Blocks.POTTED_CACTUS.defaultBlockState(),
                        Blocks.OAK_PRESSURE_PLATE.defaultBlockState())) {
            var cells=new java.util.HashMap<>(plan.cells());
            if(!state.is(Blocks.SANDSTONE_SLAB)) cells.remove(at.below());
            cells.put(at,state);
            var broken=new BiomeArchitecturePreview.Plan(plan.sample(),java.util.Map.copyOf(cells),
                    plan.access(),plan.entrance(),plan.height());
            try {
                BiomeArchitecturePreview.validateFurnitureSupport(broken);
                throw new AssertionError("Floating fixture admitted: "+state);
            } catch(IllegalStateException expected) {
                if(!expected.getMessage().contains("unsupported interior fixture")) throw expected;
            }
        }
        var cells=new java.util.HashMap<>(plan.cells());
        cells.remove(at.below()); cells.put(at,Blocks.BARREL.defaultBlockState());
        cells.put(at.above(),Blocks.POTTED_CACTUS.defaultBlockState());
        try {
            BiomeArchitecturePreview.validateFurnitureSupport(new BiomeArchitecturePreview.Plan(plan.sample(),
                    java.util.Map.copyOf(cells),plan.access(),plan.entrance(),plan.height()));
            throw new AssertionError("Floating cabinet with pot admitted");
        } catch(IllegalStateException expected) {
            if(!expected.getMessage().contains("unsupported interior fixture")) throw expected;
        }
    }
    private static void verifyLegacyCopy(BiomeArchitecturePreview.Plan plan) {
        var original=PlainsLegacyArchitecturePreview.source(plan.sample().role());
        verifyLegacyCopy(plan,original);
    }
    private static void verifyLegacyCopy(BiomeArchitecturePreview.Plan plan,PlainsLegacyArchitecturePreview.Source original) {
        int minX=original.cells().keySet().stream().mapToInt(net.minecraft.core.BlockPos::getX).min().orElseThrow();
        int minZ=original.cells().keySet().stream().mapToInt(net.minecraft.core.BlockPos::getZ).min().orElseThrow();
        var baseline=PreviewDoorwayAudit.baseline(plan);
        if(original.cells().size()!=baseline.size()) throw new AssertionError("Plains geometry changed outside doorway audit");
        int changed=0;
        var corrected=PreviewSeatingAudit.corrected(original.cells(),original.seats());
        PreviewSeatingAudit.validate(corrected,original.seats());
        for(var entry:original.cells().entrySet()) {
            var actual=baseline.get(entry.getKey().offset(-minX,0,-minZ));
            var expected=original.roofCells().contains(entry.getKey())
                    ? PlainsLegacyArchitecturePreview.oakRoof(corrected.get(entry.getKey()),entry.getKey().getY()) : corrected.get(entry.getKey());
            if(!expected.equals(actual)) throw new AssertionError("Unaudited legacy change: "+entry.getKey());
            if(!actual.equals(entry.getValue())) {
                changed++;
                for(var property:entry.getValue().getProperties())
                    if(!actual.hasProperty(property)||(!property.equals(net.minecraft.world.level.block.StairBlock.FACING)
                            &&!actual.getValue(property).equals(entry.getValue().getValue(property))))
                        throw new AssertionError("Roof state property changed: "+property.getName());
            }
        }
        // Some open-market or already-oak legacy masters do not contain dark roof cells.
        if(changed==0&&!plan.sample().id().startsWith("catalog_")) throw new AssertionError("No oak roof substitution in "+plan.sample().id());
        System.out.println("PASS retained pre-doorway legacy Plains copy: "+changed+" roof/seat cells corrected; "
                +PreviewDoorwayAudit.edits(plan)+" separately scoped doorway edits in final plan");
    }
}
