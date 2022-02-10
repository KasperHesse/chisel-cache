package cache

import chisel3._
import chisel3.util._

class Replacement(c: CacheConfig) extends Module{
  val io = IO(new ReplacementIO(c))

  //Number of data words that are written on each cycle
  val WRITES_PER_CYCLE = c.cacheMemWidth/c.wordWidth
  //Number of write cycles from memory required to replace an entire block
  val WRITES_PER_BLOCK = c.wordsPerBlock*c.wordWidth/c.cacheMemWidth

  //Each cache block takes wordsPerBlock*wordWidth/cacheMemWidth cycles to replace
  val progress = RegInit(0.U(log2Ceil(WRITES_PER_BLOCK+1).W))

  //Each cache block gets a write-enable signal
  val we = RegInit(((1 << WRITES_PER_CYCLE) - 1).U(c.wordsPerBlock.W))

  //Finish-signal to controller, indicating that a block has been replaced
  val finish = WireDefault(false.B)


  when(io.cache.memValid && progress < (WRITES_PER_BLOCK-1).U) {
    we := we << WRITES_PER_CYCLE
    progress := progress + 1.U
  } .elsewhen(io.cache.memValid) { //final memValid reset everything
    we := ((1 << WRITES_PER_CYCLE)-1).U(c.wordsPerBlock.W)
    progress := 0.U
    finish := true.B
  }

  io.cache.we := VecInit(we.asBools)
  io.cache.select := 0.U
  io.controller.finish := finish

}
