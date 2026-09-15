package com.chedidandrew.emeraldstandard.minecraft;

import com.mojang.serialization.Codec;
import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.*;

/** Commissioned and reserved styles only. Missing historical entries retain their original biome palette. */
final class BankStyleLedger extends SavedData {
    static final Codec<BankStyleLedger> CODEC=Codec.unboundedMap(Codec.STRING,Codec.STRING)
            .xmap(BankStyleLedger::new, s->s.styles);
    static final SavedDataType<BankStyleLedger> TYPE=new SavedDataType<>(
            Identifier.fromNamespaceAndPath("the_emerald_standard","bank_styles"),
            BankStyleLedger::new,CODEC,DataFixTypes.LEVEL);
    final Map<String,String> styles=new HashMap<>();
    BankStyleLedger() {}
    BankStyleLedger(Map<String,String> saved) { styles.putAll(saved); }
    static BankStyleLedger get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    void remember(long origin,String style) {
        if (style.isEmpty()) return;
        BiomeDialect.fromId(style);
        if (!style.equals(styles.put(Long.toString(origin),style))) setDirty();
    }
    BiomeDialect style(long origin) {
        String id=styles.get(Long.toString(origin));
        return id==null ? null : BiomeDialect.fromId(id);
    }
}
