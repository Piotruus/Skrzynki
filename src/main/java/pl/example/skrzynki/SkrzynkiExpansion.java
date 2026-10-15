package pl.example.skrzynki;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public final class SkrzynkiExpansion extends PlaceholderExpansion {
    private final SkrzynkiPlugin plugin;
    public SkrzynkiExpansion(SkrzynkiPlugin plugin) { this.plugin = plugin; }
    @Override public @NotNull String getIdentifier() { return "skrzynki"; }
    @Override public @NotNull String getAuthor() { return "ChatGPT"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || player.getUniqueId() == null) return "0";
        String p = params.toLowerCase(java.util.Locale.ROOT);
        if (p.equals("opened")) return String.valueOf(plugin.getOpened(player.getUniqueId()));
        if (p.equals("last_reward")) return plugin.getLastReward(player.getUniqueId());
        if (p.startsWith("opened_")) return String.valueOf(plugin.getOpened(player.getUniqueId(), params.substring(7)));
        if (p.startsWith("recent_")) {
            try { int index = Integer.parseInt(p.substring(7)) - 1; var list = plugin.getRecent(player.getUniqueId()); return index >= 0 && index < list.size() ? list.get(index) : "brak"; }
            catch (NumberFormatException ignored) { return "brak"; }
        }
        return null;
    }
}
