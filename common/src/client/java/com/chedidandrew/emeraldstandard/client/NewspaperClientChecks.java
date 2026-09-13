package com.chedidandrew.emeraldstandard.client;
import com.chedidandrew.emeraldstandard.core.*;
import com.chedidandrew.emeraldstandard.minecraft.NewspaperMenu;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.WrittenBookContent;

final class NewspaperClientChecks {
    private static NewspaperScreen preview;
    static void showPart(Minecraft game,String part) {
        if(part.equals("item-states")) {showItemStates(game);return;}
        if(part.equals("contents"))ReaderClientChecks.press(preview,"Contents");
        else if(part.equals("article"))preview.openFirstForTesting();
        else if(part.equals("browser")) {
            preview.getMenu().setData(2,1);
            preview.init(game.getWindow().getGuiScaledWidth(),game.getWindow().getGuiScaledHeight());
            ReaderClientChecks.press(preview,"Front page");
        }
    }
    private static void showItemStates(Minecraft game) {
        var rolled=NewspaperScreen.createOpenPaperIcon();
        rolled.set(DataComponents.ITEM_MODEL,com.chedidandrew.emeraldstandard.minecraft.NewspaperItem.ID);
        var open=NewspaperScreen.createOpenPaperIcon();
        check(!net.minecraft.world.item.ItemStack.isSameItemSameComponents(rolled,open),"different display-only model references");
        var rolledModel=NewspaperModelPreview.model(game,rolled,ItemDisplayContext.GUI);
        var openModel=NewspaperModelPreview.model(game,open,ItemDisplayContext.GUI);
        game.gui.setScreen(new net.minecraft.client.gui.screens.Screen(Component.literal("Newspaper item states")) {
            @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,float delta) {
                g.fill(0,0,width,height,0xFF20372E);
                g.centeredText(font,Component.literal("NATIVE MODEL RENDER"),width/2,16,0xFFF0EBD9);
                int size=Math.min(width/2-24,height-120);
                for(int i=0;i<2;i++) {
                    var stack=i==0?rolled:open;int cx=width*(i==0?1:3)/4;
                    g.centeredText(font,Component.literal(i==0?"Closed / rolled":"Reading / pocket gazette"),cx,42,0xFFF0EBD9);
                    NewspaperModelPreview.draw(g,i==0?rolledModel:openModel,cx-size/2,60,size);
                    int slotY=height-48;
                    g.fill(cx-10,slotY-2,cx+10,slotY+18,0xFF898989);
                    g.outline(cx-10,slotY-2,20,20,0xFFC7C7C7);
                    g.item(stack,cx-8,slotY);
                }
                g.centeredText(font,Component.literal("Direct model above; actual inventory size below."),width/2,height-16,0xFFF0EBD9);
            }
        });
    }
    static void verify(Minecraft game) {
        var inventory=new Inventory(null,new EntityEquipment());
        var menu=new NewspaperMenu(1,inventory);
        var state=EconomyState.fresh(112,0,0);
        for(int i=0;i<7;i++)state.advanceOneDay();
        var village=UUID.randomUUID();state.village(village);
        NewsWire.player(state,NewsWire.Kind.FOOD_REMOVED,village,UUID.randomUUID(),"CarrotBandit",32);
        List<NewsReader.Entry> reports=new ArrayList<>();
        for(int i=state.news.size()-1;i>=0;i--) {
            var a=state.news.get(i);
            reports.add(new NewsReader.Entry(a.id(),NewsEditorial.section(a),a.text(),a.day(),a.kind()==NewsWire.Kind.FOOD_REMOVED?85:20));
        }
        publish(menu,reports,1);menu.setData(2,1);
        var screen=new NewspaperScreen(menu,inventory,Component.literal("The Emerald Wire"));game.gui.setScreen(screen);
        check(screen.matchCountForTesting()==reports.size(),"reader articles");
        ReaderClientChecks.press(screen,"Next");ReaderClientChecks.press(screen,"Previous");
        var search=screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
        search.setValue("CarrotBandit");check(screen.matchCountForTesting()==1,"search actual article");
        search.setValue("missing-news-zz");check(screen.matchCountForTesting()==0,"empty search");
        search.setValue("");screen.setFocused(null);
        screen.openFirstForTesting();
        for(int i=0;i<100;i++)screen.mouseScrolled(50,100,0,-1);
        check(screen.scrollForTesting()<screen.lineCountForTesting(),"scroll bounded");
        for(int i=0;i<100;i++)screen.mouseScrolled(50,100,0,1);
        check(screen.scrollForTesting()==0,"scroll returns to first line");
        for(int i=0;i<6;i++) {
            String label=i==0?"All outlets":NewsWire.OUTLETS.get(i-1);
            ReaderClientChecks.press(screen,label);
        }
        check(screen.matchCountForTesting()==reports.size(),"outlet cycle returns to all");
        // Incoming editions wait until accepted, and search reaches beyond the former 64-item batch.
        List<NewsReader.Entry> expanded=new ArrayList<>(reports);
        for(int i=0;i<100;i++)expanded.add(new NewsReader.Entry(10000+i,NewsEditorial.Section.MARKETS,
                "The Emerald Ledger | Day 1\\nWorld-market dispatch\\n\\nArchive fixture "+i+"\\n\\nFar archive needle "+i));
        publish(menu,expanded,2);
        screen.init(game.getWindow().getGuiScaledWidth(),game.getWindow().getGuiScaledHeight());
        check(screen.matchCountForTesting()==reports.size(),"new edition replaced reading snapshot");
        screen.acceptUpdatesForTesting();
        check(screen.matchCountForTesting()==expanded.size(),"full edition acceptance");
        screen.queryForTesting("Far archive needle 99");
        check(screen.matchCountForTesting()==1,"search entire archive");
        screen.queryForTesting("");
        ReaderClientChecks.press(screen,"Front page");
        publish(menu,reports,3);menu.setData(2,0);screen.init(game.getWindow().getGuiScaledWidth(),game.getWindow().getGuiScaledHeight());
        screen.acceptUpdatesForTesting();
        check(screen.children().stream().noneMatch(EditBox.class::isInstance),"portable paper still has channel filters");
        ReaderClientChecks.press(screen,"Contents");
        ReaderClientChecks.press(screen,"Next");ReaderClientChecks.press(screen,"Previous");
        screen.openFirstForTesting();
        ReaderClientChecks.press(screen,"Next");ReaderClientChecks.press(screen,"Previous");
        for(int i=0;i<100;i++)screen.mouseScrolled(50,100,0,-1);
        check(screen.scrollForTesting()<screen.lineCountForTesting(),"paper scroll bounded");
        ReaderClientChecks.press(screen,"Front page");
        continuousReading(game,screen,menu);
        publish(menu,reports,9);screen.init(game.getWindow().getGuiScaledWidth(),game.getWindow().getGuiScaledHeight());
        screen.acceptUpdatesForTesting();ReaderClientChecks.press(screen,"Front page");
        preview=screen;
        // A title-screen fixture has no LocalPlayer. Render the actual initialized container
        // screen through a tick-free preview; vanilla's final container tick requires a world.
        game.gui.setScreen(new net.minecraft.client.gui.screens.Screen(Component.literal("News preview")) {
            @Override protected void init() { screen.init(width,height); }
            @Override public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,float delta) {
                screen.extractBackground(g,x,y,delta);
            }
            @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,float delta) {
                screen.extractRenderState(g,x,y,delta);
            }
        });
    }
    private static void continuousReading(Minecraft game,NewspaperScreen screen,NewspaperMenu menu) {
        var fixture=new ArrayList<NewsReader.Entry>();
        for(int i=0;i<19;i++)fixture.add(new NewsReader.Entry(40001+i,NewsEditorial.Section.MARKETS,
                "The Emerald Ledger | Day 10\nWorld-market dispatch\n\nReading fixture "+(i+1)+"\n\n"
                +(i==0?"The villagers debate prices while the printing press keeps working. ".repeat(100):"A short, complete report."),
                10,100-i));
        publish(menu,fixture,4);screen.acceptUpdatesForTesting();ReaderClientChecks.press(screen,"Front page");
        check(!screen.previousActiveForTesting(),"cover previous must stop");
        ReaderClientChecks.press(screen,"Next");
        check(screen.paperSectionForTesting()==-1&&screen.contentsPageForTesting()==0,"cover must reach contents");
        int pages=screen.contentsPagesForTesting();
        check(pages>1,"fixture must overflow contents");
        for(int i=1;i<pages;i++){
            ReaderClientChecks.press(screen,"Next");
            check(screen.paperSectionForTesting()==-1&&screen.contentsPageForTesting()==i,"contents sheet skipped");
        }
        ReaderClientChecks.press(screen,"Next");
        check(screen.paperSectionForTesting()==0&&screen.scrollForTesting()==0,"last contents must reach first story");
        long first=screen.selectedForTesting();
        ReaderClientChecks.press(screen,"Next");
        check(screen.selectedForTesting()==first&&screen.scrollForTesting()>0,"Next skipped unread long article text");
        int guard=0;
        while(screen.selectedForTesting()==first){ReaderClientChecks.press(screen,"Next");check(++guard<200,"article page loop");}
        check(screen.paperSectionForTesting()==1,"last article page must reach next story");
        ReaderClientChecks.press(screen,"Previous");
        check(screen.selectedForTesting()==first&&screen.scrollForTesting()==screen.maximumScrollForTesting(),"reverse must land at previous story end");
        screen.mouseScrolled(50,100,0,-1);
        check(screen.paperSectionForTesting()==1&&screen.scrollForTesting()==0,"wheel must leave article end");
        screen.mouseScrolled(50,100,0,1);
        check(screen.paperSectionForTesting()==0&&screen.scrollForTesting()==screen.maximumScrollForTesting(),"wheel reverse boundary");
        int offset=screen.scrollForTesting();
        screen.mouseScrolled(50,100,1,0);
        check(screen.scrollForTesting()==offset&&screen.selectedForTesting()==first,"horizontal wheel changed story");
        screen.init(game.getWindow().getGuiScaledWidth(),game.getWindow().getGuiScaledHeight());
        check(screen.selectedForTesting()==first&&screen.scrollForTesting()<=screen.maximumScrollForTesting(),"resize lost/clipped reading position");

        ReaderClientChecks.press(screen,"Front page");
        var seen=new HashSet<Integer>();guard=0;
        while(screen.nextActiveForTesting()){
            screen.mouseScrolled(50,100,0,-1);seen.add(screen.paperSectionForTesting());
            check(++guard<3000,"forward wheel trapped");
        }
        check(seen.size()==20&&screen.paperSectionForTesting()==18,"wheel failed to traverse all contents and stories");
        long last=screen.selectedForTesting();screen.mouseScrolled(50,100,0,-1);
        check(screen.selectedForTesting()==last,"end wrapped unexpectedly");
        guard=0;
        while(screen.previousActiveForTesting()){screen.mouseScrolled(50,100,0,1);check(++guard<3000,"reverse wheel trapped");}
        check(screen.paperSectionForTesting()==-2,"reverse did not reach front");

        screen.openFirstForTesting();ReaderClientChecks.press(screen,"Next");
        var replacement=List.of(fixture.getFirst());
        publish(menu,replacement,5);screen.init(game.getWindow().getGuiScaledWidth(),game.getWindow().getGuiScaledHeight());
        check(screen.matchCountForTesting()==19,"incoming issue replaced current issue");
        screen.acceptUpdatesForTesting();check(screen.selectedForTesting()==first&&screen.matchCountForTesting()==1,"accept lost retained story");
        menu.setData(0,0);screen.init(game.getWindow().getGuiScaledWidth(),game.getWindow().getGuiScaledHeight());
        check(screen.matchCountForTesting()==0&&screen.selectedForTesting()==0,"privacy retained stale story");
        ReaderClientChecks.press(screen,"Contents");check(!screen.nextActiveForTesting(),"empty contents loops");
        System.out.println("PASS newspaper native continuous contents/story/text navigation, reverse/end/empty bounds, resize and edition/privacy refresh");
    }
    private static void publish(NewspaperMenu menu,List<NewsReader.Entry> reports,int generation) {
        for(int batch=0;batch<4;batch++) {
            List<Filterable<Component>> pages=new ArrayList<>();
            for(int i=batch*64;i<Math.min(reports.size(),(batch+1)*64);i++)
                pages.add(Filterable.passThrough(Component.literal(reports.get(i).wire())));
            var doc=HandbookRecipes.previewStack(Items.WRITTEN_BOOK,1);
            doc.set(DataComponents.WRITTEN_BOOK_CONTENT,new WrittenBookContent(
                    Filterable.passThrough("EW:"+generation+":1:"+batch),"Village Press",0,pages,true));
            menu.slots.get(batch).set(doc);
        }
        menu.setData(0,1);menu.setData(1,reports.size());
    }
    private static void check(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
}
