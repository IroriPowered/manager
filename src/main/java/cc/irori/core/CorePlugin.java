package cc.irori.core;

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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CorePlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = Logs.logger();

    private static final String DISCORD_INVITE = "ajFSfjbpcX";

    private static final List<Integer> RESTART_HOURS = List.of(2, 14);
    private static final List<Integer> ANNOUNCE_SECONDS = List.of(1800, 600, 300, 60, 30, 10, 5, 4, 3, 2, 1);

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final Message SPACE = Message.raw(" ");

    private static final ZoneId TZ = ZoneId.of("Asia/Tokyo");

    private final ScheduledExecutorService emptyRestartExecutor = Executors.newScheduledThreadPool(1);
    private final ScheduledExecutorService scheduledRestartExecutor = Executors.newSingleThreadScheduledExecutor();

    private final Set<UUID> onlinePlayers = new HashSet<>();
    private final Set<UUID> joiningPlayers = new HashSet<>();

    public CorePlugin(@NonNullDecl JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void start() {
        LOGGER.atInfo().log("Welcome to Irori-Manager :)");

        // Add currently online players and notify them the next restart
        for (PlayerRef player : Universe.get().getPlayers()) {
            onlinePlayers.add(player.getUuid());
            sendNextRestart(player);
        }

        // Periodically restart the server if no players are online
        emptyRestartExecutor.scheduleAtFixedRate(() -> {
            if (Universe.get().getPlayerCount() == 0) {
                LOGGER.atInfo().log("No players online!");
                HytaleServer.get().shutdownServer(new ShutdownReason(0, "Scheduled restart - No players online"));
            }
        }, 15L, 15L, TimeUnit.MINUTES);

        // Send join and leave messages
        getEventRegistry().register(PlayerConnectEvent.class, event -> {
            if (onlinePlayers.add(event.getPlayerRef().getUuid())) {
                broadcastMessage(event.getPlayerRef().getUsername() + " joined the game");
                for (PlayerRef ref : Universe.get().getPlayers()) {
                    if (ref.getUuid() != event.getPlayerRef().getUuid()) {
                        ShodoAPI.getInstance().sendMessage(ref, event.getPlayerRef().getUsername() + "が参加しました", Colors.YELLOW);
                    }
                }

                joiningPlayers.add(event.getPlayerRef().getUuid());
            }
        });
        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
                if (onlinePlayers.remove(event.getPlayerRef().getUuid())) {
                    broadcastMessage(event.getPlayerRef().getUsername() + " left the game");
                    ShodoAPI.getInstance().broadcastMessage(event.getPlayerRef().getUsername() + "が退出しました", Colors.YELLOW);
                }
        });

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Ref<EntityStore> ref = event.getPlayerRef();
            Store<EntityStore> store = ref.getStore();
            PlayerRef playerRef = store.getComponent(event.getPlayerRef(), PlayerRef.getComponentType());

            if (playerRef != null && joiningPlayers.remove(playerRef.getUuid())) {
                ShodoAPI.getInstance().sendMessage(playerRef, "プレイヤーチャットは、ここに日本語で表示されます。", Colors.GREEN);
                ShodoAPI.getInstance().sendMessage(playerRef, "いろり鯖へようこそ！", Colors.GREEN);

                sendNextRestart(playerRef);

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
        });
    }

    @Override
    protected void shutdown() {
        emptyRestartExecutor.shutdownNow();
        scheduledRestartExecutor.shutdownNow();
        LOGGER.atInfo().log("Irori-Manager shutting down. Goodbye!");
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

    private static void sendNextRestart(PlayerRef player) {
        LocalDateTime nextRestart = getNextRestart();
        player.sendMessage(Message.join(
                Message.raw("(!)").color(Colors.ORANGE).bold(true),
                SPACE,
                Message.raw("Next server RE-START at "),
                Message.raw(formatTime(nextRestart)).color(Colors.YELLOW)
        ));

        ShodoAPI.getInstance().sendMessage(player, "次回のサーバー自動再起動: " + formatTimeJapanese(nextRestart), Colors.ORANGE);
    }

    private static void announceRestart(PlayerRef player, int secondsLeft) {
        player.sendMessage(Message.join(
                Message.raw("(!)").color(Colors.ORANGE).bold(true),
                SPACE,
                Message.raw("Server RE-START in"),
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

    private static String formatTime(LocalDateTime time) {
        return DAY_FORMATTER.format(time) + " " + TIME_FORMATTER.format(time);
    }

    private static String formatTimeJapanese(LocalDateTime time) {
        int hour = time.getHour();
        String period = (hour < 12) ? "午前" : "午後";
        int hour12 = hour % 12;
        if (hour12 == 0) hour12 = 12;
        return String.format("%s %s%d時", DAY_FORMATTER.format(time), period, hour12);
    }
}
