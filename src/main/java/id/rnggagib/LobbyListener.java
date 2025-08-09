package id.rnggagib;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class LobbyListener implements Listener {
    private final ChickenHunt plugin;
    
    public LobbyListener(ChickenHunt plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Remove player from lobby if they're in one
        plugin.getLobbyManager().leaveLobby(player);
    }
}
