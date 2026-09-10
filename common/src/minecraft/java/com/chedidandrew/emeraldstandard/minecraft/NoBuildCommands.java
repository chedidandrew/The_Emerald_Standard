package com.chedidandrew.emeraldstandard.minecraft;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import java.util.UUID;

/** Two inclusive X/Z corners define a full-height, dimension-local rectangle. */
final class NoBuildCommands {
    private NoBuildCommands() { }
    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("nobuild")
                .then(Commands.literal("add").then(Commands.argument("name",StringArgumentType.word())
                        .then(Commands.argument("from",ColumnPosArgument.columnPos())
                                .then(Commands.argument("to",ColumnPosArgument.columnPos()).executes(NoBuildCommands::add)))))
                .then(Commands.literal("list").executes(NoBuildCommands::list))
                .then(Commands.literal("remove").then(Commands.argument("name",StringArgumentType.word())
                        .executes(NoBuildCommands::remove)));
    }
    private static boolean admin(CommandSourceStack source) { return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER); }
    private static UUID actor(CommandSourceStack source) { return source.getEntity() == null ? new UUID(0,0) : source.getEntity().getUUID(); }
    private static int add(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        try {
            var land = DevelopmentLandProtection.land(source.getServer());
            var from = ColumnPosArgument.getColumnPos(context,"from"); var to = ColumnPosArgument.getColumnPos(context,"to");
            if (!admin(source) && (Math.abs((long)from.x()-to.x()) >= 256 || Math.abs((long)from.z()-to.z()) >= 256
                    || land.zones().stream().filter(z->z.owner().equals(actor(source))).count() >= 16))
                throw new IllegalArgumentException("Players may have 16 zones up to 256 by 256 blocks; operators can create larger zones");
            String name = StringArgumentType.getString(context,"name");
            land.add(name,actor(source),source.getLevel().dimension().identifier().toString(),from.x(),from.z(),to.x(),to.z());
            source.sendSuccess(()->Component.literal("No-build zone '"+name+"' saved: inclusive X/Z corners, full world height. Existing blocks are unchanged."),false);
            return 1;
        } catch (Exception error) { source.sendFailure(Component.literal(error.getMessage())); return 0; }
    }
    private static int list(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        try {
            var zones = DevelopmentLandProtection.land(source.getServer()).zones();
            source.sendSuccess(()->Component.literal(zones.size()+" no-build zone(s):"),false);
            for (var zone : zones) source.sendSuccess(()->Component.literal(zone.name()+" | "+zone.dimension()+" | "+zone.minX()+" "+zone.minZ()+" to "+zone.maxX()+" "+zone.maxZ()+" | full height"),false);
            return zones.size();
        } catch (Exception error) { source.sendFailure(Component.literal(error.getMessage())); return 0; }
    }
    private static int remove(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        try {
            String name = StringArgumentType.getString(context,"name");
            DevelopmentLandProtection.land(source.getServer()).remove(name,actor(source),admin(source));
            source.sendSuccess(()->Component.literal("Removed no-build zone '"+name+"'. Automatic player-build protection still applies."),false);
            return 1;
        } catch (Exception error) { source.sendFailure(Component.literal(error.getMessage())); return 0; }
    }
}
