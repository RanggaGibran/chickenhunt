package id.rnggagib;

import org.bukkit.ChatColor;
import org.bukkit.Location; // Import Location
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GameManager {
    private final ChickenHunt plugin;
    private final Map<String, GameInstance> activeGames = new HashMap<>();

    public GameManager(ChickenHunt plugin) {
        this.plugin = plugin;
    }

    public boolean startGame(String regionName, int durationSeconds, CommandSender starter) {
        Region region = plugin.getRegionManager().getRegion(regionName);
        if (region == null) {
            if (starter != null) starter.sendMessage(plugin.getMessage("region_not_found", Map.of("region", regionName)));
            return false;
        }

        if (activeGames.containsKey(regionName.toLowerCase())) {
            if (starter != null) starter.sendMessage(plugin.getMessage("game_already_started", Map.of("region", regionName)));
            return false;
        }

        GameInstance gameInstance = new GameInstance(plugin, this, region, durationSeconds);
        activeGames.put(regionName.toLowerCase(), gameInstance);
        gameInstance.start();
        if (starter != null) {
             Map<String, String> placeholders = new HashMap<>();
             placeholders.put("region", regionName);
             if (durationSeconds > 0) {
                 placeholders.put("duration", String.valueOf(durationSeconds));
                 starter.sendMessage(plugin.getMessage("game_started_duration", placeholders));
             } else {
                 starter.sendMessage(plugin.getMessage("game_started", placeholders));
             }
        }
        // Announce to players in region or globally if configured
        return true;
    }

    public boolean stopGame(String regionName, CommandSender stopper) {
        return stopGame(regionName, false, stopper);
    }
    
    // Internal stopGame that can differentiate between manual stop and timed out
    public boolean stopGame(String regionName, boolean timedOut) {
        return stopGame(regionName, timedOut, null);
    }

    private boolean stopGame(String regionName, boolean timedOut, CommandSender stopper) {
        GameInstance gameInstance = activeGames.remove(regionName.toLowerCase());
        if (gameInstance == null) {
            if (stopper != null) stopper.sendMessage(plugin.getMessage("game_not_started", Map.of("region", regionName)));
            return false;
        }

        gameInstance.stop(timedOut);
        Map<String, String> placeholders = Map.of("region", regionName);
        if (stopper != null) { // Manual stop by command
             stopper.sendMessage(plugin.getMessage("game_stopped_manual", placeholders));
        } else if (timedOut) { // Game ended due to time
            // Could send a different message or broadcast
            plugin.getLogger().info(plugin.getRawMessage("game_ended_timed", placeholders));
            // Example: Bukkit.broadcastMessage(plugin.getMessage("game_ended_timed_broadcast", placeholders));
        }
        return true;
    }

    public boolean isGameActive(String regionName) {
        return activeGames.containsKey(regionName.toLowerCase());
    }

    public GameInstance getGameInstance(String regionName) {
        return activeGames.get(regionName.toLowerCase());
    }
    
    public GameInstance getGameByChickenLocation(Location location) {
        for (GameInstance game : activeGames.values()) {
            if (game.getRegion().isInRegion(location)) {
                return game;
            }
        }
        return null;
    }

    public Map<String, GameInstance> getActiveGames() { // Add this method
        return Collections.unmodifiableMap(activeGames);
    }

    public void stopAllGames() {
        for (String regionName : new HashMap<>(activeGames).keySet()) { // Iterate over a copy of keys
            stopGame(regionName, false, null); // false for not timed out, null for no specific stopper
        }
        plugin.getLogger().info("All ChickenHunt games stopped.");
    }
}