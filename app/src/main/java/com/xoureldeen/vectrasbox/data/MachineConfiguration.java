package com.xoureldeen.vectrasbox.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class MachineConfiguration {
    private MachineConfiguration() { }

    public static void validate(String text) throws IOException {
        if (text == null || text.indexOf('\0') >= 0
                || text.getBytes(StandardCharsets.UTF_8).length > 2 * 1024 * 1024
                || !Pattern.compile("(?m)^[ \\t]*\\[Machine\\][ \\t]*\\r?$").matcher(text).find())
            throw new IOException("A valid PCBox configuration must contain a [Machine] section and fit within 2 MB.");
    }

    public static String mergeForm(String original, String before, String after) {
        if (original == null || original.trim().isEmpty()) return after;
        Map<String, String> oldValues = entries(before);
        Map<String, String> newValues = entries(after);
        List<String> lines = new ArrayList<>(Arrays.asList(original.replace("\r\n", "\n").split("\n", -1)));
        for (String key : oldValues.keySet()) {
            if (!newValues.containsKey(key)) replace(lines, key, null);
        }
        for (Map.Entry<String, String> entry : newValues.entrySet()) {
            if (!entry.getValue().equals(oldValues.get(entry.getKey())))
                replace(lines, entry.getKey(), entry.getValue());
        }
        return String.join("\n", lines);
    }

    private static Map<String, String> entries(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        String section = "";
        for (String line : text.split("\\r?\\n")) {
            String value = line.trim();
            if (value.startsWith("[") && value.endsWith("]")) section = value.substring(1, value.length() - 1);
            else if (!value.startsWith(";") && !value.startsWith("#")) {
                int equals = value.indexOf('=');
                if (equals > 0) result.put(section + "\0" + value.substring(0, equals).trim(), value.substring(equals + 1).trim());
            }
        }
        return result;
    }

    private static void replace(List<String> lines, String entry, String value) {
        String[] parts = entry.split("\0", -1);
        String current = "";
        int insertion = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("[") && line.endsWith("]")) current = line.substring(1, line.length() - 1);
            else if (current.equals(parts[0]) && !line.startsWith(";") && !line.startsWith("#")) {
                int equals = line.indexOf('=');
                if (equals > 0 && line.substring(0, equals).trim().equals(parts[1])) {
                    lines.remove(i--);
                    continue;
                }
            }
            if (current.equals(parts[0])) insertion = i + 1;
        }
        if (value == null) return;
        if (insertion < 0) {
            lines.add("");
            lines.add("[" + parts[0] + "]");
            insertion = lines.size();
        }
        lines.add(insertion, parts[1] + " = " + value);
    }
}
