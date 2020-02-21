package zynq

import chisel3._
import freechips.rocketchip.diplomacy._
import example.{MediumBoomConfig, SmallBoomConfig}
import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.rocket.{DCacheParams, ICacheParams, MulDivParams, RocketCoreParams}
import freechips.rocketchip.tile.{RocketTileParams, XLen}
import testchipip._

class WithBootROM extends Config((site, here, up) => {
  case BootROMParams => BootROMParams(
    contentFileName = s"./bootrom/bootrom.rv${site(XLen)}.img")
})

class WithZynqAdapter extends Config((site, here, up) => {
  case SerialFIFODepth => 16
  case ResetCycles => 10
  case ZynqAdapterBase => BigInt(0x43C00000L)
  case ExtMem => up(ExtMem, site) map (par => par.copy(master = par.master.copy(idBits = 6)))
  case ExtIn => up(ExtIn, site) map (_.copy(beatBytes = 4, idBits = 12))
  case BlockDeviceKey => Some(BlockDeviceConfig(nTrackers = 2))
  case BlockDeviceFIFODepth => 16
  case NetworkFIFODepth => 16
  case SerialKey => true
})

class SmallBoomZynqConfig extends Config(
  new WithBootROM ++
  new WithZynqAdapter ++
  new boom.common.WithSmallBooms ++                         // 1-wide BOOM
  new boom.common.WithNBoomCores(1) ++                      // single-core
  new WithoutTLMonitors ++                                  // disable TL verification
  new freechips.rocketchip.system.BaseConfig)

class SliceBoomZynqConfig extends Config(
  new WithBootROM ++
  new WithZynqAdapter ++
  new boom.common.WithSliceBooms ++                         // 1-wide BOOM
  new boom.common.WithNBoomCores(1) ++                      // single-core
  new WithoutTLMonitors ++                                  // disable TL verification
  new freechips.rocketchip.system.BaseConfig)

class RocketZynqConfig extends Config(
  new WithBootROM ++
  new WithZynqAdapter ++
  new WithNBigCores(1) ++                      // single-core Big Rocket
  new WithoutTLMonitors ++                     // disable TL verification
  new freechips.rocketchip.system.BaseConfig)

// for zc706_MIG
class With1GbRam extends Config(
  new WithExtMemSize(40000000L)
)
// for zc706
class With768MbRam extends Config(
  new WithExtMemSize(30000000L)
)