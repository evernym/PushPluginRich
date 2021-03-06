package com.plugin.gcm;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gcm.GCMRegistrar;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author awysocki
 */

public class PushPlugin extends CordovaPlugin {
	public static final String TAG = "PushPlugin";

	public static final String REGISTER = "register";
	public static final String UNREGISTER = "unregister";
	public static final String READCONVERSATION = "readConversation";
	public static final String REMOVE_STORED_NOTIFS = "removeStoredNotifs";
	public static final String EXIT = "exit";

	private static CordovaWebView gWebView;
	private static String gECB;
	private static String gConversationsPnHas;
	private static String gSenderID;
	private static Bundle gCachedExtras = null;
    private static boolean gForeground = false;

	/**
	 * Gets the application context from cordova's main activity.
	 * @return the application context
	 */
	private Context getApplicationContext() {
		return this.cordova.getActivity().getApplicationContext();
	}

	@Override
	public boolean execute(String action, JSONArray data, CallbackContext callbackContext) {

		boolean result = false;

		Log.v(TAG, "execute: action=" + action);

		if (REGISTER.equals(action)) {

			Log.v(TAG, "execute: data=" + data.toString());

			try {
				JSONObject jo = data.getJSONObject(0);

				gWebView = this.webView;
				Log.v(TAG, "execute: jo=" + jo.toString());

				gECB = (String) jo.get("ecb");
				gConversationsPnHas = (String) jo.get("gConversationsPnHas");
				gSenderID = (String) jo.get("senderID");

				Log.v(TAG, "execute: ECB=" + gECB + " senderID=" + gSenderID);

				GCMRegistrar.register(getApplicationContext(), gSenderID);
				result = true;
				callbackContext.success();
			} catch (JSONException e) {
				Log.e(TAG, "execute: Got JSON Exception " + e.getMessage());
				result = false;
				callbackContext.error(e.getMessage());
			}

			if ( gCachedExtras != null) {
				Log.v(TAG, "sending cached extras");
				sendExtras(gCachedExtras);
				sendConversationPnHas(getApplicationContext());
				gCachedExtras = null;
			}

		} else if (UNREGISTER.equals(action)) {

			GCMRegistrar.unregister(getApplicationContext());

			Log.v(TAG, "UNREGISTER");
			result = true;
			callbackContext.success();
		} else if(READCONVERSATION.equals(action)){
			try {
				for(Integer j=0;j<data.length();j++){
					String conversationID = data.getString(j);
					SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("notification_details",Context.MODE_PRIVATE);
					SharedPreferences.Editor editor = sharedPref.edit();
					JSONArray past_conversations = new JSONArray(sharedPref.getString("past_conversations","[]"));
					JSONArray past_messages = new JSONArray(sharedPref.getString("past_messages","[]"));
					JSONArray new_past_conversations = new JSONArray();
					JSONArray new_past_messages = new JSONArray();
					if(past_conversations.length() > 0){
						for(Integer i=0;i<past_conversations.length();i++){
							JSONObject conv = past_conversations.getJSONObject(i);
							if(!conv.getString("id").equals(conversationID)){
								new_past_conversations.put(conv);
							}
						}
					}
					if(past_messages.length() > 0){
						for(Integer i=0;i<past_messages.length();i++){
							JSONObject message = past_messages.getJSONObject(i);
							if(!message.getString("convId").equals(conversationID)){
								new_past_messages.put(message);
							}
						}
					}
					editor.putString("past_conversations", new_past_conversations.toString());
					editor.putString("past_messages", new_past_messages.toString());
					editor.commit();
				}
				sendConversationPnHas(getApplicationContext());			
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Log.v(TAG, "READCONVERSATION");
		} else if(REMOVE_STORED_NOTIFS.equals(action)) {
			clearStoredNotifs();
		} else {
			result = false;
			Log.e(TAG, "Invalid action : " + action);
			callbackContext.error("Invalid action : " + action);
		}

		return result;
	}

	/*
	 * Sends a json object to the client as parameter to a method which is defined in gECB.
	 */
	public static void sendJavascript(JSONObject _json) {
		String _d = "javascript:" + gECB + "(" + _json.toString() + ")";
		Log.v(TAG, "sendJavascript: " + _d);

		if (gECB != null && gWebView != null) {
			gWebView.sendJavascript(_d);
		}
	}

	/*
	 * Sends a json object to the client as parameter to a method which is defined in gConversationsPnHas.
	 */
	public static void sendConversationPnHas(Context context) {
		SharedPreferences sharedPref = context.getSharedPreferences("notification_details",Context.MODE_PRIVATE);
		JSONArray past_conversations = new JSONArray();
		try {
			past_conversations = new JSONArray(sharedPref.getString("past_conversations","[]"));
		} catch (JSONException e) {
		    e.printStackTrace();
		}
		if(past_conversations.length() > 0){
			String _d = "javascript:" + gConversationsPnHas + "(" + past_conversations.toString() + ")";
			Log.v(TAG, "sendJavascript: " + _d);

			if (gConversationsPnHas != null && gWebView != null) {
				gWebView.sendJavascript(_d);
			}
		}		
	}

	/*
	 * Sends the pushbundle extras to the client application.
	 * If the client application isn't currently active, it is cached for later processing.
	 */
	public static void sendExtras(Bundle extras)
	{
		if (extras != null) {
			if (gECB != null && gWebView != null) {
				sendJavascript(convertBundleToJson(extras));
			} else {
				Log.v(TAG, "sendExtras: caching extras to send at a later time.");
				gCachedExtras = extras;
			}
		}
	}

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        gForeground = true;
        clearNotifs();
    }

	@Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        gForeground = false;
        clearNotifs();
        clearNotifTimes();
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        gForeground = true; 
        clearNotifs();
        clearNotifTimes();
        sendConversationPnHas(getApplicationContext());
    }
    
    public void clearNotifs(){
    	final NotificationManager notificationManager = (NotificationManager) cordova.getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
    	notificationManager.cancelAll();    	
    }

    public void clearNotifTimes(){
    	SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("notification_details",Context.MODE_PRIVATE);
    	SharedPreferences.Editor editor = sharedPref.edit();
    	JSONArray past_conversations = new JSONArray();
		try {
			past_conversations = new JSONArray(sharedPref.getString("past_conversations","[]"));
			for(Integer i=0;i<past_conversations.length();i++){
				JSONObject conversation = past_conversations.getJSONObject(i);
				conversation.remove("firstMessageTime");
				conversation.remove("secondMessageTime");
				past_conversations.put(i, conversation);
			}
		} catch (JSONException e) {
		    e.printStackTrace();
		}
		editor.putString("past_conversations", past_conversations.toString());
		editor.commit();
    }

    public void clearStoredNotifs(){
    	SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("notification_details",Context.MODE_PRIVATE);
    	SharedPreferences.Editor editor = sharedPref.edit();
    	editor.putString("past_conversations", "[]");
		editor.putString("past_messages", "[]");
		editor.commit();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        gForeground = false;
		gECB = null;
		gWebView = null;
    }

    /*
     * serializes a bundle to JSON.
     */
    private static JSONObject convertBundleToJson(Bundle extras)
    {
		try
		{
			JSONObject json;
			json = new JSONObject().put("event", "message");

			JSONObject jsondata = new JSONObject();
			Iterator<String> it = extras.keySet().iterator();
			while (it.hasNext())
			{
				String key = it.next();
				Object value = extras.get(key);

				// System data from Android
				if (key.equals("from") || key.equals("collapse_key"))
				{
					json.put(key, value);
				}
				else if (key.equals("noOfConversationsinNotification"))
				{
					json.put(key, extras.getInt("noOfConversationsinNotification"));
				}
				else if (key.equals("foreground"))
				{
					json.put(key, extras.getBoolean("foreground"));
				}
				else if (key.equals("coldstart"))
				{
					json.put(key, extras.getBoolean("coldstart"));
				}
				else
				{
					// Maintain backwards compatibility
					if (key.equals("message") || key.equals("msgcnt") || key.equals("soundname"))
					{
						json.put(key, value);
					}

					if ( value instanceof String ) {
					// Try to figure out if the value is another JSON object

						String strValue = (String)value;
						if (strValue.startsWith("{")) {
							try {
								JSONObject json2 = new JSONObject(strValue);
								jsondata.put(key, json2);
							}
							catch (Exception e) {
								jsondata.put(key, value);
							}
							// Try to figure out if the value is another JSON array
						}
						else if (strValue.startsWith("["))
						{
							try
							{
								JSONArray json2 = new JSONArray(strValue);
								jsondata.put(key, json2);
							}
							catch (Exception e)
							{
								jsondata.put(key, value);
							}
						}
						else
						{
							jsondata.put(key, value);
						}
					}
				}
			} // while
			json.put("payload", jsondata);

			Log.v(TAG, "extrasToJSON: " + json.toString());

			return json;
		}
		catch( JSONException e)
		{
			Log.e(TAG, "extrasToJSON: JSON exception");
		}
		return null;
    }

    public static boolean isInForeground()
    {
      return gForeground;
    }

    public static boolean isActive()
    {
    	return gWebView != null;
    }
}
