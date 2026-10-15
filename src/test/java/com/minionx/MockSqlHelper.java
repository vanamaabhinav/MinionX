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

        public int currentSchemaVersion = 0;
        public boolean minionItemsTableExists = true;
        public boolean containerTypeColumnExists = false;
        public final Set<String> existingMinionsColumns = new HashSet<>();
        public final List<PendingOperation> pendingOperations = new ArrayList<>();

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
                                state.autoCommit = ac;
                                state.transactionEvents.add("setAutoCommit:" + ac);
                                return null;
                            }
                            case "getAutoCommit" -> {
                                return state.autoCommit;
                            }
                            case "commit" -> {
                                state.transactionEvents.add("commit");
                                return null;
                            }
                            case "rollback" -> {
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
                        return 1;
                    }
                    if ("executeQuery".equals(name)) {
                        state.executedStatements.add(sql);
                        if (sql.toUpperCase().contains("MINION_SCHEMA_VERSION")) {
                            return createSchemaVersionResultSet(state.currentSchemaVersion);
                        }
                        return createEmptyResultSet();
                    }
                    if ("addBatch".equals(name)) {
                        return null;
                    }
                    if ("executeBatch".equals(name)) {
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
                        return "SQLite";
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
                    }
                    if ("getLong".equals(name)) {
                        return cur[0].createdAt();
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
