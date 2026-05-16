package banano.bananominecraft.bananoeconomy.db;

import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class MysqlDBConnector extends BaseDBConnector
{
    private static final String TABLE_USERS            = "users";
    private static final String TABLE_OFFLINE_PAYMENTS = "offlinepayments";

    private static final String SQL_CREATE_USERS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_USERS + " (" +
            "    playerUUID  VARCHAR(75)  NOT NULL UNIQUE," +
            "    name        VARCHAR(50)  NOT NULL," +
            "    wallet      VARCHAR(100) NOT NULL," +
            "    frozen      BOOLEAN      NOT NULL DEFAULT FALSE," +
            "    PRIMARY KEY (playerUUID)" +
            ") ENGINE=INNODB";

    private static final String SQL_CREATE_OFFLINE_PAYMENTS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_OFFLINE_PAYMENTS + " (" +
            "    id             INT         NOT NULL AUTO_INCREMENT," +
            "    playerUUID     VARCHAR(75) NOT NULL," +
            "    fromplayername VARCHAR(50) NOT NULL," +
            "    amount         DOUBLE      NOT NULL," +
            "    blockhash      VARCHAR(250) NOT NULL," +
            "    message        VARCHAR(250) NOT NULL," +
            "    transdate      TIMESTAMP   NOT NULL," +
            "    PRIMARY KEY (id)" +
            ") ENGINE=INNODB";

    private final Plugin plugin;
    private final HikariDataSource dataSource;

    public MysqlDBConnector(Plugin plugin)
    {
        this.plugin     = plugin;
        this.dataSource = new HikariDataSource();
        initialiseDataSource();
        setupDatabase();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void close()
    {
        try
        {
            this.dataSource.close();
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Error closing MySQL data source.", ex);
        }
    }

    // -------------------------------------------------------------------------
    // IDBConnector — player records
    // -------------------------------------------------------------------------

    @Override
    protected PlayerRecord loadPlayerRecord(Player player)
    {
        return getPlayerRecord(player.getUniqueId(), true);
    }

    @Override
    public PlayerRecord getOfflinePlayerRecord(OfflinePlayer player)
    {
        return getPlayerRecord(player.getUniqueId(), false);
    }

    @Override
    protected boolean insertPlayerRecord(PlayerRecord playerRecord)
    {
        final String sql = "INSERT INTO " + TABLE_USERS +
                           " (playerUUID, name, wallet, frozen) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, playerRecord.getPlayerUUID());
            ps.setString(2, playerRecord.getPlayerName());
            ps.setString(3, playerRecord.getWallet());
            ps.setBoolean(4, playerRecord.isFrozen());

            return ps.executeUpdate() > 0;
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to insert player record.", ex);
        }
        return false;
    }

    @Override
    public boolean updatePlayerRecord(PlayerRecord playerRecord)
    {
        final String sql = "UPDATE " + TABLE_USERS +
                           " SET name = ?, wallet = ?, frozen = ? WHERE playerUUID = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, playerRecord.getPlayerName());
            ps.setString(2, playerRecord.getWallet());
            ps.setBoolean(3, playerRecord.isFrozen());
            ps.setString(4, playerRecord.getPlayerUUID());

            return ps.executeUpdate() > 0;
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to update player record.", ex);
        }
        return false;
    }

    @Override
    public boolean hasPlayerRecord(Player player)
    {
        return hasPlayerRecord(player.getUniqueId());
    }

    @Override
    public boolean hasPlayerRecord(OfflinePlayer player)
    {
        return hasPlayerRecord(player.getUniqueId());
    }

    @Override
    public boolean isAlreadyAssignedToOtherPlayer(String walletAddress, Player currentPlayer)
    {
        final String sql = "SELECT COUNT(playerUUID) AS playercount FROM " + TABLE_USERS +
                           " WHERE playerUUID <> ? AND wallet = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, currentPlayer.getUniqueId().toString());
            ps.setString(2, walletAddress);

            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next() && rs.getInt("playercount") > 0;
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to check wallet assignment.", ex);
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // IDBConnector — offline payments
    // -------------------------------------------------------------------------

    @Override
    public boolean saveOfflinePayment(OfflinePaymentRecord paymentRecord)
    {
        final String sql = "INSERT INTO " + TABLE_OFFLINE_PAYMENTS +
                           " (playerUUID, fromplayername, amount, blockhash, message, transdate)" +
                           " VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, paymentRecord.targetPlayerUUID().toString());
            ps.setString(2, paymentRecord.fromPlayerName());
            ps.setDouble(3, paymentRecord.paymentAmount());
            ps.setString(4, paymentRecord.blockHash());
            ps.setString(5, paymentRecord.message());
            ps.setTimestamp(6, Timestamp.valueOf(paymentRecord.transactionDate()));

            ps.executeUpdate();
            return true;
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to save offline payment.", ex);
        }
        return false;
    }

    @Override
    public List<OfflinePaymentRecord> getOfflinePaymentRecords(Player forPlayer)
    {
        List<OfflinePaymentRecord> paymentRecords = new ArrayList<>();
        if (forPlayer == null)
        {
            return paymentRecords;
        }

        final String sql = "SELECT playerUUID, fromplayername, amount, blockhash, message, transdate" +
                           " FROM " + TABLE_OFFLINE_PAYMENTS + " WHERE playerUUID = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, forPlayer.getUniqueId().toString());

            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    try
                    {
                        paymentRecords.add(new OfflinePaymentRecord(
                                UUID.fromString(rs.getString("playerUUID")),
                                rs.getString("fromplayername"),
                                rs.getDouble("amount"),
                                rs.getString("blockhash"),
                                rs.getTimestamp("transdate").toLocalDateTime(),
                                rs.getString("message")));
                    }
                    catch (Exception ex)
                    {
                        plugin.getLogger().log(Level.WARNING, "Skipping malformed offline payment row.", ex);
                    }
                }
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to load offline payments.", ex);
        }
        return paymentRecords;
    }

    @Override
    public void deleteOfflinePaymentRecords(Player forPlayer)
    {
        if (forPlayer == null)
        {
            return;
        }

        final String sql = "DELETE FROM " + TABLE_OFFLINE_PAYMENTS + " WHERE playerUUID = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, forPlayer.getUniqueId().toString());
            ps.executeUpdate();
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to delete offline payments.", ex);
        }
    }

    @Override
    public double getOfflinePaymentsTotal(Player forPlayer)
    {
        if (forPlayer == null)
        {
            return 0;
        }

        final String sql = "SELECT SUM(amount) AS total FROM " + TABLE_OFFLINE_PAYMENTS +
                           " WHERE playerUUID = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, forPlayer.getUniqueId().toString());

            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    return rs.getDouble("total");
                }
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to sum offline payments.", ex);
        }
        return 0;
    }

    // -------------------------------------------------------------------------
    // IDBConnector — freeze queries
    // -------------------------------------------------------------------------

    @Override
    public List<PlayerRecord> getFrozenPlayers()
    {
        return queryPlayersByFrozen(true);
    }

    @Override
    public List<PlayerRecord> getUnfrozenPlayers()
    {
        return queryPlayersByFrozen(false);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void initialiseDataSource()
    {
        try
        {
            FileConfiguration config = this.plugin.getConfig();

            String serverName   = config.getString("mysqlServerName");
            int    port         = config.getInt("mysqlPort", 3306);
            String databaseName = config.getString("mysqlDatabaseName");
            String userName     = config.getString("mysqlUsername");
            String password     = config.getString("mysqlPassword");

            this.dataSource.setDataSourceClassName("com.mysql.cj.jdbc.MysqlDataSource");
            this.dataSource.addDataSourceProperty("serverName",   serverName);
            this.dataSource.addDataSourceProperty("port",         port);
            this.dataSource.addDataSourceProperty("databaseName", databaseName);
            this.dataSource.addDataSourceProperty("user",         userName);
            this.dataSource.addDataSourceProperty("password",     password);
            this.dataSource.setMaximumPoolSize(10);
            this.dataSource.setMinimumIdle(5);
            this.dataSource.setIdleTimeout(45_000);
            this.dataSource.setMaxLifetime(60_000);
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialise MySQL data source.", ex);
        }
    }

    /** Creates the schema tables on first startup. */
    private void setupDatabase()
    {
        try (Connection conn = getConnection())
        {
            try (PreparedStatement ps = conn.prepareStatement(SQL_CREATE_USERS))
            {
                ps.execute();
            }
            catch (Exception ex)
            {
                plugin.getLogger().log(Level.SEVERE, "Failed to create '" + TABLE_USERS + "' table.", ex);
            }

            try (PreparedStatement ps = conn.prepareStatement(SQL_CREATE_OFFLINE_PAYMENTS))
            {
                ps.execute();
            }
            catch (Exception ex)
            {
                plugin.getLogger().log(Level.SEVERE, "Failed to create '" + TABLE_OFFLINE_PAYMENTS + "' table.", ex);
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Database setup failed — no connection available.", ex);
        }
    }

    /**
     * Returns a connection from the pool.
     *
     * @throws SQLException if the pool cannot provide a connection
     */
    private Connection getConnection() throws SQLException
    {
        return this.dataSource.getConnection();
    }

    private PlayerRecord getPlayerRecord(UUID playerUUID, boolean cacheRecord)
    {
        PlayerRecord cached = playerRecords.get(playerUUID);
        if (cached != null)
        {
            return cached;
        }

        final String sql = "SELECT playerUUID, name, wallet, frozen FROM " + TABLE_USERS +
                           " WHERE playerUUID = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, playerUUID.toString());

            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    PlayerRecord record = new PlayerRecord(
                            rs.getString("playerUUID"),
                            rs.getString("name"),
                            rs.getString("wallet"),
                            rs.getBoolean("frozen"));

                    if (cacheRecord)
                    {
                        playerRecords.put(playerUUID, record);
                    }
                    return record;
                }
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player record.", ex);
        }
        return null;
    }

    private boolean hasPlayerRecord(UUID playerUUID)
    {
        if (playerRecords.containsKey(playerUUID))
        {
            return true;
        }

        final String sql = "SELECT COUNT(playerUUID) AS playercount FROM " + TABLE_USERS +
                           " WHERE playerUUID = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, playerUUID.toString());

            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next() && rs.getInt("playercount") > 0;
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to check player record existence.", ex);
        }
        return false;
    }

    private List<PlayerRecord> queryPlayersByFrozen(boolean frozen)
    {
        List<PlayerRecord> records = new ArrayList<>();

        final String sql = "SELECT playerUUID, name, wallet, frozen FROM " + TABLE_USERS +
                           " WHERE frozen = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setBoolean(1, frozen);

            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    records.add(new PlayerRecord(
                            rs.getString("playerUUID"),
                            rs.getString("name"),
                            rs.getString("wallet"),
                            rs.getBoolean("frozen")));
                }
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to query players by frozen=" + frozen + ".", ex);
        }
        return records;
    }
}
