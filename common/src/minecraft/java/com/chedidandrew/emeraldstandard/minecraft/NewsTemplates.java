package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import com.google.gson.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;

/** Atomic, bounded datapack wording reload. Templates cannot change prices or reporting facts. */
final class NewsTemplates {
    private static ResourceManager loaded;
    static void reset(){loaded=null;}
    static void refresh(MinecraftServer server,EconomyService economy) {
        ResourceManager resources=server.getResourceManager();
        if(resources==loaded)return;
        loaded=resources;
        try {
            var found=resources.listResources("emerald_news",id->id.getPath().endsWith(".json"));
            if(found.size()>64)throw new IllegalArgumentException("At most 64 news template files");
            var merged=new LinkedHashMap<String,List<String>>();int total=0;
            for(var entry:found.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                StringBuilder json=new StringBuilder();
                try(var reader=entry.getValue().openAsReader()) {
                    char[] buffer=new char[4096];int count;
                    while((count=reader.read(buffer))!=-1) {
                        total+=count;if(total>262144)throw new IllegalArgumentException("News templates exceed 256 Ki characters");
                        json.append(buffer,0,count);
                    }
                }
                JsonObject root=JsonParser.parseString(json.toString()).getAsJsonObject();
                for(var group:root.entrySet()) {
                    if(!group.getValue().isJsonArray())throw new IllegalArgumentException("Expected headline array: "+group.getKey());
                    List<String> lines=new ArrayList<>();
                    for(var value:group.getValue().getAsJsonArray()) {
                        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected plain text");
                        lines.add(value.getAsString());
                    }
                    merged.put(group.getKey(),lines);
                }
            }
            economy.configureNewsTemplates(NewsEditorial.validateTemplates(merged));
            System.getLogger("The Emerald Wire").log(System.Logger.Level.INFO,"Loaded "+merged.size()+" news wording groups");
        } catch(Exception invalid) {
            System.getLogger("The Emerald Wire").log(System.Logger.Level.WARNING,"News wording reload rejected; previous valid wording retained: "+invalid.getMessage());
        }
    }
}
