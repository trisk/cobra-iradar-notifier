package com.greatnowhere.radar.location;

import com.cobra.iradar.RadarScanManager;
import com.cobra.iradar.protocol.CobraRadarMessageNotification;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import de.greenrobot.event.EventBus;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class PhoneActivityDetector implements GooglePlayServicesClient.ConnectionCallbacks,
		GooglePlayServicesClient.OnConnectionFailedListener {

	private static PhoneActivityDetector instance;
	private static ActivityRecognitionClient activityClient;
	private static ActivityStatus activity = ActivityStatus.UNKNOWN;
	private static Context ctx;
	private static final long ACTIVITY_UPDATE_INTERVAL = 60000L; // every 60 secs
	private static EventBus eventBus;
	
	public static void init(Context ctx) {
		instance = new PhoneActivityDetector();
		PhoneActivityDetector.ctx = ctx;
		eventBus = EventBus.getDefault();
		activityClient = new ActivityRecognitionClient(ctx, instance, instance);
		activityClient.connect();
	}
	
	public static void stop() {
		activityClient.disconnect();
	}

	public static ActivityStatus getActivityStatus() {
		synchronized (activity) {
			return activity;
		}
	}

	private static void setActivityStatus(ActivityStatus a) {
		synchronized (activity) {
			activity = a;
		}
	}
	
	public void onConnectionFailed(ConnectionResult result) {
		setActivityStatus(ActivityStatus.UNKNOWN);
	}


	public void onConnected(Bundle connectionHint) {
		Intent i = new Intent(ctx, ActivityDetectorIntentReceiver.class);
		PendingIntent callbackIntent = PendingIntent.getService(ctx, 0, i,
	             PendingIntent.FLAG_UPDATE_CURRENT);
		activityClient.requestActivityUpdates(ACTIVITY_UPDATE_INTERVAL, callbackIntent);
	}


	public void onDisconnected() {
		setActivityStatus(ActivityStatus.UNKNOWN);
	}

	public enum ActivityStatus {
		DRIVING("Driving",DetectedActivity.IN_VEHICLE),STILL("Still",DetectedActivity.STILL),
		FOOT("Walking",DetectedActivity.ON_FOOT),BICYCLE("Bicycling",DetectedActivity.ON_BICYCLE),
		TILTED("Tilting",DetectedActivity.TILTING),UNKNOWN("Unknown",DetectedActivity.UNKNOWN);

		private ActivityStatus(String n,int c) {
			code=c;
			name=n;
		}
		
		private int code;
		private String name;
		
		public int getCode() {
			return code;
		}
		public String getName() {
			return name;
		}
		
		public static ActivityStatus fromDetectedActivity(DetectedActivity act) {
			for (ActivityStatus s : ActivityStatus.values()) {
				if (s.getCode() == act.getType()) {
					return s;
				}
			}
			return UNKNOWN;
		}
	}
	
	public static class ActivityDetectorIntentReceiver extends IntentService {

		public ActivityDetectorIntentReceiver() {
			super(PhoneActivityDetector.class.getCanonicalName());
		}

		@Override
		protected void onHandleIntent(Intent intent) {
			if (ActivityRecognitionResult.hasResult(intent)) {
		         ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
		         setActivityStatus(ActivityStatus.fromDetectedActivity(result.getMostProbableActivity()));
		         eventBus.post(new EventActivityChanged(getActivityStatus()));
		         // Also see if we should start scanning
				 eventBus.post(new CobraRadarMessageNotification("Activity change: " + getActivityStatus().getName()));
				 RadarScanManager.scan();
		    }
		}
	}

	/**
	 * Event to be dispatched on event bus whenever activity status changes
	 * @author pzeltins
	 *
	 */
	public static class EventActivityChanged {
		
		public EventActivityChanged() {
		}
		
		public EventActivityChanged(ActivityStatus act) {
			this();
			activity = act;
		}
		
		public ActivityStatus activity;
	}
}
