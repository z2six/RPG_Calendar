package org.z2six.rpgtimeline.chronicle.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.api.RPGTimelineApi;
import org.z2six.rpgtimeline.chronicle.ChronicleEntryType;
import org.z2six.rpgtimeline.chronicle.ChronicleScope;
import org.z2six.rpgtimeline.network.ChroniclePayloads;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ChronicleDebugCommands {

    private static final Logger LOG = LogUtils.getLogger();
    private static volatile boolean REGISTERED = false;
    private static final boolean ENABLED = false;

    private ChronicleDebugCommands() {
        // no-op
    }

    public static void registerGameBus() {
        if (!ENABLED) {
            return;
        }
        if (REGISTERED) {
            return;
        }
        try {
            NeoForge.EVENT_BUS.addListener(ChronicleDebugCommands::onRegisterCommands);
            REGISTERED = true;
        } catch (Throwable t) {
            LOG.error("[ChronicleDebugCommands] registerGameBus failed safely", t);
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        try {
            CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
            dispatcher.register(
                    Commands.literal("rpgtimeline_debug")
                            .requires(source -> source.hasPermission(2))
                            .then(Commands.literal("fake")
                                    .then(Commands.argument("players", IntegerArgumentType.integer(1, 50))
                                            .then(Commands.argument("events", IntegerArgumentType.integer(1, 200))
                                                    .executes(ctx -> executeFake(ctx.getSource(),
                                                            IntegerArgumentType.getInteger(ctx, "players"),
                                                            IntegerArgumentType.getInteger(ctx, "events"),
                                                            60))
                                                    .then(Commands.argument("days", IntegerArgumentType.integer(1, 1000))
                                                            .executes(ctx -> executeFake(ctx.getSource(),
                                                                    IntegerArgumentType.getInteger(ctx, "players"),
                                                                    IntegerArgumentType.getInteger(ctx, "events"),
                                                                    IntegerArgumentType.getInteger(ctx, "days")
                                                            ))))))
            );
        } catch (Throwable t) {
            LOG.error("[ChronicleDebugCommands] onRegisterCommands failed safely", t);
        }
    }

    private static int executeFake(CommandSourceStack source, int playerCount, int eventsPerPlayer, int daysRange) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendFailure(Component.translatable("command.rpgtimeline.debug.no_server"));
            return 0;
        }

        List<AdvancementHolder> advancements = collectDisplayAdvancements(server);
        if (advancements.isEmpty()) {
            source.sendFailure(Component.translatable("command.rpgtimeline.debug.no_advancements"));
            return 0;
        }

        long currentDay = RPGTimelineApi.getDayIndexForGameTime(server.overworld().getDayTime());
        RandomSource random = RandomSource.create();
        ChronicleSavedData data = ChronicleSavedData.get(server);

        int totalEvents = 0;
        for (int i = 1; i <= playerCount; i++) {
            String name = "ChronicleBot" + i;
            UUID uuid = UUID.nameUUIDFromBytes(("rpgtimeline_fake_" + i).getBytes(StandardCharsets.UTF_8));
            for (int j = 0; j < eventsPerPlayer; j++) {
                AdvancementHolder holder = advancements.get(random.nextInt(advancements.size()));
                Advancement adv = holder.value();
                DisplayInfo display = adv.display().orElse(null);
                if (display == null || display.isHidden()) {
                    continue;
                }
                long dayIndex = Math.max(0L, currentDay - random.nextInt(Math.max(1, daysRange)));
                String title = display.getTitle().getString();
                String details = display.getDescription().getString();
                String iconItemId = getItemId(display.getIcon());

                ChronicleEvent event = new ChronicleEvent(
                        UUID.randomUUID().toString(),
                        ChronicleEntryType.ADVANCEMENT,
                        ChronicleScope.PERSONAL,
                        dayIndex,
                        title,
                        details,
                        name,
                        uuid.toString(),
                        holder.id().toString(),
                        iconItemId
                );
                data.addEvent(event);
                totalEvents++;
            }
        }

        ChroniclePayloads.broadcastFullSync(server);
        ChroniclePayloads.broadcastHallOfFame(server);
        int injected = totalEvents;
        source.sendSuccess(() -> Component.translatable("command.rpgtimeline.debug.injected", injected, playerCount), true);
        return injected;
    }

    private static List<AdvancementHolder> collectDisplayAdvancements(MinecraftServer server) {
        List<AdvancementHolder> results = new ArrayList<>();
        for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            Advancement adv = holder.value();
            DisplayInfo display = adv.display().orElse(null);
            if (display == null || display.isHidden()) {
                continue;
            }
            results.add(holder);
        }
        return results;
    }

    private static String getItemId(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }
}
