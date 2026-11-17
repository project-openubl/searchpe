package io.github.projectopenubl.xhandler.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class XmlTextTest {

    @Test
    void decodes_in_the_declared_encoding() {
        String text = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><Invoice>Señor Ñandú</Invoice>";
        byte[] latin1 = text.getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(text, XmlText.decode(latin1));
        // and back to the very same bytes
        assertArrayEquals(latin1, XmlText.decode(latin1).getBytes(StandardCharsets.ISO_8859_1));
        String utf8 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Invoice>Señor</Invoice>";
        assertEquals(utf8, XmlText.decode(utf8.getBytes(StandardCharsets.UTF_8)));
        assertEquals("<Invoice>ñ</Invoice>", XmlText.decode(("﻿<Invoice>ñ</Invoice>").getBytes(StandardCharsets.UTF_8)));
    }
}
