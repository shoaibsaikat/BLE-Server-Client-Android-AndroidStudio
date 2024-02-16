package com.shoaibsaikat.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.UUID;

public class ServerActivity extends AppCompatActivity {
    private Button mBtnStopAdv;
    private Button mBtnAdv;
    private EditText mEtInput;
    private TextView mTvServer;
    
    private BluetoothGattServer mGattServer;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private BluetoothDevice mConnectedDevice;
    
    private boolean mIsAdvertising = false;
    private boolean mIsDeviceSet = false;
    
    private ArrayList<BluetoothGattService> mAdvertisingServices;
    private ArrayList<ParcelUuid> mServiceUuids;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);
        
        mBluetoothManager = (BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        mAdvertisingServices = new ArrayList<>();
        mServiceUuids = new ArrayList<>();

        mBtnAdv = findViewById(R.id.buttonAdvStart);
        mBtnStopAdv = findViewById(R.id.buttonAdvStop);
        mTvServer = findViewById(R.id.textViewServer);
        mEtInput = findViewById(R.id.editTextInputServer);

        //adding service and characteristics
        BluetoothGattService gattService = new BluetoothGattService(UUID.fromString(BluetoothUtility.SERVICE_UUID_1), BluetoothGattService.SERVICE_TYPE_PRIMARY);
        BluetoothGattCharacteristic gattServiceChar = new BluetoothGattCharacteristic(
                UUID.fromString(BluetoothUtility.CHAR_UUID_1),
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE
        );
        gattService.addCharacteristic(gattServiceChar);
        
        mAdvertisingServices.add(gattService);
        mServiceUuids.add(new ParcelUuid(gattService.getUuid()));
    }

    @Override
    protected void onDestroy() {
        if (mAdvertisingServices != null) {
    		mAdvertisingServices.clear();
    		mAdvertisingServices = null;
    	}
        if (mServiceUuids != null) {
    		mServiceUuids.clear();
    		mServiceUuids = null;
    	}
    	stopAdvertise();
        super.onDestroy();
    }

    public void handleStartClick(View view) {
        startAdvertise();
        mBtnAdv.setEnabled(false);
        mBtnStopAdv.setEnabled(true);
    }

    public void handleStopClick(View view) {
        stopAdvertise();
        mBtnAdv.setEnabled(true);
        mBtnStopAdv.setEnabled(false);
    }
    
    public void handleSendClick(View view) {
    	if (mIsDeviceSet && writeCharacteristicToGatt(mEtInput.getText().toString())) {
    		Toast.makeText(ServerActivity.this, "Data written", Toast.LENGTH_SHORT).show();
            Log.d(BluetoothUtility.TAG, "Data written from server");
    	} else {
    		Toast.makeText(ServerActivity.this, "Data not written", Toast.LENGTH_SHORT).show();
            Log.d(BluetoothUtility.TAG, "Data not written");
    	}
    }

    //Check if bluetooth is enabled, if not, then request enable
    private void enableBluetooth() {
        if (mBluetoothAdapter == null) {
            Log.d(BluetoothUtility.TAG, "Bluetooth NOT supported");
        } else if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }
    }
    
    private void startGattServer() {
        if (mGattServer == null)
            return;
        mGattServer = mBluetoothManager.openGattServer(getApplicationContext(), gattServerCallback);
        for (int i = 0; i < mAdvertisingServices.size(); i++) {
            mGattServer.addService(mAdvertisingServices.get(i));
            Log.d(BluetoothUtility.TAG, "uuid" + mAdvertisingServices.get(i).getUuid());
        }
    }
    
    //Public method to begin advertising services
    public void startAdvertise() {
        if (mIsAdvertising)
            return;
        enableBluetooth();
        startGattServer();

        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();

        dataBuilder.setIncludeTxPowerLevel(false); //necessity to fit in 31 byte advertisement
        dataBuilder.setIncludeDeviceName(true);
        for (ParcelUuid serviceUuid : mServiceUuids)
        	dataBuilder.addServiceUuid(serviceUuid);

        settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
        settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);

        mBluetoothLeAdvertiser.startAdvertising(settingsBuilder.build(), dataBuilder.build(), advertiseCallback);
        mIsAdvertising = true;
    }

    //Stop ble advertising and clean up
    public void stopAdvertise() {
        if (!mIsAdvertising)
            return;
        mBluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
        if (mGattServer != null) {
            mGattServer.clearServices();
            mGattServer.close();
        }
        if (mAdvertisingServices != null)
            mAdvertisingServices.clear();
        mIsAdvertising = false;
    }
    
    public boolean writeCharacteristicToGatt(String data) {
    	final BluetoothGattService service = mGattServer.getService(UUID.fromString(BluetoothUtility.SERVICE_UUID_1));
    	final BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(BluetoothUtility.CHAR_UUID_1));

        if (mConnectedDevice != null && characteristic.setValue(data)) {
    		mGattServer.notifyCharacteristicChanged(mConnectedDevice, characteristic, true);
    		return true;
    	} else {
            return false;
        }
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
    	@Override
        public void onStartSuccess(AdvertiseSettings advertiseSettings) {
            String successMsg = "Advertisement command attempt successful";
            Log.d(BluetoothUtility.TAG, successMsg);
        }

    	@Override
        public void onStartFailure(int i) {
            String failMsg = "Advertisement command attempt failed: " + i;
            Log.e(BluetoothUtility.TAG, failMsg);
        }
    };

    public BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            Log.d(BluetoothUtility.TAG, "onConnectionStateChange status=" + status + "->" + newState);
            mConnectedDevice = device;
            mIsDeviceSet = true;
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            Log.d(BluetoothUtility.TAG, "service added: " + status);
        }

        @Override
        public void onCharacteristicReadRequest(
        		BluetoothDevice device,
        		int requestId,
        		int offset,
        		BluetoothGattCharacteristic characteristic
        ) {
            Log.d(BluetoothUtility.TAG, "onCharacteristicReadRequest requestId=" + requestId + " offset=" + offset);

            if (characteristic.getUuid().equals(UUID.fromString(BluetoothUtility.CHAR_UUID_1))) {
                characteristic.setValue("test");
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
            }
        }

        @Override
        public void onCharacteristicWriteRequest(
        		BluetoothDevice device,
        		int requestId,
        		BluetoothGattCharacteristic characteristic,
        		boolean preparedWrite,
        		boolean responseNeeded,
        		int offset,
        		byte[] value
        ) {
            if (value != null) {
                Log.d(BluetoothUtility.TAG, "Data written: " + BluetoothUtility.byteArrayToString(value));

                final String message = BluetoothUtility.byteArrayToString(value);
            	runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mTvServer.setText(message);
					}
				});
            	
            	mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            } else {
                Log.d(BluetoothUtility.TAG, "value is null");
            }
        }
    };
}
