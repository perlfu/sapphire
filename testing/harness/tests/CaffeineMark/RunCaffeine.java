/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
/**
 * @author Julian Dolby
 */
class RunCaffeine {

  public static void main(String[] args) {
    BenchmarkUnit bu;

    bu = new BenchmarkUnit(new SieveAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new LoopAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new LogicAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new StringAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new FloatAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new MethodAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new SieveAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new LoopAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new LogicAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new StringAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new FloatAtom());
    runTest(bu);

    bu = new BenchmarkUnit(new MethodAtom());
    runTest(bu);

  }


  static void runTest(BenchmarkUnit benchmark) {
    try {
      System.out.println(benchmark.testName()+" score:\t"+benchmark.testScore());
    }
    catch (Throwable x) { x.printStackTrace(System.err); }
  }
}
