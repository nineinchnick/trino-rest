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
import pl.net.was.rest.github.model.Repository;
import retrofit2.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.trino.spi.StandardErrorCode.GENERIC_INTERNAL_ERROR;
import static io.trino.spi.type.StandardTypes.VARCHAR;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.util.Objects.requireNonNull;
import static pl.net.was.rest.github.GithubRest.REPOS_TABLE_TYPE;
import static pl.net.was.rest.github.GithubRest.getRowType;

@ScalarFunction("user_repos")
@Description("Get user repositories")
public class UserRepos
        extends BaseFunction
{
    public UserRepos()
    {
        RowType rowType = getRowType("repos");
        arrayType = new ArrayType(rowType);
        pageBuilder = new PageBuilder(ImmutableList.of(arrayType));
    }

    @SqlType(REPOS_TABLE_TYPE)
    public Block getPage(@SqlType(VARCHAR) Slice token, @SqlType(VARCHAR) Slice username)
            throws IOException
    {
        // there should not be more than a few pages worth of repos, so try to get all of them
        List<Repository> repos = new ArrayList<>();
        int page = 1;
        while (true) {
            Response<List<Repository>> response = service.listUserRepos(
                    token.toStringUtf8(),
                    username.toStringUtf8(),
                    100,
                    page++,
                    "updated").execute();
            if (response.code() == HTTP_NOT_FOUND) {
                break;
            }
            if (!response.isSuccessful()) {
                throw new TrinoException(GENERIC_INTERNAL_ERROR, format("Invalid response, code %d, message: %s", response.code(), response.message()));
            }
            List<Repository> items = requireNonNull(response.body());
            if (items.size() == 0) {
                break;
            }
            repos.addAll(items);
        }
        return buildBlock(repos);
    }
}
