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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.slice.Slice;
import io.trino.spi.block.BlockBuilder;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConstraintApplicationResult;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.Range;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.predicate.ValueSet;
import pl.net.was.rest.RestColumnHandle;
import pl.net.was.rest.RestTableHandle;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Issue
        extends BaseBlockWriter
{
    private String owner;
    private String repo;
    private final long id;
    private final String url;
    private final String eventsUrl;
    private final String htmlUrl;
    private final long number;
    private final String state;
    private final String title;
    private final String body;
    private final User user;
    private final List<Label> labels;
    private final User assignee;
    private final Milestone milestone;
    private final Boolean locked;
    private final String activeLockReason;
    private final long comments;
    private final ZonedDateTime closedAt;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime updatedAt;
    private final String authorAssociation;

    public Issue(
            @JsonProperty("id") long id,
            @JsonProperty("url") String url,
            @JsonProperty("events_url") String eventsUrl,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("number") long number,
            @JsonProperty("state") String state,
            @JsonProperty("title") String title,
            @JsonProperty("body") String body,
            @JsonProperty("user") User user,
            @JsonProperty("labels") List<Label> labels,
            @JsonProperty("assignee") User assignee,
            @JsonProperty("milestone") Milestone milestone,
            @JsonProperty("locked") Boolean locked,
            @JsonProperty("active_lock_reason") String activeLockReason,
            @JsonProperty("comments") long comments,
            @JsonProperty("closed_at") ZonedDateTime closedAt,
            @JsonProperty("created_at") ZonedDateTime createdAt,
            @JsonProperty("updated_at") ZonedDateTime updatedAt,
            @JsonProperty("author_association") String authorAssociation)
    {
        this.id = id;
        this.url = url;
        this.eventsUrl = eventsUrl;
        this.htmlUrl = htmlUrl;
        this.number = number;
        this.state = state;
        this.title = title;
        this.body = body;
        this.user = user;
        this.labels = labels;
        this.assignee = assignee;
        this.milestone = milestone;
        this.locked = locked;
        this.activeLockReason = activeLockReason;
        this.comments = comments;
        this.closedAt = closedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.authorAssociation = authorAssociation;
    }

    public void setOwner(String owner)
    {
        this.owner = owner;
    }

    public void setRepo(String repo)
    {
        this.repo = repo;
    }

    public List<?> toRow()
    {
        // TODO allow nulls

        BlockBuilder labelIds = BIGINT.createBlockBuilder(null, labels.size());
        BlockBuilder labelNames = VARCHAR.createBlockBuilder(null, labels.size());
        for (Label label : labels) {
            BIGINT.writeLong(labelIds, label.getId());
            VARCHAR.writeString(labelNames, label.getName());
        }

        return ImmutableList.of(
                owner,
                repo,
                id,
                number,
                state,
                title != null ? title : "",
                body != null ? body : "",
                user.getId(),
                user.getLogin(),
                labelIds.build(),
                labelNames.build(),
                assignee != null ? assignee.getId() : 0,
                assignee != null ? assignee.getLogin() : "",
                milestone != null ? milestone.getId() : 0,
                milestone != null ? milestone.getTitle() : "",
                locked,
                activeLockReason != null ? activeLockReason : "",
                comments,
                packTimestamp(closedAt),
                packTimestamp(createdAt),
                packTimestamp(updatedAt),
                authorAssociation);
    }

    @Override
    public void writeTo(BlockBuilder rowBuilder)
    {
        // TODO this should be a map of column names to value getters and types should be fetched from GithubRest.columns
        writeString(rowBuilder, owner);
        writeString(rowBuilder, repo);
        BIGINT.writeLong(rowBuilder, id);
        BIGINT.writeLong(rowBuilder, number);
        writeString(rowBuilder, state);
        writeString(rowBuilder, title);
        writeString(rowBuilder, body);
        BIGINT.writeLong(rowBuilder, user.getId());
        writeString(rowBuilder, user.getLogin());

        if (labels == null) {
            rowBuilder.appendNull();
            rowBuilder.appendNull();
        }
        else {
            // labels array
            BlockBuilder labelIds = BIGINT.createBlockBuilder(null, labels.size());
            for (Label label : labels) {
                BIGINT.writeLong(labelIds, label.getId());
            }
            rowBuilder.appendStructure(labelIds.build());

            BlockBuilder labelNames = VARCHAR.createBlockBuilder(null, labels.size());
            for (Label label : labels) {
                writeString(labelNames, label.getName());
            }
            rowBuilder.appendStructure(labelNames.build());
        }

        if (assignee == null) {
            rowBuilder.appendNull();
            rowBuilder.appendNull();
        }
        else {
            BIGINT.writeLong(rowBuilder, assignee.getId());
            writeString(rowBuilder, assignee.getLogin());
        }
        if (milestone == null) {
            rowBuilder.appendNull();
            rowBuilder.appendNull();
        }
        else {
            BIGINT.writeLong(rowBuilder, milestone.getId());
            writeString(rowBuilder, milestone.getTitle());
        }
        BOOLEAN.writeBoolean(rowBuilder, locked);
        writeString(rowBuilder, activeLockReason);
        BIGINT.writeLong(rowBuilder, comments);
        writeTimestamp(rowBuilder, closedAt);
        writeTimestamp(rowBuilder, createdAt);
        writeTimestamp(rowBuilder, updatedAt);
        writeString(rowBuilder, authorAssociation);
    }

    private static final Map<String, FilterType> supportedColumnFilters = ImmutableMap.of(
            "owner", FilterType.EQUAL,
            "repo", FilterType.EQUAL,
            "updated_at", FilterType.GREATER_THAN_EQUAL);

    private enum FilterType
    {
        EQUAL, GREATER_THAN_EQUAL,
    }

    public static Optional<ConstraintApplicationResult<ConnectorTableHandle>> applyFilter(RestTableHandle table, Map<String, ColumnHandle> columns, TupleDomain<ColumnHandle> constraint)
    {
        // the only reason not to use isNone is so the linter doesn't complain about not checking an Optional
        if (constraint.isAll() || constraint.getDomains().isEmpty()) {
            return Optional.empty();
        }

        TupleDomain<ColumnHandle> currentConstraint = table.getConstraint();

        boolean found = false;
        for (String columnName : supportedColumnFilters.keySet()) {
            ColumnHandle column = columns.get(columnName);

            TupleDomain<ColumnHandle> newConstraint = normalizeConstraint((RestColumnHandle) column, constraint);
            if (newConstraint == null || newConstraint.getDomains().isEmpty()) {
                continue;
            }
            if (!validateConstraint((RestColumnHandle) column, currentConstraint, newConstraint)) {
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
            } else {
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

    private static TupleDomain<ColumnHandle> normalizeConstraint(RestColumnHandle column, TupleDomain<ColumnHandle> constraint)
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
        FilterType supportedFilter = supportedColumnFilters.get(column.getName());
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

    private static boolean validateConstraint(RestColumnHandle column, TupleDomain<ColumnHandle> currentConstraint, TupleDomain<ColumnHandle> newConstraint)
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
        FilterType supportedFilter = supportedColumnFilters.get(column.getName());
        if (supportedFilter == FilterType.EQUAL) {
            // can push down only the first predicate against this column
            throw new IllegalStateException("Already pushed down a predicate for " + column.getName() + " which only supports a single value");
        }
        // don't need to check for GREATER_THAN_EQUAL since there can only be a single low-bound range, so union would work
        return true;
    }

    public static String getFilter(RestColumnHandle column, TupleDomain<ColumnHandle> constraint)
    {
        Domain domain = null;
        if (constraint.getDomains().isPresent()) {
            domain = constraint.getDomains().get().get(column);
        }
        if ("updated_at".equals(column.getName())) {
            if (domain == null) {
                return "1970-01-01T00:00:00Z";
            }
            long since = (long) domain.getValues().getRanges().getSpan().getLowBoundedValue();
            return ISO_LOCAL_DATE_TIME.format(fromTrinoTimestamp(since)) + "Z";
        }
        if (domain == null) {
            throw new IllegalArgumentException("Missing required constraint for " + column.getName());
        }
        return ((Slice) domain.getSingleValue()).toStringUtf8();
    }
}
