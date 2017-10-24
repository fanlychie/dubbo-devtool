package org.fanlychie.dubbo;

import org.apache.commons.net.telnet.TelnetClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by FanZhongYun on 2017/10/24.
 */
public final class TelnetClientUtils {

    private TelnetClient telnetClient;

    private InputStream inputStream;

    private OutputStream outputStream;

    public TelnetClientUtils(String ip, int port) {
        connect(ip, port);
    }

    public static void xxx(String ip, int port) {
        TelnetClient telnetClient = connect(ip, port);
        telnetClient.sendCommand();
    }

    private static TelnetClient connect(String ip, int port) {
        TelnetClient telnetClient = new TelnetClient();
        try {
            telnetClient.connect(ip, port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return telnetClient;
    }

    public static void main(String[] args) throws Exception {
//        Process process = Runtime.getRuntime().exec("telnet 127.0.0.1 20801");
//        process.getOutputStream()
        TelnetClient telnetClient = new TelnetClient();
        telnetClient.setDefaultTimeout(2000);
        telnetClient.connect("127.0.0.1", 20802);
    }

}