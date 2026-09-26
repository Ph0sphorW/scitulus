package org.icarus.scitulus;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Cooldowns {

    private final Map<UUID, Map<String, Long>> until = new HashMap<>();

    public boolean ready(Player player, String key) {
        return remainingMillis(player, key) <= 0L;
    }

    public long remainingMillis(Player player, String key) {
        Map<String, Long> map = this.until.get(player.getUniqueId());
        if (map == null) {
            return 0L;
        }
        Long end = map.get(key);
        return end == null ? 0L : end - System.currentTimeMillis();
    }

    public void set(Player player, String key, int ticks) {
        this.until.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>())
                .put(key, System.currentTimeMillis() + ticks * 50L);
    }

    public void clear(UUID playerId) {
        this.until.remove(playerId);
    }
}
