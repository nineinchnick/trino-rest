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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import io.trino.spi.NodeManager;
import io.trino.spi.type.TypeManager;
import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.airlift.configuration.ConfigBinder.configBinder;
import static java.util.Objects.requireNonNull;

public class RestModule
        implements Module
{
    private final NodeManager nodeManager;
    private final TypeManager typeManager;
    private final Class<? extends Rest> rest;

    public RestModule(NodeManager nodeManager, TypeManager typeManager, Class<? extends Rest> rest)
    {
        this.nodeManager = requireNonNull(nodeManager, "nodeManager is null");
        this.typeManager = requireNonNull(typeManager, "typeManager is null");
        this.rest = requireNonNull(rest, "rest is null");
    }

    @Override
    public void configure(Binder binder)
    {
        binder.bind(NodeManager.class).toInstance(nodeManager);
        binder.bind(TypeManager.class).toInstance(typeManager);
        binder.bind(Rest.class).to(rest);

        binder.bind(RestConnector.class).in(Scopes.SINGLETON);
        binder.bind(RestMetadata.class).in(Scopes.SINGLETON);
        binder.bind(RestSplitManager.class).in(Scopes.SINGLETON);
        binder.bind(RestRecordSetProvider.class).in(Scopes.SINGLETON);
        configBinder(binder).bindConfig(RestConfig.class);
    }

    public static <T> T getService(Class<T> type, String url, Interceptor... interceptors)
    {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

        // TODO make configurable? https://github.com/nineinchnick/trino-rest/issues/22
        Path cacheDir = Paths.get(System.getProperty("java.io.tmpdir"), "trino-rest-cache");
        clientBuilder.cache(new Cache(cacheDir.toFile(), 10 * 1024 * 1024));

        if (getLogLevel().intValue() <= Level.FINE.intValue()) {
            HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            clientBuilder.addInterceptor(interceptor);
        }

        for (Interceptor interceptor : interceptors) {
            clientBuilder.addInterceptor(interceptor);
        }

        return new Retrofit.Builder()
                .baseUrl(url)
                .client(clientBuilder.build())
                .addConverterFactory(JacksonConverterFactory.create(
                        new ObjectMapper()
                                .registerModule(new Jdk8Module())
                                .registerModule(new JavaTimeModule())))
                .build()
                .create(type);
    }

    private static Level getLogLevel()
    {
        String loggerName = Rest.class.getName();
        Logger logger = Logger.getLogger(loggerName);
        Level level = logger.getLevel();
        while (level == null) {
            Logger parent = logger.getParent();
            if (parent == null) {
                return Level.OFF;
            }
            level = parent.getLevel();
        }
        return level;
    }
}
