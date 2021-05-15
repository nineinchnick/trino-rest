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

package pl.net.was.rest.github.filter;

import io.airlift.slice.Slice;
import io.trino.spi.TrinoException;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConstraintApplicationResult;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.Range;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.predicate.ValueSet;
import io.trino.spi.type.TimeZoneKey;
import pl.net.was.rest.RestColumnHandle;
import pl.net.was.rest.RestTableHandle;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.trino.spi.StandardErrorCode.INVALID_ROW_FILTER;
import static io.trino.spi.type.DateTimeEncoding.unpackMillisUtc;
import static io.trino.spi.type.DateTimeEncoding.unpackZoneKey;
import static io.trino.spi.type.Timestamps.MILLISECONDS_PER_SECOND;
import static io.trino.spi.type.Timestamps.NANOSECONDS_PER_MILLISECOND;
import static java.lang.Math.floorDiv;
import static java.lang.Math.floorMod;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static pl.net.was.rest.github.filter.FilterType.EQUAL;

public interface FilterApplier
{
    Map<String, FilterType> getSupportedFilters();

    default Optional<ConstraintApplicationResult<ConnectorTableHandle>> applyFilter(RestTableHandle table, Map<String, ColumnHandle> columns, Map<String, FilterType> supportedColumnFilters, TupleDomain<ColumnHandle> constraint)
    {
        // the only reason not to use isNone is so the linter doesn't complain about not checking an Optional
        if (constraint.isAll() || constraint.getDomains().isEmpty()) {
            return Optional.empty();
        }

        TupleDomain<ColumnHandle> currentConstraint = table.getConstraint();

        boolean found = false;
        for (Map.Entry<String, FilterType> entry : supportedColumnFilters.entrySet()) {
            String columnName = entry.getKey();
            FilterType supportedFilter = entry.getValue();
            ColumnHandle column = columns.get(columnName);

            TupleDomain<ColumnHandle> newConstraint = normalizeConstraint((RestColumnHandle) column, supportedFilter, constraint);
            if (newConstraint == null || newConstraint.getDomains().isEmpty()) {
                continue;
            }
            if (!validateConstraint((RestColumnHandle) column, supportedFilter, currentConstraint, newConstraint)) {
                continue;
            }
            // merge with other pushed down constraints
            Domain domain = newConstraint.getDomains().get().get(column);
            if (currentConstraint.getDomains().isEmpty()) {
                currentConstraint = newConstraint;
            }
            else if (!currentConstraint.getDomains().get().containsKey(column)) {
                Map<ColumnHandle, Domain> domains = new HashMap<>(currentConstraint.getDomains().get());
                domains.put(column, domain);
                currentConstraint = TupleDomain.withColumnDomains(domains);
            }
            else {
                currentConstraint.getDomains().get().get(column).union(domain);
            }
            found = true;
            // remove from remaining constraints
            constraint = constraint.filter(
                    (columnHandle, tupleDomain) -> !columnHandle.equals(column));
        }
        if (!found) {
            return Optional.empty();
        }

        return Optional.of(new ConstraintApplicationResult<>(
                new RestTableHandle(
                        table.getSchemaTableName(),
                        currentConstraint),
                constraint));
    }

    private TupleDomain<ColumnHandle> normalizeConstraint(RestColumnHandle column, FilterType supportedFilter, TupleDomain<ColumnHandle> constraint)
    {
        //noinspection OptionalGetWithoutIsPresent
        Domain domain = constraint.getDomains().get().get(column);
        if (domain == null) {
            return null;
        }
        TupleDomain<ColumnHandle> newConstraint = constraint.filter(
                (columnHandle, tupleDomain) -> columnHandle.equals(column));
        if (!domain.getType().isOrderable()) {
            if (!domain.isSingleValue()) {
                // none of the upstream filters supports multiple values
                return null;
            }
            return newConstraint;
        }
        switch (supportedFilter) {
            case GREATER_THAN_EQUAL:
                // normalize the constraint into a low-bound range
                Range span = domain.getValues().getRanges().getSpan();
                if (span.isLowUnbounded()) {
                    return null;
                }
                newConstraint = TupleDomain.withColumnDomains(Map.of(
                        column,
                        Domain.create(
                                ValueSet.ofRanges(
                                        Range.greaterThanOrEqual(
                                                domain.getType(),
                                                span.getLowBoundedValue())),
                                false)));
                break;
            case EQUAL:
                if (!domain.isSingleValue()) {
                    // none of the upstream filters supports multiple values
                    return null;
                }
                break;
        }
        return newConstraint;
    }

    private boolean validateConstraint(RestColumnHandle column, FilterType supportedFilter, TupleDomain<ColumnHandle> currentConstraint, TupleDomain<ColumnHandle> newConstraint)
    {
        if (currentConstraint.getDomains().isEmpty() || !currentConstraint.getDomains().get().containsKey(column)) {
            return true;
        }
        Domain currentDomain = currentConstraint.getDomains().get().get(column);
        Domain newDomain = newConstraint.getDomains().get().get(column);
        if (currentDomain.equals(newDomain)) {
            // it is important to avoid processing same constraint multiple times
            // so that planner doesn't get stuck in a loop
            return false;
        }
        if (supportedFilter == EQUAL) {
            // can push down only the first predicate against this column
            throw new TrinoException(INVALID_ROW_FILTER, "Already pushed down a predicate for " + column.getName() + " which only supports a single value");
        }
        // don't need to check for GREATER_THAN_EQUAL since there can only be a single low-bound range, so union would work
        return true;
    }

    default Object getFilter(RestColumnHandle column, TupleDomain<ColumnHandle> constraint)
    {
        Domain domain = null;
        if (constraint.getDomains().isPresent()) {
            domain = constraint.getDomains().get().get(column);
        }
        // TODO don't use column names here, just types
        switch (column.getName()) {
            case "updated_at":
                if (domain == null) {
                    return "1970-01-01T00:00:00Z";
                }
                long since = (long) domain.getValues().getRanges().getSpan().getLowBoundedValue();
                return ISO_LOCAL_DATE_TIME.format(fromTrinoTimestamp(since)) + "Z";
            case "owner":
            case "repo":
            case "login":
            case "owner_login":
                if (domain == null) {
                    throw new TrinoException(INVALID_ROW_FILTER, "Missing required constraint for " + column.getName());
                }
                return ((Slice) domain.getSingleValue()).toStringUtf8();
            case "pull_number":
                if (domain == null) {
                    throw new TrinoException(INVALID_ROW_FILTER, "Missing required constraint for " + column.getName());
                }
                return domain.getSingleValue();
            case "run_id":
                if (domain == null) {
                    return null;
                }
                return domain.getSingleValue();
            default:
                throw new TrinoException(INVALID_ROW_FILTER, "Unexpected constraint for " + column.getName());
        }
    }

    default ZonedDateTime fromTrinoTimestamp(long timestampWithTimeZone)
    {
        TimeZoneKey zoneKey = unpackZoneKey(timestampWithTimeZone);
        long millis = unpackMillisUtc(timestampWithTimeZone);

        long epochSecond = floorDiv(millis, MILLISECONDS_PER_SECOND);
        int nanoFraction = floorMod(millis, MILLISECONDS_PER_SECOND) * NANOSECONDS_PER_MILLISECOND;
        Instant instant = Instant.ofEpochSecond(epochSecond, nanoFraction);
        return ZonedDateTime.ofInstant(instant, zoneKey.getZoneId()).withZoneSameInstant(ZoneId.of("UTC"));
    }
}
