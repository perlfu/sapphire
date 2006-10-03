/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_CommandLineArgs;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Offset;

//-#if RVM_WITH_HPM
import com.ibm.JikesRVM.Java2HPM;
//-#endif

//BEGIN HRM
import com.ibm.JikesRVM.classloader.VM_MemberReference;
import com.ibm.JikesRVM.classloader.VM_Atom;
//END HRM

/**
 * This class provides support to hardware performance monitors
 * without making any assumption of what PowerPC architecture Jikes RVM is running on.
 * <p>
 * No instances of this class is every created.
 * <p>
 * Writes aggregate HPM data to console when called back.
 * <p> 
 * Manages trace header file that starts with
 *  version_number(int) n_counters(int) mode(int) 
 * and contains multiple record formats.
 *
 * @author Peter F. Sweeney
 * @author Dave Grove
 * @modified Matthias Hauswirth  August 2003
 */
public class VM_HardwarePerformanceMonitors implements VM_SizeConstants 
{

  /**
   * Is the HPM system enabled?
   * Enabled only if command line argument specifies hardware event to monitor.
   */
  static private boolean enabled = false;
  static public  boolean enabled() { return enabled; }
  /**
   * Is the HPM system booted?
   * Booted only after events are set.
   */
  static private boolean booted = false;
  static public  boolean booted() { return booted; }

  /*
   * output trace header file
   */
  static private FileOutputStream header_trace_file = null;
  /*
   * header trace record formats
   */
  static private int MACHINE_TYPE_RECORD = 1;
  static private int        EVENT_RECORD = 2;
  static private int       THREAD_RECORD = 3;
  //BEGIN HRM
  static private int       METHOD_RECORD = 4;
  //END HRM

  // Set true in VM_HPMs.setUpHPMinfo() to tell VM_Processor when it is safe to collect hpm data!  
  static public  boolean safe = false;

  /*
   * static fields required size calculations
   */
  //BEGIN HRM
  // (tid(16) & buffer_code(1) & thread_switch(1) & vpid(10 & trace_format(4)) (int), global_tid(int), startOfWallTime(long), endOfWallTime(long), mid1(int), mid2(int), counters(long)*
  static public  int     SIZE_OF_HEADER     = 32; 
  //END HRM

  static private int     record_size        = 0;     // in bytes, record size
  /**
   * Called from VM_
   * @return record size
   */
  static public  int getRecordSize() throws UninterruptiblePragma 
  {
    return record_size; 
  }

  /*
   * Command line options
   */
  // trace hpm events?
  static public  boolean trace         = false;  
  // trace hpm events verbosely?
  static public  int     trace_verbose = 0;  
  /*
   * hpm events verbosely?
   * -1 = no aggregate (used for timing)
   *  0 = aggregate; 
   *  1 = enter/exit once executed methods; 
   *  2 = enter/exit more frequently executed methods or details of once executed methods
   *  3 = unexpected events & infrequent events; 
   *  4 = frequent events; 
   *  5 > very verbose
   */
  static public  int     verbose                  = 0;  
  // Use HPM thread group API (instead of thread API)
  static public  boolean thread_group             = false;
  // sample HPM value more frequently than a thread switch?
  static public  boolean sample                   = false;
  // report HPM counter values during call backs from application
  static public  boolean report                   = false;

  // Print machine's processor name
  static private boolean hpm_processor            = false;
  // List all events on machine
  static private boolean hpm_list_all_events      = false;
  // List events that are selected to be counted
  static private boolean hpm_list_selected_events = false;
  // test HPM access times for sysCalls and JNI
  static private boolean hpm_test                 = false;

  /*
   * Do not allowed an instance of this class to be created
   */
  private VM_HardwarePerformanceMonitors() {}

  /**
   * Describe command line arguments 
   */
  static public void printHelp() {
    if (VM.BuildForHPM) {
      VM.sysWriteln("Boolean Options (-X:hpm:<option>=true or -X:hpm:<option>=false) default is false");
      VM.sysWriteln(" Option       Description"); 
      VM.sysWriteln(" trace        trace HPM counter values at each thread switch.");
      VM.sysWriteln(" processor    print name of processor, number of counters, and exit.");
      VM.sysWriteln(" listAll      list all events associated with each counter and exit.");
      VM.sysWriteln(" listSelected list selected events for each counter.");
      VM.sysWriteln(" report       report summary of HPM values when call back occurs from application or RVM.");
      VM.sysWriteln("              Only valid when JikesRVM is run on a single processor!");
      VM.sysWriteln("              For a multiprocessor, use trace!");
      VM.sysWriteln(" sample       sample HPM values more frequently than thread switch (set interruptQuantum and interruptQuantumMultiplier.");
      //VM.sysWriteln(" test       at end of execution, compute access time with sysCall and JNI.");
      VM.sysWriteln();
      VM.sysWriteln("Value Options (-X:hpm:<option>=<value>)");
      VM.sysWriteln(" Option        Type    Description"); 
      VM.sysWriteln(" eventN        int     specify event for counter N where 1<=N<=UB and UB is processor specific");
      VM.sysWriteln(" filename      String  prefix for file names.  Concatenate virtual processor number.");
      VM.sysWriteln(" mode          int     specify mode: 1=GROUP, 2=PROCESS, 4=KERNEL, 8=USER, 16=COUNT, 32=PROCTREE, 64=ALL");
      VM.sysWriteln(" trace_verbose int     PID to trace events for?");
      VM.sysWriteln(" verbose       int     print more information.");
      VM.sysWriteln();
    } else {
      VM.sysWriteln("\nrvm: Hardware performance monitors not supported");
    }
    VM.shutdown(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }

  /**
   * Process command line arguments
   * @param arg  command line arguments
   */
  static public void processArg(String arg) {
    if (VM.BuildForHPM) {
      //-#if RVM_WITH_HPM
      if (arg.compareTo("help") == 0) {
        printHelp();
      }
      int split = arg.indexOf('=');
      if (split == -1) {
        VM.sysWriteln("  Illegal option specification!\n  \""+arg+
                      "\" must be specified as a name-value pair in the form of option=value");
        printHelp();
      }
      String name = arg.substring(0,split-1);
      String name2 = arg.substring(0,split);
      if (name.equals("event")) {
        String num = arg.substring(split-1,split);
        String value = arg.substring(split+1);
        int eventNum = VM_CommandLineArgs.primitiveParseInt(num);
        int eventVal = VM_CommandLineArgs.primitiveParseInt(value);
        HPM_info.ids[eventNum] = eventVal;
        if (!enabled) {
          enabled = true;
        }
      } else if (name2.equals("mode")) {
        String value = arg.substring(split+1);
        int mode = VM_CommandLineArgs.primitiveParseInt(value);
        HPM_info.mode = mode;
      } else if (name2.equals("filename")) {
        HPM_info.filenamePrefix = arg.substring(split+1);
        if(verbose>=2)VM.sysWriteln("VM_HPMs.processArgs() filename prefix found \""+
                                  HPM_info.filenamePrefix+"\"");
      } else if (name2.equals("trace")) {
        String value = arg.substring(split+1);
        if (value.compareTo("true")==0) {
          trace = true;
        } else if (value.compareTo("false")==0) {
          trace = false;
        } else {
          VM.sysWriteln("\nrvm: unrecognized boolean value "+value+"\n -X:hpm:trace={true|false} is the correct syntax");
          VM.shutdown(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        }
      } else if (name2.equals("trace_verbose")) {
        String value = arg.substring(split+1);
        
        int pid = VM_CommandLineArgs.primitiveParseInt(value);
        if (pid < 0) {
          VM.sysWriteln("\nrvm: unrecognized value "+value+"\n -X:hpm:trace_verbose=PID where PID >= 0 is the correct syntax, and 0 has null functionality.");
          VM.shutdown(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        }
        trace_verbose = pid;
      } else if (name2.equals("processor")) {
        hpm_processor = true;
      } else if (name2.equals("verbose")) {
        String value = arg.substring(split+1);
        int verbose_level = VM_CommandLineArgs.primitiveParseInt(value);
        if (verbose_level < -1) {
          VM.sysWriteln("\nrvm: unrecognized value "+value+"\n -X:hpm:verbose=verbose_level where verbose_level >= -1 is the correct syntax");
          VM.shutdown(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        }
        verbose = verbose_level;
      } else if (name2.equals("listAll")) {
        String value = arg.substring(split+1);
        if (value.compareTo("true")==0) {
          hpm_list_all_events = true;
        } else if (value.compareTo("false")==0) {
          hpm_list_all_events = false;
        } else {
          VM.sysWriteln("\nrvm: unrecognized boolean value "+value+"\n -X:hpm:list={true|false} is the correct syntax");
          VM.shutdown(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        }
      } else if (name2.equals("listSelected")) {
        String value = arg.substring(split+1);
        if (value.compareTo("true")==0) {
          hpm_list_selected_events = true;
        } else if (value.compareTo("false")==0) {
          hpm_list_selected_events = false;
        } else {
          VM.sysWriteln("\nrvm: unrecognized boolean value "+value+"\n -X:hpm:events={true|false} is the correct syntax");
          VM.shutdown(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        }
      } else if (name2.equals("threadGroup")) {
        // not tested
        String value = arg.substring(split+1);
        if (value.compareTo("true")==0) {
          thread_group = true;
        } else if (value.compareTo("false")==0) {
          thread_group = false;
        } else {
          VM.sysWriteln("\nrvm: unrecognized boolean value "+value+"\n -X:hpm:threadGroup={true|false} is the correct syntax");
        }
      } else if (name2.equals("test")) {
        // hidden command line argument
        String value = arg.substring(split+1);
        if (value.compareTo("true")==0) {
          hpm_test = true;
        } else if (value.compareTo("false")!=0) {
          hpm_test = false;
        } else {
          VM.sysWriteln("\nrvm: unrecognized boolean value "+value+"\n -X:hpm:test={true|false} is the correct syntax");
          VM.shutdown(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        }
      } else if (arg.startsWith("report=")) {
        String tmp = arg.substring(split+1);
        if (tmp.compareTo("true")==0) { 
          report = true;
        } else if (tmp.compareTo("false")==0) {         
          report = false;
        } else {
          VM.sysWriteln("\n***VM_HPMs.processArgs() invalid -X:hpm:report argument \""+tmp+"\"!***\n");
          VM.shutdown(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        }
      } else if (arg.startsWith("sample=")) {
        String tmp = arg.substring(split+1);
        if (tmp.compareTo("true")==0) { 
          sample = true;
        } else if (tmp.compareTo("false")==0) {         
          sample = false;
        } else {
          VM.sysWriteln("\n***VM_HPMs.processArgs() invalid -X:hpm:sample argument \""+tmp+"\"!***\n");
          VM.shutdown(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        }
      } else {
        VM.sysWriteln("rvm: Unrecognized argument \"-X:hpm:"+arg+"\"");
        VM.shutdown(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
      }
      //-#endif
    } else { // ! VM.BuildForHPM
      VM.sysWriteln("\nrvm: Hardware performance monitors not supported.  Illegal command line options \""+arg+"\"\n");
      VM.shutdown(VM.EXIT_STATUS_HPM_TROUBLE);
    }
  }  
  
  /**
   * Initialize the hardware performance monitors with command line options after command
   * line arguments have been processed.  
   * Called from VM.boot() before VM_Scheduler.boot() is called; i.e. only one pthread is running.
   * This routine must be called before any other interaction with HPM occurs.
   * Use sysCall interface to initialize HPM because JNI environment is not yet initialized.
   * SysCall interface accesses static C methods in hpm.c.
   * <p>
   * Set up callbacks that are to be executed once for Jikes RVM independent of the 
   * number of virutal processors.
   * <p>
   * Assumption: only one OS thread is executing (only called once)!
   */
  static public void boot() 
  {
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM

      // When only the main thread Java thread is created, allocate HPM_counters.
      int events[] = HPM_info.ids;
      if (verbose>=4) {
        VM.sysWrite("VM_HPMs.boot(): Events 1: "); VM.sysWrite(events[1]);
        VM.sysWrite(", 2: ");VM.sysWrite(events[2]);VM.sysWrite(", 3: ");VM.sysWrite(events[3]);
        VM.sysWrite(", 4: ");VM.sysWrite(events[4]);VM.sysWrite(", 5: ");VM.sysWrite(events[5]);
        VM.sysWrite(", 6: ");VM.sysWrite(events[6]);VM.sysWrite(", 7: ");VM.sysWrite(events[7]);
        VM.sysWrite(", 8: ");VM.sysWrite(events[8]);VM.sysWrite(", mode: ");VM.sysWrite(HPM_info.mode);
        VM.sysWrite("\n");
      }
      if(verbose>=3)VM.sysWrite("VM_HPMs.boot() call hpmInit()\n");
      VM_SysCall.sysHPMinit();

      if(verbose>=3) {
        VM.sysWrite("VM_HPMs.boot() call hpmSetEvent(");
        VM.sysWrite(events[1]);VM.sysWrite(",");VM.sysWrite(events[2]);VM.sysWrite(",");
        VM.sysWrite(events[3]);VM.sysWrite(",");VM.sysWrite(events[4]);VM.sysWrite(")\n");
      }
      VM_SysCall.sysHPMsetEvent(events[1],events[2],events[3],events[4]);
      if(verbose>=3){
        VM.sysWrite("VM_HPMs.boot() call hpmSetEventX(");
        VM.sysWrite(events[5]);VM.sysWrite(",");VM.sysWrite(events[6]);VM.sysWrite(",");
        VM.sysWrite(events[7]);VM.sysWrite(",");VM.sysWrite(events[8]);VM.sysWrite(")\n");
      }
      VM_SysCall.sysHPMsetEventX(events[5],events[6],events[7],events[8]);
      if(verbose>=3){
        VM.sysWrite("VM_HPMs.boot() call hpmSetMode(",HPM_info.mode,")\n");
      }
      VM_SysCall.sysHPMsetMode(HPM_info.mode);

      // set hpm program for current pthread.  Inherited by other, to be created, pthreads.
      if (! thread_group) {
        if(verbose>=3)
          VM.sysWriteln("VM_HPMs.boot() call to sysHPMsetProgramMyThread() and sysHPMstartMyThread()");
        VM_SysCall.sysHPMsetProgramMyThread();
        VM_SysCall.sysHPMstartMyThread();
      } else {
        if(verbose>=3)
          VM.sysWriteln("VM_HPMs.boot() call to sysHPMsetProgramMyGroup() and sysHPMstartMyGroup()");
        VM_SysCall.sysHPMsetProgramMyGroup();
        VM_SysCall.sysHPMstartMyGroup();
      }

      HPM_info.setNumberOfEvents(VM_SysCall.sysHPMgetNumberOfEvents());
      HPM_info.setEndian(VM_SysCall.sysHPMisBigEndian());
      booted = true;
      // okay to construct HPM_counters

      // Needed to allocate the HPM counters for the primodial thread!
      VM_Thread thread = VM_Thread.getCurrentThread();
      if (thread.hpm_counters == null) {
        if(verbose>=3)VM.sysWriteln("VM_HPMs.boot() call new HPM_counters for primordial thread");
        thread.hpm_counters = new HPM_counters();
        
      }

      // set up callbacks
      if(report) {
        if(verbose>=3){ VM.sysWrite("VM_HPMs.boot() call setUpCallbacks\n"); }
        if (VM_Scheduler.numProcessors != 1) {
          VM.sysWrite("***VM_HPM.boot() -X:hpm:report can not be true when -X:processors != 1!***\n");
          VM.sysExit(VM.EXIT_STATUS_HPM_TROUBLE);
        }
        aos = new HPM_counters();
        sum = new HPM_counters();
        setUpCallbacks();
      }
      //-#endif
    }
  }
  /**
   * Use of JNI to get string information from HPM.
   * Called from VM.boot() after VM_Scheduler.boot() is called.
   * Assume that HPM's init routines have been called: 
   * hpm_init, hpm_set_event, hpm_set_event_X, hpm_set_mode, and set_settings.
   * <p>
   * Don't use VM.sysExit because rvm is in an inconsistent state during call to 
   * VM_Callbacks.notifyExit.  Instead use VM.shutdown.
   * <p>
   * Assumptions: 
   * Only one OS thread is executing (only called once)!
   * HPM command line arguments are already processed.
   */
  static public void setUpHPMinfo() {
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM
      if(verbose>=2) VM.sysWriteln("VM_HPMs.setUpHPMinfo()");
      /* 
       * Initialize HPM_info.
       */
      if(verbose>=3) {
        VM.sysWrite("VM_HPMs.setUpHPMinfo() number of events ",HPM_info.getNumberOfEvents(),"\n");
      }

      if (hpm_test) {
        Java2HPM.computeCostsToAccessHPM();
      }
      if (hpm_list_selected_events) {
        Java2HPM.listSelectedEvents();
      }
      HPM_info.setProcessorName(Java2HPM.getProcessorName());
      if(verbose>=3 || hpm_processor==true){
        VM.sysWrite("\nProcessor name: \"",HPM_info.getProcessorName(),"\" has ",HPM_info.getNumberOfEvents()); 
        VM.sysWrite(" events.\n\n");
      }
      if (hpm_processor) { // specified "processor" command line argument
        VM.shutdown(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
      }
      if (hpm_list_all_events) {
        Java2HPM.listAllEvents();
        VM.shutdown(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
      }
      String []short_names = new String[HPM_info.getNumberOfValues()];
      int[]    event_ids   = new int[HPM_info.getNumberOfValues()];
      int max_length = 10;
      for (int i=0; i<HPM_info.getNumberOfEvents(); i++) {
        short_names[ i] = Java2HPM.getEventShortName(i);
        int event_id = Java2HPM.getEventId(i);
        if (event_id != HPM_info.ids[i+1]) {
          VM.sysWrite  ("***VM_HPMs.setUpHPMinfo() Java2HPM.getEventId(",i,") ");
          VM.sysWrite  (event_id," != HPM_info.ids[",i+1);
          VM.sysWriteln("] ",HPM_info.ids[i+1],"!***");
          VM.shutdown(VM.EXIT_STATUS_HPM_TROUBLE);
        }
        if (max_length < short_names[i].length()) max_length=short_names[i].length();
        if (verbose>=4){
          VM.sysWrite("short_name[");VM.sysWrite(i+1);  
          VM.sysWrite("] \"");VM.sysWrite(short_names[i]);VM.sysWrite("\"\n");
        }
      }

      if(trace) {
        // compute trace record size
        record_size = SIZE_OF_HEADER + (HPM_info.getNumberOfValues() * BYTES_IN_LONG);

        // open the trace header file
        openFileOutputStream(HPM_info.filenamePrefix+".headerFile");
        writeHeader();

        // There could be a race condition here!
        // Alternative is to write all the threads at VM exit.  
        // Write thread records
        for (int i=1; i < VM_Scheduler.threadAllocationIndex; i++){
          // write header to file.
          VM_Thread thread = VM_Scheduler.threads[i];
          if (thread == null) {
            VM.sysWriteln("VM_HPMs.setUpHPMinfo() VM_Schedule.threads[",i,"] == null");
            continue;
          }
          int global_tid = thread.getGlobalIndex();
          String name = thread.getClass().toString();
          if (verbose>=4) { 
            VM.sysWrite  ("VM_HPMs.setUpHPMinfo() writeIdAndName(",global_tid);
            VM.sysWriteln(", ",name,")"); 
          }
          writeThread(global_tid, i, name );      
        }
      }
      // start collecting trace information
      safe = true;

      if (verbose>=4)VM.sysWrite("          max_length is ",max_length);
      max_length = ((max_length/4)+1)*4; // multiple of 4
      if (verbose>=4)VM.sysWriteln(" adjusted max_length is ",max_length);
      // format short names to same length
      // translate 0-origin to 1-origin short_names arrays
      for (int i=1;  i<HPM_info.getNumberOfValues(); i++) {
        HPM_info.short_names[i] = short_names[i-1];
        for (int j=0; j<max_length - short_names[i-1].length(); j++) {
          HPM_info.short_names[i] += " ";
        }
        if (trace) {
          writeEvent(i, HPM_info.ids[i], HPM_info.short_names[i]);
        }
        if (verbose>=4){
          VM.sysWrite("HPM_info.short_name[");VM.sysWrite(i);   
          VM.sysWrite("] \"");VM.sysWrite(HPM_info.short_names[i]);VM.sysWrite("\"\n");
        }
      }
      HPM_info.short_names[0] = "REAL_TIME";
      int length = HPM_info.short_names[0].length();
      for (int j=0; j<max_length - length; j++ ) {
        HPM_info.short_names[0] += " ";
      }
      if (trace) {
        writeEvent(0, -1, HPM_info.short_names[0]);
        writeMachineType(HPM_info.getProcessorName());  
      }
      if (verbose>=4){
        VM.sysWrite("HPM_info.short_name[0] \"");VM.sysWrite(HPM_info.short_names[0]);
        VM.sysWrite("\"\n");
      }
      //-#endif
    }
  }
  /*
   * Open FileOutputStream file to write HPM trace records!
   * CONSTRAINT: header_trace_file is null
   * CONSTRAINT: only called if trace is true!
   * CONSTRAINT: can't call printStackTrace() too early in the boot sequence.
   * Actions:
   *  Open file
   *
   * @param header_trace_filename name of file to open
   */
  static private void openFileOutputStream(String header_trace_filename)
  {
    if(verbose>=2)VM.sysWriteln("VM_HPMs.openFileOutputStream(",header_trace_filename,")");
    
    if (header_trace_file != null) {    // constraint
      VM.sysWriteln("***VM_HPMs.openFileOutputStream(",header_trace_filename,") header_trace_file != null!***");      
      VM.shutdown(VM.EXIT_STATUS_HPM_TROUBLE);
    }
    
    try {
      header_trace_file = new FileOutputStream(header_trace_filename);
    } catch (FileNotFoundException e) {
      VM.sysWriteln("***VM_HPMs.openFileOutputStream() FileNotFound exception with new FileOutputStream(",header_trace_filename,")");
      VM.shutdown(VM.EXIT_STATUS_HPM_TROUBLE);
    } catch (SecurityException e) {
      VM.sysWriteln("***VM_HPMs.openFileOutputStream() Security exception with new FileOutputStream(",header_trace_filename,")");
      VM.shutdown(VM.EXIT_STATUS_HPM_TROUBLE);
    } 
  }

  /*
   * Write header information when the trace header file is open!
   * Header prefix consists of:
   *   int version_number
   *   int endian
   *   int number_of_values
   *   int mode
   * CONSTRAINT: only called if trace is true!
   * CONSTRAINT version_number and endian must be written in BIG_ENDIAN byte order, 
   *  because we haven't read the byte order (endian field) yet!
   */
  static private void writeHeader()
  {
    if(verbose>=2){ 
      VM.sysWrite("VM_HPMs.writeHeader(",HPM_info.version_number,",");
      VM.sysWrite(HPM_info.getNumberOfValues()); VM.sysWrite(", ");
      VM.sysWrite(HPM_info.mode);VM.sysWrite(", "); 
      VM.sysWrite(HPM_info.isBigEndian()?"BIG_ENDIAN":"LITTLE_ENDIAN"); VM.sysWrite(")\n");
    }
    byte[] buffer   = new byte[100];    // temporary buffer
    Offset index    = Offset.zero();
  
    if (HPM_info.isBigEndian()) {
      // write version number 
      VM_Magic.setIntAtOffset(buffer, index, HPM_info.version_number);     index = index.plus(BYTES_IN_INT);
      // write endian
      VM_Magic.setIntAtOffset(buffer, index, HPM_info.getEndian());        index = index.plus(BYTES_IN_INT);
    } else {
      // write in default BIG_ENDIAN manner
      // write version number 
      VM_Magic.setIntAtOffset(buffer, index, HPM_info.swapByteOrder(HPM_info.version_number));index = index.plus(BYTES_IN_INT);
      // write endian
      VM_Magic.setIntAtOffset(buffer, index, HPM_info.swapByteOrder(HPM_info.getEndian()));   index = index.plus(BYTES_IN_INT);
    }
    // write number of events
    VM_Magic.setIntAtOffset(buffer, index, HPM_info.getNumberOfValues());index = index.plus(BYTES_IN_INT);
    // write mode
    VM_Magic.setIntAtOffset(buffer, index, HPM_info.mode);               index = index.plus(BYTES_IN_INT);

    if(verbose>=4){
      VM.sysWrite("VM_HPMs.writeHeaderPrefix() header: version number ", HPM_info.version_number);
      VM.sysWrite(  ", number of values ", HPM_info.getNumberOfValues());
      VM.sysWrite(", mode ", HPM_info.mode);
      VM.sysWrite(", endian ", HPM_info.getEndian());
      VM.sysWriteln(", record_size ", record_size);
    }
    // write it to the file
    writeHeaderFileOutputStream(buffer, index);
  }
  /*
   * write machine type record to header file buffer.
   * The formats are:
   * 1) machine type record
   *   1 MT_length(int) MT(byte[])
   */
  static private void writeMachineType(String machine_type)
  {
    Offset index = Offset.zero();
    // don't think we need this!
    //    machine_type = wordAlignedString(machine_type);
    byte[] buffer = new byte[machine_type.length()+8];
    // write record format number
    VM_Magic.setIntAtOffset(buffer, index, MACHINE_TYPE_RECORD);        index = index.plus(BYTES_IN_INT);
    index = writeStringToBuffer(buffer, index, machine_type.getBytes());
    // write buffer to file
    writeHeaderFileOutputStream(buffer, index);
  }
  /**
   * Write thread name to header file.
   * Called from VM_Thread's constuctors.
   *
   * @param global_tid  globally unique index
   * @param tid         local thread id
   * @param name        thread name
   */
  static public void writeThreadToHeaderFile(int global_tid, int tid, String name) 
  {
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM
      if(verbose>=4){
        VM.sysWrite  ("VM_HPMs.writeThreadToHeaderFile(",global_tid,",",tid);
        VM.sysWrite(",",name,") safe = "); VM.sysWriteln(safe);
      }
      if (safe) {
        writeThread(global_tid, tid, name);
      }
      //-#endif
    }
  }
  /*
   * Write a thread record to the trace file.
   * A thread record's format is:
   *   THREAD_RECORD(int), global_tid(int), tid(int), length(int) thread_name(byte[])
   *
   * @param global_tid  globally unique thread id
   * @param tid         local thread id
   * @param name        thread name
   */
  static private void writeThread(int global_tid, int tid, String name)
  {
    if(verbose>=5) {
      VM.sysWrite  ("VM_HPMs.writeThread(",global_tid,",",tid);
      VM.sysWriteln(",",name,")");
    }
    Offset index = Offset.zero();
    // do we need this?
    //    name = wordAlignedString(name);
    byte[] buffer = new byte[name.length()+4+12];
    // write record format number
    VM_Magic.setIntAtOffset(buffer, index, THREAD_RECORD);      index = index.plus(BYTES_IN_INT);
    // write global thread id
    VM_Magic.setIntAtOffset(buffer, index, global_tid);         index = index.plus(BYTES_IN_INT);
    // write local thread id
    VM_Magic.setIntAtOffset(buffer, index, tid);                index = index.plus(BYTES_IN_INT);
    // write event name
    index = writeStringToBuffer(buffer, index, name.getBytes());
    // write buffer to file
    writeHeaderFileOutputStream(buffer, index);
  }

  /*
   * Write an event record to the trace file
   * An event record's format is:
   *  EVENT_RECORD(int), id(int) length(int) name(byte[])
   *
   * @param counter  counter number
   * @param id       event number
   * @param name     event name
   */
  static private void writeEvent(int counter, int id, String name)
  {
    if(verbose>=4) {
      VM.sysWrite  ("VM_HPMs.writeEvent(",counter,",");
      VM.sysWrite  (id,", length ",name.length());
      VM.sysWriteln(" ",name);
    }
    Offset index = Offset.zero();
    byte[] buffer = new byte[name.length()+16];
    // write record format number

    VM_Magic.setIntAtOffset(buffer, index, EVENT_RECORD);       index = index.plus(BYTES_IN_INT);
    // write counter number
    VM_Magic.setIntAtOffset(buffer, index, counter);            index = index.plus(BYTES_IN_INT);
    // write event number
    VM_Magic.setIntAtOffset(buffer, index, id);                 index = index.plus(BYTES_IN_INT);
    // write event name
    index = writeStringToBuffer(buffer, index, name.getBytes());
    // write buffer to file
    writeHeaderFileOutputStream(buffer, index);
  }
  //BEGIN HRM
  /**
   * Write a method record to the trace ((header)) file
   * A method record's format is:
   *  METHOD_RECORD(int), mid(int), length(int), className(byte[]), length(int), methodName(byte[]), length(int), methodDescriptor(byte[])
   *
   * @param mid               method id
   * @param className         name of class (e.g. "Ljava/lang/String;")
   * @param methodName        name of method (e.g. "equals")
   * @param methodDescriptor  descriptor (argument and return types) (e.g. "(Ljava/lang/Object;)Z")
   */
  static private final void writeMethod(int mid, VM_Atom className, VM_Atom methodName, VM_Atom methodDescriptor) {
    if (verbose>=7) {
      VM.sysWrite("VM_HPMs.writeMethod(", mid, ",");
      VM.sysWrite(className);
      VM.sysWrite(", ");
      VM.sysWrite(methodName);
      VM.sysWrite(", ");
      VM.sysWrite(methodDescriptor);
      VM.sysWriteln(")");
    }
    final byte[] classNameBytes = className.toByteArray();
    final byte[] methodNameBytes = methodName.toByteArray();
    final byte[] methodDescriptorBytes = methodDescriptor.toByteArray();

    Offset index = Offset.zero();
    byte[] buffer = new byte[classNameBytes.length+methodNameBytes.length+methodDescriptorBytes.length+5*BYTES_IN_INT];

    // write record format number
    if(verbose>=8){VM.sysWriteln(  "VM_HPMs.writeIntToBuffer() buffer index ",index," value ",METHOD_RECORD);}
    VM_Magic.setIntAtOffset(buffer, index, METHOD_RECORD);      index = index.plus(BYTES_IN_INT);
    // write mid
    if(verbose>=8){VM.sysWriteln(  "VM_HPMs.writeIntToBuffer() buffer index ",index," value ",mid);}
    VM_Magic.setIntAtOffset(buffer, index, mid);                index = index.plus(BYTES_IN_INT);
    // write class name
    index = writeStringToBuffer(buffer, index, classNameBytes);
    // write method name
    index = writeStringToBuffer(buffer, index, methodNameBytes);
    // write method descriptor
    index = writeStringToBuffer(buffer, index, methodDescriptorBytes);
    // write buffer to file
    writeHeaderFileOutputStream(buffer, index);
  }
  //END HRM

  /**
   * Utility method to write a string to a buffer.
   * Assume: string.length + index < buffer.length
   *
   * @param buffer  where to write string
   * @param index   index into buffer where to start writing string
   * @param bytes   array of bytes
   */
  static public Offset writeStringToBuffer(byte[] buffer, Offset index, byte[] bytes)
    throws UninterruptiblePragma 
  {
    if (VM.BuildForHPM && enabled) {
      int length = bytes.length;
      if(verbose>=8) {
        VM.sysWrite(  "VM_HPMs.writeStringToBuffer() buffer index ",index," for length ");
        VM.sysWrite(length);
      }
      VM_Magic.setIntAtOffset(buffer, index, length);           index = index.plus(BYTES_IN_INT);
      for (int i=0; i<length; i++) {
        VM_Magic.setByteAtOffset(buffer, index, bytes[i]);
        index=index.plus(1);
      }
      if(verbose>=8)VM.sysWriteln("    return index ",index);
      return index;
    } else {
      return Offset.zero();
    }
  }

  /**
   * Utility method to write a string to a buffer.
   * Assume: string.length + index < buffer.length
   * CONSTRAINT: for little-endian implementation, swap bytes of string length.
   *
   * @param buffer  where to write string
   * @param index   index into buffer where to start writing string
   * @param bytes   array of bytes
   */
  static public Offset writeStringToBufferSwapBytes(byte[] buffer, Offset index, byte[] bytes)
    throws UninterruptiblePragma 
  {
    if (VM.BuildForHPM && enabled) {
      int length = bytes.length;
      if(verbose>=8) {
        VM.sysWrite(  "VM_HPMs.writeStringToBuffer() buffer index ",index," for length ");
        VM.sysWrite(length);
      }
      VM_Magic.setIntAtOffset(buffer, index, HPM_info.swapByteOrder(length));   index = index.plus(BYTES_IN_INT);
      for (int i=0; i<length; i++) {
        VM_Magic.setByteAtOffset(buffer, index, bytes[i]);
        index=index.plus(1);
      }
      if(verbose>=8)VM.sysWriteln("    return index ",index);
      return index;
    } else {
      return Offset.zero();
    }
  }


  /*
   * Write a buffer of length length to the header FileOutputStream.
   * This method must be synchronized because can execute concurrently.
   * 
   * CONSTRAINT: trace file has been opened.
   *
   * @param buffer bytes to write to file
   * @param length number of bytes to write 
   */
  static private synchronized void writeHeaderFileOutputStream(byte[] buffer, Offset length)
  {
    if(verbose>=8)VM.sysWriteln("VM_HPMs.writeHeaderFileOutputStream(buffer, 0, ",length,")");
    if (length.sLE(Offset.zero())) return;
    if (header_trace_file == null) {    // constraint
      VM.sysWriteln("\n***VM_HPMs.writeHeaderFileOutputStream() header_trace_file == null!  Call VM.shutdown(VM.EXIT_STATUS_HPM_TROUBLE)***");
      VM.shutdown(VM.EXIT_STATUS_HPM_TROUBLE);
    }
    try {
      header_trace_file.write(buffer, 0, length.toInt());
    } catch (IOException e) {
      VM.sysWriteln("***VM_HPMs.writeHeaderFileOutputStream(",length,") throws IOException!***");
      e.printStackTrace(); VM.shutdown(VM.EXIT_STATUS_HPM_TROUBLE);
    }
  }
  //BEGIN HRM
  /**
   * Dump a map from method id to method signature
   * of all known methods into the trace header file.
   */
  static public final void dumpMethods() {
    if (verbose>2) {
      VM.sysWriteln("VM_HPMs.dumpMethods()");
    }
    if (trace) {
      final int numberOfMethodReferenceEntries = VM_MemberReference.getNextId();
      if (verbose>2) VM.sysWriteln("Number of member reference entries: ", numberOfMethodReferenceEntries);
      int n_mids = 0;
      for (int mid=0; mid<numberOfMethodReferenceEntries; mid++) {
        //      if (verbose>6) VM.sysWrite("mid: ", mid);
        VM_MemberReference mr = VM_MemberReference.getMemberRef(mid);
        if (mr!=null) {
          if (mr.isMethodReference()) {
            final VM_Atom className = mr.getType().getName();
            final VM_Atom methodName = mr.getName();
            final VM_Atom methodDescriptor = mr.getDescriptor();            
            if(verbose>=6) { 
              VM.sysWrite(n_mids,": ");VM.sysWrite(mid," "); VM.sysWrite(className); VM.sysWrite(".");
              VM.sysWrite(methodName); VM.sysWrite(" "); VM.sysWrite(methodDescriptor); VM.sysWriteln();
            }
            writeMethod(mid, className, methodName, methodDescriptor);
            n_mids++;
          } else {
            if (verbose>=7) VM.sysWriteln("mid: ",mid," not a method reference");
          }
        } else {
          if (verbose>=7) VM.sysWriteln("mid: ",mid," has an empty VM_MemberReference!");
        }
      }
    }
  }
  //END HRM

  /*
   * Stash away thread names associated with global thread id.
   * Needed to ensure uninterruptible access to thread name.
   */
  //-#if RVM_WITH_HPM
  // size of array of stashed thread names
  static private int ptn_size  = 10;
  // array of stashed thread names
  static private String[] ptn_names = new String[ptn_size];
  static private Object ptn_LOCK = new Object();
  //-#endif
  
  /**
   * Stash away thread name with global thread index.
   * Assume index is unique.
   * @param name thread name
   * @param index global thread index
   */
  static public void putThreadName(String name, int index) {
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM
      if(verbose>=3){ VM.sysWrite("VM_HPMs.putThreadName(", name);VM.sysWrite(", ",index,")\n"); }
      synchronized (ptn_LOCK) {
        while (index >= ptn_size) {
          String[] tmp = new String[ptn_size * 2];
          for (int i=0; i<ptn_size; i++) {
            tmp[i] = ptn_names[i];
          }
          ptn_names = tmp;
          ptn_size *= 2;
        }
        ptn_names[index] = name;
      }
      //-#endif
    }
  }
  /*
   * Get stashed thread name.
   */
  static private String getThreadName(int index) throws UninterruptiblePragma 
  {
    //-#if RVM_WITH_HPM
    if (index > ptn_size) {
      VM.sysWrite("***VM_HPMs.getThreadName(");VM.sysWrite(index);VM.sysWrite(") > size ");
      VM.sysWrite(ptn_size);VM.sysWrite("!***\n");
      VM.sysExit(VM.EXIT_STATUS_HPM_TROUBLE);
    }
    return ptn_names[index];
    //-#endif
  }

  /**
   * Set up callbacks.
   * Manages aggregate HPM values for VM_Threads and VM_Processor.
   * Assume when VM starts up, HPM has started counting.
   * Assume that any calls to System.gc() are done by the application.
   * ASSUMPTION: call back called by sequential application thread.
   * These call backs only work on a uniprocessor.  On a multiprocessor, the reads
   * and writes to stored counter values are not synchronized and the distributed
   * global counter value data structure is not atomic.  For a multiprocessor, collect
   * a trace and then use the TraceFileReader with the -run and -aggregate or -aggregate_by_thread
   * command line options.
   */
  static private HPM_counters aos;
  static private HPM_counters sum;

  static private void setUpCallbacks()
  {
    VM_Callbacks.addAppStartMonitor(new VM_Callbacks.AppStartMonitor() {
        public void notifyAppStart(String app) { 
          if(verbose>=1) VM.sysWriteln("VM_HPMs.notifyAppStart(",app,")");
          if (thread_group) {
            report_MyGroup();
          } else {
            stopUpdateResetReportAndStart(); 
          }
          if(verbose>=1) VM.sysWriteln("VM_HPMs.notifyAppStart(",app,") finished");
        }
    });
    VM_Callbacks.addAppRunStartMonitor(new VM_Callbacks.AppRunStartMonitor() {
        public void notifyAppRunStart(String app, int run) { 
          if(verbose>=1) VM.sysWriteln("VM_HPMs.notifyAppRunStart(",app,",", run,")");
          if (thread_group) {
            report_MyGroup();
          } else {
            stopUpdateResetReportAndStart(); 
          }
          if(verbose>=1) VM.sysWriteln("VM_HPMs.notifyAppRunStart(",app,",", run,") finished");
        }
    });
    VM_Callbacks.addAppRunCompleteMonitor(new VM_Callbacks.AppRunCompleteMonitor() {
        public void notifyAppRunComplete(String app, int run) { 
          if(verbose>=1) VM.sysWriteln("VM_HPMs.notifyAppRunComplete(",app,",", run,")");
          if (thread_group) {
            report_MyGroup();
          } else {
            stopUpdateResetReportAndStart(); 
          }
          if(verbose>=1) VM.sysWriteln("VM_HPMs.notifyAppRunComplete(",app,",", run,") finished");
        }
    });
    VM_Callbacks.addAppCompleteMonitor(new VM_Callbacks.AppCompleteMonitor() {
        public void notifyAppComplete(String app) { 
          if(verbose>=1) VM.sysWriteln("VM_HPMs.notifyAppComplete(",app,")");
          if (thread_group) {
            report_MyGroup();
          } else {
            stopUpdateResetReportAndStart(); 
          }
          if(verbose>=1) VM.sysWriteln("VM_HPMs.notifyAppRunComplete(",app,") finished");
        }
    });
    VM_Callbacks.addExitMonitor(new VM_Callbacks.ExitMonitor() {
        public void notifyExit(int value) { 
          if(verbose>=1) VM.sysWriteln("VM_HPMs.notifyExit(",value,")");
          if (thread_group) {
            stopAndReport_MyGroup();
          } else {
            stopUpdateResetAndReport(); 
          }
          //BEGIN HRM
          if (VM_HardwarePerformanceMonitors.verbose>=1) {
            VM_HardwarePerformanceMonitors.dumpMethods();
          }
          //END HRM
          if(verbose>=1) VM.sysWriteln("VM_HPMs.notifyExit(",value,") finished");
        }
    });
  }

  /**
   * Stop HPM counting.  
   * Update HPM counters of the current thread and processor (conservation of energy).
   * Reset the HPM counters of all the threads and processors.
   * Report the aggregate counts for processors and threads.
   * Start HPM counting.
   * <p> 
   * Using sysCall interface (could use JNI interface).
   */
  static private void stopUpdateResetReportAndStart() throws UninterruptiblePragma {
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM
      if(verbose>=2)VM.sysWrite("VM_HPMs.stopUpdateResetReportAndStart()\n");
      //      stop_Update_reset_Report();
      stop_Update_reset();
      Report();

      start();
      //-#endif
    }
  }

  /**
   * Stop HPM counting.  
   * Update HPM counters of the current thread and processor (conservation of energy).
   * Reset the HPM counters of all the threads and processors.
   * Report the aggregate counts for processors and threads.
   */
  static private void stopUpdateResetAndReport() throws UninterruptiblePragma {
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM
      stop_Update_reset();
      Report();
      //-#endif
    }
  }

  /*
   * Private entry point.
   * Stop HPM counting.
   * Update HPM counters of the current thread and processor (conservation of energy).
   * Reset HPM counter values.
   * Report the aggregate counts for processors and threads.
   */
  static private void stop_Update_reset() throws UninterruptiblePragma {
    // capture MID's
    VM_Thread.captureCallChainCMIDs(false);
    // update hpm counters of current processor and thread.
    VM_Processor.getCurrentProcessor().hpm.updateHPMcounters(VM_Thread.getCurrentThread(), false, false);

    stop();
    reset();
  }

  /**
   * Report on HPM.
   * Assume VM_Processor and VM_Thread HPM_counter data structures are up-to-date.
   * After HPM_counters are reported, they are zeroed (reset).
   * The VM_Thread HPM_counter data is aggregated across all VM_Processors; that is,
   * if a VM_Thread's execution is migrated to different VM_Processors, we can't tell.
   * Use the tracing facility for more details.
   * The aggregate reporting of HPM counter values is unaware of the tracing mechanism.
   */
  static public void Report() throws UninterruptiblePragma {
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM
      if (verbose<0) return;

      //      HPM_counters sum = new HPM_counters();
      VM.sysWriteln("\nDump HPM counter values for virtual processors");
      int n_vp = 0;
      for (int i = 1; i<= VM_Scheduler.numProcessors; i++) {
        VM_Processor processor = VM_Scheduler.processors[i];
        VM.sysWriteln(" Virtual Processor: ",i);
        if (processor.hpm.vp_counters().dump()) {
          n_vp++;
        }
        processor.hpm.vp_counters().accumulate(sum, HPM_info.getNumberOfValues());
        processor.hpm.vp_counters().reset();
      }
      if (VM_Scheduler.numProcessors>1 && n_vp > 1) {
        VM.sysWriteln("Dump aggregate HPM counter values for VirtualProcessors");
        sum.dump();
      }

      VM.sysWriteln("\nDump HPM counter values for threads");
      sum.reset();
      //      HPM_counters aos = new HPM_counters();
      aos.reset();
      int n_aosThreads = 0;
      int n_nonZeroThreads = 0; 
      for (int i = 1, n = VM_Scheduler.hpm_threads.length; i < n; i++) {
        VM_Thread t = VM_Scheduler.hpm_threads[i];
        if (t != null) {
          int global_index = t.getGlobalIndex();
          String thread_name = VM_HardwarePerformanceMonitors.getThreadName(global_index); // t.getClass().getName();
          // dump HPM counter values
          //      synchronized (System.out) {
            VM.sysWrite(" ThreadIndex: ");VM.sysWrite(global_index);VM.sysWrite(" (");
            VM.sysWrite(t.getIndex());VM.sysWrite(") ");VM.sysWrite(thread_name);VM.sysWrite(" ");
            VM.sysWriteln();
            if (t.hpm_counters != null) {
              if (t.hpm_counters.dump()) n_nonZeroThreads++;
              t.hpm_counters.accumulate(sum, HPM_info.getNumberOfValues());
              /*
              if (thread_name.startsWith("VM_ControllerThread") ||
                  thread_name.startsWith("VM_MethodSampleOrganizer")) {
                t.hpm_counters.accumulate(aos, HPM_info.getNumberOfValues());
                n_aosThreads++;
              }
              */
              t.hpm_counters.reset();
            } else {
              if(verbose>=2)
                VM.sysWriteln(" hpm_counters == null!***");
            }
            //    }
        }
      }
      /*
      if (n_aosThreads > 1) {
        //      synchronized (System.out) {
          VM.sysWriteln("\nDump aggregate HPM counter values for AOS threads");
          aos.dump();
          //    }
      }
      */
      if (n_nonZeroThreads > 1) {
        //      synchronized (System.out) {
          VM.sysWriteln("\nDump aggregate HPM counter values for threads");
          sum.dump();
          //    }
      }
      //-#endif
    }
  }

  /**
   * Public interface to report the group hardware events.  Do not stop or reset the counters.
   * The group HPM interface adds the hardware counters for multiple kernel threads.
   * <p>
   * Uses sysCall interface (could use JNI interface)
   */
  public static void report_MyGroup() throws UninterruptiblePragma {
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM
      if(verbose>=2)VM.sysWrite("VM_HardwarePerformanceMonitors.report_MyGroup()\n");
      VM_SysCall.sysHPMprintMyGroup();
      //-#endif
    }
  }
  
  /**
   * Public interface to report the group hardware events.  Also, stops the counters for the threads
   * in the group. The group HPM interface adds the hardware counters for multiple kernel threads.
   * <p>
   * Uses sysCall interface (could use JNI interface)
   */
  public static void stopAndReport_MyGroup() throws UninterruptiblePragma {
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM
      if(verbose>=2)VM.sysWrite("VM_HardwarePerformanceMonitors.stopAndReport_MyGroup()\n");
      VM_SysCall.sysHPMstopMyGroup();
      VM_SysCall.sysHPMprintMyGroup();
      //-#endif
    }
  }

  /*
   * Private entry point.
   * Start HPM counting.
   * Constraint: VM.BuildForHPM and enabled are set and built with RVM_WITH_HPM.
   */
  static private void start() throws UninterruptiblePragma
  {
    //-#if RVM_WITH_HPM
    VM_SysCall.sysHPMstartMyThread();
    //-#endif
  }
  /*
   * Private entry point.
   * Stop HPM counting.
   * Constraint: VM.BuildForHPM and enabled are set and built with RVM_WITH_HPM.
   */
  static private void stop() throws UninterruptiblePragma
  {
    //-#if RVM_WITH_HPM
    VM_SysCall.sysHPMstopMyThread();
    //-#endif
  }
  /*
   * Private entry point.
   * Reset HPM counters.
   * Constraint: VM.BuildForHPM and enabled are set and built with RVM_WITH_HPM.
   */
  static private void reset() throws UninterruptiblePragma
  {
    //-#if RVM_WITH_HPM
    VM_SysCall.sysHPMresetMyThread();
    //-#endif
  }

  /**
   * Called from VM_Thread.yieldpoint if a timer-interrupt has fired
   */
  public static void takeHPMTimerSample(boolean threadSwitch) throws UninterruptiblePragma {
    if (sample || threadSwitch) {
      // sample HPM counter values at every interrupt or a thread switch.
      if (VM.BuildForHPM && safe && !thread_group) {
        VM_Thread.captureCallChainCMIDs(true);
        VM_Thread myThread = VM_Thread.getCurrentThread();
        VM_Processor.getCurrentProcessor().hpm.updateHPMcounters(myThread, true, threadSwitch);
      }
    }

    if (!threadSwitch) {
      // set start time of thread
      VM_Thread.getCurrentThread().startOfWallTime = VM_Magic.getTimeBase();
    }
  }

}
