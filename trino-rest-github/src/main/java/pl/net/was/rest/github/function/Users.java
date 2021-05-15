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
import pl.net.was.rest.github.model.User;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;

import static io.trino.spi.StandardErrorCode.GENERIC_INTERNAL_ERROR;
import static io.trino.spi.type.StandardTypes.VARCHAR;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static pl.net.was.rest.github.GithubRest.USERS_TABLE_TYPE;
import static pl.net.was.rest.github.GithubRest.getRowType;

@ScalarFunction("users")
@Description("Get users")
public class Users
        extends BaseFunction
{
    public Users()
    {
        RowType rowType = getRowType("users");
        arrayType = new ArrayType(rowType);
        pageBuilder = new PageBuilder(ImmutableList.of(arrayType));
    }

    @SqlType(USERS_TABLE_TYPE)
    public Block getPage(@SqlType(VARCHAR) Slice token, @SqlType("timestamp(3)") long since)
            throws IOException
    {
        Response<List<User>> response = service.listUsers(
                token.toStringUtf8(),
                100,
                ISO_LOCAL_DATE_TIME.format(fromTrinoTimestamp(since)) + "Z").execute();
        if (response.code() == HTTP_NOT_FOUND) {
            return null;
        }
        if (!response.isSuccessful()) {
            throw new TrinoException(GENERIC_INTERNAL_ERROR, format("Invalid response, code %d, message: %s", response.code(), response.message()));
        }
        List<User> items = response.body();
        return buildBlock(items);
    }
}
