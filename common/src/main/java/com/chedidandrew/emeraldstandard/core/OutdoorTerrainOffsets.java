package com.chedidandrew.emeraldstandard.core;

import java.util.*;

/** Canonical, bounded column shifts frozen with a new optional landscape's approval. */
public final class OutdoorTerrainOffsets {
    private OutdoorTerrainOffsets() { }
    public static boolean valid(Map<Long,Integer> offsets) {
        if(offsets==null||offsets.size()>4096)return false;
        for(var e:offsets.entrySet()) {
            if(e.getKey()==null||e.getValue()==null||Math.abs((long)e.getValue())>4)return false;
            long column=e.getKey();
            // Canonical blueprint-local X/Z columns, never a world coordinate or a Y shift key.
            if((column&4095L)!=0||(column>>38)<0||(column>>38)>255
                    ||(column<<26>>38)<0||(column<<26>>38)>255)return false;
        }
        return true;
    }
    public static String encode(Map<Long,Integer> offsets) {
        if(!valid(offsets))throw new IllegalArgumentException("Invalid outdoor terrain offsets");
        return String.join(";",new TreeMap<>(offsets).entrySet().stream().map(e->e.getKey()+":"+e.getValue()).toList());
    }
    public static Map<Long,Integer> decode(String value) {
        if(value.length()>120000)throw new IllegalArgumentException("Oversized outdoor terrain offsets");
        Map<Long,Integer> map=new TreeMap<>();
        if(!value.isEmpty())for(String entry:value.split(";",-1)) {
            String[] parts=entry.split(":",-1);
            if(parts.length!=2||map.put(Long.parseLong(parts[0]),Integer.parseInt(parts[1]))!=null)
                throw new IllegalArgumentException("Duplicate/invalid outdoor terrain column");
        }
        if(!valid(map))throw new IllegalArgumentException("Invalid outdoor terrain offsets");
        return map;
    }
}
