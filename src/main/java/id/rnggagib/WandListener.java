package id.rnggagib;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

public class WandListener implements Listener {

    private final ChickenHunt plugin;
    private String WAND_ITEM_NAME;
    private Material WAND_MATERIAL;

    public WandListener(ChickenHunt plugin) {
        this.plugin = plugin;
        loadWandDetails();
    }

    private void loadWandDetails() {
        WAND_MATERIAL = Material.getMaterial(plugin.getConfig().getString("wand-item.material", "BLAZE_ROD"));
        WAND_ITEM_NAME = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("wand-item.name", "&6ChickenHunt Wand"));
    }
    
    private String getMsg(String key, Map<String, String> placeholders) {
        return plugin.getMessage(key, placeholders);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand == null || itemInHand.getType() != WAND_MATERIAL) {
            return;
        }

        ItemMeta meta = itemInHand.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !meta.getDisplayName().equals(WAND_ITEM_NAME)) {
            return;
        }

        if (!player.hasPermission("chickenhunt.admin.wand")) {
            // Optionally send a no_permission message, though commands usually handle this
            return;
        }

        Action action = event.getAction();
        Block clickedBlock = event.getClickedBlock();

        if (clickedBlock == null) {
            return; 
        }

        event.setCancelled(true); 

        Location location = clickedBlock.getLocation();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("x", String.valueOf(location.getBlockX()));
        placeholders.put("y", String.valueOf(location.getBlockY()));
        placeholders.put("z", String.valueOf(location.getBlockZ()));
        placeholders.put("world", location.getWorld().getName());


        if (action == Action.LEFT_CLICK_BLOCK) {
            plugin.setPlayerSelection(player, 1, location);
            player.sendMessage(getMsg("pos1_set", placeholders));
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            plugin.setPlayerSelection(player, 2, location);
            player.sendMessage(getMsg("pos1_set", placeholders).replace("Position 1", "Position 2"));
        }
    }
}