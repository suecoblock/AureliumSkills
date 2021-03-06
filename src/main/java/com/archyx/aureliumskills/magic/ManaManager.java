package com.archyx.aureliumskills.magic;

import com.archyx.aureliumskills.api.ManaRegenerateEvent;
import com.archyx.aureliumskills.configuration.Option;
import com.archyx.aureliumskills.configuration.OptionL;
import com.archyx.aureliumskills.skills.SkillLoader;
import com.archyx.aureliumskills.stats.PlayerStat;
import com.archyx.aureliumskills.stats.Stat;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ManaManager implements Listener {

    private final Map<UUID, Integer> mana;
    private final Plugin plugin;

    public ManaManager(Plugin plugin) {
        this.plugin = plugin;
        mana = new HashMap<>();
    }

    /**
     * Start regenerating Mana
     */
    public void startRegen() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID id : mana.keySet()) {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(id);
                    if (player.isOnline()) {
                        if (SkillLoader.playerStats.containsKey(id)) {
                            PlayerStat stat = SkillLoader.playerStats.get(id);
                            int originalMana = mana.get(id);
                            int maxMana = OptionL.getInt(Option.BASE_MANA) + 2 * stat.getStatLevel(Stat.WISDOM);
                            if (originalMana < maxMana) {
                                int regen = OptionL.getInt(Option.REGENERATION_BASE_MANA_REGEN) + (int) (stat.getStatLevel(Stat.REGENERATION) * OptionL.getDouble(Option.REGENERATION_MANA_MODIFIER));
                                int finalRegen = Math.min(originalMana + regen, maxMana) - originalMana;
                                //Call Event
                                ManaRegenerateEvent event = new ManaRegenerateEvent(player.getPlayer(), finalRegen);
                                Bukkit.getPluginManager().callEvent(event);
                                if (!event.isCancelled()) {
                                    mana.put(id, originalMana + event.getAmount());
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public int getMana(UUID id) {
        if (mana.containsKey(id)) {
            return mana.get(id);
        }
        else {
            mana.put(id, OptionL.getInt(Option.BASE_MANA));
            return OptionL.getInt(Option.BASE_MANA);
        }
    }

    public int getMaxMana(UUID id) {
        if (SkillLoader.playerStats.containsKey(id)) {
            return OptionL.getInt(Option.BASE_MANA) + (2 * SkillLoader.playerStats.get(id).getStatLevel(Stat.WISDOM));
        }
        else {
            SkillLoader.playerStats.put(id, new PlayerStat(id));
            return OptionL.getInt(Option.BASE_MANA);
        }
    }

    public void setMana(UUID id, int amount) {
        mana.put(id, amount);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!mana.containsKey(id)) {
            if (SkillLoader.playerStats.containsKey(id)) {
                mana.put(event.getPlayer().getUniqueId(), OptionL.getInt(Option.BASE_MANA) + 2 * SkillLoader.playerStats.get(id).getStatLevel(Stat.WISDOM));
            }
        }
    }
}
