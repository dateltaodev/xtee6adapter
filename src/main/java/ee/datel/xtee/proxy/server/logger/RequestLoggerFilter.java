package ee.datel.xtee.proxy.server.logger;

import ee.datel.xtee.proxy.exception.SoapFault;
import ee.datel.xtee.proxy.exception.SoapFault.FaultCode;
import ee.datel.xtee.proxy.exception.SoapFaultException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RequestLoggerFilter implements Filter {
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  public void doFilter(final ServletRequest req, final ServletResponse resp, final FilterChain chain)
              throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) req;
    HttpServletResponse response = (HttpServletResponse) resp;
    try (ResponseLoggerWrapper responseWrapper = new ResponseLoggerWrapper(request, response)) {
      try (RequestLoggerWrapper requestWrapper = new RequestLoggerWrapper(request)) {
        chain.doFilter(requestWrapper, responseWrapper);
      } catch (SoapFaultException e) {
        responseWrapper.reset();
        SoapFault.writeFault(responseWrapper, e);
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
        responseWrapper.reset();
        Throwable cc = e;
        while (cc.getCause() != null) {
          cc = cc.getCause();
        }
        SoapFaultException ex =
                    new SoapFaultException(FaultCode.SERVER, "Uncaught server exception", cc.getClass().getName(), cc.getMessage());
        SoapFault.writeFault(responseWrapper, ex);
      }
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Override
  public void init(final FilterConfig filterConfig) throws ServletException {
    logger.info("Initialized");
  }

  @Override
  public void destroy() {}


}
