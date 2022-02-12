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

  //Read data
  //By using interleaved blocks, we read one data word from each interleaved block
  //LSB only indicate which word in block we should read
  //We use index-value to select data, use block offset to select which to read
  val blockOffsetLow = log2Ceil(c.wordWidth/8)
  val blockOffsetHigh = log2Ceil(c.wordsPerBlock) + blockOffsetLow - 1
  val blockOffset = io.proc.bits.addr(blockOffsetHigh, blockOffsetLow)
  val rdData = Wire(Vec(c.wordsPerBlock, UInt(c.wordWidth.W)))
  for(i <- 0 until c.wordsPerBlock) {
    rdData(i) := mem(i).read(index)

    //If wordwidth = memwidth, the hardware is simple, all caches have same data input
    if(c.wordWidth == c.cacheMemWidth) {
      when(replacement.io.cache.we(i) && io.mem.ack) {
        mem(i).write(index, io.mem.bits.rdData)
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
