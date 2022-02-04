package cache

import chisel3._
import chisel3.util._

/**
 * A nice and simple cache
 * @param config Cache configuration object
 */
class Cache(config: CacheConfig) extends Module {
  val io = IO(new CacheIO(config))

  require(isPow2(config.numWords), "Number of data words must be a power of 2")
  require(isPow2(config.wordsPerBlock), "Number of data words per block must be a power of 2")
  require(config.numWords >= config.wordsPerBlock, "Number of data words must be >= number of words per block")
  require(isPow2(config.wordWidth), "Word width must be a power of 2")

  // --- MODULES
  val controller = Module(new Controller(config))
  val replacement = Module(new Replacement(config))

  // Cache memory
  // interleaved blocks, each containing one data word
  val numEntries = config.numWords / config.wordsPerBlock
  val mem = Seq.fill(config.wordsPerBlock)(SyncReadMem(numEntries, UInt(config.wordWidth.W)))

  //Read data
  //Read data addresses do not correspond directly to index, but are an offset from word address
  val rdData = Wire(Vec(config.wordsPerBlock, UInt(config.wordWidth.W)))
  for(i <- 0 until config.wordsPerBlock) {
    val numBytes = config.wordWidth/8
    val logNB = log2Ceil(numBytes)
    val rdAddr = Cat(io.proc.bits.addr(config.wordWidth-1, logNB), (i*numBytes).U(logNB.W))
    rdData(i) := mem(i).read(rdAddr)
  }





  //Output signals
  io.proc.bits.rdData := rdData
  io.mem.bits.addr := controller.io.memReadAddress
}
