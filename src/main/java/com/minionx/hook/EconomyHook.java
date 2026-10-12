package com.minionx.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/**
 * Universal economy integration supporting Vault (and RoyaleEconomy via Vault or direct API).
 */
public class EconomyHook {

    private static Object vaultEconomy = null;
    private static boolean royaleEconomyDirect = false;

    static {
        setupEconomy();
    }

    public static void setupEconomy() {
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            try {
                Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy");
                RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(ecoClass);
                if (rsp != null) {
                    vaultEconomy = rsp.getProvider();
                }
            } catch (Throwable ignored) {}
        }
        royaleEconomyDirect = Bukkit.getPluginManager().isPluginEnabled("RoyaleEconomy");
    }

    public static boolean hasEconomy() {
        return vaultEconomy != null || royaleEconomyDirect;
    }

    public static double getBalance(Player player) {
        if (vaultEconomy != null) {
            try {
                Method getBalance = vaultEconomy.getClass().getMethod("getBalance", Player.class);
                return ((Number) getBalance.invoke(vaultEconomy, player)).doubleValue();
            } catch (Throwable ignored) {}
        }
        return 0.0;
    }

    public static boolean has(Player player, double amount) {
        if (vaultEconomy != null) {
            try {
                Method has = vaultEconomy.getClass().getMethod("has", Player.class, double.class);
                return (boolean) has.invoke(vaultEconomy, player, amount);
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static boolean withdraw(Player player, double amount) {
        if (vaultEconomy != null) {
            try {
                Method withdraw = vaultEconomy.getClass().getMethod("withdrawPlayer", Player.class, double.class);
                Object response = withdraw.invoke(vaultEconomy, player, amount);
                if (response != null) {
                    Method transactionSuccess = response.getClass().getMethod("transactionSuccess");
                    return (boolean) transactionSuccess.invoke(response);
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static void deposit(Player player, double amount) {
        if (vaultEconomy != null) {
            try {
                Method deposit = vaultEconomy.getClass().getMethod("depositPlayer", Player.class, double.class);
                deposit.invoke(vaultEconomy, player, amount);
            } catch (Throwable ignored) {}
        }
    }
}
