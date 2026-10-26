package io.zhijun.ratelimit.druid;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Derives `sql-table:{name}` selectors from SQL text (best-effort). */
final class JdbcSqlTableSelectors {
    private static final Pattern TABLE =
            Pattern.compile("(?:\\bfrom\\b|\\bjoin\\b|\\binto\\b|\\bupdate\\b)\\s+([`\"\\[]?)([a-zA-Z0-9_]+)\\1");

    private JdbcSqlTableSelectors() {}

    static List<String> selectors(String sql) {
        if (sql == null || sql.isBlank()) {
            return List.of();
        }
        String normalized = sql.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        Set<String> selectors = new LinkedHashSet<>();
        Matcher matcher = TABLE.matcher(normalized);
        while (matcher.find()) {
            selectors.add("sql-table:" + matcher.group(2));
        }
        return new ArrayList<>(selectors);
    }
}
