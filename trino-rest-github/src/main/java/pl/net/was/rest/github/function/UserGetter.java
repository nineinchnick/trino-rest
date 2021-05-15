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
import io.trino.spi.block.BlockBuilder;
import io.trino.spi.function.Description;
import io.trino.spi.function.ScalarFunction;
import io.trino.spi.function.SqlType;
import io.trino.spi.type.RowType;
import pl.net.was.rest.github.model.BlockWriter;
import pl.net.was.rest.github.model.User;
import retrofit2.Response;

import java.io.IOException;

import static io.trino.spi.StandardErrorCode.GENERIC_INTERNAL_ERROR;
import static io.trino.spi.type.StandardTypes.VARCHAR;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static pl.net.was.rest.github.GithubRest.USER_ROW_TYPE;
import static pl.net.was.rest.github.GithubRest.getRowType;

@ScalarFunction("user")
@Description("Get a user")
public class UserGetter
        extends BaseFunction
{
    private final RowType rowType;

    public UserGetter()
    {
        rowType = getRowType("users");
        pageBuilder = new PageBuilder(ImmutableList.of(rowType));
    }

    @SqlType(USER_ROW_TYPE)
    public Block get(@SqlType(VARCHAR) Slice token, @SqlType(VARCHAR) Slice username)
            throws IOException
    {
        Response<User> response = service.getUser(
                token.toStringUtf8(),
                username.toStringUtf8()).execute();
        if (response.code() == HTTP_NOT_FOUND) {
            return null;
        }
        if (!response.isSuccessful()) {
            throw new TrinoException(GENERIC_INTERNAL_ERROR, format("Invalid response, code %d, message: %s", response.code(), response.message()));
        }
        User item = response.body();
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
