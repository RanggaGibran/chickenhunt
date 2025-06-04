package id.rnggagib;

import net.milkbowl.vault.economy.EconomyResponse; 
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer; // Import OfflinePlayer
import org.bukkit.Bukkit; // Import Bukkit
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID; // Import UUID
import java.util.stream.Collectors;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final ChickenHunt plugin;
    private String WAND_ITEM_NAME;
    private Material WAND_MATERIAL;

    public CommandManager(ChickenHunt plugin) {
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
    
    private String getRawMsg(String key, Map<String, String> placeholders) {
        return plugin.getRawMessage(key, placeholders);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender); // Akan dimodifikasi untuk membedakan admin dan player
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "wand":
                return handleWandCommand(sender);
            case "create":
                return handleCreateCommand(sender, args);
            case "delete":
                return handleDeleteCommand(sender, args);
            case "list":
                return handleListCommand(sender);
            case "reload":
                return handleReloadCommand(sender);
            case "start":
                return handleStartCommand(sender, args);
            case "stop":
                return handleStopCommand(sender, args);
            case "sell": // Tambahkan case "sell"
                return handleSellCommand(sender);
            case "status": // Tambahkan case "status"
                return handleStatusCommand(sender, args);
            case "top": // Tambahkan case "top"
                return handleTopCommand(sender, args);
            case "help": // Tambahkan case "help"
                sendHelp(sender); // Akan dimodifikasi
                return true;
            case "schedule":
                return handleScheduleCommand(sender);
            default:
                sender.sendMessage(getMsg("unknown_command", null));
                return true;
        }
    }

    private boolean handleWandCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMsg("player_only_command", null));
            return true;
        }
        if (!sender.hasPermission("chickenhunt.admin.wand")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }
        Player player = (Player) sender;
        ItemStack wand = new ItemStack(WAND_MATERIAL);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(WAND_ITEM_NAME);
            meta.setLore(Arrays.asList(
                getRawMsg("wand_lore_pos1", null),
                getRawMsg("wand_lore_pos2", null)
            ));
            wand.setItemMeta(meta);
        }
        player.getInventory().addItem(wand);
        player.sendMessage(getMsg("wand_received", null));
        return true;
    }

    private boolean handleCreateCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMsg("player_only_command", null));
            return true;
        }
        if (!sender.hasPermission("chickenhunt.admin.create")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(getMsg("usage_create", null));
            return true;
        }
        Player player = (Player) sender;
        String regionName = args[1];

        Location pos1 = plugin.getPlayerSelection1(player);
        Location pos2 = plugin.getPlayerSelection2(player);

        if (pos1 == null || pos2 == null) {
            sender.sendMessage(getMsg("select_positions_first", null));
            return true;
        }
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            sender.sendMessage(getMsg("positions_different_worlds", null));
            return true;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("region", regionName);

        if (plugin.getRegionManager().regionExists(regionName)) {
            sender.sendMessage(getMsg("region_exists", placeholders));
            return true;
        }

        boolean success = plugin.getRegionManager().createRegion(regionName, pos1, pos2);
        if (success) {
            sender.sendMessage(getMsg("region_created", placeholders));
            plugin.clearPlayerSelections(player);
        } else {
            sender.sendMessage(getMsg("region_create_failed", placeholders));
        }
        return true;
    }

    private boolean handleDeleteCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chickenhunt.admin.delete")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(getMsg("usage_delete", null));
            return true;
        }
        String regionName = args[1];
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("region", regionName);

        if (plugin.getRegionManager().deleteRegion(regionName)) {
            sender.sendMessage(getMsg("region_deleted", placeholders));
        } else {
            sender.sendMessage(getMsg("region_not_found", placeholders));
        }
        return true;
    }

    private boolean handleListCommand(CommandSender sender) {
        if (!sender.hasPermission("chickenhunt.admin.list")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }
        Set<String> regionNames = plugin.getRegionManager().getRegionNames();
        if (regionNames.isEmpty()) {
            sender.sendMessage(getMsg("no_regions_yet", null));
        } else {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("regions", String.join(", ", regionNames));
            sender.sendMessage(getMsg("region_list", placeholders));
        }
        return true;
    }

    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("chickenhunt.admin.reload")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }
        plugin.reloadConfig();
        // Pastikan GameManager juga di-reset atau konfigurasinya di-refresh jika perlu
        // Untuk saat ini, reload config utama dan region sudah cukup.
        // Jika GameManager memiliki konfigurasi sendiri yang dimuat dari config, itu juga perlu di-refresh.
        plugin.getRegionManager().loadRegions(); 
        loadWandDetails(); 
        sender.sendMessage(getMsg("plugin_reloaded", null));
        return true;
    }

    private boolean handleStartCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chickenhunt.admin.start")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(getMsg("usage_start", null)); // Buat pesan ini di config
            return true;
        }
        String regionName = args[1];
        int duration = 0; // Default: indefinite
        if (args.length > 2) {
            try {
                duration = Integer.parseInt(args[2]);
                if (duration <= 0) {
                    sender.sendMessage(getMsg("invalid_duration", null)); // Buat pesan ini
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(getMsg("invalid_duration_format", null)); // Buat pesan ini
                return true;
            }
        }
        plugin.getGameManager().startGame(regionName, duration, sender);
        return true;
    }

    private boolean handleStopCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chickenhunt.admin.stop")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(getMsg("usage_stop", null));
            return true;
        }
        String regionName = args[1];
        plugin.getGameManager().stopGame(regionName, sender);
        return true;
    }

    private boolean handleSellCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMsg("player_only_command", null));
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("chickenhunt.player.sell")) {
            player.sendMessage(getMsg("no_permission", null));
            return true;
        }

        PlayerInventory inventory = player.getInventory();
        int headsSold = 0;
        int goldenHeadsSold = 0;
        double moneyEarned = 0.0;
        double pricePerHead = plugin.getConfig().getDouble("chicken-head-item.sell-price", 50.0);
        double pricePerGoldenHead = pricePerHead * 3; // Golden heads worth 3x

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null) continue;
            
            if (plugin.getItemManager().isGoldenChickenHeadItem(item)) {
                int amount = item.getAmount();
                goldenHeadsSold += amount;
                inventory.setItem(i, null);
            } 
            else if (plugin.getItemManager().isChickenHeadItem(item)) {
                int amount = item.getAmount();
                headsSold += amount;
                inventory.setItem(i, null);
            }
        }

        int totalHeads = headsSold + goldenHeadsSold;
        if (totalHeads == 0) {
            player.sendMessage(getMsg("sell_no_heads", null));
            return true;
        }

        if (ChickenHunt.getEconomy() != null) {
            moneyEarned = (headsSold * pricePerHead) + (goldenHeadsSold * pricePerGoldenHead);
            ChickenHunt.getEconomy().depositPlayer(player, moneyEarned);
        } else if (pricePerHead > 0) {
            player.sendMessage(getMsg("sell_vault_not_found", null));
        }
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(totalHeads));
        placeholders.put("regular", String.valueOf(headsSold));
        placeholders.put("golden", String.valueOf(goldenHeadsSold));
        placeholders.put("money", ChickenHunt.getEconomy() != null ? 
                ChickenHunt.getEconomy().format(moneyEarned) : String.format("%.2f", moneyEarned));
        
        // Pesan yang menunjukkan rincian kepala reguler dan emas
        if (goldenHeadsSold > 0) {
            player.sendMessage(ChatColor.GREEN + "Kamu menjual " + headsSold + " kepala ayam biasa dan " + 
                               ChatColor.GOLD + goldenHeadsSold + " kepala ayam emas " + 
                               ChatColor.GREEN + "seharga " + placeholders.get("money") + "!");
        } else {
            player.sendMessage(getMsg("sell_success", placeholders));
        }
        
        if (moneyEarned > 0) {
            plugin.getPlayerStatsManager().addMoneyEarned(player.getUniqueId(), moneyEarned);
        }
        
        return true;
    }

    private boolean handleStatusCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chickenhunt.player.status")) { // Bisa juga admin
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }

        GameManager gm = plugin.getGameManager();
        if (args.length == 1) { // /ch status (tampilkan semua)
            Map<String, GameInstance> activeGames = gm.getActiveGames();
            if (activeGames.isEmpty()) {
                sender.sendMessage(getMsg("status_no_active_games", null));
                return true;
            }
            sender.sendMessage(getRawMsg("status_header_all", null)); // Gunakan getRawMsg untuk header tanpa prefix
            for (GameInstance game : activeGames.values()) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("region", game.getRegion().getName());
                if (game.getDurationSeconds() > 0) {
                    placeholders.put("time_left", formatTime(game.getRemainingSeconds()));
                    sender.sendMessage(getRawMsg("status_game_entry", placeholders));
                } else {
                    sender.sendMessage(getRawMsg("status_game_entry_indefinite", placeholders));
                }
            }
        } else { // /ch status <regionName>
            String regionName = args[1];
            GameInstance game = gm.getGameInstance(regionName);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("region", regionName);
            if (game != null) {
                sender.sendMessage(getRawMsg("status_header_region", placeholders));
                 if (game.getDurationSeconds() > 0) {
                    placeholders.put("time_left", formatTime(game.getRemainingSeconds()));
                    sender.sendMessage(getRawMsg("status_game_entry", placeholders));
                } else {
                    sender.sendMessage(getRawMsg("status_game_entry_indefinite", placeholders));
                }
            } else {
                sender.sendMessage(getMsg("status_region_not_found_or_inactive", placeholders));
            }
        }
        return true;
    }
    
    private boolean handleTopCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chickenhunt.player.top")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }

        String type = "caught"; // Default
        if (args.length > 1 && (args[1].equalsIgnoreCase("money") || args[1].equalsIgnoreCase("earned"))) {
            type = "money";
        }

        int limit = plugin.getConfig().getInt("leaderboard.top-limit", 10);
        sender.sendMessage(getRawMsg("leaderboard_header", Map.of("type", type.substring(0, 1).toUpperCase() + type.substring(1))));

        PlayerStatsManager psm = plugin.getPlayerStatsManager();
        int rank = 1;

        if (type.equals("caught")) {
            Map<UUID, Integer> topCaught = psm.getTopChickensCaught(limit);
            if (topCaught.isEmpty()) {
                sender.sendMessage(getRawMsg("leaderboard_empty", Map.of("type", "chickens caught")));
                return true;
            }
            for (Map.Entry<UUID, Integer> entry : topCaught.entrySet()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                sender.sendMessage(getRawMsg("leaderboard_entry_caught", Map.of(
                        "rank", String.valueOf(rank++),
                        "player", player.getName() != null ? player.getName() : "Unknown",
                        "amount", String.valueOf(entry.getValue())
                )));
            }
        } else { // money
            Map<UUID, Double> topMoney = psm.getTopMoneyEarned(limit);
            if (topMoney.isEmpty()) {
                sender.sendMessage(getRawMsg("leaderboard_empty", Map.of("type", "money earned")));
                return true;
            }
            for (Map.Entry<UUID, Double> entry : topMoney.entrySet()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                String formattedMoney = ChickenHunt.getEconomy() != null ? ChickenHunt.getEconomy().format(entry.getValue()) : String.format("%.2f", entry.getValue());
                sender.sendMessage(getRawMsg("leaderboard_entry_money", Map.of(
                        "rank", String.valueOf(rank++),
                        "player", player.getName() != null ? player.getName() : "Unknown",
                        "amount", formattedMoney
                )));
            }
        }
        return true;
    }

    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }


    private void sendHelp(CommandSender sender) {
        if (sender.hasPermission("chickenhunt.admin")) { 
            sender.sendMessage(ChatColor.GOLD + "--- ChickenHunt Admin Commands ---");
            if (sender.hasPermission("chickenhunt.admin.wand")) sender.sendMessage(ChatColor.YELLOW + "/ch wand" + ChatColor.GRAY + " - Get the region selection wand.");
            if (sender.hasPermission("chickenhunt.admin.create")) sender.sendMessage(ChatColor.YELLOW + "/ch create <name>" + ChatColor.GRAY + " - Create a new region.");
            if (sender.hasPermission("chickenhunt.admin.delete")) sender.sendMessage(ChatColor.YELLOW + "/ch delete <name>" + ChatColor.GRAY + " - Delete a region.");
            if (sender.hasPermission("chickenhunt.admin.list")) sender.sendMessage(ChatColor.YELLOW + "/ch list" + ChatColor.GRAY + " - List all regions.");
            if (sender.hasPermission("chickenhunt.admin.start")) sender.sendMessage(ChatColor.YELLOW + "/ch start <name> [duration]" + ChatColor.GRAY + " - Start game in a region.");
            if (sender.hasPermission("chickenhunt.admin.stop")) sender.sendMessage(ChatColor.YELLOW + "/ch stop <name>" + ChatColor.GRAY + " - Stop game in a region.");
            if (sender.hasPermission("chickenhunt.admin.reload")) sender.sendMessage(ChatColor.YELLOW + "/ch reload" + ChatColor.GRAY + " - Reload plugin configuration.");
            sender.sendMessage(ChatColor.YELLOW + "/ch help" + ChatColor.GRAY + " - Shows this help message.");
        } else if (sender.hasPermission("chickenhunt.player.help") || sender.hasPermission("chickenhunt.use")) { 
            sender.sendMessage(getRawMsg("help_header_player", null));
            if (sender.hasPermission("chickenhunt.player.sell")) sender.sendMessage(getRawMsg("help_sell", null));
            if (sender.hasPermission("chickenhunt.player.status")) sender.sendMessage(getRawMsg("help_status", null));
            if (sender.hasPermission("chickenhunt.player.top")) sender.sendMessage(getRawMsg("help_top", null)); // Pastikan baris ini tidak dikomentari
            sender.sendMessage(getRawMsg("help_player_command", null));
        } else {
            sender.sendMessage(getMsg("no_permission", null));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList("wand", "create", "delete", "list", "start", "stop", "reload", "help", "sell", "top", "status"));
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .filter(s -> {
                        if (s.equals("wand") && !sender.hasPermission("chickenhunt.admin.wand")) return false;
                        if (s.equals("create") && !sender.hasPermission("chickenhunt.admin.create")) return false;
                        if (s.equals("delete") && !sender.hasPermission("chickenhunt.admin.delete")) return false;
                        if (s.equals("list") && !sender.hasPermission("chickenhunt.admin.list")) return false;
                        if (s.equals("start") && !sender.hasPermission("chickenhunt.admin.start")) return false;
                        if (s.equals("stop") && !sender.hasPermission("chickenhunt.admin.stop")) return false;
                        if (s.equals("reload") && !sender.hasPermission("chickenhunt.admin.reload")) return false;
                        if (s.equals("sell") && !sender.hasPermission("chickenhunt.player.sell")) return false;
                        if (s.equals("top") && !sender.hasPermission("chickenhunt.player.top")) return false;
                        if (s.equals("status") && !sender.hasPermission("chickenhunt.player.status")) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("create")) {
                return Collections.singletonList("<regionName>");
            } else if (subCommand.equals("delete") || subCommand.equals("start") || subCommand.equals("stop") || subCommand.equals("status")) {
                return plugin.getRegionManager().getRegionNames().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    private boolean handleScheduleCommand(CommandSender sender) {
        if (!sender.hasPermission("chickenhunt.player.schedule")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }
        
        GameScheduler scheduler = plugin.getGameScheduler();
        if (scheduler == null || !plugin.getConfig().getBoolean("auto-scheduler.enabled", false)) {
            sender.sendMessage(ChatColor.RED + "Game scheduler tidak aktif.");
            return true;
        }
        
        long nextGameTime = scheduler.getNextGameTime();
        if (nextGameTime <= System.currentTimeMillis()) {
            sender.sendMessage(ChatColor.YELLOW + "Game berikutnya akan dijadwalkan segera.");
            return true;
        }
        
        long remainingMillis = nextGameTime - System.currentTimeMillis();
        long remainingMinutes = remainingMillis / (60 * 1000);
        long remainingSeconds = (remainingMillis / 1000) % 60;
        
        sender.sendMessage(ChatColor.GREEN + "Game ChickenHunt berikutnya akan dimulai dalam " + 
                           ChatColor.YELLOW + remainingMinutes + " menit " + remainingSeconds + " detik.");
        if (scheduler.getActiveRegion() != null) {
            sender.sendMessage(ChatColor.GREEN + "Region: " + ChatColor.YELLOW + scheduler.getActiveRegion());
        }
        
        return true;
    }
}