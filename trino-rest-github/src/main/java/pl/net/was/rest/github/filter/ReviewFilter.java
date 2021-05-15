package pl.net.was.rest.github.filter;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class ReviewFilter
    implements FilterApplier
{
    public Map<String, FilterType> getSupportedFilters()
    {
        return ImmutableMap.of(
                "owner", FilterType.EQUAL,
                "repo", FilterType.EQUAL,
                "pull_number", FilterType.EQUAL);
    }
}
