package com.chedidandrew.emeraldstandard.client;
import com.chedidandrew.emeraldstandard.minecraft.BankerMenu;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;

/** Native controls, wrapping and report scrolling. No network, profile or save modifications. */
final class PriorityClientChecks {
    static Screen fixture(String kind) {
        if(!Boolean.getBoolean("the_emerald_standard.clientSmoke"))throw new IllegalStateException("Smoke only");
        try {
            Inventory inventory=new Inventory(null,new EntityEquipment());
            List<Component> report=new ArrayList<>();
            report.add(Component.literal("TOWN: WHAT IS HAPPENING?"));
            for(int i=0;i<18;i++)report.add(Component.literal("Project #"+i+": waiting for loaded land. Economic labor is paid, but physical construction still needs clear work cells and permission. Inspect the district map before funding more work."));
            BankerMenu menu=new BankerMenu(1,inventory);
            ContainerData data=(ContainerData)get(menu,"data");
            setData(data,"DATA_VILLAGE_PRESENT",1);
            Field base=BankerMenu.class.getDeclaredField("DATA_ASSET_PRICE_BASE");base.setAccessible(true);
            for(int i=0;i<com.chedidandrew.emeraldstandard.core.EconomyEngine.ASSETS.size();i++) {
                long quote = (9500L+i*350)*10_000;
                data.set(base.getInt(null)+2*i,(int)quote);
                data.set(base.getInt(null)+2*i+1,(int)(quote>>>32));
            }
            setData(data,"DATA_ASSET_OWNED_BASE",1); // Owned dust has a zero rounded value.
            Field heldBase=BankerMenu.class.getDeclaredField("DATA_ASSET_HOLDING_BASE");heldBase.setAccessible(true);
            long largeHolding=5_000_000_000L;
            data.set(heldBase.getInt(null)+2,(int)largeHolding);
            data.set(heldBase.getInt(null)+3,(int)(largeHolding>>>32));
            Field ownedBase=BankerMenu.class.getDeclaredField("DATA_ASSET_OWNED_BASE");ownedBase.setAccessible(true);
            data.set(ownedBase.getInt(null)+1,1);
            if(!menu.ownsAsset(0)||menu.assetHoldingValue(0)!=0||menu.assetHoldingValue(1)!=50_000_000.0)
                throw new IllegalStateException("Exact ownership and large holding transport");
            var marketState=com.chedidandrew.emeraldstandard.core.EconomyState.fresh(112,0,0);
            for(int day=0;day<400;day++)marketState.advanceOneDay();
            var advance=marketState.getClass().getDeclaredMethod("advanceMarketToSlot",int.class,boolean.class,boolean.class);
            advance.setAccessible(true);advance.invoke(marketState,48,true,true);
            var display=MarketDisplay.build(marketState,"VILX","VILX","RSDN",kind.equals("yesterday"));
            setData(data,"DATA_DAY",400);
            for(int i=0;i<com.chedidandrew.emeraldstandard.core.EconomyEngine.ASSETS.size();i++){
                long price=Math.round(marketState.prices.get(com.chedidandrew.emeraldstandard.core.EconomyEngine.ASSETS.get(i).ticker())*1_000_000);
                data.set(base.getInt(null)+2*i,(int)price);data.set(base.getInt(null)+2*i+1,(int)(price>>>32));
            }
            var marketBook=HandbookRecipes.previewStack(Items.WRITTEN_BOOK,1);
            marketBook.set(DataComponents.WRITTEN_BOOK_CONTENT,new WrittenBookContent(
                Filterable.passThrough("TES:market:2"),"Exchange",0,
                display.pages().stream().map(Component::literal).map(c->Filterable.passThrough((Component)c)).toList(),true));
            menu.slots.get(1).set(marketBook);
            if(menu.marketDisplay()==null)throw new IllegalStateException("market document missing");
            for(var slot:menu.slots)if(slot.mayPickup(null)||slot.mayPlace(marketBook))
                throw new IllegalStateException("transport document obtainable");
            BankerScreen screen=new BankerScreen(menu,inventory,Component.literal("The Emerald Standard"));
            set(screen,"tab",kind.equals("browser")||kind.equals("compare")||kind.equals("live")||kind.equals("yesterday")?BankerMenu.TAB_MARKET:BankerMenu.TAB_VILLAGE);
            set(screen,"watchlistLoaded",true);
            var browser=(InvestmentBrowser)get(screen,"browser");
            browser.favorites.add("VILX"); // In-memory fixture; never saved.
            if(kind.equals("browser")||kind.equals("compare")) {
                set(screen,"marketBrowser",true);
                if(kind.equals("compare")) {browser.comparison=0;set(screen,"browserCompare",1);}
            } else if(kind.equals("report")) {
                set(screen,"briefingMode",BankerMenu.BUTTON_TOWN_REPORT);
                var book=HandbookRecipes.previewStack(Items.WRITTEN_BOOK,1);
                book.set(DataComponents.WRITTEN_BOOK_CONTENT,new WrittenBookContent(
                    Filterable.passThrough("TES:brief:"+BankerMenu.BUTTON_TOWN_REPORT),"Village Office",0,
                    report.stream().map(Filterable::passThrough).toList(),true));
                menu.slots.getFirst().set(book);
            }
            return new Screen(Component.literal("Priority dashboard render fixture")) {
                @Override protected void init() {
                    screen.init(width,height);
                    BankerClientChecks.verifyTooltipWrapping(net.minecraft.client.Minecraft.getInstance());
                    if(kind.equals("town") || kind.equals("report")) verifyTownNavigation(screen,data);
                    if(kind.equals("report")) ReaderClientChecks.press(screen,"What next? Progress report");
                    if(kind.equals("browser")) {
                        verifyEditing(screen,"browserSearch","gold");
                        if(!((InvestmentBrowser)get(screen,"browser")).query.equals("gold"))throw new IllegalStateException("Search responder");
                        ReaderClientChecks.press(screen,"Clear search");
                        ReaderClientChecks.press(screen,">");ReaderClientChecks.press(screen,"<");
                        set(screen,"marketBrowser",false); screen.rebuildWidgets();
                        verifyEditing(screen,"amountField","1234");
                        // Owned dust must remain sellable even when the displayed share count rounds to zero.
                        requireActive(screen, "Sell All");
                        requireActive(screen, "Sell 25%");
                        try {
                            set(menu,"customAmount",1);
                            set(screen,"amountDraftDirty",false); set(screen,"amountDraft","1");
                            setData(data,"DATA_PHYSICAL_EMERALDS",1);
                            data.set(base.getInt(null),1); data.set(base.getInt(null)+1,0);
                        } catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}
                        if(menu.selectedAssetPrice()!=0.000001)throw new IllegalStateException("Sub-cent quote lost");
                        screen.rebuildWidgets(); requireActive(screen,"Invest");
                        try {
                            data.set(base.getInt(null),95_000_000); data.set(base.getInt(null)+1,0);
                        } catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}
                        set(screen,"marketBrowser",true); screen.rebuildWidgets();
                    } else if(kind.equals("compare")) {
                        for(String range:MarketDisplay.LABELS)ReaderClientChecks.press(screen,"Range: "+range);
                        if((int)get(screen,"compareRange")!=0)throw new IllegalStateException("Eight-range cycle");
                        if(screen.children().stream().anyMatch(c->c instanceof net.minecraft.client.gui.components.Button b
                                &&b.getMessage().getString().equals("Hover: details")))throw new IllegalStateException("obsolete hover controls");
                        float scale=(float)get(screen,"interfaceScale");
                        int lx=BankerScreenScale.origin(width,320,scale),ty=BankerScreenScale.origin(height,230,scale);
                        screen.mouseScrolled(lx+30*scale,ty+90*scale,0,-1);
                        int[] offsets=(int[])get(screen,"compareScroll");
                        if(offsets[0]<=0||offsets[1]!=0)throw new IllegalStateException("left details did not scroll independently");
                        screen.mouseScrolled(lx+190*scale,ty+90*scale,0,-1);
                        if(offsets[1]<=0)throw new IllegalStateException("right details did not scroll");
                        offsets[0]=offsets[1]=0;
                    } else if(kind.equals("report")) {
                        ReaderClientChecks.press(screen,"Down");
                        if((int)get(screen,"briefingScroll")<=0)throw new IllegalStateException("Report scroll");
                        ReaderClientChecks.press(screen,"Up");
                        if(menu.briefingPages(BankerMenu.BUTTON_FUND_PREVIEW).size()!=0)throw new IllegalStateException("Stale mode displayed");
                    }
                    for(var child:screen.children()) if(child instanceof net.minecraft.client.gui.components.AbstractWidget w)
                        if(w.getX()<0||w.getY()<0||w.getRight()>width||w.getBottom()>height)
                            throw new IllegalStateException("Dashboard control clipped: "+w.getMessage().getString());
                }
                @Override public void removed(){screen.removed();}
                private int hoverX(){float s=(float)get(screen,"interfaceScale");return kind.equals("live")?BankerScreenScale.origin(width,320,s)+(int)(220*s):-100;}
                private int hoverY(){float s=(float)get(screen,"interfaceScale");return kind.equals("live")?BankerScreenScale.origin(height,230,s)+(int)(90*s):-100;}
                @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){screen.extractBackground(g,hoverX(),hoverY(),d);}
                @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){screen.extractRenderState(g,hoverX(),hoverY(),d);}
            };
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void verifyTownNavigation(BankerScreen screen,ContainerData data) {
        ReaderClientChecks.press(screen,"Home");
        ReaderClientChecks.press(screen,"Town");
        requireTownOverview(screen);
        screen.rebuildWidgets();
        requireTownOverview(screen);
        ReaderClientChecks.press(screen,"What next? Progress report");
        if((int)get(screen,"briefingMode")!=BankerMenu.BUTTON_TOWN_REPORT)
            throw new IllegalStateException("Explicit progress report did not open");
        // Exercise the report's footer shortcut, not just the identically named top tab.
        var shortcut=screen.children().stream()
            .filter(c->c instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("Town"))
            .map(c->(net.minecraft.client.gui.components.Button)c)
            .max(Comparator.comparingInt(net.minecraft.client.gui.components.Button::getY)).orElseThrow();
        var click=new net.minecraft.client.input.MouseButtonEvent(shortcut.getX()+shortcut.getWidth()/2.0,
            shortcut.getY()+shortcut.getHeight()/2.0,new net.minecraft.client.input.MouseButtonInfo(0,0));
        if(!screen.mouseClicked(click,false))throw new IllegalStateException("Town shortcut ignored");
        screen.mouseReleased(click);
        requireTownOverview(screen);
        ReaderClientChecks.press(screen,"What next? Progress report");
        ReaderClientChecks.press(screen,"Back");
        requireTownOverview(screen);
        ReaderClientChecks.press(screen,"City expansion");
        ReaderClientChecks.press(screen,"Home");
        ReaderClientChecks.press(screen,"Town");
        requireTownOverview(screen);
        ReaderClientChecks.press(screen,"District map");
        ReaderClientChecks.press(screen,"Town");
        requireTownOverview(screen);
        try {
            setData(data,"DATA_VILLAGE_PRESENT",0);
            ReaderClientChecks.press(screen,"Town");
            if((int)get(screen,"briefingMode")!=0)throw new IllegalStateException("Missing village opened a report");
            setData(data,"DATA_VILLAGE_PRESENT",1);
            ReaderClientChecks.press(screen,"Town");
            requireTownOverview(screen);
        } catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}
    }

    private static void requireTownOverview(BankerScreen screen) {
        if((int)get(screen,"tab")!=BankerMenu.TAB_VILLAGE || (int)get(screen,"briefingMode")!=0
                || (boolean)get(screen,"expansionDetails") || (boolean)get(screen,"districtMap"))
            throw new IllegalStateException("Town must open the village overview");
        requireActive(screen,"City expansion");
        requireActive(screen,"District map");
        requireActive(screen,"What next? Progress report");
    }

    private static void requireActive(BankerScreen screen,String text) {
        boolean active=screen.children().stream().anyMatch(child -> child instanceof net.minecraft.client.gui.components.Button b
                && b.getMessage().getString().equals(text) && b.active);
        if(!active)throw new IllegalStateException("Expected active control: "+text);
    }
    private static void verifyEditing(BankerScreen screen,String field,String original) {
        var box=(net.minecraft.client.gui.components.EditBox)get(screen,field);
        box.setValue(original);
        box=(net.minecraft.client.gui.components.EditBox)get(screen,field);
        screen.setFocused(box);box.setCursorPosition(2);box.setHighlightPos(2);
        box.insertText("5");screen.rebuildWidgets();
        box=(net.minecraft.client.gui.components.EditBox)get(screen,field);
        if(!box.getHighlighted().isEmpty())throw new IllegalStateException("Rebuild selected trailing text: "+field);
        box.insertText("6");screen.rebuildWidgets();
        box=(net.minecraft.client.gui.components.EditBox)get(screen,field);
        if(!box.getValue().equals(original.substring(0,2)+"56"+original.substring(2)))
            throw new IllegalStateException("Mid-string typing lost suffix: "+field);
        box.setCursorPosition(1);box.setHighlightPos(4);screen.rebuildWidgets();
        box=(net.minecraft.client.gui.components.EditBox)get(screen,field);
        if(box.getHighlighted().length()!=3)throw new IllegalStateException("Selection lost: "+field);
        box.insertText("9");screen.rebuildWidgets();
        box=(net.minecraft.client.gui.components.EditBox)get(screen,field);
        if(!box.getValue().equals(original.substring(0,1)+"9"+original.substring(2)))
            throw new IllegalStateException("Selected paste changed suffix: "+field);
        box.deleteChars(-1);screen.rebuildWidgets();
        box=(net.minecraft.client.gui.components.EditBox)get(screen,field);
        if(!box.getValue().equals(original.substring(0,1)+original.substring(2)))
            throw new IllegalStateException("Backspace changed suffix: "+field);
        box.setValue(original); // restore screenshot fixture
    }
    static Object get(Object object,String field){
        try {
            Class<?> type=object instanceof BankerMenu?BankerMenu.class:object.getClass();
            Field f=type.getDeclaredField(field);f.setAccessible(true);return f.get(object);
        }
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    static void set(Object object,String field,Object value){
        try {Field f=object.getClass().getDeclaredField(field);f.setAccessible(true);f.set(object,value);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    static void setData(ContainerData data,String name,int value)throws ReflectiveOperationException{
        Field f=BankerMenu.class.getDeclaredField(name);f.setAccessible(true);data.set(f.getInt(null),value);
    }
}
