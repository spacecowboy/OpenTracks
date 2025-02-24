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
package de.dennisguse.opentracks.sensors;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.sensors.sensorData.SensorDataCycling;
import de.dennisguse.opentracks.sensors.sensorData.SensorDataRunning;

/**
 * Utilities for dealing with bluetooth devices.
 *
 * @author Rodrigo Damazio
 */
public class BluetoothUtils {

    public static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = new UUID(0x290200001000L, 0x800000805f9b34fbL);

    public static final UUID HEART_RATE_SERVICE_UUID = new UUID(0x180D00001000L, 0x800000805f9b34fbL);
    public static final UUID HEART_RATE_MEASUREMENT_CHAR_UUID = new UUID(0x2A3700001000L, 0x800000805f9b34fbL);

    public static final List<UUID> HEART_RATE_SUPPORTING_DEVICES = Collections.unmodifiableList(Arrays.asList(
            BluetoothUtils.HEART_RATE_SERVICE_UUID,
            //Devices that support HEART_RATE_SERVICE_UUID, but do not announce HEART_RATE_SERVICE_UUID in there BLE announcement messages (during device discovery).
            UUID.fromString("0000fee0-0000-1000-8000-00805f9b34fb") //Miband3
    ));

    public static final UUID CYCLING_POWER_UUID = new UUID(0x181800001000L, 0x800000805f9b34fbL);
    public static final UUID CYCLING_POWER_MEASUREMENT_CHAR_UUID = new UUID(0x2A6300001000L, 0x800000805f9b34fbL);

    public static final UUID CYCLING_SPEED_CADENCE_SERVICE_UUID = new UUID(0x181600001000L, 0x800000805f9b34fbL);
    public static final UUID CYCLING_SPEED_CADENCE_MEASUREMENT_CHAR_UUID = new UUID(0x2A5B00001000L, 0x800000805f9b34fbL);

    public static final UUID RUNNING_RUNNING_SPEED_CADENCE_UUID = new UUID(0x181400001000L, 0x800000805f9b34fbL);
    public static final UUID RUNNING_RUNNING_SPEED_CADENCE_CHAR_UUID = new UUID(0x2A5300001000L, 0x800000805f9b34fbL);

    private static final String TAG = BluetoothUtils.class.getSimpleName();

    private BluetoothUtils() {
    }

    public static BluetoothAdapter getAdapter(Context context) {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Log.i(TAG, "BluetoothManager not available.");
            return null;
        } else {
            return bluetoothManager.getAdapter();
        }
    }

    public static boolean hasBluetooth(Context context) {
        return BluetoothUtils.getAdapter(context) != null;
    }

    public static Integer parseHeartRate(BluetoothGattCharacteristic characteristic) {
        //DOCUMENTATION https://www.bluetooth.com/wp-content/uploads/Sitecore-Media-Library/Gatt/Xml/Characteristics/org.bluetooth.characteristic.heart_rate_measurement.xml
        byte[] raw = characteristic.getValue();
        if (raw.length == 0) {
            return null;
        }

        boolean formatUINT16 = ((raw[0] & 0x1) == 1);
        if (formatUINT16 && raw.length >= 3) {
            return characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1);
        }
        if (!formatUINT16 && raw.length >= 2) {
            return characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1);
        }

        return null;
    }

    public static Integer parseCyclingPower(BluetoothGattCharacteristic characteristic) {
        // DOCUMENTATION https://www.bluetooth.com/wp-content/uploads/Sitecore-Media-Library/Gatt/Xml/Characteristics/org.bluetooth.characteristic.cycling_power_measurement.xml
        int valueLength = characteristic.getValue().length;
        if (valueLength < 4) {
            return null;
        }

        return characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 2);
    }

    public static SensorDataCycling.CadenceAndSpeed parseCyclingCrankAndWheel(String address, String sensorName, @NonNull BluetoothGattCharacteristic characteristic) {
        // DOCUMENTATION https://www.bluetooth.com/wp-content/uploads/Sitecore-Media-Library/Gatt/Xml/Characteristics/org.bluetooth.characteristic.csc_measurement.xml
        int valueLength = characteristic.getValue().length;
        if (valueLength == 0) {
            return null;
        }

        int flags = characteristic.getValue()[0];
        boolean hasWheel = (flags & 0x01) > 0;
        boolean hasCrank = (flags & 0x02) > 0;

        int index = 1;
        SensorDataCycling.DistanceSpeed speed = null;
        if (hasWheel && valueLength - index >= 6) {
            int wheelTotalRevolutionCount = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, index);
            index += 4;
            int wheelTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, index); // 1/1024s
            speed = new SensorDataCycling.DistanceSpeed(address, sensorName, wheelTotalRevolutionCount, wheelTime);
            index += 2;
        }

        SensorDataCycling.CyclingCadence cadence = null;
        if (hasCrank && valueLength - index >= 4) {
            long crankCount = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, index);
            index += 2;

            int crankTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, index); // 1/1024s
            cadence = new SensorDataCycling.CyclingCadence(address, sensorName, crankCount, crankTime);
        }

        return new SensorDataCycling.CadenceAndSpeed(address, sensorName, cadence, speed);
    }

    public static SensorDataRunning parseRunningSpeedAndCadence(String address, String sensorName, @NonNull BluetoothGattCharacteristic characteristic) {
        // DOCUMENTATION https://www.bluetooth.com/wp-content/uploads/Sitecore-Media-Library/Gatt/Xml/Characteristics/org.bluetooth.characteristic.rsc_measurement.xml
        int valueLength = characteristic.getValue().length;
        if (valueLength == 0) {
            return null;
        }

        int flags = characteristic.getValue()[0];
        boolean hasStrideLength = (flags & 0x01) > 0;
        boolean hasTotalDistance = (flags & 0x02) > 0;
        boolean hasStatus = (flags & 0x03) > 0; // walking vs running

        Speed speed = null;
        Cadence cadence = null;
        Distance totalDistance = null;

        int index = 1;
        if (valueLength - index >= 2) {
            speed = Speed.of(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, index) / 256f);
        }

        index = 3;
        if (valueLength - index >= 1) {
            cadence = Cadence.of(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, index));
        }

        index = 4;
        if (hasStrideLength && valueLength - index >= 2) {
            Distance strideDistance = Distance.ofCM(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, index));
            index += 2;
        }

        if (hasTotalDistance && valueLength - index >= 4) {
            totalDistance = Distance.ofDM(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, index));
        }

        return new SensorDataRunning(address, sensorName, speed, cadence, totalDistance);
    }
}
