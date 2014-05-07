package com.greatnowhere.iradar;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import com.cobra.iradar.messaging.ConnectivityStatus;
import com.cobra.iradar.protocol.RadarMessageNotification;

import de.greenrobot.event.EventBus;

public class TTSManager {
	
	private static final String TAG = TTSManager.class.getCanonicalName(); 

	private static TextToSpeech tts;
	private static AtomicBoolean isReady = new AtomicBoolean(false);
	private static EventBus eventBus = EventBus.getDefault();
	private static AtomicInteger connStatus = new AtomicInteger(ConnectivityStatus.UNKNOWN.getCode());
	private static Timer timer;
	private static HashMap<String, String> ttsParams = new HashMap<String, String>();
	
	public static void init(Context ctx) {
		tts = new TextToSpeech(ctx, new TTSInitListener());
		ttsParams.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AlertAudioManager.OUTPUT_STREAM));
		tts.setOnUtteranceProgressListener(new TTSUtteranceProgressListener());
		eventBus.register(new EventListener());
	}
	
	public static class EventListener {
		public void onEvent(RadarMessageNotification msg) {
			if ( msg.type == RadarMessageNotification.TYPE_CONN ) {
				
				connStatus.set(msg.connectionStatus);
				
				switch (ConnectivityStatus.fromCode(msg.connectionStatus)) {
				case CONNECTED:
					speak(Preferences.getNotifyOnConnectText());
					startConnectivityTimer(Preferences.getNotifyWhileConnectedInterval());
					break;
				case DISCONNECTED:
					speak(Preferences.getNotifyOnDisconnectText());
					stopConnectivityTimer();
					break;
				default:
				}
				
			}
		}
	}
	
	private synchronized static void startConnectivityTimer(int seconds) {
		if ( timer != null )
			timer.cancel();
		if ( seconds > 0 ) {
			timer = new Timer(true);
			timer.scheduleAtFixedRate(new RadarActiveSpeaker(), (long) seconds * 1000L, (long) seconds * 1000L);
		}
	}
	
	private synchronized static void stopConnectivityTimer() {
		if ( timer != null )
			timer.cancel();
	}
	
	private static void speak(String text) {
		if ( isReady.get() && Preferences.isNotifyConnectivity() && text != null && !text.isEmpty() ) {
			AlertAudioManager.setOurAlertVolume();
			int i = tts.speak(text, TextToSpeech.QUEUE_ADD, ttsParams);
			Log.d(TAG, "speak " + text + " :result " + i);
		}
	}
	
	private static class RadarActiveSpeaker extends TimerTask {
		@Override
		public void run() {
			speak(Preferences.getNotifyWhileConnectedText());
		}
	}
	
	private static class TTSInitListener implements TextToSpeech.OnInitListener {
		@Override
		public void onInit(int status) {
			if ( status == TextToSpeech.SUCCESS )
				isReady.set(true);
		}
	}
	
	private static class TTSUtteranceProgressListener extends UtteranceProgressListener {
		@Override
		public void onStart(String utteranceId) {
		}

		@Override
		public void onDone(String utteranceId) {
			AlertAudioManager.restoreOldAlertVolume();
		}

		@Override
		public void onError(String utteranceId) {
		}
		
	}
	
}
