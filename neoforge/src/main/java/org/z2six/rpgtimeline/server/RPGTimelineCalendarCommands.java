package org.z2six.rpgtimeline.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.api.RPGTimelineApi;
import org.z2six.rpgtimeline.calendar.CalendarDefinition;
import org.z2six.rpgtimeline.compat.SereneSeasonsCompat;
import org.z2six.rpgtimeline.network.RPGTimelinePayloads;

/**
 * Commands for adjusting Timeline calendar interpretation without changing world time.
 *
 * <p>Primary use-case: resync Timeline's displayed date with Serene Seasons after changing
 * Serene's season length mid-save.</p>
 */
public final class RPGTimelineCalendarCommands {

    private static final Logger LOG = LogUtils.getLogger();
    private static volatile boolean REGISTERED = false;

    private RPGTimelineCalendarCommands() {
        // no-op
    }

    public static void registerGameBus() {
        if (REGISTERED) {
            LOG.debug("[RPGTimelineCalendarCommands] already registered; skipping");
            return;
        }
        try {
            NeoForge.EVENT_BUS.addListener(RPGTimelineCalendarCommands::onRegisterCommands);
            REGISTERED = true;
            LOG.debug("[RPGTimelineCalendarCommands] Registered on NeoForge EVENT_BUS");
        } catch (Throwable t) {
            LOG.error("[RPGTimelineCalendarCommands] registerGameBus failed safely", t);
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        try {
            CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
            dispatcher.register(
                    Commands.literal("rpgtimeline")
                            .requires(source -> source.hasPermission(2))
                            .then(Commands.literal("date")
                                    .then(Commands.literal("offset")
                                            .then(Commands.literal("get")
                                                    .executes(ctx -> executeOffsetGet(ctx.getSource())))
                                            .then(Commands.literal("set")
                                                    .then(Commands.argument("days", LongArgumentType.longArg())
                                                            .executes(ctx -> executeOffsetSet(
                                                                    ctx.getSource(),
                                                                    LongArgumentType.getLong(ctx, "days")
                                                            ))))
                                            .then(Commands.literal("add")
                                                    .then(Commands.argument("days", LongArgumentType.longArg())
                                                            .executes(ctx -> executeOffsetAdd(
                                                                    ctx.getSource(),
                                                                    LongArgumentType.getLong(ctx, "days")
                                                            )))))
                                    .then(Commands.literal("set")
                                            .then(Commands.argument("year", IntegerArgumentType.integer(1))
                                                    .then(Commands.argument("month", IntegerArgumentType.integer(1))
                                                            .then(Commands.argument("day", IntegerArgumentType.integer(1))
                                                                    .executes(ctx -> executeSetDateToday(
                                                                            ctx.getSource(),
                                                                            IntegerArgumentType.getInteger(ctx, "year"),
                                                                            IntegerArgumentType.getInteger(ctx, "month"),
                                                                            IntegerArgumentType.getInteger(ctx, "day")
                                                                    )))))))
                            .then(Commands.literal("ssresync")
                                    .executes(ctx -> executeSereneSeasonsResync(ctx.getSource())))
            );
        } catch (Throwable t) {
            LOG.error("[RPGTimelineCalendarCommands] onRegisterCommands failed safely", t);
        }
    }

    private static int executeOffsetGet(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        long offset = RPGTimelineCalendarSavedData.get(server).getDayOffsetDays();

        ServerLevel overworld = server.overworld();
        long worldDay = overworld == null ? 0L : RPGTimelineApi.getDayIndexForGameTime(overworld.getDayTime());
        String date = RPGTimelineApi.buildDateString(worldDay);

        source.sendSuccess(() -> Component.literal(
                "Timeline calendar offsetDays=" + offset
                        + " | worldDay=" + worldDay
                        + " | dateNow=\"" + date + "\""
        ), false);
        return 1;
    }

    private static int executeOffsetSet(CommandSourceStack source, long newOffsetDays) {
        MinecraftServer server = source.getServer();
        RPGTimelineCalendarSavedData data = RPGTimelineCalendarSavedData.get(server);
        long old = data.setDayOffsetDays(newOffsetDays);

        broadcastSettings(server);

        source.sendSuccess(() -> Component.literal(
                "Set Timeline calendar offsetDays " + old + " -> " + newOffsetDays
                        + " (delta=" + (newOffsetDays - old) + ")"
        ), true);
        return 1;
    }

    private static int executeOffsetAdd(CommandSourceStack source, long deltaDays) {
        MinecraftServer server = source.getServer();
        RPGTimelineCalendarSavedData data = RPGTimelineCalendarSavedData.get(server);
        long old = data.getDayOffsetDays();
        long next = old + deltaDays;
        data.setDayOffsetDays(next);

        broadcastSettings(server);

        source.sendSuccess(() -> Component.literal(
                "Adjusted Timeline calendar offsetDays " + old + " -> " + next
                        + " (delta=" + (next - old) + ")"
        ), true);
        return 1;
    }

    private static int executeSetDateToday(CommandSourceStack source, int year, int month1Based, int dayOfMonth) {
        MinecraftServer server = source.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            source.sendFailure(Component.literal("No overworld available."));
            return 0;
        }

        CalendarDefinition def = RPGTimelineApi.getCalendarDefinition();
        int monthCount = def.getMonthCount();
        int daysPerMonth = def.getDaysPerMonth();
        if (monthCount <= 0 || daysPerMonth <= 0) {
            source.sendFailure(Component.literal("Invalid calendar definition (months/days)."));
            return 0;
        }

        int safeYear = Math.max(1, year);
        int safeMonth = Math.max(1, Math.min(month1Based, monthCount));
        int safeDay = Math.max(1, Math.min(dayOfMonth, daysPerMonth));

        long calendarDayIndex = (long) (safeYear - 1) * (long) def.getDaysPerYear()
                + (long) (safeMonth - 1) * (long) daysPerMonth
                + (long) safeDay - 1L;

        long worldDayIndex = RPGTimelineApi.getDayIndexForGameTime(overworld.getDayTime());
        long newOffset = calendarDayIndex - worldDayIndex;

        RPGTimelineCalendarSavedData data = RPGTimelineCalendarSavedData.get(server);
        long old = data.setDayOffsetDays(newOffset);

        broadcastSettings(server);

        source.sendSuccess(() -> Component.literal(
                "Set Timeline date for today to "
                        + safeDay + "/" + safeMonth + "/" + safeYear
                        + " by changing offsetDays " + old + " -> " + newOffset
                        + " (delta=" + (newOffset - old) + ")"
        ), true);
        return 1;
    }

    private static int executeSereneSeasonsResync(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            source.sendFailure(Component.literal("No overworld available."));
            return 0;
        }

        SereneSeasonsCompat.SeasonSnapshot snapshot = SereneSeasonsCompat.getSeasonSnapshot(overworld);
        if (snapshot == null) {
            source.sendFailure(Component.literal("Serene Seasons not available (mod missing or API mismatch)."));
            return 0;
        }

        CalendarDefinition def = RPGTimelineApi.getCalendarDefinition();
        long timelineYearDays = def.getDaysPerYear();
        if (timelineYearDays <= 0L) {
            source.sendFailure(Component.literal("Invalid Timeline year length."));
            return 0;
        }

        int dayDuration = snapshot.dayDuration();
        int cycleDuration = snapshot.cycleDuration();
        long sereneYearDays = (dayDuration > 0) ? Math.max(1L, (long) cycleDuration / (long) dayDuration) : 0L;
        long sereneDay = Math.max(0L, snapshot.day());
        long sereneDayOfYear = sereneYearDays > 0 ? Math.floorMod(sereneDay, sereneYearDays) : sereneDay;

        boolean wrapped = sereneYearDays > 0L && sereneYearDays != timelineYearDays;
        long desiredDayOfYear = Math.floorMod(sereneDayOfYear, timelineYearDays);

        long worldDayIndex = RPGTimelineApi.getDayIndexForGameTime(overworld.getDayTime());
        RPGTimelineCalendarSavedData data = RPGTimelineCalendarSavedData.get(server);
        long currentOffset = data.getDayOffsetDays();

        long base = desiredDayOfYear - worldDayIndex;
        long year = timelineYearDays;
        long k0 = Math.floorDiv(currentOffset - base, year);
        long cand0 = base + k0 * year;
        long cand1 = base + (k0 + 1L) * year;
        long newOffset = Math.abs(cand1 - currentOffset) < Math.abs(cand0 - currentOffset) ? cand1 : cand0;

        long old = data.setDayOffsetDays(newOffset);
        broadcastSettings(server);

        String nowDate = RPGTimelineApi.buildDateString(worldDayIndex);
        String seasonInfo = snapshot.season().isBlank() ? "?" : snapshot.season();
        String subSeasonInfo = snapshot.subSeason().isBlank() ? "?" : snapshot.subSeason();

        String extra = wrapped
                ? " (wrapped: sereneYearDays=" + sereneYearDays + " timelineYearDays=" + timelineYearDays + ")"
                : "";

        source.sendSuccess(() -> Component.literal(
                "Serene Seasons resync: offsetDays " + old + " -> " + newOffset
                        + " (delta=" + (newOffset - old) + ")"
                        + " | sereneDayOfYear=" + sereneDayOfYear
                        + " | targetDayOfYear=" + desiredDayOfYear
                        + " | season=" + seasonInfo + "/" + subSeasonInfo
                        + " | dateNow=\"" + nowDate + "\""
                        + extra
        ), true);
        return 1;
    }

    private static void broadcastSettings(MinecraftServer server) {
        try {
            if (server == null) {
                return;
            }
            ServerLevel overworld = server.overworld();
            if (overworld == null) {
                return;
            }
            RPGTimelinePayloads.broadcastSettings(overworld);
        } catch (Throwable t) {
            LOG.warn("[RPGTimelineCalendarCommands] broadcastSettings failed: {}", t.toString());
        }
    }
}
