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

package org.apache.shardingsphere.mode.metadata.persist.version;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.infra.metadata.version.MetaDataVersion;
import org.apache.shardingsphere.mode.node.path.metadata.DatabaseMetaDataNodePath;
import org.apache.shardingsphere.mode.spi.repository.PersistRepository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Meta data version persist service.
 */
@RequiredArgsConstructor
@Slf4j
public final class MetaDataVersionPersistService {
    
    private final PersistRepository repository;
    
    /**
     * Switch active version.
     *
     * @param metaDataVersions meta data versions
     */
    public void switchActiveVersion(final Collection<MetaDataVersion> metaDataVersions) {
        for (MetaDataVersion each : metaDataVersions) {
            if (each.getNextActiveVersion().equals(each.getCurrentActiveVersion())) {
                continue;
            }
            repository.persist(each.getActiveVersionNodePath(), String.valueOf(each.getNextActiveVersion()));
            getVersions(each.getVersionsPath()).stream().filter(version -> version < each.getNextActiveVersion()).forEach(version -> repository.delete(each.getVersionsNodePath(version)));
        }
    }
    
    /**
     * Get version path by active version.
     *
     * @param path path
     * @param activeVersion active version
     * @return version path
     */
    public String getVersionPathByActiveVersion(final String path, final int activeVersion) {
        return repository.query(DatabaseMetaDataNodePath.getVersionPath(path, activeVersion));
    }
    
    /**
     * Get versions.
     *
     * @param path path
     * @return versions
     */
    public List<Integer> getVersions(final String path) {
        List<Integer> result = repository.getChildrenKeys(path).stream().map(Integer::parseInt).collect(Collectors.toList());
        if (result.size() > 2) {
            log.warn("There are multiple versions of: {}, please check the configuration.", path);
            result.sort(Collections.reverseOrder());
        }
        return result;
    }
}
