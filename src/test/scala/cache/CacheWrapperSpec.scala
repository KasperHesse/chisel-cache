package cache

import chisel3._
import chisel3.util.log2Ceil
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import scala.collection.mutable


/**
 * A class wrapping a dut cache and a software model of the cache
 * @param dut
 * @param c
 */
class CacheSimulator(dut: CacheWrapper, c: CacheConfig) {


  /** Tag values, for comparison */
  val tags = Array.ofDim[BigInt](c.numEntries)
  /** Dirty bits, for comparison */
  val dirty = Array.fill[Boolean](c.numEntries)(false)
  /** Valid bits, for comparison */
  val valid = Array.fill[Boolean](c.numEntries)(false)
  /** Memory contents, for comparison. Simulating 1KB of memory */
//  val mem = Array.tabulate[BigInt](math.pow(2,10).toInt)(n => BigInt(n))

  val mem = mutable.Map[Int, BigInt]()

  var hit = 0
  var miss = 0

  //Initial simulation setup
  dut.clock.setTimeout(100)
  dut.io.bits.we.poke(false.B)
  dut.io.req.poke(false.B)
  dut.io.bits.addr.poke(0.U)
  dut.io.bits.wrData.poke(0.U)

  /**
   * Read from the cache simulation
   * @param addr The address to read from
   * @return
   */
  private def read(addr: Int): BigInt = {
    val tag   = addr >> c.tagL
    val index = (addr & ((1 << c.indexH+1) - 1)) >> c.indexL
    val block = (addr & ((1 << c.blockH+1) - 1)) >> c.blockL
    val blockBaseAddr = (addr >> c.indexL) << log2Ceil(c.wordsPerBlock) //mask out lower bits, right-shift again to get correct memory index)
    if(!(valid(index) && tags(index) == tag) || (tags(index) != tag)) { //Non-valid data
      miss += 1
      //Fetch from memory
      valid(index) = true
      tags(index) = tag
    } else {
      hit += 1
    }
    val r = mem.getOrElseUpdate(blockBaseAddr + block, BigInt(blockBaseAddr + block))
    println(f"READ  addr=$addr%5d. tag=$tag%3d index=$index%3d, block=$block, data=$r")
    r
  }

  def issueRead(addr: Int): Unit = {
    //Issue a read to the DUT, then compare the output to the simulation
    timescope {
      dut.io.bits.addr.poke(addr.U)
      dut.io.bits.we.poke(false.B)
      dut.io.bits.wrData.poke(0.U)
      dut.io.req.poke(true.B)

      //Always requires at least one cc to get new data
      dut.clock.step()

      while (!dut.io.ack.peek().litToBoolean) {
        dut.clock.step()
      }

      val read = this.read(addr)

      dut.io.bits.rdData.expect(read.U, s"Failed to read from address $addr. Expected $read, got ${dut.io.bits.rdData.peek().litValue.toInt}")
    }
  }

  def issueWrite(addr: Int, data: BigInt): Unit = {
    val tag   = addr >> c.tagL
    val index = (addr & ((1 << c.indexH+1) - 1)) >> c.indexL
    val block = (addr & ((1 << c.blockH+1) - 1)) >> c.blockL
    val blockBaseAddr = (addr >> c.indexL) << log2Ceil(c.wordsPerBlock) //mask out lower bits, right-shift again to get correct memory index)
    println(f"WRITE addr=$addr%5d. tag=$tag%3d index=$index%3d, block=$block, data=$data")
    timescope {
      dut.io.bits.addr.poke(addr.U)
      dut.io.bits.we.poke(true.B)
      dut.io.bits.wrData.poke(data.U)
      dut.io.req.poke(true.B)

      //Always requires at least one cc
      dut.clock.step()

      while(!dut.io.ack.peek().litToBoolean) {
        dut.clock.step()
      }
      mem.update(blockBaseAddr + block, data)
    }
  }

  def issueWrite(addr: Int): Unit = {
    this.issueWrite(addr, BigInt(addr))
  }

  def stats(): Unit = {
    println(
      f"""End-of-simulation statistics
         |hits:   $hit
         |misses: $miss
         |ratio:  ${hit.toFloat/(hit+miss).toFloat}""".stripMargin)
  }
}

class CacheWrapperSpec extends AnyFlatSpec with ChiselScalatestTester {

  def cacheSpec(config: CacheConfig): Unit = {
    /** Simple test to verify that we can read from the lower addresses */
    it should "read data" in {
      test(new CacheWrapper(config)) { dut =>
        val sim = new CacheSimulator(dut, config)
        sim.issueRead(4)
        sim.issueRead(0)
        sim.issueRead(8)
        sim.issueRead(12)

        sim.stats()
      }
    }

    it should "write to a valid cache block" in {
      test(new CacheWrapper(config)) {dut =>
        val sim = new CacheSimulator(dut, config)
        sim.issueRead(4)
        sim.issueWrite(8)
        sim.issueWrite(12)
        sim.issueRead(8)
        sim.issueRead(12)
      }
    }

    /** Simple test to verify that we can read from different cache indices */
    it should "read from two different indices in a row" in {
      test(new CacheWrapper(config)) { dut =>
        val sim = new CacheSimulator(dut,config)
        //TODO Update to use config.indexL to generate addresses
        sim.issueRead(16)
        sim.issueRead(32)
      }
    }


    /** Simple test to verify read behaviour with delays (does it correctly return to idle state?) */
    it should "read from two different indices with a delay between them" in {
      test(new CacheWrapper(config)) { dut =>
        val sim = new CacheSimulator(dut,config)
        sim.issueRead(16)
        dut.clock.step(3)
        sim.issueRead(32)
      }
    }

    it should "read from two indexes in alternating order" in {
      test(new CacheWrapper(config)).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
        val sim = new CacheSimulator(dut, config)
        sim.issueRead(760)
        sim.issueRead(796)
        sim.issueRead(760)
      }
    }

    /** Simple test to verify read behaviour to multiple different addresses */
    it should "handle a series of random reads" in {
      test(new CacheWrapper(config)){ dut =>
        val sim = new CacheSimulator(dut, config)
        for(_ <- 0 until 50) {
          val addr = (scala.util.Random.nextInt(math.pow(2,15).toInt)/4)*4 //by div-mul with 4, we get addresses that end in 4
          sim.issueRead(addr)
        }
        sim.stats()
      }
    }
  }

  behavior of "Cache and memory on 32/32/4-direct mapped cache"

  it should behave like cacheSpec(CacheConfig(numWords = 1024, wordsPerBlock = 4, cacheMemWidth = 32, wordWidth = 32, addrWidth = 32, mapping = "Direct"))

  behavior of "Cache and memory on 32/32/8-direct mapped cache"

  it should behave like cacheSpec(CacheConfig(numWords = 1024, wordsPerBlock = 8, cacheMemWidth = 32, wordWidth = 32, addrWidth = 32, mapping = "Direct"))
}
