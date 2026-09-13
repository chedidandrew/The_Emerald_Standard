package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import com.chedidandrew.emeraldstandard.client.NewsReader;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.WrittenBookContent;

/** Four unreachable documents carry one coherent, bounded, privacy-filtered full archive. */
public final class NewspaperMenu extends AbstractContainerMenu {
    public static MenuType<NewspaperMenu> TYPE;
    public static final int BATCH=64, DOCUMENTS=4;
    private final SimpleContainer document=new SimpleContainer(DOCUMENTS);
    private final SimpleContainerData metadata=new SimpleContainerData(4);
    private Player readingPlayer;
    private ItemStack readingStack=ItemStack.EMPTY;
    private EconomyService economy;
    private long lastRefresh=Long.MIN_VALUE;
    private int refreshTicks,generation;
    private int cachedPolicy=-1;
    private String cachedGeneration="";
    private List<NewsReader.Entry> cachedReports=List.of(),published=List.of();
    private int publishedPolicy=-1;
    public NewspaperMenu(int id, Inventory inventory) {
        super(TYPE,id);
        for(int i=0;i<DOCUMENTS;i++)addSlot(new Slot(document,i,-10000,-10000) {
            @Override public boolean mayPickup(Player p) { return false; }
            @Override public boolean mayPlace(ItemStack s) { return false; }
        });
        addDataSlots(metadata);
    }
    public NewspaperMenu(int id,Inventory inventory,EconomyService economy) {
        this(id,inventory,economy,false);
    }
    public NewspaperMenu(int id,Inventory inventory,EconomyService economy,boolean browser) {
        this(id,inventory,economy,browser,null);
    }
    public NewspaperMenu(int id,Inventory inventory,EconomyService economy,boolean browser,net.minecraft.world.InteractionHand hand) {
        this(id,inventory);this.economy=economy;metadata.set(2,browser?1:0);
        if(!browser&&hand!=null&&inventory.player!=null) {
            ItemStack held=inventory.player.getItemInHand(hand);
            if(held.getItem() instanceof NewspaperItem) {
                readingStack=held;readingPlayer=inventory.player;metadata.set(3,hand==net.minecraft.world.InteractionHand.MAIN_HAND?1:2);
            }
        }
        refresh();
    }
    public net.minecraft.world.InteractionHand readingHand() {
        return switch(metadata.get(3)) {case 1 -> net.minecraft.world.InteractionHand.MAIN_HAND;
            case 2 -> net.minecraft.world.InteractionHand.OFF_HAND;default -> null;};
    }
    public boolean ownsReadingUse(Player player,ItemStack stack) {
        var hand=readingHand();
        return !browserMode()&&hand!=null&&player==readingPlayer&&player.isAlive()
                &&player.containerMenu==this&&!stack.isEmpty()&&stack==readingStack&&player.getItemInHand(hand)==stack;
    }
    public void beginReading(Player player) {
        if(ownsReadingUse(player,readingStack)&&!player.isUsingItem())player.startUsingItem(readingHand());
    }
    private void endReading(Player player) {
        if(player!=null&&player.isUsingItem()&&player.getUseItem()==readingStack)player.stopUsingItem();
        readingStack=ItemStack.EMPTY;readingPlayer=null;metadata.set(3,0);
    }
    @Override public void removed(Player player) {
        endReading(player);super.removed(player);
    }
    public List<NewsReader.Entry> reports() {
        if(cachedPolicy!=privacyFlags()) { cachedReports=List.of();cachedGeneration="";cachedPolicy=privacyFlags(); }
        List<WrittenBookContent> books=new ArrayList<>();
        String prefix=null;
        for(int i=0;i<DOCUMENTS;i++) {
            var book=document.getItem(i).get(DataComponents.WRITTEN_BOOK_CONTENT);
            if(book==null)return cachedReports;
            String title=book.title().raw();
            String[] parts=title.split(":");
            if(parts.length!=4||!parts[0].equals("EW")||!parts[2].equals(""+privacyFlags())||!parts[3].equals(""+i))
                return cachedReports;
            String version=parts[1]+":"+parts[2];
            if(prefix!=null&&!prefix.equals(version))return cachedReports;
            prefix=version;books.add(book);
        }
        if(!Objects.equals(prefix,cachedGeneration)) {
            List<NewsReader.Entry> entries=new ArrayList<>();
            try {
                for(var book:books) {
                    var pages=book.getPages(false);if(pages.size()>BATCH)return cachedReports;
                    for(var page:pages)entries.add(NewsReader.Entry.parse(page.getString()));
                }
                if(entries.size()!=total()||entries.stream().map(NewsReader.Entry::id).distinct().count()!=entries.size())return cachedReports;
                cachedReports=List.copyOf(entries);cachedGeneration=prefix;
            } catch(IllegalArgumentException invalid) { return cachedReports; }
        }
        return cachedReports;
    }
    public List<String> articles() { return reports().stream().map(NewsReader.Entry::text).toList(); }
    public boolean browserMode() { return metadata.get(2)==1; }
    public int offset() { return 0; }
    public int privacyFlags() { return metadata.get(0); }
    public boolean completeEdition() { reports();return !cachedGeneration.isEmpty(); }
    public int total() { return metadata.get(1); }
    private static int importance(NewsWire.Article a) {
        int base=switch(a.kind()) {
            case VIOLENCE -> 85;
            case DAMAGE -> 65;
            case FOOD_REMOVED, CROPS -> 55;
            case MARKET -> a.family().equals("CREEPER_CATASTROPHE")||a.family().equals("VILLAGER_CREDIT_SCARE")?85:70;
            case FOLLOW_UP -> 50;
            case DONATION, REPLANTED, FOOD_RETURNED -> 40;
            case ROUNDUP -> 20;
        };
        return Math.min(100,base+(int)Math.min(15,Math.log1p(a.quantity())*2));
    }
    private void refresh() {
        if(economy==null)return;
        var policy=EmeraldConfig.current().newsPolicy();
        List<NewsReader.Entry> entries=new ArrayList<>();
        var all=economy.newspaper();
        for(int i=all.size()-1;i>=0;i--) {
            var a=all.get(i);String visible=policy.text(a);
            if(visible!=null)entries.add(new NewsReader.Entry(a.id(),NewsEditorial.section(a),visible,a.day(),importance(a)));
        }
        if(entries.equals(published)&&publishedPolicy==policy.flags())return;
        published=List.copyOf(entries);publishedPolicy=policy.flags();generation++;
        for(int i=0;i<DOCUMENTS;i++) {
            List<Filterable<Component>> pages=new ArrayList<>();
            for(int j=i*BATCH;j<Math.min(entries.size(),(i+1)*BATCH);j++)
                pages.add(Filterable.passThrough(Component.literal(entries.get(j).wire())));
            ItemStack stack=new ItemStack(Items.WRITTEN_BOOK);
            stack.set(DataComponents.WRITTEN_BOOK_CONTENT,new WrittenBookContent(
                    Filterable.passThrough("EW:"+generation+":"+policy.flags()+":"+i),"Village Press",0,pages,true));
            document.setItem(i,stack);
        }
        metadata.set(0,policy.flags());metadata.set(1,entries.size());
    }
    @Override public void broadcastChanges() {
        if(readingPlayer!=null) {
            if(!ownsReadingUse(readingPlayer,readingStack))endReading(readingPlayer);
            else beginReading(readingPlayer);
        }
        if(economy!=null&&(++refreshTicks>=100||publishedPolicy!=EmeraldConfig.current().newsPolicy().flags())) {
            refreshTicks=0;refresh();
        }
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player p,int id) {
        if(id!=1&&id!=2)return false;
        if(p.level().isClientSide())return true;
        if(p.containerMenu!=this||!(p instanceof ServerPlayer)||!stillValid(p))return false;
        long tick=p.level().getGameTime();
        if(lastRefresh!=Long.MIN_VALUE&&tick-lastRefresh<10)return false;
        lastRefresh=tick;refresh();super.broadcastChanges();return true;
    }
    @Override public void clicked(int slot,int button,ContainerInput input,Player player) {}
    @Override public ItemStack quickMoveStack(Player p,int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player p) { return p.isAlive(); }
}
