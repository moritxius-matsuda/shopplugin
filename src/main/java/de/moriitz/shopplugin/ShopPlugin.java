package de.moriitz.shopplugin;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionType;
import org.bukkit.potion.PotionData;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;
import java.io.IOException;

public class ShopPlugin extends JavaPlugin {

    private final Map<String, MerchantRecipe[]> shops = new HashMap<>();
    private final Map<String, String> shopDisplayNames = new HashMap<>();
    private final Map<String, Map<Integer, Long>> tradeRestockTimes = new HashMap<>(); // shopName -> tradeIndex -> lastRestockTime
    private final Map<String, Integer> shopRestockIntervals = new HashMap<>(); // shopName -> restockIntervalHours
    
    // Plugin Info
    private static final String PLUGIN_VERSION = "1.4.3";
    private static final String PLUGIN_AUTHOR = "Moritz Meier im Auftrag von Matsuda Béla";
    
    // File management
    private File shopsFolder;

    @Override
    public void onEnable() {
        getLogger().info("ShopPlugin enabled!");
        
        // Create shops folder
        shopsFolder = new File(getDataFolder(), "shops");
        if (!shopsFolder.exists()) {
            shopsFolder.mkdirs();
        }
        
        // Register command
        ShopCommand shopCommand = new ShopCommand();
        getCommand("shop").setExecutor(shopCommand);
        getCommand("shop").setTabCompleter(shopCommand);
        
        // Load shops from files
        loadAllShops();
        
        // Initialize default shops if none exist
        if (shops.isEmpty()) {
            initializeDefaultShops();
        }
        
        // Initialize bStats
        initializeBStats();
    }
    
    @Override
    public void onDisable() {
        // Save all shops when plugin disables
        saveAllShops();
        getLogger().info("ShopPlugin disabled!");
    }

    private void initializeDefaultShops() {
        // Example shop "general"
        List<MerchantRecipe> generalShopList = new ArrayList<>();
        
        // Diamond trade: 1 Emerald -> 1 Diamond
        MerchantRecipe diamondTrade = new MerchantRecipe(new ItemStack(Material.DIAMOND), 32);
        diamondTrade.addIngredient(new ItemStack(Material.EMERALD));
        diamondTrade.setVillagerExperience(20);
        diamondTrade.setPriceMultiplier(0.05f);
        generalShopList.add(diamondTrade);
        
        // Gold trade: 1 Emerald -> 4 Gold Ingots
        MerchantRecipe goldTrade = new MerchantRecipe(new ItemStack(Material.GOLD_INGOT, 4), 1);
        goldTrade.addIngredient(new ItemStack(Material.EMERALD));
        goldTrade.setVillagerExperience(10);
        goldTrade.setPriceMultiplier(0.05f);
        generalShopList.add(goldTrade);
        
        // Bread trade: 1 Emerald -> 3 Bread
        MerchantRecipe breadTrade = new MerchantRecipe(new ItemStack(Material.BREAD, 3), 1);
        breadTrade.addIngredient(new ItemStack(Material.EMERALD));
        breadTrade.setVillagerExperience(5);
        breadTrade.setPriceMultiplier(0.05f);
        generalShopList.add(breadTrade);
        
        shops.put("general", generalShopList.toArray(new MerchantRecipe[0]));
        shopDisplayNames.put("general", "§6§lGeneral Store");
        
        // Save the default shop
        saveShop("general");
    }
    
    private void loadAllShops() {
        if (!shopsFolder.exists()) {
            return;
        }
        
        File[] shopFiles = shopsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (shopFiles == null) {
            return;
        }
        
        for (File shopFile : shopFiles) {
            String shopName = shopFile.getName().replace(".yml", "");
            loadShop(shopName);
        }
        
        getLogger().info("Loaded " + shops.size() + " shops from files.");
    }
    
    private void saveAllShops() {
        for (String shopName : shops.keySet()) {
            saveShop(shopName);
        }
        getLogger().info("Saved " + shops.size() + " shops to files.");
    }
    
    private void loadShop(String shopName) {
        File shopFile = new File(shopsFolder, shopName + ".yml");
        if (!shopFile.exists()) {
            return;
        }
        
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(shopFile);
            
            // Load display name
            String displayName = config.getString("display-name", "§6" + shopName);
            shopDisplayNames.put(shopName.toLowerCase(), displayName);
            
            // Load restock interval
            int restockHours = config.getInt("restock-hours", 24);
            shopRestockIntervals.put(shopName.toLowerCase(), restockHours);
            
            // Load restock times
            Map<Integer, Long> restockTimes = new HashMap<>();
            if (config.contains("restock-times")) {
                for (String key : config.getConfigurationSection("restock-times").getKeys(false)) {
                    int tradeIndex = Integer.parseInt(key);
                    long restockTime = config.getLong("restock-times." + key);
                    restockTimes.put(tradeIndex, restockTime);
                }
            }
            tradeRestockTimes.put(shopName.toLowerCase(), restockTimes);
            
            // Load trades
            List<Map<?, ?>> tradesList = config.getMapList("trades");
            List<MerchantRecipe> merchantRecipes = new ArrayList<>();
            
            for (Map<?, ?> tradeMap : tradesList) {
                try {
                    String inputMaterial = (String) tradeMap.get("input-material");
                    int inputAmount = (Integer) tradeMap.get("input-amount");
                    String outputMaterial = (String) tradeMap.get("output-material");
                    int outputAmount = (Integer) tradeMap.get("output-amount");
                    int maxUses = (Integer) tradeMap.get("max-uses");
                    
                    // Create input item with NBT
                    String inputDisplayName = (String) tradeMap.get("input-display-name");
                    String inputEnchantments = (String) tradeMap.get("input-enchantments");
                    String inputPotionType = (String) tradeMap.get("input-potion-type");
                    ItemStack inputItem = createItemWithNBT(Material.valueOf(inputMaterial), inputAmount, 
                                                           inputDisplayName, inputEnchantments, inputPotionType);
                    
                    // Create output item with NBT
                    String outputDisplayName = (String) tradeMap.get("output-display-name");
                    String outputEnchantments = (String) tradeMap.get("output-enchantments");
                    String outputPotionType = (String) tradeMap.get("output-potion-type");
                    ItemStack outputItem = createItemWithNBT(Material.valueOf(outputMaterial), outputAmount, 
                                                            outputDisplayName, outputEnchantments, outputPotionType);
                    
                    MerchantRecipe recipe = new MerchantRecipe(outputItem, maxUses);
                    recipe.addIngredient(inputItem);
                    recipe.setVillagerExperience(10);
                    recipe.setPriceMultiplier(0.05f);
                    
                    merchantRecipes.add(recipe);
                } catch (Exception e) {
                    getLogger().warning("Failed to load trade from shop " + shopName + ": " + e.getMessage());
                }
            }
            
            shops.put(shopName.toLowerCase(), merchantRecipes.toArray(new MerchantRecipe[0]));
            
        } catch (Exception e) {
            getLogger().severe("Failed to load shop " + shopName + ": " + e.getMessage());
        }
    }
    
    private void saveShop(String shopName) {
        File shopFile = new File(shopsFolder, shopName + ".yml");
        FileConfiguration config = new YamlConfiguration();
        
        // Save display name
        String displayName = shopDisplayNames.get(shopName.toLowerCase());
        if (displayName != null) {
            config.set("display-name", displayName);
        }
        
        // Save restock interval
        Integer restockHours = shopRestockIntervals.get(shopName.toLowerCase());
        if (restockHours != null) {
            config.set("restock-hours", restockHours);
        }
        
        // Save restock times
        Map<Integer, Long> restockTimes = tradeRestockTimes.get(shopName.toLowerCase());
        if (restockTimes != null && !restockTimes.isEmpty()) {
            for (Map.Entry<Integer, Long> entry : restockTimes.entrySet()) {
                config.set("restock-times." + entry.getKey(), entry.getValue());
            }
        }
        
        // Save trades
        MerchantRecipe[] trades = shops.get(shopName.toLowerCase());
        if (trades != null) {
            List<Map<String, Object>> tradesList = new ArrayList<>();
            
            for (MerchantRecipe trade : trades) {
                Map<String, Object> tradeMap = new HashMap<>();
                
                ItemStack input = trade.getIngredients().get(0);
                ItemStack output = trade.getResult();
                
                // Save basic item data
                tradeMap.put("input-material", input.getType().name());
                tradeMap.put("input-amount", input.getAmount());
                tradeMap.put("output-material", output.getType().name());
                tradeMap.put("output-amount", output.getAmount());
                tradeMap.put("max-uses", trade.getMaxUses());
                
                // Save NBT data for input
                if (input.hasItemMeta()) {
                    ItemMeta inputMeta = input.getItemMeta();
                    if (inputMeta.hasDisplayName()) {
                        tradeMap.put("input-display-name", inputMeta.getDisplayName());
                    }
                    if (inputMeta.hasEnchants()) {
                        tradeMap.put("input-enchantments", enchantmentsToString(inputMeta.getEnchants()));
                    }
                    if (inputMeta instanceof PotionMeta) {
                        PotionMeta potionMeta = (PotionMeta) inputMeta;
                        if (potionMeta.getBasePotionData() != null) {
                            tradeMap.put("input-potion-type", potionDataToString(potionMeta.getBasePotionData()));
                        }
                    }
                }
                
                // Save NBT data for output
                if (output.hasItemMeta()) {
                    ItemMeta outputMeta = output.getItemMeta();
                    if (outputMeta.hasDisplayName()) {
                        tradeMap.put("output-display-name", outputMeta.getDisplayName());
                    }
                    if (outputMeta.hasEnchants()) {
                        tradeMap.put("output-enchantments", enchantmentsToString(outputMeta.getEnchants()));
                    }
                    if (outputMeta instanceof PotionMeta) {
                        PotionMeta potionMeta = (PotionMeta) outputMeta;
                        if (potionMeta.getBasePotionData() != null) {
                            tradeMap.put("output-potion-type", potionDataToString(potionMeta.getBasePotionData()));
                        }
                    }
                }
                
                tradesList.add(tradeMap);
            }
            
            config.set("trades", tradesList);
        }
        
        try {
            config.save(shopFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save shop " + shopName + ": " + e.getMessage());
        }
    }
    
    private void deleteShopFile(String shopName) {
        File shopFile = new File(shopsFolder, shopName + ".yml");
        if (shopFile.exists()) {
            shopFile.delete();
        }
    }
    
    public void createShop(String shopName) {
        createShop(shopName, null);
    }
    
    public void createShop(String shopName, String displayName) {
        createShop(shopName, displayName, 24); // Default 24 hours restock
    }
    
    public void createShop(String shopName, String displayName, int restockHours) {
        String shopKey = shopName.toLowerCase();
        if (!shops.containsKey(shopKey)) {
            shops.put(shopKey, new MerchantRecipe[0]);
            if (displayName != null && !displayName.isEmpty()) {
                shopDisplayNames.put(shopKey, displayName.replace('&', '§'));
            } else {
                shopDisplayNames.put(shopKey, "§6" + shopName);
            }
            shopRestockIntervals.put(shopKey, restockHours);
            tradeRestockTimes.put(shopKey, new HashMap<>());
            saveShop(shopKey);
        }
    }
    
    public void setShopDisplayName(String shopName, String displayName) {
        String shopKey = shopName.toLowerCase();
        if (shops.containsKey(shopKey)) {
            shopDisplayNames.put(shopKey, displayName.replace('&', '§'));
            saveShop(shopKey);
        }
    }
    
    public String getShopDisplayName(String shopName) {
        String shopKey = shopName.toLowerCase();
        return shopDisplayNames.getOrDefault(shopKey, "§6" + shopName);
    }
    
    public void addTradeToShop(String shopName, Material inputMaterial, int inputAmount, 
                              Material outputMaterial, int outputAmount, int maxUses) {
        addTradeToShop(shopName, inputMaterial, inputAmount, outputMaterial, outputAmount, maxUses, null, null, null, null);
    }
    
    public void addTradeToShop(String shopName, Material inputMaterial, int inputAmount, 
                              Material outputMaterial, int outputAmount, int maxUses,
                              String inputDisplayName, String outputDisplayName,
                              String inputEnchantments, String outputEnchantments) {
        addTradeToShop(shopName, inputMaterial, inputAmount, outputMaterial, outputAmount, maxUses,
                      inputDisplayName, outputDisplayName, inputEnchantments, outputEnchantments, null, null);
    }
    
    public void addTradeToShop(String shopName, Material inputMaterial, int inputAmount, 
                              Material outputMaterial, int outputAmount, int maxUses,
                              String inputDisplayName, String outputDisplayName,
                              String inputEnchantments, String outputEnchantments,
                              String inputPotionType, String outputPotionType) {
        String shopKey = shopName.toLowerCase();
        if (!shops.containsKey(shopKey)) {
            createShop(shopName);
        }
        
        // Erstelle Input Item mit NBT
        ItemStack inputItem = createItemWithNBT(inputMaterial, inputAmount, inputDisplayName, inputEnchantments, inputPotionType);
        
        // Erstelle Output Item mit NBT
        ItemStack outputItem = createItemWithNBT(outputMaterial, outputAmount, outputDisplayName, outputEnchantments, outputPotionType);
        
        // Erstelle neuen Trade
        MerchantRecipe trade = new MerchantRecipe(outputItem, maxUses);
        trade.addIngredient(inputItem);
        trade.setVillagerExperience(10);
        trade.setPriceMultiplier(0.05f);
        
        // Füge Trade zu existierenden Trades hinzu
        MerchantRecipe[] existingTrades = shops.get(shopKey);
        MerchantRecipe[] newTrades = new MerchantRecipe[existingTrades.length + 1];
        System.arraycopy(existingTrades, 0, newTrades, 0, existingTrades.length);
        newTrades[existingTrades.length] = trade;
        
        shops.put(shopKey, newTrades);
        saveShop(shopKey);
    }
    
    private void applyEnchantments(ItemStack item, String enchantmentString) {
        if (enchantmentString == null || enchantmentString.isEmpty()) {
            return;
        }
        
        String[] enchantments = enchantmentString.split(",");
        for (String enchantment : enchantments) {
            String[] parts = enchantment.trim().split(":");
            if (parts.length == 2) {
                try {
                    Enchantment ench = Enchantment.getByName(parts[0].toUpperCase());
                    int level = Integer.parseInt(parts[1]);
                    if (ench != null) {
                        item.addUnsafeEnchantment(ench, level);
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid enchantment format
                }
            }
        }
    }
    
    private String enchantmentsToString(Map<Enchantment, Integer> enchantments) {
        if (enchantments.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append(entry.getKey().getName()).append(":").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }
    
    private ItemStack createItemWithNBT(Material material, int amount, String displayName, String enchantments, String potionType) {
        ItemStack item = new ItemStack(material, amount);
        
        // Apply display name
        if (displayName != null && !displayName.isEmpty()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(displayName.replace('&', '§'));
                item.setItemMeta(meta);
            }
        }
        
        // Apply enchantments
        if (enchantments != null && !enchantments.isEmpty()) {
            applyEnchantments(item, enchantments);
        }
        
        // Apply potion effects
        if (potionType != null && !potionType.isEmpty() && isPotionMaterial(material)) {
            applyPotionEffect(item, potionType);
        }
        
        return item;
    }
    
    private boolean isPotionMaterial(Material material) {
        return material == Material.POTION || material == Material.SPLASH_POTION || material == Material.LINGERING_POTION;
    }
    
    private void applyPotionEffect(ItemStack item, String potionTypeString) {
        if (!isPotionMaterial(item.getType())) {
            return;
        }
        
        try {
            // Parse potion type (format: "HEALING" or "HEALING:2" for level 2)
            String[] parts = potionTypeString.toUpperCase().split(":");
            String potionName = parts[0];
            boolean extended = false;
            boolean upgraded = false;
            
            if (parts.length > 1) {
                String modifier = parts[1];
                if (modifier.equals("EXTENDED") || modifier.equals("LONG")) {
                    extended = true;
                } else if (modifier.equals("UPGRADED") || modifier.equals("2") || modifier.equals("STRONG")) {
                    upgraded = true;
                }
            }
            
            PotionType potionType = PotionType.valueOf(potionName);
            PotionMeta potionMeta = (PotionMeta) item.getItemMeta();
            if (potionMeta != null) {
                potionMeta.setBasePotionData(new PotionData(potionType, extended, upgraded));
                item.setItemMeta(potionMeta);
            }
        } catch (IllegalArgumentException e) {
            // Invalid potion type, ignore
        }
    }
    
    private String potionDataToString(PotionData potionData) {
        if (potionData == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(potionData.getType().name());
        
        if (potionData.isExtended()) {
            sb.append(":EXTENDED");
        } else if (potionData.isUpgraded()) {
            sb.append(":UPGRADED");
        }
        
        return sb.toString();
    }
    
    private void restockTrades(String shopName) {
        String shopKey = shopName.toLowerCase();
        MerchantRecipe[] trades = shops.get(shopKey);
        if (trades == null) return;
        
        Map<Integer, Long> restockTimes = tradeRestockTimes.get(shopKey);
        if (restockTimes == null) {
            restockTimes = new HashMap<>();
            tradeRestockTimes.put(shopKey, restockTimes);
        }
        
        Integer restockHours = shopRestockIntervals.get(shopKey);
        if (restockHours == null) restockHours = 24;
        
        long currentTime = System.currentTimeMillis();
        long restockInterval = restockHours * 60 * 60 * 1000L; // Convert hours to milliseconds
        
        boolean tradesRestocked = false;
        
        for (int i = 0; i < trades.length; i++) {
            MerchantRecipe trade = trades[i];
            
            // Check if trade needs restocking
            if (trade.getUses() >= trade.getMaxUses()) {
                Long lastRestockTime = restockTimes.get(i);
                
                if (lastRestockTime == null || (currentTime - lastRestockTime) >= restockInterval) {
                    // Restock this trade
                    trade.setUses(0);
                    restockTimes.put(i, currentTime);
                    tradesRestocked = true;
                }
            }
        }
        
        if (tradesRestocked) {
            saveShop(shopKey);
        }
    }
    
    public void setShopRestockInterval(String shopName, int hours) {
        String shopKey = shopName.toLowerCase();
        if (shops.containsKey(shopKey)) {
            shopRestockIntervals.put(shopKey, hours);
            saveShop(shopKey);
        }
    }
    
    public int getShopRestockInterval(String shopName) {
        String shopKey = shopName.toLowerCase();
        return shopRestockIntervals.getOrDefault(shopKey, 24);
    }
    
    private void initializeBStats() {
        // Plugin ID from bStats: 26220
        Metrics metrics = new Metrics(this, 26220);
        
        // Add custom charts
        
        // Chart: Number of shops
        metrics.addCustomChart(new SingleLineChart("shops_count", () -> shops.size()));
        
        // Chart: Total trades count
        metrics.addCustomChart(new SingleLineChart("total_trades", () -> {
            int totalTrades = 0;
            for (MerchantRecipe[] shopTrades : shops.values()) {
                totalTrades += shopTrades.length;
            }
            return totalTrades;
        }));
        
        // Chart: Server type (based on server software)
        metrics.addCustomChart(new SimplePie("server_type", () -> {
            String serverVersion = Bukkit.getVersion().toLowerCase();
            if (serverVersion.contains("paper")) {
                return "Paper";
            } else if (serverVersion.contains("spigot")) {
                return "Spigot";
            } else if (serverVersion.contains("bukkit")) {
                return "Bukkit";
            } else {
                return "Other";
            }
        }));
        
        // Chart: Average restock time
        metrics.addCustomChart(new SimplePie("average_restock_time", () -> {
            if (shopRestockIntervals.isEmpty()) {
                return "24 hours";
            }
            
            double average = shopRestockIntervals.values().stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(24.0);
                
            if (average <= 6) {
                return "≤6 hours";
            } else if (average <= 12) {
                return "6-12 hours";
            } else if (average <= 24) {
                return "12-24 hours";
            } else {
                return ">24 hours";
            }
        }));
        
        // Chart: Plugin usage intensity
        metrics.addCustomChart(new SimplePie("usage_intensity", () -> {
            int totalTrades = 0;
            for (MerchantRecipe[] shopTrades : shops.values()) {
                totalTrades += shopTrades.length;
            }
            
            if (totalTrades == 0) {
                return "No trades";
            } else if (totalTrades <= 10) {
                return "Light (1-10 trades)";
            } else if (totalTrades <= 50) {
                return "Medium (11-50 trades)";
            } else if (totalTrades <= 100) {
                return "Heavy (51-100 trades)";
            } else {
                return "Very Heavy (100+ trades)";
            }
        }));
        
        getLogger().info("bStats metrics initialized successfully!");
    }
    
    public boolean removeShop(String shopName) {
        String shopKey = shopName.toLowerCase();
        shopDisplayNames.remove(shopKey);
        boolean removed = shops.remove(shopKey) != null;
        if (removed) {
            deleteShopFile(shopKey);
        }
        return removed;
    }
    
    public boolean removeTradeFromShop(String shopName, int tradeIndex) {
        String shopKey = shopName.toLowerCase();
        if (!shops.containsKey(shopKey)) {
            return false;
        }
        
        MerchantRecipe[] existingTrades = shops.get(shopKey);
        if (tradeIndex < 0 || tradeIndex >= existingTrades.length) {
            return false;
        }
        
        // Erstelle neues Array ohne den zu löschenden Trade
        MerchantRecipe[] newTrades = new MerchantRecipe[existingTrades.length - 1];
        int newIndex = 0;
        for (int i = 0; i < existingTrades.length; i++) {
            if (i != tradeIndex) {
                newTrades[newIndex++] = existingTrades[i];
            }
        }
        
        shops.put(shopKey, newTrades);
        saveShop(shopKey);
        return true;
    }
    
    public MerchantRecipe[] getShopTrades(String shopName) {
        return shops.get(shopName.toLowerCase());
    }
    
    // Permissions Helper
    private boolean hasPermission(CommandSender sender, String permission) {
        return sender.hasPermission(permission) || sender.isOp();
    }
    
    private boolean canUseShop(CommandSender sender, String shopName) {
        return hasPermission(sender, "shopplugin.shop.use") || 
               hasPermission(sender, "shopplugin.shop." + shopName.toLowerCase());
    }
    
    private boolean canManageShops(CommandSender sender) {
        return hasPermission(sender, "shopplugin.admin");
    }

    private class ShopCommand implements CommandExecutor, TabCompleter {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length < 1) {
                sender.sendMessage("§6=== ShopPlugin Commands ===");
                sender.sendMessage("§e/shop <shopname> §7- Open shop");
                sender.sendMessage("§e/shop list §7- List all shops");
                sender.sendMessage("§e/shop info §7- Plugin information");
                if (canManageShops(sender)) {
                    sender.sendMessage("§c=== Admin Commands ===");
                    sender.sendMessage("§c/shop create <shopname> [display_name] §7- Create new shop");
                    sender.sendMessage("§c/shop addtrade <shopname> <input_material> <input_amount> <output_material> <output_amount> [max_uses] §7- Add trade");
                    sender.sendMessage("§c/shop addpotion <shopname> <input_material> <input_amount> <potion_type> [max_uses] §7- Add potion trade");
                    sender.sendMessage("§c/shop addtradeadvanced <shopname> <input_material> <input_amount> <output_material> <output_amount> [max_uses] [input_name] [output_name] [input_enchants] [output_enchants] [input_potion] [output_potion] §7- Add advanced trade");
                    sender.sendMessage("§c/shop removetrade <shopname> <trade_index> §7- Remove trade");
                    sender.sendMessage("§c/shop rename <shopname> <new_display_name> §7- Rename shop display");
                    sender.sendMessage("§c/shop trades <shopname> §7- List all trades in shop");
                    sender.sendMessage("§c/shop remove <shopname> §7- Remove shop");
                    sender.sendMessage("§c/shop potions §7- List all available potion types");
                }
                return true;
            }

            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "info":
                    return handleInfo(sender);
                case "create":
                    return handleCreateShop(sender, args);
                case "addtrade":
                    return handleAddTrade(sender, args);
                case "addpotion":
                    return handleAddPotion(sender, args);
                case "addtradeadvanced":
                    return handleAddTradeAdvanced(sender, args);
                case "removetrade":
                    return handleRemoveTrade(sender, args);
                case "potions":
                    return handleListPotions(sender);
                case "restock":
                    return handleRestock(sender, args);
                case "rename":
                    return handleRenameShop(sender, args);
                case "trades":
                    return handleListTrades(sender, args);
                case "remove":
                    return handleRemoveShop(sender, args);
                case "list":
                    return handleListShops(sender);
                default:
                    return handleOpenShop(sender, args);
            }
        }
        
        private boolean handleInfo(CommandSender sender) {
            sender.sendMessage("§6=== ShopPlugin Info ===");
            sender.sendMessage("§eVersion: §f" + PLUGIN_VERSION);
            sender.sendMessage("§eAuthor: §f" + PLUGIN_AUTHOR);
            sender.sendMessage("§eShops: §f" + shops.size());
            sender.sendMessage("§6=== Commands ===");
            sender.sendMessage("§e/shop <shopname> §7- Open shop");
            sender.sendMessage("§e/shop list §7- List all shops");
            sender.sendMessage("§e/shop info §7- Plugin information");
            if (canManageShops(sender)) {
                sender.sendMessage("§c/shop create <shopname> [display_name] §7- Create shop");
                sender.sendMessage("§c/shop addtrade <shop> <input> <amount> <output> <amount> [max_uses] §7- Add trade");
                sender.sendMessage("§c/shop addpotion <shop> <input> <amount> <potion_type> [max_uses] §7- Add potion trade");
                sender.sendMessage("§c/shop addtradeadvanced <shop> <input> <amount> <output> <amount> [max_uses] [input_name] [output_name] [input_enchants] [output_enchants] [input_potion] [output_potion] §7- Add advanced trade");
                sender.sendMessage("§c/shop removetrade <shopname> <trade_index> §7- Remove trade");
                sender.sendMessage("§c/shop rename <shopname> <new_display_name> §7- Rename shop");
                sender.sendMessage("§c/shop trades <shopname> §7- List trades");
                sender.sendMessage("§c/shop remove <shopname> §7- Remove shop");
                sender.sendMessage("§c/shop potions §7- List available potion types");
                sender.sendMessage("§c/shop restock <shopname> [hours] §7- Set/view restock interval");
            }
            return true;
        }
        
        private boolean handleOpenShop(CommandSender sender, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cThis command can only be used by players!");
                return true;
            }

            Player player = (Player) sender;
            String shopName = args[0].toLowerCase();
            
            // Permission check
            if (!canUseShop(sender, shopName)) {
                player.sendMessage("§cYou don't have permission to use this shop!");
                return true;
            }
            
            MerchantRecipe[] recipes = shops.get(shopName);

            if (recipes == null) {
                player.sendMessage("§cShop '" + shopName + "' not found!");
                return true;
            }
            
            if (recipes.length == 0) {
                player.sendMessage("§cShop '" + shopName + "' has no trades yet!");
                return true;
            }

            // Create a virtual merchant without spawning a villager
            String displayName = getShopDisplayName(shopName);
            Merchant merchant = Bukkit.createMerchant(displayName);
            merchant.setRecipes(Arrays.asList(recipes));
            player.openMerchant(merchant, true);
            
            return true;
        }
        
        private boolean handleCreateShop(CommandSender sender, String[] args) {
            if (!canManageShops(sender)) {
                sender.sendMessage("§cYou don't have permission to manage shops!");
                return true;
            }
            
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /shop create <shopname> [display_name]");
                return true;
            }
            
            String shopName = args[1].toLowerCase();
            if (shops.containsKey(shopName)) {
                sender.sendMessage("§cShop '" + shopName + "' already exists!");
                return true;
            }
            
            String displayName = null;
            if (args.length > 2) {
                // Alle weiteren Argumente als Display-Name zusammenfügen
                StringBuilder sb = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    if (i > 2) sb.append(" ");
                    sb.append(args[i]);
                }
                displayName = sb.toString();
            }
            
            createShop(shopName, displayName);
            sender.sendMessage("§aShop '" + shopName + "' created successfully!");
            if (displayName != null) {
                sender.sendMessage("§aDisplay name: " + displayName.replace('&', '§'));
            }
            return true;
        }
        
        private boolean handleAddTrade(CommandSender sender, String[] args) {
            if (!canManageShops(sender)) {
                sender.sendMessage("§cYou don't have permission to manage shops!");
                return true;
            }
            
            if (args.length < 6) {
                sender.sendMessage("§cUsage: /shop addtrade <shopname> <input_material> <input_amount> <output_material> <output_amount> [max_uses]");
                return true;
            }
            
            try {
                String shopName = args[1].toLowerCase();
                Material inputMaterial = Material.valueOf(args[2].toUpperCase());
                int inputAmount = Integer.parseInt(args[3]);
                Material outputMaterial = Material.valueOf(args[4].toUpperCase());
                int outputAmount = Integer.parseInt(args[5]);
                int maxUses = args.length > 6 ? Integer.parseInt(args[6]) : 10;
                
                addTradeToShop(shopName, inputMaterial, inputAmount, outputMaterial, outputAmount, maxUses);
                sender.sendMessage("§aTrade added to shop '" + shopName + "': " + inputAmount + " " + inputMaterial.name() + " -> " + outputAmount + " " + outputMaterial.name());
                return true;
                
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§cInvalid material or number! Check your input.");
                return true;
            }
        }
        
        private boolean handleAddPotion(CommandSender sender, String[] args) {
            if (!canManageShops(sender)) {
                sender.sendMessage("§cYou don't have permission to manage shops!");
                return true;
            }
            
            if (args.length < 5) {
                sender.sendMessage("§cUsage: /shop addpotion <shopname> <input_material> <input_amount> <potion_type> [max_uses]");
                sender.sendMessage("§eExample: /shop addpotion general EMERALD 1 HEALING 10");
                sender.sendMessage("§ePotion Types: HEALING, REGENERATION, SPEED, FIRE_RESISTANCE, POISON, etc.");
                sender.sendMessage("§eModifiers: Add :EXTENDED for longer duration or :UPGRADED for stronger effect");
                sender.sendMessage("§eExample with modifier: /shop addpotion general EMERALD 2 HEALING:UPGRADED 5");
                return true;
            }
            
            try {
                String shopName = args[1].toLowerCase();
                Material inputMaterial = Material.valueOf(args[2].toUpperCase());
                int inputAmount = Integer.parseInt(args[3]);
                String potionType = args[4].toUpperCase();
                int maxUses = args.length > 5 ? Integer.parseInt(args[5]) : 10;
                
                // Create a nice display name for the potion
                String potionDisplayName = "§b" + formatPotionName(potionType) + " Potion";
                
                addTradeToShop(shopName, inputMaterial, inputAmount, Material.POTION, 1, maxUses, 
                              null, potionDisplayName, null, null, null, potionType);
                
                sender.sendMessage("§aPotion trade added to shop '" + shopName + "'!");
                sender.sendMessage("§7Input: " + inputAmount + " " + inputMaterial.name());
                sender.sendMessage("§7Output: " + potionDisplayName);
                return true;
                
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§cInvalid material, potion type, or number! Check your input.");
                sender.sendMessage("§eUse /shop potions to see available potion types.");
                return true;
            }
        }
        
        private boolean handleListPotions(CommandSender sender) {
            sender.sendMessage("§6=== Available Potion Types ===");
            sender.sendMessage("§e§lBasic Potions:");
            sender.sendMessage("§f- HEALING §7(Instant Health)");
            sender.sendMessage("§f- HARMING §7(Instant Damage)");
            sender.sendMessage("§f- REGENERATION §7(Regeneration)");
            sender.sendMessage("§f- POISON §7(Poison)");
            sender.sendMessage("§f- STRENGTH §7(Strength)");
            sender.sendMessage("§f- WEAKNESS §7(Weakness)");
            sender.sendMessage("§f- SPEED §7(Speed)");
            sender.sendMessage("§f- SLOWNESS §7(Slowness)");
            sender.sendMessage("§f- JUMP §7(Jump Boost)");
            sender.sendMessage("§f- FIRE_RESISTANCE §7(Fire Resistance)");
            sender.sendMessage("§f- WATER_BREATHING §7(Water Breathing)");
            sender.sendMessage("§f- INVISIBILITY §7(Invisibility)");
            sender.sendMessage("§f- NIGHT_VISION §7(Night Vision)");
            sender.sendMessage("§e§lModifiers:");
            sender.sendMessage("§f- Add §c:EXTENDED §ffor longer duration");
            sender.sendMessage("§f- Add §c:UPGRADED §ffor stronger effect");
            sender.sendMessage("§eExample: §fHEALING:UPGRADED §7for Healing II");
            return true;
        }
        
        private String formatPotionName(String potionType) {
            String[] parts = potionType.split(":");
            String baseName = parts[0].toLowerCase().replace("_", " ");
            String[] words = baseName.split(" ");
            StringBuilder formatted = new StringBuilder();
            
            for (String word : words) {
                if (formatted.length() > 0) formatted.append(" ");
                formatted.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
            }
            
            if (parts.length > 1) {
                String modifier = parts[1];
                if (modifier.equals("EXTENDED")) {
                    formatted.append(" (Extended)");
                } else if (modifier.equals("UPGRADED")) {
                    formatted.append(" II");
                }
            }
            
            return formatted.toString();
        }
        
        private boolean handleRestock(CommandSender sender, String[] args) {
            if (!canManageShops(sender)) {
                sender.sendMessage("§cYou don't have permission to manage shops!");
                return true;
            }
            
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /shop restock <shopname> [hours]");
                sender.sendMessage("§eExample: /shop restock general 12");
                sender.sendMessage("§7Current intervals:");
                for (String shopName : shops.keySet()) {
                    int hours = getShopRestockInterval(shopName);
                    sender.sendMessage("§f- " + shopName + ": §e" + hours + " hours");
                }
                return true;
            }
            
            String shopName = args[1].toLowerCase();
            if (!shops.containsKey(shopName)) {
                sender.sendMessage("§cShop '" + shopName + "' not found!");
                return true;
            }
            
            if (args.length >= 3) {
                try {
                    int hours = Integer.parseInt(args[2]);
                    if (hours < 1) {
                        sender.sendMessage("§cRestock interval must be at least 1 hour!");
                        return true;
                    }
                    
                    setShopRestockInterval(shopName, hours);
                    sender.sendMessage("§aRestock interval for shop '" + shopName + "' set to " + hours + " hours!");
                    return true;
                    
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid number! Please enter a valid hour amount.");
                    return true;
                }
            } else {
                // Show current interval
                int hours = getShopRestockInterval(shopName);
                sender.sendMessage("§eShop '" + shopName + "' restock interval: §f" + hours + " hours");
                return true;
            }
        }
        
        private boolean handleAddTradeAdvanced(CommandSender sender, String[] args) {
            if (!canManageShops(sender)) {
                sender.sendMessage("§cYou don't have permission to manage shops!");
                return true;
            }
            
            if (args.length < 6) {
                sender.sendMessage("§cUsage: /shop addtradeadvanced <shopname> <input_material> <input_amount> <output_material> <output_amount> [max_uses] [input_name] [output_name] [input_enchants] [output_enchants] [input_potion] [output_potion]");
                sender.sendMessage("§eExample: /shop addtradeadvanced general DIAMOND 1 DIAMOND_SWORD 1 10 \"&bMagic Diamond\" \"&cFire Sword\" \"\" \"FIRE_ASPECT:2,SHARPNESS:5\"");
                sender.sendMessage("§ePotion Example: /shop addtradeadvanced general EMERALD 1 POTION 1 10 \"\" \"&bHealing Potion\" \"\" \"\" \"\" \"HEALING\"");
                sender.sendMessage("§ePotion Types: HEALING, REGENERATION, SPEED, FIRE_RESISTANCE, POISON, etc.");
                sender.sendMessage("§ePotion Modifiers: Add :EXTENDED for longer duration or :UPGRADED for stronger effect");
                return true;
            }
            
            try {
                String shopName = args[1].toLowerCase();
                Material inputMaterial = Material.valueOf(args[2].toUpperCase());
                int inputAmount = Integer.parseInt(args[3]);
                Material outputMaterial = Material.valueOf(args[4].toUpperCase());
                int outputAmount = Integer.parseInt(args[5]);
                int maxUses = args.length > 6 ? Integer.parseInt(args[6]) : 10;
                
                String inputDisplayName = args.length > 7 ? args[7] : null;
                String outputDisplayName = args.length > 8 ? args[8] : null;
                String inputEnchantments = args.length > 9 ? args[9] : null;
                String outputEnchantments = args.length > 10 ? args[10] : null;
                String inputPotionType = args.length > 11 ? args[11] : null;
                String outputPotionType = args.length > 12 ? args[12] : null;
                
                // Remove quotes if present
                if (inputDisplayName != null && inputDisplayName.startsWith("\"") && inputDisplayName.endsWith("\"")) {
                    inputDisplayName = inputDisplayName.substring(1, inputDisplayName.length() - 1);
                }
                if (outputDisplayName != null && outputDisplayName.startsWith("\"") && outputDisplayName.endsWith("\"")) {
                    outputDisplayName = outputDisplayName.substring(1, outputDisplayName.length() - 1);
                }
                if (inputEnchantments != null && inputEnchantments.startsWith("\"") && inputEnchantments.endsWith("\"")) {
                    inputEnchantments = inputEnchantments.substring(1, inputEnchantments.length() - 1);
                }
                if (outputEnchantments != null && outputEnchantments.startsWith("\"") && outputEnchantments.endsWith("\"")) {
                    outputEnchantments = outputEnchantments.substring(1, outputEnchantments.length() - 1);
                }
                if (inputPotionType != null && inputPotionType.startsWith("\"") && inputPotionType.endsWith("\"")) {
                    inputPotionType = inputPotionType.substring(1, inputPotionType.length() - 1);
                }
                if (outputPotionType != null && outputPotionType.startsWith("\"") && outputPotionType.endsWith("\"")) {
                    outputPotionType = outputPotionType.substring(1, outputPotionType.length() - 1);
                }
                
                addTradeToShop(shopName, inputMaterial, inputAmount, outputMaterial, outputAmount, maxUses, 
                              inputDisplayName, outputDisplayName, inputEnchantments, outputEnchantments, 
                              inputPotionType, outputPotionType);
                
                sender.sendMessage("§aAdvanced trade added to shop '" + shopName + "'!");
                sender.sendMessage("§7Input: " + inputAmount + " " + inputMaterial.name() + 
                                 (inputDisplayName != null ? " (" + inputDisplayName.replace('&', '§') + ")" : ""));
                sender.sendMessage("§7Output: " + outputAmount + " " + outputMaterial.name() + 
                                 (outputDisplayName != null ? " (" + outputDisplayName.replace('&', '§') + ")" : ""));
                return true;
                
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§cInvalid material or number! Check your input.");
                return true;
            }
        }
        
        private boolean handleRemoveTrade(CommandSender sender, String[] args) {
            if (!canManageShops(sender)) {
                sender.sendMessage("§cYou don't have permission to manage shops!");
                return true;
            }
            
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /shop removetrade <shopname> <trade_index>");
                return true;
            }
            
            try {
                String shopName = args[1].toLowerCase();
                int tradeIndex = Integer.parseInt(args[2]) - 1; // 1-basiert für Benutzer
                
                if (!shops.containsKey(shopName)) {
                    sender.sendMessage("§cShop '" + shopName + "' not found!");
                    return true;
                }
                
                if (removeTradeFromShop(shopName, tradeIndex)) {
                    sender.sendMessage("§aTrade " + (tradeIndex + 1) + " removed from shop '" + shopName + "'!");
                } else {
                    sender.sendMessage("§cInvalid trade index! Use /shop trades " + shopName + " to see all trades.");
                }
                return true;
                
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid trade index! Must be a number.");
                return true;
            }
        }
        
        private boolean handleRenameShop(CommandSender sender, String[] args) {
            if (!canManageShops(sender)) {
                sender.sendMessage("§cYou don't have permission to manage shops!");
                return true;
            }
            
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /shop rename <shopname> <new_display_name>");
                return true;
            }
            
            String shopName = args[1].toLowerCase();
            if (!shops.containsKey(shopName)) {
                sender.sendMessage("§cShop '" + shopName + "' not found!");
                return true;
            }
            
            // Alle weiteren Argumente als neuen Display-Name zusammenfügen
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i > 2) sb.append(" ");
                sb.append(args[i]);
            }
            String newDisplayName = sb.toString();
            
            setShopDisplayName(shopName, newDisplayName);
            sender.sendMessage("§aShop '" + shopName + "' renamed to: " + newDisplayName.replace('&', '§'));
            return true;
        }
        
        private boolean handleListTrades(CommandSender sender, String[] args) {
            if (!canManageShops(sender)) {
                sender.sendMessage("§cYou don't have permission to manage shops!");
                return true;
            }
            
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /shop trades <shopname>");
                return true;
            }
            
            String shopName = args[1].toLowerCase();
            MerchantRecipe[] trades = getShopTrades(shopName);
            
            if (trades == null) {
                sender.sendMessage("§cShop '" + shopName + "' not found!");
                return true;
            }
            
            if (trades.length == 0) {
                sender.sendMessage("§cShop '" + shopName + "' has no trades!");
                return true;
            }
            
            sender.sendMessage("§6Trades in shop '" + shopName + "':");
            for (int i = 0; i < trades.length; i++) {
                MerchantRecipe trade = trades[i];
                ItemStack input = trade.getIngredients().get(0);
                ItemStack output = trade.getResult();
                sender.sendMessage("§e" + (i + 1) + ". §f" + input.getAmount() + " " + input.getType().name() + 
                                 " §7-> §f" + output.getAmount() + " " + output.getType().name() + 
                                 " §7(Max uses: " + trade.getMaxUses() + ")");
            }
            return true;
        }
        
        private boolean handleRemoveShop(CommandSender sender, String[] args) {
            if (!canManageShops(sender)) {
                sender.sendMessage("§cYou don't have permission to manage shops!");
                return true;
            }
            
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /shop remove <shopname>");
                return true;
            }
            
            String shopName = args[1].toLowerCase();
            if (removeShop(shopName)) {
                sender.sendMessage("§aShop '" + shopName + "' removed successfully!");
            } else {
                sender.sendMessage("§cShop '" + shopName + "' not found!");
            }
            return true;
        }
        
        private boolean handleListShops(CommandSender sender) {
            if (shops.isEmpty()) {
                sender.sendMessage("§cNo shops available!");
                return true;
            }
            
            sender.sendMessage("§6Available shops:");
            for (String shopName : shops.keySet()) {
                int tradeCount = shops.get(shopName).length;
                String displayName = getShopDisplayName(shopName);
                // Zeige nur Shops, die der Spieler verwenden kann
                if (canUseShop(sender, shopName)) {
                    sender.sendMessage("§e- §f" + shopName + " §7(" + displayName + "§7) - " + tradeCount + " trades");
                }
            }
            return true;
        }
        
        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            List<String> completions = new ArrayList<>();
            
            if (args.length == 1) {
                // Erste Argument: Subcommands oder Shop-Namen
                List<String> subCommands = new ArrayList<>();
                subCommands.addAll(Arrays.asList("info", "list"));
                
                if (canManageShops(sender)) {
                    subCommands.addAll(Arrays.asList("create", "addtrade", "addpotion", "addtradeadvanced", "removetrade", "rename", "trades", "remove", "potions", "restock"));
                }
                
                for (String subCommand : subCommands) {
                    if (subCommand.toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(subCommand);
                    }
                }
                
                // Shop-Namen hinzufügen
                for (String shopName : shops.keySet()) {
                    if (shopName.toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(shopName);
                    }
                }
                
            } else if (args.length == 2) {
                String subCommand = args[0].toLowerCase();
                if (subCommand.equals("addtrade") || subCommand.equals("addpotion") || subCommand.equals("addtradeadvanced") || subCommand.equals("remove") || 
                    subCommand.equals("removetrade") || subCommand.equals("rename") || 
                    subCommand.equals("trades") || subCommand.equals("restock")) {
                    // Shop-Namen für diese Befehle
                    for (String shopName : shops.keySet()) {
                        if (shopName.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(shopName);
                        }
                    }
                }
                
            } else if (args.length == 3) {
                String subCommand = args[0].toLowerCase();
                if (subCommand.equals("addtrade") || subCommand.equals("addpotion") || subCommand.equals("addtradeadvanced")) {
                    // Material-Namen für Input
                    for (Material material : Material.values()) {
                        if (material.isItem() && material.name().toLowerCase().startsWith(args[2].toLowerCase())) {
                            completions.add(material.name());
                        }
                    }
                } else if (subCommand.equals("removetrade")) {
                    // Trade-Indizes für removetrade
                    String shopName = args[1].toLowerCase();
                    if (shops.containsKey(shopName)) {
                        MerchantRecipe[] trades = shops.get(shopName);
                        for (int i = 1; i <= trades.length; i++) {
                            String index = String.valueOf(i);
                            if (index.startsWith(args[2])) {
                                completions.add(index);
                            }
                        }
                    }
                }
                
            } else if (args.length == 4) {
                String subCommand = args[0].toLowerCase();
                if (subCommand.equals("addtrade") || subCommand.equals("addtradeadvanced")) {
                    // Zahlen-Vorschläge für Input-Menge
                    List<String> amounts = Arrays.asList("1", "2", "3", "4", "5", "8", "10", "16", "32", "64");
                    for (String amount : amounts) {
                        if (amount.startsWith(args[3])) {
                            completions.add(amount);
                        }
                    }
                } else if (subCommand.equals("addpotion")) {
                    // Zahlen-Vorschläge für Input-Menge
                    List<String> amounts = Arrays.asList("1", "2", "3", "4", "5", "8", "10", "16", "32", "64");
                    for (String amount : amounts) {
                        if (amount.startsWith(args[3])) {
                            completions.add(amount);
                        }
                    }
                }
                
            } else if (args.length == 5) {
                String subCommand = args[0].toLowerCase();
                if (subCommand.equals("addtrade") || subCommand.equals("addtradeadvanced")) {
                    // Material-Namen für Output
                    for (Material material : Material.values()) {
                        if (material.isItem() && material.name().toLowerCase().startsWith(args[4].toLowerCase())) {
                            completions.add(material.name());
                        }
                    }
                } else if (subCommand.equals("addpotion")) {
                    // Potion-Typen
                    List<String> potionTypes = Arrays.asList(
                        "HEALING", "HEALING:UPGRADED", "HEALING:EXTENDED",
                        "HARMING", "HARMING:UPGRADED",
                        "REGENERATION", "REGENERATION:UPGRADED", "REGENERATION:EXTENDED",
                        "POISON", "POISON:UPGRADED", "POISON:EXTENDED",
                        "STRENGTH", "STRENGTH:UPGRADED", "STRENGTH:EXTENDED",
                        "WEAKNESS", "WEAKNESS:EXTENDED",
                        "SPEED", "SPEED:UPGRADED", "SPEED:EXTENDED",
                        "SLOWNESS", "SLOWNESS:EXTENDED",
                        "JUMP", "JUMP:UPGRADED", "JUMP:EXTENDED",
                        "FIRE_RESISTANCE", "FIRE_RESISTANCE:EXTENDED",
                        "WATER_BREATHING", "WATER_BREATHING:EXTENDED",
                        "INVISIBILITY", "INVISIBILITY:EXTENDED",
                        "NIGHT_VISION", "NIGHT_VISION:EXTENDED"
                    );
                    for (String potionType : potionTypes) {
                        if (potionType.toLowerCase().startsWith(args[4].toLowerCase())) {
                            completions.add(potionType);
                        }
                    }
                }
                
            } else if (args.length == 6) {
                String subCommand = args[0].toLowerCase();
                if (subCommand.equals("addtrade") || subCommand.equals("addtradeadvanced")) {
                    // Zahlen-Vorschläge für Output-Menge
                    List<String> amounts = Arrays.asList("1", "2", "3", "4", "5", "8", "10", "16", "32", "64");
                    for (String amount : amounts) {
                        if (amount.startsWith(args[5])) {
                            completions.add(amount);
                        }
                    }
                } else if (subCommand.equals("addpotion")) {
                    // Max uses für Potion
                    List<String> maxUses = Arrays.asList("1", "5", "10", "20", "50", "100");
                    for (String maxUse : maxUses) {
                        if (maxUse.startsWith(args[5])) {
                            completions.add(maxUse);
                        }
                    }
                }
                
            } else if (args.length == 7) {
                String subCommand = args[0].toLowerCase();
                if (subCommand.equals("addtrade") || subCommand.equals("addtradeadvanced")) {
                    // Max uses Vorschläge
                    List<String> maxUses = Arrays.asList("1", "5", "10", "20", "50", "100");
                    for (String maxUse : maxUses) {
                        if (maxUse.startsWith(args[6])) {
                            completions.add(maxUse);
                        }
                    }
                }
            } else if (args.length == 3) {
                String subCommand = args[0].toLowerCase();
                if (subCommand.equals("restock")) {
                    // Stunden-Vorschläge für Restock
                    List<String> hours = Arrays.asList("1", "6", "12", "24", "48", "72");
                    for (String hour : hours) {
                        if (hour.startsWith(args[2])) {
                            completions.add(hour);
                        }
                    }
                }
            } else if (args.length >= 8 && args.length <= 13) {
                String subCommand = args[0].toLowerCase();
                if (subCommand.equals("addtradeadvanced")) {
                    // Für die erweiterten Parameter geben wir Beispiele
                    if (args.length == 8) {
                        completions.add("\"&bCustom Name\"");
                        completions.add("\"\"");
                    } else if (args.length == 9) {
                        completions.add("\"&cOutput Name\"");
                        completions.add("\"\"");
                    } else if (args.length == 10) {
                        completions.add("\"SHARPNESS:5\"");
                        completions.add("\"\"");
                    } else if (args.length == 11) {
                        completions.add("\"FIRE_ASPECT:2,SHARPNESS:5\"");
                        completions.add("\"\"");
                    } else if (args.length == 12) {
                        // Input Potion Type
                        List<String> potionTypes = Arrays.asList("\"HEALING\"", "\"REGENERATION\"", "\"SPEED\"", "\"\"");
                        for (String potionType : potionTypes) {
                            if (potionType.toLowerCase().startsWith(args[11].toLowerCase())) {
                                completions.add(potionType);
                            }
                        }
                    } else if (args.length == 13) {
                        // Output Potion Type
                        List<String> potionTypes = Arrays.asList("\"HEALING\"", "\"REGENERATION\"", "\"SPEED\"", "\"\"");
                        for (String potionType : potionTypes) {
                            if (potionType.toLowerCase().startsWith(args[12].toLowerCase())) {
                                completions.add(potionType);
                            }
                        }
                    }
                }
            }
            
            return completions;
        }
    }
}