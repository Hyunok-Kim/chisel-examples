import java.io.PrintWriter

import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage
import chisel3.experimental._
import firrtl.annotations.PresetAnnotation

class OLEDTop extends Module with RequireAsyncReset{

annotate(new ChiselAnnotation {
  override def toFirrtl = PresetAnnotation(reset.toTarget)
})

  val rstn = IO(Input(Bool()))
  val btnC = IO(Input(Bool()))
  val btnD = IO(Input(Bool()))
  val btnU = IO(Input(Bool()))
  val oledSdin = IO(Output(Bool()))
  val oledSclk = IO(Output(Bool()))
  val oledDc = IO(Output(Bool()))
  val oledRes = IO(Output(Bool()))
  val oledVbat = IO(Output(Bool()))
  val oledVdd = IO(Output(Bool()))
  val led = IO(Output(UInt(8.W)))

  val msg1 = " I am the OLED  "
  val msg2 = "  Display Demo  "
  val msg3 = " for Digilent's "
  val msg4 = "  Nexys Video   "

  val str1 = VecInit(msg1.map(_.U(8.W)))
  val str2 = VecInit(msg2.map(_.U(8.W)))
  val str3 = VecInit(msg3.map(_.U(8.W)))
  val str4 = VecInit(msg4.map(_.U(8.W)))

  val str1len = msg1.length.U
  val str2len = msg2.length.U
  val str3len = msg3.length.U
  val str4len = msg4.length.U

  val idle::init::active::done::fullDisp::write::writeWait::updateWait::Nil = Enum(8)

  val state = RegInit(init)
  val count = RegInit(0.U(6.W))
  val once = RegInit(false.B)
  val updateStart = RegInit(false.B)
  val dispOnStart = RegInit(true.B)
  val dispOffStart = RegInit(false.B)
  val toggleDispStart = RegInit(false.B)
  val writeStart = RegInit(false.B)
  val updateClear = RegInit(false.B)
  val writeBaseAddr = RegInit(0.U(9.W))
  val writeAsciiData = RegInit(0.U(8.W))
  val writeReady = Wire(Bool())
  val updateReady = Wire(Bool())
  val dispOnReady = Wire(Bool())
  val dispOffReady = Wire(Bool())
  val toggleDispReady = Wire(Bool())

  val oledCtrl = Module(new OLEDCtrl)
  oledCtrl.io.writeStart := writeStart
  oledCtrl.io.writeAsciiData := writeAsciiData
  oledCtrl.io.writeBaseAddr := writeBaseAddr
  writeReady := oledCtrl.io.writeReady
  oledCtrl.io.updateStart := updateStart
  updateReady := oledCtrl.io.updateReady
  oledCtrl.io.updateClear := updateClear
  oledCtrl.io.dispOnStart := dispOnStart
  dispOnReady := oledCtrl.io.dispOnReady
  oledCtrl.io.dispOffStart := dispOffStart
  dispOffReady := oledCtrl.io.dispOffReady
  oledCtrl.io.toggleDispStart := toggleDispStart
  toggleDispReady := oledCtrl.io.toggleDispReady
  oledSdin := oledCtrl.io.SDIN
  oledSclk := oledCtrl.io.SCLK
  oledDc := oledCtrl.io.DC
  oledRes := oledCtrl.io.RES
  oledVbat := oledCtrl.io.VBAT
  oledVdd := oledCtrl.io.VDD

  writeAsciiData := MuxCase(0.U, Array(
    (writeBaseAddr(8,7) === 0.U && writeBaseAddr(6,3) < str1len) -> str1(writeBaseAddr(6,3)),
    (writeBaseAddr(8,7) === 1.U && writeBaseAddr(6,3) < str2len) -> str2(writeBaseAddr(6,3)),
    (writeBaseAddr(8,7) === 2.U && writeBaseAddr(6,3) < str3len) -> str3(writeBaseAddr(6,3)),
    (writeBaseAddr(8,7) === 3.U && writeBaseAddr(6,3) < str4len) -> str4(writeBaseAddr(6,3))
  ))

  val dBtnC = Wire(Bool())
  val getDBtnC = Module(new Debouncer(COUNT_MAX=65535))
  getDBtnC.io.A := btnC
  dBtnC := getDBtnC.io.B
  val dBtnU = Wire(Bool())
  val getDBtnU = Module(new Debouncer(COUNT_MAX=65535))
  getDBtnU.io.A := btnU
  dBtnU := getDBtnU.io.B
  val dBtnD = Wire(Bool())
  val getDBtnD = Module(new Debouncer(COUNT_MAX=65535))
  getDBtnD.io.A := btnD
  dBtnD := getDBtnD.io.B
  val rst = Wire(Bool())
  val getDBtnR = Module(new Debouncer(COUNT_MAX=65535))
  getDBtnR.io.A := ~rstn
  rst := getDBtnR.io.B

  led := updateReady
  val initDone = dispOffReady | toggleDispReady | writeReady | updateReady
  val initReady = dispOnReady

  switch (state) {
    is (idle) {
      when (rst && initReady) {
        dispOnStart := true.B
        state := init
      }
      once := false.B
    }
    is (init) {
      dispOnStart := false.B
      state := Mux(~rst && initDone, active, state)
    }
    is (active) {
      when (rst && dispOffReady) {
        dispOffStart := true.B
        state := done
      } .elsewhen (~once && writeReady) {
        writeStart := true.B
        writeBaseAddr := 0.U
        state := writeWait
      } .elsewhen (once && dBtnU) {
        updateStart := true.B
        updateClear := false.B
        state := updateWait
      } .elsewhen (once && dBtnD) {
        updateStart := true.B
        updateClear := true.B
        state := updateWait
      } .elsewhen (dBtnC && toggleDispReady) {
        toggleDispStart := true.B
        state := fullDisp
      }
    }
    is (write) {
      writeStart := true.B
      writeBaseAddr := writeBaseAddr + "h8".U(9.W)
      state := writeWait
    }
    is (writeWait) {
      writeStart := false.B
      when (writeReady) {
        when (writeBaseAddr === "h1f8".U) {
          once := true.B
          state := active
        } otherwise {
          state := write
        }
      }
    }
    is (updateWait) {
      updateStart := false.B
      state := Mux(~dBtnU && initDone, active, state)
    }
    is (done) {
      dispOffStart := false.B
      state := Mux(~rst && initReady, idle, state)
    }
    is (fullDisp) {
      toggleDispStart := false.B
      state := Mux(~dBtnC && initDone, active, state)
    }
  }
}

object OLEDTop extends App {
  //emitVerilog(new OLEDTop, Array("--target-dir", "generated"))
  val writer = new PrintWriter("OLED/OLEDTop.v")
  writer.write(ChiselStage.emitVerilog(new OLEDTop))
  writer.close()
}
