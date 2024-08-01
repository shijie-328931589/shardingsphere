/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.test.e2e.engine.type;

import com.google.common.base.Splitter;
import org.apache.shardingsphere.infra.datanode.DataNode;
import org.apache.shardingsphere.infra.expr.core.InlineExpressionParserFactory;
import org.apache.shardingsphere.test.e2e.engine.context.SingleE2EContext;
import org.apache.shardingsphere.test.e2e.env.container.atomic.enums.AdapterMode;
import org.apache.shardingsphere.test.e2e.env.container.atomic.enums.AdapterType;
import org.apache.shardingsphere.test.e2e.framework.type.SQLCommandType;
import org.apache.shardingsphere.test.e2e.framework.type.SQLExecuteType;
import org.apache.shardingsphere.test.e2e.cases.dataset.metadata.DataSetColumn;
import org.apache.shardingsphere.test.e2e.cases.dataset.metadata.DataSetIndex;
import org.apache.shardingsphere.test.e2e.cases.dataset.metadata.DataSetMetaData;
import org.apache.shardingsphere.test.e2e.engine.arg.E2ETestCaseArgumentsProvider;
import org.apache.shardingsphere.test.e2e.engine.arg.E2ETestCaseSettings;
import org.apache.shardingsphere.test.e2e.engine.composer.E2EContainerComposer;
import org.apache.shardingsphere.test.e2e.framework.param.array.E2ETestParameterFactory;
import org.apache.shardingsphere.test.e2e.framework.param.model.AssertionTestParameter;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@E2ETestCaseSettings(SQLCommandType.DDL)
class DDLE2EIT {
    
    @ParameterizedTest(name = "{0}")
    @EnabledIf("isEnabled")
    @ArgumentsSource(E2ETestCaseArgumentsProvider.class)
    void assertExecuteUpdate(final AssertionTestParameter testParam) throws SQLException {
        // TODO make sure test case can not be null
        if (null == testParam.getTestCaseContext()) {
            return;
        }
        E2EContainerComposer containerComposer = new E2EContainerComposer(testParam.getKey(), testParam.getScenario(), testParam.getDatabaseType(),
                AdapterMode.valueOf(testParam.getMode().toUpperCase()), AdapterType.valueOf(testParam.getAdapter().toUpperCase()));
        SingleE2EContext singleE2EContext = new SingleE2EContext(testParam);
        init(containerComposer, singleE2EContext);
        try (Connection connection = containerComposer.getTargetDataSource().getConnection()) {
            if (SQLExecuteType.Literal == singleE2EContext.getSqlExecuteType()) {
                executeUpdateForStatement(singleE2EContext, connection);
            } else {
                executeUpdateForPreparedStatement(singleE2EContext, connection);
            }
            assertTableMetaData(testParam, containerComposer, singleE2EContext);
        }
        tearDown(containerComposer, singleE2EContext);
    }
    
    private void executeUpdateForStatement(final SingleE2EContext singleE2EContext, final Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            assertFalse(statement.executeUpdate(singleE2EContext.getSQL()) > 0, "Not a DDL statement.");
        }
        waitCompleted();
    }
    
    private void executeUpdateForPreparedStatement(final SingleE2EContext singleE2EContext, final Connection connection) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(singleE2EContext.getSQL())) {
            assertFalse(preparedStatement.executeUpdate() > 0, "Not a DDL statement.");
        }
        waitCompleted();
    }
    
    @ParameterizedTest(name = "{0}")
    @EnabledIf("isEnabled")
    @ArgumentsSource(E2ETestCaseArgumentsProvider.class)
    void assertExecute(final AssertionTestParameter testParam) throws Exception {
        // TODO make sure test case can not be null
        if (null == testParam.getTestCaseContext()) {
            return;
        }
        E2EContainerComposer containerComposer = new E2EContainerComposer(testParam.getKey(), testParam.getScenario(), testParam.getDatabaseType(),
                AdapterMode.valueOf(testParam.getMode().toUpperCase()), AdapterType.valueOf(testParam.getAdapter().toUpperCase()));
        SingleE2EContext singleE2EContext = new SingleE2EContext(testParam);
        init(containerComposer, singleE2EContext);
        try (Connection connection = containerComposer.getTargetDataSource().getConnection()) {
            if (SQLExecuteType.Literal == singleE2EContext.getSqlExecuteType()) {
                executeForStatement(singleE2EContext, connection);
            } else {
                executeForPreparedStatement(singleE2EContext, connection);
            }
            assertTableMetaData(testParam, containerComposer, singleE2EContext);
        }
        tearDown(containerComposer, singleE2EContext);
    }
    
    private void executeForStatement(final SingleE2EContext singleE2EContext, final Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            assertFalse(statement.execute(singleE2EContext.getSQL()), "Not a DDL statement.");
        }
        waitCompleted();
    }
    
    private void executeForPreparedStatement(final SingleE2EContext singleE2EContext, final Connection connection) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(singleE2EContext.getSQL())) {
            assertFalse(preparedStatement.execute(), "Not a DDL statement.");
        }
        waitCompleted();
    }
    
    private void init(final E2EContainerComposer containerComposer, final SingleE2EContext singleE2EContext) throws SQLException {
        assertNotNull(singleE2EContext.getAssertion().getInitialSQL(), "Init SQL is required");
        assertNotNull(singleE2EContext.getAssertion().getInitialSQL().getAffectedTable(), "Expected affected table is required");
        try (Connection connection = containerComposer.getTargetDataSource().getConnection()) {
            executeInitSQLs(singleE2EContext, connection);
        }
    }
    
    private void executeInitSQLs(final SingleE2EContext singleE2EContext, final Connection connection) throws SQLException {
        if (null == singleE2EContext.getAssertion().getInitialSQL().getSql()) {
            return;
        }
        for (String each : Splitter.on(";").trimResults().omitEmptyStrings().splitToList(singleE2EContext.getAssertion().getInitialSQL().getSql())) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(each)) {
                preparedStatement.executeUpdate();
            }
            waitCompleted();
        }
    }
    
    private void tearDown(final E2EContainerComposer containerComposer, final SingleE2EContext singleE2EContext) throws SQLException {
        if (null != singleE2EContext.getAssertion().getDestroySQL()) {
            try (Connection connection = containerComposer.getTargetDataSource().getConnection()) {
                executeDestroySQLs(singleE2EContext, connection);
            }
        }
    }
    
    private void executeDestroySQLs(final SingleE2EContext singleE2EContext, final Connection connection) throws SQLException {
        if (null == singleE2EContext.getAssertion().getDestroySQL().getSql()) {
            return;
        }
        for (String each : Splitter.on(";").trimResults().omitEmptyStrings().splitToList(singleE2EContext.getAssertion().getDestroySQL().getSql())) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(each)) {
                preparedStatement.executeUpdate();
            }
            waitCompleted();
        }
    }
    
    private void assertTableMetaData(final AssertionTestParameter testParam, final E2EContainerComposer containerComposer, final SingleE2EContext singleE2EContext) throws SQLException {
        String tableName = singleE2EContext.getAssertion().getInitialSQL().getAffectedTable();
        DataSetMetaData expected = singleE2EContext.getDataSet().findMetaData(tableName);
        Collection<DataNode> dataNodes = InlineExpressionParserFactory.newInstance(expected.getDataNodes()).splitAndEvaluate().stream().map(DataNode::new).collect(Collectors.toList());
        if (expected.getColumns().isEmpty()) {
            assertNotContainsTable(containerComposer, dataNodes);
            return;
        }
        assertTableMetaData(testParam, getActualColumns(containerComposer, dataNodes), getActualIndexes(containerComposer, dataNodes), expected);
    }
    
    private void assertTableMetaData(final AssertionTestParameter testParam, final List<DataSetColumn> actualColumns, final List<DataSetIndex> actualIndexes, final DataSetMetaData expected) {
        assertColumnMetaData(testParam, actualColumns, expected.getColumns());
        assertIndexMetaData(actualIndexes, expected.getIndexes());
    }
    
    private void assertNotContainsTable(final E2EContainerComposer containerComposer, final Collection<DataNode> dataNodes) throws SQLException {
        for (DataNode each : dataNodes) {
            try (Connection connection = containerComposer.getActualDataSourceMap().get(each.getDataSourceName()).getConnection()) {
                assertNotContainsTable(connection, each.getTableName());
            }
        }
    }
    
    private void assertNotContainsTable(final Connection connection, final String tableName) throws SQLException {
        assertFalse(connection.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"}).next(), String.format("Table `%s` should not existed", tableName));
    }
    
    private List<DataSetColumn> getActualColumns(final E2EContainerComposer containerComposer, final Collection<DataNode> dataNodes) throws SQLException {
        Set<DataSetColumn> result = new LinkedHashSet<>();
        for (DataNode each : dataNodes) {
            try (Connection connection = containerComposer.getActualDataSourceMap().get(each.getDataSourceName()).getConnection()) {
                result.addAll(getActualColumns(connection, each.getTableName()));
            }
        }
        return new LinkedList<>(result);
    }
    
    private List<DataSetColumn> getActualColumns(final Connection connection, final String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(null, null, tableName, null)) {
            List<DataSetColumn> result = new LinkedList<>();
            while (resultSet.next()) {
                DataSetColumn each = new DataSetColumn();
                each.setName(resultSet.getString("COLUMN_NAME"));
                String typeName = resultSet.getString("TYPE_NAME");
                each.setType("CHARACTER VARYING".equals(typeName) ? "VARCHAR".toLowerCase() : typeName.toLowerCase());
                result.add(each);
            }
            return result;
        }
    }
    
    private List<DataSetIndex> getActualIndexes(final E2EContainerComposer containerComposer, final Collection<DataNode> dataNodes) throws SQLException {
        Set<DataSetIndex> result = new LinkedHashSet<>();
        for (DataNode each : dataNodes) {
            try (Connection connection = containerComposer.getActualDataSourceMap().get(each.getDataSourceName()).getConnection()) {
                result.addAll(getActualIndexes(connection, each.getTableName()));
            }
        }
        return new LinkedList<>(result);
    }
    
    private List<DataSetIndex> getActualIndexes(final Connection connection, final String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getIndexInfo(null, null, tableName, false, false)) {
            List<DataSetIndex> result = new LinkedList<>();
            while (resultSet.next()) {
                DataSetIndex each = new DataSetIndex();
                each.setName(resultSet.getString("INDEX_NAME"));
                each.setUnique(!resultSet.getBoolean("NON_UNIQUE"));
                each.setColumns(resultSet.getString("COLUMN_NAME"));
                result.add(each);
            }
            return result;
        }
    }
    
    private void assertColumnMetaData(final AssertionTestParameter testParam, final List<DataSetColumn> actual, final List<DataSetColumn> expected) {
        assertThat("Size of actual columns is different with size of expected columns.", actual.size(), is(expected.size()));
        for (int i = 0; i < actual.size(); i++) {
            assertColumnMetaData(testParam, actual.get(i), expected.get(i));
        }
    }
    
    private void assertColumnMetaData(final AssertionTestParameter testParam, final DataSetColumn actual, final DataSetColumn expected) {
        assertThat("Mismatched column name.", actual.getName(), is(expected.getName()));
        if ("MySQL".equals(testParam.getDatabaseType().getType()) && "integer".equals(expected.getType())) {
            assertThat("Mismatched column type.", actual.getType(), is("int"));
        } else if ("PostgreSQL".equals(testParam.getDatabaseType().getType()) && "integer".equals(expected.getType())) {
            assertThat("Mismatched column type.", actual.getType(), is("int4"));
        } else if ("openGauss".equals(testParam.getDatabaseType().getType()) && "integer".equals(expected.getType())) {
            assertThat("Mismatched column type.", actual.getType(), is("int4"));
        } else {
            assertThat("Mismatched column type.", actual.getType(), is(expected.getType()));
        }
    }
    
    private void assertIndexMetaData(final List<DataSetIndex> actual, final List<DataSetIndex> expected) {
        for (DataSetIndex each : expected) {
            assertIndexMetaData(actual, each);
        }
    }
    
    private void assertIndexMetaData(final List<DataSetIndex> actual, final DataSetIndex expected) {
        for (DataSetIndex each : actual) {
            if (expected.getName().equals(each.getName())) {
                assertThat(each.isUnique(), is(expected.isUnique()));
            }
        }
    }
    
    private void waitCompleted() {
        Awaitility.await().pollDelay(1500L, TimeUnit.MILLISECONDS).until(() -> true);
    }
    
    private static boolean isEnabled() {
        return E2ETestParameterFactory.containsTestParameter();
    }
}
