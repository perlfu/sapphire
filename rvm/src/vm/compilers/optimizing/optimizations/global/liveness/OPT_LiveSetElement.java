/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/*
 * @author Michael Hind
 *
 * A simple class that holds an element in a LiveSet.
 */
final class OPT_LiveSetElement {

  /**
   * The register operand, i.e., the data
   */
  private OPT_RegisterOperand regOp;

  /**
   * The next field
   */
  private OPT_LiveSetElement next;

  /**
   * constructor
   * @param OPT_RegisterOperand register
   */
  OPT_LiveSetElement(OPT_RegisterOperand register) {
    regOp = register;
  }

  /**
   * Returns the register operand associated with this element
   * @return the register operand associated with this element
   */
  public final OPT_RegisterOperand getRegisterOperand() {
    return  regOp;
  }

  /**
   * Returns the register associated with this element
   * @return the register associated with this element
   */
  public final OPT_Register getRegister() {
    return  regOp.register;
  }

  /**
   * Returns the register type associated with this element
   * @return the register type associated with this element
   */
  public final VM_Type getRegisterType() {
    return  regOp.type;
  }

  /**
   * Returns the next element on this list
   * @return the next element on this list
   */
  public final OPT_LiveSetElement getNext() {
    return  next;
  }

  /**
   * Sets the next element field
   * @param newNext the next element field
   */
  public final void setNext(OPT_LiveSetElement newNext) {
    next = newNext;
  }

  /**
   * Returns a string version of this element
   * @return a string version of this element
   */
  public String toString() {
    StringBuffer buf = new StringBuffer("");
    buf.append(regOp);
    return  buf.toString();
  }
}



