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

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.testng.Assert.assertEquals;

@Test(singleThreaded = true)
public class TestDbResourceGroupsFlywayMigration
{
    private TestingMysqlServer mysqlServer;

    @BeforeClass
    public final void setup()
    {
        mysqlServer = new TestingMysqlServer()
                .withDatabaseName("resource_groups")
                .withUsername("test")
                .withPassword("test");
        mysqlServer.start();
        createOldSchema();
    }

    @AfterClass(alwaysRun = true)
    public final void close()
    {
        mysqlServer.close();
    }

    @Test
    public void testSchemaMigration()
            throws SQLException
    {
        // add a row to one of the existing tables before migration
        mysqlServer.executeSql("INSERT INTO resource_groups_global_properties VALUES ('a_name', 'a_value')");
        DbResourceGroupConfig config = new DbResourceGroupConfig()
                .setConfigDbUrl(mysqlServer.getJdbcUrl())
                .setConfigDbUser(mysqlServer.getUsername())
                .setConfigDbPassword(mysqlServer.getPassword());
        new FlywayMigration(config);
        // test existing 3 tables are still present and that
        // exact_match_source_selectors was created successfully
        try (Connection connection = DriverManager.getConnection(mysqlServer.getJdbcUrl(), mysqlServer.getUsername(), mysqlServer.getPassword());
                Statement stmt = connection.createStatement()) {
            ResultSet globalProperties = stmt.executeQuery("SELECT count(*) AS props FROM resource_groups_global_properties");
            globalProperties.next();
            assertEquals(globalProperties.getLong("props"), 1);
            ResultSet resourceGroups = stmt.executeQuery("SELECT count(*) AS props FROM resource_groups");
            resourceGroups.next();
            assertEquals(resourceGroups.getLong("props"), 0);
            ResultSet selectors = stmt.executeQuery("SELECT count(*) AS props FROM selectors");
            selectors.next();
            assertEquals(selectors.getLong("props"), 0);
            ResultSet exactMatchSourceSelectors = stmt.executeQuery("SELECT count(*) AS props FROM exact_match_source_selectors");
            exactMatchSourceSelectors.next();
            assertEquals(exactMatchSourceSelectors.getLong("props"), 0);
        }
    }

    private void createOldSchema()
    {
        String propertiesTable = "CREATE TABLE resource_groups_global_properties (\n" +
                "    name VARCHAR(128) NOT NULL PRIMARY KEY,\n" +
                "    value VARCHAR(512) NULL,\n" +
                "    CHECK (name in ('cpu_quota_period'))\n" +
                ");";
        String resourceGroupsTable = "CREATE TABLE resource_groups (\n" +
                "    resource_group_id BIGINT NOT NULL AUTO_INCREMENT,\n" +
                "    name VARCHAR(250) NOT NULL,\n" +
                "    soft_memory_limit VARCHAR(128) NOT NULL,\n" +
                "    max_queued INT NOT NULL,\n" +
                "    soft_concurrency_limit INT NULL,\n" +
                "    hard_concurrency_limit INT NOT NULL,\n" +
                "    scheduling_policy VARCHAR(128) NULL,\n" +
                "    scheduling_weight INT NULL,\n" +
                "    jmx_export BOOLEAN NULL,\n" +
                "    soft_cpu_limit VARCHAR(128) NULL,\n" +
                "    hard_cpu_limit VARCHAR(128) NULL,\n" +
                "    parent BIGINT NULL,\n" +
                "    environment VARCHAR(128) NULL,\n" +
                "    PRIMARY KEY (resource_group_id),\n" +
                "    FOREIGN KEY (parent) REFERENCES resource_groups (resource_group_id) ON DELETE CASCADE\n" +
                ");";
        String selectorsTable = "CREATE TABLE selectors (\n" +
                "     resource_group_id BIGINT NOT NULL,\n" +
                "     priority BIGINT NOT NULL,\n" +
                "     user_regex VARCHAR(512),\n" +
                "     source_regex VARCHAR(512),\n" +
                "     query_type VARCHAR(512),\n" +
                "     client_tags VARCHAR(512),\n" +
                "     selector_resource_estimate VARCHAR(1024),\n" +
                "     FOREIGN KEY (resource_group_id) REFERENCES resource_groups (resource_group_id) ON DELETE CASCADE\n" +
                ");";
        mysqlServer.executeSql(propertiesTable);
        mysqlServer.executeSql(resourceGroupsTable);
        mysqlServer.executeSql(selectorsTable);
    }
}
