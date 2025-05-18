package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.CpuConfig

class WriteBackUnit extends Module {
  val io = IO(new Bundle {
    val writeBackStage = Input(new MemoryUnitWriteBackUnit())
    val regfile        = Output(new RegWrite())
    val debug          = new DEBUG()
  })

  // 写回阶段完成数据的写回操作
  // 同时该阶段还负责差分测试的比对工作
  val wb_data = io.writeBackStage.data
  val wb_pc   = wb_data.pc
  val wb_info = wb_data.info
  val wb_rd_info = wb_data.rd_info

  val has_exception = wb_data.has_exception
  val final_reg_wen = wb_info.valid && wb_info.reg_wen & !has_exception

  val wdata_selected = wb_rd_info.wdata(wb_info.fusel)

  io.regfile.wen   := final_reg_wen
  io.regfile.waddr := wb_info.reg_waddr
  io.regfile.wdata := wdata_selected

  io.debug.commit   := wb_info.valid
  io.debug.pc       := wb_pc
  io.debug.rf_wnum  := wb_info.reg_waddr
  io.debug.rf_wdata := wdata_selected
}
