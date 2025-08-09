package id.rnggagib;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class RegionListener implements Listener {
    private final ChickenHunt plugin;

    public RegionListener(ChickenHunt plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if player has moved to a different block (not just looking around)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        GameManager gameManager = plugin.getGameManager();

        // Check all active games
        for (GameInstance game : gameManager.getActiveGames().values()) {
            Region region = game.getRegion();
            boolean wasInRegion = region.isInRegion(event.getFrom());
            boolean isInRegion = region.isInRegion(event.getTo());

            // Player entered the region
            if (!wasInRegion && isInRegion) {
                plugin.getScoreboardHandler().createScoreboard(player, game);
                // Add player to phase bossbar if exists
                if (game.getPhaseManager() != null) {
                    game.getPhaseManager().addPlayerToBossBar(player);
                }
            }
            
            // Player left the region
            else if (wasInRegion && !isInRegion) {
                plugin.getScoreboardHandler().removeScoreboard(player);
                // Remove player from phase bossbar if exists
                if (game.getPhaseManager() != null) {
                    game.getPhaseManager().removePlayerFromBossBar(player);
                }
            }
            // Player moved within region - update scoreboard to ensure it's accurate
            else if (isInRegion && wasInRegion) {
                // Only update periodically to avoid performance impact
                if (event.getFrom().distance(event.getTo()) > 3 || Math.random() < 0.05) {  // 5% chance or significant movement
                    plugin.getScoreboardHandler().updateScoreboard(player, game);
                }
            }
        }
    }
}
