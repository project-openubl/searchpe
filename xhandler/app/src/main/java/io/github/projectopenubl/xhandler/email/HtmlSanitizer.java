package io.github.projectopenubl.xhandler.email;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allow-list sanitizer for tenant email templates sent from the platform's domain (DESIGN.md §26.5):
 * formatting tags only, links to http(s)/mailto only, no attributes but href; anything else is
 * escaped. No scripts, forms, frames, styles or event handlers.
 */
final class HtmlSanitizer {

    static final Set<String> TAGS = Set.of("p", "br", "b", "strong", "i", "em", "u", "a", "ul", "ol", "li", "span", "div", "table",
            "thead", "tbody", "tr", "td", "th", "h1", "h2", "h3", "h4", "hr", "small");
    static final Pattern TAG = Pattern.compile("<\\s*(/?)\\s*([a-zA-Z0-9]+)([^>]*)>");
    static final Pattern HREF = Pattern.compile("href\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    static final Map<Character, String> ESCAPES = Map.of('<', "&lt;", '>', "&gt;", '"', "&quot;");

    private HtmlSanitizer() {}

    static String sanitize(String html) {
        StringBuilder out = new StringBuilder();
        Matcher m = TAG.matcher(html);
        int last = 0;
        while (m.find()) {
            out.append(escapeText(html.substring(last, m.start())));
            String closing = m.group(1);
            String tag = m.group(2).toLowerCase(java.util.Locale.ROOT);
            if (!TAGS.contains(tag)) {
                out.append(escapeText(m.group()));
            } else if ("a".equals(tag) && closing.isEmpty()) {
                Matcher href = HREF.matcher(m.group(3));
                String url = href.find() ? href.group(1) : "";
                out.append(url.matches("(?i)(https?://|mailto:)[^\\s\"'<>]*") ? "<a href=\"" + url + "\">" : "<a>");
            } else {
                out.append('<').append(closing).append(tag).append('>');
            }
            last = m.end();
        }
        out.append(escapeText(html.substring(last)));
        return out.toString();
    }

    /** Escapes markup characters but keeps entities already escaped (&amp; stays &amp;). */
    private static String escapeText(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            out.append(ESCAPES.getOrDefault(c, String.valueOf(c)));
        }
        return out.toString();
    }
}
