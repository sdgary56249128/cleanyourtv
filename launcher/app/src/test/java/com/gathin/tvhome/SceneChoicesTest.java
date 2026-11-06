package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SceneChoicesTest {
    @Test public void noChoiceAndInvalidSavedValuesKeepTheTheme() {
        assertEquals(2, Config.chosenRange(-1, 2, 0, 1440));
        assertEquals(30, Config.chosenRange(1441, 30, 1, 1440));
        assertEquals(15, Config.chosenRange(100, 15, 0, 90));
    }

    @Test public void disablingTheIdleClockIsValidButZeroIsNotARotationInterval() {
        assertEquals(0, Config.chosenRange(0, 2, 0, 1440));
        assertEquals(30, Config.chosenRange(0, 30, 1, 1440));
    }

    @Test public void brightnessAndMinuteBoundariesAreAccepted() {
        assertEquals(0, Config.chosenRange(0, 15, 0, 90));
        assertEquals(90, Config.chosenRange(90, 15, 0, 90));
        assertEquals(1440, Config.chosenRange(1440, 2, 0, 1440));
        assertEquals(5, Config.chosenRange(5, 30, 1, 1440));
    }
}
