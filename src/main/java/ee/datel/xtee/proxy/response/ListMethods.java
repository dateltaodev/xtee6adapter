package ee.datel.xtee.proxy.response;

import ee.datel.xtee.proxy.exception.SoapFault.FaultCode;
import ee.datel.xtee.proxy.exception.SoapFaultException;
import ee.datel.xtee.proxy.pojo.XteeHeader;
import ee.datel.xtee.proxy.request.ProxyRequest;
import ee.datel.xtee.proxy.server.ServerConfiguration;
import ee.datel.xtee.proxy.util.BufferOutputStream;
import ee.datel.xtee.proxy.util.Constants;
import ee.datel.xtee.proxy.util.MessageInputStream;
import ee.datel.xtee.proxy.util.XmlTagWriter;

import org.apache.http.HttpMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

class ListMethods extends ServiceClient {
  public static final String VERSIONCOMMENT = "version:%s-%s from:%s";
  private final Logger logger = LoggerFactory.getLogger(ListMethods.class);

  @Override
  public ServiceResponse doRequest(final InputStream request, final ProxyRequest proxyRequest)
              throws IOException, SoapFaultException {
    return new ServiceResponse(getListMethods(proxyRequest.getConfiguration(), proxyRequest.getXteeHeader()),
                "text/xml;charset=UTF-8", null);
  }

  private InputStream getListMethods(final ServerConfiguration conf, final XteeHeader header) throws SoapFaultException {
    BufferOutputStream inp = new BufferOutputStream();
    try (XmlTagWriter wrt = new XmlTagWriter(new OutputStreamWriter(inp, StandardCharsets.UTF_8))) {
      wrt.writeTag(null, "Envelope");
      wrt.writeTag(null, "Body");
      wrt.writeTag(null, "listMethodsResponse");
      wrt.writeComment(String.format(VERSIONCOMMENT, conf.getProxyPropertie("metadata.version"),
                  conf.getProxyPropertie("metadata.profile"), conf.getProxyPropertie("metadata.date")));
      for (String subs : conf.getSubsystems()) {
        for (String srvc : conf.getSubsystemServices(subs)) {
          wrt.writeTag(XteeHeader.SRV_PRFX, "service",
                      new XmlTagWriter.TagAttribute(XteeHeader.XIDENT_PRFX, "objectType", "SERVICE"));
          wrt.writeValueTag(XteeHeader.XIDENT_PRFX, "xRoadInstance", header.getServiceXroadInstance());
          wrt.writeValueTag(XteeHeader.XIDENT_PRFX, "memberClass", header.getServiceMemberClass());
          wrt.writeValueTag(XteeHeader.XIDENT_PRFX, "memberCode", header.getServiceMemberCode());
          wrt.writeValueTag(XteeHeader.XIDENT_PRFX, "subsystemCode", subs);
          wrt.writeValueTag(XteeHeader.XIDENT_PRFX, "serviceCode", srvc);
          wrt.writeValueTag(XteeHeader.XIDENT_PRFX, "serviceVersion",
                      conf.getProxyPropertie(ServerConfiguration.PRFX + subs + "." + srvc + ".version"));
          wrt.writeEndTag();
        }
      }
      wrt.writeEndTag();
      wrt.writeEndTag();
      wrt.writeEndTag();
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      throw new SoapFaultException(FaultCode.SERVER, e.getMessage(), e.getClass().getName());
    }
    return new ByteArrayInputStream(inp.getBuffer(), 0, inp.size());
  }

  @Override
  protected String getContentType(final ProxyRequest proxyRequest) {
    return null;
  }

  @Override
  public InputStream getClientHeader(final XteeHeader header, final String xmlns) {
    return Constants.EMPTY;
  }

  @Override
  public InputStream getEnvelopeEnd(final XteeHeader header) {
    return Constants.EMPTY;
  }

  @Override
  protected ServiceResponse parseResponse(final InputStream inp, final String contentType, final String contentEncoding)
              throws IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  protected void addAdapterHeaders(final HttpMessage post, final XteeHeader header) {}

  @Override
  protected String getAdapterUrl(final ProxyRequest proxyRequest) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public InputStream getClientBody(final MessageInputStream inputStream, final String serviceCode) throws IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public InputStream getAdapterResponseBody(final MessageInputStream inputStream, final String serviceCode) throws IOException {
    // TODO Auto-generated method stub
    return null;
  }

}
