package banano.bananominecraft.bananoeconomy.configuration;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Single source of truth for all runtime-mutable plugin configuration values.
 *
 * <p>All classes that previously read {@code plugin.getConfig()} directly should
 * read from this class instead, so that in-memory state and persisted state are
 * always consistent.</p>
 *
 * <p>Call {@link #save()} after mutating any field to persist the change to disk
 * and keep the Bukkit config in sync.</p>
 */
public class ConfigEngine
{
    public static final int MIN_HISTORY_TRANSACTIONS = 1;
    public static final int MAX_HISTORY_TRANSACTIONS = 100;
    public static final int DEFAULT_HISTORY_TRANSACTIONS = 10;

    private final Plugin plugin;

    // --- node / RPC ---
    private String nodeAddress  = "";
    private String multiplier   = "100000000000000000000000000000";
    private String walletId     = "";
    private String walletSeed   = "";
    private String masterWallet = "";

    // --- block explorer ---
    private String explorerAccount = "https://creeper.banano.cc/explorer/account/";
    private String explorerBlock   = "https://creeper.banano.cc/explorer/block/";

    // --- feature flags ---
    private boolean enableOfflinePayment = false;
    private int maximumTransactionHistoryCount = 10;

    // --- websocket ---
    private String websocketUrl = "wss://ws.banano.trade";

    public ConfigEngine(Plugin plugin)
    {
        this.plugin = plugin;
        initialiseConfig(plugin.getConfig());
    }

    public void reload()
    {
        this.plugin.reloadConfig();
        initialiseConfig(this.plugin.getConfig());
    }

    private void initialiseConfig(FileConfiguration configuration)
    {
        if (configuration == null)
        {
            return;
        }

        this.nodeAddress = configuration.getString("IP", "");
        plugin.getLogger().info("Node address: " + this.nodeAddress);

        this.multiplier = configuration.getString("multiplier", "100000000000000000000000000000");
        this.walletId   = configuration.getString("walletID", "");
        this.walletSeed = configuration.getString("walletSeed", "");

        this.explorerAccount = configuration.getString("exploreaccount",
                "https://creeper.banano.cc/explorer/account/");
        plugin.getLogger().info("Account explorer URL: " + this.explorerAccount);

        this.explorerBlock = configuration.getString("exploreblock",
                "https://creeper.banano.cc/explorer/block/");
        plugin.getLogger().info("Block explorer URL: " + this.explorerBlock);

        if (configuration.contains("masterWallet"))
        {
            this.masterWallet = configuration.getString("masterWallet", "");
            plugin.getLogger().info("Master wallet address loaded.");
        }
        else
        {
            plugin.getLogger().warning("Master wallet address not specified in config.");
        }

        this.enableOfflinePayment = configuration.getBoolean("allowofflinepayment", false);
        plugin.getLogger().info("Offline payments: " + (this.enableOfflinePayment ? "ENABLED" : "DISABLED"));

        this.maximumTransactionHistoryCount = configuration.getInt("maximumtransactionhistorycount", 10);
        plugin.getLogger().info("Maximum Transaction History Count: " + this.maximumTransactionHistoryCount);

        this.websocketUrl = configuration.getString("websocketUrl", "wss://ws.banano.trade");
        plugin.getLogger().info("WebSocket URL: " + this.websocketUrl);
    }

    /**
     * Persist all runtime-mutable fields to disk.
     *
     * <p>Fields that are only ever written by the server operator (multiplier,
     * walletSeed) are intentionally not overwritten here.</p>
     *
     * @return {@code true} on success
     */
    public boolean save()
    {
        FileConfiguration config = this.plugin.getConfig();

        config.set("IP",                  this.nodeAddress);
        config.set("walletID",            this.walletId);
        config.set("masterWallet",        this.masterWallet);
        config.set("exploreaccount",      this.explorerAccount);
        config.set("exploreblock",        this.explorerBlock);
        config.set("allowofflinepayment", this.enableOfflinePayment);
        config.set("maximumtransactionhistorycount", this.maximumTransactionHistoryCount);
        config.set("websocketUrl",        this.websocketUrl);

        this.plugin.saveConfig();
        this.plugin.reloadConfig();

        return true;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public String getNodeAddress()                { return this.nodeAddress; }
    public void   setNodeAddress(String v)        { this.nodeAddress = v; }

    /** Raw Banano multiplier string (e.g. {@code "100000000000000000000000000000"}). */
    public String getMultiplier()                 { return this.multiplier; }

    public String getWalletId()                   { return this.walletId; }
    public void   setWalletId(String v)           { this.walletId = v; }

    public String getWalletSeed()                 { return this.walletSeed; }

    public String getMasterWallet()               { return this.masterWallet; }
    public void   setMasterWallet(String v)       { this.masterWallet = v; }

    public String getExplorerAccount()            { return this.explorerAccount; }
    public void   setExplorerAccount(String v)    { this.explorerAccount = v; }

    public String getExplorerBlock()              { return this.explorerBlock; }
    public void   setExplorerBlock(String v)      { this.explorerBlock = v; }

    public boolean getEnableOfflinePayment()      { return this.enableOfflinePayment; }
    public void    setEnableOfflinePayment(boolean v) { this.enableOfflinePayment = v; }

    public int     getMaximumTransactionHistoryCount() { return this.maximumTransactionHistoryCount; }
    public void    setMaximumTransactionHistoryCount(int count)
    {
        if (count < MIN_HISTORY_TRANSACTIONS
             || count > MAX_HISTORY_TRANSACTIONS)
        {
            count = DEFAULT_HISTORY_TRANSACTIONS;
        }

        this.maximumTransactionHistoryCount = count;
    }

    public String getWebsocketUrl()               { return this.websocketUrl; }
    public void   setWebsocketUrl(String v)       { this.websocketUrl = v; }
}
