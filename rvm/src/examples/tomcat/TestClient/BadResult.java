/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package TestClient;

class BadResult extends Exception {
    
    BadResult(Request req, String detail) {
	super(req.toString() + ": " + detail);
    }

}
