package org.darvin.CatController;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opencv.android.*;
import org.opencv.core.Mat;

public class MainActivity extends Activity implements CatDetector.OnCatDetectedListener {
    private static final String TAG = "MainActivity";
    private boolean mWaterSwitch = true;
    public TextView bleStatusTextView;
    public TextView waterStatusTextView;

    public Button mWaterButton;
    public Button mTrainCat1Button;
    public ImageView mPhotoView;

    private int mTimerTimeout = 500;
    private Camera camera;
    SurfaceTexture surfaceTexture;

    private long WATER_TIMEOUT = 30000;
    private long mLastTimeCatSeen = System.currentTimeMillis();
    private String MyPREFERENCES = "MyPREFERENCES";
    private String PREFERENCES_BLE_SWITCH_ADDRESS = "PREFERENCES_BLE_SWITCH_ADDRESS";
    private String PREFERENCES_BLE_SWITCH_NAME = "PREFERENCES_BLE_SWITCH_NAME";
    private String PREFERENCES_CAT1_TRAIN = "PREFERENCES_CAT1_TRAIN";
    private CatDetector mCatDetector;
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
//                    System.loadLibrary("cat_controller_opencv");

                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!OpenCVLoader.initDebug()) {
            Log.e(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), not working.");
        } else {
            Log.d(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), working.");
        }

        setContentView(R.layout.main);
        bleStatusTextView = (TextView)findViewById(R.id.bleStatusTextView);
        waterStatusTextView = (TextView)findViewById(R.id.waterStatusTextView);
        mWaterButton = (Button) findViewById(R.id.buttonWaterTurnOn);
        mTrainCat1Button = (Button) findViewById(R.id.trainCat1Button);
        mPhotoView = (ImageView) findViewById(R.id.photoView);

        Intent gattServiceIntent = new Intent(this, BLESwitchService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        SharedPreferences sharedpreferences = getSharedPreferences(MyPREFERENCES, Context.MODE_PRIVATE);
        mDeviceAddress = sharedpreferences.getString(PREFERENCES_BLE_SWITCH_ADDRESS, null);
        mDeviceName = sharedpreferences.getString(PREFERENCES_BLE_SWITCH_NAME, null);
        String cat1HistogramBase64 = sharedpreferences.getString(PREFERENCES_CAT1_TRAIN, null);
        mCatDetector = new CatDetector(cat1HistogramBase64);
        mCatDetector.setCatDetectedListener(this);


//        Camera camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
//
//        Camera.Parameters param = camera.getParameters();
//        List fpslist = param.getSupportedPreviewFpsRange();
//        Log.d(TAG, "size= " + fpslist.size());



        camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);


        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_FRONT, info);
        if (info.canDisableShutterSound) {
            camera.enableShutterSound(false);
        }

        Camera.Parameters params = camera.getParameters();

        List<Camera.Size> sizes = params.getSupportedPictureSizes();

        // Iterate through all available resolutions and choose one.
        // The chosen resolution will be stored in mSize.
        Camera.Size mSize = null;
        for (Camera.Size size : sizes) {
            Log.i(TAG, "Available resolution: "+size.width+" "+size.height);
            if (mSize == null || (size.width>500 && size.width<700)) {
                mSize = size;

            }
        }

        Log.i(TAG, "Chosen resolution: "+mSize.width+" "+mSize.height);
        params.setPictureSize(mSize.width, mSize.height);
        params.setPreviewSize(mSize.width, mSize.height);
        camera.setParameters(params);

        surfaceTexture = new SurfaceTexture(10);
        try {
            camera.setPreviewTexture(surfaceTexture);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        timerHandler.postDelayed(timerRunnable, 3000);




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
        timerHandler.removeCallbacks(timerRunnable);

        
        waterStatusTextView.setText("Trying to turn on...");
        setWaterSwitch(!mWaterSwitch);
    }

    private  void setWaterSwitch(boolean state) {
        if (state!=mWaterSwitch) {
            Log.d(TAG, "Setting water switch: "+state);
            mWaterSwitch = state;
            mWaterButton.setText(mWaterSwitch? "Turn Off Water": "Turn On Water");
            mBLESwitchService.turnSwitch(0, !mWaterSwitch);

        }
    }

    public void buttonTrainCat1Pressed(View view) {
        Log.d(TAG, "CAT TRAINING");


        SharedPreferences sharedpreferences = getSharedPreferences(MyPREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedpreferences.edit();

        mCatDetector.setCurrentHistogramAsCatHistogram(0);
        editor.putString(PREFERENCES_CAT1_TRAIN, mCatDetector.getCatHistogram(0));



        editor.commit();

    }





    Handler timerHandler = new Handler();

    Runnable timerRunnable = new Runnable() {

        @Override
        public void run() {
            takePicture();
            if (System.currentTimeMillis()>(mLastTimeCatSeen+WATER_TIMEOUT)) {
                setWaterSwitch(false);
            } else {
                setWaterSwitch(true);
            }
            timerHandler.postDelayed(this, mTimerTimeout);
        }
    };

    boolean takingPicture = false;

    private void takePicture() {
        if (takingPicture) {
            return;
        }
        takingPicture = true;



        camera.startPreview();

        camera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                BitmapFactory.Options scalingOptions = new BitmapFactory.Options();
                scalingOptions.inSampleSize = camera.getParameters().getPictureSize().width / mPhotoView.getMeasuredWidth();
                final Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, scalingOptions);
                camera.stopPreview();
                takingPicture = false;
                Mat frameMat = new Mat();
                Utils.bitmapToMat(bmp, frameMat);

                Mat processedFrame = mCatDetector.processFrame(frameMat);
                Bitmap newBitmap = Bitmap.createBitmap(processedFrame.cols(), processedFrame.rows(),
                        Bitmap.Config.ARGB_8888);

                Utils.matToBitmap(processedFrame, newBitmap);

                mPhotoView.setImageBitmap(newBitmap);
                mPhotoView.setVisibility(ImageView.VISIBLE);

            }
        });
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


        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

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


    @Override
    public void onCatDetected(int index) {
        mLastTimeCatSeen = System.currentTimeMillis();

    }
}
