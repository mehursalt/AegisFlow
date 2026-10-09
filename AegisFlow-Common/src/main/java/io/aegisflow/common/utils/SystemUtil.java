package io.aegisflow.common.utils;

/**
 * SystemUtil类
 */
public class SystemUtil {
    public static boolean isLinux() {
        String osName = System.getProperty("os.name").toLowerCase();
        return osName.contains("linux");
    }

}
