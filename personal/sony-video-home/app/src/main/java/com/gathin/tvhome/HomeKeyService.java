package com.gathin.tvhome;

import android.view.KeyEvent;

/** This personal build filters only the six app shortcuts. Sony handles all other remote buttons. */
public final class HomeKeyService extends ReferenceHomeKeyService {
    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (!KeyLearningActivity.isAppKey(event.getKeyCode(), event.getScanCode())) return false;
        if (KeyLearningActivity.capture(event)) return true;
        if (!KeyLearningActivity.matchesSlot(this, event)) return false;
        return super.onKeyEvent(event);
    }
}
