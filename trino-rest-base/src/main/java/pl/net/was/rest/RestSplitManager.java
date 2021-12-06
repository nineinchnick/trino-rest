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
import io.trino.spi.connector.ConnectorPartitionHandle;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorSplitManager;
import io.trino.spi.connector.ConnectorSplitSource;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.connector.DynamicFilter;

import javax.inject.Inject;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static io.trino.spi.connector.DynamicFilter.NOT_BLOCKED;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

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
            SplitSchedulingStrategy splitSchedulingStrategy,
            DynamicFilter dynamicFilter)
    {
        long timeoutMillis = 20000;
        if (!dynamicFilter.isAwaitable()) {
            return rest.getSplitSource(nodeManager, table, splitSchedulingStrategy, dynamicFilter);
        }
        CompletableFuture<?> dynamicFilterFuture = whenCompleted(dynamicFilter)
                .completeOnTimeout(null, timeoutMillis, MILLISECONDS);
        CompletableFuture<ConnectorSplitSource> splitSourceFuture = dynamicFilterFuture.thenApply(
                ignored -> rest.getSplitSource(nodeManager, table, splitSchedulingStrategy, dynamicFilter));
        return new RestDynamicFilteringSplitSource(dynamicFilterFuture, splitSourceFuture);
    }

    private static CompletableFuture<?> whenCompleted(DynamicFilter dynamicFilter)
    {
        if (dynamicFilter.isAwaitable()) {
            return dynamicFilter.isBlocked().thenCompose(ignored -> whenCompleted(dynamicFilter));
        }
        return NOT_BLOCKED;
    }

    private static class RestDynamicFilteringSplitSource
            implements ConnectorSplitSource
    {
        private final CompletableFuture<?> dynamicFilterFuture;
        private final CompletableFuture<ConnectorSplitSource> splitSourceFuture;

        private RestDynamicFilteringSplitSource(
                CompletableFuture<?> dynamicFilterFuture,
                CompletableFuture<ConnectorSplitSource> splitSourceFuture)
        {
            this.dynamicFilterFuture = requireNonNull(dynamicFilterFuture, "dynamicFilterFuture is null");
            this.splitSourceFuture = requireNonNull(splitSourceFuture, "splitSourceFuture is null");
        }

        @Override
        public CompletableFuture<ConnectorSplitBatch> getNextBatch(ConnectorPartitionHandle partitionHandle, int maxSize)
        {
            return splitSourceFuture.thenCompose(splitSource -> splitSource.getNextBatch(partitionHandle, maxSize));
        }

        @Override
        @SuppressWarnings("FutureReturnValueIgnored")
        public void close()
        {
            if (!dynamicFilterFuture.cancel(true)) {
                splitSourceFuture.thenAccept(ConnectorSplitSource::close);
            }
        }

        @Override
        public boolean isFinished()
        {
            if (!splitSourceFuture.isDone()) {
                return false;
            }
            if (splitSourceFuture.isCompletedExceptionally()) {
                return false;
            }
            try {
                return splitSourceFuture.get().isFinished();
            }
            catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
