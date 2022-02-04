import chisel3._
import chisel3.util._

package object cache {

  case class CacheConfig(
                        /** Number of data words to store in the cache */
                        numWords: Int,
                        /** Number of data words per cache block */
                        wordsPerBlock: Int,
                        /** Width of cache-to-memory bus */
                        cacheMemWidth: Int,
                        /** Width of a data word */
                        wordWidth: Int,
                        /** Width of an address */
                        addrWidth: Int,
                        /** The type of mapping ot use (Direct, 2way, 4way) */
                        mapping: String //TODO Make this an enum instead
                        )

  class CacheIO(config: CacheConfig) extends Bundle {
    val proc = Flipped(Decoupled(new ProcessorCacheIO(config)))
    val mem = Decoupled(new CacheMemIO(config))
  }

  class CacheControllerIO(config: CacheConfig) extends Bundle {
    /** Read/write data address */
    val addr = Input(UInt(config.addrWidth.W))
    /** Valid operation signal from processor */
    val procValid = Input(Bool())
    /** Read/write flag */
    val rw = Input(Bool())
    /** Read data is ready to be sampled */
    val procReady = Output(Bool())
    /** Valid operation signal to memory */
    val memValid = Output(Bool())
    /** Memory read address */
    val memReadAddress = Output(UInt(config.addrWidth.W))
    /** Read data from memory coming in */
    val memReady = Input(Bool())
    /** Cache index to be accessed */
    val index = Output(UInt(log2Ceil(config.numWords/config.wordsPerBlock+1).W))

  }

  /**
   * Returns the log2 of a number.
   * @param x The value to get the log2 of
   * @return log2(x)
   */
  def log2(x: Double): Double = Math.log(x)/Math.log(2.0)

  /**
   * I/O ports between cache and memory
   * Instantiate as-is in cache, use Flipped() in memory
   * @param CACHE_MEM_WIDTH Width of memory bus
   * @param WORD_WIDTH Width of a data word
   * @param ADDR_WIDTH Width of addresses
   */
  class CacheMemIO(c: CacheConfig) extends Bundle {
    require(c.cacheMemWidth >= c.wordWidth, "Width of cache-to-memory bus must be at least the size of a word")
    require(c.cacheMemWidth % c.wordWidth == 0, "cache-to-memory bus width must be multiple of data word width")

    /** Write data from cache */
    val wrData = Output(UInt(c.cacheMemWidth.W))
    /** Read data to cache */
    val rdData = Input(UInt(c.cacheMemWidth.W))
    /** Address from cache */
    val addr   = Output(UInt(c.addrWidth.W))
    /** Read/write bit. Read=true, write=false */
    val rw     = Output(Bool())

  }

  /**
   * I/O ports between processor and cache
   * Instantiate as-is in processor, use Flipped() in cache
   * @param WORD_WIDTH Width of a data word
   * @param ADDR_WIDTH Width of addresses
   */
  class ProcessorCacheIO(c: CacheConfig) extends Bundle {
    /** Write data from processor */
    val wrData = Output(UInt(c.wordWidth.W))
    /** Read data from cache */
    val rdData = Input(UInt(c.wordWidth.W))
    /** Address from processor */
    val addr   = Output(UInt(c.wordWidth.W))
    /** Read/write bit. Read=true, write=false */
    val rw     = Output(Bool())
  }
}
