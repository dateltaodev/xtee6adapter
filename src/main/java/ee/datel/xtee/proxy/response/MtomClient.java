package ee.datel.xtee.proxy.response;

import ee.datel.xtee.proxy.pojo.XteeHeader;
import ee.datel.xtee.proxy.request.ProxyRequest;
import ee.datel.xtee.proxy.util.Constants;
import ee.datel.xtee.proxy.util.MessageInputStream;

import org.apache.http.HttpMessage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;

class MtomClient extends ServiceClient {
  private static final byte[] headerXml = ("<" + Constants.SOAPPREFIX + ":Envelope xmlns:" + Constants.SOAPPREFIX
              + "=\"http://schemas.xmlsoap.org/soap/envelope/\"><" + Constants.SOAPPREFIX + ":Body>\n")
                          .getBytes(StandardCharsets.UTF_8);
  private static final byte[] endXml =
              ("</" + Constants.SOAPPREFIX + ":Body></" + Constants.SOAPPREFIX + ":Envelope>").getBytes(StandardCharsets.UTF_8);
  private static ThreadLocal<String> myXmlns = new ThreadLocal<>();

  @Override
  public InputStream getClientHeader(final XteeHeader header, final String xmlns) {
    myXmlns.set(xmlns);
    return new ByteArrayInputStream(headerXml);
  }

  @Override
  public InputStream getEnvelopeEnd(final XteeHeader header) {
    return new ByteArrayInputStream(endXml);
  }

  @Override
  protected ServiceResponse parseResponse(final InputStream inp, final String contentType, final String contentEncoding)
              throws IOException {
    ServiceResponse response = new ServiceResponse(inp, contentType, contentEncoding);
    return response;
  }

  @Override
  protected String getContentType(final ProxyRequest proxyRequest) {
    return proxyRequest.getContextType();
  }

  @Override
  protected void addAdapterHeaders(final HttpMessage post, final XteeHeader header) {
    addRestHeaders(post, header);
    post.addHeader("SOAPAction",
                header.getServiceSubsystemCode() + "." + header.getServiceCode() + "." + header.getServiceVersion());
  }

  @Override
  protected String getAdapterUrl(final ProxyRequest proxyRequest) {
    return proxyRequest.getServiceUrl();
  }

  @Override
  public InputStream getClientBody(final MessageInputStream inputStream, final String serviceCode) throws IOException {
    InputStream req = inputStream.getRequestTag(serviceCode, 3);
    PushbackInputStream buf = new PushbackInputStream(req);
    ByteArrayOutputStream hd = new ByteArrayOutputStream();
    int ch = 0;
    while ((ch = buf.read()) != -1) {
      if (ch == '<') {
        hd.write(ch);
        break;
      }
    }
    byte[] pfx = null;
    FIND: while ((ch = buf.read()) != -1) {
      switch (ch) {
        case ':':
          pfx = hd.toByteArray();
          break;
        case ' ':
        case '\n':
        case '\t':
        case '\r':
        case '>':
        case '/':
          buf.unread(ch);
          break FIND;
        default:
          break;
      }
      hd.write(ch);
    }
    hd.write(" xmlns".getBytes(StandardCharsets.US_ASCII));
    if (pfx != null) {
      hd.write(':');
      hd.write(pfx, 1, pfx.length - 1);
    }
    hd.write("=\"".getBytes(StandardCharsets.US_ASCII));
    hd.write(myXmlns.get().getBytes(StandardCharsets.UTF_8));
    myXmlns.remove();
    hd.write('"');
    int prev = 0;
    while ((ch = buf.read()) != -1) {
      if (ch == '>') {
        if (prev == '/') {
          hd.write(prev);
        }
        hd.write(ch);
        break;
      }
      prev = ch;
    }
    return new SequenceInputStream(new ByteArrayInputStream(hd.toByteArray()), buf);
  }

  @Override
  public InputStream getAdapterResponseBody(final MessageInputStream inputStream, final String serviceCode) throws IOException {
    return inputStream.getRequestBody(serviceCode + "Response", 3);
  }
}
