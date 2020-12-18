package ee.datel.xtee.proxy.server.logger;

import ee.datel.xtee.proxy.server.ServerConfiguration;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

class ResponseLoggerWrapper extends HttpServletResponseWrapper implements AutoCloseable {
  private final Path log;
  private ResponseLoggingOutputStream out;
  private PrintWriter wrt;
  private String type;

  /**
   * Constructor.
   *
   * @param request request
   * @param response response
   */
  public ResponseLoggerWrapper(final HttpServletRequest request, final HttpServletResponse response) {
    super(response);
    ServerConfiguration conf = (ServerConfiguration) request.getAttribute(ServerConfiguration.class.getName());
    log = conf.getTodaysPath().resolve(Thread.currentThread().getName() + "-.log");
    request.setAttribute(ResponseLoggingOutputStream.class.getName(), log);
  }

  @Override
  public void setContentType(final String type) {
    this.type = type;
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    if (out == null) {
      if (wrt != null) {
        throw new IllegalStateException("getWriter() method has already been called for this request");
      }
      out = new ResponseLoggingOutputStream(log);
    }
    return out;
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    if (wrt == null) {
      if (out != null) {
        throw new IllegalStateException("getOutputStream() method has already been called for this request");
      }
      String enc = getCharacterEncoding();
      wrt = new PrintWriter(new OutputStreamWriter(getOutputStream(), enc));
    }
    return wrt;
  }

  @Override
  public boolean isCommitted() {
    return false;
  }

  @Override
  public void reset() {
    if (out != null) {
      out.reset();
    }
    out = null;
    wrt = null;
  }

  @Override
  public void close() throws Exception {
    if (out != null) {
      out.close();
      out = null;
      long size = Files.size(log);
      if (size > 0) {
        getResponse().setContentType(type == null ? "text/xml" : type);
        getResponse().setContentLengthLong(size);
        if (type == null) {
          getResponse().setCharacterEncoding(StandardCharsets.UTF_8.name());
        }
        try (OutputStream response = getResponse().getOutputStream()) {
          Files.copy(log, response);
          response.flush();
        }
      }
    }
  }
}
