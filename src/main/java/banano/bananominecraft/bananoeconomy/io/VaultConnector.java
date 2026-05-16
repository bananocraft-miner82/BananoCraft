package banano.bananominecraft.bananoeconomy.io;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class VaultConnector implements Economy
{
    private static final EconomyResponse NOT_ONLINE =
            new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player is not online.");

    private static final EconomyResponse NO_WALLET =
            new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player has no wallet.");

    private final Plugin plugin;
    private final EconomyFuncs economyFuncs;
    private final RPC rpc;

    public VaultConnector(Plugin plugin, EconomyFuncs economyFuncs, RPC rpc)
    {
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
        this.rpc = rpc;
    }

    @Override
    public boolean isEnabled()
    {
        return plugin.isEnabled();
    }

    @Override
    public String getName()
    {
        return "Banano.cc";
    }

    @Override
    public boolean hasBankSupport()
    {
        return false;
    }

    @Override
    public int fractionalDigits()
    {
        return 0;
    }

    @Override
    public String format(double amount)
    {
        return amount + " Bans";
    }

    @Override
    public String currencyNamePlural()
    {
        return "Bans";
    }

    @Override
    public String currencyNameSingular()
    {
        return "Ban";
    }

    // -------------------------------------------------------------------------
    // hasAccount
    // -------------------------------------------------------------------------

    @Override
    @Deprecated
    public boolean hasAccount(String playerName)
    {
        Player player = Bukkit.getServer().getPlayer(playerName);
        return player != null && economyFuncs.hasWallet(player);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player)
    {
        return economyFuncs.hasWallet(player);
    }

    @Override
    @Deprecated
    public boolean hasAccount(String playerName, String worldName)
    {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName)
    {
        return hasAccount(player);
    }

    // -------------------------------------------------------------------------
    // getBalance
    // -------------------------------------------------------------------------

    @Override
    @Deprecated
    public double getBalance(String playerName)
    {
        Player player = Bukkit.getServer().getPlayer(playerName);
        return player != null ? economyFuncs.getBalance(player) : 0;
    }

    @Override
    public double getBalance(OfflinePlayer offlinePlayer)
    {
        return economyFuncs.hasWallet(offlinePlayer) ? economyFuncs.getBalance(offlinePlayer) : 0;
    }

    @Override
    @Deprecated
    public double getBalance(String playerName, String world)
    {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer offlinePlayer, String world)
    {
        return getBalance(offlinePlayer);
    }

    // -------------------------------------------------------------------------
    // has
    // -------------------------------------------------------------------------

    @Override
    @Deprecated
    public boolean has(String playerName, double amount)
    {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer offlinePlayer, double amount)
    {
        return economyFuncs.hasWallet(offlinePlayer) && economyFuncs.getBalance(offlinePlayer) >= amount;
    }

    @Override
    @Deprecated
    public boolean has(String playerName, String worldName, double amount)
    {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer offlinePlayer, String worldName, double amount)
    {
        return has(offlinePlayer, amount);
    }

    // -------------------------------------------------------------------------
    // withdrawPlayer
    // -------------------------------------------------------------------------

    @Override
    @Deprecated
    public EconomyResponse withdrawPlayer(String playerName, double amount)
    {
        Player player = Bukkit.getServer().getPlayer(playerName);
        if (player == null)
        {
            return NOT_ONLINE;
        }
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer offlinePlayer, double amount)
    {
        if (!economyFuncs.hasWallet(offlinePlayer))
        {
            return NO_WALLET;
        }
        boolean success = economyFuncs.removeBalanceFP(offlinePlayer, amount);
        double newBalance = economyFuncs.getBalance(offlinePlayer);
        return new EconomyResponse(amount, newBalance,
                success ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                success ? null : "Insufficient funds.");
    }

    @Override
    @Deprecated
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount)
    {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount)
    {
        return withdrawPlayer(player, amount);
    }

    // -------------------------------------------------------------------------
    // depositPlayer
    // -------------------------------------------------------------------------

    @Override
    @Deprecated
    public EconomyResponse depositPlayer(String playerName, double amount)
    {
        Player player = Bukkit.getServer().getPlayer(playerName);
        if (player == null)
        {
            return NOT_ONLINE;
        }
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer offlinePlayer, double amount)
    {
        if (!economyFuncs.hasWallet(offlinePlayer))
        {
            return NO_WALLET;
        }
        boolean success = economyFuncs.addBalanceTP(offlinePlayer, amount);
        double newBalance = economyFuncs.getBalance(offlinePlayer);
        return new EconomyResponse(amount, newBalance,
                success ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                success ? null : "Insufficient funds.");
    }

    @Override
    @Deprecated
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount)
    {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount)
    {
        return depositPlayer(player, amount);
    }

    // -------------------------------------------------------------------------
    // Bank operations (unsupported — hasBankSupport() returns false)
    // -------------------------------------------------------------------------

    @Override public EconomyResponse createBank(String name, String player)          { return null; }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player)   { return null; }
    @Override public EconomyResponse deleteBank(String name)                         { return null; }
    @Override public EconomyResponse bankBalance(String name)                        { return null; }
    @Override public EconomyResponse bankHas(String name, double amount)             { return null; }
    @Override public EconomyResponse bankWithdraw(String name, double amount)        { return null; }
    @Override public EconomyResponse bankDeposit(String name, double amount)         { return null; }
    @Override public EconomyResponse isBankOwner(String name, String playerName)     { return null; }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player)  { return null; }
    @Override public EconomyResponse isBankMember(String name, String playerName)    { return null; }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return null; }
    @Override public List<String> getBanks()                                          { return null; }

    // -------------------------------------------------------------------------
    // createPlayerAccount (account creation is handled on join, not via Vault)
    // -------------------------------------------------------------------------

    @Override public boolean createPlayerAccount(String playerName)                          { return false; }
    @Override public boolean createPlayerAccount(OfflinePlayer player)                       { return false; }
    @Override public boolean createPlayerAccount(String playerName, String worldName)        { return false; }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String worldName)     { return false; }
}
