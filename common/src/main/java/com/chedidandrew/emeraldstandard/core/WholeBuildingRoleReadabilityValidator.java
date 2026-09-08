package com.chedidandrew.emeraldstandard.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Loader-neutral admission gate for functional, visually readable authored-building roles.
 *
 * <p>Raw fixture counts cannot distinguish a convincing forge from a house filled with flower
 * pots. The Minecraft bridge therefore translates only immutable base-plan blocks into the small
 * semantic vocabulary below. This validator owns the role rules without knowing any block ids,
 * loader APIs, palettes, or optional workstation implementations.</p>
 */
public final class WholeBuildingRoleReadabilityValidator {
    private WholeBuildingRoleReadabilityValidator() {
    }

    /** Stable economic roles represented by the authored catalog. */
    public enum BuildingRole {
        COTTAGE,
        HOUSE,
        INN,
        WAREHOUSE,
        GRANARY,
        SMITHY,
        MINE_ENTRANCE,
        MARKET_SQUARE,
        GUARD_POST,
        EXCHANGE_HALL
    }

    /** Palette-independent evidence that a player can read a building's purpose at a glance. */
    public enum FixtureSignal {
        SLEEPING,
        DOMESTIC_HEARTH,
        HOSPITALITY_SERVICE,
        BULK_STORAGE,
        AGRICULTURAL_STORAGE,
        FORGE_WORKS,
        MINE_WORKS,
        MARKET_ANCHOR,
        DEFENSIVE_EQUIPMENT,
        EXCHANGE_DESK
    }

    /** One maximum-stage master reduced to its immutable role and semantic fixture signals. */
    public record RoleSnapshot(
            String id,
            BuildingRole role,
            Collection<FixtureSignal> fixtureSignals) {
        public RoleSnapshot {
            id = Objects.requireNonNull(id, "id").trim();
            if (id.isEmpty()) {
                throw new IllegalArgumentException("Role-readability snapshot id must not be blank");
            }
            role = Objects.requireNonNull(role, "role");
            Collection<FixtureSignal> suppliedSignals =
                    Objects.requireNonNull(fixtureSignals, "fixtureSignals");
            suppliedSignals.forEach(
                    signal -> Objects.requireNonNull(signal, "fixture signal"));
            fixtureSignals = List.copyOf(suppliedSignals);
        }
    }

    /** Deterministic measurements and failed role rules for one master. */
    public record RoleProfile(
            String id,
            BuildingRole role,
            Map<FixtureSignal, Integer> signalCounts,
            List<String> failures) {
        public RoleProfile {
            id = Objects.requireNonNull(id, "id");
            role = Objects.requireNonNull(role, "role");
            EnumMap<FixtureSignal, Integer> frozen = new EnumMap<>(FixtureSignal.class);
            frozen.putAll(Objects.requireNonNull(signalCounts, "signalCounts"));
            signalCounts = Collections.unmodifiableMap(frozen);
            failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        }

        public boolean readable() {
            return failures.isEmpty();
        }
    }

    /** Frozen catalog report sorted by stable master id. */
    public record CatalogReport(List<RoleProfile> profiles) {
        public CatalogReport {
            profiles = List.copyOf(Objects.requireNonNull(profiles, "profiles"));
        }

        public List<RoleProfile> unreadableProfiles() {
            return profiles.stream().filter(profile -> !profile.readable()).toList();
        }

        public boolean readable() {
            return unreadableProfiles().isEmpty();
        }

        public void requireReadable() {
            List<RoleProfile> unreadable = unreadableProfiles();
            if (unreadable.isEmpty()) {
                return;
            }
            StringBuilder message = new StringBuilder(
                    "Authored building catalog contains role-unreadable structures:");
            for (RoleProfile profile : unreadable) {
                message.append(System.lineSeparator())
                        .append(" - ")
                        .append(profile.id())
                        .append(" (")
                        .append(profile.role())
                        .append("): ")
                        .append(String.join("; ", profile.failures()))
                        .append(" [signals ")
                        .append(profile.signalCounts())
                        .append(']');
            }
            throw new IllegalArgumentException(message.toString());
        }
    }

    public static RoleProfile profile(RoleSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        EnumMap<FixtureSignal, Integer> counts = new EnumMap<>(FixtureSignal.class);
        for (FixtureSignal signal : snapshot.fixtureSignals()) {
            counts.merge(signal, 1, Integer::sum);
        }

        List<String> failures = new ArrayList<>();
        switch (snapshot.role()) {
            case COTTAGE -> requireAll(
                    counts,
                    failures,
                    FixtureSignal.SLEEPING,
                    FixtureSignal.DOMESTIC_HEARTH);
            case HOUSE -> requireAll(counts, failures, FixtureSignal.SLEEPING);
            case INN -> requireAll(
                    counts,
                    failures,
                    FixtureSignal.SLEEPING,
                    FixtureSignal.HOSPITALITY_SERVICE);
            case WAREHOUSE -> requireAll(counts, failures, FixtureSignal.BULK_STORAGE);
            case GRANARY -> requireAll(
                    counts, failures, FixtureSignal.AGRICULTURAL_STORAGE);
            case SMITHY -> requireAll(counts, failures, FixtureSignal.FORGE_WORKS);
            case MINE_ENTRANCE -> requireAll(counts, failures, FixtureSignal.MINE_WORKS);
            case MARKET_SQUARE -> requireAll(counts, failures, FixtureSignal.MARKET_ANCHOR);
            case GUARD_POST -> requireAll(
                    counts, failures, FixtureSignal.DEFENSIVE_EQUIPMENT);
            case EXCHANGE_HALL -> {
                int desks = counts.getOrDefault(FixtureSignal.EXCHANGE_DESK, 0);
                if (desks != 1) {
                    failures.add("EXCHANGE_HALL requires exactly one EXCHANGE_DESK signal; found "
                            + desks);
                }
            }
        }
        return new RoleProfile(snapshot.id(), snapshot.role(), counts, failures);
    }

    public static CatalogReport validateSnapshots(Collection<RoleSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots");
        List<RoleProfile> profiles = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (RoleSnapshot snapshot : snapshots) {
            Objects.requireNonNull(snapshot, "snapshot");
            if (!ids.add(snapshot.id())) {
                throw new IllegalArgumentException(
                        "Duplicate role-readability snapshot id: " + snapshot.id());
            }
            profiles.add(profile(snapshot));
        }
        profiles.sort(Comparator.comparing(RoleProfile::id));
        return new CatalogReport(profiles);
    }

    private static void requireAll(
            Map<FixtureSignal, Integer> counts,
            List<String> failures,
            FixtureSignal... requiredSignals) {
        for (FixtureSignal signal : requiredSignals) {
            if (counts.getOrDefault(signal, 0) == 0) {
                failures.add("missing required " + signal + " signal");
            }
        }
    }
}
