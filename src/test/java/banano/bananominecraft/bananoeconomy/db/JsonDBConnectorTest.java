package banano.bananominecraft.bananoeconomy.db;

import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonDBConnectorTest
{
    private ServerMock server;
    private PluginMock plugin;
    private JsonDBConnector db;
    private PlayerMock player;

    @BeforeEach
    void setUp()
    {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        db = new JsonDBConnector(plugin);
        player = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown()
    {
        db.close();
        MockBukkit.unmock();
    }

    // --- player records ---

    @Test
    void createPlayerRecord_isRetrievable()
    {
        PlayerRecord created = db.createPlayerRecord(player, "ban_wallet");

        assertNotNull(created);
        assertEquals("ban_wallet", created.getWallet());
        assertTrue(db.hasPlayerRecord(player));
        assertEquals("ban_wallet", db.getPlayerRecord(player).getWallet());
    }

    @Test
    void createPlayerRecord_isIdempotent()
    {
        PlayerRecord first  = db.createPlayerRecord(player, "ban_wallet");
        PlayerRecord second = db.createPlayerRecord(player, "ban_different");

        // The second call must not overwrite the first wallet assignment.
        assertEquals(first.getWallet(), second.getWallet());
        assertEquals("ban_wallet", db.getPlayerRecord(player).getWallet());
    }

    @Test
    void frozenListing_reflectsRecordState()
    {
        PlayerRecord record = db.createPlayerRecord(player, "ban_wallet");

        assertTrue(db.getUnfrozenPlayers().contains(record));
        assertFalse(db.getFrozenPlayers().contains(record));

        record.setFrozen(true);

        assertTrue(db.getFrozenPlayers().contains(record));
        assertFalse(db.getUnfrozenPlayers().contains(record));
    }

    @Test
    void playerRecord_persistsAcrossConnectorInstances()
    {
        db.createPlayerRecord(player, "ban_persisted");

        // A fresh connector over the same data folder must load the record from disk.
        JsonDBConnector reopened = new JsonDBConnector(plugin);
        PlayerRecord loaded = reopened.getPlayerRecord(player);

        assertNotNull(loaded);
        assertEquals("ban_persisted", loaded.getWallet());
        reopened.close();
    }

    // --- offline payments ---

    @Test
    void offlinePayments_saveGetTotalDelete()
    {
        OfflinePaymentRecord p1 = new OfflinePaymentRecord(
                player.getUniqueId(), "Bob", 1.5, "HASH1", LocalDateTime.now(), "");
        OfflinePaymentRecord p2 = new OfflinePaymentRecord(
                player.getUniqueId(), "Carol", 2.5, "HASH2", LocalDateTime.now(), "note");

        assertTrue(db.saveOfflinePayment(p1));
        assertTrue(db.saveOfflinePayment(p2));

        List<OfflinePaymentRecord> records = db.getOfflinePaymentRecords(player);
        assertEquals(2, records.size());
        assertEquals(4.0, db.getOfflinePaymentsTotal(player));

        db.deleteOfflinePaymentRecords(player);
        assertTrue(db.getOfflinePaymentRecords(player).isEmpty());
        assertEquals(0.0, db.getOfflinePaymentsTotal(player));
    }

    @Test
    void saveOfflinePayment_rejectsNull()
    {
        assertFalse(db.saveOfflinePayment(null));
    }

    @Test
    void offlinePayments_isolatedPerPlayer()
    {
        PlayerMock other = server.addPlayer("Bob");
        db.saveOfflinePayment(new OfflinePaymentRecord(
                player.getUniqueId(), "Carol", 9.0, "HASH", LocalDateTime.now(), ""));

        assertEquals(9.0, db.getOfflinePaymentsTotal(player));
        assertEquals(0.0, db.getOfflinePaymentsTotal(other));
    }

    @Test
    void offlinePaymentsTotal_nullSafe()
    {
        assertEquals(0.0, db.getOfflinePaymentsTotal(null));
        assertTrue(db.getOfflinePaymentRecords(null).isEmpty());
    }
}
