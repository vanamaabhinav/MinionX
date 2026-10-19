package com.minionx.util;

/**
 * Utility for safe encoding and decoding of serialized item payloads to prevent
 * delimiter collision attacks or corruption when item metadata contains reserved delimiters.
 */
public final class MinionPayloadUtil {

    private MinionPayloadUtil() {}

    /**
     * Escapes reserved payload delimiters within an individual serialized item YAML string.
     */
    public static String escapeItemPayload(String yaml) {
        if (yaml == null) return "";
        return yaml.replace("%", "%25")
                   .replace(";;;ITEM;;;", "%3B%3B%3BITEM%3B%3B%3B")
                   .replace(";;;PAYLOAD:::", "%3B%3B%3BPAYLOAD%3B%3B%3B")
                   .replace(";;;LEFTOVER:::", "%3B%3B%3BLEFTOVER%3B%3B%3B");
    }

    /**
     * Reverses delimiter escaping for an individual serialized item YAML string.
     */
    public static String unescapeItemPayload(String raw) {
        if (raw == null) return "";
        return raw.replace("%3B%3B%3BLEFTOVER%3B%3B%3B", ";;;LEFTOVER:::")
                  .replace("%3B%3B%3BPAYLOAD%3B%3B%3B", ";;;PAYLOAD:::")
                  .replace("%3B%3B%3BITEM%3B%3B%3B", ";;;ITEM;;;")
                  .replace("%25", "%");
    }
}
