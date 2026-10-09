package blossom.project.common.utils;

import java.util.UUID;

/** Small, printable IDs for correlation; never use them as metric labels. */
public final class RequestIds {
    public static final String HEADER = "X-Request-Id";
    private RequestIds() { }

    public static String normalize(String supplied) {
        return supplied != null && supplied.matches("[A-Za-z0-9._-]{1,64}")
                ? supplied : UUID.randomUUID().toString();
    }
}
