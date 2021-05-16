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
import io.trino.spi.function.Description;
import io.trino.spi.function.ScalarFunction;
import io.trino.spi.function.SqlType;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.RowType;
import pl.net.was.rest.github.model.Run;
import pl.net.was.rest.github.model.RunsList;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;

import static io.trino.spi.type.StandardTypes.INTEGER;
import static io.trino.spi.type.StandardTypes.VARCHAR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.util.Objects.requireNonNull;
import static pl.net.was.rest.github.GithubRest.RUNS_TABLE_TYPE;
import static pl.net.was.rest.github.GithubRest.checkServiceResponse;
import static pl.net.was.rest.github.GithubRest.getRowType;

@ScalarFunction("runs")
@Description("Get workflow runs")
public class Runs
        extends BaseFunction
{
    public Runs()
    {
        RowType rowType = getRowType("runs");
        arrayType = new ArrayType(rowType);
        pageBuilder = new PageBuilder(ImmutableList.of(arrayType));
    }

    @SqlType(RUNS_TABLE_TYPE)
    public Block getPage(@SqlType(VARCHAR) Slice owner, @SqlType(VARCHAR) Slice repo, @SqlType(INTEGER) long page)
            throws IOException
    {
        Response<RunsList> response = service.listRuns(
                "Bearer " + token,
                owner.toStringUtf8(),
                repo.toStringUtf8(),
                100,
                (int) page).execute();
        if (response.code() == HTTP_NOT_FOUND) {
            return null;
        }
        checkServiceResponse(response);
        RunsList envelope = response.body();
        List<Run> items = requireNonNull(envelope).getItems();
        items.forEach(i -> i.setOwner(owner.toStringUtf8()));
        items.forEach(i -> i.setRepo(repo.toStringUtf8()));
        return buildBlock(items);
    }
}
