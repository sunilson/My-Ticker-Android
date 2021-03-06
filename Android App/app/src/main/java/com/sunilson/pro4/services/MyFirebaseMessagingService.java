package com.sunilson.pro4.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.sunilson.pro4.R;
import com.sunilson.pro4.activities.MainActivity;
import com.sunilson.pro4.utilities.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;

import static com.sunilson.pro4.utilities.Constants.CONTENT_TYPE_IMAGE;
import static com.sunilson.pro4.utilities.Constants.CONTENT_TYPE_STATE;
import static com.sunilson.pro4.utilities.Constants.CONTENT_TYPE_TEXT;

/**
 * @author Linus Weiss
 */

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private NotificationCompat.Builder mBuilder = null;
    private SharedPreferences sharedPreferences;

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.i(Constants.LOGGING_TAG, remoteMessage.toString());

        //Get shared preferences
        sharedPreferences = getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE);
        Boolean notifications = sharedPreferences.getBoolean(Constants.SHARED_PREF_KEY_NOTIFICATIONS, true);

        //If notification contains any data and notifications are activated
        if (remoteMessage.getData().size() > 0 && notifications) {
            String author, type, authorID;

            type = remoteMessage.getData().get("type");
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

            //Build notification depending on the content of the notification
            if (type != null) {
                final String livetickerID = remoteMessage.getData().get("livetickerID");
                authorID = remoteMessage.getData().get("authorID");
                final String title = remoteMessage.getData().get("title");

                //New Event in a liveticker
                if (type.equals("livetickerEventAdded")) {
                    String contentType = remoteMessage.getData().get("contentType");
                    if (livetickerID != null && user != null && title != null && contentType != null && !authorID.equals(user.getUid())) {
                        //Check what type the content of the event has
                        if (contentType.equals(CONTENT_TYPE_STATE)) {
                            String content = remoteMessage.getData().get("content");
                            if (content.equals("started")) {
                                mBuilder = new NotificationCompat.Builder(this)
                                        .setSmallIcon(R.drawable.icontransparent)
                                        .setAutoCancel(true)
                                        .setContentTitle(getString(R.string.new_liveticker_event_started))
                                        .setContentText(title + " " + getString(R.string.new_liveticker_event_started_subtitle));
                            } else if (content.equals("finished")) {
                                mBuilder = new NotificationCompat.Builder(this)
                                        .setSmallIcon(R.drawable.icontransparent)
                                        .setAutoCancel(true)
                                        .setContentTitle(getString(R.string.new_liveticker_event_finished))
                                        .setContentText(title + " " + getString(R.string.new_liveticker_event_finished_subtitle));
                            }

                            showNotification(mBuilder, livetickerID);
                        } else if (contentType.equals(CONTENT_TYPE_TEXT)) {
                            String content = remoteMessage.getData().get("content");

                            mBuilder = new NotificationCompat.Builder(this)
                                    .setSmallIcon(R.drawable.icontransparent)
                                    .setAutoCancel(true)
                                    .setContentTitle(getString(R.string.new_liveticker_event) + " " + title)
                                    .setContentText(content);

                            showNotification(mBuilder, livetickerID);
                        } else if (contentType.equals(CONTENT_TYPE_IMAGE)) {
                            String thumbnail = remoteMessage.getData().get("thumbnail");
                            final String caption = remoteMessage.getData().get("caption");

                            //Get thumbnail bitmap
                            Bitmap bitmap = getBitmapFromURL(thumbnail);

                            mBuilder = new NotificationCompat.Builder(MyFirebaseMessagingService.this)
                                    .setSmallIcon(R.drawable.icontransparent)
                                    .setAutoCancel(true)
                                    .setContentTitle(getString(R.string.new_liveticker_event) + " " + title)
                                    .setContentText(caption)
                                    .setStyle(new NotificationCompat.BigPictureStyle().bigPicture(bitmap));

                            showNotification(mBuilder, livetickerID);
                        }
                    }
                }

                //New Liveticker has been added
                if (type.equals("livetickerAdded")) {
                    author = remoteMessage.getData().get("author");

                    if (livetickerID != null && author != null && title != null && user != null && !authorID.equals(user.getUid())) {
                        mBuilder = new NotificationCompat.Builder(this)
                                .setSmallIcon(R.drawable.icontransparent)
                                .setAutoCancel(true)
                                .setContentTitle(getString(R.string.new_liveticker_notification_title))
                                .setContentText(author + " created a new Liveticker: " + title);

                        showNotification(mBuilder, livetickerID);
                    }
                }
            }
        }
    }

    /**
     * Finish building the notification and show it to the user
     *
     * @param mBuilder Initialized Notification Builder
     * @param livetickerID ID of liveticker of the notification
     */
    private void showNotification(NotificationCompat.Builder mBuilder, String livetickerID) {
        if (mBuilder != null) {
            if (sharedPreferences.getBoolean(Constants.SHARED_PREF_KEY_NOTIFICATIONS_VIBRATION, true)) {
                mBuilder.setVibrate(new long[]{0, 500, 200, 500});
            }

            //Intent that is used to navigate to the liveticker after notification is clicked
            Intent resultIntent = new Intent(this, MainActivity.class);
            resultIntent.putExtra("livetickerID", livetickerID);
            resultIntent.putExtra("type", Constants.INTENT_TYPE_NOTIFICATION);
            resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(resultPendingIntent);

            //Show notification
            NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            Random random = new Random();
            //Give notification a random id so it doesn't stack with other notifications
            int m = random.nextInt(9999 - 1000) + 1000;
            mNotificationManager.notify(m, mBuilder.build());
        }
    }

    /**
     * Get a bitmap from a given image URL
     *
     * @param strURL URL of image
     * @return Generated bitmap from downloaded image
     */
    private Bitmap getBitmapFromURL(String strURL) {
        try {
            URL url = new URL(strURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}


