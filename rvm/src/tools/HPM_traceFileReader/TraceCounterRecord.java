/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id:&

/**
 * Represent a trace record of HPM counter values: 
 *    PID(int), TID(int), wall_time(long), end_wall_time(long), n_counters(int), counters(long)*
 *    where the number of counters (n_counters) is machine dependent.
 * Counter[0] is wall time (end_wall_time) taken when thread is swapped out.
 *
 * @author Peter F. Sweeney
 * @date 2/12/2003
 */

public class TraceCounterRecord extends TraceRecord
{
  /*
   * record's fields
   */
  // which buffer was this record written from?  (values 1 or 0)
  public int  buffer_code   = -1;
  // thread switch occurred? (1 yes, 0 no)
  public boolean  thread_switch = false;
  // virtual processor id
  public int vpid = -1;
  // local thread id (assume always positive)
  public int  local_tid = -99999;
  // global thread id (negative if yielded)
  public int  tid = -99999;
  // real time when thread was scheduled
  public long start_wall_time   = 0;
  // callee method id
  public int callee_MID = 0;
  // caller method id
  public int caller_MID = 0;
  // number of number of HPM counters 
  public int n_counters;
  // HPM counter values (0th counter is end wall time)
  public long []counters;

  /*
   * Constants
   */
  // callee method id
  static public final int CALLEE_MID = 512;
  // caller method id
  static public final int CALLER_MID = 1024;
  /**
   * constructor
   */
  TraceCounterRecord(int n_counters) {
    this.n_counters = n_counters;
    counters = new long[n_counters+1];
    for (int i=0; i<=n_counters; i++) {
      counters[i]=0;
    }
  }
  
  /*
   * TraceRecord copy from another trace record
   */
  TraceCounterRecord(TraceCounterRecord tr) {
    vpid             = tr.vpid;
    tid              = tr.tid;
    local_tid        = tr.local_tid;
    start_wall_time  = tr.start_wall_time;
    n_counters = tr.n_counters;
    counters = new long[n_counters+1];
    for (int i=0; i<=n_counters; i++) {
      counters[i]=tr.counters[i];
    }
  }
  
  /*
   * Initial wall time needed to compute relative wall time
   * Set externally.
   */
  static public long start_time = -1;

  /*
   * print trace record on one line.
   * Interface method
   *
   * @return true if at least one counter is non zero
   */
  public boolean print() 
  {
    // System.out.println("TraceRecord.print() # of counters "+info.numberOfCounters);
    boolean notZero = false;
    if (thread_switch) System.out.print(" ");
    else               System.out.print("*");
    System.out.print("VP "+vpid+" TID ");
    if (         tid  > -1) System.out.print(" ");
    if (Math.abs(tid) < 10) System.out.print(" ");
    System.out.print(+tid);
    if(TraceFileReader.options.debug>=5) {
      System.out.print(" tid ");
      if (Math.abs(local_tid) < 10) System.out.print(" ");
      System.out.print(local_tid);
    }
    //    System.out.print(" RT "+start_wall_time);
    // alignment
    //    if (Math.abs(tid) < 10) System.out.print(" ");
    long start_thread_time = 0;
    start_thread_time = start_wall_time - start_time;
    if (start_thread_time == 0) {
      start_thread_time = start_time;
    }
    
    //    System.out.print(" ST "+start_thread_time+" ET "+counters[0]);
    System.out.print(" ST "+start_thread_time+" RT "+counters[0]);
    if (TraceFileReader.options.event_mask == CommandLineOptions.UNINITIALIZED) {
      for(int i=1; i<=n_counters; i++) {
        System.out.print(" "+i+": "+counters[i]);
      }
    } else {
      for(int i=1; i<=n_counters; i++) {
        int mask = TraceFileReader.options.event_mask_array[i];
        if ((mask & TraceFileReader.options.event_mask) == mask) {
          if (counters[i] > 0) notZero = true;
          System.out.print(" "+i+": "+counters[i]);
        }
      }
    }
    if ((TraceFileReader.options.event_mask & CALLER_MID) == CALLER_MID) {
      if (TraceFileReader.options.print_fullname) {
        System.out.print(" "+caller_MID+" "+TraceHeader.getFullMIDName(caller_MID));
      } else {
        System.out.print(" "+caller_MID);
      }
    } 
    if ((TraceFileReader.options.event_mask & CALLEE_MID) == CALLEE_MID) {
      if (TraceFileReader.options.print_fullname) {
        System.out.print(" "+callee_MID+" "+TraceHeader.getFullMIDName(callee_MID));
      } else {
        System.out.print(" "+callee_MID);
      }
    } 
    if (TraceFileReader.options.group_index == CommandLineOptions.P4_LSU_BUSY) {
      double value = counters[5] / (double)counters[6];
      System.out.print(" \test. load latency "+Utilities.twoDigitDouble(value));
      
    }
    System.out.println();
    return notZero;
  }

  /*
   * Print trace record
   * Each counter is identified by its name.
   *
   * @param trace_header
   * @return true if at least one counter is non zero
   */
  private boolean printLong(TraceHeader trace_header) {
    // System.out.println("TraceRecord.print() # of counters "+info.numberOfCounters);
    boolean notZero = false;
    for (int i=0; i<=n_counters; i++) {
      int mask = TraceFileReader.options.event_mask_array[i];
      if ((mask & TraceFileReader.options.event_mask) == mask) {
        if (counters[i] > 0) {
          notZero = true;
          System.out.println(i+":"+trace_header.short_event_name(i)+": "+Utilities.format_long(counters[i]));
        }
      }
    }
    return notZero;
  }
  /*
   * Print trace record.  
   * Make performance calculations depending on the group index.
   * Currently only computations are made for Power4.
   *
   * @param trace_header
   */
  public boolean print(TraceHeader trace_header) 
  {
    if (trace_header.isPower4()) {
      int group_index = trace_header.ids[1];
      printP4PerformanceComputation(group_index);
      System.out.println();
    }
    return printLong(trace_header);
  }
  /**
   * Print the performance computations.
   * @param group_index
   */
  private void printP4PerformanceComputation(int group_index) 
  {
    //    System.out.println("TraceCounterRecord.printP4Performance("+group_index+")");
    double value = 0; double value1 = 0;
    if (group_index == CommandLineOptions.P4_SLICE0) {                  //  group  0
      // instructions per group = instructions / group_completed
      value  =  counters[4] / (double)counters[7];
      value1 = (counters[7] / (double)counters[4])*100.0;
      System.out.print(  "inst  GRP_CMPL "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // group completed         = cycles / group completed
      value  =  counters[2] / (double)counters[7];
      value1 = (counters[7] / (double)counters[2])*100.0;
      System.out.print(  "  cyc  GRP_CMPL_CYC "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // group dispatch rejected = cycles / group dispatch rejected
      value  =  counters[2] / (double)counters[8];
      value1 = (counters[8] / (double)counters[2])*100.0;
      System.out.print(  "  GRP_DISP_REJECT "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[2] / (double)counters[5];
      value1 = (counters[5] / (double)counters[2])*100.0;
      System.out.print(  "  1PLUS_PPC_CMPL "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_BASIC) {            //  group  2
      // speculative instructions = dispatched / completed
      value   =  (counters[6] / (double)counters[5])*100.0;
      value1  =  (counters[5] / (double)counters[6]);
      System.out.print(  "inst  INST_DISP "+Utilities.threeDigitDouble(value));
      System.out.print(  "%("+Utilities.threeDigitDouble(value1)+")");
      // L1 dcache misses = instructions / L1 dcache misses
      value   =   counters[6] / (double)counters[3];
      value1  =  (counters[3] / (double)counters[6])*100.0;
      System.out.print(  "  LD_MISS_L1 "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // L1 load references = instructions / L1 dcache loads
      value   =   counters[6] / (double)counters[8];
      value1  =  (counters[8] / (double)counters[6])*100.0;
      System.out.print(  "  LD_REF_L1 "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // L1 store references = instructions / L1 dcache stores
      value   =   counters[6] / (double)counters[7];
      value1  =  (counters[7] / (double)counters[6])*100.0;
      System.out.print(  "  ST_REF_L1 "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // L1 dcache misses = L1 dcache loads / L1 dcache misses
      value   =   counters[8] / (double)counters[3];
      value1  =  (counters[3] / (double)counters[8])*100.0;
      System.out.print(  "  LD_REF_L1_per_miss "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // L1 dcache misses = L1 dcache loads / L1 dcache misses
      value   =   counters[8] / (double)counters[4];
      value1  =  (counters[4] / (double)counters[8])*100.0;
      System.out.print(  "  DC_INV_L2 "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_IFU) {              //  group  3
      // instructions per branch = instructions completed / branches issued
      value  = (counters[1] / (double)counters[3]);
      value1 = (counters[3] / (double)counters[1])*100.0;
      System.out.print(  "inst   BR_ISSUED "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // branch mispredict rate (direction) = branches issued / branch mispredict CR value 
      value  = (counters[3] / (double)counters[4]);
      value1 = (counters[4] / (double)counters[3])*100.0;
      System.out.print(  "  BR_MPRED_CR "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // branch mispredict rate (count/link) = branches issued / branch mispredict Target address
      value   = (counters[3] / (double)counters[7]);
      value1 = (counters[7] / (double)counters[3])*100.0;
      System.out.print(  "  BR_MPRED_TA "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // Cycles L1 ICACHE writes = CYC / L1_WRITE_CYC
      value   = (counters[6] / (double)counters[8]);
      value1  = (counters[8] / (double)counters[6])*100.0;
      System.out.print(  "  cyc  L1_WRITE "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // Cycles BIQ or IDU full = CYC / BIQ_IDU_FULL_CYC
      value   = (counters[6] / (double)counters[2]);
      value1  = (counters[2] / (double)counters[6])*100.0;
      System.out.print(  "  BIQ_IDU_FULL "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // Cycles instruction fetched = CYC / INST_FETCH_CYC
      value   = (counters[6] / (double)counters[5]);
      value1  = (counters[5] / (double)counters[6])*100.0;
      System.out.print(  "  INST_FETCH "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // synthesize instructions per block: INST_CMPL / (BR_ISSUED - (BR_MPRED_CR + BR_MPRED_TA))
      double sum = (double)(counters[3] + counters[4] + counters[7]);
      value   = counters[1] / sum;
      value1  = sum         / (double)counters[1]*100.0;
      System.out.print(  "  INST_per_BB "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_ISU) {              //  group  4
      // FPR mapper full = cycles / fpr map full
      value   =   counters[7] / (double)counters[1];
      value1  =  (counters[1] / (double)counters[7])*100.0;
      System.out.print(  "cyc   FPR_MAP_FULL "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // BRQ full = cycles / brq full
      value   =   counters[7] / (double)counters[2];
      value1  =  (counters[2] / (double)counters[7])*100.0;
      System.out.print(  "  BRQ_FULL "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // GPR mapper full = cycles / GPR map full
      value   =   counters[7] / (double)counters[3];
      value1  =  (counters[3] / (double)counters[7])*100.0;
      System.out.print(  "  GPR_MAP_FULL "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // FPU full = cycles / FPU full
      value   =   counters[7] / (double)counters[5];
      value1  =  (counters[5] / (double)counters[7])*100.0;
      System.out.print(  "  FPU_FULL "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // GCT full = cycles / GCT full
      value   =   counters[7] / (double)counters[6];
      value1  =  (counters[6] / (double)counters[7])*100.0;
      System.out.print(  "  GCT_FULL "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // FXLS queue full = cycles / FXLS queue full
      value   =   counters[7] / (double)counters[8];
      value1  =  (counters[8] / (double)counters[7])*100.0;
      System.out.print(  "  FXLS_FULL "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_LSOURCE) {          //  group  5
      // computed cycles
      double cycles = counters[1] + counters[2] + counters[3] + counters[4] + 
        counters[5] + counters[6] + counters[7] + counters[8];
      System.out.print(  "  INST "+Utilities.threeDigitDouble(cycles));
      // DATA FROM mem
      value  =  cycles      / (double)counters[2];
      value1 = (counters[2] / (double)cycles)*100;
      System.out.print(  "  DATA_FROM_MEM "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // DATA FROM L3
      value  =  cycles      / (double)counters[1];
      value1 = (counters[1] / (double)cycles)*100;
      System.out.print(  "  DATA_FROM_L3 "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // DATA FROM L35
      value  =  cycles      / (double)counters[3];
      value1 = (counters[3] / (double)cycles)*100;
      System.out.print(  "  DATA_FROM_L35 "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // DATA FROM L2
      value  =  cycles      / counters[4];
      value1 = (counters[4] / (double)cycles)*100;
      System.out.print(  "  DATA_FROM_L2 "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // DATA FROM L25_SHR
      if (counters[5] != 0) {
        value  =  cycles      / (double)counters[5];
        value1 = (counters[5] / (double)cycles)*100;
        System.out.print(  "  DATA_FROM_L25_SHR "+Utilities.threeDigitDouble(value));
        System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      } else {
        System.out.print(  "  DATA_FROM_L25_SHR 0(0%)");
      }
      // DATA FROM L25_MOD
      if (counters[8] != 0) {
        value  =  cycles      / (double)counters[8];
        value1 = (counters[8] / (double)cycles)*100;
        System.out.print(  "  DATA_FROM_L25_MOD "+Utilities.threeDigitDouble(value));
        System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      } else {
        System.out.print(  "  DATA_FROM_L25_MOD 0(0%)");
      }
      // DATA FROM L275_SHR
      if (counters[6] != 0) {
        value  =  cycles      / (double)counters[6];
        value1 = (counters[6] / (double)cycles)*100;
        System.out.print(  "  DATA_FROM_L275_SHR "+Utilities.threeDigitDouble(value));
        System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      } else {
        System.out.print(  "  DATA_FROM_L275_SHR 0(0%)");
      }
      // DATA FROM L275_MOD
      if (counters[7] != 0) {
        value  =  cycles      / (double)counters[7];
        value1 = (counters[7] / (double)cycles)*100;
        System.out.print(  "  DATA_FROM_L275_MOD "+Utilities.threeDigitDouble(value));
        System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      } else {
        System.out.print(  "  DATA_FROM_L275_MOD 0(0%)");
      }

    } else if (group_index == CommandLineOptions.P4_ISOURCE) {          //  group  6
      // computed cycles
      double cycles = counters[1] + counters[2] + counters[3] + counters[4] + 
        counters[5] + counters[6] + counters[7] + counters[8];
      System.out.print(  "   INST "+Utilities.threeDigitDouble(cycles));
      // instructions from mem
      value  =  cycles      / (double)counters[1];
      value1 = (counters[1] / (double)cycles)*100;
      System.out.print(  "  INST_FROM_MEM "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // instructions from L3
      value  =  cycles      / (double)counters[5];
      value1 = (counters[5] / (double)cycles)*100;
      System.out.print(  "  INST_FROM_L3 "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // instructions from L35
      if (counters[4] != 0) {
        value  =  cycles      / (double)counters[4];
        value1 = (counters[4] / (double)cycles)*100;
        System.out.print(  "  INST_FROM_L35 "+Utilities.threeDigitDouble(value));
        System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      } else {
        System.out.print(  "  INST_FROM_L35 0(0%)");
      }
      // instructions from L2
      value  =  cycles      / counters[3];
      value1 = (counters[3] / (double)cycles)*100;
      System.out.print(  "  INST_FROM_L2 "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // instructions from L25_SHR
      if (counters[2] != 0) {
        value  =  cycles      / counters[2];
        value1 = (counters[2] / (double)cycles)*100;
        System.out.print(  "  INST_FROM_L25_L275 "+Utilities.threeDigitDouble(value));
        System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      } else {
        System.out.print(  "  INST_FROM_L25_L275 0(0%)");
      }
      // instructions from L1
      value  =  cycles      / counters[6];
      value1 = (counters[6] / (double)cycles)*100;
      System.out.print(  "  INST_FROM_L1 "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // instructions from prefetch buffer
      value  =  cycles      / counters[7];
      value1 = (counters[7] / (double)cycles)*100;
      System.out.print(  "  INST_FROM_PREF "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // no instructions feteched
      value  =  cycles      / counters[8];
      value1 = (counters[8] / (double)cycles)*100;
      System.out.print(  "  0INST_FETCH "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_LSU) {              //  group  7
      // 
      value  =  counters[3] / (double)counters[1];
      value1 = (counters[1] / (double)counters[3])*100;
      System.out.print("cyc  LSU_FLUSH_ULD "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[3] / (double)counters[2];
      value1 = (counters[2] / (double)counters[3])*100;
      System.out.print("  LSU_FLUSH_UST "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[3] / (double)counters[5];
      value1 = (counters[5] / (double)counters[3])*100;
      System.out.print("  LSU_FLUSH_SRQ "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[3] / (double)counters[6];
      value1 = (counters[6] / (double)counters[3])*100;
      System.out.print("  LSU_FLUSH_LRQ "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[3] / (double)counters[7];
      value1 = (counters[7] / (double)counters[3])*100;
      System.out.print("  ST_REF_L1 "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[3] / (double)counters[8];
      value1 = (counters[8] / (double)counters[3])*100;
      System.out.print("  LD_REF_L1 "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_XLATE1) {           //  group  8
      // average tablewalk = tablewalk duration / (ITLB miss + DTLB MISS) (group 34)
      value  =  counters[3] / (double)(counters[1] + counters[2]);
      value1 = (counters[3] / (double)counters[8])*100;
      System.out.print("cyc   DATA_TABLEWALK "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)TC");
      // DERAT Miss Rate = instructions / DERAT miss 
      value  =  counters[7] / (double)counters[6];
      value1 = (counters[6] / (double)counters[7])*100;
      System.out.print("  inst   DERAT_MISS "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // IERAT Miss Rate = instructions / translation written to  IERAT
      value  =  counters[7] / (double)counters[5];
      value1 = (counters[5] / (double)counters[7])*100;
      System.out.print("  IERAT_MISS "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // DTLB miss rate = instructions / DTLB_miss
      value  =  counters[7] / (double)counters[2];
      value1 = (counters[2] / (double)counters[7])*100;
      System.out.print("  DTLB_MISS "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // ITLB miss rate = instructions / ITLB_miss
      value  =  counters[7] / (double)counters[1];
      value1 = (counters[1] / (double)counters[7])*100;
      System.out.print("  ITLB_MISS "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_XLATE2) {           //  group  9
      // DERAT Miss Rate = instructions / DERAT miss 
      value  =  counters[7] / (double)counters[6];
      value1 = (counters[6] / (double)counters[7])*100;
      System.out.print("inst  DERAT_MISS "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // IERAT Miss Rate = instructions / IERAT miss 
      value  =  counters[7] / (double)counters[5];
      value1 = (counters[5] / (double)counters[7])*100;
      System.out.print("  IERAT_XLATE_WR "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // DSLB miss rate = instructions / DSLB_miss
      value  =  counters[7] / (double)counters[2];
      value1 = (counters[2] / (double)counters[7])*100;
      System.out.print("  DSLB_MISS "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // ISLB miss rate = instructions / ISLB_miss
      value  =  counters[7] / (double)counters[1];
      value1 = (counters[1] / (double)counters[7])*100;
      System.out.print("  ISLB_MISS "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[8] / (double)counters[3];
      value1 = (counters[3] / (double)counters[8])*100;
      System.out.print("  cyc  LSU_SRQ_SNC_CYC "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[8] / (double)counters[4];
      value1 = (counters[4] / (double)counters[8])*100;
      System.out.print("  LSU_LMQ_S0_ALLOC "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_FPU1) {             //  group 14
      // FPU fdiv = instructions / FPU fdiv
      value  =  counters[7] / (double)counters[1];
      value1 = (counters[1] / (double)counters[7])*100;
      System.out.print("inst FPU_FDIV "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // FPU fma = instructions / FPU fdiv
      value  =  counters[7] / (double)counters[2];
      value1 = (counters[2] / (double)counters[7])*100;
      System.out.print("  FPU_FMA "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // FPU fest = instructions / FPU fdiv
      value  =  counters[7] / (double)counters[3];
      value1 = (counters[3] / (double)counters[7])*100;
      System.out.print("  FPU_FEST "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // FPU fin = instructions / FPU fin
      value  =  counters[7] / (double)counters[4];
      value1 = (counters[4] / (double)counters[7])*100;
      System.out.print("  FPU_FIN "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // FPU sqrt = instructions / FPU sqrt
      value  =  counters[7] / (double)counters[6];
      value1 = (counters[6] / (double)counters[7])*100;
      System.out.print("  FPU_FSQRT "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // FPU fmove fest = instructions / FPU fmove fest
      value  =  counters[7] / (double)counters[8];
      value1 = (counters[8] / (double)counters[7])*100;
      System.out.print("  FPU_FMOVE_FEST "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_FPU2) {             //  group 15
      // 
      value  =  counters[3] / (double)counters[1];
      value1 = (counters[1] / (double)counters[3])*100;
      System.out.print("cyc  FPU_DENORM "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[3] / (double)counters[2];
      value1 = (counters[2] / (double)counters[3])*100;
      System.out.print("  FPU_STALL3 "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[3] / (double)counters[5];
      value1 = (counters[5] / (double)counters[3])*100;
      System.out.print("  FPU_ALL "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[3] / (double)counters[6];
      value1 = (counters[6] / (double)counters[3])*100;
      System.out.print("  FPU_STF "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[3] / (double)counters[7];
      value1 = (counters[7] / (double)counters[3])*100;
      System.out.print("  FPU_FRSP_CONV "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[3] / (double)counters[8];
      value1 = (counters[8] / (double)counters[3])*100;
      System.out.print("  LSU_LDF "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_IDU1) {             //  group 16
      // 
      value  =  counters[6] / (double)counters[3];
      value1 = (counters[3] / (double)counters[6])*100.0;
      System.out.print("cyc  1INST_CLB "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // 
      value  =  counters[6] / (double)counters[4];
      value1 = (counters[4] / (double)counters[6])*100.0;
      System.out.print("  2INST_CLB "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // 
      value  =  counters[6] / (double)counters[7];
      value1 = (counters[7] / (double)counters[6])*100.0;
      System.out.print("  3INST_CLB "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // 
      value  =  counters[6] / (double)counters[8];
      value1 = (counters[8] / (double)counters[6])*100.0;
      System.out.print("  4INST_CLB "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // 
      value  =  counters[6] / (double)counters[5];
      value1 = (counters[5] / (double)counters[6])*100.0;
      System.out.print("  1PLUS_PPC_CMPL "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_IDU2) {             //  group 17
      // 
      value  =  counters[6] / (double)counters[3];
      value1 = (counters[3] / (double)counters[6])*100.0;
      System.out.print("cyc  5INST_CLB "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // 
      value  =  counters[6] / (double)counters[4];
      value1 = (counters[4] / (double)counters[6])*100.0;
      System.out.print("  6INST_CLB "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // 
      value  =  counters[6] / (double)counters[7];
      value1 = (counters[7] / (double)counters[6])*100.0;
      System.out.print("  7INST_CLB "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // 
      value  =  counters[6] / (double)counters[8];
      value1 = (counters[8] / (double)counters[6])*100.0;
      System.out.print("  8INST_CLB "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // 
      value  =  counters[6] / (double)counters[5];
      value1 = (counters[5] / (double)counters[6])*100.0;
      System.out.print("  GRP_DISP_SUCCESS "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_ISU_RENAME) {       //  group 18
      // SPECULATIVE INSTRUCTIONS = instructions dispatched / instructions completed
      value  =  counters[6] / (double)counters[7];
      value1 = (counters[7] / (double)counters[6])*100.0;
      System.out.print("inst  INST_DISP "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // cycles group dispatch blocked by scoreboard
      value  =  counters[8] / (double)counters[4];
      value1 = (counters[4] / (double)counters[8])*100.0;
      System.out.print("  cyc  GRP_DISP_BLK_SB "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // cycles CR logical operation mapper full
      value  =  counters[8] / (double)counters[2];
      value1 = (counters[2] / (double)counters[8])*100.0;
      System.out.print("  CR_MAP_FULL "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // cycles CR issue Queue full
      value  =  counters[8] / (double)counters[3];
      value1 = (counters[3] / (double)counters[8])*100.0;
      System.out.print("  CRQ_FULL "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // cycles LR/CTR mapper full
      value  =  counters[8] / (double)counters[5];
      value1 = (counters[5] / (double)counters[8])*100.0;
      System.out.print("  LR_CTR_MAP_FULL "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_ISU_QUEUES1) {      //  group 19
      // FPU0 cycles issue queue is full = cycles / FPU0_FULL_CYC
      value  =  counters[5] / (double)counters[1];
      value1 = (counters[1] / (double)counters[5])*100.0;
      System.out.print("cyc  FPU0_FULL "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // FPU1 cycles issue queue is full = cycles / FPU1_FULL_CYC
      value  =  counters[5] / (double)counters[2];
      value1 = (counters[2] / (double)counters[5])*100.0;
      System.out.print("  FPU1_FULL "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // cycles FX0/LS0 issue queue is full (stall) = cycles / FX0/LS0_FULL_CYC
      value  =  counters[5] / (double)counters[3];
      value1 = (counters[3] / (double)counters[5])*100.0;
      System.out.print("  inst  FXLS0_FULL "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // cycles FX1/LS1 issue queue is full (stall) = cycles / FX1/LS1_FULL_CYC
      value  =  counters[5] / (double)counters[4];
      value1 = (counters[4] / (double)counters[5])*100.0;
      System.out.print("  FXLS1_FULL "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // LRQ0 cycles  is full = cycles / LRQ0_FULL_CYC
      value  =  counters[5] / (double)counters[7];
      value1 = (counters[7] / (double)counters[5])*100.0;
      System.out.print("  LSU_LRQ_FULL "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // SRQ cycles queue is full = cycles / SRQ_FULL
      value  =  counters[5] / (double)counters[8];
      value1 = (counters[8] / (double)counters[5])*100.0;
      System.out.print("  LSU_SRQ_FULL "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_ISU_FLOW) {         //  group 20
      // FXU0 produced a result = cycles / FXU0_FIN
      value  =   counters[8] / (double)counters[3];
      value1 = (counters[3] / (double)counters[8])*100.0;
      System.out.print("cyc  FXU0_FIN "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // FXU1 produced a result = cycles / FXU1_FIN
      value  =   counters[8] / (double)counters[4];
      value1 = (counters[4] / (double)counters[8])*100.0;
      System.out.print("  FXU1_FIN "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // Group dispatch valid = cycles / grp dispatch valid
      value  =   counters[8] / (double)counters[5];
      value1 = (counters[5] / (double)counters[8])*100.0;
      System.out.print("  GRP_DISP_VALID "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // Group dispatch rejected = cycles / grp dispatch rejected
      value  =   counters[8] / (double)counters[6];
      value1 = (counters[6] / (double)counters[8])*100.0;
      System.out.print("  GRP_DISP_REJECT "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_SERIALIZE) {        //  group 22
      // 
      value  =  counters[4] / (double)counters[2];
      value1 = (counters[2] / (double)counters[4])*100.0;
      System.out.print("cyc  STCX_FAIL "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // 
      value  =  counters[4] / (double)counters[3];
      value1 = (counters[3] / (double)counters[4])*100.0;
      System.out.print("  STCX_PASS "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // 
      value  =  counters[4] / (double)counters[7];
      value1 = (counters[7] / (double)counters[4])*100.0;
      System.out.print("  LARX_LSU0 "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // 
      value  =  counters[4] / (double)counters[8];
      value1 = (counters[8] / (double)counters[4])*100.0;
      System.out.print("  LARX_LSU1 "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // 
      value  =  counters[4] / (double)counters[5];
      value1 = (counters[5] / (double)counters[4])*100.0;
      System.out.print("  1PLUS_PPC_CMPL "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_LSU_BUSY) {         //  group 23
      // estimated load time = LRQ slot 0 valid / LRQ slot 0 allocated
      value   =  counters[5] / (double)counters[6];
      value1  = (counters[5] / (double)counters[8])*100.0;
      System.out.print("cyc  est_LRQ_latency "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%TC)");
      // estimated store time = SRQ slot 0 valid / SRQ slot 0 allocated
      value   =  counters[1] / (double)counters[2];
      value1  = (counters[1] / (double)counters[8])*100.0;
      System.out.print("  est_SRQ_latency "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%TC)");
      // LSU0 busy = cycles / LSU0 busy
      value   = counters[8] / (double)counters[3];
      value1  = (counters[3] / (double)counters[8])*100.0;
      System.out.print("  LSU0_BUSY "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");
      // LSU1 busy = cycles / LSU1 busy
      value   = counters[8] / (double)counters[4];
      value1  = (counters[4] / (double)counters[8])*100.0;
      System.out.print("  LSU1_BUSY "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.twoDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_LSOURCE3) {         //  group 25
      // Where are instructions coming from?
      // data from memory = inst_completed / data from memory
      value  =  counters[8] / (double)counters[2];
      value1 = (counters[2] / (double)counters[8])*100;
      System.out.print("  DATA_FROM_MEM "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // data from L3 = inst_completed / data from L3
      value  =  counters[8] / (double)counters[1];
      value1 = (counters[1] / (double)counters[8])*100;
      System.out.print("  DATA_FROM_L3 "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // data from L2 = inst_completed / data from L2
      value  =  counters[8] / (double)counters[4];
      value1 = (counters[4] / (double)counters[8])*100;
      System.out.print("  DATA_FROM_L2 "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // data from L2.75 = inst_completed / data from L2
      value  =  counters[8] / (double)counters[7];
      value1 = (counters[7] / (double)counters[8])*100;
      System.out.print("  DATA_FROM_L275 "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // data from L2.75 modified = inst_completed / data from L2.75 mod
      value  =  counters[8] / (double)counters[7];
      value1 = (counters[7] / (double)counters[8])*100;
      System.out.print("  DATA_FROM_L275_MOD "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // dcache reloaded valid = inst_completed / data cache reloaded
      value  =  counters[8] / (double)counters[1];
      value1 = (counters[1] / (double)counters[8])*100;
      System.out.print("  DCCHE_RELOAD_VALID "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_ISOURCE3) {         //  group 27
      // Where are instructions coming from?
      // instructions from memory = inst_completed / instructions from memory
      value  =  counters[8] / (double)counters[1];
      value1 = (counters[1] / (double)counters[8])*100;
      System.out.print("inst   INST_FROM_MEM "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // instructions from L3 = inst_completed / instructions from L3
      value  =  counters[8] / (double)counters[5];
      value1 = (counters[5] / (double)counters[8])*100;
      System.out.print("  INST_FROM_L3 "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // instructions from L2 = inst_completed / instructions from L2
      value  =  counters[8] / (double)counters[3];
      value1 = (counters[3] / (double)counters[8])*100;
      System.out.print("  INST_FROM_L2 "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // instructions from L1 = inst_completed / instructions from L1
      value  =  counters[8] / (double)counters[6];
      value1 = (counters[6] / (double)counters[8])*100;
      System.out.print("  INST_FROM_L1 "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // instructions from L35 = inst_completed / instructions from L35
      value  =  counters[8] / (double)counters[4];
      value1 = (counters[4] / (double)counters[8])*100;
      System.out.print("  INST_FROM_L35 "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_FPU3) {             //  group 28
      //
      value  =  counters[8] / (double)counters[1];
      value1 = (counters[1] / (double)counters[8])*100;
      System.out.print("cyc  FPU0_FDIV "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      //
      value  =  counters[8] / (double)counters[2];
      value1 = (counters[2] / (double)counters[8])*100;
      System.out.print("  FPU1_FDIV "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      //
      value  =  counters[8] / (double)counters[3];
      value1 = (counters[3] / (double)counters[8])*100;
      System.out.print("  FPU0_FRSP_FCONV "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      //
      value  =  counters[8] / (double)counters[4];
      value1 = (counters[4] / (double)counters[8])*100;
      System.out.print("  FPU1_FRSP_FCONV "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      //
      value  =  counters[8] / (double)counters[5];
      value1 = (counters[5] / (double)counters[8])*100;
      System.out.print("  FPU0_FMA "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      //
      value  =  counters[8] / (double)counters[6];
      value1 = (counters[6] / (double)counters[8])*100;
      System.out.print("  FPU1_FMA "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_FPU4) {             //  group 29
      //
      value  =  counters[8] / (double)counters[1];
      value1 = (counters[1] / (double)counters[8])*100;
      System.out.print("cyc  FPU0_FSQRT "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      //
      value  =  counters[8] / (double)counters[2];
      value1 = (counters[2] / (double)counters[8])*100;
      System.out.print("  FPU1_FSQRT "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      //
      value  =  counters[8] / (double)counters[3];
      value1 = (counters[3] / (double)counters[8])*100;
      System.out.print("  FPU0_FIN "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      //
      value  =  counters[8] / (double)counters[4];
      value1 = (counters[4] / (double)counters[8])*100;
      System.out.print("  FPU1_FIN "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      //
      value  =  counters[8] / (double)counters[5];
      value1 = (counters[5] / (double)counters[8])*100;
      System.out.print("  FPU0_ALL "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      //
      value  =  counters[8] / (double)counters[6];
      value1 = (counters[6] / (double)counters[8])*100;
      System.out.print("  FPU1_ALL "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_FPU5) {             //  group 30
      // 
      value  =  counters[5] / (double)counters[1];
      value1 = (counters[1] / (double)counters[5])*100;
      System.out.print("cyc  FPU0_DENORM "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[5] / (double)counters[2];
      value1 = (counters[2] / (double)counters[5])*100;
      System.out.print("  FPU1_DENORM "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[5] / (double)counters[3];
      value1 = (counters[3] / (double)counters[5])*100;
      System.out.print("  FPU0_FMOV_FEST "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[5] / (double)counters[4];
      value1 = (counters[4] / (double)counters[5])*100;
      System.out.print("  FPU1_FMOV_FEST "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      if (counters[7] != 0) {
        value  =  counters[5] / (double)counters[7];
        value1 = (counters[7] / (double)counters[5])*100;
        System.out.print("  FPU0_FEST "+Utilities.twoDigitDouble(value));
        System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      } else {
        System.out.print("  FPU0_FEST 0.0 (0.0%)");
      }
      // 
      if (counters[7] != 0) {
        value  =  counters[5] / (double)counters[8];
        value1 = (counters[8] / (double)counters[5])*100;
        System.out.print("  FPU1_FEST "+Utilities.twoDigitDouble(value));
        System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      } else {
        System.out.print("  FPU1_FEST 0.0 (0.0%)");
      }

    } else if (group_index == CommandLineOptions.P4_FPU6) {             //  group 31
      // 
      value  =  counters[7] / (double)counters[1];
      value1 = (counters[1] / (double)counters[7])*100;
      System.out.print("cyc  FPU0_SINGLE "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[7] / (double)counters[2];
      value1 = (counters[2] / (double)counters[7])*100;
      System.out.print("  FPU1_SINGLE "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[7] / (double)counters[3];
      value1 = (counters[3] / (double)counters[7])*100;
      System.out.print("  LSU0_LDF "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[7] / (double)counters[4];
      value1 = (counters[4] / (double)counters[7])*100;
      System.out.print("  LSU1_LDF "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[7] / (double)counters[5];
      value1 = (counters[5] / (double)counters[7])*100;
      System.out.print("  FPU0_STF "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[7] / (double)counters[6];
      value1 = (counters[6] / (double)counters[7])*100;
      System.out.print("  FPU1_STF "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_FPU7) {             //  group 32
      // 
      value  =  counters[5] / (double)counters[1];
      value1 = (counters[1] / (double)counters[5])*100;
      System.out.print("cyc  FPU0_STALL3 "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[5] / (double)counters[2];
      value1 = (counters[2] / (double)counters[5])*100;
      System.out.print("  FPU1_STALL3 "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[5] / (double)counters[3];
      value1 = (counters[3] / (double)counters[5])*100;
      System.out.print("  FPU0_FIN "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[5] / (double)counters[4];
      value1 = (counters[4] / (double)counters[5])*100;
      System.out.print("  FPU1_FIN "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[5] / (double)counters[8];
      value1 = (counters[8] / (double)counters[5])*100;
      System.out.print("  FPU0_FPSCR "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_FXU) {              //  group 33
      // FXU produced a result = instructions / fxu fin
      value  =  counters[1] / (double)counters[3];
      value1 = (counters[3] / (double)counters[1])*100;
      System.out.print("inst  FXU_FIN "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // FXU idle = instrucions / fxu idle
      value  =  counters[2] / (double)counters[5];
      value1 = (counters[5] / (double)counters[2])*100;
      System.out.print("  cyc  FXU_IDLE "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // FXU busy = cycles / fxu busy
      value  =  counters[2] / (double)counters[6];
      value1 = (counters[6] / (double)counters[2])*100;
      System.out.print("  FXU_BUSY "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // FXU1 busy FXU0 idle = cycles / FXU1 busy and FXU0 idle
      value  =  counters[2] / (double)counters[4];
      value1 = (counters[4] / (double)counters[2])*100;
      System.out.print("  FXU1_BUSY_FXU0_IDLE "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // FXU0 busy FXU1 idle = cycles / FXU0 busy and FXU1 idle
      value  =  counters[2] / (double)counters[7];
      value1 = (counters[7] / (double)counters[2])*100;
      System.out.print("  FXU0_BUSY_FXU1_IDLE "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // FXLS full = cycles / FXLS full
      value  =  counters[2] / (double)counters[8];
      value1 = (counters[8] / (double)counters[2])*100;
      System.out.print("  FXLS_FULL "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_LSU_LMQ) {          //  group 34
      // estimated load miss time = LMQ slot 0 valid / LMQ slot 0 allocated
      value  =  counters[4] / (double)counters[3];
      value1 = (counters[4] / (double)counters[5])*100;
      System.out.print( "cyc  est_LMQ_latency "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%TC)");
      // data table walk cycles as per centage of total cycles. (group  8)
      value  = (counters[5] / (double)counters[8])*100.0;
      value1 = (counters[8] / (double)counters[5])*100.0;
      System.out.print(  "  DATA_TABLEWALK "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // LSU LMQ full = cycles / LSU_LMQ_FULL
      value  =  counters[5] / (double)counters[2];
      value1 = (counters[2] / (double)counters[5])*100;
      System.out.print("  LSU_LMQ_FULL "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[5] / (double)counters[1];
      value1 = (counters[1] / (double)counters[5])*100;
      System.out.print("  LSU_LMQ_LHR_MERGE "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[5] / (double)counters[7];
      value1 = (counters[7] / (double)counters[5])*100;
      System.out.print("  LSU_SRQ_SYNC "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_LSU_LOAD1) {                // group 36
      // 
      value  =  counters[6] / (double)counters[3];
      value1 = (counters[3] / (double)counters[6])*100;
      System.out.print("inst  LD_REF_L1_LSU0 "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[6] / (double)counters[4];
      value1 = (counters[4] / (double)counters[6])*100;
      System.out.print("  LD_REF_L1_LSU1 "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[6] / (double)counters[7];
      value1 = (counters[7] / (double)counters[6])*100;
      System.out.print("  LD_MISS_L1_LSU0 "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[6] / (double)counters[8];
      value1 = (counters[8] / (double)counters[6])*100;
      System.out.print("  LD_MISS_L1_LSU1 "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // L1 dcache load both sides / L1 dcache load miss both sides 
      value = ((counters[3]+counters[4])/(double)(counters[7]+counters[8]))*100.0;
      System.out.print(  " DL1_loads_per_misses "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[5] / (double)counters[1];
      value1 = (counters[1] / (double)counters[5])*100;
      System.out.print("  cyc  LSU0_FLUSH_ULD "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[5] / (double)counters[2];
      value1 = (counters[2] / (double)counters[5])*100;
      System.out.print("  LSU1_FLUSH_ULD "+Utilities.twoDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_LSU_STORE1) {       // group 37
      // 
      value  = (counters[5] / (double)counters[1]);
      value1 = (counters[1] / (double)counters[5])*100.0;
      System.out.print(  "cyc  LSU0_FLUSH_UST "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  = (counters[5] / (double)counters[2]);
      value1 = (counters[2] / (double)counters[5])*100.0;
      System.out.print(  "  LSU1_FLUSH_UST "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  = (counters[5] / (double)counters[3]);
      value1 = (counters[3] / (double)counters[5])*100.0;
      System.out.print(  "  ST_REF_L1_LSU0 "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  = (counters[5] / (double)counters[4]);
      value1 = (counters[4] / (double)counters[5])*100.0;
      System.out.print(  "  ST_REF_L1_LSU1 "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  = (counters[5] / (double)counters[7]);
      value1 = (counters[7] / (double)counters[5])*100.0;
      System.out.print(  "  ST_MISS_L1 "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  = (counters[5] / (double)counters[8]);
      value1 = (counters[8] / (double)counters[5])*100.0;
      System.out.print(  "  DC_INV_L2 "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_LSU7) {             // group 39
      // 
      value  =  counters[7] / (double)counters[1];
      value1 = (counters[1] / (double)counters[7])*100.0;
      System.out.print("inst  LSU0_DERAT_MISS "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[7] / (double)counters[2];
      value1 = (counters[2] / (double)counters[7])*100.0;
      System.out.print("  LSU1_DERAT_MISS "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[6] / (double)counters[5];
      value1 = (counters[5] / (double)counters[6])*100.0;
      System.out.print("  cyc  L1_DCACHE_RELOAD_VALID "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_MISC) {             // group 41
      // 
      value  =  counters[4] / (double)counters[1];
      value1 = (counters[1] / (double)counters[4])*100.0;
      System.out.print(  "cyc  GCT_EMPTY "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[4] / (double)counters[2];
      value1 = (counters[2] / (double)counters[4])*100.0;
      System.out.print(  "  LSU_LMQ_SRQ_EMPTY "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[4] / (double)counters[5];
      value1 = (counters[5] / (double)counters[4])*100.0;
      System.out.print(  "  1PLUS_PPC_CMPL "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[4] / (double)counters[7];
      value1 = (counters[7] / (double)counters[4])*100.0;
      System.out.print(  "  GRP_CMPLx "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[4] / (double)counters[3];
      value1 = (counters[3] / (double)counters[4])*100.0;
      System.out.print(  "  HV "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  =  counters[4] / (double)counters[8];
      value1 = (counters[8] / (double)counters[4])*100.0;
      System.out.print(  "  TB_BIT_TRANS "+Utilities.threeDigitDouble(value));
      System.out.print(  "("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_BRANCH_ANALYSIS) {          // group 55
      // instructions per branch = instructions completed / branches issued
      value  = (counters[1] / (double)counters[3]);
      value1 = (counters[3] / (double)counters[1])*100.0;
      System.out.print("inst  BR_ISSUED "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // branch mispredict rate (direction) = branches issued / branch mispredict CR value 
      value  = (counters[3] / (double)counters[4]);
      value1 = (counters[4] / (double)counters[3])*100.0;
      System.out.print("  BR_MPRED_CR "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // branch mispredict rate (count/link) = branches issued / branch mispredict Target address
      value  = (counters[3] / (double)counters[7]);
      value1 = (counters[7] / (double)counters[3])*100.0;
      System.out.print("  BR_MPRED_TA "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // Cycles BIQ or IDU full = CYC / BIQ_IDU_FULL_CYC
      value   = (counters[6] / (double)counters[2]);
      value1  = (counters[2] / (double)counters[6])*100.0;
      System.out.print("  cyc  BIQ_IDU_full "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // Cycles BRQ full = CYC / BRQ_FULL_CYC
      value   = (counters[6] / (double)counters[5]);
      value1  = (counters[5] / (double)counters[6])*100.0;
      System.out.print("  BRQ_FULL "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_L1_AND_TLB_ANALYSIS) {      // group 56
      // ITLB miss rate                 = instructions / ITLB misses
      value  = (counters[6] / (double)counters[2]);
      value1 = (counters[2] / (double)counters[6])*100.0;
      System.out.print("inst  ITLB_MISS "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // instructions per L1 load       = instructions / L1 dcache load 
      value  = (counters[6] / (double)counters[8]);
      value1 = (counters[8] / (double)counters[6])*100.0;
      System.out.print("  LD_REF_L1 "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // instructions per L1 load  miss = instructions / L1 dcache load miss
      value  = (counters[8] / (double)counters[3]);
      value1 = (counters[3] / (double)counters[8])*100.0;
      System.out.print("  LD_REF_L1_per_miss "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // DTLB miss rate                 = instructions / D TLB miss
      value  = (counters[6] / (double)counters[1]);
      value1 = (counters[1] / (double)counters[6])*100.0;
      System.out.print("  DTLB_MISS "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // instructions per L1 store      = instructions / L1 dcache store 
      value  = (counters[6] / (double)counters[7]);
      value1 = (counters[7] / (double)counters[6])*100.0;
      System.out.print("  ST_REF_L1 "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // instructions per L1 store miss = instructions / L1 dcache store miss
      value  = (counters[7] / (double)counters[4]);
      value1 = (counters[4] / (double)counters[7])*100.0;
      System.out.print("  ST_REF_L1_per_miss "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
    } else if (group_index == CommandLineOptions.P4_L2_ANALYSIS) {              // group 57
      // 
      value  = (counters[1] / (double)counters[3]);
      value1 = (counters[3] / (double)counters[1])*100.0;
      System.out.print("inst  DATA_FROM_L35 "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  = (counters[1] / (double)counters[4]);
      value1 = (counters[4] / (double)counters[1])*100.0;
      System.out.print("  DATA_FROM_L2 "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  = (counters[1] / (double)counters[5]);
      value1 = (counters[5] / (double)counters[1])*100.0;
      System.out.print("  DATA_FROM_L25_SHR "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  = (counters[1] / (double)counters[8]);
      value1 = (counters[8] / (double)counters[1])*100.0;
      System.out.print("  DATA_FROM_L25_MOD "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  = (counters[1] / (double)counters[6]);
      value1 = (counters[6] / (double)counters[1])*100.0;
      System.out.print("  DATA_FROM_L275_SHR "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  = (counters[1] / (double)counters[7]);
      value1 = (counters[7] / (double)counters[1])*100.0;
      System.out.print("  DATA_FROM_L275_MOD "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    } else if (group_index == CommandLineOptions.P4_L3_ANALYSIS) {              // group 58
      // 
      value  = (counters[7] / (double)counters[2]);
      value1 = (counters[1] / (double)counters[7])*100.0;
      System.out.print("inst  DATA_FROM_MEM "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  = (counters[7] / (double)counters[1]);
      value1 = (counters[1] / (double)counters[7])*100.0;
      System.out.print("  DATA_FROM_L3 "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  = (counters[7] / (double)counters[3]);
      value1 = (counters[3] / (double)counters[7])*100.0;
      System.out.print("  DATA_FROM_L35 "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  = (counters[7] / (double)counters[4]);
      value1 = (counters[4] / (double)counters[7])*100.0;
      System.out.print("  DATA_FROM_L2 "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  = (counters[7] / (double)counters[5]);
      value1 = (counters[5] / (double)counters[7])*100.0;
      System.out.print("  DATA_FROM_L25_SHR "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");
      // 
      value  = (counters[7] / (double)counters[8]);
      value1 = (counters[8] / (double)counters[7])*100.0;
      System.out.print("  DATA_FROM_L25_MOD "+Utilities.threeDigitDouble(value));
      System.out.print("("+Utilities.threeDigitDouble(value1)+"%)");

    }
  }
  /**
   * Print the performance computations.
   * @param header
   */
  public void printCPI(TraceHeader header) 
  {
    long cycles = 0;
    long instructions = 0;
    if (header.isPower4()){
      int group_index = header.groupNumber();
      if (group_index == CommandLineOptions.P4_SLICE0) {                        // group  0
        cycles = counters[2];      instructions = counters[4];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_BASIC) {                  // group  2
        cycles = counters[2];      instructions = counters[6];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_IFU) {                    // group  3
        cycles = counters[6];      instructions = counters[1];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_ISU) {                    // group  4
        cycles = counters[7];      instructions = counters[4];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_LSOURCE) {                // group  5
        System.out.print("  CPI N/A");
        // not available
      } else if (group_index == CommandLineOptions.P4_ISOURCE) {                // group  6
        System.out.print("  CPI N/A");
      } else if (group_index == CommandLineOptions.P4_LSU) {                    // group  7
        cycles = counters[3];      instructions = counters[4];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_XLATE1) {                 // group  8
        cycles = counters[8];      instructions = counters[7];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_XLATE2) {                 // group  9
        cycles = counters[8];      instructions = counters[7];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_FPU1) {                   // group 14
        cycles = counters[5];      instructions = counters[7];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_FPU2) {                   // group 15
        cycles = counters[3];      instructions = counters[4];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_IDU1) {                   // group 16
        cycles = counters[2];      instructions = counters[1];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_IDU2) {                   // group 17
        cycles = counters[2];      instructions = counters[1];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_ISU_RENAME) {             // group 18
        cycles = counters[8];      instructions = counters[7];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_ISU_QUEUES1) {            // group 19
        cycles = counters[5];      instructions = counters[6];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_ISU_FLOW) {               // group 20
        cycles = counters[8];      instructions = counters[7];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_SERIALIZE) {              // group 22
        cycles = counters[4];      instructions = counters[6];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_LSU_BUSY) {               // group 23
        cycles = counters[8];      instructions = counters[7];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_LSOURCE3) {               // group 25
        cycles = counters[6];      instructions = counters[8];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_ISOURCE3) {               // group 27
        cycles = counters[7];      instructions = counters[8];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_FPU3) {                   // group 28
        cycles = counters[8];      instructions = counters[7];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_FPU4) {                   // group 29
        cycles = counters[8];      instructions = counters[7];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_FPU5) {                   // group 30
        cycles = counters[5];      instructions = counters[6];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_FPU6) {                   // group 31
        cycles = counters[7];      instructions = counters[8];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_FPU7) {                   // group 31
        cycles = counters[5];      instructions = counters[6];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_FXU) {                    // group 33
        cycles = counters[2];      instructions = counters[1];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_LSU_LMQ) {                // group 34
        cycles = counters[5];      instructions = counters[6];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_LSU_LOAD1) {              // group 36
        cycles = counters[5];      instructions = counters[6];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_LSU_STORE1) {             // group 37
        cycles = counters[5];      instructions = counters[6];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_LSU7) {                   // group 39
        cycles = counters[6];      instructions = counters[7];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_MISC) {                   // group 41
        cycles = counters[4];      instructions = counters[6];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_BRANCH_ANALYSIS) {        // group 55
        cycles = counters[6];      instructions = counters[1];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_L1_AND_TLB_ANALYSIS) {    // group 56
        cycles = counters[5];      instructions = counters[6];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_L2_ANALYSIS) {            // group 57
        cycles = counters[2];      instructions = counters[1];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else if (group_index == CommandLineOptions.P4_L3_ANALYSIS) {            // group 58
        cycles = counters[6];      instructions = counters[7];
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else {
        System.out.print("  CPI not computed for group "+group_index);
      }
    } else if (header.isRS64_III()){
      // look up cycles and instructions in counters.
      for (int i=1; i<=header.n_counters; i++) {
        if        (header.short_event_names[i].startsWith("PM_CYC")) {
          cycles = counters[i];
        } else if (header.short_event_names[i].startsWith("PM_INST_CMPL")) {
          instructions = counters[i];
        }
      }
      if (cycles != 0 && instructions != 0) {
        double value = (cycles/ (double)instructions);
        System.out.print("  CPI "+Utilities.threeDigitDouble(value));
      } else {
        System.out.print("  don't know how to compute CPI.  Either cycles "+cycles+
                         " or instructions "+instructions+" == 0");     
      }
    } else {
      System.out.print("  don't know how to compute CPI");
    }
  }

  /*
   * Print trace record for Power4 when group 23
   * IN particular, 
   * 1 PM_LSU_SRQ_SO_VALID  
   * 2 PM_LSU_SRQ_SO_ALLOC
   * 3 PM_LSU0_BUSY
   * 4 PM_LSU1_BUSY
   * 5 PM_LSU_LRQ_SO_VALID
   * 6 PM_LSU_LRQ_SO_ALLOC
   * 7 PM_INST_CMPL
   * 8 PM_CYC
   */
  public boolean printP4G23(TraceHeader trace_header) {
    // System.out.println("TraceRecord.printP4G23() # of counters "+info.numberOfCounters);
    boolean notZero = false;
    double store_time = Utilities.threeDigitDouble(counters[1]/(double)counters[2]);
    double  load_time = Utilities.threeDigitDouble(counters[5]/(double)counters[6]);
    for (int i=0; i<=n_counters; i++) {
      if (counters[i] > 0) {
        notZero = true;
        System.out.print(i+":"+trace_header.short_event_name(i)+": "+Utilities.format_long(counters[i]));
        if (i == 2 ) {
          System.out.println(" "+store_time);
        } else if (i == 6) {
          System.out.println(" "+load_time);
        } else {
          System.out.println();
        }
      }
    }
    return notZero;
  }
  public void reset() 
  {
    vpid       = -1;
    tid        = -99999;
    local_tid  = -99999;
    start_wall_time = 0;
    for (int i=0; i<=n_counters; i++) {
      counters[i]=0;
    }
  }

  /**
   * Accumulate a trace record.
   *
   * @param unit trace record to be accumulated
   */
  public void accumulate(TraceCounterRecord tcr) {
    for (int i=0; i<=n_counters; i++) {
      counters[i] += tcr.counters[i];
    }
  }

}
