package com.sangluo.onestep.feature.embedding;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

public class EmbeddedStartEpochStoreTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void incrementsPersistedEpochAcrossInstances() throws Exception {
        File stateDirectory = temporaryFolder.newFolder("state");
        EmbeddedStartEpochStore firstStore = new EmbeddedStartEpochStore(stateDirectory);
        EmbeddedStartEpochStore secondStore = new EmbeddedStartEpochStore(stateDirectory);

        assertEquals(1, firstStore.beginSession());
        assertEquals(2, secondStore.beginSession());
        assertEquals("2", new String(Files.readAllBytes(
                new File(secondStore.getFilePath()).toPath()), StandardCharsets.UTF_8).trim());
    }
}
