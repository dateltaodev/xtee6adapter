package ee.datel.xtee.proxy.response;

import ee.datel.xtee.proxy.exception.SoapFault.FaultCode;
import ee.datel.xtee.proxy.exception.SoapFaultException;
import ee.datel.xtee.proxy.pojo.XteeHeader;
import ee.datel.xtee.proxy.request.ProxyRequest;
import ee.datel.xtee.proxy.util.MessageInputStream;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.http.HttpEntity;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter server client.
 * <p>
 * When logger on TRACE level, writes request into {@code System.getProperty("java.io.tmpdir")}
 * path.
 * </p>
 *
 * @author aldoa
 *
 */
public abstract class ServiceClient {
  private static CloseableHttpClient httpClient;
  private static final int proxyConnectTimeout = 5 * 1000; // 5 seconds
  private static final int proxySocketTimeout = 20 * 60 * 1000; // 20 minutes

  private static final String HEADER_MEMBER = "Xtee-ClientMemberCode";
  private static final String HEADER_USER = "Xtee-UserId";
  private static final String HEADER_THREAD = "Xtee-ThreadNumber";
  private static final String HEADER_REQUESTID = "Xtee-RequestId";

  private final Logger logger = LoggerFactory.getLogger(getClass());
  protected final InputStream empty = new ByteArrayInputStream(new byte[0]);

  /**
   * Initializes component.
   */
  public static void init() {
    final ConnectionSocketFactory plainsf = PlainConnectionSocketFactory.getSocketFactory();
    final LayeredConnectionSocketFactory sslsf = SSLConnectionSocketFactory.getSocketFactory();
    final Registry<ConnectionSocketFactory> registry =
        RegistryBuilder.<ConnectionSocketFactory>create().register("http", plainsf).register("https", sslsf).build();
    final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(registry);
    cm.setMaxTotal(100);
    cm.setDefaultMaxPerRoute(20);
    final RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(proxyConnectTimeout)
        .setSocketTimeout(proxySocketTimeout).setRedirectsEnabled(false).build();
    httpClient = HttpClients.custom().setConnectionManager(cm).setDefaultRequestConfig(requestConfig)
        .disableCookieManagement().build();
  }

  /**
   * Destroys component.
   */
  public static void destroy() {
    if (httpClient != null) {
      try {
        httpClient.close();
      } catch (IOException e) {
        // nothing to do
      }
    }
  }

  public static ServiceClient getClient(final ServiceClientType type) {
    return type.getClient();
  }

  protected ServiceClient() {

  }

  /**
   * Performs request.
   * 
   * @param request request
   * @param proxyRequest request data
   * @return response
   * @throws IOException process error
   */
  public ServiceResponse doRequest(final InputStream request, final ProxyRequest proxyRequest) throws IOException {
    final InputStreamEntity entity;
    InputStream requestStream = null;
    if (logger.isTraceEnabled()) {
      Path path = Paths.get(System.getProperty("java.io.tmpdir"), "sent-" + Thread.currentThread().getName() + ".xml");
      Files.copy(request, path);
      requestStream = Files.newInputStream(path);
      entity = new InputStreamEntity(request);
      path.toFile().deleteOnExit();
    } else {
      entity = new InputStreamEntity(request);
    }
    entity.setContentType(getContentType(proxyRequest));
    final String url = getAdapterUrl(proxyRequest);
    if (url == null) {
      throw new IllegalStateException("Missing adapter url");
    }
    HttpPost post = new HttpPost(url);
    proxyRequest.addAdapterHeaders(post);
    addAdapterHeaders(post, proxyRequest.getXteeHeader());
    post.setEntity(entity);
    long start = System.currentTimeMillis();
    try (CloseableHttpResponse adapterResponse = httpClient.execute(post)) {
      proxyRequest.close(); // close input log file
      if (logger.isTraceEnabled()) {
        logger.trace("Execute {} - {}ms", proxyRequest.getXteeHeader().getFullName(),
            Long.toString(System.currentTimeMillis() - start));
      }
      final int status = adapterResponse.getStatusLine().getStatusCode();
      final ServiceResponse response;
      if (status >= HttpStatus.SC_BAD_REQUEST && status < HttpStatus.SC_INTERNAL_SERVER_ERROR) {
        String message = adapterResponse.getStatusLine().getReasonPhrase();
        logger.error("{} Status: {} {}", url, Integer.toString(status), message, new IOException(message));
        throw new SoapFaultException(FaultCode.SERVER, "" + status + " - " + message);
      } else if (status >= HttpStatus.SC_INTERNAL_SERVER_ERROR) {
        String message = extractErrorMessage(adapterResponse);
        if (status == HttpStatus.SC_INTERNAL_SERVER_ERROR && this instanceof MtomClient
            && message.indexOf("http://schemas.xmlsoap.org/soap/envelope/") > 0) {
          byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
          response = parseResponse(new ByteArrayInputStream(bytes), null, null);
        } else {
          logger.warn("{} {}\n\tStatus:{} {}", proxyRequest.getXteeHeader().getFullName(), url,
              Integer.toString(status), message);
          throw new SoapFaultException(FaultCode.SERVER, "" + status + " - " + message);
        }
      } else {
        HttpEntity result = adapterResponse.getEntity();
        response = parseResponse(result.getContent(),
            result.getContentType() == null ? null : result.getContentType().getValue(),
            result.getContentEncoding() != null ? result.getContentEncoding().getValue() : null);
      }
      return response;
    } catch (SoapFaultException e) {
      throw e;
    } catch (InterruptedIOException e) {
      logger.warn("Timeout: {}ms: {} {}", Long.toString(System.currentTimeMillis() - start),
          proxyRequest.getXteeHeader().getFullName(), url);
      throw new SoapFaultException(FaultCode.SERVER, "Timeout " + (System.currentTimeMillis() - start) / 1000 + " sec");
    } catch (ClientProtocolException e) {
      final Throwable ex = getCause(e);
      logger.error("{}\n\t{}", url, ex.getMessage(), ex);
      throw new SoapFaultException(FaultCode.SERVER, ex.getClass().getName());
    } catch (IOException e) {
      if (!e.getMessage().contains("Connection reset by peer")) {
        final Throwable ex = getCause(e);
        logger.warn("{} {}\n\tException: {}", proxyRequest.getXteeHeader().getFullName(), url, ex.getMessage());
        throw new SoapFaultException(FaultCode.SERVER, ex.getMessage(), ex.getClass().getName());
      } else {
        throw e;
      }
    } catch (Exception e) {
      final Throwable ex = getCause(e);
      logger.error("{} {}\n\tException: {}", proxyRequest.getXteeHeader().getFullName(), url, e.getMessage(), e);
      throw new SoapFaultException(FaultCode.SERVER, ex.getMessage(), ex.getClass().getName());
    } finally {
      post.reset();
      if (requestStream != null) {
        requestStream.close();
      }
    }
  }

  private Throwable getCause(Exception ex) {
    Throwable th = ex;
    while (th.getCause() != null) {
      th = th.getCause();
    }
    return th;
  }

  private String extractErrorMessage(final HttpResponse response) {
    StringBuilder sb = new StringBuilder();
    HttpEntity result = response.getEntity();
    if (result == null || !result.isChunked() && result.getContentLength() == 0) {
      sb.append(response.getStatusLine().getReasonPhrase());
    } else {
      try (InputStream in = result.getContent()) {
        if (in != null) {
          try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String ln;
            while ((ln = br.readLine()) != null) {
              if (sb.length() > 0) {
                sb.append('\n');
              }
              sb.append(ln);
            }
          }
        }
        if (sb.length() == 0) {
          sb.append(response.getStatusLine().getReasonPhrase());
        }
      } catch (IOException e) {
        sb.append(response.getStatusLine().getReasonPhrase());
      } catch (RuntimeException e) {
        sb.append(e.getMessage());
      }
    }
    return sb.toString();
  }

  protected void addRestHeaders(final HttpMessage post, final XteeHeader header) {
    post.addHeader(HEADER_MEMBER, header.getClientMemberCode());
    post.addHeader(HEADER_USER, header.getUserId() == null ? "" : header.getUserId());
    post.addHeader(HEADER_THREAD, Thread.currentThread().getName());
    post.addHeader(HEADER_REQUESTID, header.getRequestId());
  }


  protected abstract String getContentType(ProxyRequest proxyRequest);

  public abstract InputStream getClientHeader(XteeHeader header, String xmlns);

  public abstract InputStream getClientBody(MessageInputStream inputStream, String serviceCode) throws IOException;

  public abstract InputStream getEnvelopeEnd(XteeHeader header);

  protected abstract ServiceResponse parseResponse(InputStream inp, String contentType, String contentEncoding)
      throws IOException;

  protected abstract void addAdapterHeaders(HttpMessage post, XteeHeader header);

  protected abstract String getAdapterUrl(ProxyRequest proxyRequest);

  public abstract InputStream getAdapterResponseBody(MessageInputStream inputStream, String serviceCode)
      throws IOException;

}
