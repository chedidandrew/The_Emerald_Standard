package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Saved-plan lookup only. Never loads chunks, repairs blocks or changes a plan. */
final class StructureIdentifyCommand {
    static LiteralArgumentBuilder<CommandSourceStack> command(String name,EconomyService economy) {
        return Commands.literal(name)
                .executes(ctx->identify(ctx.getSource(),economy,null))
                .then(Commands.argument("position",BlockPosArgument.blockPos())
                        .executes(ctx->identify(ctx.getSource(),economy,BlockPosArgument.getBlockPos(ctx,"position"))));
    }
    private static int identify(CommandSourceStack source,EconomyService economy,BlockPos explicit) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        BlockPos at=explicit;
        if(at==null) {
            var player=source.getPlayerOrException();
            var hit=loadedPick(player);
            at=hit.getType()==HitResult.Type.BLOCK?((BlockHitResult)hit).getBlockPos():player.blockPosition();
        }
        if(source.getLevel().getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4)==null) {
            source.sendFailure(Component.literal("Structure lookup: target chunk is not loaded. No chunks were loaded."));return 0;
        }
        var state=economy.snapshot();
        if(state==null) {source.sendFailure(Component.literal("No TES saved plans are available."));return 0;}
        var dimension=source.getLevel().dimension().identifier().toString();
        List<String> reports=new ArrayList<>();
        Map<Long,Long> banks=new HashMap<>(state.generatedBankAnchors);
        state.pendingBankConstructions.forEach((key,plan)->banks.put(key,plan.bankerAnchor()));
        for(var village:state.villages.values()) {
            if(!village.dimensionKey.equals(dimension)) continue;
            for(var p:village.projects) {
                if(p.totalBlocks<=0||!contains(at,p.boundsMinPos,p.boundsMaxPos)) continue;
                String template=p.vanillaPlan!=null?p.vanillaPlan.templateId():p.designTemplateId.isBlank()?p.designSchema:p.designTemplateId;
                reports.add("TES structure: "+template+" | "+p.type+" | village="+village.villageId
                        +" | project="+p.projectId+" | revision="+p.designTemplateRevision+" | outdoor="+p.designOutdoorLevel
                        +" | rotation="+(p.designRotation*90)+" | mirrored="+p.designMirrored+" | origin="+coords(BlockPos.of(p.originPos))
                        +" | state="+(p.materializedComplete?"complete":p.blocked?"blocked":"construction")
                        +" | progress="+p.materializedBlocks+"/"+p.totalBlocks+" | hash="+p.designPlanHash);
            }
            for(var entry:banks.entrySet()) {
                var pending=state.pendingBankConstructions.get(entry.getKey());
                if(!village.villageId.equals(pending==null?state.bankRegionVillageIds.get(entry.getKey()):pending.villageId())) continue;
                int version=state.bankStructureVersions.getOrDefault(entry.getKey(),0);
                if(pending!=null) version=pending.version();
                if(!ApprovedBankStructures.approvedVersion(version)) continue;
                String dialect=pending!=null&&!pending.style().isEmpty()?pending.style():village.architectureDialect;
                if(dialect.isBlank()) continue; // No guessed geometry for historical unknown-style records.
                var style=VillageArchitecture.BiomeDialect.fromId(dialect);
                var plan=ApprovedBankStructures.plan(style,version);
                var origin=pending!=null?BlockPos.of(pending.origin()):BlockPos.of(entry.getValue()).offset(-6,-1,-9);
                if(at.getX()<origin.getX()+plan.minX()||at.getX()>origin.getX()+plan.maxX()
                        ||at.getZ()<origin.getZ()+plan.minZ()||at.getZ()>origin.getZ()+plan.maxZ()
                        ||at.getY()<origin.getY()-1||at.getY()>origin.getY()+plan.height()) continue;
                reports.add("TES Bank: "+ApprovedBankStructures.templateId(style,version)+" | village="+village.villageId+" | style="+style.id()
                        +" | bank version="+version+" | region="+entry.getKey()+" | origin="+coords(origin)
                        +" | state="+(pending==null?"complete":"construction"));
            }
        }
        if(reports.isEmpty()) {source.sendFailure(Component.literal("No saved TES structure contains "+coords(at)+". Aim at a building within 32 blocks, stand inside it, or use /tesstructure x y z. Naturally generated vanilla buildings are not TES plans."));return 0;}
        if(reports.size()>1) source.sendSuccess(()->Component.literal("Overlapping saved plans: "+reports.size()+". All matches follow; none was selected or modified."),false);
        reports.stream().sorted().forEach(report->source.sendSuccess(()->Component.literal(report)
                .withStyle(style->style.withClickEvent(new ClickEvent.CopyToClipboard(report))),false));
        return reports.size();
    }
    static boolean contains(BlockPos at,long min,long max) {
        if(min==0&&max==0) return false;
        var a=BlockPos.of(min);var b=BlockPos.of(max);
        return at.getX()>=a.getX()&&at.getX()<=b.getX()&&at.getY()>=a.getY()&&at.getY()<=b.getY()&&at.getZ()>=a.getZ()&&at.getZ()<=b.getZ();
    }
    static BlockHitResult loadedPick(net.minecraft.server.level.ServerPlayer player) {
        var level=player.level();
        var view=new net.minecraft.world.level.BlockGetter() {
            public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos at) {
                var chunk=level.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4);
                return chunk==null?net.minecraft.world.level.block.Blocks.AIR.defaultBlockState():chunk.getBlockState(at);
            }
            public net.minecraft.world.level.material.FluidState getFluidState(BlockPos at) {return getBlockState(at).getFluidState();}
            public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos at) {return null;}
            public int getHeight() {return level.getHeight();}
            public int getMinY() {return level.getMinY();}
        };
        var eye=player.getEyePosition();
        return view.clip(new net.minecraft.world.level.ClipContext(eye,eye.add(player.getViewVector(1.0f).scale(32)),
                net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
    }
    private static String coords(BlockPos at) {return at.getX()+" "+at.getY()+" "+at.getZ();}
}
