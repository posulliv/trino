/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.resourcegroups.db;

import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface H2ResourceGroupsDao
        extends ResourceGroupsDao
{
    @SqlUpdate("CREATE TABLE IF NOT EXISTS resource_groups_global_properties (\n" +
            "  name VARCHAR(128) NOT NULL PRIMARY KEY,\n" +
            "  value VARCHAR(512) NULL,\n" +
            "  CHECK (name in ('cpu_quota_period'))\n" +
            ")")
    void createResourceGroupsGlobalPropertiesTable();

    @SqlUpdate("CREATE TABLE IF NOT EXISTS resource_groups (\n" +
            "  resource_group_id BIGINT NOT NULL AUTO_INCREMENT,\n" +
            "  name VARCHAR(250) NOT NULL,\n" +
            "  soft_memory_limit VARCHAR(128) NOT NULL,\n" +
            "  max_queued INT NOT NULL,\n" +
            "  soft_concurrency_limit INT NULL,\n" +
            "  hard_concurrency_limit INT NOT NULL,\n" +
            "  scheduling_policy VARCHAR(128) NULL,\n" +
            "  scheduling_weight INT NULL,\n" +
            "  jmx_export BOOLEAN NULL,\n" +
            "  soft_cpu_limit VARCHAR(128) NULL,\n" +
            "  hard_cpu_limit VARCHAR(128) NULL,\n" +
            "  parent BIGINT NULL,\n" +
            "  environment VARCHAR(128) NULL,\n" +
            "  PRIMARY KEY (resource_group_id),\n" +
            "  FOREIGN KEY (parent) REFERENCES resource_groups (resource_group_id)\n" +
            ")")
    void createResourceGroupsTable();

    @SqlUpdate("CREATE TABLE IF NOT EXISTS selectors (\n" +
            "  resource_group_id BIGINT NOT NULL,\n" +
            "  priority BIGINT NOT NULL,\n" +
            "  user_regex VARCHAR(512),\n" +
            "  source_regex VARCHAR(512),\n" +
            "  query_type VARCHAR(512),\n" +
            "  client_tags VARCHAR(512),\n" +
            "  selector_resource_estimate VARCHAR(1024),\n" +
            "  FOREIGN KEY (resource_group_id) REFERENCES resource_groups (resource_group_id)\n" +
            ")")
    void createSelectorsTable();

    @SqlUpdate("CREATE TABLE IF NOT EXISTS exact_match_source_selectors(\n" +
            "  environment VARCHAR(128),\n" +
            "  source VARCHAR(512) NOT NULL,\n" +
            "  query_type VARCHAR(512),\n" +
            "  update_time DATETIME NOT NULL,\n" +
            "  resource_group_id VARCHAR(256) NOT NULL,\n" +
            "  PRIMARY KEY (environment, source, query_type),\n" +
            "  UNIQUE (source, environment, query_type, resource_group_id)\n" +
            ")")
    void createExactMatchSelectorsTable();
}
