# dubbo-devtool

DUBBO服务本地开发直连提供者的调试工具包

# 本地启动提供者服务

1．IntelliJ IDEA 启动的配置示例：
`org.fanlychie.dubbo.provider.Main`
![](https://raw.githubusercontent.com/fanlychie/mdimg/master/dubbo-provider-main.png)

2．Java Main 方法启动

```java
public static void main(String[] args) {
    org.fanlychie.dubbo.provider.Main.main(args);
}
```

注：如果提供者中配置了消费服务，则提供者应用也会自动尝试直连这些服务。

# 本地启动消费者服务(面向WEB服务)

在`web.xml`配置文件中添加如下配置：

```xml
<listener>
    <listener-class>org.fanlychie.dubbo.consumer.ConsumerStartupListener</listener-class>
</listener>
```

注：需要配置在SPRING监听器`org.springframework.web.context.ContextLoaderListener`之前。

启动WEB项目时，添加启动参数：`-Ddubbo.local.debug`或`-Ddebugg`。IntelliJ IDEA 启动的配置示例：

![](https://raw.githubusercontent.com/fanlychie/mdimg/master/dubbo-consumer.png)