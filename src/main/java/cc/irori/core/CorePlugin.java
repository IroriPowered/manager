package cc.irori.core;

import cc.irori.core.command.CoreDebugCommand;
import cc.irori.core.command.ShigenCommand;
import cc.irori.core.command.SpawnCommand;
import cc.irori.shodo.ShodoAPI;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ShutdownReason;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerConfigData;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CorePlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = Logs.logger();

    private static final String DISCORD_INVITE = "ajFSfjbpcX";

    private static final List<Integer> RESTART_HOURS = List.of(2, 14);
    private static final List<Integer> ANNOUNCE_SECONDS = List.of(1800, 600, 300, 240, 180, 120, 60, 30, 10, 5, 4, 3, 2, 1);

    private static final Message SPACE = Message.raw(" ");

    private static final ZoneId TZ = ZoneId.of("Asia/Tokyo");

    private final ScheduledExecutorService emptyRestartExecutor = Executors.newScheduledThreadPool(1);
    private final ScheduledExecutorService scheduledRestartExecutor = Executors.newSingleThreadScheduledExecutor();

    private final Set<UUID> onlinePlayers = new HashSet<>();
    private final Set<UUID> joiningPlayers = new HashSet<>();

    private final Map<UUID, ShigenHud> shigenHuds = new ConcurrentHashMap<>();

    public CorePlugin(@NonNullDecl JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void start() {
        LOGGER.atInfo().log("Welcome to Irori-Manager :)");

        // Add currently online players
        for (PlayerRef ref : Universe.get().getPlayers()) {
            onlinePlayers.add(ref.getUuid());

            Store<EntityStore> store = ref.getReference().getStore();
            store.getExternalData().getWorld().execute(() -> {
                Player player = store.getComponent(ref.getReference(), Player.getComponentType());
                shigenHuds.put(ref.getUuid(), new ShigenHud(player, ref));
            });
        }

        scheduleNextRestart();

        // Send join and leave messages
        getEventRegistry().register(PlayerConnectEvent.class, event -> {
            Holder<EntityStore> holder = event.getHolder();
            Player player = holder.getComponent(Player.getComponentType());

            if (onlinePlayers.add(event.getPlayerRef().getUuid())) {
                broadcastMessage(event.getPlayerRef().getUsername() + " joined the game");
                for (PlayerRef ref : Universe.get().getPlayers()) {
                    if (ref.getUuid() != event.getPlayerRef().getUuid()) {
                        ShodoAPI.getInstance().sendMessage(ref, event.getPlayerRef().getUsername() + "が参加しました", Colors.YELLOW);
                    }
                }

                joiningPlayers.add(event.getPlayerRef().getUuid());
            }

            assert player != null;

            PlayerConfigData playerConfig = player.getPlayerConfigData();
            String lastWorldName = playerConfig.getWorld();
            World lastWorld = Universe.get().getWorld(lastWorldName);

            // Trying to join a shigen world that no longer exists
            if (lastWorld == null && isShigenWorld(lastWorldName)) {
                World defaultWorld = Universe.get().getDefaultWorld();

                Transform spawn = defaultWorld.getWorldConfig().getSpawnProvider().getSpawnPoint(event.getPlayerRef().getReference(), event.getPlayerRef().getReference().getStore());
                TransformComponent transformComponent = holder.ensureAndGetComponent(TransformComponent.getComponentType());
                transformComponent.setPosition(spawn.getPosition());
                transformComponent.setRotation(spawn.getRotation());
                HeadRotation headRotationComponent = holder.ensureAndGetComponent(HeadRotation.getComponentType());
                headRotationComponent.teleportRotation(spawn.getRotation());
            }

            shigenHuds.put(event.getPlayerRef().getUuid(), new ShigenHud(player, event.getPlayerRef()));
        });
        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
                if (onlinePlayers.remove(event.getPlayerRef().getUuid())) {
                    broadcastMessage(event.getPlayerRef().getUsername() + " left the game");
                    ShodoAPI.getInstance().broadcastMessage(event.getPlayerRef().getUsername() + "が退出しました", Colors.YELLOW);

                    shigenHuds.remove(event.getPlayerRef().getUuid());
                }
        });

        getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> {
            PlayerRef ref = event.getHolder().getComponent(PlayerRef.getComponentType());
            ShigenHud shigenHud = shigenHuds.get(ref.getUuid());

            if (shigenHud != null) {
                shigenHud.setShigenId(getShigenId(event.getWorld()));
                shigenHud.setVisible(isShigenWorld(event.getWorld()));
                shigenHud.update();
            }
        });

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Ref<EntityStore> ref = event.getPlayerRef();
            Store<EntityStore> store = ref.getStore();

            World world = store.getExternalData().getWorld();
            world.execute(() -> {
                PlayerRef playerRef = store.getComponent(event.getPlayerRef(), PlayerRef.getComponentType());

                assert playerRef != null;

                if (joiningPlayers.remove(playerRef.getUuid())) {
                    ShodoAPI.getInstance().sendMessage(playerRef, "プレイヤーチャットは、ここに日本語で表示されます。", Colors.GREEN);
                    ShodoAPI.getInstance().sendMessage(playerRef, "いろり鯖へようこそ！", Colors.GREEN);

                    playerRef.sendMessage(Message.join(
                            Message.raw("> Welcome to ").color(Colors.GOLD_LIGHT),
                            Message.raw("IRORI Server").color(Colors.MUSTARD).bold(true),
                            Message.raw("!").color(Colors.GOLD_LIGHT)
                    ));
                    playerRef.sendMessage(Message.join(
                            Message.raw("> Please join our ").color(Colors.SKY_LIGHT),
                            Message.raw("DISCORD").color(Colors.BLUE_LIGHT).bold(true),
                            Message.raw(": ").color(Colors.GOLD_LIGHT),
                            Message.raw("discord.gg/" + DISCORD_INVITE).color(Colors.TEAL).link("https://discord.gg/" + DISCORD_INVITE)
                    ));
                }

                if (isShigenWorld(world)) {
                    ShodoAPI.getInstance().sendMessage(playerRef, "このワールドは定期的にリセットされるので、拠点の製作などは控えてください。", Colors.SCARLET_LIGHT);
                    ShodoAPI.getInstance().sendMessage(playerRef, "[!] 資源ワールドに入りました [!]", Colors.SCARLET_LIGHT);
                }
            });
        });

        getCommandRegistry().registerCommand(new SpawnCommand());
        getCommandRegistry().registerCommand(new ShigenCommand());
        getCommandRegistry().registerCommand(new CoreDebugCommand());

        // Server watchdog thread
        new ServerWatchdog(Universe.get().getDefaultWorld()).start();
    }

    @Override
    protected void shutdown() {
        LOGGER.atInfo().log("Irori-Manager shutting down. Goodbye!");
        emptyRestartExecutor.shutdown();
        scheduledRestartExecutor.shutdown();
    }

    private void scheduleNextRestart() {
        LocalDateTime nextRestart = getNextRestart();
        long delayMillis = Duration.between(LocalDateTime.now(TZ), nextRestart).toMillis();

        for (int secondsBefore : ANNOUNCE_SECONDS) {
            long announceDelay = delayMillis - secondsBefore * 1000L;
            if (announceDelay > 0) {
                scheduledRestartExecutor.schedule(() -> {
                    for (PlayerRef player : Universe.get().getPlayers()) {
                        announceRestart(player, secondsBefore);
                    }
                }, announceDelay, TimeUnit.MILLISECONDS);
            }
        }

        scheduledRestartExecutor.schedule(() ->
                        HytaleServer.get().shutdownServer(ShutdownReason.SHUTDOWN.withMessage("Automatic restart")),
                delayMillis, TimeUnit.MILLISECONDS);
    }

    private static void broadcastMessage(String message) {
        Universe.get().sendMessage(Message.raw(message).color(Colors.YELLOW));
    }


    private static LocalDateTime getNextRestart() {
        LocalDateTime now = LocalDateTime.now(TZ);
        LocalDate today = now.toLocalDate();

        List<Integer> sortedHours = RESTART_HOURS.stream().sorted().toList();
        for (int hour : sortedHours) {
            LocalDateTime candidate = today.atTime(hour, 0, 0);
            if (candidate.isAfter(now)) {
                return candidate;
            }
        }

        return today.plusDays(1).atTime(sortedHours.getFirst(), 0, 0);
    }

    private static void announceRestart(PlayerRef player, int secondsLeft) {
        player.sendMessage(Message.join(
                Message.raw("(!)").color(Colors.ORANGE).bold(true),
                SPACE,
                Message.raw("Server RE-START in "),
                Message.raw(getTimeString(secondsLeft)).color(Colors.YELLOW)
        ));

        ShodoAPI.getInstance().sendMessage(player, getTimeStringJapanese(secondsLeft) + "後 にサーバーを自動再起動します", Colors.ORANGE);
    }

    private static String getTimeString(int totalSeconds) {
        if (totalSeconds >= 60) {
            int minutes = totalSeconds / 60;
            return minutes + " minute" + (minutes > 1 ? "s" : "");
        }
        return totalSeconds + " second" + (totalSeconds > 1 ? "s" : "");
    }

    private static String getTimeStringJapanese(int totalSeconds) {
        if (totalSeconds >= 60) {
            int minutes = totalSeconds / 60;
            return minutes + "分";
        }
        return totalSeconds + "秒";
    }

    public static boolean isShigenWorld(String worldName) {
        return worldName.startsWith("shigen");
    }

    public static boolean isShigenWorld(World world) {
        return isShigenWorld(world.getName());
    }

    public static @Nullable World getNewestShigenWorld() {
        World shigen = null;
        int newestId = -1;

        for (World world : Universe.get().getWorlds().values()) {
            if (isShigenWorld(world)) {
                int id = Integer.parseInt(world.getName().substring(6));
                if (id > newestId) {
                    newestId = id;
                    shigen = world;
                }
            }
        }
        return shigen;
    }

    public static int getShigenId(World world) {
        if (isShigenWorld(world)) {
            return Integer.parseInt(world.getName().substring(6));
        }
        return 0;
    }
}
