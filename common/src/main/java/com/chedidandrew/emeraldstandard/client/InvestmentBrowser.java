package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.core.EconomyEngine;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.IntPredicate;

/** Local navigation preferences only. Never places orders or changes server selection. */
public final class InvestmentBrowser {
    public enum Filter { ALL, STOCK, COMMODITY, INDEX, FUND }
    public String query = "";
    public Filter filter = Filter.ALL;
    public boolean favoritesOnly, holdingsOnly;
    public int page, comparison = -1;
    public final Set<String> favorites = new HashSet<>();
    public List<Integer> matches(IntPredicate held) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < EconomyEngine.ASSETS.size(); i++) {
            var a = EconomyEngine.ASSETS.get(i);
            if (filter != Filter.ALL && !filter.name().equals(a.type().name())) continue;
            if (favoritesOnly && !favorites.contains(a.ticker())) continue;
            if (holdingsOnly && !held.test(i)) continue;
            if (!(a.ticker()+" "+a.name()+" "+a.sector()+" "+a.type()).toLowerCase(Locale.ROOT).contains(q)) continue;
            result.add(i);
        }
        return List.copyOf(result);
    }
    public void toggle(int index) {
        String ticker = EconomyEngine.ASSETS.get(index).ticker();
        if (!favorites.remove(ticker)) favorites.add(ticker);
    }
    public void load(Path path) throws IOException {
        favorites.clear();
        if (!Files.exists(path)) return;
        if (Files.size(path) > 16_384) throw new IOException("Watchlist exceeds 16 KiB");
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(path)) { p.load(r); }
        catch (IllegalArgumentException malformed) { throw new IOException("Malformed watchlist", malformed); }
        Set<String> known = new HashSet<>();
        for (var a : EconomyEngine.ASSETS) known.add(a.ticker());
        for (String ticker : p.getProperty("favorites", "").split(",")) if (known.contains(ticker)) favorites.add(ticker);
    }
    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Properties p = new Properties();
        p.setProperty("favorites", String.join(",", new TreeSet<>(favorites)));
        Path temp = Files.createTempFile(path.getParent(), "tes-watchlist-", ".tmp");
        try {
            try (Writer w = Files.newBufferedWriter(temp)) { p.store(w, "Local watchlist; no automatic orders"); }
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
