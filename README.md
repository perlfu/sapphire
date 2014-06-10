# Sapphire Garbage Collector for Jikes RVM

This repository contains a copy of Jikes RVM patched with an
implement of the Sapphire garbage collection algorithm.

These patches are the result of research and technical work by:
* Laurence Hellyer
* Tomoharu Ugawa (Kochi University of Technology)
* Carl G. Ritson (University of Kent)
* Richard E. Jones (University of Kent)

The official Jikes RVM website and repository can be found [here](http://jikesrvm.org).

Sapphire is an on-the-fly concurrent copying garbage collector.
This means it does not need to stop all user threads to perform
garbage collection.  Thread stacks are scanned individually.
This leading to sub-millisecond pause times during garbage
collection.

Sapphire was designed by Richard Hudson and Eliot Moss.
It is documented in a journal paper [here](http://onlinelibrary.wiley.com/doi/10.1002/cpe.712/abstract).

This implementation was performed with permission from Intel
who hold patents on Sapphire's design.
