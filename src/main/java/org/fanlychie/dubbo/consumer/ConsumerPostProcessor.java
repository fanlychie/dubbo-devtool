package org.fanlychie.dubbo.consumer;

import org.fanlychie.dubbo.DubboConfigHandler;
import org.fanlychie.dubbo.util.RuntimeArgument;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * 消费者启动直连处理器
 * Created by Fanlychie on 2018/1/9.
 */
public class ConsumerPostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // 通过启动参数：[ -Ddebug | -Ddubbo.local.debug]
        if (RuntimeArgument.isDirectConnectProvider()) {
            try {
                // DUBBO消费者服务启动前的预处理工作
                DubboConfigHandler.preHandleConsumer(DubboConfigHandler.getWebApplicationContextConfigLocation());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}