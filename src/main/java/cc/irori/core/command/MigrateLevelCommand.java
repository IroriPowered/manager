package cc.irori.core.command;

import cc.irori.core.Logs;
import com.azuredoom.levelingcore.api.LevelingCoreApi;
import com.azuredoom.levelingcore.level.LevelServiceImpl;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.File;
import java.io.FileReader;
import java.util.Optional;
import java.util.UUID;

public class MigrateLevelCommand extends CommandBase {

    // Old system
    private static final double LEVEL_BASE_XP = 50.0D;
    private static final double LEVEL_OFFSET = 0.0D;

    // New system
    private static final double LEVEL_MIGRATION_FACTOR = 1.0D;
    private static final int STAT_POINTS_PER_LEVEL = 3;

    private static final HytaleLogger LOGGER = Logs.logger();

    public MigrateLevelCommand() {
        super("migratelevel", "Migrate RPGLeveling data to LevelingCore.");
    }

    @Override
    protected void executeSync(@NonNull CommandContext context) {
        Optional<LevelServiceImpl> apiOptional = LevelingCoreApi.getLevelServiceIfPresent();
        if (apiOptional.isEmpty()) {
            context.sendMessage(Message.raw("LevelingCore is not available."));
            return;
        }

        LevelServiceImpl api = apiOptional.get();
        File playersDir = new File("universe/players");
        File[] playerFiles = playersDir.listFiles((dir, name) -> name.endsWith(".json"));

        if (playerFiles == null || playerFiles.length == 0) {
            context.sendMessage(Message.raw("No player data found."));
            return;
        }

        for (File playerFile : playerFiles) {
            String uuidStr = playerFile.getName().replace(".json", "");
            UUID uuid = UUID.fromString(uuidStr);

            context.sendMessage(Message.raw("Migrating: " + uuid));
            try (JsonReader reader = new JsonReader(new FileReader(playerFile))) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject components = root.getAsJsonObject("Components");
                JsonObject playerLevel = components.getAsJsonObject("PlayerLevelData");

                int oldLevel = playerLevel.get("Level").getAsInt();
                double oldExperience = playerLevel.get("Experience").getAsDouble();
                double xpProgress = oldExperience / xpNeededForNextLevel(oldLevel);

                int newLevel = (int) Math.floor(oldLevel * LEVEL_MIGRATION_FACTOR);

                long currentLevelXp = api.getXpForLevel(newLevel);
                long nextLevelXp = api.getXpForLevel(newLevel + 1);
                long newXp = currentLevelXp + Math.round((nextLevelXp - currentLevelXp) * xpProgress);
                int abilityPoints = calculateAbilityPoints(newLevel);

                context.sendMessage(Message.join(
                        Message.raw("  - Level: " + newLevel + "\n"),
                        Message.raw("  - Level Progress: " + (xpProgress * 100) + "%\n"),
                        Message.raw("  - New XP: " + newXp + "\n"),
                        Message.raw("  - Ability Points: " + abilityPoints)
                ));

                if (newLevel > 0) {
                    api.setLevel(uuid, newLevel);
                    api.setXp(uuid, newXp);
                    api.setUsedAbilityPoints(uuid, 0);
                    api.setAbilityPoints(uuid, abilityPoints);

                    api.setAgi(uuid, 0);
                    api.setCon(uuid, 0);
                    api.setInt(uuid, 0);
                    api.setPer(uuid, 0);
                    api.setStr(uuid, 0);
                    api.setVit(uuid, 0);
                }
            } catch (Exception e) {
                context.sendMessage(Message.raw("Failed to migrate " + uuid + ": " + e.getMessage()));
                LOGGER.atSevere().withCause(e).log("Error migrating player data for UUID: " + uuid);
            }
        }
    }

    private static double xpRequiredForLevel(int level) {
        if (level <= 1) {
            return 0.0D;
        } else {
            double xpRequired = LEVEL_BASE_XP * Math.pow((double)level, 2.5D) + LEVEL_OFFSET;
            return xpRequired;
        }
    }

    private static double xpNeededForNextLevel(int currentLevel) {
        if (currentLevel >= 100) {
            return 0.0D;
        } else {
            double xpForCurrent = xpRequiredForLevel(currentLevel);
            double xpForNext = xpRequiredForLevel(currentLevel + 1);
            double xpNeeded = xpForNext - xpForCurrent;
            return xpNeeded;
        }
    }

    private static int calculateAbilityPoints(int level) {
        /*
        int points = 0;
        for (int i = 1; i <= level; i++) {
            if (i % 5 == 0) {
                points += 5;
            } else {
                points += 3;
            }
        }
        return points;
        */
        return STAT_POINTS_PER_LEVEL * level;
    }
}
