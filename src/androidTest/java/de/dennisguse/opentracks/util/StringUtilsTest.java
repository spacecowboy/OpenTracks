/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package de.dennisguse.opentracks.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Collectors;

import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Speed;

/**
 * Tests for {@link StringUtils}.
 *
 * @author Rodrigo Damazio
 */
@RunWith(AndroidJUnit4.class)
public class StringUtilsTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    /**
     * Tests {@link StringUtils#formatElapsedTime(Duration)}.
     */
    @Test
    public void testformatElapsedTime() {
        assertEquals("00:01", StringUtils.formatElapsedTime(Duration.ofMillis(1000)));
        assertEquals("00:10", StringUtils.formatElapsedTime(Duration.ofMillis(10000)));
        assertEquals("01:00", StringUtils.formatElapsedTime(Duration.ofMillis(60000)));
        assertEquals("10:00", StringUtils.formatElapsedTime(Duration.ofMillis(600000)));
        assertEquals("1:00:00", StringUtils.formatElapsedTime(Duration.ofMillis(3600000)));
        assertEquals("10:00:00", StringUtils.formatElapsedTime(Duration.ofMillis(36000000)));
        assertEquals("100:00:00", StringUtils.formatElapsedTime(Duration.ofMillis(360000000)));
    }

    /**
     * Tests {@link StringUtils#formatElapsedTimeWithHour(Duration)}.
     */
    @Test
    public void testformatElapsedTimeWithHour() {
        assertEquals("0:00:01", StringUtils.formatElapsedTimeWithHour(Duration.ofMillis(1000)));
        assertEquals("0:00:10", StringUtils.formatElapsedTimeWithHour(Duration.ofMillis(10000)));
        assertEquals("0:01:00", StringUtils.formatElapsedTimeWithHour(Duration.ofMillis(60000)));
        assertEquals("0:10:00", StringUtils.formatElapsedTimeWithHour(Duration.ofMillis(600000)));
        assertEquals("1:00:00", StringUtils.formatElapsedTimeWithHour(Duration.ofMillis(3600000)));
        assertEquals("10:00:00", StringUtils.formatElapsedTimeWithHour(Duration.ofMillis(36000000)));
        assertEquals("100:00:00", StringUtils.formatElapsedTimeWithHour(Duration.ofMillis(360000000)));
    }

    /**
     * Tests {@link StringUtils#formatDistance(android.content.Context, Distance, boolean)}.
     */
    @Test
    public void testFormatDistance() {
        // A large number in metric
        assertEquals("5.00 km", StringUtils.formatDistance(context, Distance.of(5000), true));
        // A large number in imperial
        assertEquals("3.11 mi", StringUtils.formatDistance(context, Distance.of(5000), false));
        // A small number in metric
        assertEquals("100.00 m", StringUtils.formatDistance(context, Distance.of(100), true));
        // A small number in imperial
        assertEquals("328.08 ft", StringUtils.formatDistance(context, Distance.of(100), false));
    }

    /**
     * Tests {@link StringUtils#formatCData(String)}.
     */
    @Test
    public void testFormatCData() {
        assertEquals("<![CDATA[hello]]>", StringUtils.formatCData("hello"));
        assertEquals("<![CDATA[hello]]]]><![CDATA[>there]]>", StringUtils.formatCData("hello]]>there"));
    }

    @Test
    public void testGetTime_fractional() {
        assertGetTime("2010-05-04T03:02:01.352Z", 2010, 5, 4, 3, 2, 1, 352);
        assertGetTime("2010-05-04T03:02:01.3529Z", 2010, 5, 4, 3, 2, 1, 352);
    }

    @Test
    public void testGetTime_timezone() {
        assertGetTime("2010-05-04T03:02:01", 2010, 5, 4, 3, 2, 1, 0);
        assertGetTime("2010-05-04T03:02:01Z", 2010, 5, 4, 3, 2, 1, 0);
        assertGetTime("2010-05-04T03:02:01+00:00", 2010, 5, 4, 3, 2, 1, 0);
        assertGetTime("2010-05-04T03:02:01-00:00", 2010, 5, 4, 3, 2, 1, 0);
        assertGetTime("2010-05-04T03:02:01+01:00", 2010, 5, 4, 2, 2, 1, 0);
        assertGetTime("2010-05-04T03:02:01+10:30", 2010, 5, 3, 16, 32, 1, 0);
        assertGetTime("2010-05-04T03:02:01-09:30", 2010, 5, 4, 12, 32, 1, 0);
        assertGetTime("2010-05-04T03:02:01-05:00", 2010, 5, 4, 8, 2, 1, 0);
    }

    @Test
    public void testGetTime_fractionalAndTimezone() {
        assertGetTime("2010-05-04T03:02:01.352Z", 2010, 5, 4, 3, 2, 1, 352);
        assertGetTime("2010-05-04T03:02:01.47+00:00", 2010, 5, 4, 3, 2, 1, 470);
        assertGetTime("2010-05-04T03:02:01.5791+03:00", 2010, 5, 4, 0, 2, 1, 579);
        assertGetTime("2010-05-04T03:02:01.8-05:30", 2010, 5, 4, 8, 32, 1, 800);
    }

    /**
     * Asserts the {@link StringUtils#parseTime(String)} returns the expected values.
     *
     * @param xmlDateTime the xml date time string
     * @param year        the expected year
     * @param month       the expected month
     * @param day         the expected day
     * @param hour        the expected hour
     * @param minute      the expected minute
     * @param second      the expected second
     * @param millisecond the expected milliseconds
     */
    private void assertGetTime(String xmlDateTime, int year, int month, int day, int hour, int minute, int second, int millisecond) {
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.set(year, month - 1, day, hour, minute, second);
        calendar.set(GregorianCalendar.MILLISECOND, millisecond);

        // This comparision tends to be flaky (difference of 1ms)
        // Assert.assertEquals(calendar.getTimeInMillis(), StringUtils.parseTime(xmlDateTime));
        assertTrue(calendar.getTimeInMillis() + " vs. " + StringUtils.parseTime(xmlDateTime), Math.abs(calendar.getTimeInMillis() - StringUtils.parseTime(xmlDateTime).toInstant().toEpochMilli()) <= 1);
    }

    @Test
    public void testFormatDecimal() {
        assertEquals("0", StringUtils.formatDecimal(0.0, 0));
        assertEquals("0", StringUtils.formatDecimal(0.1, 0));
        assertEquals("1", StringUtils.formatDecimal(1.1, 0));
        assertEquals("10", StringUtils.formatDecimal(10, 0));
        assertEquals("10", StringUtils.formatDecimal(10.1, 0));
        assertEquals("-0", StringUtils.formatDecimal(-0.1, 0));

        assertEquals("0.00", StringUtils.formatDecimal(0.0, 2));
        assertEquals("0.10", StringUtils.formatDecimal(0.1, 2));
        assertEquals("1.10", StringUtils.formatDecimal(1.1, 2));
        assertEquals("10.00", StringUtils.formatDecimal(10, 2));
        assertEquals("10.10", StringUtils.formatDecimal(10.1, 2));
        assertEquals("10.11", StringUtils.formatDecimal(10.111, 2));
        assertEquals("-0.10", StringUtils.formatDecimal(-0.1, 2));

        assertEquals("1.0", StringUtils.formatDecimal(0.99, 1));
    }

    @Test
    public void testGetSpeedParts() {
        assertEquals("4:59", StringUtils.getSpeedParts(context, Speed.of(3.34), true, false).first);
        assertEquals("5:00", StringUtils.getSpeedParts(context, Speed.of(3.33), true, false).first);

        assertEquals("11.9", StringUtils.getSpeedParts(context, Speed.of(3.31), true, true).first);
        assertEquals("7.5", StringUtils.getSpeedParts(context, Speed.of(3.34), false, true).first);

        assertEquals("min/km", StringUtils.getSpeedParts(context, Speed.zero(), true, false).second);
        assertEquals("min/mi", StringUtils.getSpeedParts(context, Speed.zero(), false, false).second);
    }

    @Test
    public void testFormatSpeed() {
        assertEquals("4:59 min/km", StringUtils.formatSpeed(context, Speed.of(3.34), true, false));
        assertEquals("8:02 min/mi", StringUtils.formatSpeed(context, Speed.of(3.34), false, false));

        assertEquals("12.0 km/h", StringUtils.formatSpeed(context, Speed.of(3.34), true, true));
        assertEquals("7.5 mph", StringUtils.formatSpeed(context, Speed.of(3.34), false, true));
    }

    @Test
    public void testFormatDateTodayRelative() {
        // given
        ArrayList<String> shortDays = Arrays.stream(DayOfWeek.values()).map(d -> d.getDisplayName(TextStyle.FULL, Locale.getDefault())).collect(Collectors.toCollection(ArrayList::new));
        ArrayList<String> shortMonths = Arrays.stream(Month.values()).map(m -> m.getDisplayName(TextStyle.SHORT, Locale.getDefault())).collect(Collectors.toCollection(ArrayList::new));

        LocalDate today = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()).toLocalDate();
        LocalDate yesterday = today.minus(1, ChronoUnit.DAYS);
        LocalDate dayName = today.minus(2, ChronoUnit.DAYS);
        LocalDate thisYear = today.minus(15, ChronoUnit.DAYS);
        LocalDate aYearAgo = today.minus(400, ChronoUnit.DAYS);

        int offsetFromLocale = OffsetDateTime.now().getOffset().getTotalSeconds() - 28800; // -08:00

        OffsetDateTime todayRecordedOdt = OffsetDateTime.of(LocalDateTime.of(today.getYear(), today.getMonth(), today.getDayOfMonth(), 20, 0, 0), ZoneOffset.ofTotalSeconds(offsetFromLocale));
        OffsetDateTime yesterdayRecordedOdt = OffsetDateTime.of(LocalDateTime.of(yesterday.getYear(), yesterday.getMonth(), yesterday.getDayOfMonth(), 1, 0, 0), ZoneOffset.ofTotalSeconds(offsetFromLocale));
        OffsetDateTime dayNameRecordedOdt = OffsetDateTime.of(LocalDateTime.of(dayName.getYear(), dayName.getMonth(), dayName.getDayOfMonth(), 7, 0, 0), ZoneOffset.ofTotalSeconds(offsetFromLocale));
        OffsetDateTime thisYearRecordedOdt = OffsetDateTime.of(LocalDateTime.of(thisYear.getYear(), thisYear.getMonth(), thisYear.getDayOfMonth(), 12, 0, 0), ZoneOffset.ofTotalSeconds(offsetFromLocale));
        OffsetDateTime aYearAgoRecordedOdt = OffsetDateTime.of(LocalDateTime.of(aYearAgo.getYear(), aYearAgo.getMonth(), aYearAgo.getDayOfMonth(), 17, 0, 0), ZoneOffset.ofTotalSeconds(offsetFromLocale));

        // when
        String formatToday = StringUtils.formatDateTodayRelative(context, todayRecordedOdt);
        String formatYesterday = StringUtils.formatDateTodayRelative(context, yesterdayRecordedOdt);
        String formatDayName = StringUtils.formatDateTodayRelative(context, dayNameRecordedOdt);
        String formatThisYear = StringUtils.formatDateTodayRelative(context, thisYearRecordedOdt);
        String formatAYearAgo = StringUtils.formatDateTodayRelative(context, aYearAgoRecordedOdt);

        // then
        assertEquals("Today", formatToday);
        assertEquals("Yesterday", formatYesterday);
        assertTrue(shortDays.contains(formatDayName)); // Something like Friday
        if (today.getYear() != thisYear.getYear()) {
            assertTrue(shortMonths.stream().anyMatch(fty -> formatThisYear.matches("\\d+ " + fty + " \\d{4}"))); // Something like 14 Dec 2021
        } else {
            assertTrue(shortMonths.stream().anyMatch(fty -> formatThisYear.matches("\\d+ " + fty))); // Something like 14 Dec
        }
        assertTrue(shortMonths.stream().anyMatch(fty -> formatAYearAgo.matches("\\d+ " + fty + " \\d{4}"))); // Something like 14 Dec 2021
    }
}
