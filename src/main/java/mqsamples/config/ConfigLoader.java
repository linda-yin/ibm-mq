package mqsamples.config;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * 加载 config.yml 配置。
 * 配置文件为必须参数，通过以下方式指定（按优先级）：
 *   1. -Dconfig=/path/to/config.yml 系统属性
 *   2. 命令行第一个参数：java -jar target/mqdemo-1.0.jar config.yml
 * 未指定或文件不存在时抛出异常。
 */
public class ConfigLoader {

    public static AppConfig load(String[] args) {
        String path = resolvePath(args);
        if (path == null) {
            throw new IllegalArgumentException("缺少配置文件参数。用法：java -jar target/mqdemo-1.0.jar config.yml");
        }

        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("配置文件不存在: " + file.getAbsolutePath());
        }

        Map<String, Object> data = loadFromFile(file);
        System.out.println("已加载配置文件: " + file.getAbsolutePath());
        return new AppConfig(data);
    }

    private static String resolvePath(String[] args) {
        // 系统属性优先
        String sys = System.getProperty("config");
        if (sys != null && !sys.trim().isEmpty()) {
            return sys.trim();
        }
        // 命令行第一个参数
        if (args != null && args.length > 0 && !args[0].startsWith("-")) {
            return args[0];
        }
        return null;
    }

    private static Map<String, Object> loadFromFile(File file) {
        try (InputStream in = new FileInputStream(file)) {
            return parse(in);
        } catch (IOException e) {
            throw new RuntimeException("读取配置文件失败: " + file.getAbsolutePath(), e);
        }
    }

    private static Map<String, Object> parse(InputStream in) {
        Yaml yaml = new Yaml();
        Object loaded = yaml.load(in);
        if (loaded == null) {
            throw new IllegalStateException("config.yml 内容为空");
        }
        if (!(loaded instanceof Map)) {
            throw new IllegalStateException("config.yml 格式错误：应为 YAML 键值映射");
        }
        return (Map<String, Object>) loaded;
    }
}
