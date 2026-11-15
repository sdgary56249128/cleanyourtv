package com.gathin.tvhome;

import android.os.Bundle;

public final class FontClockActivity extends ClockActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FontChoice.apply(getWindow().getDecorView(), FontChoice.face(this, FontChoice.selected(this)));
    }
}
