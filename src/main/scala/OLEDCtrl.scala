import chisel3._
import chisel3.util._
import chisel3.experimental.ExtModule

class OLEDCtrlIO extends Bundle {
  // Write command pins, when writeStart asserted high, load pixel data for ascii character into local memory at address.
  // When ready to use / operation completed, assert writeReady high - start ignored when display off or machine is otherwise busy.
  val writeStart = Input(Bool()) // inserts an ascii character's bitmap into display memory at specified address
  val writeAsciiData = Input(UInt(8.W)) // ascii value of character to add to memory
  val writeBaseAddr = Input(UInt(9.W)) // on screen address of character to add (y[1:0], x[3:0], 3'b0)
  val writeReady = Output(Bool()) // end of character bitmap write sequence

  // Update command pins, when updateStart asserted high, send pixel data contents of local memory - or zeroes if updateClear asserted - to OLED over SPI.
  // When ready to use / operation completed, assert updateReady high - start ignored when display off or machine is otherwise busy.
  val updateStart = Input(Bool()) // updates oled display with memory contents
  val updateClear = Input(Bool()) 
  val updateReady = Output(Bool()) // end of update sequence flag

  // Display On command pins, when dispOnStart asserted high, do initialization sequence as per spec.
  // When ready to use / operation completed, assert dispOnReady high - start ignored when display is already on.
  val dispOnStart = Input(Bool()) // starts initialization sequence
  val dispOnReady = Output(Bool()) // end of startup sequence flag

  // Display Off command pins, when dispOffStart asserted high, do safe shutdown sequence as spec.
  // When ready to use / operation completed, assert updateReady high - start ignored when display off or machine is otherwise busy.
  val dispOffStart = Input(Bool()) // starts shutdown sequence
  val dispOffReady = Output(Bool()) // shutdown sequence available flag

  // Toggle Display command pins, when toggleDispStart asserted high, sends commands to turn all display pixels on / revert to original state.
  // When ready to use / operation completed, toggleDispReady asserted high - start ignored when display off or machine is otherwise busy.
  val toggleDispStart = Input(Bool())
  val toggleDispReady = Output(Bool())

  // OLED command pins
  val SDIN = Output(Bool())
  val SCLK = Output(Bool())
  val DC = Output(Bool())
  val RES = Output(Bool())
  val VBAT = Output(Bool())
  val VDD = Output(Bool())
}

// read only memory for character bitmaps
class CharLib extends ExtModule {
  val clka = IO(Input(Clock()))
  val addra = IO(Input(UInt(10.W)))
  val douta = IO(Output(UInt(8.W)))
}

// pixel buffer
class PixelBuffer extends ExtModule {
  val clka = IO(Input(Clock()))
  val wea = IO(Input(Bool()))
  val addra = IO(Input(UInt(9.W)))
  val dina = IO(Input(UInt(8.W)))
  val clkb = IO(Input(Clock()))
  val addrb = IO(Input(UInt(9.W)))
  val doutb = IO(Output(UInt(8.W)))
}

// initialization sequence op code look up
class InitSequenceRom extends ExtModule {
  val clka = IO(Input(Clock()))
  val addra = IO(Input(UInt(4.W)))
  val douta = IO(Output(UInt(16.W)))
}

class OLEDCtrl extends Module {
  val io = IO(new OLEDCtrlIO)

  // STATE MACHINE CODES
  val (idle::
      startup::startupFetch:: /* STARTUP STATES */
      activeWait::activeUpdatePage::activeUpdateScreen::activeSendByte::activeUpdateWait::activeToggleDisp::activeToggleDispWait::activeWrite::activeWriteTran::activeWriteWait:: /* ACTIVE STATES */
      bringdownDispOff::bringdownVbatOff::bringdownDelay::bringdownVddOff:: /* BRINGDOWN STATES */
      utilitySpiWait::utilityDelayWait::utilityFullDispWait:: /* UTILITY/MISCELLANEOUS STATES */
      Nil) = Enum(20)

  // Details of OLED Commands can be found in the Solomon Systech SSD1306 Datasheet

  val state, afterState, afterPageState, afterCharState, afterUpdateState = RegInit(idle)

  val tempSpiStart = RegInit(false.B)
  val tempSpiData = RegInit(0.U(8.W))
  val tempSpiDone = Wire(Bool())

  // controller for spi connection to oled
  val spiCtrl = Module(new SpiCtrl)
  spiCtrl.io.sendStart := tempSpiStart
  spiCtrl.io.sendData := tempSpiData
  tempSpiDone := spiCtrl.io.sendReady
  io.SDIN := spiCtrl.io.SDO
  io.SCLK := spiCtrl.io.SCLK

  val tempDelayStart = RegInit(false.B)
  val tempDelayMs = RegInit(0.U(12.W))
  val tempDelayDone = Wire(Bool())

  // delay controller to handle N-millisecond waits
  val delayMs = Module(new DelayMs)
  delayMs.io.delayStart := tempDelayStart
  delayMs.io.delayTimeMs := tempDelayMs
  tempDelayDone := delayMs.io.delayDone

  val tempPage = RegInit(0.U(2.W))
  val tempIndex = RegInit(0.U(7.W))
  val tempWriteAscii = RegInit(0.U(8.W))
  val tempWriteBaseAddr = RegInit(0.U(9.W))
  val writeByteCount = RegInit(0.U(3.W))

  // combinatorial control signals for memories
  val pbufReadAddr = tempPage ## tempIndex
  val charLibAddr = tempWriteAscii ## writeByteCount
  val pbufWriteEn = state === activeWrite
  val pbufWriteAddr = tempWriteBaseAddr + writeByteCount
  val pbufWriteData = Wire(UInt(8.W))
  val pbufReadData = Wire(UInt(8.W))

  // read only memory for character bitmaps
  val charLib = Module(new CharLib)
  charLib.clka := clock
  charLib.addra := charLibAddr
  pbufWriteData := charLib.douta

  // pixel buffer
  val pixelBuffer = Module(new PixelBuffer)
  pixelBuffer.clka := clock
  pixelBuffer.wea := pbufWriteEn
  pixelBuffer.addra := pbufWriteAddr
  pixelBuffer.dina := pbufWriteData
  pixelBuffer.clkb := clock
  pixelBuffer.addrb := pbufReadAddr
  pbufReadData := pixelBuffer.doutb
  
  val startupCount = RegInit(0.U(4.W))
  val initOperation = Wire(UInt(16.W))

  // initialization sequence op code look up
  val initSeq = Module(new InitSequenceRom)
  initSeq.clka := clock
  initSeq.addra := startupCount
  initOperation := initSeq.douta

  val dispIsFull = RegInit(false.B)
  val clearScreen = RegInit(false.B)
  val updatePageCount = RegInit(0.U(3.W))
  val oledDc = RegInit(true.B)
  val oledRes = RegInit(true.B)
  val oledVdd = RegInit(true.B)
  val oledVbat = RegInit(true.B)
  val iopStateSelect = RegInit(false.B)
  val iopResSet = RegInit(false.B)
  val iopResVal = RegInit(false.B)
  val iopVbatSet = RegInit(false.B)
  val iopVbatVal = RegInit(false.B)
  val iopVddSet = RegInit(false.B)
  val iopVddVal = RegInit(false.B)
  val iopData = RegInit(0.U(8.W))

  // state machine
  switch (state) {
    is (idle) {
      when (io.dispOnStart) {
        state := startupFetch
        startupCount := 0.U
      }
      dispIsFull := false.B
    }
    /*
     INITIALIZATION SEQUENCE: (contained in init_sequence.dat)
     Turn VDD on (active low), delay 1ms
     Send DisplayOff command (hAE)
     Turn RES on (active low), delay 1ms
     Turn RES off (active low), delay 1ms
     Send ChargePump1 command (h8D)
     Send ChargePump2 command (h14)
     Send PreCharge1 command (hD9)
     Send PreCharge2 command (hF1)
     Turn VBAT on (active low), delay 100ms
     Send DispContrast1 command (h81)
     Send DispContrast2 command (h0F)
     Send SetSegRemap command (hA0)
     Send SetScanDirection command (hC0)
     Send Set Lower Column Address command (hDA)
     Send Lower Column Address (h00)
     Send Display On command (hAF)
     */
    is (startup) {
      oledDc := false.B
      oledVdd := Mux(iopVddSet, iopVddVal, oledVdd)
      oledRes := Mux(iopResSet, iopResVal, oledRes)
      oledVbat := Mux(iopVbatSet, iopVbatVal, oledVbat)

      when (~iopStateSelect) {
        tempDelayStart := true.B
        tempDelayMs := 0.U(4.W) ## iopData
        state := utilityDelayWait
      } otherwise {
        tempSpiStart := true.B
        tempSpiData := iopData
        state := utilitySpiWait
      }

      when (startupCount === 15.U) {
        afterState := activeUpdatePage
        afterUpdateState := activeWait
        afterCharState := activeUpdateScreen
        afterPageState := activeUpdateScreen
        updatePageCount := 0.U
        tempPage := 0.U
        tempIndex := 0.U
        clearScreen := true.B
      } otherwise {
        afterState := startupFetch
        startupCount := startupCount + 1.U
      }
    }
    is (startupFetch) {
      state := startup
      iopStateSelect := initOperation(14)
      iopResSet := initOperation(13)
      iopResVal := initOperation(12)
      iopVddSet := initOperation(11)
      iopVddVal := initOperation(10)
      iopVbatSet := initOperation(9)
      iopVbatVal := initOperation(8)
      iopData := initOperation(7, 0)
    }
    is (activeWait) {
      when (io.dispOffStart) {
        state := bringdownDispOff
      } .elsewhen (io.updateStart) {
        afterUpdateState := activeUpdateWait
        afterCharState := activeUpdateScreen
        afterPageState := activeUpdateScreen
        state := activeUpdatePage
        updatePageCount := 0.U
        tempPage := 0.U
        tempIndex := 0.U
        clearScreen := io.updateClear
      } .elsewhen (io.writeStart) {
        state := activeWriteTran
        writeByteCount := 0.U
        tempWriteAscii := io.writeAsciiData
        tempWriteBaseAddr := io.writeBaseAddr
      } .elsewhen (io.toggleDispStart) {
        oledDc := false.B
        dispIsFull := ~dispIsFull
        tempSpiData := "hA4".U(8.W) | (0.U(7.W) ## ~dispIsFull)
        tempSpiStart := true.B
        afterState := activeToggleDispWait
        state := utilitySpiWait
      }
    }
    is (activeWrite) {
      state := Mux(writeByteCount === 7.U, activeWriteWait, activeWriteTran)
      writeByteCount := writeByteCount + 1.U
    }
    is (activeWriteTran) {
      // give char lib a cycle for read to complete
      state := activeWrite
    }
    is (activeWriteWait) {
      state := Mux(~io.writeStart, activeWait, state)
      writeByteCount := 0.U
    }
    is (activeUpdatePage) {
      tempSpiData := MuxLookup(updatePageCount, tempSpiData, Array(
                      0.U -> "h22".U(8.W),
                      1.U -> 0.U(6.W) ## tempPage,
                      2.U -> "h00".U(8.W),
                      3.U -> "h10".U(8.W)
                     ))
      when (updatePageCount < 4.U) {
        oledDc := false.B
        afterState := activeUpdatePage
        tempSpiStart := true.B
        state := utilitySpiWait
      } otherwise {
        state := afterPageState
      }
      updatePageCount := updatePageCount + 1.U
    }
    is (activeSendByte) {
      oledDc := true.B
      when (clearScreen) {
        tempSpiData := 0.U
        afterState := afterCharState
        state := utilitySpiWait
        tempSpiStart := true.B
      } otherwise {
        tempSpiData := pbufReadData
        afterState := afterCharState
        state := utilitySpiWait
        tempSpiStart := true.B
      }
    }
    is (activeUpdateScreen) {
      when (tempIndex === 127.U) {
        tempIndex := 0.U
        tempPage := tempPage + 1.U
        updatePageCount := 0.U
        afterCharState := activeUpdatePage
        afterPageState := Mux(tempPage === 3.U, afterUpdateState, activeUpdateScreen)
      } otherwise {
        tempIndex := tempIndex + 1.U
        afterCharState := activeUpdateScreen
      }
      state := activeSendByte
    }
    is (activeUpdateWait) {
      state := Mux(~io.updateStart, activeWait, state)
    }
    is (activeToggleDispWait) {
      state := Mux(~io.toggleDispStart, activeWait, state)
    }
    // Bringdown States:
    // 1. turn off display
    // 2. power off vbat
    // 3. delay 100ms
    // 4. power off vdd
    is (bringdownDispOff) {
      oledDc := false.B
      tempSpiStart := true.B
      tempSpiData := "hAE".U(8.W)
      afterState := bringdownVbatOff
      state := utilitySpiWait
    }
    is (bringdownVbatOff) {
      oledVbat := false.B
      tempDelayStart := true.B
      tempDelayMs := 100.U(12.W)
      afterState := bringdownVddOff
      state := utilityDelayWait
    }
    is (bringdownVddOff) {
      oledVdd := false.B
      state := Mux(~io.dispOnStart, idle, state)
    }
    // Utility States, control states for SPI and DELAY handshakes.
    is (utilitySpiWait) {
      tempSpiStart := false.B
      state := Mux(tempSpiDone, afterState, state)
    }
    is (utilityDelayWait) {
      tempDelayStart := false.B
      state := Mux(tempDelayDone, afterState, state)
    }
  }

  // handshake flags, ready means associated start will be accepted
  io.dispOnReady := (state === idle && ~io.dispOnStart)
  io.updateReady := (state === activeWait && ~io.updateStart)
  io.writeReady := (state === activeWait && ~io.writeStart)
  io.dispOffReady := (state === activeWait && ~io.dispOffStart)
  io.toggleDispReady := (state === activeWait && ~io.toggleDispStart)

  // non-spi oled control signals
  io.DC := oledDc
  io.RES := oledRes
  io.VDD := oledVdd
  io.VBAT := oledVbat
}

object OLEDCtrl extends App {
  emitVerilog(new OLEDCtrl, Array("--target-dir", "generated"))
}
