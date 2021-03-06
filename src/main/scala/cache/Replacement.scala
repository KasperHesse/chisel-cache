package cache

import chisel3._
import chisel3.util._

/** Replacement module implementing the replacement policy for the cache */
class Replacement(c: CacheConfig) extends Module{
  val io = IO(new ReplacementIO(c))

  //Number of data words that are retrieved from memory on each cycle
  val WORDS_PER_CYCLE = c.cacheMemWidth/c.wordWidth

  //Each cache block takes c.memAccessesPerBlock cycles to replace
  val progress = RegInit(0.U(log2Ceil(c.memAccesesPerBlock+1).W))

  //Each word in cache block gets a write-enable signal
  val we = RegInit(((1 << WORDS_PER_CYCLE) - 1).U(c.wordsPerBlock.W))

  //Finish-signal to controller, indicating that a block has been replaced
  val finish = WireDefault(false.B)


  //Data received from memory
  when(io.cache.memAck && progress < (c.memAccesesPerBlock-1).U) {
    we := we << WORDS_PER_CYCLE
    progress := progress + 1.U
  } .elsewhen(io.cache.memAck) { //final group of data received, reset counters
    we := ((1 << WORDS_PER_CYCLE)-1).U(c.wordsPerBlock.W)
    progress := 0.U
    finish := true.B
  }

  io.cache.we := VecInit(we.asBools)
  io.cache.select := 0.U
  io.controller.finish := finish

}
