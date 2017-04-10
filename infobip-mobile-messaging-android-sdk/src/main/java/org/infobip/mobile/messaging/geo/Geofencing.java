package org.infobip.mobile.messaging.geo;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import org.infobip.mobile.messaging.BootReceiver;
import org.infobip.mobile.messaging.Message;
import org.infobip.mobile.messaging.MobileMessagingCore;
import org.infobip.mobile.messaging.MobileMessagingLogger;
import org.infobip.mobile.messaging.api.support.Tuple;
import org.infobip.mobile.messaging.gcm.PlayServicesSupport;
import org.infobip.mobile.messaging.geo.ConfigurationException.Reason;
import org.infobip.mobile.messaging.storage.MessageStore;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author pandric
 * @since 03.06.2016.
 */
public class Geofencing implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static String TAG = "Geofencing";

    private static Context context;
    private static Geofencing instance;
    private GoogleApiClient googleApiClient;
    private List<Geofence> geofences;
    private PendingIntent geofencePendingIntent;
    private MessageStore messageStore;
    private GoogleApiClientRequestType requestType;

    private enum GoogleApiClientRequestType {
        ADD_GEOFENCES,
        REMOVE_GEOFENCES,
        NONE
    }

    private Geofencing(Context context) {
        Geofencing.context = context;
        requestType = GoogleApiClientRequestType.NONE;
        geofences = new ArrayList<>();
        messageStore = MobileMessagingCore.getInstance(context).getMessageStoreForGeo();
        googleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    public static Geofencing getInstance(Context context) {
        if (instance != null) {
            return instance;
        }

        instance = new Geofencing(context);
        return instance;
    }

    public static void scheduleRefresh(Context context) {
        scheduleRefresh(context, new Date());
    }

    private static void scheduleRefresh(Context context, Date when) {
        MobileMessagingLogger.i(TAG, "Next refresh in: " + when);

        GeofencingConsistencyReceiver.scheduleConsistencyAlarm(context, AlarmManager.RTC_WAKEUP, when,
                GeofencingConsistencyReceiver.SCHEDULED_GEO_REFRESH_ACTION, 0);
    }

    public static void scheduleExpiry(Context context, Date when) {
        GeofencingConsistencyReceiver.scheduleConsistencyAlarm(context, AlarmManager.RTC_WAKEUP, when,
                GeofencingConsistencyReceiver.SCHEDULED_GEO_EXPIRE_ACTION, 0);
    }

    public void removeExpiredAreasFromStorage() {
        GeoSQLiteMessageStore messageStoreForGeo = (GeoSQLiteMessageStore) MobileMessagingCore.getInstance(context).getMessageStoreForGeo();
        List<Message> messages = messageStoreForGeo.findAll(context);
        List<String> messageIdsToDelete = new ArrayList<>(messages.size());
        Date now = new Date();

        for (Message message : messages) {
            Geo geo = message.getGeo();
            if (geo == null) {
                continue;
            }

            List<Area> areasList = geo.getAreasList();
            Date expiryDate = geo.getExpiryDate();

            if (areasList == null || areasList.isEmpty()) {
                continue;
            }

            for (Area area : areasList) {
                if (!area.isValid() || expiryDate == null) {
                    continue;
                }

                if (expiryDate.before(now)) {
                    messageIdsToDelete.add(message.getMessageId());
                }
            }
        }

        if (!messageIdsToDelete.isEmpty()) {
            messageStoreForGeo.deleteByIds(context, messageIdsToDelete.toArray(new String[]{}));
        }
    }

    @SuppressWarnings("WeakerAccess")
    static Tuple<List<Geofence>, Tuple<Date, Date>> calculateGeofencesToMonitorAndNextCheckDates(MessageStore messageStore) {
        Date nextCheckRefreshDate = null;
        Date nextCheckExpireDate = null;
        Map<String, Geofence> geofences = new HashMap<>();
        Map<String, Date> expiryDates = new HashMap<>();
        List<Message> messages = messageStore.findAll(context);

        for (Message message : messages) {
            Geo geo = message.getGeo();
            if (geo == null || geo.getAreasList() == null || geo.getAreasList().isEmpty()) {
                continue;
            }

            nextCheckExpireDate = calculateNextCheckDateForGeoExpiry(geo, nextCheckExpireDate);

            final Set<String> finishedCampaignIds = MobileMessagingCore.getInstance(context).getFinishedCampaignIds();
            if (finishedCampaignIds.contains(geo.getCampaignId())) {
                continue;
            }

            if (geo.isEligibleForMonitoring()) {
                List<Area> geoAreasList = message.getGeo().getAreasList();
                for (Area area : geoAreasList) {
                    if (!area.isValid()) {
                        continue;
                    }

                    Date expiry = expiryDates.get(area.getId());
                    if (expiry != null && expiry.after(geo.getExpiryDate())) {
                        continue;
                    }

                    expiryDates.put(area.getId(), geo.getExpiryDate());
                    geofences.put(area.getId(), area.toGeofence(geo.getExpiryDate()));
                }
            }

            nextCheckRefreshDate = calculateNextCheckDateForGeoStart(geo, nextCheckRefreshDate);
        }

        List<Geofence> geofenceList = new ArrayList<>(geofences.values());
        return new Tuple<>(geofenceList, new Tuple<>(nextCheckRefreshDate, nextCheckExpireDate));
    }

    private static Date calculateNextCheckDateForGeoStart(Geo geo, Date oldCheckDate) {
        Date now = new Date();
        Date expiryDate = geo.getExpiryDate();
        if (expiryDate != null && expiryDate.before(now)) {
            return oldCheckDate;
        }

        Date startDate = geo.getStartDate();
        if (startDate == null || startDate.before(now)) {
            return oldCheckDate;
        }

        if (oldCheckDate != null && oldCheckDate.before(startDate)) {
            return oldCheckDate;
        }

        return startDate;
    }

    private static Date calculateNextCheckDateForGeoExpiry(Geo geo, Date oldCheckDate) {
        Date now = new Date();
        Date expiryDate = geo.getExpiryDate();

        if (expiryDate == null) {
            if (oldCheckDate == null) {
                return null;

            } else {
                return oldCheckDate;
            }
        }

        if (oldCheckDate != null && oldCheckDate.before(expiryDate)) {
            if (oldCheckDate.before(now)) {
                return now;

            } else {
                return oldCheckDate;
            }
        }

        if (expiryDate.before(now)) {
            return now;
        }

        return expiryDate;
    }

    @SuppressWarnings("MissingPermission")
    public void startGeoMonitoring() {

        if (!PlayServicesSupport.isPlayServicesAvailable(context) ||
                !MobileMessagingCore.isGeofencingActivated(context) ||
                // checking this to avoid multiple activation of geofencing API on Play services
                MobileMessagingCore.areAllActiveGeoAreasMonitored(context)) {
            return;
        }

        if (!checkRequiredPermissions()) {
            return;
        }

        Tuple<List<Geofence>, Tuple<Date, Date>> tuple = calculateGeofencesToMonitorAndNextCheckDates(messageStore);
        Date nextRefreshDate = tuple.getRight().getLeft();
        Date nextExpireDate = tuple.getRight().getRight();

        scheduleRefresh(context, nextRefreshDate);
        scheduleExpiry(context, nextExpireDate);

        geofences = tuple.getLeft();
        if (geofences.isEmpty()) {
            return;
        }

        requestType = GoogleApiClientRequestType.ADD_GEOFENCES;
        if (!googleApiClient.isConnected()) {
            googleApiClient.connect();
            return;
        }

        LocationServices.GeofencingApi.addGeofences(googleApiClient, geofencingRequest(), geofencePendingIntent())
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        logGeofenceStatus(status, true);
                        requestType = GoogleApiClientRequestType.NONE;
                        MobileMessagingCore.getInstance(context).setAllActiveGeoAreasMonitored(status.isSuccess());
                    }
                });
    }

    public void stopGeoMonitoring() {

        MobileMessagingCore.getInstance(context).setAllActiveGeoAreasMonitored(false);

        if (!checkRequiredPermissions()) {
            return;
        }

        requestType = GoogleApiClientRequestType.REMOVE_GEOFENCES;
        if (!googleApiClient.isConnected()) {
            googleApiClient.connect();
            return;
        }

        LocationServices.GeofencingApi.removeGeofences(googleApiClient, geofencePendingIntent())
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        logGeofenceStatus(status, false);
                        requestType = GoogleApiClientRequestType.NONE;
                    }
                });
    }

    /**
     * Sets component enabled parameter to enabled or disabled for every required geo component
     *
     * @param context                android context object
     * @param componentsStateEnabled state of the component from {@link PackageManager}
     * @throws ConfigurationException if component is missing in Manifest
     * @see #setComponentEnabledSetting(Context, int, Class)
     */
    public void setGeoComponentsEnabledSettings(Context context, boolean componentsStateEnabled) {
        int state = componentsStateEnabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        setComponentEnabledSetting(context, state, GeofenceTransitionsIntentService.class);
        setComponentEnabledSetting(context, state, GeofencingConsistencyReceiver.class);
        setComponentEnabledSetting(context, state, BootReceiver.class);
    }

    private void setComponentEnabledSetting(Context context, int state, Class componentClass) {
        ComponentName componentName = new ComponentName(context, componentClass);
        try {
            context.getPackageManager().setComponentEnabledSetting(componentName, state, PackageManager.DONT_KILL_APP);
        } catch (Exception e) {
            throw new ConfigurationException(Reason.MISSING_REQUIRED_COMPONENT, componentClass.getCanonicalName());
        }
    }

    public boolean checkRequiredPermissions() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            MobileMessagingLogger.e("Unable to initialize geofencing", new ConfigurationException(Reason.MISSING_REQUIRED_PERMISSION, Manifest.permission.ACCESS_FINE_LOCATION));
            return false;
        }

        return true;
    }

    private void logGeofenceStatus(@NonNull Status status, boolean activated) {
        if (status.isSuccess()) {
            MobileMessagingLogger.d(TAG, "Geofencing monitoring " + (activated ? "" : "de-") + "activated successfully");

        } else {
            MobileMessagingLogger.e(TAG, "Geofencing monitoring " + (activated ? "" : "de-") + "activation failed", new Throwable(status.getStatusMessage()));
        }
    }

    private GeofencingRequest geofencingRequest() {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofences(geofences);
        return builder.build();
    }

    private PendingIntent geofencePendingIntent() {
        if (geofencePendingIntent == null) {
            Intent intent = new Intent(context, GeofenceTransitionsIntentService.class);
            geofencePendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        return geofencePendingIntent;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        MobileMessagingLogger.d(TAG, "GoogleApiClient connected");
        if (GoogleApiClientRequestType.ADD_GEOFENCES.equals(requestType)) {
            startGeoMonitoring();

        } else if (GoogleApiClientRequestType.REMOVE_GEOFENCES.equals(requestType)) {
            stopGeoMonitoring();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        MobileMessagingLogger.e(TAG, connectionResult.getErrorMessage(), new ConfigurationException(Reason.CHECK_LOCATION_SETTINGS));
    }
}
