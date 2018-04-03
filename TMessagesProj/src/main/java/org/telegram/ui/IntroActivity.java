/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.telegram.messenger.R;

public class IntroActivity extends Activity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_TMessages);
        super.onCreate(savedInstanceState);
        Intent intent2 = new Intent(IntroActivity.this, LaunchActivity.class);
        intent2.putExtra("fromIntro", true);
        startActivity(intent2);
        finish();
    }
}
