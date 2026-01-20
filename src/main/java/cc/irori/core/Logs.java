package cc.irori.core;

import com.hypixel.hytale.logger.HytaleLogger;

public class Logs {

    private static final String LOGGER_NAME = "Irori-Manager";

    // Private constructor to prevent instantiation
    private Logs() {
    }

    public static HytaleLogger logger() {
        return HytaleLogger.get(LOGGER_NAME);
    }
}
