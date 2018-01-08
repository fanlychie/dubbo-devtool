package org.fanlychie.dubbo.util;

import org.springframework.util.StringUtils;

/**
 * 程序运行时参数
 * Created by Fanlychie on 2018/1/4.
 */
public final class RuntimeArgument {

    public static void set(String key, String value) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException();
        }
        System.setProperty(key, value);
    }

    public static boolean isDirectConnectProvider() {
        String debug = System.getProperty("debug");
        if (debug == null) {
            debug = System.getProperty("dubbo.local.debug");
        }
        return debug != null;
    }

}