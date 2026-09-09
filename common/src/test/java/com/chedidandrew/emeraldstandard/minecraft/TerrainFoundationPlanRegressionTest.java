package com.chedidandrew.emeraldstandard.minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Regression coverage for stable terrain foundations and unsupported outdoor details. */
public final class TerrainFoundationPlanRegressionTest {
    private TerrainFoundationPlanRegressionTest() {
    }

    public static void main(String[] args) {
        testFloorAndOutdoorPostSupports();
        testFootingWithoutTerrainDepth();
        testSuspendedDetailsStaySuspended();
        testBelowOriginStairSupports();
        testIdempotentAndOrderedSuffix();
        testTransformedAnnexGroundContactEnvelope();
        testSuspendedAuthorityDoesNotOwnCosmeticFooting();
        testTerrainRangeBounds();
        System.out.println("PASS terrain foundation planning regressions");
    }

    private static void testFloorAndOutdoorPostSupports() {
        List<TerrainFoundationPlan.Cell> authored = List.of(
                new TerrainFoundationPlan.Cell(0, 0, 0),
                new TerrainFoundationPlan.Cell(1, 0, 0),
                new TerrainFoundationPlan.Cell(2, 1, -1));
        List<TerrainFoundationPlan.Cell> suffix =
                TerrainFoundationPlan.appendSupportCells(authored, 2);
        require(suffix.contains(new TerrainFoundationPlan.Cell(0, -1, 0))
                        && suffix.contains(new TerrainFoundationPlan.Cell(0, -2, 0))
                        && suffix.contains(new TerrainFoundationPlan.Cell(1, -1, 0))
                        && suffix.contains(new TerrainFoundationPlan.Cell(1, -2, 0)),
                "A floor column lost its two-block terrain foundation");
        require(suffix.contains(new TerrainFoundationPlan.Cell(2, 0, -1))
                        && suffix.contains(new TerrainFoundationPlan.Cell(2, -1, -1))
                        && suffix.contains(new TerrainFoundationPlan.Cell(2, -2, -1)),
                "An outdoor y=1 post did not receive a footing and terrain supports");
    }

    private static void testSuspendedDetailsStaySuspended() {
        List<TerrainFoundationPlan.Cell> suffix = TerrainFoundationPlan.appendSupportCells(
                List.of(
                        new TerrainFoundationPlan.Cell(4, 3, -1),
                        new TerrainFoundationPlan.Cell(4, 4, -1)),
                2);
        require(suffix.isEmpty(),
                "An intentional roof/fascia column was extended down to the terrain");
    }

    private static void testFootingWithoutTerrainDepth() {
        List<TerrainFoundationPlan.Cell> suffix = TerrainFoundationPlan.appendSupportCells(
                List.of(new TerrainFoundationPlan.Cell(4, 1, -1)),
                0);
        require(suffix.equals(List.of(new TerrainFoundationPlan.Cell(4, 0, -1))),
                "A y=1 outdoor detail lost its structural footing at zero terrain depth");
    }

    private static void testBelowOriginStairSupports() {
        List<TerrainFoundationPlan.Cell> middleSupport =
                TerrainFoundationPlan.appendSupportCells(
                        List.of(new TerrainFoundationPlan.Cell(6, -1, -3)),
                        2);
        require(middleSupport.equals(List.of(
                        new TerrainFoundationPlan.Cell(6, -2, -3))),
                "A below-origin stair did not receive only the support beneath it");

        List<TerrainFoundationPlan.Cell> lowestSupport =
                TerrainFoundationPlan.appendSupportCells(
                        List.of(new TerrainFoundationPlan.Cell(6, -2, -4)),
                        2);
        require(lowestSupport.isEmpty(),
                "Foundation planning filled walkable space above the lowest terrain stair");
    }

    private static void testIdempotentAndOrderedSuffix() {
        List<TerrainFoundationPlan.Cell> authored = new ArrayList<>(List.of(
                new TerrainFoundationPlan.Cell(3, 1, -1),
                new TerrainFoundationPlan.Cell(0, 0, 0)));
        List<TerrainFoundationPlan.Cell> first =
                TerrainFoundationPlan.appendSupportCells(authored, 2);
        authored.addAll(first);
        require(TerrainFoundationPlan.appendSupportCells(authored, 2).isEmpty(),
                "Applying a support suffix twice introduced duplicate cells");
        require(first.equals(List.of(
                        new TerrainFoundationPlan.Cell(3, 0, -1),
                        new TerrainFoundationPlan.Cell(3, -1, -1),
                        new TerrainFoundationPlan.Cell(3, -2, -1),
                        new TerrainFoundationPlan.Cell(0, -1, 0),
                        new TerrainFoundationPlan.Cell(0, -2, 0))),
                "Foundation suffix order is not stable by z, x, then descending support level");
    }

    private static void testTerrainRangeBounds() {
        require(TerrainFoundationPlan.supportsTerrainRange(64, 66, 2),
                "A bridgeable two-block surface range was rejected");
        require(!TerrainFoundationPlan.supportsTerrainRange(64, 67, 2),
                "A terrain drop deeper than the foundation was accepted");
        require(!TerrainFoundationPlan.supportsTerrainRange(
                        Integer.MAX_VALUE, Integer.MIN_VALUE, 2),
                "An empty terrain sample was accepted");
    }

    private static void testTransformedAnnexGroundContactEnvelope() {
        List<TerrainFoundationPlan.Cell> canonical = List.of(
                new TerrainFoundationPlan.Cell(0, 0, 0),
                new TerrainFoundationPlan.Cell(4, 1, 4),
                new TerrainFoundationPlan.Cell(2, 5, -6));
        require(TerrainFoundationPlan.groundContactColumns(canonical).equals(List.of(
                        new TerrainFoundationPlan.Column(0, 0),
                        new TerrainFoundationPlan.Column(4, 4))),
                "A suspended roof detail was mistaken for a terrain-bearing annex");

        int width = 9;
        int depth = 7;
        TerrainFoundationPlan.Cell annex = new TerrainFoundationPlan.Cell(3, 0, -6);
        Set<TerrainFoundationPlan.Column> transformed = new java.util.HashSet<>();
        for (int rotation = 0; rotation < 4; rotation++) {
            TerrainFoundationPlan.Cell rotated = rotate(annex, width, depth, rotation);
            List<TerrainFoundationPlan.Column> columns =
                    TerrainFoundationPlan.groundContactColumns(List.of(rotated));
            require(columns.size() == 1,
                    "A rotated out-of-descriptor annex lost its ground-contact column");
            transformed.add(columns.getFirst());
        }
        require(transformed.size() == 4
                        && transformed.stream().anyMatch(column -> column.z() < 0)
                        && transformed.stream().anyMatch(column -> column.x() >= depth),
                "Ground-contact extraction collapsed or clipped rotated annex coordinates");
        require(TerrainFoundationPlan.supportsTerrainRange(80, 84, 4)
                        && !TerrainFoundationPlan.supportsTerrainRange(79, 84, 4),
                "The four-block mountain support proof accepted an unbridgeable cliff");
    }

    private static void testSuspendedAuthorityDoesNotOwnCosmeticFooting() {
        List<TerrainFoundationPlan.Cell> authoritative = List.of(
                new TerrainFoundationPlan.Cell(3, 3, -2));
        List<TerrainFoundationPlan.Cell> authored = List.of(
                authoritative.getFirst(),
                new TerrainFoundationPlan.Cell(3, 0, -2));
        require(TerrainFoundationPlan.groundContactColumns(authoritative).isEmpty(),
                "A suspended structural roof detail became terrain-bearing authority");
        require(TerrainFoundationPlan.groundContactColumns(authored).equals(List.of(
                        new TerrainFoundationPlan.Column(3, -2))),
                "The regression fixture lost its cosmetic ground-contact column");
        require(!TerrainFoundationPlan.appendSupportCells(authored, 2).isEmpty(),
                "The regression fixture no longer synthesizes the optional footing under test");
    }

    private static TerrainFoundationPlan.Cell rotate(
            TerrainFoundationPlan.Cell cell, int width, int depth, int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> new TerrainFoundationPlan.Cell(
                    depth - 1 - cell.z(), cell.y(), cell.x());
            case 2 -> new TerrainFoundationPlan.Cell(
                    width - 1 - cell.x(), cell.y(), depth - 1 - cell.z());
            case 3 -> new TerrainFoundationPlan.Cell(
                    cell.z(), cell.y(), width - 1 - cell.x());
            default -> cell;
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
