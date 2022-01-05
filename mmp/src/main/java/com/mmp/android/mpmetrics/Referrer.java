package com.mmp.android.mpmetrics;

public class Referrer {
    private String reffer;


    private  String utm_medium;
    private  String utm_campaign;
    private  String utm_source;
    private String click_id;



    private static Referrer single_instance = null;


    public static Referrer getInstance()
    {
        if (single_instance == null)
            single_instance = new Referrer();

        return single_instance;
    }

    public String getReffer() {
        return reffer;
    }

    public void setReffer(String reffer) {
        this.reffer = reffer;
    }


    public String getClick_id() {
        return click_id;
    }

    public void setClick_id(String click_id) {
        this.click_id = click_id;
    }
    public String getUtm_medium() {
        return utm_medium;
    }

    public void setUtm_medium(String utm_medium) {
        this.utm_medium = utm_medium;
    }

    public String getUtm_campaign() {
        return utm_campaign;
    }

    public void setUtm_campaign(String utm_campaign) {
        this.utm_campaign = utm_campaign;
    }

    public String getUtm_source() {
        return utm_source;
    }

    public void setUtm_source(String utm_source) {
        this.utm_source = utm_source;
    }


}
