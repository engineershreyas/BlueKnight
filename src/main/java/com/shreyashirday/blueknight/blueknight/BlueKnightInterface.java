package com.shreyashirday.blueknight.blueknight;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import java.util.List;

/**
 * Created by shreyashirday on 3/18/16.
 */
public interface BlueKnightInterface {

    //called when device is found after scanning
    void deviceFound(BluetoothDevice device, int rssi, byte[] data);

    //called when a write is complete, "success" indicates if the write succeeded or not
    void writeStatus(String deviceName,int status, BluetoothGattCharacteristic characteristic, boolean success);

    //called when a characteristic on the peripheral devices gatt sends a notification
    void notificationReceived(BluetoothGattCharacteristic characteristic);

    //called when value is fetched after a notification and it contains data in different forms
    void notificationValue(byte[] rawValue, int intValue, String strValue, BluetoothGattCharacteristic characteristic, BluetoothDevice device, String timeStampe);

    //list of all characteristics belonging to a service
    void characteristicsForService(BluetoothDevice bluetoothDevice,List<BluetoothGattCharacteristic> characteristicList, BluetoothGattService service);

    //list of all services belonging to a device
    void servicesForDevice(BluetoothDevice bluetoothDevice, List<BluetoothGattService> services);

    //called when the rssi value is broadcasted from peripheral
    void newRssiValueReceived(BluetoothDevice device, int rssi);

    //called when device is disconnected or connected, state can be determined by looking at "connected"
    void connectionStatus(BluetoothDevice device, boolean connected);

    //called when notifications are enabled/disabled on characteristic, "success" indicates if set succeeded and "descriptor" indicates if a descriptor was found
    void notificationSet(BluetoothGattCharacteristic characteristic, boolean success, boolean descriptor);

}
