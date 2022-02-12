package cache

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class CacheWrapperSpec extends AnyFlatSpec with ChiselScalatestTester {

  /**
   * A class wrapping a dut cache and a software model of the cache
   * @param dut
   * @param c
   */
  class CacheSimulator(dut: CacheWrapper, c: CacheConfig) {
    val cache = Array.ofDim[BigInt](c.numEntries, c.wordsPerBlock)
    val tags = Array.ofDim[BigInt](c.numEntries)
    val dirty = Array.fill[Boolean](c.numEntries)(false)
    val valid = Array.fill[Boolean](c.numEntries)(false)
    val mem = Array.tabulate[BigInt](math.pow(2,10).toInt)(n => BigInt(n))

    var hit = 0
    var miss = 0

    dut.clock.setTimeout(200)

    /**
     * Read from the cache simulation
     * @param addr The address to read from
     * @return
     */
    private def read(addr: Int): BigInt = {
      val tag   = addr >> c.tagL
      val index = (addr & ((1 << c.indexH+1) - 1)) >> c.indexL
      val block = (addr & ((1 << c.blockH+1) - 1)) >> c.blockL
//      println(f"Read from addr=$addr, index=$index, block=$block, tag=$tag")
      if(!(valid(index) && tags(index) == tag)) {
        miss += 1
        //Fetch from memory
        val blockBaseAddr = addr >> c.indexL //mask out lower bits
        for(i <- 0 until c.memAccesesPerBlock) {
          if(c.wordWidth == c.cacheMemWidth) {
            cache(index)(i) = mem(blockBaseAddr + i)
          } else {
            ???
          }
        }
        valid(index) = true
        tags(index) = tag
      } else {
        hit += 1
      }
      cache(index)(block)
    }

    def issueRead(addr: Int): Unit = {
      //Issue a read to the DUT, then compare the output to the simulation
      dut.io.bits.addr.poke(addr.U)
      dut.io.bits.we.poke(false.B)
      dut.io.bits.wrData.poke(0.U)
      dut.io.req.poke(true.B)

      while(!dut.io.ack.peek().litToBoolean) {dut.clock.step() }

      dut.io.bits.rdData.expect(this.read(addr).U)
    }

    def stats(): Unit = {
      println(
        f"""End-of-simulation statistics
           |hits:   $hit
           |misses: $miss
           |ratio:  ${hit.toFloat/(hit+miss).toFloat}""".stripMargin)
    }
  }

  behavior of "Cache and memory"

  it should "read data" in {
    val config = CacheConfig(numWords = 1024, wordsPerBlock = 4, cacheMemWidth = 32, wordWidth = 32, addrWidth = 32, mapping = "Direct")
    test(new CacheWrapper(config)).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      val sim = new CacheSimulator(dut, config)
      sim.issueRead(4)
      sim.issueRead(0)
      sim.issueRead(8)
      sim.issueRead(12)

      sim.stats()
    }
  }
}
