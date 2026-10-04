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
        verifyDoorwayGlazing();
        verifyWindowLighting();
        verifyFloorBearingRejection();
        verifyRoomCeilings();
        verifyInteriorCirculation();
        verifyRoomFurnishings();
        verifyLadderSupports();
        verifySupportRejections();
        verifyOutdoorGrade();
        verifyTaigaOverhangs();
        verifyCompactReviewLayout();
        if (BiomeArchitecturePreview.samples().size()!=13) throw new AssertionError("Preview scope changed");
        for(var sample:BiomeArchitecturePreview.samples()) {
            var plan=BiomeArchitecturePreview.plan(sample);
            verifyDesertDoors(plan);
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
        verifyFacadeRejections();
        PreviewFacadePrograms.report(BiomeArchitectureCatalogPreview.samples());
        PreviewOutdoorPrograms.report(BiomeArchitectureCatalogPreview.samples());
        PreviewSupportAudit.report(BiomeArchitecturePreview.samples());
        PreviewSupportAudit.report(BiomeArchitectureCatalogPreview.samples());
        verifyOutdoorRejections();
        PreviewDoorwayAudit.report();
        PreviewDoorwayGlazing.report(BiomeArchitectureCatalogPreview.samples());
        PreviewWindowLighting.report(BiomeArchitectureCatalogPreview.samples());
        for(var style:VillageArchitecture.BiomeDialect.values()) {
            int designs=0,rooms=0,blocks=0,upper=0;
            for(var sample:BiomeArchitectureCatalogPreview.samples()) if(sample.style()==style) {
                var edits=PreviewFacadePrograms.audit(BiomeArchitecturePreview.plan(sample)).furnishings();
                if(!edits.additions().isEmpty()) designs++;
                rooms+=edits.rooms();blocks+=edits.additions().size();
                upper+=(int)edits.additions().keySet().stream().filter(at->at.getY()>3).count();
            }
            if(designs==0||upper==0) throw new AssertionError("Missing room/upper-floor furniture coverage "+style);
            System.out.println("FURNISHING CENSUS "+style+": "+designs+" designs, "+rooms+" rooms, "+blocks+" added cells, "+upper+" upper-floor cells");
        }
        int dividerColumns=0,upperLevels=0,ladderRungs=0,ladderFrames=0,ladderDesigns=0;
        for(var sample:BiomeArchitectureCatalogPreview.samples()) {
            var p=BiomeArchitecturePreview.plan(sample);
            PreviewRoomLayout.validate(p);
            var narrow=PreviewRoomLayout.enclosedCorridors(p);
            var drops=PreviewRoomLayout.upperDrops(p.cells(),p.entrance());
            if(!narrow.isEmpty()||!drops.isEmpty()) throw new AssertionError("Incomplete circulation "+sample.id()+": narrow="+narrow+" drops="+drops);
            dividerColumns+=PreviewRoomLayout.layout(p).dividerTops().size();
            upperLevels+=PreviewRoomLayout.layout(p).upperLevels();
            int rungs=(int)p.cells().values().stream().filter(s->s.getBlock() instanceof net.minecraft.world.level.block.LadderBlock).count();
            if(rungs>0) ladderDesigns++;
            ladderRungs+=rungs;
            var frames=PreviewFacadePrograms.audit(p).ladderBearings();ladderFrames+=frames.size();
            if(!frames.isEmpty()) System.out.println("LADDER FRAMED "+sample.id()+": "+frames);
            verifyLadderPlacementOrder(p);
        }
        System.out.println("LADDER CENSUS: 375 designs; "+ladderDesigns+" ladder-bearing designs; "+ladderRungs
                +" sturdy-backed rungs; "+ladderFrames+" glazed bearings framed; all ladders placed after their supports");
        System.out.println("CEILING CENSUS: 375 designs; "+dividerColumns+" closed divider columns; "+upperLevels
                +" accessible upper levels; zero usable floor areas below three-block structural headroom");
        System.out.println("CIRCULATION CENSUS: 375 designs; zero long one-block enclosed corridors; zero unguarded indoor upper-floor drops; roofed walking areas have block-light >=7 without skylight");
        if(VillageArchitecture.legacyBlueprints().size()!=52
                ||VillageArchitecture.legacyBlueprints().stream().anyMatch(b->b.templateRevision()!=11)
                ||VillageBankManager.galleryBankStructureVersion()!=12)
            throw new AssertionError("Preview altered the production catalog");
        System.out.println("PASS legacy review source unchanged; 52 revision-11 designs and version-12 Bank");
        } finally {
            if(galleryBefore==null) System.clearProperty(StructureGallery.ENABLE_PROPERTY);
            else System.setProperty(StructureGallery.ENABLE_PROPERTY,galleryBefore);
        }
    }
    private static void verifyRoomFurnishings() {
        for(var style:VillageArchitecture.BiomeDialect.values()) {
            var source=ceilingFixture(style,8);
            var cells=new java.util.LinkedHashMap<>(source.cells());
            var edits=PreviewInteriorFurnishings.apply(source,cells);
            var furnished=new BiomeArchitecturePreview.Plan(source.sample(),java.util.Map.copyOf(cells),edits.approaches(),source.entrance(),source.height());
            if(edits.additions().isEmpty()||edits.rooms()==0) throw new AssertionError("Empty room left unfurnished "+style);
            source.cells().forEach((at,state)-> {if(!state.equals(cells.get(at))) throw new AssertionError("Original furniture replaced "+at);});
            PreviewInteriorFurnishings.validate(furnished,edits);
            PreviewRoomLayout.validate(furnished);PreviewDoorwayAudit.validate(furnished);
            PreviewWindowLighting.validateViews(furnished);BiomeArchitecturePreview.validateFurnitureSupport(furnished);
            if(!PreviewRoomLayout.reachable(cells,source.entrance()).containsAll(edits.approaches()))
                throw new AssertionError("Unreachable workstation");
            var repeat=new java.util.LinkedHashMap<>(source.cells());
            if(!edits.equals(PreviewInteriorFurnishings.apply(source,repeat))||!cells.equals(repeat))
                throw new AssertionError("Nondeterministic furnishings");
            var at=edits.additions().keySet().iterator().next();var broken=new java.util.LinkedHashMap<>(cells);
            broken.remove(at.below());
            try {PreviewInteriorFurnishings.validate(new BiomeArchitecturePreview.Plan(source.sample(),java.util.Map.copyOf(broken),edits.approaches(),source.entrance(),source.height()),edits);
                throw new AssertionError("Missing furniture bearing accepted");}
            catch(IllegalStateException expected) { }
        }
        System.out.println("PASS additive room furnishings: five palettes, usable approaches, original furniture retained, repeatability and missing-bearing rejection");
    }
    private static void verifyLadderSupports() {
        for(var style:VillageArchitecture.BiomeDialect.values()) for(var facing:net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var sample=new BiomeArchitecturePreview.Sample(style,"HOUSE","ladder_fixture_"+style.id()+"_"+facing,11,11);
            var cells=new java.util.LinkedHashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
            var rung=new net.minecraft.core.BlockPos(5,1,5);
            var backing=rung.relative(facing.getOpposite());
            var glass=backing.relative(facing.getClockWise());
            for(int y=0;y<=4;y++) cells.put(backing.atY(y),Blocks.COBBLESTONE.defaultBlockState());
            for(int y=1;y<=4;y++) cells.put(rung.atY(y),Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING,facing));
            cells.put(glass,Blocks.GLASS_PANE.defaultBlockState());
            PreviewLadderSupport.validate(sample.id(),cells);
            for(var bad:java.util.List.of(Blocks.GLASS_PANE,Blocks.STAINED_GLASS_PANE.blue(),Blocks.AIR)) {
                var broken=new java.util.LinkedHashMap<>(cells);
                broken.put(backing.above(),bad.defaultBlockState());
                try { PreviewLadderSupport.validate(sample.id(),broken);throw new AssertionError("Non-sturdy ladder bearing admitted"); }
                catch(IllegalStateException expected) { if(!expected.getMessage().contains("Unsupported review ladder")) throw expected; }
                var p=new BiomeArchitecturePreview.Plan(sample,java.util.Map.copyOf(broken),java.util.Set.of(),rung,6);
                if(bad==Blocks.AIR) {
                    try { PreviewLadderSupport.frameGlazedBearings(p,broken);throw new AssertionError("Arbitrary missing ladder wall repaired"); }
                    catch(IllegalStateException expected) { if(!expected.getMessage().contains("Unsupported review ladder")) throw expected; }
                } else {
                    var changed=PreviewLadderSupport.frameGlazedBearings(p,broken);
                    if(!changed.equals(java.util.Set.of(backing.above()))
                            ||!broken.get(backing.above()).is(PreviewDoorwayGlazing.jamb(sample))
                            ||!broken.get(glass).is(Blocks.GLASS_PANE)
                            ||!PreviewLadderSupport.frameGlazedBearings(p,broken).isEmpty())
                        throw new AssertionError("Ladder mullion changed neighboring glazing or was not idempotent");
                }
            }
            var expected=new StructureGalleryBlock(rung,cells.get(rung));
            try { StructureGallery.validateAttachmentState(0,expected,Blocks.AIR.defaultBlockState(),true);
                throw new AssertionError("A disappeared rung passed native survival as air"); }
            catch(IllegalStateException rejected) { if(!rejected.getMessage().contains("missing or unsupported")) throw rejected; }
        }
        System.out.println("PASS ladder supports: five palettes/four directions; ordinary and stained panes rejected, scoped mullions preserve windows, missing walls and vanished rungs rejected");
    }
    private static void verifyLadderPlacementOrder(BiomeArchitecturePreview.Plan p) {
        var blocks=p.blocks(net.minecraft.core.BlockPos.ZERO);
        var positions=new java.util.HashMap<net.minecraft.core.BlockPos,Integer>();
        for(int i=0;i<blocks.size();i++) positions.put(blocks.get(i).position(),i);
        for(var block:blocks) if(block.state().getBlock() instanceof net.minecraft.world.level.block.LadderBlock) {
            var backing=block.position().relative(block.state().getValue(net.minecraft.world.level.block.LadderBlock.FACING).getOpposite());
            if(!positions.containsKey(backing)||positions.get(backing)>=positions.get(block.position()))
                throw new AssertionError("Ladder placed before bearing: "+p.sample().id()+" "+block.position());
        }
    }
    private static void verifyRoomCeilings() {
        for(var style:VillageArchitecture.BiomeDialect.values()) {
            var p=ceilingFixture(style,8);
            var layout=PreviewRoomLayout.layout(p);
            if(layout.partitions()==0||layout.dividerTops().isEmpty()||layout.upperLevels()!=1)
                throw new AssertionError("Finished rooms and three-block loft missing: "+style);
            var ceiling=new net.minecraft.core.BlockPos(4,4,4);
            if(!net.minecraft.world.level.block.Block.isShapeFullBlock(p.cells().get(ceiling)
                    .getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,ceiling)))
                throw new AssertionError("Missing finished room ceiling: "+style);
            if(PreviewRoomLayout.layout(ceilingFixture(style,7)).upperLevels()!=0)
                throw new AssertionError("Two-block loft admitted: "+style);
            var changed=new java.util.HashMap<>(p.cells());
            changed.put(new net.minecraft.core.BlockPos(7,3,4),Blocks.STONE.defaultBlockState());
            expectRoomFailure(p,changed,"ceiling below three clear blocks");
            changed=new java.util.HashMap<>(p.cells());
            changed.put(new net.minecraft.core.BlockPos(4,3,4),Blocks.STONE.defaultBlockState());
            expectRoomFailure(p,changed,"ceiling below three clear blocks over bed");
            changed=new java.util.HashMap<>(p.cells());
            changed.remove(layout.dividerTops().iterator().next().above());
            expectRoomFailure(p,changed,"room divider does not meet its ceiling");
        }
        System.out.println("PASS room ceilings: five biome finishes, closed partitions, three-block lofts; low ceilings and uncapped dividers rejected");
    }
    private static BiomeArchitecturePreview.Plan ceilingFixture(VillageArchitecture.BiomeDialect style,int roof) {
        var b=new BiomeArchitecturePreview.Builder(new BiomeArchitecturePreview.Sample(style,"HOUSE","ceiling_fixture_"+style+roof,16,17));
        b.room(2,11,2,13,roof-1);b.terrace(2,11,2,13,roof);b.door(6,2);b.floor(5,7,0,1);
        b.beds(4,4,1);
        // Sleeping space is not walkable air: its low beam must still be lifted.
        b.put(4,3,4,Blocks.OAK_PLANKS);b.put(4,3,5,Blocks.OAK_PLANKS);
        for(int y=4;y<roof;y++) b.put(8,y,10,Blocks.IRON_CHAIN);
        b.access.add(new net.minecraft.core.BlockPos(6,1,10));
        return PreviewRoomLayout.apply(b.finish());
    }
    private static void expectRoomFailure(BiomeArchitecturePreview.Plan p,
            java.util.Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState> cells,String reason) {
        try { PreviewRoomLayout.validate(new BiomeArchitecturePreview.Plan(p.sample(),java.util.Map.copyOf(cells),p.access(),p.entrance(),p.height())); }
        catch(IllegalStateException expected) {
            if(!expected.getMessage().contains(reason)) throw expected;
            return;
        }
        throw new AssertionError("Invalid room admitted: "+reason);
    }
    private static void verifyInteriorCirculation() {
        for(var style:VillageArchitecture.BiomeDialect.values()) {
            var b=new BiomeArchitecturePreview.Builder(new BiomeArchitecturePreview.Sample(style,"HOUSE","corridor_fixture_"+style,18,20));
            b.room(2,14,2,16,7);b.terrace(2,14,2,16,8);b.door(7,2);b.floor(6,8,0,1);
            for(int x:new int[]{6,8}) for(int z=3;z<=13;z++) for(int y=1;y<=7;y++) b.put(x,y,z,b.p.wall());
            var source=b.finish();
            if(PreviewRoomLayout.enclosedCorridors(source).isEmpty()) throw new AssertionError("Cramped corridor fixture did not exercise repair");
            expectRoomFailure(source,source.cells(),"cramped enclosed corridor");
            var fixed=PreviewRoomLayout.apply(source);
            if(!PreviewRoomLayout.enclosedCorridors(fixed).isEmpty()) throw new AssertionError("Cramped room corridor retained: "+style);
            PreviewSupportAudit.validate(fixed.cells(),"widened corridor");
            var cells=new java.util.LinkedHashMap<>(fixed.cells());
            cells.entrySet().removeIf(e->e.getValue().getBlock() instanceof net.minecraft.world.level.block.LanternBlock||e.getValue().is(Blocks.IRON_CHAIN));
            var dark=new BiomeArchitecturePreview.Plan(fixed.sample(),java.util.Map.copyOf(cells),fixed.access(),fixed.entrance(),fixed.height());
            try { PreviewWindowLighting.validate(dark);throw new AssertionError("Dark indoor hallway admitted"); }
            catch(IllegalStateException expected) { if(!expected.getMessage().contains("Dark review room")) throw expected; }
            PreviewWindowLighting.brightenRooms(dark,cells);
            var lit=new BiomeArchitecturePreview.Plan(fixed.sample(),java.util.Map.copyOf(cells),fixed.access(),fixed.entrance(),fixed.height());
            PreviewWindowLighting.validate(lit);PreviewSupportAudit.validate(cells,"comfortable corridor lighting");
            if(!PreviewWindowLighting.brightenRooms(lit,cells).isEmpty()) throw new AssertionError("Non-idempotent indoor lighting");
            var loft=ceilingFixture(style,8);var hole=new net.minecraft.core.BlockPos(6,4,10);
            var broken=new java.util.LinkedHashMap<>(loft.cells());broken.remove(hole);
            expectRoomFailure(loft,broken,"unguarded upper-floor drop");
            var chainSlot=new net.minecraft.core.BlockPos(8,4,10);
            if(!loft.cells().get(chainSlot).isFaceSturdy(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,chainSlot,net.minecraft.core.Direction.UP)
                    ||loft.cells().get(chainSlot.above())!=null&&loft.cells().get(chainSlot.above()).is(Blocks.IRON_CHAIN))
                throw new AssertionError("Hanging chain pierced the finished loft floor");
            var opening=new net.minecraft.core.BlockPos(4,4,10);
            var guarded=new java.util.LinkedHashMap<>(loft.cells());guarded.remove(opening);
            PreviewRoomLayout.guardUpperEdges(loft,guarded,new java.util.HashSet<>(loft.access()));
            if(!PreviewRoomLayout.upperDrops(guarded,loft.entrance()).isEmpty()
                    ||!PreviewRoomLayout.reachable(guarded,loft.entrance()).containsAll(loft.access()))
                throw new AssertionError("Intentional loft opening lacks a safe reachable railing: "+style);
            if(guarded.values().stream().noneMatch(s->s.getBlock() instanceof net.minecraft.world.level.block.FenceBlock
                    ||s.getBlock() instanceof net.minecraft.world.level.block.WallBlock))
                throw new AssertionError("Missing regional loft barrier");
            PreviewSupportAudit.validate(guarded,"guarded loft opening");
        }
        System.out.println("PASS interior circulation: five-biome corridor widening, supported night lighting, repeatability and upper-floor hole rejection");
    }
    private static void verifyOutdoorGrade() {
        var sample=BiomeArchitecturePreview.samples().getFirst();
        var p=BiomeArchitecturePreview.plan(sample);
        var identity=new PreviewOutdoorPrograms.PlanIdentity(sample.id(),p.cells());
        var cells=new java.util.HashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(int y=-1;y<=5;y++) cells.put(new net.minecraft.core.BlockPos(-5,y,y+2),Blocks.STONE.defaultBlockState());
        var route=new net.minecraft.core.BlockPos(-2,1,-2);
        var draft=new PreviewOutdoorPrograms.Site(identity,java.util.Map.copyOf(cells),java.util.Set.of(route),
                java.util.List.of(PreviewOutdoorPrograms.Feature.POND),-6,20,-4,25);
        var lowered=PreviewOutdoorPrograms.lowerToGrade(draft);
        if(lowered.cells().size()!=draft.cells().size()||!lowered.identity().equals(identity)
                ||!lowered.features().equals(draft.features())||lowered.minX()!=draft.minX()||lowered.maxX()!=draft.maxX()
                ||lowered.minZ()!=draft.minZ()||lowered.maxZ()!=draft.maxZ()
                ||!lowered.routes().equals(java.util.Set.of(route.below())))
            throw new AssertionError("Outdoor lowering changed identity, footprint, features or routes");
        draft.cells().forEach((at,state)-> {
            if(!state.equals(lowered.cells().get(at.below())))throw new AssertionError("Incomplete one-block outdoor offset");
        });
        for(var design:BiomeArchitectureCatalogPreview.samples()) {
            var plan=BiomeArchitecturePreview.plan(design);var site=PreviewOutdoorPrograms.site(plan);
            if(site.routes().stream().anyMatch(at->at.getY()!=0)
                    ||!site.cells().containsKey(new net.minecraft.core.BlockPos(-6,-1,-3))
                    ||site.cells().containsKey(new net.minecraft.core.BlockPos(-6,0,-3)))
                throw new AssertionError("Raised outdoor surface retained: "+design.id());
            var placed=new java.util.HashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
            plan.blocks(net.minecraft.core.BlockPos.ZERO).forEach(block->placed.put(block.position(),block.state()));
            var accessibleBuilding=new java.util.HashMap<>(plan.cells());
            NativeDoorwayClearance.setBackRugs(accessibleBuilding);
            accessibleBuilding.forEach((at,state)-> {
                if(!state.equals(placed.get(at)))throw new AssertionError("Building moved during yard lowering: "+design.id());
            });
        }
        var unsupported=new java.util.HashMap<>(p.cells());
        var aboveGrade=new net.minecraft.core.BlockPos(-6,0,-3);
        unsupported.put(aboveGrade,Blocks.STONE.defaultBlockState());
        try { PreviewSupportAudit.validate(p,unsupported);throw new AssertionError("Above-grade outdoor prop treated as ground"); }
        catch(IllegalStateException expected) { }
        System.out.println("PASS outdoor grade: exact one-block cells/routes transform, 375 flush sites, original building cells retained and unsupported y=0 props rejected");
    }
    private static void verifyTaigaOverhangs() {
        for(var style:VillageArchitecture.BiomeDialect.values()) for(var form:PreviewExteriorPrograms.Form.values()) {
            var b=new BiomeArchitecturePreview.Builder(new BiomeArchitecturePreview.Sample(style,"HOUSE","compact_roof_fixture",18,18));
            b.room(2,13,2,13,4);
            PreviewExteriorPrograms.shell(b,form,2,13,2,13,6,0,4);
            PreviewRoofEnvelope.seal(b);
            var before=java.util.Map.copyOf(b.cells);
            PreviewTaigaCraft.apply(b);
            if(style!=VillageArchitecture.BiomeDialect.TAIGA) {
                if(!b.roofEaves.isEmpty()||!before.equals(b.cells)) throw new AssertionError("Taiga eaves changed another biome");
                continue;
            }
            if(b.roofEaves.isEmpty())throw new AssertionError("Roof form missing eaves: "+form);
            b.roofEaves.forEach((at,log)-> {
                if(!before.containsKey(at)&&!log.equals(b.cells.get(at)))throw new AssertionError("Missing projected log course: "+form+" "+at);
            });
            PreviewRoofEnvelope.validate(b.cells,b.roofJoins.keySet());
            PreviewSupportAudit.validate(b.cells,"taiga-eaves-"+form);
        }
        var b=new BiomeArchitecturePreview.Builder(new BiomeArchitecturePreview.Sample(VillageArchitecture.BiomeDialect.TAIGA,"HOUSE","compact_roof_fixture",14,14));
        b.room(2,11,2,11,4);b.gable(2,11,2,11,5,false);
        var protectedCell=new net.minecraft.core.BlockPos(5,8,1);
        b.cells.put(protectedCell,Blocks.COBBLESTONE.defaultBlockState());
        PreviewTaigaCraft.apply(b);
        if(!b.cells.get(new net.minecraft.core.BlockPos(2,5,1)).is(Blocks.SPRUCE_LOG)
                ||!b.cells.get(new net.minecraft.core.BlockPos(1,5,5)).is(Blocks.SPRUCE_LOG)
                ||!b.cells.get(new net.minecraft.core.BlockPos(5,8,12)).is(Blocks.SPRUCE_LOG)
                ||!b.cells.get(protectedCell).is(Blocks.COBBLESTONE))throw new AssertionError("One-block gable/eave projection or occupied-cell preservation failed");
        System.out.println("PASS Taiga eaves: all ten roof forms carried; front, rear and side log projections; occupied geometry and other biomes retained");
    }
    private static void verifyCompactReviewLayout() {
        var samples=BiomeArchitectureCatalogPreview.samples();
        var origins=new java.util.ArrayList<net.minecraft.core.BlockPos>();
        var entries=new java.util.ArrayList<VillageComparisonGallery.ComparisonEntry>();
        int maxX=0,maxZ=0;
        for(int i=0;i<samples.size();i++) {
            var plan=BiomeArchitecturePreview.plan(samples.get(i));
            VillageComparisonGallery.validatePreviewParcel(plan,1,1);
            var origin=VillageComparisonGallery.previewPlotOrigin(i,75,true);
            for(var old:origins) if(Math.abs(origin.getX()-old.getX())<112&&Math.abs(origin.getZ()-old.getZ())<72)
                throw new AssertionError("Compact parcels overlap: "+i);
            origins.add(origin);maxX=Math.max(maxX,origin.getX()+112);maxZ=Math.max(maxZ,origin.getZ()+72);
            entries.add(new VillageComparisonGallery.ComparisonEntry(i+1,samples.get(i).style().id(),"HOUSE","fixture",10,
                    "vanilla","fixture",0,origin.getX(),origin.getZ(),origin.getX()+8,origin.getZ()+12,20,20,12,
                    origin.getX()+68,origin.getZ()+12,9,9,6));
        }
        if(origins.get(1).getX()-origins.getFirst().getX()!=112
                ||origins.get(8).getZ()-origins.getFirst().getZ()!=72
                ||origins.get(225).getX()!=0||origins.get(225).getZ()!=736
                ||maxX>2736||maxZ>1472)throw new AssertionError("Compact district grid regressed");
        if(VillageComparisonGallery.previousPreviewRow(entries,entries.getFirst())!=null
                ||VillageComparisonGallery.previousPreviewRow(entries,entries.get(8))!=entries.getFirst()
                ||VillageComparisonGallery.previousPreviewRow(entries,entries.get(225))!=entries.get(72)
                ||VillageComparisonGallery.previousPreviewRow(java.util.List.of(entries.get(225)),entries.get(225))!=null)
            throw new AssertionError("Camera predecessor lookup failed at district boundary or missing row");
        try { VillageComparisonGallery.validatePreviewParcel(BiomeArchitecturePreview.plan(samples.getFirst()),200,200);
            throw new AssertionError("Oversized reference admitted into compact parcel");
        } catch(IllegalStateException expected) { }
        System.out.println("PASS compact review: 375 disjoint parcels, complete yards reserved; 112-block columns / 72-block rows; three-by-two district grid");
    }
    private static void verifySupportRejections() {
        var cells=new java.util.HashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        var zero=net.minecraft.core.BlockPos.ZERO;
        cells.put(zero,Blocks.SANDSTONE.defaultBlockState());
        cells.put(zero.above(2),Blocks.CUT_SANDSTONE.defaultBlockState());
        expectSupportFailure(cells,"air gap below sandstone trim");
        cells.put(zero.above(),Blocks.SANDSTONE_SLAB.defaultBlockState());
        expectSupportFailure(cells,"half-slab air gap");
        cells.put(zero.above(),Blocks.CUT_SANDSTONE.defaultBlockState());
        PreviewSupportAudit.validate(cells,"supported parapet");
        cells.clear();cells.put(zero,Blocks.SANDSTONE.defaultBlockState());
        cells.put(zero.offset(2,3,0),Blocks.SANDSTONE.defaultBlockState());
        cells.put(zero.offset(2,2,0),Blocks.LANTERN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LanternBlock.HANGING,true));
        expectSupportFailure(cells,"lamp attached to an ungrounded bracket");
        cells.clear();cells.put(zero,Blocks.SANDSTONE.defaultBlockState());
        cells.put(zero.offset(1,1,1),Blocks.SANDSTONE.defaultBlockState());
        expectSupportFailure(cells,"sole diagonal corner contact");
        cells.clear();cells.put(zero,Blocks.SANDSTONE.defaultBlockState());
        cells.put(zero.above(2),Blocks.OAK_SLAB.defaultBlockState());
        cells.put(zero.above(2).east(),Blocks.OAK_SLAB.defaultBlockState());
        expectSupportFailure(cells,"internally connected floating canopy");
        cells.put(zero.above(),Blocks.OAK_LOG.defaultBlockState());
        PreviewSupportAudit.validate(cells,"carried canopy");
        cells.clear();cells.put(zero,Blocks.OAK_LOG.defaultBlockState());
        cells.put(zero.above(),Blocks.OAK_LOG.defaultBlockState());
        cells.put(zero.above().east(),Blocks.OAK_FENCE.defaultBlockState());
        PreviewSupportAudit.validate(cells,"neighbor-connected fence arm");
        cells.clear();cells.put(zero,Blocks.OAK_LOG.defaultBlockState());
        cells.put(zero.offset(1,1,0),Blocks.OAK_STAIRS.defaultBlockState());
        PreviewSupportAudit.validate(cells,"continuous stepped roof edge");
        cells.clear();cells.put(zero,Blocks.OAK_LOG.defaultBlockState());
        cells.put(zero.above(),Blocks.OAK_LOG.defaultBlockState());
        cells.put(zero.above(2),Blocks.OAK_LOG.defaultBlockState());
        cells.put(zero.above(3),Blocks.OAK_LOG.defaultBlockState());
        cells.put(zero.above(3).east(),Blocks.OAK_LOG.defaultBlockState());
        cells.put(zero.above(2).east(),Blocks.IRON_CHAIN.defaultBlockState());
        cells.put(zero.above().east(),Blocks.LANTERN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LanternBlock.HANGING,true));
        PreviewSupportAudit.validate(cells,"supported pendant hook and chain");
        cells.clear();cells.put(zero,Blocks.FARMLAND.defaultBlockState());
        cells.put(zero.above(),Blocks.WHEAT.defaultBlockState());
        PreviewSupportAudit.validate(cells,"native farmland offset with rooted crop");
        System.out.println("PASS support negatives: isolated trim, half-slab gap, ungrounded lamp bracket, point-only contact and floating canopy");
    }
    private static void expectSupportFailure(java.util.Map<net.minecraft.core.BlockPos,
            net.minecraft.world.level.block.state.BlockState> cells,String reason) {
        try { PreviewSupportAudit.validate(cells,reason); }
        catch(IllegalStateException expected) { return; }
        throw new AssertionError("Floating geometry admitted: "+reason);
    }
    private static void verifyDesertDoors(BiomeArchitecturePreview.Plan plan) {
        if(plan.sample().style()!=VillageArchitecture.BiomeDialect.DESERT) return;
        if(plan.cells().values().stream()
                .anyMatch(state->state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                        &&!state.is(Blocks.JUNGLE_DOOR)))
            throw new AssertionError("Non-jungle desert door: "+plan.sample().id());
    }
    private static void verifyFacadeRejections() {
        var sample=PreviewCompactBuildings.samples(VillageArchitecture.BiomeDialect.SAVANNA).getFirst();
        var plan=BiomeArchitecturePreview.plan(sample);
        var audit=PreviewFacadePrograms.audit(plan);
        if(audit.skin().isEmpty()) throw new AssertionError("Compact facade had no actual detail work");
        var bed=plan.cells().entrySet().stream().filter(e->e.getValue().getBlock() instanceof BedBlock)
                .map(java.util.Map.Entry::getKey).findFirst().orElseThrow();
        var changed=new java.util.HashMap<>(plan.cells());changed.put(bed,Blocks.STONE.defaultBlockState());
        expectFacadeFailure(plan,changed,"interior furnishing replaced");
        var wall=audit.skin().iterator().next();
        changed=new java.util.HashMap<>(plan.cells());changed.put(wall,Blocks.AIR.defaultBlockState());
        expectFacadeFailure(plan,changed,"unsealed facade opening");
        var lamp=audit.added().stream().filter(at->plan.cells().get(at).is(Blocks.LANTERN)).findFirst().orElseThrow();
        changed=new java.util.HashMap<>(plan.cells());changed.remove(lamp.above());
        expectFacadeFailure(plan,changed,"missing exterior lantern bearing");
        var gap=PreviewRoomLayout.reachable(plan.cells(),plan.entrance()).stream()
                .filter(at->!plan.cells().containsKey(at)).findFirst().orElseThrow();
        changed=new java.util.HashMap<>(plan.cells());changed.put(gap,Blocks.STONE.defaultBlockState());
        expectFacadeFailure(plan,changed,"unscoped interior air filled");
        System.out.println("PASS facade negatives: interior edits, unsealed walls, unscoped additions and missing lantern bearings rejected");
    }
    private static void verifyOutdoorRejections() {
        var p=BiomeArchitecturePreview.plan(PreviewCompactBuildings.samples(VillageArchitecture.BiomeDialect.PLAINS).getFirst());
        var s=PreviewOutdoorPrograms.site(p);
        if(!s.equals(PreviewOutdoorPrograms.site(p)))throw new AssertionError("Nondeterministic yard");
        var changed=new java.util.HashMap<>(s.cells());
        changed.put(s.routes().iterator().next(),Blocks.STONE.defaultBlockState());
        expectOutdoorFailure(p,s,changed,"blocked route");
        changed=new java.util.HashMap<>(s.cells());changed.put(p.entrance(),Blocks.STONE.defaultBlockState());
        expectOutdoorFailure(p,s,changed,"door overlap");
        var lamp=s.cells().entrySet().stream().filter(e->e.getValue().is(Blocks.LANTERN)).map(java.util.Map.Entry::getKey).findFirst().orElseThrow();
        changed=new java.util.HashMap<>(s.cells());changed.remove(lamp.below());
        expectOutdoorFailure(p,s,changed,"floating lantern");
        changed=new java.util.HashMap<>(s.cells());changed.put(new net.minecraft.core.BlockPos(-6,1,-3),Blocks.WATER.defaultBlockState());
        expectOutdoorFailure(p,s,changed,"uncontained water");
        changed=new java.util.HashMap<>(s.cells());changed.put(lamp.below(),Blocks.DIRT_PATH.defaultBlockState());
        expectOutdoorFailure(p,s,changed,"unstable dirt-path footing under a prop");
        System.out.println("PASS outdoor negatives: doorway overlap, route blocking, floating lanterns, leaking water and unstable path footings rejected");
    }
    private static void expectOutdoorFailure(BiomeArchitecturePreview.Plan p,PreviewOutdoorPrograms.Site s,
            java.util.Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState> cells,String reason) {
        try { PreviewOutdoorPrograms.validate(p,new PreviewOutdoorPrograms.Site(s.identity(),java.util.Map.copyOf(cells),s.routes(),
                s.features(),s.minX(),s.maxX(),s.minZ(),s.maxZ())); }
        catch(IllegalStateException expected){return;}
        throw new AssertionError("Invalid outdoor plan admitted: "+reason);
    }
    private static void expectFacadeFailure(BiomeArchitecturePreview.Plan p,
            java.util.Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState> cells,String reason) {
        try { PreviewFacadePrograms.validate(new BiomeArchitecturePreview.Plan(p.sample(),java.util.Map.copyOf(cells),
                p.access(),p.entrance(),p.height())); }
        catch(IllegalStateException expected) { return; }
        throw new AssertionError("Invalid facade admitted: "+reason);
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
    private static void verifyWindowLighting() {
        for(var style:VillageArchitecture.BiomeDialect.values()) for(var normal:net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var sample=new BiomeArchitecturePreview.Sample(style,"HOUSE","view_test",11,11);
            var at=new net.minecraft.core.BlockPos(5,2,5);
            var cells=new java.util.LinkedHashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
            var wall=new BiomeArchitecturePreview.Builder(sample).p.wall().defaultBlockState();
            for(int side=-1;side<=1;side++) for(int y=1;y<=4;y++)
                cells.put(new net.minecraft.core.BlockPos(at.getX(),y,at.getZ()).relative(normal.getClockWise(),side),wall);
            cells.put(at,Blocks.GLASS_PANE.defaultBlockState());cells.put(at.above(),Blocks.STAINED_GLASS_PANE.green().defaultBlockState());
            var p=new BiomeArchitecturePreview.Plan(sample,java.util.Map.copyOf(cells),java.util.Set.of(),new net.minecraft.core.BlockPos(1,1,1),6);
            PreviewWindowLighting.validate(p);
            // A two-block-deep room remains valid even when a wall is close beyond it.
            cells.put(at.relative(normal,3),wall);cells.put(at.above().relative(normal,3),wall);
            if(!PreviewWindowLighting.view(cells,at)) throw new AssertionError("Honest shallow room glazing rejected");
            // A long gap parallel to the window is not a view through its normal.
            cells.put(at.relative(normal,2),wall);cells.put(at.above().relative(normal,2),wall);
            if(PreviewWindowLighting.view(cells,at)) throw new AssertionError("Window looking across a one-block wall gap retained");
            // Isolate that one-cell pocket on each side and cap it: it is merely a wall recess.
            for(int y=2;y<=3;y++) for(var side:java.util.List.of(normal.getClockWise(),normal.getCounterClockWise()))
                cells.put(new net.minecraft.core.BlockPos(at.getX(),y,at.getZ()).relative(normal).relative(side),wall);
            if(PreviewWindowLighting.view(cells,at)) throw new AssertionError("Blind recessed window retained");
            try {
                PreviewWindowLighting.validate(new BiomeArchitecturePreview.Plan(sample,java.util.Map.copyOf(cells),p.access(),p.entrance(),6));
                throw new AssertionError("Final admission allowed a blind window");
            } catch(IllegalStateException expected) { }
            var before=java.util.Map.copyOf(cells);
            var changed=PreviewWindowLighting.sealBlindWindows(p,cells);
            if(!changed.equals(java.util.Set.of(at,at.above()))||cells.size()!=before.size()) throw new AssertionError("Window repair scope changed");
            before.forEach((pos,state)-> {
                if(!changed.contains(pos)&&!state.equals(cells.get(pos))) throw new AssertionError("Window repair cut a protected wall or furnishing");
            });
            if(!PreviewWindowLighting.sealBlindWindows(p,cells).isEmpty()) throw new AssertionError("Non-idempotent window repair");
            cells.clear();
            for(int x=3;x<=7;x++) for(int z=3;z<=7;z++) cells.put(new net.minecraft.core.BlockPos(x,4,z),Blocks.GLASS.defaultBlockState());
            for(var pos:cells.keySet()) if(!PreviewWindowLighting.view(cells,pos)) throw new AssertionError("Honest roof skylight rejected");
        }
        var sample=new BiomeArchitecturePreview.Sample(VillageArchitecture.BiomeDialect.PLAINS,"HOUSE","light_test",9,9);
        var cells=new java.util.LinkedHashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(int x=0;x<9;x++) for(int z=0;z<9;z++) {
            cells.put(new net.minecraft.core.BlockPos(x,0,z),Blocks.OAK_PLANKS.defaultBlockState());
            cells.put(new net.minecraft.core.BlockPos(x,5,z),Blocks.OAK_PLANKS.defaultBlockState());
            if(x==0||z==0||x==8||z==8) for(int y=1;y<5;y++)
                cells.put(new net.minecraft.core.BlockPos(x,y,z),Blocks.OAK_PLANKS.defaultBlockState());
        }
        for(var lamp:java.util.List.of(new net.minecraft.core.BlockPos(2,3,2),new net.minecraft.core.BlockPos(3,3,2),
                new net.minecraft.core.BlockPos(6,3,6),new net.minecraft.core.BlockPos(2,3,6))) {
            cells.put(lamp,Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING,true));
            cells.put(lamp.above(),Blocks.IRON_CHAIN.defaultBlockState());
        }
        var p=new BiomeArchitecturePreview.Plan(sample,java.util.Map.copyOf(cells),java.util.Set.of(),new net.minecraft.core.BlockPos(4,1,1),6);
        try { PreviewWindowLighting.validate(p);throw new AssertionError("Final admission allowed a lighting cluster"); }
        catch(IllegalStateException expected) { }
        var before=java.util.Map.copyOf(cells);var lighting=BiomeArchitecturePreview.lighting(p);
        var changes=PreviewWindowLighting.thinLanterns(p,cells);
        if(changes.removed().isEmpty()) throw new AssertionError("Redundant adjacent fixtures retained");
        for(var entry:before.entrySet())
            if(!(entry.getValue().getBlock() instanceof net.minecraft.world.level.block.LanternBlock)&&!entry.getValue().is(Blocks.IRON_CHAIN)
                    &&!entry.getValue().equals(cells.get(entry.getKey()))) throw new AssertionError("Light pruning changed structure");
        var fixed=new BiomeArchitecturePreview.Plan(sample,java.util.Map.copyOf(cells),p.access(),p.entrance(),6);
        if(PreviewWindowLighting.crowdedPairs(fixed)!=0) throw new AssertionError("Lighting cluster retained");
        var light=BiomeArchitecturePreview.lighting(fixed);light.requireSpawnSafe();
        for(var feet:BiomeArchitecturePreview.reachable(p)) {
            var voxel=new com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel(feet.getX(),feet.getY(),feet.getZ());
            if(light.blockLightAt(voxel)<Math.min(7,lighting.blockLightAt(voxel))) throw new AssertionError("Lighting coverage lost");
        }
        for(var at:changes.removed()) if(before.get(at).is(Blocks.LANTERN)&&cells.containsKey(at.above()))
            throw new AssertionError("Unused pendant chain remained");
        PreviewSupportAudit.validate(cells,"lighting fixture");
        if(!PreviewWindowLighting.thinLanterns(fixed,cells).removed().isEmpty()) throw new AssertionError("Non-idempotent light pruning");
        cells.put(new net.minecraft.core.BlockPos(4,3,3),Blocks.OAK_PLANKS.defaultBlockState());
        if(PreviewWindowLighting.crowded(cells,new net.minecraft.core.BlockPos(3,3,3),new net.minecraft.core.BlockPos(5,3,3)))
            throw new AssertionError("Separate rooms treated as one lighting cluster");
        System.out.println("PASS window/light negatives: backed niches and one-block wall gaps sealed without carving rooms; honest shallow rooms and skylights retained; redundant lamps and unused chains removed; midnight coverage, separation, supports and repeatability preserved");
    }
    private static void verifyDoorwayGlazing() {
        for(var style:VillageArchitecture.BiomeDialect.values())
            for(var facing:net.minecraft.core.Direction.Plane.HORIZONTAL)
                for(var hinge:net.minecraft.world.level.block.state.properties.DoorHingeSide.values())
                    for(int floor:new int[]{0,4}) {
                        var sample=new BiomeArchitecturePreview.Sample(style,"HOUSE","glazing_test",9,9);
                        var cells=new java.util.LinkedHashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
                        for(int x=0;x<9;x++) for(int z=0;z<9;z++)
                            cells.put(new net.minecraft.core.BlockPos(x,floor,z),Blocks.COBBLESTONE.defaultBlockState());
                        var at=new net.minecraft.core.BlockPos(4,floor+1,4);
                        var lower=Blocks.SPRUCE_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING,facing)
                                .setValue(net.minecraft.world.level.block.DoorBlock.HINGE,hinge);
                        cells.put(at,lower);
                        cells.put(at.above(),lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
                        var changed=new java.util.HashSet<net.minecraft.core.BlockPos>();
                        for(var side:java.util.List.of(facing.getClockWise(),facing.getCounterClockWise())) {
                            var jamb=at.relative(side);
                            for(int y=0;y<=2;y++) {
                                cells.put(jamb.above(y),(y==1?Blocks.STAINED_GLASS_PANE.green():Blocks.GLASS_PANE).defaultBlockState());
                                changed.add(jamb.above(y));
                            }
                            cells.put(jamb.relative(side),Blocks.GLASS_PANE.defaultBlockState());
                        }
                        // A real lintel, fixtures and full glass must not become part of the repair.
                        cells.put(at.above(2),Blocks.COBBLESTONE.defaultBlockState());
                        cells.put(at.relative(facing,3),Blocks.GLASS.defaultBlockState());
                        var before=java.util.Map.copyOf(cells);
                        var unframed=new BiomeArchitecturePreview.Plan(sample,before,java.util.Set.of(),at,floor+5);
                        try { PreviewDoorwayGlazing.validate(unframed);throw new AssertionError("Unframed pane/door accepted"); }
                        catch(IllegalStateException expected) {
                            if(!expected.getMessage().contains("Unframed doorway glazing")) throw expected;
                        }
                        if(!PreviewDoorwayGlazing.frame(sample,cells).equals(changed)||cells.size()!=before.size())
                            throw new AssertionError("Jamb repair changed scope or footprint");
                        for(var entry:before.entrySet()) {
                            var expected=changed.contains(entry.getKey())?PreviewDoorwayGlazing.jamb(sample).defaultBlockState():entry.getValue();
                            if(!expected.equals(cells.get(entry.getKey()))) throw new AssertionError("Unscoped glazing repair");
                        }
                        var plan=new BiomeArchitecturePreview.Plan(sample,java.util.Map.copyOf(cells),java.util.Set.of(),at,floor+5);
                        PreviewDoorwayGlazing.validate(plan);
                        PreviewDoorwayAudit.validate(plan);
                        if(!PreviewDoorwayGlazing.frame(sample,cells).isEmpty()) throw new AssertionError("Non-idempotent jamb repair");
                        // Double doors keep both halves, hinges and clear approaches. Iron bars stay bars.
                        var partner=at.relative(facing.getClockWise());
                        cells.put(partner,lower);cells.put(partner.above(),cells.get(at.above()));
                        cells.put(partner.above(2),Blocks.IRON_BARS.defaultBlockState());
                        var doubleBefore=java.util.Map.copyOf(cells);
                        var doubleChanges=PreviewDoorwayGlazing.frame(sample,cells);
                        if(!doubleChanges.equals(java.util.Set.of(partner.relative(facing.getClockWise()))))
                            throw new AssertionError("Double-door outer glazing scope changed");
                        for(var entry:doubleBefore.entrySet())
                            if(!doubleChanges.contains(entry.getKey())&&!entry.getValue().equals(cells.get(entry.getKey())))
                                throw new AssertionError("Glazing repair changed a double door or bars");
                        var doublePlan=new BiomeArchitecturePreview.Plan(sample,java.util.Map.copyOf(cells),java.util.Set.of(),at,floor+5);
                        PreviewDoorwayGlazing.validate(doublePlan);PreviewDoorwayAudit.validate(doublePlan);
                    }
        System.out.println("PASS doorway glazing: five palettes, four directions, both hinges and upper floors; scoped frames, clear approaches, idempotence, double doors and bars retained");
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
                verifyDesertDoors(plan);
                PreviewFacadePrograms.validate(plan);
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
