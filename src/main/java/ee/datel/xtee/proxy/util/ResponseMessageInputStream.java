package ee.datel.xtee.proxy.util;

import ee.datel.xtee.proxy.exception.SoapFaultException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;

public class ResponseMessageInputStream extends MessageInputStream {
  protected static final byte[] ENVELOPE = "Envelope".getBytes(StandardCharsets.UTF_8);
  protected static final byte[] BODY = "Body".getBytes(StandardCharsets.UTF_8);
  protected static final byte[] FAULT = "Fault".getBytes(StandardCharsets.UTF_8);
  protected static final byte[] XMLPREAMBLE =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n".getBytes(StandardCharsets.UTF_8);

  private boolean fault;

  public ResponseMessageInputStream(final InputStream source, final boolean isXml)
      throws IOException {
    super(source, isXml);
  }

  public boolean isFault() {
    return fault;
  }

  /**
   * Builds new fault response.
   */
  public InputStream getFaultInputStream() throws IOException {
    if (!fault) {
      return Constants.EMPTY;
    }
    Fault fault = XteeParser.getXteeParser()
        .parse(new SequenceInputStream(getPreambleInputStream(), source), Fault.class);
    throw new SoapFaultException(fault);
  }

  @Override
  public InputStream getPreambleInputStream() throws IOException {
    return new ByteArrayInputStream(getPreamble().length == 0 ? XMLPREAMBLE : getPreamble());
  }


  @Override
  public byte[] getXteeHeader() throws IOException {
    if (xteeHeader == null && !end) {
      xteeHeader = Constants.NULL;
      trimToLt();
      int len = readNext();
      if (isTag(ENVELOPE, len)) {
        trimToLt();
        try (BufferOutputStream collect = new BufferOutputStream()) {
          collect.write(buffer, 0, len);
          len = readNext();
          if (isTag(HEADER, len)) {
            if (buffer[len - 2] != '/') {
              partend = false;
              int stop = level;
              while (readTillLevelEnd(stop) != -1) { // swing to header end
              }
            }
            collect.write(buffer, 0, len);
            trimToLt();
            len = readNext();
          }
          if (isTag(BODY, len)) {
            collect.write(buffer, 0, len);
            trimToLt();
            len = readNext();
            if (isTag(FAULT, len)) {
              fault = true;
            }
            source.unread(buffer, 0, len);
            source.unread(collect.getBuffer(), 0, collect.size());
            level = 0;
          }
        }
      } else { // not Envelope
        source.unread(buffer, 0, len);
        level = 0;
      }
    }
    return xteeHeader;
  }
}
