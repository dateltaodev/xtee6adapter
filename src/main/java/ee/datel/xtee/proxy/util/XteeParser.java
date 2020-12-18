package ee.datel.xtee.proxy.util;

import ee.datel.xtee.proxy.pojo.XteeHeader;
import ee.datel.xtee.proxy.pojo.XteeVersion;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class XteeParser {
  private static final XteeParser ITEM = new XteeParser();

  public static XteeParser getXteeParser() {
    return ITEM;
  }

  private final ObjectMapper mapper;

  private XteeParser() {
    mapper = new XmlMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false);
  }

  /**
   * Parses XTEE version.
   *
   * @param xml XTEE header byte[]
   * @throws IOException parse error
   */
  public XteeVersion parseVersion(final byte[] xml) throws IOException {
    XteeVersion result = parse(xml, XteeVersion.class);
    return result;
  }

  /**
   * Parser proxy request's header.
   *
   * @param xml XTEE header byte[]
   * @throws IOException parse error
   */
  public XteeHeader parseHeader(final byte[] xml, final Class<? extends XteeHeader> type) throws IOException {
    XteeHeader result = parse(xml, type);
    return result;
  }

  public <T> T parse(final InputStream in, final Class<T> type) throws JsonParseException, JsonMappingException, IOException {
    return mapper.readValue(in, type);
  }

  private <T> T parse(final byte[] xml, final Class<T> type) throws JsonParseException, JsonMappingException, IOException {
    return mapper.readValue(new ByteArrayInputStream(xml), type);
  }
}
