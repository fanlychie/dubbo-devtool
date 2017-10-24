package org.fanlychie.dubbo.provider;

/**
 * Created by FanZhongYun on 2017/10/22.
 */
public class Debug {

    public static void main(String[] args) throws Exception {
//        FileHandlerHelper.recreateDubboProviderCachedInfo();
        System.setProperty("dubbo.registry.register", "false");
        com.alibaba.dubbo.container.Main.main(args);
    }

}