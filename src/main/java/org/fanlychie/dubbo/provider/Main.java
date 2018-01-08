package org.fanlychie.dubbo.provider;

import org.fanlychie.dubbo.DubboConfigHandler;

/**
 * DUBBO提供者本地调试启动类
 * Created by Fanlychie on 2017/10/22.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        // 预处理DUBBO提供者服务
        DubboConfigHandler.preHandleProvider();
        // 调DUBBO的Main启动服务
        com.alibaba.dubbo.container.Main.main(args);
    }

}