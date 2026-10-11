package net.timafe.triptale.domain;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One entry link: a URL with an optional display name. Stored in frontmatter either as the bare
 * URL or as {@code "[Name](url)"}. Values that aren't an http(s) URL are kept as-is (lenient, so
 * hand-edited frontmatter is never lost) and flagged via {@link #valid()}.
 *
 * @param url   the URL, or the raw value if it couldn't be parsed
 * @param name  display name, or null
 * @param valid whether {@code url} is an absolute http(s) URL
 */
public record Link(String url, String name, boolean valid) {

    // Non-greedy name, greedy URL anchored at the end, so ".../Bonn_(city))" keeps its parentheses
    private static final Pattern NAMED = Pattern.compile("^\\[(.+?)\\]\\((.+)\\)$");

    /** A link from separately entered url and name; brackets are stripped from the name, a name equal to the URL is dropped. */
    public static Link of(String url, String name) {
        String u = url == null ? "" : url.strip();
        String n = name == null ? "" : name.replace("[", "").replace("]", "").strip();
        return new Link(u, n.isEmpty() || n.equals(u) ? null : n, isHttpUrl(u));
    }

    /** Parses a frontmatter value: a bare URL or {@code [Name](url)}. */
    public static Link parse(String raw) {
        String s = raw == null ? "" : raw.strip();
        Matcher m = NAMED.matcher(s);
        if (m.matches()) return of(m.group(2), m.group(1));
        return new Link(s, null, isHttpUrl(s));
    }

    /** The frontmatter value: {@code [Name](url)} when named, the bare URL otherwise. */
    public String format() {
        return name == null ? url : "[" + name + "](" + url + ")";
    }

    /** Text to show for the link: its name, else the URL's host, else the raw value. */
    public String displayText() {
        if (name != null) return name;
        if (valid) {
            String host = URI.create(url).getHost();
            return host.startsWith("www.") ? host.substring(4) : host;
        }
        return url;
    }

    /** True when the value is a well-formed absolute http(s) URL. */
    public static boolean isHttpUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value.strip());
            String scheme = uri.getScheme();
            return uri.isAbsolute()
                    && scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
