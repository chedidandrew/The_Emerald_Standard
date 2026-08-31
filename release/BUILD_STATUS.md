# Build status for 1.0.0

- Shared economy core compilation: PASS
- Deterministic 100-year smoke test: PASS, seed 42 CAGR 10.74%
- 500-seed x 100-year calibration: PASS, mean CAGR 9.97%, observed range 4.76% to 17.56%
- 10,000 simulated one-year outcomes: mean arithmetic return 11.8%, 30.7% negative years, observed minimum -50.1%, maximum +111.1%
- Fabric metadata JSON: PASS
- Fabric Minecraft 26.2 Gradle project: PREPARED, final loader JAR not locally compiled because the sandbox cannot download the Fabric Loom plugin/JDK 25.
- NeoForge Minecraft 26.2 Gradle project: PREPARED, final loader JAR not locally compiled because the sandbox cannot download ModDevGradle/NeoForge/JDK 25.
- GitHub Actions workflow: INCLUDED and configured to build both loader projects using Java 25.

No fake or unverified playable JAR is included. The repository CI is the publication gate for loader JARs.
