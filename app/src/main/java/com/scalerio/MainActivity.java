package com.scalerio;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.mmp.android.mpmetrics.MmpAPI;

public class MainActivity extends AppCompatActivity {

    MmpAPI MmpApi;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }
}