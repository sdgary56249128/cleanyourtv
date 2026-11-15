package com.gathin.tvhome;

public final class ClockDream extends ReferenceClockDream {
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        FontChoice.apply(getWindow().getDecorView(), FontChoice.face(this, FontChoice.selected(this)));
    }
}
