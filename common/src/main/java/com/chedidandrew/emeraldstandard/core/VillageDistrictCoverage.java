package com.chedidandrew.emeraldstandard.core;

/** The developed survey rectangle, not a land claim. Neighboring coverage can overlap. */
public final class VillageDistrictCoverage {
    public static final int STARTER_RADIUS = 64, FARM_MARGIN = 24;
    public record Bounds(int minX, int minZ, int maxX, int maxZ) {}

    public static Bounds of(EconomyState.VillageRecord village) {
        if(village.organicTerritory && !village.territoryCells.isEmpty()) {
            int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
            for(long c:village.territoryCells) {
                int x=VillageTerritory.cx(c)*16,z=VillageTerritory.cz(c)*16;
                minX=Math.min(minX,x);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x+15);maxZ=Math.max(maxZ,z+15);
            }
            return new Bounds(minX,minZ,maxX,maxZ);
        }
        int minX = x(village.centerPos) - STARTER_RADIUS, maxX = x(village.centerPos) + STARTER_RADIUS;
        int minZ = z(village.centerPos) - STARTER_RADIUS, maxZ = z(village.centerPos) + STARTER_RADIUS;
        for (var project : village.projects) {
            if (project.originPos == 0 || (project.materializedBlocks == 0 && !project.materializedComplete)) continue;
            long low = project.boundsMinPos, high = project.boundsMaxPos;
            int lowX = x(low), lowZ = z(low), highX = x(high), highZ = z(high);
            if (low == 0 && high == 0) {
                lowX = x(project.originPos) - 24; lowZ = z(project.originPos) - 24;
                highX = x(project.originPos) + 24; highZ = z(project.originPos) + 24;
            }
            minX = Math.min(minX, lowX - FARM_MARGIN); maxX = Math.max(maxX, highX + FARM_MARGIN);
            minZ = Math.min(minZ, lowZ - FARM_MARGIN); maxZ = Math.max(maxZ, highZ + FARM_MARGIN);
        }
        if (village.bankAnchorPos != 0) {
            minX = Math.min(minX, x(village.bankAnchorPos) - 8 - FARM_MARGIN);
            maxX = Math.max(maxX, x(village.bankAnchorPos) + 8 + FARM_MARGIN);
            minZ = Math.min(minZ, z(village.bankAnchorPos) - 14 - FARM_MARGIN);
            maxZ = Math.max(maxZ, z(village.bankAnchorPos) + 3 + FARM_MARGIN);
        }
        return new Bounds(minX, minZ, maxX, maxZ);
    }
    private static int x(long pos) { return (int) (pos >> 38); }
    private static int z(long pos) { return (int) (pos << 26 >> 38); }
    private VillageDistrictCoverage() {}
}
