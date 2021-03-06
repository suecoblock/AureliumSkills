package com.archyx.aureliumskills.modifier;

import com.archyx.aureliumskills.configuration.Option;
import com.archyx.aureliumskills.configuration.OptionL;
import com.archyx.aureliumskills.skills.SkillLoader;
import com.archyx.aureliumskills.skills.abilities.ForagingAbilities;
import com.archyx.aureliumskills.skills.abilities.MiningAbilities;
import com.archyx.aureliumskills.stats.PlayerStat;
import com.archyx.aureliumskills.stats.Stat;
import com.archyx.aureliumskills.stats.StatLeveler;
import com.archyx.aureliumskills.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ItemListener implements Listener {

    private final Plugin plugin;
    private final Map<Player, ItemStack> heldItems;
    private final Map<Player, ItemStack> offHandItems;

    public ItemListener(Plugin plugin) {
        this.plugin = plugin;
        heldItems = new HashMap<>();
        offHandItems = new HashMap<>();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        heldItems.put(player, held);
        PlayerStat playerStat = SkillLoader.playerStats.get(player.getUniqueId());
        if (playerStat != null) {
            if (!held.getType().equals(Material.AIR)) {
                for (StatModifier modifier : ItemModifier.getItemModifiers(held)) {
                    playerStat.addModifier(modifier, false);
                }
            }
            if (OptionL.getBoolean(Option.MODIFIER_ITEM_ENABLE_OFF_HAND)) {
                ItemStack offHandItem = player.getInventory().getItemInOffHand();
                offHandItems.put(player, offHandItem);
                if (!offHandItem.getType().equals(Material.AIR)) {
                    for (StatModifier modifier : ItemModifier.getItemModifiers(offHandItem)) {
                        StatModifier offHandModifier = new StatModifier(modifier.getName() + "-offhand", modifier.getStat(), modifier.getValue());
                        playerStat.addModifier(offHandModifier);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        heldItems.remove(player);
    }

    public void scheduleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                //For every player
                for (Player player : Bukkit.getOnlinePlayers()) {
                    //Gets stored and held items
                    ItemStack stored = heldItems.get(player);
                    ItemStack held = player.getInventory().getItemInMainHand();
                    //If stored item is not null
                    if (stored != null) {
                        //If stored item is different than held
                        if (!stored.equals(held)) {
                            Set<Stat> statsToReload = new HashSet<>();
                            //Remove modifiers from stored item
                            if (!stored.getType().equals(Material.AIR)) {
                                PlayerStat playerStat = SkillLoader.playerStats.get(player.getUniqueId());
                                if (playerStat != null) {
                                    for (StatModifier modifier : ItemModifier.getItemModifiers(stored)) {
                                        playerStat.removeModifier(modifier.getName(), false);
                                        statsToReload.add(modifier.getStat());
                                    }
                                    //Remove valor
                                    if (ItemUtils.isAxe(stored.getType())) {
                                        ForagingAbilities.removeValor(playerStat);
                                    }
                                    //Remove stamina
                                    if (ItemUtils.isPickaxe(stored.getType())) {
                                        MiningAbilities.removeStamina(playerStat);
                                    }
                                }
                            }
                            //Add modifiers from held item
                            if (!held.getType().equals(Material.AIR)) {
                                PlayerStat playerStat = SkillLoader.playerStats.get(player.getUniqueId());
                                if (playerStat != null) {
                                    if (ItemRequirement.meetsRequirements(player, held)) {
                                        for (StatModifier modifier : ItemModifier.getItemModifiers(held)) {
                                            playerStat.addModifier(modifier, false);
                                            statsToReload.add(modifier.getStat());
                                        }
                                    }
                                    //Apply valor
                                    if (ItemUtils.isAxe(held.getType())) {
                                        ForagingAbilities.applyValor(player, playerStat);
                                    }
                                    //Apply stamina
                                    if (ItemUtils.isPickaxe(held.getType())) {
                                        MiningAbilities.applyStamina(player, playerStat);
                                    }
                                }
                            }
                            for (Stat stat : statsToReload) {
                                StatLeveler.reloadStat(player, stat);
                            }
                            //Set stored item to held item
                            heldItems.put(player, held.clone());
                        }
                    }
                    //If no mapping exists, add held item
                    else {
                        heldItems.put(player, held.clone());
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, OptionL.getInt(Option.MODIFIER_ITEM_CHECK_PERIOD));
        scheduleOffHandTask();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!event.isCancelled()) { // Make sure event is not cancelled
            if (OptionL.getBoolean(Option.MODIFIER_ITEM_ENABLE_OFF_HAND)) { // Check off hand support is enabled
                Player player = event.getPlayer();
                PlayerStat playerStat = SkillLoader.playerStats.get(player.getUniqueId());
                if (playerStat != null) {
                    // Get items switched
                    ItemStack itemOffHand = event.getOffHandItem();
                    ItemStack itemMainHand = event.getMainHandItem();
                    // Update items
                    offHandItems.put(player, itemOffHand);
                    heldItems.put(player, itemMainHand);
                    // Things to prevent double reloads
                    Set<String> offHandModifiers = new HashSet<>();
                    Set<Stat> statsToReload = new HashSet<>();
                    Integer originalHealthModifier = null;
                    Integer newHealthModifier = null;
                    // Check off hand item
                    if (itemOffHand != null) {
                        if (itemOffHand.getType() != Material.AIR) {
                            boolean meetsRequirements = ItemRequirement.meetsRequirements(player, itemOffHand); // Get whether player meets requirements
                            // For each modifier on the item
                            for (StatModifier modifier : ItemModifier.getItemModifiers(itemOffHand)) {
                                // Removes the old modifier from main hand
                                StatModifier offHandModifier = new StatModifier(modifier.getName() + "-offhand", modifier.getStat(), modifier.getValue());
                                playerStat.removeModifier(modifier.getName(), false);
                                // Add new one if meets requirements
                                if (meetsRequirements) {
                                    playerStat.addModifier(offHandModifier, false);
                                }
                                // Reload check stuff
                                offHandModifiers.add(offHandModifier.getName());
                                statsToReload.add(modifier.getStat());
                                if (modifier.getStat() == Stat.HEALTH) {
                                    originalHealthModifier = modifier.getValue();
                                }
                            }
                        }
                    }
                    // Check main hand item
                    if (itemMainHand != null) {
                        if (itemMainHand.getType() != Material.AIR) {
                            boolean meetsRequirements = ItemRequirement.meetsRequirements(player, itemMainHand); // Get whether player meets requirements
                            // For each modifier on the item
                            for (StatModifier modifier : ItemModifier.getItemModifiers(itemMainHand)) {
                                // Removes the offhand modifier if wasn't already added
                                if (!offHandModifiers.contains(modifier.getName() + "-offhand")) {
                                    playerStat.removeModifier(modifier.getName() + "-offhand", false);
                                }
                                // Add if meets requirements
                                if (meetsRequirements) {
                                    playerStat.addModifier(modifier, false);
                                }
                                // Reload check stuff
                                statsToReload.add(modifier.getStat());
                                if (modifier.getStat() == Stat.HEALTH) {
                                    newHealthModifier = modifier.getValue();
                                }
                            }
                        }
                    }
                    // Reload stats
                    for (Stat stat : statsToReload) {
                        StatLeveler.reloadStat(player, stat);
                    }
                }
            }
        }
    }

    public void scheduleOffHandTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (OptionL.getBoolean(Option.MODIFIER_ITEM_ENABLE_OFF_HAND)) {
                    //For every player
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        //Gets stored and held items
                        ItemStack stored = offHandItems.get(player);
                        ItemStack held = player.getInventory().getItemInOffHand();
                        //If stored item is not null
                        if (stored != null) {
                            //If stored item is different than held
                            if (!stored.equals(held)) {
                                //Remove modifiers from stored item
                                if (!stored.getType().equals(Material.AIR)) {
                                    PlayerStat playerStat = SkillLoader.playerStats.get(player.getUniqueId());
                                    if (playerStat != null) {
                                        for (StatModifier modifier : ItemModifier.getItemModifiers(stored)) {
                                            playerStat.removeModifier(modifier.getName() + "-offhand");
                                        }
                                    }
                                }
                                //Add modifiers from held item
                                if (!held.getType().equals(Material.AIR)) {
                                    PlayerStat playerStat = SkillLoader.playerStats.get(player.getUniqueId());
                                    if (playerStat != null) {
                                        if (ItemRequirement.meetsRequirements(player, held)) {
                                            for (StatModifier modifier : ItemModifier.getItemModifiers(held)) {
                                                StatModifier offHandModifier = new StatModifier(modifier.getName() + "-offhand", modifier.getStat(), modifier.getValue());
                                                playerStat.addModifier(offHandModifier);
                                            }
                                        }
                                    }
                                }
                                //Set stored item to held item
                                offHandItems.put(player, held.clone());
                            }
                        }
                        //If no mapping exists, add held item
                        else {
                            offHandItems.put(player, held.clone());
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, OptionL.getInt(Option.MODIFIER_ITEM_CHECK_PERIOD));
    }

}
