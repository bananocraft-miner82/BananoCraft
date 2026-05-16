package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import banano.bananominecraft.bananoeconomy.classes.MessageGenerator;
import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

public class TipCommand extends BaseCommand implements CommandExecutor
{
    private static final String ARG_ALL = "all";

    private final JavaPlugin plugin;
    private final IDBConnector db;
    private final EconomyFuncs economyFuncs;
    private final ConfigEngine configEngine;
    private final RPC rpc;
    private final TaskTracker taskTracker;

    public TipCommand(final JavaPlugin plugin, EconomyFuncs economyFuncs, ConfigEngine configEngine,
                      IDBConnector db, RPC rpc, TaskTracker taskTracker)
    {
        super(plugin.getLogger());
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
        this.configEngine = configEngine;
        this.db = db;
        this.rpc = rpc;
        this.taskTracker = taskTracker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player))
        {
            return false;
        }

        final Player player = (Player) sender;
        final PlayerRecord senderRecord = this.db.getPlayerRecord(player);

        if (senderRecord == null)
        {
            SendMessage(player, "Your wallet has not been set up yet. Please try again in a moment.", ChatColor.RED);
            return false;
        }

        if (args.length < 2)
        {
            SendMessage(player, "You need to enter an amount to send and a player to send to:", ChatColor.RED);
            SendMessage(player, "/tip [amount] [playername] [optional message]", ChatColor.RED);
            return false;
        }

        final String message = generateMessageString(args);

        final String sAmount = args[0];
        final double amount;

        try
        {
            if (sAmount.equalsIgnoreCase(ARG_ALL))
            {
                amount = rpc.getBalance(senderRecord.getWallet());
            }
            else
            {
                amount = Double.parseDouble(sAmount);
            }

            if (amount <= 0)
            {
                SendMessage(player, String.format("Amount ('%s') has to be greater than 0", sAmount), ChatColor.RED);
                return false;
            }
        }
        catch (final Exception e)
        {
            SendMessage(player, String.format("Amount ('%s') is not a number greater than 0", sAmount), ChatColor.RED);
            return false;
        }

        final String targetPlayerName = args[1];
        final PlayerRecord target = findPlayer(this.db, this.configEngine, targetPlayerName);

        if (target != null && senderRecord.getPlayerUUID().equals(target.getPlayerUUID()))
        {
            SendMessage(player, "You cannot tip yourself", ChatColor.RED);
            return false;
        }
        else if (target == null)
        {
            SendMessage(player, "Player needs to be online for you to tip them, or a unique player could not be identified by name,", ChatColor.RED);
            return false;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            if (!plugin.isEnabled())
            {
                return;
            }

            if (senderRecord.isFrozen())
            {
                SendMessage(player, "You cannot access your wallet because it is frozen!", ChatColor.RED);
                return;
            }

            if (target.isFrozen())
            {
                SendMessage(player, "You cannot tip " + targetPlayerName + " because their wallet is frozen!", ChatColor.RED);
                return;
            }

            SendMessage(player, "Tipping " + target.getPlayerName() + " with " + amount + " bans.", ChatColor.WHITE);

            final String sWallet = senderRecord.getWallet();
            final String tWallet = target.getWallet();
            final String blockHash;

            try
            {
                blockHash = rpc.sendTransaction(sWallet, tWallet, amount);
            }
            catch (final TransactionError error)
            {
                SendMessage(player, String.format("Tip of %s to %s failed with: %s", sAmount, targetPlayerName, error.getUserError()), ChatColor.RED);
                return;
            }

            try
            {
                final URL blockURL = new URL(new URL(this.configEngine.getExplorerBlock()) + blockHash);
                final TextComponent blocklink = new TextComponent("Click me to view the transaction in the block explorer");

                blocklink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, blockURL.toString()));
                blocklink.setUnderlined(true);

                MessageGenerator.generateTipSenderMessage(target.getPlayerName(), amount, blockHash, message);
                player.spigot().sendMessage(MessageGenerator.generateBlockExplorerLink(this.configEngine, blockHash));

                Player targetPlayer = Bukkit.getPlayer(UUID.fromString(target.getPlayerUUID()));

                if (targetPlayer != null && targetPlayer.isOnline())
                {
                    targetPlayer.spigot().sendMessage(MessageGenerator.generateTipReceiverMessage(player.getDisplayName(), amount, blockHash, message));
                    targetPlayer.spigot().sendMessage(blocklink);
                }
                else
                {
                    // Generate an offline transaction record to tell them when they next log in
                    OfflinePaymentRecord paymentRecord = new OfflinePaymentRecord(
                            UUID.fromString(target.getPlayerUUID()),
                            player.getDisplayName(),
                            amount,
                            blockHash,
                            LocalDateTime.now(),
                            message);

                    this.db.saveOfflinePayment(paymentRecord);
                }
            }
            catch (Exception e)
            {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Tip command error.", e);
            }
        });

        taskTracker.track(task);
        return true;
    }

    private String generateMessageString(String[] args)
    {
        if (args.length <= 2)
        {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, 2, args.length));
    }
}
