package cache

import chisel3._
import chisel3.util._

class Controller(c: CacheConfig) extends Module {
  val io = IO(new ControllerIO(c))

  val sOp :: sFetch :: sWriteback :: Nil = Enum(3)
  val state = RegInit(sOp)


  val index = io.addr(c.indexH, c.indexL)
  val tag = io.addr(c.tagH, c.tagL)

  //Metadata registers
  /** Dirty bit register */
  val dirty = RegInit(VecInit(Seq.fill(c.numEntries)(false.B)))
  /** Valid bit register */
  val valid = RegInit(VecInit(Seq.fill(c.numEntries)(false.B)))
  /** Tag register */
  val tags = RegInit(VecInit(Seq.fill(c.numEntries)(0.U((c.tagH-c.tagL+1).W))))

  //Output signals
  /** Acknowledge signal back to processor, signalling read data can be sampled on next cc / write is being performed */
  val ack = WireDefault(false.B)
  /** Valid signal to memory, indicating a memory read should be performed */
  val memValid = WireDefault(false.B)


  //Helper signals
  /** Base address of block to access: Tag + index bits, but block and byte offset set to 0 */
  val blockBaseAddr = Cat(io.addr(c.wordWidth-1, c.indexL), 0.U(c.indexL.W))
  /** Number of memory reads issued when fetching a cache line */
  val memReadsIssued = RegInit(0.U(log2Ceil(c.memAccesesPerBlock+1).W))
  /** Signal indicating that current line is valid and tag matches */
  val validData = tags(index) === tag && valid(index)


  //Next state logic and internal signal handling
  switch(state) {
    is(sOp) {
      when(io.procReq && !validData && !dirty(index)) { //non-cached, non-dirty access: Fetch new data
        state := sFetch
      } .elsewhen(io.procReq && !validData && dirty(index)) { //non-cached, dirty access: Writeback before fetching new data
        state := sWriteback //TODO implement writeback
      }

      when(io.procReq && io.we && validData) { //Write to cached address
        dirty(index) := true.B
      }
    }
    //Fetch data from memory state
    //Once data arrives, go to op-state and reset count register
    is(sFetch) {
      when(io.replacement.finish) {
        state := sOp
        valid(index) := true.B
        tags(index) := tag
      }
    }
  }

  //Output generation logic
  switch(state) {
    is(sOp) {
      ack := io.procReq && validData
    }
    //Issue the required number of reads, then wait until we leave this state
    //Assuming that we can issue reads immediately when we enter this state
    //Reads will be stored in a FIFO while waiting to be processed by bus
    is(sFetch) {
      memReadsIssued := Mux(memReadsIssued === c.memAccesesPerBlock.U, memReadsIssued, memReadsIssued + 1.U)
      memValid := memReadsIssued < c.memAccesesPerBlock.U
      when(io.replacement.finish) {
        memReadsIssued := 0.U
      }
    }
  }


  io.procAck := ack
  io.memReq := memValid
  io.memReadAddress := blockBaseAddr + (memReadsIssued << log2Ceil(c.cacheMemWidth/8).U)
}
