package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.core.NewsEditorial;
import java.util.*;

/** Stable editions: incoming stories wait for explicit acceptance; privacy changes never wait. */
public final class NewsReader {
    public record Entry(long id, NewsEditorial.Section section, String text, long day, int importance) {
        public Entry(long id, NewsEditorial.Section section, String text) { this(id,section,text,0,20); }
        public Entry {
            if(id<1||text.length()>10000||day<0||importance<0||importance>100)throw new IllegalArgumentException("Invalid article");
            Objects.requireNonNull(section);
        }
        public String headline() { var lines=text.split("\n",-1);return lines.length>3?lines[3]:"Report"; }
        public String outlet() { return text.split(" \\|",2)[0]; }
        public String wire() { return id+"|"+section.name()+"|"+day+"|"+importance+"\n"+text; }
        public static Entry parse(String value) {
            int newline=value.indexOf('\n'),bar=value.indexOf('|');
            if(newline<0||bar<0||bar>newline)throw new IllegalArgumentException("Invalid article envelope");
            String[] fields=value.substring(0,newline).split("\\|");
            if(fields.length!=2&&fields.length!=4)throw new IllegalArgumentException("Invalid article envelope");
            return new Entry(Long.parseLong(fields[0]),NewsEditorial.Section.valueOf(fields[1]),value.substring(newline+1),
                    fields.length==4?Long.parseLong(fields[2]):0,fields.length==4?Integer.parseInt(fields[3]):20);
        }
    }
    /** Recent major stories lead; old disasters cannot permanently crowd out new reporting. */
    public static List<Entry> ranked(List<Entry> entries) {
        long latest=entries.stream().mapToLong(Entry::day).max().orElse(0);
        return entries.stream().sorted(Comparator.comparingLong((Entry e)->
                e.importance()-Math.min(100000,latest-e.day())*8).reversed()
                .thenComparing(Comparator.comparingLong(Entry::day).reversed())
                .thenComparing(Comparator.comparingLong(Entry::id).reversed())).toList();
    }
    private List<Entry> edition=List.of(),incoming=List.of();
    private int privacy=-1;
    private final Map<Long,String> read=new HashMap<>();
    public boolean receive(List<Entry> entries,int policy) {
        if(entries.size()>256||entries.stream().map(Entry::id).distinct().count()!=entries.size())
            throw new IllegalArgumentException("Invalid edition");
        incoming=List.copyOf(entries);
        if(privacy!=policy) { privacy=policy;read.clear();accept();return true; }
        return false;
    }
    public void accept() {
        edition=incoming;
        Set<Long> keep=new HashSet<>();for(var e:edition)keep.add(e.id());
        read.keySet().retainAll(keep);
    }
    public boolean hasUpdates() { return !incoming.equals(edition); }
    public List<Entry> edition() { return edition; }
    public void markRead(Entry e) { read.put(e.id(),e.text()); }
    public boolean unread(Entry e) { return !e.text().equals(read.get(e.id())); }
    public int unreadCount(){return (int)edition.stream().filter(this::unread).count();}
}
