package org.infobip.mobile.messaging.tasks;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import org.infobip.mobile.messaging.Event;
import org.infobip.mobile.messaging.MobileMessaging;
import org.infobip.mobile.messaging.api.registration.RegistrationResponse;
import org.infobip.mobile.messaging.telephony.MobileNetworkInfo;

/**
 * @author mstipanov
 * @since 03.03.2016.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class UpsertRegistrationTask extends AsyncTask<Object, Void, RegistrationResponse> {
    private final Context context;

    public UpsertRegistrationTask(Context context) {
        this.context = context;
    }

    @Override
    protected RegistrationResponse doInBackground(Object... notUsed) {
        MobileMessaging mobileMessaging = MobileMessaging.getInstance(context);
        try {
            String deviceApplicationInstanceId = mobileMessaging.getDeviceApplicationInstanceId();
            String registrationId = mobileMessaging.getRegistrationId();
            MobileNetworkInfo mobileNetworkInfo = MobileNetworkInfo.fromProperties(context);
            return MobileApiResourceProvider.INSTANCE.getMobileApiRegistration(context).upsert(deviceApplicationInstanceId,
                    registrationId, mobileNetworkInfo.getCarrierName(), mobileNetworkInfo.getMCC(), mobileNetworkInfo.getMNC());
        } catch (Exception e) {
            mobileMessaging.setLastHttpException(e);
            Log.e(MobileMessaging.TAG, "Error creating registration!", e);
            cancel(true);

            Intent registrationSaveError = new Intent(Event.API_COMMUNICATION_ERROR.getKey());
            registrationSaveError.putExtra("exception", e);
            context.sendBroadcast(registrationSaveError);
            LocalBroadcastManager.getInstance(context).sendBroadcast(registrationSaveError);

            return null;
        }
    }
}
