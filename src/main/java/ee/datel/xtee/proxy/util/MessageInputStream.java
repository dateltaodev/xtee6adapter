package ee.datel.xtee.proxy.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.input.BOMInputStream;

/**
 * Proxy input and adapter output <code>text/xml</code> or <code>Multipart/Related</code> message
 * parser.
 * <p>
 * <i>Not thread safe.</i>
 * </p>
 *
 * @author aldoa
 *
 */
public class MessageInputStream implements AutoCloseable {
  private static final int BUFFERLENGTH = 8 * 1024;
  protected static final byte[] HEADER = new byte[] {'H', 'e', 'a', 'd', 'e', 'r'};

  protected static final byte[] REQUEST = new byte[] {'r', 'e', 'q', 'u', 'e', 's', 't'};

  protected final byte[] buffer = new byte[BUFFERLENGTH];
  protected final PushbackInputStream source;
  protected boolean skipRequest;
  protected boolean end;
  protected int level;
  protected boolean partend;

  private byte[] preamble;
  protected byte[] xteeHeader;
  private InputStream attachments;

  /**
   * Constructor.
   *
   * @param source input stream
   * @param isXml <code>Content-Type is "text/xml"</code>
   * @throws IOException read error
   */
  public MessageInputStream(final InputStream source, final boolean isXml) throws IOException {
    this.source = new PushbackInputStream(isXml ? new BOMInputStream(source) : source, BUFFERLENGTH);
    getPreamble();
    getXteeHeader();
  }


  /**
   * Gets message preamble.
   *
   * @return preamble
   * @throws IOException on buffer read
   */
  public byte[] getPreamble() throws IOException {
    if (preamble == null) {
      ByteArrayOutputStream collect = null;
      int len;
      while ((len = readTag(buffer)) != -1) {
        if (len > 0) {
          if (buffer[0] == '<') {
            if (buffer[1] != '?') {
              source.unread(buffer, 0, len);
              break;
            }
          }
          if (collect == null) {
            collect = new ByteArrayOutputStream();
          }
          collect.write(buffer, 0, len);
          if (buffer[0] == '<') {
            break;
          }
        }
      }
      preamble = collect == null ? Constants.NULL : collect.toByteArray();
      if (preamble.length > 0 && preamble[0] == '<') {
        attachments = Constants.EMPTY;
      }
      level = 0;
    }
    return preamble;
  }

  /**
   * Gets message preamble as input stream. Repeatedly gettable.
   *
   * @return of {@link ByteArrayInputStream}
   * @throws IOException on preamble read
   */
  public InputStream getPreambleInputStream() throws IOException {
    return getPreamble().length == 0 ? Constants.EMPTY : new ByteArrayInputStream(preamble);
  }

  /**
   * Reads request XTEE header.
   * 
   * @return XTEE SOAP header
   * @throws IOException request read error
   */
  public byte[] getXteeHeader() throws IOException {
    if (xteeHeader == null) {
      swingToTag(HEADER, 2);
      ByteArrayOutputStream collect = new ByteArrayOutputStream();
      int stop = level + 1;
      partend = false;
      int len;
      while ((len = readTillLevelEnd(stop)) != -1) {
        if (len > 4 && buffer[0] == '<' && buffer[1] != '!') {
          len = removePrefixes(len);
        }
        collect.write(buffer, 0, len);
      }
      xteeHeader = collect.toByteArray();
    }
    return xteeHeader;
  }

  /**
   * Swings to XTEE request body.
   * 
   * @param tagName tag name
   * @param onLevel depth in xml structure
   * @return in request SOAP body
   * @throws IOException request read error
   */
  public InputStream getRequestBody(final String tagName, final int onLevel) throws IOException {
    swingToTag(tagName.getBytes(StandardCharsets.UTF_8), onLevel);
    readNext(); // skip begin tag
    if (end) {
      return Constants.EMPTY;
    }
    int current = level;
    if (skipRequest) {
      trimToLt();
      int len = readNext();
      if (isTag(REQUEST, len)) {
        partend = false;
        len = onLevel + 1;
        while (readTillLevelEnd(len) != -1) {
        }
      } else {
        source.unread(buffer, 0, len);
        level = current;
      }
    }
    return end ? Constants.EMPTY : new InputStream() {
      int len;
      int pos;
      boolean ended;

      @Override
      public int read() throws IOException {
        if (ended) {
          return -1;
        }
        if (pos == len) {
          pos = 0;
          len = readNext();
          if (len == -1 || level < onLevel) {
            partend = false;
            while (readTillLevelEnd(1) != -1) { // swing to envelope end
            }
            ended = true;
            return -1;
          }
        }
        return buffer[pos++];
      }
    };
  }

  /**
   * Swing to tag.
   * 
   * @param tagName tag name
   * @param onLevel depth in xml structure
   * @return in request SOAP body
   * @throws IOException request read error
   */
  public InputStream getRequestTag(final String tagName, final int onLevel) throws IOException {
    swingToTag(tagName.getBytes(StandardCharsets.UTF_8), onLevel);
    return end ? Constants.EMPTY : new InputStream() {
      int len;
      int pos;
      boolean ended;

      @Override
      public int read() throws IOException {
        if (ended) {
          return -1;
        }
        if (pos == len) {
          pos = 0;
          len = readNext();
          if (len == -1 || level < onLevel - 1) {
            partend = false;
            while (readTillLevelEnd(1) != -1) { // swing to envelope end
            }
            ended = true;
            return -1;
          }
        }
        return buffer[pos++];
      }
    };
  }

  public InputStream getAttachments() {
    return end ? Constants.EMPTY : attachments != null ? attachments : source;
  }

  private int removePrefixes(int len) {
    boolean apos = false;
    for (int destPos = 1, i = 1; i < len; i++) {
      if (buffer[i] == '"' || buffer[i] == '\'') {
        apos = !apos;
      } else if (!apos) {
        if (buffer[i] == ' ' || buffer[i] == '/') {
          destPos = i + 1;
        } else if (buffer[i] == ':') {
          System.arraycopy(buffer, i + 1, buffer, destPos--, len - i);
          len = len - i + destPos;
          i = destPos + 1;
        }
      }
    }
    return len;
  }

  protected int readTillLevelEnd(final int onLevel) throws IOException {
    if (partend) {
      return -1;
    }
    int len = readTag(buffer);
    if (level < onLevel) {
      partend = true;
    }
    return len;
  }

  protected void swingToTag(final byte[] tag, final int onLevel) throws IOException {
    int len;
    trimToLt();
    while ((len = readNext()) != -1) {
      if (level == (buffer[len - 2] == '/' ? onLevel - 1 : onLevel) && isTag(tag, len)) {
        level--;
        source.unread(buffer, 0, len);
        return;
      }
      trimToLt();
    }
  }

  protected boolean isTag(final byte[] tag, final int len) {
    for (int i = 1, n = 0; i < len; i++) {
      if (buffer[i] == ' ' || buffer[i] == '>' || buffer[i] == '/') {
        return n == tag.length;
      } else if (n == tag.length) {
        return false;
      } else if (buffer[i] == tag[n]) {
        n++;
      } else {
        n = 0;
      }
    }
    return false;
  }

  private int readTag(final byte[] buffer) throws IOException {
    if (end) {
      return -1;
    }
    boolean tag = false;
    boolean cdata = false;
    int cdataChr = 0;
    int len = 0;
    READER: while (len < buffer.length) {
      int ch = source.read();
      boolean exit = false;
      switch (ch) {
        case -1:
          end = true;
          break READER;
        case '!':
          if (tag && len == 1) {
            tag = false;
            cdata = true;
          }
          break;
        case '-':
        case '[':
          if (cdata && len == 2) {
            cdataChr = ch == '[' ? ']' : ch;
          }
          break;
        case '\n':
          exit = !tag && !cdata;
          break;
        case '>':
          if (cdata) {
            if (len > 7) {
              if (buffer[len - 1] == cdataChr && buffer[len - 2] == cdataChr) {
                exit = true;
              }
            }
          } else if (tag) {
            exit = true;
            if (len > 1) {
              if (buffer[1] == '?' || buffer[1] == '!') {
                // preamble or comment
              } else if (buffer[len - 1] == '/') {
                // a empty tag <a />
              } else if (buffer[1] == '/') {
                // a end tag </a>
                level--;
              } else { // a tag <a>
                level++;
              }
            }
          }
          break;
        case '<':
          if (!cdata) {
            if (len > 0) {
              source.unread(ch);
              break READER;
            }
            tag = true;
          }
          break;
        default:
          break;
      }
      if (tag && (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n')) {
        if (buffer[len - 1] != ' ') {
          buffer[len++] = ' ';
        }
      } else {
        buffer[len++] = (byte) ch;
      }
      if (exit) {
        break READER;
      }
    }
    return len;
  }

  protected void trimToLt() throws IOException {
    int ch;
    while ((ch = source.read()) != -1) {
      if (ch == '<') {
        source.unread(ch);
        return;
      }
    }
    end = true;
  }

  protected void trimToGt() throws IOException {
    int ch;
    while ((ch = source.read()) != -1) {
      if (ch == '>') {
        return;
      }
    }
    end = true;
  }

  protected int readNext() throws IOException {
    int len;
    while ((len = readTag(buffer)) == 0) { // read till got
    }
    return len;
  }

  @Override
  public void close() throws Exception {
    if (source != null) {
      try {
        source.close();
      } catch (IOException e) {
        // ignore
      }
    }
  }


}
