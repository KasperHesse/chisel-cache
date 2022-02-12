package cache

import chisel3._
import chisel3.util.experimental.loadMemoryFromFile
import chisel3.util._

import java.io.{BufferedWriter, FileWriter}
/**
 * A wrapper module around the cache, hooking the cache up to a memory block
 * Primarily used for testing cache behaviour
 */
class CacheWrapper(c: CacheConfig) extends Module {
  val io = IO(Flipped(ReqAck(new ProcessorCacheIO(c))))

  //The cache
  val cache = Module(new Cache(c))
  //The memory that the cache is hooked up to
  val mem = SyncReadMem(math.pow(2,10).toInt, UInt(c.cacheMemWidth.W))

  //Generate meminit file and hook up
  CacheWrapper(c)
  loadMemoryFromFile(mem, "cachewrappermeminit.txt")

  //Interface between cache and memory
  //We wish to use word-aligned memory acceses
  val addr = (cache.io.mem.bits.addr >> log2Ceil(c.cacheMemWidth/8)).asUInt
  val rdData = mem.read(addr)
  when(cache.io.mem.bits.we && cache.io.mem.req) {
    mem.write(addr, cache.io.mem.bits.wrData)
  }
  cache.io.mem.ack := RegNext(cache.io.mem.req)
  cache.io.mem.bits.rdData := rdData

  cache.io.proc.req := io.req
  cache.io.proc.bits.addr := io.bits.addr
  cache.io.proc.bits.we := io.bits.we
  cache.io.proc.bits.wrData := io.bits.wrData
  io.bits.rdData := cache.io.proc.bits.rdData
  io.ack := cache.io.proc.ack
}

/**
 * Companion object for the cache wrapper

 */
object CacheWrapper extends App {
  /**
   * Generates a memory initialization file for the memory in the cache wrapper
   * @param c
   */
  def apply(c: CacheConfig): Unit = {
    val file = new BufferedWriter(new FileWriter("cachewrappermeminit.txt"))
    val zeros = "0"*16
    for(i <- 0 until math.pow(2,10).toInt) {
      val w = (zeros + i.toHexString).takeRight(c.cacheMemWidth/4)
      file.write(f"$w\n")
    }
    file.close()
  }
}
