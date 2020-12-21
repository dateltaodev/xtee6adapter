package ee.datel.xtee.proxy.response;

import ee.datel.xtee.proxy.request.ProxyRequest;
import ee.datel.xtee.proxy.util.ResponseMessageInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Vector;

import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.LoggerFactory;

public class ServiceResponse implements AutoCloseable {
  private final Path filePath = Files.createTempFile("xresponse-", ".xml");
  private final InputStream response;
  private final String contentType;
  private final String contentEncoding;
  private final boolean multipart;

  private ResponseMessageInputStream source;

  ServiceResponse(final InputStream result, final String contentType, final String contentEncoding) throws IOException {
    try (BOMInputStream bom = new BOMInputStream(result)) {
      Files.copy(bom, filePath, StandardCopyOption.REPLACE_EXISTING);
    }
    response = new BufferedInputStream(Files.newInputStream(filePath));
    this.contentType = contentType;
    this.contentEncoding = contentEncoding;
    multipart = contentType != null && contentType.indexOf("boundary=") > 0;
    source =
        new ResponseMessageInputStream(response, multipart || contentType != null && contentType.indexOf("/xml") > 0);
  }

  /**
   * Constructs response stream.
   * 
   * @param adapter request target
   * @param client request source
   * @return response
   * @throws IOException read error
   */
  public InputStream getProxyResponseStream(final ServiceClient adapter, final ProxyRequest client) throws IOException {
    if (source.isFault()) {
      return source.getFaultInputStream();
    }
    InputStream body = adapter.getAdapterResponseBody(source, client.getXteeHeader().getServiceCode());
    Vector<InputStream> streams = new Vector<>();
    streams.add(source.getPreambleInputStream());
    streams.add(client.getResponseHeader());
    streams.add(body);
    streams.add(client.getEnvelopeEnd());
    if (adapter instanceof MtomClient) {
      streams.add(source.getAttachments());
    }
    return new SequenceInputStream(streams.elements());
  }

  public boolean isMultipart() {
    return multipart;
  }

  public String getContentType() {
    return contentType;
  }

  String getContentEncoding() {
    return contentEncoding;
  }

  // InputStream getResponseInputStream() {
  // return response;
  // }


  @Override
  public void close() throws Exception {
    try {
      if (response != null) {
        response.close();
      }
      Files.deleteIfExists(filePath);
    } catch (Exception ex) {
      LoggerFactory.getLogger(getClass()).error(ex.getMessage(), ex);
      filePath.toFile().deleteOnExit();
    }
  }
}
