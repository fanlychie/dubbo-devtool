package org.fanlychie.dubbo.provider;

import org.fanlychie.dubbo.DubboConfigHandler;

/**
 * DUBBO提供者本地调试启动类
 * Created by Fanlychie on 2017/10/22.
 */
public class Main {

    public static void main(String[] args) {
        try {
            // DUBBO提供者服务启动前的预处理工作
            DubboConfigHandler.preHandleProvider();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 调DUBBO的Main启动服务
        com.alibaba.dubbo.container.Main.main(args);
    }

}