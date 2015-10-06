/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.zhy_horizontalscrollview;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private Thread serviceDiscoveryThread = null;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private ArrayList<BluetoothGatt> connectionQueue=new ArrayList<BluetoothGatt>();
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private int mConnectionState = STATE_DISCONNECTED;


    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;


    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";




    public final static UUID UUID_PWM_brightness_level =
            UUID.fromString(SampleGattAttributes.PWM_brightness_level);
    public final static UUID UUID_CRIO_LIGHT_DEVICE =
            UUID.fromString(SampleGattAttributes.CRIO_LIGHT_DEVICE);
    public static int bortype;
    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    intentAction = ACTION_GATT_CONNECTED;
                    mConnectionState = STATE_CONNECTED;
                    broadcastUpdate(intentAction);
                    Log.i(TAG, "Connected to GATT server.");

                    // Attempts to discover services after successful connection.
                    Log.i(TAG, "Attempting to start service discovery:" +mBluetoothGatt.discoverServices());
                    }
                 else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    mBluetoothGatt.close();
                    intentAction = ACTION_GATT_DISCONNECTED;
                    mConnectionState = STATE_DISCONNECTED;
                    listClose(gatt);
                    Log.i(TAG, "Disconnected from GATT server.");
//                    broadcastUpdate(intentAction);

                }

            }
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                findService(gatt);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                bortype=characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16,1);
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }


        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void onCharacteristicWrite (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status){

        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {



            }
        }


    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }
    private void broadcastUpdate(final String action, final String strAddress) {
        final Intent intent = new Intent(action);
        intent.putExtra("DEVICE_ADDRESS", strAddress);
        sendBroadcast(intent);
    }
    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);



            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }

        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.
        // In this particular example, close() is invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    private boolean checkGatt(BluetoothGatt bluetoothGatt) {
        if (!connectionQueue.isEmpty()) {
            for(BluetoothGatt btg:connectionQueue){
                if(btg.equals(bluetoothGatt)){
                    return false;
                }
            }
        }
        return true;
    }

    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }



public Boolean connect(final String address) {
    if (mBluetoothAdapter == null || address == null) {
        Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
        return false;
    }
//    if(mBluetoothGatt!=null){mBluetoothGatt.disconnect();}
//     以前連接的設備。嘗試重新連接。
//    if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
//            && mBluetoothGatt != null) {
//        if(mBluetoothGatt!=null){mBluetoothGatt.close();}
//        Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
//        if (mBluetoothGatt.connect()) {
//            mConnectionState = STATE_CONNECTING;
//            if(!connectionQueue.contains(mBluetoothGatt))
//            {
//                connectionQueue.add(mBluetoothGatt);}
//            return true;
//        } else {
//            return false;
//        }
//    }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        //我們希望直接連接到設備，所以我們設置了自動連接
        //參數設置為false。
//        if(mBluetoothGatt!=null){mBluetoothGatt.close();}
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        if(checkGatt(mBluetoothGatt))
        {
            connectionQueue.add(mBluetoothGatt);

        }
        return true;
    }


    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        for(BluetoothGatt ga : connectionQueue)
        {ga.disconnect();}
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (connectionQueue.isEmpty()) {
            return;
        }
        listClose(null);
    }

    private synchronized void listClose(BluetoothGatt gatt) {
        if (!connectionQueue.isEmpty()) {
            if (gatt != null) {
                for(final BluetoothGatt bluetoothGatt:connectionQueue){
                    if(bluetoothGatt.equals(gatt)){
                        bluetoothGatt.close();

                        new Thread(new Runnable() {
                            public void run() {
                                try {
                                    Thread.sleep(250);
                                    connectionQueue.remove(bluetoothGatt);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }).start();
                    }
                }
            }else{
                for (BluetoothGatt bluetoothGatt : connectionQueue) {
                    bluetoothGatt.close();
                }
                connectionQueue.clear();
            }
        }
    }
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    public void writeCharacteristic(BluetoothGattCharacteristic mCharacteristic,byte[] value) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mCharacteristic.setValue(value);
        mBluetoothGatt.writeCharacteristic(mCharacteristic);
    }

//    public BluetoothGatt getGatt(BluetoothDevice device)
//    {
//        for(BluetoothGatt gat:connectionQueue)
//        {
//            if(gat.getDevice().equals(device))
//            {
//                mBluetoothGatt=gat;
//                return gat;
//            }
//        }
//        return null;
//    }
//    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
//                                                  boolean enabled) {
//        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
//            Log.w(TAG, "BluetoothAdapter not initialized");
//            return;
//        }
//        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
//
//
//
//
//    }
//    public List<BluetoothDevice> getConnectedGattDevices() {
//        if (mBluetoothGatt == null) return null;
//
//        return mBluetoothGatt.getConnectedDevices();
//    }

    public BluetoothGattCharacteristic getCurioCharacteristic(BluetoothDevice device){

                for(BluetoothGatt gat:connectionQueue){

                    if(gat.getDevice().equals(device)){
                        mBluetoothGatt=gat;
                        findService1(gat);
                        return mNotifyCharacteristic;
                    }

                }
        return null;
    }


//    public List<BluetoothGattService> getSupportedGattServices() {
//        if (mBluetoothGatt == null) return null;
//
//        return mBluetoothGatt.getServices();
//    }
//
//    public void getrssi(){
//        mBluetoothGatt.readRemoteRssi();
//    }
//
//    public BluetoothGattService getGattserver(BluetoothGatt gatt, UUID uuid){
////       if (mBluetoothGatt == null) return null;
//
//      return gatt.getService(uuid);
//
//    }

    public void findService(BluetoothGatt gatt)
    {
        List<BluetoothGattService> gattServices = gatt.getServices();
        Log.i(TAG, "Count is:" + gattServices.size());
        for (BluetoothGattService gattService : gattServices)
        {
            Log.i(TAG, gattService.getUuid().toString());
            Log.i(TAG, UUID_CRIO_LIGHT_DEVICE.toString());
            if(gattService.getUuid().equals(UUID_CRIO_LIGHT_DEVICE))
            {
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();
                Log.i(TAG, "Count is:" + gattCharacteristics.size());
                for (BluetoothGattCharacteristic gattCharacteristic :
                        gattCharacteristics)
                {
                    if(gattCharacteristic.getUuid().equals(UUID_PWM_brightness_level))
                    {
                        Log.i(TAG, gattCharacteristic.getUuid().toString());
                        Log.i(TAG, UUID_PWM_brightness_level.toString());
                        broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED,gatt.getDevice().getAddress());
                        return;
                    }
                }
            }
        }
    }
    public void findService1(BluetoothGatt gatt)
    {
        List<BluetoothGattService> gattServices = gatt.getServices();
        Log.i(TAG, "Count is:" + gattServices.size());
        for (BluetoothGattService gattService : gattServices)
        {
            Log.i(TAG, gattService.getUuid().toString());
            Log.i(TAG, UUID_CRIO_LIGHT_DEVICE.toString());
            if(gattService.getUuid().equals(UUID_CRIO_LIGHT_DEVICE))
            {
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();
                Log.i(TAG, "Count is:" + gattCharacteristics.size());
                for (BluetoothGattCharacteristic gattCharacteristic :
                        gattCharacteristics)
                {
                    if(gattCharacteristic.getUuid().equals(UUID_PWM_brightness_level))
                    {
                        Log.i(TAG, gattCharacteristic.getUuid().toString());
                        Log.i(TAG, UUID_PWM_brightness_level.toString());
                        mNotifyCharacteristic = gattCharacteristic;
                        return;
                    }
                }
            }
        }
    }

}
