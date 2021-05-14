package pl.net.was.rest.github.filter;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class IssueCommentFilter
    implements FilterApplier
{
    public Map<String, FilterType> getSupportedFilters()
    {
        return ImmutableMap.of(
                "owner", FilterType.EQUAL,
                "repo", FilterType.EQUAL,
                "updated_at", FilterType.GREATER_THAN_EQUAL);
    }
}
