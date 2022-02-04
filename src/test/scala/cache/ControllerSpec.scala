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

    test(new Controller(config)).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      dut.io.memReady
      //Setup inputs
      dut.io.addr.poke(0.U)
      dut.io.rw.poke(true.B)
      dut.io.procValid.poke(true.B)
      dut.clock.step()

      //After 1 cc, mem valid and proc ready should both be false
      dut.io.memValid.expect(false.B)
      dut.io.procReady.expect(false.B)
      dut.clock.step()

      //After 1 more cc, we expect memvalid to be true and read addresses to be output
      for(i <- 0 until numSteps) {
        dut.io.memValid.expect(true.B)
        dut.io.procReady.expect(false.B)
        dut.io.memReadAddress.expect((i*step).U)
        dut.clock.step()
      }
      //Now, mem read should not be valid anymore
      dut.io.memReadAddress.expect((step*numSteps).U)
      dut.io.memValid.expect(false.B)
    }
  }
}
