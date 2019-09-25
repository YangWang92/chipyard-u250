Debugging RTL
======================

While the packaged Chipyard configs and RTL have been tested to work,
users will typically want to build custom chips by adding their own
IP, or by modifying existing Chisel generators. Such changes might introduce
bugs. This section aims to run through a typical debugging flow
using Chipyard. We assume the user has a custom SoC configuration,
and is trying to verify functionality by running some software test.

Bringup Method
---------------------------

There are two approaches for running a custom application binary on a
Chipyard. TSI uses a Test Serial Interface to write the binary into
simulated memory, while DTM uses the SoC's debug module to execute a program
that fetches instruction bits from the host.

The DTM is used in actual chip bringup, while the TSI in simulation is
designed to fast and deterministic. For this reason we generally prefer
TSI for initial debugging of SW simulation.

Waveforms
---------------------------

The default SW simulators do not dump waveforms during execution. To build
simulators with wave dump capabilities use must use the ``debug`` make target.
For example:

.. code-block:: shell

   make CONFIG=CustomConfig debug

The ``run-binary-debug`` rule will also automatically build a simulator,
run it on a custom binary, and generate a waveform. For example, to run a
test on ``helloworld.riscv``, use

.. code-block:: shell

   make CONFIG=CustomConfig run-binary-debug BINARY=helloworld.riscv

You can also directly call the simulator with appropriate flags to begin
dumping waves only after a certain number of cycles. This is very useful for
long-running application, where only a segment of waves over the entire
simulation is desired.

Print Output
---------------------------

Both Rocket and BOOM can be configured with vary levels of print output.
For information see the Rocket core source code, or the BOOM documentation
website. In addition, developers may insert arbitrary prints at arbitrary
conditions within the Chisel generators. See the Chisel documentation
for information on this.

Once the cores have been configured with the desired print statements, the
``+verbose`` flag will cause the simulator to print the statements. The following
commands will all generate desired print statements:

.. code-block:: shell

   make CONFIG=CustomConfig run-binary-debug BINARY=helloworld.riscv
   ./simv-CustomConfig-debug +verbose helloworld.riscv

Both cores can be configured to print out commit logs, which can then be compared
against a spike commit log to verify correctedness.

Basic tests
---------------------------
``riscv-tests`` includes basic ISA-level tests and basic benchmarks. These
are used in Chipyard CI, and should be the first step in verifying a chip's
functionality. The make rule is

.. code-block:: shell

   make CONFIG=CustomConfig run-asm-tests run-bmark-tests


Torture tests
---------------------------
The RISC-V torture utility generates random RISC-V assembly streams, compiles them,
runs them on both the Spike functional model and the SW simulator, and verifies
identical program behavior. The torture utility can also be configured to run
continuously for stress-testing. The torture utility exists within the ``utilities``
directory.

Firesim Debugging
---------------------------
Chisel printfs, asserts, and waveform generation are also available in FireSim
FPGA-accelerated simulation. See the FireSim docs for more detail.

