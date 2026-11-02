package com.minionx.hook;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/**
 * Universal economy integration supporting Vault (and RoyaleEconomy via Vault or direct API).
 * Implements cached reflection handles supporting OfflinePlayer, Player, and String parameter signatures.
 */
public class EconomyHook {

    private static Object vaultEconomy = null;
    private static boolean royaleEconomyDirect = false;

    // --- Cached Reflection Handles ---
    private static Method cachedGetBalance = null;
    private static ParamType getBalanceParam = ParamType.NONE;

    private static Method cachedHas = null;
    private static ParamType hasParam = ParamType.NONE;

    private static Method cachedWithdraw = null;
    private static ParamType withdrawParam = ParamType.NONE;

    private static Method cachedDeposit = null;
    private static ParamType depositParam = ParamType.NONE;

    private enum ParamType {
        OFFLINE_PLAYER,
        PLAYER,
        STRING,
        NONE
    }

    static {
        setupEconomy();
    }

    public static void setVaultEconomy(Object eco) {
        vaultEconomy = eco;
        cacheMethods();
    }

    public static synchronized void setupEconomy() {
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
        cacheMethods();
    }

    private static void cacheMethods() {
        if (vaultEconomy == null) {
            cachedGetBalance = null;
            getBalanceParam = ParamType.NONE;
            cachedHas = null;
            hasParam = ParamType.NONE;
            cachedWithdraw = null;
            withdrawParam = ParamType.NONE;
            cachedDeposit = null;
            depositParam = ParamType.NONE;
            return;
        }

        Class<?> clazz = vaultEconomy.getClass();

        // 1. getBalance(OfflinePlayer) -> getBalance(Player) -> getBalance(String)
        try {
            cachedGetBalance = clazz.getMethod("getBalance", OfflinePlayer.class);
            getBalanceParam = ParamType.OFFLINE_PLAYER;
        } catch (NoSuchMethodException e1) {
            try {
                cachedGetBalance = clazz.getMethod("getBalance", Player.class);
                getBalanceParam = ParamType.PLAYER;
            } catch (NoSuchMethodException e2) {
                try {
                    cachedGetBalance = clazz.getMethod("getBalance", String.class);
                    getBalanceParam = ParamType.STRING;
                } catch (NoSuchMethodException e3) {
                    cachedGetBalance = null;
                    getBalanceParam = ParamType.NONE;
                }
            }
        }

        // 2. has(OfflinePlayer, double) -> has(Player, double) -> has(String, double)
        try {
            cachedHas = clazz.getMethod("has", OfflinePlayer.class, double.class);
            hasParam = ParamType.OFFLINE_PLAYER;
        } catch (NoSuchMethodException e1) {
            try {
                cachedHas = clazz.getMethod("has", Player.class, double.class);
                hasParam = ParamType.PLAYER;
            } catch (NoSuchMethodException e2) {
                try {
                    cachedHas = clazz.getMethod("has", String.class, double.class);
                    hasParam = ParamType.STRING;
                } catch (NoSuchMethodException e3) {
                    cachedHas = null;
                    hasParam = ParamType.NONE;
                }
            }
        }

        // 3. withdrawPlayer(OfflinePlayer, double) -> withdrawPlayer(Player, double) -> withdrawPlayer(String, double)
        try {
            cachedWithdraw = clazz.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            withdrawParam = ParamType.OFFLINE_PLAYER;
        } catch (NoSuchMethodException e1) {
            try {
                cachedWithdraw = clazz.getMethod("withdrawPlayer", Player.class, double.class);
                withdrawParam = ParamType.PLAYER;
            } catch (NoSuchMethodException e2) {
                try {
                    cachedWithdraw = clazz.getMethod("withdrawPlayer", String.class, double.class);
                    withdrawParam = ParamType.STRING;
                } catch (NoSuchMethodException e3) {
                    cachedWithdraw = null;
                    withdrawParam = ParamType.NONE;
                }
            }
        }

        // 4. depositPlayer(OfflinePlayer, double) -> depositPlayer(Player, double) -> depositPlayer(String, double)
        try {
            cachedDeposit = clazz.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            depositParam = ParamType.OFFLINE_PLAYER;
        } catch (NoSuchMethodException e1) {
            try {
                cachedDeposit = clazz.getMethod("depositPlayer", Player.class, double.class);
                depositParam = ParamType.PLAYER;
            } catch (NoSuchMethodException e2) {
                try {
                    cachedDeposit = clazz.getMethod("depositPlayer", String.class, double.class);
                    depositParam = ParamType.STRING;
                } catch (NoSuchMethodException e3) {
                    cachedDeposit = null;
                    depositParam = ParamType.NONE;
                }
            }
        }
    }

    public static boolean hasEconomy() {
        if (vaultEconomy == null && Bukkit.getServer() != null) {
            setupEconomy();
        }
        return vaultEconomy != null || royaleEconomyDirect;
    }

    public static double getBalance(Player player) {
        if (player == null) return 0.0;
        if (vaultEconomy == null && Bukkit.getServer() != null) {
            setupEconomy();
        }
        if (vaultEconomy != null && cachedGetBalance != null) {
            try {
                Object arg = switch (getBalanceParam) {
                    case OFFLINE_PLAYER, PLAYER -> player;
                    case STRING -> player.getName();
                    case NONE -> null;
                };
                if (arg != null) {
                    return ((Number) cachedGetBalance.invoke(vaultEconomy, arg)).doubleValue();
                }
            } catch (Throwable ignored) {}
        }
        return 0.0;
    }

    public static boolean has(Player player, double amount) {
        if (player == null || amount <= 0.0) return true;
        if (vaultEconomy == null && Bukkit.getServer() != null) {
            setupEconomy();
        }
        if (vaultEconomy != null && cachedHas != null) {
            try {
                Object arg = switch (hasParam) {
                    case OFFLINE_PLAYER, PLAYER -> player;
                    case STRING -> player.getName();
                    case NONE -> null;
                };
                if (arg != null) {
                    return (boolean) cachedHas.invoke(vaultEconomy, arg, amount);
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static boolean withdraw(Player player, double amount) {
        if (player == null || amount <= 0.0) return true;
        if (vaultEconomy == null && Bukkit.getServer() != null) {
            setupEconomy();
        }
        if (vaultEconomy != null && cachedWithdraw != null) {
            try {
                Object arg = switch (withdrawParam) {
                    case OFFLINE_PLAYER, PLAYER -> player;
                    case STRING -> player.getName();
                    case NONE -> null;
                };
                if (arg != null) {
                    Object response = cachedWithdraw.invoke(vaultEconomy, arg, amount);
                    if (response != null) {
                        Method transactionSuccess = response.getClass().getMethod("transactionSuccess");
                        return (boolean) transactionSuccess.invoke(response);
                    }
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
        if (vaultEconomy == null && Bukkit.getServer() != null) {
            setupEconomy();
        }
        if (vaultEconomy != null && cachedDeposit != null) {
            try {
                Object arg = switch (depositParam) {
                    case OFFLINE_PLAYER, PLAYER -> player;
                    case STRING -> player != null ? player.getName() : null;
                    case NONE -> null;
                };
                if (arg != null) {
                    Object response = cachedDeposit.invoke(vaultEconomy, arg, amount);
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
