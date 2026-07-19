package net.coreprotect.config;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import net.coreprotect.CoreProtect;

/**
 * UUID based access control for all CoreProtect commands.
 */
public final class CommandWhitelist {

    public static final String FILE_NAME = "whitelisted.yml";
    private static final String UUIDS_KEY = "uuids";

    private static volatile Set<UUID> whitelistedUuids = Collections.emptySet();

    private CommandWhitelist() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean isWhitelisted(CommandSender sender) {
        return sender instanceof Player && whitelistedUuids.contains(((Player) sender).getUniqueId());
    }

    public static synchronized void reload() {
        CoreProtect plugin = CoreProtect.getInstance();
        File file = new File(plugin.getDataFolder(), FILE_NAME);

        try {
            if (!file.exists()) {
                plugin.saveResource(FILE_NAME, false);
            }

            YamlConfiguration configuration = new YamlConfiguration();
            configuration.load(file);
            whitelistedUuids = parseUuids(configuration.getList(UUIDS_KEY), plugin.getLogger()::warning);
        }
        catch (Exception exception) {
            whitelistedUuids = Collections.emptySet();
            plugin.getLogger().severe("Unable to load " + FILE_NAME + "; all CoreProtect commands are locked: " + exception.getMessage());
        }
    }

    static Set<UUID> parseUuids(List<?> configuredUuids, Consumer<String> warningLogger) {
        if (configuredUuids == null) {
            warningLogger.accept(FILE_NAME + " must contain a YAML list named '" + UUIDS_KEY + "'; all CoreProtect commands are locked.");
            return Collections.emptySet();
        }

        Set<UUID> parsedUuids = new HashSet<>();
        for (Object configuredUuid : configuredUuids) {
            String value = configuredUuid == null ? "" : configuredUuid.toString().trim();
            try {
                UUID uuid = UUID.fromString(value);
                if (!uuid.toString().equalsIgnoreCase(value)) {
                    throw new IllegalArgumentException("UUID must use the canonical format");
                }
                parsedUuids.add(uuid);
            }
            catch (IllegalArgumentException exception) {
                warningLogger.accept("Ignoring invalid UUID in " + FILE_NAME + ": " + value);
            }
        }

        return Collections.unmodifiableSet(parsedUuids);
    }
}
