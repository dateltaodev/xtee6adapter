package ee.datel.xtee.proxy.pojo;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import ee.datel.xtee.proxy.util.BufferOutputStream;
import ee.datel.xtee.proxy.util.Constants;
import ee.datel.xtee.proxy.util.XmlTagWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.SequenceInputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Vector;

@JacksonXmlRootElement(localName = "Header")
public class Xtee6Header implements XteeHeader {
  private static final String EMPTY = "";
  private static final String XTEE_XMLNS = "http://x-road.eu/xsd/xroad.xsd";
  private static final String ID_XMLNS = "http://x-road.eu/xsd/identifiers";
  private static final String METAXMLNS =
      XTEE_XMLNS + "\" xmlns:" + XteeHeader.XIDENT_PRFX + "=\"" + ID_XMLNS;

  private static final byte[] startXml =
      ("<" + Constants.SOAPPREFIX + ":Envelope xmlns:" + Constants.SOAPPREFIX
          + "=\"http://schemas.xmlsoap.org/soap/envelope/\">").getBytes(StandardCharsets.UTF_8);
  private static final String midXml = "<" + Constants.SOAPPREFIX + ":Body>%n<"
      + XteeHeader.SRV_PRFX + ":%sResponse xmlns:" + XteeHeader.SRV_PRFX + "=\"%s\">%n";
  private static final String endXml = "%n</" + XteeHeader.SRV_PRFX + ":%sResponse>%n</"
      + Constants.SOAPPREFIX + ":Body></" + Constants.SOAPPREFIX + ":Envelope>";

  @JacksonXmlProperty(localName = "client")
  private Participant clent;
  @JacksonXmlProperty(localName = "service")
  private Participant service;

  private String id;
  private String userId;
  private String issue;
  // @JacksonXmlProperty(localName = "protocolVersion")
  // private String protocolVersion;

  @JacksonXmlProperty(localName = "id")
  protected void setId(final String id) {
    this.id = id == null ? EMPTY : id;
  }

  @JacksonXmlProperty(localName = "userId")
  protected void setUserId(final String userId) {
    this.userId = userId == null ? EMPTY : userId;
  }

  @JacksonXmlProperty(localName = "issue")
  protected void setIssue(final String issue) {
    this.issue = issue == null ? EMPTY : issue;
  }

  @Override
  public InputStream getResponseHeader(final String xmlns) throws IOException {
    Vector<InputStream> streams = new Vector<>();
    streams.add(new ByteArrayInputStream(startXml));
    streams.add(getInputStream());
    streams
        .add(getStream(midXml, new Object[] {getServiceCode(), xmlns == null ? METAXMLNS : xmlns}));
    return new SequenceInputStream(streams.elements());
  }

  @Override
  public InputStream getEnvelopeEnd() {
    return getStream(endXml, new Object[] {getServiceCode()});
  }

  @Override
  public InputStream getInputStream() throws IOException {
    BufferOutputStream inp = new BufferOutputStream();
    try (XmlTagWriter wrt = new XmlTagWriter(new OutputStreamWriter(inp, StandardCharsets.UTF_8))) {
      wrt.writeTag(XteeHeader.SOAPENV_PRFX, "Header",
          new XmlTagWriter.TagAttribute("xmlns", XteeHeader.XTEE_PRFX, XTEE_XMLNS),
          new XmlTagWriter.TagAttribute("xmlns", XteeHeader.XIDENT_PRFX, ID_XMLNS));
      // client
      wrt.writeTag(XteeHeader.XTEE_PRFX, "client",
          new XmlTagWriter.TagAttribute(XteeHeader.XIDENT_PRFX, "objectType", clent.getType()));
      writeParticipant(wrt, clent);
      wrt.writeEndTag();
      // service
      wrt.writeTag(XteeHeader.XTEE_PRFX, "service",
          new XmlTagWriter.TagAttribute(XteeHeader.XIDENT_PRFX, "objectType", service.getType()));
      writeParticipant(wrt, service);
      wrt.writeEndTag();

      wrt.writeValueTag(XteeHeader.XTEE_PRFX, "id", getRequestId());
      if (getUserId() != null) {
        wrt.writeValueTag(XteeHeader.XTEE_PRFX, "userId", getUserId());
      }
      if (getIssue() != null) {
        wrt.writeValueTag(XteeHeader.XTEE_PRFX, "issue", getIssue());
      }
      wrt.writeValueTag(XteeHeader.XTEE_PRFX, "protocolVersion", getProtocolVersion());

      wrt.write("<!-- <requestInfo xmlns=\"\"><timestamp>");
      wrt.write(LocalDateTime.now().toString());
      wrt.write("</timestamp><logged>");
      wrt.write(Thread.currentThread().getName());
      wrt.write("</logged></requestInfo> -->");

      wrt.writeEndTag(); // Header
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
    return new ByteArrayInputStream(inp.getBuffer(), 0, inp.size());
  }

  private void writeParticipant(final XmlTagWriter wrt, final Participant part) throws IOException {
    wrt.writeValueTag(XteeHeader.XIDENT_PRFX, "xRoadInstance", part.getXroadInstance());
    wrt.writeValueTag(XteeHeader.XIDENT_PRFX, "memberClass", part.getMemberClass());
    wrt.writeValueTag(XteeHeader.XIDENT_PRFX, "memberCode", part.getMemberCode());
    if (part.getSubsystemCode() != null) {
      wrt.writeValueTag(XteeHeader.XIDENT_PRFX, "subsystemCode", part.getSubsystemCode());
    }
    if (part == service) {
      wrt.writeValueTag(XteeHeader.XIDENT_PRFX, "serviceCode", part.getServiceCode());
      if (part.getServiceVersion() != null) {
        wrt.writeValueTag(XteeHeader.XIDENT_PRFX, "serviceVersion", part.getServiceVersion());
      }
    }
  }

  private String getIssue() {
    return issue;
  }

  @Override
  public String getProtocolVersion() {
    return "4.0";
  }

  @Override
  public String getServiceMemberCode() {
    return service.getMemberCode();
  }

  @Override
  public String getClientMemberCode() {
    return clent.getMemberCode();
  }

  @Override
  public String getServiceSubsystemCode() {
    return service.getSubsystemCode();
  }

  @Override
  public String getServiceCode() {
    return service.getServiceCode();
  }

  @Override
  public String getServiceVersion() {
    return service.getServiceVersion();
  }

  @Override
  public void setServiceVersion(final String version) {
    service.serviceVersion = version;
  }

  @Override
  public String getServiceXroadInstance() {
    return service.getXroadInstance();
  }

  @Override
  public String getServiceMemberClass() {
    return service.getMemberClass();
  }

  @Override
  public String getUserId() {
    return userId;
  }

  @Override
  public String getRequestId() {
    return id;
  }

  @Override
  public String getFullName() {
    return "" + getServiceSubsystemCode() + "." + getServiceCode() + "." + getServiceVersion();
  }

  private InputStream getStream(final String format, final Object[] val) {
    BufferOutputStream byd = new BufferOutputStream();
    Writer wrt;
    try {
      wrt = new OutputStreamWriter(byd, StandardCharsets.UTF_8.name());
      wrt.write(String.format(format, val));
      wrt.close();
    } catch (IOException e) {
      // never
    }
    return new ByteArrayInputStream(byd.getBuffer(), 0, byd.size());
  }

  private static class Participant {
    @JacksonXmlProperty(localName = "objectType", isAttribute = true)
    private String type;
    @JacksonXmlProperty(localName = "xRoadInstance")
    private String xroadInstance;
    @JacksonXmlProperty(localName = "memberClass")
    private String memberClass;
    @JacksonXmlProperty(localName = "memberCode")
    private String memberCode;
    @JacksonXmlProperty(localName = "subsystemCode")
    private String subsystemCode;
    @JacksonXmlProperty(localName = "serviceCode")
    private String serviceCode;
    @JacksonXmlProperty(localName = "serviceVersion")
    private String serviceVersion;

    protected String getType() {
      return type;
    }

    protected String getXroadInstance() {
      return xroadInstance;
    }

    protected String getMemberClass() {
      return memberClass;
    }

    protected String getMemberCode() {
      return memberCode;
    }

    protected String getSubsystemCode() {
      return subsystemCode;
    }

    protected String getServiceCode() {
      return serviceCode;
    }

    protected String getServiceVersion() {
      return serviceVersion;
    }
  }
}
