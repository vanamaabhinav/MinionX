package com.minionx.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Integrates directly with RPG & Skill progression plugins (AuraSkills, AureliumSkills, and EcoSkills)
 * using cached reflection handles for zero-allocation performance on minion harvest cycles.
 */
public class SkillHook {

    private static boolean auraSkillsAvailable = false;
    private static Object auraSkillsApiInstance = null;
    private static Method auraGetUserMethod = null;
    private static Object auraGlobalRegistryInstance = null;
    private static Method auraGetSkillMethod = null;
    private static Method auraAddSkillXpMethod = null;

    private static boolean legacyAureliumAvailable = false;
    private static Method legacyAureliumAddXp = null;

    private static boolean ecoSkillsAvailable = false;
    private static Method ecoSkillsGetSkill = null;
    private static Method ecoSkillsAddXp = null;
    private static boolean ecoSkillsAddXpTakesString = false;

    static {
        checkHooks();
    }

    public static synchronized void checkHooks() {
        initAuraSkills();
        initEcoSkills();
    }

    private static void initAuraSkills() {
        auraSkillsAvailable = false;
        auraSkillsApiInstance = null;
        auraGetUserMethod = null;
        auraGlobalRegistryInstance = null;
        auraGetSkillMethod = null;
        auraAddSkillXpMethod = null;
        legacyAureliumAvailable = false;
        legacyAureliumAddXp = null;

        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
            return;
        }

        // 1. Try modern AuraSkills
        if (Bukkit.getPluginManager().isPluginEnabled("AuraSkills")) {
            try {
                Class<?> apiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
                auraSkillsApiInstance = apiClass.getMethod("get").invoke(null);
                auraGetUserMethod = apiClass.getMethod("getUser", UUID.class);

                Method getGlobalReg = apiClass.getMethod("getGlobalRegistry");
                auraGlobalRegistryInstance = getGlobalReg.invoke(auraSkillsApiInstance);

                Class<?> regClass = auraGlobalRegistryInstance.getClass();
                auraGetSkillMethod = regClass.getMethod("getSkill", String.class);

                Class<?> userClass = Class.forName("dev.aurelium.auraskills.api.user.SkillsUser");
                Class<?> skillClass = Class.forName("dev.aurelium.auraskills.api.skill.Skill");
                auraAddSkillXpMethod = userClass.getMethod("addSkillXp", skillClass, double.class);

                auraSkillsAvailable = true;
                return;
            } catch (Throwable ignored) {}
        }

        // 2. Try legacy AureliumSkills fallback
        if (Bukkit.getPluginManager().isPluginEnabled("AureliumSkills")) {
            try {
                Class<?> aureliumApi = Class.forName("com.archyx.aureliumskills.api.AureliumSkillsApi");
                try {
                    legacyAureliumAddXp = aureliumApi.getMethod("addXp", Player.class, String.class, double.class);
                    legacyAureliumAvailable = true;
                } catch (NoSuchMethodException e) {
                    Class<?> skillClass = Class.forName("com.archyx.aureliumskills.skills.Skill");
                    legacyAureliumAddXp = aureliumApi.getMethod("addXp", Player.class, skillClass, double.class);
                    legacyAureliumAvailable = true;
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void initEcoSkills() {
        ecoSkillsAvailable = false;
        ecoSkillsGetSkill = null;
        ecoSkillsAddXp = null;
        ecoSkillsAddXpTakesString = false;

        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
            return;
        }

        if (Bukkit.getPluginManager().isPluginEnabled("EcoSkills")) {
            try {
                Class<?> apiClass = Class.forName("com.willfp.ecoskills.api.EcoSkillsAPI");

                // Check string direct XP method first
                try {
                    ecoSkillsAddXp = apiClass.getMethod("giveSkillExperience", Player.class, String.class, double.class);
                    ecoSkillsAddXpTakesString = true;
                    ecoSkillsAvailable = true;
                    return;
                } catch (NoSuchMethodException ignored) {}

                // Check skill object lookup
                Class<?> skillClass = Class.forName("com.willfp.ecoskills.skills.Skill");
                ecoSkillsGetSkill = apiClass.getMethod("getSkill", String.class);

                try {
                    ecoSkillsAddXp = apiClass.getMethod("addXp", Player.class, skillClass, double.class);
                    ecoSkillsAddXpTakesString = false;
                    ecoSkillsAvailable = true;
                } catch (NoSuchMethodException e) {
                    try {
                        ecoSkillsAddXp = apiClass.getMethod("giveSkillExperience", Player.class, skillClass, double.class);
                        ecoSkillsAddXpTakesString = false;
                        ecoSkillsAvailable = true;
                    } catch (NoSuchMethodException ignored) {}
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Awards direct skill XP to the player.
     * @param player Target player
     * @param skillName Internal skill identifier (e.g. "mining", "farming", "combat")
     * @param amount XP amount
     */
    public static void addSkillXp(Player player, String skillName, double amount) {
        if (player == null || skillName == null || amount <= 0) return;

        // 1. Modern AuraSkills (cached reflection)
        if (auraSkillsAvailable && auraSkillsApiInstance != null && auraGetUserMethod != null &&
                auraGlobalRegistryInstance != null && auraGetSkillMethod != null && auraAddSkillXpMethod != null) {
            try {
                Object user = auraGetUserMethod.invoke(auraSkillsApiInstance, player.getUniqueId());
                if (user != null) {
                    Object skillObj = auraGetSkillMethod.invoke(auraGlobalRegistryInstance, skillName.toLowerCase());
                    if (skillObj != null) {
                        auraAddSkillXpMethod.invoke(user, skillObj, amount);
                        return;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 2. Legacy AureliumSkills fallback
        if (legacyAureliumAvailable && legacyAureliumAddXp != null) {
            try {
                legacyAureliumAddXp.invoke(null, player, skillName.toUpperCase(), amount);
                return;
            } catch (Throwable ignored) {}
        }

        // 3. EcoSkills (cached reflection)
        if (ecoSkillsAvailable && ecoSkillsAddXp != null) {
            try {
                if (ecoSkillsAddXpTakesString) {
                    ecoSkillsAddXp.invoke(null, player, skillName.toLowerCase(), amount);
                } else if (ecoSkillsGetSkill != null) {
                    Object skillObj = ecoSkillsGetSkill.invoke(null, skillName.toLowerCase());
                    if (skillObj != null) {
                        ecoSkillsAddXp.invoke(null, player, skillObj, amount);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    public static boolean isAuraSkillsAvailable() {
        return auraSkillsAvailable || legacyAureliumAvailable;
    }

    public static boolean isEcoSkillsAvailable() {
        return ecoSkillsAvailable;
    }
}
