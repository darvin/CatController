package org.darvin.CatController;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    public TextView bleStatusTextView;
    public TextView waterStatusTextView;
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        bleStatusTextView = (TextView)findViewById(R.id.bleStatusTextView);
        waterStatusTextView = (TextView)findViewById(R.id.waterStatusTextView);
    }


    public void buttonTurnOnPressed(View view) {
        Log.v(TAG, "button pressed");

        waterStatusTextView.setText("Trying to turn on...");



    }
}
