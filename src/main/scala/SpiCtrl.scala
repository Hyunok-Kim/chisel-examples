import chisel3._
import chisel3.util._


class SpiCtrlIO extends Bundle {
  val sendStart = Input(Bool())
  val sendData = Input(UInt(8.W))
  val sendReady = Output(Bool())
  val CS = Output(Bool())
  val SDO = Output(Bool())
  val SCLK = Output(Bool())
}

class SpiCtrl extends Module {
  val io = IO(new SpiCtrlIO)

  val idle::send::holdCS::hold::Nil = Enum(4)

  val COUNTER_MID = 4.U
  val COUNTER_MAX = 9.U
  val SCLK_DUTY = 5.U

  val state = RegInit(idle)
  val shiftRegister = RegInit(0.U(8.W))
  val shiftCounter = RegInit(0.U(4.W))
  val counter = RegInit(0.U(5.W))
  val tempSDO = RegInit(false.B)

  counter := 0.U

  switch (state) {
    is (idle) {
      shiftCounter := 0.U
      shiftRegister := io.sendData
      state := Mux(io.sendStart, send, state)
    }
    is (send) {
      when (counter === COUNTER_MID) {
        tempSDO := shiftRegister(7)
        shiftRegister := shiftRegister(6, 0) ## 0.U
        shiftCounter := Mux(shiftCounter === 8.U, 0.U, shiftCounter + 1.U)
      }
      when (shiftCounter === 8.U && counter === COUNTER_MID) {
        state := holdCS
      } otherwise {
        counter := Mux(counter === COUNTER_MAX, 0.U, counter + 1.U)
      }
    }
    is (holdCS) {
      shiftCounter := shiftCounter + 1.U
      state := Mux(shiftCounter === 4.U, hold, state)
    }
    is (hold) {
      state := Mux(~io.sendStart, idle, state)
    }
  }

  io.sendReady := (state === idle && ~io.sendStart)
  io.SCLK := (counter < SCLK_DUTY) | io.CS
  io.CS := (state =/= send) && (state =/= holdCS)
  io.SDO := tempSDO | io.CS | (state === holdCS)
}

object SpiCtrl extends App {
  emitVerilog(new SpiCtrl, Array("--target-dir", "generated"))
}
