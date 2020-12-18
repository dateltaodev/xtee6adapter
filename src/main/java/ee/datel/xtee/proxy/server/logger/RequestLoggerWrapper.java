package ee.datel.xtee.proxy.server.logger;

import ee.datel.xtee.proxy.exception.SoapFault.FaultCode;
import ee.datel.xtee.proxy.exception.SoapFaultException;
import ee.datel.xtee.proxy.server.ServerConfiguration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

class RequestLoggerWrapper extends HttpServletRequestWrapper implements AutoCloseable {
  private final Path log;
  private ServletInputStream inp;
  private BufferedReader rdr;

  /**
   * Constructor.
   *
   * @param request request
   * @throws SoapFaultException when fails reading/storing request stream
   */
  public RequestLoggerWrapper(final HttpServletRequest request) throws SoapFaultException {
    super(request);
    ServerConfiguration conf = (ServerConfiguration) request.getAttribute(ServerConfiguration.class.getName());
    log = conf.getTodaysPath().resolve(Thread.currentThread().getName() + ".log");
    request.setAttribute(RequestLoggingInputStream.class.getName(), log);
    try (InputStream in = request.getInputStream()) {
      Files.copy(in, log, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      StackTraceElement ex = e.getStackTrace()[0];
      throw new SoapFaultException(FaultCode.SERVER, e.getMessage(), ex.getClassName(),
                  ex.getMethodName() + "@" + ex.getLineNumber());
    }
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    if (inp == null) {
      if (rdr != null) {
        throw new IllegalStateException("getReader() method has already been called for this request");
      }
      inp = new RequestLoggingInputStream(log);
    }
    return inp;
  }

  @Override
  public BufferedReader getReader() throws IOException {
    if (rdr == null) {
      if (inp != null) {
        throw new IllegalStateException("getInputStream() method has already been called for this request");
      }
      String enc = getCharacterEncoding();
      rdr = new BufferedReader(new InputStreamReader(getInputStream(), enc));
    }
    return rdr;
  }

  @Override
  public void close() throws Exception {
    if (inp != null) {
      inp.close();
    }
  }
}

