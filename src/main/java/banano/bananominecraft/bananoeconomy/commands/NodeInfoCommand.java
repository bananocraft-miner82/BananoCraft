package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.logging.Level;

public class NodeInfoCommand implements CommandExecutor
{
    private final JavaPlugin plugin;
    private final RPC rpc;
    private final TaskTracker taskTracker;

    public NodeInfoCommand(final JavaPlugin plugin, RPC rpc, TaskTracker taskTracker)
    {
        this.plugin = plugin;
        this.rpc = rpc;
        this.taskTracker = taskTracker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            if (!plugin.isEnabled())
            {
                return;
            }

            try
            {
                if (sender instanceof Player || sender instanceof ConsoleCommandSender)
                {
                    List<String> payload = rpc.getBlockCount();
                    String checked   = payload.get(0);
                    String unchecked = payload.get(1);
                    String msg0 = "The node IP is: " + rpc.getURL();
                    String msg1 = "Checked blocks: " + checked + " - Unchecked blocks: " + unchecked;
                    String msg2 = "The server wallet is " + rpc.getMasterWallet();
                    String msg3 = "It currently contains: " + rpc.getBalance(rpc.getMasterWallet());

                    if (sender instanceof Player player)
                    {
                        if (sender.isOp())
                        {
                            player.sendMessage(msg0);
                        }

                        player.sendMessage(msg1);
                        player.sendMessage(msg2);
                        player.sendMessage(msg3);
                    }
                    else if (sender instanceof ConsoleCommandSender console)
                    {
                        console.sendMessage(msg0);
                        console.sendMessage(msg1);
                        console.sendMessage(msg2);
                        console.sendMessage(msg3);
                    }
                }
            }
            catch (Exception e)
            {
                plugin.getLogger().log(Level.WARNING, "NodeInfo command failed.", e);
            }
        });

        taskTracker.track(task);
        return false;
    }
}
