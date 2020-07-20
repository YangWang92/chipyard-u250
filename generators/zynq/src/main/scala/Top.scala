package zynq

import chipyard.{Subsystem, SubsystemModuleImp}
import chisel3._
import freechips.rocketchip.amba.axi4.AXI4Bundle
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.devices.debug.{HasPeripheryDebug, HasPeripheryDebugModuleImp}
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system.{ExampleRocketSystem, SimAXIMem}
import freechips.rocketchip.util.DontTouch
import testchipip._
import utilities._

case object ZynqAdapterBase extends Field[BigInt]

class Top(implicit val p: Parameters) extends Module {
  val address = p(ZynqAdapterBase)
  val config = p(ExtIn).get
  val test = LazyModule(new FPGAZynqTop)
  val target = Module(test.module)
  val adapter = Module(LazyModule(new ZynqAdapter(address, config)).module)

//  require(test.mem_axi4.size == 1)
  val io = IO(new Bundle {
    val ps_axi_slave = Flipped(adapter.axi.cloneType)
//    val mem_axi: Option[AXI4Bundle] = None
    val mem_axi: Option[AXI4Bundle] = test.mem_axi4.headOption.map(_.cloneType)
    val fan_speed = Output(UInt(10.W))
    val fan_rpm = Input(UInt(16.W))
  })
  target.resetctrl.get.hartIsInReset.foreach(_ := adapter.io.sys_reset)
//  SimAXIMem.connectMem(test)
  io.mem_axi.map(_ <> test.mem_axi4.head)
  adapter.axi <> io.ps_axi_slave
  adapter.io.serial <> target.serial.get
  adapter.io.bdev <> target.bdev.get
  io.fan_speed := adapter.io.fan_speed
  adapter.io.fan_rpm := io.fan_rpm

  target.debug.map(_ := DontCare) //TODO: figure out if we need this! - probably not due to NoDebug
//  target.tieOffInterrupts()
  target.dontTouchPorts()
  target.reset := adapter.io.sys_reset
}

class FPGAZynqTop(implicit p: Parameters) extends Subsystem // don't use system because we want synchronous interrupts
//  with HasHierarchicalBusTopology // no idea what this does but it seems to help - WithIncoherentBusTopology?
  with CanHaveMasterAXI4MemPort
//    with HasSystemErrorSlave
  with HasPeripheryBootROM
//  with HasSyncExtInterrupts //TODO: check if we actually need this
  with HasNoDebug
  with CanHavePeripherySerial
  with CanHavePeripheryBlockDevice {
  override lazy val module = new FPGAZynqTopModule(this)
}

class FPGAZynqTopModule(outer: FPGAZynqTop) extends SubsystemModuleImp(outer)
  with HasRTCModuleImp
//  with CanHaveMasterAXI4MemPortModuleImp
  with HasPeripheryBootROMModuleImp
//  with HasExtInterruptsModuleImp
  with HasNoDebugModuleImp
  with CanHavePeripherySerialModuleImp
  with CanHavePeripheryBlockDeviceModuleImp
  with DontTouch
