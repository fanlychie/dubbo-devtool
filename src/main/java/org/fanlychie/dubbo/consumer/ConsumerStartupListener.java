package org.fanlychie.dubbo.consumer;

import org.fanlychie.dubbo.DubboConfigHandler;
import org.fanlychie.dubbo.util.RuntimeArgument;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * 消费者启动监听
 * Created by Fanlychie on 2018/1/4.
 */
public class ConsumerStartupListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        // 通过启动参数：[ -Ddebug | -Ddubbo.local.debug]
        if (RuntimeArgument.isDirectConnectProvider()) {
            // DUBBO消费者服务启动前的预处理工作
            DubboConfigHandler.preHandleConsumer(servletContextEvent.getServletContext().getInitParameter("contextConfigLocation"));
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {

    }

}