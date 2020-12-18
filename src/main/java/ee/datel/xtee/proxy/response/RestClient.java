package ee.datel.xtee.proxy.response;

import ee.datel.xtee.proxy.pojo.XteeHeader;
import ee.datel.xtee.proxy.request.ProxyRequest;
import ee.datel.xtee.proxy.util.MessageInputStream;

import org.apache.http.HttpMessage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

class RestClient extends ServiceClient {
  private static final String SERVICE_NSPREFIX = "ns1";

  @Override
  public InputStream getClientHeader(final XteeHeader header, final String xmlns) {
    return new ByteArrayInputStream(
                ("<" + SERVICE_NSPREFIX + ":" + header.getServiceCode() + " xmlns:" + SERVICE_NSPREFIX + "=\"" + xmlns + "\">")
                            .getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public InputStream getEnvelopeEnd(final XteeHeader header) {
    return new ByteArrayInputStream(
                ("</" + SERVICE_NSPREFIX + ":" + header.getServiceCode() + ">").getBytes(StandardCharsets.UTF_8));
  }

  @Override
  protected ServiceResponse parseResponse(final InputStream inp, final String contentType, final String contentEncoding)
              throws IOException {
    ServiceResponse response = new ServiceResponse(inp, contentType, contentEncoding);
    return response;
  }

  @Override
  protected String getContentType(final ProxyRequest proxyRequest) {
    return "text/xml";
  }

  @Override
  protected void addAdapterHeaders(final HttpMessage post, final XteeHeader header) {
    addRestHeaders(post, header);
  }

  @Override
  protected String getAdapterUrl(final ProxyRequest proxyRequest) {
    String url = proxyRequest.getServiceUrl();

    try {
      String version = proxyRequest.getXteeHeader().getServiceVersion();
      String serviceCode = proxyRequest.getXteeHeader().getServiceCode();
      if (!url.endsWith("/")) {
        url += "/";
      }
      return url + serviceCode + "/" + version;
    } catch (IOException e) {
      return url;
    }
  }

  @Override
  public InputStream getClientBody(final MessageInputStream inputStream, final String serviceCode) throws IOException {
    return inputStream.getRequestBody(serviceCode, 3);
  }

  @Override
  public InputStream getAdapterResponseBody(final MessageInputStream inputStream, final String serviceCode) throws IOException {
    return inputStream.getRequestBody(serviceCode + "Response", 1);
  }
}
