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
package io.trino.server.ui;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.execution.QueryState;
import io.trino.server.BasicQueryInfo;
import io.trino.server.BasicQueryStats;
import io.trino.spi.ErrorCode;
import io.trino.spi.ErrorType;
import io.trino.spi.QueryId;
import io.trino.spi.memory.MemoryPoolId;
import io.trino.spi.resourcegroups.QueryType;
import io.trino.spi.resourcegroups.ResourceGroupId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import java.net.URI;
import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

@Immutable
public class UiBasicQueryInfo
{
    private static final int MAX_QUERY_LENGTH = 300;

    private final QueryId queryId;
    private final String user;
    private final Optional<String> principal;
    private final Optional<String> source;
    private final Optional<ResourceGroupId> resourceGroupId;
    private final QueryState state;
    private final MemoryPoolId memoryPool;
    private final boolean scheduled;
    private final URI self;
    private final String query; // trim to 300 characters
    private final Optional<String> updateType;
    private final Optional<String> preparedQuery;
    private final BasicQueryStats queryStats;
    private final ErrorType errorType;
    private final ErrorCode errorCode;
    private final Optional<QueryType> queryType;

    @JsonCreator
    public UiBasicQueryInfo(
            @JsonProperty("queryId") QueryId queryId,
            @JsonProperty("user") String user,
            @JsonProperty("principal") Optional<String> principal,
            @JsonProperty("source") Optional<String> source,
            @JsonProperty("resourceGroupId") Optional<ResourceGroupId> resourceGroupId,
            @JsonProperty("state") QueryState state,
            @JsonProperty("memoryPool") MemoryPoolId memoryPool,
            @JsonProperty("scheduled") boolean scheduled,
            @JsonProperty("self") URI self,
            @JsonProperty("query") String query,
            @JsonProperty("updateType") Optional<String> updateType,
            @JsonProperty("preparedQuery") Optional<String> preparedQuery,
            @JsonProperty("queryStats") BasicQueryStats queryStats,
            @JsonProperty("errorType") ErrorType errorType,
            @JsonProperty("errorCode") ErrorCode errorCode,
            @JsonProperty("queryType") Optional<QueryType> queryType)
    {
        this.queryId = requireNonNull(queryId, "queryId is null");
        this.user = requireNonNull(user, "user is null");
        this.principal = principal;
        this.source = source;
        this.resourceGroupId = requireNonNull(resourceGroupId, "resourceGroupId is null");
        this.state = requireNonNull(state, "state is null");
        this.memoryPool = memoryPool;
        this.errorType = errorType;
        this.errorCode = errorCode;
        this.scheduled = scheduled;
        this.self = requireNonNull(self, "self is null");
        this.query = requireNonNull(query, "query is null").substring(0, Math.min(query.length(), MAX_QUERY_LENGTH - 1));
        this.updateType = requireNonNull(updateType, "updateType is null");
        this.preparedQuery = requireNonNull(preparedQuery, "preparedQuery is null");
        this.queryStats = requireNonNull(queryStats, "queryStats is null");
        this.queryType = requireNonNull(queryType, "queryType is null");
    }

    public UiBasicQueryInfo(BasicQueryInfo queryInfo)
    {
        this(queryInfo.getQueryId(),
                queryInfo.getSession().getUser(),
                queryInfo.getSession().getPrincipal(),
                queryInfo.getSession().getSource(),
                queryInfo.getResourceGroupId(),
                queryInfo.getState(),
                queryInfo.getMemoryPool(),
                queryInfo.isScheduled(),
                queryInfo.getSelf(),
                queryInfo.getQuery(),
                queryInfo.getUpdateType(),
                queryInfo.getPreparedQuery(),
                queryInfo.getQueryStats(),
                queryInfo.getErrorType(),
                queryInfo.getErrorCode(),
                queryInfo.getQueryType());
    }

    @JsonProperty
    public QueryId getQueryId()
    {
        return queryId;
    }

    @JsonProperty
    public String getUser()
    {
        return user;
    }

    @JsonProperty
    public Optional<String> getPrincipal()
    {
        return principal;
    }

    @JsonProperty
    public Optional<String> getSource()
    {
        return source;
    }

    @JsonProperty
    public Optional<ResourceGroupId> getResourceGroupId()
    {
        return resourceGroupId;
    }

    @JsonProperty
    public QueryState getState()
    {
        return state;
    }

    @JsonProperty
    public MemoryPoolId getMemoryPool()
    {
        return memoryPool;
    }

    @JsonProperty
    public boolean isScheduled()
    {
        return scheduled;
    }

    @JsonProperty
    public URI getSelf()
    {
        return self;
    }

    @JsonProperty
    public String getQuery()
    {
        return query;
    }

    @JsonProperty
    public Optional<String> getUpdateType()
    {
        return updateType;
    }

    @JsonProperty
    public Optional<String> getPreparedQuery()
    {
        return preparedQuery;
    }

    @JsonProperty
    public BasicQueryStats getQueryStats()
    {
        return queryStats;
    }

    @Nullable
    @JsonProperty
    public ErrorType getErrorType()
    {
        return errorType;
    }

    @Nullable
    @JsonProperty
    public ErrorCode getErrorCode()
    {
        return errorCode;
    }

    @JsonProperty
    public Optional<QueryType> getQueryType()
    {
        return queryType;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("queryId", queryId)
                .add("state", state)
                .toString();
    }
}
