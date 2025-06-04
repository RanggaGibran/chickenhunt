package id.rnggagib;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ItemManager {

    private final ChickenHunt plugin;

    public ItemManager(ChickenHunt plugin) {
        this.plugin = plugin;
    }

    public ItemStack getChickenHeadItem() {
        String materialName = plugin.getConfig().getString("chicken-head-item.material", "PLAYER_HEAD").toUpperCase();
        Material material = Material.getMaterial(materialName);
        if (material == null) {
            plugin.getLogger().warning("Invalid material for chicken-head-item: " + materialName + ". Defaulting to GOLD_NUGGET.");
            material = Material.GOLD_NUGGET;
        }

        ItemStack headItem = new ItemStack(material);
        ItemMeta meta = headItem.getItemMeta();

        if (meta != null) {
            String name = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("chicken-head-item.name", "&6Kepala Ayam Spesial"));
            meta.setDisplayName(name);

            List<String> lore = plugin.getConfig().getStringList("chicken-head-item.lore").stream()
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                    .collect(Collectors.toList());
            meta.setLore(lore);

            if (meta instanceof SkullMeta && material == Material.PLAYER_HEAD) {
                SkullMeta skullMeta = (SkullMeta) meta;
                String textureValue = plugin.getConfig().getString("chicken-head-item.texture_value");
                if (textureValue != null && !textureValue.isEmpty()) {
                    try {
                        // Gunakan UUID tetap agar kepala dapat ditumpuk
                        UUID fixedUUID = UUID.fromString("43c28ffa-37d1-4f7e-bca3-718136e6eb6e");
                        PlayerProfile profile = plugin.getServer().createPlayerProfile(fixedUUID, "ChickenHead");
                        PlayerTextures textures = profile.getTextures();
                        
                        URL textureUrl = new URL("https://textures.minecraft.net/texture/" + extractTextureId(textureValue));
                        textures.setSkin(textureUrl);
                        profile.setTextures(textures);
                        
                        skullMeta.setOwnerProfile(profile);
                    } catch (MalformedURLException e) {
                        plugin.getLogger().warning("Invalid texture URL: " + e.getMessage());
                    }
                }
            }
            
            meta.setLocalizedName("chickenhunt.special_head");
            headItem.setItemMeta(meta);
        }
        return headItem;
    }

    public ItemStack getGoldenChickenHeadItem() {
        ItemStack headItem = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) headItem.getItemMeta();
        
        if (meta != null) {
            String name = ChatColor.translateAlternateColorCodes('&', "&eKepala Ayam Emas");
            meta.setDisplayName(name);
            
            List<String> lore = Arrays.asList(
                ChatColor.translateAlternateColorCodes('&', "&7Item langka dari menangkap Ayam Emas!"),
                ChatColor.translateAlternateColorCodes('&', "&7Jual ini dengan /ch sell untuk hadiah lebih besar!")
            );
            meta.setLore(lore);
            
            try {
                // UUID tetap, beda dengan ayam biasa agar tidak tercampur dalam stack
                UUID fixedUUID = UUID.fromString("f59b2fca-8f7e-42b3-9f12-a8b472639371");
                PlayerProfile profile = plugin.getServer().createPlayerProfile(fixedUUID, "GoldenChickenHead");
                PlayerTextures textures = profile.getTextures();
                
                String textureValue = plugin.getConfig().getString("game-settings.golden-chicken.texture_value", 
                    "1ae3855f952cd4a03c148a946e3f812a5955a09c6881c6e259cb37eab6dcc5f");
                URL textureUrl = new URL("https://textures.minecraft.net/texture/" + extractTextureId(textureValue));
                textures.setSkin(textureUrl);
                profile.setTextures(textures);
                
                meta.setOwnerProfile(profile);
            } catch (MalformedURLException e) {
                plugin.getLogger().warning("Invalid golden chicken head texture URL: " + e.getMessage());
            }
            
            meta.setLocalizedName("chickenhunt.golden_head");
            headItem.setItemMeta(meta);
        }
        return headItem;
    }

    // Ekstrak ID tekstur dari nilai base64 atau URL lengkap
    private String extractTextureId(String textureValue) {
        if (textureValue.startsWith("http")) {
            int lastSlash = textureValue.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < textureValue.length() - 1) {
                return textureValue.substring(lastSlash + 1);
            }
            return textureValue;
        }
        
        return "fd82aae0b5a825f376bcfa1669e45855a7ca5c2c4cf751ad87d042ba1fb47a9e";
    }

    public boolean isChickenHeadItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        String configName = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("chicken-head-item.name", "&6Kepala Ayam Spesial"));
        
        // Cukup periksa nama displayName dan materialnya saja, tanpa local name
        return meta.hasDisplayName() && meta.getDisplayName().equals(configName) && 
               (item.getType() == Material.PLAYER_HEAD || 
                item.getType().toString().equals(plugin.getConfig().getString("chicken-head-item.material", "PLAYER_HEAD").toUpperCase()));
    }

    public boolean isGoldenChickenHeadItem(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasLocalizedName() && meta.getLocalizedName().equals("chickenhunt.golden_head");
    }
}