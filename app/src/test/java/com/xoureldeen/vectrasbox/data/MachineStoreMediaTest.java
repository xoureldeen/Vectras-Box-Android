package com.xoureldeen.vectrasbox.data;

import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class MachineStoreMediaTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private File image(long size) throws Exception {
        File file = temporary.newFile();
        try (RandomAccessFile output = new RandomAccessFile(file, "rw")) { output.setLength(size); }
        return file;
    }

    @Test public void standardFloppySizesAreAccepted() throws Exception {
        for (int size : new int[]{160, 180, 360, 720, 1200, 1440, 2880})
            MachineStore.validateFloppyImage(image(size * 1024L));
    }

    @Test public void emptyAndUndersizedImagesAreRejected() throws Exception {
        for (long size : new long[]{0, 512, 159 * 1024L}) {
            File file = image(size);
            assertThrows(java.io.IOException.class, () -> MachineStore.validateFloppyImage(file));
        }
    }

    @Test public void oversizedAndMisalignedImagesAreRejected() throws Exception {
        for (long size : new long[]{5 * 1024 * 1024L, 1440 * 1024L + 1}) {
            File file = image(size);
            assertThrows(java.io.IOException.class, () -> MachineStore.validateFloppyImage(file));
        }
    }

    @Test public void containerDisksAreNotAcceptedAsFloppies() throws Exception {
        File file = image(1440 * 1024L);
        try (RandomAccessFile output = new RandomAccessFile(file, "rw")) {
            output.write("vhdxfile".getBytes(StandardCharsets.US_ASCII));
        }
        assertThrows(java.io.IOException.class, () -> MachineStore.validateFloppyImage(file));
    }

    @Test public void fixedVhdFooterIsRejected() throws Exception {
        File file = image(1440 * 1024L);
        try (RandomAccessFile output = new RandomAccessFile(file, "rw")) {
            output.seek(output.length() - 512);
            output.write("conectix".getBytes(StandardCharsets.US_ASCII));
        }
        assertThrows(java.io.IOException.class, () -> MachineStore.validateFloppyImage(file));
    }
}
