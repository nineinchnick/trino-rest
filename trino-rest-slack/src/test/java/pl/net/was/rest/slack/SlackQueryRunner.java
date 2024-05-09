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

package pl.net.was.rest.slack;

import com.google.common.collect.ImmutableMap;
import io.airlift.log.Level;
import io.airlift.log.Logger;
import io.airlift.log.Logging;
import io.trino.Session;
import io.trino.testing.DistributedQueryRunner;
import io.trino.testing.QueryRunner;

import java.util.Map;

import static io.trino.testing.TestingSession.testSessionBuilder;
import static java.util.Objects.requireNonNullElse;

public class SlackQueryRunner
{
    private SlackQueryRunner() {}

    public static QueryRunner createQueryRunner()
            throws Exception
    {
        Session defaultSession = testSessionBuilder()
                .setCatalog("slack")
                .setSchema("default")
                .build();

        QueryRunner queryRunner = DistributedQueryRunner.builder(defaultSession)
                .setCoordinatorProperties(Map.of(
                        "http-server.http.port", requireNonNullElse(System.getenv("TRINO_PORT"), "8082")))
                .setWorkerCount(0)
                .build();
        queryRunner.installPlugin(new SlackPlugin());

        String token = requireNonNullElse(
                System.getenv("SLACK_TOKEN"),
                "");
        queryRunner.createCatalog(
                "slack",
                "slack",
                ImmutableMap.of(
                        "token", token));

        return queryRunner;
    }

    public static void main(String[] args)
            throws Exception
    {
        Logging logger = Logging.initialize();
        logger.setLevel("pl.net.was", Level.DEBUG);
        logger.setLevel("io.trino", Level.INFO);

        QueryRunner queryRunner = createQueryRunner();

        Logger log = Logger.get(SlackQueryRunner.class);
        log.info("======== SERVER STARTED ========");
        log.info("\n====\n%s\n====", ((DistributedQueryRunner) queryRunner).getCoordinator().getBaseUrl());
    }
}
