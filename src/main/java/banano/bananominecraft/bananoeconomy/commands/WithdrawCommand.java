package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.net.URL;
import java.util.logging.Level;

public class WithdrawCommand implements CommandExecutor
{
    private static final String ARG_ALL = "all";

    private final JavaPlugin plugin;
    private final EconomyFuncs economyFuncs;
    private final RPC rpc;
    private final ConfigEngine configEngine;
    private final TaskTracker taskTracker;

    public WithdrawCommand(final JavaPlugin plugin, EconomyFuncs economyFuncs, RPC rpc,
                           ConfigEngine configEngine, TaskTracker taskTracker)
    {
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
        this.rpc = rpc;
        this.configEngine = configEngine;
        this.taskTracker = taskTracker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        BukkitTask task = new BukkitRunnable()
        {
            @Override
            public void run()
            {
                if (!plugin.isEnabled())
                {
                    return;
                }

                if (sender instanceof Player)
                {
                    Player player = (Player) sender;

                    if (economyFuncs.isFrozen(player))
                    {
                        player.sendMessage("Your account has been frozen");
                        return;
                    }

                    String playerWallet = economyFuncs.getWallet(player);

                    try
                    {
                        double amount;

                        if (args[0].equalsIgnoreCase(ARG_ALL))
                        {
                            amount = rpc.getBalance(playerWallet);
                        }
                        else
                        {
                            amount = Double.parseDouble(args[0]);
                        }

                        if (amount <= 0)
                        {
                            player.sendMessage("Amount has to be greater than 0");
                            return;
                        }

                        String amountStr = Double.toString(amount);

                        // args[0] = amount, args[1] = destination address
                        if (args.length == 2)
                        {
                            final String withdrawAddr = args[1];
                            final String blockHash;

                            try
                            {
                                blockHash = rpc.sendTransaction(playerWallet, withdrawAddr, amount);
                            }
                            catch (final TransactionError error)
                            {
                                player.sendMessage(String.format("/withdraw %f %s failed with: %s",
                                        amount, withdrawAddr, error.getUserError()));
                                return;
                            }

                            player.sendMessage(blockHash);

                            try
                            {
                                final URL blockURL = new URL(configEngine.getExplorerBlock() + blockHash);

                                player.spigot().sendMessage(new ComponentBuilder("You have sent ")
                                        .color(ChatColor.YELLOW)
                                        .append(amountStr).color(ChatColor.WHITE).bold(true)
                                        .append(" to ").color(ChatColor.YELLOW)
                                        .append(withdrawAddr).color(ChatColor.WHITE).bold(true)
                                        .append(" with block ID : ")
                                        .append(blockHash).color(ChatColor.YELLOW).bold(true)
                                        .create());

                                TextComponent blocklink = new TextComponent("Click me to view the transaction in the block explorer");
                                blocklink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, blockURL.toString()));
                                blocklink.setUnderlined(true);
                                player.spigot().sendMessage(blocklink);
                            }
                            catch (Exception e)
                            {
                                plugin.getLogger().log(Level.WARNING, "Failed to build block explorer link.", e);
                            }
                        }
                        else
                        {
                            throw new Exception();
                        }
                    }
                    catch (Exception e)
                    {
                        player.sendMessage("Usage: /withdraw [amount|all] [ban_address]");
                    }
                }
            }
        }.runTaskAsynchronously(plugin);

        taskTracker.track(task);
        return true;
    }
}
