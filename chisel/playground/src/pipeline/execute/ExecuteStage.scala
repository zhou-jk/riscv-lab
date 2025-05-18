package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class IdExeData extends Bundle {
  val pc       = UInt(XLEN.W)
  val info     = new Info()
  val src_info = new SrcInfo()
  val ex       = new ExceptionInfo()
}

class DecodeUnitExecuteUnit extends Bundle {
  val data = new IdExeData()
}

class ExecuteStage extends Module {
  val io = IO(new Bundle {
    val decodeUnit  = Input(new DecodeUnitExecuteUnit())
    val executeUnit = Output(new DecodeUnitExecuteUnit())
    val ctrl        = Input(new CtrlSignal())
  })

  val data = RegInit(0.U.asTypeOf(new IdExeData()))

  when(io.ctrl.do_flush) {
    data := 0.U.asTypeOf(data)
  }.elsewhen(!io.ctrl.allow_to_go) {
    data := 0.U.asTypeOf(data)
  }.otherwise {
    data := io.decodeUnit.data
  }

  io.executeUnit.data := data
}
