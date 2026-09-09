package com.chedidandrew.emeraldstandard.core;

/** Boundary and priority checks for fact-based Village dashboard guidance. */
public final class VillageDashboardPolicyRegressionTest {
    private VillageDashboardPolicyRegressionTest() {
    }

    public static void main(String[] args) {
        testHeadlinePriority();
        testGrowthBoundaries();
        testFundAwareGuidance();
        testDeterminismAndSanitization();
        System.out.println("PASS Village dashboard policy regression tests");
    }

    private static void testHeadlinePriority() {
        var missing = VillageDashboardPolicy.assess(snapshot(
                false, VillageProsperityEngine.Lifecycle.ACTIVE,
                8, 12, 55.0, 70.0, 500.0, null, false,
                VillageProsperityEngine.IncidentCause.NONE, 0,
                true, true, true, true));
        require(missing.bulletin() == VillageDashboardPolicy.BulletinKind.NO_VILLAGE,
                "An unlinked access point invented local news");

        var abandoned = VillageDashboardPolicy.assess(snapshot(
                true, VillageProsperityEngine.Lifecycle.ABANDONED,
                0, 4, 0.0, 0.0, 0.0, null, false,
                VillageProsperityEngine.IncidentCause.PLAYER, 1,
                true, true, true, true));
        require(abandoned.bulletin() == VillageDashboardPolicy.BulletinKind.RESTORATION,
                "An abandoned settlement did not lead with restoration");

        var recovering = VillageDashboardPolicy.assess(snapshot(
                true, VillageProsperityEngine.Lifecycle.RECOVERING,
                3, 6, 30.0, 47.0, 100.0, null, false,
                VillageProsperityEngine.IncidentCause.RAID, 2,
                true, true, true, true));
        require(recovering.bulletin() == VillageDashboardPolicy.BulletinKind.RECOVERY,
                "Recovery status was hidden by an older incident detail");

        var incident = VillageDashboardPolicy.assess(snapshot(
                true, VillageProsperityEngine.Lifecycle.ACTIVE,
                8, 12, 55.0, 70.0, 500.0, null, false,
                VillageProsperityEngine.IncidentCause.RAID,
                VillageDashboardPolicy.RECENT_INCIDENT_DAYS,
                true, true, true, true));
        require(incident.bulletin() == VillageDashboardPolicy.BulletinKind.INCIDENT,
                "A day-seven incident was not treated as recent");

        var oldIncident = VillageDashboardPolicy.assess(snapshot(
                true, VillageProsperityEngine.Lifecycle.ACTIVE,
                8, 12, 55.0, 70.0, 500.0, null, false,
                VillageProsperityEngine.IncidentCause.RAID,
                VillageDashboardPolicy.RECENT_INCIDENT_DAYS + 1,
                true, true, true, true));
        require(oldIncident.bulletin() == VillageDashboardPolicy.BulletinKind.STEADY
                        && oldIncident.safetyGuidance()
                                == VillageDashboardPolicy.SafetyGuidance.MAINTAIN_SECURITY,
                "An incident older than the recovery window remained urgent");

        var devastated = VillageDashboardPolicy.assess(snapshot(
                true, VillageProsperityEngine.Lifecycle.DEVASTATED,
                2, 8, 20.0, 55.0, 200.0, null, false,
                VillageProsperityEngine.IncidentCause.NONE, 0,
                true, true, true, true));
        require(devastated.bulletin() == VillageDashboardPolicy.BulletinKind.HARDSHIP,
                "A devastated settlement received a routine headline");
    }

    private static void testGrowthBoundaries() {
        var lowSafety = VillageDashboardPolicy.assess(snapshot(
                true, VillageProsperityEngine.Lifecycle.ACTIVE,
                8, 12, 55.0, VillageDashboardPolicy.SECURITY_PROJECT_THRESHOLD - 0.01,
                500.0, null, false, VillageProsperityEngine.IncidentCause.NONE, 0,
                true, true, true, true));
        require(lowSafety.bulletin() == VillageDashboardPolicy.BulletinKind.SECURITY
                        && lowSafety.safetyGuidance()
                                == VillageDashboardPolicy.SafetyGuidance.TARGET_SECURITY,
                "Low safety did not produce security guidance");

        var growthSafety = VillageDashboardPolicy.assess(snapshot(
                true, VillageProsperityEngine.Lifecycle.ACTIVE,
                8, 12, 55.0, VillageDashboardPolicy.GROWTH_SAFETY_THRESHOLD - 0.01,
                500.0, null, false, VillageProsperityEngine.IncidentCause.NONE, 0,
                true, true, true, true));
        require(growthSafety.prosperityGuidance()
                        == VillageDashboardPolicy.ProsperityGuidance.SAFETY_BLOCKS_GROWTH,
                "Safety just below the growth floor did not explain the growth pause");

        var exactGrowthSafety = VillageDashboardPolicy.assess(snapshot(
                true, VillageProsperityEngine.Lifecycle.ACTIVE,
                8, 12, 55.0, VillageDashboardPolicy.GROWTH_SAFETY_THRESHOLD,
                500.0, null, false, VillageProsperityEngine.IncidentCause.NONE, 0,
                true, true, true, true));
        require(exactGrowthSafety.prosperityGuidance()
                        == VillageDashboardPolicy.ProsperityGuidance.MAINTAIN_FOUNDATIONS,
                "The exact safety-growth threshold was treated as blocking");

        double exactGrowthFood = 8 * VillageDashboardPolicy.GROWTH_FOOD_PER_RESIDENT;
        var lowFood = VillageDashboardPolicy.assess(snapshot(
                true, VillageProsperityEngine.Lifecycle.ACTIVE,
                8, 12, 55.0, 70.0, exactGrowthFood - 0.01, null, false,
                VillageProsperityEngine.IncidentCause.NONE, 0,
                true, true, true, true));
        require(lowFood.bulletin() == VillageDashboardPolicy.BulletinKind.FOOD
                        && lowFood.prosperityGuidance()
                                == VillageDashboardPolicy.ProsperityGuidance.TARGET_FOOD,
                "A growth-blocking food reserve did not produce a food action");

        var exactFood = VillageDashboardPolicy.assess(snapshot(
                true, VillageProsperityEngine.Lifecycle.ACTIVE,
                8, 12, 55.0, 70.0, exactGrowthFood, null, false,
                VillageProsperityEngine.IncidentCause.NONE, 0,
                true, true, true, true));
        require(exactFood.bulletin() == VillageDashboardPolicy.BulletinKind.STEADY,
                "The exact food-growth threshold was treated as a shortage");

        var fullHousing = VillageDashboardPolicy.assess(snapshot(
                true, VillageProsperityEngine.Lifecycle.ACTIVE,
                8, 8, 55.0, 70.0, 500.0, null, false,
                VillageProsperityEngine.IncidentCause.NONE, 0,
                true, true, true, true));
        require(fullHousing.bulletin() == VillageDashboardPolicy.BulletinKind.HOUSING
                        && fullHousing.prosperityGuidance()
                                == VillageDashboardPolicy.ProsperityGuidance.TARGET_HOUSING,
                "Full housing did not produce a housing action");

        var project = VillageDashboardPolicy.assess(snapshot(
                true, VillageProsperityEngine.Lifecycle.ACTIVE,
                8, 12, 55.0, 70.0, 500.0,
                VillageProsperityEngine.ProjectType.WAREHOUSE, true,
                VillageProsperityEngine.IncidentCause.NONE, 0,
                true, true, true, true));
        require(project.bulletin() == VillageDashboardPolicy.BulletinKind.PROJECT
                        && project.prosperityGuidance()
                                == VillageDashboardPolicy.ProsperityGuidance.SPONSOR_PROJECT,
                "An active planning project did not offer available sponsorship");

        var thriving = VillageDashboardPolicy.assess(snapshot(
                true, VillageProsperityEngine.Lifecycle.ACTIVE,
                10, 16, 70.0, 70.0, 500.0, null, false,
                VillageProsperityEngine.IncidentCause.NONE, 0,
                true, true, true, true));
        require(thriving.bulletin() == VillageDashboardPolicy.BulletinKind.PROSPERITY,
                "A thriving settlement did not receive a positive headline");
    }

    private static void testFundAwareGuidance() {
        var untargetedFood = VillageDashboardPolicy.assess(snapshot(
                true, VillageProsperityEngine.Lifecycle.ACTIVE,
                8, 12, 45.0, 70.0, 20.0, null, false,
                VillageProsperityEngine.IncidentCause.NONE, 0,
                true, true, false, true));
        require(untargetedFood.prosperityGuidance()
                        == VillageDashboardPolicy.ProsperityGuidance.FOOD_UNTARGETED,
                "Disabled targeting still advertised a targeted Food gift");

        var noSponsorship = VillageDashboardPolicy.assess(snapshot(
                true, VillageProsperityEngine.Lifecycle.ACTIVE,
                8, 12, 45.0, 70.0, 500.0,
                VillageProsperityEngine.ProjectType.COTTAGE, true,
                VillageProsperityEngine.IncidentCause.NONE, 0,
                true, true, true, false));
        require(noSponsorship.prosperityGuidance()
                        == VillageDashboardPolicy.ProsperityGuidance.SUPPORT_FOUNDATIONS,
                "Disabled sponsorship still advertised a project sponsorship");

        var simulationOff = VillageDashboardPolicy.assess(snapshot(
                true, VillageProsperityEngine.Lifecycle.ACTIVE,
                8, 12, 45.0, 20.0, 20.0, null, false,
                VillageProsperityEngine.IncidentCause.HOSTILE, 1,
                false, false, true, true));
        require(simulationOff.bulletin()
                                == VillageDashboardPolicy.BulletinKind.SIMULATION_PAUSED
                        && simulationOff.prosperityGuidance()
                        == VillageDashboardPolicy.ProsperityGuidance.SIMULATION_DISABLED
                        && simulationOff.safetyGuidance()
                                == VillageDashboardPolicy.SafetyGuidance.SIMULATION_DISABLED,
                "Simulation-off guidance advertised unavailable economic actions");

        var visualsOff = VillageDashboardPolicy.assess(snapshot(
                true, VillageProsperityEngine.Lifecycle.ACTIVE,
                8, 12, 45.0, 70.0, 500.0,
                VillageProsperityEngine.ProjectType.COTTAGE, false,
                VillageProsperityEngine.IncidentCause.NONE, 0,
                true, false, true, true, true));
        require(visualsOff.bulletin()
                        == VillageDashboardPolicy.BulletinKind.DEVELOPMENT_PAUSED,
                "Disabled visual progression still claimed that local crews were building");

        var untargetedFoundations = VillageDashboardPolicy.assess(snapshot(
                true, VillageProsperityEngine.Lifecycle.ACTIVE,
                8, 12, 45.0, 70.0, 500.0, null, false,
                VillageProsperityEngine.IncidentCause.NONE, 0,
                true, true, false, true));
        require(untargetedFoundations.prosperityGuidance()
                        == VillageDashboardPolicy.ProsperityGuidance
                                .SUPPORT_FOUNDATIONS_UNTARGETED,
                "Disabled targeting still advertised an Infrastructure-purpose gift");
    }

    private static void testDeterminismAndSanitization() {
        var input = snapshot(
                true, VillageProsperityEngine.Lifecycle.ACTIVE,
                8, 12, Double.NaN, Double.POSITIVE_INFINITY, Double.NaN,
                null, false, VillageProsperityEngine.IncidentCause.NONE, -99,
                true, true, true, true);
        var first = VillageDashboardPolicy.assess(input);
        var second = VillageDashboardPolicy.assess(input);
        require(first.equals(second)
                        && first.bulletin() == VillageDashboardPolicy.BulletinKind.SECURITY,
                "Invalid synchronized values made the dashboard nondeterministic or unsafe");
    }

    private static VillageDashboardPolicy.Snapshot snapshot(
            boolean present,
            VillageProsperityEngine.Lifecycle lifecycle,
            int population,
            int housing,
            double prosperity,
            double safety,
            double food,
            VillageProsperityEngine.ProjectType projectType,
            boolean projectPlanning,
            VillageProsperityEngine.IncidentCause incidentCause,
            int incidentAge,
            boolean simulationEnabled,
            boolean fundAvailable,
            boolean targetedDonationsEnabled,
            boolean projectSponsorshipEnabled) {
        return snapshot(
                present,
                lifecycle,
                population,
                housing,
                prosperity,
                safety,
                food,
                projectType,
                projectPlanning,
                incidentCause,
                incidentAge,
                simulationEnabled,
                true,
                fundAvailable,
                targetedDonationsEnabled,
                projectSponsorshipEnabled);
    }

    private static VillageDashboardPolicy.Snapshot snapshot(
            boolean present,
            VillageProsperityEngine.Lifecycle lifecycle,
            int population,
            int housing,
            double prosperity,
            double safety,
            double food,
            VillageProsperityEngine.ProjectType projectType,
            boolean projectPlanning,
            VillageProsperityEngine.IncidentCause incidentCause,
            int incidentAge,
            boolean simulationEnabled,
            boolean visualProgressionEnabled,
            boolean fundAvailable,
            boolean targetedDonationsEnabled,
            boolean projectSponsorshipEnabled) {
        return new VillageDashboardPolicy.Snapshot(
                present,
                lifecycle,
                population,
                housing,
                2,
                prosperity,
                safety,
                food,
                250.0,
                75.0,
                projectType,
                42.0,
                projectPlanning,
                projectType == null ? 0 : 1,
                12.0,
                incidentCause,
                incidentAge,
                4.0,
                2.0,
                1.5,
                simulationEnabled,
                visualProgressionEnabled,
                fundAvailable,
                targetedDonationsEnabled,
                projectSponsorshipEnabled);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
