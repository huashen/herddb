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
package herddb.core;

import herddb.log.CommitLog;
import herddb.log.CommitLogManager;
import herddb.metadata.MetadataStorageManager;
import herddb.model.TableSpace;
import herddb.model.Statement;
import herddb.model.StatementExecutionException;
import herddb.model.StatementExecutionResult;
import herddb.model.Transaction;
import herddb.model.commands.CreateTableSpaceStatement;
import herddb.storage.DataStorageManager;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * General Manager of the local instance of HerdDB
 *
 * @author enrico.olivelli
 */
public class DBManager {

    private final Logger LOGGER = Logger.getLogger(DBManager.class.getName());
    private final Map<String, TableSpaceManager> tablesSpaces = new ConcurrentHashMap<>();
    private final MetadataStorageManager metadataStorageManager;
    private final DataStorageManager dataStorageManager;
    private final CommitLogManager commitLogManager;
    private final String nodeId;
    private final ReentrantReadWriteLock generalLock = new ReentrantReadWriteLock();

    public DBManager(String nodeId, MetadataStorageManager metadataStorageManager, DataStorageManager dataStorageManager, CommitLogManager commitLogManager) {
        this.metadataStorageManager = metadataStorageManager;
        this.dataStorageManager = dataStorageManager;
        this.commitLogManager = commitLogManager;
        this.nodeId = nodeId;
    }

    /**
     * Initial boot of the system
     */
    public void start() {
        generalLock.writeLock().lock();
        try {
            for (String tableSpace : metadataStorageManager.listTableSpaces()) {
                bootTableSpace(tableSpace);
            }
        } finally {
            generalLock.writeLock().unlock();
        }
    }

    private void bootTableSpace(String tableSpaceName) {
        TableSpace tableSpace = metadataStorageManager.describeTableSpace(tableSpaceName);
        if (!tableSpace.replicas.contains(nodeId)) {
            return;
        }
        LOGGER.log(Level.SEVERE, "Booting tablespace " + tableSpaceName);
        CommitLog commitLog = commitLogManager.createCommitLog(tableSpaceName);
        generalLock.writeLock().lock();
        try {
            TableSpaceManager manager = new TableSpaceManager(tableSpaceName, metadataStorageManager, dataStorageManager, commitLog);
            tablesSpaces.put(tableSpaceName, manager);
            manager.start();
        } finally {
            generalLock.writeLock().unlock();
        }
    }

    public StatementExecutionResult executeStatement(Statement statement, Transaction transaction) throws StatementExecutionException {
        LOGGER.log(Level.FINEST, "executeStatement {0} {1}", new Object[]{statement, transaction});
        String tableSpace = statement.getTableSpace();
        if (tableSpace == null) {
            throw new StatementExecutionException("invalid tableSpace " + tableSpace);
        }

        if (statement instanceof CreateTableSpaceStatement) {
            if (transaction != null) {
                throw new StatementExecutionException("CREATE TABLESPACE cannot be issued inside a transaction");
            }
            return createTableSpace((CreateTableSpaceStatement) statement);
        }
        if (transaction != null && !transaction.tableSpace.equals(tableSpace)) {
            throw new StatementExecutionException("transaction " + transaction.transactionId + " is for tablespace " + transaction.tableSpace + ", not for " + tableSpace);
        }

        TableSpaceManager manager;
        generalLock.readLock().lock();
        try {
            manager = tablesSpaces.get(tableSpace);
        } finally {
            generalLock.readLock().unlock();
        }
        if (manager == null) {
            throw new StatementExecutionException("not such tableSpace " + tableSpace + " here");
        }
        return manager.executeStatement(statement, transaction);
    }

    /**
     * Utility method for auto-commit statements
     *
     * @param statement
     * @return
     * @throws StatementExecutionException
     */
    public StatementExecutionResult executeStatement(Statement statement) throws StatementExecutionException {
        return executeStatement(statement, null);
    }

    private StatementExecutionResult createTableSpace(CreateTableSpaceStatement createTableSpaceStatement) throws StatementExecutionException {
        TableSpace tableSpace;
        try {
            tableSpace = TableSpace.builder().leader(createTableSpaceStatement.getLeaderId()).name(createTableSpaceStatement.getTableSpace()).replicas(createTableSpaceStatement.getReplicas()).build();
        } catch (IllegalArgumentException invalid) {
            throw new StatementExecutionException("invalid CREATE TABLESPACE statement: " + invalid.getMessage(), invalid);
        }

        metadataStorageManager.registerTableSpace(tableSpace);
        if (tableSpace.replicas.contains(nodeId)) {
            bootTableSpace(tableSpace.name);
        }
        return new StatementExecutionResult(1);
    }

    void close() {

    }

}