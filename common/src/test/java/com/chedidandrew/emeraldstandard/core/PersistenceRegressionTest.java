package com.chedidandrew.emeraldstandard.core;

import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.deleteTree;

import java.nio.file.Files;
import java.nio.file.Path;

/** Persistence, maturity, clock, journal, rollback, and no-debt checks. */
public final class PersistenceRegressionTest {
    private PersistenceRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("emerald-standard-test-");
        try {
            ClockAndMaturityRegression.run(root);
            JournalAndMigrationRegression.run(root);
            DurabilityAndInvariantRegression.run(root);
            System.out.println("PASS PersistenceRegressionTest");
        } finally {
            deleteTree(root);
        }
    }
}
