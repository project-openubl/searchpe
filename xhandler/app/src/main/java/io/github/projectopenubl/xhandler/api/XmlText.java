package io.github.projectopenubl.xhandler.api;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XML bytes to text in the encoding the document declares (SUNAT documents are UTF-8 or
 * ISO-8859-1). The engine turns the text back into the same bytes, so signatures and digests
 * survive the round trip; decoding ISO-8859-1 as UTF-8 would not.
 */
public final class XmlText {

    private static final Pattern DECLARED = Pattern.compile("^\\s*<\\?xml[^>]*encoding\\s*=\\s*[\"']([A-Za-z0-9._-]+)[\"']");

    private XmlText() {}

    public static String decode(byte[] bytes) {
        int offset = bytes.length >= 3 && (bytes[0] & 0xff) == 0xEF && (bytes[1] & 0xff) == 0xBB && (bytes[2] & 0xff) == 0xBF ? 3 : 0;
        String head = new String(bytes, offset, Math.min(bytes.length - offset, 200), StandardCharsets.ISO_8859_1);
        Matcher m = DECLARED.matcher(head);
        Charset charset = StandardCharsets.UTF_8;
        if (m.find()) {
            String name = m.group(1).toUpperCase();
            if (name.equals("ISO-8859-1") || name.equals("LATIN1") || name.equals("ISO8859-1")) {
                charset = StandardCharsets.ISO_8859_1;
            } else if (name.equals("WINDOWS-1252") || name.equals("CP1252")) {
                charset = Charset.forName("windows-1252");
            }
        }
        return new String(bytes, offset, bytes.length - offset, charset);
    }
}
