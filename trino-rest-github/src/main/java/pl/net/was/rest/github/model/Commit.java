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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

import java.util.List;

public class Commit
{
    private final String url;
    private final Identity author;
    private final Identity committer;
    private final String message;
    private final Ref tree;
    private final long commentsCount;
    private final Verification verification;

    public Commit(
            @JsonProperty("url") String url,
            @JsonProperty("author") Identity author,
            @JsonProperty("committer") Identity committer,
            @JsonProperty("message") String message,
            @JsonProperty("tree") Ref tree,
            @JsonProperty("comments_count") long commentsCount,
            @JsonProperty("verification") Verification verification)
    {
        this.url = url;
        this.author = author;
        this.committer = committer;
        this.message = message;
        this.tree = tree;
        this.commentsCount = commentsCount;
        this.verification = verification;
    }

    public List<?> toRow()
    {
        return ImmutableList.of(
                url,
                author.getName(),
                author.getEmail(),
                committer.getName(),
                committer.getEmail(),
                message,
                tree.getUrl(),
                tree.getSha(),
                commentsCount,
                verification.getVerified(),
                verification.getReason());
    }

    public String getUrl()
    {
        return url;
    }

    public Identity getAuthor()
    {
        return author;
    }

    public Identity getCommitter()
    {
        return committer;
    }

    public String getMessage()
    {
        return message;
    }

    public Ref getTree()
    {
        return tree;
    }

    public long getCommentsCount()
    {
        return commentsCount;
    }

    public Verification getVerification()
    {
        return verification;
    }
}
