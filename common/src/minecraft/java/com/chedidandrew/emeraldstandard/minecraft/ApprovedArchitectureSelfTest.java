package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** Release admission for the assets used by real construction, including every Bank. */
public final class ApprovedArchitectureSelfTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var desk=AuthoredVillageStructuresSelfTest.class.getDeclaredMethod("registerExactDeskFixture");
        desk.setAccessible(true);desk.invoke(null);
        validateProductionCatalog();
        if(args.length>0&&args[0].equals("review")) compareApprovedReview();
        System.out.println("PASS approved production architecture: 375 frozen assets, native support, doors, ladders, beds and stage safety");
    }
    static void validateProductionCatalog() {
        int ordinary=0,banks=0;
        for(var entry:ApprovedArchitectureCatalog.entries()) {
            var data=ApprovedVillageStructures.data(entry.id());
            Map<BlockPos,BlockState> cells=new HashMap<>();
            for(var stage:List.of(data.base(),data.first(),data.second())) {
                for(var cell:stage) cells.put(new BlockPos(cell.x(),cell.y(),cell.z()),cell.state());
                NativeStructureSupport.validate(cells,entry.id());
                for(var cell:cells.entrySet()) {
                    BlockPos at=cell.getKey();BlockState state=cell.getValue();
                    if(state.getBlock() instanceof LadderBlock) {
                        BlockPos behind=at.relative(state.getValue(LadderBlock.FACING).getOpposite());
                        var support=cells.get(behind);
                        require(support!=null&&support.isFaceSturdy(EmptyBlockGetter.INSTANCE,behind,
                                state.getValue(LadderBlock.FACING)),"Unsupported ladder "+entry.id()+" "+at);
                    }
                    if(state.getBlock() instanceof DoorBlock) {
                        boolean lower=state.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER;
                        BlockState other=cells.get(lower?at.above():at.below());
                        require(other!=null&&other.getBlock()==state.getBlock()
                                &&other.getValue(DoorBlock.HALF)!=state.getValue(DoorBlock.HALF),"Incomplete door "+entry.id());
                    }
                }
            }
            long beds=cells.values().stream().filter(s->s.getBlock() instanceof BedBlock&&s.getValue(BedBlock.PART)==BedPart.HEAD).count();
            require(beds==entry.beds(),"Bed manifest mismatch "+entry.id());
            require(data.air().stream().noneMatch(cells::containsKey),"Occupied circulation "+entry.id());
            require(NativeDoorwayClearance.setBackRugs(new HashMap<>(cells))==0,"Doorstep carpet blocks villagers "+entry.id());
            var exit=data.walkwayExit();
            require(!cells.containsKey(exit)&&!cells.containsKey(exit.above()),"Blocked yard connection "+entry.id());
            if(entry.role().equals("BANK")) {
                banks++;
                int version=entry.id().endsWith("bank_kiosk")?14:entry.id().endsWith("savings_branch")?15:13;
                var bank=ApprovedBankStructures.plan(VillageArchitecture.BiomeDialect.fromId(entry.dialect()),version);
                require(BankerProfessionSupport.isExchangeDesk(bank.cells().get(new BlockPos(6,1,8))),"Bank anchor moved");
                require(bank.cells().get(bank.entrance()).getBlock() instanceof DoorBlock,"Bank primary door misidentified");
                long desks=cells.values().stream().filter(BankerProfessionSupport::isExchangeDesk).count();
                require(desks==1,"Bank must have exactly one Exchange Desk "+entry.id()+": "+desks);
                var placements=bank.cells().entrySet().stream().map(e->new SupportedConstructionOrder.Cell(e.getKey(),e.getValue(),7)).toList();
                require(SupportedConstructionOrder.sequence(placements,List.of(0,placements.size())).disconnected().isEmpty(),
                        "Unsupported Bank sequence "+entry.id());
            } else {
                ordinary++;
                var descriptor=VillageArchitecture.requireBlueprint(entry.id(),12);
                require(descriptor.width()==entry.width()&&descriptor.depth()==entry.depth(),"Unreserved yard "+entry.id());
                var plan=AuthoredVillageStructures.plan(descriptor.type(),entry.id(),12,VillageArchitecture.PALETTE_BALANCED,
                        VillageArchitecture.DRESSING_PROSPEROUS,VillageArchitecture.Character.MERCANTILE,
                        VillageArchitecture.BiomeDialect.fromId(entry.dialect()));
                require(plan.base().equals(data.base()),"Production adapter changed geometry");
                var placements=new ArrayList<SupportedConstructionOrder.Cell>();
                var boundaries=new ArrayList<Integer>();boundaries.add(0);
                for(var stage:List.of(plan.base(),plan.stageOne(),plan.stageTwo())) {
                    for(var cell:stage) placements.add(new SupportedConstructionOrder.Cell(new BlockPos(cell.x(),cell.y(),cell.z()),cell.state(),cell.phase().ordinal()));
                    if(boundaries.getLast()!=placements.size()) boundaries.add(placements.size());
                }
                var order=SupportedConstructionOrder.sequence(placements,boundaries);
                require(order.disconnected().isEmpty(),"Unsupported production build sequence "+entry.id()+": "+order.disconnected().stream().map(placements::get).toList());
                var project=new EconomyState.VillageProject();
                project.type=descriptor.type();project.designTemplateId=entry.id();project.designTemplateRevision=12;
                require(project.actualHousingBeds()==beds,"Compact residence capacity does not match actual beds");
            }
        }
        require(ordinary==360&&banks==15,"Release catalog coverage "+ordinary+" / "+banks);
    }
    private static void compareApprovedReview() {
        System.setProperty(StructureGallery.ENABLE_PROPERTY,"true");
        for(var sample:BiomeArchitectureCatalogPreview.samples()) {
            String suffix=sample.id().startsWith("compact_")?sample.id().substring(("compact_"+sample.style().id()+"_").length())
                    :BiomeArchitectureCatalogPreview.masterId(sample);
            String id="biome_"+sample.style().id()+"_"+suffix;
            var p=BiomeArchitecturePreview.plan(sample); var blocks=p.blocks(BlockPos.ZERO);
            int minX=blocks.stream().mapToInt(b->b.position().getX()).min().orElseThrow();
            int minZ=blocks.stream().mapToInt(b->b.position().getZ()).min().orElseThrow();
            Map<BlockPos,BlockState> expected=new HashMap<>();
            for(var b:blocks) expected.put(b.position().offset(-minX,0,-minZ),b.state());
            require(expected.equals(ApprovedVillageStructures.data(id).cells()),"Approved review differs from release "+id);
        }
        System.out.println("PASS exact approved-review/release equality across all 375 sites");
    }
    private static void require(boolean value,String message) { if(!value) throw new IllegalStateException(message); }
}
