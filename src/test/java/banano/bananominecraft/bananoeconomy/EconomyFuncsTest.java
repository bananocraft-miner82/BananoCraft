package banano.bananominecraft.bananoeconomy;

import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.classes.TransactionRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.enums.TransactionDirection;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.io.RPC;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
class EconomyFuncsTest {

    private static final String WALLET = "ban_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";
    private static final String MASTER_WALLET = "ban_1masterwalletaddressxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx1111";

    @Mock private IDBConnector db;
    @Mock private Plugin plugin;
    @Mock private RPC rpc;
    @Mock private Player player;
    @Mock private OfflinePlayer offlinePlayer;
    @Mock private ConfigEngine configEngine;

    private EconomyFuncs economyFuncs;

    @BeforeEach
    void setUp() {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        economyFuncs = new EconomyFuncs(plugin, db, rpc, configEngine);
    }

    // --- getBalance(Player) ---

    @Test
    void getBalance_player_returnsZero_whenNoRecord() {
        when(db.getPlayerRecord(player)).thenReturn(null);
        assertEquals(0d, economyFuncs.getBalance(player));
    }

    @Test
    void getBalance_player_returnsZero_whenFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, true);
        when(db.getPlayerRecord(player)).thenReturn(record);
        assertEquals(0d, economyFuncs.getBalance(player));
    }

    @Test
    void getBalance_player_returnsZero_whenNullWallet() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", null, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        assertEquals(0d, economyFuncs.getBalance(player));
    }

    @Test
    void getBalance_player_delegatesToRPC() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(42.0);

        assertEquals(42.0, economyFuncs.getBalance(player));
    }

    // --- getBalance(OfflinePlayer) ---

    @Test
    void getBalance_offlinePlayer_returnsZero_whenNoRecord() {
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(null);
        assertEquals(0d, economyFuncs.getBalance(offlinePlayer));
    }

    @Test
    void getBalance_offlinePlayer_returnsZero_whenFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, true);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        assertEquals(0d, economyFuncs.getBalance(offlinePlayer));
    }

    @Test
    void getBalance_offlinePlayer_delegatesToRPC() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(7.5);

        assertEquals(7.5, economyFuncs.getBalance(offlinePlayer));
    }

    // --- isFrozen(Player) ---

    @Test
    void isFrozen_returnsFalse_whenNoRecord() {
        when(db.getPlayerRecord(player)).thenReturn(null);
        assertFalse(economyFuncs.isFrozen(player));
    }

    @Test
    void isFrozen_returnsTrue_whenRecordIsFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, true);
        when(db.getPlayerRecord(player)).thenReturn(record);
        assertTrue(economyFuncs.isFrozen(player));
    }

    @Test
    void isFrozen_returnsFalse_whenRecordIsNotFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        assertFalse(economyFuncs.isFrozen(player));
    }

    // --- hasWallet(Player) ---

    @Test
    void hasWallet_player_returnsFalse_whenNoRecord() {
        when(db.getPlayerRecord(player)).thenReturn(null);
        assertFalse(economyFuncs.hasWallet(player));
    }

    @Test
    void hasWallet_player_returnsFalse_whenNullWallet() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", null, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        assertFalse(economyFuncs.hasWallet(player));
    }

    @Test
    void hasWallet_player_returnsTrue_whenWalletStartsWithBan() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        assertTrue(economyFuncs.hasWallet(player));
    }

    // --- removeBalanceFP(OfflinePlayer, double) ---

    @Test
    void removeBalanceFP_offline_returnsFalse_whenNoRecord() {
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(null);
        assertFalse(economyFuncs.removeBalanceFP(offlinePlayer, 5.0));
    }

    @Test
    void removeBalanceFP_offline_returnsFalse_whenFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, true);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        assertFalse(economyFuncs.removeBalanceFP(offlinePlayer, 5.0));
    }

    @Test
    void removeBalanceFP_offline_returnsFalse_whenInsufficientBalance() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(3.0);

        assertFalse(economyFuncs.removeBalanceFP(offlinePlayer, 5.0));
    }

    @Test
    void removeBalanceFP_offline_returnsTrue_onSuccessfulTransaction() throws Exception {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(10.0);
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.sendTransaction(WALLET, MASTER_WALLET, 5.0)).thenReturn("block123");

        assertTrue(economyFuncs.removeBalanceFP(offlinePlayer, 5.0));
    }

    @Test
    void removeBalanceFP_offline_returnsFalse_whenTransactionThrows() throws Exception {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(10.0);
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.sendTransaction(WALLET, MASTER_WALLET, 5.0)).thenThrow(new TransactionError("Node error"));

        assertFalse(economyFuncs.removeBalanceFP(offlinePlayer, 5.0));
    }

    // --- addBalanceTP(OfflinePlayer, double) ---

    @Test
    void addBalanceTP_offline_returnsFalse_whenNoRecord() {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(null);

        assertFalse(economyFuncs.addBalanceTP(offlinePlayer, 5.0));
    }

    @Test
    void addBalanceTP_offline_returnsFalse_whenServerInsufficientFunds() {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(2.0);
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);

        assertFalse(economyFuncs.addBalanceTP(offlinePlayer, 5.0));
    }

    @Test
    void addBalanceTP_offline_returnsTrue_onSuccessfulTransaction() throws Exception {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(rpc.sendTransaction(MASTER_WALLET, WALLET, 5.0)).thenReturn("block456");

        assertTrue(economyFuncs.addBalanceTP(offlinePlayer, 5.0));
    }

    @Test
    void addBalanceTP_offline_returnsFalse_whenFrozen() {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, true);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);

        assertFalse(economyFuncs.addBalanceTP(offlinePlayer, 5.0));
    }

    @Test
    void addBalanceTP_offline_returnsFalse_whenTransactionThrows() throws Exception {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(rpc.sendTransaction(MASTER_WALLET, WALLET, 5.0)).thenThrow(new TransactionError("Node error"));

        assertFalse(economyFuncs.addBalanceTP(offlinePlayer, 5.0));
    }

    // --- getBalance(OfflinePlayer) missing case ---

    @Test
    void getBalance_offlinePlayer_returnsZero_whenNullWallet() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", null, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        assertEquals(0d, economyFuncs.getBalance(offlinePlayer));
    }

    // --- hasWallet(OfflinePlayer) ---

    @Test
    void hasWallet_offlinePlayer_returnsFalse_whenNoRecord() {
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(null);
        assertFalse(economyFuncs.hasWallet(offlinePlayer));
    }

    @Test
    void hasWallet_offlinePlayer_returnsFalse_whenNullWallet() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", null, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        assertFalse(economyFuncs.hasWallet(offlinePlayer));
    }

    @Test
    void hasWallet_offlinePlayer_returnsTrue_whenValidWallet() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        assertTrue(economyFuncs.hasWallet(offlinePlayer));
    }

    // --- getWallet(Player) ---

    @Test
    void getWallet_returnsEmpty_whenNoRecord() {
        when(db.getPlayerRecord(player)).thenReturn(null);
        assertEquals("", economyFuncs.getWallet(player));
    }

    @Test
    void getWallet_returnsWalletAddress_whenRecordExists() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        assertEquals(WALLET, economyFuncs.getWallet(player));
    }

    // --- removeBalanceFP(Player, double) ---

    @Test
    void removeBalanceFP_player_returnsFalse_whenNoRecord() {
        when(db.getPlayerRecord(player)).thenReturn(null);
        assertFalse(economyFuncs.removeBalanceFP(player, 5.0));
    }

    @Test
    void removeBalanceFP_player_returnsFalse_whenFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, true);
        when(db.getPlayerRecord(player)).thenReturn(record);
        // getBalance also calls getPlayerRecord; frozen → returns 0.0 from getBalance
        assertFalse(economyFuncs.removeBalanceFP(player, 5.0));
    }

    @Test
    void removeBalanceFP_player_returnsFalse_whenInsufficientBalance() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(3.0);  // balance < amount

        assertFalse(economyFuncs.removeBalanceFP(player, 5.0));
    }

    @Test
    void removeBalanceFP_player_returnsTrue_onSuccessfulTransaction() throws Exception {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(10.0);
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.sendTransaction(WALLET, MASTER_WALLET, 5.0)).thenReturn("blockABC");

        assertTrue(economyFuncs.removeBalanceFP(player, 5.0));
    }

    @Test
    void removeBalanceFP_player_returnsFalse_whenTransactionThrows() throws Exception {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(10.0);
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.sendTransaction(WALLET, MASTER_WALLET, 5.0)).thenThrow(new TransactionError("Network error"));

        assertFalse(economyFuncs.removeBalanceFP(player, 5.0));
    }

    // --- addBalanceTP(Player, double) ---

    @Test
    void addBalanceTP_player_returnsFalse_whenNoRecord() {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        when(db.getPlayerRecord(player)).thenReturn(null);

        assertFalse(economyFuncs.addBalanceTP(player, 5.0));
    }

    @Test
    void addBalanceTP_player_returnsFalse_whenServerInsufficientFunds() {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(2.0);  // less than 5.0
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);

        assertFalse(economyFuncs.addBalanceTP(player, 5.0));
    }

    @Test
    void addBalanceTP_player_returnsFalse_whenFrozen() {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        // Record is frozen; isFrozen() will call db.getPlayerRecord(player) a second time
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, true);
        when(db.getPlayerRecord(player)).thenReturn(record);

        assertFalse(economyFuncs.addBalanceTP(player, 5.0));
    }

    @Test
    void addBalanceTP_player_returnsTrue_onSuccessfulTransaction() throws Exception {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(rpc.sendTransaction(MASTER_WALLET, WALLET, 5.0)).thenReturn("blockDEF");

        assertTrue(economyFuncs.addBalanceTP(player, 5.0));
    }

    @Test
    void addBalanceTP_player_returnsFalse_whenTransactionThrows() throws Exception {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(rpc.sendTransaction(MASTER_WALLET, WALLET, 5.0)).thenThrow(new TransactionError("Timeout"));

        assertFalse(economyFuncs.addBalanceTP(player, 5.0));
    }

    // --- getTransactionHistory(UUID, int) ---

    @Test
    void getTransactionHistory_returnsNull_whenNoRecord() {
        UUID id = UUID.randomUUID();
        when(db.getPlayerRecord(id)).thenReturn(null);
        when(configEngine.getMaximumTransactionHistoryCount()).thenReturn(100);

        assertNull(economyFuncs.getTransactionHistory(id, 10));
    }

    @Test
    void getTransactionHistory_returnsNull_whenFrozen() {
        UUID id = UUID.randomUUID();
        PlayerRecord record = new PlayerRecord(id.toString(), "Alice", WALLET, true);
        when(db.getPlayerRecord(id)).thenReturn(record);
        when(configEngine.getMaximumTransactionHistoryCount()).thenReturn(100);

        assertNull(economyFuncs.getTransactionHistory(id, 10));
    }

    @Test
    void getTransactionHistory_returnsNull_whenCountNegative() {
        UUID id = UUID.randomUUID();
        PlayerRecord record = new PlayerRecord(id.toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(id)).thenReturn(record);
        when(configEngine.getMaximumTransactionHistoryCount()).thenReturn(100);

        assertNull(economyFuncs.getTransactionHistory(id, -1));
    }

    @Test
    void getTransactionHistory_returnsNull_whenCountExceedsMax() {
        UUID id = UUID.randomUUID();
        PlayerRecord record = new PlayerRecord(id.toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(id)).thenReturn(record);
        when(configEngine.getMaximumTransactionHistoryCount()).thenReturn(50);

        assertNull(economyFuncs.getTransactionHistory(id, 51));
    }

    @Test
    void getTransactionHistory_delegatesToRPC() {
        UUID id = UUID.randomUUID();
        PlayerRecord record = new PlayerRecord(id.toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(id)).thenReturn(record);
        when(configEngine.getMaximumTransactionHistoryCount()).thenReturn(100);

        List<TransactionRecord> expected = List.of(
                new TransactionRecord(LocalDateTime.now(), WALLET, TransactionDirection.Receive, 5.0, "abc123def456abc1", false)
        );
        when(rpc.getTransactionHistory(WALLET, 10)).thenReturn(expected);

        List<TransactionRecord> result = economyFuncs.getTransactionHistory(id, 10);
        assertEquals(expected, result);
    }

    @Test
    void getTransactionHistory_returnsNull_whenRPCThrows() {
        UUID id = UUID.randomUUID();
        PlayerRecord record = new PlayerRecord(id.toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(id)).thenReturn(record);
        when(configEngine.getMaximumTransactionHistoryCount()).thenReturn(100);
        when(rpc.getTransactionHistory(WALLET, 10)).thenThrow(new RuntimeException("Node unreachable"));

        assertNull(economyFuncs.getTransactionHistory(id, 10));
    }

    // --- freezePlayer(Player) ---

    @Test
    void freezePlayer_player_returnsFalse_whenNoRecord() {
        when(db.getPlayerRecord(player)).thenReturn(null);
        assertFalse(economyFuncs.freezePlayer(player));
    }

    @Test
    void freezePlayer_player_returnsTrue_andMarksRecordFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);

        assertTrue(economyFuncs.freezePlayer(player));
        assertTrue(record.isFrozen(), "Record should be marked frozen after call");
        verify(db).updatePlayerRecord(record);
    }

    // --- freezePlayer(OfflinePlayer) ---

    @Test
    void freezePlayer_offlinePlayer_returnsFalse_whenNoRecord() {
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(null);
        assertFalse(economyFuncs.freezePlayer(offlinePlayer));
    }

    @Test
    void freezePlayer_offlinePlayer_returnsTrue_andMarksRecordFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);

        assertTrue(economyFuncs.freezePlayer(offlinePlayer));
        assertTrue(record.isFrozen(), "Record should be marked frozen after call");
        verify(db).updatePlayerRecord(record);
    }

    // --- unfreezePlayer(Player) ---

    @Test
    void unfreezePlayer_player_returnsFalse_whenNoRecord() {
        when(db.getPlayerRecord(player)).thenReturn(null);
        assertFalse(economyFuncs.unfreezePlayer(player));
    }

    @Test
    void unfreezePlayer_player_returnsTrue_andClearsRecordFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, true);
        when(db.getPlayerRecord(player)).thenReturn(record);

        assertTrue(economyFuncs.unfreezePlayer(player));
        assertFalse(record.isFrozen(), "Record should no longer be frozen after call");
        verify(db).updatePlayerRecord(record);
    }

    // --- unfreezePlayer(OfflinePlayer) ---

    @Test
    void unfreezePlayer_offlinePlayer_returnsFalse_whenNoRecord() {
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(null);
        assertFalse(economyFuncs.unfreezePlayer(offlinePlayer));
    }

    @Test
    void unfreezePlayer_offlinePlayer_returnsTrue_andClearsRecordFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, true);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);

        assertTrue(economyFuncs.unfreezePlayer(offlinePlayer));
        assertFalse(record.isFrozen(), "Record should no longer be frozen after call");
        verify(db).updatePlayerRecord(record);
    }

    // --- accountExists(Player) ---

    @Test
    void accountExists_returnsTrue_whenDBHasRecord() {
        when(db.hasPlayerRecord(player)).thenReturn(true);
        assertTrue(economyFuncs.accountExists(player));
    }

    @Test
    void accountExists_returnsFalse_whenDBHasNoRecord() {
        when(db.hasPlayerRecord(player)).thenReturn(false);
        assertFalse(economyFuncs.accountExists(player));
    }
}
