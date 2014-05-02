package com.cobra.iradar;

import java.util.ArrayList;
import java.util.List;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

/**
 * A service handling connection to iRadar
 * Communicates with any bound callers via messages
 * @author pzeltins
 *
 */
public class RadarConnectionService extends Service {

	public static final int MSG_REGISTER_LISTENER = 1;
	public static final int MSG_SET_IRADAR_DEVICE = 2;
	public static final int MSG_IRADAR_FAILURE = 3;
	public static final int MSG_RADAR_MESSAGE_RECV = 4;
	public static final int MSG_NOTIFICATION = 5;
	public static final int MSG_STOP = 6;
	public static final int MSG_RECONNECT = 7;
	
	public static final String BUNDLE_KEY_RADAR_MESSAGE = "radarMsgRecv";
	
	private HandlerThread thread = null;
    	
	private BluetoothDevice iRadarDevice;
	private RadarConnectionThread radarThread;
	private ServiceHandler radarThreadHandler;
	
	/**
    * Target we publish for clients to send messages to ServiceHandler.
    */
    private Messenger mMessenger;
    
    private List<Messenger> messengers = new ArrayList<Messenger>();
	
	@Override
	public IBinder onBind(Intent intent) {
		// super.startForeground(NOTIFICATION_ID, new Notification.Builder(getApplicationContext()).setContentText("Cobra iRadar").build());
		if ( thread == null ) {
			 thread = new HandlerThread("iRadar BT Handler");
			 thread.start();
		}
		if ( mMessenger == null ) {
			radarThreadHandler = new ServiceHandler(thread.getLooper());
			mMessenger = new Messenger(radarThreadHandler);
		}
		return mMessenger.getBinder();
	}
	
	private void runConnection() {
    	if ( radarThread == null || !radarThread.isAlive() ) {
	    	radarThread = new RadarConnectionThread(iRadarDevice, radarThreadHandler);
	    	radarThread.start();
    	}
	}
	
	private void stopConnection() {
    	if ( radarThread != null && !radarThread.isInterrupted() )
    		radarThread.interrupt();
	}
	
    private final class ServiceHandler extends Handler {

    	public ServiceHandler(Looper l) {
        	super(l);
        }
        
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_REGISTER_LISTENER:
            	messengers.add(msg.replyTo);
                break;
            case MSG_SET_IRADAR_DEVICE:
            	iRadarDevice = (BluetoothDevice) msg.obj;
            	runConnection();
            	break;
            case MSG_RECONNECT:
   				runConnection();
            	break;
            case MSG_IRADAR_FAILURE:
            	sendAllRecipients(MSG_IRADAR_FAILURE, ( msg.obj != null ? msg.obj.toString() : "" ));
            	break;
            case MSG_RADAR_MESSAGE_RECV:
            	sendAllRecipients(MSG_RADAR_MESSAGE_RECV, msg.getData());
            	break;
            case MSG_STOP:
            	stopConnection();
            	break;
            default:
                super.handleMessage(msg);
            }
        }
        
        private void sendAllRecipients(int code, String msg) {
        	Message m = this.obtainMessage();
        	m.what = code;
        	m.obj = msg;
        	sendAllRecipients(m);
        }
        
        private void sendAllRecipients(Message m) {
        	for ( Messenger msngr : messengers ) {
        		try {
					msngr.send(m);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
        	}
        }
        
        private void sendAllRecipients(int code, Bundle b) {
        	Message m = this.obtainMessage();
        	m.what = code;
        	m.setData(b);
        	sendAllRecipients(m);
        }
    }
    
}
