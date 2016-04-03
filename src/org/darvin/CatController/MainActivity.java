package org.darvin.CatController;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.*;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.TextView;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private boolean mWaterSwitch;
    public TextView bleStatusTextView;
    public TextView waterStatusTextView;
    private String MyPREFERENCES = "MyPREFERENCES";
    private String PREFERENCES_BLE_SWITCH_ADDRESS = "PREFERENCES_BLE_SWITCH_ADDRESS";
    private String PREFERENCES_BLE_SWITCH_NAME = "PREFERENCES_BLE_SWITCH_NAME";

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        bleStatusTextView = (TextView)findViewById(R.id.bleStatusTextView);
        waterStatusTextView = (TextView)findViewById(R.id.waterStatusTextView);
        Intent gattServiceIntent = new Intent(this, BLESwitchService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        SharedPreferences sharedpreferences = getSharedPreferences(MyPREFERENCES, Context.MODE_PRIVATE);
        mDeviceAddress = sharedpreferences.getString(PREFERENCES_BLE_SWITCH_ADDRESS, null);
        mDeviceName = sharedpreferences.getString(PREFERENCES_BLE_SWITCH_NAME, null);


    }


    static final int SCAN_BLE_DEVICES_REQUEST = 1;

    @Override
    public  void onStart() {
        super.onStart();

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SCAN_BLE_DEVICES_REQUEST) {
            if (resultCode == RESULT_OK) {
                mDeviceAddress = data.getStringExtra(DeviceScanActivity.EXTRAS_DEVICE_ADDRESS);
                mDeviceName = data.getStringExtra(DeviceScanActivity.EXTRAS_DEVICE_NAME);
                bleStatusTextView.setText("Connecting to "+mDeviceName);
                SharedPreferences sharedpreferences = getSharedPreferences(MyPREFERENCES, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putString(PREFERENCES_BLE_SWITCH_ADDRESS, mDeviceAddress);
                editor.commit();
                mBLESwitchService.connect(mDeviceAddress);

            }
        }
    }





    public void buttonTurnOnPressed(View view) {
        Log.v(TAG, "button pressed");

        waterStatusTextView.setText("Trying to turn on...");
        mWaterSwitch = !mWaterSwitch;
        mBLESwitchService.turnSwitch(0, mWaterSwitch);


    }




    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BLESwitchService mBLESwitchService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBLESwitchService = ((BLESwitchService.LocalBinder) service).getService();
            if (!mBLESwitchService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

            if (mDeviceAddress==null) {
                bleStatusTextView.setText("Not Connected");
                final Intent intent = new Intent(MainActivity.this, DeviceScanActivity.class);
                startActivityForResult(intent, SCAN_BLE_DEVICES_REQUEST);
            } else {
                mBLESwitchService.connect(mDeviceAddress);

            }

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBLESwitchService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BLESwitchService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                bleStatusTextView.setText("Connected");

                invalidateOptionsMenu();
            } else if (BLESwitchService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                bleStatusTextView.setText("Disconnected");
                invalidateOptionsMenu();
            } else if (BLESwitchService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
            } else if (BLESwitchService.ACTION_DATA_AVAILABLE.equals(action)) {
//                displayData(intent.getStringExtra(BLESwitchService.EXTRA_DATA));
            }
        }
    };


    private void connectToBle(){
        if (mBLESwitchService != null) {
            final boolean result = mBLESwitchService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        connectToBle();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBLESwitchService = null;
    }


    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLESwitchService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLESwitchService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLESwitchService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BLESwitchService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }


}
