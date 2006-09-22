/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author Julian Dolby
 */

package TestClient;

import java.io.*;
import HTTPClient.*;

abstract class Request {

    protected URI url;
    protected byte[] desired;

    public void setUrl(String url) {
        try {
            this.url = new URI( url );
        } catch (ParseException e) {
            System.err.println("bad url: " + url);
            System.exit(1);
        }
    }

    URI getUrl() {
        return url;
    }

    public void setDesired(String fileName) {
        try {
            File f = new File( fileName );
            long length = f.length();
            desired = new byte[ (int) length];
            new FileInputStream(f).read(desired);
        } catch (Exception e) {
            System.err.println("Error reading requests: " + e.toString());
            System.exit(1);
        }
    }

    byte[] getDesired() {
        return desired;
    }

    byte[] getActual(HTTPConnection server) throws BadRequest {
        try {
            HTTPResponse rsp = doGet( server );
            if (rsp.getStatusCode() >= 300)
                throw new BadRequest( rsp.getReasonLine(), rsp.getText() );
            else
                return rsp.getData();
        } catch (IOException e) {
            throw new BadRequest( url.toString(), e.toString() );
        } catch (ModuleException e) {
            throw new BadRequest( url.toString(), e.toString() );
        } catch (ParseException e) {
            throw new BadRequest( url.toString(), e.toString() );
        }
    }

    abstract HTTPResponse doGet(HTTPConnection server) 
        throws IOException, ModuleException;

}

