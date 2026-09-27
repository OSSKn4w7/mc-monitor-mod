package com.osskn4w7.mcmonitor.core.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 极简 JSON 实现（零依赖，核心模块必须不依赖任何库，才能跨加载器/跨版本复用）。
// parse 返回：Map<String,Object> / List<Object> / String / Long|Double / Boolean / null
public final class MiniJson {

    private MiniJson() {}

    // ---------- 解析 ----------
    public static Object parse(String text) {
        P p = new P(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (p.i < p.s.length()) throw new IllegalArgumentException("JSON 末尾有多余内容");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object o = parse(text);
        if (!(o instanceof Map)) throw new IllegalArgumentException("JSON 顶层必须是对象");
        return (Map<String, Object>) o;
    }

    private static final class P {
        final String s;
        int i;

        P(String s) { this.s = s; }

        void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object value() {
            if (i >= s.length()) throw err("意外的结尾");
            char c = s.charAt(i);
            switch (c) {
                case '{': return obj();
                case '[': return arr();
                case '"': return str();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default: return num();
            }
        }

        Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            skipWs();
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            while (true) {
                skipWs();
                if (i >= s.length() || s.charAt(i) != '"') throw err("对象键必须是字符串");
                String k = str();
                skipWs();
                if (i >= s.length() || s.charAt(i) != ':') throw err("缺少冒号");
                i++;
                skipWs();
                m.put(k, value());
                skipWs();
                if (i >= s.length()) throw err("对象未闭合");
                char c = s.charAt(i++);
                if (c == '}') return m;
                if (c != ',') throw err("对象分隔符错误");
            }
        }

        List<Object> arr() {
            List<Object> l = new ArrayList<>();
            i++; // [
            skipWs();
            if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
            while (true) {
                skipWs();
                l.add(value());
                skipWs();
                if (i >= s.length()) throw err("数组未闭合");
                char c = s.charAt(i++);
                if (c == ']') return l;
                if (c != ',') throw err("数组分隔符错误");
            }
        }

        String str() {
            i++; // "
            StringBuilder b = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw err("字符串未闭合");
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c == '\\') {
                    if (i >= s.length()) throw err("转义意外结尾");
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"' -> b.append('"');
                        case '\\' -> b.append('\\');
                        case '/' -> b.append('/');
                        case 'n' -> b.append('\n');
                        case 'r' -> b.append('\r');
                        case 't' -> b.append('\t');
                        case 'b' -> b.append('\b');
                        case 'f' -> b.append('\f');
                        case 'u' -> {
                            if (i + 4 > s.length()) throw err("\\u 转义不完整");
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw err("未知转义 \\" + e);
                    }
                } else {
                    b.append(c);
                }
            }
        }

        Object num() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String t = s.substring(start, i);
            if (t.isEmpty()) throw err("非法值");
            if (t.indexOf('.') < 0 && t.indexOf('e') < 0 && t.indexOf('E') < 0) {
                try { return Long.parseLong(t); } catch (NumberFormatException ignore) { }
            }
            try { return Double.parseDouble(t); } catch (NumberFormatException e) { throw err("非法数字 " + t); }
        }

        void expect(String word) {
            if (!s.startsWith(word, i)) throw err("非法字面量");
            i += word.length();
        }

        IllegalArgumentException err(String msg) { return new IllegalArgumentException("JSON 解析失败@" + i + ": " + msg); }
    }

    // ---------- 序列化 ----------
    public static String write(Object o) {
        StringBuilder b = new StringBuilder();
        writeValue(b, o);
        return b.toString();
    }

    private static void writeValue(StringBuilder b, Object o) {
        if (o == null) { b.append("null"); return; }
        if (o instanceof String s) { writeString(b, s); return; }
        if (o instanceof Boolean v) { b.append(v); return; }
        if (o instanceof Number n) { b.append(n); return; }
        if (o instanceof Map<?, ?> m) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) b.append(',');
                first = false;
                writeString(b, String.valueOf(e.getKey()));
                b.append(':');
                writeValue(b, e.getValue());
            }
            b.append('}');
            return;
        }
        if (o instanceof List<?> l) {
            b.append('[');
            boolean first = true;
            for (Object v : l) {
                if (!first) b.append(',');
                first = false;
                writeValue(b, v);
            }
            b.append(']');
            return;
        }
        writeString(b, String.valueOf(o));
    }

    private static void writeString(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                case '\b' -> b.append("\\b");
                case '\f' -> b.append("\\f");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        b.append('"');
    }
}
