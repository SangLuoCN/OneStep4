package com.sangluo.onestep.feature.logging;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class SessionLogRecorderTest {
    @Test
    public void exportFileNameContainsTimestampAndTxtExtension() {
        String fileName = SessionLogRecorder.createExportFileName(1_785_220_982_123L);

        assertTrue(fileName.matches("OneStep4-log-\\d{8}-\\d{6}-\\d{3}\\.txt"));
    }
}
