package com.chedidandrew.emeraldstandard.minecraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bounded round-robin work queue for save-ordered generated-Banker conversion handoffs. */
public final class BankerConversionRetryQueue {
    private final int attemptsPerPass;
    private final LinkedHashMap<Long, Handoff> pending = new LinkedHashMap<>();

    public BankerConversionRetryQueue(int attemptsPerPass) {
        if (attemptsPerPass <= 0) {
            throw new IllegalArgumentException("Conversion-retry work limit must be positive");
        }
        this.attemptsPerPass = attemptsPerPass;
    }

    /** Never replaces different unresolved lineage for the same generated Bank. */
    public synchronized boolean offer(
            long regionKey, UUID previousId, UUID convertedId, boolean continuingBanker) {
        return offer(regionKey, previousId, previousId, convertedId, continuingBanker);
    }

    public synchronized boolean offer(
            long regionKey,
            UUID previousId,
            UUID sourceId,
            UUID convertedId,
            boolean continuingBanker) {
        Handoff handoff = new Handoff(
                previousId, sourceId, convertedId, continuingBanker);
        Handoff existing = pending.get(regionKey);
        if (existing != null) {
            return existing.equals(handoff);
        }
        pending.put(regionKey, handoff);
        return true;
    }

    /** Collapses A-to-B followed by B-to-C into one unresolved A-to-C handoff. */
    public synchronized boolean chain(
            long regionKey,
            UUID expectedIntermediateId,
            UUID convertedId,
            boolean continuingBanker) {
        Objects.requireNonNull(expectedIntermediateId, "expectedIntermediateId");
        Handoff existing = pending.get(regionKey);
        if (existing == null || !expectedIntermediateId.equals(existing.convertedId())) {
            return false;
        }
        pending.put(
                regionKey,
                new Handoff(
                        existing.previousId(),
                        expectedIntermediateId,
                        convertedId,
                        continuingBanker));
        return true;
    }

    public synchronized boolean containsRegion(long regionKey) {
        return pending.containsKey(regionKey);
    }

    public synchronized Handoff handoffForRegion(long regionKey) {
        return pending.get(regionKey);
    }

    public synchronized PendingHandoff findByConvertedId(UUID convertedId) {
        Objects.requireNonNull(convertedId, "convertedId");
        for (Map.Entry<Long, Handoff> entry : pending.entrySet()) {
            if (convertedId.equals(entry.getValue().convertedId())) {
                return new PendingHandoff(entry.getKey(), entry.getValue());
            }
        }
        return null;
    }

    public synchronized boolean remove(long regionKey, Handoff handoff) {
        Objects.requireNonNull(handoff, "handoff");
        return pending.remove(regionKey, handoff);
    }

    public synchronized Handoff removeRegion(long regionKey) {
        return pending.remove(regionKey);
    }

    public synchronized int size() {
        return pending.size();
    }

    /** Failed work moves to the tail so one unavailable entity or disk cannot starve another. */
    public synchronized int retry(Resolver resolver) {
        Objects.requireNonNull(resolver, "resolver");
        int attempts = Math.min(attemptsPerPass, pending.size());
        int resolved = 0;
        for (int attempt = 0; attempt < attempts; attempt++) {
            Map.Entry<Long, Handoff> entry = pending.entrySet().iterator().next();
            long regionKey = entry.getKey();
            Handoff handoff = entry.getValue();
            boolean complete;
            try {
                complete = resolver.resolve(regionKey, handoff);
            } catch (RuntimeException exception) {
                if (pending.remove(regionKey, handoff)) {
                    pending.put(regionKey, handoff);
                }
                throw exception;
            }
            if (complete) {
                if (pending.remove(regionKey, handoff)) {
                    resolved++;
                }
            } else if (pending.remove(regionKey, handoff)) {
                pending.put(regionKey, handoff);
            }
        }
        return resolved;
    }

    public synchronized void clear() {
        pending.clear();
    }

    public record Handoff(
            UUID previousId,
            UUID sourceId,
            UUID convertedId,
            boolean continuingBanker) {
        public Handoff(UUID previousId, UUID convertedId, boolean continuingBanker) {
            this(previousId, previousId, convertedId, continuingBanker);
        }

        public Handoff {
            Objects.requireNonNull(previousId, "previousId");
            Objects.requireNonNull(sourceId, "sourceId");
            Objects.requireNonNull(convertedId, "convertedId");
            if (previousId.equals(convertedId) || sourceId.equals(convertedId)) {
                throw new IllegalArgumentException("A conversion must change entity UUID");
            }
        }
    }

    public record PendingHandoff(long regionKey, Handoff handoff) {
    }

    @FunctionalInterface
    public interface Resolver {
        boolean resolve(long regionKey, Handoff handoff);
    }
}
