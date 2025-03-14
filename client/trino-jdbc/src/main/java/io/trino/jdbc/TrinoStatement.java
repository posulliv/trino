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
package io.trino.jdbc;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import io.trino.client.ClientException;
import io.trino.client.QueryStatusInfo;
import io.trino.client.StatementClient;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.trino.jdbc.ResultUtils.resultsException;
import static java.lang.Math.toIntExact;
import static java.util.Objects.requireNonNull;

public class TrinoStatement
        implements Statement
{
    private final AtomicLong maxRows = new AtomicLong();
    private final AtomicInteger queryTimeoutSeconds = new AtomicInteger();
    private final AtomicInteger fetchSize = new AtomicInteger();
    private final AtomicBoolean closeOnCompletion = new AtomicBoolean();
    private final AtomicReference<TrinoConnection> connection;
    private final Consumer<TrinoStatement> onClose;
    private final AtomicReference<StatementClient> executingClient = new AtomicReference<>();
    private final AtomicReference<TrinoResultSet> currentResult = new AtomicReference<>();
    private final AtomicReference<WarningsManager> currentWarningsManager = new AtomicReference<>();
    private final AtomicLong currentUpdateCount = new AtomicLong(-1);
    private final AtomicReference<String> currentUpdateType = new AtomicReference<>();
    private final AtomicReference<Consumer<QueryStats>> progressCallback = new AtomicReference<>();

    TrinoStatement(TrinoConnection connection, Consumer<TrinoStatement> onClose)
    {
        this.connection = new AtomicReference<>(requireNonNull(connection, "connection is null"));
        this.onClose = requireNonNull(onClose, "onClose is null");
    }

    public void setProgressMonitor(Consumer<QueryStats> progressMonitor)
    {
        progressCallback.set(requireNonNull(progressMonitor, "progressMonitor is null"));
    }

    private void progressCallback(QueryStats stats)
    {
        Consumer<QueryStats> consumer = progressCallback.get();
        if (consumer != null) {
            consumer.accept(stats);
        }
    }

    public void clearProgressMonitor()
    {
        progressCallback.set(null);
    }

    @Override
    public ResultSet executeQuery(String sql)
            throws SQLException
    {
        if (!execute(sql)) {
            throw new SQLException("SQL statement is not a query: " + sql);
        }
        return currentResult.get();
    }

    @Override
    public void close()
            throws SQLException
    {
        TrinoConnection connection = this.connection.getAndSet(null);
        if (connection == null) {
            return;
        }

        onClose.accept(this);

        StatementClient client = executingClient.get();
        if (client != null) {
            client.close();
        }

        closeResultSet();
    }

    @Override
    public int getMaxFieldSize()
            throws SQLException
    {
        checkOpen();
        return 0;
    }

    @Override
    public void setMaxFieldSize(int max)
            throws SQLException
    {
        checkOpen();
        if (max < 0) {
            throw new SQLException("Max field size must be positive");
        }
        // ignore: full values are always returned
    }

    @Override
    public int getMaxRows()
            throws SQLException
    {
        long result = getLargeMaxRows();
        if (result > Integer.MAX_VALUE) {
            throw new SQLException("Max rows exceeds limit of 2147483647");
        }
        return toIntExact(result);
    }

    @Override
    public long getLargeMaxRows()
            throws SQLException
    {
        checkOpen();
        return maxRows.get();
    }

    @Override
    public void setMaxRows(int max)
            throws SQLException
    {
        setLargeMaxRows(max);
    }

    @Override
    public void setLargeMaxRows(long max)
            throws SQLException
    {
        checkOpen();
        if (max < 0) {
            throw new SQLException("Max rows must be positive");
        }
        maxRows.set(max);
    }

    @Override
    public void setEscapeProcessing(boolean enable)
            throws SQLException
    {
        checkOpen();
        // Escape processing is not implemented. JDBC mandates that escapes processing is enabled by default,
        // so throwing here doesn't make sense.
    }

    @Override
    public int getQueryTimeout()
            throws SQLException
    {
        checkOpen();
        return queryTimeoutSeconds.get();
    }

    @Override
    public void setQueryTimeout(int seconds)
            throws SQLException
    {
        checkOpen();
        if (seconds < 0) {
            throw new SQLException("Query timeout seconds must be positive");
        }
        queryTimeoutSeconds.set(seconds);
    }

    @Override
    public void cancel()
            throws SQLException
    {
        checkOpen();

        StatementClient client = executingClient.get();
        if (client != null) {
            client.close();
        }

        closeResultSet();
    }

    @Override
    public SQLWarning getWarnings()
            throws SQLException
    {
        checkOpen();
        WarningsManager manager = currentWarningsManager.get();
        if (manager == null) {
            return null;
        }
        return manager.getWarnings();
    }

    @Override
    public void clearWarnings()
            throws SQLException
    {
        checkOpen();
        WarningsManager manager = currentWarningsManager.get();
        if (manager != null) {
            manager.clearWarnings();
        }
    }

    @Override
    public void setCursorName(String name)
            throws SQLException
    {
        checkOpen();
        // ignore: positioned modifications not supported
    }

    private Map<String, String> getStatementSessionProperties()
    {
        ImmutableMap.Builder<String, String> sessionProperties = ImmutableMap.builder();
        if (queryTimeoutSeconds.get() > 0) {
            sessionProperties.put("query_max_run_time", queryTimeoutSeconds.get() + "s");
        }
        return sessionProperties.buildOrThrow();
    }

    @Override
    public boolean execute(String sql)
            throws SQLException
    {
        if (connection().shouldStartTransaction()) {
            internalExecute(connection().getStartTransactionSql());
        }
        return internalExecute(sql);
    }

    final boolean internalExecute(String sql)
            throws SQLException
    {
        clearCurrentResults();
        checkOpen();

        StatementClient client = null;
        TrinoResultSet resultSet = null;
        try {
            client = connection().startQuery(sql, getStatementSessionProperties());
            if (client.isFinished()) {
                QueryStatusInfo finalStatusInfo = client.finalStatusInfo();
                if (finalStatusInfo.getError() != null) {
                    throw resultsException(finalStatusInfo);
                }
            }
            executingClient.set(client);
            WarningsManager warningsManager = new WarningsManager();
            currentWarningsManager.set(warningsManager);
            resultSet = TrinoResultSet.create(this, client, maxRows.get(), this::progressCallback, warningsManager);

            // check if this is a query
            if (client.currentStatusInfo().getUpdateType() == null) {
                currentResult.set(resultSet);
                return true;
            }

            // this is an update, not a query
            while (resultSet.next()) {
                // ignore rows
            }

            connection().updateSession(client);

            currentUpdateCount.set(client.finalStatusInfo().getUpdateCount().orElse(0));
            currentUpdateType.set(client.finalStatusInfo().getUpdateType());
            warningsManager.addWarnings(client.finalStatusInfo().getWarnings());
            return false;
        }
        catch (ClientException e) {
            throw new SQLException(e.getMessage(), e);
        }
        catch (RuntimeException e) {
            throw new SQLException("Error executing query: " + e.getMessage(), e);
        }
        finally {
            executingClient.set(null);
            if (currentResult.get() == null) {
                if (resultSet != null) {
                    resultSet.close();
                }
                if (client != null) {
                    client.close();
                }
            }
        }
    }

    private void clearCurrentResults()
    {
        currentResult.set(null);
        currentUpdateCount.set(-1);
        currentUpdateType.set(null);
        currentWarningsManager.set(null);
    }

    @Override
    public ResultSet getResultSet()
            throws SQLException
    {
        checkOpen();
        return currentResult.get();
    }

    @Override
    public int getUpdateCount()
            throws SQLException
    {
        return Ints.saturatedCast(getLargeUpdateCount());
    }

    @Override
    public long getLargeUpdateCount()
            throws SQLException
    {
        checkOpen();
        return currentUpdateCount.get();
    }

    @Override
    public boolean getMoreResults()
            throws SQLException
    {
        return getMoreResults(CLOSE_CURRENT_RESULT);
    }

    @Override
    public void setFetchDirection(int direction)
            throws SQLException
    {
        checkOpen();
        if (!validFetchDirection(direction)) {
            throw new SQLException("Invalid fetch direction");
        }
        // ignore: fetch direction is always forward
    }

    @Override
    public int getFetchDirection()
            throws SQLException
    {
        checkOpen();
        return ResultSet.FETCH_FORWARD;
    }

    @Override
    public void setFetchSize(int rows)
            throws SQLException
    {
        checkOpen();
        if (rows < 0) {
            throw new SQLException("Fetch size must be positive");
        }
        fetchSize.set(rows);
    }

    @Override
    public int getFetchSize()
            throws SQLException
    {
        checkOpen();
        return fetchSize.get();
    }

    @Override
    public int getResultSetConcurrency()
            throws SQLException
    {
        checkOpen();
        return ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public int getResultSetType()
            throws SQLException
    {
        checkOpen();
        return ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public void addBatch(String sql)
            throws SQLException
    {
        checkOpen();
        throw new SQLFeatureNotSupportedException("Batches not supported");
    }

    @Override
    public void clearBatch()
            throws SQLException
    {
        checkOpen();
        throw new SQLFeatureNotSupportedException("Batches not supported");
    }

    @Override
    public int[] executeBatch()
            throws SQLException
    {
        checkOpen();
        throw new SQLFeatureNotSupportedException("Batches not supported");
    }

    @Override
    public Connection getConnection()
            throws SQLException
    {
        return connection();
    }

    @Override
    public boolean getMoreResults(int current)
            throws SQLException
    {
        checkOpen();

        currentUpdateCount.set(-1);
        currentUpdateType.set(null);

        if (current == CLOSE_CURRENT_RESULT) {
            closeResultSet();
            return false;
        }

        if (current != KEEP_CURRENT_RESULT && current != CLOSE_ALL_RESULTS) {
            throw new SQLException("Invalid argument: " + current);
        }

        throw new SQLFeatureNotSupportedException("Multiple open results not supported");
    }

    @Override
    public ResultSet getGeneratedKeys()
            throws SQLException
    {
        throw new SQLFeatureNotSupportedException("getGeneratedKeys");
    }

    @Override
    public int executeUpdate(String sql)
            throws SQLException
    {
        return Ints.saturatedCast(executeLargeUpdate(sql));
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys)
            throws SQLException
    {
        return executeUpdate(sql);
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes)
            throws SQLException
    {
        return executeUpdate(sql);
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames)
            throws SQLException
    {
        return executeUpdate(sql);
    }

    @Override
    public long executeLargeUpdate(String sql)
            throws SQLException
    {
        if (execute(sql)) {
            throw new SQLException("SQL is not an update statement: " + sql);
        }
        return currentUpdateCount.get();
    }

    @Override
    public long executeLargeUpdate(String sql, int autoGeneratedKeys)
            throws SQLException
    {
        return executeLargeUpdate(sql);
    }

    @Override
    public long executeLargeUpdate(String sql, int[] columnIndexes)
            throws SQLException
    {
        return executeLargeUpdate(sql);
    }

    @Override
    public long executeLargeUpdate(String sql, String[] columnNames)
            throws SQLException
    {
        return executeLargeUpdate(sql);
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys)
            throws SQLException
    {
        return execute(sql);
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes)
            throws SQLException
    {
        return execute(sql);
    }

    @Override
    public boolean execute(String sql, String[] columnNames)
            throws SQLException
    {
        return execute(sql);
    }

    @Override
    public int getResultSetHoldability()
            throws SQLException
    {
        return ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }

    @Override
    public boolean isClosed()
            throws SQLException
    {
        return connection.get() == null;
    }

    @Override
    public void setPoolable(boolean poolable)
            throws SQLException
    {
        checkOpen();
        // ignore: statement pooling not supported
    }

    @Override
    public boolean isPoolable()
            throws SQLException
    {
        checkOpen();
        return false;
    }

    @Override
    public void closeOnCompletion()
            throws SQLException
    {
        checkOpen();
        closeOnCompletion.set(true);
    }

    @Override
    public boolean isCloseOnCompletion()
            throws SQLException
    {
        checkOpen();
        return closeOnCompletion.get();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T unwrap(Class<T> iface)
            throws SQLException
    {
        if (isWrapperFor(iface)) {
            return (T) this;
        }
        throw new SQLException("No wrapper for " + iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface)
            throws SQLException
    {
        return iface.isInstance(this);
    }

    public String getUpdateType()
            throws SQLException
    {
        checkOpen();
        return currentUpdateType.get();
    }

    public void partialCancel()
            throws SQLException
    {
        checkOpen();

        StatementClient client = executingClient.get();
        if (client != null) {
            client.cancelLeafStage();
        }
        else {
            TrinoResultSet resultSet = currentResult.get();
            if (resultSet != null) {
                resultSet.partialCancel();
            }
        }
    }

    protected final void checkOpen()
            throws SQLException
    {
        connection();
    }

    protected final TrinoConnection connection()
            throws SQLException
    {
        TrinoConnection connection = this.connection.get();
        if (connection == null) {
            throw new SQLException("Statement is closed");
        }
        if (connection.isClosed()) {
            throw new SQLException("Connection is closed");
        }
        return connection;
    }

    protected final Optional<TrinoConnection> optionalConnection()
    {
        return Optional.ofNullable(connection.get());
    }

    private void closeResultSet()
            throws SQLException
    {
        ResultSet resultSet = currentResult.getAndSet(null);
        if (resultSet != null) {
            resultSet.close();
        }
    }

    private static boolean validFetchDirection(int direction)
    {
        return (direction == ResultSet.FETCH_FORWARD) ||
                (direction == ResultSet.FETCH_REVERSE) ||
                (direction == ResultSet.FETCH_UNKNOWN);
    }
}
