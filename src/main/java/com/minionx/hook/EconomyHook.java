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

    public static void setVaultEconomy(Object eco) {
        vaultEconomy = eco;
    }

    public static void setupEconomy() {
        if (Bukkit.getServer() != null) {
            try {
                if (Bukkit.getPluginManager() != null && Bukkit.getPluginManager().isPluginEnabled("Vault")) {
                    Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy");
                    RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(ecoClass);
                    if (rsp != null) {
                        vaultEconomy = rsp.getProvider();
                    }
                }
                royaleEconomyDirect = Bukkit.getPluginManager() != null && Bukkit.getPluginManager().isPluginEnabled("RoyaleEconomy");
            } catch (Throwable ignored) {}
        }
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

    public enum PayoutStatus {
        SUCCESS,
        DEFINITIVE_FAILURE,
        AMBIGUOUS_ERROR
    }

    public record PayoutResult(PayoutStatus status, String errorMessage) {
        public boolean isSuccess() { return status == PayoutStatus.SUCCESS; }
        public boolean isDefinitiveFailure() { return status == PayoutStatus.DEFINITIVE_FAILURE; }
        public boolean isAmbiguous() { return status == PayoutStatus.AMBIGUOUS_ERROR; }
    }

    public static PayoutResult depositWithResult(Player player, double amount) {
        if (amount <= 0.0) {
            return new PayoutResult(PayoutStatus.SUCCESS, null);
        }
        if (vaultEconomy != null) {
            try {
                Method deposit = vaultEconomy.getClass().getMethod("depositPlayer", Player.class, double.class);
                Object response = deposit.invoke(vaultEconomy, player, amount);
                if (response != null) {
                    Method transactionSuccess = response.getClass().getMethod("transactionSuccess");
                    boolean ok = (boolean) transactionSuccess.invoke(response);
                    if (ok) {
                        return new PayoutResult(PayoutStatus.SUCCESS, null);
                    } else {
                        String errMsg = "Vault deposit transaction failed";
                        try {
                            Method getError = response.getClass().getMethod("errorMessage");
                            Object err = getError.invoke(response);
                            if (err != null) errMsg = err.toString();
                        } catch (Throwable ignored) {}
                        return new PayoutResult(PayoutStatus.DEFINITIVE_FAILURE, errMsg);
                    }
                }
            } catch (Throwable t) {
                Throwable cause = (t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null) ? t.getCause() : t;
                String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
                return new PayoutResult(PayoutStatus.AMBIGUOUS_ERROR, msg);
            }
        }
        return new PayoutResult(PayoutStatus.SUCCESS, "No economy provider");
    }

    public static void deposit(Player player, double amount) {
        depositWithResult(player, amount);
    }
}
