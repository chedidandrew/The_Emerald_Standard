package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.core.NewsWire;
import java.util.Locale;

/** Closed texture allowlist; the server never sends paths, image bytes, or private village data. */
public enum NewsIllustration {
    MARKETS("The counting house"),
    TRADE("Freight routes"),
    COMMUNITY("Village life"),
    MEMORIAL("In remembrance");

    private final String caption;
    NewsIllustration(String caption){this.caption=caption;}
    public String caption(){return caption;}
    public String texture(){return "textures/gui/news/"+name().toLowerCase(Locale.ROOT)+".png";}
    public static NewsIllustration forArticle(NewsWire.Article a) {
        if(a.kind()==NewsWire.Kind.VIOLENCE||a.family().contains("CREEPER"))return MEMORIAL;
        if(a.kind()==NewsWire.Kind.FEATURE) {
            if(a.outlet().equals(NewsWire.OUTLETS.get(2)))return TRADE;
            if(a.outlet().equals(NewsWire.OUTLETS.get(3)))return COMMUNITY;
        }
        if(!a.village().isEmpty())return COMMUNITY;
        if(a.family().contains("NETHER")||a.family().contains("PORTAL")||a.family().contains("RAIL")||a.family().contains("END_"))return TRADE;
        if(a.family().contains("HARVEST")||a.family().contains("REBUILD")||a.family().contains("FISH"))return COMMUNITY;
        return MARKETS;
    }
}
