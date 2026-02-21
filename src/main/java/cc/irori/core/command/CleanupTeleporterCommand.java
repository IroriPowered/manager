package cc.irori.core.command;

import cc.irori.core.Logs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class CleanupTeleporterCommand extends CommandBase {

    private static final HytaleLogger LOGGER = Logs.logger();

    public CleanupTeleporterCommand() {
        super("cleanupteleporter", "Cleans up the teleporter system");
    }

    @Override
    protected void executeSync(@NonNull CommandContext context) {
        LOGGER.atInfo().log("Clearing ExtendedTeleportHistory teleporters");
        File file = new File("universe/ExtendedTeleportHistory/Teleporters.json");
        try (JsonReader reader = new JsonReader(new FileReader(file))) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("Teleporters");
            for (JsonElement element : array) {
                JsonObject teleporter = element.getAsJsonObject();
                String dimension = teleporter.get("Dimension").getAsString();

                World world = Universe.get().getWorld(dimension);
                if (world == null) {
                    LOGGER.atInfo().log("Clearing teleporter in world '%s'", dimension);
                    array.remove(element);
                }
            }

            // Save the modified JSON back to the file
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(root.toString());
            } catch (IOException e) {
                LOGGER.atSevere().withCause(e).log("An error occurred while saving cleaned teleporters");
                context.sendMessage(Message.raw("An error occurred while saving cleaned teleporters: " + e.getMessage()));
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("An error occurred while cleaning up teleporters");
            context.sendMessage(Message.raw("An error occurred while cleaning up teleporters: " + e.getMessage()));
        }

        LOGGER.atInfo().log("Clearing vanilla teleporters");
        File oldFile = new File("universe/warps.json");
        try (JsonReader reader = new JsonReader(new FileReader(oldFile))) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("Warps");
            for (JsonElement element : array) {
                JsonObject warp = element.getAsJsonObject();
                String worldName = warp.get("World").getAsString();

                World world = Universe.get().getWorld(worldName);
                if (world == null) {
                    LOGGER.atInfo().log("Clearing teleporter in world '%s'", worldName);
                    array.remove(element);
                }
            }

            // Save the modified JSON back to the file
            try (FileWriter writer = new FileWriter(oldFile)) {
                writer.write(root.toString());
            } catch (IOException e) {
                LOGGER.atSevere().withCause(e).log("An error occurred while saving cleaned teleporters");
                context.sendMessage(Message.raw("An error occurred while saving cleaned teleporters: " + e.getMessage()));
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("An error occurred while cleaning up teleporters");
            context.sendMessage(Message.raw("An error occurred while cleaning up teleporters: " + e.getMessage()));
        }
    }
}
