/*
 Licensed to Diennea S.r.l. under one
 or more contributor license agreements. See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership. Diennea S.r.l. licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.

 */

package herddb.sql.functions;

import static herddb.model.Column.column;
import herddb.core.AbstractTableManager;
import herddb.core.DBManager;
import herddb.core.TableSpaceManager;
import herddb.model.Column;
import herddb.model.ColumnTypes;
import herddb.model.ExecutionPlan;
import herddb.model.Index;
import herddb.model.StatementExecutionException;
import herddb.model.Table;
import herddb.model.TableDoesNotExistException;
import herddb.model.TableSpaceDoesNotExistException;
import herddb.model.commands.SQLPlannedOperationStatement;
import herddb.model.planner.ValuesOp;
import herddb.sql.SQLStatementEvaluationContext;
import herddb.sql.TranslatedQuery;
import herddb.sql.expressions.ConstantExpression;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;


/**
 * Utility class that is responsible for generating Show Create Table command
 *
 * @author amitchavan
 */
public class ShowCreateTableCalculator {

    public static String calculate(boolean showCreateIndex, String tableName, String tableSpace, AbstractTableManager tableManager) {

        Table t = tableManager.getTable();
        if (t == null) {
            throw new TableDoesNotExistException(String.format("Table %s does not exist.", tableName));
        }

        StringBuilder sb = new StringBuilder("CREATE TABLE " + tableSpace + "." + tableName);
        StringJoiner joiner = new StringJoiner(",", "(", ")");
        for (Column c : t.getColumns()) {
            joiner.add(c.name + " " + ColumnTypes.typeToString(c.type) + autoIncrementColumn(t, c) + defaultClause(c));
        }

        if (t.getPrimaryKey().length > 0) {
            joiner.add("PRIMARY KEY(" + String.join(",", t.getPrimaryKey()) + ")");
        }

        if (showCreateIndex) {
            List<Index> indexes = tableManager.getAvailableIndexes();

            if (!indexes.isEmpty()) {
                indexes.forEach(idx -> {
                    if (idx.unique) {
                        joiner.add("UNIQUE KEY " + idx.name + " (" + String.join(",", idx.columnNames) + ")");
                    } else {
                        joiner.add("INDEX " + idx.name + "(" + String.join(",", idx.columnNames) + ")");
                    }
                });
            }
        }

        sb.append(joiner.toString());
        return sb.toString();
    }

    private static String autoIncrementColumn(Table t, Column c) {
        if (t.auto_increment
                && c.name.equals(t.primaryKey[0])
                && (c.type == ColumnTypes.INTEGER
                || c.type == ColumnTypes.NOTNULL_INTEGER
                || c.type == ColumnTypes.LONG
                || c.type == ColumnTypes.NOTNULL_LONG)) {
            return " auto_increment";
        }
        return "";
    }

    private static String defaultClause(Column c) {
        if (c.defaultValue != null) {
            return " DEFAULT " + Column.defaultValueToString(c);
        }
        return "";
    }


    public static TranslatedQuery calculateShowCreateTable(String query, String defaultTablespace, List<Object> parameters, DBManager manager) {
        String[] items = {"SHOW", "CREATE", "TABLE"};
        if (Arrays.stream(items).allMatch(query::contains)) {
            query = query.substring(Arrays.stream(items).collect(Collectors.joining(" ")).length()).trim();
            String tableSpace = defaultTablespace;
            String tableName;
            boolean showCreateIndex = query.contains("WITH INDEXES");
            if (showCreateIndex) {
                query = query.substring(0, query.indexOf("WITH INDEXES"));
            }

            if (query.contains(".")) {
                String[] tokens = query.split("\\.");
                tableSpace = tokens[0].trim();
                tableName = tokens[1].trim();
            } else {
                tableName = query.trim();
            }

            TableSpaceManager tableSpaceManager = manager.getTableSpaceManager(tableSpace);

            if (tableSpaceManager == null) {
                throw new TableSpaceDoesNotExistException(String.format("Tablespace %s does not exist.", tableSpace));
            }

            AbstractTableManager tableManager = tableSpaceManager.getTableManager(tableName);

            if (tableManager == null || tableManager.getCreatedInTransaction() > 0) {
                throw new TableDoesNotExistException(String.format("Table %s does not exist.", tableName));
            }

            String showCreateResult = ShowCreateTableCalculator.calculate(showCreateIndex, tableName, tableSpace, tableManager);
            ValuesOp values = new ValuesOp(manager.getNodeId(),
                    new String[]{"tabledef"},
                    new Column[]{
                            column("tabledef", ColumnTypes.STRING)},
                    Arrays.asList(
                            Arrays.asList(
                                    new ConstantExpression(showCreateResult, ColumnTypes.NOTNULL_STRING)
                            )
                    )
            );
            ExecutionPlan executionPlan = ExecutionPlan.simple(
                    new SQLPlannedOperationStatement(values),
                    values
            );
            return new TranslatedQuery(executionPlan, new SQLStatementEvaluationContext(query, parameters, false, false));
        } else {
            throw new StatementExecutionException(String.format("Incorrect Syntax for SHOW CREATE TABLE tablespace.tablename"));
        }
    }

}
