package cache

import chisel3._
import chisel3.util._

/**
 * A nice and simple cache
 * @param config Cache configuration object
 */
class Cache(config: CacheConfig) extends Module {
  val io = IO(new CacheIO(config))

  // --- MODULES
  val controller = Module(new Controller(config))
  val replacement = Module(new Replacement(config))

  // Cache memory
  // interleaved blocks, each containing one data word
  val numEntries = config.numWords / config.wordsPerBlock
  val mem = Seq.fill(config.wordsPerBlock)(SyncReadMem(numEntries, UInt(config.wordWidth.W)))

  //Read data
  //By using interleaved blocks, we read one data word from each interleaved block
  //LSB only indicate which word in block we should read
  //We use index-value to select data, perform selection later
  val rdData = Wire(Vec(config.wordsPerBlock, UInt(config.wordWidth.W)))
  for(i <- 0 until config.wordsPerBlock) {
    rdData(i) := mem(i).read(controller.io.index)
  }





  //Output signals
  io.proc.bits.rdData := rdData
  io.mem.bits.addr := controller.io.memReadAddress
}
