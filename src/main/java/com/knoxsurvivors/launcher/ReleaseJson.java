package com.knoxsurvivors.launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Strict JSON reader for release metadata; object key order has no meaning. */
final class ReleaseJson {
    private final String text;
    private int at;
    private ReleaseJson(String text) { this.text = text; }
    static Object parse(String text) throws IOException {
        if (text.length() > 4_000_000) throw new IOException("Release metadata is too large.");
        ReleaseJson parser = new ReleaseJson(text);
        Object value = parser.value(0);
        parser.space();
        if (parser.at != text.length()) throw parser.invalid();
        return value;
    }
    private IOException invalid() { return new IOException("Invalid release JSON at " + at); }
    private void space() { while (at < text.length() && " \r\n\t".indexOf(text.charAt(at)) >= 0) at++; }
    private boolean take(char c) { space(); if (at < text.length() && text.charAt(at) == c) { at++; return true; } return false; }
    private void expect(char c) throws IOException { if (!take(c)) throw invalid(); }
    private Object value(int depth) throws IOException {
        if (depth > 64) throw invalid();
        space();
        if (at == text.length()) throw invalid();
        if (take('{')) {
            var map = new LinkedHashMap<String, Object>();
            if (take('}')) return map;
            do {
                String key = string(); expect(':');
                if (map.containsKey(key)) throw invalid();
                map.put(key, value(depth + 1));
            } while (take(','));
            expect('}'); return map;
        }
        if (take('[')) {
            var list = new ArrayList<Object>();
            if (take(']')) return list;
            do { list.add(value(depth + 1)); } while (take(','));
            expect(']'); return list;
        }
        if (text.charAt(at) == '"') return string();
        for (String literal : new String[] { "true", "false", "null" }) {
            if (text.startsWith(literal, at)) {
                at += literal.length();
                return literal.equals("null") ? null : Boolean.valueOf(literal);
            }
        }
        var number = java.util.regex.Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
            .matcher(text).region(at, text.length());
        if (!number.lookingAt()) throw invalid();
        at = number.end(); return number.group(); // Numeric fields are not used by the updater.
    }
    private String string() throws IOException {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (at < text.length()) {
            char c = text.charAt(at++);
            if (c == '"') return result.toString();
            if (c < 32) throw invalid();
            if (c == '\\') {
                if (at == text.length()) throw invalid();
                char escape = text.charAt(at++);
                switch (escape) {
                    case '"', '\\', '/' -> result.append(escape);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> {
                        if (at + 4 > text.length()) throw invalid();
                        try { result.append((char)Integer.parseInt(text.substring(at, at + 4), 16)); }
                        catch (NumberFormatException e) { throw invalid(); }
                        at += 4;
                    }
                    default -> throw invalid();
                }
            } else result.append(c);
        }
        throw invalid();
    }
}
