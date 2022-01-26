/*
 * Copyright 2009 Google Inc.
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

package de.dennisguse.opentracks.stats;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.time.Duration;
import java.util.List;

import de.dennisguse.opentracks.data.models.Altitude;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.TrackPoint;

/**
 * Updater for {@link TrackStatistics}.
 * For updating track {@link TrackStatistics} as new {@link TrackPoint}s are added.
 * NOTE: Some of the locations represent pause/resume separator.
 * NOTE: Has still support for segments (at the moment unused).
 *
 * @author Sandor Dornbush
 * @author Rodrigo Damazio
 */
public class TrackStatisticsUpdater {

    /**
     * The number of altitude readings to smooth to get a somewhat accurate signal.
     */
    @VisibleForTesting
    private static final int ALTITUDE_SMOOTHING_FACTOR = 25;

    private static final String TAG = TrackStatisticsUpdater.class.getSimpleName();
    /**
     * Ignore any acceleration faster than this.
     * Will ignore any speeds that imply acceleration greater than 2g's
     * 2g = 19.6 m/s^2 = 0.0002 m/ms^2 = 0.02 m/(m*ms)
     */
    private static final double MAX_ACCELERATION = 0.02;

    private final TrackStatistics trackStatistics;

    private final AltitudeRingBuffer altitudeBuffer;

    // The current segment's statistics
    private final TrackStatistics currentSegment;
    // Current segment's last trackPoint
    private TrackPoint lastTrackPoint;

    private Speed lastValidSpeed;

    public TrackStatisticsUpdater() {
        this(new TrackStatistics());
    }

    /**
     * Creates a new{@link TrackStatisticsUpdater} with a {@link TrackStatisticsUpdater} already existed.
     *
     * @param trackStatistics a {@link TrackStatisticsUpdater}
     */
    public TrackStatisticsUpdater(TrackStatistics trackStatistics) {
        this.trackStatistics = trackStatistics;
        this.currentSegment = new TrackStatistics();

        altitudeBuffer = new AltitudeRingBuffer(ALTITUDE_SMOOTHING_FACTOR);
    }

    public TrackStatisticsUpdater(TrackStatisticsUpdater toCopy) {
        this.currentSegment = new TrackStatistics(toCopy.currentSegment);
        this.trackStatistics = new TrackStatistics(toCopy.trackStatistics);

        this.altitudeBuffer = new AltitudeRingBuffer(toCopy.altitudeBuffer);

        this.lastTrackPoint = toCopy.lastTrackPoint;

        this.lastValidSpeed = toCopy.lastValidSpeed;
    }

    public TrackStatistics getTrackStatistics() {
        // Take a snapshot - we don't want anyone messing with our trackStatistics
        TrackStatistics stats = new TrackStatistics(trackStatistics);
        stats.merge(currentSegment);
        return stats;
    }

    public void addTrackPoints(List<TrackPoint> trackPoints) {
        trackPoints.stream().forEachOrdered(this::addTrackPoint);
    }

    public void addTrackPoint(TrackPoint trackPoint) {
        if (trackPoint.isSegmentStart()) {
            reset(trackPoint);
        }

        if (!currentSegment.isInitialized()) {
            currentSegment.setStartTime(trackPoint.getTime());
        }

        // Always update time
        currentSegment.setStopTime(trackPoint.getTime());
        currentSegment.setTotalTime(Duration.between(currentSegment.getStartTime(), trackPoint.getTime()));

        // Process sensor data: barometer
        if (trackPoint.hasAltitudeGain()) {
            currentSegment.addTotalAltitudeGain(trackPoint.getAltitudeGain());
        }

        if (trackPoint.hasAltitudeLoss()) {
            currentSegment.addTotalAltitudeLoss(trackPoint.getAltitudeLoss());
        }

        //Update absolute (GPS-based) altitude
        if (trackPoint.hasAltitude()) {
            // Update altitude using the smoothed average
            altitudeBuffer.setNext(trackPoint.getAltitude());
            Altitude newAverage = altitudeBuffer.getAverage();

            currentSegment.updateAltitudeExtremities(newAverage);
        }

        // Update total distance
        if (trackPoint.hasSensorDistance()) {
            // Sensor-based distance/speed
            currentSegment.addTotalDistance(trackPoint.getSensorDistance());
        } else if (lastTrackPoint != null
                && lastTrackPoint.hasLocation()
                && trackPoint.hasLocation() && trackPoint.isMoving()) {
            // GPS-based distance/speed
            // Assumption: we ignore TrackPoints that are not moving as those are likely imprecise GPS measurements
            Distance movingDistance = trackPoint.distanceToPrevious(lastTrackPoint);
            currentSegment.addTotalDistance(movingDistance);
        }


        // Update moving time
        if (trackPoint.isMoving() && lastTrackPoint != null && lastTrackPoint.isMoving()) {
            currentSegment.addMovingTime(trackPoint, lastTrackPoint);

            // Update max speed
            updateSpeed(trackPoint, lastTrackPoint);
        }


        if (trackPoint.isSegmentEnd()) {
            reset(trackPoint);
            return;
        }

        lastTrackPoint = trackPoint;
    }

    private void reset(TrackPoint trackPoint) {
        if (currentSegment.isInitialized()) {
            trackStatistics.merge(currentSegment);
        }
        currentSegment.reset(trackPoint.getTime());

        lastTrackPoint = null;
        altitudeBuffer.reset();
        lastValidSpeed = null;
    }

    /**
     * Gets the smoothed altitude over several readings.
     * The altitude readings is noisy so the smoothed altitude is better than the raw altitude for many tasks.
     */
    public Altitude getSmoothedAltitude() {
        return altitudeBuffer.getAverage();
    }

    /**
     * Updates a speed reading while assuming the user is moving.
     */
    @VisibleForTesting
    private void updateSpeed(@NonNull TrackPoint trackPoint, @NonNull TrackPoint lastTrackPoint) {
        /*Log.e("probando", "TrackStatisticsUpdater.updateSpeed---------------------------");
        Log.e("probando", "\t\tVelocidad del trackPoint: " + trackPoint.getSpeed());*/
        if (isValidSpeed(trackPoint, lastTrackPoint)) {
            lastValidSpeed = trackPoint.getSpeed();
            Log.e("probando", "\t\tla velocidad es válida");
            Log.e("probando", "\t\tla velocidad máxima hata ahora del segmento: " + Math.round(currentSegment.getMaxSpeed().toKMH() * Math.pow(10, 2)) / Math.pow(10, 2));
            if (trackPoint.getSpeed().greaterThan(currentSegment.getMaxSpeed())) {
                Log.e("probando", "\t\tactualizamos la velocidad máxima del segmento por " + Math.round(trackPoint.getSpeed().toKMH() * Math.pow(10, 2)) / Math.pow(10, 2));
                currentSegment.setMaxSpeed(trackPoint.getSpeed());
                Log.e("probando", "\t\tactualizada como se ve: " + Math.round(currentSegment.getMaxSpeed().toKMH() * Math.pow(10, 2)) / Math.pow(10, 2));
            }
        } else {
            //Log.e("probando", "\tvelocidad inválida: " + trackPoint.getSpeed() + " lastLocationSpeed: " + lastTrackPoint.getSpeed());
            Log.d(TAG, "Invalid speed. speed: " + trackPoint.getSpeed() + " lastLocationSpeed: " + lastTrackPoint.getSpeed());
        }
    }

    private boolean isValidSpeed(@NonNull TrackPoint trackPoint, @NonNull TrackPoint lastTrackPoint) {
        Log.e("probando", "isValidSpeed-------------------------------------------------");
        // There are a lot of noisy speed readings. Do the cheapest checks first, most expensive last.
        if (trackPoint.getSpeed().isZero()) {
            return false;
        }

        // See if the speed seems physically likely. Ignore any speeds that imply acceleration greater than 2g.
        Duration timeDifference = Duration.between(lastTrackPoint.getTime(), trackPoint.getTime());
        Speed maxAcceleration = Speed.of(MAX_ACCELERATION * timeDifference.getSeconds());
        Speed speedDifference = Speed.absDiff(lastTrackPoint.getSpeed(), trackPoint.getSpeed());
        Speed acceleration = Speed.of(speedDifference.toMPS() / timeDifference.getSeconds());

        Log.e("probando", "\t\ttimeDifference (s): " + timeDifference);
        Log.e("probando", "\t\tmaxAcceleration (km/h): " + (Math.round(maxAcceleration.toKMH() * Math.pow(10, 2)) / Math.pow(10, 2)));
        Log.e("probando", "\t\tacceleration (km/h): " + (Math.round(acceleration.toKMH() * Math.pow(10, 2)) / Math.pow(10, 2)));
        Log.e("probando", "\t\tspeedDiference (km/h): " + (Math.round(speedDifference.toKMH() * Math.pow(10, 2)) / Math.pow(10, 2)));
        Log.e("probando", "\t\t\tlast speed: " + (Math.round(lastTrackPoint.getSpeed().toKMH() * Math.pow(10, 2)) / Math.pow(10, 2)));
        Log.e("probando", "\t\t\tcurrent speed: " + (Math.round(trackPoint.getSpeed().toKMH() * Math.pow(10, 2)) / Math.pow(10, 2)));
        Log.e("probando", "\t\tisValid? " + (!speedDifference.greaterThan(maxAcceleration)));

        return !acceleration.greaterThan(maxAcceleration);
    }

    public Speed getSmoothedSpeed() {
        return lastValidSpeed;
    }

    @NonNull
    @Override
    public String toString() {
        return "TrackStatisticsUpdater{" +
                "trackStatistics=" + trackStatistics +
                '}';
    }
}
