package com.chedidandrew.emeraldstandard.minecraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Round-robin retry queue for durably recording a canonical generated Banker's observed death.
 *
 * <p>The queue bridges a transient save outage until the economy state can persist its UUID-bound
 * death tombstone. The durable tombstone, rather than this session-only queue, authorizes a safe
 * replacement after restart. Until then the old canonical UUID remains authoritative.</p>
 */
public final class BankerDeathRetryQueue {
    private final int attemptsPerPass;
    private final LinkedHashMap<Long, UUID> pending = new LinkedHashMap<>();

    public BankerDeathRetryQueue(int attemptsPerPass) {
        if (attemptsPerPass <= 0) {
            throw new IllegalArgumentException("Death-retry work limit must be positive");
        }
        this.attemptsPerPass = attemptsPerPass;
    }

    /** Adds one canonical death proof without replacing a different unresolved identity. */
    public synchronized boolean offer(long regionKey, UUID bankerId) {
        Objects.requireNonNull(bankerId, "bankerId");
        UUID existing = pending.get(regionKey);
        if (existing != null) {
            return existing.equals(bankerId);
        }
        pending.put(regionKey, bankerId);
        return true;
    }

    public synchronized boolean containsRegion(long regionKey) {
        return pending.containsKey(regionKey);
    }

    public synchronized int size() {
        return pending.size();
    }

    /** Removes only the death proof for the identity that was actually resolved. */
    public synchronized boolean remove(long regionKey, UUID bankerId) {
        Objects.requireNonNull(bankerId, "bankerId");
        return pending.remove(regionKey, bankerId);
    }

    /**
     * Tries a bounded number of entries and moves failures to the tail so one disk error cannot
     * starve every later region. The resolver returns true when the proof is no longer needed.
     */
    public synchronized int retry(BiPredicate<Long, UUID> resolver) {
        Objects.requireNonNull(resolver, "resolver");
        int attempts = Math.min(attemptsPerPass, pending.size());
        int resolved = 0;
        for (int attempt = 0; attempt < attempts; attempt++) {
            Map.Entry<Long, UUID> entry = pending.entrySet().iterator().next();
            long regionKey = entry.getKey();
            UUID bankerId = entry.getValue();
            boolean complete;
            try {
                complete = resolver.test(regionKey, bankerId);
            } catch (RuntimeException exception) {
                if (pending.remove(regionKey, bankerId)) {
                    pending.put(regionKey, bankerId);
                }
                throw exception;
            }
            if (complete) {
                if (pending.remove(regionKey, bankerId)) {
                    resolved++;
                }
            } else if (pending.remove(regionKey, bankerId)) {
                pending.put(regionKey, bankerId);
            }
        }
        return resolved;
    }

    /** Clears session-only proof when a server stops or another world starts in-process. */
    public synchronized void clear() {
        pending.clear();
    }
}
