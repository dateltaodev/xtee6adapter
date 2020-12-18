package ee.datel.xtee.proxy.server.logger;

import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

class ResponseLoggingOutputStream extends ServletOutputStream {
  private static final byte[] NULL = new byte[] {'n', 'u', 'l', 'l'};

  private final Path file;
  private BufferedOutputStream log;

  public ResponseLoggingOutputStream(final Path file) throws IOException {
    this.file = file;
    log = new BufferedOutputStream(Files.newOutputStream(file));
  }

  @Override
  public boolean isReady() {
    return true;
  }

  @Override
  public void setWriteListener(final WriteListener writeListener) {}

  @Override
  public void write(final int ch) throws IOException {
    log.write(ch);
  }

  @Override
  public void print(final String str) throws IOException {
    write(str == null ? NULL : str.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public void close() throws IOException {
    if (log != null) {
      try {
        log.close();
      } catch (IOException ex) {
        LoggerFactory.getLogger(getClass()).error(ex.getMessage(), ex);
      }
      log = null;
    }
  }

  public void reset() {
    try {
      close();
      log = new BufferedOutputStream(Files.newOutputStream(file));
    } catch (IOException e) {
      // can'nt happen
    }
  }
}
