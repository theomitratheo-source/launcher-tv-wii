package com.wii.launcher;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {
    private WiiView view;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        view = new WiiView(this);
        setContentView(view);
    }

    @Override protected void onResume() {
        super.onResume();
        view.reload();
    }

    @Override public void onBackPressed() { /* un launcher ne quitte jamais */ }
}
