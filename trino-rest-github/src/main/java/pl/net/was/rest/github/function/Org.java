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
import io.trino.spi.block.RowBlockBuilder;
import io.trino.spi.block.SqlRow;
import io.trino.spi.function.Description;
import io.trino.spi.function.ScalarFunction;
import io.trino.spi.function.SqlType;
import io.trino.spi.type.RowType;
import pl.net.was.rest.Rest;
import pl.net.was.rest.github.GithubTable;
import pl.net.was.rest.github.model.BlockWriter;
import pl.net.was.rest.github.model.Organization;
import retrofit2.Response;

import java.io.IOException;

import static io.trino.spi.type.StandardTypes.VARCHAR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static pl.net.was.rest.github.GithubRest.ORG_ROW_TYPE;
import static pl.net.was.rest.github.GithubRest.getRowType;

@ScalarFunction(value = "org", deterministic = false)
@Description("Get an organization")
public class Org
        extends BaseFunction
{
    private final RowType rowType;

    public Org()
    {
        rowType = getRowType(GithubTable.ORGS);
        pageBuilder = new PageBuilder(ImmutableList.of(rowType));
    }

    @SqlType(ORG_ROW_TYPE)
    public SqlRow get(@SqlType(VARCHAR) Slice org)
            throws IOException
    {
        Response<Organization> response = service.getOrg(
                "Bearer " + token,
                org.toStringUtf8()).execute();
        if (response.code() == HTTP_NOT_FOUND) {
            return null;
        }
        Rest.checkServiceResponse(response);
        Organization item = response.body();
        return buildBlock(item);
    }

    private SqlRow buildBlock(BlockWriter writer)
    {
        if (pageBuilder.isFull()) {
            pageBuilder.reset();
        }

        RowBlockBuilder blockBuilder = (RowBlockBuilder) pageBuilder.getBlockBuilder(0);
        blockBuilder.buildEntry(writer::writeTo);
        pageBuilder.declarePosition();
        Block block = blockBuilder.build();
        return rowType.getObject(block, block.getPositionCount() - 1);
    }
}
