package org.z2six.rpgtimeline.chronicle.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.api.RPGTimelineApi;
import org.z2six.rpgtimeline.chronicle.ChronicleEntryType;
import org.z2six.rpgtimeline.chronicle.ChronicleScope;
import org.z2six.rpgtimeline.config.RPGTimelineConfig;
import org.z2six.rpgtimeline.network.ChroniclePayloads;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Imports completed advancements from world/advancements/{@code <uuid>.json} into the chronicle.
 */
public final class ChronicleRetroactiveAdvancementImporter {

    private static final Logger LOG = LogUtils.getLogger();
    private static final DateTimeFormatter OBTAINED_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);

    private ChronicleRetroactiveAdvancementImporter() {
        // no-op
    }

    public static RunSummary runManualImport(MinecraftServer server) {
        try {
            if (server == null) {
                return RunSummary.EMPTY;
            }
            if (!RPGTimelineConfig.isRetroactiveAdvancementImportEnabled()) {
                LOG.debug("[ChronicleRetroactiveAdvancementImporter] Retroactive advancement import disabled by config");
                return RunSummary.EMPTY;
            }

            ImportResult result = importFromAdvancementFiles(server);
            if (result.imported() > 0 || result.updated() > 0) {
                ChroniclePayloads.broadcastFullSync(server);
                ChroniclePayloads.broadcastHallOfFame(server);
            }

            LOG.info(
                    "[ChronicleRetroactiveAdvancementImporter] Scan complete (files={}, imported={}, updated={}, skipped={})",
                    result.filesScanned(),
                    result.imported(),
                    result.updated(),
                    result.skipped()
            );
            return new RunSummary(result.filesScanned(), result.imported(), result.updated(), result.skipped());
        } catch (Throwable t) {
            LOG.error("[ChronicleRetroactiveAdvancementImporter] runManualImport failed safely", t);
            return RunSummary.EMPTY;
        }
    }

    private static ImportResult importFromAdvancementFiles(MinecraftServer server) {
        Path advancementsDir = server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR);
        if (advancementsDir == null || !Files.isDirectory(advancementsDir)) {
            return new ImportResult(0, 0, 0, 0);
        }
        if (server.overworld() == null) {
            return new ImportResult(0, 0, 0, 0);
        }

        long currentDayIndex = RPGTimelineApi.getDayIndexForGameTime(server.overworld().getDayTime());
        boolean includeRecipes = RPGTimelineConfig.isRetroactiveAdvancementImportIncludeRecipes();
        boolean mapByRealDays = RPGTimelineConfig.isRetroactiveAdvancementImportMapByRealDays();
        MappingContext mapping = buildMappingContext(server, currentDayIndex, mapByRealDays);

        ChronicleSavedData data = ChronicleSavedData.get(server);
        Set<String> existingKeys = buildExistingAdvancementKeys(data);
        var registryProvider = server.overworld().registryAccess();

        LOG.debug(
                "[ChronicleRetroactiveAdvancementImporter] Mapping context (worldStart={}, reference={}, currentDayIndex={}, secondsPerDay={}, mapByRealDays={})",
                mapping.worldStartInstant(),
                mapping.referenceInstant(),
                mapping.currentDayIndex(),
                String.format(Locale.ROOT, "%.3f", mapping.secondsPerTimelineDay()),
                mapping.mapByRealDays()
        );

        int filesScanned = 0;
        int imported = 0;
        int updated = 0;
        int skipped = 0;

        try (DirectoryStream<Path> files = Files.newDirectoryStream(advancementsDir, "*.json")) {
            for (Path file : files) {
                filesScanned++;
                PlayerImportResult perFile = importPlayerFile(
                        server,
                        data,
                        existingKeys,
                        registryProvider,
                        file,
                        mapping,
                        includeRecipes
                );
                imported += perFile.imported();
                updated += perFile.updated();
                skipped += perFile.skipped();
            }
        } catch (IOException e) {
            LOG.error("[ChronicleRetroactiveAdvancementImporter] Failed to scan {}", advancementsDir, e);
        }

        return new ImportResult(filesScanned, imported, updated, skipped);
    }

    private static PlayerImportResult importPlayerFile(
            MinecraftServer server,
            ChronicleSavedData data,
            Set<String> existingKeys,
            net.minecraft.core.HolderLookup.Provider registryProvider,
            Path file,
            MappingContext mapping,
            boolean includeRecipes
    ) {
        UUID playerUuid = parseUuidFromFile(file);
        if (playerUuid == null) {
            return PlayerImportResult.EMPTY;
        }

        String actorUuid = playerUuid.toString();
        String actorName = resolvePlayerName(server, playerUuid);

        JsonObject root = readJsonRoot(file);
        if (root == null) {
            return PlayerImportResult.EMPTY;
        }

        int imported = 0;
        int updated = 0;
        int skipped = 0;

        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            try {
                ResourceLocation sourceId = ResourceLocation.tryParse(entry.getKey());
                if (sourceId == null) {
                    skipped++;
                    continue;
                }

                if (!includeRecipes && sourceId.getPath().startsWith("recipes/")) {
                    skipped++;
                    continue;
                }

                if (!entry.getValue().isJsonObject()) {
                    skipped++;
                    continue;
                }

                JsonObject progress = entry.getValue().getAsJsonObject();
                if (!isDone(progress)) {
                    skipped++;
                    continue;
                }

                AdvancementHolder holder = server.getAdvancements().get(sourceId);
                if (holder == null) {
                    skipped++;
                    continue;
                }

                Advancement advancement = holder.value();
                DisplayInfo display = advancement.display().orElse(null);
                if (display == null || display.isHidden()) {
                    skipped++;
                    continue;
                }

                Map<String, Instant> criterionTimes = parseCriterionTimes(progress.getAsJsonObject("criteria"));
                Instant completion = resolveCompletionInstant(advancement, criterionTimes);
                if (completion == null) {
                    completion = readFallbackInstant(file);
                }
                if (completion == null) {
                    skipped++;
                    continue;
                }

                long dayIndex = mapToDayIndex(completion, mapping);
                String sourceIdRaw = sourceId.toString();
                String key = buildImportKey(actorUuid, sourceIdRaw);
                if (existingKeys.contains(key)) {
                    skipped++;
                    continue;
                }

                ChronicleEvent importedEvent = new ChronicleEvent(
                        UUID.randomUUID().toString(),
                        ChronicleEntryType.ADVANCEMENT,
                        ChronicleScope.PERSONAL,
                        dayIndex,
                        Component.Serializer.toJson(display.getTitle(), registryProvider),
                        Component.Serializer.toJson(display.getDescription(), registryProvider),
                        actorName,
                        actorUuid,
                        sourceIdRaw,
                        getItemId(display.getIcon())
                );

                data.addEvent(importedEvent);
                existingKeys.add(key);
                imported++;
            } catch (Throwable t) {
                skipped++;
                LOG.debug("[ChronicleRetroactiveAdvancementImporter] Skipping malformed advancement entry in {}", file, t);
            }
        }

        return new PlayerImportResult(imported, updated, skipped);
    }

    private static Set<String> buildExistingAdvancementKeys(ChronicleSavedData data) {
        Set<String> keys = new HashSet<>();
        for (ChronicleEvent event : data.getEvents()) {
            if (event.type() != ChronicleEntryType.ADVANCEMENT) {
                continue;
            }
            String actorUuid = event.actorUuid();
            String sourceId = event.sourceId();
            if (actorUuid == null || actorUuid.isBlank() || sourceId == null || sourceId.isBlank()) {
                continue;
            }
            keys.add(buildImportKey(actorUuid, sourceId));
        }
        return keys;
    }

    private static String buildImportKey(String actorUuid, String sourceId) {
        return actorUuid + "|" + sourceId;
    }

    private static UUID parseUuidFromFile(Path file) {
        if (file == null || file.getFileName() == null) {
            return null;
        }
        String fileName = file.getFileName().toString();
        if (!fileName.endsWith(".json")) {
            return null;
        }
        String raw = fileName.substring(0, fileName.length() - ".json".length());
        try {
            return UUID.fromString(raw);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static JsonObject readJsonRoot(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                return null;
            }
            return parsed.getAsJsonObject();
        } catch (Throwable t) {
            LOG.debug("[ChronicleRetroactiveAdvancementImporter] Failed to read {}", file, t);
            return null;
        }
    }

    private static boolean isDone(JsonObject progress) {
        if (progress == null) {
            return false;
        }
        JsonElement done = progress.get("done");
        if (done == null || done.isJsonNull()) {
            // Vanilla codec defaults this field to true when missing.
            return true;
        }
        return done.isJsonPrimitive() && done.getAsJsonPrimitive().isBoolean() && done.getAsBoolean();
    }

    private static Map<String, Instant> parseCriterionTimes(JsonObject criteriaObject) {
        Map<String, Instant> times = new HashMap<>();
        if (criteriaObject == null) {
            return times;
        }

        for (Map.Entry<String, JsonElement> entry : criteriaObject.entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                continue;
            }
            Instant parsed = parseObtainedInstant(value.getAsString());
            if (parsed == null) {
                continue;
            }
            times.put(entry.getKey(), parsed);
        }

        return times;
    }

    private static Instant parseObtainedInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw, OBTAINED_TIME_FORMAT).toInstant();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Instant resolveCompletionInstant(Advancement advancement, Map<String, Instant> criterionTimes) {
        if (advancement == null || criterionTimes == null || criterionTimes.isEmpty()) {
            return null;
        }

        List<List<String>> requirements = advancement.requirements().requirements();
        if (requirements == null || requirements.isEmpty()) {
            return maxInstant(criterionTimes.values());
        }

        Instant completion = Instant.MIN;
        for (List<String> requirementGroup : requirements) {
            Instant groupSatisfiedAt = null;
            for (String criterionName : requirementGroup) {
                Instant obtained = criterionTimes.get(criterionName);
                if (obtained == null) {
                    continue;
                }
                if (groupSatisfiedAt == null || obtained.isBefore(groupSatisfiedAt)) {
                    groupSatisfiedAt = obtained;
                }
            }

            if (groupSatisfiedAt == null) {
                return maxInstant(criterionTimes.values());
            }

            if (completion == Instant.MIN || groupSatisfiedAt.isAfter(completion)) {
                completion = groupSatisfiedAt;
            }
        }

        return completion == Instant.MIN ? maxInstant(criterionTimes.values()) : completion;
    }

    private static Instant maxInstant(Collection<Instant> values) {
        Instant max = null;
        for (Instant value : values) {
            if (value == null) {
                continue;
            }
            if (max == null || value.isAfter(max)) {
                max = value;
            }
        }
        return max;
    }

    private static Instant readFallbackInstant(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static MappingContext buildMappingContext(MinecraftServer server, long currentDayIndex, boolean mapByRealDays) {
        LevelTimeSnapshot snapshot = readLevelTimeSnapshot(server);
        Instant referenceInstant = resolveReferenceInstant(snapshot);
        Instant worldStartInstant = resolveWorldStartInstant(server, referenceInstant, snapshot);
        double secondsPerTimelineDay = resolveSecondsPerTimelineDay(worldStartInstant, referenceInstant, currentDayIndex);
        return new MappingContext(worldStartInstant, referenceInstant, Math.max(0L, currentDayIndex), mapByRealDays, secondsPerTimelineDay);
    }

    private static double resolveSecondsPerTimelineDay(Instant worldStartInstant, Instant referenceInstant, long currentDayIndex) {
        double fixedSecondsPerDay = Math.max(1.0D, RPGTimelineConfig.TICKS_PER_DAY / 20.0D);
        long safeCurrentDay = Math.max(0L, currentDayIndex);
        if (safeCurrentDay <= 0L || worldStartInstant == null || referenceInstant == null) {
            return fixedSecondsPerDay;
        }

        long elapsedSeconds = Duration.between(worldStartInstant, referenceInstant).getSeconds();
        if (elapsedSeconds <= 0L) {
            return fixedSecondsPerDay;
        }

        return Math.max(1.0D, elapsedSeconds / (double) safeCurrentDay);
    }

    private static LevelTimeSnapshot readLevelTimeSnapshot(MinecraftServer server) {
        if (server == null) {
            return new LevelTimeSnapshot(0L, 0L);
        }
        try {
            Path levelDat = server.getWorldPath(LevelResource.LEVEL_DATA_FILE);
            if (levelDat == null || !Files.isRegularFile(levelDat)) {
                return new LevelTimeSnapshot(0L, 0L);
            }

            CompoundTag root = NbtIo.readCompressed(levelDat, NbtAccounter.unlimitedHeap());
            if (root == null || !root.contains("Data")) {
                return new LevelTimeSnapshot(0L, 0L);
            }

            CompoundTag data = root.getCompound("Data");
            long lastPlayedMs = data.getLong("LastPlayed");
            long gameTimeTicks = data.getLong("Time");
            return new LevelTimeSnapshot(lastPlayedMs, Math.max(0L, gameTimeTicks));
        } catch (Throwable t) {
            LOG.debug("[ChronicleRetroactiveAdvancementImporter] Failed reading level.dat time snapshot", t);
            return new LevelTimeSnapshot(0L, 0L);
        }
    }

    private static Instant resolveReferenceInstant(LevelTimeSnapshot snapshot) {
        if (snapshot != null && snapshot.lastPlayedMs() > 0L) {
            try {
                return Instant.ofEpochMilli(snapshot.lastPlayedMs());
            } catch (Throwable ignored) {
                // no-op
            }
        }
        return Instant.now();
    }

    private static Instant resolveWorldStartInstant(MinecraftServer server, Instant referenceInstant, LevelTimeSnapshot snapshot) {
        if (snapshot != null && snapshot.gameTimeTicks() > 0L && referenceInstant != null) {
            long secondsFromStart = Math.max(1L, Math.round(snapshot.gameTimeTicks() / 20.0D));
            return referenceInstant.minusSeconds(secondsFromStart);
        }

        List<Instant> candidates = new ArrayList<>();
        if (server != null) {
            Path levelDat = server.getWorldPath(LevelResource.LEVEL_DATA_FILE);
            if (levelDat != null) {
                addCreationCandidate(candidates, levelDat, referenceInstant);
                Path worldDir = levelDat.getParent();
                if (worldDir != null) {
                    addCreationCandidate(candidates, worldDir, referenceInstant);
                }
            }
        }

        Instant earliest = earliestInstant(candidates);
        if (earliest != null) {
            return earliest;
        }

        return referenceInstant.minusSeconds(Math.max(1L, RPGTimelineConfig.TICKS_PER_DAY / 20L));
    }

    private static void addCreationCandidate(List<Instant> candidates, Path path, Instant referenceInstant) {
        try {
            if (path == null || !Files.exists(path)) {
                return;
            }
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            Instant created = attrs.creationTime() == null ? null : attrs.creationTime().toInstant();
            if (created != null && !created.isAfter(referenceInstant)) {
                candidates.add(created);
            }
        } catch (Throwable ignored) {
            // no-op
        }
    }

    private static Instant earliestInstant(Collection<Instant> values) {
        Instant earliest = null;
        if (values == null) {
            return null;
        }
        for (Instant value : values) {
            if (value == null) {
                continue;
            }
            if (earliest == null || value.isBefore(earliest)) {
                earliest = value;
            }
        }
        return earliest;
    }

    private static long mapToDayIndex(Instant completion, MappingContext mapping) {
        if (mapping == null) {
            return 0L;
        }
        long currentDay = Math.max(0L, mapping.currentDayIndex());
        if (!mapping.mapByRealDays() || completion == null) {
            return currentDay;
        }
        if (currentDay <= 0L) {
            return 0L;
        }
        if (!completion.isAfter(mapping.worldStartInstant())) {
            return 0L;
        }
        if (!completion.isBefore(mapping.referenceInstant())) {
            return currentDay;
        }

        long secondsFromStart = Duration.between(mapping.worldStartInstant(), completion).getSeconds();
        if (secondsFromStart <= 0L) {
            return 0L;
        }

        long mapped = Math.round(secondsFromStart / Math.max(1.0D, mapping.secondsPerTimelineDay()));
        if (mapped < 0L) {
            return 0L;
        }
        if (mapped > currentDay) {
            return currentDay;
        }
        return mapped;
    }

    private static String resolvePlayerName(MinecraftServer server, UUID playerUuid) {
        try {
            if (server == null || playerUuid == null) {
                return "Player";
            }
            var cache = server.getProfileCache();
            if (cache != null) {
                Optional<GameProfile> optional = cache.get(playerUuid);
                if (optional.isPresent()) {
                    String name = optional.get().getName();
                    if (name != null && !name.isBlank()) {
                        return name;
                    }
                }
            }
        } catch (Throwable ignored) {
            // no-op
        }

        String raw = playerUuid == null ? "" : playerUuid.toString();
        if (raw.length() >= 8) {
            return "Player-" + raw.substring(0, 8);
        }
        return "Player";
    }

    private static String getItemId(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "minecraft:paper";
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "minecraft:paper" : id.toString();
    }

    private record MappingContext(
            Instant worldStartInstant,
            Instant referenceInstant,
            long currentDayIndex,
            boolean mapByRealDays,
            double secondsPerTimelineDay
    ) {
    }

    private record LevelTimeSnapshot(long lastPlayedMs, long gameTimeTicks) {
    }

    private record PlayerImportResult(int imported, int updated, int skipped) {
        private static final PlayerImportResult EMPTY = new PlayerImportResult(0, 0, 0);
    }

    public record RunSummary(int filesScanned, int imported, int updated, int skipped) {
        private static final RunSummary EMPTY = new RunSummary(0, 0, 0, 0);
    }

    private record ImportResult(int filesScanned, int imported, int updated, int skipped) {
    }
}
