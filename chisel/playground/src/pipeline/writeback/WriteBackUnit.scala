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
  // 完成WriteBackUnit模块的逻辑
  val wb_data = io.writeBackStage.data
  val wb_pc   = wb_data.pc
  val wb_info = wb_data.info
  val wb_rd_info = wb_data.rd_info
  val has_exception_from_prev_stage = wb_data.has_exception

  // 如果有异常，不写寄存器
  val final_reg_wen = wb_info.valid && wb_info.reg_wen && !has_exception_from_prev_stage

  val wdata_selected = wb_rd_info.wdata(wb_info.fusel)

  // ADD PRINTF STATEMENTS HERE
  // Print if current PC for WB is 0xdc (meaning it's the problematic instruction itself)
  // OR if current PC for WB is 0xe0 (meaning it's the instruction *after* 0xdc, to see its effect)
  when(wb_pc === "h800000dc".U(XLEN.W) || wb_pc === "h800000e0".U(XLEN.W) ) { 
    printf(p"WriteBackUnit: wb_pc=0x${Hexadecimal(wb_pc)}, inst=0x${Hexadecimal(wb_info.inst)}\n")
    printf(p"               wb_info.valid=${wb_info.valid}, wb_info.reg_wen=${wb_info.reg_wen}, wb_info.fusel=${wb_info.fusel}\n")
    printf(p"               wb_info.reg_waddr=0x${Hexadecimal(wb_info.reg_waddr)}\n")
    printf(p"               has_exception_from_prev_stage=${has_exception_from_prev_stage}\n")
    printf(p"               wdata_selected=0x${Hexadecimal(wdata_selected)}, final_reg_wen=${final_reg_wen}\n")
  }

  io.regfile.wen   := final_reg_wen
  io.regfile.waddr := wb_info.reg_waddr
  io.regfile.wdata := wdata_selected

  // 对有异常的指令，不在debug接口中提交
  io.debug.commit   := wb_info.valid && !has_exception_from_prev_stage
  io.debug.pc       := wb_pc
  io.debug.rf_wnum  := wb_info.reg_waddr
  io.debug.rf_wdata := wdata_selected
}
