package cache

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ControllerSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Cache controller"

  it should "generate read addresses when fetching data from memory" in {
    val config = CacheConfig(numWords = 1024, wordsPerBlock = 4, cacheMemWidth=32, wordWidth = 32, addrWidth = 32, mapping = "Direct")

    val step = config.cacheMemWidth/8 //address increments when fetching data
    val numSteps = config.wordWidth*config.wordsPerBlock/config.cacheMemWidth

    test(new Controller(config)) {dut =>
      //Setup inputs
      dut.io.addr.poke(0.U)
      dut.io.we.poke(false.B)
      dut.io.procReq.poke(true.B)
      dut.clock.step()

      //After 1 cc, we expect memvalid to be true and read addresses to be output
      for(i <- 0 until numSteps) {
        dut.io.memReq.expect(true.B)
        dut.io.procAck.expect(false.B)
        dut.io.memReadAddress.expect((i*step).U)
        dut.clock.step()
      }
      //Now, mem read should not be valid anymore
      dut.io.memReadAddress.expect((step*numSteps).U)
      dut.io.memReq.expect(false.B)
    }
  }

  it should "return data from cache once a block has been fetched" in {
    val config = CacheConfig(numWords = 1024, wordsPerBlock = 4, cacheMemWidth = 32, wordWidth = 32, addrWidth = 32, mapping = "Direct")
    test(new Controller(config)) {dut =>

      //Move to fetch state, step a couple of times, it should just wait there
      dut.io.addr.poke(0.U)
      dut.io.we.poke(false.B)
      dut.io.procReq.poke(true.B)
      dut.clock.step()

      //step until we've "fetched" data
      while(!dut.io.memReq.peek().litToBoolean) {dut.clock.step()} //Step until we start accessing memory
      while(dut.io.memReq.peek().litToBoolean) {dut.clock.step()} //Step while accessing memory

      dut.io.replacement.finish.poke(true.B) //Signal that mem access is finished
      dut.clock.step()
      dut.io.replacement.finish.poke(false.B)
      //Should now be in op state and ack request
      dut.io.procAck.expect(true.B)
    }
  }
}
