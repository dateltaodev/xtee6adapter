package ee.datel.xtee.proxy.response;

import ee.datel.xtee.proxy.pojo.XteeHeader;
import ee.datel.xtee.proxy.request.ProxyRequest;
import ee.datel.xtee.proxy.util.BufferOutputStream;
import ee.datel.xtee.proxy.util.Constants;
import ee.datel.xtee.proxy.util.MessageInputStream;

import org.apache.http.HttpMessage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service mode=xtee5 provider.
 *
 * @author aldoa
 *
 */
class Xtee5Client extends ServiceClient {
  private static final String headerXml = "<" + Constants.SOAPPREFIX + ":Envelope xmlns:" + Constants.SOAPPREFIX
              + "=\"http://schemas.xmlsoap.org/soap/envelope/\">" + "<" + Constants.SOAPPREFIX
              + ":Header xmlns:xrd=\"http://x-road.ee/xsd/x-road.xsd\">"
              + "%n  <xrd:producer>%s</xrd:producer>%n  <xrd:consumer>%s</xrd:consumer>"
              + "%n  <xrd:userId>%s</xrd:userId>%n  <xrd:id>%s</xrd:id>%n  <xrd:service>%s.%s.%s</xrd:service>" + "%n</"
              + Constants.SOAPPREFIX + ":Header><" + Constants.SOAPPREFIX + ":Body>%n<srv:%s xmlns:srv=\"%s\">%n";
  private static final String endXml = "%n</srv:%s>%n</" + Constants.SOAPPREFIX + ":Body></" + Constants.SOAPPREFIX + ":Envelope>";
  private static final Map<String, byte[]> ENDS = new ConcurrentHashMap<>();

  @Override
  public InputStream getClientHeader(final XteeHeader header, final String xmlns) {
    BufferOutputStream byd = new BufferOutputStream();
    Writer wrt;
    try {
      wrt = new OutputStreamWriter(byd, StandardCharsets.UTF_8.name());
      wrt.write(String.format(headerXml, header.getServiceSubsystemCode(), header.getClientMemberCode(), header.getUserId(),
                  header.getRequestId(), header.getServiceSubsystemCode(), header.getServiceCode(), header.getServiceVersion(),
                  header.getServiceCode(), xmlns));
      wrt.close();
    } catch (IOException e) {
      // never
    }
    return new ByteArrayInputStream(byd.getBuffer(), 0, byd.size());
  }

  @Override
  public InputStream getClientBody(final MessageInputStream inputStream, final String serviceCode) throws IOException {
    return inputStream.getRequestBody(serviceCode, 3);
  }

  @Override
  public InputStream getEnvelopeEnd(final XteeHeader header) {
    byte[] value = ENDS.get(header.getServiceCode());
    if (value == null) {
      BufferOutputStream byd = new BufferOutputStream();
      Writer wrt;
      try {
        wrt = new OutputStreamWriter(byd, StandardCharsets.UTF_8.name());
        wrt.write(String.format(endXml, header.getServiceCode()));
        wrt.close();
      } catch (IOException e) {
        // never
      }
      value = new byte[byd.size()];
      System.arraycopy(byd.getBuffer(), 0, value, 0, byd.size());
      ENDS.put(header.getServiceCode(), value);
    }
    return new ByteArrayInputStream(value);
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
  protected void addAdapterHeaders(final HttpMessage post, final XteeHeader header) {}

  @Override
  protected String getAdapterUrl(final ProxyRequest proxyRequest) {
    return proxyRequest.getServiceUrl();
  }

  @Override
  public InputStream getAdapterResponseBody(final MessageInputStream inputStream, final String serviceCode) throws IOException {
    return inputStream.getRequestBody(serviceCode + "Response", 3);
  }

}
