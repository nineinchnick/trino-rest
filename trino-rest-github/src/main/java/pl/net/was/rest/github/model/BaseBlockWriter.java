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

import io.trino.spi.block.BlockBuilder;
import io.trino.spi.type.DateTimeEncoding;
import io.trino.spi.type.TimeZoneKey;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static io.trino.spi.type.DateTimeEncoding.unpackMillisUtc;
import static io.trino.spi.type.DateTimeEncoding.unpackZoneKey;
import static io.trino.spi.type.TimestampWithTimeZoneType.TIMESTAMP_TZ_SECONDS;
import static io.trino.spi.type.Timestamps.MILLISECONDS_PER_SECOND;
import static io.trino.spi.type.Timestamps.NANOSECONDS_PER_MILLISECOND;
import static io.trino.spi.type.Timestamps.roundDiv;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static java.lang.Math.floorDiv;
import static java.lang.Math.floorMod;

abstract class BaseBlockWriter
        implements BlockWriter
{
    public abstract void writeTo(BlockBuilder rowBuilder);

    protected static long packTimestamp(ZonedDateTime timestamp)
    {
        if (timestamp == null) {
            return 0;
        }
        return DateTimeEncoding.packDateTimeWithZone(
                timestamp.toEpochSecond() * MILLISECONDS_PER_SECOND + roundDiv(timestamp.toLocalTime().getNano(), NANOSECONDS_PER_MILLISECOND),
                timestamp.getZone().getId());
    }

    protected static void writeString(BlockBuilder rowBuilder, String value)
    {
        if (value == null) {
            rowBuilder.appendNull();
            return;
        }
        VARCHAR.writeString(rowBuilder, value);
    }

    protected static void writeTimestamp(BlockBuilder rowBuilder, ZonedDateTime value)
    {
        if (value == null) {
            rowBuilder.appendNull();
            return;
        }
        TIMESTAMP_TZ_SECONDS.writeLong(rowBuilder, packTimestamp(value));
    }

    protected static ZonedDateTime fromTrinoTimestamp(long timestampWithTimeZone)
    {
        TimeZoneKey zoneKey = unpackZoneKey(timestampWithTimeZone);
        long millis = unpackMillisUtc(timestampWithTimeZone);

        long epochSecond = floorDiv(millis, MILLISECONDS_PER_SECOND);
        int nanoFraction = floorMod(millis, MILLISECONDS_PER_SECOND) * NANOSECONDS_PER_MILLISECOND;
        Instant instant = Instant.ofEpochSecond(epochSecond, nanoFraction);
        return ZonedDateTime.ofInstant(instant, zoneKey.getZoneId()).withZoneSameInstant(ZoneId.of("UTC"));
    }
}
