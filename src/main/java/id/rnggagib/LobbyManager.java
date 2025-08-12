package id.rnggagib;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class LobbyManager {
    private final ChickenHunt plugin;
    private final Map<String, Lobby> lobbies = new HashMap<>();
    // Single global lobby spawn (single-region mode). Stored in config under lobby.spawn.*
    
    public LobbyManager(ChickenHunt plugin) {
        this.plugin = plugin;
    }
    
    public boolean createLobby(String regionName) {
        Region region = plugin.getRegionManager().getRegion(regionName);
        if (region == null) {
            return false;
        }
        
        if (lobbies.containsKey(regionName.toLowerCase())) {
            return false; // Lobby already exists
        }
        
        Lobby lobby = new Lobby(plugin, region);
        lobbies.put(regionName.toLowerCase(), lobby);
        return true;
    }
    
    public boolean joinLobby(Player player, String regionName) {
        Lobby lobby = lobbies.get(regionName.toLowerCase());
        if (lobby == null) {
            return false;
        }
        
        return lobby.addPlayer(player);
    }
    
    public boolean leaveLobby(Player player) {
        for (Lobby lobby : lobbies.values()) {
            if (lobby.removePlayer(player)) {
                return true;
            }
        }
        return false;
    }
    
    public void forceStart(String regionName) {
        Lobby lobby = lobbies.get(regionName.toLowerCase());
        if (lobby != null) {
            lobby.forceStart();
        }
    }
    
    public Lobby getLobby(String regionName) {
        return lobbies.get(regionName.toLowerCase());
    }
    
    public Lobby getPlayerLobby(Player player) {
        for (Lobby lobby : lobbies.values()) {
            if (lobby.hasPlayer(player)) {
                return lobby;
            }
        }
        return null;
    }
    
    public void removeLobby(String regionName) {
        Lobby lobby = lobbies.remove(regionName.toLowerCase());
        if (lobby != null) {
            lobby.cleanup();
        }
    }
    
    public Collection<Lobby> getLobbies() {
        return lobbies.values();
    }
    
    public boolean isInLobby(Player player) {
        return getPlayerLobby(player) != null;
    }

    /**
     * Sets the global lobby spawn location and saves it to config
     */
    public void setLobbySpawnLocation(Location location) {
        if (location == null || location.getWorld() == null) return;
        plugin.getConfig().set("lobby.spawn.world", location.getWorld().getName());
        plugin.getConfig().set("lobby.spawn.x", location.getX());
        plugin.getConfig().set("lobby.spawn.y", location.getY());
        plugin.getConfig().set("lobby.spawn.z", location.getZ());
        plugin.getConfig().set("lobby.spawn.yaw", location.getYaw());
        plugin.getConfig().set("lobby.spawn.pitch", location.getPitch());
        plugin.saveConfig();
    }

    /**
     * Returns the lobby spawn location if set, else null
     */
    public Location getLobbySpawnLocation() {
        String worldName = plugin.getConfig().getString("lobby.spawn.world");
        if (worldName == null) return null;
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) return null;
        double x = plugin.getConfig().getDouble("lobby.spawn.x");
        double y = plugin.getConfig().getDouble("lobby.spawn.y");
        double z = plugin.getConfig().getDouble("lobby.spawn.z");
        float yaw = (float) plugin.getConfig().getDouble("lobby.spawn.yaw");
        float pitch = (float) plugin.getConfig().getDouble("lobby.spawn.pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }
}
