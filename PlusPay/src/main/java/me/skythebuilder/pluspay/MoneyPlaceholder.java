package me.skythebuilder.pluspay;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class MoneyPlaceholder extends PlaceholderExpansion {

    private final PlusPay plugin;

    public MoneyPlaceholder(PlusPay plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "pluspay";
    }

    @Override
    public String getAuthor() {
        return "Snowdesert";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }

        if (identifier.equals("snowdesertmoney")) {
            double balance = plugin.economy.getBalance(player);
            return plugin.getFormattedBalance(balance);
        }

        return null;
    }

}
