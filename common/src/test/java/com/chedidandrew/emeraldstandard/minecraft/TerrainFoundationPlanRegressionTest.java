package com.chedidandrew.emeraldstandard.minecraft;

import java.util.ArrayList;
import java.util.List;

/** Regression coverage for stable terrain foundations and unsupported outdoor details. */
public final class TerrainFoundationPlanRegressionTest {
    private TerrainFoundationPlanRegressionTest() {
    }

    public static void main(String[] args) {
        testFloorAndOutdoorPostSupports();
        testFootingWithoutTerrainDepth();
        testSuspendedDetailsStaySuspended();
        testIdempotentAndOrderedSuffix();
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
