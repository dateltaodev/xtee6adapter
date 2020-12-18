package ee.datel.xtee.proxy.request;

import ee.datel.xtee.proxy.exception.SoapFault.FaultCode;
import ee.datel.xtee.proxy.exception.SoapFaultException;
import ee.datel.xtee.proxy.pojo.XteeHeader;
import ee.datel.xtee.proxy.pojo.XteeVersion;
import ee.datel.xtee.proxy.response.ServiceClient;
import ee.datel.xtee.proxy.response.ServiceClientType;
import ee.datel.xtee.proxy.server.ServerConfiguration;
import ee.datel.xtee.proxy.util.MessageInputStream;
import ee.datel.xtee.proxy.util.XteeParser;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.HttpMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Vector;

import javax.servlet.ServletRequest;

public abstract class ProxyRequest implements AutoCloseable {
  private static final Logger logger = LoggerFactory.getLogger(ProxyRequest.class);

  /**
   * Factory method.
   *
   * @param request servlet request
   * @return appropriate parser
   * @throws SoapFaultException re-throws exceptions
   */
  public static ProxyRequest getClient(final ServletRequest request) throws SoapFaultException {
    String contentType = request.getContentType();
    if (contentType != null) {
      ProxyRequest proxer = null;
      try {
        if (contentType.startsWith("text/xml")) {
          proxer = new XmlRequestParser();
          proxer.charset = request.getCharacterEncoding().toUpperCase();
          proxer.contentType = "text/xml;charset=" + proxer.charset;
          proxer.source = new MessageInputStream(request.getInputStream(), true);
        } else if (contentType.startsWith("Multipart/Related") || contentType.startsWith("multipart/related")) {
          proxer = new MultipartRequestParser();
          proxer.contentType = contentType;
          proxer.source = new MessageInputStream(request.getInputStream(), false);
        }
      } catch (IOException e) {
        logger.error(e.getMessage(), e);
        throw new SoapFaultException(FaultCode.SERVER, "Input buffer read error");
      }
      if (proxer != null) {
        proxer.configuration = (ServerConfiguration) request.getAttribute(ServerConfiguration.class.getName());
        try {
          proxer.xteeHeader = proxer.getXteeHeader();
        } catch (IOException e) {
          logger.error(e.getMessage(), e);
          throw new SoapFaultException(FaultCode.CLIENT, "Header read error");
        }
        return proxer;
      }
    }
    throw new SoapFaultException(FaultCode.CLIENT, "Unsupported content type", "Content-Type",
                contentType == null ? "" : StringEscapeUtils.escapeXml10(contentType));
  }

  private String contentType;
  private String charset;
  protected ServerConfiguration configuration;
  protected MessageInputStream source;
  private XteeHeader xteeHeader;
  private String prefix;
  private String type;

  protected ProxyRequest() {}

  /**
   * Constructs adapter server request.
   *
   * @param adapter service client
   * @return adapter server request
   */
  public InputStream getInputStream(final ServiceClient adapter) throws IOException {
    InputStream header = adapter.getClientHeader(getXteeHeader(), getServiceXmlns());
    InputStream body = adapter.getClientBody(source, getXteeHeader().getServiceCode());
    Vector<InputStream> streams = new Vector<>();
    streams.add(source.getPreambleInputStream());
    if (header != null) {
      streams.add(header);
    }
    streams.add(body);
    InputStream end = adapter.getEnvelopeEnd(getXteeHeader());
    if (end != null) {
      streams.add(end);
    }
    streams.add(source.getAttachments());
    return new SequenceInputStream(streams.elements());
  }

  /**
   * Parses XTEE header from proxy request.
   *
   * @return header
   */
  public XteeHeader getXteeHeader() throws IOException {
    if (xteeHeader == null) {
      XteeVersion version = XteeParser.getXteeParser().parseVersion(source.getXteeHeader());
      xteeHeader = XteeParser.getXteeParser().parseHeader(source.getXteeHeader(), version.getXteeHeaderClass());
      prefix = ServerConfiguration.PRFX + xteeHeader.getServiceSubsystemCode() + "." + xteeHeader.getServiceCode() + ".";
      if (xteeHeader.getServiceVersion() == null) {
        xteeHeader.setServiceVersion(configuration.getProxyPropertie(prefix + "version"));
      }
    }
    return xteeHeader;
  }

  /**
   * Determines adapter service type.
   *
   * @return appropriate client
   */
  public ServiceClient getServiceClient() throws IOException {
    type = configuration.getProxyPropertie(prefix + "mode");
    if (type == null) {
      if (xteeHeader.getServiceSubsystemCode() == null) { // meta request
        type = xteeHeader.getServiceCode();
      }
      if (type == null) {
        logger.warn("Not implemented method {}", xteeHeader.getFullName());
        throw new SoapFaultException(FaultCode.CLIENT, "Not implemented method " + xteeHeader.getFullName());
      }
    }
    ServiceClientType srv = Enum.valueOf(ServiceClientType.class, type);
    return ServiceClient.getClient(srv);
  }

  public String getContextType() {
    return contentType;
  }

  public ServerConfiguration getConfiguration() {
    return configuration;
  }

  @Override
  public void close() throws Exception {
    source.close();
  }

  public String getServiceXmlns() {
    return configuration.getProxyPropertie(prefix + "xmlns");
  }

  public String getServiceUrl() {
    return configuration.getProxyPropertie(prefix + "url");
  }

  public abstract void addAdapterHeaders(HttpMessage post);

  public InputStream getResponseHeader() throws IOException {
    return xteeHeader.getResponseHeader(getServiceXmlns());
  }

  public InputStream getEnvelopeEnd() {
    return xteeHeader.getEnvelopeEnd();
  }

  public String getRequestName() throws IOException {
    return getXteeHeader().getFullName();
  }
}
