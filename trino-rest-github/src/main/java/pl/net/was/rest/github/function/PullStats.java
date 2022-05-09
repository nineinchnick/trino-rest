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

package pl.net.was.rest.github.function;

import com.google.common.collect.ImmutableList;
import io.airlift.slice.Slice;
import io.trino.spi.PageBuilder;
import io.trino.spi.block.Block;
import io.trino.spi.block.BlockBuilder;
import io.trino.spi.function.Description;
import io.trino.spi.function.ScalarFunction;
import io.trino.spi.function.SqlType;
import io.trino.spi.type.RowType;
import pl.net.was.rest.Rest;
import pl.net.was.rest.github.GithubTable;
import pl.net.was.rest.github.model.BlockWriter;
import pl.net.was.rest.github.model.PullStatistics;
import retrofit2.Response;

import java.io.IOException;

import static io.trino.spi.type.StandardTypes.BIGINT;
import static io.trino.spi.type.StandardTypes.VARCHAR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static pl.net.was.rest.github.GithubRest.PULL_STATS_ROW_TYPE;
import static pl.net.was.rest.github.GithubRest.getRowType;

@ScalarFunction(value = "pull_stats", deterministic = true)
@Description("Get pull request statistics")
public class PullStats
        extends BaseFunction
{
    private final RowType rowType;

    public PullStats()
    {
        rowType = getRowType(GithubTable.PULL_STATS);
        pageBuilder = new PageBuilder(ImmutableList.of(rowType));
    }

    @SqlType(PULL_STATS_ROW_TYPE)
    public Block get(@SqlType(VARCHAR) Slice owner, @SqlType(VARCHAR) Slice repo, @SqlType(BIGINT) long pullNumber)
            throws IOException
    {
        final String ownerString = owner.toStringUtf8();
        final String repoString = repo.toStringUtf8();
        Response<PullStatistics> response = service.getPull(
                "Bearer " + token,
                ownerString,
                repoString,
                pullNumber).execute();
        if (response.code() == HTTP_NOT_FOUND) {
            return null;
        }
        Rest.checkServiceResponse(response);
        PullStatistics item = response.body();
        item.setOwner(ownerString);
        item.setRepo(repoString);
        item.setPullNumber(pullNumber);
        return buildBlock(item);
    }

    private Block buildBlock(BlockWriter writer)
    {
        if (pageBuilder.isFull()) {
            pageBuilder.reset();
        }

        BlockBuilder blockBuilder = pageBuilder.getBlockBuilder(0);
        BlockBuilder rowBuilder = blockBuilder.beginBlockEntry();
        writer.writeTo(rowBuilder);
        blockBuilder.closeEntry();
        pageBuilder.declarePosition();
        return rowType.getObject(blockBuilder, blockBuilder.getPositionCount() - 1);
    }
}
