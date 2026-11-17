package io.github.projectopenubl.xhandler.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HtmlSanitizerTest {

    @Test
    void formatting_stays_and_everything_else_is_escaped() {
        assertEquals("<p>Hola <b>Ana</b></p>", HtmlSanitizer.sanitize("<p>Hola <b>Ana</b></p>"));
        assertEquals("<a href=\"https://x.pe/doc\">ver</a>", HtmlSanitizer.sanitize("<a href=\"https://x.pe/doc\" onclick=\"evil()\">ver</a>"));
        assertEquals("<a>x</a>", HtmlSanitizer.sanitize("<a href=\"javascript:alert(1)\">x</a>"));
        assertFalse(HtmlSanitizer.sanitize("<script>alert(1)</script>").contains("<script"));
        assertFalse(HtmlSanitizer.sanitize("<img src=x onerror=alert(1)>").contains("<img"));
        assertFalse(HtmlSanitizer.sanitize("<form action=https://evil><input name=p></form>").contains("<form"));
        assertEquals("<p>", HtmlSanitizer.sanitize("<p style=\"x\">"), "attributes other than href are dropped");
    }

    @Test
    void the_default_template_survives() {
        String body = HtmlSanitizer.sanitize(DocumentEmailJob.DEFAULT_BODY.replace("{link}", "https://x.pe/d/abc"));
        assertTrue(body.contains("<a href=\"https://x.pe/d/abc\">"), body);
    }
}
