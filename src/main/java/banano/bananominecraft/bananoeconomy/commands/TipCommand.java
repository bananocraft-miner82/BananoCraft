package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.classes.MessageGenerator;
import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
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
    private final I18n i18n;
    private final MessageGenerator messageGenerator;

    public TipCommand(final JavaPlugin plugin, EconomyFuncs economyFuncs, ConfigEngine configEngine,
                      IDBConnector db, RPC rpc, TaskTracker taskTracker,
                      I18n i18n, MessageGenerator messageGenerator)
    {
        super(plugin.getLogger());
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
        this.configEngine = configEngine;
        this.db = db;
        this.rpc = rpc;
        this.taskTracker = taskTracker;
        this.i18n = i18n;
        this.messageGenerator = messageGenerator;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player player))
        {
            return false;
        }

        final Locale locale = I18n.parseMinecraftLocale(player.getLocale());
        final PlayerRecord senderRecord = this.db.getPlayerRecord(player);

        if (senderRecord == null)
        {
            SendMessage(player, i18n.get(locale, "tip.wallet_not_setup"), ChatColor.RED);
            return false;
        }

        if (args.length < 2)
        {
            SendMessage(player, i18n.get(locale, "tip.usage"),        ChatColor.RED);
            SendMessage(player, i18n.get(locale, "tip.usage_format"), ChatColor.RED);
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
                SendMessage(player, i18n.get(locale, "tip.amount_not_positive", sAmount), ChatColor.RED);
                return false;
            }
        }
        catch (final Exception e)
        {
            SendMessage(player, i18n.get(locale, "tip.amount_not_number", sAmount), ChatColor.RED);
            return false;
        }

        final String targetPlayerName = args[1];
        final PlayerRecord target = findPlayer(this.db, this.configEngine, targetPlayerName);

        if (target != null && senderRecord.getPlayerUUID().equals(target.getPlayerUUID()))
        {
            SendMessage(player, i18n.get(locale, "tip.self"), ChatColor.RED);
            return false;
        }
        else if (target == null)
        {
            SendMessage(player, i18n.get(locale, "tip.player_not_found"), ChatColor.RED);
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
                SendMessage(player, i18n.get(locale, "tip.sender_frozen"), ChatColor.RED);
                return;
            }

            if (target.isFrozen())
            {
                SendMessage(player, i18n.get(locale, "tip.target_frozen", targetPlayerName), ChatColor.RED);
                return;
            }

            SendMessage(player, i18n.get(locale, "tip.sending", target.getPlayerName(), amount), ChatColor.WHITE);

            final String sWallet  = senderRecord.getWallet();
            final String tWallet  = target.getWallet();
            final String blockHash;

            try
            {
                blockHash = rpc.sendTransaction(sWallet, tWallet, amount);
            }
            catch (final TransactionError error)
            {
                SendMessage(player, i18n.get(locale, "tip.failed", sAmount, targetPlayerName, error.getUserError()), ChatColor.RED);
                return;
            }

            try
            {
                player.spigot().sendMessage(messageGenerator.generateTipSenderMessage(locale, target.getPlayerName(), amount, blockHash, message));
                player.spigot().sendMessage(messageGenerator.generateBlockExplorerLink(locale, blockHash));

                Player targetPlayer = Bukkit.getPlayer(UUID.fromString(target.getPlayerUUID()));

                if (targetPlayer != null && targetPlayer.isOnline())
                {
                    // Resolve the recipient's own locale for the message they see
                    Locale targetLocale = I18n.parseMinecraftLocale(targetPlayer.getLocale());
                    targetPlayer.spigot().sendMessage(messageGenerator.generateTipReceiverMessage(
                            targetLocale, player.getDisplayName(), amount, blockHash, message));
                    targetPlayer.spigot().sendMessage(messageGenerator.generateBlockExplorerLink(targetLocale, blockHash));
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
