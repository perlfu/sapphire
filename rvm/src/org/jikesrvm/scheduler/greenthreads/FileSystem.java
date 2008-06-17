/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.scheduler.greenthreads;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import org.jikesrvm.VM;
import org.jikesrvm.Callbacks;
import org.jikesrvm.runtime.Magic;
import static org.jikesrvm.runtime.SysCall.sysCall;
import org.jikesrvm.runtime.Time;
import org.jikesrvm.runtime.TimeoutException;
import org.jikesrvm.util.StringUtilities;
import org.vmmagic.pragma.Inline;

/**
 * Interface to filesystem of underlying operating system.
 * These methods use nonblocking I/O for reads and writes and, if necessary,
 * use the Processor's IO queue (via the Wait.ioWaitRead()
 * and Wait.ioWaitWrite() methods) to suspend the calling thread
 * until the underlying file descriptor is ready.
 *
 * <p> Originally, some of these methods returned a special value
 * to indicate that the operation would block.  Because all<sup>*</sup>
 * file descriptors are nonblocking now, this is no longer necessary.
 *
 * <p> <sup>*</sup> due to Unix brain damage, we can not actually
 * make every file descriptor nonblocking.  The problem is for stdin,
 * stdout, and stderr, which we must sometimes share with other
 * processes.  The <code>O_NONBLOCK</code> flag is a sticky property
 * of the <em>file</em>, not the file descriptor.  Hence, every
 * file descriptor sharing the same file (even those copied with
 * <code>dup()</code> or <code>fork()</code>) shares the blocking
 * mode.  Thus, if we make stdin nonblocking, and stdin is a tty shared
 * with another process, we will hose the other process.
 *
 * <p> The current "solution" for this problem is to special case
 * file descriptors 0, 1, and 2 for reads and writes.  When they are not
 * connected to a tty, we assume they're private, and set them to nonblocking.
 * If they are connected to a tty, then we kluge our read and write
 * functions to perform a preemptive IO wait, which hopefully prevents
 * them from blocking in the OS.
 */
public class FileSystem {

  // options for open()
  public static final int OPEN_READ = 0; // open for read/only  access
  public static final int OPEN_WRITE = 1; // open for read/write access, create if doesn't already exist,
  // truncate if already exists
  public static final int OPEN_MODIFY = 2; // open for read/write access, create if doesn't already exist
  public static final int OPEN_APPEND = 3; // open for read/write access, create if doesn't already exist, append writes

  // options for seek()
  public static final int SEEK_SET = 0;    // set i/o position to start of file plus "offset"
  public static final int SEEK_CUR = 1;    // set i/o position to current position plus "offset"
  public static final int SEEK_END = 2;    // set i/o position to end of file plus "offset"

  // options for stat()
  public static final int STAT_EXISTS = 0;
  public static final int STAT_IS_FILE = 1;
  public static final int STAT_IS_DIRECTORY = 2;
  public static final int STAT_IS_READABLE = 3;
  public static final int STAT_IS_WRITABLE = 4;
  public static final int STAT_LAST_MODIFIED = 5;
  public static final int STAT_LENGTH = 6;

  // options for access()
  public static final int ACCESS_F_OK = 00;
  public static final int ACCESS_R_OK = 04;
  public static final int ACCESS_W_OK = 02;
  public static final int ACCESS_X_OK = 01;

  /**
   * Keep track of whether or not we were able to make
   * the stdin, stdout, and stderr file descriptors nonblocking.
   * By default, we assume that these descriptors ARE blocking,
   * and we kluge around this problem when we read or write them.
   */
  private static boolean[] standardFdIsNonblocking = new boolean[3];

  /**
   * Get file status.
   * @param fileName file name
   * @param kind     kind of info desired (one of STAT_XXX, above)
   * @return desired info (-1 -> error)
   *    The boolean ones return 0 in case of non-true, 1 in case of
   *    true status.
   */
  public static int stat(String fileName, int kind) {
    // convert file name from unicode to filesystem character set
    // (assume file name is ascii, for now)
    byte[] asciiName = StringUtilities.stringToBytesNullTerminated(fileName);
    int rc = sysCall.sysStat(asciiName, kind);
    if (VM.TraceFileSystem) VM.sysWrite("FileSystem.stat: name=" + fileName + " kind=" + kind + " rc=" + rc + "\n");
    return rc;
  }

  /**
   * Get user's perms for a file.
   * @param fileName file name
   * @param kind     kind of access perm(s) to check for (ACCESS_W_OK,...)
   * @return 0 if access ok (-1 -> error)
   */
  public static int access(String fileName, int kind) {
    // convert file name from unicode to filesystem character set
    // (assume file name is ascii, for now)
    byte[] asciiName = StringUtilities.stringToBytesNullTerminated(fileName);

    int rc = sysCall.sysAccess(asciiName, kind);

    if (VM.TraceFileSystem) {
      VM.sysWrite("FileSystem.access: name=" + fileName + " kind=" + kind + " rc=" + rc + "\n");
    }
    return rc;
  }

  /**
   * Is the given fd returned from an ioWaitRead() or ioWaitWrite()
   * ready?
   */
  @Inline
  private static boolean isFdReady(int fd) {
    return (fd & ThreadIOConstants.FD_READY_BIT) != 0;
  }

  /**
   * Hack to cope with the fact that we sometimes cannot
   * make the standard file descriptors nonblocking.
   * If called on the stdin file descriptor when we couldn't
   * set it to nonblocking, it will try to block the calling
   * thread until the file descriptor is ready (so the read
   * will hopefully complete without blocking).
   *
   * @return true if the wait was successful and the file descriptor
   *    is ready, or false if an error occurred (and the read should
   *    be avoided)
   */
  @Inline
  private static boolean blockingReadHack(int fd) {
    if (fd >= 3 || standardFdIsNonblocking[fd]) {
      return true;
    }

    ThreadIOWaitData waitData = Wait.ioWaitRead(fd);
    return isFdReady(waitData.readFds[0]);
  }

  /**
   * Hack to cope with the fact that we sometimes cannot
   * make the standard file descriptors nonblocking.
   * If called on the stdout or stderr file descriptors when we couldn't
   * set them to nonblocking, it will try to block the calling
   * thread until the file descriptor is ready (so the write
   * will hopefully complete without blocking).
   *
   * @return true if the wait was successful and the file descriptor
   *    is ready, or false if an error occurred (and the write should
   *    be avoided)
   */
  @Inline
  private static boolean blockingWriteHack(int fd) {
    if (fd >= 3 || standardFdIsNonblocking[fd]) {
      return true;
    }

    ThreadIOWaitData waitData = Wait.ioWaitWrite(fd);
    return isFdReady(waitData.writeFds[0]);
  }

  /**
   * Read single byte from file.
   * FIXME: should throw an IOException to indicate an error?
   *
   * @param fd file descriptor
   * @return byte that was read (< -1: i/o error, -1: eof, >= 0: data)
   */
  public static int readByte(int fd) {
    if (!blockingReadHack(fd)) {
      return -2;
    }

    // See readBytes() method for an explanation of how the read loop works.

    for (; ;) {
      int b = sysCall.sysReadByte(fd);
      if (b >= -1) {
        // Either a valid read, or we reached EOF
        return b;
      } else if (b == -2) {
        // Operation would have blocked
        ThreadIOWaitData waitData = Wait.ioWaitRead(fd);
        if (!isFdReady(waitData.readFds[0])) {
          // Hmm, the wait returned, but the fd is not ready.
          // Assume an error was detected (such as the fd becoming invalid).
          return -2;
        }
      } else {
        // Read returned with a genuine error
        return -2;
      }
    }
  }

  /**
   * Write single byte to file.
   * FIXME: should throw an IOException to indicate an error?
   *
   * @param fd file descriptor
   * @param b  byte to be written
   * @return  -1: i/o error
   */
  public static int writeByte(int fd, int b) {
    if (!blockingWriteHack(fd)) {
      return -1;
    }

    // See writeBytes() for an explanation of how the write loop works

    for (; ;) {
      int rc = sysCall.sysWriteByte(fd, b);
      if (rc == 0) {
        return 0; // success
      } else if (rc == -2) {
        // Write would have blocked.
        ThreadIOWaitData waitData = Wait.ioWaitWrite(fd);
        if (!isFdReady(waitData.writeFds[0])) {
          // Looks like an error occurred.
          return -1;
        }
        // Fd looks like it's ready, so retry the write
      } else {
        // The write returned with an error.
        return -1;
      }
    }
  }

  /**
   * Version of <code>readBytes()</code> which does not support timeouts.
   * Will block indefinitely until data can be read.
   * @see #readBytes(int,byte[],int,int,double)
   */
  public static int readBytes(int fd, byte[] buf, int off, int cnt) {
    try {
      return readBytes(fd, buf, off, cnt, ThreadEventConstants.WAIT_INFINITE);
    } catch (TimeoutException e) {
      if (VM.VerifyAssertions) VM._assert(false); // impossible
      return -2;
    }
  }

  /**
   * Read multiple bytes from file or socket.
   * FIXME: should throw an IOException to indicate an error?
   *
   * @param fd file or socket descriptor
   * @param buf buffer to be filled
   * @param off position in buffer
   * @param cnt number of bytes to read
   * @param totalWaitTime number of seconds caller is willing to wait
   * @return number of bytes read (-2: error)
   * @throws TimeoutException if the read times out
   */
  public static int readBytes(int fd, byte[] buf, int off, int cnt, double totalWaitTime) throws TimeoutException {

    if (off < 0) {
      throw new IndexOutOfBoundsException();
    }

    // trim request to fit array
    // note: this behavior is the way the JDK does it (as of version 1.1.3)
    // whereas the language spec says to throw IndexOutOfBounds exception...
    //
    if (off + cnt > buf.length) {
      cnt = buf.length - off;
    }

    // The canonical read loop.  Try the read repeatedly until either
    //   - it succeeds,
    //   - it returns EOF, or
    //   - it returns with an error.
    // If the read fails because it would have blocked, then
    // put this thread on the IO queue, then try again if it
    // looks like the fd is ready.

    boolean hasTimeout = (totalWaitTime >= 0.0);
    double lastWaitTime = hasTimeout ? now() : 0.0;

    if (!blockingReadHack(fd)) {
      return -2;
    }

    int read = 0;
    for (; ;) {
      int rc = sysCall.sysReadBytes(fd, Magic.objectAsAddress(buf).plus(off), cnt);

      if (rc == 0) {
        // EOF
        return read;
      } else if (rc > 0) {
        // Read succeeded, perhaps partially
        read += rc;
        off += rc;
        cnt -= rc;
        if (cnt == 0) return read;
        // did not get everything, let's try again
      } else if (rc == -1) {
        // last read would have blocked

        // perhaps we have read some stuff already, if so, return just that
        if (read != 0) {
          return read;
        }

        // Put thread on IO wait queue
        if (VM.VerifyAssertions) VM._assert(!hasTimeout || totalWaitTime >= 0.0);
        ThreadIOWaitData waitData = Wait.ioWaitRead(fd, totalWaitTime);

        // Did the wait time out?
        if (waitData.isTimedOut()) {
          throw new TimeoutException("read timed out");
        }

        // Did the file descriptor become ready?
        if (!isFdReady(waitData.readFds[0])) {
          // Fd not ready; presumably an error was detected while on the IO queue
          return -2;
        } else {
          // Fd appears to be ready, so update the wait time (if necessary)
          // and try the read again.
          if (hasTimeout) {
            double now = now();
            totalWaitTime -= (now - lastWaitTime);
            if (totalWaitTime < 0.0) {
              throw new TimeoutException("read timed out");
            }
            lastWaitTime = now;
          }
        }
      } else {
        // Read returned an error
        return -2;
      }
    }
  }

  private static double now() {
    return ((double) Time.currentTimeMillis()) / 1000;
  }

  /**
   * Write multiple bytes to file or socket.
   * FIXME: should throw an IOException to indicate an error?
   *
   * @param fd file or socket descriptor
   * @param buf buffer to be written
   * @param off position in buffer
   * @param cnt number of bytes to write
   * @return number of bytes written (-2: error)
   */
  public static int writeBytes(int fd, byte[] buf, int off, int cnt) {
    if (cnt == 0) return 0;

    if (off < 0) {
      throw new IndexOutOfBoundsException();
    }

    // trim request to fit array
    // note: this behavior is the way the JDK does it (as of version 1.1.3)
    // whereas the language spec says to throw IndexOutOfBounds exception...
    //
    if (off + cnt > buf.length) {
      cnt = buf.length - off;
    }

    // The canonical write loop.  Try the write repeatedly until
    //   - it succeeds, or
    //   - it returns an error
    // If the write would have blocked, put this thread on the
    // IO queue, then try again if it looks like the fd is ready.

    if (!blockingWriteHack(fd)) {
      return -2;
    }

    int written = 0;
    for (; ;) {
      int rc = sysCall.sysWriteBytes(fd, Magic.objectAsAddress(buf).plus(off), cnt);
      if (rc >= 0) {
        // Write succeeded, perhaps partially
        written += rc;
        off += rc;
        cnt -= rc;
        if (cnt == 0) return written;
      } else if (rc == -1) {
        // Write would have blocked
        ThreadIOWaitData waitData = Wait.ioWaitWrite(fd);
        if (!isFdReady(waitData.writeFds[0])) {
          // Fd is not ready, so presumably an error occurred while on IO queue
          return -2;
        }
        // Fd apprears to be ready, so try write again
      } else {
        // Write returned with an error
        return -2;
      }
    }
  }

  public static boolean sync(int fd) {
    return sysCall.sysSyncFile(fd) == 0;
  }

  public static int bytesAvailable(int fd) {
    return sysCall.sysBytesAvailable(fd);
  }

  /**
   * File descriptor registration hook.
   * All (valid) FileDescriptor objects created in the system should
   * pass their underlying OS file descriptors to this method,
   * so we can set them to nonblocking mode.
   *
   * <p>No file descriptors should be created before the
   * VM is booted sufficiently that thread switching is enabled.
   * The reason is that all Java streams use nonblocking file descriptors,
   * and we use the VM rather than the OS to block threads until their
   * streams are ready.
   *
   * @param fd the file descriptor
   * @param shared true if the file descriptor will be shared,
   *   false otherwise; we set the close-on-exec flag for non-shared fds,
   *   to prevent child processes from accessing them
   */
  public static void onCreateFileDescriptor(int fd, boolean shared) {
    // The most likely reason for this assertion to be triggered is that
    // some code caused class loading before Scheduler.boot()
    // finished initializing the virtual processors.  It should generally
    // be possible to move such code later in the initialization sequence.
    if (VM.VerifyAssertions) {
      VM._assert(GreenScheduler.allProcessorsInitialized, "fd used before system is fully booted\n");
    }

    int rc;

    // Set the file descriptor to be nonblocking.
    rc = sysCall.sysNetSocketNoBlock(fd, 1);
    if (rc < 0) {
      VM.sysWrite("VM: warning: could not set file descriptor " + fd + " to nonblocking\n");
    }

    // Note: it is conceivable that file descriptor 0, 1, or 2 could
    // get passed to this method.  For example, a program could close
    // stdin, then open a new FileInputStream, which might get assigned
    // fd 0.  Unlikely, but possible.  If this does happen, it's OK for
    // us to make the file descriptor nonblocking, because we don't share
    // such file descriptors with other processes.
    if (fd < 3) {
      standardFdIsNonblocking[fd] = (rc == 0);
    }

    // If file descriptor will not be shared, set close-on-exec flag
    if (!shared) {
      rc = sysCall.sysSetFdCloseOnExec(fd);
      if (rc < 0) {
        VM.sysWrite("VM: warning: could not set close-on-exec flag " + "for fd " + fd);
      }
    }
  }

  /**
   * Called from VM.boot to set up java.lang.System.in, java.lang.System.out,
   * and java.lang.System.err
   */
  public static void initializeStandardStreams() {
    FileInputStream fdIn = new FileInputStream(FileDescriptor.in);
    FileOutputStream fdOut = new FileOutputStream(FileDescriptor.out);
    FileOutputStream fdErr = new FileOutputStream(FileDescriptor.err);
    System.setIn(new BufferedInputStream(fdIn));
    System.setOut(new PrintStream(new BufferedOutputStream(fdOut, 128), true));
    System.setErr(new PrintStream(new BufferedOutputStream(fdErr, 128), true));
    Callbacks.addExitMonitor(new Callbacks.ExitMonitor() {
      public void notifyExit(int value) {
        try {
          System.err.flush();
          System.out.flush();
        } catch (Throwable e) {
          VM.sysWriteln("vm: error flushing stdout, stderr");
        }
      }
    });
  }
}

