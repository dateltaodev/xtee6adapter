package ee.datel.xtee.proxy.server;

import ee.datel.xtee.proxy.WsdlServlet;
import ee.datel.xtee.proxy.exception.SoapFault;
import ee.datel.xtee.proxy.exception.SoapFault.FaultCode;
import ee.datel.xtee.proxy.exception.SoapFaultException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b> Rejects all except GET and POST requests. </b>
 * <p>
 * If request's character encoding not provided adds such http header. (
 * </p>
 *
 * @author aldoa
 *
 */
public class RequestToUtf8Filter implements Filter {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private ServletContext servletContext;

  @Override
  public void doFilter(final ServletRequest req, final ServletResponse resp, final FilterChain chain)
      throws IOException, ServletException {
    req.setAttribute(ServerConfiguration.class.getName(), ServerContextListener.getServerConfiguration());
    switch (((HttpServletRequest) req).getMethod()) {
      case "POST":
        try {
          if (req.getCharacterEncoding() == null) {
            req.setCharacterEncoding(StandardCharsets.UTF_8.name());
          }
          chain.doFilter(req, resp);
        } catch (Exception ex) {
          if ("ClientAbortException".equals(ex.getClass().getSimpleName())) {
            logger.info("Client-Abort-Exception");
          } else {
            logger.error(ex.getMessage(), ex);
            Throwable cc = ex;
            while (cc.getCause() != null) {
              cc = cc.getCause();
            }
            SoapFaultException ew = new SoapFaultException(FaultCode.SERVER, "Uncaught server exception",
                cc.getClass().getName(), cc.getMessage());
            SoapFault.writeFault(resp, ew);
          }
        }
        break;
      case "GET":
        try {
          RequestDispatcher dispatcher = servletContext.getRequestDispatcher(WsdlServlet.SERVLETPATH);
          dispatcher.forward(req, resp);
        } catch (Exception e) {
          logger.error(e.getMessage(), e);
          Throwable cc = e;
          while (cc.getCause() != null) {
            cc = cc.getCause();
          }
          ((HttpServletResponse) resp).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
              cc.getClass().getSimpleName() + ": " + cc.getMessage());
        }
        break;
      default:
        ((HttpServletResponse) resp).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
            "Only POST and GET methods allowed");
    }
  }

  @Override
  public void init(final FilterConfig filterConfig) throws ServletException {
    servletContext = filterConfig.getServletContext();
    logger.info("Initialized");
  }

  @Override
  public void destroy() {}

}
