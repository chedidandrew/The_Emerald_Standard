package com.chedidandrew.emeraldstandard.minecraft;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

/** Explicit community designation, independent of construction protection and financial ownership. */
final class NewsPropertyCommands {
    static LiteralArgumentBuilder<CommandSourceStack> command() {
        var property=Commands.literal("property");
        for(String action:new String[]{"add","remove"}) property.then(Commands.literal(action)
                .then(Commands.argument("from",BlockPosArgument.blockPos())
                .then(Commands.argument("to",BlockPosArgument.blockPos()).executes(ctx->{
                    var source=ctx.getSource();
                    var from=BlockPosArgument.getBlockPos(ctx,"from");var to=BlockPosArgument.getBlockPos(ctx,"to");
                    long dx=Math.abs((long)from.getX()-to.getX())+1,dy=Math.abs((long)from.getY()-to.getY())+1,dz=Math.abs((long)from.getZ()-to.getZ())+1;
                    if(dx>4096||dy>4096||dz>4096||dx*dy*dz>4096
                            ||Math.abs((long)from.getX())>29999984||Math.abs((long)to.getX())>29999984
                            ||Math.abs((long)from.getZ())>29999984||Math.abs((long)to.getZ())>29999984
                            ||from.getY()<source.getLevel().getMinY()||to.getY()<source.getLevel().getMinY()
                            ||from.getY()>source.getLevel().getMaxY()||to.getY()>source.getLevel().getMaxY()) {
                        source.sendFailure(Component.literal("Choose at most 4096 block positions."));return 0;
                    }
                    var data=NewsEvidence.get(source.getLevel());
                    java.util.Set<Long> positions=new java.util.HashSet<>();
                    for(var pos:BlockPos.betweenClosed(from,to))positions.add(pos.asLong());
                    if(action.equals("add")) {
                        java.util.Set<Long> union=new java.util.HashSet<>(data.community);union.addAll(positions);
                        if(union.size()>16384){source.sendFailure(Component.literal("Community reporting limit: 16384 positions per dimension."));return 0;}
                        data.community.addAll(positions);
                    } else data.community.removeAll(positions);
                    data.setDirty();
                    source.sendSuccess(()->Component.literal("News community property "+action+": "+positions.size()
                            +" positions. No blocks changed; this grants reporting designation, not building permission."),true);
                    return positions.size();
                }))));
        return Commands.literal("news").requires(s->s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)).then(property);
    }
}
