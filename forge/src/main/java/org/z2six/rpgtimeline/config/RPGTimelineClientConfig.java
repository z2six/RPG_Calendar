package org.z2six.rpgtimeline.config;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

public final class RPGTimelineClientConfig {

    private static final Logger LOG = LogUtils.getLogger();
    private static final int DEFAULT_DAY_TOAST_OFFSET_X = 0;
    private static final int DEFAULT_DAY_TOAST_OFFSET_Y = 0;

    public enum DayToastFont {
        VANILLA,
        GOTHIC12,
        GOTHIC24
    }

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.EnumValue<DayToastFont> DAY_TOAST_FONT;
    public static final ForgeConfigSpec.IntValue DAY_TOAST_OFFSET_X;
    public static final ForgeConfigSpec.IntValue DAY_TOAST_OFFSET_Y;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("calendar");

        DAY_TOAST_FONT = builder
                .comment(
                        "Font used for the day-change toast.",
                        "VANILLA uses Minecraft's default font.",
                        "GOTHIC12 and GOTHIC24 use RPG Calendar's Gothic fonts."
                )
                .defineEnum("dayToastFont", DayToastFont.GOTHIC12);

        DAY_TOAST_OFFSET_X = builder
                .comment(
                        "Horizontal offset in pixels for the day-change toast.",
                        "Positive moves right, negative moves left."
                )
                .defineInRange("dayToastOffsetX", DEFAULT_DAY_TOAST_OFFSET_X, -4096, 4096);

        DAY_TOAST_OFFSET_Y = builder
                .comment(
                        "Vertical offset in pixels for the day-change toast.",
                        "Positive moves down, negative moves up."
                )
                .defineInRange("dayToastOffsetY", DEFAULT_DAY_TOAST_OFFSET_Y, -4096, 4096);

        builder.pop();

        CLIENT_SPEC = builder.build();
        LOG.debug("[RPGTimelineClientConfig] Built CLIENT config spec (calendar)");
    }

    public static void register() {
        try {
            ModLoadingContext.get()
                    .registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);

            LOG.debug("[RPGTimelineClientConfig] Registered CLIENT config with active ModContainer");
        } catch (Throwable t) {
            LOG.error("[RPGTimelineClientConfig] Failed to register CLIENT config", t);
        }
    }

    public static DayToastFont getDayToastFont() {
        try {
            return DAY_TOAST_FONT.get();
        } catch (Throwable t) {
            LOG.error("[RPGTimelineClientConfig] getDayToastFont failed; using VANILLA", t);
            return DayToastFont.VANILLA;
        }
    }

    public static int getDayToastOffsetX() {
        try {
            return DAY_TOAST_OFFSET_X.get();
        } catch (Throwable t) {
            LOG.error("[RPGTimelineClientConfig] getDayToastOffsetX failed; using {}", DEFAULT_DAY_TOAST_OFFSET_X, t);
            return DEFAULT_DAY_TOAST_OFFSET_X;
        }
    }

    public static int getDayToastOffsetY() {
        try {
            return DAY_TOAST_OFFSET_Y.get();
        } catch (Throwable t) {
            LOG.error("[RPGTimelineClientConfig] getDayToastOffsetY failed; using {}", DEFAULT_DAY_TOAST_OFFSET_Y, t);
            return DEFAULT_DAY_TOAST_OFFSET_Y;
        }
    }

    private RPGTimelineClientConfig() {
        // no-op
    }
}
