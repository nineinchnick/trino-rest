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

@SuppressWarnings("unused")
public class Plan
{
    private final String name;
    private final int space;
    private final int privateRepos;
    private final int filledSeats;
    private final int seats;

    public Plan(
            @JsonProperty("name") String name,
            @JsonProperty("space") int space,
            @JsonProperty("private_repos") int privateRepos,
            @JsonProperty("filled_seats") int filledSeats,
            @JsonProperty("seats") int seats)
    {
        this.name = name;
        this.space = space;
        this.privateRepos = privateRepos;
        this.filledSeats = filledSeats;
        this.seats = seats;
    }

    public String getName()
    {
        return name;
    }

    public int getSpace()
    {
        return space;
    }

    public int getPrivateRepos()
    {
        return privateRepos;
    }

    public int getFilledSeats()
    {
        return filledSeats;
    }

    public int getSeats()
    {
        return seats;
    }
}
