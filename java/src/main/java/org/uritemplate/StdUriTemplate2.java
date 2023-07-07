package org.uritemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StdUriTemplate2 {

    // Public API
    // the substitutions map accepts: String, List<String> and Map<String, String>
    public static String expand(final String template, final Map<String, Object> substitutions) {
        System.err.println("SUBSTITUTIONS: " + substitutions.keySet().stream().collect(Collectors.joining(",")));
        return expandImpl(template, substitutions);
    }

    private static String expandImpl(String str, Map<String, Object> substitutions) {
        StringBuilder result = new StringBuilder();

        StringBuilder token = null;
        List<String> tokens = null;
        for (var byt : str.getBytes()) {
            char character = (char) byt;

            switch (character) {
                case '{':
                    token = new StringBuilder();
                    tokens = new ArrayList<>();
                    break;
                case '}':
                    if (token != null) {
                        tokens.add(token.toString());
                        token = null;
                        result.append(expandTokens(tokens, substitutions));
                    } else {
                        throw new RuntimeException("Failed to expand token, invalid.");
                    }
                    break;
                case ',':
                    if (token != null) {
                        tokens.add(token.toString());
                        token = new StringBuilder();
                        break;
                    };
                    // Intentional fall-through for commas outside the {}
                default:
                    if (token != null) {
                        token.append(character);
                    } else {
                        result.append(character);
                    }
                    break;
            }
        }

        return result.toString();
    }

    static final class NoMod extends Modifier {
        NoMod(String token) { super(token); }

        @Override
        String key() {
            return sanitized;
        }
    }

    static final class Plus extends Modifier {
        Plus(String token) { super(token); }

        @Override
        String expand(String key, String value) {
            return trim(value.replace(" ", "%20"), maxChar);
        }
    }

    static final class Dash extends Modifier {
        Dash(String token) { super(token); }

        @Override
        String prefix() {
            return "#";
        }
        @Override
        String expand(String key, String value) {
            return trim(value.replace(" ", "%20"), maxChar);
        }
    }

    static final class Dot extends Modifier {
        Dot(String token) { super(token); }

        @Override
        char separator() {
            return '.';
        }
        @Override
        String prefix() {
            return ".";
        }
    }

    static final class Slash extends Modifier {
        Slash(String token) { super(token); }

        @Override
        char separator() {
            return '/';
        }
        @Override
        String prefix() {
            return "/";
        }
    }

    static final class Semicolon extends Modifier {
        Semicolon(String token) { super(token); }

        @Override
        char separator() {
            return ';';
        }
        @Override
        String prefix() {
            return ";";
        }
        @Override
        String expand(String key, String value) {
            var encoded = expandValue(value, maxChar);
            if (encoded != null && !encoded.isEmpty()) {
                return key + "=" + encoded;
            } else {
                return key;
            }
        }
    }

    static final class QuestionMark extends Modifier {
        QuestionMark(String token) { super(token); }

        @Override
        char separator() {
            return '&';
        }
        @Override
        String prefix() {
            return "?";
        }
        @Override
        String expand(String key, String value) {
            return expandKV(key, value, maxChar);
        }
    }

    static final class At extends Modifier {
        At(String token) { super(token); }

        @Override
        char separator() {
            return '&';
        }
        @Override
        String prefix() {
            return "&";
        }
        @Override
        String expand(String key, String value) {
            return expandKV(key, value, maxChar);
        }
    }

    static class Modifier {
        protected final String token;
        protected final String sanitized;
        protected final int maxChar;
        protected final boolean composite;

        Modifier(String token) {
            this.token = token;

            var sanitized = token;
            int suffixIndex = token.indexOf(':');
            if (suffixIndex != -1) {
                sanitized = token.substring(0, suffixIndex);
                maxChar = Integer.parseInt(token.substring(suffixIndex + 1));
            } else {
                maxChar = -1;
            }

            suffixIndex = token.indexOf('*');
            if (suffixIndex != -1) {
                sanitized = token.substring(0, suffixIndex);
                composite = true;
            } else {
                composite = false;
            }

            this.sanitized = sanitized;
        }

        String expand(String key, String value) {
            return expandValue(value, maxChar);
        }

        String key() {
            return sanitized.substring(1);
        }

        String sanitized() {
            return sanitized;
        }

        boolean composite() {
            return composite;
        }

        int maxChar() {
            return maxChar;
        }

        char separator() {
            return ',';
        }

        String prefix() {
            return "";
        }
    }

    static String expandKV(String key, String value, int maxChar) {
        return key + "=" + expandValue(value, maxChar);
    }

    static String trim(String value, int maxChar) {
        return (maxChar < 0) ? value : value.substring(0, Math.min(maxChar, value.length()));
    }

    static String expandValue(String value, int maxChar) {
        return URLEncoder.encode(trim(value, maxChar), StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    public static Modifier getModifier(String token) {
        if (token.trim().length() <= 0) {
            return new NoMod(token);
        } else {
            switch (token.charAt(0)) {
                case '+':
                    return new Plus(token);
                case '#':
                    return new Dash(token);
                case '.':
                    return new Dot(token);
                case '/':
                    return new Slash(token);
                case ';':
                    return new Semicolon(token);
                case '?':
                    return new QuestionMark(token);
                case '&':
                    return new At(token);
                default:
                    return new NoMod(token);
            }
        }
    }

    private static String expandTokens(List<String> tokens, Map<String, Object> substitutions) {
        StringBuilder result = new StringBuilder();

        Modifier firstMod = null;
        for (var token: tokens) {
            System.err.println("DEBUG " + firstMod + " -> token: " + token);

            var mod = getModifier(token);
            if (firstMod == null) {
                firstMod = mod;
                result.append(mod.prefix());
            } else {
                result.append(firstMod.separator());
            }

            if (substitutions.containsKey(mod.key())) {
                Object value = substitutions.get(mod.key());
                System.err.println("value is " + value);

                if (value instanceof String) {
                    result.append(firstMod.expand(mod.key(), (String) value));
                } else if (value instanceof List) {
                    boolean first = true;
                    for (var subst: (List<String>)value) {
                        if (first) {
                            first = false;
                        } else {
                            if (mod.composite) {
                                result.append(firstMod.separator());
                            } else {
                                result.append(',');
                            }
                        }
                        result.append(firstMod.expand(mod.key(), subst));
                    }
                } else if (value instanceof Map) {
                    boolean first = true;
                    for (var subst: ((Map<String, String>) value).entrySet()) {
                        if (first) {
                            first = false;
                        } else {
                            if (mod.composite) {
                                result.append(firstMod.separator());
                            } else {
                                result.append(',');
                            }
                        }
                        result.append(firstMod.expand(mod.key(), subst.getKey()));
                        if (mod.composite) {
                            result.append('=');
                        } else {
                            result.append(',');
                        }
                        result.append(firstMod.expand(mod.key(), subst.getValue()));
                    }
                } else {
                    throw new IllegalArgumentException("Substitution type not supported, found " + value.getClass() + ", but only String, List<String> and Map<String, String> are allowed.");
                }
            }
        }

        return result.toString();
    }

}
