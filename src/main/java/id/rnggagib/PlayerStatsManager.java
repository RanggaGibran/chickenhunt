package id.rnggagib;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlayerStatsManager {
    private final ChickenHunt plugin;
    private File statsFile;
    private FileConfiguration statsConfig;

    private final Map<UUID, Integer> chickensCaught = new HashMap<>();
    private final Map<UUID, Double> moneyEarned = new HashMap<>();
    private final Map<UUID, Integer> pointsEarned = new HashMap<>(); // New points map
    private final Map<UUID, Set<Integer>> rewardsGiven = new HashMap<>();
    // Track which speed milestones have already given potion rewards (so players don't get duplicates)
    private final Map<UUID, Set<Integer>> speedRewardsGiven = new HashMap<>();

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
                // Silent fail
            }
        }
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
    }

    public void loadStats() {
        chickensCaught.clear();
        moneyEarned.clear();
        pointsEarned.clear();
        rewardsGiven.clear();
    speedRewardsGiven.clear();

        ConfigurationSection caughtSection = statsConfig.getConfigurationSection("chickensCaught");
        if (caughtSection != null) {
            for (String uuidString : caughtSection.getKeys(false)) {
                try {
                    chickensCaught.put(UUID.fromString(uuidString), caughtSection.getInt(uuidString));
                } catch (IllegalArgumentException e) {
                    // Invalid UUID, skip
                }
            }
        }

        ConfigurationSection moneySection = statsConfig.getConfigurationSection("moneyEarned");
        if (moneySection != null) {
            for (String uuidString : moneySection.getKeys(false)) {
                try {
                    moneyEarned.put(UUID.fromString(uuidString), moneySection.getDouble(uuidString));
                } catch (IllegalArgumentException e) {
                    // Invalid UUID, skip
                }
            }
        }
        
        // Load points data
        ConfigurationSection pointsSection = statsConfig.getConfigurationSection("pointsEarned");
        if (pointsSection != null) {
            for (String uuidString : pointsSection.getKeys(false)) {
                try {
                    pointsEarned.put(UUID.fromString(uuidString), pointsSection.getInt(uuidString));
                } catch (IllegalArgumentException e) {
                    // Invalid UUID, skip
                }
            }
        }
        
        ConfigurationSection rewardsSection = statsConfig.getConfigurationSection("rewardsGiven");
        if (rewardsSection != null) {
            for (String uuidString : rewardsSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    List<?> tiersList = rewardsSection.getList(uuidString);
                    Set<Integer> tiers = new HashSet<>();
                    if (tiersList != null) {
                        for (Object tier : tiersList) {
                            if (tier instanceof Integer) {
                                tiers.add((Integer) tier);
                            }
                        }
                    }
                    rewardsGiven.put(uuid, tiers);
                } catch (IllegalArgumentException e) {
                    // Invalid UUID, skip
                }
            }
        }

        // Load speed reward milestones section (optional)
        ConfigurationSection speedRewardsSection = statsConfig.getConfigurationSection("speedRewardsGiven");
        if (speedRewardsSection != null) {
            for (String uuidString : speedRewardsSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    List<?> list = speedRewardsSection.getList(uuidString);
                    Set<Integer> milestones = new HashSet<>();
                    if (list != null) {
                        for (Object o : list) {
                            if (o instanceof Integer) {
                                milestones.add((Integer) o);
                            }
                        }
                    }
                    speedRewardsGiven.put(uuid, milestones);
                } catch (IllegalArgumentException e) {
                    // skip invalid
                }
            }
        }
    }

    public void saveStats() {
        statsConfig.set("chickensCaught", null);
        statsConfig.set("moneyEarned", null);
        statsConfig.set("pointsEarned", null);
        statsConfig.set("rewardsGiven", null);
    statsConfig.set("speedRewardsGiven", null);

        chickensCaught.forEach((uuid, count) -> statsConfig.set("chickensCaught." + uuid.toString(), count));
        moneyEarned.forEach((uuid, amount) -> statsConfig.set("moneyEarned." + uuid.toString(), amount));
        pointsEarned.forEach((uuid, points) -> statsConfig.set("pointsEarned." + uuid.toString(), points));
        
        for (Map.Entry<UUID, Set<Integer>> entry : rewardsGiven.entrySet()) {
            statsConfig.set("rewardsGiven." + entry.getKey().toString(), new ArrayList<>(entry.getValue()));
        }

        for (Map.Entry<UUID, Set<Integer>> entry : speedRewardsGiven.entrySet()) {
            statsConfig.set("speedRewardsGiven." + entry.getKey().toString(), new ArrayList<>(entry.getValue()));
        }

        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            // Silent fail
        }
    }

    public void incrementChickensCaught(UUID playerId, int amount) {
        int previousCaught = chickensCaught.getOrDefault(playerId, 0);
        int newTotal = previousCaught + amount;
        chickensCaught.put(playerId, newTotal);
        saveStats(); // Save changes to file
        
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            checkAndGiveRewards(player, newTotal);
        awardSpeedMilestones(player, previousCaught, newTotal);
            
            // Update scoreboards to reflect the new chicken count
            updateScoreboards();
        }
    }

    /**
     * Awards potion speed effects at specific chicken catch milestones:
     * 10: Speed I 10s, 25: Speed I 15s, 50: Speed I 20s, 100: Speed II 30s, 200: Speed II 60s
     */
    private void awardSpeedMilestones(Player player, int previousTotal, int newTotal) {
    UUID uuid = player.getUniqueId();
    Set<Integer> given = speedRewardsGiven.computeIfAbsent(uuid, k -> new HashSet<>());

    // milestone -> [amplifier(level-1), durationSeconds]
    int[][] milestones = new int[][]{
        {10, 0, 10},
        {25, 0, 15},
        {50, 0, 20},
        {100, 1, 30},
        {200, 1, 60}
    };

    for (int[] m : milestones) {
        int count = m[0];
        int amplifier = m[1];
        int seconds = m[2];
        if (previousTotal < count && newTotal >= count && !given.contains(count)) {
        // Give effect
        try {
            org.bukkit.potion.PotionEffect effect = new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SPEED,
                seconds * 20,
                amplifier,
                false,
                true,
                true
            );
            player.addPotionEffect(effect);
        } catch (Exception ignored) {}

        given.add(count);

        String msg = ChatColor.translateAlternateColorCodes('&',
            "&bBonus Speed &7(\u2708) &fkarena telah menangkap &e" + count + " &fayam!&7 (" +
                (amplifier + 1) + "&fx &b" + seconds + "s)");
        player.sendMessage(msg);
        player.sendTitle(
            ChatColor.translateAlternateColorCodes('&', "&b&lSPEED BOOST!"),
            ChatColor.translateAlternateColorCodes('&', "&fMenangkap &e" + count + " &fayam"),
            10, 40, 10
        );
        }
    }
    // Persist updated milestone grants
    saveStats();
    }
    
    private void checkAndGiveRewards(Player player, int newTotal) {
        UUID playerId = player.getUniqueId();
        Set<Integer> playerRewards = rewardsGiven.computeIfAbsent(playerId, k -> new HashSet<>());
        
        if (!plugin.getConfig().getBoolean("rewards.tier-rewards.enabled", false)) {
            return;
        }
        
        List<?> tiers = plugin.getConfig().getList("rewards.tier-rewards.tiers");
        if (tiers == null || tiers.isEmpty()) {
            return;
        }
        
        for (Object tierObj : tiers) {
            if (!(tierObj instanceof Map)) continue;
            
            Map<?, ?> tier = (Map<?, ?>) tierObj;
            Integer count = (Integer) tier.get("count");
            if (count == null) continue;
            
            if (newTotal >= count && !playerRewards.contains(count)) {
                playerRewards.add(count);
                
                List<?> commands = (List<?>) tier.get("commands");
                if (commands != null) {
                    for (Object cmdObj : commands) {
                        if (cmdObj instanceof String) {
                            String cmd = (String) cmdObj;
                            cmd = cmd.replace("%player%", player.getName());
                            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
                        }
                    }
                }
                
                String message = plugin.getConfig().getString("rewards.tier-rewards.message", "&a&lSELAMAT! &eKamu telah mendapatkan hadiah untuk menangkap &6%count% &eayam!");
                message = ChatColor.translateAlternateColorCodes('&', message.replace("%count%", String.valueOf(count)));
                player.sendMessage(message);
                
                player.sendTitle(
                    ChatColor.translateAlternateColorCodes('&', "&6&lREWARD UNLOCKED!"),
                    ChatColor.translateAlternateColorCodes('&', "&eMenangkap " + count + " ayam"),
                    10, 70, 20
                );
            }
        }
    }

    public void addPoints(UUID playerId, int amount) {
        pointsEarned.put(playerId, getPoints(playerId) + amount);
        saveStats(); // Save changes to file
        updateScoreboards(); // Update all active scoreboards
    }
    
    public void setPoints(UUID playerId, int amount) {
        pointsEarned.put(playerId, amount);
        saveStats(); // Save changes to file
        updateScoreboards(); // Update all active scoreboards
    }
    
    public int getPoints(UUID playerId) {
        return pointsEarned.getOrDefault(playerId, 0);
    }
    
    /**
     * Updates all active scoreboards to show the latest point values
     */
    private void updateScoreboards() {
        // Check if plugin and gameManager are available
        if (plugin.getGameManager() == null || plugin.getScoreboardHandler() == null) {
            return;
        }
        
        // Get all active games
        Map<String, GameInstance> activeGames = plugin.getGameManager().getActiveGames();
        
        // For each active game, update scoreboards for all players in the region
        for (GameInstance game : activeGames.values()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                // Only update for players in the game region
                if (game.getRegion().isInRegion(player.getLocation())) {
                    plugin.getScoreboardHandler().updateScoreboard(player, game);
                }
            }
        }
    }

    public Map<UUID, Integer> getTopPoints(int limit) {
        return pointsEarned.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
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
    
    public Set<Integer> getRewardsReceived(UUID playerId) {
        return rewardsGiven.getOrDefault(playerId, new HashSet<>());
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