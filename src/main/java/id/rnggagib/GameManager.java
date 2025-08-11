package id.rnggagib;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location; // Import Location
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameManager {
    private final ChickenHunt plugin;
    private final Map<String, GameInstance> activeGames = new HashMap<>();
    private final Map<String, BukkitTask> countdownTasks = new HashMap<>();
    private ScoreboardHandler scoreboardHandler;

    public GameManager(ChickenHunt plugin) {
        this.plugin = plugin;
        this.scoreboardHandler = new ScoreboardHandler(plugin);
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

        // Implement countdown before starting the game
        int countdownSeconds = plugin.getConfig().getInt("game-settings.countdown-seconds", 10);
        
        // Cancel any existing countdown for this region
        if (countdownTasks.containsKey(regionName.toLowerCase())) {
            countdownTasks.get(regionName.toLowerCase()).cancel();
        }
        
        // Start the countdown
        BukkitTask countdownTask = new BukkitRunnable() {
            int secondsLeft = countdownSeconds;
            
            @Override
            public void run() {
                if (secondsLeft <= 0) {
                    // Time to start the game
                    GameInstance gameInstance = new GameInstance(plugin, GameManager.this, region, durationSeconds, scoreboardHandler);
                    activeGames.put(regionName.toLowerCase(), gameInstance);
                    gameInstance.start();
                    
                    // Display start message
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
                    
                    // Create scoreboards for all players in the region
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (region.isInRegion(player.getLocation())) {
                            scoreboardHandler.createScoreboard(player, gameInstance);
                        }
                    }
                    
                    // Cancel this task
                    this.cancel();
                    countdownTasks.remove(regionName.toLowerCase());
                } else if (secondsLeft <= 5 || secondsLeft % 5 == 0) {
                    // Announce countdown at intervals
                    Map<String, String> placeholders = Map.of(
                        "region", regionName,
                        "seconds", String.valueOf(secondsLeft)
                    );
                    
                    // Broadcast to all players or just those in the region
                    String countdownMessage = plugin.getRawMessage("game_countdown", placeholders);
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (region.isInRegion(player.getLocation())) {
                            player.sendMessage(countdownMessage);
                        }
                    }
                }
                
                secondsLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L); // Run every second
        
        countdownTasks.put(regionName.toLowerCase(), countdownTask);
        
        // Inform about the countdown (only if not an admin doing /ch start)
        if (starter != null && starter instanceof Player) {
            // Don't send the initial countdown message to avoid duplication
            // The first countdown message will be sent by the runnable
        }
        
        return true;
    }

    public boolean openGame(String regionName, int durationSeconds, CommandSender starter) {
        Region region = plugin.getRegionManager().getRegion(regionName);
        if (region == null) {
            if (starter != null) starter.sendMessage(plugin.getMessage("region_not_found", Map.of("region", regionName)));
            return false;
        }
        if (activeGames.containsKey(regionName.toLowerCase())) {
            if (starter != null) starter.sendMessage(plugin.getMessage("game_already_started", Map.of("region", regionName)));
            return false;
        }
        GameInstance gameInstance = new GameInstance(plugin, this, region, durationSeconds, scoreboardHandler);
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
        // Create scoreboards for players already inside region
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (region.isInRegion(player.getLocation())) {
                scoreboardHandler.createScoreboard(player, gameInstance);
            }
        }
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

        // Remove all scoreboards for players in the region
        scoreboardHandler.removeAllScoreboards();

        gameInstance.stop(timedOut);
        Map<String, String> placeholders = Map.of("region", regionName);
        if (stopper != null) { // Manual stop by command
             stopper.sendMessage(plugin.getMessage("game_stopped_manual", placeholders));
        } else if (timedOut) { // Game ended due to time
            // Could send a different message or broadcast
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
    }
}