package ee.datel.xtee.proxy.server;

import ee.datel.xtee.proxy.server.logger.ConversiationDeleter;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.servlet.ServletContextEvent;

/**
 * Server configuration provider.
 *
 * @author aldoa
 *
 */
public class ServerConfiguration {
  public static final String PRFX = "proxy.";
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final String[] empty = new String[0];

  private final Properties appProperties = new Properties();
  private final Properties proxyProperties = new Properties();

  private final Lock lock = new ReentrantLock();
  private final AtomicReference<Path> current = new AtomicReference<>();
  private final AtomicInteger year = new AtomicInteger();
  private final AtomicInteger month = new AtomicInteger();
  private final AtomicInteger day = new AtomicInteger();

  private FileSystem confFileSystem;
  private Path logPath;
  private String[] subsystems;
  private String[][] services;
  private String proxyUrl;

  private ConversiationDeleter cleaner;

  /**
   * Retrieves subsystems.
   *
   * @return subsystems array
   */
  public String[] getSubsystems() {
    return subsystems;
  }

  /**
   * Retrieves subsystem's services.
   *
   * @return services array, when non existing subsystem, returns empty array
   */
  public String[] getSubsystemServices(final String subsystem) {
    int idx = Arrays.binarySearch(subsystems, subsystem);

    return idx >= 0 ? services[idx] : empty;
  }

  /**
   * Retrieves proxy (WSDL soap:address) URL.
   *
   * @return proxy URL
   */
  public String getProxyUrl() {
    return proxyUrl;
  }

  /**
   * Retrieves value from application properties. Factory values.
   *
   * @param key property name
   * @return value
   */
  public String getApplicationPropertie(final String key) {
    return Objects.requireNonNull(appProperties).getProperty(key);
  }

  /**
   * Retrieves value from proxy properties. Configuration values.
   *
   * @param key property name
   * @return value
   */
  public String getProxyPropertie(final String key) {
    return Objects.requireNonNull(proxyProperties).getProperty(key);
  }

  /**
   * Request logging path. <i> Logs organized in paths - ./[YEAR]/[MONTH]/[DAY]/.</i>
   *
   * @return todays path
   */
  public Path getTodaysPath() {
    Calendar now = Calendar.getInstance();
    if (day.get() != now.get(Calendar.DAY_OF_MONTH) || month.get() != now.get(Calendar.MONTH)
                || year.get() != now.get(Calendar.YEAR)) {
      lock.lock();
      try {
        if (day.get() != now.get(Calendar.DAY_OF_MONTH) || month.get() != now.get(Calendar.MONTH)
                    || year.get() != now.get(Calendar.YEAR)) {
          Path todays = logPath.resolve(Integer.toString(now.get(Calendar.YEAR)))
                      .resolve(Integer.toString(now.get(Calendar.MONTH) + 1))
                      .resolve(Integer.toString(now.get(Calendar.DAY_OF_MONTH)));
          try {
            Files.createDirectories(todays);
          } catch (IOException e) {
            todays = FileSystems.getDefault().getPath(System.getProperty("java.io.tmpdir"));
          }
          current.set(todays);
          year.set(now.get(Calendar.YEAR));
          month.set(now.get(Calendar.MONTH));
          day.set(now.get(Calendar.DAY_OF_MONTH));
        }
      } finally {
        lock.unlock();
      }
    }
    if (!Files.exists(current.get())) {
      try {
        Files.createDirectories(current.get());
      } catch (IOException e) {
        return FileSystems.getDefault().getPath(System.getProperty("java.io.tmpdir"));
      }
    }
    return current.get();
  }

  void init(final ServletContextEvent sce) throws IOException {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream("/application.properties")) {
      appProperties.load(in);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      return;
    }
    logPath = FileSystems.getDefault().getPath(getApplicationPropertie("request.log.path"));
    proxyUrl = getApplicationPropertie("proxy.url");

    final String confZip = getApplicationPropertie("proxy.cofiguration.file");
    if (!StringUtils.isBlank(confZip)) {
      Path zipfile = Paths.get(confZip).normalize();
      URI uri = URI.create("jar:file:///" + zipfile.toString().replace('\\', '/'));
      Map<String, String> env = new HashMap<>();
      confFileSystem = FileSystems.newFileSystem(uri, env);
    } // on classpath

    CharsetDecoder utf8Decoder = StandardCharsets.UTF_8.newDecoder();
    utf8Decoder.onMalformedInput(CodingErrorAction.IGNORE);
    utf8Decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
    try (Reader in = getPropertiesReader(utf8Decoder)) {
      proxyProperties.load(in);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      return;
    }

    List<String> values = new ArrayList<>();
    subsystems = collectKeys(values, "proxy.");
    services = new String[subsystems.length][];
    for (int i = 0; i < subsystems.length; i++) {
      services[i] = collectKeys(values, "proxy." + subsystems[i] + ".");
    }

    Integer initial = Integer.valueOf(0);
    Pattern pattern = Pattern.compile("R-\\d{6}\\.log");
    String[] max = new String[] {""};
    try (final Stream<Path> stream = Files.list(getTodaysPath())) {
      stream.sorted().filter(p -> pattern.matcher(p.getFileName().toString()).matches())
                  .forEach(p -> max[0] = p.getFileName().toString());
      if (max[0].length() > 6) {
        initial = Integer.valueOf(max[0].substring(2, max[0].length() - 4));
      }
    } catch (NoSuchFileException e) {
      // no files
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    }
    sce.getServletContext().setInitParameter(RequestTimerListener.INITIALCOUNT, initial.toString());
    cleaner = new ConversiationDeleter(logPath, getApplicationPropertie("request.log.max-age"));
    logger.info("Initialized");
  }

  private Reader getPropertiesReader(final CharsetDecoder utf8Decoder) throws IOException {
    return new BufferedReader(new InputStreamReader(
                confFileSystem == null ? getClass().getClassLoader().getResourceAsStream("/configuration/xtee-proxy.properties")
                            : Files.newInputStream(confFileSystem.getPath("xtee-proxy.properties")),
                utf8Decoder));
  }

  /**
   * Retrieves InputStream of the file from configuration files.
   *
   * @return path. Returns null if not found.
   * @throws IOException
   */
  public InputStream getXsdStream(final String fileName, final String fileDir) throws IOException {
    final InputStream out;
    if (confFileSystem != null) {
      out = getZipXsdInputStream(fileName, fileDir);
    } else {
      out = fileDir == null ? getClass().getClassLoader().getResourceAsStream("/configuration/" + fileName)
                  : getClass().getClassLoader().getResourceAsStream("/configuration/" + fileDir + "/" + fileName);
    }
    return out;
  }

  private InputStream getZipXsdInputStream(final String fileName, final String fileDir) throws IOException {
    Path temp;
    if (fileDir != null) {
      temp = confFileSystem.getPath(fileDir, fileName);
    } else {
      temp = confFileSystem.getPath(fileName);
    }
    return Files.exists(temp) ? Files.newInputStream(temp) : null;
  }

  private String[] collectKeys(final List<String> values, final String pattern) {
    values.clear();
    Enumeration<Object> keys = proxyProperties.keys();
    while (keys.hasMoreElements()) {
      String key = keys.nextElement().toString();
      if (key.startsWith(pattern)) {
        int idx = key.indexOf('.', pattern.length());
        if (idx > 0) {
          String val = key.substring(pattern.length(), idx);
          if (!"metadata".equals(val)) {
            if (!values.contains(val)) {
              values.add(val);
            }
          }
        }
      }
    }
    String[] result = values.toArray(new String[values.size()]);
    Arrays.sort(result);
    return result;
  }

  void destroy() {
    if (cleaner != null) {
      cleaner.destroy();
      cleaner = null;
    }
    try {
      if (confFileSystem != null && confFileSystem.isOpen()) {
        confFileSystem.close();
      }
    } catch (IOException e) {
      // nothing to do
    }
  }
}
