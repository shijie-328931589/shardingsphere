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

package org.apache.shardingsphere.mode.metadata.refresher.statistics;

import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.config.props.temporary.TemporaryConfigurationProperties;
import org.apache.shardingsphere.infra.config.props.temporary.TemporaryConfigurationPropertyKey;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereColumn;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereSchema;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereTable;
import org.apache.shardingsphere.infra.metadata.statistics.DatabaseStatistics;
import org.apache.shardingsphere.infra.metadata.statistics.SchemaStatistics;
import org.apache.shardingsphere.infra.metadata.statistics.ShardingSphereStatistics;
import org.apache.shardingsphere.infra.metadata.statistics.TableStatistics;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.test.util.PropertiesBuilder;
import org.apache.shardingsphere.test.util.PropertiesBuilder.Property;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatisticsRefreshEngineTest {
    
    @Test
    void assertRefresh() {
        ContextManager contextManager = mock(ContextManager.class, RETURNS_DEEP_STUBS);
        ShardingSphereStatistics statistics = mockStatistics();
        when(contextManager.getMetaDataContexts().getStatistics()).thenReturn(statistics);
        ShardingSphereMetaData metaData = mockMetaData();
        when(contextManager.getMetaDataContexts().getMetaData()).thenReturn(metaData);
        when(contextManager.getMetaDataContexts().getMetaData().getProps()).thenReturn(new ConfigurationProperties(new Properties()));
        when(contextManager.getMetaDataContexts().getMetaData().getTemporaryProps()).thenReturn(new TemporaryConfigurationProperties(
                PropertiesBuilder.build(new Property(TemporaryConfigurationPropertyKey.PROXY_META_DATA_COLLECTOR_ENABLED.getKey(), Boolean.TRUE.toString()))));
        when(contextManager.getComputeNodeInstanceContext().getLockContext().tryLock(any(), anyLong())).thenReturn(true);
        new StatisticsRefreshEngine(contextManager).refresh();
        verify(contextManager.getPersistServiceFacade().getMetaDataPersistFacade().getStatisticsService()).update(any());
    }
    
    private ShardingSphereStatistics mockStatistics() {
        ShardingSphereStatistics result = new ShardingSphereStatistics();
        DatabaseStatistics databaseStatistics = new DatabaseStatistics();
        SchemaStatistics schemaStatistics = new SchemaStatistics();
        databaseStatistics.putSchemaStatistics("foo_schema", schemaStatistics);
        TableStatistics tableStatistics = new TableStatistics("test_table");
        schemaStatistics.putTableStatistics("test_table", tableStatistics);
        result.getDatabaseStatisticsMap().put("foo_db", databaseStatistics);
        return result;
    }
    
    private ShardingSphereMetaData mockMetaData() {
        ShardingSphereMetaData result = mock(ShardingSphereMetaData.class);
        ShardingSphereSchema schema = new ShardingSphereSchema("foo_schema", Collections.singleton(mockTable()), Collections.emptyList());
        ShardingSphereDatabase database = new ShardingSphereDatabase("foo_db", mock(), mock(), mock(), Collections.singleton(schema));
        when(result.getAllDatabases()).thenReturn(Collections.singleton(database));
        when(result.getDatabase("foo_db")).thenReturn(database);
        when(result.containsDatabase("foo_db")).thenReturn(true);
        return result;
    }
    
    private static ShardingSphereTable mockTable() {
        ShardingSphereTable result = mock(ShardingSphereTable.class);
        when(result.getName()).thenReturn("test_table");
        ShardingSphereColumn column1 = new ShardingSphereColumn("col_1", Types.INTEGER, false, false, false, true, false, false);
        ShardingSphereColumn column2 = new ShardingSphereColumn("col_2", Types.INTEGER, false, false, false, true, false, false);
        when(result.getAllColumns()).thenReturn(Arrays.asList(column1, column2));
        return result;
    }
}
