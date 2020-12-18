package ee.datel.xtee.proxy;

import ee.datel.xtee.proxy.server.ServerConfiguration;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet(urlPatterns = {WsdlServlet.SERVLETPATH}, name = "WSDL Servlet")
public class WsdlServlet extends HttpServlet {
  public static final String SERVLETPATH = "/~getter";
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private static final long serialVersionUID = 1L;

  private final Pattern xmldecl = Pattern.compile("^<\\?xml.*\\?>");
  private final Pattern servicetns = Pattern.compile("#servicetns#");
  private final Pattern servicename = Pattern.compile("#servicename#");
  private final Pattern serviceversion = Pattern.compile("#serviceversion#");
  private final Pattern servicetitle = Pattern.compile("#servicetitle#");
  private final Pattern proxyserver = Pattern.compile("#proxy-server#");


  @Override
  public void doPost(final HttpServletRequest request, final HttpServletResponse response)
      throws IOException {
    // never
  }

  @Override
  public void doGet(final HttpServletRequest request, final HttpServletResponse response)
      throws IOException {
    ServerConfiguration conf =
        (ServerConfiguration) request.getAttribute(ServerConfiguration.class.getName());
    final String subSystem = request.getParameter("subsystemCode");
    if (subSystem != null && conf.getSubsystemServices(subSystem).length == 0) {
      responseSendError(response, HttpStatus.SC_NOT_FOUND,
          "Unregistered parameter 'subsystemCode' value");
      return;
    }
    final String serviceCode = request.getParameter("serviceCode");
    if (serviceCode == null) {
      responseSendError(response, HttpStatus.SC_BAD_REQUEST, "Parameter 'serviceCode' is missing");
      return;
    }
    final String currentVersion = conf.getProxyPropertie(ServerConfiguration.PRFX
        + (subSystem != null ? subSystem + "." : "") + serviceCode + ".version");
    if (currentVersion == null) {
      responseSendError(response, HttpStatus.SC_NOT_FOUND,
          "Unregistered parameter 'serviceCode' value");
      return;
    }
    String requestVersion = request.getParameter("version");
    if (requestVersion == null) {
      requestVersion = currentVersion;
    }
    InputStream template = null;
    try {
      template = conf.getXsdStream(serviceCode + "." + requestVersion + ".xsd", subSystem);
      if (template == null) {
        if (!requestVersion.equals(currentVersion)) {
          template = conf.getXsdStream(serviceCode + "." + currentVersion + ".xsd", subSystem);
        }
        if (template == null) {
          if (!requestVersion.equals(currentVersion)) {
            responseSendError(response, HttpStatus.SC_NOT_IMPLEMENTED,
                "Neither " + serviceCode + "." + requestVersion + ".xsd" + " nor " + serviceCode
                    + "." + currentVersion + ".xsd found");
          } else {
            responseSendError(response, HttpStatus.SC_NOT_IMPLEMENTED,
                serviceCode + "." + requestVersion + ".xsd not found");
          }
          return;
        }
        requestVersion = currentVersion;
      }
      try (InputStream ins =
          getClass().getClassLoader().getResourceAsStream("/templateWsdlBegin.txt")) {
        if (ins == null) {
          responseSendError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
              "Missing /templateWsdlBegin.txt");
          return;
        }
        try (InputStream ine =
            getClass().getClassLoader().getResourceAsStream("/templateWsdlEnd.txt");) {
          if (ine == null) {
            responseSendError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                "Missing /templateWsdlEnd.txt");
            return;
          }
          try (InputStreamReader srs = new InputStreamReader(ins, StandardCharsets.UTF_8);
              BufferedReader bg = new BufferedReader(srs);
              Stream<String> wsdlBeginLines = bg.lines();
              InputStreamReader sre = new InputStreamReader(ine, StandardCharsets.UTF_8);
              BufferedReader en = new BufferedReader(sre);
              Stream<String> wsdlEndLines = en.lines();
              BOMInputStream bom = new BOMInputStream(template);
              BufferedReader rd =
                  new BufferedReader(new InputStreamReader(bom, StandardCharsets.UTF_8));) {
            // ver1.2 response.setContentType("application/soap+xml");
            response.setContentType("text/xml");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            try (PrintWriter out = response.getWriter()) {
              final String serviceTns = conf.getProxyPropertie(ServerConfiguration.PRFX
                  + (subSystem != null ? subSystem + "." : "") + serviceCode + ".xmlns");
              // wsdl's begin part
              wsdlBeginLines.map(w -> servicetns.matcher(w).replaceAll(serviceTns))
                  .forEach(out::println);
              // wsdl's schema part
              rd.lines().map(s -> xmldecl.matcher(s).replaceFirst("")).forEach(out::println);
              // wsdl's end part
              final String serviceTitle = conf.getProxyPropertie(ServerConfiguration.PRFX
                  + (subSystem != null ? subSystem + "." : "") + serviceCode + ".title");
              final String serviceVersion = requestVersion;
              wsdlEndLines.map(w -> proxyserver
                  .matcher(servicetitle.matcher(
                      serviceversion.matcher(servicename.matcher(w).replaceAll(serviceCode))
                          .replaceAll(serviceVersion))
                      .replaceAll(serviceTitle))
                  .replaceAll(conf.getProxyUrl())).forEach(out::println);
            }
          }
        }
      }
    } catch (IOException ex) {
      logger.error(ex.getMessage(), ex);
      responseSendError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
    } finally {
      if (template != null) {
        try {
          template.close();
        } catch (IOException ex) {
          logger.warn(ex.getMessage());
        }
      }
    }
  }

  private void responseSendError(final HttpServletResponse response, final int code,
      final String string) {
    response.setContentType("text/plain");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setStatus(code);
    try (Writer wrt = response.getWriter()) {
      wrt.write(string);
    } catch (IOException e) {
      // nothing to do
    }
  }

  @Override
  public void init() throws ServletException {
    logger.info("Initialized");
  }

}
