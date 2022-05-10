import chisel3._
import chisel3.util._

class DelayMsIO extends Bundle {
  val delayTimeMs = Input(UInt(12.W))
  val delayStart = Input(Bool())
  val delayDone = Output(Bool())
}

class DelayMs(max: Int = 100000) extends Module {
  val io = IO(new DelayMsIO)

  val MAX = (max-1).U
  val idle::hold::done::Nil = Enum(3)

  val state = RegInit(idle)
  val stopTime, msCounter = RegInit(0.U(12.W))
  val clkCounter = RegInit(0.U(log2Ceil(max-1).W))

  switch (state) {
    is (idle) {
      clkCounter := 0.U
      msCounter := 0.U
      stopTime := io.delayTimeMs - 1.U
      state := Mux(io.delayStart, hold, state)
    }
    is (hold) {
      when (clkCounter === MAX) {
        clkCounter := 0.U
        msCounter := Mux(msCounter === stopTime, 0.U, msCounter + 1.U)
      } otherwise {
        clkCounter := clkCounter + 1.U
      }
      when (msCounter === stopTime && clkCounter === MAX) {
        state := Mux(io.delayStart, done, idle)
      }
    }
    is (done) {
      state := Mux(~io.delayStart, idle, state)
    }
  }

  io.delayDone := (state === idle) && (~io.delayStart)
}

object DelayMs extends App {
  emitVerilog(new DelayMs(100000), Array("--target-dir", "generated"))
}
