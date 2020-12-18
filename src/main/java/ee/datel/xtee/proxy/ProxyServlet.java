package ee.datel.xtee.proxy;

import ee.datel.xtee.proxy.exception.SoapFault;
import ee.datel.xtee.proxy.exception.SoapFault.FaultCode;
import ee.datel.xtee.proxy.exception.SoapFaultException;
import ee.datel.xtee.proxy.request.ProxyRequest;
import ee.datel.xtee.proxy.response.ServiceClient;
import ee.datel.xtee.proxy.response.ServiceResponse;
import ee.datel.xtee.proxy.server.RequestTimerListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet(urlPatterns = "/*", name = "Service Servlet")
public class ProxyServlet extends HttpServlet {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private static final long serialVersionUID = 1L;

  @Override
  public void doGet(final HttpServletRequest request, final HttpServletResponse response)
      throws IOException {
    // never
  }

  @Override
  public void doPost(final HttpServletRequest request, final HttpServletResponse response)
      throws IOException {
    try (ProxyRequest client = ProxyRequest.getClient(request)) {
      request.setAttribute(RequestTimerListener.XTEEREQUESTNAME, client.getRequestName());
      ServiceClient adapter = client.getServiceClient();
      try (InputStream adapterRequest = client.getInputStream(adapter);
          ServiceResponse resp = adapter.doRequest(adapterRequest, client);
          InputStream inp = resp.getProxyResponseStream(adapter, client);) {
        byte[] bff = new byte[8 * 1024];
        if (resp.isMultipart()) {
          response.setContentType(resp.getContentType());
        }
        try (OutputStream out = response.getOutputStream()) {
          int len;
          while ((len = inp.read(bff)) != -1) {
            if (len > 0) {
              out.write(bff, 0, len);
            }
          }
        }
      }
    } catch (SoapFaultException ex) {
      SoapFault.writeFault(response, ex);
    } catch (Exception ex) {
      if (!ex.getMessage().contains("Connection reset by peer")) {
        logger.error(ex.getMessage(), ex);
        SoapFault.writeFault(response,
            new SoapFaultException(FaultCode.SERVER, ex.getMessage(), ex.getClass().getName()));
      } else {
        logger.warn(ex.getMessage());
      }
    }
  }

  @Override
  public void destroy() {
    return;
  }


  @Override
  public void init() throws ServletException {
    logger.info("Initialized");
  }

}
