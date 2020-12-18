package ee.datel.xtee.proxy.exception;

import ee.datel.xtee.proxy.exception.SoapFault.FaultCode;
import ee.datel.xtee.proxy.util.Fault;

import java.io.IOException;
import java.util.Objects;

/**
 * SOAP Fault exception.
 *
 * @author aldoa
 *
 */
public class SoapFaultException extends IOException {
  private static final long serialVersionUID = 1L;
  private final FaultCode code;
  private final String message;
  private String actor;
  private String detail;

  /**
   * Minimal constructor.
   *
   * @param code fault origin, not <i>null</i>
   * @param message send message, not <i>null</i>
   */
  public SoapFaultException(final FaultCode code, final String message) {
    super(message);
    if (Objects.isNull(code)) {
      throw new IllegalArgumentException("Missing fault code");
    }
    if (Objects.isNull(message)) {
      throw new IllegalArgumentException("Missing message");
    }
    this.code = code;
    this.message = message;
  }


  /**
   * Constructor with actor.
   *
   * @param code fault origin, not <i>null</i>
   * @param message send message, not <i>null</i>
   * @param actor fault actor
   */
  public SoapFaultException(final FaultCode code, final String message, final String actor) {
    this(code, message == null ? "NullPointerException" : message);
    this.actor = actor;
  }

  /**
   * Constructor with actor and detail.
   *
   * @param code fault origin, not <i>null</i>
   * @param message send message, not <i>null</i>
   * @param actor fault actor
   * @param detail detailed message
   */
  public SoapFaultException(final FaultCode code, final String message, final String actor, final String detail) {
    this(code, message, actor);
    this.detail = detail;
  }

  /**
   * Constructor.
   */
  public SoapFaultException(final Fault fault) {
    super(fault.getFaultcode() + " " + fault.getFaultstring());
    code = fault.getFaultcode().indexOf("Cons") >= 0 || fault.getFaultcode().indexOf("Clie") >= 0 ? FaultCode.CLIENT
                : FaultCode.SERVER;
    message = fault.getFaultstring();
    actor = fault.getFaultactor();
    detail = fault.getFaulttDetail();
  }

  public String getCode() {
    return code.toString();
  }

  public String getString() {
    return message;
  }

  public String getActor() {
    return actor;
  }

  public String getDetail() {
    return detail;
  }

  @Override
  public String toString() {
    return getMessage() + (actor == null ? "" : "\n" + actor) + (detail == null ? "" : "\n" + detail);
  }
}
