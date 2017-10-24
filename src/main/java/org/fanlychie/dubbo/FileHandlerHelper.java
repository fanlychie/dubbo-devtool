package org.fanlychie.dubbo;

import com.alibaba.dubbo.rpc.service.EchoService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by FanZhongYun on 2017/10/22.
 */
public final class FileHandlerHelper {

    // 存储目录
    private static final File DUBBO_DIR = new File(System.getProperty("user.home") + "/.dubbo-dev-cache");

    // 缓存文件
    private static final File CACHE_FILE = new File(DUBBO_DIR, "dubbo-provider.cache");

    static {
        // 存储目录
        if (!DUBBO_DIR.exists()) {
            DUBBO_DIR.mkdir();
        }
        // 缓存文件
        if (!CACHE_FILE.exists()) {
            try {
                CACHE_FILE.createNewFile();
            } catch (IOException e) {}
        }
    }

    public static void recreateDubboProviderCachedInfo() throws Exception {
        // 提供者定义的端口
        String port = findXmlAttributeValueByClasspath("dubbo:protocol", "port");
        // 提供者定义的服务
        List<String> services = findXmlAttributeValuesByClasspath("dubbo:service", "interface");
        // 清空, 重置
        clearFile(CACHE_FILE);

        /*// 转换包路径和服务的映射
        Map<String, String> packageMapping = new HashMap<>();
        for (String service : services) {
            packageMapping.put(service.substring(0, service.lastIndexOf(".")), service);
        }
        // 缓存文件
        File cacheFile = new File(DUBBO_DIR, getProjectName() + ".cache");
        // 创建文件
        if (!cacheFile.exists()) {
            cacheFile.createNewFile();
        }
        // 重置, 清空
        writeFile(cacheFile, "");
        // 迭代输出: com.xxxx@xxService@20080
        for (String pack : packageMapping.keySet()) {
            String service = packageMapping.get(pack);
            String serviceName = service.substring(pack.length() + 1);
            appendFile(cacheFile, pack + SEPARATOR + serviceName + SEPARATOR + port);
        }*/
    }

    public static void generateDirectConnectProviderInfo(String springContext) throws  Exception {
        Map<String, String> serviceMapping = getActiveProviders(springContext);
        List<String> interfaces = findXmlAttributeValuesByClasspath("dubbo:reference", "interface");
        // 项目配置文件
        File configFile = new File(DUBBO_DIR, getProjectName() + ".properties");
        // 重置, 清空
        writeFile(configFile, "");
        for (String serviceInterface : interfaces) {
            String packageName = serviceInterface.substring(0, serviceInterface.lastIndexOf("."));
            if (serviceMapping.containsKey(packageName)) {
                appendFile(configFile, serviceInterface + "=dubbo://127.0.0.1:" + serviceMapping
                        .get(packageName));
            }
        }
    }

    private static Map<String, String> getActiveProviders(String springContext) throws Exception {
        // 所有提供者的缓存文件
        List<File> providerCacheFiles = findCacheFiles(DUBBO_DIR);
        if (providerCacheFiles.size() == 0) {
            throw new RuntimeException("未发现本地提供者服务！");
        }
        // 服务列表
        Map<String, String> serviceMapping = new HashMap<>();
        // 叠加
        for (File providerCacheFile : providerCacheFiles) {
            List<String> lines = readFileLineByLine(providerCacheFile);
            for (String line : lines) {
                String[] parts = line.split("@");
                String packageName = parts[0];
                String serviceName = parts[1];
                String port = parts[2];
                if (echoTest(springContext, serviceName)) {
                    serviceMapping.put(packageName, port);
                }
            }
        }
        return serviceMapping;
    }

    private static boolean echoTest(String springContext, String serviceName) {
        try {
            serviceName = uncapitalize(serviceName);
            ApplicationContext context = new ClassPathXmlApplicationContext(springContext);
            EchoService echoService = (EchoService) context.getBean(serviceName);
            return "OK".equals(echoService.$echo("OK"));
        } catch (Throwable e) {
            if (!serviceName.endsWith("Impl")) {
                return echoTest(springContext, serviceName + "Impl");
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    private static String uncapitalize(String str) {
        char first = str.charAt(0);
        if (!Character.isLowerCase(first)) {
            return Character.toLowerCase(first) + str.substring(1);
        }
        return str;
    }

    private static String findXmlAttributeValueByClasspath(String tag, String attributeName) throws Exception {
        return findXmlAttributeValuesByClasspath(tag, attributeName).get(0);
    }

    private static List<String> findXmlAttributeValuesByClasspath(String tag, String attributeName) throws Exception {
        List<String> attributeValues = new ArrayList<>();
        // 类路径
        File classPath = new File(Thread.currentThread().getContextClassLoader()
                .getResource("").getFile());
        // 类路径下所有的XML文件
        List<File> xmlFiles = findXmlFiles(classPath);
        // 迭代XML文件
        for (File xmlFile : xmlFiles) {
            // 按行读取的文件内容
            List<String> lines = readFileLineByLine(xmlFile);
            // 迭代每一行查找字符
            for (String line : lines) {
                if (line.contains(tag)) {
                    attributeValues.add(getTagAttributeValue(line, attributeName));
                }
            }
        }
        return attributeValues;
    }

    private static String getProjectName() {
        // 类路径
        File classPath = new File(Thread.currentThread().getContextClassLoader()
                .getResource("").getFile());
        return classPath.getParentFile().getParentFile().getName();
    }

    private static List<File> findXmlFiles(File path) {
        return findFilesBySuffixName(path, ".xml");
    }

    private static List<File> findCacheFiles(File path) {
        return findFilesBySuffixName(path, ".cache");
    }

    private static List<File> findFilesBySuffixName(File path, final String suffixName) {
        // 存储查找到的所有文件
        final List<File> files = new ArrayList<>();
        // 过滤给定的路径下所有的匹配参数类型的文件
        path.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                // 文件
                File file = new File(dir, name);
                // 如果是文件夹, 递归处理
                if (file.isDirectory()) {
                    files.addAll(findFilesBySuffixName(file, suffixName));
                } else {
                    if (name.endsWith(suffixName)) {
                        files.add(file);
                    }
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

    private static void clearFile(File file) throws Exception {
        writeFile(file, "");
    }

    private static void writeFile(File file, String text) throws Exception {
        writeFile(file, text, false);
    }

    private static void appendFile(File file, String text) throws Exception {
        writeFile(file, text, true);
    }

    private static void writeFile(File file, String text, boolean append) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file, append)))) {
            writer.write(text);
            writer.flush();
        }
    }

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

}