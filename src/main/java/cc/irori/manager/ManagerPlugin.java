package cc.irori.manager;

import cc.irori.shodo.ShodoAPI;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ShutdownReason;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ManagerPlugin extends JavaPlugin {

    private static final String DISCORD_INVITE = "ajFSfjbpcX";

    private static final HytaleLogger LOGGER = Logs.logger();

    private final ScheduledExecutorService emptyRestartExecutor = Executors.newScheduledThreadPool(1);
    private final ScheduledExecutorService worldRestartExecutor = Executors.newScheduledThreadPool(1);

    private final Set<UUID> joiningPlayers = new HashSet<>();

    public ManagerPlugin(@NonNullDecl JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void start() {
        LOGGER.atInfo().log("Welcome to Irori-Manager :)");

        // Periodically restart the server if no players are online
        emptyRestartExecutor.scheduleAtFixedRate(() -> {
            if (Universe.get().getPlayerCount() == 0) {
                LOGGER.atInfo().log("No players online!");
                HytaleServer.get().shutdownServer(new ShutdownReason(0, "Scheduled restart - No players online"));
            }
        }, 15L, 15L, TimeUnit.MINUTES);

        // Restart if default world is dead (bug)
        worldRestartExecutor.scheduleAtFixedRate(() -> {
            if (!Universe.get().getDefaultWorld().isAlive()) {
                HytaleLogger.getLogger().atSevere().log("Default world is dead!");
                HytaleServer.get().shutdownServer(new ShutdownReason(1, "Scheduled restart - Default world dead"));
            }
        }, 30L, 30L, TimeUnit.SECONDS);

        // Send join and leave messages
        getEventRegistry().register(PlayerConnectEvent.class, event -> {
            broadcastMessage(event.getPlayerRef().getUsername() + " joined the game");

            event.getPlayerRef().sendMessage(Message.join(
                    Message.raw("> Welcome to ").color(Colors.GOLD_LIGHT),
                    Message.raw("IRORI Server").color(Colors.MUSTARD).bold(true),
                    Message.raw("!").color(Colors.GOLD_LIGHT)
            ));
            event.getPlayerRef().sendMessage(Message.join(
                    Message.raw("> Please join our ").color(Colors.SKY_LIGHT),
                    Message.raw("DISCORD").color(Colors.BLUE_LIGHT).bold(true),
                    Message.raw(": ").color(Colors.GOLD_LIGHT),
                    Message.raw("discord.gg/" + DISCORD_INVITE).color(Colors.TEAL).link("https://discord.gg/" + DISCORD_INVITE)
            ));

            joiningPlayers.add(event.getPlayerRef().getUuid());
        });
        getEventRegistry().register(PlayerDisconnectEvent.class, event ->
                broadcastMessage(event.getPlayerRef().getUsername() + " left the game"));

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Ref<EntityStore> ref = event.getPlayerRef();
            Store<EntityStore> store = ref.getStore();
            PlayerRef playerRef = store.getComponent(event.getPlayerRef(), PlayerRef.getComponentType());

            if (joiningPlayers.remove(playerRef.getUuid())) {
                ShodoAPI.getInstance().sendMessage(playerRef, "プレイヤーチャットは、ここに日本語で表示されます。", Colors.YELLOW);
                ShodoAPI.getInstance().sendMessage(playerRef, "いろり鯖へようこそ！", Colors.YELLOW);
            }
        });
    }

    @Override
    protected void shutdown() {
        emptyRestartExecutor.shutdownNow();
        worldRestartExecutor.shutdownNow();
        LOGGER.atInfo().log("Irori-Manager shutting down. Goodbye!");
    }

    private static void broadcastMessage(String message) {
        Universe.get().sendMessage(Message.raw(message).color(Colors.YELLOW));
    }
}
