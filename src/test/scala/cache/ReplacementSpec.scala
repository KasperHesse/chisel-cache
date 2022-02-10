package cache

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ReplacementSpec extends AnyFlatSpec with ChiselScalatestTester{
  behavior of "Cache replacement"

  def expectWe(we: Vec[Bool], expect: Seq[Boolean]): Unit = {
    for(i <- we.indices) {
      we(i).expect(expect(i).B)
    }
  }

  it should "generate we-signals for a direct-mapped cache" in {
    val config = CacheConfig(1024, 4, 32, 32, 32, "Direct")
    test(new Replacement(config)) {dut =>
      //Ensure correct signals at the start
      dut.io.cache.memValid.poke(false.B)
      dut.io.cache.select.expect(0.U)
      dut.io.controller.finish.expect(false.B)
      dut.clock.step()

      //Poke memValid, start generating outputs
      //Whenever memValid is asserted, we signals are used, shifted on next cc
      dut.io.cache.memValid.poke(true.B)
      expectWe(dut.io.cache.we, Seq(true, false, false, false))
      dut.io.controller.finish.expect(false.B)
      dut.clock.step()

      //On next cycle, we expect the true-signal to have shifted
      expectWe(dut.io.cache.we, Seq(false, true, false, false))
      dut.io.controller.finish.expect(false.B)
      dut.clock.step()

      //Same
      expectWe(dut.io.cache.we, Seq(false, false, true, false))
      dut.io.controller.finish.expect(false.B)
      dut.clock.step()

      //Same
      //At this point it should also assert finish=true
      expectWe(dut.io.cache.we, Seq(false, false, false, true))
      dut.io.controller.finish.expect(true.B)
      dut.clock.step()

      //Now, it should have reset
      //memValid will be low at this point
      dut.io.cache.memValid.poke(false.B)
      expectWe(dut.io.cache.we, Seq(true, false, false, false))
      dut.io.controller.finish.expect(false.B)
    }
  }

//  it should "keep we signals constant when memValid is not asserted" in {
//    ???
//  }
}
