package wolverine

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.util.{HasBlackBoxPath, HasBlackBoxResource}
import testchipip.SerialIO

class WolverineTestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val dut = Module(new WolverineTop())

  val harn = Module(new SimWolverineTestHarness)
  harn.io.clock := clock
  harn.io.reset := reset.asBool()
  io.success := harn.io.exit
  dut.io <> harn.io.wolv
}

class SimWolverineTestHarness extends BlackBox with HasBlackBoxPath {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())
    val wolv = Flipped(new TopWrapperInterface(1,32))
    val exit = Output(Bool())
  })

  // TODO: fix hardcoding (figure out addResource)
  addPath("/home/david/git/chipyard-dev/generators/wolverine/src/resources/vsrc/SimWolverineTestHarness.v")
  addPath("/home/david/git/chipyard-dev/generators/wolverine/src/resources/csrc/SimWolverineTestHarness.cc")
  addPath("/home/david/git/chipyard-dev/generators/wolverine/src/resources/csrc/SimWolverineTestHarness.h")
  addPath("/home/david/git/chipyard-dev/generators/wolverine/src/resources/csrc/SimWolverineMemory.cc")
}
