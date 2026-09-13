package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.core.*;
import com.chedidandrew.emeraldstandard.minecraft.NewspaperMenu;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/** Newspaper front page and stable article reader; no banking or trading actions. */
public final class NewspaperScreen extends AbstractContainerScreen<NewspaperMenu> {
    private final NewsReader reader=new NewsReader();
    private net.minecraft.world.item.ItemStack readingVisual=net.minecraft.world.item.ItemStack.EMPTY;
    private final net.minecraft.world.item.ItemStack openPaperIcon=createOpenPaperIcon();
    static net.minecraft.world.item.ItemStack createOpenPaperIcon() {
        var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                com.chedidandrew.emeraldstandard.minecraft.NewspaperItem.ID);
        var icon=HandbookRecipes.previewStack(item,1);
        // A display-only copy: never attached to the player's inventory or sent to the server.
        icon.set(net.minecraft.core.component.DataComponents.ITEM_MODEL,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("the_emerald_standard","newspaper_open"));
        return icon;
    }
    @Override protected void containerTick() {
        super.containerTick();
        var player=minecraft.player;var hand=menu.readingHand();
        if(player==null)return;
        if(menu.browserMode()||hand==null) {stopReadingVisual();return;}
        var held=player.getItemInHand(hand);
        if(!(held.getItem() instanceof com.chedidandrew.emeraldstandard.minecraft.NewspaperItem)) {
            stopReadingVisual();return;
        }
        if(readingVisual!=held) {stopReadingVisual();readingVisual=held;}
        if(!player.isUsingItem())player.startUsingItem(hand);
    }
    private void stopReadingVisual() {
        if(minecraft.player!=null&&minecraft.player.isUsingItem()&&minecraft.player.getUseItem()==readingVisual)
            minecraft.player.stopUsingItem();
        readingVisual=net.minecraft.world.item.ItemStack.EMPTY;
    }
    @Override public void removed() {stopReadingVisual();super.removed();}
    private int scroll,outlet,section,frontPage,panelWidth,panelHeight;
    private long selected;
    private String query="";
    private boolean restoreSearchFocus;
    private boolean waitingForEdition=true;
    private List<NewsReader.Entry> matches=List.of(),previous=List.of();
    private List<FormattedCharSequence> lines=List.of();
    private int previousPolicy=-1;
    private Button updates,paperPrevious,paperNext;
    private List<NewspaperPaper.Mark> paperMarks=List.of();
    private boolean contents,previousBrowser;
    private final List<Button> paperLinks=new ArrayList<>();
    private int columnWidth() { return Math.max(60,(panelWidth-54)/2); }
    public NewspaperScreen(NewspaperMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title,600,380);titleLabelY=inventoryLabelY=-10000;
    }
    @Override protected void init() {
        panelWidth=Math.min(740,width-16);panelHeight=height-16;super.init();
        leftPos=(width-panelWidth)/2;topPos=(height-panelHeight)/2;
        paperMarks=NewspaperPaper.marks(panelWidth,panelHeight);
        receive();previousBrowser=menu.browserMode();paperLinks.clear();
        if(!menu.browserMode()){initPaper();return;}
        int x=leftPos+10,y=topPos+30,w=panelWidth-20;
        EditBox search=new EditBox(font,x,y,Math.max(60,w/2-4),20,Component.literal("Search entire archive"));
        search.setMaxLength(80);search.setValue(query);search.setHint(Component.literal("Search all reports..."));
        search.setResponder(s->{query=s;selected=0;frontPage=scroll=0;restoreSearchFocus=true;reflow();rebuildWidgets();});
        addRenderableWidget(search);
        if(restoreSearchFocus){setInitialFocus(search);restoreSearchFocus=false;}
        button(outlet==0?"All outlets":NewsWire.OUTLETS.get(outlet-1),x+w/2+4,y,w/2-4,()->{
            outlet=(outlet+1)%6;selected=0;frontPage=scroll=0;reflow();rebuildWidgets();
        });
        String[] tabs={"All","Markets","Local","Players","Community"};
        int tw=(w-16)/5;
        for(int i=0;i<5;i++) {
            final int index=i;
            var b=button(tabs[i],x+i*(tw+4),topPos+56,tw,()->{
                section=index;selected=0;frontPage=scroll=0;reflow();rebuildWidgets();
            });
            b.active=section!=i;
        }
        reflow();
        if(selected==0) {
            int count=frontCount();frontPage=Math.max(0,Math.min(frontPage,Math.max(0,(matches.size()-1)/count)));
            for(int i=0;i<count&&frontPage*count+i<matches.size();i++) {
                var entry=matches.get(frontPage*count+i);
                button((reader.unread(entry)?"* ":"")+entry.headline(),x,topPos+114+i*24,w,()->open(entry));
            }
        }
        int bw=(w-24)/5,bottom=topPos+panelHeight-28;
        button("Front page",x,bottom,bw,()->{selected=0;scroll=0;rebuildWidgets();});
        button("Previous",x+bw+6,bottom,bw,()->navigate(-1));
        button("Next",x+2*(bw+6),bottom,bw,()->navigate(1));
        updates=button("Up to date",x+3*(bw+6),bottom,bw,()->{
            reader.accept();reflow();rebuildWidgets();
        });
        button("Done",x+4*(bw+6),bottom,bw,this::onClose);
    }
    private Button link(String label,int x,int y,int w,Runnable action) {
        Button b=button(label,x,y,w,action);b.setAlpha(0);paperLinks.add(b);return b;
    }
    private void initPaper() {
        reflow();
        int x=leftPos+18,w=panelWidth-36,bottom=topPos+panelHeight-28,bw=(w-24)/5;
        if(selected==0&&contents) {
            int count=contentsCount();
            frontPage=Math.max(0,Math.min(frontPage,Math.max(0,(matches.size()-1)/count)));
            for(int i=0;i<count&&frontPage*count+i<matches.size();i++) {
                int index=frontPage*count+i;var entry=matches.get(index);
                link("Story "+(index+1)+": "+entry.headline(),x,topPos+91+i*24,w,()->open(entry));
            }
        } else if(selected==0&&!matches.isEmpty()) {
            var lead=matches.getFirst();int col=columnWidth();
            link("Read lead - Story 1",x,topPos+panelHeight-69,col,()->open(lead));
            int count=Math.max(1,(panelHeight-149)/44);
            for(int i=1;i<matches.size()&&i<=count;i++) {
                var e=matches.get(i);
                link("Story "+(i+1)+": "+e.headline(),x+col+18,topPos+100+(i-1)*44,col,()->open(e)).setHeight(28);
            }
        }
        link("Front page",x,bottom,bw,()->{selected=0;contents=false;scroll=0;rebuildWidgets();});
        link("Contents",x+bw+6,bottom,bw,()->{selected=0;contents=true;frontPage=0;rebuildWidgets();});
        paperPrevious=link("Previous",x+2*(bw+6),bottom,bw,()->navigate(-1));
        paperNext=link("Next",x+3*(bw+6),bottom,bw,()->navigate(1));
        paperPrevious.active=canTurn(-1);paperNext.active=canTurn(1);
        link("Done",x+4*(bw+6),bottom,bw,this::onClose);
        updates=link("Up to date",leftPos+panelWidth-142,topPos+47,122,()->{
            reader.accept();reflow();rebuildWidgets();
        });
    }
    private int contentsCount(){return Math.max(1,(panelHeight-145)/24);}
    private Button button(String label,int x,int y,int w,Runnable action) {
        // Native buttons scroll long labels and preserve the full accessibility/narration text.
        var b=Button.builder(Component.literal(label),unused->action.run()).bounds(x,y,w,20).build();
        b.setTooltip(GuiTooltips.widget(Component.literal(label)));return addRenderableWidget(b);
    }
    private void open(NewsReader.Entry entry) {contents=false;selected=entry.id();reader.markRead(entry);scroll=0;rebuildWidgets();}
    private void navigate(int delta) {
        if(!menu.browserMode()){turnPaper(delta,visibleLines()*2);return;}
        if(selected==0){frontPage+=delta;rebuildWidgets();return;}
        int index=0;for(int i=0;i<matches.size();i++)if(matches.get(i).id()==selected)index=i;
        if(!matches.isEmpty())open(matches.get(Math.max(0,Math.min(matches.size()-1,index+delta))));
    }
    private NewspaperPaging.Position paperPosition() {
        return new NewspaperPaging.Position(selected==0?(contents?NewspaperPaging.CONTENTS:NewspaperPaging.COVER):storyIndex(),
                selected==0?(contents?frontPage:0):scroll);
    }
    private NewspaperPaging.Position paperStep(int direction,int amount) {
        return NewspaperPaging.move(paperPosition(),direction,amount,matches.size(),
                Math.max(1,(matches.size()+contentsCount()-1)/contentsCount()),
                Math.max(0,lines.size()-visibleLines()*2));
    }
    private boolean canTurn(int direction){return !paperStep(direction,visibleLines()*2).equals(paperPosition());}
    private void turnPaper(int direction,int amount) {
        var next=paperStep(direction,amount);
        if(next.equals(paperPosition()))return;
        contents=next.story()==NewspaperPaging.CONTENTS;
        if(next.story()<0){selected=0;frontPage=contents?next.offset():0;scroll=0;}
        else {
            var entry=matches.get(next.story());selected=entry.id();scroll=next.offset();reader.markRead(entry);
        }
        rebuildWidgets(); // Reflow clamps a previous story's end marker after native font wrapping.
    }
    private boolean receive() {
        var current=menu.reports();int policy=menu.privacyFlags();
        if(current!=previous||policy!=previousPolicy) {
            if(policy!=previousPolicy)waitingForEdition=true;
            previous=current;previousPolicy=policy;
            boolean forced=reader.receive(current,policy);
            if(waitingForEdition&&menu.completeEdition()){reader.accept();waitingForEdition=false;return true;}
            return forced;
        }
        return false;
    }
    private void reflow() {
        String needle=query.toLowerCase(Locale.ROOT);
        matches=menu.browserMode()?reader.edition().stream().filter(a->(outlet==0||a.outlet().equals(NewsWire.OUTLETS.get(outlet-1)))
                &&(section==0||a.section().ordinal()==section-1)&&a.text().toLowerCase(Locale.ROOT).contains(needle)).toList():NewsReader.ranked(reader.edition());
        var chosen=matches.stream().filter(a->a.id()==selected).findFirst().orElse(null);
        if(chosen==null)selected=0;
        String text=chosen==null?"No reports match these filters.":chosen.text();
        List<FormattedCharSequence> wrapped=new ArrayList<>();int paragraph=0;
        for(String line:text.split("\n",-1)) {
            var component=Component.literal(line.isEmpty()?" ":line);
            if(paragraph==0||paragraph==3)component.withStyle(net.minecraft.ChatFormatting.BOLD);
            wrapped.addAll(font.split(component,(menu.browserMode()?Math.max(60,panelWidth-36):columnWidth())));paragraph++;
        }
        lines=wrapped;scroll=Math.max(0,Math.min(scroll,Math.max(0,lines.size()-visibleLines()*(menu.browserMode()?1:2))));
    }
    private int frontCount() {return Math.max(1,(panelHeight-164)/24);}
    private int visibleLines() {return Math.max(1,(panelHeight-(menu.browserMode()?136:126))/12);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(vertical==0)return super.mouseScrolled(x,y,horizontal,vertical);
        if(!menu.browserMode()){turnPaper(-(int)Math.signum(vertical),3);return true;}
        if(selected==0){frontPage-=(int)Math.signum(vertical);rebuildWidgets();}
        else{scroll-=(int)Math.signum(vertical)*3;reflow();}
        return true;
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta) {
        if(!menu.browserMode()){paperBackground(g);return;}
        g.fill(leftPos,topPos,leftPos+panelWidth,topPos+panelHeight,0xFFEEE8D3);
        g.fill(leftPos,topPos,leftPos+panelWidth,topPos+24,0xFF173D2E);
        g.text(font,Component.literal("THE EMERALD WIRE"),leftPos+10,topPos+8,0xFFF2C14E,false);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int x,int y) {}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta) {
        boolean changed=receive();
        if(changed||previousBrowser!=menu.browserMode()){reflow();rebuildWidgets();}
        updates.active=reader.hasUpdates();
        updates.setMessage(Component.literal(font.plainSubstrByWidth(reader.hasUpdates()?"New stories!":"Up to date",Math.max(12,updates.getWidth()-8))));
        updates.setTooltip(GuiTooltips.widget(Component.literal("Refresh stories; keep your reading position.")));
        super.extractRenderState(g,x,y,delta);
        if(!menu.browserMode()){drawPaper(g);return;}
        if(selected==0) {
            g.text(font,Component.literal("Front page | "+reader.unreadCount()+" unread"),leftPos+12,topPos+84,0xFF173D2E,false);
            String quote=reader.edition().stream().filter(e->e.section()==NewsEditorial.Section.MARKETS)
                    .flatMap(e->Arrays.stream(e.text().split("\n"))).filter(l->l.startsWith("VILX:")).findFirst().orElse("Market summary: awaiting a published close.");
            g.text(font,Component.literal(font.plainSubstrByWidth(quote,panelWidth-24)),leftPos+12,topPos+98,0xFF365646,false);
            if(matches.isEmpty())g.text(font,Component.literal("No matching reports yet."),leftPos+12,topPos+116,0xFF365646,false);
        } else {
            g.enableScissor(leftPos+10,topPos+84,leftPos+panelWidth-10,topPos+panelHeight-49);
            for(int i=0;i<visibleLines()&&i+scroll<lines.size();i++)
                g.text(font,lines.get(i+scroll),leftPos+18,topPos+86+i*12,0xFF1B3025,false);
            g.disableScissor();
        }
        String status=selected==0?"Page "+(frontPage+1)+"/"+Math.max(1,(matches.size()+frontCount()-1)/frontCount())
                +" | "+matches.size()+" reports":"Scroll to read | Article #"+selected;
        g.text(font,Component.literal(font.plainSubstrByWidth(status,panelWidth-20)),leftPos+10,topPos+panelHeight-43,0xFF365646,false);
    }

    private void paperBackground(GuiGraphicsExtractor g) {
        g.fill(leftPos,topPos,leftPos+panelWidth,topPos+panelHeight,0xFFB9B4A5);
        g.fill(leftPos+3,topPos+3,leftPos+panelWidth-3,topPos+panelHeight-3,0xFFF0EBD9);
        g.outline(leftPos+7,topPos+7,panelWidth-14,panelHeight-14,0xFF777364);
        for(var mark:paperMarks)g.fill(leftPos+mark.x(),topPos+mark.y(),
                leftPos+mark.x()+mark.width(),topPos+mark.y()+mark.height(),mark.color());
        g.centeredText(font,Component.literal("THE EMERALD WIRE").withStyle(net.minecraft.ChatFormatting.BOLD),
                leftPos+panelWidth/2,topPos+24,0xFF27251F);
        // Keep an unobtrusive open-edition stamp visible while the full reader covers the held item.
        if(panelWidth>=330) {
            g.pose().pushMatrix();g.pose().translate(leftPos+21,topPos+14);g.pose().scale(1.65f,1.65f);
            g.item(openPaperIcon,0,0);g.pose().popMatrix();
        }
        rule(g,topPos+44);rule(g,topPos+70);rule(g,topPos+panelHeight-35);
    }
    private void rule(GuiGraphicsExtractor g,int y) {
        g.fill(leftPos+18,y,leftPos+panelWidth-18,y+1,0xFF5D594D);
    }
    private void ink(GuiGraphicsExtractor g,String text,int x,int y,int w,int color) {
        int available=Math.max(1,w);
        String fitted=font.width(text)<=available?text:font.plainSubstrByWidth(text,Math.max(1,available-font.width("...")))+"...";
        g.text(font,Component.literal(fitted),x,y,color,false);
    }
    private int paragraph(GuiGraphicsExtractor g,String text,int x,int y,int w,int max,boolean bold) {
        var c=Component.literal(text);
        if(bold)c.withStyle(net.minecraft.ChatFormatting.BOLD);
        var wrapped=font.split(c,w);
        int count=Math.min(max,wrapped.size());
        for(int i=0;i<count;i++)g.text(font,wrapped.get(i),x,y+i*12,0xFF27251F,false);
        return count*12;
    }
    private void drawPaper(GuiGraphicsExtractor g) {
        int x=leftPos+18,y=topPos+82,w=panelWidth-36,col=columnWidth();
        long edition=reader.edition().stream().mapToLong(NewsReader.Entry::day).max().orElse(0);
        ink(g,"Edition day "+edition+" | "+reader.unreadCount()+" unread",x,topPos+53,w-132,0xFF59564C);
        if(selected==0&&contents) {
            ink(g,"CONTENTS - ranked by significance and recency",x,y,w,0xFF27251F);
            if(matches.isEmpty())ink(g,"The presses are waiting for their first report.",x,y+28,w,0xFF59564C);
        } else if(selected==0) {
            if(matches.isEmpty())paragraph(g,"The presses are waiting. Reports appear as the economy advances and confirmed village events occur.",
                    x,y,w,6,false);
            else {
                var lead=matches.getFirst();
                ink(g,"LEAD STORY  /  "+lead.section().name(),x,y,col,0xFF59564C);
                int next=y+16+paragraph(g,lead.headline(),x,y+16,col,4,true);
                ink(g,lead.outlet()+" | Day "+lead.day(),x,next+4,col,0xFF59564C);
                String[] parts=lead.text().split("\\n",6);
                String body=parts.length==6?parts[5]:"Open the story to read the full report.";
                paragraph(g,body.replace("\n"," "),x,next+21,col,
                        Math.max(0,(topPos+panelHeight-78-(next+21))/12),false);
                g.fill(x+col+8,y,x+col+9,topPos+panelHeight-76,0xFFB1AA98);
                ink(g,"ALSO IN THIS EDITION",x+col+18,y,col,0xFF59564C);
                int count=Math.max(1,(panelHeight-149)/44);
                for(int i=1;i<matches.size()&&i<=count;i++) {
                    var e=matches.get(i);
                    ink(g,e.outlet()+" / Day "+e.day(),x+col+18,topPos+132+(i-1)*44,col,0xFF777364);
                }
            }
        } else {
            int rows=visibleLines();
            g.enableScissor(x,topPos+80,leftPos+panelWidth-18,topPos+panelHeight-45);
            for(int c=0;c<2;c++)for(int i=0;i<rows;i++) {
                int n=scroll+c*rows+i;
                if(n<lines.size())g.text(font,lines.get(n),x+c*(col+18),topPos+82+i*12,0xFF27251F,false);
            }
            g.fill(x+col+8,topPos+82,x+col+9,topPos+panelHeight-47,0xFFB1AA98);
            g.disableScissor();
        }
        String status=selected!=0?"Story "+(storyIndex()+1)+" / "+matches.size()+" | Text "+(scroll+1)+"-"+Math.min(lines.size(),scroll+visibleLines()*2)+" / "+lines.size()
                :contents?"Contents "+(frontPage+1)+" / "+Math.max(1,(matches.size()+contentsCount()-1)/contentsCount())
                :"Front page | "+matches.size()+" stories";
        ink(g,status,x,topPos+panelHeight-43,w,0xFF59564C);
        for(Button b:paperLinks) {
            if(!b.visible)continue;
            int color=b.active?(b.isHoveredOrFocused()?0xFF73562F:0xFF27251F):0xFF8A8576;
            String label=b.getMessage().getString();
            if(label.equals("Front page"))label="Front";
            if(selected==0&&!contents&&label.startsWith("Story ")) {
                paragraph(g,label,b.getX(),b.getY()+3,b.getWidth(),2,false);
            }else ink(g,label,b.getX(),b.getY()+6,b.getWidth(),color);
            if(b.isHoveredOrFocused()&&b.active)g.fill(b.getX(),b.getY()+17,b.getX()+b.getWidth(),b.getY()+18,color);
        }
    }
    private int storyIndex(){for(int i=0;i<matches.size();i++)if(matches.get(i).id()==selected)return i;return 0;}

    int paperSectionForTesting(){return paperPosition().story();}
    int contentsPageForTesting(){return frontPage;}
    int contentsPagesForTesting(){return Math.max(1,(matches.size()+contentsCount()-1)/contentsCount());}
    int maximumScrollForTesting(){return Math.max(0,lines.size()-visibleLines()*2);}
    boolean nextActiveForTesting(){return paperNext.active;}
    boolean previousActiveForTesting(){return paperPrevious.active;}
    long selectedForTesting(){return selected;}
    int scrollForTesting(){return scroll;} int lineCountForTesting(){return lines.size();}
    int matchCountForTesting(){return matches.size();}
    void openFirstForTesting(){if(!matches.isEmpty())open(matches.getFirst());}
    void acceptUpdatesForTesting(){receive();reader.accept();reflow();rebuildWidgets();}
    void queryForTesting(String text){query=text;selected=0;frontPage=scroll=0;reflow();rebuildWidgets();}
}
