package id.rnggagib;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ChickenHuntExpansion extends PlaceholderExpansion {

    private final ChickenHunt plugin;
    private final DecimalFormat moneyFormat = new DecimalFormat("#,##0.00");

    public ChickenHuntExpansion(ChickenHunt plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "chickenhunt";
    }

    @Override
    public String getAuthor() {
        return "RngGaGib";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        if (player == null) {
            return "";
        }

        PlayerStatsManager statsManager = plugin.getPlayerStatsManager();
        GameManager gameManager = plugin.getGameManager();
        GameScheduler scheduler = plugin.getGameScheduler();

        // %chickenhunt_caught%
        if (identifier.equals("caught")) {
            return String.valueOf(statsManager.getChickensCaught(player.getUniqueId()));
        }

        // %chickenhunt_points%
        if (identifier.equals("points")) {
            return String.valueOf(statsManager.getPoints(player.getUniqueId()));
        }

        // %chickenhunt_money_earned%
        if (identifier.equals("money_earned")) {
            double earned = statsManager.getMoneyEarned(player.getUniqueId());
            return moneyFormat.format(earned);
        }

        // %chickenhunt_active_games%
        if (identifier.equals("active_games")) {
            return String.valueOf(gameManager.getActiveGames().size());
        }
        
        // %chickenhunt_next_game_time%
        if (identifier.equals("next_game_time")) {
            if (scheduler == null || !plugin.getConfig().getBoolean("auto-scheduler.enabled", false)) {
                return "N/A";
            }
            
            long nextGameTime = scheduler.getNextGameTime();
            long now = System.currentTimeMillis();
            
            if (nextGameTime <= now) {
                return "Soon";
            }
            
            long diff = nextGameTime - now;
            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
            long seconds = TimeUnit.MILLISECONDS.toSeconds(diff) - TimeUnit.MINUTES.toSeconds(minutes);
            
            return minutes + "m " + seconds + "s";
        }
        
        // %chickenhunt_next_game_region%
        if (identifier.equals("next_game_region")) {
            if (scheduler == null || !plugin.getConfig().getBoolean("auto-scheduler.enabled", false)) {
                return "None";
            }
            
            String region = scheduler.getActiveRegion();
            return region != null ? region : "Random";
        }
        
        // Top placeholders
        if (identifier.startsWith("top_caught_")) {
            try {
                int rank = Integer.parseInt(identifier.substring(11)) - 1;
                Map<UUID, Integer> topCaught = statsManager.getTopChickensCaught(10);
                if (rank >= 0 && rank < topCaught.size()) {
                    UUID uuid = topCaught.keySet().stream().collect(Collectors.toList()).get(rank);
                    OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
                    String name = p.getName() != null ? p.getName() : "Unknown";
                    int count = topCaught.get(uuid);
                    return name + ": " + count;
                }
            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                return "";
            }
        }
        
        if (identifier.startsWith("top_money_")) {
            try {
                int rank = Integer.parseInt(identifier.substring(10)) - 1;
                Map<UUID, Double> topMoney = statsManager.getTopMoneyEarned(10);
                if (rank >= 0 && rank < topMoney.size()) {
                    UUID uuid = topMoney.keySet().stream().collect(Collectors.toList()).get(rank);
                    OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
                    String name = p.getName() != null ? p.getName() : "Unknown";
                    double money = topMoney.get(uuid);
                    return name + ": " + moneyFormat.format(money);
                }
            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                return "";
            }
        }
        
        if (identifier.startsWith("top_points_")) {
            try {
                int rank = Integer.parseInt(identifier.substring(11)) - 1;
                Map<UUID, Integer> topPoints = statsManager.getTopPoints(10);
                if (rank >= 0 && rank < topPoints.size()) {
                    UUID uuid = topPoints.keySet().stream().collect(Collectors.toList()).get(rank);
                    OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
                    String name = p.getName() != null ? p.getName() : "Unknown";
                    int points = topPoints.get(uuid);
                    return name + ": " + points;
                }
            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                return "";
            }
        }
        
        return null;
    }
}