package id.rnggagib;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class ChickenHunt extends JavaPlugin {
  private static final Logger LOGGER = Logger.getLogger("chickenhunt");
  private RegionManager regionManager;
  private GameManager gameManager;
  private ItemManager itemManager;
  private PlayerStatsManager playerStatsManager;
  private final Map<UUID, Location> playerSelectionsPos1 = new HashMap<>();
  private final Map<UUID, Location> playerSelectionsPos2 = new HashMap<>();
  private String prefix;
  private static Economy econ = null;
  private GameScheduler gameScheduler;
  private ChickenHuntExpansion placeholderExpansion;

  @Override
  public void onEnable() {
    LOGGER.info("ChickenHunt is enabling...");

    saveDefaultConfig();
    this.prefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.prefix", "&e[ChickenHunt] &r"));
    this.regionManager = new RegionManager(this);
    this.gameManager = new GameManager(this);
    this.itemManager = new ItemManager(this);
    this.playerStatsManager = new PlayerStatsManager(this);
    this.gameScheduler = new GameScheduler(this);

    if (!setupEconomy()) {
        LOGGER.info("Vault not found or no economy plugin hooked. Chicken head selling will not provide money.");
    } else {
        LOGGER.info("Successfully hooked into Vault for economy features.");
    }

    CommandManager commandManager = new CommandManager(this);
    this.getCommand("ch").setExecutor(commandManager);
    this.getCommand("ch").setTabCompleter(commandManager);

    getServer().getPluginManager().registerEvents(new WandListener(this), this);
    getServer().getPluginManager().registerEvents(new GameListener(this), this);

    getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
        for (GameInstance game : gameManager.getActiveGames().values()) {
            // Logic untuk efek partikel pada ayam emas jika diperlukan
        }
    }, 20L, 20L);

    if (getConfig().getBoolean("auto-scheduler.enabled", false)) {
        this.gameScheduler.start();
        LOGGER.info("Auto-scheduler has been enabled. Games will start automatically according to schedule.");
    }
    
    // Daftarkan ekspansi PlaceholderAPI jika tersedia
    if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
        this.placeholderExpansion = new ChickenHuntExpansion(this);
        if (this.placeholderExpansion.register()) {
            LOGGER.info("Successfully registered PlaceholderAPI expansion.");
        } else {
            LOGGER.warning("Failed to register PlaceholderAPI expansion.");
        }
    }

    LOGGER.info("ChickenHunt has been enabled successfully!");
  }

  @Override
  public void onDisable() {
    if (this.gameScheduler != null) {
        this.gameScheduler.stop();
    }
    if (this.gameManager != null) {
        this.gameManager.stopAllGames();
    }
    if (this.regionManager != null) {
        this.regionManager.saveRegions();
    }
    if (this.playerStatsManager != null) {
        this.playerStatsManager.saveStats();
    }
    
    // Unregister PlaceholderAPI expansion
    if (this.placeholderExpansion != null) {
        this.placeholderExpansion.unregister();
    }
    
    LOGGER.info("ChickenHunt has been disabled.");
  }

  public RegionManager getRegionManager() {
    return regionManager;
  }

  public GameManager getGameManager() { 
    return gameManager;
  }

  public ItemManager getItemManager() {
    return itemManager;
  }

  public PlayerStatsManager getPlayerStatsManager() { // Tambahkan getter
    return playerStatsManager;
  }

  public void setPlayerSelection(Player player, int position, Location location) {
    if (position == 1) {
        playerSelectionsPos1.put(player.getUniqueId(), location);
    } else if (position == 2) {
        playerSelectionsPos2.put(player.getUniqueId(), location);
    }
  }

  public Location getPlayerSelection1(Player player) {
    return playerSelectionsPos1.get(player.getUniqueId());
  }

  public Location getPlayerSelection2(Player player) {
    return playerSelectionsPos2.get(player.getUniqueId());
  }

  public void clearPlayerSelections(Player player) {
    playerSelectionsPos1.remove(player.getUniqueId());
    playerSelectionsPos2.remove(player.getUniqueId());
  }

  public String getMessage(String path, Map<String, String> placeholders) {
    String message = getConfig().getString("messages." + path, "&cMessage not found: " + path);
    message = ChatColor.translateAlternateColorCodes('&', message);
    if (placeholders != null) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }
    }
    return prefix + message;
  }

  public String getRawMessage(String path, Map<String, String> placeholders) {
    String message = getConfig().getString("messages." + path, "&cMessage not found: " + path);
    message = ChatColor.translateAlternateColorCodes('&', message);
    if (placeholders != null) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }
    }
    return message;
  }

  public String getPrefix() {
    return prefix;
  }

  private boolean setupEconomy() {
      if (getServer().getPluginManager().getPlugin("Vault") == null) {
          return false;
      }
      RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
      if (rsp == null) {
          return false;
      }
      econ = rsp.getProvider();
      return econ != null;
  }

  public static Economy getEconomy() { // Getter untuk Economy
      return econ;
  }

  public GameScheduler getGameScheduler() {
      return gameScheduler;
  }
}
