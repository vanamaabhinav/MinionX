package com.minionx.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Integrates directly with RPG & Skill progression plugins (AuraSkills and EcoSkills).
 */
public class SkillHook {

    private static boolean auraSkillsAvailable = false;
    private static boolean ecoSkillsAvailable = false;

    static {
        checkHooks();
    }

    public static void checkHooks() {
        auraSkillsAvailable = Bukkit.getPluginManager().isPluginEnabled("AuraSkills");
        ecoSkillsAvailable = Bukkit.getPluginManager().isPluginEnabled("EcoSkills");
    }

    /**
     * Awards direct skill XP to the player.
     * @param player Target player
     * @param skillName Internal skill identifier (e.g. "mining", "farming", "combat")
     * @param amount XP amount
     */
    public static void addSkillXp(Player player, String skillName, double amount) {
        if (player == null || skillName == null || amount <= 0) return;

        // 1. AuraSkills
        if (auraSkillsAvailable) {
            try {
                Class<?> apiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
                Object api = apiClass.getMethod("get").invoke(null);
                Object user = api.getClass().getMethod("getUser", java.util.UUID.class).invoke(api, player.getUniqueId());
                if (user != null) {
                    Class<?> registryClass = Class.forName("dev.aurelium.auraskills.api.skill.Skill");
                    Method getSkill = api.getClass().getMethod("getGlobalRegistry");
                    Object globalReg = getSkill.invoke(api);
                    Method getSkillMethod = globalReg.getClass().getMethod("getSkill", String.class);
                    Object skillObj = getSkillMethod.invoke(globalReg, skillName);
                    if (skillObj != null) {
                        Method addXp = user.getClass().getMethod("addSkillXp", registryClass, double.class);
                        addXp.invoke(user, skillObj, amount);
                        return;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 2. EcoSkills
        if (ecoSkillsAvailable) {
            try {
                Class<?> apiClass = Class.forName("com.willfp.ecoskills.api.EcoSkillsAPI");
                Class<?> skillClass = Class.forName("com.willfp.ecoskills.skills.Skill");
                Method getSkill = apiClass.getMethod("getSkill", String.class);
                Object skillObj = getSkill.invoke(null, skillName);
                if (skillObj != null) {
                    Method addXp = apiClass.getMethod("addXp", Player.class, skillClass, double.class);
                    addXp.invoke(null, player, skillObj, amount);
                }
            } catch (Throwable ignored) {}
        }
    }
}
