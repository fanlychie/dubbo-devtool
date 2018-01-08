package org.fanlychie.dubbo;

import com.alibaba.dubbo.common.utils.ConfigUtils;
import com.alibaba.dubbo.container.spring.SpringContainer;
import org.fanlychie.dubbo.util.ConsoleLogger;
import org.fanlychie.dubbo.util.RuntimeArgument;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * DUBBO直连本地服务的配置处理类
 * Created by Fanlychie on 2017/10/22.
 */
public final class DubboConfigHandler {

    // 存储目录
    private static final File DUBBO_USER_HOME_PATHNAME = new File(String.format("%s/.dubbo-devtool", System.getProperty("user.home")));

    // 提供者文件后缀名称
    private static final String PROVIDER_FILE_SUFFIX_NAME = ".provider";

    static {
        // 首次创建存储目录
        if (!DUBBO_USER_HOME_PATHNAME.exists()) { DUBBO_USER_HOME_PATHNAME.mkdir(); }
    }

    /**
     * DUBBO提供者服务启动前的预处理工作
     *
     * @throws Exception
     */
    public static void preHandleProvider() throws Exception {
        // 提供者定义的端口
        String port = null;
        // 提供者定义的服务
        List<String> services = new LinkedList<>();
        // DUBBO的SPRING配置文件
        List<File> configFiles = getDubboSpringConfig();
        for (File configFile : configFiles) {
            if (port == null) {
                port = findAttributeValueByXmlFile(configFile,"dubbo:protocol", "port");
            }
            // 配置文件声明的服务
            List<String> configServices = findAttributeValuesByXmlFile(configFile,"dubbo:service", "interface", "group");
            if (!CollectionUtils.isEmpty(configServices)) {
                for (int i = 1; i < configServices.size(); i += 2) {
                    String configService = configServices.get(i - 1);
                    if (!services.contains(configService)) {
                        services.add(configService);
                    } else {
                        // 配置文件中声明的服务出现重复, 说明使用了分组
                        String group = configServices.get(i);
                        // 分组的直连需要在服务接口的后面标记计数的字符
                        services.add(group + "/" + configService + (getCount(services, configService) + 1));
                    }
                }
            }
        }
        // 缓存文件
        File cacheFile = new File(DUBBO_USER_HOME_PATHNAME, String.format("%s@%s%s", getProjectName(), port, PROVIDER_FILE_SUFFIX_NAME));
        if (!cacheFile.exists()) {
            cacheFile.createNewFile();
        } else {
            clearFile(cacheFile);
        }
        // 迭代处理输出
        for (String service : services) {
            appendFile(cacheFile, service);
        }
        // 禁止提供者向服务注册中心注册自己
        RuntimeArgument.set("dubbo.registry.register", "false");
        // 直连本地其它提供者服务「如果有」
        directConnectProviderConfigFile(configFiles);
        // 启动日志
        ConsoleLogger.logStartupProvider();
    }

    /**
     * DUBBO消费者服务启动前的预处理工作
     *
     * @param contextConfigLocation SPRING 配置文件位置, web.xml中配置的contextConfigLocation参数
     */
    public static void preHandleConsumer(String contextConfigLocation) {
        try {
            // 配置文件可能有多个
            String[] configFileNames = contextConfigLocation.split("[,\\s]+");
            List<File> configFileList = new LinkedList<>();
            for (String configFileName : configFileNames) {
                configFileList.addAll(getClasspathMatchFiles(configFileName));
            }
            directConnectProviderConfigFile(configFileList);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 直连
     *
     * @param configFiles 配置文件
     * @throws Exception
     */
    private static void directConnectProviderConfigFile(List<File> configFiles) throws Exception {
        // 本地提供者配置
        List<ProviderConfig> providerConfigs = getLocalProviderConfig();
        // 本地没有提供者
        if (providerConfigs == null) {
            ConsoleLogger.logNoProvider();
            return;
        }
        // 消费者直连的配置文件
        File configFile = new File(DUBBO_USER_HOME_PATHNAME, getProjectName() + ".properties");
        if (!configFile.exists()) {
            configFile.createNewFile();
        } else {
            clearFile(configFile);
        }
        // 行内容
        String line = null;
        for (File config : configFiles) {
            // 查找 <dubbo:reference>
            List<String> references = findAttributeValuesByXmlFile(config, "dubbo:reference", "interface", "group");
            for (int i = 1; i < references.size(); i += 2) {
                String group = references.get(i);
                String interfaceName = references.get(i - 1);
                for (ProviderConfig providerConfig : providerConfigs) {
                    if (providerConfig.contains(group, interfaceName)) {
                        ServiceConfig serviceConfig = providerConfig.getServiceConfig(group, interfaceName);
                        // 有分组
                        if (serviceConfig.getGroup() != null) {
                            line = String.format("%s=dubbo://127.0.0.1:%s/%s", interfaceName, providerConfig.getPort(),
                                    serviceConfig.getInterfaceName());
                        } else {
                            line = String.format("%s=dubbo://127.0.0.1:%s", interfaceName, providerConfig.getPort());
                        }
                        appendFile(configFile, line);
                    }
                }
            }
        }
        if (line == null) {
            ConsoleLogger.logNoLocalConsume();
            return;
        } else {
            // 启动本地提供者直连其它提供者
            RuntimeArgument.set("dubbo.resolve.file", configFile.getAbsolutePath());
            // 本地直连调用超时时间设为一天, 项目中配置的超时时间将被此覆盖
            RuntimeArgument.set("dubbo.consumer.timeout", "86400000");
        }
    }

    /**
     * 获取DUBBO的SPRING配置文件
     *
     * @return
     * @throws Exception
     */
    private static List<File> getDubboSpringConfig() throws Exception {
        // 用户自定义的DUBBO SPRING 配置文件
        String configPath = ConfigUtils.getProperty(SpringContainer.SPRING_CONFIG);
        // 用户没有配置的情况
        if (!StringUtils.hasText(configPath)) {
            // DUBBO默认的SPRING配置文件
            configPath = SpringContainer.DEFAULT_SPRING_CONFIG;
        }
        // 配置文件可能有多个
        String[] configFileNames = configPath.split("[,\\s]+");
        List<File> configFileList = new LinkedList<>();
        for (String configFileName : configFileNames) {
            configFileList.addAll(getClasspathMatchFiles(configFileName));
        }
        return configFileList;
    }

    /**
     * 获取类路径下的匹配到的文件
     *
     * @param filename 文件名, 文件名模式
     * @return
     * @throws Exception
     */
    private static List<File> getClasspathMatchFiles(String filename) throws Exception {
        // 去掉以"classpath:"和"classpath*:"开头的字符
        if (filename.matches("classpath[*]?:\\S+")) {
            filename = filename.substring(filename.indexOf(":") + 1);
        }
        // 去类路径下查找
        List<File> configFiles = matchFiles(getClassPath(), filename);
        // 文件引用的其它文件
        List<File> importFiles = getXmlFileImportFiles(configFiles);
        // 追加到
        configFiles.addAll(importFiles);
        return configFiles;
    }

    /**
     * 匹配目录下的文件
     *
     * @param parentFile  文件所在的目录
     * @param filePattern 文件名称, 文件模式
     * @return
     * @throws Exception
     */
    private static List<File> matchFiles(File parentFile, String filePattern) throws Exception {
        String pathname;
        String filename;
        String separator;
        // 解析出路径和文件名
        if (filePattern.contains("/") || filePattern.contains("\\")) {
            if (filePattern.contains("/")) {
                separator = "/";
            } else {
                separator = "\\";
            }
            filename = filePattern.substring(filePattern.lastIndexOf(separator) + 1);
            pathname = filePattern.replace(filename, "");
        } else {
            pathname = null;
            filename = filePattern;
        }
        if (pathname != null) {
            parentFile = new File(parentFile, pathname);
        }
        return findFilesByName(parentFile, filename);
    }

    /**
     * XML文件中的<import resource="..."/>标签引用的资源文件
     *
     * @param configFiles 配置文件
     * @return
     * @throws Exception
     */
    private static List<File> getXmlFileImportFiles(List<File> configFiles) throws Exception {
        List<File> resourceFiles = new LinkedList<>();
        for (File configFile : configFiles) {
            List<String> resourceFilePaths = findAttributeValuesByXmlFile(configFile, "import", "resource");
            for (String resourceFilePath : resourceFilePaths) {
                resourceFiles.addAll(matchFiles(configFile.getParentFile(), resourceFilePath));
            }
        }
        return resourceFiles;
    }

    /**
     * 获取集合中包含的键的计数
     *
     * @param list 记录
     * @param key  键值
     * @return
     */
    private static int getCount(List<String> list, String key) {
        int count = 0;
        for (String item : list) {
            if (item.contains(key)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取本地活跃的本地提供者服务
     *
     * @return
     * @throws Exception
     */
    private static List<ProviderConfig> getLocalProviderConfig() throws Exception {
        // 用户目录下提供者服务的配置文件
        List<File> providerFiles = findUserHomeProviderFiles();
        if (!CollectionUtils.isEmpty(providerFiles)) {
            // 服务列表
            List<ProviderConfig> providerConfigs = new ArrayList<>();
            // 迭代处理现在启用的服务列表
            for (File providerFile : providerFiles) {
                String name = providerFile.getName();
                String port = name.substring(name.indexOf("@") + 1, name.lastIndexOf("."));
                // telnet 127.0.0.1 port
                if (telnet("127.0.0.1", Integer.parseInt(port))) {
                    List<String> lines = readFileLineByLine(providerFile);
                    List<ServiceConfig> serviceConfigs = new ArrayList<>();
                    for (String line : lines) {
                        if (line.contains("/")) {
                            int separator = line.indexOf("/");
                            serviceConfigs.add(new ServiceConfig(line.substring(0, separator), line.substring(separator + 1)));
                        } else {
                            serviceConfigs.add(new ServiceConfig(null, line));
                        }
                    }
                    providerConfigs.add(new ProviderConfig(port, serviceConfigs));
                }
            }
            if (!CollectionUtils.isEmpty(providerConfigs)) {
                return providerConfigs;
            }
        }
        return null;
    }

    /**
     * telnet 模拟回声测试
     *
     * @param host 主机
     * @param port 端口
     * @return
     */
    public static boolean telnet(String host, int port) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), 2000);
        } catch (IOException e) {
            return false;
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
        return true;
    }

    /**
     * 查找XML文件下给定的标签的属性的值
     *
     * @param file           XML文件
     * @param tag            标签
     * @param attributeNames 属性名
     * @return
     * @throws Exception
     */
    private static String findAttributeValueByXmlFile(File file, String tag, String... attributeNames) throws Exception {
        return findAttributeValuesByXmlFile(file, tag, attributeNames).get(0);
    }

    /**
     * 查找XML文件下给定的标签的属性的值
     *
     * @param file           XML文件
     * @param tag            标签
     * @param attributeNames 属性名
     * @return
     * @throws Exception
     */
    private static List<String> findAttributeValuesByXmlFile(File file, String tag, String... attributeNames) throws Exception {
        List<String> attributeValues = new LinkedList<>();
        // 按行读取的文件内容
        List<String> lines = readFileLineByLine(file);
        // 迭代每一行查找字符
        for (String line : lines) {
            if (StringUtils.hasText(line) && !line.trim().startsWith("<!--") && line.contains(String.format("<%s ", tag))) {
                for (String attributeName : attributeNames) {
                    attributeValues.add(getTagAttributeValue(line, attributeName));
                }
            }
        }
        return attributeValues;
    }

    /**
     * 获取类路径
     *
     * @return
     * @throws Exception
     */
    private static File getClassPath() throws Exception {
        // 类路径
        File classPath = new File(Thread.currentThread().getContextClassLoader().getResource("").getFile());
        // 兼容路径中含有空格的字符
        classPath = new File(URLDecoder.decode(classPath.getAbsolutePath(), "UTF-8"));
        return classPath;
    }

    /**
     * 获取项目名
     *
     * @return
     */
    private static String getProjectName() {
        // 类路径
        File classPath = new File(Thread.currentThread().getContextClassLoader()
                .getResource("").getFile());
        return classPath.getParentFile().getParentFile().getName();
    }

    /**
     * 查找用户目录下提供者服务的配置文件
     *
     * @return
     */
    private static List<File> findUserHomeProviderFiles() {
        return findFilesByName(DUBBO_USER_HOME_PATHNAME, String.format("*%s", PROVIDER_FILE_SUFFIX_NAME));
    }

    /**
     * 在给定的路径下查找文件
     *
     * @param path 路径
     * @param filename 文件名
     * @return
     */
    private static List<File> findFilesByName(File path, final String filename) {
        // 存储查找到的所有文件
        final List<File> files = new LinkedList<>();
        // 转换成正则表达式匹配
        final String regex = filename.replaceAll("\\.", "\\\\.").replaceAll("\\*", ".*");
        // 过滤给定的路径下所有的匹配参数类型的文件
        path.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                // 文件
                File file = new File(dir, name);
                // 如果是文件夹, 递归处理
                if (!file.isDirectory() && name.matches(regex)) {
                    files.add(file);
                }
                return false;
            }
        });
        return files;
    }

    /**
     * 逐行读取文件内容
     *
     * @param file 读取的文件对象
     */
    private static List<String> readFileLineByLine(File file) throws Exception {
        List<String> lines = new ArrayList<>();
        Reader fileReader = new InputStreamReader(new FileInputStream(file), "UTF-8");
        String line;
        try (BufferedReader reader = new BufferedReader(fileReader)) {
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * 清空, 重置文件内容
     */
    private static void clearFile(File file) throws Exception {
        writeFile(file, "");
    }

    /**
     * 写出内容到文件
     */
    private static void writeFile(File file, String text) throws Exception {
        writeFile(file, text, false);
    }

    /**
     * 追加内容到文件
     */
    private static void appendFile(File file, String text) throws Exception {
        writeFile(file, text + "\r\n", true);
    }

    /**
     * 写出内容到文件
     */
    private static void writeFile(File file, String text, boolean append) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file, append)))) {
            writer.write(text);
            writer.flush();
        }
    }

    /**
     * 获取标签属性的值
     */
    private static String getTagAttributeValue(String line, String attributeName) {
        String attributeValue = null;
        // 按空格拆分
        String[] parts = line.split(" ");
        // 迭代查找
        for (String part : parts) {
            if (part.startsWith(attributeName + "=")) {
                attributeValue = part.substring(part.indexOf("\"") + 1, part.lastIndexOf("\""));
                break;
            }
        }
        return attributeValue;
    }

    /**
     * 提供者配置
     */
    private static class ProviderConfig {

        private String port;

        private List<ServiceConfig> serviceConfigs;

        public ProviderConfig(String port, List<ServiceConfig> serviceConfigs) {
            this.port = port;
            this.serviceConfigs = serviceConfigs;
        }

        public String getPort() {
            return port;
        }

        public List<ServiceConfig> getServiceConfigs() {
            return serviceConfigs;
        }

        public boolean contains(String group, String interfaceName) {
            return getServiceConfig(group, interfaceName) != null;
        }

        private ServiceConfig getServiceConfig(String group, String interfaceName) {
            if (!CollectionUtils.isEmpty(serviceConfigs)) {
                for (ServiceConfig serviceConfig : serviceConfigs) {
                    if (StringUtils.hasText(group)) {
                        if (group.equals(serviceConfig.getGroup()) && serviceConfig.getInterfaceName().startsWith(interfaceName)) {
                            return serviceConfig;
                        }
                    } else {
                        if (serviceConfig.getInterfaceName().equals(interfaceName)) {
                            return serviceConfig;
                        }
                    }
                }
            }
            return null;
        }

    }

    /**
     * <dubbo:service>、<dubbo:reference> 节点配置
     */
    private static class ServiceConfig {

        private String group;

        private String interfaceName;

        public ServiceConfig(String group, String interfaceName) {
            this.group = group;
            this.interfaceName = interfaceName;
        }

        public String getGroup() {
            return group;
        }

        public String getInterfaceName() {
            return interfaceName;
        }

    }

}