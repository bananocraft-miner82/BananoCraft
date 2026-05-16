package banano.bananominecraft.bananoeconomy.events;

import banano.bananominecraft.bananoeconomy.io.BananoWebSocket;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import banano.bananominecraft.bananoeconomy.classes.MessageGenerator;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class OnJoin implements Listener
{
    private final Plugin plugin;
    private final EconomyFuncs economyFuncs;
    private final IDBConnector db;
    private final ConfigEngine configEngine;
    private final BananoWebSocket webSocket;
    private final TaskTracker taskTracker;

    public OnJoin(Plugin plugin, EconomyFuncs economyFuncs, IDBConnector db,
                  ConfigEngine configEngine, BananoWebSocket webSocket, TaskTracker taskTracker)
    {
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
        this.db = db;
        this.configEngine = configEngine;
        this.webSocket = webSocket;
        this.taskTracker = taskTracker;
    }

    @EventHandler
    public void onJoinServer(PlayerJoinEvent event)
    {
        Player player = event.getPlayer();

        TextComponent welcomeMessage = new TextComponent("This server is running BananoEconomy!");
        welcomeMessage.setColor(ChatColor.YELLOW);
        welcomeMessage.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/Kirby1997/BananoCraft"));
        welcomeMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("See the code!").create()));
        player.spigot().sendMessage(welcomeMessage);

        BukkitTask task = new BukkitRunnable()
        {
            @Override
            public void run()
            {
                if (!plugin.isEnabled())
                {
                    return;
                }

                try
                {
                    plugin.getLogger().info("Creating account for player: " + player.getName());

                    if (!economyFuncs.accountCreate(player))
                    {
                        player.sendMessage(ChatColor.RED + "Your BananoEconomy wallet could not be configured! The node or database may be unavailable. Please try again by logging out and logging back in later.");
                    }
                    else
                    {
                        // Account is ready — subscribe to real-time deposit notifications
                        String wallet = economyFuncs.getWallet(player);
                        webSocket.watchAccount(wallet, player.getUniqueId());
                    }
                }
                catch (Exception ex)
                {
                    player.sendMessage(org.bukkit.ChatColor.RED + "There was an error configuring your BananoEconomy wallet!");
                }

                if (configEngine.getEnableOfflinePayment())
                {
                    try
                    {
                        double totalAmount = db.getOfflinePaymentsTotal(player);

                        if (totalAmount > 0)
                        {
                            player.sendMessage(ChatColor.GOLD + ChatColor.BOLD.toString()
                                    + "You have received transactions totalling " + totalAmount
                                    + " Banano while you were offline!");

                            player.spigot().sendMessage(MessageGenerator.generateClickToViewOfflinePayments());
                        }
                    }
                    catch (Exception ex)
                    {
                        plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to check offline payments on join.", ex);
                    }
                }
            }
        }.runTaskAsynchronously(this.plugin);

        taskTracker.track(task);
    }
}
