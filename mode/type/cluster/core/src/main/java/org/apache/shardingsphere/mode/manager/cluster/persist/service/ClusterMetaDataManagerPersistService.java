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

package org.apache.shardingsphere.mode.manager.cluster.persist.service;

import lombok.SneakyThrows;
import org.apache.shardingsphere.infra.config.rule.RuleConfiguration;
import org.apache.shardingsphere.infra.datasource.pool.destroyer.DataSourcePoolDestroyer;
import org.apache.shardingsphere.infra.datasource.pool.props.domain.DataSourcePoolProperties;
import org.apache.shardingsphere.infra.metadata.database.resource.node.StorageNode;
import org.apache.shardingsphere.infra.metadata.database.rule.RuleMetaData;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereSchema;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereTable;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereView;
import org.apache.shardingsphere.infra.metadata.version.MetaDataVersion;
import org.apache.shardingsphere.mode.manager.cluster.persist.coordinator.database.ClusterDatabaseListenerCoordinatorType;
import org.apache.shardingsphere.mode.manager.cluster.persist.coordinator.database.ClusterDatabaseListenerPersistCoordinator;
import org.apache.shardingsphere.mode.metadata.manager.MetaDataContextManager;
import org.apache.shardingsphere.mode.metadata.MetaDataContexts;
import org.apache.shardingsphere.mode.metadata.factory.MetaDataContextsFactory;
import org.apache.shardingsphere.mode.metadata.manager.resource.SwitchingResource;
import org.apache.shardingsphere.mode.metadata.persist.MetaDataPersistFacade;
import org.apache.shardingsphere.mode.metadata.persist.config.database.DataSourceUnitPersistService;
import org.apache.shardingsphere.mode.metadata.persist.metadata.DatabaseMetaDataPersistFacade;
import org.apache.shardingsphere.mode.persist.service.MetaDataManagerPersistService;
import org.apache.shardingsphere.mode.spi.repository.PersistRepository;
import org.apache.shardingsphere.single.config.SingleRuleConfiguration;
import org.apache.shardingsphere.single.rule.SingleRule;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Cluster meta data manager persist service.
 */
public final class ClusterMetaDataManagerPersistService implements MetaDataManagerPersistService {
    
    private final MetaDataContextManager metaDataContextManager;
    
    private final MetaDataPersistFacade metaDataPersistFacade;
    
    private final ClusterDatabaseListenerPersistCoordinator clusterDatabaseListenerPersistCoordinator;
    
    public ClusterMetaDataManagerPersistService(final MetaDataContextManager metaDataContextManager, final PersistRepository repository) {
        this.metaDataContextManager = metaDataContextManager;
        metaDataPersistFacade = metaDataContextManager.getMetaDataPersistFacade();
        clusterDatabaseListenerPersistCoordinator = new ClusterDatabaseListenerPersistCoordinator(repository);
    }
    
    @Override
    public void createDatabase(final String databaseName) {
        metaDataPersistFacade.getDatabaseMetaDataFacade().getDatabase().add(databaseName);
        clusterDatabaseListenerPersistCoordinator.persist(databaseName, ClusterDatabaseListenerCoordinatorType.CREATE);
    }
    
    @Override
    public void dropDatabase(final String databaseName) {
        clusterDatabaseListenerPersistCoordinator.persist(databaseName, ClusterDatabaseListenerCoordinatorType.DROP);
        metaDataPersistFacade.getDatabaseMetaDataFacade().getDatabase().drop(databaseName);
    }
    
    @Override
    public void createSchema(final String databaseName, final String schemaName) {
        metaDataPersistFacade.getDatabaseMetaDataFacade().getSchema().add(databaseName, schemaName);
    }
    
    @Override
    public void alterSchema(final String databaseName, final String schemaName,
                            final Collection<ShardingSphereTable> alteredTables, final Collection<ShardingSphereView> alteredViews,
                            final Collection<String> droppedTables, final Collection<String> droppedViews) {
        DatabaseMetaDataPersistFacade databaseMetaDataFacade = metaDataPersistFacade.getDatabaseMetaDataFacade();
        databaseMetaDataFacade.getTable().persist(databaseName, schemaName, alteredTables);
        databaseMetaDataFacade.getView().persist(databaseName, schemaName, alteredViews);
        droppedTables.forEach(each -> databaseMetaDataFacade.getTable().drop(databaseName, schemaName, each));
        droppedViews.forEach(each -> databaseMetaDataFacade.getView().drop(databaseName, schemaName, each));
    }
    
    @Override
    public void renameSchema(final String databaseName, final String schemaName, final String renameSchemaName) {
        ShardingSphereSchema schema = metaDataContextManager.getMetaDataContexts().getMetaData().getDatabase(databaseName).getSchema(schemaName);
        if (schema.isEmpty()) {
            metaDataPersistFacade.getDatabaseMetaDataFacade().getSchema().add(databaseName, renameSchemaName);
        } else {
            metaDataPersistFacade.getDatabaseMetaDataFacade().getTable().persist(databaseName, renameSchemaName, schema.getAllTables());
            metaDataPersistFacade.getDatabaseMetaDataFacade().getView().persist(databaseName, renameSchemaName, schema.getAllViews());
        }
        metaDataPersistFacade.getDatabaseMetaDataFacade().getSchema().drop(databaseName, schemaName);
    }
    
    @Override
    public void dropSchema(final String databaseName, final Collection<String> schemaNames) {
        schemaNames.forEach(each -> dropSchema(databaseName, each));
    }
    
    private void dropSchema(final String databaseName, final String schemaName) {
        metaDataPersistFacade.getDatabaseMetaDataFacade().getSchema().drop(databaseName, schemaName);
    }
    
    @Override
    public void createTable(final String databaseName, final String schemaName, final ShardingSphereTable table) {
        metaDataPersistFacade.getDatabaseMetaDataFacade().getTable().persist(databaseName, schemaName, Collections.singleton(table));
    }
    
    @Override
    public void dropTable(final String databaseName, final String schemaName, final String tableName) {
        metaDataPersistFacade.getDatabaseMetaDataFacade().getTable().drop(databaseName, schemaName, tableName);
    }
    
    @Override
    public void registerStorageUnits(final String databaseName, final Map<String, DataSourcePoolProperties> toBeRegisteredProps) throws SQLException {
        MetaDataContexts originalMetaDataContexts = new MetaDataContexts(metaDataContextManager.getMetaDataContexts().getMetaData(), metaDataContextManager.getMetaDataContexts().getStatistics());
        Map<StorageNode, DataSource> newDataSources = new HashMap<>(toBeRegisteredProps.size());
        try {
            SwitchingResource switchingResource = metaDataContextManager.getResourceSwitchManager()
                    .switchByRegisterStorageUnit(originalMetaDataContexts.getMetaData().getDatabase(databaseName).getResourceMetaData(), toBeRegisteredProps);
            newDataSources.putAll(switchingResource.getNewDataSources());
            MetaDataContexts reloadMetaDataContexts = new MetaDataContextsFactory(metaDataPersistFacade, metaDataContextManager.getComputeNodeInstanceContext()).createBySwitchResource(
                    databaseName, false, switchingResource, originalMetaDataContexts);
            metaDataPersistFacade.getDataSourceUnitService().persist(databaseName, toBeRegisteredProps);
            afterStorageUnitsAltered(databaseName, originalMetaDataContexts, reloadMetaDataContexts);
            reloadMetaDataContexts.getMetaData().close();
        } finally {
            closeNewDataSources(newDataSources);
        }
    }
    
    @Override
    public void alterStorageUnits(final String databaseName, final Map<String, DataSourcePoolProperties> toBeUpdatedProps) throws SQLException {
        MetaDataContexts originalMetaDataContexts = new MetaDataContexts(metaDataContextManager.getMetaDataContexts().getMetaData(), metaDataContextManager.getMetaDataContexts().getStatistics());
        Map<StorageNode, DataSource> newDataSources = new HashMap<>(toBeUpdatedProps.size());
        try {
            SwitchingResource switchingResource = metaDataContextManager.getResourceSwitchManager()
                    .switchByAlterStorageUnit(originalMetaDataContexts.getMetaData().getDatabase(databaseName).getResourceMetaData(), toBeUpdatedProps);
            newDataSources.putAll(switchingResource.getNewDataSources());
            MetaDataContexts reloadMetaDataContexts = new MetaDataContextsFactory(metaDataPersistFacade, metaDataContextManager.getComputeNodeInstanceContext()).createBySwitchResource(
                    databaseName, false, switchingResource, originalMetaDataContexts);
            DataSourceUnitPersistService dataSourceService = metaDataPersistFacade.getDataSourceUnitService();
            metaDataPersistFacade.getMetaDataVersionService()
                    .switchActiveVersion(dataSourceService.persist(databaseName, toBeUpdatedProps));
            afterStorageUnitsAltered(databaseName, originalMetaDataContexts, reloadMetaDataContexts);
            reloadMetaDataContexts.getMetaData().close();
        } finally {
            closeNewDataSources(newDataSources);
        }
    }
    
    @Override
    public void unregisterStorageUnits(final String databaseName, final Collection<String> toBeDroppedStorageUnitNames) throws SQLException {
        for (String each : getToBeDroppedResourceNames(databaseName, toBeDroppedStorageUnitNames)) {
            MetaDataContexts originalMetaDataContexts = new MetaDataContexts(metaDataContextManager.getMetaDataContexts().getMetaData(), metaDataContextManager.getMetaDataContexts().getStatistics());
            SwitchingResource switchingResource = metaDataContextManager.getResourceSwitchManager()
                    .createByUnregisterStorageUnit(originalMetaDataContexts.getMetaData().getDatabase(databaseName).getResourceMetaData(), Collections.singletonList(each));
            MetaDataContexts reloadMetaDataContexts = new MetaDataContextsFactory(metaDataPersistFacade, metaDataContextManager.getComputeNodeInstanceContext()).createBySwitchResource(
                    databaseName, false, switchingResource, originalMetaDataContexts);
            metaDataPersistFacade.getDataSourceUnitService().delete(databaseName, each);
            afterStorageUnitsDropped(databaseName, originalMetaDataContexts, reloadMetaDataContexts);
            reloadMetaDataContexts.getMetaData().close();
        }
    }
    
    private void closeNewDataSources(final Map<StorageNode, DataSource> newDataSources) {
        for (Map.Entry<StorageNode, DataSource> entry : newDataSources.entrySet()) {
            if (null != entry.getValue()) {
                new DataSourcePoolDestroyer(entry.getValue()).asyncDestroy();
            }
        }
    }
    
    private Collection<String> getToBeDroppedResourceNames(final String databaseName, final Collection<String> toBeDroppedResourceNames) {
        Map<String, DataSourcePoolProperties> propsMap = metaDataPersistFacade.getDataSourceUnitService().load(databaseName);
        return toBeDroppedResourceNames.stream().filter(propsMap::containsKey).collect(Collectors.toList());
    }
    
    private void afterStorageUnitsAltered(final String databaseName, final MetaDataContexts originalMetaDataContexts, final MetaDataContexts reloadMetaDataContexts) {
        Optional.ofNullable(reloadMetaDataContexts.getStatistics().getDatabaseStatistics(databaseName))
                .ifPresent(optional -> optional.getSchemaStatisticsMap().forEach((schemaName, schemaStatistics) -> metaDataPersistFacade.getStatisticsService()
                        .persist(originalMetaDataContexts.getMetaData().getDatabase(databaseName), schemaName, schemaStatistics)));
        metaDataPersistFacade.persistReloadDatabaseByAlter(databaseName, reloadMetaDataContexts.getMetaData().getDatabase(databaseName),
                originalMetaDataContexts.getMetaData().getDatabase(databaseName));
    }
    
    private void afterStorageUnitsDropped(final String databaseName, final MetaDataContexts originalMetaDataContexts, final MetaDataContexts reloadMetaDataContexts) {
        reloadMetaDataContexts.getMetaData().getDatabase(databaseName).getAllSchemas().forEach(each -> metaDataPersistFacade.getDatabaseMetaDataFacade()
                .getSchema().alterByRuleDropped(reloadMetaDataContexts.getMetaData().getDatabase(databaseName).getName(), each));
        Optional.ofNullable(reloadMetaDataContexts.getStatistics().getDatabaseStatistics(databaseName))
                .ifPresent(optional -> optional.getSchemaStatisticsMap().forEach((schemaName, schemaStatistics) -> metaDataPersistFacade.getStatisticsService()
                        .persist(originalMetaDataContexts.getMetaData().getDatabase(databaseName), schemaName, schemaStatistics)));
        metaDataPersistFacade.persistReloadDatabaseByDrop(databaseName, reloadMetaDataContexts.getMetaData().getDatabase(databaseName),
                originalMetaDataContexts.getMetaData().getDatabase(databaseName));
    }
    
    @Override
    public void alterSingleRuleConfiguration(final String databaseName, final RuleMetaData ruleMetaData) {
        SingleRuleConfiguration singleRuleConfig = ruleMetaData.getSingleRule(SingleRule.class).getConfiguration();
        Collection<MetaDataVersion> metaDataVersions = metaDataPersistFacade.getDatabaseRuleService().persist(databaseName, Collections.singleton(singleRuleConfig));
        metaDataPersistFacade.getMetaDataVersionService().switchActiveVersion(metaDataVersions);
    }
    
    @Override
    public void alterRuleConfiguration(final String databaseName, final RuleConfiguration toBeAlteredRuleConfig) {
        if (null == toBeAlteredRuleConfig) {
            return;
        }
        MetaDataContexts originalMetaDataContexts = new MetaDataContexts(metaDataContextManager.getMetaDataContexts().getMetaData(), metaDataContextManager.getMetaDataContexts().getStatistics());
        Collection<MetaDataVersion> metaDataVersions = metaDataPersistFacade.getDatabaseRuleService().persist(databaseName, Collections.singleton(toBeAlteredRuleConfig));
        metaDataPersistFacade.getMetaDataVersionService().switchActiveVersion(metaDataVersions);
        afterRuleConfigurationAltered(databaseName, originalMetaDataContexts);
    }
    
    @SneakyThrows(InterruptedException.class)
    private void afterRuleConfigurationAltered(final String databaseName, final MetaDataContexts originalMetaDataContexts) {
        Thread.sleep(3000L);
        MetaDataContexts reloadMetaDataContexts = metaDataContextManager.getMetaDataContexts();
        metaDataPersistFacade.persistReloadDatabaseByAlter(
                databaseName, reloadMetaDataContexts.getMetaData().getDatabase(databaseName), originalMetaDataContexts.getMetaData().getDatabase(databaseName));
    }
    
    @Override
    public void removeRuleConfigurationItem(final String databaseName, final RuleConfiguration toBeRemovedRuleConfig) {
        if (null != toBeRemovedRuleConfig) {
            metaDataPersistFacade.getDatabaseRuleService().delete(databaseName, Collections.singleton(toBeRemovedRuleConfig));
        }
    }
    
    @Override
    public void removeRuleConfiguration(final String databaseName, final String ruleName) {
        MetaDataContexts originalMetaDataContexts = new MetaDataContexts(metaDataContextManager.getMetaDataContexts().getMetaData(), metaDataContextManager.getMetaDataContexts().getStatistics());
        metaDataPersistFacade.getDatabaseRuleService().delete(databaseName, ruleName);
        afterRuleConfigurationDropped(databaseName, originalMetaDataContexts);
    }
    
    @SneakyThrows(InterruptedException.class)
    private void afterRuleConfigurationDropped(final String databaseName, final MetaDataContexts originalMetaDataContexts) {
        Thread.sleep(3000L);
        MetaDataContexts reloadMetaDataContexts = metaDataContextManager.getMetaDataContexts();
        metaDataPersistFacade.persistReloadDatabaseByDrop(
                databaseName, reloadMetaDataContexts.getMetaData().getDatabase(databaseName), originalMetaDataContexts.getMetaData().getDatabase(databaseName));
    }
    
    @Override
    public void alterGlobalRuleConfiguration(final RuleConfiguration toBeAlteredRuleConfig) {
        metaDataPersistFacade.getGlobalRuleService().persist(Collections.singleton(toBeAlteredRuleConfig));
    }
    
    @Override
    public void alterProperties(final Properties props) {
        metaDataPersistFacade.getPropsService().persist(props);
    }
}
