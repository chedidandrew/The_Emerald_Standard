package com.chedidandrew.emeraldstandard.core;

import com.chedidandrew.emeraldstandard.core.WholeBuildingRoleReadabilityValidator.BuildingRole;
import com.chedidandrew.emeraldstandard.core.WholeBuildingRoleReadabilityValidator.FixtureSignal;
import com.chedidandrew.emeraldstandard.core.WholeBuildingRoleReadabilityValidator.RoleProfile;
import com.chedidandrew.emeraldstandard.core.WholeBuildingRoleReadabilityValidator.RoleSnapshot;

import java.util.ArrayList;
import java.util.List;

/** Regression coverage for palette-independent, role-readable building fixtures. */
public final class WholeBuildingRoleReadabilityValidatorRegressionTest {
    private WholeBuildingRoleReadabilityValidatorRegressionTest() {
    }

    public static void main(String[] args) {
        testEveryRoleAcceptsItsRequiredSignals();
        testUnrelatedClutterCannotSubstituteForRoleIdentity();
        testCottageAndInnRequireBothSignals();
        testExchangeHallRequiresExactlyOneDesk();
        testCatalogOrderingIdentityAndDiagnostics();
        testSnapshotGuards();
        System.out.println("PASS whole-building role-readability validator regressions");
    }

    private static void testEveryRoleAcceptsItsRequiredSignals() {
        for (BuildingRole role : BuildingRole.values()) {
            RoleProfile profile = WholeBuildingRoleReadabilityValidator.profile(
                    readableSnapshot("readable_" + role.name().toLowerCase(), role));
            require(profile.readable(), role + " rejected its required signals: "
                    + profile.failures());
        }
    }

    private static void testUnrelatedClutterCannotSubstituteForRoleIdentity() {
        for (BuildingRole role : BuildingRole.values()) {
            FixtureSignal unrelated = role == BuildingRole.HOUSE
                    ? FixtureSignal.FORGE_WORKS
                    : FixtureSignal.SLEEPING;
            RoleProfile profile = WholeBuildingRoleReadabilityValidator.profile(
                    new RoleSnapshot("wrong_" + role.name().toLowerCase(), role,
                            List.of(unrelated, unrelated, unrelated, unrelated)));
            require(!profile.readable(),
                    "Raw fixture abundance substituted for " + role + " role identity");
        }
    }

    private static void testCottageAndInnRequireBothSignals() {
        RoleProfile cottage = WholeBuildingRoleReadabilityValidator.profile(new RoleSnapshot(
                "bed_only_cottage", BuildingRole.COTTAGE, List.of(FixtureSignal.SLEEPING)));
        require(!cottage.readable()
                        && cottage.failures().stream()
                                .anyMatch(failure -> failure.contains("DOMESTIC_HEARTH")),
                "A bed-only cottage passed without a domestic hearth");

        RoleProfile inn = WholeBuildingRoleReadabilityValidator.profile(new RoleSnapshot(
                "beds_only_inn",
                BuildingRole.INN,
                List.of(FixtureSignal.SLEEPING, FixtureSignal.SLEEPING)));
        require(!inn.readable()
                        && inn.failures().stream()
                                .anyMatch(failure -> failure.contains("HOSPITALITY_SERVICE")),
                "An inn passed without visible hospitality service");
    }

    private static void testExchangeHallRequiresExactlyOneDesk() {
        for (int desks : new int[] {0, 2, 3}) {
            List<FixtureSignal> signals = new ArrayList<>();
            for (int index = 0; index < desks; index++) {
                signals.add(FixtureSignal.EXCHANGE_DESK);
            }
            RoleProfile profile = WholeBuildingRoleReadabilityValidator.profile(
                    new RoleSnapshot("exchange_" + desks, BuildingRole.EXCHANGE_HALL, signals));
            require(!profile.readable()
                            && profile.failures().getFirst().contains("exactly one"),
                    "Exchange Hall accepted " + desks + " desks");
        }
        require(WholeBuildingRoleReadabilityValidator.profile(new RoleSnapshot(
                        "exchange_one",
                        BuildingRole.EXCHANGE_HALL,
                        List.of(FixtureSignal.EXCHANGE_DESK))).readable(),
                "Exchange Hall rejected its one authoritative desk");
    }

    private static void testCatalogOrderingIdentityAndDiagnostics() {
        RoleSnapshot valid = readableSnapshot("z_smithy", BuildingRole.SMITHY);
        RoleSnapshot invalid = new RoleSnapshot(
                "a_smithy", BuildingRole.SMITHY, List.of(FixtureSignal.BULK_STORAGE));
        WholeBuildingRoleReadabilityValidator.CatalogReport report =
                WholeBuildingRoleReadabilityValidator.validateSnapshots(List.of(valid, invalid));
        require(report.profiles().getFirst().id().equals("a_smithy")
                        && report.unreadableProfiles().size() == 1,
                "Role catalog report is not stable-id ordered or isolated");
        try {
            report.requireReadable();
            throw new AssertionError("Role-unreadable catalog passed admission");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("a_smithy")
                            && expected.getMessage().contains("FORGE_WORKS")
                            && expected.getMessage().contains("signals"),
                    "Role-readability failure omitted actionable diagnostics");
        }
        expectIllegal(() -> WholeBuildingRoleReadabilityValidator.validateSnapshots(
                List.of(valid, valid)), "Duplicate role-readability snapshot id");
    }

    private static void testSnapshotGuards() {
        expectIllegal(() -> new RoleSnapshot(
                " ", BuildingRole.HOUSE, List.of(FixtureSignal.SLEEPING)), "must not be blank");
        expectIllegal(() -> new RoleSnapshot(
                "null_signal", BuildingRole.HOUSE, java.util.Arrays.asList((FixtureSignal) null)),
                "fixture signal");
    }

    private static RoleSnapshot readableSnapshot(String id, BuildingRole role) {
        List<FixtureSignal> signals = switch (role) {
            case COTTAGE -> List.of(
                    FixtureSignal.SLEEPING, FixtureSignal.DOMESTIC_HEARTH);
            case HOUSE -> List.of(FixtureSignal.SLEEPING);
            case INN -> List.of(
                    FixtureSignal.SLEEPING, FixtureSignal.HOSPITALITY_SERVICE);
            case WAREHOUSE -> List.of(FixtureSignal.BULK_STORAGE);
            case GRANARY -> List.of(FixtureSignal.AGRICULTURAL_STORAGE);
            case SMITHY -> List.of(FixtureSignal.FORGE_WORKS);
            case MINE_ENTRANCE -> List.of(FixtureSignal.MINE_WORKS);
            case MARKET_SQUARE -> List.of(FixtureSignal.MARKET_ANCHOR);
            case GUARD_POST -> List.of(FixtureSignal.DEFENSIVE_EQUIPMENT);
            case EXCHANGE_HALL -> List.of(FixtureSignal.EXCHANGE_DESK);
        };
        return new RoleSnapshot(id, role, signals);
    }

    private static void expectIllegal(Runnable action, String fragment) {
        try {
            action.run();
            throw new AssertionError("Invalid role-readability input was accepted");
        } catch (IllegalArgumentException | NullPointerException expected) {
            require(expected.getMessage().contains(fragment),
                    "Validation error omitted '" + fragment + "': " + expected.getMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
