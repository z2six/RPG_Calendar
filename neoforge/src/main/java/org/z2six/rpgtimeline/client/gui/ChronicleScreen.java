package org.z2six.rpgtimeline.client.gui;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.Constants;
import org.z2six.rpgtimeline.client.tooltip.ChronicleItemTooltipClient;
import org.z2six.rpgtimeline.api.RPGTimelineApi;
import org.z2six.rpgtimeline.calendar.CalendarDefinition;
import org.z2six.rpgtimeline.chronicle.ChronicleDetail;
import org.z2six.rpgtimeline.chronicle.ChronicleEntry;
import org.z2six.rpgtimeline.chronicle.ChronicleEntryType;
import org.z2six.rpgtimeline.chronicle.ChronicleScope;
import org.z2six.rpgtimeline.chronicle.HallOfFameEntry;
import org.z2six.rpgtimeline.network.ChroniclePayloads;
import org.z2six.rpgtimeline.registry.RPGTimelineItems;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Near-fullscreen chronicle timeline UI.
 */
public class ChronicleScreen extends Screen {

    private static final Logger LOG = LogUtils.getLogger();

    private static final int OUTER_MARGIN = 24;
    private static final int PANEL_PADDING = 16;
    private static final int TAB_WIDTH = 110;
    private static final int TAB_HEIGHT = 20;
    private static final int BAR_HEIGHT = 8;
    private static final int BAR_GAP = 4;
    private static final int BAR_INSET = 12;
    private static final int DETAIL_PANEL_WIDTH = 260;
    private static final float MIN_ZOOM = 0.000001f;
    private static final float MAX_ZOOM = 1000.0f;
    private static final float ZOOM_STEP = 1.12f;
    private static final int TICK_LABEL_MIN_SPACING = 120;
    private static final int SECTION_PIXEL_WIDTH = 18;
    private static final int SECTION_PIXEL_GAP = 6;
    private static final int NODE_BASE_OFFSET = 18;
    private static final int NODE_STACK_SPACING = 12;
    private static final int MAX_GROUP_DETAILS = 50;

    private static final int BG_COLOR = 0xFF121318;
    private static final int PANEL_COLOR = 0xFF1B1D24;
    private static final int CARD_COLOR = 0xFF20222B;
    private static final int CARD_BORDER = 0xFF2E313D;
    private static final int TAB_INACTIVE_COLOR = 0xFF0E0F14;
    private static final int BAR_BG_COLOR = 0xFF050506;
    private static final int BAR_HANDLE_COLOR = 0xFF5B5F6D;
    private static final int ACCENT_COLOR = 0xFF8DA3FF;
    private static final int WORLD_FIRST_COLOR = 0xFFF4D37C;
    private static final ResourceLocation HALL_OF_FAME_ICON =
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/item/chronicle_world_first.png");

    private ChronicleTab tab = ChronicleTab.SERVER;
    private ChronicleScale scale = ChronicleScale.MONTH;
    private float viewStartUnit = 0.0f;
    private float zoomFactor = 1.0f;
    private boolean snapToLatest = true;
    private float maxViewStartUnit = 0.0f;
    private long minUnitIndex = 0L;
    private long maxUnitIndex = 0L;
    private long currentUnitIndex = 0L;
    private float currentUnitTime = 0.0f;
    private float maxUnitTime = 0.0f;
    private float unitSpacing = 0.0f;
    private int panelX = 0;
    private int panelY = 0;
    private int panelW = 0;
    private int panelH = 0;
    private int railLeft = 0;
    private int railRight = 0;
    private int timelineAreaX = 0;
    private int timelineAreaY = 0;
    private int timelineAreaW = 0;
    private int timelineAreaH = 0;
    private int scrollbarX = 0;
    private int scrollbarY = 0;
    private int scrollbarW = 0;
    private int scrollbarH = 0;
    private int scrollHandleX = 0;
    private int scrollHandleY = 0;
    private int scrollHandleW = 0;
    private int scrollHandleH = 0;
    private boolean draggingScroll = false;
    private int dragStartMouseX = 0;
    private int dragHandleGrabOffset = 0;

    private SectionBucket selectedSection = null;
    private String selectedHallUuid = null;
    private ChronicleEntry hoveredEntry = null;
    private int hoveredNodeX = 0;
    private int hoveredNodeY = 0;
    private final List<NodeBounds> nodeBounds = new ArrayList<>();
    private final List<HallRowBounds> hallRowBounds = new ArrayList<>();
    private int hallScrollOffset = 0;
    private int hallScrollMax = 0;

    private boolean showAddPanel = false;

    private Button submitButton;
    private Button cancelButton;
    private Button closeDetailButton;
    private EditBox titleBox;
    private MultiLineEditBox detailsBox;
    private EditBox eventDayBox;
    private EditBox eventYearBox;
    private int eventMonthIndex = 0;
    private boolean showEventMonthDropdown = false;
    private List<String> eventMonthNames = List.of();
    private int eventMonthX = 0;
    private int eventMonthY = 0;
    private int eventMonthW = 0;
    private int eventMonthH = 0;
    private boolean suppressEventDateChange = false;
    private int eventMonthScroll = 0;
    private int addPanelX = 0;
    private int addPanelY = 0;
    private int addPanelW = 0;
    private int addPanelH = 0;
    private EditBox jumpDayBox;
    private EditBox jumpYearBox;
    private int jumpMonthIndex = 0;
    private boolean showMonthDropdown = false;
    private List<String> jumpMonthNames = List.of();
    private int jumpLabelX = 0;
    private int jumpLabelY = 0;
    private int jumpMonthX = 0;
    private int jumpMonthY = 0;
    private int jumpMonthW = 0;
    private int jumpMonthH = 0;
    private boolean suppressJumpChange = false;
    private int detailPanelX = 0;
    private int detailPanelY = 0;
    private int detailPanelW = 0;
    private int detailPanelH = 0;
    private int detailScrollOffset = 0;
    private int detailScrollMax = 0;
    private int detailScrollBarX = 0;
    private int detailScrollBarY = 0;
    private int detailScrollBarW = 0;
    private int detailScrollBarH = 0;
    private int detailScrollHandleY = 0;
    private int detailScrollHandleH = 0;
    private boolean draggingDetailScroll = false;
    private int detailDragOffset = 0;
    private int syncTicker = 0;
    private boolean showAddButton = false;
    private boolean showInfoButton = false;
    private boolean hoverAddButton = false;
    private boolean hoverInfoButton = false;
    private int addButtonX = 0;
    private int addButtonY = 0;
    private int addButtonW = 24;
    private int addButtonH = 24;
    private int infoButtonX = 0;
    private int infoButtonY = 0;
    private int infoButtonW = 24;
    private int infoButtonH = 24;
    private boolean hoverSubmitButton = false;
    private boolean hoverCancelButton = false;
    private int submitButtonX = 0;
    private int submitButtonY = 0;
    private int submitButtonW = 0;
    private int submitButtonH = 0;
    private int cancelButtonX = 0;
    private int cancelButtonY = 0;
    private int cancelButtonW = 0;
    private int cancelButtonH = 0;
    private boolean hoverCloseButton = false;
    private int closeButtonX = 0;
    private int closeButtonY = 0;
    private int closeButtonW = 0;
    private int closeButtonH = 0;

    public ChronicleScreen() {
        super(Component.literal("Chronicle"));
    }

    @Override
    public void tick() {
        super.tick();
        if (!ChroniclePayloads.ClientState.hasSynced()) {
            return;
        }
        syncTicker++;
        if (syncTicker >= 40) {
            syncTicker = 0;
            PacketDistributor.sendToServer(new ChroniclePayloads.RequestChroniclePayload());
            if (tab == ChronicleTab.HALL_OF_FAME) {
                PacketDistributor.sendToServer(new ChroniclePayloads.RequestHallOfFamePayload());
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        updateLayout();
        this.clearWidgets();
        updateAddEventButtonVisibility();

        buildAddPanel();
        buildJumpControls();
        buildDetailPanelControls();

        PacketDistributor.sendToServer(new ChroniclePayloads.RequestChroniclePayload());
    }

    private void buildAddPanel() {
        CalendarDefinition def = RPGTimelineApi.getCalendarDefinition();
        addPanelW = Math.max(260, Math.min(360, panelW - 64));
        addPanelH = Math.max(240, Math.min(320, panelH - 64));
        addPanelX = panelX + (panelW - addPanelW) / 2;
        addPanelY = panelY + (panelH - addPanelH) / 2;

        int fieldWidth = addPanelW - 32;

        titleBox = new EditBox(font, addPanelX + 16, addPanelY + 40, fieldWidth, 18, Component.literal("Title"));
        titleBox.setMaxLength(128);
        titleBox.setHint(Component.literal("Title"));

        eventDayBox = new EditBox(font, addPanelX + 16, addPanelY + 66, 36, 18, Component.literal("Day"));
        eventDayBox.setMaxLength(3);
        eventDayBox.setFilter(this::isNumericOrEmpty);
        eventDayBox.setHint(Component.literal("Day"));

        eventMonthX = eventDayBox.getX() + eventDayBox.getWidth() + 6;
        eventMonthY = addPanelY + 66;
        eventMonthW = 140;
        eventMonthH = 18;
        refreshEventMonthList(def);

        eventYearBox = new EditBox(font, eventMonthX + eventMonthW + 6, addPanelY + 66, 60, 18, Component.literal("Year"));
        eventYearBox.setMaxLength(6);
        eventYearBox.setFilter(this::isNumericOrEmpty);
        eventYearBox.setHint(Component.literal("Year"));

        int detailsHeight = Math.max(80, addPanelH - 160);
        detailsBox = new MultiLineEditBox(
                font,
                addPanelX + 16,
                addPanelY + 92,
                fieldWidth,
                detailsHeight,
                Component.literal("Description"),
                Component.literal("Description")
        );
        detailsBox.setCharacterLimit(512);

        submitButton = Button.builder(Component.literal("Submit"), btn -> submitAdminEvent())
                .bounds(addPanelX + 16, addPanelY + addPanelH - 56, fieldWidth, 20)
                .build();

        cancelButton = Button.builder(Component.literal("Cancel"), btn -> toggleAddPanel())
                .bounds(addPanelX + 16, addPanelY + addPanelH - 30, fieldWidth, 20)
                .build();

        addRenderableWidget(titleBox);
        addRenderableWidget(eventDayBox);
        addRenderableWidget(eventYearBox);
        addRenderableWidget(detailsBox);
        addRenderableWidget(submitButton);
        addRenderableWidget(cancelButton);

        syncEventDateFromCurrent(def);
        updateAddPanelVisibility();
    }

    private void layoutAddPanel() {
        addPanelX = panelX + (panelW - addPanelW) / 2;
        addPanelY = panelY + (panelH - addPanelH) / 2;
        int fieldX = addPanelX + 16;
        int fieldW = addPanelW - 32;
        int y = addPanelY + 40;

        if (titleBox != null) {
            titleBox.setX(fieldX);
            titleBox.setY(y);
            titleBox.setWidth(fieldW);
        }
        y += 26;

        if (eventDayBox != null) {
            eventDayBox.setX(fieldX + font.width("Date:") + 6);
            eventDayBox.setY(y);
        }

        eventMonthX = (eventDayBox != null ? eventDayBox.getX() : fieldX) + 36 + 6;
        eventMonthY = y;
        eventMonthW = 140;
        eventMonthH = 18;

        if (eventYearBox != null) {
            eventYearBox.setX(eventMonthX + eventMonthW + 6);
            eventYearBox.setY(y);
        }

        y += 26;
        int detailsHeight = Math.max(80, addPanelH - (y - addPanelY) - 72);
        if (detailsBox != null) {
            detailsBox.setX(fieldX);
            detailsBox.setY(y);
            detailsBox.setWidth(fieldW);
            detailsBox.setHeight(detailsHeight);
        }

        int buttonY = addPanelY + addPanelH - 56;
        submitButtonX = fieldX;
        submitButtonY = buttonY;
        submitButtonW = fieldW;
        submitButtonH = 20;
        cancelButtonX = fieldX;
        cancelButtonY = buttonY + 26;
        cancelButtonW = fieldW;
        cancelButtonH = 20;
        if (submitButton != null) {
            submitButton.setX(fieldX);
            submitButton.setY(buttonY);
            submitButton.setWidth(fieldW);
        }
        if (cancelButton != null) {
            cancelButton.setX(fieldX);
            cancelButton.setY(buttonY + 26);
            cancelButton.setWidth(fieldW);
        }
    }

    private void buildDetailPanelControls() {
        closeDetailButton = Button.builder(Component.literal("Close"), btn -> closeDetailPanel())
                .bounds(0, 0, 0, 0)
                .build();
        addRenderableWidget(closeDetailButton);
        updateDetailPanelVisibility();
    }

    private void buildJumpControls() {
        CalendarDefinition def = RPGTimelineApi.getCalendarDefinition();
        int x = panelX + PANEL_PADDING;
        int y = panelY + 42;
        int h = 18;
        int gap = 6;
        int labelWidth = font.width("Jump to:");
        int cursorX = x + labelWidth + gap;

        jumpLabelX = x;
        jumpLabelY = panelY + 46;

        jumpDayBox = new EditBox(font, cursorX, y, 36, h, Component.literal("Day"));
        jumpDayBox.setMaxLength(3);
        jumpDayBox.setFilter(this::isNumericOrEmpty);
        jumpDayBox.setResponder(value -> tryJumpToDate());
        addRenderableWidget(jumpDayBox);

        cursorX += 36 + gap;
        jumpMonthX = cursorX;
        jumpMonthY = y;
        jumpMonthW = 120;
        jumpMonthH = h;
        refreshJumpMonthList(def);

        cursorX += jumpMonthW + gap;
        jumpYearBox = new EditBox(font, cursorX, y, 60, h, Component.literal("Year"));
        jumpYearBox.setMaxLength(6);
        jumpYearBox.setFilter(this::isNumericOrEmpty);
        jumpYearBox.setResponder(value -> tryJumpToDate());
        addRenderableWidget(jumpYearBox);

        syncJumpControlsFromCurrent(def);
    }

    private void updateAddPanelVisibility() {
        boolean visible = showAddPanel;
        if (titleBox != null) {
            titleBox.setVisible(visible);
            titleBox.setFocused(visible);
        }
        if (eventDayBox != null) {
            eventDayBox.setVisible(visible);
        }
        if (eventYearBox != null) {
            eventYearBox.setVisible(visible);
        }
        if (detailsBox != null) {
            detailsBox.visible = visible;
            detailsBox.active = visible;
        }
        if (submitButton != null) {
            submitButton.visible = false;
            submitButton.active = false;
        }
        if (cancelButton != null) {
            cancelButton.visible = false;
            cancelButton.active = false;
        }
        updateAddEventButtonVisibility();
        if (!visible) {
            showEventMonthDropdown = false;
        }
        updateJumpControlsVisibility();
        updateDetailPanelVisibility();
    }

    private void updateDetailPanelVisibility() {
        boolean visible = (selectedSection != null || selectedHallUuid != null) && !showAddPanel;
        if (closeDetailButton != null) {
            closeDetailButton.visible = false;
            closeDetailButton.active = false;
        }
        updateAddEventButtonVisibility();
        updateJumpControlsVisibility();
    }

    private void updateAddEventButtonVisibility() {
        boolean visible = canAddEvent() && !showAddPanel && selectedSection == null && selectedHallUuid == null;
        showAddButton = visible;
        showInfoButton = visible;
    }

    private void updateJumpControlsVisibility() {
        boolean visible = !shouldHideJumpControls();
        if (jumpDayBox != null) {
            jumpDayBox.setVisible(visible);
        }
        if (jumpYearBox != null) {
            jumpYearBox.setVisible(visible);
        }
        if (!visible) {
            showMonthDropdown = false;
        }
    }

    private boolean shouldHideJumpControls() {
        return showAddPanel || selectedSection != null || selectedHallUuid != null || tab == ChronicleTab.HALL_OF_FAME;
    }

    private boolean isAdminPlayer() {
        return minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(4);
    }

    private boolean canAddEvent() {
        return isAdminPlayer() && tab != ChronicleTab.HALL_OF_FAME;
    }

    private void toggleAddPanel() {
        if (!isAdminPlayer()) {
            return;
        }
        showAddPanel = !showAddPanel;
        if (!showAddPanel) {
            selectedSection = null;
            selectedHallUuid = null;
        } else {
            syncEventDateFromCurrent(RPGTimelineApi.getCalendarDefinition());
        }
        updateAddPanelVisibility();
        if (showAddPanel && titleBox != null) {
            titleBox.setFocused(true);
            titleBox.setCanLoseFocus(true);
            titleBox.setCursorPosition(0);
            titleBox.setHighlightPos(0);
            setFocused(titleBox);
        }
    }

    private void closeDetailPanel() {
        selectedSection = null;
        selectedHallUuid = null;
        detailScrollOffset = 0;
        updateDetailPanelVisibility();
    }

    private void submitAdminEvent() {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        String title = titleBox != null ? titleBox.getValue() : "";
        String details = detailsBox != null ? detailsBox.getValue() : "";
        if (title == null || title.isBlank()) {
            return;
        }
        Long dayIndex = getEventDayIndex();
        if (dayIndex == null) {
            return;
        }
        ChronicleScope scope = tab == ChronicleTab.SERVER ? ChronicleScope.SERVER : ChronicleScope.PERSONAL;
        PacketDistributor.sendToServer(new ChroniclePayloads.AddAdminEventPayload(scope.name(), title, details, dayIndex));
        PacketDistributor.sendToServer(new ChroniclePayloads.RequestChroniclePayload());
        snapToLatest = true;
        if (titleBox != null) titleBox.setValue("");
        if (detailsBox != null) detailsBox.setValue("");
        toggleAddPanel();
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateLayout();
        layoutTopButtons();
        renderBackground(g, mouseX, mouseY, partialTick);
        drawTabs(g, mouseX, mouseY);
        drawPanelBackground(g);

        nodeBounds.clear();
        hallRowBounds.clear();
        hoveredEntry = null;

        drawHeader(g);
        drawTopButtons(g, mouseX, mouseY);
        if (!shouldHideJumpControls()) {
            drawJumpLabel(g);
        }

        if (tab == ChronicleTab.HALL_OF_FAME) {
            drawHallOfFame(g, mouseX, mouseY);
        } else {
            drawTimeline(g, mouseX, mouseY);
        }

        updateDetailPanelVisibility();
        if (showAddPanel) {
            drawAddPanel(g, mouseX, mouseY);
        } else if (selectedSection != null) {
            drawDetailPanel(g, selectedSection, mouseX, mouseY);
        } else if (selectedHallUuid != null) {
            drawHallOfFameDetailPanel(g, selectedHallUuid, mouseX, mouseY);
        }

        if (!ChroniclePayloads.ClientState.hasSynced()) {
            g.drawCenteredString(font, "Loading Chronicle...", panelX + panelW / 2, panelY + panelH / 2 - 8, 0xFFCCCCCC);
        }

        super.render(g, mouseX, mouseY, partialTick);

        if (showAddPanel && showEventMonthDropdown) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            drawEventMonthDropdownList(g, mouseX, mouseY);
            g.pose().popPose();
        }

        if (showMonthDropdown) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            drawMonthDropdownList(g, mouseX, mouseY);
            g.pose().popPose();
        }

        if (hoverInfoButton) {
            List<net.minecraft.util.FormattedCharSequence> tooltip = List.of(
                    Component.literal("Scroll: pan the timeline"),
                    Component.literal("CTRL + Scroll: zoom in/out"),
                    Component.literal("Drag the bar: scrub time"),
                    Component.literal("Click nodes: open details")
            ).stream().map(Component::getVisualOrderText).toList();
            g.renderTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Intentionally skip vanilla blur/menu background; custom UI draws its own background.
    }

    private void updateLayout() {
        int margin = OUTER_MARGIN;
        panelX = margin;
        panelY = margin;
        panelW = Math.max(0, width - margin * 2);
        panelH = Math.max(0, height - margin * 2);
    }

    private void drawPanelBackground(@NotNull GuiGraphics g) {
        if (panelW <= 0 || panelH <= 0) {
            return;
        }
        int topColor = 0xFF171A22;
        int bottomColor = 0xFF121318;
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, topColor, bottomColor);
        drawPanelBorder(g);
    }

    private void drawPanelBorder(@NotNull GuiGraphics g) {
        if (panelW <= 0 || panelH <= 0) {
            return;
        }
        int x0 = panelX;
        int y0 = panelY;
        int x1 = panelX + panelW;
        int y1 = panelY + panelH;
        g.fill(x0, y0, x1, y0 + 1, CARD_BORDER);
        g.fill(x0, y1 - 1, x1, y1, CARD_BORDER);
        g.fill(x0, y0, x0 + 1, y1, CARD_BORDER);
        g.fill(x1 - 1, y0, x1, y1, CARD_BORDER);
        drawPanelOrnaments(g);
    }

    private void drawPanelOrnaments(@NotNull GuiGraphics g) {
        int x0 = panelX;
        int y0 = panelY;
        int x1 = panelX + panelW;
        int y1 = panelY + panelH;
        int midX = panelX + panelW / 2;
        int midY = panelY + panelH / 2;
        int notchW = 28;
        int notchH = 3;
        int tickW = 3;
        int tickH = 16;

        g.fill(midX - notchW / 2, y0, midX + notchW / 2, y0 + notchH, CARD_BORDER);
        g.fill(midX - notchW / 2, y1 - notchH, midX + notchW / 2, y1, CARD_BORDER);
        g.fill(x0, midY - tickH / 2, x0 + tickW, midY + tickH / 2, CARD_BORDER);
        g.fill(x1 - tickW, midY - tickH / 2, x1, midY + tickH / 2, CARD_BORDER);

        int cornerSize = 6;
        g.fill(x0, y0, x0 + cornerSize, y0 + 2, CARD_BORDER);
        g.fill(x0, y0, x0 + 2, y0 + cornerSize, CARD_BORDER);
        g.fill(x1 - cornerSize, y0, x1, y0 + 2, CARD_BORDER);
        g.fill(x1 - 2, y0, x1, y0 + cornerSize, CARD_BORDER);
        g.fill(x0, y1 - 2, x0 + cornerSize, y1, CARD_BORDER);
        g.fill(x0, y1 - cornerSize, x0 + 2, y1, CARD_BORDER);
        g.fill(x1 - cornerSize, y1 - 2, x1, y1, CARD_BORDER);
        g.fill(x1 - 2, y1 - cornerSize, x1, y1, CARD_BORDER);
    }

    private void layoutTopButtons() {
        int y = panelY + 12;
        int gap = 6;
        int size = 12;
        addButtonW = size;
        addButtonH = size;
        infoButtonW = size;
        infoButtonH = size;
        int right = panelX + panelW - PANEL_PADDING;

        infoButtonX = right - infoButtonW;
        infoButtonY = y;
        right -= infoButtonW + gap;

        addButtonX = right - addButtonW;
        addButtonY = y;
    }

    private void drawTopButtons(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        hoverAddButton = showAddButton && inRect(mouseX, mouseY, addButtonX, addButtonY, addButtonW, addButtonH);
        hoverInfoButton = showInfoButton && inRect(mouseX, mouseY, infoButtonX, infoButtonY, infoButtonW, infoButtonH);

        if (showAddButton) {
            drawTopButton(g, addButtonX, addButtonY, addButtonW, addButtonH, "+", hoverAddButton);
        }
        if (showInfoButton) {
            drawTopButton(g, infoButtonX, infoButtonY, infoButtonW, infoButtonH, "?", hoverInfoButton);
        }
    }

    private void drawTopButton(@NotNull GuiGraphics g, int x, int y, int w, int h, String label, boolean hover) {
        int fill = hover ? 0xFF2B2F3A : PANEL_COLOR;
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, CARD_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, CARD_BORDER);
        g.fill(x, y, x + 1, y + h, CARD_BORDER);
        g.fill(x + w - 1, y, x + w, y + h, CARD_BORDER);
        int textW = font.width(label);
        int textX = x + (w - textW) / 2;
        int textY = y + Math.max(1, (h - font.lineHeight) / 2 + 1);
        g.drawString(font, label, textX, textY, 0xFFEDEDED, false);
    }

    private void updateScrollBounds() {
        if (unitSpacing <= 0.0f) {
            maxViewStartUnit = 0.0f;
            viewStartUnit = 0.0f;
            return;
        }

        float viewUnits = Math.max(1.0f, (railRight - railLeft) / unitSpacing);
        float latestUnit = Math.max(0.0f, maxUnitTime);
        if (latestUnit > 0.0f && viewUnits > latestUnit) {
            float targetSpacing = (railRight - railLeft) / latestUnit;
            float base = baseSpacing();
            zoomFactor = clamp(targetSpacing / base, MIN_ZOOM, MAX_ZOOM);
            unitSpacing = base * zoomFactor;
            viewUnits = Math.max(1.0f, (railRight - railLeft) / unitSpacing);
        }
        maxViewStartUnit = Math.max(0.0f, latestUnit - viewUnits);
        viewStartUnit = clamp(viewStartUnit, 0.0f, maxViewStartUnit);
    }

    private void drawOverviewBar(@NotNull GuiGraphics g, List<ChronicleEntry> entries) {
        int barX = panelX + BAR_INSET;
        int barW = panelW - BAR_INSET * 2;
        int scrollBarY = panelY + panelH - BAR_INSET - BAR_HEIGHT;
        int overviewY = scrollBarY - BAR_GAP - BAR_HEIGHT;

        g.fill(barX, overviewY, barX + barW, overviewY + BAR_HEIGHT, BAR_BG_COLOR);

        if (entries == null || entries.isEmpty()) {
            return;
        }

        float range = Math.max(1.0f, maxUnitTime);
        for (ChronicleEntry entry : entries) {
            long dayIndex = Math.max(0L, entry.dayIndex());
            float t = dayIndex / range;
            int x = (int) (barX + t * (barW - 1));
            int color = entryMarkerColor(entry);
            g.fill(x, overviewY + 1, x + 1, overviewY + BAR_HEIGHT - 1, color);
        }
    }

    private void drawScrollbar(@NotNull GuiGraphics g) {
        int barX = panelX + BAR_INSET;
        int barW = panelW - BAR_INSET * 2;
        int barY = panelY + panelH - BAR_INSET - BAR_HEIGHT;

        scrollbarX = barX;
        scrollbarY = barY;
        scrollbarW = barW;
        scrollbarH = BAR_HEIGHT;

        g.fill(barX, barY, barX + barW, barY + BAR_HEIGHT, PANEL_COLOR);

        float viewUnits = Math.max(1.0f, (railRight - railLeft) / unitSpacing);
        float totalUnits = Math.max(1.0f, maxUnitTime);
        float handleW = (maxUnitTime <= 0.0f || viewUnits >= totalUnits)
                ? barW
                : Math.max(24.0f, barW * (viewUnits / totalUnits));

        float progress = maxViewStartUnit <= 0.0f ? 0.0f : viewStartUnit / maxViewStartUnit;
        progress = clamp(progress, 0.0f, 1.0f);
        int handleX = (int) (barX + (barW - handleW) * progress);

        scrollHandleX = handleX;
        scrollHandleY = barY + 1;
        scrollHandleW = (int) handleW;
        scrollHandleH = BAR_HEIGHT - 2;
        g.fill(scrollHandleX, scrollHandleY, scrollHandleX + scrollHandleW, scrollHandleY + scrollHandleH, BAR_HANDLE_COLOR);
    }

    private void drawTimeTicks(@NotNull GuiGraphics g, CalendarDefinition def, float viewStartUnit, float viewUnits, int railY) {
        if (unitSpacing <= 0.0f) {
            return;
        }

        long startDay = (long) Math.floor(viewStartUnit);
        long endDay = (long) Math.ceil(viewStartUnit + viewUnits);
        if (endDay < 0) {
            return;
        }
        startDay = Math.max(0L, startDay);
        endDay = Math.min(endDay, maxUnitIndex);

        long daysPerYear = def.getDaysPerYear();
        long daysPerMonth = def.getDaysPerMonth();
        long unitDays;
        float unitPixels;

        if (scale == ChronicleScale.YEAR) {
            unitDays = Math.max(1L, daysPerYear);
            unitPixels = unitSpacing * unitDays;
        } else if (scale == ChronicleScale.MONTH) {
            unitDays = Math.max(1L, daysPerMonth);
            unitPixels = unitSpacing * unitDays;
        } else {
            unitDays = 1L;
            unitPixels = unitSpacing;
        }

        long startUnit = startDay / unitDays;
        long endUnit = endDay / unitDays;

        long step = chooseTickStep(unitPixels);
        if (step <= 0) {
            step = 1;
        }

        long first = alignToStep(endUnit, step);
        int labelY = railY + 10;
        for (long unit = first; unit >= startUnit; unit -= step) {
            long day = unit * unitDays;
            float x = railLeft + (day - viewStartUnit) * unitSpacing;
            if (x < railLeft || x > railRight) {
                continue;
            }
            g.fill((int) x, railY - 5, (int) x + 1, railY + 5, 0xFF4D5262);
            String label = formatTickLabel(def, day);
            int width = font.width(label);
            int labelX = (int) (x - width / 2.0f);
            if (labelX < railLeft) {
                labelX = railLeft;
            } else if (labelX + width > railRight) {
                labelX = railRight - width;
            }
            g.drawString(font, label, labelX, labelY, 0xFF8F93A2, false);
        }

        if (scale == ChronicleScale.DAY) {
            float hourSpacing = unitSpacing / 24.0f;
            if (hourSpacing >= 18.0f) {
                drawHourTicks(g, viewStartUnit, railY, hourSpacing, startDay, endDay);
            }
        }
    }

    private void drawHourTicks(@NotNull GuiGraphics g, float viewStartUnit, int railY, float hourSpacing, long startDay, long endDay) {
        int labelY = railY - 16;
        long hourStep = Math.max(1L, (long) Math.ceil(TICK_LABEL_MIN_SPACING / hourSpacing));

        if (endDay < 0) {
            return;
        }
        startDay = Math.max(0L, startDay);

        for (long day = startDay; day <= endDay; day++) {
            float dayX = railLeft + (day - viewStartUnit) * unitSpacing;
            if (dayX + unitSpacing < railLeft || dayX - unitSpacing > railRight) {
                continue;
            }
            for (long hour = 0; hour < 24; hour += hourStep) {
                float x = dayX + hour * hourSpacing;
                if (x < railLeft || x > railRight) {
                    continue;
                }
                g.fill((int) x, railY - 3, (int) x + 1, railY + 3, 0xFF3F4554);
                String label = String.format("%02dh", hour);
                g.drawString(font, label, (int) x + 2, labelY, 0xFF7A7F8E, false);
            }
        }
    }

    private long chooseTickStep(float spacing) {
        if (spacing <= 0.0f) {
            return 1L;
        }
        long step = (long) Math.ceil(TICK_LABEL_MIN_SPACING / spacing);
        return Math.max(1L, step);
    }

    private String formatTickLabel(CalendarDefinition def, long dayIndex) {
        if (dayIndex < 0) {
            dayIndex = 0;
        }
        CalendarParts parts = getCalendarParts(dayIndex, def);
        String suffix = def.getYearSuffix();
        if (scale == ChronicleScale.YEAR) {
            return parts.year + " " + suffix;
        }
        if (scale == ChronicleScale.MONTH) {
            String month = abbrevMonth(def.getMonthName(parts.monthIndex), false);
            return month + " " + parts.year + " " + suffix;
        }
        String month = abbrevMonth(def.getMonthName(parts.monthIndex), true);
        return parts.dayOfMonth + " " + month + " " + parts.year + " " + suffix;
    }

    private static long alignToStep(long value, long step) {
        return Math.floorDiv(value, step) * step;
    }

    private int entryMarkerColor(ChronicleEntry entry) {
        if (entry == null) {
            return 0xFF6B6F7C;
        }
        if (entry.type() == ChronicleEntryType.WORLD_FIRST) {
            return WORLD_FIRST_COLOR;
        }
        if (entry.type() == ChronicleEntryType.ADMIN_NOTE) {
            return ACCENT_COLOR;
        }
        if (entry.summary()) {
            return 0xFF5F7DFF;
        }
        return 0xFF6B6F7C;
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private boolean isAtRightEdge() {
        return Math.abs(viewStartUnit - maxViewStartUnit) <= 0.001f;
    }

    private void drawHeader(@NotNull GuiGraphics g) {
        Component title = Component.literal(buildChronicleTitle());
        g.drawString(font, title, panelX + 18, panelY + 18, 0xFFEDEDED, false);
    }

    private String buildChronicleTitle() {
        Minecraft mc = Minecraft.getInstance();
        if (tab == ChronicleTab.HALL_OF_FAME) {
            return "Hall of Fame";
        }
        if (tab == ChronicleTab.SERVER) {
            if (mc != null) {
                if (mc.getSingleplayerServer() != null) {
                    String worldName = mc.getSingleplayerServer().getWorldData().getLevelName();
                    if (worldName != null && !worldName.isBlank()) {
                        return "Chronicles of " + worldName;
                    }
                }
                if (mc.getCurrentServer() != null && mc.getCurrentServer().name != null) {
                    String serverName = mc.getCurrentServer().name;
                    if (!serverName.isBlank()) {
                        return "Chronicles of " + serverName;
                    }
                }
            }
        } else {
            if (mc != null && mc.player != null) {
                String playerName = mc.player.getGameProfile().getName();
                if (playerName != null && !playerName.isBlank()) {
                    return "Chronicles of " + playerName;
                }
            }
        }
        return "Chronicles";
    }

    private void drawTabs(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        int x = panelX + PANEL_PADDING;
        int y = panelY - TAB_HEIGHT + 2;
        int w = TAB_WIDTH;
        int h = TAB_HEIGHT;

        drawTab(g, x, y, w, h, "Server", tab == ChronicleTab.SERVER);
        drawTab(g, x + w + 8, y, w, h, "Personal", tab == ChronicleTab.PERSONAL);
        drawTabWithIcon(g, x + (w + 8) * 2, y, w, h, "Hall of Fame", tab == ChronicleTab.HALL_OF_FAME);
    }

    private void drawTab(@NotNull GuiGraphics g, int x, int y, int w, int h, String label, boolean active) {
        int activeColor = 0xFF171A22;
        int color = active ? activeColor : TAB_INACTIVE_COLOR;
        g.fill(x, y, x + w, y + h, color);
        if (active) {
            g.fill(x, y, x + w, y + 1, CARD_BORDER);
            g.fill(x, y + h - 1, x + w, y + h, CARD_BORDER);
            g.fill(x, y, x + 1, y + h, CARD_BORDER);
            g.fill(x + w - 1, y, x + w, y + h, CARD_BORDER);
            drawTabOrnaments(g, x, y, w);
        }
        int textW = font.width(label);
        int textX = x + (w - textW) / 2;
        g.drawString(font, label, textX, y + 6, 0xFFEDEDED, false);
    }

    private void drawTabWithIcon(@NotNull GuiGraphics g, int x, int y, int w, int h, String label, boolean active) {
        int activeColor = 0xFF171A22;
        int color = active ? activeColor : TAB_INACTIVE_COLOR;
        g.fill(x, y, x + w, y + h, color);
        if (active) {
            g.fill(x, y, x + w, y + 1, CARD_BORDER);
            g.fill(x, y + h - 1, x + w, y + h, CARD_BORDER);
            g.fill(x, y, x + 1, y + h, CARD_BORDER);
            g.fill(x + w - 1, y, x + w, y + h, CARD_BORDER);
            drawTabOrnaments(g, x, y, w);
        }
        int iconSize = 8;
        int gap = 6;
        int iconY = y + 6;
        int textW = font.width(label);
        int contentW = iconSize + gap + textW;
        int contentX = x + (w - contentW) / 2;
        int iconX = contentX;
        int textX = iconX + iconSize + gap;
        g.pose().pushPose();
        g.pose().scale(0.5f, 0.5f, 1.0f);
        g.blit(HALL_OF_FAME_ICON, iconX * 2, iconY * 2, 0, 0, 16, 16, 16, 16);
        g.pose().popPose();
        g.drawString(font, label, textX, y + 6, 0xFFEDEDED, false);
    }

    private void drawTabOrnaments(@NotNull GuiGraphics g, int x, int y, int w) {
        int c = CARD_BORDER;
        g.fill(x + 2, y + 2, x + 8, y + 3, c);
        g.fill(x + 2, y + 2, x + 3, y + 8, c);
        g.fill(x + w - 8, y + 2, x + w - 2, y + 3, c);
        g.fill(x + w - 3, y + 2, x + w - 2, y + 8, c);
    }

    private void drawJumpLabel(@NotNull GuiGraphics g) {
        g.drawString(font, "Jump to:", jumpLabelX, jumpLabelY, 0xFFEDEDED, false);
        drawMonthDropdownControl(g);
    }

    private void drawMonthDropdownControl(@NotNull GuiGraphics g) {
        if (jumpMonthNames.isEmpty()) {
            return;
        }
        int x = jumpMonthX;
        int y = jumpMonthY;
        int w = jumpMonthW;
        int h = jumpMonthH;
        g.fill(x, y, x + w, y + h, PANEL_COLOR);
        g.fill(x, y, x + w, y + 1, CARD_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, CARD_BORDER);
        g.fill(x, y, x + 1, y + h, CARD_BORDER);
        g.fill(x + w - 1, y, x + w, y + h, CARD_BORDER);
        String label = jumpMonthNames.get(Math.max(0, Math.min(jumpMonthIndex, jumpMonthNames.size() - 1)));
        g.drawString(font, label, x + 6, y + 5, 0xFFEDEDED, false);

        int caretX = x + w - 10;
        int caretY = y + 7;
        g.fill(caretX, caretY, caretX + 6, caretY + 1, 0xFFB0B4C2);
        g.fill(caretX + 1, caretY + 1, caretX + 5, caretY + 2, 0xFFB0B4C2);
        g.fill(caretX + 2, caretY + 2, caretX + 4, caretY + 3, 0xFFB0B4C2);
    }

    private void drawEventMonthDropdownControl(@NotNull GuiGraphics g) {
        if (!showAddPanel || eventMonthNames.isEmpty()) {
            return;
        }
        int x = eventMonthX;
        int y = eventMonthY;
        int w = eventMonthW;
        int h = eventMonthH;
        g.fill(x, y, x + w, y + h, PANEL_COLOR);
        g.fill(x, y, x + w, y + 1, CARD_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, CARD_BORDER);
        g.fill(x, y, x + 1, y + h, CARD_BORDER);
        g.fill(x + w - 1, y, x + w, y + h, CARD_BORDER);
        String label = eventMonthNames.get(Math.max(0, Math.min(eventMonthIndex, eventMonthNames.size() - 1)));
        g.drawString(font, label, x + 6, y + 5, 0xFFEDEDED, false);

        int caretX = x + w - 10;
        int caretY = y + 7;
        g.fill(caretX, caretY, caretX + 6, caretY + 1, 0xFFB0B4C2);
        g.fill(caretX + 1, caretY + 1, caretX + 5, caretY + 2, 0xFFB0B4C2);
        g.fill(caretX + 2, caretY + 2, caretX + 4, caretY + 3, 0xFFB0B4C2);

        // Dropdown list is rendered after widgets for proper z-order.
    }

    private void drawMonthDropdownList(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        if (jumpMonthNames.isEmpty()) {
            return;
        }
        int rowH = 18;
        int listW = jumpMonthW;
        int listH = jumpMonthNames.size() * rowH;
        int listX = jumpMonthX;
        int listY = jumpMonthY + jumpMonthH + 2;
        g.fill(listX, listY, listX + listW, listY + listH, CARD_COLOR);
        g.fill(listX, listY, listX + listW, listY + 1, CARD_BORDER);
        g.fill(listX, listY + listH - 1, listX + listW, listY + listH, CARD_BORDER);
        g.fill(listX, listY, listX + 1, listY + listH, CARD_BORDER);
        g.fill(listX + listW - 1, listY, listX + listW, listY + listH, CARD_BORDER);
        for (int i = 0; i < jumpMonthNames.size(); i++) {
            int rowY = listY + i * rowH;
            boolean hover = inRect(mouseX, mouseY, listX, rowY, listW, rowH);
            if (i == jumpMonthIndex) {
                g.fill(listX, rowY, listX + listW, rowY + rowH, PANEL_COLOR);
            } else if (hover) {
                g.fill(listX, rowY, listX + listW, rowY + rowH, TAB_INACTIVE_COLOR);
            }
            int textX = hover ? listX + 18 : listX + 12;
            g.drawString(font, jumpMonthNames.get(i), textX, rowY + 5, 0xFFEDEDED, false);
            if (hover) {
                drawDropdownOrnament(g, listX + 6, rowY + 5);
            }
        }
    }

    private void drawEventMonthDropdownList(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        if (eventMonthNames.isEmpty()) {
            return;
        }
        DropdownLayout layout = buildEventMonthDropdownLayout();
        g.fill(layout.x, layout.y, layout.x + layout.w, layout.y + layout.h, CARD_COLOR);
        g.fill(layout.x, layout.y, layout.x + layout.w, layout.y + 1, CARD_BORDER);
        g.fill(layout.x, layout.y + layout.h - 1, layout.x + layout.w, layout.y + layout.h, CARD_BORDER);
        g.fill(layout.x, layout.y, layout.x + 1, layout.y + layout.h, CARD_BORDER);
        g.fill(layout.x + layout.w - 1, layout.y, layout.x + layout.w, layout.y + layout.h, CARD_BORDER);
        for (int i = 0; i < layout.visibleCount; i++) {
            int idx = layout.startIndex + i;
            if (idx >= eventMonthNames.size()) {
                break;
            }
            int rowY = layout.y + i * layout.rowH;
            boolean hover = inRect(mouseX, mouseY, layout.x, rowY, layout.w, layout.rowH);
            if (idx == eventMonthIndex) {
                g.fill(layout.x, rowY, layout.x + layout.w, rowY + layout.rowH, PANEL_COLOR);
            } else if (hover) {
                g.fill(layout.x, rowY, layout.x + layout.w, rowY + layout.rowH, TAB_INACTIVE_COLOR);
            }
            int textX = hover ? layout.x + 18 : layout.x + 12;
            g.drawString(font, eventMonthNames.get(idx), textX, rowY + 5, 0xFFEDEDED, false);
            if (hover) {
                drawDropdownOrnament(g, layout.x + 6, rowY + 5);
            }
        }
    }

    private void drawDropdownOrnament(@NotNull GuiGraphics g, int x, int y) {
        int c = 0xFFB0B4C2;
        g.fill(x, y + 3, x + 4, y + 4, c);
        g.fill(x + 1, y + 2, x + 3, y + 3, c);
        g.fill(x + 1, y + 4, x + 3, y + 5, c);
    }

    private void drawTimeline(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return;
        }
        boolean blockTimelineHover = selectedSection != null || showAddPanel;

        CalendarDefinition def = RPGTimelineApi.getCalendarDefinition();
        refreshJumpMonthList(def);
        long dayTime = mc.level.getDayTime();
        long currentDayIndex = Math.max(0L, RPGTimelineApi.getDayIndexForGameTime(dayTime));
        currentUnitTime = Math.max(0.0f, getDayTimeUnits(dayTime, def));
        currentUnitIndex = currentDayIndex;
        unitSpacing = scaleSpacing();

        int scrollBarY = panelY + panelH - BAR_INSET - BAR_HEIGHT;
        int overviewY = scrollBarY - BAR_GAP - BAR_HEIGHT;
        int timelineTop = jumpMonthY + jumpMonthH + 12;
        int timelineBottom = overviewY - 12;
        if (timelineBottom <= timelineTop) {
            timelineTop = panelY + 72;
            timelineBottom = panelY + panelH - 72;
        }
        int railY = timelineTop + (timelineBottom - timelineTop) / 2;
        railLeft = panelX + 32;
        railRight = panelX + panelW - 32;

        timelineAreaX = panelX + PANEL_PADDING;
        timelineAreaY = timelineTop;
        timelineAreaW = panelW - PANEL_PADDING * 2;
        timelineAreaH = Math.max(0, timelineBottom - timelineTop);
        g.fill(timelineAreaX, timelineAreaY, timelineAreaX + timelineAreaW, timelineAreaY + timelineAreaH, BG_COLOR);
        drawTimelineBorder(g);
        g.enableScissor(timelineAreaX, timelineAreaY, timelineAreaX + timelineAreaW, timelineAreaY + timelineAreaH);

        g.fill(railLeft, railY - 1, railRight, railY + 1, 0xFF3A3D4A);

        List<ChronicleEntry> allEntries = getEntriesForTab();
        minUnitIndex = 0L;
        maxUnitIndex = currentUnitIndex;
        maxUnitTime = currentUnitTime;
        for (ChronicleEntry entry : allEntries) {
            long dayIndex = entry.dayIndex();
            if (dayIndex < 0L) {
                continue;
            }
            if (dayIndex > maxUnitIndex) {
                maxUnitIndex = dayIndex;
            }
            if (dayIndex > maxUnitTime) {
                maxUnitTime = dayIndex;
            }
        }
        if (maxUnitTime < maxUnitIndex) {
            maxUnitTime = maxUnitIndex;
        }

        boolean wasAtRight = isAtRightEdge();
        updateScrollBounds();
        if (snapToLatest) {
            viewStartUnit = maxViewStartUnit;
            snapToLatest = false;
        } else if (wasAtRight) {
            viewStartUnit = maxViewStartUnit;
        }

        float viewUnits = Math.max(1.0f, (railRight - railLeft) / unitSpacing);
        scale = chooseScaleForView(viewUnits, def);

        List<ChronicleEntry> entries = getFilteredEntries();
        int railWidth = Math.max(1, railRight - railLeft);
        int sectionStride = SECTION_PIXEL_WIDTH + SECTION_PIXEL_GAP;
        int sectionCount = Math.max(1, (railWidth + SECTION_PIXEL_GAP) / Math.max(1, sectionStride));
        double sectionSpan = Math.max(1.0, viewUnits / sectionCount);
        double viewStart = viewStartUnit;
        double viewEnd = viewStartUnit + viewUnits;
        long viewStartDay = (long) Math.floor(viewStart);
        long viewEndDay = (long) Math.ceil(viewEnd);

        Map<Integer, SectionBucket> sections = new HashMap<>();
        for (ChronicleEntry entry : entries) {
            long dayIndex = entry.dayIndex();
            if (dayIndex < 0L || dayIndex > maxUnitIndex) {
                continue;
            }
            if (dayIndex < viewStartDay || dayIndex > viewEndDay) {
                continue;
            }
            int sectionIndex = (int) Math.floor((dayIndex - viewStart) / sectionSpan);
            sectionIndex = Math.max(0, Math.min(sectionIndex, sectionCount - 1));
            SectionBucket bucket = sections.computeIfAbsent(sectionIndex, SectionBucket::new);
            bucket.add(entry, maxUnitIndex);
        }
        float currentX = railLeft + (currentUnitTime - viewStartUnit) * unitSpacing;
        if (currentX >= railLeft && currentX <= railRight) {
            g.fill((int) currentX - 1, railY - 10, (int) currentX + 1, railY + 10, ACCENT_COLOR);
        }

        drawTimeTicks(g, def, viewStartUnit, viewUnits, railY);

        List<Integer> keys = new ArrayList<>(sections.keySet());
        keys.sort(Integer::compareTo);

        for (Integer sectionIndex : keys) {
            SectionBucket bucket = sections.get(sectionIndex);
            if (bucket == null) {
                continue;
            }
            float x = railLeft + (float) ((bucket.anchorTime - viewStartUnit) * unitSpacing);
            if (x < railLeft || x > railRight) {
                continue;
            }

            g.fill((int) x - 3, railY - 3, (int) x + 3, railY + 3, 0xFFB9BCC6);

            List<ChronicleEntry> nodes = new ArrayList<>();
            ChronicleEntry groupedOther = buildGroupedEntry(bucket.others, NodeCategory.OTHER, bucket.anchorDay);
            if (groupedOther != null) {
                nodes.add(groupedOther);
            }
            ChronicleEntry groupedWorldFirsts = buildGroupedEntry(bucket.worldFirsts, NodeCategory.WORLD_FIRST, bucket.anchorDay);
            if (groupedWorldFirsts != null) {
                nodes.add(groupedWorldFirsts);
            }
            ChronicleEntry groupedNotes = buildGroupedEntry(bucket.notes, NodeCategory.NOTES, bucket.anchorDay);
            if (groupedNotes != null) {
                nodes.add(groupedNotes);
            }

            int stack = 0;
            for (ChronicleEntry entry : nodes) {
                int nodeOffset = NODE_BASE_OFFSET + stack * NODE_STACK_SPACING;
                int nodeY = railY - nodeOffset;
                int nodeSize = 6;
                int nodeX = (int) x - nodeSize / 2;

                int stemX = (int) x;
                int stemY0 = railY;
                int stemY1 = nodeY;
                g.fill(stemX, Math.min(stemY0, stemY1), stemX + 1, Math.max(stemY0, stemY1), 0xFF5C606E);

                int nodeColor = entryMarkerColor(entry);
                g.fill(nodeX, nodeY - nodeSize / 2, nodeX + nodeSize, nodeY + nodeSize / 2, nodeColor);
                NodeBounds bounds = new NodeBounds(entry, bucket, nodeX - 2, nodeY - 4, nodeSize + 4, nodeSize + 8);
                nodeBounds.add(bounds);
                if (!blockTimelineHover && hoveredEntry == null && bounds.contains(mouseX, mouseY)) {
                    hoveredEntry = entry;
                    hoveredNodeX = (int) x;
                    hoveredNodeY = nodeY;
                }
                stack++;
            }
        }

        if (hoveredEntry != null) {
            g.disableScissor();
            drawHoverTooltip(g, hoveredEntry, mouseX, mouseY);
        } else {
            g.disableScissor();
        }
        drawOverviewBar(g, entries);
        drawScrollbar(g);
    }

    private void drawTimelineBorder(@NotNull GuiGraphics g) {
        if (timelineAreaW <= 0 || timelineAreaH <= 0) {
            return;
        }
        int x0 = timelineAreaX;
        int y0 = timelineAreaY;
        int x1 = timelineAreaX + timelineAreaW;
        int y1 = timelineAreaY + timelineAreaH;
        g.fill(x0, y0, x1, y0 + 1, CARD_BORDER);
        g.fill(x0, y1 - 1, x1, y1, CARD_BORDER);
        g.fill(x0, y0, x0 + 1, y1, CARD_BORDER);
        g.fill(x1 - 1, y0, x1, y1, CARD_BORDER);
    }

    private void drawHallOfFame(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        drawHallOfFameBackground(g, mouseX, mouseY);
        drawHallOfFameRows(g, mouseX, mouseY);
    }

    private void drawHallOfFameBackground(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        List<HallOfFameEntry> entries = ChroniclePayloads.ClientState.getHallOfFameEntries();
        int listX = panelX + PANEL_PADDING;
        int listY = panelY + 56;
        int listW = panelW - PANEL_PADDING * 2;
        if (selectedHallUuid != null) {
            listW = Math.max(120, listW - DETAIL_PANEL_WIDTH);
        }
        int listBottom = panelY + panelH - 24;
        int listH = Math.max(1, listBottom - listY);

        int rowH = 26;
        int contentH = entries.size() * rowH;
        hallScrollMax = Math.max(0, contentH - listH);
        hallScrollOffset = (int) clamp(hallScrollOffset, 0, hallScrollMax);

        int y = listY - hallScrollOffset;
        for (int i = 0; i < entries.size(); i++) {
            HallOfFameEntry entry = entries.get(i);
            int rowY = y + i * rowH;
            if (rowY + rowH < listY || rowY > listBottom) {
                continue;
            }
            int rowColor = (i % 2 == 0) ? CARD_COLOR : PANEL_COLOR;
            g.fill(listX, rowY, listX + listW, rowY + rowH - 1, rowColor);
        }

        if (entries.isEmpty()) {
            g.drawString(font, "No world-firsts recorded yet.", listX, listY + 4, 0xFF9AA0AF, false);
        }
    }

    private void drawHallOfFameRows(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        List<HallOfFameEntry> entries = ChroniclePayloads.ClientState.getHallOfFameEntries();
        int listX = panelX + PANEL_PADDING;
        int listY = panelY + 56;
        int listW = panelW - PANEL_PADDING * 2;
        if (selectedHallUuid != null) {
            listW = Math.max(120, listW - DETAIL_PANEL_WIDTH);
        }
        int listBottom = panelY + panelH - 24;

        int rowH = 26;
        int y = listY - hallScrollOffset;
        for (int i = 0; i < entries.size(); i++) {
            HallOfFameEntry entry = entries.get(i);
            int rowY = y + i * rowH;
            if (rowY + rowH < listY || rowY > listBottom) {
                continue;
            }
            int faceX = listX + 6;
            int faceY = rowY + 4;
            drawPlayerFace(g, entry, faceX, faceY, 16);

            int textX = faceX + 20;
            g.drawString(font, entry.playerName(), textX, rowY + 8, 0xFFE0E2EC, false);

            String countText = Integer.toString(entry.worldFirstCount());
            int countW = font.width(countText);
            int countAreaRight = listX + listW - 60 - 10;
            int iconSize = 16;
            int gap = 4;
            int totalW = iconSize + gap + countW;
            int countX = countAreaRight - totalW + iconSize + gap;
            int iconX = countX - gap - iconSize;
            int iconY = rowY + 5;
            g.renderItem(new ItemStack(RPGTimelineItems.CHRONICLE_WORLD_FIRST.get()), iconX, iconY);
            g.drawString(font, countText, countX, rowY + 8, WORLD_FIRST_COLOR, false);

            int btnW = 50;
            int btnH = 18;
            int btnX = listX + listW - btnW - 8;
            int btnY = rowY + 4;
            boolean hover = inRect(mouseX, mouseY, btnX, btnY, btnW, btnH);
            int btnColor = hover ? ACCENT_COLOR : CARD_BORDER;
            g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnColor);
            g.drawString(font, "View", btnX + 12, btnY + 5, 0xFFEDEDED, false);

            hallRowBounds.add(new HallRowBounds(entry.playerUuid(), btnX, btnY, btnW, btnH));
        }
    }

    private void drawHallOfFameDetailPanel(@NotNull GuiGraphics g, String playerUuid, int mouseX, int mouseY) {
        List<ChronicleEntry> entries = ChroniclePayloads.ClientState.getHallOfFameDetails(playerUuid);
        String playerName = findHallOfFameName(playerUuid);

        int panelWidth = DETAIL_PANEL_WIDTH;
        int panelX = width - panelWidth;
        int panelY = 0;
        int panelHeight = height;

        detailPanelX = panelX;
        detailPanelY = panelY;
        detailPanelW = panelWidth;
        detailPanelH = panelHeight;

        g.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_COLOR);
        drawDetailPanelBorder(g, panelX, panelY, panelHeight);
        drawDetailPanelBorder(g, panelX, panelY, panelHeight);

        closeButtonX = panelX + 12;
        closeButtonY = panelY + panelHeight - 30;
        closeButtonW = panelWidth - 24;
        closeButtonH = 20;
        drawDetailCloseButton(g, mouseX, mouseY);

        int headerX = panelX + 12;
        int headerY = panelY + 12;
        ItemStack headerIcon = new ItemStack(RPGTimelineItems.CHRONICLE_WORLD_FIRST.get());
        g.renderItem(headerIcon, headerX, headerY);
        g.drawString(font, "World Firsts - " + playerName, headerX + 20, headerY + 4, WORLD_FIRST_COLOR, false);

        int innerX = panelX + 12;
        int innerW = panelWidth - 24;
        int lineHeight = font.lineHeight;
        int iconSize = 16;
        int rowGap = 8;
        int textX = innerX + iconSize + 8;
        int textWidth = innerW - iconSize - 8;

        String header = playerName.isBlank() ? "World Firsts" : "World Firsts - " + playerName;
        int contentAreaY = headerY + lineHeight + 10;
        int contentAreaH = panelHeight - 56;
        int contentHeight = lineHeight + 12;
        for (ChronicleEntry entry : entries) {
            ChronicleDetail detail = new ChronicleDetail(entry.title(), entry.iconItemId(), entry.dayIndex(), entry.actorUuid(), entry.actorName());
            contentHeight += measureDetailHeight(ChronicleEntryType.WORLD_FIRST, detail, textWidth, lineHeight, iconSize) + rowGap;
        }
        detailScrollMax = Math.max(0, contentHeight - contentAreaH);
        detailScrollOffset = (int) clamp(detailScrollOffset, 0, detailScrollMax);

        int y = contentAreaY - detailScrollOffset;
        g.fill(innerX, y, innerX + innerW, y + 1, CARD_BORDER);
        y += 8;

        if (entries.isEmpty()) {
            g.drawString(font, "No world-firsts yet.", innerX, y, 0xFF9AA0AF, false);
            drawDetailScrollbar(g, contentAreaY, contentAreaH);
            return;
        }

        for (ChronicleEntry entry : entries) {
            ChronicleDetail detail = new ChronicleDetail(entry.title(), entry.iconItemId(), entry.dayIndex(), entry.actorUuid(), entry.actorName());
            int rowHeight = measureDetailHeight(ChronicleEntryType.WORLD_FIRST, detail, textWidth, lineHeight, iconSize);
            if (y + rowHeight < contentAreaY) {
                y += rowHeight + rowGap;
                continue;
            }
            if (y > contentAreaY + contentAreaH) {
                break;
            }

            ItemStack icon = iconForDetail(ChronicleEntryType.WORLD_FIRST, detail);
            g.renderItem(icon, innerX, y);

            int textY = y;
            String title = detail.title();
            if (title != null && title.startsWith("World First: ")) {
                title = title.substring("World First: ".length());
            }
            g.drawString(font, "World First: " + title, textX, textY, 0xFFE0E2EC, false);
            textY += lineHeight;
            String author = detail.actorName() == null || detail.actorName().isBlank() ? "Unknown" : detail.actorName();
            g.drawString(font, "Completed by: " + author, textX, textY, 0xFFB0B4C2, false);
            textY += lineHeight;
            g.drawString(font, RPGTimelineApi.buildDateString(detail.dayIndex()), textX, textY, 0xFF8F93A2, false);

            y += rowHeight + rowGap;
        }

        drawDetailScrollbar(g, contentAreaY, contentAreaH);
    }

    private String findHallOfFameName(String playerUuid) {
        if (playerUuid == null) {
            return "";
        }
        List<HallOfFameEntry> entries = ChroniclePayloads.ClientState.getHallOfFameEntries();
        for (HallOfFameEntry entry : entries) {
            if (playerUuid.equals(entry.playerUuid())) {
                return entry.playerName() == null ? "" : entry.playerName();
            }
        }
        return "";
    }

    private void drawPlayerFace(@NotNull GuiGraphics g, HallOfFameEntry entry, int x, int y, int size) {
        try {
            java.util.UUID uuid = java.util.UUID.fromString(entry.playerUuid());
            GameProfile profile = new GameProfile(uuid, entry.playerName());
            net.minecraft.client.resources.PlayerSkin skin = Minecraft.getInstance().getSkinManager().getInsecureSkin(profile);
            PlayerFaceRenderer.draw(g, skin, x, y, size);
        } catch (Throwable t) {
            // fallback: no face
        }
    }

    private void drawHoverTooltip(@NotNull GuiGraphics g, ChronicleEntry entry, int mouseX, int mouseY) {
        ItemStack stack = tooltipStackForEntry(entry);
        Component title = Component.literal(entry.title());
        if (entry.type() == ChronicleEntryType.WORLD_FIRST) {
            title = title.copy().withStyle(ChatFormatting.GOLD);
        }
        stack.set(DataComponents.CUSTOM_NAME, title);
        List<Component> lines = getTooltipFromItem(minecraft, stack);
        int maxWidth = 0;
        for (Component line : lines) {
            maxWidth = Math.max(maxWidth, font.width(line));
        }
        ChronicleItemTooltipClient.setExpectedWidth(maxWidth);
        g.renderTooltip(font, stack, mouseX, mouseY);
        ChronicleItemTooltipClient.clearExpectedWidth();
    }

    private ItemStack tooltipStackForEntry(ChronicleEntry entry) {
        if (entry.type() == ChronicleEntryType.ADMIN_NOTE) {
            return new ItemStack(RPGTimelineItems.CHRONICLE_NOTE.get());
        }
        if (entry.type() == ChronicleEntryType.WORLD_FIRST) {
            return new ItemStack(RPGTimelineItems.CHRONICLE_WORLD_FIRST.get());
        }
        return new ItemStack(RPGTimelineItems.CHRONICLE_ADVANCEMENT.get());
    }


    private DropdownLayout buildEventMonthDropdownLayout() {
        int rowH = 18;
        int total = eventMonthNames.size();
        int listX = eventMonthX;
        int listW = eventMonthW;
        int maxY = addPanelY + addPanelH - 8;
        int below = maxY - (eventMonthY + eventMonthH + 2);
        int available = Math.max(rowH, below);
        int maxRows = Math.max(1, available / rowH);
        int visibleCount = Math.min(total, maxRows);
        int listH = visibleCount * rowH;
        int listY = eventMonthY + eventMonthH + 2;

        int maxStart = Math.max(0, total - visibleCount);
        eventMonthScroll = Math.max(0, Math.min(eventMonthScroll, maxStart));

        return new DropdownLayout(listX, listY, listW, listH, rowH, visibleCount, eventMonthScroll);
    }
    private void drawDetailPanel(@NotNull GuiGraphics g, SectionBucket bucket, int mouseX, int mouseY) {
        int panelWidth = DETAIL_PANEL_WIDTH;
        int panelX = width - panelWidth;
        int panelY = 0;
        int panelHeight = height;

        detailPanelX = panelX;
        detailPanelY = panelY;
        detailPanelW = panelWidth;
        detailPanelH = panelHeight;

        g.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_COLOR);

        closeButtonX = panelX + 12;
        closeButtonY = panelY + panelHeight - 30;
        closeButtonW = panelWidth - 24;
        closeButtonH = 20;
        drawDetailCloseButton(g, mouseX, mouseY);

        int innerX = panelX + 12;
        int innerW = panelWidth - 24;
        int lineHeight = font.lineHeight;
        int iconSize = 16;
        int rowGap = 8;
        int groupGap = 10;
        int textX = innerX + iconSize + 8;
        int textWidth = innerW - iconSize - 8;

        List<ChronicleEntry> groups = buildSectionGroups(bucket);
        int contentAreaY = panelY + 12;
        int contentAreaH = panelHeight - 56;

        int contentHeight = 0;
        for (ChronicleEntry group : groups) {
            List<ChronicleDetail> drilldown = resolveDrilldown(group);
            int groupHeight = lineHeight + 6 + 1 + 6;
            for (ChronicleDetail detail : drilldown) {
                groupHeight += measureDetailHeight(group.type(), detail, textWidth, lineHeight, iconSize) + rowGap;
            }
            contentHeight += groupHeight + groupGap;
        }
        if (contentHeight > 0) {
            contentHeight -= groupGap;
        }
        detailScrollMax = Math.max(0, contentHeight - contentAreaH);
        detailScrollOffset = (int) clamp(detailScrollOffset, 0, detailScrollMax);

        int y = contentAreaY - detailScrollOffset;
        for (ChronicleEntry group : groups) {
            int headerColor = headerColorForType(group.type());
            List<ChronicleDetail> drilldown = resolveDrilldown(group);

            ItemStack headerIcon = headerIconForType(group.type());
            g.renderItem(headerIcon, innerX, y - 1);
            g.drawString(font, group.title(), innerX + iconSize + 6, y, headerColor, false);
            y += lineHeight + 10;
            g.fill(innerX, y, innerX + innerW, y + 1, CARD_BORDER);
            y += 6;

            for (ChronicleDetail detail : drilldown) {
                int rowHeight = measureDetailHeight(group.type(), detail, textWidth, lineHeight, iconSize);
                if (y + rowHeight < contentAreaY) {
                    y += rowHeight + rowGap;
                    continue;
                }
                if (y > contentAreaY + contentAreaH) {
                    break;
                }

                ItemStack icon = iconForDetail(group.type(), detail);
                g.renderItem(icon, innerX, y);

                int textY = y;
                if (group.type() == ChronicleEntryType.ADMIN_NOTE) {
                    g.drawString(font, detail.title(), textX, textY, 0xFFE0E2EC, false);
                    textY += lineHeight;

                    String noteDetails = detail.subtitle();
                    if (noteDetails != null && !noteDetails.isBlank()) {
                        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(noteDetails), textWidth);
                        for (net.minecraft.util.FormattedCharSequence line : lines) {
                            if (textY + lineHeight >= contentAreaY && textY <= contentAreaY + contentAreaH) {
                                g.drawString(font, line, textX, textY, 0xFFC8CBD6, false);
                            }
                            textY += lineHeight;
                        }
                    }

                    String author = detail.actorName() == null || detail.actorName().isBlank() ? "Server" : detail.actorName();
                    g.drawString(font, "By: " + author, textX, textY, 0xFFB0B4C2, false);
                    textY += lineHeight;
                    g.drawString(font, RPGTimelineApi.buildDateString(detail.dayIndex()), textX, textY, 0xFF8F93A2, false);
                } else {
                    String label = group.type() == ChronicleEntryType.WORLD_FIRST ? "World First: " : "Advancement: ";
                    String title = detail.title();
                    if (group.type() == ChronicleEntryType.WORLD_FIRST && title != null && title.startsWith("World First: ")) {
                        title = title.substring("World First: ".length());
                    }
                    g.drawString(font, label + title, textX, textY, 0xFFE0E2EC, false);
                    textY += lineHeight;
                    String author = detail.actorName() == null || detail.actorName().isBlank() ? "Unknown" : detail.actorName();
                    g.drawString(font, "Completed by: " + author, textX, textY, 0xFFB0B4C2, false);
                    textY += lineHeight;
                    g.drawString(font, RPGTimelineApi.buildDateString(detail.dayIndex()), textX, textY, 0xFF8F93A2, false);
                }

                y += rowHeight + rowGap;
            }
            y += groupGap;
        }

        drawDetailScrollbar(g, contentAreaY, contentAreaH);
    }

    private void drawAddPanel(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        refreshEventMonthList(RPGTimelineApi.getCalendarDefinition());
        layoutAddPanel();
        g.fill(addPanelX, addPanelY, addPanelX + addPanelW, addPanelY + addPanelH, PANEL_COLOR);
        drawAddPanelBorder(g);
        g.drawString(font, "Add Event", addPanelX + 12, addPanelY + 10, 0xFFEDEDED, false);
        g.drawString(font, "Date:", addPanelX + 16, addPanelY + 66 + 5, 0xFFB0B4C2, false);
        drawEventMonthDropdownControl(g);
        drawAddPanelButtons(g, mouseX, mouseY);
    }

    private void drawAddPanelBorder(@NotNull GuiGraphics g) {
        int x0 = addPanelX;
        int y0 = addPanelY;
        int x1 = addPanelX + addPanelW;
        int y1 = addPanelY + addPanelH;
        g.fill(x0, y0, x1, y0 + 1, CARD_BORDER);
        g.fill(x0, y1 - 1, x1, y1, CARD_BORDER);
        g.fill(x0, y0, x0 + 1, y1, CARD_BORDER);
        g.fill(x1 - 1, y0, x1, y1, CARD_BORDER);
        int midX = addPanelX + addPanelW / 2;
        g.fill(midX - 10, y0, midX + 10, y0 + 2, CARD_BORDER);
    }

    private void drawDetailPanelBorder(@NotNull GuiGraphics g, int x, int y, int h) {
        int x0 = x;
        int y0 = y;
        int y1 = y + h;
        g.fill(x0, y0, x0 + 1, y1, CARD_BORDER);
        int midY = y + h / 2;
        g.fill(x0, midY - 12, x0 + 2, midY + 12, CARD_BORDER);
        g.fill(x0, y0 + 8, x0 + 3, y0 + 9, CARD_BORDER);
        g.fill(x0, y1 - 9, x0 + 3, y1 - 8, CARD_BORDER);
    }

    private void drawAddPanelButtons(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        boolean suppressHover = false;
        if (showEventMonthDropdown) {
            DropdownLayout layout = buildEventMonthDropdownLayout();
            suppressHover = inRect(mouseX, mouseY, layout.x, layout.y, layout.w, layout.h);
        }
        if (suppressHover) {
            hoverSubmitButton = false;
            hoverCancelButton = false;
        } else {
            hoverSubmitButton = inRect(mouseX, mouseY, submitButtonX, submitButtonY, submitButtonW, submitButtonH);
            hoverCancelButton = inRect(mouseX, mouseY, cancelButtonX, cancelButtonY, cancelButtonW, cancelButtonH);
        }

        drawTopButton(g, submitButtonX, submitButtonY, submitButtonW, submitButtonH, "Submit", hoverSubmitButton);
        drawTopButton(g, cancelButtonX, cancelButtonY, cancelButtonW, cancelButtonH, "Cancel", hoverCancelButton);
    }

    private void drawDetailCloseButton(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        hoverCloseButton = inRect(mouseX, mouseY, closeButtonX, closeButtonY, closeButtonW, closeButtonH);
        drawTopButton(g, closeButtonX, closeButtonY, closeButtonW, closeButtonH, "Close", hoverCloseButton);
    }

    private List<ChronicleEntry> buildSectionGroups(SectionBucket bucket) {
        if (bucket == null) {
            return List.of();
        }
        List<ChronicleEntry> groups = new ArrayList<>(3);
        ChronicleEntry notes = buildGroupedEntry(bucket.notes, NodeCategory.NOTES, bucket.anchorDay);
        if (notes != null) {
            groups.add(notes);
        }
        ChronicleEntry worldFirsts = buildGroupedEntry(bucket.worldFirsts, NodeCategory.WORLD_FIRST, bucket.anchorDay);
        if (worldFirsts != null) {
            groups.add(worldFirsts);
        }
        ChronicleEntry others = buildGroupedEntry(bucket.others, NodeCategory.OTHER, bucket.anchorDay);
        if (others != null) {
            groups.add(others);
        }
        return groups;
    }

    private List<ChronicleDetail> resolveDrilldown(ChronicleEntry entry) {
        List<ChronicleDetail> drilldown = entry.drilldown();
        if (drilldown != null && !drilldown.isEmpty()) {
            return drilldown;
        }
        String subtitle = entry.type() == ChronicleEntryType.ADMIN_NOTE
                ? (entry.details() == null ? "" : entry.details())
                : (entry.iconItemId() == null ? "" : entry.iconItemId());
        return List.of(new ChronicleDetail(entry.title(), subtitle, entry.dayIndex(), entry.actorUuid(), entry.actorName()));
    }

    private int headerColorForType(ChronicleEntryType type) {
        if (type == ChronicleEntryType.WORLD_FIRST) {
            return WORLD_FIRST_COLOR;
        }
        if (type == ChronicleEntryType.ADMIN_NOTE) {
            return ACCENT_COLOR;
        }
        return 0xFFEDEDED;
    }

    private int measureDetailHeight(ChronicleEntryType type, ChronicleDetail detail, int textWidth, int lineHeight, int iconSize) {
        int textHeight;
        if (type == ChronicleEntryType.ADMIN_NOTE) {
            int lines = 3;
            String details = detail.subtitle();
            if (details != null && !details.isBlank()) {
                lines += font.split(Component.literal(details), textWidth).size();
            }
            textHeight = lines * lineHeight;
        } else {
            textHeight = lineHeight * 3;
        }
        return Math.max(iconSize, textHeight);
    }

    private ItemStack iconForDetail(ChronicleEntryType type, ChronicleDetail detail) {
        if (type == ChronicleEntryType.ADMIN_NOTE) {
            return new ItemStack(Items.BOOK);
        }
        String id = detail.subtitle();
        if (id != null && !id.isBlank()) {
            try {
                ResourceLocation rl = ResourceLocation.parse(id);
                return new ItemStack(BuiltInRegistries.ITEM.get(rl));
            } catch (Throwable ignored) {
            }
        }
        return new ItemStack(Items.PAPER);
    }

    private ItemStack headerIconForType(ChronicleEntryType type) {
        if (type == ChronicleEntryType.ADMIN_NOTE) {
            return new ItemStack(RPGTimelineItems.CHRONICLE_NOTE.get());
        }
        if (type == ChronicleEntryType.WORLD_FIRST) {
            return new ItemStack(RPGTimelineItems.CHRONICLE_WORLD_FIRST.get());
        }
        return new ItemStack(RPGTimelineItems.CHRONICLE_ADVANCEMENT.get());
    }

    private void drawDetailScrollbar(@NotNull GuiGraphics g, int contentY, int contentH) {
        int barW = 6;
        int barX = detailPanelX + detailPanelW - barW - 6;
        int barY = contentY;
        int barH = contentH;
        detailScrollBarX = barX;
        detailScrollBarY = barY;
        detailScrollBarW = barW;
        detailScrollBarH = barH;

        g.fill(barX, barY, barX + barW, barY + barH, PANEL_COLOR);

        if (detailScrollMax <= 0) {
            detailScrollHandleY = barY;
            detailScrollHandleH = barH;
            g.fill(barX, barY, barX + barW, barY + barH, BAR_HANDLE_COLOR);
            return;
        }

        int handleH = Math.max(12, (int) ((barH / (float) (detailScrollMax + barH)) * barH));
        int maxOffset = barH - handleH;
        int handleY = barY + (int) ((detailScrollOffset / (float) detailScrollMax) * maxOffset);

        detailScrollHandleY = handleY;
        detailScrollHandleH = handleH;
        g.fill(barX, handleY, barX + barW, handleY + handleH, BAR_HANDLE_COLOR);
    }

    private List<ChronicleEntry> getFilteredEntries() {
        return getEntriesForTab();
    }

    private List<ChronicleEntry> getEntriesForTab() {
        List<ChronicleEntry> entries = tab == ChronicleTab.SERVER
                ? ChroniclePayloads.ClientState.getServerEntries()
                : ChroniclePayloads.ClientState.getPersonalEntries();
        return entries == null ? List.of() : entries;
    }

    private ChronicleEntry buildGroupedEntry(List<ChronicleEntry> entries, NodeCategory category, long anchorDay) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        int count = entries.size();
        ChronicleEntry first = entries.get(0);
        ChronicleEntryType type;
        boolean highlight = false;
        String title;
        String details = "";

        if (category == NodeCategory.NOTES) {
            type = ChronicleEntryType.ADMIN_NOTE;
            title = formatCountTitle(count, "Note");
        } else if (category == NodeCategory.WORLD_FIRST) {
            type = ChronicleEntryType.WORLD_FIRST;
            highlight = true;
            title = formatCountTitle(count, "World-first advancement");
        } else {
            type = ChronicleEntryType.ADVANCEMENT;
            title = formatCountTitle(count, "Advancement");
        }

        String iconId = first.iconItemId();
        if (iconId == null || iconId.isBlank()) {
            iconId = category == NodeCategory.NOTES ? "minecraft:book" : "minecraft:paper";
        }

        List<ChronicleDetail> drilldown = buildDrilldown(entries);
        return new ChronicleEntry(
                "section:" + category.name().toLowerCase() + ":" + anchorDay,
                type,
                first.scope(),
                anchorDay,
                title,
                details,
                "",
                "",
                highlight,
                true,
                iconId,
                drilldown
        );
    }

    private String formatCountTitle(int count, String base) {
        String label = base;
        if (count != 1) {
            if (!base.endsWith("s")) {
                label = base + "s";
            }
        }
        return count + "x " + label;
    }

    private List<ChronicleDetail> buildDrilldown(List<ChronicleEntry> entries) {
        List<ChronicleDetail> details = new ArrayList<>();
        entries.stream()
                .sorted(Comparator.comparingLong(ChronicleEntry::dayIndex))
                .limit(MAX_GROUP_DETAILS)
                .forEach(entry -> {
                    String subtitle = "";
                    if (entry.type() == ChronicleEntryType.ADMIN_NOTE) {
                        subtitle = entry.details() == null ? "" : entry.details();
                    } else if (entry.iconItemId() != null) {
                        subtitle = entry.iconItemId();
                    }
                    details.add(new ChronicleDetail(entry.title(), subtitle, entry.dayIndex(), entry.actorUuid(), entry.actorName()));
                });
        return details;
    }

    private ChronicleScale chooseScaleForView(float viewUnits, CalendarDefinition def) {
        float daysPerYear = def.getDaysPerYear();
        float daysPerMonth = def.getDaysPerMonth();
        if (viewUnits > daysPerYear) {
            return ChronicleScale.YEAR;
        }
        if (viewUnits > daysPerMonth) {
            return ChronicleScale.MONTH;
        }
        return ChronicleScale.DAY;
    }

    private void tryJumpToDate() {
        if (suppressJumpChange || jumpDayBox == null || jumpYearBox == null) {
            return;
        }
        Integer day = parseInt(jumpDayBox.getValue());
        Integer year = parseInt(jumpYearBox.getValue());
        if (day == null || year == null || jumpMonthNames.isEmpty()) {
            return;
        }
        CalendarDefinition def = RPGTimelineApi.getCalendarDefinition();
        int daysPerMonth = def.getDaysPerMonth();
        int monthCount = def.getMonthCount();
        if (monthCount <= 0) {
            return;
        }
        int clampedMonth = Math.max(0, Math.min(jumpMonthIndex, monthCount - 1));
        int clampedDay = Math.max(1, Math.min(day, daysPerMonth));
        int clampedYear = Math.max(1, year);
        long yearIndex = (long) clampedYear - 1L;

        long dayIndex = yearIndex * def.getDaysPerYear()
                + (long) clampedMonth * daysPerMonth
                + (long) clampedDay - 1L;

        updateLayout();
        railLeft = panelX + 32;
        railRight = panelX + panelW - 32;
        unitSpacing = scaleSpacing();

        updateLatestRangeForJump();
        updateScrollBounds();

        float viewUnits = Math.max(1.0f, (railRight - railLeft) / unitSpacing);
        viewStartUnit = clamp(dayIndex - viewUnits / 2.0f, 0.0f, maxViewStartUnit);
        snapToLatest = false;
    }

    private void updateLatestRangeForJump() {
        long currentDay = 0L;
        float currentTime = 0.0f;
        if (minecraft != null && minecraft.level != null) {
            long dayTime = minecraft.level.getDayTime();
            currentDay = Math.max(0L, RPGTimelineApi.getDayIndexForGameTime(dayTime));
            currentTime = Math.max(0.0f, getDayTimeUnits(dayTime, RPGTimelineApi.getCalendarDefinition()));
        }
        long latest = currentDay;
        List<ChronicleEntry> entries = getFilteredEntries();
        for (ChronicleEntry entry : entries) {
            if (entry.dayIndex() > latest) {
                latest = entry.dayIndex();
            }
        }
        maxUnitIndex = latest;
        maxUnitTime = Math.max(currentTime, (float) latest);
    }

    private void syncJumpControlsFromCurrent(CalendarDefinition def) {
        long dayIndex = 0L;
        if (minecraft != null && minecraft.level != null) {
            dayIndex = Math.max(0L, RPGTimelineApi.getDayIndexForGameTime(minecraft.level.getDayTime()));
        }
        syncJumpControlsFromDay(dayIndex, def);
    }

    private void syncJumpControlsFromDay(long dayIndex, CalendarDefinition def) {
        if (jumpDayBox == null || jumpYearBox == null) {
            return;
        }
        CalendarParts parts = getCalendarParts(dayIndex, def);
        suppressJumpChange = true;
        jumpDayBox.setValue(Integer.toString(parts.dayOfMonth));
        jumpYearBox.setValue(Long.toString(parts.year));
        jumpMonthIndex = parts.monthIndex;
        suppressJumpChange = false;
    }

    private CalendarParts getCalendarParts(long dayIndex, CalendarDefinition def) {
        long daysPerYear = def.getDaysPerYear();
        int daysPerMonth = def.getDaysPerMonth();
        if (daysPerYear <= 0 || daysPerMonth <= 0) {
            return new CalendarParts(1L, 0, 1);
        }
        long year = (dayIndex / daysPerYear) + 1L;
        long dayOfYear = dayIndex % daysPerYear;
        int monthIndex = (int) (dayOfYear / daysPerMonth);
        int dayOfMonth = (int) (dayOfYear % daysPerMonth) + 1;
        int monthCount = def.getMonthCount();
        if (monthCount > 0) {
            monthIndex = Math.max(0, Math.min(monthIndex, monthCount - 1));
        } else {
            monthIndex = 0;
        }
        dayOfMonth = Math.max(1, Math.min(dayOfMonth, Math.max(1, daysPerMonth)));
        return new CalendarParts(year, monthIndex, dayOfMonth);
    }

    private void refreshJumpMonthList(CalendarDefinition def) {
        String[] months = def.getMonthNames();
        if (months.length == 0) {
            jumpMonthNames = List.of();
            jumpMonthIndex = 0;
            return;
        }
        List<String> newNames = new ArrayList<>(months.length);
        for (String month : months) {
            newNames.add(month == null ? "" : month);
        }
        jumpMonthNames = newNames;
        if (jumpMonthIndex < 0 || jumpMonthIndex >= jumpMonthNames.size()) {
            jumpMonthIndex = Math.max(0, Math.min(jumpMonthIndex, jumpMonthNames.size() - 1));
        }
    }

    private void refreshEventMonthList(CalendarDefinition def) {
        String[] months = def.getMonthNames();
        if (months.length == 0) {
            eventMonthNames = List.of();
            eventMonthIndex = 0;
            return;
        }
        List<String> newNames = new ArrayList<>(months.length);
        for (String month : months) {
            newNames.add(month == null ? "" : month);
        }
        eventMonthNames = newNames;
        if (eventMonthIndex < 0 || eventMonthIndex >= eventMonthNames.size()) {
            eventMonthIndex = Math.max(0, Math.min(eventMonthIndex, eventMonthNames.size() - 1));
        }
    }

    private void syncEventDateFromCurrent(CalendarDefinition def) {
        long dayIndex = 0L;
        if (minecraft != null && minecraft.level != null) {
            dayIndex = Math.max(0L, RPGTimelineApi.getDayIndexForGameTime(minecraft.level.getDayTime()));
        }
        syncEventDateFromDay(dayIndex, def);
    }

    private void syncEventDateFromDay(long dayIndex, CalendarDefinition def) {
        if (eventDayBox == null || eventYearBox == null) {
            return;
        }
        CalendarParts parts = getCalendarParts(dayIndex, def);
        suppressEventDateChange = true;
        eventDayBox.setValue(Integer.toString(parts.dayOfMonth));
        eventYearBox.setValue(Long.toString(parts.year));
        eventMonthIndex = parts.monthIndex;
        suppressEventDateChange = false;
    }

    private Long getEventDayIndex() {
        if (eventDayBox == null || eventYearBox == null) {
            return null;
        }
        Integer day = parseInt(eventDayBox.getValue());
        Integer year = parseInt(eventYearBox.getValue());
        if (day == null || year == null || eventMonthNames.isEmpty()) {
            return null;
        }
        CalendarDefinition def = RPGTimelineApi.getCalendarDefinition();
        int daysPerMonth = def.getDaysPerMonth();
        int monthCount = def.getMonthCount();
        if (monthCount <= 0) {
            return null;
        }
        int clampedMonth = Math.max(0, Math.min(eventMonthIndex, monthCount - 1));
        int clampedDay = Math.max(1, Math.min(day, daysPerMonth));
        int clampedYear = Math.max(1, year);
        long yearIndex = (long) clampedYear - 1L;

        return yearIndex * def.getDaysPerYear()
                + (long) clampedMonth * daysPerMonth
                + (long) clampedDay - 1L;
    }

    private String abbrevMonth(String month, boolean withDot) {
        if (month == null || month.isBlank()) {
            return withDot ? "Mon." : "Mon";
        }
        String trimmed = month.trim();
        String abbr = trimmed.length() <= 3 ? trimmed : trimmed.substring(0, 3);
        return withDot ? abbr + "." : abbr;
    }

    private boolean isNumericOrEmpty(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private float scaleSpacing() {
        return baseSpacing() * zoomFactor;
    }

    private float baseSpacing() {
        return 40.0f;
    }

    private float getDayTimeUnits(long gameTime, CalendarDefinition def) {
        long ticksPerDay = def.getTicksPerDay();
        if (ticksPerDay <= 0L) {
            ticksPerDay = 24000L;
        }
        return (float) gameTime / (float) ticksPerDay;
    }

    private ItemStack iconFromEntry(ChronicleEntry entry) {
        if (entry.type() == ChronicleEntryType.ADMIN_NOTE) {
            return new ItemStack(Items.BOOK);
        }
        try {
            String id = entry.iconItemId();
            if (id != null && !id.isBlank()) {
                ResourceLocation rl = ResourceLocation.parse(id);
                return new ItemStack(BuiltInRegistries.ITEM.get(rl));
            }
        } catch (Throwable ignored) {
        }
        return new ItemStack(Items.PAPER);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        updateLayout();
        if (showAddPanel && showEventMonthDropdown) {
            DropdownLayout layout = buildEventMonthDropdownLayout();
            if (inRect(mouseX, mouseY, layout.x, layout.y, layout.w, layout.h)) {
                int maxStart = Math.max(0, eventMonthNames.size() - layout.visibleCount);
                int step = deltaY > 0 ? -1 : 1;
                eventMonthScroll = Math.max(0, Math.min(eventMonthScroll + step, maxStart));
                return true;
            }
        }
        if (showAddPanel && detailsBox != null && detailsBox.mouseScrolled(mouseX, mouseY, deltaX, deltaY)) {
            return true;
        }
        if ((selectedSection != null || selectedHallUuid != null) && !showAddPanel) {
            int scrollStep = Math.max(12, font.lineHeight * 2);
            float nextOffset = (float) (detailScrollOffset - deltaY * scrollStep);
            detailScrollOffset = (int) clamp(nextOffset, 0, detailScrollMax);
            return true;
        }
        if (tab == ChronicleTab.HALL_OF_FAME && !showAddPanel) {
            int scrollStep = Math.max(12, font.lineHeight * 2);
            float nextOffset = (float) (hallScrollOffset - deltaY * scrollStep);
            hallScrollOffset = (int) clamp(nextOffset, 0, hallScrollMax);
            return true;
        }
        if (Screen.hasControlDown()) {
            float oldZoom = zoomFactor;
            float zoomScale = (float) Math.pow(ZOOM_STEP, deltaY);
            zoomFactor = clamp(zoomFactor * zoomScale, MIN_ZOOM, MAX_ZOOM);
            if (Math.abs(zoomFactor - oldZoom) > 0.0001f) {
                unitSpacing = scaleSpacing();
                updateScrollBounds();
            }
            snapToLatest = false;
            return true;
        }
        float unitStep = 30.0f / Math.max(1.0f, unitSpacing);
        viewStartUnit -= deltaY * unitStep;
        viewStartUnit = clamp(viewStartUnit, 0.0f, maxViewStartUnit);
        snapToLatest = false;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateLayout();
        if (button == 0) {
            if ((selectedSection != null || selectedHallUuid != null)
                    && inRect(mouseX, mouseY, closeButtonX, closeButtonY, closeButtonW, closeButtonH)) {
                closeDetailPanel();
                return true;
            }
            if (showAddPanel) {
                if (handleEventMonthClick(mouseX, mouseY)) {
                    return true;
                }
                if (inRect(mouseX, mouseY, submitButtonX, submitButtonY, submitButtonW, submitButtonH)) {
                    submitAdminEvent();
                    return true;
                }
                if (inRect(mouseX, mouseY, cancelButtonX, cancelButtonY, cancelButtonW, cancelButtonH)) {
                    toggleAddPanel();
                    return true;
                }
            }
            if (showAddButton && inRect(mouseX, mouseY, addButtonX, addButtonY, addButtonW, addButtonH)) {
                toggleAddPanel();
                return true;
            }
            if (showInfoButton && inRect(mouseX, mouseY, infoButtonX, infoButtonY, infoButtonW, infoButtonH)) {
                return true;
            }
            if ((selectedSection != null || selectedHallUuid != null)
                    && inRect(mouseX, mouseY, detailScrollBarX, detailScrollHandleY, detailScrollBarW, detailScrollHandleH)) {
                draggingDetailScroll = true;
                detailDragOffset = (int) mouseY - detailScrollHandleY;
                return true;
            }
            if (inRect(mouseX, mouseY, scrollHandleX, scrollHandleY, scrollHandleW, scrollHandleH)) {
                draggingScroll = true;
                dragStartMouseX = (int) mouseX;
                dragHandleGrabOffset = (int) mouseX - scrollHandleX;
                snapToLatest = false;
                return true;
            }
            if (handleJumpMonthClick(mouseX, mouseY)) {
                return true;
            }
            if (handleTabClick(mouseX, mouseY)) {
                return true;
            }

            if (tab == ChronicleTab.HALL_OF_FAME) {
                for (HallRowBounds bounds : hallRowBounds) {
                    if (bounds.contains(mouseX, mouseY)) {
                        selectedHallUuid = bounds.playerUuid;
                        detailScrollOffset = 0;
                        showAddPanel = false;
                        PacketDistributor.sendToServer(new ChroniclePayloads.RequestHallOfFameDetailPayload(bounds.playerUuid));
                        updateAddPanelVisibility();
                        return true;
                    }
                }
            }

            for (NodeBounds bounds : nodeBounds) {
                if (bounds.contains(mouseX, mouseY)) {
                    selectedSection = bounds.bucket;
                    detailScrollOffset = 0;
                    showAddPanel = false;
                    updateAddPanelVisibility();
                    return true;
                }
            }
            if ((selectedSection != null || selectedHallUuid != null) && !showAddPanel
                    && !inRect(mouseX, mouseY, detailPanelX, detailPanelY, detailPanelW, detailPanelH)) {
                closeDetailPanel();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingDetailScroll && button == 0) {
            int track = Math.max(1, detailScrollBarH - detailScrollHandleH);
            if (detailScrollMax <= 0 || track <= 0) {
                return true;
            }
            float handlePos = (float) mouseY - detailScrollBarY - detailDragOffset;
            handlePos = clamp(handlePos, 0.0f, track);
            float progress = handlePos / track;
            detailScrollOffset = (int) clamp(progress * detailScrollMax, 0, detailScrollMax);
            return true;
        }
        if (draggingScroll && button == 0) {
            updateLayout();
            float trackSpan = Math.max(1.0f, scrollbarW - scrollHandleW);
            if (maxViewStartUnit <= 0.0f || trackSpan <= 0.0f) {
                return true;
            }
            float handlePos = (float) mouseX - scrollbarX - dragHandleGrabOffset;
            handlePos = clamp(handlePos, 0.0f, trackSpan);
            float progress = handlePos / trackSpan;
            viewStartUnit = clamp(progress * maxViewStartUnit, 0.0f, maxViewStartUnit);
            snapToLatest = false;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingScroll = false;
            draggingDetailScroll = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean handleTabClick(double mouseX, double mouseY) {
        int x = panelX + PANEL_PADDING;
        int y = panelY - TAB_HEIGHT + 2;
        int w = TAB_WIDTH;
        int h = TAB_HEIGHT;
        if (inRect(mouseX, mouseY, x, y, w, h)) {
            tab = ChronicleTab.SERVER;
            selectedSection = null;
            selectedHallUuid = null;
            snapToLatest = true;
            updateAddEventButtonVisibility();
            updateJumpControlsVisibility();
            return true;
        }
        if (inRect(mouseX, mouseY, x + w + 8, y, w, h)) {
            tab = ChronicleTab.PERSONAL;
            selectedSection = null;
            selectedHallUuid = null;
            snapToLatest = true;
            updateAddEventButtonVisibility();
            updateJumpControlsVisibility();
            return true;
        }
        if (inRect(mouseX, mouseY, x + (w + 8) * 2, y, w, h)) {
            tab = ChronicleTab.HALL_OF_FAME;
            selectedSection = null;
            selectedHallUuid = null;
            hallScrollOffset = 0;
            PacketDistributor.sendToServer(new ChroniclePayloads.RequestHallOfFamePayload());
            updateAddEventButtonVisibility();
            updateJumpControlsVisibility();
            return true;
        }
        return false;
    }

    private boolean handleJumpMonthClick(double mouseX, double mouseY) {
        if (shouldHideJumpControls()) {
            return false;
        }
        if (jumpMonthNames.isEmpty()) {
            return false;
        }
        if (inRect(mouseX, mouseY, jumpMonthX, jumpMonthY, jumpMonthW, jumpMonthH)) {
            showMonthDropdown = !showMonthDropdown;
            return true;
        }
        if (showMonthDropdown) {
            int rowH = 18;
            int listW = jumpMonthW;
            int listH = jumpMonthNames.size() * rowH;
            int listX = jumpMonthX;
            int listY = jumpMonthY + jumpMonthH + 2;
            if (inRect(mouseX, mouseY, listX, listY, listW, listH)) {
                int idx = (int) ((mouseY - listY) / rowH);
                if (idx >= 0 && idx < jumpMonthNames.size()) {
                    jumpMonthIndex = idx;
                    showMonthDropdown = false;
                    tryJumpToDate();
                    return true;
                }
            } else {
                showMonthDropdown = false;
            }
        }
        return false;
    }

    private boolean handleEventMonthClick(double mouseX, double mouseY) {
        if (eventMonthNames.isEmpty()) {
            return false;
        }
        if (inRect(mouseX, mouseY, eventMonthX, eventMonthY, eventMonthW, eventMonthH)) {
            showEventMonthDropdown = !showEventMonthDropdown;
            if (showEventMonthDropdown) {
                eventMonthScroll = 0;
            }
            updateAddPanelVisibility();
            return true;
        }
        if (showEventMonthDropdown) {
            DropdownLayout layout = buildEventMonthDropdownLayout();
            if (inRect(mouseX, mouseY, layout.x, layout.y, layout.w, layout.h)) {
                int row = (int) ((mouseY - layout.y) / layout.rowH);
                int idx = layout.startIndex + row;
                if (idx >= 0 && idx < eventMonthNames.size()) {
                    eventMonthIndex = idx;
                    showEventMonthDropdown = false;
                    updateAddPanelVisibility();
                    return true;
                }
            }
            showEventMonthDropdown = false;
            updateAddPanelVisibility();
        }
        return false;
    }


    private boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private enum ChronicleTab {
        SERVER,
        PERSONAL,
        HALL_OF_FAME
    }

    private enum ChronicleScale {
        YEAR,
        MONTH,
        DAY
    }

    private enum NodeCategory {
        NOTES,
        WORLD_FIRST,
        OTHER
    }

    private static final class SectionBucket {
        private final int index;
        private double anchorTime;
        private long anchorDay;
        private long totalDays;
        private int totalCount;
        private final List<ChronicleEntry> notes = new ArrayList<>();
        private final List<ChronicleEntry> worldFirsts = new ArrayList<>();
        private final List<ChronicleEntry> others = new ArrayList<>();

        private SectionBucket(int index) {
            this.index = index;
        }

        private void add(ChronicleEntry entry, long maxUnitIndex) {
            if (entry == null) {
                return;
            }
            if (entry.type() == ChronicleEntryType.ADMIN_NOTE) {
                notes.add(entry);
            } else if (entry.type() == ChronicleEntryType.WORLD_FIRST) {
                worldFirsts.add(entry);
            } else {
                others.add(entry);
            }
            updateAnchor(entry.dayIndex(), maxUnitIndex);
        }

        private void updateAnchor(long dayIndex, long maxUnitIndex) {
            totalDays += dayIndex;
            totalCount++;
            if (totalCount <= 0) {
                return;
            }
            anchorTime = totalDays / (double) totalCount;
            anchorDay = clampDay(Math.round(anchorTime), maxUnitIndex);
        }

        private static long clampDay(long day, long maxUnitIndex) {
            if (day < 0L) {
                return 0L;
            }
            if (day > maxUnitIndex) {
                return maxUnitIndex;
            }
            return day;
        }
    }

    private static final class CalendarParts {
        private final long year;
        private final int monthIndex;
        private final int dayOfMonth;

        private CalendarParts(long year, int monthIndex, int dayOfMonth) {
            this.year = year;
            this.monthIndex = monthIndex;
            this.dayOfMonth = dayOfMonth;
        }
    }

    private static final class NodeBounds {
        private final ChronicleEntry entry;
        private final SectionBucket bucket;
        private final int x;
        private final int y;
        private final int w;
        private final int h;

        private NodeBounds(ChronicleEntry entry, SectionBucket bucket, int x, int y, int w, int h) {
            this.entry = entry;
            this.bucket = bucket;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        private boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private static final class HallRowBounds {
        private final String playerUuid;
        private final int x;
        private final int y;
        private final int w;
        private final int h;

        private HallRowBounds(String playerUuid, int x, int y, int w, int h) {
            this.playerUuid = playerUuid;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        private boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private static final class DropdownLayout {
        private final int x;
        private final int y;
        private final int w;
        private final int h;
        private final int rowH;
        private final int visibleCount;
        private final int startIndex;

        private DropdownLayout(int x, int y, int w, int h, int rowH, int visibleCount, int startIndex) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.rowH = rowH;
            this.visibleCount = visibleCount;
            this.startIndex = startIndex;
        }
    }

}
