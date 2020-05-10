package zynq

import boom.common.BoomTilesKey
import chisel3._
import freechips.rocketchip.diplomacy._
import example.{MediumBoomConfig, SmallBoomConfig}
import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.rocket.{DCacheParams, ICacheParams, MulDivParams, RocketCoreParams}
import freechips.rocketchip.tile.{RocketTileParams, XLen}
import testchipip._
import boom.common.{WithOriginalIbda, WithOneBitPcIbda, WithHash14Ibda, WithBloomIbda}

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

class With50Mhz extends Config((site, here, up) => {
  // If a frequency other than 1GHz is used CLOCK_FREQ in the proxy-kernel (sascall.c) has to be adjusted!
  case BoomTilesKey => up(BoomTilesKey, site) map { b => b.copy(
    core = b.core.copy(bootFreqHz = 50000000)
  )}
  case RocketTilesKey => up(RocketTilesKey, site) map { b => b.copy(
    core = b.core.copy(bootFreqHz = 50000000)
  )}
  case PeripheryBusKey => up(PeripheryBusKey, site).copy(frequency = 50000000)
})

class WithNPerfCounters(n: Int) extends Config((site, here, up) => {
  case BoomTilesKey => up(BoomTilesKey, site) map { b => b.copy(core = b.core.copy(
    nPerfCounters = n
  ))}
  case RocketTilesKey => up(RocketTilesKey, site) map { r => r.copy(core = r.core.copy(
    nPerfCounters = n
  ))}
})

class WithZynqConfig extends Config(
  new WithBootROM ++
  new WithZynqAdapter ++
  new With50Mhz ++
  new WithoutTLMonitors ++                                  // disable TL verification
  new WithNPerfCounters(10)
)

class BoomBenchmarkZynqConfig extends Config(
  new WithBenchmarkConfig ++
  new WithZynqConfig ++
  new boom.common.WithNBoomCores(1) ++                      // single-core
  new freechips.rocketchip.system.BaseConfig)

class RocketZynqConfig extends Config( // rocket should be able to run at ~80MHz in this config - needs to also be changed in clocking.vh
  new WithZynqConfig ++
  new WithNBigCores(1) ++                      // single-core Big Rocket
  new freechips.rocketchip.system.BaseConfig)

// for zc706_MIG
class With1GbRam extends Config(
  new WithExtMemSize(0x40000000L)
)

class WithL2Cache extends Config(
  new WithInclusiveCache(

  )
)
class WithL2TLB extends Config(
  new WithL2TLBs
)
class WithL2TLBs(entries: Int=1024) extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey) map (tile => tile.copy(
    core = tile.core.copy(nL2TLBEntries = entries)
  ))
  case BoomTilesKey => up(BoomTilesKey) map (tile => tile.copy(
    core = tile.core.copy(nL2TLBEntries = entries)
  ))
})

class WithBenchmarkConfig extends Config(
  new WithL2TLB ++
  new WithL2Cache ++
  new With1GbRam
)

// for zc706
class With768MbRam extends Config(
  new WithExtMemSize(0x30000000L)
)
// for upcoming combined version
class With1p5GbRam extends Config(
  new WithExtMemSize(0x60000000L) ++
  new WithNMemoryChannels(2)
)
