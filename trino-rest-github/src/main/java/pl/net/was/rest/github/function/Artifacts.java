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
import okhttp3.ResponseBody;
import pl.net.was.rest.github.GithubService;
import pl.net.was.rest.github.model.Artifact;
import pl.net.was.rest.github.model.ArtifactsList;
import retrofit2.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static io.trino.spi.type.StandardTypes.BIGINT;
import static io.trino.spi.type.StandardTypes.VARCHAR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.util.Objects.requireNonNull;
import static pl.net.was.rest.github.GithubRest.ARTIFACTS_TABLE_TYPE;
import static pl.net.was.rest.github.GithubRest.checkServiceResponse;
import static pl.net.was.rest.github.GithubRest.getRowType;

@ScalarFunction("artifacts")
@Description("Get workflow run artifacts")
public class Artifacts
        extends BaseFunction
{
    public Artifacts()
    {
        RowType rowType = getRowType("artifacts");
        arrayType = new ArrayType(rowType);
        pageBuilder = new PageBuilder(ImmutableList.of(arrayType));
    }

    @SqlType(ARTIFACTS_TABLE_TYPE)
    public Block getPage(@SqlType(VARCHAR) Slice owner, @SqlType(VARCHAR) Slice repo, @SqlType(BIGINT) long runId)
            throws IOException
    {
        // there should not be more than a few pages worth of artifacts, so try to get all of them
        List<Artifact> result = new ArrayList<>();
        long total = Long.MAX_VALUE;
        int page = 1;
        while (result.size() < total) {
            Response<ArtifactsList> response = service.listRunArtifacts(
                    "Bearer " + token,
                    owner.toStringUtf8(),
                    repo.toStringUtf8(),
                    runId,
                    100,
                    page++).execute();
            if (response.code() == HTTP_NOT_FOUND) {
                break;
            }
            checkServiceResponse(response);
            ArtifactsList envelope = response.body();
            total = requireNonNull(envelope).getTotalCount();
            List<Artifact> items = envelope.getItems();
            if (items.size() == 0) {
                break;
            }
            for (Artifact artifact : items) {
                artifact.setOwner(owner.toStringUtf8());
                artifact.setRepo(repo.toStringUtf8());
                artifact.setRunId(runId);

                artifact.setContents(download(service, token, owner.toStringUtf8(), repo.toStringUtf8(), artifact.getId()));
            }
            result.addAll(items);
        }
        return buildBlock(result);
    }

    public static InputStream download(GithubService service, String token, String owner, String repo, long artifactId)
            throws IOException
    {
        Response<ResponseBody> response = service.getArtifact("Bearer " + token, owner, repo, artifactId).execute();
        if (response.code() == HTTP_NOT_FOUND) {
            return null;
        }
        checkServiceResponse(response);
        ResponseBody body = requireNonNull(response.body());
        return body.byteStream();
    }
}
