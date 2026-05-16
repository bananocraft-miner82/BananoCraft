package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.text.DecimalFormat;

public class BalanceCommand implements CommandExecutor
{
    private final Plugin plugin;
    private final EconomyFuncs economyFuncs;
    private final TaskTracker taskTracker;

    public BalanceCommand(Plugin plugin, EconomyFuncs economyFuncs, TaskTracker taskTracker)
    {
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
        this.taskTracker = taskTracker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (sender instanceof Player)
        {
            Player player = (Player) sender;

            BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            {
                if (!plugin.isEnabled())
                {
                    return;
                }

                try
                {
                    final Double balance = economyFuncs.getBalance(player);
                    final DecimalFormat df = new DecimalFormat("#.##");

                    player.sendMessage(ChatColor.YELLOW + "Your current balance is: " + df.format(balance) + " bans");
                }
                catch (Exception ex)
                {
                    player.sendMessage(ChatColor.RED + "An error occurred retrieving your balance! Please try again in a moment.");
                }
            });

            taskTracker.track(task);
        }
        else
        {
            plugin.getLogger().warning("Balance command issued by non-player sender: " + sender.getName());
        }

        return false;
    }
}
