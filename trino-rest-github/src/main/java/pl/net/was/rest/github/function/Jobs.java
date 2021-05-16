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
import io.trino.spi.TrinoException;
import io.trino.spi.block.Block;
import io.trino.spi.function.Description;
import io.trino.spi.function.ScalarFunction;
import io.trino.spi.function.SqlType;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.RowType;
import pl.net.was.rest.github.model.Job;
import pl.net.was.rest.github.model.JobsList;
import retrofit2.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.trino.spi.StandardErrorCode.GENERIC_INTERNAL_ERROR;
import static io.trino.spi.type.StandardTypes.BIGINT;
import static io.trino.spi.type.StandardTypes.VARCHAR;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static pl.net.was.rest.github.GithubRest.JOBS_TABLE_TYPE;
import static pl.net.was.rest.github.GithubRest.getRowType;

@ScalarFunction("jobs")
@Description("Get workflow jobs")
public class Jobs
        extends BaseFunction
{
    public Jobs()
    {
        RowType rowType = getRowType("jobs");
        arrayType = new ArrayType(rowType);
        pageBuilder = new PageBuilder(ImmutableList.of(arrayType));
    }

    @SqlType(JOBS_TABLE_TYPE)
    public Block getPage(@SqlType(VARCHAR) Slice token, @SqlType(VARCHAR) Slice owner, @SqlType(VARCHAR) Slice repo, @SqlType(BIGINT) long runId)
            throws IOException
    {
        // there should not be more than a few pages worth of jobs, so try to get all of them
        List<Job> jobs = new ArrayList<>();
        long total = Long.MAX_VALUE;
        int page = 1;
        while (jobs.size() < total) {
            Response<JobsList> response = service.listRunJobs(
                    token.toStringUtf8(),
                    owner.toStringUtf8(),
                    repo.toStringUtf8(),
                    runId,
                    "all",
                    100,
                    page++).execute();
            if (response.code() == HTTP_NOT_FOUND) {
                break;
            }
            if (!response.isSuccessful()) {
                throw new TrinoException(GENERIC_INTERNAL_ERROR, format("Invalid response, code %d, message: %s", response.code(), response.message()));
            }
            JobsList envelope = response.body();
            total = Objects.requireNonNull(envelope).getTotalCount();
            List<Job> items = envelope.getItems();
            if (items.size() == 0) {
                break;
            }
            items.forEach(i -> i.setOwner(owner.toStringUtf8()));
            items.forEach(i -> i.setRepo(repo.toStringUtf8()));
            jobs.addAll(items);
        }
        return buildBlock(jobs);
    }
}
