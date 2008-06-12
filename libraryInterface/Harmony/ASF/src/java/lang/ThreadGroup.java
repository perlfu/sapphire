/* 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package java.lang;

/**
 * An implementation of this class is provided, but the documented constructors
 * are used by the vm specific implementation to create the required "system"
 * and "main" ThreadGroups. The documented methods are used by java.lang.Thread
 * to add and remove Threads from their ThreadGroups.
 * 
 * ThreadGroups are containers of Threads and ThreadGroups, therefore providing
 * a tree-like structure to organize Threads. The root ThreadGroup name is
 * "system" and it has no parent ThreadGroup. All other ThreadGroups have
 * exactly one parent ThreadGroup. All Threads belong to exactly one
 * ThreadGroup.
 * 
 * @see Thread
 * @see SecurityManager
 */
public class ThreadGroup implements Thread.UncaughtExceptionHandler {

    // Name of this ThreadGroup
    private String name;

    // Maximum priority for Threads inside this ThreadGroup
    private int maxPriority = Thread.MAX_PRIORITY;

    // The ThreadGroup to which this ThreadGroup belongs
    ThreadGroup parent;

    int numThreads;

    // The Threads this ThreadGroup contains
    private Thread[] childrenThreads = new Thread[5];

    // The number of children groups
    int numGroups;

    // The ThreadGroups this ThreadGroup contains
    private ThreadGroup[] childrenGroups = new ThreadGroup[3];

    // Locked when using the childrenGroups field
    private class ChildrenGroupsLock {}
    private Object childrenGroupsLock = new ChildrenGroupsLock();

    // Locked when using the childrenThreads field
    private class ChildrenThreadsLock {}
    private Object childrenThreadsLock = new ChildrenThreadsLock();

    // Whether this ThreadGroup is a daemon ThreadGroup or not
    private boolean isDaemon;

    // Whether this ThreadGroup has already been destroyed or not
    private boolean isDestroyed;

    /**
     * Used by the JVM to create the "system" ThreadGroup. Construct a
     * ThreadGroup instance, and assign the name "system".
     */
    private ThreadGroup() {
        name = "system";
    }

    /**
     * Constructs a new ThreadGroup with the name provided. The new ThreadGroup
     * will be child of the ThreadGroup to which the
     * <code>Thread.currentThread()</code> belongs.
     * 
     * @param name Name for the ThreadGroup being created
     * 
     * @throws SecurityException if <code>checkAccess()</code> for the parent
     *         group fails with a SecurityException
     * 
     * @see java.lang.Thread#currentThread
     */

    public ThreadGroup(String name) {
        this(Thread.currentThread().getThreadGroup(), name);
    }

    /**
     * Constructs a new ThreadGroup with the name provided, as child of the
     * ThreadGroup <code>parent</code>
     * 
     * @param parent Parent ThreadGroup
     * @param name Name for the ThreadGroup being created
     * 
     * @throws NullPointerException if <code>parent</code> is
     *         <code>null</code>
     * @throws SecurityException if <code>checkAccess()</code> for the parent
     *         group fails with a SecurityException
     * @throws IllegalThreadStateException if <code>parent</code> has been
     *         destroyed already
     */
    public ThreadGroup(ThreadGroup parent, String name) {
        super();
        if (Thread.currentThread() != null) {
            // If parent is null we must throw NullPointerException, but that
            // will be done "for free" with the message send below
            parent.checkAccess();
        }

        this.name = name;
        this.setParent(parent);
        if (parent != null) {
            this.setMaxPriority(parent.getMaxPriority());
            if (parent.isDaemon()) {
                this.setDaemon(true);
            }
        }
    }

    /**
     * Initialize the "main" ThreadGroup
     */
    ThreadGroup(ThreadGroup parent) {
        this.name = "main";
        this.setParent(parent);
    }

    /**
     * Returns the number of Threads which are children of the receiver,
     * directly or indirectly.
     * 
     * @return Number of children Threads
     */

    public int activeCount() {
        int count = numThreads;
        // Lock this subpart of the tree as we walk
        synchronized (this.childrenGroupsLock) {
            for (int i = 0; i < numGroups; i++) {
                count += this.childrenGroups[i].activeCount();
            }
        }
        return count;
    }

    /**
     * Returns the number of ThreadGroups which are children of the receiver,
     * directly or indirectly.
     * 
     * @return Number of children ThreadGroups
     */
    public int activeGroupCount() {
        int count = 0;
        // Lock this subpart of the tree as we walk
        synchronized (this.childrenGroupsLock) {
            for (int i = 0; i < numGroups; i++) {
                // One for this group & the subgroups
                count += 1 + this.childrenGroups[i].activeGroupCount();
            }
        }
        return count;
    }

    /**
     * Adds a Thread to the receiver. This should only be visible to class
     * java.lang.Thread, and should only be called when a new Thread is created
     * and initialized by the constructor.
     * 
     * @param thread Thread to add to the receiver
     * 
     * @throws IllegalThreadStateException if the receiver has been destroyed
     *         already
     * 
     * @see #remove(java.lang.Thread)
     */
    final void add(Thread thread) throws IllegalThreadStateException {
        synchronized (this.childrenThreadsLock) {
            if (!isDestroyed) {
                if (childrenThreads.length == numThreads) {
                    Thread[] newThreads = new Thread[childrenThreads.length * 2];
                    System.arraycopy(childrenThreads, 0, newThreads, 0, numThreads);
                    newThreads[numThreads++] = thread;
                    childrenThreads = newThreads;
                } else {
                    childrenThreads[numThreads++] = thread;
                }
            } else {
                throw new IllegalThreadStateException();
            }
        }
    }

    /**
     * Adds a ThreadGroup to the receiver.
     * 
     * @param g ThreadGroup to add to the receiver
     * 
     * @throws IllegalThreadStateException if the receiver has been destroyed
     *         already
     */
    private void add(ThreadGroup g) throws IllegalThreadStateException {
        synchronized (this.childrenGroupsLock) {
            if (!isDestroyed) {
                if (childrenGroups.length == numGroups) {
                    ThreadGroup[] newGroups = new ThreadGroup[childrenGroups.length * 2];
                    System.arraycopy(childrenGroups, 0, newGroups, 0, numGroups);
                    newGroups[numGroups++] = g;
                    childrenGroups = newGroups;
                } else {
                    childrenGroups[numGroups++] = g;
                }
            } else {
                throw new IllegalThreadStateException();
            }
        }
    }

    /**
     * The definition of this method depends on the deprecated method
     * <code>suspend()</code>. The behavior of this call was never specified.
     * 
     * @param b Used to control low memory implicit suspension
     * 
     * @deprecated Required deprecated method suspend().
     */
    @Deprecated
    public boolean allowThreadSuspension(boolean b) {
        // Does not apply to this VM, no-op
        return true;
    }

    /**
     * If there is a SecurityManager installed, call <code>checkAccess</code>
     * in it passing the receiver as parameter, otherwise do nothing.
     */
    public final void checkAccess() {
        // Forwards the message to the SecurityManager (if there's one) passing
        // the receiver as parameter
        SecurityManager currentManager = System.getSecurityManager();
        if (currentManager != null) {
            currentManager.checkAccess(this);
        }
    }

    /**
     * Destroys the receiver and recursively all its subgroups. It is only legal
     * to destroy a ThreadGroup that has no Threads. Any daemon ThreadGroup is
     * destroyed automatically when it becomes empty (no Threads and no
     * ThreadGroups in it).
     * 
     * @throws IllegalThreadStateException if the receiver or any of its
     *         subgroups has been destroyed already
     * @throws SecurityException if <code>this.checkAccess()</code> fails with
     *         a SecurityException
     */

    public final void destroy() {
        checkAccess();

        // Lock this subpart of the tree as we walk
        synchronized (this.childrenThreadsLock) {
            synchronized (this.childrenGroupsLock) {
                int toDestroy = numGroups;
                // Call recursively for subgroups
                for (int i = 0; i < toDestroy; i++) {
                    // We always get the first element - remember, when the
                    // child dies it removes itself from our collection. See
                    // below.
                    this.childrenGroups[0].destroy();
                }

                if (parent != null) {
                    parent.remove(this);
                }

                // Now that the ThreadGroup is really destroyed it can be tagged
                // as so
                this.isDestroyed = true;
            }
        }
    }

    /*
     * Auxiliary method that destroys the receiver and recursively all its
     * subgroups if the receiver is a daemon ThreadGroup.
     * 
     * @see #destroy
     * @see #setDaemon
     * @see #isDaemon
     */
    private void destroyIfEmptyDaemon() {
        // Has to be non-destroyed daemon to make sense
        synchronized (this.childrenThreadsLock) {
            if (isDaemon && !isDestroyed && numThreads == 0) {
                synchronized (this.childrenGroupsLock) {
                    if (numGroups == 0) {
                        destroy();
                    }
                }
            }
        }
    }

    /**
     * Copies an array with all Threads which are children of the receiver
     * (directly or indirectly) into the array <code>threads</code> passed as
     * parameters. If the array passed as parameter is too small no exception is
     * thrown - the extra elements are simply not copied.
     * 
     * @param threads Thread array into which the Threads will be copied
     * @return How many Threads were copied over
     * 
     */
    public int enumerate(Thread[] threads) {
        return enumerate(threads, true);
    }

    /**
     * Copies an array with all Threads which are children of the receiver into
     * the array <code>threads</code> passed as parameter. Children Threads of
     * subgroups are recursively copied as well if parameter
     * <code>recurse</code> is <code>true</code>.
     * 
     * If the array passed as parameter is too small no exception is thrown -
     * the extra elements are simply not copied.
     * 
     * @param threads array into which the Threads will be copied
     * @param recurse Indicates whether Threads in subgroups should be
     *        recursively copied as well or not
     * @return How many Threads were copied over
     * 
     */
    public int enumerate(Thread[] threads, boolean recurse) {
        return enumerateGeneric(threads, recurse, 0, true);
    }

    /**
     * Copies an array with all ThreadGroups which are children of the receiver
     * (directly or indirectly) into the array <code>groups</code> passed as
     * parameters. If the array passed as parameter is too small no exception is
     * thrown - the extra elements are simply not copied.
     * 
     * @param groups array into which the ThreadGroups will be copied
     * @return How many ThreadGroups were copied over
     * 
     */
    public int enumerate(ThreadGroup[] groups) {
        return enumerate(groups, true);
    }

    /**
     * Copies an array with all ThreadGroups which are children of the receiver
     * into the array <code>groups</code> passed as parameter. Children
     * ThreadGroups of subgroups are recursively copied as well if parameter
     * <code>recurse</code> is <code>true</code>.
     * 
     * If the array passed as parameter is too small no exception is thrown -
     * the extra elements are simply not copied.
     * 
     * @param groups array into which the ThreadGroups will be copied
     * @param recurse Indicates whether ThreadGroups in subgroups should be
     *        recursively copied as well or not
     * @return How many ThreadGroups were copied over
     * 
     */
    public int enumerate(ThreadGroup[] groups, boolean recurse) {
        return enumerateGeneric(groups, recurse, 0, false);
    }

    /**
     * Copies into <param>enumeration</param> starting at
     * <param>enumerationIndex</param> all Threads or ThreadGroups in the
     * receiver. If <param>recurse</param> is true, recursively enumerate the
     * elements in subgroups.
     * 
     * If the array passed as parameter is too small no exception is thrown -
     * the extra elements are simply not copied.
     * 
     * @param enumeration array into which the elements will be copied
     * @param recurse Indicates whether subgroups should be enumerated or not
     * @param enumerationIndex Indicates in which position of the enumeration
     *        array we are
     * @param enumeratingThreads Indicates whether we are enumerating Threads or
     *        ThreadGroups
     * @return How many elements were enumerated/copied over
     */
    private int enumerateGeneric(Object[] enumeration, boolean recurse, int enumerationIndex,
            boolean enumeratingThreads) {
        checkAccess();

        Object[] immediateCollection = enumeratingThreads ? (Object[]) childrenThreads
                : (Object[]) childrenGroups;
        Object syncLock = enumeratingThreads ? childrenThreadsLock : childrenGroupsLock;

        synchronized (syncLock) { // Lock this subpart of the tree as we walk
            for (int i = enumeratingThreads ? numThreads : numGroups; --i >= 0;) {
                if (!enumeratingThreads || ((Thread) immediateCollection[i]).isAlive()) {
                    if (enumerationIndex >= enumeration.length) {
                        return enumerationIndex;
                    }
                    enumeration[enumerationIndex++] = immediateCollection[i];
                }
            }
        }

        if (recurse) { // Lock this subpart of the tree as we walk
            synchronized (this.childrenGroupsLock) {
                for (int i = 0; i < numGroups; i++) {
                    if (enumerationIndex >= enumeration.length) {
                        return enumerationIndex;
                    }
                    enumerationIndex = childrenGroups[i].enumerateGeneric(enumeration, recurse,
                            enumerationIndex, enumeratingThreads);
                }
            }
        }
        return enumerationIndex;
    }

    /**
     * Answers the maximum allowed priority for a Thread in the receiver.
     * 
     * @return the maximum priority (an <code>int</code>)
     * 
     * @see #setMaxPriority
     */
    public final int getMaxPriority() {
        return maxPriority;
    }

    /**
     * Answers the name of the receiver.
     * 
     * @return the receiver's name (a java.lang.String)
     */
    public final String getName() {
        return name;
    }

    /**
     * Answers the receiver's parent ThreadGroup. It can be null if the receiver
     * is the the root ThreadGroup.
     * 
     * @return the parent ThreadGroup
     * 
     */
    public final ThreadGroup getParent() {
        if (parent != null) {
            parent.checkAccess();
        }
        return parent;
    }

    /**
     * Interrupts every Thread in the receiver and recursively in all its
     * subgroups.
     * 
     * @throws SecurityException if <code>this.checkAccess()</code> fails with
     *         a SecurityException
     * 
     * @see Thread#interrupt
     */
    public final void interrupt() {
        checkAccess();
        // Lock this subpart of the tree as we walk
        synchronized (this.childrenThreadsLock) {
            for (int i = 0; i < numThreads; i++) {
                this.childrenThreads[i].interrupt();
            }
        }
        // Lock this subpart of the tree as we walk
        synchronized (this.childrenGroupsLock) {
            for (int i = 0; i < numGroups; i++) {
                this.childrenGroups[i].interrupt();
            }
        }
    }

    /**
     * Answers true if the receiver is a daemon ThreadGroup, false otherwise.
     * 
     * @return if the receiver is a daemon ThreadGroup
     * 
     * @see #setDaemon
     * @see #destroy
     */
    public final boolean isDaemon() {
        return isDaemon;
    }

    /**
     * Answers true if the receiver has been destroyed already, false otherwise.
     * 
     * @return if the receiver has been destroyed already
     * 
     * @see #destroy
     */
    public boolean isDestroyed() {
        return isDestroyed;
    }

    /**
     * Outputs to <code>System.out</code> a text representation of the
     * hierarchy of Threads and ThreadGroups in the receiver (and recursively).
     * Proper indentation is done to suggest the nesting of groups inside groups
     * and threads inside groups.
     */
    public void list() {
        // We start in a fresh line
        System.out.println();
        list(0);
    }

    /*
     * Outputs to <code>System.out</code>a text representation of the
     * hierarchy of Threads and ThreadGroups in the receiver (and recursively).
     * The indentation will be four spaces per level of nesting.
     * 
     * @param levels How many levels of nesting, so that proper indentation can
     * be output.
     */
    private void list(int levels) {
        for (int i = 0; i < levels; i++) {
            System.out.print("    "); // 4 spaces for each level
        }

        // Print the receiver
        System.out.println(this.toString());

        // Print the children threads, with 1 extra indentation
        synchronized (this.childrenThreadsLock) {
            for (int i = 0; i < numThreads; i++) {
                // children get an extra indentation, 4 spaces for each level
                for (int j = 0; j <= levels; j++) {
                    System.out.print("    ");
                }
                System.out.println(this.childrenThreads[i]);
            }
        }
        synchronized (this.childrenGroupsLock) {
            for (int i = 0; i < numGroups; i++) {
                this.childrenGroups[i].list(levels + 1);
            }
        }
    }

    /**
     * Answers true if the receiver is a direct or indirect parent group of
     * ThreadGroup <code>g</code>, false otherwise.
     * 
     * @param g ThreadGroup to test
     * 
     * @return if the receiver is parent of the ThreadGroup passed as parameter
     * 
     */
    public final boolean parentOf(ThreadGroup g) {
        while (g != null) {
            if (this == g) {
                return true;
            }
            g = g.parent;
        }
        return false;
    }

    /**
     * Removes a Thread from the receiver. This should only be visible to class
     * java.lang.Thread, and should only be called when a Thread dies.
     * 
     * @param thread Thread to remove from the receiver
     * 
     * @see #add(Thread)
     */
    final void remove(java.lang.Thread thread) {
        synchronized (this.childrenThreadsLock) {
            for (int i = 0; i < numThreads; i++) {
                if (childrenThreads[i].equals(thread)) {
                    numThreads--;
                    System
                            .arraycopy(childrenThreads, i + 1, childrenThreads, i, numThreads
                                    - i);
                    childrenThreads[numThreads] = null;
                    break;
                }
            }
        }
        destroyIfEmptyDaemon();
    }

    /**
     * Removes an immediate subgroup from the receiver.
     * 
     * @param g Threadgroup to remove from the receiver
     * 
     * @see #add(Thread)
     * @see #add(ThreadGroup)
     */
    private void remove(ThreadGroup g) {
        synchronized (this.childrenGroupsLock) {
            for (int i = 0; i < numGroups; i++) {
                if (childrenGroups[i].equals(g)) {
                    numGroups--;
                    System.arraycopy(childrenGroups, i + 1, childrenGroups, i, numGroups - i);
                    childrenGroups[numGroups] = null;
                    break;
                }
            }
        }
        destroyIfEmptyDaemon();
    }

    /**
     * Resumes every Thread in the receiver and recursively in all its
     * subgroups.
     * 
     * @throws SecurityException if <code>this.checkAccess()</code> fails with
     *         a SecurityException
     * 
     * @see Thread#resume
     * @see #suspend
     * 
     * @deprecated Requires deprecated method Thread.resume().
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public final void resume() {
        checkAccess();
        // Lock this subpart of the tree as we walk
        synchronized (this.childrenThreadsLock) {
            for (int i = 0; i < numThreads; i++) {
                this.childrenThreads[i].resume();
            }
        }
        // Lock this subpart of the tree as we walk
        synchronized (this.childrenGroupsLock) {
            for (int i = 0; i < numGroups; i++) {
                this.childrenGroups[i].resume();
            }
        }
    }

    /**
     * Configures the receiver to be a daemon ThreadGroup or not. Daemon
     * ThreadGroups are automatically destroyed when they become empty.
     * 
     * @param isDaemon new value defining if receiver should be daemon or not
     * 
     * @throws SecurityException if <code>checkAccess()</code> for the parent
     *         group fails with a SecurityException
     * 
     * @see #isDaemon
     * @see #destroy
     */
    public final void setDaemon(boolean isDaemon) {
        checkAccess();
        this.isDaemon = isDaemon;
    }

    /**
     * Configures the maximum allowed priority for a Thread in the receiver and
     * recursively in all its subgroups.
     * 
     * One can never change the maximum priority of a ThreadGroup to be higher
     * than it was. Such an attempt will not result in an exception, it will
     * simply leave the ThreadGroup with its current maximum priority.
     * 
     * @param newMax the new maximum priority to be set
     * 
     * @throws SecurityException if <code>checkAccess()</code> fails with a
     *         SecurityException
     * @throws IllegalArgumentException if the new priority is greater than
     *         Thread.MAX_PRIORITY or less than Thread.MIN_PRIORITY
     * 
     * @see #getMaxPriority
     */
    public final void setMaxPriority(int newMax) {
        checkAccess();

        if (newMax <= this.maxPriority) {
            if (newMax < Thread.MIN_PRIORITY) {
                newMax = Thread.MIN_PRIORITY;
            }

            int parentPriority = parent == null ? newMax : parent.getMaxPriority();
            this.maxPriority = parentPriority <= newMax ? parentPriority : newMax;
            // Lock this subpart of the tree as we walk
            synchronized (this.childrenGroupsLock) {
                // ??? why not maxPriority
                for (int i = 0; i < numGroups; i++) {
                    this.childrenGroups[i].setMaxPriority(newMax);
                }
            }
        }
    }

    /**
     * Sets the parent ThreadGroup of the receiver, and adds the receiver to the
     * parent's collection of immediate children (if <code>parent</code> is
     * not <code>null</code>).
     * 
     * @param parent The parent ThreadGroup, or null if the receiver is to be
     *        the root ThreadGroup
     * 
     * @see #getParent
     * @see #parentOf
     */
    private void setParent(ThreadGroup parent) {
        if (parent != null) {
            parent.add(this);
        }
        this.parent = parent;
    }

    /**
     * Stops every Thread in the receiver and recursively in all its subgroups.
     * 
     * @throws SecurityException if <code>this.checkAccess()</code> fails with
     *         a SecurityException
     * 
     * @see Thread#stop()
     * @see Thread#stop(Throwable)
     * @see ThreadDeath
     * 
     * @deprecated Requires deprecated method Thread.stop().
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public final void stop() {
        if (stopHelper()) {
            Thread.currentThread().stop();
        }
    }

    /**
     * @deprecated Requires deprecated method Thread.suspend().
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    private final boolean stopHelper() {
        checkAccess();

        boolean stopCurrent = false;
        // Lock this subpart of the tree as we walk
        synchronized (this.childrenThreadsLock) {
            Thread current = Thread.currentThread();
            for (int i = 0; i < numThreads; i++) {
                if (this.childrenThreads[i] == current) {
                    stopCurrent = true;
                } else {
                    this.childrenThreads[i].stop();
                }
            }
        }
        // Lock this subpart of the tree as we walk
        synchronized (this.childrenGroupsLock) {
            for (int i = 0; i < numGroups; i++) {
                stopCurrent |= this.childrenGroups[i].stopHelper();
            }
        }
        return stopCurrent;
    }

    /**
     * Suspends every Thread in the receiver and recursively in all its
     * subgroups.
     * 
     * @throws SecurityException if <code>this.checkAccess()</code> fails with
     *         a SecurityException
     * 
     * @see Thread#suspend
     * @see #resume
     * 
     * @deprecated Requires deprecated method Thread.suspend().
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public final void suspend() {
        if (suspendHelper()) {
            Thread.currentThread().suspend();
        }
    }

    /**
     * @deprecated Requires deprecated method Thread.suspend().
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    private final boolean suspendHelper() {
        checkAccess();

        boolean suspendCurrent = false;
        // Lock this subpart of the tree as we walk
        synchronized (this.childrenThreadsLock) {
            Thread current = Thread.currentThread();
            for (int i = 0; i < numThreads; i++) {
                if (this.childrenThreads[i] == current) {
                    suspendCurrent = true;
                } else {
                    this.childrenThreads[i].suspend();
                }
            }
        }
        // Lock this subpart of the tree as we walk
        synchronized (this.childrenGroupsLock) {
            for (int i = 0; i < numGroups; i++) {
                suspendCurrent |= this.childrenGroups[i].suspendHelper();
            }
        }
        return suspendCurrent;
    }

    /**
     * Answers a string containing a concise, human-readable description of the
     * receiver.
     * 
     * @return a printable representation for the receiver.
     */
    @Override
    public String toString() {
        return getClass().getName() + "[name=" + this.getName() + ",maxpri="
                + this.getMaxPriority() + "]";
    }

    /**
     * Any uncaught exception in any Thread has to be forwarded (by the VM) to
     * the Thread's ThreadGroup by sending this message (uncaughtException).
     * This allows users to define custom ThreadGroup classes and custom
     * behavior for when a Thread has an uncaughtException or when it does
     * (ThreadDeath).
     * 
     * @param t Thread with an uncaught exception
     * @param e The uncaught exception itself
     * 
     * @see Thread#stop()
     * @see Thread#stop(Throwable)
     * @see ThreadDeath
     */
    public void uncaughtException(Thread t, Throwable e) {
        if (parent != null) {
            parent.uncaughtException(t, e);
        } else if (!(e instanceof ThreadDeath)) {
            // No parent group, has to be 'system' Thread Group
            e.printStackTrace(System.err);
        }
    }
}
