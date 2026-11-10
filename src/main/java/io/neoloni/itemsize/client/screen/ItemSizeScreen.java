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
 * ItemSize editor, simple version: item list with search and a few filters on the left,
 * one big live preview with one big size slider on the right.
 * The whole layout is computed from the window size, so nothing can overlap.
 */
public final class ItemSizeScreen extends Screen {
    // ---------------------------------------------------------------- palette
    private static final int DARK = 0xFF140F33;
    private static final int PINK = 0xFFFF5CA8;
    private static final int VIOLET = 0xFFA970FF;
    private static final int CYAN = 0xFF3DE8FF;
    private static final int YELLOW = 0xFFFFD24D;
    private static final int GREEN = 0xFF5CF2A0;
    private static final int RED = 0xFFFF6B7E;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int TEXT = 0xFFEDE8FF;
    private static final int MUTED = 0xFFA79BD6;
    private static final int BTN = 0xFF2A1F5E;
    private static final int BTN_HOVER = 0xFF3E2F85;

    private static final float STEP = 0.05f;
    private static final float SLIDER_MIN = 0.25f;
    private static final float SLIDER_MAX = 3.00f;
    private static final int ROW_H = 24;
    private static final int HEADER_H = 40;
    private static final int BTN_H = 18;
    private static final int CHIP_H = 16;
    private static final int TAB_H = 22;
    private static final int BIG = 24;

    private static final Dimension[] DIMS = {Dimension.GROUND, Dimension.HAND, Dimension.INVENTORY};
    private static final String[] VIEW_LABEL = {"GROUND", "HAND", "INVENTORY"};
    private static final int[] VIEW_COLOR = {CYAN, PINK, YELLOW};
    private static final float[] PRESETS = {0.5f, 1.0f, 1.5f, 2.0f};

    private enum Category {ALL, BLOCK, TOOL, ARMOR, EDITED, OTHER}

    private static final Category[] CHIPS = {Category.ALL, Category.BLOCK, Category.TOOL, Category.ARMOR, Category.EDITED};
    private static final String[] CHIP_LABEL = {"ALL", "BLOCK", "TOOL", "ARMOR", ""};
    private static final int[] CHIP_COLOR = {VIOLET, CYAN, YELLOW, PINK, GREEN};

    private record Entry(Item item, ItemStack stack, String name, String lower, String id, Category category) {}

    // ------------------------------------------------------------------ state
    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private final Particle[] particles = new Particle[46];
    private Entry selected;

    private String search = "";
    private boolean searchFocused;
    private Category category = Category.ALL;
    private boolean linkViews = true;
    private int activeView;

    private boolean editing;
    private String editBuffer = "";
    private boolean editFresh;

    private boolean draggingSlider;
    private boolean draggingScrollbar;

    private float listScroll, listScrollTarget;
    private float opening;
    private float selectFade = 1.0f;
    private float toggleAnim = 1.0f;
    private float linkAnim = 1.0f;
    private final float[] shown = {1.0f, 1.0f, 1.0f};
    private int hoverRow = -1;
    private float hoverEase;
    private long openedAt;
    private long resetAllArmedAt;

    // ----------------------------------------------------------------- layout
    private int panelX, panelY, panelW, panelH;
    private int leftX, leftW, rightX, rightW, top, bottom;
    private int searchX, searchY, searchW, searchH;
    private final int[] chipX = new int[CHIPS.length];
    private final int[] chipY = new int[CHIPS.length];
    private final int[] chipW = new int[CHIPS.length];
    private int listX, listY, listW, listH;
    private int closeX, closeY, resetAllX, resetAllY, resetAllW, toggleX, toggleY, toggleW;
    private int itemResetX, itemResetY, itemResetW;
    private final int[] tabX = new int[3];
    private int tabY, tabW;
    private int prevX, prevY, prevW, prevH;
    private int ctrlY, minusX, plusX, sliderX, sliderW, pctX, pctW;
    private int presetsY, presetW;
    private final int[] presetX = new int[PRESETS.length];
    private int linkX, linkW;

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
        if (item instanceof BlockItem) return Category.BLOCK;
        if (stack.has(DataComponents.EQUIPPABLE)) return Category.ARMOR;
        if (stack.has(DataComponents.TOOL)) return Category.TOOL;
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
        panelW = Math.min(760, width - 16);
        panelH = Math.min(430, height - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        int pad = 14;
        top = panelY + HEADER_H + 8;
        bottom = panelY + panelH - 12;

        leftX = panelX + pad;
        leftW = Math.max(120, Math.min(230, (int) (panelW * 0.36f)));
        rightX = leftX + leftW + 12;
        rightW = panelX + panelW - pad - rightX;

        // header buttons (right aligned)
        closeX = panelX + panelW - pad - 18;
        closeY = panelY + 11;
        resetAllW = font.width(resetAllLabel()) + 16;
        resetAllX = closeX - 6 - resetAllW;
        resetAllY = closeY;
        toggleW = 40;
        toggleX = resetAllX - 6 - toggleW;
        toggleY = closeY;

        // left column
        searchX = leftX;
        searchY = top;
        searchW = leftW;
        searchH = 24;
        int cx = leftX;
        int cy = searchY + searchH + 6;
        for (int i = 0; i < CHIPS.length; i++) {
            int w = CHIP_LABEL[i].isEmpty() ? 24 : font.width(CHIP_LABEL[i]) + 14;
            if (cx + w > leftX + leftW && cx > leftX) {
                cx = leftX;
                cy += CHIP_H + 4;
            }
            chipX[i] = cx;
            chipY[i] = cy;
            chipW[i] = w;
            cx += w + 4;
        }
        listX = leftX;
        listY = cy + CHIP_H + 8;
        listW = leftW;
        listH = Math.max(30, bottom - listY);

        // right column
        itemResetW = font.width("RESET") + 16;
        itemResetX = rightX + rightW - itemResetW;
        itemResetY = top + 11;
        tabY = top + 46;
        int tabGap = 4;
        tabW = (rightW - 2 * tabGap) / 3;
        for (int i = 0; i < 3; i++) {
            tabX[i] = rightX + i * (tabW + tabGap);
        }
        prevX = rightX;
        prevW = rightW;
        prevY = tabY + TAB_H + 6;
        ctrlY = bottom - 56;
        presetsY = ctrlY + BIG + 10;
        prevH = Math.max(36, ctrlY - 8 - prevY);

        pctW = 72;
        pctX = rightX + rightW - pctW;
        minusX = rightX;
        plusX = pctX - 6 - BIG;
        sliderX = minusX + BIG + 8;
        sliderW = Math.max(30, plusX - 8 - sliderX);

        presetW = font.width("200%") + 14;
        for (int i = 0; i < PRESETS.length; i++) {
            presetX[i] = rightX + i * (presetW + 5);
        }
        linkW = 28 + 8 + font.width("ALL VIEWS");
        linkX = rightX + rightW - linkW;
    }

    private String resetAllLabel() {
        return resetAllArmed() ? "SURE?" : "RESET ALL";
    }

    private boolean resetAllArmed() {
        return System.currentTimeMillis() - resetAllArmedAt < 2500L;
    }

    private float currentValue() {
        return ItemSizeConfig.profile(selected.item()).get(DIMS[activeView]);
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
        linkAnim += ((linkViews ? 1.0f : 0.0f) - linkAnim) * 0.3f;
        listScroll += (listScrollTarget - listScroll) * 0.35f;
        if (Math.abs(listScrollTarget - listScroll) < 0.4f) listScroll = listScrollTarget;
        if (selected != null) {
            for (int i = 0; i < DIMS.length; i++) {
                float target = ItemSizeConfig.profile(selected.item()).get(DIMS[i]);
                shown[i] += (target - shown[i]) * 0.28f;
            }
        }

        g.fillGradient(0, 0, width, height, 0xD00A0620, 0xD0050B20);
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

        g.pose().popMatrix();
    }

    private void renderParticles(GuiGraphics g) {
        long now = System.currentTimeMillis();
        for (Particle particle : particles) {
            float drift = (now * 0.000025f * particle.speed) % 1.0f;
            float px = particle.x * width + (float) Math.sin(now * 0.0005f + particle.phase) * 12.0f;
            float py = (particle.y + drift) % 1.0f * height;
            int alpha = 40 + (int) (50 * (0.5 + 0.5 * Math.sin(now * 0.001f + particle.phase)));
            int base = particle.phase < 2.1f ? PINK : particle.phase < 4.2f ? CYAN : VIOLET;
            int color = (base & 0x00FFFFFF) | (alpha << 24);
            int size = particle.size > 1.25f ? 2 : 1;
            g.fill((int) px, (int) py, (int) px + size, (int) py + size, color);
        }
    }

    private void drawPanel(GuiGraphics g) {
        long now = System.currentTimeMillis();
        // soft glow
        g.fill(panelX - 6, panelY - 6, panelX + panelW + 6, panelY + panelH + 6, 0x1EA970FF);
        g.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, 0x30FF5CA8);
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, 0xF5200F4D, 0xF50A1236);
        g.fillGradient(panelX, panelY + 3, panelX + panelW, panelY + HEADER_H, 0x40FF5CA8, 0x00FF5CA8);
        // rainbow top bar
        for (int x = 0; x < panelW; x += 2) {
            float t = x / (float) panelW;
            int color = t < 0.5f ? blend(PINK, VIOLET, t * 2.0f) : blend(VIOLET, CYAN, (t - 0.5f) * 2.0f);
            g.fill(panelX + x, panelY, Math.min(panelX + x + 2, panelX + panelW), panelY + 3, color);
        }
        // side + bottom borders
        g.fillGradient(panelX, panelY, panelX + 1, panelY + panelH, 0xCCFF5CA8, 0xCC3DE8FF);
        g.fillGradient(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0xCC3DE8FF, 0xCCFF5CA8);
        g.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xCC6A55C8);
        // animated light on the header line
        float sweep = (now % 3000L) / 3000.0f;
        int sweepX = panelX + 14 + Math.round(sweep * (panelW - 28));
        g.fill(panelX + 14, panelY + HEADER_H - 1, panelX + panelW - 14, panelY + HEADER_H, 0x55A970FF);
        g.fill(Math.max(panelX + 14, sweepX - 34), panelY + HEADER_H - 1,
                Math.min(panelX + panelW - 14, sweepX + 34), panelY + HEADER_H, 0xDDFF5CA8);
    }

    private void drawHeader(GuiGraphics g, int mouseX, int mouseY) {
        g.pose().pushMatrix();
        g.pose().translate(panelX + 16, panelY + 13);
        g.pose().scale(1.7f, 1.7f);
        g.drawString(font, "ITEM", 0, 0, WHITE, false);
        g.drawString(font, "SIZE", font.width("ITEM"), 0, CYAN, false);
        g.pose().popMatrix();

        // ON / OFF pill
        boolean on = ItemSizeConfig.isEnabled();
        boolean toggleHover = in(mouseX, mouseY, toggleX, toggleY, toggleW, BTN_H);
        int pillColor = blend(0xFF5A1E3A, 0xFF1B6B4A, toggleAnim);
        if (toggleHover) pillColor = blend(pillColor, WHITE, 0.14f);
        g.fill(toggleX, toggleY, toggleX + toggleW, toggleY + BTN_H, pillColor);
        g.fill(toggleX, toggleY, toggleX + toggleW, toggleY + 2, on ? GREEN : RED);
        int knobX = toggleX + 4 + Math.round(toggleAnim * (toggleW - 18));
        g.fill(knobX, toggleY + 5, knobX + 10, toggleY + BTN_H - 4, on ? GREEN : RED);
        String label = on ? "ON" : "OFF";
        int labelX = on ? toggleX + 7 : toggleX + toggleW - 7 - font.width(label);
        g.drawString(font, label, labelX, toggleY + 6, WHITE, false);

        drawButton(g, resetAllX, resetAllY, resetAllW, BTN_H, resetAllLabel(),
                in(mouseX, mouseY, resetAllX, resetAllY, resetAllW, BTN_H),
                resetAllArmed() ? 0xFF6B2F55 : BTN, resetAllArmed() ? RED : PINK);
        boolean closeHover = in(mouseX, mouseY, closeX, closeY, 18, BTN_H);
        g.fill(closeX, closeY, closeX + 18, closeY + BTN_H, closeHover ? 0xFF8A3358 : BTN);
        g.fill(closeX, closeY, closeX + 18, closeY + 2, closeHover ? RED : VIOLET);
        g.drawCenteredString(font, "x", closeX + 9, closeY + 6, WHITE);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String label,
                            boolean hover, int baseColor, int accent) {
        g.fill(x, y, x + w, y + h, hover ? blend(baseColor, WHITE, 0.16f) : baseColor);
        g.fill(x, y, x + w, y + 2, accent);
        g.drawCenteredString(font, label, x + w / 2, y + (h - font.lineHeight) / 2 + 1, WHITE);
    }

    // ------------------------------------------------------------- left side
    private void drawLeft(GuiGraphics g, int mouseX, int mouseY) {
        // search field
        boolean searchHover = in(mouseX, mouseY, searchX, searchY, searchW, searchH);
        g.fill(searchX, searchY, searchX + searchW, searchY + searchH,
                searchFocused || searchHover ? 0xFF251A63 : 0xFF1A1245);
        g.fillGradient(searchX, searchY, searchX + 2, searchY + searchH, searchFocused ? PINK : VIOLET, searchFocused ? CYAN : 0xFF5A47B0);
        int iy = searchY + (searchH - 10) / 2;
        g.fill(searchX + 8, iy, searchX + 13, iy + 5, CYAN);
        g.fill(searchX + 9, iy + 1, searchX + 12, iy + 4, 0xFF1A1245);
        g.fill(searchX + 13, iy + 5, searchX + 15, iy + 7, CYAN);
        boolean caret = searchFocused && (System.currentTimeMillis() / 450L) % 2L == 0L;
        String shownText = search.isEmpty() ? "Search..." : fit(search, searchW - 56);
        g.drawString(font, shownText + (caret ? "|" : ""), searchX + 22, searchY + (searchH - 8) / 2,
                search.isEmpty() ? MUTED : WHITE, false);
        if (!search.isEmpty()) {
            boolean xHover = in(mouseX, mouseY, searchX + searchW - 20, searchY + 2, 18, searchH - 4);
            g.drawCenteredString(font, "x", searchX + searchW - 11, searchY + (searchH - 8) / 2,
                    xHover ? WHITE : MUTED);
        }

        // filter chips
        for (int i = 0; i < CHIPS.length; i++) {
            boolean active = CHIPS[i] == category;
            boolean hover = in(mouseX, mouseY, chipX[i], chipY[i], chipW[i], CHIP_H);
            int color = CHIP_COLOR[i];
            int bg = active ? blend(DARK, color, 0.38f) : hover ? 0xFF2A1F5E : 0xFF1B1346;
            g.fill(chipX[i], chipY[i], chipX[i] + chipW[i], chipY[i] + CHIP_H, bg);
            g.fill(chipX[i], chipY[i] + CHIP_H - 2, chipX[i] + chipW[i], chipY[i] + CHIP_H,
                    active ? color : hover ? blend(DARK, color, 0.6f) : blend(DARK, color, 0.28f));
            if (CHIP_LABEL[i].isEmpty()) {
                drawDiamond(g, chipX[i] + chipW[i] / 2, chipY[i] + (CHIP_H - 2) / 2,
                        active || hover ? color : blend(DARK, color, 0.7f));
            } else {
                g.drawCenteredString(font, CHIP_LABEL[i], chipX[i] + chipW[i] / 2, chipY[i] + 4,
                        active ? WHITE : hover ? TEXT : MUTED);
            }
        }

        drawList(g, mouseX, mouseY);
    }

    private void drawDiamond(GuiGraphics g, int cx, int cy, int color) {
        for (int r = -3; r <= 3; r++) {
            int half = 3 - Math.abs(r);
            g.fill(cx - half, cy + r, cx + half + 1, cy + r + 1, color);
        }
    }

    private int maxListScroll() {
        return Math.max(0, filtered.size() * ROW_H - listH);
    }

    private void drawList(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(listX, listY, listX + listW, listY + listH, 0x6610082C);
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
            g.drawCenteredString(font, "-", listX + listW / 2, listY + 18, MUTED);
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
                g.fillGradient(listX, y, listX + listW - 6, y + ROW_H - 1, 0xFF3D2B8A, 0xFF2A1E66);
                g.fillGradient(listX, y, listX + 3, y + ROW_H - 1, PINK, VIOLET);
            } else if (hovered) {
                g.fill(listX, y, listX + listW - 6, y + ROW_H - 1, 0xFF2A1F5E);
                g.fill(listX, y, listX + Math.max(1, Math.round(3 * hoverEase)), y + ROW_H - 1, CYAN);
            }
            int lift = hovered ? -1 : 0;
            g.renderItem(entry.stack(), listX + 8, y + 4 + lift);
            g.drawString(font, fit(entry.name(), textW), listX + 30, y + 8,
                    isSelected ? WHITE : hovered ? TEXT : 0xFFBDB4E6, false);
            if (ItemSizeConfig.isModified(entry.item())) {
                g.fill(listX + listW - 17, y + 9, listX + listW - 12, y + 14, PINK);
            }
        }
        g.disableScissor();

        int contentH = filtered.size() * ROW_H;
        if (contentH > listH) {
            int thumbH = Math.max(14, listH * listH / contentH);
            int thumbY = listY + Math.round(listScroll / (float) (contentH - listH) * (listH - thumbH));
            boolean hot = draggingScrollbar || in(mouseX, mouseY, listX + listW - 8, listY, 8, listH);
            g.fill(listX + listW - 5, listY, listX + listW - 2, listY + listH, 0x33000000);
            g.fill(listX + listW - 5, thumbY, listX + listW - 2, thumbY + thumbH, hot ? PINK : 0xFF7A6BC4);
        }
    }

    // ----------------------------------------------------------- right side
    private void drawEditor(GuiGraphics g, int mouseX, int mouseY) {
        if (selected == null) return;
        float ease = 1.0f - (float) Math.pow(1.0f - selectFade, 3.0);
        g.pose().pushMatrix();
        g.pose().translate((1.0f - ease) * 14.0f, 0.0f);

        // title: big icon + name + reset
        g.pose().pushMatrix();
        g.pose().translate(rightX + 2, top + 4);
        g.pose().scale(2.0f, 2.0f);
        g.renderItem(selected.stack(), 0, 0);
        g.pose().popMatrix();
        float nameScale = 1.4f;
        int nameW = (int) ((itemResetX - (rightX + 44) - 10) / nameScale);
        g.pose().pushMatrix();
        g.pose().translate(rightX + 44, top + 15);
        g.pose().scale(nameScale, nameScale);
        g.drawString(font, fit(selected.name(), nameW), 0, 0, WHITE, false);
        g.pose().popMatrix();
        boolean modified = ItemSizeConfig.isModified(selected.item());
        boolean resetHover = modified && in(mouseX, mouseY, itemResetX, itemResetY, itemResetW, BTN_H);
        drawButton(g, itemResetX, itemResetY, itemResetW, BTN_H, "RESET", resetHover,
                modified ? BTN : 0xFF1B1346, modified ? PINK : 0xFF3A2F66);

        // view tabs
        for (int i = 0; i < 3; i++) {
            drawTab(g, i, mouseX, mouseY);
        }

        // big live preview
        drawPreview(g, activeView, prevX, prevY, prevW, prevH, System.currentTimeMillis());
        if (!ItemSizeConfig.isEnabled()) {
            g.fill(prevX, prevY, prevX + prevW, prevY + prevH, 0x99100A28);
            g.drawCenteredString(font, "OFF", prevX + prevW / 2, prevY + prevH / 2 - 4, RED);
        }

        drawControls(g, mouseX, mouseY);

        g.pose().popMatrix();
    }

    private void drawTab(GuiGraphics g, int i, int mouseX, int mouseY) {
        boolean active = i == activeView;
        boolean hover = in(mouseX, mouseY, tabX[i], tabY, tabW, TAB_H);
        int accent = VIEW_COLOR[i];
        int bg = active ? blend(DARK, accent, 0.30f) : hover ? 0xFF2A1F5E : 0xFF1B1346;
        g.fill(tabX[i], tabY, tabX[i] + tabW, tabY + TAB_H, bg);
        g.fill(tabX[i], tabY + TAB_H - 3, tabX[i] + tabW, tabY + TAB_H,
                active ? accent : hover ? blend(DARK, accent, 0.6f) : blend(DARK, accent, 0.3f));
        String name = VIEW_LABEL[i];
        String pct = Math.round(ItemSizeConfig.profile(selected.item()).get(DIMS[i]) * 100.0f) + "%";
        int textY = tabY + (TAB_H - 3 - font.lineHeight) / 2 + 1;
        int nameColor = active ? WHITE : hover ? TEXT : MUTED;
        if (font.width(name) + font.width(pct) + 18 <= tabW) {
            g.drawString(font, name, tabX[i] + 7, textY, nameColor, false);
            g.drawString(font, pct, tabX[i] + tabW - 7 - font.width(pct), textY, active ? accent : 0xFF7F73B8, false);
        } else {
            g.drawCenteredString(font, fit(name, tabW - 8), tabX[i] + tabW / 2, textY, nameColor);
        }
    }

    private void drawControls(GuiGraphics g, int mouseX, int mouseY) {
        float value = currentValue();
        int accent = VIEW_COLOR[activeView];

        // minus / plus
        boolean minusHover = in(mouseX, mouseY, minusX, ctrlY, BIG, BIG);
        boolean plusHover = in(mouseX, mouseY, plusX, ctrlY, BIG, BIG);
        drawButton(g, minusX, ctrlY, BIG, BIG, "-", minusHover, BTN, accent);
        drawButton(g, plusX, ctrlY, BIG, BIG, "+", plusHover, BTN, accent);

        // slider
        boolean sliderHover = draggingSlider || in(mouseX, mouseY, sliderX - 6, ctrlY, sliderW + 12, BIG);
        int ty = ctrlY + 8;
        g.fill(sliderX, ty, sliderX + sliderW, ty + 8, 0xFF241A55);
        float progress = Math.max(0.0f, Math.min(1.0f, (value - SLIDER_MIN) / (SLIDER_MAX - SLIDER_MIN)));
        int knobX = sliderX + Math.round(progress * sliderW);
        g.fill(sliderX, ty, knobX, ty + 8, accent);
        g.fill(sliderX, ty, knobX, ty + 2, blend(accent, WHITE, 0.45f));
        for (float mark : new float[]{1.0f, 2.0f}) {
            int mx = sliderX + Math.round((mark - SLIDER_MIN) / (SLIDER_MAX - SLIDER_MIN) * sliderW);
            g.fill(mx, ty - 3, mx + 1, ty + 11, mark == 1.0f ? 0xCCFFFFFF : 0x66FFFFFF);
        }
        int half = sliderHover ? 7 : 6;
        g.fill(knobX - half, ctrlY, knobX + half, ctrlY + BIG, WHITE);
        g.fill(knobX - 2, ctrlY + 4, knobX + 2, ctrlY + BIG - 4, accent);

        // big percent box (click to type)
        boolean pctHover = in(mouseX, mouseY, pctX, ctrlY, pctW, BIG);
        g.fill(pctX, ctrlY, pctX + pctW, ctrlY + BIG, editing ? 0xFF251A63 : pctHover ? 0xFF1F1654 : DARK);
        g.fill(pctX, ctrlY + BIG - 2, pctX + pctW, ctrlY + BIG, editing ? accent : 0xFF4A3C8F);
        String text = editing ? editBuffer + "%" : Math.round(value * 100.0f) + "%";
        float sc = 1.5f;
        float tw = font.width(text) * sc;
        float tx = pctX + (pctW - tw) / 2.0f;
        float tyy = ctrlY + (BIG - 2 - font.lineHeight * sc) / 2.0f + 1.0f;
        g.pose().pushMatrix();
        g.pose().translate(tx, tyy);
        g.pose().scale(sc, sc);
        g.drawString(font, text, 0, 0, WHITE, false);
        g.pose().popMatrix();
        if (editing && (System.currentTimeMillis() / 450L) % 2L == 0L) {
            int caretX = Math.round(tx + font.width(editBuffer) * sc);
            g.fill(caretX + 1, ctrlY + 4, caretX + 2, ctrlY + BIG - 5, accent);
        }

        // presets
        for (int i = 0; i < PRESETS.length; i++) {
            boolean active = Math.abs(value - PRESETS[i]) < 0.001f;
            boolean hover = in(mouseX, mouseY, presetX[i], presetsY, presetW, BTN_H);
            g.fill(presetX[i], presetsY, presetX[i] + presetW, presetsY + BTN_H,
                    active ? blend(DARK, accent, 0.38f) : hover ? BTN_HOVER : BTN);
            g.fill(presetX[i], presetsY + BTN_H - 2, presetX[i] + presetW, presetsY + BTN_H,
                    active ? accent : hover ? blend(DARK, accent, 0.6f) : blend(DARK, accent, 0.3f));
            g.drawCenteredString(font, Math.round(PRESETS[i] * 100.0f) + "%", presetX[i] + presetW / 2,
                    presetsY + 5, active || hover ? WHITE : TEXT);
        }

        // "all views" switch
        boolean linkHover = in(mouseX, mouseY, linkX, presetsY, linkW, BTN_H);
        int pillBg = blend(0xFF3A2F66, 0xFF1B6B4A, linkAnim);
        g.fill(linkX, presetsY + 2, linkX + 28, presetsY + BTN_H - 2, linkHover ? blend(pillBg, WHITE, 0.14f) : pillBg);
        int pk = linkX + 2 + Math.round(linkAnim * 14);
        g.fill(pk, presetsY + 4, pk + 10, presetsY + BTN_H - 4, linkViews ? GREEN : 0xFF8C80C8);
        g.drawString(font, "ALL VIEWS", linkX + 36, presetsY + 5, linkViews ? WHITE : MUTED, false);
    }

    // ---------------------------------------------------------- live preview
    private void drawPreview(GuiGraphics g, int i, int x, int y, int w, int h, long now) {
        int accent = VIEW_COLOR[i];
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, blend(DARK, accent, 0.5f));
        g.fillGradient(x, y, x + w, y + h, 0xFF2B1A66, 0xFF0B1033);
        g.fill(x, y, x + w, y + 2, accent);
        g.enableScissor(x, y, x + w, y + h);
        int t = Math.min(w, h);
        float k = t / 48.0f;
        float s = shown[i];
        ItemStack stack = selected.stack();
        switch (DIMS[i]) {
            case GROUND -> {
                int groundY = y + Math.round(h * 0.74f);
                g.fillGradient(x, groundY, x + w, y + h, 0xFF1D4A38, 0xFF0F2A24);
                g.fill(x, groundY, x + w, groundY + 2, GREEN);
                float bob = (float) Math.sin(now * 0.004) * t * 0.025f;
                float itemPx = 16.0f * k * s;
                int cx = x + w / 2;
                int shadow = Math.round(Math.min(t * 0.9f, 14.0f * k * Math.max(0.6f, Math.min(s, 2.0f))));
                int shadowY = groundY + Math.round(t * 0.05f);
                g.fill(cx - shadow / 2, shadowY, cx + shadow / 2, shadowY + Math.max(2, Math.round(t * 0.035f)), 0x66000000);
                g.fill(cx - shadow / 3, shadowY - 1, cx + shadow / 3, shadowY, 0x44000000);
                float cy = groundY - itemPx * 0.5f - t * 0.06f - bob;
                drawItem(g, stack, cx, cy, k * s, 0.0f);
            }
            case INVENTORY -> {
                int slot = Math.round(20.0f * k);
                int sx = x + (w - slot) / 2;
                int sy = y + (h - slot) / 2;
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
                g.pose().translate(x + w / 2.0f + t * 0.16f, y + h + t * 0.05f);
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
        if (resetScroll) {
            listScrollTarget = 0.0f;
            listScroll = 0.0f;
        } else {
            listScrollTarget = Math.max(0.0f, Math.min(listScrollTarget, maxListScroll()));
        }
    }

    private void refilterIfDynamic() {
        if (category == Category.EDITED) refilter(false);
    }

    private void select(Entry entry) {
        if (entry == null) return;
        commitEdit();
        selected = entry;
        selectFade = 0.0f;
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

    /** Sets a size for the active view, or for all three views when "ALL VIEWS" is on. */
    private void applyValue(float value) {
        Item item = selected.item();
        float safe = ItemSizeConfig.clamp(value);
        if (linkViews) {
            for (Dimension other : DIMS) ItemSizeConfig.set(item, other, safe);
        } else {
            ItemSizeConfig.set(item, DIMS[activeView], safe);
        }
    }

    private void nudge(int direction) {
        float next = Math.round((currentValue() + direction * STEP) * 100.0f) / 100.0f;
        applyValue(next);
        ItemSizeConfig.save();
        refilterIfDynamic();
    }

    private void sliderTo(double mouseX) {
        float t = (float) Math.max(0.0, Math.min(1.0, (mouseX - sliderX) / (double) sliderW));
        float value = SLIDER_MIN + t * (SLIDER_MAX - SLIDER_MIN);
        value = Math.round(value / STEP) * STEP;
        value = Math.round(value * 100.0f) / 100.0f;
        applyValue(value);
    }

    private void scrollbarTo(double mouseY) {
        int contentH = filtered.size() * ROW_H;
        if (contentH <= listH) return;
        int thumbH = Math.max(14, listH * listH / contentH);
        float t = (float) ((mouseY - listY - thumbH / 2.0) / (listH - thumbH));
        listScrollTarget = Math.max(0.0f, Math.min(1.0f, t)) * (contentH - listH);
        listScroll = listScrollTarget;
    }

    private void beginEdit() {
        editing = true;
        editBuffer = Integer.toString(Math.round(currentValue() * 100.0f));
        editFresh = true;
    }

    private void commitEdit() {
        if (!editing) return;
        editing = false;
        if (!editBuffer.isEmpty() && selected != null) {
            try {
                int percent = Integer.parseInt(editBuffer);
                applyValue(percent / 100.0f);
            } catch (NumberFormatException ignored) {
                // keep the old value
            }
        }
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
        if (in(mx, my, closeX, closeY, 18, BTN_H)) {
            onClose();
            return true;
        }
        if (in(mx, my, toggleX, toggleY, toggleW, BTN_H)) {
            commitEdit();
            ItemSizeConfig.setEnabled(!ItemSizeConfig.isEnabled());
            play(SoundEvents.UI_BUTTON_CLICK, ItemSizeConfig.isEnabled() ? 1.2f : 0.7f);
            return true;
        }
        if (in(mx, my, resetAllX, resetAllY, resetAllW, BTN_H)) {
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
            if (!search.isEmpty() && mx >= searchX + searchW - 20) {
                search = "";
                refilter(true);
            }
            searchFocused = true;
            play(SoundEvents.UI_BUTTON_CLICK, 1.05f);
            return true;
        }
        searchFocused = false;

        // chips
        for (int i = 0; i < CHIPS.length; i++) {
            if (in(mx, my, chipX[i], chipY[i], chipW[i], CHIP_H)) {
                category = CHIPS[i];
                refilter(true);
                play(SoundEvents.UI_BUTTON_CLICK, 1.1f);
                return true;
            }
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
        if (selected != null && in(mx, my, rightX, top, rightW, bottom - top)) {
            if (ItemSizeConfig.isModified(selected.item())
                    && in(mx, my, itemResetX, itemResetY, itemResetW, BTN_H)) {
                ItemSizeConfig.reset(selected.item());
                refilterIfDynamic();
                play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.72f);
                return true;
            }
            for (int i = 0; i < 3; i++) {
                if (in(mx, my, tabX[i], tabY, tabW, TAB_H)) {
                    activeView = i;
                    play(SoundEvents.UI_BUTTON_CLICK, 1.0f + i * 0.15f);
                    return true;
                }
            }
            if (in(mx, my, minusX, ctrlY, BIG, BIG)) {
                nudge(-1);
                play(SoundEvents.UI_BUTTON_CLICK, 0.9f);
                return true;
            }
            if (in(mx, my, plusX, ctrlY, BIG, BIG)) {
                nudge(1);
                play(SoundEvents.UI_BUTTON_CLICK, 1.3f);
                return true;
            }
            if (in(mx, my, pctX, ctrlY, pctW, BIG)) {
                beginEdit();
                play(SoundEvents.UI_BUTTON_CLICK, 1.2f);
                return true;
            }
            if (in(mx, my, sliderX - 6, ctrlY, sliderW + 12, BIG)) {
                draggingSlider = true;
                sliderTo(mx);
                play(SoundEvents.UI_BUTTON_CLICK, 1.2f);
                return true;
            }
            for (int i = 0; i < PRESETS.length; i++) {
                if (in(mx, my, presetX[i], presetsY, presetW, BTN_H)) {
                    applyValue(PRESETS[i]);
                    ItemSizeConfig.save();
                    refilterIfDynamic();
                    play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9f + i * 0.12f);
                    return true;
                }
            }
            if (in(mx, my, linkX, presetsY, linkW, BTN_H)) {
                linkViews = !linkViews;
                if (linkViews) {
                    // make all views match the one that is shown
                    applyValue(currentValue());
                    ItemSizeConfig.save();
                    refilterIfDynamic();
                }
                play(SoundEvents.UI_BUTTON_CLICK, linkViews ? 1.2f : 0.85f);
                return true;
            }
            return true;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_1) {
            if (draggingSlider && selected != null) {
                layout();
                sliderTo(click.x());
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
            boolean changed = draggingSlider;
            draggingSlider = false;
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
        if (selected != null && in(mouseX, mouseY, rightX, tabY, rightW, bottom - tabY)) {
            nudge(verticalAmount > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        // ignore the character of the key press that opened this screen
        if (System.currentTimeMillis() - openedAt < 200L) return true;
        int codePoint = event.codepoint();
        if (Character.isISOControl(codePoint)) return super.charTyped(event);
        if (editing) {
            if (codePoint >= '0' && codePoint <= '9') {
                if (editFresh) {
                    editBuffer = "";
                    editFresh = false;
                }
                if (editBuffer.length() < 3) editBuffer += (char) codePoint;
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
            if (editing) {
                editing = false;
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
        if (editing) {
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
        if (selected != null && keyCode == GLFW.GLFW_KEY_RIGHT) {
            nudge(1);
            return true;
        }
        if (selected != null && keyCode == GLFW.GLFW_KEY_LEFT) {
            nudge(-1);
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
