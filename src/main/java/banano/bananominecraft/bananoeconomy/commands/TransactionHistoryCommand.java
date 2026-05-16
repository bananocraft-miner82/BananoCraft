package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.enums.TransactionDirection;
import banano.bananominecraft.bananoeconomy.helpers.StringHelper;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import banano.bananominecraft.bananoeconomy.classes.TransactionRecord;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.logging.Level;

public class TransactionHistoryCommand implements CommandExecutor
{
    public static final String ARG_ALL = "all";

    private final Plugin plugin;
    private final EconomyFuncs economyFuncs;
    private final TaskTracker taskTracker;
    private final ConfigEngine configEngine;

    public TransactionHistoryCommand(Plugin plugin, EconomyFuncs economyFuncs, TaskTracker taskTracker, ConfigEngine configEngine)
    {
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
        this.taskTracker = taskTracker;
        this.configEngine = configEngine;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (sender instanceof Player player)
        {
            int recordCount = ConfigEngine.DEFAULT_HISTORY_TRANSACTIONS;
            int maxTransactions = configEngine.getMaximumTransactionHistoryCount();

            if (args.length > 0)
            {
                if (args[0].equalsIgnoreCase(ARG_ALL))
                {
                    recordCount = maxTransactions;
                }
                else
                {
                    try
                    {
                        recordCount = Integer.parseInt(args[0]);
                    } catch (NumberFormatException e)
                    {
                        player.sendMessage(ChatColor.RED + "Invalid record count! Usage: /bc history <record count>");
                        return true;
                    }
                }
            }

            if (recordCount < ConfigEngine.MIN_HISTORY_TRANSACTIONS)
            {
                player.sendMessage(ChatColor.RED +  "Record Count must be greater than " + ConfigEngine.MIN_HISTORY_TRANSACTIONS + "! Usage: /bc history <record count>");
                return true;
            }

            if (recordCount > maxTransactions)
            {
                player.sendMessage(ChatColor.RED +  "Record Count must be less than " + maxTransactions + "! Usage: /bc history <record count>");
                return true;
            }

            int finalRecordCount = recordCount;
            BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            {
                if (!plugin.isEnabled())
                {
                    return;
                }

                try
                {
                    final List<TransactionRecord> records = economyFuncs.getTransactionHistory(player.getUniqueId(), finalRecordCount);

                    if (records != null
                          && !records.isEmpty())
                    {
                        player.sendMessage(ChatColor.GOLD + TransactionRecord.getHeaderString());

                        for (TransactionRecord record : records)
                        {
                            String displayAddress = StringHelper.left(record.address(), 15) + "..." + StringHelper.right(record.address(), 15);

                            player.spigot().sendMessage(record.toRecordString(this.configEngine));

                            if (record.direction() == TransactionDirection.Send)
                            {
                                player.sendMessage(ChatColor.YELLOW + "To:   " + displayAddress);
                            }
                            else
                            {
                                player.sendMessage(ChatColor.GREEN + "From: " + displayAddress);
                            }
                        }
                    }
                    else
                    {
                        player.sendMessage(ChatColor.YELLOW + "No records found!");
                    }
                }
                catch (Exception ex)
                {
                    plugin.getLogger().log(Level.WARNING, "Failed to retrieve history for player: " + player.getName(), ex);
                    player.sendMessage(ChatColor.RED + "An error occurred retrieving your transaction history! Please try again in a moment.");
                }
            });

            taskTracker.track(task);
        }
        else
        {
            plugin.getLogger().warning("Transaction History command issued by non-player sender: " + sender.getName());
        }

        return true;
    }
}
