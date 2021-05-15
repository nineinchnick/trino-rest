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

import io.airlift.slice.Slice;
import io.airlift.slice.Slices;
import io.trino.spi.TrinoException;
import io.trino.spi.function.Description;
import io.trino.spi.function.ScalarFunction;
import io.trino.spi.function.SqlType;
import okhttp3.ResponseBody;
import retrofit2.Response;

import java.io.IOException;

import static io.trino.spi.StandardErrorCode.GENERIC_INTERNAL_ERROR;
import static io.trino.spi.type.StandardTypes.BIGINT;
import static io.trino.spi.type.StandardTypes.VARCHAR;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

@ScalarFunction("job_logs")
@Description("Get workflow job logs")
public class JobLogs
        extends BaseFunction
{
    public JobLogs() {}

    @SqlType("varchar")
    public Slice getLog(@SqlType(VARCHAR) Slice token, @SqlType(VARCHAR) Slice owner, @SqlType(VARCHAR) Slice repo, @SqlType(BIGINT) long jobId)
            throws IOException
    {
        Response<ResponseBody> response = service.jobLogs(
                token.toStringUtf8(),
                owner.toStringUtf8(),
                repo.toStringUtf8(),
                jobId).execute();
        if (response.code() == HTTP_NOT_FOUND) {
            return Slices.EMPTY_SLICE;
        }
        if (!response.isSuccessful()) {
            throw new TrinoException(GENERIC_INTERNAL_ERROR, format("Invalid response, code %d, message: %s", response.code(), response.message()));
        }
        ResponseBody body = response.body();
        String log = "";
        if (body != null) {
            log = body.string().replaceAll("\u0000", "");
        }
        return Slices.utf8Slice(log);
    }
}
