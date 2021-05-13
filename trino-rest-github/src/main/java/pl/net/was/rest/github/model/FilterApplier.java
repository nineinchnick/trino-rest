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

package pl.net.was.rest.github.model;

import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConstraintApplicationResult;
import io.trino.spi.predicate.TupleDomain;
import pl.net.was.rest.RestTableHandle;

import java.util.Map;
import java.util.Optional;

public interface FilterApplier
{
    Optional<ConstraintApplicationResult<ConnectorTableHandle>> applyFilter(RestTableHandle table, Map<String, ColumnHandle> columns, TupleDomain<ColumnHandle> constraint);
}
