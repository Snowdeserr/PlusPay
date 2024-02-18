package me.skythebuilder.pluspay;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.ChatColor;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlusPay extends JavaPlugin implements TabCompleter, Listener {

    public Economy economy;
    private Map<String, Double> offlinePayments = new HashMap<>();
    private FileConfiguration offlinePaymentsConfig;
    private File offlinePaymentsFile;
    private String playerNotFoundMessage;
    private String notEnoughMoneyMessage;
    private String paymentSuccessMessage;
    private String offlinePaymentSuccessMessage;
    private String actionBarPrefix;
    private String receivePaymentMessage;
    private Sound paymentSuccessSound;
    private String payToggleOnMessage;
    private String payToggleOffMessage;
    private Map<UUID, Boolean> messageToggle = new HashMap<>();

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault not found! Make sure Vault is installed.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loadOfflinePayments();
        loadMessages();

        getServer().getPluginManager().registerEvents(this, this);

        getCommand("pay").setExecutor(this);
        getCommand("pay").setTabCompleter(this);
        getCommand("paytoggle").setExecutor(this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new MoneyPlaceholder(this).register();
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        return economy != null;
    }

    private void loadMessages() {
        getConfig().addDefault("messages.playerNotFound", "&cPlayer not found.");
        getConfig().addDefault("messages.notEnoughMoney", "&cYou don't have enough money.");
        getConfig().addDefault("messages.paymentSuccess", "&aYou have transferred %amount% to %player%.");
        getConfig().addDefault("messages.offlinePaymentSuccess", "&aYou have received %amount% from an offline payment.");
        getConfig().addDefault("messages.actionBarPrefix", "&aPlusPay | Build by Snowdesert.");
        getConfig().addDefault("messages.receivePayment", "&aYou have received %amount% from %player%.");
        getConfig().addDefault("messages.payToggleOn", "&aPayment messages are ON.");
        getConfig().addDefault("messages.payToggleOff", "&aPayment messages are OFF.");
        getConfig().addDefault("sounds.paymentSuccess", "ENTITY_PLAYER_LEVELUP"); // Default sound

        getConfig().options().copyDefaults(true);
        saveConfig();

        playerNotFoundMessage = colorize(getConfig().getString("messages.playerNotFound"));
        notEnoughMoneyMessage = colorize(getConfig().getString("messages.notEnoughMoney"));
        paymentSuccessMessage = colorize(getConfig().getString("messages.paymentSuccess"));
        offlinePaymentSuccessMessage = colorize(getConfig().getString("messages.offlinePaymentSuccess"));
        actionBarPrefix = colorize(getConfig().getString("messages.actionBarPrefix"));
        receivePaymentMessage = colorize(getConfig().getString("messages.receivePayment"));
        payToggleOnMessage = colorize(getConfig().getString("messages.payToggleOn"));
        payToggleOffMessage = colorize(getConfig().getString("messages.payToggleOff"));

        paymentSuccessSound = Sound.valueOf(getConfig().getString("sounds.paymentSuccess"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("paytoggle")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                return true;
            }

            Player player = (Player) sender;
            UUID playerId = player.getUniqueId();

            boolean showMessages = messageToggle.getOrDefault(playerId, true);
            messageToggle.put(playerId, !showMessages);

            player.sendMessage(showMessages ? payToggleOnMessage : payToggleOffMessage);
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "Usage: /pay <player> <amount>");
            return true;
        }

        String targetName = args[0].toLowerCase();
        double amount;

        try {
            amount = parseAmount(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(notEnoughMoneyMessage);
            return true;
        }

        if (targetName.equals(player.getName().toLowerCase())) {
            player.sendMessage(ChatColor.RED + "You cannot pay yourself.");
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetName);

        if (target == null) {
            offlinePayments.put(targetName, amount);
            player.sendMessage(playerNotFoundMessage);
            return true;
        }

        if (economy.has(player, amount)) {
            economy.withdrawPlayer(player, amount);
            economy.depositPlayer(target, amount);

            sendActionBar(player, paymentSuccessMessage.replace("%amount%", formatBalance(amount)).replace("%player%", target.getName()));
            playSound(player, paymentSuccessSound);

            if (messageToggle.getOrDefault(player.getUniqueId(), true)) {
                sendActionBar(target, receivePaymentMessage.replace("%amount%", formatBalance(amount)).replace("%player%", player.getName()));
            }
        } else {
            player.sendMessage(notEnoughMoneyMessage);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partialName = args[0].toLowerCase();
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.getName().toLowerCase().startsWith(partialName)) {
                    completions.add(onlinePlayer.getName());
                }
            }
            for (String offlinePlayer : offlinePayments.keySet()) {
                if (offlinePlayer.toLowerCase().startsWith(partialName)) {
                    completions.add(offlinePlayer);
                }
            }
        }

        return completions;
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    private void playSound(Player player, Sound sound) {
        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (offlinePayments.containsKey(player.getName().toLowerCase())) {
            double amount = offlinePayments.get(player.getName().toLowerCase());
            economy.depositPlayer(player, amount);
            offlinePayments.remove(player.getName().toLowerCase());
            sendActionBar(player, offlinePaymentSuccessMessage.replace("%amount%", formatBalance(amount)));
        }

        sendActionBar(player, actionBarPrefix);
    }

    private double parseAmount(String amountStr) throws NumberFormatException {
        char lastChar = amountStr.charAt(amountStr.length() - 1);
        if (Character.isLetter(lastChar)) {
            double multiplier = 1.0;
            switch (Character.toUpperCase(lastChar)) {
                case 'K':
                    multiplier = 1000.0;
                    break;
                case 'M':
                    multiplier = 1000000.0;
                    break;
                case 'B':
                    multiplier = 1.0E9;
                    break;
                case 'T':
                    multiplier = 1.0E12;
                    break;
                default:
                    throw new NumberFormatException("Invalid multiplier character: " + lastChar);
            }
            return Double.parseDouble(amountStr.substring(0, amountStr.length() - 1)) * multiplier;
        } else {
            return Double.parseDouble(amountStr);
        }
    }

    public String formatBalance(double balance) {
        if (balance >= 1.0E12) {
            return String.format("%.2fT", balance / 1.0E12);
        } else if (balance >= 1.0E9) {
            return String.format("%.2fB", balance / 1.0E9);
        } else if (balance >= 1000000.0) {
            return String.format("%.2fM", balance / 1000000.0);
        } else {
            return balance >= 1000.0 ? String.format("%.2fK", balance / 1000.0) : String.format("%.2f", balance);
        }
    }

    public String colorize(String message) {
        message = ChatColor.translateAlternateColorCodes('&', message);
        Pattern hexColorPattern = Pattern.compile("#[a-fA-F0-9]{6}");
        Matcher matcher = hexColorPattern.matcher(message);

        while (matcher.find()) {
            String hexColor = matcher.group();
            ChatColor color = ChatColor.of(hexColor);
            if (color != null) {
                message = message.replace(hexColor, color.toString());
            }
        }

        return message;
    }

    private void saveOfflinePayments() {
        if (offlinePaymentsConfig == null || offlinePaymentsFile == null) {
            return;
        }
        offlinePaymentsConfig.set("offlinePayments", new HashMap<>(offlinePayments));
        try {
            offlinePaymentsConfig.save(offlinePaymentsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadOfflinePayments() {
        offlinePaymentsFile = new File(getDataFolder(), "offlinePayments.yml");

        if (!offlinePaymentsFile.exists()) {
            saveResource("offlinePayments.yml", false);
        }

        offlinePaymentsConfig = YamlConfiguration.loadConfiguration(offlinePaymentsFile);
        ConfigurationSection paymentSection = offlinePaymentsConfig.getConfigurationSection("offlinePayments");

        if (paymentSection != null) {
            for (String playerName : paymentSection.getKeys(false)) {
                double amount = paymentSection.getDouble(playerName);
                offlinePayments.put(playerName, amount);
            }
        }
    }

    @Override
    public void onDisable() {
        saveOfflinePayments();
    }

    public String getFormattedBalance(double balance) {
        return formatBalance(balance);
    }
}
