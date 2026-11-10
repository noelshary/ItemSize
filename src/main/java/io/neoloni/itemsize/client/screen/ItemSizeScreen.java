package io.neoloni.itemsize.client.screen;

import io.neoloni.itemsize.client.config.ItemSizeConfig;
import io.neoloni.itemsize.client.config.ItemSizeConfig.Dimension;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * ItemSize editor: item list with search / categories / sorting on the left,
 * live previews (ground, hand, inventory) with sliders and typed values on the right.
 * The whole layout is computed from the window size, so nothing can overlap.
 */
public final class ItemSizeScreen extends Screen {
    // ---------------------------------------------------------------- palette
    private static final int PANEL_BG = 0xF20D1020;
    private static final int CARD_BG = 0xD91A2035;
    private static final int CARD_HOVER = 0xF02A3150;
    private static final int CARD_SELECTED = 0xFF27304D;
    private static final int FIELD_BG = 0xFF141A2E;
    private static final int CYAN = 0xFF66E7F2;
    private static final int PURPLE = 0xFFB58CFF;
    private static final int AMBER = 0xFFFFC76B;
    private static final int GREEN = 0xFF7CF29C;
    private static final int RED = 0xFFFF8A9A;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int TEXT = 0xFFE9ECFF;
    private static final int MUTED = 0xFF97A4BC;

    private static final float MIN_SCALE = 0.10f;
    private static final float MAX_SCALE = 3.00f;
    private static final int ROW_H = 24;
    private static final int HEADER_H = 40;
    private static final int FOOTER_H = 20;

    private static final Dimension[] DIMS = {Dimension.GROUND, Dimension.HAND, Dimension.INVENTORY};
    private static final String[] VIEW_LABEL = {"GROUND", "HAND", "INVENTORY"};
    private static final String[] VIEW_DESC = {
            "Dropped item in the world",
            "Held in first & third person",
            "Icon in inventory screens"
    };
    private static final int[] VIEW_COLOR = {CYAN, PURPLE, AMBER};
    private static final float[] PRESETS = {0.5f, 1.0f, 1.5f, 2.0f};

    private enum Category {
        ALL("ALL"), EDITED("EDITED"), BLOCKS("BLOCKS"), TOOLS("TOOLS"),
        ARMOR("ARMOR"), FOOD("FOOD"), OTHER("OTHER");

        final String label;

        Category(String label) {
            this.label = label;
        }
    }

    private enum Sort {
        AZ("A-Z"), ZA("Z-A"), EDITED_FIRST("EDITED FIRST");

        final String label;

        Sort(String label) {
            this.label = label;
        }

        Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private record Entry(Item item, ItemStack stack, String name, String lower, String id, Category category) {}

    // ------------------------------------------------------------------ state
    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private final Particle[] particles = new Particle[40];
    private Entry selected;

    private String search = "";
    private boolean searchFocused;
    private Category category = Category.ALL;
    private Sort sort = Sort.AZ;
    private boolean linkViews;

    private Dimension editing;
    private String editBuffer = "";
    private boolean editFresh;

    private Dimension dragging;
    private boolean draggingScrollbar;

    private float listScroll, listScrollTarget;
    private float editorScroll, editorScrollTarget;
    private float opening;
    private float selectFade = 1.0f;
    private float toggleAnim = 1.0f;
    private final float[] shown = {1.0f, 1.0f, 1.0f};
    private int hoverRow = -1;
    private float hoverEase;
    private long openedAt;
    private long resetAllArmedAt;

    // ----------------------------------------------------------------- layout
    private int panelX, panelY, panelW, panelH;
    private int leftX, leftW, rightX, rightW, top, bottom;
    private int searchX, searchY, searchW, searchH;
    private final int[] chipX = new int[Category.values().length];
    private final int[] chipY = new int[Category.values().length];
    private final int[] chipW = new int[Category.values().length];
    private int infoY, sortX, sortY, sortW, sortH;
    private int listX, listY, listW, listH;
    private int closeX, closeY, resetAllX, resetAllY, resetAllW, toggleX, toggleY, toggleW;
    private int edTop, edH, titleH, rowH, barH, gap, maxEditorScroll;
    private final int[] rowY = new int[3];
    private int barY;
    private int tile, tileX, ctlX, trackW, fieldX, fieldW, miniW;
    private int itemResetX, itemResetY, itemResetW;
    private int linkX, linkY;
    private final int[] presetX = new int[PRESETS.length];
    private int presetW;
    private boolean showSetAllLabel;

    public ItemSizeScreen() {
        super(Component.literal("ItemSize"));
        BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .filter(ItemSizeConfig::isAllowed)
                .forEach(item -> {
                    ItemStack stack = new ItemStack(item);
                    String name = stack.getHoverName().getString();
                    all.add(new Entry(item, stack, name, name.toLowerCase(Locale.ROOT),
                            ItemSizeConfig.id(item).toLowerCase(Locale.ROOT), classify(item, stack)));
                });
        all.sort(Comparator.comparing(Entry::lower));
        for (Entry entry : all) {
            if (entry.item() == Items.DIAMOND) selected = entry;
        }
        if (selected == null && !all.isEmpty()) selected = all.get(0);
        refilter(true);

        Random random = new Random(0x1A2B3C);
        for (int i = 0; i < particles.length; i++) {
            particles[i] = new Particle(random.nextFloat(), random.nextFloat(),
                    0.12f + random.nextFloat() * 0.28f,
                    random.nextFloat() * 6.28f,
                    0.4f + random.nextFloat() * 1.2f);
        }
        if (selected != null) {
            for (int i = 0; i < DIMS.length; i++) {
                shown[i] = ItemSizeConfig.profile(selected.item()).get(DIMS[i]);
            }
        }
    }

    private static Category classify(Item item, ItemStack stack) {
        if (item instanceof BlockItem) return Category.BLOCKS;
        if (stack.has(DataComponents.FOOD)) return Category.FOOD;
        if (stack.has(DataComponents.EQUIPPABLE)) return Category.ARMOR;
        if (stack.has(DataComponents.TOOL)) return Category.TOOLS;
        return Category.OTHER;
    }

    // ------------------------------------------------------------------ setup
    @Override
    protected void init() {
        super.init();
        openedAt = System.currentTimeMillis();
        layout();
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.82f);
    }

    private void play(Holder<SoundEvent> sound, float pitch) {
        play(sound.value(), pitch);
    }

    private void play(SoundEvent sound, float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
        }
    }

    // ----------------------------------------------------------------- layout
    private void layout() {
        panelW = Math.min(780, width - 16);
        panelH = Math.min(460, height - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        int pad = 14;
        top = panelY + HEADER_H + 8;
        bottom = panelY + panelH - FOOTER_H;

        leftX = panelX + pad;
        leftW = Math.max(130, Math.min(250, (int) (panelW * 0.38f)));
        rightX = leftX + leftW + 10;
        rightW = panelX + panelW - pad - rightX;

        // header buttons (right aligned)
        closeX = panelX + panelW - pad - 16;
        closeY = panelY + 11;
        resetAllW = font.width(resetAllLabel()) + 14;
        resetAllX = closeX - 6 - resetAllW;
        resetAllY = panelY + 11;
        toggleW = 38;
        toggleX = resetAllX - 6 - toggleW;
        toggleY = panelY + 11;

        // left column
        searchX = leftX;
        searchY = top;
        searchW = leftW;
        searchH = 22;
        int cx = leftX;
        int cy = searchY + searchH + 6;
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            int w = font.width(categories[i].label) + 10;
            if (cx + w > leftX + leftW && cx > leftX) {
                cx = leftX;
                cy += 17;
            }
            chipX[i] = cx;
            chipY[i] = cy;
            chipW[i] = w;
            cx += w + 3;
        }
        infoY = cy + 14 + 7;
        String sortLabel = "SORT: " + sort.label;
        sortW = font.width(sortLabel) + 10;
        sortH = 13;
        sortX = leftX + leftW - sortW;
        sortY = infoY - 2;
        listX = leftX;
        listY = infoY + 14;
        listW = leftW;
        listH = Math.max(30, bottom - listY);

        // editor (right column)
        edTop = top;
        edH = Math.max(40, bottom - top);
        titleH = 44;
        barH = 26;
        gap = 6;
        rowH = Math.max(48, (edH - titleH - 3 * gap - barH) / 3);
        int contentH = titleH + 3 * rowH + 3 * gap + barH;
        maxEditorScroll = Math.max(0, contentH - edH);
        editorScrollTarget = Math.max(0, Math.min(editorScrollTarget, maxEditorScroll));
        editorScroll = Math.max(0, Math.min(editorScroll, maxEditorScroll));
        int oy = edTop - Math.round(editorScroll);
        for (int i = 0; i < 3; i++) {
            rowY[i] = oy + titleH + i * (rowH + gap);
        }
        barY = oy + titleH + 3 * (rowH + gap);

        itemResetW = 74;
        itemResetX = rightX + rightW - itemResetW;
        itemResetY = oy + 8;

        tile = Math.max(30, Math.min(rowH - 10, Math.min(84, rightW / 3)));
        tileX = rightX + 8;
        ctlX = tileX + tile + 14;
        int ctlW = rightX + rightW - 10 - ctlX;
        fieldW = 52;
        miniW = 18;
        trackW = Math.max(30, ctlW - fieldW - miniW - 14);
        fieldX = ctlX + trackW + 8;

        presetW = font.width("200%") + 10;
        int presetsTotal = PRESETS.length * presetW + (PRESETS.length - 1) * 4;
        int px = rightX + rightW - 8 - presetsTotal;
        for (int i = 0; i < PRESETS.length; i++) {
            presetX[i] = px + i * (presetW + 4);
        }
        showSetAllLabel = (px - (rightX + 8 + 16 + font.width("LINK VIEWS"))) > font.width("SET ALL") + 16;
        linkX = rightX + 8;
        linkY = barY + (barH - 10) / 2;
    }

    private String resetAllLabel() {
        return resetAllArmed() ? "SURE?" : "RESET ALL";
    }

    private boolean resetAllArmed() {
        return System.currentTimeMillis() - resetAllArmedAt < 2500L;
    }

    private int tileY(int i) {
        return rowY[i] + (rowH - tile) / 2;
    }

    private int ctlTop(int i) {
        return rowY[i] + (rowH - 33) / 2 + 17;
    }

    // ----------------------------------------------------------------- render
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ItemSizeConfig.bypass = true;
        try {
            renderInternal(graphics, mouseX, mouseY);
        } finally {
            ItemSizeConfig.bypass = false;
        }
    }

    private void renderInternal(GuiGraphics g, int mouseX, int mouseY) {
        layout();
        opening = Math.min(1.0f, opening + 0.075f);
        selectFade = Math.min(1.0f, selectFade + 0.08f);
        toggleAnim += ((ItemSizeConfig.isEnabled() ? 1.0f : 0.0f) - toggleAnim) * 0.25f;
        listScroll += (listScrollTarget - listScroll) * 0.35f;
        if (Math.abs(listScrollTarget - listScroll) < 0.4f) listScroll = listScrollTarget;
        editorScroll += (editorScrollTarget - editorScroll) * 0.35f;
        if (Math.abs(editorScrollTarget - editorScroll) < 0.4f) editorScroll = editorScrollTarget;
        if (selected != null) {
            for (int i = 0; i < DIMS.length; i++) {
                float target = ItemSizeConfig.profile(selected.item()).get(DIMS[i]);
                shown[i] += (target - shown[i]) * 0.28f;
            }
        }

        g.fill(0, 0, width, height, 0xC9050914);
        renderParticles(g);

        float eased = 1.0f - (float) Math.pow(1.0f - opening, 3.0);
        float scale = 0.94f + eased * 0.06f;
        g.pose().pushMatrix();
        g.pose().translate(width / 2.0f, height / 2.0f);
        g.pose().scale(scale, scale);
        g.pose().translate(-width / 2.0f, -height / 2.0f);

        drawPanel(g);
        drawHeader(g, mouseX, mouseY);
        drawLeft(g, mouseX, mouseY);
        drawEditor(g, mouseX, mouseY);
        drawFooter(g);

        g.pose().popMatrix();
    }

    private void renderParticles(GuiGraphics g) {
        long now = System.currentTimeMillis();
        for (Particle particle : particles) {
            float drift = (now * 0.000025f * particle.speed) % 1.0f;
            float px = particle.x * width + (float) Math.sin(now * 0.0005f + particle.phase) * 12.0f;
            float py = (particle.y + drift) % 1.0f * height;
            int alpha = 35 + (int) (35 * (0.5 + 0.5 * Math.sin(now * 0.001f + particle.phase)));
            int color = ((Math.sin(particle.phase) > 0 ? CYAN : PURPLE) & 0x00FFFFFF) | (alpha << 24);
            int size = particle.size > 1.25f ? 2 : 1;
            g.fill((int) px, (int) py, (int) px + size, (int) py + size, color);
        }
    }

    private void drawPanel(GuiGraphics g) {
        long now = System.currentTimeMillis();
        g.fill(panelX - 5, panelY - 5, panelX + panelW + 5, panelY + panelH + 5, 0x352F65A7);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + HEADER_H, 0x40324A8C, 0x00324A8C);
        g.fill(panelX, panelY, panelX + panelW, panelY + 2, CYAN);
        g.fill(panelX, panelY + 2, panelX + panelW, panelY + 3, PURPLE);
        g.fill(panelX + 1, panelY + 1, panelX + 2, panelY + panelH - 1, 0xFF5D83C8);
        g.fill(panelX + panelW - 2, panelY + 1, panelX + panelW - 1, panelY + panelH - 1, 0xFF9D74DB);
        // animated light sweep along the header line
        float sweep = (now % 3200L) / 3200.0f;
        int sweepX = panelX + 14 + Math.round(sweep * (panelW - 28));
        g.fill(panelX + 14, panelY + HEADER_H - 1, panelX + panelW - 14, panelY + HEADER_H, 0x553D4C70);
        g.fill(Math.max(panelX + 14, sweepX - 30), panelY + HEADER_H - 1,
                Math.min(panelX + panelW - 14, sweepX + 30), panelY + HEADER_H, 0xCC66E7F2);
    }

    private void drawHeader(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "ITEMSIZE", panelX + 14, panelY + 10, WHITE, false);
        g.drawString(font, "ITEM DIMENSION EDITOR", panelX + 14, panelY + 23, CYAN, false);
        // version tag, only if there is room
        int tagX = panelX + 14 + font.width("ITEM DIMENSION EDITOR") + 12;
        if (tagX + font.width("1.21.11 / FABRIC") < toggleX - 8) {
            g.drawString(font, "1.21.11 / FABRIC", tagX, panelY + 23, MUTED, false);
        }

        // ON / OFF pill
        boolean on = ItemSizeConfig.isEnabled();
        boolean toggleHover = in(mouseX, mouseY, toggleX, toggleY, toggleW, 16);
        int pillColor = blend(0xFF5A1E2A, 0xFF1E5A3A, toggleAnim);
        if (toggleHover) pillColor = blend(pillColor, WHITE, 0.12f);
        g.fill(toggleX, toggleY, toggleX + toggleW, toggleY + 16, pillColor);
        g.fill(toggleX, toggleY, toggleX + toggleW, toggleY + 1, on ? GREEN : RED);
        int knobX = toggleX + 3 + Math.round(toggleAnim * (toggleW - 16));
        g.fill(knobX, toggleY + 3, knobX + 10, toggleY + 13, on ? GREEN : RED);
        String label = on ? "ON" : "OFF";
        int labelX = on ? toggleX + 6 : toggleX + toggleW - 6 - font.width(label);
        g.drawString(font, label, labelX, toggleY + 4, WHITE, false);

        drawButton(g, resetAllX, resetAllY, resetAllW, 16, resetAllLabel(),
                in(mouseX, mouseY, resetAllX, resetAllY, resetAllW, 16),
                resetAllArmed() ? 0xFF6B2F44 : 0xFF30243F, resetAllArmed() ? RED : PURPLE);
        boolean closeHover = in(mouseX, mouseY, closeX, closeY, 16, 16);
        g.fill(closeX, closeY, closeX + 16, closeY + 16, closeHover ? 0xFF6B2F44 : 0xFF222A42);
        g.drawCenteredString(font, "x", closeX + 8, closeY + 4, closeHover ? WHITE : MUTED);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String label,
                            boolean hover, int baseColor, int accent) {
        g.fill(x, y, x + w, y + h, hover ? blend(baseColor, WHITE, 0.14f) : baseColor);
        g.fill(x, y, x + w, y + 1, accent);
        g.drawCenteredString(font, label, x + w / 2, y + (h - font.lineHeight) / 2 + 1, WHITE);
    }

    // ------------------------------------------------------------- left side
    private void drawLeft(GuiGraphics g, int mouseX, int mouseY) {
        // search field
        boolean searchHover = in(mouseX, mouseY, searchX, searchY, searchW, searchH);
        g.fill(searchX, searchY, searchX + searchW, searchY + searchH,
                searchFocused || searchHover ? 0xFF242D49 : 0xFF181F34);
        g.fill(searchX, searchY, searchX + 2, searchY + searchH, searchFocused ? CYAN : 0xFF596887);
        g.fill(searchX + 8, searchY + 8, searchX + 13, searchY + 13, CYAN);
        g.fill(searchX + 9, searchY + 9, searchX + 12, searchY + 12, 0xFF181F34);
        g.fill(searchX + 13, searchY + 13, searchX + 15, searchY + 15, CYAN);
        boolean caret = searchFocused && (System.currentTimeMillis() / 450L) % 2L == 0L;
        String shownText = search.isEmpty() ? "Search items..." : fit(search, searchW - 54);
        g.drawString(font, shownText + (caret ? "|" : ""), searchX + 22, searchY + 7,
                search.isEmpty() ? MUTED : WHITE, false);
        if (!search.isEmpty()) {
            boolean xHover = in(mouseX, mouseY, searchX + searchW - 18, searchY + 3, 16, 16);
            g.drawCenteredString(font, "x", searchX + searchW - 10, searchY + 7, xHover ? WHITE : MUTED);
        }

        // category chips
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            boolean active = categories[i] == category;
            boolean hover = in(mouseX, mouseY, chipX[i], chipY[i], chipW[i], 14);
            int bg = active ? 0xFF2D3B66 : hover ? 0xFF222B46 : 0xFF171D31;
            g.fill(chipX[i], chipY[i], chipX[i] + chipW[i], chipY[i] + 14, bg);
            if (active) g.fill(chipX[i], chipY[i] + 13, chipX[i] + chipW[i], chipY[i] + 14, CYAN);
            g.drawCenteredString(font, categories[i].label, chipX[i] + chipW[i] / 2, chipY[i] + 3,
                    active ? WHITE : hover ? TEXT : MUTED);
        }

        // count + sort button
        g.drawString(font, filtered.size() + " ITEMS", leftX, infoY, MUTED, false);
        boolean sortHover = in(mouseX, mouseY, sortX, sortY, sortW, sortH);
        g.fill(sortX, sortY, sortX + sortW, sortY + sortH, sortHover ? 0xFF2A3454 : 0xFF1A2138);
        g.drawCenteredString(font, "SORT: " + sort.label, sortX + sortW / 2, sortY + 3,
                sortHover ? WHITE : MUTED);

        drawList(g, mouseX, mouseY);
    }

    private int maxListScroll() {
        return Math.max(0, filtered.size() * ROW_H - listH);
    }

    private void drawList(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(listX, listY, listX + listW, listY + listH, 0x66101628);
        int newHover = -1;
        if (in(mouseX, mouseY, listX, listY, listW - 8, listH)) {
            int index = (int) ((mouseY - listY + listScroll) / ROW_H);
            if (index >= 0 && index < filtered.size()) newHover = index;
        }
        if (newHover != hoverRow) {
            hoverRow = newHover;
            hoverEase = 0.0f;
        } else {
            hoverEase = Math.min(1.0f, hoverEase + 0.2f);
        }

        if (filtered.isEmpty()) {
            g.drawCenteredString(font, "No items found", listX + listW / 2, listY + 18, MUTED);
            return;
        }

        g.enableScissor(listX, listY, listX + listW, listY + listH);
        int first = Math.max(0, (int) (listScroll / ROW_H));
        int textW = listW - 28 - 18;
        for (int i = first; i < filtered.size(); i++) {
            int y = listY + i * ROW_H - Math.round(listScroll);
            if (y >= listY + listH) break;
            Entry entry = filtered.get(i);
            boolean hovered = i == hoverRow;
            boolean isSelected = entry == selected;
            if (isSelected) {
                g.fill(listX, y, listX + listW - 6, y + ROW_H - 1, CARD_SELECTED);
                g.fill(listX, y, listX + 2, y + ROW_H - 1, CYAN);
            } else if (hovered) {
                g.fill(listX, y, listX + listW - 6, y + ROW_H - 1, CARD_HOVER);
                g.fill(listX, y, listX + Math.max(1, Math.round(3 * hoverEase)), y + ROW_H - 1, PURPLE);
            }
            int lift = hovered ? -1 : 0;
            g.renderItem(entry.stack(), listX + 7, y + 4 + lift);
            g.drawString(font, fit(entry.name(), textW), listX + 29, y + 8,
                    isSelected ? WHITE : hovered ? TEXT : 0xFFB7C2D9, false);
            if (ItemSizeConfig.isModified(entry.item())) {
                g.fill(listX + listW - 16, y + 9, listX + listW - 11, y + 14, CYAN);
            }
        }
        g.disableScissor();

        int contentH = filtered.size() * ROW_H;
        if (contentH > listH) {
            int thumbH = Math.max(14, listH * listH / contentH);
            int thumbY = listY + Math.round(listScroll / (float) (contentH - listH) * (listH - thumbH));
            boolean hot = draggingScrollbar || in(mouseX, mouseY, listX + listW - 8, listY, 8, listH);
            g.fill(listX + listW - 5, listY, listX + listW - 2, listY + listH, 0x33000000);
            g.fill(listX + listW - 5, thumbY, listX + listW - 2, thumbY + thumbH, hot ? CYAN : 0xFF5D6F99);
        }
    }

    // ----------------------------------------------------------- right side
    private void drawEditor(GuiGraphics g, int mouseX, int mouseY) {
        if (selected == null) return;
        float ease = 1.0f - (float) Math.pow(1.0f - selectFade, 3.0);
        g.enableScissor(rightX - 2, edTop, rightX + rightW + 2, edTop + edH);
        g.pose().pushMatrix();
        g.pose().translate((1.0f - ease) * 14.0f, 0.0f);

        int oy = edTop - Math.round(editorScroll);
        // title block
        g.pose().pushMatrix();
        g.pose().translate(rightX + 6, oy + 6);
        g.pose().scale(2.0f, 2.0f);
        g.renderItem(selected.stack(), 0, 0);
        g.pose().popMatrix();
        int nameW = Math.max(40, itemResetX - (rightX + 48) - 8);
        g.drawString(font, fit(selected.name(), nameW), rightX + 48, oy + 8, WHITE, false);
        g.drawString(font, fit(selected.id(), nameW), rightX + 48, oy + 21, MUTED, false);
        boolean modified = ItemSizeConfig.isModified(selected.item());
        boolean resetHover = modified && in(mouseX, mouseY, itemResetX, itemResetY, itemResetW, 18);
        drawButton(g, itemResetX, itemResetY, itemResetW, 18, "RESET ITEM", resetHover,
                modified ? 0xFF30243F : 0xFF1A1F30, modified ? PURPLE : 0xFF3A4260);
        if (!ItemSizeConfig.isEnabled()) {
            g.drawString(font, "OFF - scales are not applied", rightX + 48, oy + 32, RED, false);
        }

        long now = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            drawRow(g, i, mouseX, mouseY, now);
        }
        drawBar(g, mouseX, mouseY);

        g.pose().popMatrix();
        g.disableScissor();

        // editor scrollbar, only when the window is very small
        if (maxEditorScroll > 0) {
            int thumbH = Math.max(14, edH * edH / (edH + maxEditorScroll));
            int thumbY = edTop + Math.round(editorScroll / (float) maxEditorScroll * (edH - thumbH));
            g.fill(rightX + rightW - 2, edTop, rightX + rightW, edTop + edH, 0x33000000);
            g.fill(rightX + rightW - 2, thumbY, rightX + rightW, thumbY + thumbH, 0xFF5D6F99);
        }
    }

    private void drawRow(GuiGraphics g, int i, int mouseX, int mouseY, long now) {
        int y = rowY[i];
        int accent = VIEW_COLOR[i];
        g.fill(rightX, y, rightX + rightW, y + rowH, CARD_BG);
        g.fill(rightX, y, rightX + 3, y + rowH, accent);

        drawTile(g, i, tileX, tileY(i), tile, now);

        int blockTop = y + (rowH - 33) / 2;
        g.drawString(font, VIEW_LABEL[i], ctlX, blockTop, accent, false);
        float value = ItemSizeConfig.profile(selected.item()).get(DIMS[i]);
        String percent = Math.round(value * 100.0f) + "%";
        int percentX = ctlX + trackW + 8 + fieldW - font.width(percent);
        g.drawString(font, percent, percentX, blockTop, MUTED, false);
        int descX = ctlX + font.width(VIEW_LABEL[i]) + 8;
        if (descX + font.width(VIEW_DESC[i]) < percentX - 6) {
            g.drawString(font, VIEW_DESC[i], descX, blockTop, 0xFF6F7B96, false);
        }

        int cy = ctlTop(i);
        boolean sliderHover = dragging == DIMS[i] || in(mouseX, mouseY, ctlX - 4, cy - 4, trackW + 8, 24);
        // track
        g.fill(ctlX, cy + 6, ctlX + trackW, cy + 10, 0xFF2A3350);
        float progress = (value - MIN_SCALE) / (MAX_SCALE - MIN_SCALE);
        int knobX = ctlX + Math.round(progress * trackW);
        g.fill(ctlX, cy + 6, knobX, cy + 10, accent);
        int tickX = ctlX + Math.round((1.0f - MIN_SCALE) / (MAX_SCALE - MIN_SCALE) * trackW);
        g.fill(tickX, cy + 3, tickX + 1, cy + 13, 0x99FFFFFF);
        int knobHalf = sliderHover ? 5 : 4;
        g.fill(knobX - knobHalf, cy + 1, knobX + knobHalf + 1, cy + 15, WHITE);
        g.fill(knobX - 1, cy + 4, knobX + 2, cy + 12, accent);

        // typed value field
        boolean isEditing = editing == DIMS[i];
        boolean fieldHover = in(mouseX, mouseY, fieldX, cy, fieldW, 16);
        g.fill(fieldX, cy, fieldX + fieldW, cy + 16, isEditing ? 0xFF1E2744 : fieldHover ? 0xFF1B2340 : FIELD_BG);
        g.fill(fieldX, cy + 15, fieldX + fieldW, cy + 16, isEditing ? accent : 0xFF39435C);
        String text;
        if (isEditing) {
            boolean caret = (System.currentTimeMillis() / 450L) % 2L == 0L;
            text = editBuffer + (caret ? "|" : " ");
        } else {
            text = formatNumber(value);
        }
        g.drawString(font, text + "x", fieldX + 6, cy + 4, WHITE, false);

        // per-view reset to 1x
        int miniX = fieldX + fieldW + 6;
        boolean miniHover = in(mouseX, mouseY, miniX, cy, miniW, 16);
        boolean isDefault = Math.abs(value - 1.0f) < 0.0001f;
        g.fill(miniX, cy, miniX + miniW, cy + 16, miniHover && !isDefault ? 0xFF3A2F55 : 0xFF1A2038);
        g.drawCenteredString(font, "1x", miniX + miniW / 2, cy + 4, isDefault ? 0xFF4A5470 : TEXT);
    }

    private void drawBar(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(rightX, barY, rightX + rightW, barY + barH, CARD_BG);
        g.fill(rightX, barY, rightX + rightW, barY + 1, 0xFF39435C);
        // link toggle (checkbox)
        boolean linkHover = in(mouseX, mouseY, linkX - 2, barY, 16 + font.width("LINK VIEWS") + 8, barH);
        g.fill(linkX, linkY, linkX + 10, linkY + 10, linkHover ? 0xFF2A3454 : FIELD_BG);
        g.fill(linkX, linkY, linkX + 10, linkY + 1, 0xFF5D6F99);
        g.fill(linkX, linkY + 9, linkX + 10, linkY + 10, 0xFF5D6F99);
        g.fill(linkX, linkY, linkX + 1, linkY + 10, 0xFF5D6F99);
        g.fill(linkX + 9, linkY, linkX + 10, linkY + 10, 0xFF5D6F99);
        if (linkViews) g.fill(linkX + 3, linkY + 3, linkX + 7, linkY + 7, CYAN);
        g.drawString(font, "LINK VIEWS", linkX + 16, barY + (barH - font.lineHeight) / 2 + 1,
                linkViews ? WHITE : MUTED, false);
        // presets
        if (showSetAllLabel) {
            g.drawString(font, "SET ALL", presetX[0] - 8 - font.width("SET ALL"),
                    barY + (barH - font.lineHeight) / 2 + 1, MUTED, false);
        }
        for (int i = 0; i < PRESETS.length; i++) {
            boolean hover = in(mouseX, mouseY, presetX[i], barY + 4, presetW, barH - 8);
            g.fill(presetX[i], barY + 4, presetX[i] + presetW, barY + barH - 4,
                    hover ? 0xFF2D3B66 : 0xFF1A2138);
            g.drawCenteredString(font, Math.round(PRESETS[i] * 100.0f) + "%", presetX[i] + presetW / 2,
                    barY + (barH - font.lineHeight) / 2 + 1, hover ? WHITE : TEXT);
        }
    }

    // ---------------------------------------------------------- live preview
    private void drawTile(GuiGraphics g, int i, int x, int y, int t, long now) {
        int accent = VIEW_COLOR[i];
        g.fill(x - 1, y - 1, x + t + 1, y + t + 1, 0xFF2B3552);
        g.fillGradient(x, y, x + t, y + t, 0xFF141B33, 0xFF0A0E1C);
        g.fill(x, y - 1, x + t, y, accent);
        g.enableScissor(x, y, x + t, y + t);
        float k = t / 48.0f;
        float s = shown[i];
        ItemStack stack = selected.stack();
        switch (DIMS[i]) {
            case GROUND -> {
                int groundY = y + Math.round(t * 0.74f);
                g.fill(x, groundY, x + t, y + t, 0xFF18261C);
                g.fill(x, groundY, x + t, groundY + 1, 0xFF2F5A34);
                float bob = (float) Math.sin(now * 0.004) * t * 0.025f;
                float itemPx = 16.0f * k * s;
                int cx = x + t / 2;
                int shadow = Math.round(Math.min(t * 0.9f, 14.0f * k * Math.max(0.6f, Math.min(s, 2.0f))));
                int shadowY = groundY + Math.round(t * 0.05f);
                g.fill(cx - shadow / 2, shadowY, cx + shadow / 2, shadowY + Math.max(2, Math.round(t * 0.035f)), 0x66000000);
                g.fill(cx - shadow / 3, shadowY - 1, cx + shadow / 3, shadowY, 0x44000000);
                float cy = groundY - itemPx * 0.5f - t * 0.06f - bob;
                drawItem(g, stack, cx, cy, k * s, 0.0f);
            }
            case INVENTORY -> {
                int slot = Math.round(20.0f * k);
                int sx = x + (t - slot) / 2;
                int sy = y + (t - slot) / 2;
                g.fill(sx, sy, sx + slot, sy + slot, 0xFFFFFFFF);
                g.fill(sx, sy, sx + slot - 1, sy + slot - 1, 0xFF373737);
                g.fill(sx + 1, sy + 1, sx + slot - 1, sy + slot - 1, 0xFF8B8B8B);
                drawItem(g, stack, sx + slot / 2.0f, sy + slot / 2.0f, k * s, 0.0f);
            }
            case HAND -> {
                float sway = (float) Math.sin(now * 0.0025) * 0.06f;
                int aw = Math.max(3, Math.round(t * 0.13f));
                int ah = Math.round(t * 0.62f);
                int split = -Math.round(ah * 0.45f);
                g.pose().pushMatrix();
                g.pose().translate(x + t * 0.66f, y + t * 1.05f);
                g.pose().rotate(-0.42f + sway);
                g.fill(-aw, -ah, aw, split, 0xFFC59A78);
                g.fill(aw - Math.max(1, aw / 4), -ah, aw, split, 0xFFA97F5F);
                g.fill(-aw, split, aw, 0, 0xFF3A5BA8);
                g.fill(aw - Math.max(1, aw / 4), split, aw, 0, 0xFF2D4A8A);
                g.pose().translate(0.0f, -ah);
                g.pose().rotate(0.9f);
                g.pose().scale(k * s, k * s);
                g.pose().translate(-8.0f, -12.0f);
                g.renderItem(stack, 0, 0);
                g.pose().popMatrix();
            }
        }
        g.disableScissor();
    }

    private void drawItem(GuiGraphics g, ItemStack stack, float cx, float cy, float scale, float rotation) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        if (rotation != 0.0f) g.pose().rotate(rotation);
        g.pose().scale(scale, scale);
        g.pose().translate(-8.0f, -8.0f);
        g.renderItem(stack, 0, 0);
        g.pose().popMatrix();
    }

    private void drawFooter(GuiGraphics g) {
        int y = panelY + panelH - 15;
        String saved = "Auto-saved";
        int savedW = font.width(saved) + 10;
        g.fill(panelX + panelW - 14 - savedW, y + 2, panelX + panelW - 14 - savedW + 4, y + 6, GREEN);
        g.drawString(font, saved, panelX + panelW - 14 - savedW + 9, y, MUTED, false);
        String hint = "Type to search  |  Up/Down select  |  Click a value to type it  |  O reopens";
        String shortHint = "Type to search  |  Up/Down select  |  O reopens";
        int room = panelW - 28 - savedW - 12;
        if (font.width(hint) <= room) {
            g.drawString(font, hint, panelX + 14, y, MUTED, false);
        } else if (font.width(shortHint) <= room) {
            g.drawString(font, shortHint, panelX + 14, y, MUTED, false);
        } else {
            g.drawString(font, fit("O reopens", room), panelX + 14, y, MUTED, false);
        }
    }

    // --------------------------------------------------------------- helpers
    private void refilter(boolean resetScroll) {
        filtered.clear();
        String query = search.trim().toLowerCase(Locale.ROOT);
        Set<Item> edited = new HashSet<>();
        for (Entry entry : all) {
            if (ItemSizeConfig.isModified(entry.item())) edited.add(entry.item());
        }
        for (Entry entry : all) {
            if (category == Category.EDITED && !edited.contains(entry.item())) continue;
            if (category != Category.ALL && category != Category.EDITED && entry.category() != category) continue;
            if (!query.isEmpty() && !entry.lower().contains(query) && !entry.id().contains(query)) continue;
            filtered.add(entry);
        }
        switch (sort) {
            case AZ -> filtered.sort(Comparator.comparing(Entry::lower));
            case ZA -> filtered.sort(Comparator.comparing(Entry::lower).reversed());
            case EDITED_FIRST -> filtered.sort(Comparator
                    .comparing((Entry entry) -> edited.contains(entry.item()) ? 0 : 1)
                    .thenComparing(Entry::lower));
        }
        if (resetScroll) {
            listScrollTarget = 0.0f;
            listScroll = 0.0f;
        } else {
            listScrollTarget = Math.max(0.0f, Math.min(listScrollTarget, maxListScroll()));
        }
    }

    private void refilterIfDynamic() {
        if (category == Category.EDITED || sort == Sort.EDITED_FIRST) refilter(false);
    }

    private void select(Entry entry) {
        if (entry == null) return;
        commitEdit();
        selected = entry;
        selectFade = 0.0f;
        editorScrollTarget = 0.0f;
        editorScroll = 0.0f;
    }

    private void ensureVisible(int index) {
        float rowTop = index * ROW_H;
        if (rowTop < listScrollTarget) {
            listScrollTarget = rowTop;
        } else if (rowTop + ROW_H > listScrollTarget + listH) {
            listScrollTarget = rowTop + ROW_H - listH;
        }
        listScrollTarget = Math.max(0.0f, Math.min(listScrollTarget, maxListScroll()));
    }

    private void applyValue(Dimension dimension, float value) {
        Item item = selected.item();
        if (linkViews) {
            for (Dimension other : DIMS) ItemSizeConfig.set(item, other, value);
        } else {
            ItemSizeConfig.set(item, dimension, value);
        }
    }

    private void sliderTo(int index, double mouseX) {
        float t = (float) Math.max(0.0, Math.min(1.0, (mouseX - ctlX) / (double) trackW));
        float value = MIN_SCALE + t * (MAX_SCALE - MIN_SCALE);
        value = Math.round(value * 100.0f) / 100.0f;
        if (Math.abs(value - 1.0f) < 0.03f) value = 1.0f;
        applyValue(DIMS[index], value);
    }

    private void scrollbarTo(double mouseY) {
        int contentH = filtered.size() * ROW_H;
        if (contentH <= listH) return;
        int thumbH = Math.max(14, listH * listH / contentH);
        float t = (float) ((mouseY - listY - thumbH / 2.0) / (listH - thumbH));
        listScrollTarget = Math.max(0.0f, Math.min(1.0f, t)) * (contentH - listH);
        listScroll = listScrollTarget;
    }

    private void beginEdit(Dimension dimension) {
        editing = dimension;
        editBuffer = formatNumber(ItemSizeConfig.profile(selected.item()).get(dimension));
        editFresh = true;
    }

    private void commitEdit() {
        if (editing == null) return;
        try {
            float value = Float.parseFloat(editBuffer.replace(',', '.'));
            if (value > 10.0f) value /= 100.0f;
            applyValue(editing, ItemSizeConfig.clamp(value));
        } catch (NumberFormatException ignored) {
            // keep the old value
        }
        editing = null;
        editBuffer = "";
        ItemSizeConfig.save();
        refilterIfDynamic();
    }

    private String fit(String value, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(value) <= maxWidth) return value;
        String result = value;
        while (result.length() > 1 && font.width(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private static String formatNumber(float value) {
        String text = String.format(Locale.US, "%.2f", value);
        text = text.replaceAll("0+$", "");
        text = text.replaceAll("\\.$", "");
        return text;
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int blend(int a, int b, float t) {
        int aa = (a >>> 24) & 255, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24) & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int alpha = (int) (aa + (ba - aa) * t);
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int blue = (int) (ab + (bb - ab) * t);
        return (alpha << 24) | (r << 16) | (g << 8) | blue;
    }

    // ----------------------------------------------------------------- input
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_1) return super.mouseClicked(click, doubleClick);
        layout();
        double mx = click.x();
        double my = click.y();

        // header
        if (in(mx, my, closeX, closeY, 16, 16)) {
            onClose();
            return true;
        }
        if (in(mx, my, toggleX, toggleY, toggleW, 16)) {
            commitEdit();
            ItemSizeConfig.setEnabled(!ItemSizeConfig.isEnabled());
            play(SoundEvents.UI_BUTTON_CLICK, ItemSizeConfig.isEnabled() ? 1.2f : 0.7f);
            return true;
        }
        if (in(mx, my, resetAllX, resetAllY, resetAllW, 16)) {
            commitEdit();
            if (resetAllArmed()) {
                ItemSizeConfig.resetAll();
                resetAllArmedAt = 0L;
                refilterIfDynamic();
                play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.72f);
            } else {
                resetAllArmedAt = System.currentTimeMillis();
                play(SoundEvents.UI_BUTTON_CLICK, 0.9f);
            }
            return true;
        }

        // anything else ends a running text edit first
        commitEdit();

        // search
        if (in(mx, my, searchX, searchY, searchW, searchH)) {
            if (!search.isEmpty() && mx >= searchX + searchW - 18) {
                search = "";
                refilter(true);
            }
            searchFocused = true;
            play(SoundEvents.UI_BUTTON_CLICK, 1.05f);
            return true;
        }
        searchFocused = false;

        // chips
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            if (in(mx, my, chipX[i], chipY[i], chipW[i], 14)) {
                category = categories[i];
                refilter(true);
                play(SoundEvents.UI_BUTTON_CLICK, 1.1f);
                return true;
            }
        }
        // sort
        if (in(mx, my, sortX, sortY, sortW, sortH)) {
            sort = sort.next();
            refilter(true);
            play(SoundEvents.UI_BUTTON_CLICK, 1.0f);
            return true;
        }
        // list
        if (in(mx, my, listX, listY, listW, listH)) {
            if (mx >= listX + listW - 8 && filtered.size() * ROW_H > listH) {
                draggingScrollbar = true;
                scrollbarTo(my);
                return true;
            }
            int index = (int) ((my - listY + listScroll) / ROW_H);
            if (index >= 0 && index < filtered.size()) {
                select(filtered.get(index));
                play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.12f);
            }
            return true;
        }
        // editor
        if (selected != null && in(mx, my, rightX, edTop, rightW, edH)) {
            boolean modified = ItemSizeConfig.isModified(selected.item());
            if (modified && in(mx, my, itemResetX, itemResetY, itemResetW, 18)) {
                ItemSizeConfig.reset(selected.item());
                refilterIfDynamic();
                play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.72f);
                return true;
            }
            for (int i = 0; i < 3; i++) {
                int cy = ctlTop(i);
                if (in(mx, my, fieldX, cy, fieldW, 16)) {
                    beginEdit(DIMS[i]);
                    play(SoundEvents.UI_BUTTON_CLICK, 1.2f);
                    return true;
                }
                if (in(mx, my, fieldX + fieldW + 6, cy, miniW, 16)) {
                    applyValue(DIMS[i], 1.0f);
                    ItemSizeConfig.save();
                    refilterIfDynamic();
                    play(SoundEvents.UI_BUTTON_CLICK, 0.94f);
                    return true;
                }
                if (in(mx, my, ctlX - 4, cy - 4, trackW + 8, 24)) {
                    dragging = DIMS[i];
                    sliderTo(i, mx);
                    play(SoundEvents.UI_BUTTON_CLICK, 1.2f);
                    return true;
                }
            }
            if (in(mx, my, linkX - 2, barY, 16 + font.width("LINK VIEWS") + 8, barH)) {
                linkViews = !linkViews;
                if (linkViews) {
                    // make all three views match the one that is farthest from 100%
                    float best = 1.0f;
                    for (Dimension dimension : DIMS) {
                        float value = ItemSizeConfig.profile(selected.item()).get(dimension);
                        if (Math.abs(value - 1.0f) > Math.abs(best - 1.0f)) best = value;
                    }
                    applyValue(Dimension.GROUND, best);
                    ItemSizeConfig.save();
                    refilterIfDynamic();
                }
                play(SoundEvents.UI_BUTTON_CLICK, linkViews ? 1.2f : 0.85f);
                return true;
            }
            for (int i = 0; i < PRESETS.length; i++) {
                if (in(mx, my, presetX[i], barY + 4, presetW, barH - 8)) {
                    for (Dimension dimension : DIMS) {
                        ItemSizeConfig.set(selected.item(), dimension, PRESETS[i]);
                    }
                    ItemSizeConfig.save();
                    refilterIfDynamic();
                    play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9f + i * 0.12f);
                    return true;
                }
            }
            return true;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_1) {
            if (dragging != null && selected != null) {
                layout();
                sliderTo(dragging.ordinal(), click.x());
                return true;
            }
            if (draggingScrollbar) {
                scrollbarTo(click.y());
                return true;
            }
        }
        return super.mouseDragged(click, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_1) {
            boolean changed = dragging != null;
            dragging = null;
            draggingScrollbar = false;
            if (changed) {
                ItemSizeConfig.save();
                refilterIfDynamic();
            }
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        layout();
        if (in(mouseX, mouseY, listX, listY, listW, listH)) {
            listScrollTarget = Math.max(0.0f, Math.min(maxListScroll(),
                    listScrollTarget - (float) verticalAmount * ROW_H * 2.0f));
            return true;
        }
        if (selected != null && in(mouseX, mouseY, rightX, edTop, rightW, edH)) {
            for (int i = 0; i < 3; i++) {
                int cy = ctlTop(i);
                if (in(mouseX, mouseY, ctlX - 4, cy - 6, trackW + 8, 28)) {
                    float current = ItemSizeConfig.profile(selected.item()).get(DIMS[i]);
                    float next = Math.round((current + (verticalAmount > 0 ? 0.05f : -0.05f)) * 100.0f) / 100.0f;
                    applyValue(DIMS[i], ItemSizeConfig.clamp(next));
                    ItemSizeConfig.save();
                    refilterIfDynamic();
                    return true;
                }
            }
            if (maxEditorScroll > 0) {
                editorScrollTarget = Math.max(0.0f, Math.min(maxEditorScroll,
                        editorScrollTarget - (float) verticalAmount * 24.0f));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        // ignore the character of the key press that opened this screen
        if (System.currentTimeMillis() - openedAt < 200L) return true;
        int codePoint = event.codepoint();
        if (Character.isISOControl(codePoint)) return super.charTyped(event);
        if (editing != null) {
            boolean digit = codePoint >= '0' && codePoint <= '9';
            if (digit || codePoint == '.' || codePoint == ',') {
                if (editFresh) {
                    editBuffer = "";
                    editFresh = false;
                }
                if (editBuffer.length() < 6) editBuffer += (char) (codePoint == ',' ? '.' : codePoint);
            }
            return true;
        }
        if (search.length() < 32) {
            search += new String(Character.toChars(codePoint));
            searchFocused = true;
            refilter(true);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (editing != null) {
                editing = null;
                editBuffer = "";
            } else if (!search.isEmpty()) {
                search = "";
                searchFocused = false;
                refilter(true);
            } else {
                onClose();
            }
            return true;
        }
        if (editing != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitEdit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (editFresh) {
                    editBuffer = "";
                    editFresh = false;
                } else if (!editBuffer.isEmpty()) {
                    editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
                }
                return true;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            refilter(true);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            searchFocused = false;
            return true;
        }
        int move = 0;
        if (keyCode == GLFW.GLFW_KEY_DOWN) move = 1;
        if (keyCode == GLFW.GLFW_KEY_UP) move = -1;
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) move = 8;
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) move = -8;
        if (move != 0 && !filtered.isEmpty()) {
            int index = filtered.indexOf(selected);
            int next = index < 0 ? 0 : Math.max(0, Math.min(filtered.size() - 1, index + move));
            if (next != index) {
                select(filtered.get(next));
                ensureVisible(next);
                play(SoundEvents.UI_BUTTON_CLICK, 1.3f);
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        commitEdit();
        ItemSizeConfig.save();
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.66f);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class Particle {
        final float x;
        final float y;
        final float speed;
        final float phase;
        final float size;

        Particle(float x, float y, float speed, float phase, float size) {
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.phase = phase;
            this.size = size;
        }
    }
}
