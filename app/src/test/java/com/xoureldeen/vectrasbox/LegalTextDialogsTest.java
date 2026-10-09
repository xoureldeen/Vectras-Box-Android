package com.xoureldeen.vectrasbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class LegalTextDialogsTest {
    @Test public void emptyDocumentHasOnePage() {
        assertEquals(List.of(""), LegalTextDialogs.splitPages(""));
    }

    @Test public void shortDocumentIsUnchanged() {
        String text = "GNU GPL\nCopyright notice\n";
        assertEquals(List.of(text), LegalTextDialogs.splitPages(text));
    }

    @Test public void longDocumentIsPreservedExactly() {
        String text = "Copyright and license notice\n".repeat(10000);
        List<String> pages = LegalTextDialogs.splitPages(text);
        assertTrue(pages.size() > 1);
        assertEquals(text, String.join("", pages));
        for (String page : pages) assertTrue(page.length() <= 32000 && page.endsWith("\n"));
    }

    @Test public void longLineIsSplitWithoutDroppingText() {
        String text = "x".repeat(96001);
        List<String> pages = LegalTextDialogs.splitPages(text);
        assertEquals(4, pages.size());
        assertEquals(text, String.join("", pages));
    }

    @Test public void unicodeCharacterIsNotSplitBetweenPages() {
        String text = "x".repeat(31999) + "\ud83d\udcbb" + "y".repeat(32000);
        List<String> pages = LegalTextDialogs.splitPages(text);
        assertEquals(text, String.join("", pages));
        for (String page : pages) {
            assertFalse(Character.isHighSurrogate(page.charAt(page.length() - 1)));
            assertFalse(Character.isLowSurrogate(page.charAt(0)));
        }
    }
}
