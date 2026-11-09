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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Animated custom editor screen for ItemSize. */
public final class ItemSizeScreen extends Screen {
    private enum Page { BROWSE, DETAILS }

    private static final int PANEL_BG = 0xF20D1020;
    private static final int CARD_BG = 0xD91A2035;
    private static final int CARD_HOVER = 0xF02A3150;
    private static final int CYAN = 0xFF66E7F2;
    private static final int PURPLE = 0xFFB58CFF;
    private static final int MUTED = 0xFF97A4BC;
    private static final float MIN_SCALE = 0.10f;
    private static final float MAX_SCALE = 3.00f;

    private final List<Item> allItems = new ArrayList<>();
    private final Particle[] particles = new Particle[48];
    private Page page = Page.BROWSE;
    private Item selectedItem = Items.DIAMOND;
    private String search = "";
    private boolean searchFocused;
    private int scrollRow;
    private float opening = 0.0f;
    private float closePulse = 0.0f;
    private Dimension draggingDimension;
    private int panelX, panelY, panelW, panelH;
    private int gridX, gridY, columns, tileSize, gridRows;
    private int searchX, searchY, searchW, searchH;
    private int sliderX, sliderW;

    public ItemSizeScreen() {
        super(Component.literal("ItemSize"));
        BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .filter(ItemSizeConfig::isAllowed)
                .sorted(Comparator.comparing(item -> new ItemStack(item).getHoverName().getString()))
                .forEach(allItems::add);
        Random random = new Random(0x1A2B3C);
        for (int i = 0; i < particles.length; i++) {
            particles[i] = new Particle(random.nextFloat(), random.nextFloat(),
                    0.12f + random.nextFloat() * 0.28f,
                    random.nextFloat() * 6.28f,
                    0.4f + random.nextFloat() * 1.2f);
        }
    }

    @Override
    protected void init() {
        super.init();
        panelW = Math.min(704, width - 24);
        panelH = Math.min(420, height - 24);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.82f);
    }

    private void play(Holder<SoundEvent> sound, float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound.value(), pitch));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        opening = Math.min(1.0f, opening + 0.075f);
        closePulse = (float) (System.currentTimeMillis() % 2600L) / 2600.0f;

        graphics.fill(0, 0, width, height, 0xC9050914);
        renderParticles(graphics);

        float eased = 1.0f - (float) Math.pow(1.0f - opening, 3.0);
        float scale = 0.94f + eased * 0.06f;
        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2.0f, height / 2.0f);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-width / 2.0f, -height / 2.0f);

        drawPanel(graphics);
        if (page == Page.BROWSE) {
            renderBrowse(graphics, mouseX, mouseY);
        } else {
            renderDetails(graphics, mouseX, mouseY);
        }
        graphics.pose().popMatrix();
    }

    private void renderParticles(GuiGraphics graphics) {
        long now = System.currentTimeMillis();
        for (Particle particle : particles) {
            float drift = (now * 0.000025f * particle.speed) % 1.0f;
            float px = particle.x * width + (float) Math.sin(now * 0.0005f + particle.phase) * 12.0f;
            float py = (particle.y + drift) % 1.0f * height;
            int alpha = 35 + (int) (35 * (0.5 + 0.5 * Math.sin(now * 0.001f + particle.phase)));
            int color = (iColor(particle.phase) & 0x00FFFFFF) | (alpha << 24);
            int size = particle.size > 1.25f ? 2 : 1;
            graphics.fill((int) px, (int) py, (int) px + size, (int) py + size, color);
        }
    }

    private int iColor(float phase) {
        return Math.sin(phase) > 0 ? CYAN : PURPLE;
    }

    private void drawPanel(GuiGraphics g) {
        g.fill(panelX - 5, panelY - 5, panelX + panelW + 5, panelY + panelH + 5, 0x352F65A7);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        g.fill(panelX, panelY, panelX + panelW, panelY + 2, CYAN);
        g.fill(panelX, panelY + 2, panelX + panelW, panelY + 3, PURPLE);
        g.fill(panelX + 1, panelY + 1, panelX + 2, panelY + panelH - 1, 0xFF5D83C8);
        g.fill(panelX + panelW - 2, panelY + 1, panelX + panelW - 1, panelY + panelH - 1, 0xFF9D74DB);

        int sweepY = panelY + 44 + (int) (closePulse * (panelH - 54));
        g.fill(panelX + 3, sweepY, panelX + panelW - 3, sweepY + 1, 0x1F9BEAF5);
        g.drawString(font, "ITEMSIZE", panelX + 22, panelY + 15, 0xFFFFFFFF, false);
        g.drawString(font, "ITEM DIMENSION EDITOR", panelX + 112, panelY + 17, CYAN, false);
        g.drawString(font, "1.21.11  /  FABRIC", panelX + panelW - 132, panelY + 17, MUTED, false);
        g.fill(panelX + 20, panelY + 34, panelX + panelW - 20, panelY + 35, 0x553D4C70);
    }

    private void renderBrowse(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "SELECT AN ITEM", panelX + 22, panelY + 47, 0xFFE9ECFF, false);
        g.drawString(font, "Decorative & utility items. Three independent dimensions.", panelX + 22, panelY + 61, MUTED, false);

        searchX = panelX + 22;
        searchY = panelY + 82;
        searchW = Math.min(390, panelW - 278);
        searchH = 26;
        boolean searchHover = in(mouseX, mouseY, searchX, searchY, searchW, searchH);
        g.fill(searchX, searchY, searchX + searchW, searchY + searchH,
                searchFocused || searchHover ? 0xFF242D49 : 0xFF181F34);
        g.fill(searchX, searchY, searchX + 2, searchY + searchH, searchFocused ? CYAN : 0xFF596887);
        g.drawString(font, "⌕", searchX + 9, searchY + 8, CYAN, false);
        String shownSearch = search.isEmpty() ? "Search items..." : search;
        g.drawString(font, shownSearch + (searchFocused ? "▏" : ""), searchX + 25, searchY + 9,
                search.isEmpty() ? MUTED : 0xFFFFFFFF, false);

        List<Item> filtered = filteredItems();
        g.drawString(font, filtered.size() + " ITEMS", searchX, searchY + 34, MUTED, false);

        gridX = panelX + 22;
        gridY = searchY + 53;
        tileSize = 48;
        int gap = 8;
        columns = Math.max(1, Math.min(6, (searchW + gap) / (tileSize + gap)));
        int visibleHeight = panelY + panelH - 28 - gridY;
        gridRows = Math.max(1, visibleHeight / (tileSize + gap));
        int maxScroll = Math.max(0, (int) Math.ceil(filtered.size() / (double) columns) - gridRows);
        scrollRow = Math.max(0, Math.min(scrollRow, maxScroll));

        int start = scrollRow * columns;
        int end = Math.min(filtered.size(), start + gridRows * columns);
        for (int index = start; index < end; index++) {
            Item item = filtered.get(index);
            int local = index - start;
            int col = local % columns;
            int row = local / columns;
            int x = gridX + col * (tileSize + gap);
            int y = gridY + row * (tileSize + gap);
            boolean hovered = in(mouseX, mouseY, x, y, tileSize, tileSize);
            boolean isSelected = item == selectedItem;
            g.fill(x, y, x + tileSize, y + tileSize,
                    hovered ? CARD_HOVER : isSelected ? 0xFF29304B : CARD_BG);
            if (hovered || isSelected) {
                g.fill(x, y, x + tileSize, y + 1, hovered ? CYAN : PURPLE);
                g.fill(x, y + tileSize - 1, x + tileSize, y + tileSize, 0x885E72A5);
            }
            int lift = hovered ? 2 : 0;
            g.renderItem(new ItemStack(item), x + 16, y + 13 - lift);
            g.drawString(font, trim(new ItemStack(item).getHoverName().getString(), 8), x + 4, y + 37,
                    hovered ? 0xFFFFFFFF : 0xFFB7C2D9, false);
        }

        int rightX = panelX + panelW - 232;
        int rightY = panelY + 82;
        int rightW = 210;
        int rightH = panelH - 111;
        g.fill(rightX, rightY, rightX + rightW, rightY + rightH, CARD_BG);
        g.fill(rightX, rightY, rightX + rightW, rightY + 1, PURPLE);
        g.drawString(font, "PREVIEW", rightX + 13, rightY + 13, CYAN, false);
        int previewX = rightX + rightW / 2 - 8;
        int previewY = rightY + 55;
        g.fill(rightX + 72, rightY + 38, rightX + 138, rightY + 104, 0x66313C5B);
        g.renderItem(new ItemStack(selectedItem), previewX, previewY);
        String name = new ItemStack(selectedItem).getHoverName().getString();
        g.drawCenteredString(font, trim(name, 24), rightX + rightW / 2, rightY + 117, 0xFFFFFFFF);
        g.drawCenteredString(font, trim(ItemSizeConfig.id(selectedItem), 26), rightX + rightW / 2,
                rightY + 133, MUTED);
        g.drawString(font, "WORLD", rightX + 14, rightY + 164, CYAN, false);
        g.drawString(font, format(ItemSizeConfig.profile(selectedItem).ground), rightX + rightW - 53,
                rightY + 164, 0xFFFFFFFF, false);
        g.drawString(font, "HAND", rightX + 14, rightY + 184, PURPLE, false);
        g.drawString(font, format(ItemSizeConfig.profile(selectedItem).hand), rightX + rightW - 53,
                rightY + 184, 0xFFFFFFFF, false);
        g.drawString(font, "INVENTORY", rightX + 14, rightY + 204, CYAN, false);
        g.drawString(font, format(ItemSizeConfig.profile(selectedItem).inventory), rightX + rightW - 53,
                rightY + 204, 0xFFFFFFFF, false);

        int buttonY = rightY + rightH - 42;
        boolean buttonHover = in(mouseX, mouseY, rightX + 12, buttonY, rightW - 24, 28);
        g.fill(rightX + 12, buttonY, rightX + rightW - 12, buttonY + 28,
                buttonHover ? 0xFF384A72 : 0xFF273652);
        g.fill(rightX + 12, buttonY, rightX + rightW - 12, buttonY + 1, CYAN);
        g.drawCenteredString(font, "EDIT DIMENSIONS  ›", rightX + rightW / 2, buttonY + 10, 0xFFFFFFFF);
        g.drawString(font, "Press O anytime to reopen", panelX + 22, panelY + panelH - 16, MUTED, false);
    }

    private void renderDetails(GuiGraphics g, int mouseX, int mouseY) {
        int backX = panelX + 20;
        int backY = panelY + 48;
        boolean backHover = in(mouseX, mouseY, backX, backY, 74, 24);
        g.fill(backX, backY, backX + 74, backY + 24, backHover ? CARD_HOVER : CARD_BG);
        g.drawCenteredString(font, "‹  BACK", backX + 37, backY + 8, backHover ? CYAN : MUTED);

        String itemName = new ItemStack(selectedItem).getHoverName().getString();
        g.drawString(font, itemName.toUpperCase(Locale.ROOT), panelX + 112, panelY + 52, 0xFFFFFFFF, false);
        g.drawString(font, ItemSizeConfig.id(selectedItem), panelX + 112, panelY + 67, MUTED, false);
        g.drawString(font, "LIVE ITEM PREVIEW", panelX + 22, panelY + 102, CYAN, false);

        int previewBoxX = panelX + 22;
        int previewBoxY = panelY + 123;
        int previewBoxW = 148;
        int previewBoxH = 208;
        g.fill(previewBoxX, previewBoxY, previewBoxX + previewBoxW, previewBoxY + previewBoxH, CARD_BG);
        g.fill(previewBoxX, previewBoxY, previewBoxX + previewBoxW, previewBoxY + 1, PURPLE);
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() * 0.003f);
        int ringColor = blend(CYAN, PURPLE, pulse);
        g.fill(previewBoxX + 38, previewBoxY + 45, previewBoxX + 110, previewBoxY + 46, ringColor);
        g.fill(previewBoxX + 38, previewBoxY + 115, previewBoxX + 110, previewBoxY + 116, ringColor);
        g.fill(previewBoxX + 38, previewBoxY + 45, previewBoxX + 39, previewBoxY + 116, ringColor);
        g.fill(previewBoxX + 109, previewBoxY + 45, previewBoxX + 110, previewBoxY + 116, ringColor);
        g.renderItem(new ItemStack(selectedItem), previewBoxX + 69, previewBoxY + 78);
        g.drawCenteredString(font, "ITEM MODEL", previewBoxX + previewBoxW / 2, previewBoxY + 137, 0xFFE4E9FF);
        g.drawCenteredString(font, "Adjust each view", previewBoxX + previewBoxW / 2, previewBoxY + 153, MUTED);
        g.drawCenteredString(font, "independently", previewBoxX + previewBoxW / 2, previewBoxY + 165, MUTED);

        sliderX = panelX + 196;
        sliderW = panelW - 222;
        Dimension[] dimensions = {Dimension.GROUND, Dimension.HAND, Dimension.INVENTORY};
        String[] labels = {"01  /  WORLD SCALE", "02  /  HAND SCALE", "03  /  INVENTORY SCALE"};
        String[] hints = {"Dropped item on the ground", "First- and third-person held item", "Item icons inside inventory screens"};
        for (int i = 0; i < dimensions.length; i++) {
            int rowY = panelY + 112 + i * 70;
            float value = ItemSizeConfig.profile(selectedItem).get(dimensions[i]);
            g.fill(sliderX, rowY, sliderX + sliderW, rowY + 58, CARD_BG);
            g.fill(sliderX, rowY, sliderX + 3, rowY + 58, i == 1 ? PURPLE : CYAN);
            g.drawString(font, labels[i], sliderX + 13, rowY + 9, 0xFFF0F3FF, false);
            g.drawString(font, hints[i], sliderX + 13, rowY + 23, MUTED, false);
            int trackX = sliderX + 14;
            int trackY = rowY + 43;
            int trackW = sliderW - 94;
            g.fill(trackX, trackY, trackX + trackW, trackY + 4, 0xFF39435C);
            float progress = (value - MIN_SCALE) / (MAX_SCALE - MIN_SCALE);
            int knobX = trackX + Math.round(progress * trackW);
            g.fill(trackX, trackY, knobX, trackY + 4, i == 1 ? PURPLE : CYAN);
            g.fill(knobX - 4, trackY - 3, knobX + 5, trackY + 8, 0xFFFFFFFF);
            g.drawString(font, String.format(Locale.US, "%.0f%%", value * 100.0f),
                    sliderX + sliderW - 63, rowY + 37, 0xFFFFFFFF, false);
        }

        int resetX = sliderX;
        int resetY = panelY + panelH - 48;
        boolean resetHover = in(mouseX, mouseY, resetX, resetY, 120, 28);
        g.fill(resetX, resetY, resetX + 120, resetY + 28, resetHover ? 0xFF503B62 : 0xFF30243F);
        g.drawCenteredString(font, "RESET ITEM", resetX + 60, resetY + 10, 0xFFE8D7FF);
        g.drawString(font, "Range: 10% – 300%", resetX + 135, resetY + 10, MUTED, false);
        g.drawString(font, "Changes save automatically", panelX + panelW - 175, panelY + panelH - 16,
                MUTED, false);
    }

    private List<Item> filteredItems() {
        if (search.isBlank()) return allItems;
        String query = search.toLowerCase(Locale.ROOT);
        return allItems.stream().filter(item -> {
            String name = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
            String id = ItemSizeConfig.id(item).toLowerCase(Locale.ROOT);
            return name.contains(query) || id.contains(query);
        }).toList();
    }

    private void updateSlider(double mouseX, Dimension dimension) {
        int trackX = sliderX + 14;
        int trackW = sliderW - 94;
        float value = MIN_SCALE + (float) Math.max(0.0, Math.min(1.0, (mouseX - trackX) / trackW))
                * (MAX_SCALE - MIN_SCALE);
        value = Math.round(value * 100.0f) / 100.0f;
        ItemSizeConfig.set(selectedItem, dimension, value);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String format(float value) {
        return String.format(Locale.US, "%.0f%%", value * 100.0f);
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int blue = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | blue;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (button != GLFW.GLFW_MOUSE_BUTTON_1) return super.mouseClicked(click, doubleClick);
        if (page == Page.BROWSE) {
            if (in(mouseX, mouseY, searchX, searchY, searchW, searchH)) {
                searchFocused = true;
                play(SoundEvents.UI_BUTTON_CLICK, 1.05f);
                return true;
            }
            searchFocused = false;
            List<Item> filtered = filteredItems();
            int start = scrollRow * columns;
            int end = Math.min(filtered.size(), start + gridRows * columns);
            int gap = 8;
            for (int index = start; index < end; index++) {
                int local = index - start;
                int x = gridX + (local % columns) * (tileSize + gap);
                int y = gridY + (local / columns) * (tileSize + gap);
                if (in(mouseX, mouseY, x, y, tileSize, tileSize)) {
                    selectedItem = filtered.get(index);
                    page = Page.DETAILS;
                    play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.12f);
                    return true;
                }
            }
            int rightX = panelX + panelW - 232;
            int rightY = panelY + 82;
            int rightH = panelH - 111;
            int buttonY = rightY + rightH - 42;
            if (in(mouseX, mouseY, rightX + 12, buttonY, 186, 28)) {
                page = Page.DETAILS;
                play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.12f);
                return true;
            }
        } else {
            if (in(mouseX, mouseY, panelX + 20, panelY + 48, 74, 24)) {
                page = Page.BROWSE;
                play(SoundEvents.UI_BUTTON_CLICK, 0.94f);
                return true;
            }
            int resetX = panelX + 196;
            int resetY = panelY + panelH - 48;
            if (in(mouseX, mouseY, resetX, resetY, 120, 28)) {
                ItemSizeConfig.reset(selectedItem);
                play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.72f);
                return true;
            }
            Dimension[] dimensions = {Dimension.GROUND, Dimension.HAND, Dimension.INVENTORY};
            for (int i = 0; i < dimensions.length; i++) {
                int rowY = panelY + 112 + i * 70;
                if (in(mouseX, mouseY, sliderX + 8, rowY + 34, sliderW - 16, 20)) {
                    draggingDimension = dimensions[i];
                    updateSlider(mouseX, draggingDimension);
                    play(SoundEvents.UI_BUTTON_CLICK, 1.2f);
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
        if (draggingDimension != null && click.button() == GLFW.GLFW_MOUSE_BUTTON_1) {
            updateSlider(click.x(), draggingDimension);
            return true;
        }
        return super.mouseDragged(click, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_1) {
            draggingDimension = null;
            ItemSizeConfig.save();
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int codePoint = event.codepoint();
        if (page == Page.BROWSE && searchFocused && !Character.isISOControl(codePoint)) {
            if (search.length() < 40) search += new String(Character.toChars(codePoint));
            scrollRow = 0;
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (page == Page.DETAILS) {
                page = Page.BROWSE;
            } else {
                onClose();
            }
            return true;
        }
        if (page == Page.BROWSE && searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                scrollRow = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (page == Page.BROWSE) {
            scrollRow = Math.max(0, scrollRow - (int) Math.signum(verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
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
