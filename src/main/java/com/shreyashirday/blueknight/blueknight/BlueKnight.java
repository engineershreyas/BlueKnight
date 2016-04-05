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

import com.shreyashirday.blueknight.blueknight.utils.MessageQueue;

import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Created by shreyashirday on 3/17/16.
 */
public class BlueKnight {

    private int rssiUpdateInterval = 1500;
    private int payloadSize = 121;
    private String magic = null;
    private byte[] lastDataSent = null;

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

    private MessageQueue messageQueue;


    public BlueKnight(Activity parent, BlueKnightInterface blueKnightInterface){
        this.mParent = parent;
        this.mBlueKnightInterface = blueKnightInterface;
        messageQueue = new MessageQueue();
        initializeSequenceByteMap();
    }

    //BlueKnight specific methods and stuff

    private enum Sequences {
        FIRST_ONLY, FIRST_MORE, IN_BETWEEN, LAST
    }

    private class Result {
        byte[] payload = null;
        boolean complete = false;
        int offset = 0;
    }

    private Map<Sequences,Byte> sequencesByteMap = new EnumMap<Sequences, Byte>(Sequences.class);

    private void initializeSequenceByteMap(){

        sequencesByteMap.put(Sequences.FIRST_ONLY,(byte)1);
        sequencesByteMap.put(Sequences.FIRST_MORE,(byte)2);
        sequencesByteMap.put((Sequences.IN_BETWEEN),(byte)3);
        sequencesByteMap.put(Sequences.LAST,(byte)4);


    }

    private Result payloadPacker(byte[] data, Sequences s,int offset){

        Result result = new Result();

        int trueLength = data.length - offset;
        int arraySize;


        if(trueLength > payloadSize){

            arraySize = payloadSize;


        }
        else{

            arraySize = trueLength;

        }

        byte[] payload = new byte[arraySize + 5];
        byte[] magicBytes = magicToHex();

        for(int i = 0; i < 2; i++){

            payload[i] = magicBytes[i];

        }

        payload[2] = (byte)arraySize;

        payload[3] = sequencesByteMap.get(s);

        for(int i = 4; i < arraySize + 4; i++){
            payload[i] = data[offset];
            offset++;
        }


        payload[arraySize + 4] = (byte)(charToHex('A',0) + charToHex('A',1));

        result.payload = payload;
        result.offset = offset;
        result.complete = offset >= data.length;

        return result;

    }

    public boolean setMagic(String magic){

        //Only four hexadecimal characters!
        if(magic.length() != 4) return false;

        String patternString = "^[0-9a-fA-F]+$";

        Pattern pattern = Pattern.compile(patternString);

        //only hexadecimal characters allowed!
        if(!pattern.matcher(magic).matches()) return false;

        this.magic = magic.toUpperCase();

        return true;

    }

    public void setPayloadSize(int pSize){

        this.payloadSize = pSize;

    }

    private byte[] magicToHex(){

        if(this.magic == null) return null;


        char[] chars = this.magic.toCharArray();

        int index = 0;
        int sum = 0;
        int sumTwo = 0;

        for(char c : chars){

            String charString = Character.toString(c);

            try {

                int intValue = Integer.parseInt(charString);

                int valToAdd = (int)(intValue * Math.pow(16,index));

                if(index < 2){
                    sum += valToAdd;
                }
                else{
                    sumTwo += valToAdd;
                }


            }catch (NumberFormatException e){




                int valToAdd = charToHex(c,index);

                if(index < 2){
                    sum += valToAdd;
                }
                else{
                    sumTwo += valToAdd;
                }



            }

            index++;

        }

        byte[] returnBytes = new byte[2];

        returnBytes[0] = (byte)sum;
        returnBytes[1] = (byte)sumTwo;

        return returnBytes;

    }

    private int charToHex(char c, int index){

        char a = 'A';
        int aVal = (int)a;

        int diff = (int)c - aVal;

        int trueVal = 10 + diff;

        int valToAdd = (int)(trueVal * Math.pow(16,index));


        return valToAdd;

    }

    //End BlueKnight specific methods


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
                if (mBluetoothGatt == null ||
                        mBluetoothAdapter == null ||
                        mConnected == false) {
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

    /*

             first four bytes are header
             next X bytes are payload
             last two bytes are footer

             REMINDER: EACH CHUNK IS 20 BYTES
             TOTAL NUMBER OF CHUNKS IS (PAYLOAD_SIZE + 6)/20


     */
    public void getCharacteristicValue(BluetoothGattCharacteristic ch){


        byte[] rawValue = ch.getValue();
        String strValue;

        Date date = new Date();
        String timestamp = date.toString();

        //do decoding here

        byte[] magicBytes = magicToHex();
        if(rawValue[0] == magicBytes[0] && rawValue[1] == magicBytes[1]){

            byte sequenceByte = rawValue[2];
            byte length = rawValue[3];

            Byte[] payload = new Byte[payloadSize];

            for(int i = 4; i < payloadSize + 4; i++){

                payload[i - 4] = rawValue[i];

            }

            if(messageQueue.addMessage(payload, length)) {


                if (sequenceByte == sequencesByteMap.get(Sequences.LAST) || sequenceByte == sequencesByteMap.get(Sequences.FIRST_ONLY)) {

                    strValue = messageQueue.assembleMessage();
                    if (messageQueue.validateMessage()) {

                        messageQueue.flush();

                        //ui callback
                        mBlueKnightInterface.notificationValue(rawValue, strValue, ch, mBluetoothDevice, timestamp);

                    }

                }
            }

        }




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

        lastDataSent = data;

        Sequences s = data.length > payloadSize ? Sequences.FIRST_MORE : Sequences.FIRST_ONLY;

        Result result = payloadPacker(data,s,0);

        byte[] payload;

        while(!result.complete){

            payload = result.payload;

            characteristic.setValue(payload);

            mBluetoothGatt.writeCharacteristic(characteristic);

            s = data.length - result.offset < payloadSize ? Sequences.LAST : Sequences.IN_BETWEEN;

            result = payloadPacker(data,s,result.offset);

        }


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

                mBlueKnightInterface.writeStatus(deviceName,status,characteristic,true,lastDataSent);

            }
            else {

                mBlueKnightInterface.writeStatus(deviceName,status,characteristic,false,lastDataSent);

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
