package ee.datel.xtee.proxy.server.logger;

import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

class RequestLoggingInputStream extends ServletInputStream {
  private InputStream input;
  private boolean eof;

  public RequestLoggingInputStream(final Path file) throws IOException {
    input = new BufferedInputStream(Files.newInputStream(file));
  }

  @Override
  public boolean isFinished() {
    return eof;
  }

  @Override
  public boolean isReady() {
    return !eof;
  }

  @Override
  public void setReadListener(final ReadListener readListener) {}

  @Override
  public int read() throws IOException {
    int ch = -1;
    if (!isFinished()) {
      ch = input.read();
      if (ch == -1) {
        eof = true;
      }
    }
    return ch;
  }

  @Override
  public void close() throws IOException {
    if (input != null) {
      try {
        input.close();
      } catch (IOException ex) {
        LoggerFactory.getLogger(getClass()).error(ex.getMessage(), ex);
      }
      input = null;
    }
  }
}
