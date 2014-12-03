package com.google.beaconhack;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.google.beaconhack.demos.R;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.estimote.sdk.BeaconManager.MonitoringListener;

/**
 * Demo that shows how to use region monitoring. Two important steps are:
 * <ul>
 * <li>start monitoring region, in example in {@link #onResume()}</li>
 * <li>respond to monitoring changes by registering {@link MonitoringListener} in {@link BeaconManager}</li>
 * </ul>
 */
public class NotifyDemoActivity extends Activity {

    private static final String TAG = NotifyDemoActivity.class.getSimpleName();
    private static final int NOTIFICATION_ID = 123;

    private BeaconManager beaconManager;
    private NotificationManager notificationManager;
    private Region region;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.notify_demo);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        Beacon beacon = getIntent().getParcelableExtra(ListBeaconsActivity.EXTRAS_BEACON);
        region = new Region("regionId", beacon.getProximityUUID(), beacon.getMajor(), beacon.getMinor());
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        beaconManager = new BeaconManager(this);

        // Default values are 5s of scanning and 25s of waiting time to save CPU cycles.
        // In order for this demo to be more responsive and immediate we lower down those values.
        beaconManager.setBackgroundScanPeriod(TimeUnit.SECONDS.toMillis(1), 0);

        beaconManager.setMonitoringListener(new MonitoringListener() {
            @Override
            public void onEnteredRegion(Region region, List<Beacon> beacons) {
                Log.i(TAG, "Found beacon : " + beacons.get(0).getMacAddress());
                postNotification("Customer is within range of the product.");
                trackBeaconEvent(beacons.get(0));
            }

            @Override
            public void onExitedRegion(Region region) {
                postNotification("Customer is no longer next to the product.");
            }
        });
    }

    /**
     * Records the beacon activity remotely
     */
    public void trackBeaconEvent(Beacon beacon) {
        RequestQueue queue = Volley.newRequestQueue(this);
        String url = "http://4605712.fls.doubleclick.net/activityi;src=4605712;type=;cat=;qty=1;cost=[Revenue];ord=[OrderID]?";

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        // send a notification
                        Log.i(TAG, "Floodlight response : " + response);
                        postNotification("Floodlight sent");
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.i(TAG, "Floodlight error : " + error.getMessage());
            }
        });

        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        notificationManager.cancel(NOTIFICATION_ID);
        beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
            @Override
            public void onServiceReady() {
                try {
                    beaconManager.startMonitoring(region);
                } catch (RemoteException e) {
                    Log.d(TAG, "Error while starting monitoring");
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        notificationManager.cancel(NOTIFICATION_ID);
        beaconManager.disconnect();
        super.onDestroy();
    }

    private void postNotification(String msg) {
        Intent notifyIntent = new Intent(NotifyDemoActivity.this, NotifyDemoActivity.class);
        notifyIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivities(
                NotifyDemoActivity.this,
                0,
                new Intent[]{notifyIntent},
                PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(NotifyDemoActivity.this)
                .setSmallIcon(R.drawable.beacon_gray)
                .setContentTitle("Notify Demo")
                .setContentText(msg)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();
        notification.defaults |= Notification.DEFAULT_SOUND;
        notification.defaults |= Notification.DEFAULT_LIGHTS;
        notificationManager.notify(NOTIFICATION_ID, notification);

        TextView statusTextView = (TextView) findViewById(R.id.status);
        statusTextView.setText(msg);
    }
}
