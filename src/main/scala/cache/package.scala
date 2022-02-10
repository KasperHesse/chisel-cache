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
                        ) {
    require(isPow2(numWords), "Number of data words must be a power of 2")
    require(isPow2(wordsPerBlock), "Number of data words per block must be a power of 2")
    require(numWords >= wordsPerBlock, "Number of data words must be >= number of words per block")
    require(isPow2(wordWidth), "Word width must be a power of 2")
  }

  class CacheIO(config: CacheConfig) extends Bundle {
    val proc = Flipped(Decoupled(new ProcessorCacheIO(config)))
    val mem = Decoupled(new CacheMemIO(config))
  }

  class ReplacementIO(config: CacheConfig) extends Bundle {
    val controller = Flipped(new ControllerReplacementIO(config))
    val cache = new ReplacementCacheIO(config)
  }

  /**
   * I/O ports between cache controller and replacement module
   * Instantiate as-is in controller, use Flipped() in replacement module
   * @param config
   */
  class ControllerReplacementIO(config: CacheConfig) extends Bundle {
    /** Index currently being accessed in cache */
    val index: UInt = Output(UInt(log2Ceil(config.numWords/config.wordsPerBlock).W))
    /** High for one cc when a cache block has been replaced */
    val finish: Bool = Input(Bool())
  }

  /**
   * I/O ports between replacement module and cache module
   * Instantiate as/is in replacement module
   * @param config
   */
  class ReplacementCacheIO(config: CacheConfig) extends Bundle {
    /** Write-enable signals for each word in cache block */
    val we: Vec[Bool] = Output(Vec(config.wordsPerBlock, Bool()))
    /** Selector indicating which cache block should be replaced */
    val select: UInt = Output(UInt(2.W))
    /** Asserted when valid data has arrived from cache */
    val memValid: Bool = Input(Bool())
  }

  class CacheControllerIO(config: CacheConfig) extends Bundle {
    /** Read/write data address */
    val addr = Input(UInt(config.addrWidth.W))
    /** Valid operation signal from processor */
    val procValid = Input(Bool())
    /** Write enable high / read enable low */
    val we = Input(Bool())
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
    /** Write enable high / read enable low */
    val we     = Output(Bool())

  }

  /**
   * I/O ports between processor and cache
   * Instantiate as-is in processor, use Flipped() in cache
   */
  class ProcessorCacheIO(c: CacheConfig) extends Bundle {
    /** Write data from processor */
    val wrData = Output(UInt(c.wordWidth.W))
    /** Read data from cache */
    val rdData = Input(UInt(c.wordWidth.W))
    /** Address from processor */
    val addr   = Output(UInt(c.wordWidth.W))
    /** Write enable high, read enable low */
    val we     = Output(Bool())
  }
}
