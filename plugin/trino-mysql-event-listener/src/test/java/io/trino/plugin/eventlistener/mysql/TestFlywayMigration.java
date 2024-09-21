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
package io.trino.plugin.eventlistener.mysql;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.Isolated;
import org.testcontainers.containers.MySQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
@Isolated
public class TestFlywayMigration
{
    private final MySQLContainer<?> container = startContainer();
    private final Jdbi jdbi = Jdbi.create(container.getJdbcUrl(), container.getUsername(), container.getPassword());

    private MySQLContainer<?> startContainer()
    {
        MySQLContainer<?> container = new MySQLContainer<>("mysql:8.0.36");
        container.start();
        return container;
    }

    @AfterAll
    public final void close()
    {
        container.close();
    }

    @Test
    public void testMigrationWithEmptyDatabase()
    {
        MysqlEventListenerConfig config = new MysqlEventListenerConfig()
                .setUrl(container.getJdbcUrl())
                .setUser(container.getUsername())
                .setPassword(container.getPassword());
        FlywayMigration.migrate(config);
        verifySchema(0);
        dropTrinoQueriesTable();
    }

    @Test
    public void testMigrationWithNonEmptyDatabase()
    {
        String t1Create = "CREATE TABLE t1 (id INT)";
        String t2Create = "CREATE TABLE t2 (id INT)";
        Handle jdbiHandle = jdbi.open();
        jdbiHandle.execute(t1Create);
        jdbiHandle.execute(t2Create);
        MysqlEventListenerConfig config = new MysqlEventListenerConfig()
                .setUrl(container.getJdbcUrl())
                .setUser(container.getUsername())
                .setPassword(container.getPassword());
        FlywayMigration.migrate(config);
        verifySchema(0);
        String t1Drop = "DROP TABLE t1";
        String t2Drop = "DROP TABLE t2";
        jdbiHandle.execute(t1Drop);
        jdbiHandle.execute(t2Drop);
        jdbiHandle.close();
        dropTrinoQueriesTable();
    }

    @Test
    public void testMigrationWithExistingTrinoQueriesTable()
    {
        Handle jdbiHandle = jdbi.open();
        jdbiHandle.execute(createTrinoQueriesSql());
        // add a row before migration
        String insertionSql = "INSERT INTO trino_queries (" +
                "  query_id," +
                "  transaction_id," +
                "  query," +
                "  update_type," +
                "  prepared_query," +
                "  query_state," +
                "  plan," +
                "  stage_info_json," +
                "  user," +
                "  principal," +
                "  trace_token," +
                "  remote_client_address," +
                "  user_agent," +
                "  client_info," +
                "  client_tags_json," +
                "  source," +
                "  catalog," +
                "  `schema`," +
                "  resource_group_id," +
                "  session_properties_json," +
                "  server_address," +
                "  server_version," +
                "  environment," +
                "  query_type," +
                "  inputs_json," +
                "  output_json," +
                "  error_code," +
                "  error_type," +
                "  failure_type," +
                "  failure_message," +
                "  failure_task," +
                "  failure_host," +
                "  failures_json," +
                "  warnings_json," +
                "  cpu_time_millis," +
                "  failed_cpu_time_millis," +
                "  wall_time_millis," +
                "  queued_time_millis," +
                "  scheduled_time_millis," +
                "  failed_scheduled_time_millis," +
                "  waiting_time_millis," +
                "  analysis_time_millis," +
                "  planning_time_millis," +
                "  planning_cpu_time_millis," +
                "  starting_time_millis," +
                "  execution_time_millis," +
                "  input_blocked_time_millis," +
                "  failed_input_blocked_time_millis," +
                "  output_blocked_time_millis," +
                "  failed_output_blocked_time_millis," +
                "  physical_input_read_time_millis," +
                "  peak_memory_bytes," +
                "  peak_task_memory_bytes," +
                "  physical_input_bytes," +
                "  physical_input_rows," +
                "  internal_network_bytes," +
                "  internal_network_rows," +
                "  total_bytes," +
                "  total_rows," +
                "  output_bytes," +
                "  output_rows," +
                "  written_bytes," +
                "  written_rows," +
                "  cumulative_memory," +
                "  failed_cumulative_memory," +
                "  completed_splits," +
                "  retry_policy," +
                "  operator_summaries_json" +
                ") VALUES(" +
                "'query_id'," +
                "'transaction_id'," +
                "'query'," +
                "'update_type'," +
                "'prepared_query'," +
                "'query_state'," +
                "'plan'," +
                "'stage_info_json'," +
                "'user'," +
                "'principal'," +
                "'trace_token'," +
                "'remote_client_address'," +
                "'user_agent'," +
                "'client_info'," +
                "'client_tags_json'," +
                "'source'," +
                "'catalog'," +
                "'schema'," +
                "'resource_group_id'," +
                "'session_properties_json'," +
                "'server_address'," +
                "'server_version'," +
                "'environment'," +
                "'query_type'," +
                "'inputs_json'," +
                "'output_json'," +
                "'error_code'," +
                "'error_type'," +
                "'failure_type'," +
                "'failure_message'," +
                "'failure_task'," +
                "'failure_host'," +
                "'failures_json'," +
                "'warnings_json'," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "0," +
                "'retry_policy'," +
                "'operator_summaries_json'" +
                ")";
        jdbi.withHandle(handle ->
                handle.execute(insertionSql));
        MysqlEventListenerConfig config = new MysqlEventListenerConfig()
                .setUrl(container.getJdbcUrl())
                .setUser(container.getUsername())
                .setPassword(container.getPassword());
        FlywayMigration.migrate(config);
        verifySchema(1);
        String dropTable = "DROP TABLE trino_queries";
        jdbiHandle.execute(dropTable);
        jdbiHandle.close();
        dropTrinoQueriesTable();
    }

    private String createTrinoQueriesSql()
    {
        return "CREATE TABLE IF NOT EXISTS trino_queries (\n" +
                "  query_id VARCHAR(255) NOT NULL PRIMARY KEY,\n" +
                "  transaction_id VARCHAR(255) NULL,\n" +
                "  query MEDIUMTEXT NOT NULL,\n" +
                "  update_type VARCHAR(255) NULL,\n" +
                "  prepared_query MEDIUMTEXT NULL,\n" +
                "  query_state VARCHAR(255) NOT NULL,\n" +
                "  plan MEDIUMTEXT NULL,\n" +
                "  stage_info_json MEDIUMTEXT NULL,\n" +
                "  user VARCHAR(255) NOT NULL,\n" +
                "  principal VARCHAR(255) NULL,\n" +
                "  trace_token VARCHAR(255) NULL,\n" +
                "  remote_client_address VARCHAR(255) NULL,\n" +
                "  user_agent VARCHAR(255) NULL,\n" +
                "  client_info VARCHAR(255) NULL,\n" +
                "  client_tags_json MEDIUMTEXT NOT NULL,\n" +
                "  source VARCHAR(255) NULL,\n" +
                "  catalog VARCHAR(255) NULL,\n" +
                "  `schema` VARCHAR(255) NULL,\n" +
                "  resource_group_id VARCHAR(255) NULL,\n" +
                "  session_properties_json MEDIUMTEXT NOT NULL,\n" +
                "  server_address VARCHAR(255) NOT NULL,\n" +
                "  server_version VARCHAR(255) NOT NULL,\n" +
                "  environment VARCHAR(255) NOT NULL,\n" +
                "  query_type VARCHAR(255) NULL,\n" +
                "  inputs_json MEDIUMTEXT NOT NULL,\n" +
                "  output_json MEDIUMTEXT NULL,\n" +
                "  error_code VARCHAR(255) NULL,\n" +
                "  error_type VARCHAR(255) NULL,\n" +
                "  failure_type VARCHAR(255) NULL,\n" +
                "  failure_message MEDIUMTEXT NULL,\n" +
                "  failure_task VARCHAR(255) NULL,\n" +
                "  failure_host VARCHAR(255) NULL,\n" +
                "  failures_json MEDIUMTEXT NULL,\n" +
                "  warnings_json MEDIUMTEXT NOT NULL,\n" +
                "  cpu_time_millis BIGINT NOT NULL,\n" +
                "  failed_cpu_time_millis BIGINT NOT NULL,\n" +
                "  wall_time_millis BIGINT NOT NULL,\n" +
                "  queued_time_millis BIGINT NOT NULL,\n" +
                "  scheduled_time_millis BIGINT NOT NULL,\n" +
                "  failed_scheduled_time_millis BIGINT NOT NULL,\n" +
                "  waiting_time_millis BIGINT NOT NULL,\n" +
                "  analysis_time_millis BIGINT NOT NULL,\n" +
                "  planning_time_millis BIGINT NOT NULL,\n" +
                "  planning_cpu_time_millis BIGINT NOT NULL,\n" +
                "  starting_time_millis BIGINT NOT NULL,\n" +
                "  execution_time_millis BIGINT NOT NULL,\n" +
                "  input_blocked_time_millis BIGINT NOT NULL,\n" +
                "  failed_input_blocked_time_millis BIGINT NOT NULL,\n" +
                "  output_blocked_time_millis BIGINT NOT NULL,\n" +
                "  failed_output_blocked_time_millis BIGINT NOT NULL,\n" +
                "  physical_input_read_time_millis BIGINT NOT NULL,\n" +
                "  peak_memory_bytes BIGINT NOT NULL,\n" +
                "  peak_task_memory_bytes BIGINT NOT NULL,\n" +
                "  physical_input_bytes BIGINT NOT NULL,\n" +
                "  physical_input_rows BIGINT NOT NULL,\n" +
                "  internal_network_bytes BIGINT NOT NULL,\n" +
                "  internal_network_rows BIGINT NOT NULL,\n" +
                "  total_bytes BIGINT NOT NULL,\n" +
                "  total_rows BIGINT NOT NULL,\n" +
                "  output_bytes BIGINT NOT NULL,\n" +
                "  output_rows BIGINT NOT NULL,\n" +
                "  written_bytes BIGINT NOT NULL,\n" +
                "  written_rows BIGINT NOT NULL,\n" +
                "  cumulative_memory DOUBLE NOT NULL,\n" +
                "  failed_cumulative_memory DOUBLE NOT NULL,\n" +
                "  completed_splits BIGINT NOT NULL,\n" +
                "  retry_policy VARCHAR(255) NOT NULL,\n" +
                "  operator_summaries_json MEDIUMTEXT NOT NULL\n" +
                ")\n" +
                "DEFAULT CHARACTER SET utf8";
    }

    private void verifySchema(long expectedRowCount)
    {
        verifyResultSetCount("SELECT query_id FROM trino_queries", expectedRowCount);
    }

    private void verifyResultSetCount(String sql, long expectedCount)
    {
        List<String> results = jdbi.withHandle(handle ->
                handle.createQuery(sql).mapTo(String.class).list());
        assertThat(results.size()).isEqualTo(expectedCount);
    }

    private void dropTrinoQueriesTable()
    {
        String dropTrinoQueriesTable = "DROP TABLE IF EXISTS trino_queries";
        String dropFlywayHistoryTable = "DROP TABLE IF EXISTS flyway_schema_history";
        Handle jdbiHandle = jdbi.open();
        jdbiHandle.execute(dropTrinoQueriesTable);
        jdbiHandle.execute(dropFlywayHistoryTable);
        jdbiHandle.close();
    }
}
