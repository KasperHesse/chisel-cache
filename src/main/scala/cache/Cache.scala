package cache

import chisel3._
import chisel3.util._

/**
 * A nice and simple cache
 * @param c Cache configuration object
 */
class Cache(c: CacheConfig) extends Module {
  val io = IO(new CacheIO(c))

  // --- MODULES
  val controller = Module(new Controller(c))
  val replacement = Module(new Replacement(c))

  // Cache memory
  // interleaved blocks, each containing one data word
  val numEntries = c.numWords / c.wordsPerBlock
  val mem = Seq.fill(c.wordsPerBlock)(SyncReadMem(numEntries, UInt(c.wordWidth.W)))

  //Signals
  val index = io.proc.bits.addr(c.indexH, c.indexL)
  val blockOffset = io.proc.bits.addr(c.blockH, c.blockL)

  //Read data
  //By using interleaved blocks, we read one data word from each interleaved block
  /** Read data from this cache line */
  val rdData = Wire(Vec(c.wordsPerBlock, UInt(c.wordWidth.W)))
  //Populate rdData
  for(i <- 0 until c.wordsPerBlock) {
    rdData(i) := mem(i).read(index)
  }

  //Populate wrData
  for(i <- 0 until c.wordsPerBlock) {
    //Fetching: When replacement.we and mem.ack, write to given cache line
    //Writing from processor: When proc.we and controller.ack, write to the specific index/block
    //Write data: When writing from processor && valid && !dirty, only toggle the cache block
    //which matches block offset.
    //If wordwidth = memwidth, the hardware is simple, all caches have same data input
    if(c.wordWidth == c.cacheMemWidth) {

      //When processor's we-signal was high on previous cc, that means we're issuing a write
      val wrData = Mux(io.proc.bits.we, io.proc.bits.wrData, io.mem.bits.rdData)
      val we = WireDefault(false.B)
      when(io.proc.bits.we) { //we was high: writing data from processor
        //Write when controller signals action has been performed, only write to specified block offset
        we := controller.io.procAck && blockOffset === i.U
      } .otherwise {
        //Write when fetching: Only when replacement module signals to write and memory acknowledges our action
        we := replacement.io.cache.we(i) && io.mem.ack
      }

      when(we) {
        mem(i).write(index, wrData)
      }
    } else {
      //Otherwise, must assign a subfield of that mem data based on index
      when(replacement.io.cache.we(i) && io.mem.ack) {
        val lowBit = (i % (c.cacheMemWidth/c.wordWidth)) * c.wordWidth
        val wrData = io.mem.bits.rdData(lowBit+c.wordWidth-1, lowBit)
        mem(i).write(index, wrData)
      }
    }
  }

  controller.io.replacement <> replacement.io.controller
  controller.io.addr := io.proc.bits.addr
  controller.io.procReq := io.proc.req
  controller.io.we := io.proc.bits.we

  replacement.io.cache.memAck := io.mem.ack

  //Output signals
  io.proc.bits.rdData := rdData(blockOffset)
  io.proc.ack := controller.io.procAck

  io.mem.bits.addr := controller.io.memReadAddress
  io.mem.req := controller.io.memReq

  io.mem.bits.we := false.B //Only reading from memory right now
  io.mem.bits.wrData := 0.U //Not writing to memory to right now
}
