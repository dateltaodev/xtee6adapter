package ee.datel.xtee.proxy.server;

import ee.datel.xtee.proxy.response.ServiceClient;
import ee.datel.xtee.proxy.server.logger.RequestLoggerFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.FilterRegistration.Dynamic;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * Server context initializer.
 *
 * @author aldoa
 *
 */
@WebListener
public class ServerContextListener implements ServletContextListener {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private static final ServerConfiguration SERVERCONFIG = new ServerConfiguration();

  static ServerConfiguration getServerConfiguration() {
    return SERVERCONFIG;
  }

  @Override
  public void contextInitialized(final ServletContextEvent sce) {
    try {
      SERVERCONFIG.init(sce);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      throw new RuntimeException(e.getMessage());
    }
    Dynamic filter = sce.getServletContext().addFilter("UTF-8 Filter", RequestToUtf8Filter.class);
    filter.addMappingForUrlPatterns(null, true, "/*");
    filter = sce.getServletContext().addFilter("Logger Filter", RequestLoggerFilter.class);
    filter.addMappingForUrlPatterns(null, true, "/*");
    ServiceClient.init();
    logger.info("Initialized");
  }

  @Override
  public void contextDestroyed(final ServletContextEvent sce) {
    SERVERCONFIG.destroy();
    ServiceClient.destroy();
  }

}
