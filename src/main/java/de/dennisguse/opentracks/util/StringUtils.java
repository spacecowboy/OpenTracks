/*
 * Copyright 2008 Google Inc.
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

import static java.time.temporal.ChronoUnit.DAYS;

import android.content.Context;
import android.location.Location;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAccessor;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.data.models.Speed;

/**
 * Various string manipulation methods.
 *
 * @author Sandor Dornbush
 * @author Rodrigo Damazio
 */
public class StringUtils {

    private static final String TAG = StringUtils.class.getSimpleName();

    private StringUtils() {
    }

    /**
     * Formats the date and time of the OffsetDateTime (using default Locale format)
     */
    public static String formatDateTime(OffsetDateTime odt) {
        return odt.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM));
    }

    /**
     * Formats the date and time with the offset (using default Locale format).
     */
    public static String formatDateTimeWithOffset(OffsetDateTime odt) {
        return odt.toZonedDateTime().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL));
    }

    public static String formatLocalDateTime(LocalDateTime localDateTime) {
        return localDateTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL));
    }

    /**
     * Formats the date relative to today date.
     */
    public static String formatDateTodayRelative(Context context, OffsetDateTime odt) {
        LocalDate today = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()).toLocalDate();
        LocalDate ld = odt.toLocalDate();
        long daysBetween = DAYS.between(ld, today);

        if (daysBetween == 0) {
            // Today
            return context.getString(R.string.generic_today);
        } else if (daysBetween == 1) {
            // Yesterday
            return context.getString(R.string.generic_yesterday);
        } else if (daysBetween < 7) {
            // Name of the week day
            return ld.format(DateTimeFormatter.ofPattern("EEEE"));
        } else if (today.getYear() == ld.getYear()) {
            // Short date without year
            return ld.format(DateTimeFormatter.ofPattern("d MMM"));
        } else {
            // Short date with year
            return ld.format(DateTimeFormatter.ofPattern("d MMM y"));
        }
    }

    /**
     * Formats the time using the ISO 8601 date time format with fractional seconds.
     */
    public static String formatDateTimeIso8601(@NonNull Instant time, ZoneOffset zoneOffset) {
        return time
                .atOffset(zoneOffset)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * Formats the elapsed timed in the form "MM:SS" or "H:MM:SS".
     */
    public static String formatElapsedTime(@NonNull Duration time) {
        return DateUtils.formatElapsedTime(time.getSeconds());
    }

    /**
     * Formats the elapsed time in the form "H:MM:SS".
     */
    public static String formatElapsedTimeWithHour(@NonNull Duration time) {
        String value = formatElapsedTime(time);
        return TextUtils.split(value, ":").length == 2 ? "0:" + value : value;
    }

    /**
     * Formats the distance in meters.
     *
     * @param context     the context
     * @param distance    the distance
     * @param metricUnits true to use metric units. False to use imperial units
     */
    public static String formatDistance(Context context, Distance distance, boolean metricUnits) {
        if (distance.isInvalid()) {
            return context.getString(R.string.value_unknown);
        }

        Pair<String, String> distanceParts = getDistanceParts(context, distance, metricUnits);

        return context.getString(R.string.distance_with_unit, distanceParts.first, distanceParts.second);
    }

    public static String formatSpeed(Context context, Speed speed, boolean metricUnits, boolean reportSpeed) {
        Pair<String, String> distanceParts = getSpeedParts(context, speed, metricUnits, reportSpeed);

        return context.getString(R.string.speed_with_unit, distanceParts.first, distanceParts.second);
    }

    private static String formatDecimal(double value) {
        return StringUtils.formatDecimal(value, 2);
    }

    /**
     * Format a decimal number while removing trailing zeros of the decimal part (if present).
     */
    public static String formatDecimal(double value, int decimalPlaces) {
        DecimalFormat df = new DecimalFormat();
        df.setMinimumFractionDigits(decimalPlaces);
        df.setMaximumFractionDigits(decimalPlaces);
        df.setRoundingMode(RoundingMode.HALF_EVEN);
        return df.format(value);
    }

    /**
     * Formats a coordinate
     *
     * @param context    the context's object.
     * @param coordinate the coordinate
     */
    public static String formatCoordinate(Context context, double coordinate) {
        return context.getString(R.string.location_coordinate, Location.convert(coordinate, Location.FORMAT_DEGREES));
    }

    /**
     * Formats a complete coordinate (latitude, longitude)
     *
     * @param context   the context's object.
     * @param latitude  the coordinate's latitude.
     * @param longitude the coordinate's longitude.
     */
    public static String formatCoordinate(Context context, double latitude, double longitude) {
        return context.getString(R.string.location_latitude_longitude, Location.convert(latitude, Location.FORMAT_DEGREES), Location.convert(longitude, Location.FORMAT_DEGREES));
    }

    /**
     * Get the formatted distance with unit.
     *
     * @param context     the context
     * @param distance    the distance
     * @param metricUnits true to use metric unit
     * @return the formatted distance (or null) and it's unit as {@link Pair}
     */
    public static Pair<String, String> getDistanceParts(Context context, Distance distance, boolean metricUnits) {
        if (distance.isInvalid()) {
            return new Pair<>(null, context.getString(metricUnits ? R.string.unit_meter : R.string.unit_feet));
        }

        if (metricUnits) {
            if (distance.greaterThan(Distance.of(500))) {
                return new Pair<>(formatDecimal(distance.toKM()), context.getString(R.string.unit_kilometer));
            } else {
                return new Pair<>(formatDecimal(distance.toM()), context.getString(R.string.unit_meter));
            }
        } else {
            if (distance.greaterThan(Distance.ofMile(0.5))) {
                return new Pair<>(formatDecimal(distance.toMI()), context.getString(R.string.unit_mile));
            } else {
                return new Pair<>(formatDecimal(distance.toFT()), context.getString(R.string.unit_feet));
            }
        }
    }

    /**
     * Gets the formatted speed with unit.
     *
     * @param context     the context
     * @param speed       the speed
     * @param metricUnits true to use metric unit
     * @param reportSpeed true to report speed; false for pace
     * @return the formatted speed (or null) and it's unit as {@link Pair}
     */
    public static Pair<String, String> getSpeedParts(Context context, Speed speed, boolean metricUnits, boolean reportSpeed) {
        int unitId;
        if (metricUnits) {
            unitId = reportSpeed ? R.string.unit_kilometer_per_hour : R.string.unit_minute_per_kilometer;
        } else {
            unitId = reportSpeed ? R.string.unit_mile_per_hour : R.string.unit_minute_per_mile;
        }
        String unitString = context.getString(unitId);

        if (speed == null) {
            speed = Speed.zero();
        }

        if (reportSpeed) {
            return new Pair<>(StringUtils.formatDecimal(speed.to(metricUnits), 1), unitString);
        }

        int pace = (int) speed.toPace(metricUnits).getSeconds();

        int minutes = pace / 60;
        int seconds = pace % 60;
        return new Pair<>(context.getString(R.string.time, minutes, seconds), unitString);
    }

    public static Pair<String, String> getHeartRateParts(Context context, HeartRate heartrate) {
        String value = context.getString(R.string.value_unknown);
        if (heartrate != null) {
            value = StringUtils.formatDecimal(heartrate.getBPM(), 0);
        }

        return new Pair<>(value, context.getString(R.string.sensor_unit_beats_per_minute));
    }

    public static Pair<String, String> getCadenceParts(Context context, Cadence cadence) {
        String value = context.getString(R.string.value_unknown);
        if (cadence != null) {
            value = StringUtils.formatDecimal(cadence.getRPM(), 0);
        }

        return new Pair<>(value, context.getString(R.string.sensor_unit_rounds_per_minute));
    }

    public static Pair<String, String> getPowerParts(Context context, Power power) {
        String value = context.getString(R.string.value_unknown);
        if (power != null) {
            value = StringUtils.formatDecimal(power.getW(), 0);
        }

        return new Pair<>(value, context.getString(R.string.sensor_unit_power));
    }

    /**
     * Gets a string for category.
     *
     * @param category the category
     */
    public static String getCategory(String category) {
        if (category == null || category.length() == 0) {
            return null;
        }
        return "[" + category + "]";
    }

    /**
     * Gets a string for category and description.
     *
     * @param category    the category
     * @param description the description
     */
    public static String getCategoryDescription(String category, String description) {
        if (category == null || category.length() == 0) {
            return description;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(getCategory(category));
        if (description != null && description.length() != 0) {
            builder.append(" ").append(description);
        }
        return builder.toString();
    }

    /**
     * Formats the given text as a XML CDATA element.
     * This includes adding the starting and ending CDATA tags.
     * NOTE: This may result in multiple consecutive CDATA tags.
     *
     * @param text the given text
     */
    public static String formatCData(String text) {
        return "<![CDATA[" + text.replaceAll("]]>", "]]]]><![CDATA[>") + "]]>";
    }

    /**
     * Gets the time, in milliseconds, from an XML date time string (ISO8601) as defined at http://www.w3.org/TR/xmlschema-2/#dateTime
     * Let's be lenient: if timezone information is not provided, UTC will be used.
     *
     * @param xmlDateTime the XML date time string
     */
    public static OffsetDateTime parseTime(String xmlDateTime) {
        try {
            TemporalAccessor t = DateTimeFormatter.ISO_DATE_TIME.parseBest(xmlDateTime, ZonedDateTime::from, LocalDateTime::from);
            if (t instanceof LocalDateTime) {
                Log.w(TAG, "Date does not contain timezone information: using UTC.");
                t = ((LocalDateTime) t).atZone(ZoneOffset.UTC);
            }
            return OffsetDateTime.from(t);
        } catch (Exception e) {
            Log.e(TAG, "Invalid XML dateTime value");
            throw e;
        }
    }

    /**
     * @return the formatted altitude_m (or null) and it's unit as {@link Pair}
     */
    public static Pair<String, String> getAltitudeParts(Context context, Float altitude_m, boolean metricUnits) {
        String formattedValue = context.getString(R.string.value_unknown);
        String unit = context.getString(metricUnits ? R.string.unit_meter : R.string.unit_feet);

        if (altitude_m != null) {
            double value = Distance.of(altitude_m).toM_FT(metricUnits);
            formattedValue = StringUtils.formatDecimal(value, 1);
        }

        return new Pair<>(formattedValue, unit);
    }

    public static String formatAltitude(Context context, Float altitude_m, boolean metricUnits) {
        Pair<String, String> altitudeParts = getAltitudeParts(context, altitude_m, metricUnits);

        return context.getString(R.string.altitude_with_unit, altitudeParts.first, altitudeParts.second);
    }

    /**
     * @return the formatted altitudeDifference_m (or null) and it's unit as {@link Pair}
     */
    public static Pair<String, String> getAltitudeChangeParts(Context context, Float altitudeDifference_m, boolean metricUnits) {
        String formattedValue = altitudeDifference_m != null ? String.valueOf((int) Distance.of(altitudeDifference_m).toM_FT(metricUnits)) : context.getString(R.string.value_unknown);
        String unit = context.getString(metricUnits ? R.string.unit_meter : R.string.unit_feet);
        return new Pair<>(formattedValue, unit);
    }

    public static String formatAltitudeChange(Context context, Float altitude_m, boolean metricUnits) {
        Pair<String, String> altitudeParts = getAltitudeChangeParts(context, altitude_m, metricUnits);

        return context.getString(R.string.altitude_with_unit, altitudeParts.first, altitudeParts.second);
    }

    public static String valueInParentheses(String text) {
        return "(" + text + ")";
    }
}
