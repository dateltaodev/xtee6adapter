package ee.datel.xtee.proxy.exception;

import ee.datel.xtee.proxy.util.Constants;

import org.apache.commons.lang3.StringEscapeUtils;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import javax.servlet.ServletResponse;

public class SoapFault {
  public enum FaultCode {
    SERVER(Constants.SOAPPREFIX + ":Server"), CLIENT(Constants.SOAPPREFIX + ":Client");
    private final String code;

    private FaultCode(final String code) {
      this.code = code;
    }

    @Override
    public String toString() {
      return code;
    }
  }

  // private static final Logger LOGGER = LoggerFactory.getLogger(SoapFault.class);
  private static final String FAULT_BEGIN = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "\n<" + Constants.SOAPPREFIX
              + ":Envelope xmlns:" + Constants.SOAPPREFIX + "=\"http://schemas.xmlsoap.org/soap/envelope/\"><"
              + Constants.SOAPPREFIX + ":Body><" + Constants.SOAPPREFIX + ":Fault>";
  private static final String FAULTCODE = "%n  <faultcode>%s</faultcode>%n  <faultstring>%s</faultstring>";
  private static final String FAULTACTOR = "%n  <faultactor>%s</faultactor>";
  private static final String FAULTDETAIL = "<faultDetail xmlns=\"\">%s</faultDetail>";
  private static final String FAULT_END =
              "\n</" + Constants.SOAPPREFIX + ":Fault></" + Constants.SOAPPREFIX + ":Body></" + Constants.SOAPPREFIX + ":Envelope>";

  /**
   * Writes fault to response.
   *
   * @param resp servlet response
   * @param exception thrown exception
   */
  public static void writeFault(final ServletResponse resp, final SoapFaultException exception) {
    resp.setContentType("text/xml");
    resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
    try {
      Writer wrt = resp.getWriter();
      writeFault(wrt, exception);
    } catch (IOException e) {
      // Nothing to do. Probably connection closed.
    }

  }

  /**
   * Writes fault to output stream.
   *
   * @param out output stream
   * @param exception thrown exception
   */
  public static void writeFault(final OutputStream out, final SoapFaultException exception) {
    Writer wrt = new OutputStreamWriter(new BufferedOutputStream(out), StandardCharsets.UTF_8);
    writeFault(wrt, exception);
  }

  /**
   * Writes fault to writer.
   *
   * @param wrt output writer
   * @param exception thrown exception
   */
  public static void writeFault(final Writer wrt, final SoapFaultException exception) {
    try {
      wrt.write(FAULT_BEGIN);
      wrt.append(String.format(FAULTCODE, StringEscapeUtils.escapeXml10(exception.getCode()),
                  StringEscapeUtils.escapeXml10(exception.getString())));
      if (exception.getActor() != null || exception.getDetail() != null) {
        wrt.append(String.format(FAULTACTOR,
                    exception.getActor() == null ? "" : StringEscapeUtils.escapeXml10(exception.getActor())));
      }
      wrt.write("\n  <detail>");
      if (exception.getDetail() != null) {
        wrt.append(String.format(FAULTDETAIL, StringEscapeUtils.escapeXml10(exception.getDetail())));
      }
      wrt.write("\n    <requestInfo xmlns=\"\"><timestamp>");
      wrt.write(LocalDateTime.now().toString());
      wrt.write("</timestamp><logged>");
      wrt.write(Thread.currentThread().getName());
      wrt.write("</logged></requestInfo></detail>");
      wrt.write(FAULT_END);
      wrt.flush();
    } catch (IOException e) {
      // Nothing to do. Probably connection closed.
    }
  }
}
