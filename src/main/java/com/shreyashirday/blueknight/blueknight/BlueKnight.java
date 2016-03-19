package com.shreyashirday.blueknight.blueknight;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import java.util.List;
import java.util.UUID;

/**
 * Created by shreyashirday on 3/17/16.
 */
public class BlueKnight {

    private int rssiUpdateInterval = 1500;

    private Activity mParent = null;
    private boolean mConnected = false;
    private String mDeviceAddress = "";

    private BluetoothManager mBluetoothManager = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothDevice mBluetoothDevice = null;
    private BluetoothGatt mBluetoothGatt = null;
    private BluetoothGattService mBluetoothSelectedService = null;
    private List<BluetoothGattService> mBluetoothGattServices = null;
    public  BlueKnightInterface mBlueKnightInterface = null;

    private Handler mTimerHandler = new Handler();
    private boolean mTimerEnabled = false;


    public BlueKnight(Activity parent, BlueKnightInterface blueKnightInterface){
        this.mParent = parent;
        this.mBlueKnightInterface = blueKnightInterface;
    }

    public BluetoothManager getManager(){
        return mBluetoothManager;
    }

    public BluetoothAdapter getAdapter(){
        return mBluetoothAdapter;
    }

    public BluetoothDevice getBluetoothDevice(){
        return mBluetoothDevice;
    }

    public  BluetoothGatt getBluetoothGatt(){
        return mBluetoothGatt;
    }

    public BluetoothGattService getBluetoothGattService(){
        return mBluetoothSelectedService;
    }

    public List<BluetoothGattService> getBluetoothGattServices(){
        return mBluetoothGattServices;
    }



    public boolean checkBleAvailable(){


        BluetoothManager manager = (BluetoothManager)mParent.getSystemService(Context.BLUETOOTH_SERVICE);
        if(manager == null) return false;

        BluetoothAdapter adapter = manager.getAdapter();
        if(adapter == null) return false;

        boolean isBleAvailable = mParent.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);

        return isBleAvailable;
    }

    public boolean checkBluetoothEnabled(){

        if(checkBleAvailable()) {

            if (mBluetoothAdapter != null) return mBluetoothAdapter.isEnabled();

            BluetoothManager manager = (BluetoothManager) mParent.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager.getAdapter();

            return adapter.isEnabled();

        }

        return false;

    }

    public void startScanning(){

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){

           BluetoothLeScanner scanner =  mBluetoothAdapter.getBluetoothLeScanner();

            scanner.startScan(setAndGetScancallback());

        }
        else{

            mBluetoothAdapter.startLeScan(mDeviceFoundCallback);

        }



    }

    public void stopScanning(){


        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){

            BluetoothLeScanner scanner =  mBluetoothAdapter.getBluetoothLeScanner();

            scanner.stopScan(setAndGetScancallback());

        }
        else{

            mBluetoothAdapter.stopLeScan(mDeviceFoundCallback);

        }



    }


    public boolean initialize(){


        if(checkBleAvailable()) {

            mBluetoothManager = (BluetoothManager) mParent.getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = mBluetoothManager.getAdapter();

            return true;
        }

        return  false;

    }

    public boolean connect(final String macAddress){
        if (mBluetoothAdapter == null || macAddress == null) return false;
        mDeviceAddress = macAddress;


        if(mBluetoothGatt != null && mBluetoothGatt.getDevice().getAddress().equals(macAddress)) {

            return mBluetoothGatt.connect();
        }
        else {

            mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(mDeviceAddress);
            if (mBluetoothDevice == null) {

                return false;
            }

            mBluetoothGatt = mBluetoothDevice.connectGatt(mParent, true, mBleCallback);
        }
        return true;
    }

    public void disconnect(){

        if(mBluetoothGatt != null) mBluetoothGatt.disconnect();

    }

    public void close(){

        if(mBluetoothGatt != null) mBluetoothGatt.close();

    }

    public void periodicallyReadRssiValue(final boolean repeat){

        mTimerEnabled = repeat;
        // check if we should stop checking RSSI value
        if(mConnected == false || mBluetoothGatt == null || mTimerEnabled == false) {
            mTimerEnabled = false;
            return;
        }

        mTimerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(mBluetoothGatt == null ||
                        mBluetoothAdapter == null ||
                        mConnected == false)
                {
                    mTimerEnabled = false;
                    return;
                }

                // request RSSI value
                mBluetoothGatt.readRemoteRssi();
                // add call it once more in the future
                periodicallyReadRssiValue(mTimerEnabled);
            }
        }, rssiUpdateInterval);

    }

    public void startReadingRssi(){
        periodicallyReadRssiValue(true);
    }

    public void stopReadingRssi(){
        periodicallyReadRssiValue(false);
    }

    public void startServiceDiscovery(){
        if(mBluetoothGatt != null) mBluetoothGatt.discoverServices();
    }

    public void getSupportedServices(){
        if(mBluetoothGattServices != null && mBluetoothGattServices.size() > 0) mBluetoothGattServices.clear();
        // keep reference to all services in local array:
        if(mBluetoothGatt != null) mBluetoothGattServices = mBluetoothGatt.getServices();

        mBlueKnightInterface.servicesForDevice(mBluetoothDevice,mBluetoothGattServices);

    }

    public void getCharacteristicsForService(final BluetoothGattService service){

        if(service == null) return;

        List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();

        mBlueKnightInterface.characteristicsForService(mBluetoothDevice,characteristics,service);

        mBluetoothSelectedService = service;

    }

    public void requestCharacteristicValue(BluetoothGattCharacteristic characteristic){
        if (mBluetoothAdapter == null || mBluetoothGatt == null) return;

        mBluetoothGatt.readCharacteristic(characteristic);
    }

    public void getCharacteristicValue(BluetoothGattCharacteristic ch){

        byte[] rawValue = ch.getValue();
        String strValue = null;
        int intValue = 0;
        String timestamp = null;


        //do decoding here



        //ui callback
        mBlueKnightInterface.notificationValue(rawValue,intValue,strValue,ch,mBluetoothDevice,timestamp);

    }

    public int getValueFormat(BluetoothGattCharacteristic ch){
        int properties = ch.getProperties();

        if((BluetoothGattCharacteristic.FORMAT_FLOAT & properties) != 0) return BluetoothGattCharacteristic.FORMAT_FLOAT;
        if((BluetoothGattCharacteristic.FORMAT_SFLOAT & properties) != 0) return BluetoothGattCharacteristic.FORMAT_SFLOAT;
        if((BluetoothGattCharacteristic.FORMAT_SINT16 & properties) != 0) return BluetoothGattCharacteristic.FORMAT_SINT16;
        if((BluetoothGattCharacteristic.FORMAT_SINT32 & properties) != 0) return BluetoothGattCharacteristic.FORMAT_SINT32;
        if((BluetoothGattCharacteristic.FORMAT_SINT8 & properties) != 0) return BluetoothGattCharacteristic.FORMAT_SINT8;
        if((BluetoothGattCharacteristic.FORMAT_UINT16 & properties) != 0) return BluetoothGattCharacteristic.FORMAT_UINT16;
        if((BluetoothGattCharacteristic.FORMAT_UINT32 & properties) != 0) return BluetoothGattCharacteristic.FORMAT_UINT32;
        if((BluetoothGattCharacteristic.FORMAT_UINT8 & properties) != 0) return BluetoothGattCharacteristic.FORMAT_UINT8;

        return 0;
    }

    public void writeDataToCharacteristic(final BluetoothGattCharacteristic characteristic, final byte[] data){

        if (mBluetoothAdapter == null || mBluetoothGatt == null || characteristic == null) return;


        characteristic.setValue(data);

        mBluetoothGatt.writeCharacteristic(characteristic);

    }

    public void setNotificationForCharacteristic(BluetoothGattCharacteristic characteristic, boolean enabled, boolean indications){
        if (mBluetoothAdapter == null || mBluetoothGatt == null) return;

        boolean success = mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        if(!success) {
            Log.e("------", "Setting proper notification status for characteristic failed!");
        }


        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        if(descriptor != null) {
            byte[] val = enabled ? (indications ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
            descriptor.setValue(val);
            mBluetoothGatt.writeDescriptor(descriptor);
        }

        mBlueKnightInterface.notificationSet(characteristic,success,descriptor != null);

    }


    private BluetoothAdapter.LeScanCallback mDeviceFoundCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {


            mBlueKnightInterface.deviceFound(device,rssi,scanRecord);

        }
    };


    private ScanCallback scanCallback = null;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ScanCallback setAndGetScancallback(){

        if(scanCallback == null) {

            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);

                    mBlueKnightInterface.deviceFound(result.getDevice(),result.getRssi(),result.getScanRecord().getBytes());



                }

                @Override
                public void onScanFailed(int errorCode) {
                    super.onScanFailed(errorCode);

                }
            };
        }
        return scanCallback;

    }




    private final BluetoothGattCallback mBleCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnected = true;

                
                mBluetoothGatt.readRemoteRssi();



                startServiceDiscovery();


                startReadingRssi();

                mBlueKnightInterface.connectionStatus(gatt.getDevice(),true);


            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnected = false;
                mBlueKnightInterface.connectionStatus(gatt.getDevice(),true);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {

                getSupportedServices();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status)
        {

            if (status == BluetoothGatt.GATT_SUCCESS) {

                getCharacteristicValue(characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic)
        {

            getCharacteristicValue(characteristic);



            mBlueKnightInterface.notificationReceived(characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            String deviceName = gatt.getDevice().getName();


            if(status == BluetoothGatt.GATT_SUCCESS) {

                mBlueKnightInterface.writeStatus(deviceName,status,characteristic,true);

            }
            else {

                mBlueKnightInterface.writeStatus(deviceName,status,characteristic,false);

            }
        };

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS) {

                mBlueKnightInterface.newRssiValueReceived(gatt.getDevice(),rssi);
            }
        };
    };





}
