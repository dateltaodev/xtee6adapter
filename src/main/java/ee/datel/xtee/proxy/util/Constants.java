package ee.datel.xtee.proxy.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public interface Constants {
  byte[] NULL = new byte[0];
  InputStream EMPTY = new ByteArrayInputStream(NULL);
  String SOAPPREFIX = "SOAP-ENV";

}
