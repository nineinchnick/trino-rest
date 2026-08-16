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

package pl.net.was.rest;

import io.trino.spi.NodeManager;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorSplit;
import io.trino.spi.connector.ConnectorSplitManager;
import io.trino.spi.connector.ConnectorSplitSource;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.connector.Constraint;
import io.trino.spi.connector.DynamicFilterSnapshot;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static io.trino.spi.connector.DynamicFilterSnapshot.EMPTY;
import static java.util.Objects.requireNonNull;

public class RestSplitManager
        implements ConnectorSplitManager
{
    private final Rest rest;
    private final NodeManager nodeManager;

    @Inject
    public RestSplitManager(Rest rest, NodeManager nodeManager)
    {
        this.rest = rest;
        this.nodeManager = nodeManager;
    }

    @Override
    public ConnectorSplitSource getSplits(
            ConnectorTransactionHandle transaction,
            ConnectorSession session,
            ConnectorTableHandle table,
            Set<ColumnHandle> dynamicFilterColumns,
            Constraint constraint)
    {
        if (dynamicFilterColumns.isEmpty()) {
            return rest.getSplitSource(nodeManager, table, EMPTY);
        }
        return new RestDynamicFilteringSplitSource(rest, nodeManager, table);
    }

    private static class RestDynamicFilteringSplitSource
            implements ConnectorSplitSource
    {
        private static final long DYNAMIC_FILTERING_WAIT_TIMEOUT_MILLIS = 20_000;

        private final Rest rest;
        private final NodeManager nodeManager;
        private final ConnectorTableHandle table;

        private ConnectorSplitSource delegate;

        private RestDynamicFilteringSplitSource(
                Rest rest,
                NodeManager nodeManager,
                ConnectorTableHandle table)
        {
            this.rest = requireNonNull(rest, "rest is null");
            this.nodeManager = requireNonNull(nodeManager, "nodeManager is null");
            this.table = requireNonNull(table, "table is null");
        }

        @Override
        public long getRequestedDynamicFilterWaitTimeoutMillis()
        {
            return DYNAMIC_FILTERING_WAIT_TIMEOUT_MILLIS;
        }

        @Override
        public CompletableFuture<List<ConnectorSplit>> getNextBatch(int maxSize, DynamicFilterSnapshot dynamicFilterSnapshot)
        {
            return getDelegate(dynamicFilterSnapshot).getNextBatch(maxSize, dynamicFilterSnapshot);
        }

        @Override
        public synchronized void close()
        {
            if (delegate != null) {
                delegate.close();
            }
        }

        @Override
        public synchronized boolean isFinished()
        {
            return delegate != null && delegate.isFinished();
        }

        private synchronized ConnectorSplitSource getDelegate(DynamicFilterSnapshot dynamicFilterSnapshot)
        {
            if (delegate == null) {
                delegate = rest.getSplitSource(nodeManager, table, dynamicFilterSnapshot);
            }
            return delegate;
        }
    }
}
