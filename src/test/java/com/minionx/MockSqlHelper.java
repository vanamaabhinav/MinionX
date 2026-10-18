package com.minionx;

import com.minionx.storage.PendingOperation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;

public class MockSqlHelper {

    public static class PreparedStatementCall {
        public final String sql;
        public final Map<Integer, Object> parameters = new HashMap<>();

        public PreparedStatementCall(String sql) {
            this.sql = sql;
        }

        @Override
        public String toString() {
            return sql + " " + parameters;
        }
    }

    public static class State {
        public final List<String> executedStatements = new ArrayList<>();
        public final List<PreparedStatementCall> preparedCalls = new ArrayList<>();
        public final List<String> transactionEvents = new ArrayList<>();
        public boolean autoCommit = true;
        public boolean throwOnExecute = false;
        public boolean throwOnRollback = false;
        public boolean throwOnSetAutoCommitTrue = false;
        public boolean throwOnCommit = false;
        public String databaseProductName = "SQLite";

        public final Set<String> minions = new HashSet<>();
        public final Map<String, String> pendingOperationStates = new HashMap<>();
        public final Map<String, String> pendingOperationMinions = new HashMap<>();
        public final Map<String, String> pendingOperationPlayers = new HashMap<>();
        public final Map<String, String> pendingOperationPayloads = new HashMap<>();
        public final Map<String, Double> minionCoins = new HashMap<>();
        public final Map<String, String> tombstoneOperations = new HashMap<>();
        public Integer defaultUpdateCount = null;
        public boolean trackMinionPresence = false;

        public int currentSchemaVersion = 0;
        public boolean minionItemsTableExists = true;
        public boolean containerTypeColumnExists = false;
        public final Set<String> existingMinionsColumns = new HashSet<>();
        public final List<PendingOperation> pendingOperations = new ArrayList<>();
        public final Map<String, Long> tombstones = new HashMap<>();

        public State() {
            // Default legacy columns in minions table (v1)
            existingMinionsColumns.addAll(List.of(
                    "id", "owner", "type", "tier", "world", "x", "y", "z", "yaw", "pitch",
                    "fuel", "fuel_end", "upgrade1", "upgrade2", "last_action", "last_was_place",
                    "harvested", "xp", "custom_id"
            ));
        }
    }

    public static Connection createMockConnection(State state) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        switch (name) {
                            case "setAutoCommit" -> {
                                boolean ac = (Boolean) args[0];
                                if (ac && state.throwOnSetAutoCommitTrue) {
                                    state.transactionEvents.add("setAutoCommit_failed");
                                    throw new SQLException("Simulated setAutoCommit(true) failure");
                                }
                                state.autoCommit = ac;
                                state.transactionEvents.add("setAutoCommit:" + ac);
                                return null;
                            }
                            case "getAutoCommit" -> {
                                return state.autoCommit;
                            }
                            case "commit" -> {
                                if (state.throwOnCommit) {
                                    state.transactionEvents.add("commit_failed");
                                    throw new SQLException("Simulated commit failure");
                                }
                                state.transactionEvents.add("commit");
                                return null;
                            }
                            case "rollback" -> {
                                if (state.throwOnRollback) {
                                    state.transactionEvents.add("rollback_failed");
                                    throw new SQLException("Simulated rollback failure");
                                }
                                state.transactionEvents.add("rollback");
                                return null;
                            }
                            case "isClosed" -> {
                                return false;
                            }
                            case "isValid" -> {
                                return true;
                            }
                            case "close" -> {
                                state.transactionEvents.add("close");
                                return null;
                            }
                            case "createStatement" -> {
                                return createMockStatement(state);
                            }
                            case "prepareStatement" -> {
                                String sql = (String) args[0];
                                return createMockPreparedStatement(state, sql);
                            }
                            case "getMetaData" -> {
                                return createMockDatabaseMetaData(state);
                            }
                            default -> {
                                return defaultValue(method.getReturnType());
                            }
                        }
                    }
                }
        );
    }

    private static Statement createMockStatement(State state) {
        return (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("executeUpdate".equals(name)) {
                        String sql = (String) args[0];
                        state.executedStatements.add(sql);
                        if (state.throwOnExecute) {
                            throw new SQLException("Simulated database failure for testing rollback");
                        }
                        return 1;
                    }
                    if ("executeQuery".equals(name)) {
                        String sql = (String) args[0];
                        state.executedStatements.add(sql);
                        if (sql.toUpperCase().contains("MINION_SCHEMA_VERSION")) {
                            return createSchemaVersionResultSet(state.currentSchemaVersion);
                        }
                        if (sql.toUpperCase().contains("PRAGMA TABLE_INFO(MINIONS)")) {
                            return createColumnsResultSet(state.existingMinionsColumns);
                        }
                        if (sql.toUpperCase().contains("PRAGMA TABLE_INFO(MINION_ITEMS)")) {
                            Set<String> cols = new HashSet<>(List.of("minion_id", "slot_index", "item_data"));
                            if (state.containerTypeColumnExists) cols.add("container_type");
                            return createColumnsResultSet(cols);
                        }
                        if (sql.toUpperCase().contains("SELECT OPERATION_ID")) {
                            return createPendingOperationsResultSet(state.pendingOperations);
                        }
                        if (sql.toUpperCase().contains("MINION_TOMBSTONES")) {
                            return createTombstonesResultSet(state.tombstones);
                        }
                        return createEmptyResultSet();
                    }
                    if ("close".equals(name)) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static PreparedStatement createMockPreparedStatement(State state, String sql) {
        PreparedStatementCall call = new PreparedStatementCall(sql);
        state.preparedCalls.add(call);

        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer idx) {
                        call.parameters.put(idx, args[1]);
                        return null;
                    }
                    if ("executeUpdate".equals(name)) {
                        state.executedStatements.add(sql);
                        if (state.throwOnExecute) {
                            throw new SQLException("Simulated database error for rollback test");
                        }
                        String upper = sql.toUpperCase();
                        if (upper.startsWith("DELETE FROM MINIONS WHERE ID = ?")) {
                            if (state.defaultUpdateCount != null) {
                                return state.defaultUpdateCount;
                            }
                            if (state.trackMinionPresence) {
                                String id = call.parameters.get(1) != null ? call.parameters.get(1).toString() : null;
                                if (id != null && state.minions.contains(id)) {
                                    state.minions.remove(id);
                                    return 1;
                                }
                                return 0;
                            }
                            return 1;
                        }
                        if (upper.contains("MINION_PENDING_OPERATIONS SET STATE = 'PAYOUT_FAILED'")) {
                            String opId = call.parameters.get(1) != null ? call.parameters.get(1).toString() : null;
                            String curState = state.pendingOperationStates.get(opId);
                            if ("ATTEMPTED".equals(curState)) {
                                state.pendingOperationStates.put(opId, "PAYOUT_FAILED");
                                return 1;
                            }
                            return 0;
                        }
                        if (upper.contains("MINION_PENDING_OPERATIONS SET STATE = 'CONFIRMED'")) {
                            String opId = call.parameters.get(1) != null ? call.parameters.get(1).toString() : null;
                            String curState = state.pendingOperationStates.get(opId);
                            if ("ATTEMPTED".equals(curState)) {
                                state.pendingOperationStates.put(opId, "CONFIRMED");
                                return 1;
                            }
                            return 0;
                        }
                        if (upper.contains("SET STATE = 'DELIVERY_ATTEMPTED'")) {
                            String opId = call.parameters.get(1) != null ? call.parameters.get(1).toString() : null;
                            String curState = state.pendingOperationStates.get(opId);
                            if ("MINION_REMOVED".equalsIgnoreCase(curState)) {
                                state.pendingOperationStates.put(opId, "DELIVERY_ATTEMPTED");
                                return 1;
                            }
                            return 0;
                        }
                        if (upper.contains("SET STATE = 'COMPLETED'")) {
                            String opId = call.parameters.get(1) != null ? call.parameters.get(1).toString() : null;
                            String curState = state.pendingOperationStates.get(opId);
                            if ("DELIVERY_ATTEMPTED".equalsIgnoreCase(curState)) {
                                state.pendingOperationStates.put(opId, "COMPLETED");
                                return 1;
                            }
                            return 0;
                        }
                        if (upper.contains("INSERT INTO MINION_TOMBSTONES")) {
                            String mId = call.parameters.get(1) != null ? call.parameters.get(1).toString() : null;
                            String opId = call.parameters.get(2) != null ? call.parameters.get(2).toString() : null;
                            if (mId != null) {
                                if (state.tombstones.containsKey(mId)) {
                                    throw new SQLException("Duplicate entry for primary key minion_tombstones: " + mId);
                                }
                                if (opId != null) state.tombstoneOperations.put(mId, opId);
                                state.tombstones.put(mId, 1L);
                            }
                            return 1;
                        }
                        if (upper.contains("INSERT INTO MINION_PENDING_OPERATIONS")) {
                            String opId = call.parameters.get(1) != null ? call.parameters.get(1).toString() : null;
                            String mId = call.parameters.get(2) != null ? call.parameters.get(2).toString() : null;
                            String pId = call.parameters.get(3) != null ? call.parameters.get(3).toString() : null;
                            String st = "ATTEMPTED";
                            if (call.parameters.get(4) != null) {
                                String p4 = call.parameters.get(4).toString();
                                if (!p4.matches("\\d+")) {
                                    st = p4;
                                }
                            }
                            String payload = call.parameters.get(6) != null ? call.parameters.get(6).toString() : (call.parameters.get(5) != null ? call.parameters.get(5).toString() : null);
                            if (opId != null) {
                                state.pendingOperationStates.put(opId, st);
                                if (mId != null) state.pendingOperationMinions.put(opId, mId);
                                if (pId != null) state.pendingOperationPlayers.put(opId, pId);
                                if (payload != null) state.pendingOperationPayloads.put(opId, payload);
                            }
                            return 1;
                        }
                        if (upper.contains("UPDATE MINIONS SET SHIPPING_COINS = ROUND(SHIPPING_COINS - ?") || upper.contains("UPDATE MINIONS SET SHIPPING_COINS = SHIPPING_COINS - ?")) {
                            String mId = call.parameters.get(2) != null ? call.parameters.get(2).toString() : (call.parameters.get(3) != null ? call.parameters.get(3).toString() : null);
                            double amt = call.parameters.get(1) instanceof Number n ? n.doubleValue() : 0.0;
                            if (mId != null) {
                                double cur = state.minionCoins.getOrDefault(mId, 0.0);
                                state.minionCoins.put(mId, Math.max(0.0, cur - amt));
                            }
                            return 1;
                        }
                        if (upper.contains("UPDATE MINIONS SET SHIPPING_COINS = ROUND(SHIPPING_COINS + ?") || upper.contains("UPDATE MINIONS SET SHIPPING_COINS = SHIPPING_COINS + ?")) {
                            String mId = call.parameters.get(2) != null ? call.parameters.get(2).toString() : null;
                            double amt = call.parameters.get(1) instanceof Number n ? n.doubleValue() : 0.0;
                            if (mId != null) {
                                double cur = state.minionCoins.getOrDefault(mId, 0.0);
                                state.minionCoins.put(mId, cur + amt);
                            }
                            return 1;
                        }
                        return state.defaultUpdateCount != null ? state.defaultUpdateCount : 1;
                    }
                    if ("executeQuery".equals(name)) {
                        state.executedStatements.add(sql);
                        String upper = sql.toUpperCase();
                        if (upper.contains("MINION_SCHEMA_VERSION")) {
                            return createSchemaVersionResultSet(state.currentSchemaVersion);
                        }
                        if (upper.contains("MINION_TOMBSTONES WHERE MINION_ID = ? AND OPERATION_ID = ?")) {
                            String mId = call.parameters.get(1) != null ? call.parameters.get(1).toString() : null;
                            String opId = call.parameters.get(2) != null ? call.parameters.get(2).toString() : null;
                            if (mId != null && opId != null && opId.equals(state.tombstoneOperations.get(mId))) {
                                return createSingleRowResultSet();
                            }
                            return createEmptyResultSet();
                        }
                        if (upper.contains("SELECT OPERATION_ID FROM MINION_TOMBSTONES WHERE MINION_ID = ?")) {
                            String mId = call.parameters.get(1) != null ? call.parameters.get(1).toString() : null;
                            if (mId != null && state.tombstoneOperations.containsKey(mId)) {
                                return createSingleColumnResultSet("operation_id", state.tombstoneOperations.get(mId));
                            }
                            return createEmptyResultSet();
                        }
                        if (upper.contains("SELECT 1 FROM MINION_TOMBSTONES WHERE MINION_ID = ?")) {
                            String mId = call.parameters.get(1) != null ? call.parameters.get(1).toString() : null;
                            if (mId != null && (state.tombstones.containsKey(mId) || state.tombstoneOperations.containsKey(mId))) {
                                return createSingleRowResultSet();
                            }
                            return createEmptyResultSet();
                        }
                        if (upper.contains("SELECT 1 FROM MINIONS WHERE ID = ?")) {
                            String mId = call.parameters.get(1) != null ? call.parameters.get(1).toString() : null;
                            if (mId != null && state.minions.contains(mId)) {
                                return createSingleRowResultSet();
                            }
                            return createEmptyResultSet();
                        }
                        if (upper.contains("FROM MINION_PENDING_OPERATIONS WHERE OPERATION_ID = ?")) {
                            String opId = call.parameters.get(1) != null ? call.parameters.get(1).toString() : null;
                            if (opId != null && state.pendingOperationStates.containsKey(opId)) {
                                String opState = state.pendingOperationStates.get(opId);
                                String mId = state.pendingOperationMinions.getOrDefault(opId, "");
                                String pId = state.pendingOperationPlayers.getOrDefault(opId, "");
                                String payload = state.pendingOperationPayloads.getOrDefault(opId, "");
                                return createSinglePendingOpResultSet(opId, mId, pId, opState, payload);
                            }
                            return createEmptyResultSet();
                        }
                        if (upper.contains("MINION_TOMBSTONES")) {
                            return createTombstonesResultSet(state.tombstones);
                        }
                        return createEmptyResultSet();
                    }
                    if ("addBatch".equals(name)) {
                        return null;
                    }
                    if ("executeBatch".equals(name)) {
                        state.executedStatements.add(sql);
                        return new int[]{1};
                    }
                    if ("close".equals(name)) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static DatabaseMetaData createMockDatabaseMetaData(State state) {
        return (DatabaseMetaData) Proxy.newProxyInstance(
                DatabaseMetaData.class.getClassLoader(),
                new Class<?>[]{DatabaseMetaData.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getDatabaseProductName".equals(name)) {
                        return state.databaseProductName;
                    }
                    if ("getTables".equals(name)) {
                        String tableName = args != null && args.length >= 3 && args[2] != null ? args[2].toString() : "";
                        boolean exists = true;
                        if ("minion_items".equalsIgnoreCase(tableName)) {
                            exists = state.minionItemsTableExists;
                        }
                        return createTableExistsResultSet(exists);
                    }
                    if ("getColumns".equals(name)) {
                        String tableName = args != null && args.length >= 3 && args[2] != null ? args[2].toString() : "";
                        String columnName = args != null && args.length >= 4 && args[3] != null ? args[3].toString() : "";
                        boolean exists = false;
                        if ("minion_items".equalsIgnoreCase(tableName) && "container_type".equalsIgnoreCase(columnName)) {
                            exists = state.containerTypeColumnExists;
                        } else if ("minions".equalsIgnoreCase(tableName)) {
                            exists = state.existingMinionsColumns.contains(columnName.toLowerCase());
                        }
                        return createColumnExistsResultSet(exists, columnName);
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static ResultSet createTableExistsResultSet(boolean exists) {
        final boolean[] read = {false};
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("next".equals(name)) {
                        if (exists && !read[0]) {
                            read[0] = true;
                            return true;
                        }
                        return false;
                    }
                    if ("close".equals(name)) return null;
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static ResultSet createColumnExistsResultSet(boolean exists, String colName) {
        final boolean[] read = {false};
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("next".equals(name)) {
                        if (exists && !read[0]) {
                            read[0] = true;
                            return true;
                        }
                        return false;
                    }
                    if ("getString".equals(name)) {
                        return colName;
                    }
                    if ("close".equals(name)) return null;
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static ResultSet createColumnsResultSet(Set<String> columnNames) {
        Iterator<String> it = columnNames.iterator();
        final String[] current = {null};
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("next".equals(name)) {
                        if (it.hasNext()) {
                            current[0] = it.next();
                            return true;
                        }
                        return false;
                    }
                    if ("getString".equals(name)) {
                        return current[0];
                    }
                    if ("close".equals(name)) return null;
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static ResultSet createSchemaVersionResultSet(int version) {
        final boolean[] read = {false};
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("next".equals(name)) {
                        if (!read[0]) {
                            read[0] = true;
                            return true;
                        }
                        return false;
                    }
                    if ("getInt".equals(name)) {
                        return version;
                    }
                    if ("close".equals(name)) return null;
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static ResultSet createPendingOperationsResultSet(List<PendingOperation> ops) {
        Iterator<PendingOperation> it = ops.iterator();
        final PendingOperation[] cur = {null};
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("next".equals(name)) {
                        if (it.hasNext()) {
                            cur[0] = it.next();
                            return true;
                        }
                        return false;
                    }
                    if ("getString".equals(name)) {
                        String col = (String) args[0];
                        if ("operation_id".equalsIgnoreCase(col)) return cur[0].operationId();
                        if ("minion_id".equalsIgnoreCase(col)) return cur[0].minionId().toString();
                        if ("player_uuid".equalsIgnoreCase(col)) return cur[0].playerUuid().toString();
                        if ("state".equalsIgnoreCase(col)) return cur[0].state();
                        if ("payload".equalsIgnoreCase(col)) return cur[0].payload();
                    }
                    if ("getLong".equals(name)) {
                        return cur[0].createdAt();
                    }
                    if ("close".equals(name)) return null;
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static class TombstoneEntryHolder {
        Map.Entry<String, Long> entry;
    }

    private static ResultSet createTombstonesResultSet(Map<String, Long> tombstones) {
        Iterator<Map.Entry<String, Long>> it = tombstones.entrySet().iterator();
        final TombstoneEntryHolder cur = new TombstoneEntryHolder();
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("next".equals(name)) {
                        if (it.hasNext()) {
                            cur.entry = it.next();
                            return true;
                        }
                        return false;
                    }
                    if ("getString".equals(name)) {
                        return cur.entry != null ? cur.entry.getKey() : null;
                    }
                    if ("getLong".equals(name)) {
                        return cur.entry != null && cur.entry.getValue() != null ? cur.entry.getValue() : 0L;
                    }
                    if ("close".equals(name)) return null;
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static ResultSet createSingleColumnResultSet(String colName, String value) {
        final boolean[] read = {false};
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("next".equals(name)) {
                        if (!read[0]) {
                            read[0] = true;
                            return true;
                        }
                        return false;
                    }
                    if ("getString".equals(name) && args != null && args.length >= 1) {
                        String col = (String) args[0];
                        if (colName.equalsIgnoreCase(col)) return value;
                    }
                    if ("close".equals(name)) return null;
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static ResultSet createSingleRowResultSet() {
        final boolean[] read = {false};
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("next".equals(name)) {
                        if (!read[0]) {
                            read[0] = true;
                            return true;
                        }
                        return false;
                    }
                    if ("close".equals(name)) return null;
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static ResultSet createSinglePendingOpResultSet(String opId, String minionId, String playerUuid, String opState, String payload) {
        final boolean[] read = {false};
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("next".equals(name)) {
                        if (!read[0]) {
                            read[0] = true;
                            return true;
                        }
                        return false;
                    }
                    if ("getString".equals(name)) {
                        String col = (String) args[0];
                        if ("operation_id".equalsIgnoreCase(col)) return opId;
                        if ("minion_id".equalsIgnoreCase(col)) return minionId;
                        if ("player_uuid".equalsIgnoreCase(col)) return playerUuid;
                        if ("state".equalsIgnoreCase(col)) return opState;
                        if ("payload".equalsIgnoreCase(col)) return payload;
                    }
                    if ("getLong".equals(name)) {
                        return System.currentTimeMillis();
                    }
                    if ("close".equals(name)) return null;
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static ResultSet createEmptyResultSet() {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if ("next".equals(method.getName())) return false;
                    if ("close".equals(method.getName())) return null;
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == void.class) return null;
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == double.class) return 0.0;
        if (returnType == float.class) return 0.0f;
        return null;
    }
}
