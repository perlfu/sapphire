/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * This class extends VM_PriorityQueue to safely
 * support multiple producers/consumers where 
 * the consumers are blocked if no objects are available
 * to consume.
 *
 * @author Dave Grove
 * @author Michael Hind
 */
class VM_BlockingPriorityQueue extends VM_PriorityQueue {

  /**
   * Used to notify consumers when about to wait and when notified
   * Default implementation does nothing, but can be overriden as needed by client.
   */
  public static class CallBack {
    void aboutToWait() {}
    void doneWaiting() {}
  }
  CallBack callback;

  /**
   * @param initialSize the initial number of elements
   * @param _cb the callback object
   */
  VM_BlockingPriorityQueue(int initialSize, CallBack _cb) {
    super(initialSize);
    callback = _cb;
  }

  /**
   * @param initialSize the initial number of elements
   */
  VM_BlockingPriorityQueue(int initialSize) {
    this(initialSize, new CallBack());
  }

  /**
   * Insert the object passed with the priority value passed
   * 
   * Notify any sleeping consumer threads that an object
   * is available for consumption.
   *
   * @param _priority  the priority to 
   * @param _data the object to insert
   * @return true if the insert succeeded, false if it failed because the queue was full
   */
  synchronized final public boolean insert(double _priority, Object _data) { 
    boolean success = super.insert(_priority, _data);
    if (success) {
      try {
	notifyAll();
      } catch (Exception e) {
	// TODO: should we exit or something more dramatic?
	VM.sysWrite("Exception occurred while notifying that element was inserted!\n");
      }
    }
    return success;
  }

  /**
   * Remove and return the front (minimum) object.  If the queue is currently
   * empty, then block until an object is available to be dequeued.
   * @param callback a VM_BlockingPriorityQueueCallback object. 
   * @return the front (minimum) object.
   */
  synchronized final public Object deleteMin() {
    // While the queue is empty, sleep until notified that an object has been enqueued.
    while (isEmpty()) {
      try {
	callback.aboutToWait();
	wait();
	callback.doneWaiting();
      } catch (InterruptedException e) {
	// TODO: should we exit or something more dramatic?
	VM.sysWrite("Interrupted Exception occurred!\n");
      }
    }

    // When we get to here, we know the queue is non-empty, so dequeue an object and return it.
    return super.deleteMin();
  }
}
