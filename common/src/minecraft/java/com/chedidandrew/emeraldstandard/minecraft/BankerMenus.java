package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.world.inventory.MenuType;

/** Loader bridge for the Banker menu type. */
public final class BankerMenus {
    private static MenuType<BankerMenu> bankerMenu;

    private BankerMenus() {
    }

    public static synchronized void setType(MenuType<BankerMenu> type) {
        if (type == null) {
            throw new IllegalArgumentException("Banker menu type cannot be null");
        }
        bankerMenu = type;
    }

    public static synchronized MenuType<BankerMenu> type() {
        if (bankerMenu == null) {
            throw new IllegalStateException("Banker menu type has not been registered");
        }
        return bankerMenu;
    }
}
