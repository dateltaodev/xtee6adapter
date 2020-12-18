package ee.datel.xtee.proxy.request;

import org.apache.http.HttpMessage;

class MultipartRequestParser extends ProxyRequest {

  @Override
  public void addAdapterHeaders(final HttpMessage post) {
    post.addHeader("MIME-Version", "1.0");
  }


}
