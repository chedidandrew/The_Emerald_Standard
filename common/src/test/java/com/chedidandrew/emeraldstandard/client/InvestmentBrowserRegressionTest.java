package com.chedidandrew.emeraldstandard.client;
import com.chedidandrew.emeraldstandard.core.EconomyEngine;
import java.nio.file.Files;
public final class InvestmentBrowserRegressionTest {
    public static void main(String[] args) throws Exception {
        var b=new InvestmentBrowser();
        check(b.matches(i->false).size()==EconomyEngine.ASSETS.size(),"full catalog");
        for(var filter:InvestmentBrowser.Filter.values()) {
            b.filter=filter;
            for(int index:b.matches(i->true))
                check(filter==InvestmentBrowser.Filter.ALL || EconomyEngine.ASSETS.get(index).type().name().equals(filter.name()),"type");
        }
        b.filter=InvestmentBrowser.Filter.ALL;b.query=" GoLd ";
        check(b.matches(i->true).stream().anyMatch(i->EconomyEngine.ASSETS.get(i).name().contains("Gold")),"case-insensitive search");
        b.query="";b.holdingsOnly=true;check(b.matches(i->i==2).equals(java.util.List.of(2)),"holdings");
        b.holdingsOnly=false;b.toggle(1);b.favoritesOnly=true;
        check(b.matches(i->true).equals(java.util.List.of(1)),"favorite");
        var dir=Files.createTempDirectory("tes-browser-test-");var path=dir.resolve("watchlist.properties");
        try {
            b.save(path);var restored=new InvestmentBrowser();restored.load(path);
            check(restored.favorites.equals(b.favorites),"restart");
            Files.writeString(path,"favorites=UNKNOWN,"+EconomyEngine.ASSETS.get(1).ticker());
            restored.load(path);check(restored.favorites.equals(b.favorites),"unknown tickers ignored");
            Files.writeString(path,"favorites=\\uZZZZ");
            try { restored.load(path);throw new AssertionError("malformed accepted"); } catch(java.io.IOException expected) {}
            Files.writeString(path,"a".repeat(16385));
            try { restored.load(path);throw new AssertionError("oversized accepted"); } catch(java.io.IOException expected) {}
        } finally {Files.deleteIfExists(path);Files.deleteIfExists(dir);}
        System.out.println("PASS InvestmentBrowserRegressionTest");
    }
    static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);}
}
