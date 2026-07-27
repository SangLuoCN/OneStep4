package com.sangluo.onestep.ui.format;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DurationFormatterTest {
    @Test
    public void formatsDurationsWithAndWithoutHours() {
        assertEquals("1:05", DurationFormatter.formatDuration(65_000));
        assertEquals("1:01:01", DurationFormatter.formatDuration(3_661_000));
        assertEquals("0:01.234", DurationFormatter.formatDurationWithMilliseconds(1_234));
    }

    @Test
    public void addsMillisecondsOnlyWhenMissing() {
        assertEquals("1:23.000", DurationFormatter.ensureMilliseconds("1:23"));
        assertEquals("1:23.456", DurationFormatter.ensureMilliseconds("1:23.456"));
    }

    @Test
    public void extractsRecordingDurationFromNotificationText() {
        assertEquals("12:34", DurationFormatter.findRecordingDuration("录音中 12:34"));
        assertEquals("1:02:03", DurationFormatter.findRecordingDuration("时长 1:02:03"));
        assertEquals("0:00", DurationFormatter.findRecordingDuration("录音中"));
    }
}
