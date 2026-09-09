package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel;
import com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidator.Bounds;
import com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidator.Code;
import com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidator.LightEmitter;
import com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidator.LightingSnapshot;
import com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidator.ValidationReport;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Executable regressions for deterministic, loader-neutral block-light propagation. */
public final class WholeBuildingLightingValidatorRegressionTest {
    private WholeBuildingLightingValidatorRegressionTest() {
    }

    public static void main(String[] args) {
        verifiesModernSpawnThresholdAtLightOne();
        requiresASeparateVisualComfortThreshold();
        rejectsTheFirstZeroLightTile();
        respectsStateDerivedLightDampening();
        routesLightThroughAuthoredOpenings();
        neverCreditsSkylightOrExteriorAmbientLight();
        rejectsOutOfEnvelopeInputs();
        System.out.println("PASS whole-building lighting validator regressions");
    }

    private static void verifiesModernSpawnThresholdAtLightOne() {
        Voxel source = voxel(0, 3, 0);
        Voxel edge = voxel(12, 1, 0);
        ValidationReport report = WholeBuildingLightingValidator.validate(snapshot(
                new Bounds(voxel(0, 1, 0), voxel(12, 3, 0)),
                Set.of(edge),
                Map.of(),
                List.of(new LightEmitter(source, 15))));
        require(report.spawnSafe(), "Block light one should be hostile-spawn safe");
        require(!report.comfortablyLit(),
                "Block light one should remain below the visual-comfort threshold");
        require(report.blockLightAt(edge) == 1,
                "Propagation did not preserve the exact safe edge at block light one");
    }

    private static void rejectsTheFirstZeroLightTile() {
        Voxel source = voxel(0, 3, 0);
        Voxel dark = voxel(13, 1, 0);
        ValidationReport report = WholeBuildingLightingValidator.validate(snapshot(
                new Bounds(voxel(0, 1, 0), voxel(13, 3, 0)),
                Set.of(dark),
                Map.of(),
                List.of(new LightEmitter(source, 15))));
        require(!report.spawnSafe(), "A zero-light interior tile passed the spawn-safety gate");
        require(report.blockLightAt(dark) == 0,
                "The first tile beyond a lantern's range should receive zero block light");
        require(report.count(Code.SPAWN_UNSAFE_TRAVERSABLE_FLOOR) == 1,
                "The zero-light floor was not reported precisely once");
    }

    private static void requiresASeparateVisualComfortThreshold() {
        Voxel floor = voxel(6, 1, 0);
        ValidationReport report = WholeBuildingLightingValidator.validate(snapshot(
                new Bounds(voxel(0, 1, 0), voxel(6, 1, 0)),
                Set.of(floor),
                Map.of(),
                List.of(new LightEmitter(voxel(0, 1, 0), 12))));
        require(report.blockLightAt(floor) == 6,
                "Comfort regression fixture should receive block light six");
        require(report.spawnSafe(), "Dim light six was incorrectly called spawn-unsafe");
        require(!report.comfortablyLit(), "Dim light six passed the comfort gate");
        require(report.count(Code.DIM_TRAVERSABLE_FLOOR) == 1,
                "Dim-but-safe floor was not distinguished from a spawn-unsafe floor");
    }

    private static void respectsStateDerivedLightDampening() {
        Voxel target = voxel(3, 1, 0);
        ValidationReport report = WholeBuildingLightingValidator.validate(snapshot(
                new Bounds(voxel(0, 1, 0), voxel(3, 1, 0)),
                Set.of(target),
                Map.of(voxel(1, 1, 0), 15),
                List.of(new LightEmitter(voxel(0, 1, 0), 15))));
        require(!report.spawnSafe(),
                "Light passed through a fully dampening authored wall cell");
        require(report.blockLightAt(target) == 0,
                "Opaque-cell propagation leaked emitted light to the far side");
    }

    private static void routesLightThroughAuthoredOpenings() {
        Voxel target = voxel(4, 1, 1);
        Map<Voxel, Integer> wallWithOpening = Map.of(
                voxel(2, 1, 0), 15,
                voxel(2, 1, 2), 15);
        ValidationReport report = WholeBuildingLightingValidator.validate(snapshot(
                new Bounds(voxel(0, 1, 0), voxel(4, 1, 2)),
                Set.of(target),
                wallWithOpening,
                List.of(new LightEmitter(voxel(0, 1, 1), 8))));
        require(report.spawnSafe(), "An authored doorway failed to carry emitted block light");
        require(report.blockLightAt(target) == 4,
                "Opening propagation did not use orthogonal one-level attenuation");
    }

    private static void neverCreditsSkylightOrExteriorAmbientLight() {
        Voxel floor = voxel(2, 1, 2);
        ValidationReport report = WholeBuildingLightingValidator.validate(snapshot(
                new Bounds(voxel(0, 1, 0), voxel(4, 4, 4)),
                Set.of(floor),
                Map.of(),
                List.of()));
        require(!report.spawnSafe(),
                "A roofless-looking snapshot received implicit skylight or ambient light");
        require(report.blockLightAt(floor) == 0,
                "Loader-neutral validation must begin at emitted block light zero");
    }

    private static void rejectsOutOfEnvelopeInputs() {
        LightingSnapshot snapshot = new LightingSnapshot(
                "out-of-envelope",
                new Bounds(voxel(0, 0, 0), voxel(1, 1, 1)),
                Set.of(voxel(2, 1, 1)),
                Map.of(voxel(-1, 0, 0), 3),
                List.of(new LightEmitter(voxel(2, 0, 0), 10)));
        ValidationReport report = WholeBuildingLightingValidator.validate(snapshot);
        require(report.count(Code.INVALID_SNAPSHOT) == 3,
                "Every out-of-envelope floor, state, and emitter must be diagnosed");
    }

    private static LightingSnapshot snapshot(
            Bounds bounds,
            Set<Voxel> floors,
            Map<Voxel, Integer> dampening,
            List<LightEmitter> emitters) {
        return new LightingSnapshot("test", bounds, floors, dampening, emitters);
    }

    private static Voxel voxel(int x, int y, int z) {
        return new Voxel(x, y, z);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
