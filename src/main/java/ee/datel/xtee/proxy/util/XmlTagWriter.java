package ee.datel.xtee.proxy.util;

import java.io.IOException;
import java.io.Writer;
import java.util.Stack;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * XML writer.
 * <p>
 * <i>Not thread safe.</i>
 * </p>
 *
 * @author aldoa
 *
 */
public class XmlTagWriter implements AutoCloseable {

  private final Writer wrt;
  private final Stack<Tag> stack = new Stack<>();

  public XmlTagWriter(final Writer wrt) {
    this.wrt = wrt;
  }

  /**
   * Writes xml comment.
   * 
   * @param coment comment text
   * @throws IOException write error
   */
  public void writeComment(final String coment) throws IOException {
    wrt.write("\n<!-- ");
    wrt.write(StringEscapeUtils.escapeXml10(coment));
    wrt.write(" -->\n");
  }

  /**
   * Writes a XML tag.
   *
   * <pre>
   * &lt;prefix:name attribute-xmlns:attribute-name="attribute-value" ...&gt;value&lt;/prefix:name&gt;
   * </pre>
   *
   * @param prefix namespace prefix
   * @param name tag name
   * @param value tag value
   * @param attributes null-able
   * @throws IOException write error
   */
  public void writeValueTag(final String prefix, final String name, final String value,
      final TagAttribute... attributes) throws IOException {
    if (StringUtils.isEmpty(value)) {
      writeClosedTag(prefix, name, attributes);
    } else {
      writeTag(prefix, name, attributes);
      if (value.startsWith("<![CDATA[") && value.endsWith("]]>")) {
        wrt.write(value);
      } else {
        wrt.write(StringEscapeUtils.escapeXml10(value));
      }
      writeEndTag();
    }
  }

  private void writeClosedTag(final String prefix, final String name, final TagAttribute... attributes)
      throws IOException {
    writetheTag(prefix, name, attributes);
    wrt.write("/>");
  }

  private void writetheTag(final String prefix, final String name, final TagAttribute... attributes)
      throws IOException {
    wrt.write('<');
    if (prefix != null) {
      wrt.write(prefix);
      wrt.write(':');
    }
    wrt.write(name);
    if (attributes != null) {
      for (TagAttribute attr : attributes) {
        wrt.write(' ');
        if (attr.prefix != null) {
          wrt.write(attr.prefix);
          wrt.write(':');
        }
        wrt.write(attr.name);
        wrt.write("=\"");
        if (attr.value != null) {
          wrt.write(StringEscapeUtils.escapeXml10(attr.value));
        }
        wrt.write('"');
      }
    }
  }

  /**
   * Opens XML tag.
   *
   * <pre>
   * &lt;prefix:name attribute-xmlns:attribute-name="attribute-value" ...&gt;
   * </pre>
   *
   * @param prefix namespace prefix
   * @param name tag name
   * @param attributes null-able
   * @throws IOException write error
   */
  public void writeTag(final String prefix, final String name, final TagAttribute... attributes) throws IOException {
    stack.push(new Tag(prefix, name));
    writetheTag(prefix, name, attributes);
    wrt.write('>');
  }

  public void writeTag(final String name) throws IOException {
    writeTag(null, name);
  }

  /**
   * Closes last opened with {@link #writeTag(String, String, TagAttribute...)}XML tag.
   *
   * @throws IOException write error
   */
  public void writeEndTag() throws IOException {
    Tag tag = stack.pop();
    wrt.write("</");
    if (tag.prefix != null) {
      wrt.write(tag.prefix);
      wrt.write(':');
    }
    wrt.write(tag.name);
    wrt.write('>');
  }

  @Override
  public void close() throws Exception {
    if (wrt != null) {
      wrt.close();
    }
  }

  public static class TagAttribute {
    protected final String prefix;
    protected final String name;
    protected final String value;

    /**
     * Constructor.
     *
     * @param name tag name
     * @param value tag value
     */
    public TagAttribute(final String name, final String value) {
      this(null, name, value);
    }

    /**
     * Constructor.
     *
     * @param prefix namespace prefix
     * @param name tag name
     * @param value tag value
     */
    public TagAttribute(final String prefix, final String name, final String value) {
      this.prefix = prefix;
      this.name = name;
      this.value = value;
    }
  }

  private static class Tag {
    protected final String prefix;
    protected final String name;

    protected Tag(final String prefix, final String name) {
      this.prefix = prefix;
      this.name = name;
    }
  }

  public void write(final String string) throws IOException {
    wrt.write(string);
  }


}
