package org.fanlychie.dubbo.util;

/**
 * 控制台信息
 * Created by Fanlychie on 2018/1/4.
 */
public final class ConsoleLogger {

    public static void log(Object... messages) {
        System.err.println("[INFO] <=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=>");
        for (Object message : messages) {
            System.err.println(String.format("[INFO] %s", message));
        }
        System.err.println("[INFO] <=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=><=>\n");
    }

}