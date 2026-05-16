package banano.bananominecraft.bananoeconomy;

import banano.bananominecraft.bananoeconomy.commands.*;
import banano.bananominecraft.bananoeconomy.commands.tabcompleters.*;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.DBConnectorFactory;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.events.OnJoin;
import banano.bananominecraft.bananoeconomy.events.OnLeave;
import banano.bananominecraft.bananoeconomy.io.BananoWebSocket;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.io.VaultConnector;
import banano.bananominecraft.bananoeconomy.trackers.BukkitTaskTracker;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class BananoEconomyMain extends JavaPlugin
{
    private IDBConnector       db;
    private ConfigEngine       configEngine;
    private RPC rpc;
    private EconomyFuncs economyFuncs;
    private BananoWebSocket webSocket;

    /** Tracks every async task so they can all be cancelled in onDisable. */
    private final BukkitTaskTracker taskTracker = new BukkitTaskTracker();

    @Override
    public void onEnable()
    {
        getConfig().options().copyDefaults();
        saveDefaultConfig();

        // --- infrastructure (order matters: config before rpc before economy) ---
        this.db           = DBConnectorFactory.create(this);
        this.configEngine = new ConfigEngine(this);
        this.rpc          = new RPC(this, this.configEngine);
        this.economyFuncs = new EconomyFuncs(this, this.db, this.rpc, this.configEngine);
        this.webSocket    = new BananoWebSocket(this, this.rpc, this.configEngine);

        // --- event listeners ---
        getServer().getPluginManager().registerEvents(
                new OnJoin(this, this.economyFuncs, this.db, this.configEngine, this.webSocket, this.taskTracker), this);
        getServer().getPluginManager().registerEvents(
                new OnLeave(this, this.economyFuncs, this.webSocket), this);

        // --- commands ---
        getCommand("deposit").setExecutor(
                new DepositCommand(this, this.economyFuncs, this.configEngine, this.rpc));
        getCommand("nodeinfo").setExecutor(
                new NodeInfoCommand(this, this.rpc, this.taskTracker));
        getCommand("tip").setExecutor(
                new TipCommand(this, this.economyFuncs, this.configEngine, this.db, this.rpc, this.taskTracker));
        getCommand("withdraw").setExecutor(
                new WithdrawCommand(this, this.economyFuncs, this.rpc, this.configEngine, this.taskTracker));
        getCommand("balance").setExecutor(
                new BalanceCommand(this, this.economyFuncs, this.taskTracker));
        getCommand("showofflinetips").setExecutor(
                new ShowOfflineTransactionsCommand(this, this.economyFuncs, this.db, this.configEngine, this.taskTracker));
        getCommand("history").setExecutor(
                new TransactionHistoryCommand(this, this.economyFuncs, this.taskTracker, this.configEngine));
        getCommand("bc").setExecutor(
                new AdminCommand(this, this.configEngine, this.economyFuncs, this.db, this.rpc, this.webSocket, this.taskTracker));

        // --- tab completers ---
        getCommand("tip").setTabCompleter(new TipTabCompleter(this.configEngine));
        getCommand("withdraw").setTabCompleter(new WithdrawTabCompleter());
        getCommand("deposit").setTabCompleter(new DepositTabCompleter());
        getCommand("history").setTabCompleter(new TransactionHistoryTabCompleter(this.configEngine));
        getCommand("bc").setTabCompleter(new AdminCommandTabCompleter(this.configEngine, this.db));

        getLogger().info("Commands registered.");

        setupVault();
        getLogger().info("Economy setup complete.");

        setupWallet();
        getLogger().info("Wallet setup complete.");

        this.webSocket.connect();
        getLogger().info("WebSocket connecting to " + this.configEngine.getWebsocketUrl());
    }

    @Override
    public void onDisable()
    {
        if (this.webSocket != null)
        {
            this.webSocket.shutdown();
        }

        // Cancel every tracked async task, then cancel any that slipped through
        // (e.g. the JsonDBConnector repeating save task).
        taskTracker.cancelAll();
        Bukkit.getServer().getScheduler().cancelTasks(this);

        if (this.db != null)
        {
            this.db.close();
        }
    }

    // -------------------------------------------------------------------------
    // Private setup helpers
    // -------------------------------------------------------------------------

    private void setupVault()
    {
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") != null)
        {
            Bukkit.getServer().getServicesManager().register(
                    Economy.class,
                    new VaultConnector(this, this.economyFuncs, this.rpc),
                    this,
                    ServicePriority.Highest);
        }
    }

    private void setupWallet()
    {
        if (!this.rpc.wallet_exists())
        {
            getLogger().warning("Master wallet not found — creating wallet now.");
            this.rpc.walletCreate(); // persists walletID via ConfigEngine.save()

            String masterWallet = this.rpc.accountCreate(0);
            getLogger().info("Master wallet created: " + masterWallet);

            // Persist through ConfigEngine so the value is consistent with all
            // other config writes and is included in the next save() call.
            this.configEngine.setMasterWallet(masterWallet);
            this.configEngine.save();
        }
        else
        {
            getLogger().info("Master wallet verified.");
        }
    }
}
