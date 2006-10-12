/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 * 
 * (C) Copyright Richard Jones, 2005-6
 * Computing Laboratory, University of Kent at Canterbury
 */
package org.mmtk.plan.semispace.gcspy;

import org.mmtk.plan.Phase;
import org.mmtk.plan.semispace.SSMutator;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.ImmortalLocal;
import org.mmtk.utility.Log;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.utility.gcspy.GCspy;
import org.mmtk.utility.gcspy.drivers.LinearSpaceDriver;

import org.vmmagic.pragma.InlinePragma;
import org.vmmagic.pragma.Uninterruptible;

import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements <i>per-mutator thread</i> behavior and state for the
 * <i>SSGCspy</i> plan.
 * 
 * See {@link SSGCspy} for an overview of the GC-spy mechanisms.
 * <p>
 * 
 * @see SSMutator
 * @see SSGCspy
 * @see SSGCspyCollector
 * @see org.mmtk.plan.StopTheWorldMutator
 * @see org.mmtk.plan.MutatorContext
 * @see org.mmtk.plan.SimplePhase#delegatePhase
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @author <a href="http://www.cs.ukc.ac.uk/~rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class SSGCspyMutator extends SSMutator implements Uninterruptible {

  /*****************************************************************************
   * Instance fields
   */

  private static final boolean DEBUG = false;

  /** Per-mutator allocator into GCspy's space */
  private BumpPointer gcspy = new ImmortalLocal(SSGCspy.gcspySpace);


  
  /*****************************************************************************
   * 
   * Mutator-time allocation
   */

  /**
   * Allocate space (for an object)
   * 
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @param allocator The allocator number to be used for this allocation
   * @param site Allocation site
   * @return The address of the first byte of the allocated region
   */
  public Address alloc(int bytes, int align, int offset, int allocator, int site)
      throws InlinePragma {
    if (allocator == SSGCspy.ALLOC_GCSPY)
      return gcspy.alloc(bytes, align, offset, false);
    else
      return super.alloc(bytes, align, offset, allocator, site);
  }

  /**
   * Perform post-allocation actions. For many allocators none are required.
   * 
   * @param object The newly allocated object
   * @param typeRef The type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  public void postAlloc(ObjectReference object, ObjectReference typeRef,
                        int bytes, int allocator) throws InlinePragma {
    if (allocator == SSGCspy.ALLOC_GCSPY)
      SSGCspy.gcspySpace.initializeHeader(object);
    else
      super.postAlloc(object, typeRef, bytes, allocator);
  }

  /*****************************************************************************
   * 
   * Collection
   */

  /**
   * Perform a per-mutator collection phase.
   * Before a collection, we need to discover
   * <ul>
   * <li>the tospace objects copied by the collector in the last GC cycle
   * <li>the ojects allocated since by the mutator.
   * <li>all immortal objects allocated by the mutator
   * <li>all large objects allocated by the mutator
   * </ul>
   * After the semispace has been copied, we need to discover
   * <ul>
   * <li>the tospace objects copied by the collector
   * <li>all immortal objects allocated by the mutator
   * <li>all large objects allocated by the mutator
   * </ul>
   */
  public final void collectionPhase(int phaseId, boolean primary)
      throws InlinePragma {
    if (DEBUG) { Log.write("--Phase Mutator."); Log.writeln(Phase.getName(phaseId)); }
    
    // TODO do we need to worry any longer about primary??
    if (phaseId == SSGCspy.PREPARE_MUTATOR) {
      //if (primary) 
        gcspyGatherData(SSGCspy.BEFORE_COLLECTION, true);
      super.collectionPhase(phaseId, primary);
      return;
    }

    if (phaseId == SSGCspy.RELEASE_MUTATOR) {
      //if (primary) 
        gcspyGatherData(SSGCspy.SEMISPACE_COPIED, true);
      super.collectionPhase(phaseId, primary);
      //if (primary) 
        gcspyGatherData(SSGCspy.AFTER_COLLECTION, true);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /**
   * Gather data for GCspy for the semispaces, the immortal space and the large
   * object space.
   * <p>
   * This method sweeps the semispace under consideration to gather data.
   * Alternatively and more efficiently, 'used space' can obviously be
   * discovered in constant time simply by comparing the start and the end
   * addresses of the semispace. However, per-object information can only be
   * gathered by sweeping through the space and we do this here for tutorial
   * purposes.
   * 
   * @param event
   *          The event, either BEFORE_COLLECTION, SEMISPACE_COPIED or
   *          AFTER_COLLECTION
   * @param fullHeap full heap collection
   */
  private void gcspyGatherData(int event, boolean fullHeap) {
    if(DEBUG) {
      Log.writeln("SSGCspyMutator.gcspyGatherData, event=", event);
      Log.writeln("SSGCspyMutator.gcspyGatherData, port=", GCspy.getGCspyPort());
    }
    
    // If port = 0 there can be no GCspy client connected
    if (GCspy.getGCspyPort() == 0)
      return;

    // If the server is connected to a client that is interested in this
    // event, then we gather data. But first we start a timer to
    // compensate for the time spent gathering data here.
    if (GCspy.server.isConnected(event)) {
      
      if (DEBUG) {
        if (SSGCspy.hi) 
          Log.write("\nMutator Examining Lowspace (event ", event);
        else    
          Log.write("\nMutator Examining Highspace (event ", event);
        Log.write(")");
        SSGCspy.reportSpaces(); Log.writeln();
      }
      
      if (event == SSGCspy.BEFORE_COLLECTION) {       
        GCspy.server.startCompensationTimer();
        
        // -- Handle the semispaces
        // Here I need to scan newly allocated objects      
        if (DEBUG) {
          debugSpaces(SSGCspy.fromSpace());
          Log.write("SSGCspyMutator.gcspyGatherData reset, gather and transmit driver ");
          Log.writeln(SSGCspy.fromSpace().getName());
        }       
        ss.gcspyGatherData(fromSpaceDriver(), SSGCspy.fromSpace());
        
        // -- Handle the immortal space --
        gatherImmortal(event);
        
        // -- Handle the LOSes
        
        // reset, collect and scan los data for the nursery and tospace
        SSGCspy.losNurseryDriver.resetData();
        los.gcspyGatherData(event, SSGCspy.losNurseryDriver); 
        SSGCspy.losDriver.resetData();
        los.gcspyGatherData(event, SSGCspy.losDriver, true); 
        
        // reset, collect and scan plos data for the nursery and tospace
        SSGCspy.plosNurseryDriver.resetData();
        plos.gcspyGatherData(event, SSGCspy.plosNurseryDriver); 
        SSGCspy.plosDriver.resetData();
        plos.gcspyGatherData(event, SSGCspy.plosDriver, true); 
        
        // transmit the data
        GCspy.server.stopCompensationTimer();
        fromSpaceDriver().transmit(event);
        SSGCspy.immortalDriver.transmit(event);
        SSGCspy.losNurseryDriver.transmit(event);
        SSGCspy.losDriver.transmit(event);
        SSGCspy.plosNurseryDriver.transmit(event);
        SSGCspy.plosDriver.transmit(event);
        
        // As this follows Collector.gcspyGatherData, I'll safepoint here
        // This is a safepoint for the server, i.e. it is a point at which
        // the server can pause.
        GCspy.server.serverSafepoint(event);
      }
      
      
      else if (event == SSGCspy.SEMISPACE_COPIED) {
        // -- Handle the semispaces
        if (DEBUG) {
          debugSpaces(SSGCspy.toSpace());
          Log.writeln("SSGCspyMutator.gcspyGatherData: do nothing");
        }
        
        // -- Handle the immortal space --
        GCspy.server.startCompensationTimer();
        gatherImmortal(event);
        
        // reset, scan and send the los for the nursery and tospace
        // and fromspace as well if full heap collection
        SSGCspy.losNurseryDriver.resetData();
        los.gcspyGatherData(event, SSGCspy.losNurseryDriver); 
        SSGCspy.losDriver.resetData();
        if (fullHeap)
          los.gcspyGatherData(event, SSGCspy.losDriver, false);
        los.gcspyGatherData(event, SSGCspy.losDriver, true);
        
        // reset, scan and send the plos for the nursery and tospace
        // and fromspace as well if full heap collection
        SSGCspy.plosNurseryDriver.resetData();
        plos.gcspyGatherData(event, SSGCspy.plosNurseryDriver); 
        SSGCspy.plosDriver.resetData();
        if (fullHeap)
          plos.gcspyGatherData(event, SSGCspy.plosDriver, false);
        plos.gcspyGatherData(event, SSGCspy.plosDriver, true);
        
        // transmit
        GCspy.server.stopCompensationTimer();
        SSGCspy.immortalDriver.transmit(event);
        SSGCspy.losNurseryDriver.transmit(event);
        SSGCspy.losDriver.transmit(event);
        SSGCspy.plosNurseryDriver.transmit(event);
        SSGCspy.plosDriver.transmit(event);
        
        // As this follows Collector.gcspyGatherData, I'll safepoint here
        // This is a safepoint for the server, i.e. it is a point at which
        // the server can pause.
        GCspy.server.serverSafepoint(event);
      }
      
      else if (event == SSGCspy.AFTER_COLLECTION) {
        GCspy.server.startCompensationTimer();
        
        // -- Handle the semispaces
        if (DEBUG) debugSpaces(SSGCspy.toSpace());

        // -- Handle the immortal space --
        gatherImmortal(event);

        // -- Handle the LOSes
        
        // reset, scan and send the los
        SSGCspy.losNurseryDriver.resetData();
        SSGCspy.losDriver.resetData();
        // no need to scan empty nursery
        los.gcspyGatherData(event, SSGCspy.losDriver, true);
        
        // reset, scan and send the plos
        SSGCspy.plosNurseryDriver.resetData();
        SSGCspy.plosDriver.resetData();
        // no need to scan empty nursery
        plos.gcspyGatherData(event, SSGCspy.plosDriver, true);
        
        //transmit
        GCspy.server.stopCompensationTimer();
        SSGCspy.immortalDriver.transmit(event);
        SSGCspy.losNurseryDriver.transmit(event);
        SSGCspy.losDriver.transmit(event);
        SSGCspy.plosNurseryDriver.transmit(event);
        SSGCspy.plosDriver.transmit(event);
        
        // Reset fromspace
        if (DEBUG) {
          Log.write("SSGCspyMutator.gcspyGatherData: reset and zero range for driver ");
          Log.write(SSGCspy.toSpace().getName());
        }
      }

    }
    // else Log.write("not transmitting...");
  }

  /**
   * Gather data for the immortal space
   * @param event
   * The event, either BEFORE_COLLECTION, SEMISPACE_COPIED or
   *          AFTER_COLLECTION
   */
  private void gatherImmortal(int event) {
    // We want to do this at every GCspy event
    if (DEBUG) {
      Log.write("SSGCspyMutator.gcspyGatherData: gather data for immortal space ");
      Log.write(SSGCspy.immortalSpace.getStart()); Log.writeln("-",immortal.getCursor());
    }
    SSGCspy.immortalDriver.resetData();
    immortal.gcspyGatherData(SSGCspy.immortalDriver);
    if (DEBUG) Log.writeln("Finished immortal space.");
  }

  /**
   * Debugging info for the semispaces
   * @param scannedSpace
   */
  private void debugSpaces(CopySpace scannedSpace) {
    Log.write("SSGCspyMutator.gcspyGatherData: gather data for active semispace ");
    Log.write(scannedSpace.getStart()); Log.write("-",ss.getCursor()); Log.flush();
    Log.write(". The space is: "); Log.writeln(ss.getSpace().getName());
    Log.write("scannedSpace is "); Log.writeln(scannedSpace.getName());
    Log.write("The range is "); Log.write(ss.getSpace().getStart());
    Log.write(" to "); Log.writeln(ss.getCursor());
    SSGCspy.reportSpaces();
  }
  
  /** @return the driver for fromSpace */
  private LinearSpaceDriver fromSpaceDriver() { 
    return SSGCspy.hi ? SSGCspy.ss0Driver : SSGCspy.ss1Driver;
  }

}
