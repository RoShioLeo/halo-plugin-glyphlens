// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class UnicodeInput {
    private UnicodeInput() {}
    static String decode(String input) {
        if (input == null) return "";
        String value = input.trim();
        String number = value.matches("(?i)^(U\\+|0x)[0-9a-f]{1,6}$") ? value.substring(2)
            : value.matches("(?i)^[0-9a-f]{4,6}$") ? value : null;
        if (number == null) return value;
        int point = Integer.parseInt(number, 16);
        if (!Character.isValidCodePoint(point) || point >= 0xD800 && point <= 0xDFFF) throw invalid("Unicode 码值无效");
        return new String(Character.toChars(point));
    }
    static String character(String input) {
        String value = decode(input);
        if (value.codePointCount(0, value.length()) != 1 || value.isBlank()
            || Character.isISOControl(value.codePointAt(0))
            || value.codePointAt(0) >= 0xD800 && value.codePointAt(0) <= 0xDFFF) {
            throw invalid("原字和每个候选必须分别为一个有效字符");
        }
        return value;
    }
    static String code(String value) {
        return value.codePoints().mapToObj(point -> "U+" + String.format(Locale.ROOT, "%04X", point)).reduce((a,b) -> a + " " + b).orElse("");
    }
    static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
