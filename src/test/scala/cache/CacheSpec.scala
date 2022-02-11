package cache

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class CacheSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Chisel Cache"

  it should "fetch and return data" in {
    val config = CacheConfig(numWords = 1024, wordsPerBlock = 4, cacheMemWidth = 32, wordWidth = 32, addrWidth = 32, mapping = "Direct")

    test(new Cache(config)).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      //Setup data

      dut.io.proc.bits.addr.poke(0.U)
      dut.io.proc.bits.we.poke(false.B)
      dut.io.proc.req.poke(true.B) //Must keep high - maybe not the right choice?

      //Step until wrData is wanted
      while(!dut.io.mem.req.peek().litToBoolean) {dut.clock.step()}

      //Poke the data to be written
      for(i <- 0 until 4) {
        dut.io.mem.ack.poke(true.B)
        dut.io.mem.bits.rdData.poke(i.U)
        dut.clock.step()
      }
      //Once finished, deassert mem.ack and wait until proc.ack
      dut.io.mem.ack.poke(false.B)
      while(!dut.io.proc.ack.peek().litToBoolean) {dut.clock.step()}

      //Read from all those addresses
      dut.io.proc.bits.rdData.expect(0.U)

      for(i <- 1 until 4) {
        dut.io.proc.bits.addr.poke((i << 2).U)
        dut.clock.step()
        dut.io.proc.bits.rdData.expect(i.U)
      }
    }
  }

//  it should "not write data into cache unless mem.ack is high but no load has been issued" in {
//
//  }
}
