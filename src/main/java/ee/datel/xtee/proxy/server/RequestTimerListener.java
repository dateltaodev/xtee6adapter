package ee.datel.xtee.proxy.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;

/**
 * Request timer and thread naming.
 * <p>
 * Uniquely names thread. Format: <code>String.format("R-%05d",[unique-number])</code>
 * </p>
 *
 * @author aldoa
 *
 */
@WebListener
public class RequestTimerListener implements ServletRequestListener {
  public static final String INITIALCOUNT = "initail-request-number";
  public static final String XTEEREQUESTNAME = RequestTimerListener.class.getName() + "#RequestName";
  private final String requestThreadName = RequestTimerListener.class.getName() + "#ThreadName";
  private final String requestStartTme = RequestTimerListener.class.getName() + "#StartTime";
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final AtomicInteger number = new AtomicInteger(-1);
  private boolean initiated;


  @Override
  public void requestInitialized(final ServletRequestEvent sre) {
    if (!initiated) {
      number.compareAndSet(-1, Integer.parseInt(sre.getServletContext().getInitParameter(RequestTimerListener.INITIALCOUNT)));
      initiated = true;
    }
    number.compareAndSet(999999, 0);
    long start = System.currentTimeMillis();
    sre.getServletRequest().setAttribute(requestStartTme, Long.valueOf(start));
    String tname = Thread.currentThread().getName();
    sre.getServletRequest().setAttribute(requestThreadName, tname);
    Thread.currentThread().setName(String.format("R-%06d", number.incrementAndGet()));
  }

  @Override
  public void requestDestroyed(final ServletRequestEvent sre) {
    Long start = (Long) sre.getServletRequest().getAttribute(requestStartTme);
    if (start != null) {
      String method = ((HttpServletRequest) sre.getServletRequest()).getMethod();
      if ("GET".equals(method)) {
        logger.info("{} {}ms", ((HttpServletRequest) sre.getServletRequest()).getQueryString(),
                    Long.valueOf(System.currentTimeMillis() - start.longValue()));
      } else {
        logger.info("{} {}ms", sre.getServletRequest().getAttribute(XTEEREQUESTNAME),
                    Long.valueOf(System.currentTimeMillis() - start.longValue()));
      }
    }
    String tname = (String) sre.getServletRequest().getAttribute(requestThreadName);
    if (tname != null) {
      Thread.currentThread().setName(tname);
    }
  }
}
