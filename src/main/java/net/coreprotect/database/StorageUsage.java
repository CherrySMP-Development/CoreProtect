package net.coreprotect.database;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.stream.Stream;

import net.coreprotect.config.ConfigHandler;

/**
 * Determines the physical storage used by the active CoreProtect database.
 */
public final class StorageUsage {

    private static final double BYTES_PER_MEGABYTE = 1024.0 * 1024.0;
    private static final double BYTES_PER_GIGABYTE = BYTES_PER_MEGABYTE * 1024.0;

    private StorageUsage() {
        throw new IllegalStateException("Utility class");
    }

    public static long getUsedBytes() throws Exception {
        if (ConfigHandler.databaseType.isSQLite()) {
            Path database = new File(ConfigHandler.path, ConfigHandler.sqlite).toPath();
            return sizeOf(database) + sizeOf(database.resolveSibling(database.getFileName() + "-wal")) + sizeOf(database.resolveSibling(database.getFileName() + "-shm"))
                    + sizeOf(database.resolveSibling(database.getFileName() + "-journal"));
        }
        if (ConfigHandler.databaseType.isDuckDB()) {
            Path database = new File(ConfigHandler.path, ConfigHandler.duckdb).toPath();
            return sizeOf(database) + sizeOf(database.resolveSibling(database.getFileName() + ".wal")) + sizeOf(database.resolveSibling(database.getFileName() + ".tmp"));
        }
        if (ConfigHandler.databaseType.isMySQL()) {
            return queryMySqlStorage();
        }
        if (ConfigHandler.databaseType.isClickHouse()) {
            return queryClickHouseStorage();
        }
        throw new IllegalStateException("Unsupported database type: " + ConfigHandler.databaseType);
    }

    public static String formatMegabytes(long bytes) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0L, bytes) / BYTES_PER_MEGABYTE);
    }

    public static String formatGigabytes(long bytes) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0L, bytes) / BYTES_PER_GIGABYTE);
    }

    private static long queryMySqlStorage() throws SQLException {
        String query = "SELECT COALESCE(SUM(COALESCE(DATA_LENGTH, 0) + COALESCE(INDEX_LENGTH, 0)), 0) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND LEFT(TABLE_NAME, ?) = ?";
        try (Connection connection = requireConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, ConfigHandler.database);
            statement.setInt(2, ConfigHandler.prefix.length());
            statement.setString(3, ConfigHandler.prefix);
            return readSize(statement);
        }
    }

    private static long queryClickHouseStorage() throws SQLException {
        String query = "SELECT coalesce(sum(bytes_on_disk), 0) FROM system.parts WHERE database = ? AND startsWith(`table`, ?)";
        try (Connection connection = requireConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, ConfigHandler.database);
            statement.setString(2, ConfigHandler.prefix);
            return readSize(statement);
        }
    }

    private static Connection requireConnection() throws SQLException {
        Connection connection = Database.getConnection(true, 1000);
        if (connection == null) {
            throw new SQLException("Database connection is unavailable");
        }
        return connection;
    }

    private static long readSize(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? Math.max(0L, resultSet.getLong(1)) : 0L;
        }
    }

    private static long sizeOf(Path path) {
        if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return fileSize(path);
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return 0L;
        }

        try (Stream<Path> paths = Files.walk(path)) {
            return paths.filter(candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)).mapToLong(StorageUsage::fileSize).sum();
        }
        catch (Exception exception) {
            return 0L;
        }
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        }
        catch (Exception exception) {
            return 0L;
        }
    }
}
