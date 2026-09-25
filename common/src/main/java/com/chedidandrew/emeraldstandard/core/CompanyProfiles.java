package com.chedidandrew.emeraldstandard.core;

/** Operating profiles, not promises of return or labels inferred from one share's price. */
public final class CompanyProfiles {
    public enum Size { LARGE, MEDIUM, SMALL }
    public record Profile(Size size, String business, String outlook) {
        public double startingCapital() { return switch(size) {case LARGE->10000;case MEDIUM->5000;case SMALL->1500;}; }
    }
    public static Profile get(String ticker) {
        return switch(ticker) {
            case "GLDH", "ENDR", "IRNG" -> new Profile(Size.LARGE,"Established network",
                    "Broad operations; still exposed to widespread setbacks.");
            case "RSDN", "DPMN", "NSPC", "MCRT", "BRCK" -> new Profile(Size.MEDIUM,"Regional business",
                    "Expansion opportunities, with meaningful operating risks.");
            case "AURM" -> new Profile(Size.MEDIUM,"Defensive reserve business",
                    "May cushion difficult markets, but can lag during prosperity.");
            case "FISH" -> new Profile(Size.SMALL,"Established fishery",
                    "Seasonal catches matter; small does not mean speculative.");
            case "POTN" -> new Profile(Size.SMALL,"Specialist laboratory",
                    "New products offer opportunity; recalls can hurt substantially.");
            case "VENT" -> new Profile(Size.SMALL,"Speculative expeditions",
                    "Exceptional discoveries or lasting losses; patience is no guarantee.");
            default -> null;
        };
    }
    private CompanyProfiles() {}
}
