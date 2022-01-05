package com.mmp.android.mpmetrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

public class DeepLinkReferral {
    Referrer ref = Referrer.getInstance();
    

    public void SetData(Uri Uri) {

//        ref.setReffer(Uri.toString());
        ref.setReffer(Uri.getQuery());
       ref.setUtm_source(Uri.getQueryParameter("utm_source"));
        ref.setUtm_medium(Uri.getQueryParameter("utm_medium"));
       ref.setUtm_campaign(Uri.getQueryParameter("utm_campaign"));
       ref.setClick_id(Uri.getQueryParameter("click_id"));




    }

}
