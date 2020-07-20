package wolverine

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.iotesters.PeekPokeTester
import chisel3.util.Queue
import wolverine.{RocketWolverineConfig, WolverineTop}

object WolverineTester extends App {
  println("Testing Wolverine Viewer")
  // does not stop with scratchpad
  //  implicit val p: Parameters = new RocketScratchpadWolverineConfig ++ Parameters.empty
  //  implicit val p: Parameters = new RocketAxiRamWolverineConfig ++ Parameters.empty
  implicit val p: Parameters = new RocketWolverineConfig ++ Parameters.empty
  iotesters.Driver.execute(Array("--backend-name", "verilator", "--generate-vcd-output", "on"), () => new WolverineTop()) {
    c =>
      new PeekPokeTester(c) {
        var cycle = 0
        val memCycles = 10

        case class MemRequest (
          cycle: Int,
          cmd: BigInt,
          sub: BigInt,
          size: BigInt,
          vadr: BigInt,
          rtnctl: BigInt,
          data: BigInt
        )
        var reqq = scala.collection.mutable.Queue[MemRequest]()
        val memSize = 0x40000000L
        val memBase = 0x20000000L
        val mem = new Array[Byte](memSize)// 1GB

        def processMem(): Unit = {
          val rqValid = peek(c.io.mcReqValid)
          if(rqValid != 0){
            val mr = MemRequest(
              cycle+memCycles,
              peek(c.io.mcReqCmd),
              peek(c.io.mcReqSCmd),
              peek(c.io.mcReqSize),
              peek(c.io.mcReqAddr),
              peek(c.io.mcReqRtnCtl),
              peek(c.io.mcReqData)
            )
            reqq.enqueue(mr)
          }
          poke(c.io.mcResValid, false.B)
          poke(c.io.mcResCmd, 0)
          poke(c.io.mcResSCmd, 0)
          poke(c.io.mcResData, 0)
          poke(c.io.mcResRtnCtl, 0)
          if(reqq.nonEmpty && reqq.head.cycle <= cycle){
            // TODO: handle response stall
            val mr = reqq.dequeue()
            val addr = mr.vadr - memBase
            assert(addr<memSize)
            assert(mr.sub == 0)
            mr.cmd.intValue() match {
              case 2 => { // write
                mr.size.intValue() match {
                  case 2 => {
                    for(i <- 0 until 4){
                      mem(addr.intValue()+i) = ((mr.data >> (8*i)) & 0xFF).byteValue()
                    }
                  }
                  case 3 => {
                    for(i <- 0 until 8){
                      mem(addr.intValue()+i) = ((mr.data >> (8*i)) & 0xFF).byteValue()
                    }
                  }
                  case _ => assert(false, "invalid size")
                }
                poke(c.io.mcResValid, true.B)
                poke(c.io.mcResCmd, 3)
                poke(c.io.mcResSCmd, 0)
                poke(c.io.mcResData, 0)
                poke(c.io.mcResRtnCtl, mr.rtnctl)
              }
              case 1 => {//read
                assert(mr.size == 3)
                var dt = BigInt(0)
                for(i <- 0 until 8){
                  dt |= BigInt(mem(addr.intValue()+i)&0xFF) << (i*8)
                }
                poke(c.io.mcResValid, true.B)
                poke(c.io.mcResCmd, 2)
                poke(c.io.mcResSCmd, 0)
                poke(c.io.mcResData, dt)
                poke(c.io.mcResRtnCtl, mr.rtnctl)
              }
              case _ => assert(false, "invalid command")
            }
          }
        }

        override def step(n: Int): Unit = {
          for(i <- 0 until n){
            processMem()
            cycle+=1
            super.step(1)
          }
        }

        def writeReg(id: Int, data: BigInt) {
          poke(c.io.dispRegID, id)
          poke(c.io.dispRegWrite, true)
          poke(c.io.dispRegWrData, data.U)
          step(1)
          poke(c.io.dispRegWrite, false)
        }

        def readReg(id: Int): BigInt = {
          poke(c.io.dispRegID, id)
          poke(c.io.dispRegRead, true)
          step(1)
          poke(c.io.dispRegRead, false)
          expect(c.io.dispRtnValid, true)
          peek(c.io.dispRtnData)
        }

        def writeCSR(id: Int, data: BigInt) {
          poke(c.io.csrAddr, id)
          poke(c.io.csrWrValid, true)
          poke(c.io.csrWrData, data.U)
          step(1)
          poke(c.io.csrWrValid, false)
        }

        def readCSR(id: Int): BigInt = {
          poke(c.io.csrAddr, id)
          poke(c.io.csrRdValid, true)
          step(1)
          poke(c.io.csrRdValid, false)
          expect(c.io.csrReadAck, true)
          peek(c.io.csrReadData)
        }

        def writeAXI(addr: Long, data: BigInt): Unit = {
          val tmp = BigInt(0x80000000L | addr) << 32 | data
          writeCSR(8, tmp)
          step(20)
        }

        def readAXI(addr: Long): BigInt = {
          val tmp = BigInt(0x00000000L | addr) << 32
          writeCSR(8, tmp)
          step(20)
          readCSR(8) & 0xFFFFFFFFL
        }

        expect(c.io.dispIdle, true)
        expect(c.io.dispStall, false)
        writeReg(1, memBase)
        readReg(1)
        assert(readReg(1) == memBase)
        assert(readCSR(1) == memBase)

        val axiBase = 0x43C00000L
        //reset
        writeAXI(axiBase + 0x10, 1)
        writeAXI(axiBase + 0x10, 0)
        //block device
        writeAXI(axiBase + 0x38, 0)
        writeAXI(axiBase + 0x3c, 0)

//        // SAI_CMD_WRITE
//        writeAXI(axiBase + 0x8, 1)
//        // addr 0x80000004
//        writeAXI(axiBase + 0x8, 0x80000004L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        // 0x0 (0+1=1) chunks
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        // chunk
//        writeAXI(axiBase + 0x8, 0xDEADBEEFL)
//
//        // SAI_CMD_READ
//        writeAXI(axiBase + 0x8, 0)
//        // addr 0x80000004
//        writeAXI(axiBase + 0x8, 0x80000004L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        // 0x0 (0+1=1) chunks
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)

        //transfer ibda.elf binary into memory
        // SAI_CMD_WRITE
        writeAXI(axiBase + 0x8, 1)
        // addr 0x80000000
        writeAXI(axiBase + 0x8, 0x80000000L)
        writeAXI(axiBase + 0x8, 0x0L)
        // 0x21 (33+1=34) chunks
        writeAXI(axiBase + 0x8, 0x21L)
        writeAXI(axiBase + 0x8, 0x0L)
        // .text and .data of ibda.elf
        writeAXI(axiBase + 0x8, 0x20637L)
        writeAXI(axiBase + 0x8, 0x16061bL)
        writeAXI(axiBase + 0x8, 0xe61613L)
        writeAXI(axiBase + 0x8, 0x10000593L)
        writeAXI(axiBase + 0x8, 0xb606b3L)
        writeAXI(axiBase + 0x8, 0x870315fdL)
        writeAXI(axiBase + 0x8, 0x1be30006L)
        writeAXI(axiBase + 0x8, 0x4285feb0L)
        writeAXI(axiBase + 0x8, 0x317L)
        writeAXI(axiBase + 0x8, 0x2030313L)

        writeAXI(axiBase + 0x8, 0x532023L)
        writeAXI(axiBase + 0x8, 0xa001L)
        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)

        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)

        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)

        // start execution
        // SAI_CMD_WRITE
        writeAXI(axiBase + 0x8, 1)
        // addr 0x2000000
        writeAXI(axiBase + 0x8, 0x2000000L)
        writeAXI(axiBase + 0x8, 0x0L)
        // 0x0 (0+1=1) chunks
        writeAXI(axiBase + 0x8, 0x0L)
        writeAXI(axiBase + 0x8, 0x0L)
        // 0x1
        writeAXI(axiBase + 0x8, 0x1L)

        var finished = false
        while (!finished) {
          // SAI_CMD_READ
          writeAXI(axiBase + 0x8, 0)
          // addr 0x80000040
          writeAXI(axiBase + 0x8, 0x80000040L)
          writeAXI(axiBase + 0x8, 0x0L)
          // 0x1 (1+1=2) chunks
          writeAXI(axiBase + 0x8, 0x1L)
          writeAXI(axiBase + 0x8, 0x0L)
          // wait until 2 chunks are available
          while (readAXI(axiBase + 0x4L) < 2) {}
          val c1 = readAXI(axiBase + 0x0L)
          val c2 = readAXI(axiBase + 0x0L)
          if (c1 != 0 || c2 != 0) {
            finished = true
          }
        }
      step(50)
    }
  }
}