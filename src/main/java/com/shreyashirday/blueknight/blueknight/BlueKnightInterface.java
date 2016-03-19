package com.shreyashirday.blueknight.blueknight;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;

/**
 * Created by shreyashirday on 3/18/16.
 */
public interface BlueKnightInterface {


    void deviceFound(BluetoothDevice device, int rssi, byte[] data);

    void writeStatus(String deviceName,int status, BluetoothGattCharacteristic characteristic, boolean success);




}
