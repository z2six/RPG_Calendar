package org.z2six.rpgtimeline.chronicle.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.config.RPGTimelineConfig;

/**
 * Admin command for running retroactive chronicle imports manually.
 */
public final class ChronicleRetroImportCommands {

    private static final Logger LOG = LogUtils.getLogger();
    private static volatile boolean REGISTERED = false;

    private ChronicleRetroImportCommands() {
        // no-op
    }

    public static void registerGameBus() {
        if (REGISTERED) {
            LOG.debug("[ChronicleRetroImportCommands] already registered; skipping");
            return;
        }
        try {
            MinecraftForge.EVENT_BUS.addListener(ChronicleRetroImportCommands::onRegisterCommands);
            REGISTERED = true;
            LOG.debug("[ChronicleRetroImportCommands] Registered on Forge EVENT_BUS");
        } catch (Throwable t) {
            LOG.error("[ChronicleRetroImportCommands] registerGameBus failed safely", t);
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        try {
            CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
            dispatcher.register(
                    Commands.literal("rpgtimeline")
                            .requires(source -> source.hasPermission(2))
                            .then(Commands.literal("retroimport")
                                    .then(Commands.literal("advancements")
                                            .executes(ctx -> executeRetroImportAdvancements(ctx.getSource()))))
            );
        } catch (Throwable t) {
            LOG.error("[ChronicleRetroImportCommands] onRegisterCommands failed safely", t);
        }
    }

    private static int executeRetroImportAdvancements(CommandSourceStack source) {
        if (!RPGTimelineConfig.isRetroactiveAdvancementImportEnabled()) {
            source.sendFailure(Component.literal(
                    "Retro import is disabled in config (chronicle.retroactiveAdvancementImportEnabled=false)"
            ));
            return 0;
        }

        MinecraftServer server = source.getServer();
        ChronicleRetroactiveAdvancementImporter.RunSummary summary =
                ChronicleRetroactiveAdvancementImporter.runManualImport(server);
        int changed = summary.imported() + summary.updated();

        source.sendSuccess(
                () -> Component.literal(
                        "Retro import complete (files="
                                + summary.filesScanned()
                                + ", imported="
                                + summary.imported()
                                + ", updated="
                                + summary.updated()
                                + ", skipped="
                                + summary.skipped()
                                + ")"
                ),
                true
        );
        return changed;
    }
}
