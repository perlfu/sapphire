/*
 * (C) Copyright IBM Corp 2003, 2004
 */
//$Id$
package java.lang;

import java.io.File;
import java.util.Properties;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.VM_ClassLoader;
import com.ibm.JikesRVM.memoryManagers.mmInterface.*;

/**
 * Jikes RVM implementation of GNU Classpath's java.lang.VMRuntime.
 * See reference implementation for javadoc.
 *
 * @author Julian Dolby
 * @author Dave Grove
 */
final class VMRuntime {

  private static boolean runFinalizersOnExit = false;
  
  private VMRuntime() { }

  static int availableProcessors() {
    return VM_Scheduler.numProcessors;
  }
    
  static long freeMemory() {
    return MM_Interface.freeMemory();
  }
    
  static long totalMemory() {
    return MM_Interface.totalMemory();
  }
    
  static long maxMemory() {
    return MM_Interface.maxMemory();
  }
    
  static void gc() {
    MM_Interface.gc();
  }
    
  static void runFinalization() {
    // TODO: talk to Steve B & Perry and figure out what to do.
    // as this is a hint, we can correctly ignore it.
    // However, there might be something else we should do.
  }
    
  static void runFinalizationForExit() {
    if (runFinalizersOnExit) {
      // TODO: talk to Steve B & Perry and figure out what to do.
      throw new VM_UnimplementedError();
    }
  }
    
  static void traceInstructions(boolean on) {
    // VMs are free to ignore this...
  }
    
  static void traceMethodCalls(boolean on) {
    // VMs are free to ignore this...
  }

  static void runFinalizersOnExit(boolean value) {
    runFinalizersOnExit = value;
  }

  static void exit(int status) {
    VM.sysExit(status);
  }    

  static int nativeLoad(String libName) {
    return VM_DynamicLibrary.load(libName);
  }

  static String nativeGetLibname(String pathname, String libname) {
    String libSuffix;
    if (VM.BuildForLinux) {
      libSuffix = ".so";
    } else if (VM.BuildForOsx) {
      libSuffix = ".jnilib";
    } else {
      libSuffix = ".a";
    }
    if (pathname != null && !("".equals(pathname)))
      return pathname + File.separator + "lib" + libname + libSuffix;
    else
      return "lib" + libname + libSuffix;
  }

  static Process exec(String[] cmd, String[] env, File dir) {
    String dirPath = (dir != null) ? dir.getPath() : null;
    return new VM_Process(cmd[0], cmd, env, dirPath);
  }

  /** The following are set by the "runrvm" script before we go into the C
   * boot image runner, by passing them as command-line args with the -D flag:
   *
   * os.name, os.arch, os.version
   * user.name, user.home, user.dir
   * gnu.classpath.vm.shortname, gnu.classpath.home.url, 
   * java.home,
   * rvm.root, rvm.build
   *
   * We can look at them here via VM_CommandLineArgs.getEnvironmentArg().
   *
   * They will be automatically set for us by
   * VM_CommandLineArgs.lateProcessCommandLineArguments() if we do not handle
   * them here.  That won't occur until the VM is fully booted.  That's too
   * late for some classes, such as java.util.TimeZone, which will already be
   * initialized.
   */
  static void insertSystemProperties(Properties p) {
    p.put("java.version", "1.3.0"); // change to 1.4.2 ?
    p.put("java.vendor", "Jikes RVM Project");
    p.put("java.vm.vendor", "Jikes RVM Project");
    p.put("java.vendor.url", "http://oss.software.ibm.com");
    
    p.put("java.specification.name", "Java Platform API Specification");
    p.put("java.specification.vendor", "Sun Microsystems Inc.");
    p.put("java.specification.version", "1.3"); // Change to 1.4?

    p.put("java.vm.specification.name", "Java Virtual Machine Specification");
    p.put("java.vm.specification.vendor", "Sun Microsystems Inc.");
    p.put("java.vm.specification.version", "1.0");

    p.put("java.specification.version", "1.3"); // change to 1.4.0 ?
    p.put("java.specification.version", "1.3"); // change to 1.4.0 ?

    p.put("java.class.version", "48.0");

    p.put("file.separator", "/");
    p.put("path.separator", ":");
    p.put("line.separator", "\n");
        
    p.put("java.compiler", "JikesRVM");
    p.put("java.vm.version", "1.3.0");
    p.put("java.vm.name", "JikesRVM");
    p.put("file.encoding", "8859_1");
    p.put("java.io.tmpdir", "/tmp");

    String s;
    s = VM_ClassLoader.getVmRepositories();
    p.put("java.boot.class.path", s);
    /* sun.boot.class.path is not necessary, yes, but possibly useful; Steve
     * Augart has seen at least one piece of code on the web that reads
     * this. */ 
    p.put("sun.boot.class.path", s);
    

    /* user.timezone

       I (Steve Augart) started a discussion about this on classpath@gnu.org
       on 23 March 2003.  Summary: we define user.timezone specifically in
       order to pass that information to java.util.TimeZone, which initializes
       later on in the boot process.  It does not seem to be required by the
       spec, and it's the empty string in Blackdown 1.4.2.

       We have to do this here, because otherwise it wouldn't be set until
       VM_CommandLineArgs.lateProcessCommandLineArguments().  That won't occur
       until the VM is fully booted; too late for java.util.TimeZone, which
       reads this value when it runs its initializer.
    */
    s = VM_CommandLineArgs.getEnvironmentArg("user.timezone");
    s = (s == null ) ? "" : s;  // Maybe it's silly to set it to the empty
                                // string.  Well, this should never succeed
                                // anyway, since we're always called by
                                // runrvm.  
    p.put("user.timezone", s);

    /* java.library.path
       Now the library path.  This is the path used for system
       dynamically-loaded libraries, the things that end in ".so" on Linux. */
    insertLibraryPath(p);

    /* What should we do about java.ext.dirs?  
       XXX TODO

       java.ext.dirs is allegedly mandatory, according to the API docs shipped
       with the Sun 1.4.2 JDK.

       Ridiculous, since we don't search it for anything, and since if the
       user were to set it it wouldn't do anything anyway.   We keep all of
       the extensions stored with the other bits of the JDK.   So, this would
       really need to be prepended to the list of VM classes, wouldn't it?  Or
       appended, perhaps? */
    s = VM_CommandLineArgs.getEnvironmentArg("java.ext.dirs");
    if (s == null) {
      s = "";
    } else {
      VM.sysWrite("Jikes RVM: Warning: You have explicitly set java.ext.dirs; that will not do anything under Jikes RVM");
    }
    
    s = (s == null ) ? "" : s;
    p.put("java.ext.dirs", s);
    

    /* We also set java.class.path in setApplicationRepositories().  
       We don't ever explicitly set that list of repositories to the
       default, ".", so we need to set it here if it hasn't been done
       already. */
    
    s = VM_CommandLineArgs.getEnvironmentArg("java.class.path");
    p.put("java.class.path", s == null ? "." : s);


    /* Now the rest of the special ones that we set on the command line.   Do
     * this just in case they turn out to be needed earlier on in this
     * process. */
    final String[] clProps = new String[] {"os.name", "os.arch", "os.version", "user.name", "user.home", "user.dir", "gnu.classpath.vm.shortname", "gnu.classpath.home.url", "java.home", "rvm.root", "rvm.build"};
    
    for (int i = 0; i < clProps.length; ++i ) {
      final String prop = clProps[i];
      s = VM_CommandLineArgs.getEnvironmentArg(prop);
      if (s != null) {
        p.put(prop, s);
      }
    }

    /* Tell GNU Classpath that we need the portable-native-sync
     * implementation.   This is not yet in Classpath CVS, but will be
     * soon. --Steve Augart, 19 June 2004 */ 
    p.put("gnu.classpath.awt.gtk.portable.native.sync", "true");

  }
    
  /** Set java.library.path.

      I wish I knew where to check in the source code to confirm that this
      is, in fact, the process we actually follow.  I do not understand this
      code.  I do not understand why we are adding something to
      java.library.path.  --Steve Augart, 3/23/2004 XXX
  */
  private static void insertLibraryPath(Properties p) {
    String jlp = VM_CommandLineArgs.getEnvironmentArg("java.library.path");
    String snp = VM_CommandLineArgs.getEnvironmentArg("rvm.build");
    if (jlp == null) jlp = ".";
    p.put("java.library.path", snp + p.get("path.separator") +jlp);
  }
  
}
