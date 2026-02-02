package org.z2six.rpgtimeline.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.Constants;
import org.z2six.rpgtimeline.client.gui.ChronicleScreen;
import org.z2six.rpgtimeline.network.ChroniclePayloads;

/**
 * Client-only keybinding for opening the Chronicle UI.
 */
public final class ChronicleKeyBindings {

    private static final Logger LOG = LogUtils.getLogger();
    private static KeyMapping OPEN_CHRONICLE_KEY;

    private ChronicleKeyBindings() {
        // no-op
    }

    public static void register(@NotNull IEventBus modEventBus) {
        try {
            modEventBus.addListener(ChronicleKeyBindings::onRegisterKeyMappings);
            NeoForge.EVENT_BUS.addListener(ChronicleKeyBindings::onClientTick);
            LOG.debug("[ChronicleKeyBindings] Registered key mapping + client tick listeners.");
        } catch (Throwable t) {
            LOG.error("[ChronicleKeyBindings] register() failed safely", t);
        }
    }

    private static void onRegisterKeyMappings(@NotNull RegisterKeyMappingsEvent event) {
        try {
            final String category = "key.categories." + Constants.MOD_ID;

            OPEN_CHRONICLE_KEY = new KeyMapping(
                    "key." + Constants.MOD_ID + ".open_chronicle",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_O,
                    category
            );

            event.register(OPEN_CHRONICLE_KEY);
            LOG.debug("[ChronicleKeyBindings] Registered key mapping: open_chronicle");
        } catch (Throwable t) {
            LOG.error("[ChronicleKeyBindings] onRegisterKeyMappings failed safely", t);
        }
    }

    private static void onClientTick(@NotNull ClientTickEvent.Post event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;

            LocalPlayer player = mc.player;
            if (player == null) return;

            if (OPEN_CHRONICLE_KEY != null) {
                while (OPEN_CHRONICLE_KEY.consumeClick()) {
                    try {
                        if (!ChroniclePayloads.ClientState.hasSynced()) {
                            PacketDistributor.sendToServer(new ChroniclePayloads.RequestChroniclePayload());
                        }
                        mc.setScreen(new ChronicleScreen());
                    } catch (Throwable t) {
                        LOG.error("[ChronicleKeyBindings] Failed to open Chronicle screen", t);
                    }
                }
            }
        } catch (Throwable t) {
            LOG.error("[ChronicleKeyBindings] onClientTick failed safely", t);
        }
    }
}
