package org.z2six.rpgtimeline.config;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

public final class RPGTimelineClientConfig {

    private static final Logger LOG = LogUtils.getLogger();

    public enum DayToastFont {
        VANILLA,
        GOTHIC12,
        GOTHIC24
    }

    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.EnumValue<DayToastFont> DAY_TOAST_FONT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("calendar");

        DAY_TOAST_FONT = builder
                .comment(
                        "Font used for the day-change toast.",
                        "VANILLA uses Minecraft's default font.",
                        "GOTHIC12 and GOTHIC24 use RPG Calendar's Gothic fonts."
                )
                .defineEnum("dayToastFont", DayToastFont.GOTHIC12);

        builder.pop();

        CLIENT_SPEC = builder.build();
        LOG.debug("[RPGTimelineClientConfig] Built CLIENT config spec (calendar)");
    }

    public static void register() {
        try {
            ModLoadingContext.get()
                    .getActiveContainer()
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

    private RPGTimelineClientConfig() {
        // no-op
    }
}
