## Sapphire Garbage Collector for Jikes RVM

This repository contains a copy of Jikes RVM patched with an
implement of the Sapphire garbage collection algorithm.

This implementation is the result of research and technical work by:
* Laurence Hellyer
* [Tomoharu Ugawa](http://spa.cs.uec.ac.jp/~ugawa/index-e.html) (Kochi University of Technology)
* [Carl G. Ritson](http://www.cs.kent.ac.uk/~cgr/) (University of Kent)
* [Richard E. Jones](http://www.cs.kent.ac.uk/~rej/) (University of Kent)

Please contact [Carl Ritson](mailto:c.g.ritson@kent.ac.uk) or Richard Jones for more information.

Sapphire is an on-the-fly concurrent copying garbage collector.
This means it does not need to stop all user threads to perform
garbage collection.  Thread stacks are scanned individually.
This leading to sub-millisecond pause times during garbage
collection.

Sapphire was designed by Richard Hudson and Eliot Moss.
It is documented in a journal paper [here](http://onlinelibrary.wiley.com/doi/10.1002/cpe.712/abstract).

This implementation was performed with permission from Intel
who hold patents on Sapphire's design.

The official Jikes RVM website and repository can be found [here](http://jikesrvm.org).

### Building

Standard Jikes RVM dependencies apply for building with *ant* (see [here](http://docs.codehaus.org/display/RVM/Building+the+RVM)).

To build optimised (adjust host.name appropriately):
```
ant -Dhost.name=x86_64-osx -Dconfig.name=FastAdaptiveOTFSapphire
```

To build debug (adjust host.name appropriately):
```
ant -Dhost.name=x86_64-osx -Dconfig.name=ExtremeAssertionsBaseBaseOTFSapphire
```

RVM will be in the *dist* directory.


### Run-time Options

The following options can be used to changed Sapphire's run-time behaviour.

#### ConcurrentCopyMethod

Select the method used for copying concurrent with the mutator, e.g.
```
-X:gc:concurrentCopyMethod=always-htm2
```

value | description
--- | ---
cas / cas2 | default per-word synchronised methods
unsafe / unsafe2 | unsynchronised methods
stm / mstm / stmseq / stmseq2 / stmseq2p / stmseq2n | software transactional
htm / htm2 / mhtm | hardware transactional

All of these options can be prefixed with "always-" to force the method to be used for all collections.  Normally stop-the-world collection will default to *unsafe2*.  Methods postfixed with *2* use a more efficient copying order than the same method without the postfix.

#### ConcurrentCopyTransactionSize

Select the size of transactions for mhtm and mstm concurrent copying methods, e.g.
```
-X:gc:concurrentCopyTransactionSize=256
```

#### ConcurrentTriggerMethod

Select the method used to trigger concurrent collection, i.e. when collection will be started.  Interacts with *ConcurrentTrigger*.
```
-X:gc:concurrentTriggerMethod=allocation
```

value | description
--- | ---
allocation | pages allocated since last collection start
OOGCAllocation | pages allocated since last collection end
percentage | percentage of the heap allocated 
period | microseconds since last collection start
time | microseconds since last collection end

#### ConcurrentTrigger

Value used with *ConcurrentTriggerMethod* may represent pages, time or a percentage.

#### SapphireSTWPhase

Force one or more Sapphire phases to run stop-the-world, e.g.
```
-X:gc:SapphireSTWPhase=flip
```

One or more of the following separated by commas:
* alloc
* copy
* flip
* root

