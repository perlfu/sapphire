package org.mmtk.utility.options;

import org.vmutil.options.StringOption;

public class ConcurrentCopyMethod extends StringOption {
  public static final int CAS = 00;
  public static final int STM = 10;
  public static final int MSTM = 20;
  public static final int HTM = 30;
  public static final int HTM2 = 31;
  public static final int MHTM = 32;
  public static final int UNSAFE = 40;
  public static final int STMSEQ = 70;
  public static final int STMSEQ2 = 80;
  public static final int UNSAFE2 = 90;
  public static final int STMSEQ2P = 100;
  public static final int STMSEQ2N = 110;
  public static final int CAS2 = 120;
  
  public ConcurrentCopyMethod() {
    super(Options.set, "Concurrent Copy Method",
        "Concurrent Copy Method ([always-] htm/htm2/mhtm/stm/mstm/stmseq/stmseq2/stmseq2p/stmseq2n/cas/cas2s/unsafe/unsafe2)", "stmseq2p");
  }

  public int method() {
    if (value.equals("htm") || value.equals("always-htm")) return HTM;
    if (value.equals("htm2") || value.equals("always-htm2")) return HTM2;
    if (value.equals("mhtm") || value.equals("always-mhtm")) return MHTM;
    if (value.equals("stm") || value.equals("always-stm")) return STM;
    if (value.equals("mstm") || value.equals("always-mstm")) return MSTM;
    if (value.equals("cas") || value.equals("always-cas")) return CAS;
    if (value.equals("unsafe") || value.equals("always-unsafe")) return UNSAFE;
    if (value.equals("stmseq") || value.equals("always-stmseq")) return STMSEQ;
    if (value.equals("stmseq2") || value.equals("always-stmseq2")) return STMSEQ2;
    if (value.equals("unsafe2") || value.equals("always-unsafe2")) return UNSAFE2;
    if (value.equals("stmseq2p") || value.equals("always-stmseq2p")) return STMSEQ2P;
    if (value.equals("stmseq2n") || value.equals("always-stmseq2n")) return STMSEQ2N;
    if (value.equals("cas2") || value.equals("always-cas2")) return CAS2;
    return -1;
  }
  
  public boolean always() {
    return value.startsWith("always-");
  }
  
  @Override
  protected void validate() {
    failIf(method() < 0, "copy method should be one of htm/stm/mstm/cas, with optionally leading always-");
  }
}
