package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;

/** Bank handover is separate from village projects; feed it into the shared finishing queue. */
final class BankWalkways {
    // Ordinary project IDs are positive. The origin disambiguates replacements/legacy Banks.
    static final long PROJECT = Long.MIN_VALUE;
    record Bank(long key, long anchor,int version,VillageArchitecture.BiomeDialect style) {
        Bank(long key,long anchor) {this(key,anchor,12,null);}
        BlockPos origin() { return BlockPos.of(anchor).offset(-6, -1, -9); }
        BlockPos start() {
            if(version>=13) {
                var p=ApprovedBankStructures.plan(style,version);
                return origin().offset(p.walkwayExit()).below();
            }
            return origin().offset(6, 0, -2);
        }
        String job(UUID village) { return WalkwayConnectionLedger.key(village, PROJECT, anchor); }
    }

    static List<Bank> candidates(ServerLevel level, EconomyService economy, EconomyState.VillageRecord village) {
        if (!level.dimension().identifier().toString().equals("minecraft:overworld")
                || !EmeraldConfig.current().villageBanksEnabled()) return List.of();
        var pending = economy.pendingBankConstructionsSnapshot();
        List<Bank> result = new ArrayList<>();
        economy.generatedBankAnchorsSnapshot().forEach((key, anchor) -> {
            if (pending.containsKey(key) || economy.isFallbackBankRegion(key)
                    || economy.generatedBankStructureVersion(key) < 3
                    || !village.villageId.equals(economy.canonicalVillageId(economy.villageIdForBankRegion(key)))) return;
            int version=economy.generatedBankStructureVersion(key);
            var saved=BankStyleLedger.get(level).style(BlockPos.of(anchor).offset(-6,-1,-9).asLong());
            if(version>=13&&saved==null) return;
            Bank bank = new Bank(key, anchor,version,saved);
            if (!VillageBankManager.bankWorkActive(level, economy, village.villageId, bank.start(),
                    EmeraldConfig.current().villageDevelopmentRadius())) return;
            // No palette lookup, heightmap query, or block access in an unloaded chunk.
            BlockPos start = bank.start();
            if (!level.hasChunk(start.getX() >> 4, start.getZ() >> 4)
                    || !level.hasChunk(bank.origin().getX() >> 4, bank.origin().getZ() >> 4)) return;
            var stair = level.getBlockState(start);
            if(version>=13) {
                if(!stair.getCollisionShape(level,start).isEmpty()&&level.getBlockState(start.above()).isAir()
                        &&level.getBlockState(start.above(2)).isAir()) result.add(bank);
            } else if (stair.getBlock() instanceof StairBlock && stair.getValue(StairBlock.FACING) == Direction.SOUTH
                    && stair.getValue(StairBlock.HALF) == Half.BOTTOM) result.add(bank);
        });
        result.sort(Comparator.comparingLong(Bank::key).thenComparingLong(Bank::anchor));
        return List.copyOf(result);
    }

    static WalkwayConnections.Request request(ServerLevel level, EconomyState.VillageRecord village, Bank bank,
            List<EconomyService.VillageProjectLot> lots, List<Long> banks) {
        // Only join an already connected building; two unfinished connectors must not form an
        // isolated loop and call it a village road. Otherwise seek a real road near the center.
        var ledger = WalkwayConnectionLedger.get(level);
        var branch = village.projects.stream().filter(p -> VillageProsperityManager.walkwayReady(village, p)
                && ledger.job(WalkwayConnectionLedger.key(village.villageId, p.projectId, p.originPos)).done())
                .min(Comparator.<EconomyState.VillageProject>comparingDouble(p ->
                        VillageProsperityManager.projectEntrance(BlockPos.of(p.originPos), p).distSqr(bank.start()))
                        .thenComparingLong(p -> p.projectId)).orElse(null);
        BlockPos destination = branch == null ? BlockPos.of(village.centerPos).below()
                : VillageProsperityManager.projectEntrance(BlockPos.of(branch.originPos), branch).below();
        BlockPos origin = bank.origin();
        List<EconomyService.VillageProjectLot> exclusions = new ArrayList<>(lots);
        if(bank.version()>=13) {
            var p=ApprovedBankStructures.plan(bank.style(),bank.version());
            int entry=p.walkwayExit().getX();
            exclusions.removeIf(l->BlockPos.of(l.boundsMinPos()).getX()==origin.getX()+p.minX()
                    &&BlockPos.of(l.boundsMaxPos()).getX()==origin.getX()+p.maxX()
                    &&BlockPos.of(l.boundsMaxPos()).getZ()==origin.getZ()+p.maxZ());
            exclusions.add(lot(origin,p.minX(),p.minZ()+2,p.maxX(),p.maxZ()));
            if(entry-3>=p.minX()) exclusions.add(lot(origin,p.minX(),p.minZ(),entry-3,p.minZ()+1));
            if(entry+3<=p.maxX()) exclusions.add(lot(origin,entry+3,p.minZ(),p.maxX(),p.minZ()+1));
            Set<Long> court=new HashSet<>();
            for(int x=p.minX();x<=p.maxX();x++) for(int z=p.minZ();z<=p.maxZ();z++)
                court.add(WalkwayConnections.column(origin.offset(x,0,z)));
            return new WalkwayConnections.Request(village.villageId,PROJECT,bank.anchor(),bank.start(),destination,
                    branch==null,court,List.copyOf(exclusions),banks.stream().filter(a->a!=bank.anchor()).toList(),
                    bank.style()==VillageArchitecture.BiomeDialect.DESERT,WalkwayStyle.forDialect(bank.style().id()));
        }
        // Replace only THIS Bank's coarse 20-block buffer with its actual lot. Leave a narrow
        // doorway corridor for new paving. The shared lot guard adds a one-block margin;
        // existing clear steps can be traversed but neither furniture nor stairs are replaced.
        exclusions.add(lot(origin, -1, -4, 4, 11));
        exclusions.add(lot(origin, 8, -4, 13, 11));
        exclusions.add(lot(origin, -1, -1, 13, 11));
        Set<Long> forecourt = new HashSet<>();
        for (int x = -2; x <= 14; x++) for (int z = -6; z <= 12; z++)
            forecourt.add(WalkwayConnections.column(origin.offset(x, 0, z)));
        return new WalkwayConnections.Request(village.villageId, PROJECT, bank.anchor(), bank.start(), destination,
                branch == null, forecourt, List.copyOf(exclusions),
                banks.stream().filter(anchor -> anchor != bank.anchor()).toList(),
                (village.architectureDialect.isBlank() ? VillageProsperityManager.biomeDialect(level,bank.origin())
                        : VillageArchitecture.BiomeDialect.fromId(village.architectureDialect))
                        == VillageArchitecture.BiomeDialect.DESERT,
                WalkwayStyle.forDialect(dialect(level,village,bank).id()));
    }

    private static VillageArchitecture.BiomeDialect dialect(ServerLevel level, EconomyState.VillageRecord village, Bank bank) {
        if(bank.version()>=13) return bank.style();
        // A Bank can finish before the first expansion project establishes a saved dialect.
        return village.architectureDialect.isBlank() ? VillageProsperityManager.biomeDialect(level, BlockPos.of(village.centerPos))
                : VillageArchitecture.BiomeDialect.fromId(village.architectureDialect);
    }

    static AuthoredVillageStructures.Materials materials(ServerLevel level, EconomyState.VillageRecord village, Bank bank) {
        var character = village.architectureCharacter.isBlank() ? VillageArchitecture.character(village.villageId)
                : VillageArchitecture.Character.fromId(village.architectureCharacter);
        return AuthoredVillageStructures.walkwayMaterials(character, dialect(level, village, bank));
    }

    private static EconomyService.VillageProjectLot lot(BlockPos o, int x1, int z1, int x2, int z2) {
        return new EconomyService.VillageProjectLot(o.offset(x1, 0, z1).asLong(), o.offset(x2, 11, z2).asLong());
    }
}
