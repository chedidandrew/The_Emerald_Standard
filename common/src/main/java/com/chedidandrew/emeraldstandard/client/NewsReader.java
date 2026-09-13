package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.core.NewsEditorial;
import java.util.*;

/** Stable editions: incoming stories wait for explicit acceptance; privacy changes never wait. */
public final class NewsReader {
    public record Entry(long id, NewsEditorial.Section section, String text, long day, int importance, NewsIllustration illustration, String topic, long sourceId, boolean local, boolean feature) {
        public Entry(long id,NewsEditorial.Section section,String text,long day,int importance,NewsIllustration illustration) {
            this(id,section,text,day,importance,illustration,section.name(),0,false,false);
        }
        public Entry(long id, NewsEditorial.Section section, String text, long day, int importance) {
            this(id,section,text,day,importance,section==NewsEditorial.Section.MARKETS?NewsIllustration.MARKETS:NewsIllustration.COMMUNITY);
        }
        public Entry(long id, NewsEditorial.Section section, String text) { this(id,section,text,0,20); }
        public Entry {
            if(topic==null||topic.length()>100||!topic.matches("[A-Za-z0-9:_-]+")||sourceId<0)throw new IllegalArgumentException("Invalid story metadata");
            if(id<1||text.length()>10000||day<0||importance<0||importance>100)throw new IllegalArgumentException("Invalid article");
            Objects.requireNonNull(section);Objects.requireNonNull(illustration);
        }
        public String headline() { var lines=text.split("\n",-1);return lines.length>3?lines[3]:"Report"; }
        public String outlet() { return text.split(" \\|",2)[0]; }
        public String wire() { return id+"|"+section.name()+"|"+day+"|"+importance+"|"+illustration.name()+"|"+topic+"|"+sourceId+"|"+(local?1:0)+"|"+(feature?1:0)+"\n"+text; }
        public static Entry parse(String value) {
            int newline=value.indexOf('\n'),bar=value.indexOf('|');
            if(newline<0||bar<0||bar>newline)throw new IllegalArgumentException("Invalid article envelope");
            String[] fields=value.substring(0,newline).split("\\|");
            if(fields.length!=2&&fields.length!=4&&fields.length!=5&&fields.length!=9)throw new IllegalArgumentException("Invalid article envelope");
            var entry=new Entry(Long.parseLong(fields[0]),NewsEditorial.Section.valueOf(fields[1]),value.substring(newline+1),
                    fields.length>=4?Long.parseLong(fields[2]):0,fields.length>=4?Integer.parseInt(fields[3]):20);
            if(fields.length==9) {
                if(!Set.of("0","1").contains(fields[7])||!Set.of("0","1").contains(fields[8]))throw new IllegalArgumentException("Invalid flags");
                return new Entry(entry.id,entry.section,entry.text,entry.day,entry.importance,NewsIllustration.valueOf(fields[4]),
                    fields[5],Long.parseLong(fields[6]),fields[7].equals("1"),fields[8].equals("1"));
            }
            return fields.length==5?new Entry(entry.id,entry.section,entry.text,entry.day,entry.importance,NewsIllustration.valueOf(fields[4])):entry;
        }
    }
    /** Recent major stories lead; old disasters cannot permanently crowd out new reporting. */
    public static List<Entry> ranked(List<Entry> entries) {
        long latest=entries.stream().mapToLong(Entry::day).max().orElse(0);
        var remaining=new ArrayList<>(entries);var ordered=new ArrayList<Entry>();
        Map<String,Integer> topics=new HashMap<>(),outlets=new HashMap<>();
        Map<NewsEditorial.Section,Integer> sections=new EnumMap<>(NewsEditorial.Section.class);
        while(!remaining.isEmpty()) {
            // Reserve a modest fresh light feature after the lead and three briefs, not as the lead.
            boolean light=ordered.size()==4&&remaining.stream().anyMatch(e->e.feature()&&latest-e.day()<=4);
            boolean freshNews=ordered.size()<4&&remaining.stream().anyMatch(e->!e.feature()&&latest-e.day()<=4);
            Entry best=null;long score=Long.MIN_VALUE;
            for(var e:remaining) {
                long value=e.importance()+(e.local()?22:0)-Math.min(100000,latest-e.day())*8;
                if(ordered.size()<6) {
                    value-=topics.getOrDefault(e.topic(),0)*55+sections.getOrDefault(e.section(),0)*14
                        +outlets.getOrDefault(e.outlet(),0)*6;
                    if(e.feature()&&freshNews)value-=1000;
                    if(light&&(!e.feature()||latest-e.day()>4))value-=10000;
                }
                if(best==null||value>score||value==score&&(e.day()>best.day()||e.day()==best.day()&&e.id()>best.id())) {
                    best=e;score=value;
                }
            }
            ordered.add(best);remaining.remove(best);
            topics.merge(best.topic(),1,Integer::sum);sections.merge(best.section(),1,Integer::sum);outlets.merge(best.outlet(),1,Integer::sum);
        }
        return List.copyOf(ordered);
    }
    /** The notebook is separate from the story, but remains fully searchable and transportable. */
    public static String storyText(Entry e) {return e.text().split("\n\nFrom the notebook\n",2)[0];}
    public static String facts(Entry e) {
        String[] parts=e.text().split("\n\nFrom the notebook\n",2);
        return parts.length==2?parts[1]:"";
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
