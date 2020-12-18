package ee.datel.xtee.proxy.pojo;

import ee.datel.xtee.proxy.util.Constants;

import java.io.IOException;
import java.io.InputStream;

public interface XteeHeader {
  String SOAPENV_PRFX = Constants.SOAPPREFIX;
  String XTEE_PRFX = "xrd";
  String XIDENT_PRFX = "id";
  String SRV_PRFX = "tns";

  String getProtocolVersion();

  String getServiceMemberCode();

  String getClientMemberCode();

  String getServiceSubsystemCode();

  String getServiceCode();

  String getServiceVersion();

  void setServiceVersion(String version);

  String getUserId();

  String getRequestId();

  InputStream getInputStream() throws IOException;

  String getFullName();

  InputStream getResponseHeader(String xmlns) throws IOException;

  InputStream getEnvelopeEnd();

  String getServiceXroadInstance();

  String getServiceMemberClass();
}
