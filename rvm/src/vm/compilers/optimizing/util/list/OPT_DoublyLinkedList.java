/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * put your documentation comment here
 */
class OPT_DoublyLinkedList {
  OPT_DoublyLinkedListElement start;
  OPT_DoublyLinkedListElement end;

  /**
   * put your documentation comment here
   */
  OPT_DoublyLinkedList () {
  }

  /**
   * put your documentation comment here
   * @param   OPT_DoublyLinkedListElement e
   */
  OPT_DoublyLinkedList (OPT_DoublyLinkedListElement e) {
    start = end = e;
  }

  /**
   * put your documentation comment here
   * @param   OPT_DoublyLinkedListElement s
   * @param   OPT_DoublyLinkedListElement e
   */
  OPT_DoublyLinkedList (OPT_DoublyLinkedListElement s, 
      OPT_DoublyLinkedListElement e) {
    start = s;
    end = e;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  final OPT_DoublyLinkedListElement first () {
    return  start;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  final OPT_DoublyLinkedListElement last () {
    return  end;
  }

  /**
   * put your documentation comment here
   * @param e
   */
  final void remove (OPT_DoublyLinkedListElement e) {
    if (e == start) {
      if (e == end) {
        start = end = null;
      } 
      else {
        start = e.next;
        start.prev = null;
      }
    } 
    else if (e == end) {
      end = e.prev;
      end.next = null;
    } 
    else {
      e.remove();
    }
  }

  /**
   * put your documentation comment here
   * @return 
   */
  final OPT_DoublyLinkedListElement removeLast () {
    OPT_DoublyLinkedListElement e = end;
    if (e == start) {
      start = e = null;
    } 
    else {
      e = e.prev;
      e.next = null;
    }
    end = e;
    return  e;
  }

  // append at the end of the list
  final void 
  /*OPT_DoublyLinkedListElement*/
  append (OPT_DoublyLinkedListElement e) {
    OPT_DoublyLinkedListElement End = end;
    if (End != null) {
      End.append(e);
    } 
    else {
      start = e;
    }
    end = e;
    //return e;
  }

  // insert at the start of the list
  final void 
  /* OPT_DoublyLinkedListElement*/
  insert (OPT_DoublyLinkedListElement e) {
    OPT_DoublyLinkedListElement Start = start;
    if (Start != null) {
      Start.insertBefore(e);
    } 
    else {
      end = e;
    }
    start = e;
    //return e;
  }

  /**
   * put your documentation comment here
   */
  final void deleteAll () {
    start = end = null;
  }
}



