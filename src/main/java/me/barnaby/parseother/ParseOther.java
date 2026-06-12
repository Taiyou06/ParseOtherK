package me.barnaby.parseother;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.md_5.bungee.api.ChatColor;

public class ParseOther extends PlaceholderExpansion {

    private final Map<String, String> nameCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> uuidCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cacheCleaner = Executors.newScheduledThreadPool(1);
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
    private static final Pattern SUFFIX_SPLIT = Pattern.compile("(?<!\\\\)\\}_");

    public ParseOther() {
        // Reduce cache lifetime to prevent outdated player data issues
        cacheCleaner.scheduleAtFixedRate(() -> {
            nameCache.clear();
            uuidCache.clear();
        }, 5, 5, TimeUnit.MINUTES); // Cleared every 5 minutes
    }

    @Override
    public String getAuthor() {
        return "cj89898";
    }

    @Override
    public String getIdentifier() {
        return "parseother";
    }

    @Override
    public String getVersion() {
        return "2.0.0";
    }

    @SuppressWarnings("deprecation")
    @Override
    public String onRequest(OfflinePlayer p, String s) {
        boolean unsafe = false;
        if (s.startsWith("unsafe_")) {
            s = s.substring(7);
            unsafe = true;
        }

        String[] strings = SUFFIX_SPLIT.split(s, 2);
        if (strings.length < 2 || strings[1].length() < 2) {
            return "0";
        }

        strings[0] = strings[0].substring(1).replace("\\}_", "}_");
        strings[1] = strings[1].substring(1, strings[1].length() - 1);

        String user = unsafe ? PlaceholderAPI.setPlaceholders(p, "%" + strings[0] + "%") : strings[0];

        // Strip colors and invalid characters
        user = stripInvalid(ChatColor.stripColor(user), false);

        if (user.isBlank() || user.equalsIgnoreCase("none") || user.contains("%") || !USERNAME_PATTERN.matcher(user).matches()) {
            return "0";
        }

        OfflinePlayer player = resolvePlayer(user);

        // If the player is offline, return "0"
        if (player == null || !player.isOnline()) {
            return "0";
        }

        try {
            String placeholderResult = PlaceholderAPI.setPlaceholders(player, "%" + strings[1] + "%");

            placeholderResult = stripInvalid(ChatColor.stripColor(placeholderResult), true);

            // If unresolved placeholders or empty results, return "0"
            if (placeholderResult == null || placeholderResult.trim().isEmpty() || placeholderResult.contains("{") || placeholderResult.contains("}")) {
                return "0";
            }

            return ChatColor.translateAlternateColorCodes('&', placeholderResult);
        } catch (Exception e) {
            return "0"; // If any error occurs, return "0"
        }
    }

    // Removes disallowed characters without compiling a regex per call. Returns the
    // input unchanged (no allocation) when every character is already valid.
    private static String stripInvalid(String input, boolean allowResultChars) {
        if (input == null) {
            return null;
        }
        int len = input.length();
        StringBuilder sb = null;
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            boolean valid = allowResultChars ? isValidResultChar(c) : isValidUserChar(c);
            if (valid) {
                if (sb != null) {
                    sb.append(c);
                }
            } else if (sb == null) {
                sb = new StringBuilder(len);
                sb.append(input, 0, i);
            }
        }
        return sb == null ? input : sb.toString();
    }

    private static boolean isValidUserChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    private static boolean isValidResultChar(char c) {
        if (isValidUserChar(c)) {
            return true;
        }
        switch (c) {
            case ' ': case '.': case ',': case ':': case ';':
            case '!': case '?': case '(': case ')': case '-':
                return true;
            default:
                return false;
        }
    }

    private OfflinePlayer resolvePlayer(String user) {
        OfflinePlayer player = null;

        if (USERNAME_PATTERN.matcher(user).matches()) {
            player = Bukkit.getOfflinePlayer(user);
            if (player.isOnline()) {
                nameCache.put(user.toLowerCase(), player.getName());
            }
        } else if (user.length() == 36) {
            try {
                UUID uuid = UUID.fromString(user);
                player = Bukkit.getOfflinePlayer(uuid);
                if (player.isOnline()) {
                    uuidCache.put(uuid, player.getName());
                }
            } catch (IllegalArgumentException ignored) {}
        }

        return (player != null && player.isOnline()) ? player : null;
    }

    public void onUnregister() {
        cacheCleaner.shutdown();
        try {
            if (!cacheCleaner.awaitTermination(5, TimeUnit.SECONDS)) {
                cacheCleaner.shutdownNow();
                Bukkit.getLogger().warning("[ParseOther] Forced shutdown of cacheCleaner due to timeout.");
            }
        } catch (InterruptedException ignored) {
            cacheCleaner.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
