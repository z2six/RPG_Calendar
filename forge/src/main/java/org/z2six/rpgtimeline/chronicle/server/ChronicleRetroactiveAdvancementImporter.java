package org.z2six.rpgtimeline.chronicle.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.registries.BuiltInRegistries;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
 * Imports completed advancements from world/advancements/<uuid>.json into the chronicle.
 */
public final class ChronicleRetroactiveAdvancementImporter {

    private static final Logger LOG = LogUtils.getLogger();
    private static final DateTimeFormatter OBTAINED_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);

    private static volatile boolean HAS_RUN = false;

    private ChronicleRetroactiveAdvancementImporter() {
        // no-op
    }

    public static void runAutoImport(MinecraftServer server) {
        if (HAS_RUN) {
            return;
        }
        HAS_RUN = true;

        try {
            if (server == null) {
                return;
            }
            if (!RPGTimelineConfig.isRetroactiveAdvancementImportEnabled()) {
                LOG.debug("[ChronicleRetroactiveAdvancementImporter] Retroactive advancement import disabled by config");
                return;
            }

            ImportResult result = importFromAdvancementFiles(server);
            if (result.imported() > 0) {
                ChroniclePayloads.broadcastFullSync(server);
                ChroniclePayloads.broadcastHallOfFame(server);
            }

            LOG.info(
                    "[ChronicleRetroactiveAdvancementImporter] Scan complete (files={}, imported={}, skipped={})",
                    result.filesScanned(),
                    result.imported(),
                    result.skipped()
            );
        } catch (Throwable t) {
            LOG.error("[ChronicleRetroactiveAdvancementImporter] runAutoImport failed safely", t);
        }
    }

    private static ImportResult importFromAdvancementFiles(MinecraftServer server) {
        Path advancementsDir = server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR);
        if (advancementsDir == null || !Files.isDirectory(advancementsDir)) {
            return new ImportResult(0, 0, 0);
        }

        long currentDayIndex = 0L;
        if (server.overworld() != null) {
            currentDayIndex = RPGTimelineApi.getDayIndexForGameTime(server.overworld().getDayTime());
        }

        Instant now = Instant.now();
        boolean includeRecipes = RPGTimelineConfig.isRetroactiveAdvancementImportIncludeRecipes();
        boolean mapByRealDays = RPGTimelineConfig.isRetroactiveAdvancementImportMapByRealDays();

        ChronicleSavedData data = ChronicleSavedData.get(server);
        Set<String> existingKeys = buildExistingAdvancementKeys(data);

        int filesScanned = 0;
        int imported = 0;
        int skipped = 0;

        try (DirectoryStream<Path> files = Files.newDirectoryStream(advancementsDir, "*.json")) {
            for (Path file : files) {
                filesScanned++;
                PlayerImportResult perFile = importPlayerFile(
                        server,
                        data,
                        existingKeys,
                        file,
                        currentDayIndex,
                        now,
                        includeRecipes,
                        mapByRealDays
                );
                imported += perFile.imported();
                skipped += perFile.skipped();
            }
        } catch (IOException e) {
            LOG.error("[ChronicleRetroactiveAdvancementImporter] Failed to scan {}", advancementsDir, e);
        }

        return new ImportResult(filesScanned, imported, skipped);
    }

    private static PlayerImportResult importPlayerFile(
            MinecraftServer server,
            ChronicleSavedData data,
            Set<String> existingKeys,
            Path file,
            long currentDayIndex,
            Instant now,
            boolean includeRecipes,
            boolean mapByRealDays
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

                String sourceIdRaw = sourceId.toString();
                String key = buildImportKey(actorUuid, sourceIdRaw);
                if (existingKeys.contains(key)) {
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

                Advancement advancement = server.getAdvancements().getAdvancement(sourceId);
                if (advancement == null) {
                    skipped++;
                    continue;
                }

                DisplayInfo display = advancement.getDisplay();
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

                long dayIndex = mapToDayIndex(completion, now, currentDayIndex, mapByRealDays);

                ChronicleEvent importedEvent = new ChronicleEvent(
                        UUID.randomUUID().toString(),
                        ChronicleEntryType.ADVANCEMENT,
                        ChronicleScope.PERSONAL,
                        dayIndex,
                        Component.Serializer.toJson(display.getTitle()),
                        Component.Serializer.toJson(display.getDescription()),
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

        return new PlayerImportResult(imported, skipped);
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

    private static Instant resolveCompletionInstant(Object advancement, Map<String, Instant> criterionTimes) {
        if (criterionTimes == null || criterionTimes.isEmpty()) {
            return null;
        }

        List<List<String>> requirements = readRequirements(advancement);
        if (requirements.isEmpty()) {
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
                // Data is incomplete for requirement evaluation; fallback keeps import robust.
                return maxInstant(criterionTimes.values());
            }

            if (completion == Instant.MIN || groupSatisfiedAt.isAfter(completion)) {
                completion = groupSatisfiedAt;
            }
        }

        return completion == Instant.MIN ? maxInstant(criterionTimes.values()) : completion;
    }

    private static List<List<String>> readRequirements(Object advancement) {
        List<List<String>> legacy = readLegacyRequirements(advancement);
        if (!legacy.isEmpty()) {
            return legacy;
        }
        return readModernRequirements(advancement);
    }

    private static List<List<String>> readLegacyRequirements(Object advancement) {
        if (advancement == null) {
            return List.of();
        }
        try {
            Object raw = advancement.getClass().getMethod("getRequirements").invoke(advancement);
            if (!(raw instanceof String[][] matrix)) {
                return List.of();
            }

            List<List<String>> groups = new ArrayList<>(matrix.length);
            for (String[] row : matrix) {
                if (row == null || row.length == 0) {
                    continue;
                }
                List<String> group = new ArrayList<>(row.length);
                for (String criterion : row) {
                    if (criterion == null || criterion.isBlank()) {
                        continue;
                    }
                    group.add(criterion);
                }
                if (!group.isEmpty()) {
                    groups.add(group);
                }
            }
            return groups;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static List<List<String>> readModernRequirements(Object advancement) {
        if (advancement == null) {
            return List.of();
        }
        try {
            Object requirements = advancement.getClass().getMethod("requirements").invoke(advancement);
            if (requirements == null) {
                return List.of();
            }
            if (requirements instanceof List<?> list) {
                return normalizeRequirementGroups(list);
            }

            Object rawGroups = requirements.getClass().getMethod("requirements").invoke(requirements);
            if (rawGroups instanceof List<?> list) {
                return normalizeRequirementGroups(list);
            }
            return List.of();
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static List<List<String>> normalizeRequirementGroups(List<?> rawGroups) {
        if (rawGroups == null || rawGroups.isEmpty()) {
            return List.of();
        }

        List<List<String>> groups = new ArrayList<>(rawGroups.size());
        for (Object rawGroup : rawGroups) {
            if (!(rawGroup instanceof List<?> list)) {
                continue;
            }
            List<String> group = new ArrayList<>(list.size());
            for (Object criterion : list) {
                if (criterion == null) {
                    continue;
                }
                String name = criterion.toString();
                if (!name.isBlank()) {
                    group.add(name);
                }
            }
            if (!group.isEmpty()) {
                groups.add(group);
            }
        }

        return groups;
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

    private static long mapToDayIndex(Instant completion, Instant now, long currentDayIndex, boolean mapByRealDays) {
        if (completion == null || now == null || !mapByRealDays) {
            return Math.max(0L, currentDayIndex);
        }

        long daysAgo = ChronoUnit.DAYS.between(
                completion.atZone(ZoneId.systemDefault()).toLocalDate(),
                now.atZone(ZoneId.systemDefault()).toLocalDate()
        );

        long mapped = currentDayIndex - daysAgo;
        if (mapped < 0L) {
            return 0L;
        }
        if (mapped > currentDayIndex) {
            return currentDayIndex;
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

    private record PlayerImportResult(int imported, int skipped) {
        private static final PlayerImportResult EMPTY = new PlayerImportResult(0, 0);
    }

    private record ImportResult(int filesScanned, int imported, int skipped) {
    }
}
