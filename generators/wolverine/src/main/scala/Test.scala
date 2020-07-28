package wolverine

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.iotesters.PeekPokeTester
import chisel3.util.Queue

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
        var beat = 0

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
            val mr = reqq.head
            val addr = mr.vadr - memBase
            assert(addr<memSize)
            assert(mr.sub == 0)
            mr.cmd.intValue() match {
              case 2 => { // write
                reqq.dequeue()
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
                reqq.dequeue()
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
              case 6 => {//multi write
                reqq.dequeue()
                assert(mr.size == 3)
                val beats = if(mr.sub == 0) 8 else mr.sub
                for(i <- 0 until 8){
                  mem(addr.intValue()+i) = ((mr.data >> (8*i)) & 0xFF).byteValue()
                }
                beat += 1
                if(beat == beats) {
                  beat = 0
                  // respond when all beats are completed
                  poke(c.io.mcResValid, true.B)
                  poke(c.io.mcResCmd, 3)
                  poke(c.io.mcResSCmd, 0)
                  poke(c.io.mcResData, 0)
                  poke(c.io.mcResRtnCtl, mr.rtnctl)
                }
              }
              case 7 => {//multi read
                assert(mr.size == 3)
                assert(mr.sub == 0)
                var dt = BigInt(0)
                for(i <- 0 until 8){
                  dt |= BigInt(mem(addr.intValue()+i+beat*8)&0xFF) << (i*8)
                }
                poke(c.io.mcResValid, true.B)
                poke(c.io.mcResCmd, 7)
                poke(c.io.mcResSCmd, beat)
                poke(c.io.mcResData, dt)
                poke(c.io.mcResRtnCtl, mr.rtnctl)
                beat += 1
                if(beat == 8) {
                  beat = 0
                  // only dequeue once read si complete
                  reqq.dequeue()
                }
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

//        //transfer ibda.elf binary into memory
//        // SAI_CMD_WRITE
//        writeAXI(axiBase + 0x8, 1)
//        // addr 0x80000000
//        writeAXI(axiBase + 0x8, 0x80000000L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        // 0x21 (33+1=34) chunks
//        writeAXI(axiBase + 0x8, 0x21L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        // .text and .data of ibda.elf
//        writeAXI(axiBase + 0x8, 0x20637L)
//        writeAXI(axiBase + 0x8, 0x16061bL)
//        writeAXI(axiBase + 0x8, 0xe61613L)
//        writeAXI(axiBase + 0x8, 0x10000593L)
//        writeAXI(axiBase + 0x8, 0xb606b3L)
//        writeAXI(axiBase + 0x8, 0x870315fdL)
//        writeAXI(axiBase + 0x8, 0x1be30006L)
//        writeAXI(axiBase + 0x8, 0x4285feb0L)
//        writeAXI(axiBase + 0x8, 0x317L)
//        writeAXI(axiBase + 0x8, 0x2030313L)
//
//        writeAXI(axiBase + 0x8, 0x532023L)
//        writeAXI(axiBase + 0x8, 0xa001L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//
//        // start execution
//        // SAI_CMD_WRITE
//        writeAXI(axiBase + 0x8, 1)
//        // addr 0x2000000
//        writeAXI(axiBase + 0x8, 0x2000000L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        // 0x0 (0+1=1) chunks
//        writeAXI(axiBase + 0x8, 0x0L)
//        writeAXI(axiBase + 0x8, 0x0L)
//        // 0x1
//        writeAXI(axiBase + 0x8, 0x1L)
//
//        var finished = false
//        while (!finished) {
//          // SAI_CMD_READ
//          writeAXI(axiBase + 0x8, 0)
//          // addr 0x80000040
//          writeAXI(axiBase + 0x8, 0x80000040L)
//          writeAXI(axiBase + 0x8, 0x0L)
//          // 0x1 (1+1=2) chunks
//          writeAXI(axiBase + 0x8, 0x1L)
//          writeAXI(axiBase + 0x8, 0x0L)
//          // wait until 2 chunks are available
//          while (readAXI(axiBase + 0x4L) < 2) {}
//          val c1 = readAXI(axiBase + 0x0L)
//          val c2 = readAXI(axiBase + 0x0L)
//          if (c1 != 0 || c2 != 0) {
//            finished = true
//          }
//        }
//      step(50)


        // write
        writeAXI(axiBase + 0x8, 0x1L);
        // addr
        writeAXI(axiBase + 0x8, 0x80000000L);
        writeAXI(axiBase + 0x8, 0x0L);
        // len
        writeAXI(axiBase + 0x8, 0x5cL);
        writeAXI(axiBase + 0x8, 0x0L);
        // data
        writeAXI(axiBase + 0x8, 0xc0006fL);
        writeAXI(axiBase + 0x8, 0x2600206fL);
        writeAXI(axiBase + 0x8, 0x25c0206fL);
        writeAXI(axiBase + 0x8, 0x297L);
        writeAXI(axiBase + 0x8, 0xffc28293L);
        writeAXI(axiBase + 0x8, 0x30529073L);
        writeAXI(axiBase + 0x8, 0x9117L);
        writeAXI(axiBase + 0x8, 0x6b810113L);
        writeAXI(axiBase + 0x8, 0xf14022f3L);
        writeAXI(axiBase + 0x8, 0xc29293L);
        writeAXI(axiBase + 0x8, 0x510133L);
        writeAXI(axiBase + 0x8, 0x34011073L);
        writeAXI(axiBase + 0x8, 0x269020efL);
        writeAXI(axiBase + 0x8, 0x3517L);
        writeAXI(axiBase + 0x8, 0xa6850513L);
        writeAXI(axiBase + 0x8, 0x750206fL);
        writeAXI(axiBase + 0x8, 0x10853283L);
        writeAXI(axiBase + 0x8, 0x14129073L);
        writeAXI(axiBase + 0x8, 0x853083L);
        writeAXI(axiBase + 0x8, 0x1053103L);
        writeAXI(axiBase + 0x8, 0x1853183L);
        writeAXI(axiBase + 0x8, 0x2053203L);
        writeAXI(axiBase + 0x8, 0x2853283L);
        writeAXI(axiBase + 0x8, 0x3053303L);
        writeAXI(axiBase + 0x8, 0x3853383L);
        writeAXI(axiBase + 0x8, 0x4053403L);
        writeAXI(axiBase + 0x8, 0x4853483L);
        writeAXI(axiBase + 0x8, 0x5853583L);
        writeAXI(axiBase + 0x8, 0x6053603L);
        writeAXI(axiBase + 0x8, 0x6853683L);
        writeAXI(axiBase + 0x8, 0x7053703L);
        writeAXI(axiBase + 0x8, 0x7853783L);
        writeAXI(axiBase + 0x8, 0x8053803L);
        writeAXI(axiBase + 0x8, 0x8853883L);
        writeAXI(axiBase + 0x8, 0x9053903L);
        writeAXI(axiBase + 0x8, 0x9853983L);
        writeAXI(axiBase + 0x8, 0xa053a03L);
        writeAXI(axiBase + 0x8, 0xa853a83L);
        writeAXI(axiBase + 0x8, 0xb053b03L);
        writeAXI(axiBase + 0x8, 0xb853b83L);
        writeAXI(axiBase + 0x8, 0xc053c03L);
        writeAXI(axiBase + 0x8, 0xc853c83L);
        writeAXI(axiBase + 0x8, 0xd053d03L);
        writeAXI(axiBase + 0x8, 0xd853d83L);
        writeAXI(axiBase + 0x8, 0xe053e03L);
        writeAXI(axiBase + 0x8, 0xe853e83L);
        writeAXI(axiBase + 0x8, 0xf053f03L);
        writeAXI(axiBase + 0x8, 0xf853f83L);
        writeAXI(axiBase + 0x8, 0x5053503L);
        writeAXI(axiBase + 0x8, 0x10200073L);
        writeAXI(axiBase + 0x8, 0x14011173L);
        writeAXI(axiBase + 0x8, 0x113423L);
        writeAXI(axiBase + 0x8, 0x313c23L);
        writeAXI(axiBase + 0x8, 0x2413023L);
        writeAXI(axiBase + 0x8, 0x2513423L);
        writeAXI(axiBase + 0x8, 0x2613823L);
        writeAXI(axiBase + 0x8, 0x2713c23L);
        writeAXI(axiBase + 0x8, 0x4813023L);
        writeAXI(axiBase + 0x8, 0x4913423L);
        writeAXI(axiBase + 0x8, 0x4a13823L);
        writeAXI(axiBase + 0x8, 0x4b13c23L);
        writeAXI(axiBase + 0x8, 0x6c13023L);
        writeAXI(axiBase + 0x8, 0x6d13423L);
        writeAXI(axiBase + 0x8, 0x6e13823L);
        writeAXI(axiBase + 0x8, 0x6f13c23L);
        writeAXI(axiBase + 0x8, 0x9013023L);
        writeAXI(axiBase + 0x8, 0x9113423L);
        writeAXI(axiBase + 0x8, 0x9213823L);
        writeAXI(axiBase + 0x8, 0x9313c23L);
        writeAXI(axiBase + 0x8, 0xb413023L);
        writeAXI(axiBase + 0x8, 0xb513423L);
        writeAXI(axiBase + 0x8, 0xb613823L);
        writeAXI(axiBase + 0x8, 0xb713c23L);
        writeAXI(axiBase + 0x8, 0xd813023L);
        writeAXI(axiBase + 0x8, 0xd913423L);
        writeAXI(axiBase + 0x8, 0xda13823L);
        writeAXI(axiBase + 0x8, 0xdb13c23L);
        writeAXI(axiBase + 0x8, 0xfc13023L);
        writeAXI(axiBase + 0x8, 0xfd13423L);
        writeAXI(axiBase + 0x8, 0xfe13823L);
        writeAXI(axiBase + 0x8, 0xff13c23L);
        writeAXI(axiBase + 0x8, 0x140112f3L);
        writeAXI(axiBase + 0x8, 0x513823L);
        writeAXI(axiBase + 0x8, 0x100022f3L);
        writeAXI(axiBase + 0x8, 0x10513023L);
        writeAXI(axiBase + 0x8, 0x141022f3L);
        writeAXI(axiBase + 0x8, 0x10513423L);
        writeAXI(axiBase + 0x8, 0x143022f3L);
        writeAXI(axiBase + 0x8, 0x10513823L);
        writeAXI(axiBase + 0x8, 0x142022f3L);
        writeAXI(axiBase + 0x8, 0x10513c23L);
        writeAXI(axiBase + 0x8, 0x10513L);
        writeAXI(axiBase + 0x8, 0x4200206fL);

        // write
        writeAXI(axiBase + 0x8, 0x1L);
        // addr
        writeAXI(axiBase + 0x8, 0x80002000L);
        writeAXI(axiBase + 0x8, 0x0L);
        // len
        writeAXI(axiBase + 0x8, 0xffL);
        writeAXI(axiBase + 0x8, 0x0L);
        // data
        writeAXI(axiBase + 0x8, 0xc5e7b3L);
        writeAXI(axiBase + 0x8, 0xf567b3L);
        writeAXI(axiBase + 0x8, 0x77f793L);
        writeAXI(axiBase + 0x8, 0xc506b3L);
        writeAXI(axiBase + 0x8, 0x2078463L);
        writeAXI(axiBase + 0x8, 0xc58633L);
        writeAXI(axiBase + 0x8, 0x50793L);
        writeAXI(axiBase + 0x8, 0x2d57e63L);
        writeAXI(axiBase + 0x8, 0x5c703L);
        writeAXI(axiBase + 0x8, 0x158593L);
        writeAXI(axiBase + 0x8, 0x178793L);
        writeAXI(axiBase + 0x8, 0xfee78fa3L);
        writeAXI(axiBase + 0x8, 0xfec598e3L);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0xfed57ee3L);
        writeAXI(axiBase + 0x8, 0x50793L);
        writeAXI(axiBase + 0x8, 0x5b703L);
        writeAXI(axiBase + 0x8, 0x878793L);
        writeAXI(axiBase + 0x8, 0x858593L);
        writeAXI(axiBase + 0x8, 0xfee7bc23L);
        writeAXI(axiBase + 0x8, 0xfed7e8e3L);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0xc567b3L);
        writeAXI(axiBase + 0x8, 0x77f793L);
        writeAXI(axiBase + 0x8, 0xc50633L);
        writeAXI(axiBase + 0x8, 0xff5f593L);
        writeAXI(axiBase + 0x8, 0x78e63L);
        writeAXI(axiBase + 0x8, 0x50793L);
        writeAXI(axiBase + 0x8, 0x4c57263L);
        writeAXI(axiBase + 0x8, 0x178793L);
        writeAXI(axiBase + 0x8, 0xfeb78fa3L);
        writeAXI(axiBase + 0x8, 0xfef61ce3L);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0x859793L);
        writeAXI(axiBase + 0x8, 0xb7e5b3L);
        writeAXI(axiBase + 0x8, 0x1059793L);
        writeAXI(axiBase + 0x8, 0xb7e7b3L);
        writeAXI(axiBase + 0x8, 0x2079593L);
        writeAXI(axiBase + 0x8, 0xf5e5b3L);
        writeAXI(axiBase + 0x8, 0xfec572e3L);
        writeAXI(axiBase + 0x8, 0x50793L);
        writeAXI(axiBase + 0x8, 0x878793L);
        writeAXI(axiBase + 0x8, 0xfeb7bc23L);
        writeAXI(axiBase + 0x8, 0xfec7ece3L);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0x54783L);
        writeAXI(axiBase + 0x8, 0x78e63L);
        writeAXI(axiBase + 0x8, 0x50793L);
        writeAXI(axiBase + 0x8, 0x17c703L);
        writeAXI(axiBase + 0x8, 0x178793L);
        writeAXI(axiBase + 0x8, 0xfe071ce3L);
        writeAXI(axiBase + 0x8, 0x40a78533L);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0x513L);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0x54783L);
        writeAXI(axiBase + 0x8, 0x158593L);
        writeAXI(axiBase + 0x8, 0x150513L);
        writeAXI(axiBase + 0x8, 0xfff5c703L);
        writeAXI(axiBase + 0x8, 0x78a63L);
        writeAXI(axiBase + 0x8, 0xfee786e3L);
        writeAXI(axiBase + 0x8, 0x7851bL);
        writeAXI(axiBase + 0x8, 0x40e5053bL);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0x513L);
        writeAXI(axiBase + 0x8, 0xff5ff06fL);
        writeAXI(axiBase + 0x8, 0xb567b3L);
        writeAXI(axiBase + 0x8, 0x77f793L);
        writeAXI(axiBase + 0x8, 0x2079e63L);
        writeAXI(axiBase + 0x8, 0xff867813L);
        writeAXI(axiBase + 0x8, 0x1050833L);
        writeAXI(axiBase + 0x8, 0x3057863L);
        writeAXI(axiBase + 0x8, 0x50793L);
        writeAXI(axiBase + 0x8, 0x100006fL);
        writeAXI(axiBase + 0x8, 0x878793L);
        writeAXI(axiBase + 0x8, 0x858593L);
        writeAXI(axiBase + 0x8, 0x107f863L);
        writeAXI(axiBase + 0x8, 0x7b683L);
        writeAXI(axiBase + 0x8, 0x5b703L);
        writeAXI(axiBase + 0x8, 0xfee686e3L);
        writeAXI(axiBase + 0x8, 0x40a78533L);
        writeAXI(axiBase + 0x8, 0x40a60633L);
        writeAXI(axiBase + 0x8, 0x78513L);
        writeAXI(axiBase + 0x8, 0xc58633L);
        writeAXI(axiBase + 0x8, 0x140006fL);
        writeAXI(axiBase + 0x8, 0x5c703L);
        writeAXI(axiBase + 0x8, 0xfff54783L);
        writeAXI(axiBase + 0x8, 0x158593L);
        writeAXI(axiBase + 0x8, 0xe79a63L);
        writeAXI(axiBase + 0x8, 0x150513L);
        writeAXI(axiBase + 0x8, 0xfec596e3L);
        writeAXI(axiBase + 0x8, 0x513L);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0x40e7853bL);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0x50793L);
        writeAXI(axiBase + 0x8, 0x5c703L);
        writeAXI(axiBase + 0x8, 0x178793L);
        writeAXI(axiBase + 0x8, 0x158593L);
        writeAXI(axiBase + 0x8, 0xfee78fa3L);
        writeAXI(axiBase + 0x8, 0xfe0718e3L);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0x54703L);
        writeAXI(axiBase + 0x8, 0x2000693L);
        writeAXI(axiBase + 0x8, 0x50793L);
        writeAXI(axiBase + 0x8, 0xd71863L);
        writeAXI(axiBase + 0x8, 0x17c703L);
        writeAXI(axiBase + 0x8, 0x178793L);
        writeAXI(axiBase + 0x8, 0xfed70ce3L);
        writeAXI(axiBase + 0x8, 0x2d00693L);
        writeAXI(axiBase + 0x8, 0x6d70063L);
        writeAXI(axiBase + 0x8, 0x2b00693L);
        writeAXI(axiBase + 0x8, 0x4d70063L);
        writeAXI(axiBase + 0x8, 0x7c683L);
        writeAXI(axiBase + 0x8, 0x593L);
        writeAXI(axiBase + 0x8, 0x4068263L);
        writeAXI(axiBase + 0x8, 0x513L);
        writeAXI(axiBase + 0x8, 0x178793L);
        writeAXI(axiBase + 0x8, 0xfd06861bL);
        writeAXI(axiBase + 0x8, 0x251713L);
        writeAXI(axiBase + 0x8, 0x7c683L);
        writeAXI(axiBase + 0x8, 0xa70533L);
        writeAXI(axiBase + 0x8, 0x151513L);
        writeAXI(axiBase + 0x8, 0xa60533L);
        writeAXI(axiBase + 0x8, 0xfe0692e3L);
        writeAXI(axiBase + 0x8, 0x2058063L);
        writeAXI(axiBase + 0x8, 0x40a00533L);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0x17c683L);
        writeAXI(axiBase + 0x8, 0x593L);
        writeAXI(axiBase + 0x8, 0x178793L);
        writeAXI(axiBase + 0x8, 0xfc0692e3L);
        writeAXI(axiBase + 0x8, 0x513L);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0x17c683L);
        writeAXI(axiBase + 0x8, 0x100593L);
        writeAXI(axiBase + 0x8, 0x178793L);
        writeAXI(axiBase + 0x8, 0xfa0696e3L);
        writeAXI(axiBase + 0x8, 0x513L);
        writeAXI(axiBase + 0x8, 0xfe9ff06fL);
        writeAXI(axiBase + 0x8, 0xfffff797L);
        writeAXI(axiBase + 0x8, 0xdc878793L);
        writeAXI(axiBase + 0x8, 0x7b683L);
        writeAXI(axiBase + 0x8, 0x50713L);
        writeAXI(axiBase + 0x8, 0x68a63L);
        writeAXI(axiBase + 0x8, 0xfffff697L);
        writeAXI(axiBase + 0x8, 0xde06ba23L);
        writeAXI(axiBase + 0x8, 0x7b683L);
        writeAXI(axiBase + 0x8, 0xfe069ae3L);
        writeAXI(axiBase + 0x8, 0xe7b023L);
        writeAXI(axiBase + 0x8, 0x6fL);
        writeAXI(axiBase + 0x8, 0xff010113L);
        writeAXI(axiBase + 0x8, 0x34900513L);
        writeAXI(axiBase + 0x8, 0x113423L);
        writeAXI(axiBase + 0x8, 0xfc9ff0efL);
        writeAXI(axiBase + 0x8, 0xfe010113L);
        writeAXI(axiBase + 0x8, 0x810613L);
        writeAXI(axiBase + 0x8, 0x1710793L);
        writeAXI(axiBase + 0x8, 0x900813L);
        writeAXI(axiBase + 0x8, 0x80006fL);
        writeAXI(axiBase + 0x8, 0x70793L);
        writeAXI(axiBase + 0x8, 0xf57693L);
        writeAXI(axiBase + 0x8, 0x68713L);
        writeAXI(axiBase + 0x8, 0x3000593L);
        writeAXI(axiBase + 0x8, 0xd87463L);
        writeAXI(axiBase + 0x8, 0x5700593L);
        writeAXI(axiBase + 0x8, 0xb7073bL);
        writeAXI(axiBase + 0x8, 0xe78023L);
        writeAXI(axiBase + 0x8, 0x455513L);
        writeAXI(axiBase + 0x8, 0xfff78713L);
        writeAXI(axiBase + 0x8, 0xfcf61ce3L);
        writeAXI(axiBase + 0x8, 0x814783L);
        writeAXI(axiBase + 0x8, 0x10c23L);
        writeAXI(axiBase + 0x8, 0x4078263L);
        writeAXI(axiBase + 0x8, 0x10100513L);
        writeAXI(axiBase + 0x8, 0x60693L);
        writeAXI(axiBase + 0x8, 0xfffff717L);
        writeAXI(axiBase + 0x8, 0xd3870713L);
        writeAXI(axiBase + 0x8, 0x3051513L);
        writeAXI(axiBase + 0x8, 0x73583L);
        writeAXI(axiBase + 0x8, 0x168693L);
        writeAXI(axiBase + 0x8, 0xa7e633L);
        writeAXI(axiBase + 0x8, 0x58a63L);
        writeAXI(axiBase + 0x8, 0xfffff797L);
        writeAXI(axiBase + 0x8, 0xd407be23L);
        writeAXI(axiBase + 0x8, 0x73783L);
        writeAXI(axiBase + 0x8, 0xfe079ae3L);
        writeAXI(axiBase + 0x8, 0x6c783L);
        writeAXI(axiBase + 0x8, 0xc73023L);
        writeAXI(axiBase + 0x8, 0xfc079ce3L);
        writeAXI(axiBase + 0x8, 0x2010113L);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0xfffff6b7L);
        writeAXI(axiBase + 0x8, 0xd50733L);
        writeAXI(axiBase + 0x8, 0x3e7b7L);
        writeAXI(axiBase + 0x8, 0x14f77063L);
        writeAXI(axiBase + 0x8, 0xc55893L);
        writeAXI(axiBase + 0x8, 0x60088813L);
        writeAXI(axiBase + 0x8, 0x2617L);
        writeAXI(axiBase + 0x8, 0xce060613L);
        writeAXI(axiBase + 0x8, 0x381793L);
        writeAXI(axiBase + 0x8, 0xf607b3L);
        writeAXI(axiBase + 0x8, 0x7b703L);
        writeAXI(axiBase + 0x8, 0xd57533L);
        writeAXI(axiBase + 0x8, 0x2070663L);
        writeAXI(axiBase + 0x8, 0x4077693L);
        writeAXI(axiBase + 0x8, 0xe068c63L);
        writeAXI(axiBase + 0x8, 0x8077693L);
        writeAXI(axiBase + 0x8, 0x16069263L);
        writeAXI(axiBase + 0x8, 0xf00693L);
        writeAXI(axiBase + 0x8, 0x14d59e63L);
        writeAXI(axiBase + 0x8, 0x8076713L);
        writeAXI(axiBase + 0x8, 0xe7b023L);
        writeAXI(axiBase + 0x8, 0x12050073L);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0x6697L);
        writeAXI(axiBase + 0x8, 0x48468693L);
        writeAXI(axiBase + 0x8, 0x6b703L);
        writeAXI(axiBase + 0x8, 0x18070463L);
        writeAXI(axiBase + 0x8, 0x873783L);
        writeAXI(axiBase + 0x8, 0x6597L);
        writeAXI(axiBase + 0x8, 0x4685b583L);
        writeAXI(axiBase + 0x8, 0xf6b023L);
        writeAXI(axiBase + 0x8, 0xcb78263L);
        writeAXI(axiBase + 0x8, 0x73783L);
        writeAXI(axiBase + 0x8, 0x381593L);
        writeAXI(axiBase + 0x8, 0xb605b3L);
        writeAXI(axiBase + 0x8, 0xc7d793L);
        writeAXI(axiBase + 0x8, 0xa79793L);
        writeAXI(axiBase + 0x8, 0xdf7e313L);
        writeAXI(axiBase + 0x8, 0x1f7e693L);
        writeAXI(axiBase + 0x8, 0x65b023L);
        writeAXI(axiBase + 0x8, 0x12050073L);
        writeAXI(axiBase + 0x8, 0x6797L);
        writeAXI(axiBase + 0x8, 0x4478793L);
        writeAXI(axiBase + 0x8, 0x489893L);
        writeAXI(axiBase + 0x8, 0x11788b3L);
        writeAXI(axiBase + 0x8, 0x8b783L);
        writeAXI(axiBase + 0x8, 0x18079263L);
        writeAXI(axiBase + 0x8, 0x73783L);
        writeAXI(axiBase + 0x8, 0xf8b023L);
        writeAXI(axiBase + 0x8, 0x873783L);
        writeAXI(axiBase + 0x8, 0xf8b423L);
        writeAXI(axiBase + 0x8, 0x408b7L);
        writeAXI(axiBase + 0x8, 0x1008a8f3L);
        writeAXI(axiBase + 0x8, 0xffe007b7L);
        writeAXI(axiBase + 0x8, 0xf507b3L);
        writeAXI(axiBase + 0x8, 0x15b7L);
        writeAXI(axiBase + 0x8, 0x50713L);
        writeAXI(axiBase + 0x8, 0xb785b3L);
        writeAXI(axiBase + 0x8, 0x7bf03L);
        writeAXI(axiBase + 0x8, 0x87be83L);
        writeAXI(axiBase + 0x8, 0x107be03L);
        writeAXI(axiBase + 0x8, 0x187b303L);

        // write
        writeAXI(axiBase + 0x8, 0x1L);
        // addr
        writeAXI(axiBase + 0x8, 0x80002400L);
        writeAXI(axiBase + 0x8, 0x0L);
        // len
        writeAXI(axiBase + 0x8, 0xffL);
        writeAXI(axiBase + 0x8, 0x0L);
        // data
        writeAXI(axiBase + 0x8, 0x1e73023L);
        writeAXI(axiBase + 0x8, 0x1d73423L);
        writeAXI(axiBase + 0x8, 0x1c73823L);
        writeAXI(axiBase + 0x8, 0x673c23L);
        writeAXI(axiBase + 0x8, 0x2078793L);
        writeAXI(axiBase + 0x8, 0x2070713L);
        writeAXI(axiBase + 0x8, 0xfcb79ce3L);
        writeAXI(axiBase + 0x8, 0x10089073L);
        writeAXI(axiBase + 0x8, 0x381813L);
        writeAXI(axiBase + 0x8, 0x1060633L);
        writeAXI(axiBase + 0x8, 0xd63023L);
        writeAXI(axiBase + 0x8, 0x12050073L);
        writeAXI(axiBase + 0x8, 0x100fL);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0x4076713L);
        writeAXI(axiBase + 0x8, 0xe7b023L);
        writeAXI(axiBase + 0x8, 0x12050073L);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0x6797L);
        writeAXI(axiBase + 0x8, 0x3807bc23L);
        writeAXI(axiBase + 0x8, 0xf39ff06fL);
        writeAXI(axiBase + 0x8, 0x10100613L);
        writeAXI(axiBase + 0x8, 0x4100713L);
        writeAXI(axiBase + 0x8, 0x697L);
        writeAXI(axiBase + 0x8, 0x6cc68693L);
        writeAXI(axiBase + 0x8, 0xfffff797L);
        writeAXI(axiBase + 0x8, 0xb9c78793L);
        writeAXI(axiBase + 0x8, 0x3061613L);
        writeAXI(axiBase + 0x8, 0x7b503L);
        writeAXI(axiBase + 0x8, 0x168693L);
        writeAXI(axiBase + 0x8, 0xc765b3L);
        writeAXI(axiBase + 0x8, 0x50a63L);
        writeAXI(axiBase + 0x8, 0xfffff717L);
        writeAXI(axiBase + 0x8, 0xbc073023L);
        writeAXI(axiBase + 0x8, 0x7b703L);
        writeAXI(axiBase + 0x8, 0xfe071ae3L);
        writeAXI(axiBase + 0x8, 0x6c703L);
        writeAXI(axiBase + 0x8, 0xb7b023L);
        writeAXI(axiBase + 0x8, 0xfc071ce3L);
        writeAXI(axiBase + 0x8, 0xff010113L);
        writeAXI(axiBase + 0x8, 0x300513L);
        writeAXI(axiBase + 0x8, 0x113423L);
        writeAXI(axiBase + 0x8, 0xd91ff0efL);
        writeAXI(axiBase + 0x8, 0x10100613L);
        writeAXI(axiBase + 0x8, 0x4100713L);
        writeAXI(axiBase + 0x8, 0x697L);
        writeAXI(axiBase + 0x8, 0x6bc68693L);
        writeAXI(axiBase + 0x8, 0xfffff797L);
        writeAXI(axiBase + 0x8, 0xb4478793L);
        writeAXI(axiBase + 0x8, 0x3061613L);
        writeAXI(axiBase + 0x8, 0x7b503L);
        writeAXI(axiBase + 0x8, 0x168693L);
        writeAXI(axiBase + 0x8, 0xc765b3L);
        writeAXI(axiBase + 0x8, 0x50a63L);
        writeAXI(axiBase + 0x8, 0xfffff717L);
        writeAXI(axiBase + 0x8, 0xb6073423L);
        writeAXI(axiBase + 0x8, 0x7b703L);
        writeAXI(axiBase + 0x8, 0xfe071ae3L);
        writeAXI(axiBase + 0x8, 0x6c703L);
        writeAXI(axiBase + 0x8, 0xb7b023L);
        writeAXI(axiBase + 0x8, 0xfc071ce3L);
        writeAXI(axiBase + 0x8, 0xfa9ff06fL);
        writeAXI(axiBase + 0x8, 0x10100693L);
        writeAXI(axiBase + 0x8, 0x4100713L);
        writeAXI(axiBase + 0x8, 0x617L);
        writeAXI(axiBase + 0x8, 0x6b860613L);
        writeAXI(axiBase + 0x8, 0xfffff797L);
        writeAXI(axiBase + 0x8, 0xaf878793L);
        writeAXI(axiBase + 0x8, 0x3069693L);
        writeAXI(axiBase + 0x8, 0x7b503L);
        writeAXI(axiBase + 0x8, 0x160613L);
        writeAXI(axiBase + 0x8, 0xd765b3L);
        writeAXI(axiBase + 0x8, 0x50a63L);
        writeAXI(axiBase + 0x8, 0xfffff717L);
        writeAXI(axiBase + 0x8, 0xb0073e23L);
        writeAXI(axiBase + 0x8, 0x7b703L);
        writeAXI(axiBase + 0x8, 0xfe071ae3L);
        writeAXI(axiBase + 0x8, 0x64703L);
        writeAXI(axiBase + 0x8, 0xb7b023L);
        writeAXI(axiBase + 0x8, 0xfc071ce3L);
        writeAXI(axiBase + 0x8, 0xf5dff06fL);
        writeAXI(axiBase + 0x8, 0x10100693L);
        writeAXI(axiBase + 0x8, 0x4100713L);
        writeAXI(axiBase + 0x8, 0x617L);
        writeAXI(axiBase + 0x8, 0x68460613L);
        writeAXI(axiBase + 0x8, 0xfffff797L);
        writeAXI(axiBase + 0x8, 0xaac78793L);
        writeAXI(axiBase + 0x8, 0x3069693L);
        writeAXI(axiBase + 0x8, 0x7b503L);
        writeAXI(axiBase + 0x8, 0x160613L);
        writeAXI(axiBase + 0x8, 0xd765b3L);
        writeAXI(axiBase + 0x8, 0x50a63L);
        writeAXI(axiBase + 0x8, 0xfffff717L);
        writeAXI(axiBase + 0x8, 0xac073823L);
        writeAXI(axiBase + 0x8, 0x7b703L);
        writeAXI(axiBase + 0x8, 0xfe071ae3L);
        writeAXI(axiBase + 0x8, 0x64703L);
        writeAXI(axiBase + 0x8, 0xb7b023L);
        writeAXI(axiBase + 0x8, 0xfc071ce3L);
        writeAXI(axiBase + 0x8, 0xf11ff06fL);
        writeAXI(axiBase + 0x8, 0x11853583L);
        writeAXI(axiBase + 0x8, 0xf8010113L);
        writeAXI(axiBase + 0x8, 0x6813823L);
        writeAXI(axiBase + 0x8, 0x6113c23L);
        writeAXI(axiBase + 0x8, 0x6913423L);
        writeAXI(axiBase + 0x8, 0x7213023L);
        writeAXI(axiBase + 0x8, 0x5313c23L);
        writeAXI(axiBase + 0x8, 0x5413823L);
        writeAXI(axiBase + 0x8, 0x5513423L);
        writeAXI(axiBase + 0x8, 0x5613023L);
        writeAXI(axiBase + 0x8, 0x3713c23L);
        writeAXI(axiBase + 0x8, 0x3813823L);
        writeAXI(axiBase + 0x8, 0x3913423L);
        writeAXI(axiBase + 0x8, 0x3a13023L);
        writeAXI(axiBase + 0x8, 0x1b13c23L);
        writeAXI(axiBase + 0x8, 0x800793L);
        writeAXI(axiBase + 0x8, 0x50413L);
        writeAXI(axiBase + 0x8, 0x12f58663L);
        writeAXI(axiBase + 0x8, 0x200793L);
        writeAXI(axiBase + 0x8, 0x6f58063L);
        writeAXI(axiBase + 0x8, 0xff458793L);
        writeAXI(axiBase + 0x8, 0x100713L);
        writeAXI(axiBase + 0x8, 0xf77663L);
        writeAXI(axiBase + 0x8, 0xf00793L);
        writeAXI(axiBase + 0x8, 0x1cf59e63L);
        writeAXI(axiBase + 0x8, 0x11043503L);
        writeAXI(axiBase + 0x8, 0xd11ff0efL);
        writeAXI(axiBase + 0x8, 0x40513L);
        writeAXI(axiBase + 0x8, 0x7013403L);
        writeAXI(axiBase + 0x8, 0x7813083L);
        writeAXI(axiBase + 0x8, 0x6813483L);
        writeAXI(axiBase + 0x8, 0x6013903L);
        writeAXI(axiBase + 0x8, 0x5813983L);
        writeAXI(axiBase + 0x8, 0x5013a03L);
        writeAXI(axiBase + 0x8, 0x4813a83L);
        writeAXI(axiBase + 0x8, 0x4013b03L);
        writeAXI(axiBase + 0x8, 0x3813b83L);
        writeAXI(axiBase + 0x8, 0x3013c03L);
        writeAXI(axiBase + 0x8, 0x2813c83L);
        writeAXI(axiBase + 0x8, 0x2013d03L);
        writeAXI(axiBase + 0x8, 0x1813d83L);
        writeAXI(axiBase + 0x8, 0x8010113L);
        writeAXI(axiBase + 0x8, 0xa09fd06fL);
        writeAXI(axiBase + 0x8, 0x10853703L);
        writeAXI(axiBase + 0x8, 0x377793L);
        writeAXI(axiBase + 0x8, 0x6079863L);
        writeAXI(axiBase + 0x8, 0x8007efL);
        writeAXI(axiBase + 0x8, 0x301073L);
        writeAXI(axiBase + 0x8, 0x72703L);
        writeAXI(axiBase + 0x8, 0x7a783L);
        writeAXI(axiBase + 0x8, 0x4f70a63L);
        writeAXI(axiBase + 0x8, 0x10100513L);
        writeAXI(axiBase + 0x8, 0x4100793L);
        writeAXI(axiBase + 0x8, 0x697L);
        writeAXI(axiBase + 0x8, 0x64468693L);
        writeAXI(axiBase + 0x8, 0xfffff717L);
        writeAXI(axiBase + 0x8, 0x99470713L);
        writeAXI(axiBase + 0x8, 0x3051513L);
        writeAXI(axiBase + 0x8, 0x73583L);
        writeAXI(axiBase + 0x8, 0x168693L);
        writeAXI(axiBase + 0x8, 0xa7e633L);
        writeAXI(axiBase + 0x8, 0x58a63L);
        writeAXI(axiBase + 0x8, 0xfffff797L);
        writeAXI(axiBase + 0x8, 0x9a07bc23L);
        writeAXI(axiBase + 0x8, 0x73783L);
        writeAXI(axiBase + 0x8, 0xfe079ae3L);
        writeAXI(axiBase + 0x8, 0x6c783L);
        writeAXI(axiBase + 0x8, 0xc73023L);
        writeAXI(axiBase + 0x8, 0xfc079ce3L);
        writeAXI(axiBase + 0x8, 0x300513L);
        writeAXI(axiBase + 0x8, 0xb91ff0efL);
        writeAXI(axiBase + 0x8, 0x100513L);
        writeAXI(axiBase + 0x8, 0xb89ff0efL);
        writeAXI(axiBase + 0x8, 0x10100793L);
        writeAXI(axiBase + 0x8, 0x617L);
        writeAXI(axiBase + 0x8, 0x5c860613L);
        writeAXI(axiBase + 0x8, 0x4100693L);
        writeAXI(axiBase + 0x8, 0xfffff717L);
        writeAXI(axiBase + 0x8, 0x93c70713L);
        writeAXI(axiBase + 0x8, 0x3079793L);
        writeAXI(axiBase + 0x8, 0x73503L);
        writeAXI(axiBase + 0x8, 0x160613L);
        writeAXI(axiBase + 0x8, 0xf6e5b3L);
        writeAXI(axiBase + 0x8, 0x50a63L);
        writeAXI(axiBase + 0x8, 0xfffff697L);
        writeAXI(axiBase + 0x8, 0x9606b023L);
        writeAXI(axiBase + 0x8, 0x73683L);
        writeAXI(axiBase + 0x8, 0xfe069ae3L);
        writeAXI(axiBase + 0x8, 0x64683L);
        writeAXI(axiBase + 0x8, 0xb73023L);
        writeAXI(axiBase + 0x8, 0xfc069ce3L);
        writeAXI(axiBase + 0x8, 0xfa9ff06fL);
        writeAXI(axiBase + 0x8, 0x5052903L);
        writeAXI(axiBase + 0x8, 0x1c37L);
        writeAXI(axiBase + 0x8, 0x6497L);
        writeAXI(axiBase + 0x8, 0xce848493L);
        writeAXI(axiBase + 0x8, 0x2b97L);
        writeAXI(axiBase + 0x8, 0x8f0b8b93L);
        writeAXI(axiBase + 0x8, 0x40b37L);
        writeAXI(axiBase + 0x8, 0xffe00ab7L);
        writeAXI(axiBase + 0x8, 0x6d97L);
        writeAXI(axiBase + 0x8, 0xc0d8d93L);
        writeAXI(axiBase + 0x8, 0x6a17L);
        writeAXI(axiBase + 0x8, 0xc0a0a13L);
        writeAXI(axiBase + 0x8, 0x3f9b7L);
        writeAXI(axiBase + 0x8, 0x180006fL);
        writeAXI(axiBase + 0x8, 0xf73423L);
        writeAXI(axiBase + 0x8, 0xfdb023L);
        writeAXI(axiBase + 0x8, 0x17b7L);
        writeAXI(axiBase + 0x8, 0xfc0c33L);
        writeAXI(axiBase + 0x8, 0x173c0063L);
        writeAXI(axiBase + 0x8, 0xcc5793L);
        writeAXI(axiBase + 0x8, 0x479413L);
        writeAXI(axiBase + 0x8, 0x848733L);
        writeAXI(axiBase + 0x8, 0x73703L);
        writeAXI(axiBase + 0x8, 0xfe0702e3L);
        writeAXI(axiBase + 0x8, 0x60078793L);
        writeAXI(axiBase + 0x8, 0x379793L);
        writeAXI(axiBase + 0x8, 0xfb87b3L);
        writeAXI(axiBase + 0x8, 0x7bc83L);
        writeAXI(axiBase + 0x8, 0x40cf793L);
        writeAXI(axiBase + 0x8, 0xe078663L);
        writeAXI(axiBase + 0x8, 0x100b2d73L);
        writeAXI(axiBase + 0x8, 0x1637L);
        writeAXI(axiBase + 0x8, 0x15c05b3L);
        writeAXI(axiBase + 0x8, 0xc0513L);
        writeAXI(axiBase + 0x8, 0xb13423L);
        writeAXI(axiBase + 0x8, 0x985ff0efL);
        writeAXI(axiBase + 0x8, 0x50e63L);
        writeAXI(axiBase + 0x8, 0x80cfc93L);
        writeAXI(axiBase + 0x8, 0x813583L);
        writeAXI(axiBase + 0x8, 0x60c8e63L);
        writeAXI(axiBase + 0x8, 0x1637L);
        writeAXI(axiBase + 0x8, 0xc0513L);
        writeAXI(axiBase + 0x8, 0x859ff0efL);
        writeAXI(axiBase + 0x8, 0x8487b3L);
        writeAXI(axiBase + 0x8, 0x100d1073L);
        writeAXI(axiBase + 0x8, 0xdb703L);
        writeAXI(axiBase + 0x8, 0x7b023L);
        writeAXI(axiBase + 0x8, 0xf6071ee3L);
        writeAXI(axiBase + 0x8, 0xfdb023L);
        writeAXI(axiBase + 0x8, 0xfa3023L);
        writeAXI(axiBase + 0x8, 0xf79ff06fL);
        writeAXI(axiBase + 0x8, 0x10100793L);
        writeAXI(axiBase + 0x8, 0x4100613L);
        writeAXI(axiBase + 0x8, 0x697L);
        writeAXI(axiBase + 0x8, 0x50468693L);
        writeAXI(axiBase + 0x8, 0xfffff717L);
        writeAXI(axiBase + 0x8, 0x82470713L);
        writeAXI(axiBase + 0x8, 0x3079793L);
        writeAXI(axiBase + 0x8, 0x73503L);
        writeAXI(axiBase + 0x8, 0x168693L);
        writeAXI(axiBase + 0x8, 0xf665b3L);
        writeAXI(axiBase + 0x8, 0x50a63L);
        writeAXI(axiBase + 0x8, 0xfffff617L);
        writeAXI(axiBase + 0x8, 0x84063423L);

        // write
        writeAXI(axiBase + 0x8, 0x1L);
        // addr
        writeAXI(axiBase + 0x8, 0x80002800L);
        writeAXI(axiBase + 0x8, 0x0L);
        // len
        writeAXI(axiBase + 0x8, 0xffL);
        writeAXI(axiBase + 0x8, 0x0L);
        // data
        writeAXI(axiBase + 0x8, 0x73603L);
        writeAXI(axiBase + 0x8, 0xfe061ae3L);
        writeAXI(axiBase + 0x8, 0x6c603L);
        writeAXI(axiBase + 0x8, 0xb73023L);
        writeAXI(axiBase + 0x8, 0xfc061ce3L);
        writeAXI(axiBase + 0x8, 0xe91ff06fL);
        writeAXI(axiBase + 0x8, 0x10100793L);
        writeAXI(axiBase + 0x8, 0x4100613L);
        writeAXI(axiBase + 0x8, 0x697L);
        writeAXI(axiBase + 0x8, 0x42868693L);
        writeAXI(axiBase + 0x8, 0xffffe717L);
        writeAXI(axiBase + 0x8, 0x7d870713L);
        writeAXI(axiBase + 0x8, 0x3079793L);
        writeAXI(axiBase + 0x8, 0x168693L);
        writeAXI(axiBase + 0x8, 0xf665b3L);
        writeAXI(axiBase + 0x8, 0xc0006fL);
        writeAXI(axiBase + 0x8, 0xfffff617L);
        writeAXI(axiBase + 0x8, 0x80063023L);
        writeAXI(axiBase + 0x8, 0x73603L);
        writeAXI(axiBase + 0x8, 0xfe061ae3L);
        writeAXI(axiBase + 0x8, 0x6c603L);
        writeAXI(axiBase + 0x8, 0xb73023L);
        writeAXI(axiBase + 0x8, 0xfc061ee3L);
        writeAXI(axiBase + 0x8, 0xe49ff06fL);
        writeAXI(axiBase + 0x8, 0x10100793L);
        writeAXI(axiBase + 0x8, 0x4100613L);
        writeAXI(axiBase + 0x8, 0x697L);
        writeAXI(axiBase + 0x8, 0x3a868693L);
        writeAXI(axiBase + 0x8, 0xffffe717L);
        writeAXI(axiBase + 0x8, 0x79070713L);
        writeAXI(axiBase + 0x8, 0x3079793L);
        writeAXI(axiBase + 0x8, 0x168693L);
        writeAXI(axiBase + 0x8, 0xf665b3L);
        writeAXI(axiBase + 0x8, 0xc0006fL);
        writeAXI(axiBase + 0x8, 0xffffe617L);
        writeAXI(axiBase + 0x8, 0x7a063c23L);
        writeAXI(axiBase + 0x8, 0x73603L);
        writeAXI(axiBase + 0x8, 0xfe061ae3L);
        writeAXI(axiBase + 0x8, 0x6c603L);
        writeAXI(axiBase + 0x8, 0xb73023L);
        writeAXI(axiBase + 0x8, 0xfc061ee3L);
        writeAXI(axiBase + 0x8, 0xe01ff06fL);
        writeAXI(axiBase + 0x8, 0x90513L);
        writeAXI(axiBase + 0x8, 0x98dff0efL);
        writeAXI(axiBase + 0x8, 0xf14027f3L);
        writeAXI(axiBase + 0x8, 0x18079463L);
        writeAXI(axiBase + 0x8, 0x3797L);
        writeAXI(axiBase + 0x8, 0x74878793L);
        writeAXI(axiBase + 0x8, 0xed010113L);
        writeAXI(axiBase + 0x8, 0xc7d793L);
        writeAXI(axiBase + 0x8, 0x12813023L);
        writeAXI(axiBase + 0x8, 0x4817L);
        writeAXI(axiBase + 0x8, 0x73480813L);
        writeAXI(axiBase + 0x8, 0x50413L);
        writeAXI(axiBase + 0x8, 0xa79793L);
        writeAXI(axiBase + 0x8, 0x2517L);
        writeAXI(axiBase + 0x8, 0x72450513L);
        writeAXI(axiBase + 0x8, 0x12113423L);
        writeAXI(axiBase + 0x8, 0xc55693L);
        writeAXI(axiBase + 0x8, 0xc85713L);
        writeAXI(axiBase + 0x8, 0x17e793L);
        writeAXI(axiBase + 0x8, 0x1897L);
        writeAXI(axiBase + 0x8, 0x70c88893L);
        writeAXI(axiBase + 0x8, 0xfff00613L);
        writeAXI(axiBase + 0x8, 0xfef53c23L);
        writeAXI(axiBase + 0x8, 0xa69693L);
        writeAXI(axiBase + 0x8, 0xa71713L);
        writeAXI(axiBase + 0x8, 0x200007b7L);
        writeAXI(axiBase + 0x8, 0x16e693L);
        writeAXI(axiBase + 0x8, 0x176713L);
        writeAXI(axiBase + 0x8, 0xc8d593L);
        writeAXI(axiBase + 0x8, 0x3f61313L);
        writeAXI(axiBase + 0x8, 0xcf78793L);
        writeAXI(axiBase + 0x8, 0xfef83c23L);
        writeAXI(axiBase + 0x8, 0xd8b023L);
        writeAXI(axiBase + 0x8, 0xe53023L);
        writeAXI(axiBase + 0x8, 0x65e7b3L);
        writeAXI(axiBase + 0x8, 0x18079073L);
        writeAXI(axiBase + 0x8, 0x1f00793L);
        writeAXI(axiBase + 0x8, 0xb65613L);
        writeAXI(axiBase + 0x8, 0x297L);
        writeAXI(axiBase + 0x8, 0x1428293L);
        writeAXI(axiBase + 0x8, 0x305292f3L);
        writeAXI(axiBase + 0x8, 0x3b061073L);
        writeAXI(axiBase + 0x8, 0x3a079073L);
        writeAXI(axiBase + 0x8, 0xbff00813L);
        writeAXI(axiBase + 0x8, 0x1581813L);
        writeAXI(axiBase + 0x8, 0xffffd797L);
        writeAXI(axiBase + 0x8, 0x76c78793L);
        writeAXI(axiBase + 0x8, 0x10787b3L);
        writeAXI(axiBase + 0x8, 0x10579073L);
        writeAXI(axiBase + 0x8, 0x340027f3L);
        writeAXI(axiBase + 0x8, 0x10787b3L);
        writeAXI(axiBase + 0x8, 0x14079073L);
        writeAXI(axiBase + 0x8, 0xb7b7L);
        writeAXI(axiBase + 0x8, 0x1007879bL);
        writeAXI(axiBase + 0x8, 0x30279073L);
        writeAXI(axiBase + 0x8, 0x1e7b7L);
        writeAXI(axiBase + 0x8, 0x30079073L);
        writeAXI(axiBase + 0x8, 0x30405073L);
        writeAXI(axiBase + 0x8, 0x5697L);
        writeAXI(axiBase + 0x8, 0x67068693L);
        writeAXI(axiBase + 0x8, 0x10687b3L);
        writeAXI(axiBase + 0x8, 0x3e078713L);
        writeAXI(axiBase + 0x8, 0x6617L);
        writeAXI(axiBase + 0x8, 0xe4f63423L);
        writeAXI(axiBase + 0x8, 0x6797L);
        writeAXI(axiBase + 0x8, 0xe2e7bc23L);
        writeAXI(axiBase + 0x8, 0x6317L);
        writeAXI(axiBase + 0x8, 0xa4030313L);
        writeAXI(axiBase + 0x8, 0x3e00793L);
        writeAXI(axiBase + 0x8, 0x808b7L);
        writeAXI(axiBase + 0x8, 0x1080813L);
        writeAXI(axiBase + 0x8, 0x3f7871bL);
        writeAXI(axiBase + 0x8, 0x2071713L);
        writeAXI(axiBase + 0x8, 0x17d61bL);
        writeAXI(axiBase + 0x8, 0x2075713L);
        writeAXI(axiBase + 0x8, 0xc7c7b3L);
        writeAXI(axiBase + 0x8, 0x1170733L);
        writeAXI(axiBase + 0x8, 0x10685b3L);
        writeAXI(axiBase + 0x8, 0xc71713L);
        writeAXI(axiBase + 0x8, 0x57979bL);
        writeAXI(axiBase + 0x8, 0xe6b023L);
        writeAXI(axiBase + 0x8, 0xb6b423L);
        writeAXI(axiBase + 0x8, 0x207f793L);
        writeAXI(axiBase + 0x8, 0x1068693L);
        writeAXI(axiBase + 0x8, 0xc7e7b3L);
        writeAXI(axiBase + 0x8, 0xfcd314e3L);
        writeAXI(axiBase + 0x8, 0x12000613L);
        writeAXI(axiBase + 0x8, 0x593L);
        writeAXI(axiBase + 0x8, 0x10513L);
        writeAXI(axiBase + 0x8, 0x6797L);
        writeAXI(axiBase + 0x8, 0x9c07be23L);
        writeAXI(axiBase + 0x8, 0xe48ff0efL);
        writeAXI(axiBase + 0x8, 0x800007b7L);
        writeAXI(axiBase + 0x8, 0xf40433L);
        writeAXI(axiBase + 0x8, 0x10513L);
        writeAXI(axiBase + 0x8, 0x10813423L);
        writeAXI(axiBase + 0x8, 0xe18fd0efL);
        writeAXI(axiBase + 0x8, 0x12813083L);
        writeAXI(axiBase + 0x8, 0x12013403L);
        writeAXI(axiBase + 0x8, 0x13010113L);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0xf6267b7L);
        writeAXI(axiBase + 0x8, 0x805b7L);
        writeAXI(axiBase + 0x8, 0x79178793L);
        writeAXI(axiBase + 0x8, 0xffc58593L);
        writeAXI(axiBase + 0x8, 0x100613L);
        writeAXI(axiBase + 0x8, 0xb7f733L);
        writeAXI(axiBase + 0x8, 0x1f61613L);
        writeAXI(axiBase + 0x8, 0x17f693L);
        writeAXI(axiBase + 0x8, 0x2079793L);
        writeAXI(axiBase + 0x8, 0x207d793L);
        writeAXI(axiBase + 0x8, 0xc70733L);
        writeAXI(axiBase + 0x8, 0x2068263L);
        writeAXI(axiBase + 0x8, 0x7202fL);
        writeAXI(axiBase + 0x8, 0x17d793L);
        writeAXI(axiBase + 0x8, 0xb7f733L);
        writeAXI(axiBase + 0x8, 0x17f693L);
        writeAXI(axiBase + 0x8, 0x2079793L);
        writeAXI(axiBase + 0x8, 0x207d793L);
        writeAXI(axiBase + 0x8, 0xc70733L);
        writeAXI(axiBase + 0x8, 0xfe0692e3L);
        writeAXI(axiBase + 0x8, 0x72003L);
        writeAXI(axiBase + 0x8, 0x17d793L);
        writeAXI(axiBase + 0x8, 0xfe1ff06fL);
        writeAXI(axiBase + 0x8, 0x8067L);
        writeAXI(axiBase + 0x8, 0x80000537L);
        writeAXI(axiBase + 0x8, 0x80000593L);
        writeAXI(axiBase + 0x8, 0x697L);
        writeAXI(axiBase + 0x8, 0x55c68693L);
        writeAXI(axiBase + 0x8, 0xa6b023L);
        writeAXI(axiBase + 0x8, 0xb6b72fL);
        writeAXI(axiBase + 0x8, 0x80000eb7L);
        writeAXI(axiBase + 0x8, 0x200193L);
        writeAXI(axiBase + 0x8, 0x5d71863L);
        writeAXI(axiBase + 0x8, 0x6b783L);
        writeAXI(axiBase + 0x8, 0xfff00e9bL);
        writeAXI(axiBase + 0x8, 0x1fe9e93L);
        writeAXI(axiBase + 0x8, 0x800e8e93L);
        writeAXI(axiBase + 0x8, 0x300193L);
        writeAXI(axiBase + 0x8, 0x3d79c63L);
        writeAXI(axiBase + 0x8, 0xb6b72fL);
        writeAXI(axiBase + 0x8, 0xfff00e9bL);
        writeAXI(axiBase + 0x8, 0x1fe9e93L);
        writeAXI(axiBase + 0x8, 0x800e8e93L);
        writeAXI(axiBase + 0x8, 0x400193L);
        writeAXI(axiBase + 0x8, 0x3d71063L);
        writeAXI(axiBase + 0x8, 0x6b783L);
        writeAXI(axiBase + 0x8, 0xfff80eb7L);
        writeAXI(axiBase + 0x8, 0xfffe8e9bL);
        writeAXI(axiBase + 0x8, 0xce9e93L);
        writeAXI(axiBase + 0x8, 0x500193L);
        writeAXI(axiBase + 0x8, 0x1d79463L);
        writeAXI(axiBase + 0x8, 0x301a63L);
        writeAXI(axiBase + 0x8, 0x119513L);
        writeAXI(axiBase + 0x8, 0x50063L);
        writeAXI(axiBase + 0x8, 0x156513L);
        writeAXI(axiBase + 0x8, 0x73L);
        writeAXI(axiBase + 0x8, 0x100513L);
        writeAXI(axiBase + 0x8, 0x73L);
        writeAXI(axiBase + 0x8, 0xc0001073L);
        writeAXI(axiBase + 0x8, 0x65737341L);
        writeAXI(axiBase + 0x8, 0x6f697472L);
        writeAXI(axiBase + 0x8, 0x6166206eL);
        writeAXI(axiBase + 0x8, 0x64656c69L);
        writeAXI(axiBase + 0x8, 0x6461203aL);
        writeAXI(axiBase + 0x8, 0x3e207264L);
        writeAXI(axiBase + 0x8, 0x3128203dL);
        writeAXI(axiBase + 0x8, 0x3c204c55L);
        writeAXI(axiBase + 0x8, 0x3231203cL);
        writeAXI(axiBase + 0x8, 0x26262029L);
        writeAXI(axiBase + 0x8, 0x64646120L);
        writeAXI(axiBase + 0x8, 0x203c2072L);
        writeAXI(axiBase + 0x8, 0x2a203336L);
        writeAXI(axiBase + 0x8, 0x55312820L);
        writeAXI(axiBase + 0x8, 0x3c3c204cL);
        writeAXI(axiBase + 0x8, 0x29323120L);
        writeAXI(axiBase + 0x8, 0xaL);
        writeAXI(axiBase + 0x8, 0x0L);
        writeAXI(axiBase + 0x8, 0x65737341L);
        writeAXI(axiBase + 0x8, 0x6f697472L);
        writeAXI(axiBase + 0x8, 0x6166206eL);
        writeAXI(axiBase + 0x8, 0x64656c69L);
        writeAXI(axiBase + 0x8, 0x2821203aL);
        writeAXI(axiBase + 0x8, 0x335b7470L);
        writeAXI(axiBase + 0x8, 0x64615b5dL);
        writeAXI(axiBase + 0x8, 0x282f7264L);
        writeAXI(axiBase + 0x8, 0x204c5531L);
        writeAXI(axiBase + 0x8, 0x31203c3cL);
        writeAXI(axiBase + 0x8, 0x205d2932L);
        writeAXI(axiBase + 0x8, 0x78302026L);
        writeAXI(axiBase + 0x8, 0x29303830L);
        writeAXI(axiBase + 0x8, 0x20262620L);
        writeAXI(axiBase + 0x8, 0x73756163L);
        writeAXI(axiBase + 0x8, 0x3d3d2065L);
        writeAXI(axiBase + 0x8, 0x66783020L);
        writeAXI(axiBase + 0x8, 0xaL);
        writeAXI(axiBase + 0x8, 0x65737341L);
        writeAXI(axiBase + 0x8, 0x6f697472L);
        writeAXI(axiBase + 0x8, 0x6166206eL);
        writeAXI(axiBase + 0x8, 0x64656c69L);
        writeAXI(axiBase + 0x8, 0x6f6e203aL);
        writeAXI(axiBase + 0x8, 0xa6564L);
        writeAXI(axiBase + 0x8, 0x65737341L);
        writeAXI(axiBase + 0x8, 0x6f697472L);
        writeAXI(axiBase + 0x8, 0x6166206eL);
        writeAXI(axiBase + 0x8, 0x64656c69L);
        writeAXI(axiBase + 0x8, 0x7375203aL);
        writeAXI(axiBase + 0x8, 0x6d5f7265L);
        writeAXI(axiBase + 0x8, 0x69707061L);
        writeAXI(axiBase + 0x8, 0x615b676eL);
        writeAXI(axiBase + 0x8, 0x2f726464L);
        writeAXI(axiBase + 0x8, 0x4c553128L);
        writeAXI(axiBase + 0x8, 0x203c3c20L);
        writeAXI(axiBase + 0x8, 0x5d293231L);

        // write
        writeAXI(axiBase + 0x8, 0x1L);
        // addr
        writeAXI(axiBase + 0x8, 0x80002c00L);
        writeAXI(axiBase + 0x8, 0x0L);
        // len
        writeAXI(axiBase + 0x8, 0x41L);
        writeAXI(axiBase + 0x8, 0x0L);
        // data
        writeAXI(axiBase + 0x8, 0x6464612eL);
        writeAXI(axiBase + 0x8, 0x3d3d2072L);
        writeAXI(axiBase + 0x8, 0xa3020L);
        writeAXI(axiBase + 0x8, 0x0L);
        writeAXI(axiBase + 0x8, 0x65737341L);
        writeAXI(axiBase + 0x8, 0x6f697472L);
        writeAXI(axiBase + 0x8, 0x6166206eL);
        writeAXI(axiBase + 0x8, 0x64656c69L);
        writeAXI(axiBase + 0x8, 0x7470203aL);
        writeAXI(axiBase + 0x8, 0x5b5d335bL);
        writeAXI(axiBase + 0x8, 0x72646461L);
        writeAXI(axiBase + 0x8, 0x5531282fL);
        writeAXI(axiBase + 0x8, 0x3c3c204cL);
        writeAXI(axiBase + 0x8, 0x29323120L);
        writeAXI(axiBase + 0x8, 0x2026205dL);
        writeAXI(axiBase + 0x8, 0x34307830L);
        writeAXI(axiBase + 0x8, 0xa30L);
        writeAXI(axiBase + 0x8, 0x0L);
        writeAXI(axiBase + 0x8, 0x65737341L);
        writeAXI(axiBase + 0x8, 0x6f697472L);
        writeAXI(axiBase + 0x8, 0x6166206eL);
        writeAXI(axiBase + 0x8, 0x64656c69L);
        writeAXI(axiBase + 0x8, 0x7470203aL);
        writeAXI(axiBase + 0x8, 0x5b5d335bL);
        writeAXI(axiBase + 0x8, 0x72646461L);
        writeAXI(axiBase + 0x8, 0x5531282fL);
        writeAXI(axiBase + 0x8, 0x3c3c204cL);
        writeAXI(axiBase + 0x8, 0x29323120L);
        writeAXI(axiBase + 0x8, 0x2026205dL);
        writeAXI(axiBase + 0x8, 0x38307830L);
        writeAXI(axiBase + 0x8, 0xa30L);
        writeAXI(axiBase + 0x8, 0x0L);
        writeAXI(axiBase + 0x8, 0x65737341L);
        writeAXI(axiBase + 0x8, 0x6f697472L);
        writeAXI(axiBase + 0x8, 0x6166206eL);
        writeAXI(axiBase + 0x8, 0x64656c69L);
        writeAXI(axiBase + 0x8, 0x6674203aL);
        writeAXI(axiBase + 0x8, 0x70653e2dL);
        writeAXI(axiBase + 0x8, 0x20252063L);
        writeAXI(axiBase + 0x8, 0x3d3d2034L);
        writeAXI(axiBase + 0x8, 0xa3020L);
        writeAXI(axiBase + 0x8, 0x0L);
        writeAXI(axiBase + 0x8, 0x65737341L);
        writeAXI(axiBase + 0x8, 0x6f697472L);
        writeAXI(axiBase + 0x8, 0x6166206eL);
        writeAXI(axiBase + 0x8, 0x64656c69L);
        writeAXI(axiBase + 0x8, 0x2221203aL);
        writeAXI(axiBase + 0x8, 0x656c6c69L);
        writeAXI(axiBase + 0x8, 0x206c6167L);
        writeAXI(axiBase + 0x8, 0x74736e69L);
        writeAXI(axiBase + 0x8, 0x74637572L);
        writeAXI(axiBase + 0x8, 0x226e6f69L);
        writeAXI(axiBase + 0x8, 0xaL);
        writeAXI(axiBase + 0x8, 0x0L);
        writeAXI(axiBase + 0x8, 0x65737341L);
        writeAXI(axiBase + 0x8, 0x6f697472L);
        writeAXI(axiBase + 0x8, 0x6166206eL);
        writeAXI(axiBase + 0x8, 0x64656c69L);
        writeAXI(axiBase + 0x8, 0x2221203aL);
        writeAXI(axiBase + 0x8, 0x78656e75L);
        writeAXI(axiBase + 0x8, 0x74636570L);
        writeAXI(axiBase + 0x8, 0x65206465L);
        writeAXI(axiBase + 0x8, 0x70656378L);
        writeAXI(axiBase + 0x8, 0x6e6f6974L);
        writeAXI(axiBase + 0x8, 0xa22L);
        writeAXI(axiBase + 0x8, 0x0L);

        // write
        writeAXI(axiBase + 0x8, 0x1L);
        // addr
        writeAXI(axiBase + 0x8, 0x2000000L);
        writeAXI(axiBase + 0x8, 0x0L);
        // len
        writeAXI(axiBase + 0x8, 0x0L);
        writeAXI(axiBase + 0x8, 0x0L);
        // data
        writeAXI(axiBase + 0x8, 0x1L);

        var finished = false
        while (!finished) {
          // SAI_CMD_READ
          writeAXI(axiBase + 0x8, 0)
          // addr 0x80001000
          writeAXI(axiBase + 0x8, 0x80001000L)
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
         // write
        writeAXI(axiBase + 0x8, 0x1L);
        // addr
        writeAXI(axiBase + 0x8, 0x80001000L);
        writeAXI(axiBase + 0x8, 0x0L);
        // len
        writeAXI(axiBase + 0x8, 0x1L);
        writeAXI(axiBase + 0x8, 0x0L);
        // data
        writeAXI(axiBase + 0x8, 0x0L);
        writeAXI(axiBase + 0x8, 0x0L);
        step(50)
    }
  }
}