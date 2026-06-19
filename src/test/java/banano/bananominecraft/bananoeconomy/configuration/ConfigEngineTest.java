package banano.bananominecraft.bananoeconomy.configuration;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
class ConfigEngineTest
{
    private Plugin plugin;
    private FileConfiguration config;

    @BeforeEach
    void setUp()
    {
        plugin = mock(Plugin.class);
        config = mock(FileConfiguration.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getConfig()).thenReturn(config);

        // Default behaviour: every getString/getBoolean/getInt returns the supplied default.
        when(config.getString(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(config.getBoolean(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        when(config.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        when(config.contains("masterWallet")).thenReturn(true);
        when(config.getString("masterWallet", "")).thenReturn("");
    }

    @Test
    void loadsDefaults_whenConfigEmpty()
    {
        ConfigEngine engine = new ConfigEngine(plugin);

        assertEquals("", engine.getNodeAddress());
        assertEquals("100000000000000000000000000000", engine.getMultiplier());
        assertEquals("https://creeper.banano.cc/explorer/account/", engine.getExplorerAccount());
        assertEquals("https://creeper.banano.cc/explorer/block/", engine.getExplorerBlock());
        assertFalse(engine.getEnableOfflinePayment());
        assertEquals(10, engine.getMaximumTransactionHistoryCount());
        assertEquals("wss://ws.banano.trade", engine.getWebsocketUrl());
    }

    @Test
    void readsConfiguredValues()
    {
        when(config.getString("IP", "")).thenReturn("http://node:7072");
        when(config.getString("walletID", "")).thenReturn("WID");
        when(config.getBoolean("allowofflinepayment", false)).thenReturn(true);
        when(config.getInt("maximumtransactionhistorycount", 10)).thenReturn(25);
        when(config.getString("websocketUrl", "wss://ws.banano.trade")).thenReturn("wss://custom");

        ConfigEngine engine = new ConfigEngine(plugin);

        assertEquals("http://node:7072", engine.getNodeAddress());
        assertEquals("WID", engine.getWalletId());
        assertTrue(engine.getEnableOfflinePayment());
        assertEquals(25, engine.getMaximumTransactionHistoryCount());
        assertEquals("wss://custom", engine.getWebsocketUrl());
    }

    @Test
    void warnsWhenMasterWalletMissing()
    {
        when(config.contains("masterWallet")).thenReturn(false);

        ConfigEngine engine = new ConfigEngine(plugin);

        assertEquals("", engine.getMasterWallet());
    }

    @Test
    void setMaximumTransactionHistoryCount_acceptsInRange()
    {
        ConfigEngine engine = new ConfigEngine(plugin);

        engine.setMaximumTransactionHistoryCount(50);
        assertEquals(50, engine.getMaximumTransactionHistoryCount());

        engine.setMaximumTransactionHistoryCount(ConfigEngine.MIN_HISTORY_TRANSACTIONS);
        assertEquals(ConfigEngine.MIN_HISTORY_TRANSACTIONS, engine.getMaximumTransactionHistoryCount());

        engine.setMaximumTransactionHistoryCount(ConfigEngine.MAX_HISTORY_TRANSACTIONS);
        assertEquals(ConfigEngine.MAX_HISTORY_TRANSACTIONS, engine.getMaximumTransactionHistoryCount());
    }

    @Test
    void setMaximumTransactionHistoryCount_resetsToDefault_whenOutOfRange()
    {
        ConfigEngine engine = new ConfigEngine(plugin);

        engine.setMaximumTransactionHistoryCount(ConfigEngine.MAX_HISTORY_TRANSACTIONS + 1);
        assertEquals(ConfigEngine.DEFAULT_HISTORY_TRANSACTIONS, engine.getMaximumTransactionHistoryCount());

        engine.setMaximumTransactionHistoryCount(0);
        assertEquals(ConfigEngine.DEFAULT_HISTORY_TRANSACTIONS, engine.getMaximumTransactionHistoryCount());

        engine.setMaximumTransactionHistoryCount(-5);
        assertEquals(ConfigEngine.DEFAULT_HISTORY_TRANSACTIONS, engine.getMaximumTransactionHistoryCount());
    }

    @Test
    void save_writesMutableFields_andPersists()
    {
        ConfigEngine engine = new ConfigEngine(plugin);
        engine.setNodeAddress("http://new-node");
        engine.setWalletId("NEWWID");
        engine.setMasterWallet("ban_master");
        engine.setEnableOfflinePayment(true);
        engine.setWebsocketUrl("wss://new");

        assertTrue(engine.save());

        verify(config).set("IP", "http://new-node");
        verify(config).set("walletID", "NEWWID");
        verify(config).set("masterWallet", "ban_master");
        verify(config).set("allowofflinepayment", true);
        verify(config).set("websocketUrl", "wss://new");
        verify(plugin).saveConfig();
        verify(plugin).reloadConfig();
    }

    @Test
    void reload_reReadsConfig()
    {
        ConfigEngine engine = new ConfigEngine(plugin);
        when(config.getString("IP", "")).thenReturn("http://reloaded");

        engine.reload();

        verify(plugin).reloadConfig();
        assertEquals("http://reloaded", engine.getNodeAddress());
    }

    @Test
    void initialiseConfig_isNullSafe()
    {
        when(plugin.getConfig()).thenReturn(null);
        // Constructor must not throw even if the Bukkit config is unavailable.
        assertDoesNotThrow(() -> new ConfigEngine(plugin));
    }
}
