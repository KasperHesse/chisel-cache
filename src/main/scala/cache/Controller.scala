package cache

import chisel3._
import chisel3.util._

class Controller(c: CacheConfig) extends Module {
  val io = IO(new ControllerIO(c))

  val sIdle :: sOp :: sRead :: sFetch :: sWrite :: Nil = Enum(5)
  val state = RegInit(sIdle)


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
  /** Acknowledge signal back to processor, signalling read data can be sampled */
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
    //If write && block is valid && block is not dirty, write into that block
    is(sOp) {
      when(!io.we && validData) {
        state := sRead
      } .elsewhen(!io.we && !validData) {
        state := sFetch
      } .elsewhen(io.we && validData && !dirty(index)) {
        state := sWrite
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
    //Write from processor to cache state
    //If another write to valid non-dirty block, stay here
    //Otherwise, go to idle
    is(sWrite) {
      dirty(index) := true.B
      //Just output ack? Cache module itself can update the data using state information
      when(io.procReq && io.we && validData && !dirty(index)) {
        state := sWrite
      } .otherwise { //TODO handle subsequent reads or writes to dirty/non-valid locations
        state := sIdle
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
    //Assert ack high to signal to processor that data has arrived
    is(sRead) {
      ack := true.B
    }
    is(sWrite) {
      ack := true.B
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
