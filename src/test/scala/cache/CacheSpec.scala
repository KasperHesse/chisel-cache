package cache

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class CacheSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Chisel Cache"

  /**
   * Performs initial setup of the DUT before starting any tests by assigning known
   * values to all inputs
   * @param dut
   */
  def setupDut(dut: Cache): Unit = {
    dut.io.proc.req.poke(false.B)
    dut.io.proc.bits.wrData.poke(0.U)
    dut.io.proc.bits.addr.poke(0.U)
    dut.io.proc.bits.we.poke(false.B)

    dut.io.mem.ack.poke(false.B)
    dut.io.mem.bits.rdData.poke(0.U)
    dut.clock.setTimeout(100)
  }
  /**
   * Issues a read command to the cache, clocking the DUT until the cache signals that the read data has arrived
   * If the read address is not yet placed in the cache, the fetched data will be set equal to the address being loaded from+1
   * This command does NOT perform any expecting of the read data
   * @param dut
   * @param addr The address to read
   */
  def issueRead(dut: Cache, c: CacheConfig, addr: Int): Unit = {
    timescope {
      dut.io.proc.req.poke(true.B)
      dut.io.proc.bits.addr.poke(addr.U)

      while(!dut.io.proc.ack.peek().litToBoolean && !dut.io.mem.req.peek().litToBoolean) {dut.clock.step() }

      //Block fetch: Return some data based on address being accessed
      if(dut.io.mem.req.peek().litToBoolean) {
        val ad = dut.io.mem.bits.addr.peek().litValue.toInt
        for(i <- 1 to c.memAccesesPerBlock) {
          dut.io.mem.bits.rdData.poke((ad+i).U)
          dut.io.mem.ack.poke(true.B)
          dut.clock.step()
        }
        dut.io.mem.ack.poke(false.B)

        while(!dut.io.proc.ack.peek().litToBoolean) {dut.clock.step() }
      }
    }
  }

  /**
   * Expect some data to be present on the cache's processor-port
   * @param dut The DUT
   * @param v The value to expect
   */
  def expectData(dut: Cache, addr: Int, v: Int): Unit = {
    timescope {
      dut.io.proc.bits.addr.poke(addr.U)
      dut.io.proc.req.poke(true.B)
      dut.io.proc.ack.expect(true.B)
      dut.io.proc.bits.rdData.expect(v.U)
    }

  }

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

  it should "fetch and return some data v2" in {
    val config = CacheConfig(numWords = 1024, wordsPerBlock = 4, cacheMemWidth = 32, wordWidth = 32, addrWidth = 32, mapping = "Direct")
    test(new Cache(config)).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      setupDut(dut)
      issueRead(dut, config, 4)
      dut.io.proc.bits.addr.poke(4.U)
      dut.io.proc.req.poke(true.B)
      dut.io.proc.bits.rdData.expect(2.U)
    }
  }

}
