package org.z2six.rpgtimeline.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.Constants;
import org.z2six.rpgtimeline.api.RPGTimelineApi;
import org.z2six.rpgtimeline.config.RPGTimelineClientConfig;

/**
 * Client-side handler that:
 * - Detects when a new Minecraft day starts (based on world time).
 * - Computes the in-world calendar date.
 * - Shows a centered popup at the top of the screen with a fade-in/hold/fade-out.
 *
 * Uses the custom Gothic font (assets/rpgtimeline/font/gothic12.json).
 *
 * This class is wired via @EventBusSubscriber on the GAME bus, client side only.
 */
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientCalendarEvents {
    private static final Logger LOG = LogUtils.getLogger();

    // Popup timing (ticks at 20 TPS)
    private static final int FADE_IN_TICKS = 20;   // 1.0s fade in
    private static final int HOLD_TICKS = 60;      // 3.0s hold
    private static final int FADE_OUT_TICKS = 20;  // 1.0s fade out
    private static final int TOTAL_TICKS = FADE_IN_TICKS + HOLD_TICKS + FADE_OUT_TICKS;

    // Keep track of last day index we saw, to detect day changes
    private static long lastSeenDayIndex = -1L;

    // Current popup state
    private static Component currentMessage = null;
    private static int popupAgeTicks = 0;
    private static boolean popupActive = false;

    // Custom font id (assets/rpgtimeline/font/gothic12.json)
    private static final ResourceLocation GOTHIC_FONT_ID =
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "gothic12");
    private static final ResourceLocation GOTHIC24_FONT_ID =
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "gothic24");

    private static final ResourceLocation TOP_ORNAMENT =
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/top_ornament_dropshadow.png");
    private static final ResourceLocation BOTTOM_ORNAMENT =
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/bottom_ornament_dropshadow.png");
    private static final int TOP_ORNAMENT_W = 48;
    private static final int TOP_ORNAMENT_H = 9;
    private static final int BOTTOM_ORNAMENT_W = 62;
    private static final int BOTTOM_ORNAMENT_H = 14;

    private ClientCalendarEvents() {
        // no-op
    }

    // -------------------------------------------------------------------------
    // Event hooks (registered automatically via @EventBusSubscriber)
    // -------------------------------------------------------------------------

    /**
     * Called every client tick (POST).
     */
    @SubscribeEvent
    public static void onClientTick(@NotNull ClientTickEvent.Post event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null || mc.player == null) {
                return;
            }

            long dayTime = mc.level.getDayTime(); // absolute time in ticks
            long dayIndex = RPGTimelineApi.getDayIndexForGameTime(dayTime);

            if (dayIndex != lastSeenDayIndex) {
                long old = lastSeenDayIndex;
                lastSeenDayIndex = dayIndex;

                LOG.debug("[ClientCalendarEvents] Detected new day: oldDayIndex={} newDayIndex={}", old, dayIndex);

                Component msg = RPGTimelineApi.buildDateMessage(dayIndex);
                startPopup(msg);
            }

            // Advance popup animation if active
            if (popupActive) {
                popupAgeTicks++;
                if (popupAgeTicks >= TOTAL_TICKS) {
                    popupActive = false;
                    currentMessage = null;
                    popupAgeTicks = 0;
                    LOG.debug("[ClientCalendarEvents] Popup finished");
                }
            }

        } catch (Throwable t) {
            LOG.error("[ClientCalendarEvents] onClientTick failed", t);
        }
    }

    /**
     * Called each frame after GUI is rendered; we draw our popup on top.
     */
    @SubscribeEvent
    public static void onRenderGui(@NotNull RenderGuiEvent.Post event) {
        try {
            if (!popupActive || currentMessage == null) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null) {
                return;
            }

            if (mc.options.hideGui) {
                // Respect F1 "hide GUI"
                return;
            }

            int alpha = computeCurrentAlpha();
            if (alpha <= 0) {
                return;
            }

            GuiGraphics g = event.getGuiGraphics();
            Font font = mc.font;

            MutableComponent styled = currentMessage.copy();
            applyToastFont(styled);

            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int textWidth = font.width(styled);
            int textHeight = font.lineHeight;

            int x = (screenWidth - textWidth) / 2;
            int y = 24; // near top, but below boss bar / title

            // Color: white with computed alpha
            int argb = (alpha << 24) | 0x00FFFFFF;

            // Simple drop shadow
            int shadowArgb = (alpha << 24) | 0x00101010;
            g.drawString(font, styled, x + 1, y + 1, shadowArgb, false);
            g.drawString(font, styled, x, y, argb, false);

            drawOrnaments(g, x, y, textWidth, textHeight, alpha);

        } catch (Throwable t) {
            LOG.error("[ClientCalendarEvents] onRenderGui failed", t);
        }
    }

    // -------------------------------------------------------------------------
    // Popup helpers
    // -------------------------------------------------------------------------

    private static void startPopup(@NotNull Component message) {
        try {
            currentMessage = message;
            popupAgeTicks = 0;
            popupActive = true;
            LOG.debug("[ClientCalendarEvents] Starting popup with message='{}'", message.getString());
        } catch (Throwable t) {
            LOG.error("[ClientCalendarEvents] startPopup failed", t);
        }
    }

    /**
     * Compute current alpha (0-255) based on popupAgeTicks.
     */
    private static int computeCurrentAlpha() {
        try {
            if (!popupActive) {
                return 0;
            }

            int t = popupAgeTicks;
            if (t < 0 || t >= TOTAL_TICKS) {
                return 0;
            }

            if (t < FADE_IN_TICKS) {
                // Fade in 0 -> 255
                float f = (float) t / (float) FADE_IN_TICKS;
                int alpha = (int) (f * 255.0f);
                return Math.max(0, Math.min(255, alpha));
            }

            if (t < FADE_IN_TICKS + HOLD_TICKS) {
                // Hold at full opacity
                return 255;
            }

            // Fade out
            int outT = t - FADE_IN_TICKS - HOLD_TICKS;
            float f = 1.0f - ((float) outT / (float) FADE_OUT_TICKS);
            int alpha = (int) (f * 255.0f);
            return Math.max(0, Math.min(255, alpha));

        } catch (Throwable t) {
            LOG.error("[ClientCalendarEvents] computeCurrentAlpha failed", t);
            // Fail-safe: no popup instead of broken visuals
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Custom ornament drawing
    // -------------------------------------------------------------------------

    /**
     * Draws the top/bottom PNG ornaments around the day toast.
     */
    private static void applyToastFont(@NotNull MutableComponent styled) {
        RPGTimelineClientConfig.DayToastFont choice = RPGTimelineClientConfig.getDayToastFont();
        if (choice == RPGTimelineClientConfig.DayToastFont.GOTHIC12) {
            styled.setStyle(Style.EMPTY.withFont(GOTHIC_FONT_ID));
        } else if (choice == RPGTimelineClientConfig.DayToastFont.GOTHIC24) {
            styled.setStyle(Style.EMPTY.withFont(GOTHIC24_FONT_ID));
        }
    }

    private static void drawOrnaments(@NotNull GuiGraphics g, int textX, int textY, int textWidth, int textHeight, int alpha) {
        try {
            float scale = 0.5f;
            float invScale = 1.0f / scale;
            int centerX = textX + textWidth / 2;

            g.pose().pushPose();
            g.pose().scale(scale, scale, 1.0f);
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha / 255.0f);

            int scaledCenterX = Math.round(centerX * invScale);
            int scaledTextY = Math.round(textY * invScale);
            int scaledTextHeight = Math.round(textHeight * invScale);

            int topX = scaledCenterX - TOP_ORNAMENT_W / 2;
            int topY = scaledTextY - TOP_ORNAMENT_H - 6;
            g.blit(TOP_ORNAMENT, topX, topY, 0, 0, TOP_ORNAMENT_W, TOP_ORNAMENT_H, TOP_ORNAMENT_W, TOP_ORNAMENT_H);

            int bottomX = scaledCenterX - BOTTOM_ORNAMENT_W / 2;
            int bottomY = scaledTextY + scaledTextHeight + 6;
            g.blit(BOTTOM_ORNAMENT, bottomX, bottomY, 0, 0, BOTTOM_ORNAMENT_W, BOTTOM_ORNAMENT_H, BOTTOM_ORNAMENT_W, BOTTOM_ORNAMENT_H);

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
            g.pose().popPose();
        } catch (Throwable t) {
            LOG.error("[ClientCalendarEvents] drawOrnaments failed", t);
        }
    }
}
