import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class OLEDTest extends AnyFlatSpec with ChiselScalatestTester {
  "SpiCtrl" should "pass" in {
    test(new SpiCtrl).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      while(!dut.io.sendReady.peek.litToBoolean)
        dut.clock.step()

      dut.io.sendStart.poke(true.B)
      dut.io.sendData.poke("hAA".U)
      dut.clock.step()

      dut.io.sendStart.poke(false.B)
      dut.io.sendData.poke(0.U)

      while(!dut.io.sendReady.peek.litToBoolean)
        dut.clock.step()

      dut.clock.step()

      dut.io.sendStart.poke(true.B)
      dut.io.sendData.poke("h55".U)
      dut.clock.step()

      dut.io.sendStart.poke(false.B)
      dut.io.sendData.poke(0.U)

      while(!dut.io.sendReady.peek.litToBoolean)
        dut.clock.step()

      dut.clock.step(5)

    }
  }
  "DelayMs" should "pass" in {
    test(new DelayMs(10)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      while(!dut.io.delayDone.peek.litToBoolean)
        dut.clock.step()

      dut.io.delayStart.poke(true.B)
      dut.io.delayTimeMs.poke(2.U)
      dut.clock.step()

      dut.io.delayStart.poke(false.B)
      dut.io.delayTimeMs.poke(0.U)

      while(!dut.io.delayDone.peek.litToBoolean)
        dut.clock.step()

      dut.clock.step(5)
    }
  }
  "Debouncer" should "pass" in {
    test(new Debouncer).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      dut.clock.step(5)

      dut.io.A.poke(true.B)
      dut.clock.step()
      dut.io.A.poke(false.B)
      dut.clock.step()
      dut.io.A.poke(true.B)
      dut.clock.step(20)

      dut.io.A.poke(false.B)
      dut.clock.step()
      dut.io.A.poke(true.B)
      dut.clock.step()
      dut.io.A.poke(false.B)
      dut.clock.step(20)
    }
  }
}
