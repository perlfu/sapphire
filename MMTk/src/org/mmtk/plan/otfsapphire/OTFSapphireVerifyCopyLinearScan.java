package org.mmtk.plan.otfsapphire;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.mmtk.utility.alloc.LinearScan;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;


/**
 * Verify that the object does not have any from-space pointer
 */
class VerifyToSpaceObjectScanner extends TransitiveClosure {
  /**
   * Trace an edge during GC.
   *
   * @param source The source of the reference.
   * @param slot The location containing the object reference.
   */
  @Uninterruptible
  @Override
  @Inline
  public void processEdge(ObjectReference source, Address slot) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(OTFSapphire.inToSpace(slot));
    ObjectReference value = slot.loadObjectReference();
    if (!value.isNull()) {
      if (!Space.isMappedAddress(value.toAddress())) {
        Log.write("Pointer to ummpaped area: ");
        Log.writeln(value);
        Log.write("SRC");
        VM.objectModel.dumpObject(source);
      } else if (OTFSapphire.inFromSpace(value)) {
        Log.writeln("Pointer to from-space");
        Log.write("REF");
        VM.objectModel.dumpObject(value);
        Log.write("SRC");
        VM.objectModel.dumpObject(source);
      }
    }
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!OTFSapphire.inFromSpace(slot.loadObjectReference()));
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(slot.loadAddress().isZero() || Space.isMappedAddress(slot.loadAddress()));
  }
}

public class OTFSapphireVerifyCopyLinearScan extends LinearScan {
  public static final VerifyToSpaceObjectScanner verifyToSpace = new VerifyToSpaceObjectScanner();
  @Uninterruptible
  @Override
  public void scan(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(OTFSapphire.inToSpace(object));
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!ReplicatingSpace.getReplicaPointer(object).isNull());
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(OTFSapphire.inFromSpace(ReplicatingSpace.getReplicaPointer(object)));
    //if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(VM.objectModel.debugSapphire_checkReplicaConsistency(ReplicatingSpace.getReplicaPointer(object), object));
    if (VM.VERIFY_ASSERTIONS) VM.scanning.scanObject(verifyToSpace, object);
  }
}
