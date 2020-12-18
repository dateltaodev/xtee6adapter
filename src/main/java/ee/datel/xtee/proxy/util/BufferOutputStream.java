package ee.datel.xtee.proxy.util;

import java.io.ByteArrayOutputStream;

public class BufferOutputStream extends ByteArrayOutputStream {
  public BufferOutputStream() {
    super(1024);
  }

  public byte[] getBuffer() {
    return buf;
  }

}
