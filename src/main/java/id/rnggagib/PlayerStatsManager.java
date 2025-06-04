package id.rnggagib;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class PlayerStatsManager {
    private final ChickenHunt plugin;
    private File statsFile;
    private FileConfiguration statsConfig;

    private final Map<UUID, Integer> chickensCaught = new HashMap<>();
    private final Map<UUID, Double> moneyEarned = new HashMap<>();
    // private final Map<UUID, Integer> headsSold = new HashMap<>(); // Optional: if you want to track heads sold separately

    public PlayerStatsManager(ChickenHunt plugin) {
        this.plugin = plugin;
        setupStatsFile();
        loadStats();
    }

    private void setupStatsFile() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        statsFile = new File(plugin.getDataFolder(), "playerstats.yml");
        if (!statsFile.exists()) {
            try {
                statsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create playerstats.yml", e);
            }
        }
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
    }

    public void loadStats() {
        chickensCaught.clear();
        moneyEarned.clear();
        // headsSold.clear();

        ConfigurationSection caughtSection = statsConfig.getConfigurationSection("chickensCaught");
        if (caughtSection != null) {
            for (String uuidString : caughtSection.getKeys(false)) {
                try {
                    chickensCaught.put(UUID.fromString(uuidString), caughtSection.getInt(uuidString));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in chickensCaught: " + uuidString);
                }
            }
        }

        ConfigurationSection moneySection = statsConfig.getConfigurationSection("moneyEarned");
        if (moneySection != null) {
            for (String uuidString : moneySection.getKeys(false)) {
                try {
                    moneyEarned.put(UUID.fromString(uuidString), moneySection.getDouble(uuidString));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in moneyEarned: " + uuidString);
                }
            }
        }
        plugin.getLogger().info("Loaded stats for " + chickensCaught.size() + " players (caught) and " + moneyEarned.size() + " players (money).");
    }

    public void saveStats() {
        statsConfig.set("chickensCaught", null);
        statsConfig.set("moneyEarned", null);

        chickensCaught.forEach((uuid, count) -> statsConfig.set("chickensCaught." + uuid.toString(), count));
        moneyEarned.forEach((uuid, amount) -> statsConfig.set("moneyEarned." + uuid.toString(), amount));

        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save playerstats.yml", e);
        }
    }

    public void incrementChickensCaught(UUID playerId, int amount) {
        chickensCaught.put(playerId, chickensCaught.getOrDefault(playerId, 0) + amount);
        // Consider saving periodically or on specific events rather than every increment
        // For now, we'll save onDisable or manually.
    }

    public void addMoneyEarned(UUID playerId, double amount) {
        moneyEarned.put(playerId, moneyEarned.getOrDefault(playerId, 0.0) + amount);
    }
    
    public int getChickensCaught(UUID playerId) {
        return chickensCaught.getOrDefault(playerId, 0);
    }

    public double getMoneyEarned(UUID playerId) {
        return moneyEarned.getOrDefault(playerId, 0.0);
    }

    public Map<UUID, Integer> getTopChickensCaught(int limit) {
        return chickensCaught.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public Map<UUID, Double> getTopMoneyEarned(int limit) {
        return moneyEarned.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }
}