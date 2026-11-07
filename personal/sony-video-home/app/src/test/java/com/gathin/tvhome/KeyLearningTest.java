package com.gathin.tvhome;

import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.*;

public class KeyLearningTest {
    @Test public void appButtonsCanBeLearned() {
        assertTrue(KeyLearningActivity.isAppKey(KeyEvent.KEYCODE_BUTTON_9, 485));
        assertTrue(KeyLearningActivity.isAppKey(KeyEvent.KEYCODE_UNKNOWN, 492));
        assertTrue(KeyLearningActivity.isAppKey(KeyEvent.KEYCODE_BUTTON_1, 0));
        assertFalse(KeyLearningActivity.isAppKey(KeyEvent.KEYCODE_UNKNOWN, 0));
    }

    @Test public void nativeControlsCannotBeLearnedEvenWithAnAppScancode() {
        int[] nativeKeys = {KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
                KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_SETTINGS,
                KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_SEARCH};
        for (int code : nativeKeys) assertFalse("Native key " + code, KeyLearningActivity.isAppKey(code, 485));
    }
}
