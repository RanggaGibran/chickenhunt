package id.rnggagib;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScoreboardHandler {
    private final ChickenHunt plugin;
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    
    public ScoreboardHandler(ChickenHunt plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Creates and displays a scoreboard for a player in an active game
     * @param player The player to show the scoreboard to
     * @param game The game instance the player is in
     */
    public void createScoreboard(Player player, GameInstance game) {
        if (!plugin.getConfig().getBoolean("game-settings.display-scoreboard", true)) {
            return;
        }
        
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        
        Scoreboard scoreboard = manager.getNewScoreboard();
        
        String title = ChatColor.translateAlternateColorCodes('&', 
                plugin.getConfig().getString("scoreboard.title", "&6&lChicken Hunt"));
        
        Objective objective = scoreboard.registerNewObjective("chickenhunt", "dummy", title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        playerScoreboards.put(player.getUniqueId(), scoreboard);
        updateScoreboard(player, game);
        
        player.setScoreboard(scoreboard);
    }
    
    /**
     * Updates a player's scoreboard with the latest game information
     * @param player The player whose scoreboard to update
     * @param game The game instance with current data
     */
    public void updateScoreboard(Player player, GameInstance game) {
        // Check if player is in the region first
        if (!game.getRegion().isInRegion(player.getLocation())) {
            // Player left the region, remove their scoreboard
            if (playerScoreboards.containsKey(player.getUniqueId())) {
                removeScoreboard(player);
            }
            return;
        }
        
        Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());
        if (scoreboard == null || !plugin.getConfig().getBoolean("game-settings.display-scoreboard", true)) {
            // Player doesn't have a scoreboard yet but is in the region, create one
            if (plugin.getConfig().getBoolean("game-settings.display-scoreboard", true)) {
                createScoreboard(player, game);
            }
            return;
        }
        
        Objective objective = scoreboard.getObjective("chickenhunt");
        if (objective == null) {
            return;
        }
        
        // Clear any existing scores and teams
        for (String entry : new ArrayList<>(scoreboard.getEntries())) {
            scoreboard.resetScores(entry);
        }
        
        for (Team team : new ArrayList<>(scoreboard.getTeams())) {
            team.unregister();
        }
        
        // Get lines from config
        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        if (lines.isEmpty()) {
            // Default lines if none configured
            lines = new ArrayList<>();
            lines.add("&7Region: &f%region%");
            lines.add("&7Time left: &f%time_left%");
            lines.add("");
            lines.add("&7Your Points: &f%points%");
            lines.add("&7Chickens Caught: &f%caught%");
        }
        
        // Replace placeholders in lines
        PlayerStatsManager statsManager = plugin.getPlayerStatsManager();
        Map<UUID, Integer> topPoints = statsManager.getTopPoints(1);
        String topPlayerName = "None";
        int topPlayerPoints = 0;
        
        if (!topPoints.isEmpty()) {
            UUID topPlayerId = topPoints.keySet().iterator().next();
            topPlayerName = Bukkit.getOfflinePlayer(topPlayerId).getName();
            if (topPlayerName == null) topPlayerName = "Unknown";
            topPlayerPoints = topPoints.get(topPlayerId);
        }
        
        int lineNumber = lines.size();
        for (String line : lines) {
            // Replace placeholders
            String formattedLine = ChatColor.translateAlternateColorCodes('&', line)
                .replace("%region%", game.getRegion().getName())
                .replace("%time_left%", formatTime(game.getRemainingSeconds()))
                .replace("%points%", String.valueOf(statsManager.getPoints(player.getUniqueId())))
                .replace("%caught%", String.valueOf(statsManager.getChickensCaught(player.getUniqueId())))
                .replace("%top_player%", topPlayerName)
                .replace("%top_points%", String.valueOf(topPlayerPoints));
            
            // Debug log time value for this specific placeholder
            if (line.contains("%time_left%")) {
                plugin.getLogger().info("Time placeholder: " + game.getRemainingSeconds() + 
                                      " seconds, formatted as: " + formatTime(game.getRemainingSeconds()));
            }
            
            // Register team to handle longer text and formatting
            Team team = scoreboard.registerNewTeam("line" + lineNumber);
            String entry = getUniqueEntry(lineNumber);
            team.addEntry(entry);
            team.setPrefix(formattedLine);
            
            // Set score
            objective.getScore(entry).setScore(lineNumber);
            lineNumber--;
        }
        
        player.setScoreboard(scoreboard);
    }
    
    /**
     * Removes a player's scoreboard
     * @param player The player whose scoreboard to remove
     */
    public void removeScoreboard(Player player) {
        playerScoreboards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }
    
    /**
     * Removes all scoreboards when a game ends
     */
    public void removeAllScoreboards() {
        for (UUID playerId : new ArrayList<>(playerScoreboards.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                removeScoreboard(player);
            }
        }
        playerScoreboards.clear();
    }
    
    /**
     * Creates a unique entry for the scoreboard line
     * @param lineNumber The line number
     * @return A unique string entry for the scoreboard
     */
    private String getUniqueEntry(int lineNumber) {
        // Use color codes to create unique entries
        ChatColor[] colors = ChatColor.values();
        return colors[lineNumber % colors.length] + "" + ChatColor.RESET;
    }
    
    /**
     * Formats seconds into MM:SS format
     * @param seconds The seconds to format
     * @return Formatted time string
     */
    private String formatTime(int seconds) {
        // Ensure seconds is not negative
        seconds = Math.max(0, seconds);
        
        // Log only occasionally to prevent spam (every 30 seconds or last 10 seconds)
        if (seconds % 30 == 0 || seconds <= 10) {
            plugin.getLogger().info("Formatting time: " + seconds + " seconds, display as: " + 
                                  String.format("%02d:%02d", seconds / 60, seconds % 60));
        }
        
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }
}
