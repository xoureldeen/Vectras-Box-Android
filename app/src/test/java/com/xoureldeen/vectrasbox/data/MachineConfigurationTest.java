package com.xoureldeen.vectrasbox.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class MachineConfigurationTest {
    @Test public void validMachineSectionIsAccepted() throws Exception {
        MachineConfiguration.validate("[General]\r\n\t[Machine] \r\nmachine = example\r\n");
    }

    @Test public void sectionInsideCommentOrValueIsNotAccepted() {
        for (String config : new String[]{null, "", "; [Machine]\n", "[General]\nvalue = [Machine]\n"})
            assertThrows(java.io.IOException.class, () -> MachineConfiguration.validate(config));
    }

    @Test public void nullBytesAndOversizedConfigurationsAreRejected() {
        assertThrows(java.io.IOException.class, () -> MachineConfiguration.validate("[Machine]\nname = bad\0"));
        assertThrows(java.io.IOException.class, () -> MachineConfiguration.validate("[Machine]\n" + "x".repeat(2 * 1024 * 1024)));
    }
    @Test public void newConfigurationUsesFormDefaults() {
        assertEquals("[Machine]\nmem_size = 131072\n", MachineConfiguration.mergeForm("", "", "[Machine]\nmem_size = 131072\n"));
    }

    @Test public void unchangedFormPreservesAdvancedConfiguration() {
        String config = "[Machine]\nmem_size = 131072\n; custom board setting\ncustom = 7\n[Hard disks]\nhdd_01_parameters = 63, 16, 1024, 0, scsi\n[Network]\nnet_01_card = ne2000\n";
        String form = "[Machine]\nmem_size = 131072\n[Hard disks]\nhdd_01_parameters = 63, 16, 1024, 0, ide\n";
        assertEquals(config, MachineConfiguration.mergeForm(config, form, form));
    }

    @Test public void onlyChangedFormFieldsAreUpdated() {
        String config = "[Machine]\nmem_size = 131072\ncpu_multi = 4\n[Custom device]\nvalue = yes\n";
        String result = MachineConfiguration.mergeForm(config, "[Machine]\nmem_size = 131072\n", "[Machine]\nmem_size = 262144\n");
        assertTrue(result.contains("mem_size = 262144"));
        assertTrue(result.contains("cpu_multi = 4"));
        assertTrue(result.contains("[Custom device]\nvalue = yes"));
        assertFalse(result.contains("mem_size = 131072"));
    }

    @Test public void removedMediaDoesNotLeaveDuplicatePaths() {
        String config = "[Machine]\n[Floppy and CD-ROM drives]\nfdd_01_fn = first.img\nfdd_01_fn = second.img\nfdd_02_fn = other.img\n";
        String result = MachineConfiguration.mergeForm(config, "[Floppy and CD-ROM drives]\nfdd_01_fn = second.img\n", "[Floppy and CD-ROM drives]\n");
        assertFalse(result.contains("fdd_01_fn"));
        assertTrue(result.contains("fdd_02_fn = other.img"));
    }

    @Test public void newSettingIsAddedToTheCorrectSection() {
        String result = MachineConfiguration.mergeForm("[Machine]\nmachine = example\n[Video]\ngfxcard = vga\n", "[Machine]\n", "[Machine]\nfpu_softfloat = 1\n");
        assertTrue(result.indexOf("fpu_softfloat = 1") < result.indexOf("[Video]"));
    }

    @Test public void natAndMuntChangesPreserveOtherDeviceSettings() {
        String config = "[Machine]\nmachine = example\n[Network]\nnet_01_card = none\nnet_01_net_type = none\nnet_02_card = ne2k\n[Sound]\nmidi_device = none\nsndcard = sb16\n";
        String before = "[Network]\nnet_01_card = none\nnet_01_net_type = none\n[Sound]\nmidi_device = none\n";
        String after = "[Network]\nnet_01_card = rtl8139c+\nnet_01_net_type = slirp\n[Sound]\nmidi_device = mt32\n";
        String result = MachineConfiguration.mergeForm(config, before, after);
        assertTrue(result.contains("net_01_card = rtl8139c+"));
        assertTrue(result.contains("net_01_net_type = slirp"));
        assertTrue(result.contains("midi_device = mt32"));
        assertTrue(result.contains("net_02_card = ne2k"));
        assertTrue(result.contains("sndcard = sb16"));
    }
}
