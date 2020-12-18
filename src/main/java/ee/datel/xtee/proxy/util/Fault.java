package ee.datel.xtee.proxy.util;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

@JacksonXmlRootElement(localName = "Envelope")
public class Fault {

  @JacksonXmlProperty(localName = "Body")
  private Body body;

  public String getFaultcode() {
    return body.fault.faultcode;
  }

  public String getFaultstring() {
    return body.fault.faultstring;
  }

  public String getFaultactor() {
    return body.fault.faultactor;
  }

  public String getFaulttDetail() {
    return body.fault.detail == null ? null : body.fault.detail.getFaultDetail();
  }

  private static class Body {
    @JacksonXmlProperty(localName = "Fault")
    private SoapFault fault;
  }

  private static class SoapFault {
    @JacksonXmlProperty(localName = "faultcode")
    private String faultcode;
    @JacksonXmlProperty(localName = "faultstring")
    private String faultstring;
    @JacksonXmlProperty(localName = "faultactor")
    private String faultactor;
    @JacksonXmlProperty(localName = "detail")
    private Detail detail;
  }

  private static class Detail {
    @JacksonXmlText
    private String text;
    @JacksonXmlProperty(localName = "faultDetail")
    private String faultDetail;

    private String getFaultDetail() {
      return faultDetail == null ? text : faultDetail;
    }
  }
}
