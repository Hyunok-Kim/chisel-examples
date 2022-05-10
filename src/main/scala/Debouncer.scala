import chisel3._
import chisel3.util._

class DebouncerIO extends Bundle {
  val A = Input(Bool())
  val B = Output(Bool())
}

class Debouncer(COUNT_MAX : Int = 15) extends Module {
  val io = IO(new DebouncerIO)

  val offIdle::offTran::onTran::onIdle::Nil = List("b00", "b01", "b11", "b10").map(_.U(2.W))

  val state = RegInit(offIdle)
  val count = RegInit(0.U(log2Ceil(COUNT_MAX).W))

  count := Mux(~state(0), 0.U, count + 1.U)

  switch (state) {
    is (offIdle) {
      state := Mux(io.A, offTran, state)
    }
    is (offTran) {
      when (~io.A) {
        state := offIdle
      } .elsewhen (count === COUNT_MAX.U) {
        state := onIdle
      }
    }
    is (onTran) {
      when (io.A) {
        state := onIdle
      } .elsewhen (count === COUNT_MAX.U) {
        state := offIdle
      }
    }
    is (onIdle) {
      state := Mux(~io.A, onTran, state)
    }
  }

  io.B := state(1)
}

object Debouncer extends App {
  emitVerilog(new Debouncer, Array("--target-dir", "generated"))
}
