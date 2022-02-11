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

    /** Number of memory accesses that must be completed to fetch an entire block */
    val memAccesesPerBlock = wordsPerBlock*wordWidth/cacheMemWidth
    /** Number of bytes in a dataword */
    val bytesPerWord = log2Ceil(wordWidth/8)
    /** Number of entries in the cache */
    val numEntries = numWords / wordsPerBlock
    /** Low index bit */
    val indexL = log2Ceil(wordsPerBlock*wordWidth/8)
    /** High index bit */
    val indexH = indexL + log2Ceil(numEntries) - 1
    /** Low block offset bit */
    val blockL = bytesPerWord
    /** High block offset bit */
    val blockH = indexL - 1
    /** Low tag bit */
    val tagL = indexH + 1
    /** High tag bit */
    val tagH = wordWidth-1

  }

  /**
   * An I/O bundle with a request/acknowledge signal.
   * The request signal should be pulled high when the producer wants the slave
   * to process a request, and the acknowledge signal is pulled high by the slave
   * when the request has been acknowledged (and potentiel read data is valid)
   * @param gen The type of data to be wrapped in the req/ack handshake
   * @tparam T
   */
  class ReqAck[+T <: Data](gen: T) extends Bundle {
    /** Request signal from producer to consumer */
    val req = Output(Bool())
    /** Acknowledge signal from consumer to producer */
    val ack = Input(Bool())
    /** The data wrapped by this req/ack handshake */
    val bits = gen
  }

  /** Helper object that can be used to wrap some data with a [[ReqAck]] handshake */
  object ReqAck {
    def apply[T <: Data](gen: T): ReqAck[T] = new ReqAck(gen)
  }

  /**
   * I/O ports for the cache module
   * @param config
   */
  class CacheIO(config: CacheConfig) extends Bundle {
    val proc = Flipped(ReqAck(new ProcessorCacheIO(config)))
    val mem = ReqAck(new CacheMemIO(config))
  }

  /**
   * I/O ports for the replacement module
   * @param config
   */
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
    /** Selector indicating which cache set should be replaced */
    val select: UInt = Output(UInt(2.W))
    /** Asserted when valid data has arrived from memory */
    val memAck: Bool = Input(Bool())
  }

  class ControllerIO(config: CacheConfig) extends Bundle {
    /** Read/write data address */
    val addr = Input(UInt(config.addrWidth.W))
    /** Valid operation signal from processor */
    val procReq = Input(Bool())
    /** Write enable high / read enable low */
    val we = Input(Bool())
    /** Read data is ready to be sampled */
    val procAck = Output(Bool())
    /** Valid operation signal to memory */
    val memReq = Output(Bool())
    /** Memory read address */
    val memReadAddress = Output(UInt(config.addrWidth.W))
    /** I/O ports to replacement module */
    val replacement = new ControllerReplacementIO(config)

  }

  /**
   * I/O ports between cache and memory
   * Instantiate as-is in cache, use Flipped() in memory
   */
  class CacheMemIO(c: CacheConfig) extends Bundle {
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
