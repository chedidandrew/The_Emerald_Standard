package com.chedidandrew.emeraldstandard.client;

/** Pure geometry shared by rendering and regression checks. Coordinates are GUI pixels. */
public record HandbookLayout(int x, int y, int width, int height, int sidebar) {
    public static HandbookLayout fit(int screenWidth, int screenHeight) {
        int width = Math.min(900, Math.max(1, screenWidth - 20));
        int height = Math.min(620, Math.max(1, screenHeight - 20));
        return new HandbookLayout((screenWidth - width) / 2, (screenHeight - height) / 2,
                width, height, Math.min(172, Math.max(120, width / 3)));
    }
    public int bodyX() { return x + sidebar + 14; }
    public int bodyY() { return y + 61; }
    public int bodyWidth() { return Math.max(24, width - sidebar - 32); }
    public int bodyHeight() { return Math.max(24, height - 103); }
    public int visibleChapters() { return Math.max(1, (height - 104) / 23); }
    public static int maximumScroll(int lines, int visibleLines) {
        return Math.max(0, lines - Math.max(1, visibleLines));
    }
}
