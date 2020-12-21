package ee.datel.xtee.proxy.pojo;

import ee.datel.xtee.proxy.exception.SoapFault.FaultCode;
import ee.datel.xtee.proxy.exception.SoapFaultException;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "Header")
public class XteeVersion {
  @JacksonXmlProperty(localName = "protocolVersion")
  private String protocolVersion;

  /**
   * Determines XTEE version.
   * 
   * @return HttpHeader subtype
   * @throws SoapFaultException Missing protocolVersion
   */
  public Class<? extends XteeHeader> getXteeHeaderClass() throws SoapFaultException {
    if (protocolVersion == null) {
      throw new SoapFaultException(FaultCode.CLIENT, "Missing protocolVersion");
    }
    switch (protocolVersion) {
      case "4.0":
        return Xtee6Header.class;
      default:
        throw new IllegalArgumentException("Not supported protocol version " + protocolVersion);
    }
  }
}
