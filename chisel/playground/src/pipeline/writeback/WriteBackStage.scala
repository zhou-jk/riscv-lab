package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class MemWbData extends Bundle {
  val pc            = UInt(XLEN.W)
  val info          = new Info()
  val rd_info       = new RdInfo()
  val has_exception = Bool()
}

class MemoryUnitWriteBackUnit extends Bundle {
  val data = new MemWbData()
}

class WriteBackStage extends Module {
  val io = IO(new Bundle {
    val memoryUnit    = Input(new MemoryUnitWriteBackUnit())
    val writeBackUnit = Output(new MemoryUnitWriteBackUnit())
    val ctrl          = Input(new CtrlSignal())
  })

  val data = RegInit(0.U.asTypeOf(new MemWbData()))
  
  when(io.ctrl.do_flush) {
    data := 0.U.asTypeOf(new MemWbData())
  }.elsewhen(io.ctrl.allow_to_go) {
    data := io.memoryUnit.data
  }

  io.writeBackUnit.data := data
}
