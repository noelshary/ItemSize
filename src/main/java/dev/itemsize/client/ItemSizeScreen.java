package dev.itemsize.client;

import dev.itemsize.config.ItemSizeConfig;
import dev.itemsize.config.ItemSizeConfig.ScaleProfile;
import dev.itemsize.config.ItemSizeConfig.ScaleType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Main ItemSize editor screen. */
public final class ItemSizeScreen extends Screen {
    private static final int PAGE_SIZE = 20;
    private static final int GRID_COLUMNS = 5;
    private static final float MIN_SCALE = 0.10f;
    private static final float MAX_SCALE = 5.00f;

    private final List<Item> allItems = new ArrayList<>();
    private final List<Item> filteredItems = new ArrayList<>();
    private final List<Button> itemButtons = new ArrayList<>();

    private Item selectedItem;
    private EditBox searchBox;
    private Button previousPageButton;
    private Button nextPageButton;
    private ScaleSlider groundSlider;
    private ScaleSlider handSlider;
    private ScaleSlider inventorySlider;

    private int page;
    private int age;
    private int cardX;
    private int cardY;
    private int cardWidth;
    private int cardHeight;
    private int leftWidth;
    private int rightX;
    private int rightWidth;
    private int gridX;
    private int gridY;
    private int itemWidth;
    private int itemHeight;
    private String status = "";

    public ItemSizeScreen() {
        super(Component.literal("ItemSize"));
        allItems.addAll(BuiltInRegistries.ITEM.stream().toList());
        allItems.sort(Comparator.comparing(item -> new ItemStack(item).getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
        filteredItems.addAll(allItems);
        selectedItem = BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("minecraft", "diamond"));
        if (selectedItem == null && !allItems.isEmpty()) {
            selectedItem = allItems.getFirst();
        }
    }

    @Override
    protected void init() {
        cardWidth = Math.min(940, this.width - 24);
        cardHeight = Math.min(540, this.height - 24);
        cardX = (this.width - cardWidth) / 2;
        cardY = (this.height - cardHeight) / 2;
        leftWidth = (int) (cardWidth * 0.545f);
        rightX = cardX + leftWidth + 14;
        rightWidth = cardWidth - leftWidth - 14;

        int innerLeft = cardX + 18;
        int searchY = cardY + 76;
        gridX = innerLeft;
        gridY = cardY + 118;
        int availableGridWidth = leftWidth - 36;
        int gapX = 7;
        itemWidth = (availableGridWidth - (GRID_COLUMNS - 1) * gapX) / GRID_COLUMNS;
        itemHeight = 54;

        EditBox searchBox = new EditBox(this.font, innerLeft, searchY, availableGridWidth, 22, Component.literal("Search items"));
        searchBox.setHint(Component.literal("Search any item..."));
        searchBox.setMaxLength(80);
        searchBox.setResponder(this::filterItems);
        this.addRenderableWidget(searchBox);

        itemButtons.clear();
        for (int i = 0; i < PAGE_SIZE; i++) {
            final int slot = i;
            int column = i % GRID_COLUMNS;
            int row = i / GRID_COLUMNS;
            int x = gridX + column * (itemWidth + gapX);
            int y = gridY + row * (itemHeight + 7);
            Button itemButton = Button.builder(Component.empty(), button -> {
                int index = this.page * PAGE_SIZE + slot;
                if (index >= 0 && index < filteredItems.size()) {
                    selectItem(filteredItems.get(index));
                }
            }).bounds(x, y, itemWidth, itemHeight).build();
            itemButtons.add(this.addRenderableWidget(itemButton));
        }

        int footerY = cardY + cardHeight - 42;
        previousPageButton = this.addRenderableWidget(Button.builder(Component.literal("‹"), button -> {
            if (page > 0) {
                page--;
                updateGridButtons();
            }
        }).bounds(innerLeft, footerY, 34, 22).build());
        nextPageButton = this.addRenderableWidget(Button.builder(Component.literal("›"), button -> {
            int pages = Math.max(1, (filteredItems.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            if (page + 1 < pages) {
                page++;
                updateGridButtons();
            }
        }).bounds(innerLeft + 42, footerY, 34, 22).build());

        int sliderX = rightX + 18;
        int sliderWidth = rightWidth - 36;
        int sliderStartY = cardY + 260;
        groundSlider = this.addRenderableWidget(new ScaleSlider(sliderX, sliderStartY, sliderWidth, "WORLD SCALE", ScaleType.GROUND, currentScale(ScaleType.GROUND)));
        handSlider = this.addRenderableWidget(new ScaleSlider(sliderX, sliderStartY + 48, sliderWidth, "HAND SCALE", ScaleType.HAND, currentScale(ScaleType.HAND)));
        inventorySlider = this.addRenderableWidget(new ScaleSlider(sliderX, sliderStartY + 96, sliderWidth, "INVENTORY SCALE", ScaleType.INVENTORY, currentScale(ScaleType.INVENTORY)));

        int actionY = cardY + cardHeight - 42;
        int actionWidth = (rightWidth - 48) / 2;
        this.addRenderableWidget(Button.builder(Component.literal("RESET ITEM"), button -> {
            ItemSizeConfig.reset(selectedItem);
            syncSliders();
            status = "Item values reset";
        }).bounds(rightX + 18, actionY, actionWidth, 22).build());
        this.addRenderableWidget(Button.builder(Component.literal("SAVE"), button -> {
            ItemSizeConfig.save();
            status = "Saved to config";
        }).bounds(rightX + 30 + actionWidth, actionY, actionWidth, 22).build());

        updateGridButtons();
        syncSliders();
    }

    private void filterItems(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filteredItems.clear();
        if (needle.isEmpty()) {
            filteredItems.addAll(allItems);
        } else {
            for (Item item : allItems) {
                String id = BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase(Locale.ROOT);
                String name = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
                if (id.contains(needle) || name.contains(needle)) {
                    filteredItems.add(item);
                }
            }
        }
        page = 0;
        updateGridButtons();
    }

    private void updateGridButtons() {
        int first = page * PAGE_SIZE;
        for (int i = 0; i < itemButtons.size(); i++) {
            boolean show = first + i < filteredItems.size();
            itemButtons.get(i).visible = show;
            itemButtons.get(i).active = show;
        }
        if (previousPageButton != null) {
            previousPageButton.active = page > 0;
        }
        if (nextPageButton != null) {
            int pages = Math.max(1, (filteredItems.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            nextPageButton.active = page + 1 < pages;
        }
    }

    private void selectItem(Item item) {
        selectedItem = item;
        status = "";
        syncSliders();
    }

    private float currentScale(ScaleType scaleType) {
        if (selectedItem == null) {
            return 1.0f;
        }
        ScaleProfile profile = ItemSizeConfig.getProfile(selectedItem);
        return switch (scaleType) {
            case GROUND -> profile.groundScale;
            case HAND -> profile.handScale;
            case INVENTORY -> profile.inventoryScale;
        };
    }

    private void syncSliders() {
        if (selectedItem == null || groundSlider == null) {
            return;
        }
        ScaleProfile profile = ItemSizeConfig.getProfile(selectedItem);
        groundSlider.sync(profile.groundScale);
        handSlider.sync(profile.handScale);
        inventorySlider.sync(profile.inventoryScale);
    }

    @Override
    public void tick() {
        super.tick();
        age++;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xF208101B, 0xF20D1D30);

        float open = Math.min(1.0f, age / 12.0f);
        int panelAlpha = Math.max(0, Math.min(255, (int) (238 * open)));
        int panelColor = (panelAlpha << 24) | 0x00101D2B;
        int innerColor = (panelAlpha << 24) | 0x00142638;
        graphics.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, panelColor);
        graphics.outline(cardX, cardY, cardWidth, cardHeight, 0x9956D8F5);
        graphics.fill(cardX + 12, cardY + 60, cardX + leftWidth, cardY + cardHeight - 54, innerColor);
        graphics.fill(rightX, cardY + 60, cardX + cardWidth - 12, cardY + cardHeight - 54, innerColor);
        graphics.fill(cardX + 18, cardY + 56, cardX + cardWidth - 18, cardY + 58, 0xAA39BFEA);

        for (int i = 0; i < 22; i++) {
            double phase = age * 0.035 + i * 1.71;
            int px = cardX + 10 + (int) ((Math.sin(phase) * 0.5 + 0.5) * Math.max(1, cardWidth - 20));
            int py = cardY + 14 + (int) ((Math.cos(phase * 0.73) * 0.5 + 0.5) * Math.max(1, cardHeight - 28));
            graphics.fill(px, py, px + 2, py + 2, 0x6658DDFB);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.text(this.font, "ITEM", cardX + 20, cardY + 20, 0xFFF0F7FF, true);
        graphics.text(this.font, "SIZE", cardX + 51, cardY + 20, 0xFF58D9FF, true);
        graphics.text(this.font, "ITEM DIMENSION EDITOR  /  CLIENT CONFIGURATION", cardX + 110, cardY + 21, 0xFF91AFC2, false);
        graphics.text(this.font, "ITEM LIBRARY", cardX + 20, cardY + 68, 0xFFBFEFFF, true);

        int first = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && first + i < filteredItems.size(); i++) {
            Item item = filteredItems.get(first + i);
            Button button = itemButtons.get(i);
            int bx = button.getX();
            int by = button.getY();
            if (item == selectedItem) {
                graphics.outline(bx, by, itemWidth, itemHeight, 0xFF55D9FF);
            }
            graphics.fakeItem(new ItemStack(item), bx + (itemWidth - 16) / 2, by + 5);
            String name = new ItemStack(item).getHoverName().getString();
            if (name.length() > 11) {
                name = name.substring(0, 10) + "…";
            }
            graphics.centeredText(this.font, name, bx + itemWidth / 2, by + 36, 0xFFE7F4FF);
        }

        int pages = Math.max(1, (filteredItems.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        graphics.text(this.font, "PAGE " + (page + 1) + " / " + pages + "   ·   " + filteredItems.size() + " ITEMS", cardX + 105, cardY + cardHeight - 35, 0xFF88A7BA, false);
        if (filteredItems.isEmpty()) {
            graphics.centeredText(this.font, "No matching items", cardX + leftWidth / 2, gridY + 90, 0xFF91AFC2);
        }

        graphics.text(this.font, "SIZE CONTROLS", rightX + 18, cardY + 68, 0xFFBFEFFF, true);
        if (selectedItem != null) {
            String id = BuiltInRegistries.ITEM.getKey(selectedItem).toString();
            graphics.text(this.font, id, rightX + 18, cardY + 88, 0xFF7D9DB2, false);
            graphics.fill(rightX + 18, cardY + 108, rightX + rightWidth - 18, cardY + 222, 0x5538A8D4);
            graphics.outline(rightX + 18, cardY + 108, rightWidth - 36, 114, 0x5549C8EB);
            graphics.centeredText(this.font, "SELECTED ITEM", rightX + rightWidth / 2, cardY + 120, 0xFF83BBD0);
            graphics.fakeItem(new ItemStack(selectedItem), rightX + rightWidth / 2 - 8, cardY + 151);
            graphics.centeredText(this.font, new ItemStack(selectedItem).getHoverName().getString(), rightX + rightWidth / 2, cardY + 190, 0xFFF1F8FF);
        }
        graphics.text(this.font, "WORLD SCALE", rightX + 18, cardY + 239, 0xFF7D9DB2, false);
        graphics.text(this.font, "HAND SCALE", rightX + 18, cardY + 287, 0xFF7D9DB2, false);
        graphics.text(this.font, "INVENTORY SCALE", rightX + 18, cardY + 335, 0xFF7D9DB2, false);
        if (!status.isEmpty()) {
            graphics.text(this.font, status, rightX + 18, cardY + cardHeight - 61, 0xFF71E4B6, false);
        }
        graphics.text(this.font, "I  OPEN MENU   ·   ESC  CLOSE", cardX + cardWidth - 190, cardY + cardHeight - 35, 0xFF68879A, false);
    }

    @Override
    public void onClose() {
        ItemSizeConfig.save();
        this.minecraft.gui.setScreen(null);
    }

    private static double normalizedValue(float scale) {
        return Math.max(0.0, Math.min(1.0, (scale - MIN_SCALE) / (MAX_SCALE - MIN_SCALE)));
    }

    private static float scaleFromNormalized(double normalized) {
        return MIN_SCALE + (float) normalized * (MAX_SCALE - MIN_SCALE);
    }

    private final class ScaleSlider extends AbstractSliderButton {
        private final ScaleType type;

        private ScaleSlider(int x, int y, int width, String label, ScaleType type, float initialScale) {
            super(x, y, width, 20, Component.empty(), normalizedValue(initialScale));
            this.type = type;
            updateMessage();
        }

        private void sync(float scale) {
            this.value = normalizedValue(scale);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            float scale = scaleFromNormalized(this.value);
            setMessage(Component.literal(Math.round(scale * 100.0f) + "%"));
        }

        @Override
        protected void applyValue() {
            if (selectedItem == null) {
                return;
            }
            float scale = Math.round(scaleFromNormalized(this.value) * 100.0f) / 100.0f;
            ItemSizeConfig.setScale(selectedItem, this.type, scale);
            status = "";
            updateMessage();
        }
    }
}
