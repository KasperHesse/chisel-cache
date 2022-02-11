package cache

import chisel3._
import chisel3.util._

class Controller(c: CacheConfig) extends Module {
  val io = IO(new ControllerIO(c))

  val sIdle :: sOp :: sRead :: sFetch :: Nil = Enum(4)
  val state = RegInit(sIdle)


  val index = io.addr(c.indexH, c.indexL)
  val tag = io.addr(c.tagH, c.tagL)

  //Metadata registers
  /** Dirty bit register */
  val dirty = RegInit(VecInit(Seq.fill(c.numEntries)(false.B)))
  /** Valid bit register */
  val valid = RegInit(VecInit(Seq.fill(c.numEntries)(false.B)))
  /** Tag register */
  val tags = RegInit(VecInit(Seq.fill(c.numEntries)(0.U(log2Ceil(c.wordWidth-c.indexH).W))))

  //Output signals
  /** Ready signal back to processor, signalling read data can be sampled */
  val readyReadData = WireDefault(false.B)
  /** Valid signal to memory, indicating a memory read should be performed */
  val memValid = WireDefault(false.B)


  //Helper signals
  /** Base address of block: Tag + index bits, but block and byte offset set to 0 */
  val blockBaseAddr = Cat(io.addr(c.wordWidth-1, c.indexL), 0.U(c.indexL.W))

  val memReadsIssued = RegInit(0.U(log2Ceil(c.memAccesesPerBlock+1).W))

  val validData = tags(index) === tag && valid(index)
  //Next state logic
  switch(state) {
    //Idle state: Move out when a valid operation is received
    is(sIdle) {
      when(io.procReq) {
        state := sOp
      }
    }
    //Start operation state
    //If read && data is already stored in cache, return that data on next cc
    //If read && data is not already stored, go fetch that data
    is(sOp) {
      when(!io.we && validData) {
        state := sRead
      } .elsewhen(!io.we && !validData) {
        state := sFetch
      } .otherwise { //TODO: Implement write behaviour
        state := sIdle
      }
    }
    //Read data state
    //If another read for valid data follows, stay here
    //If another read for invalid data follows, go to fetch
    is(sRead) {
      when(io.procReq && validData && !io.we) {
        state := sRead
      } .elsewhen(io.procReq && !io.we) {
        state := sFetch
      } .otherwise { //TODO should go to op-state if operation is a write
        state := sIdle
      }
    }
    //Fetch data from memory state
    //Once data arrives, go to op-state and reset count register
    is(sFetch) {
      when(io.replacement.finish) {
        state := sOp
        memReadsIssued := 0.U
        valid(index) := true.B
        tags(index) := tag

      }
    }
  }

  //Output generation logic
  switch(state) {
    //Assert readyReadData high to signal to processor that data has arrived
    is(sRead) {
      readyReadData := true.B
    }
    //Issue the required number of reads, then wait until we leave this state
    //Assuming that we can issue reads immediately when we enter this state
    //Reads will be stored in a FIFO while waiting to be processed by bus
    is(sFetch) {
      memReadsIssued := Mux(memReadsIssued === c.memAccesesPerBlock.U, memReadsIssued, memReadsIssued + 1.U)
      memValid := memReadsIssued < c.memAccesesPerBlock.U
    }
  }

  io.procAck := readyReadData
  io.memReq := memValid
  io.memReadAddress := blockBaseAddr + (memReadsIssued << log2Ceil(c.cacheMemWidth/8).U)
}
