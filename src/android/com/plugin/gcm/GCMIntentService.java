package com.plugin.gcm;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;

@SuppressLint("NewApi")
public class GCMIntentService extends GCMBaseIntentService {

	private static final String TAG = "GCMIntentService";
	
	public GCMIntentService() {
		super("GCMIntentService");
	}

	@Override
	public void onRegistered(Context context, String regId) {

		Log.v(TAG, "onRegistered: "+ regId);

		JSONObject json;

		try
		{
			json = new JSONObject().put("event", "registered");
			json.put("regid", regId);

			Log.v(TAG, "onRegistered: " + json.toString());

			// Send this JSON data to the JavaScript application above EVENT should be set to the msg type
			// In this case this is the registration ID
			PushPlugin.sendJavascript( json );

		}
		catch( JSONException e)
		{
			// No message to the user is sent, JSON failed
			Log.e(TAG, "onRegistered: JSON exception");
		}
	}

	@Override
	public void onUnregistered(Context context, String regId) {
		Log.d(TAG, "onUnregistered - regId: " + regId);
	}

	@Override
	protected void onMessage(Context context, Intent intent) {
		Log.d(TAG, "onMessage - context: " + context);

		// Extract the payload from the message
		Bundle extras = intent.getExtras();
		if (extras != null)
		{
			// if we are in the foreground, just surface the payload, else post it to the statusbar
            if (PushPlugin.isInForeground()) {
				extras.putBoolean("foreground", true);
                PushPlugin.sendExtras(extras);
			}
			else {
				extras.putBoolean("foreground", false);

                // Send a notification if there is a message
                if (extras.getString("message") != null && extras.getString("message").length() != 0) {
                    createNotification(context, extras);
                }
            }
        }
	}

	public void createNotification(Context context, Bundle extras)
	{
		Integer unreadMessages=1;
		String senderFirstName="";
		String senderLastName="";
		String notifTitle = extras.getString("title");
		String convId = extras.getString("cnvrsnId");
		String message = extras.getString("message");
		try{
			JSONObject mainObject = new JSONObject(extras.getString("pushNotifReqData"));
			unreadMessages = mainObject.getInt("unreadMsgs");
			senderFirstName = mainObject.getString("senderFirstname");
			senderLastName = mainObject.getString("senderLastname");
		} catch (JSONException e){
			
		}	
		
		
		SharedPreferences sharedPref = context.getSharedPreferences("notification_details",Context.MODE_PRIVATE);

		JSONArray past_conversations = new JSONArray();
		JSONArray past_messages = new JSONArray();
		try {
			past_conversations = new JSONArray(sharedPref.getString("past_conversations","[]"));
			past_messages = new JSONArray(sharedPref.getString("past_messages","[]"));
		} catch (JSONException e) {
		    e.printStackTrace();
		}
		
		SharedPreferences.Editor editor = sharedPref.edit();
		Boolean foundConv = false;

		JSONObject current_message = new JSONObject();
		try {
			current_message.put("convId",convId);
			current_message.put("message",message);
			current_message.put("senderName",senderFirstName + senderLastName);
			past_messages.put(current_message);
		} catch (JSONException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		
		
		if(past_conversations.length() > 0){
			for(Integer i=0;i<past_conversations.length();i++){
				try{
					JSONObject conversation = past_conversations.getJSONObject(i);
					if(conversation.getString("id").equals(convId)){
						foundConv = true;
						conversation.put("unread_messages", unreadMessages);
						past_conversations.put(i, conversation);
						break;
					}					
				} catch(JSONException e){
					
				}				
			}			
		}
		
		if(!foundConv){
			//add new conversation
			JSONObject conversation = new JSONObject();
			try{
				conversation.put("unread_messages", unreadMessages);
				conversation.put("id", convId);
				past_conversations.put(conversation);				
			} catch (JSONException e) {
				
			}
									
		}

		editor.putString("past_conversations", past_conversations.toString());
		editor.putString("past_messages", past_messages.toString());
		editor.commit();		
		
		
		Integer xMessages = 0;
		Integer yConv = past_conversations.length();
		if(yConv>0){
			for(Integer i=0;i<yConv;i++){
				try{
					JSONObject conversation = past_conversations.getJSONObject(i);
					xMessages += conversation.getInt("unread_messages");		
				} catch(JSONException e){
					
				}				
			}	
		}
		extras.putInt("noOfConversationsinNotification", yConv);
		
		Notification.InboxStyle inboxStyleNotif = new Notification.InboxStyle();		
		if(xMessages > 1){
			Boolean showSenderName = true;
			if(yConv > 1){
				notifTitle = xMessages + " Messages from "+yConv + " Conversations";
			} else {
				notifTitle = xMessages + " Messages from "+senderFirstName + " " + senderLastName;
			}
			
			Integer addedLines = 0;
			String lastSenderName = null;
			for(Integer i=past_messages.length() - 1;i>=0 && i>past_messages.length()-6;i--){
				JSONObject message_elem = new JSONObject();
				try {
					message_elem = past_messages.getJSONObject(i);
					if(showSenderName == true){
						String currentSenderName = message_elem.getString("senderName");
						if(currentSenderName.equals(lastSenderName)){
							inboxStyleNotif.addLine(message_elem.getString("message"));
						} else {
							inboxStyleNotif.addLine(message_elem.getString("senderName")+": "+message_elem.getString("message"));
							lastSenderName = message_elem.getString("senderName");
						}
					} else {
						inboxStyleNotif.addLine(message_elem.getString("message"));
					}				
					addedLines++;
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}				
			}
			Integer otherMessages = xMessages - addedLines;
			if(otherMessages > 0){
				inboxStyleNotif.setSummaryText("+ "+otherMessages+" more");
			}			
		}
		
		
		
		
 		
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		String appName = getAppName(this);

		Intent notificationIntent = new Intent(this, PushHandlerActivity.class);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		notificationIntent.putExtra("pushBundle", extras);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		
		int defaults = Notification.DEFAULT_ALL;

		if (extras.getString("defaults") != null) {
			try {
				defaults = Integer.parseInt(extras.getString("defaults"));
			} catch (NumberFormatException e) {}
		}
		
		Notification.Builder mBuilder =
			new Notification.Builder(context)
				.setDefaults(defaults)
				.setSmallIcon(context.getApplicationInfo().icon)
				.setWhen(System.currentTimeMillis())
				.setContentTitle(notifTitle)
				.setTicker(notifTitle)
				.setContentIntent(contentIntent)
				.setAutoCancel(true);
				
		if (message != null) {
			if(xMessages < 2){
				mBuilder.setContentText(message)
				.setStyle(new Notification.BigTextStyle()
	            .bigText(message));
			} else {
				mBuilder.setContentText(notifTitle)
				.setStyle(inboxStyleNotif);
			}
		} else {
			mBuilder.setContentText("<missing message content>");
		}

		String msgcnt = extras.getString("msgcnt");
		if (msgcnt != null) {
			mBuilder.setNumber(Integer.parseInt(msgcnt));
		}
		
		int notId = 0;
		
		try {
			notId = Integer.parseInt(extras.getString("notId"));
		}
		catch(NumberFormatException e) {
			Log.e(TAG, "Number format exception - Error parsing Notification ID: " + e.getMessage());
		}
		catch(Exception e) {
			Log.e(TAG, "Number format exception - Error parsing Notification ID" + e.getMessage());
		}
		
		mNotificationManager.notify((String) appName, notId, mBuilder.build());
	}
	
	private static String getAppName(Context context)
	{
		CharSequence appName = 
				context
					.getPackageManager()
					.getApplicationLabel(context.getApplicationInfo());
		
		return (String)appName;
	}
	
	@Override
	public void onError(Context context, String errorId) {
		Log.e(TAG, "onError - errorId: " + errorId);
	}

}
