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
package herddb.model.commands;

import herddb.model.Column;
import herddb.model.DDLStatement;
import java.util.List;

/**
 * Manipulate Table Structure
 *
 * @author enrico.olivelli
 */
public class AlterTableStatement extends DDLStatement {

    private final List<Column> addColumns;
    private final List<String> dropColumns;
    private final String table;

    public AlterTableStatement(List<Column> addColumns, List<String> dropColumns, String table, String tableSpace) {
        super(tableSpace);
        this.table = table;
        this.addColumns = addColumns;
        this.dropColumns = dropColumns;
    }

    public String getTable() {
        return table;
    }

    public List<Column> getAddColumns() {
        return addColumns;
    }

    public List<String> getDropColumns() {
        return dropColumns;
    }

}
