package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class ForwardInfo extends Bundle {
  val reg_wen   = Bool()
  val reg_waddr = UInt(REG_ADDR_WID.W)
  val fusel     = UInt(FuType().getWidth.W)
}

class ForwardSrc extends Bundle {
  val src_ren   = Bool()
  val src_raddr = UInt(REG_ADDR_WID.W)
}

class ForwardData extends Bundle {
  val wdata = Vec(FuType.num, UInt(XLEN.W))
}

class ForwardCtrl extends Module {
  val io = IO(new Bundle {
    val src1 = Input(new ForwardSrc())
    val src2 = Input(new ForwardSrc())

    val exe_info = Input(new ForwardInfo())
    val mem_info = Input(new ForwardInfo())
    val wb_info  = Input(new ForwardInfo())

    val exe_data = Input(new ForwardData())
    val mem_data = Input(new ForwardData())
    val wb_data  = Input(new ForwardData())

    val reg_src1_data = Input(UInt(XLEN.W))
    val reg_src2_data = Input(UInt(XLEN.W))

    val final_src1_data = Output(UInt(XLEN.W))
    val final_src2_data = Output(UInt(XLEN.W))
  })

  val src1_forward = Wire(UInt(XLEN.W))
  
  when(!io.src1.src_ren || io.src1.src_raddr === 0.U) {
    src1_forward := io.reg_src1_data
  }.elsewhen(io.exe_info.reg_wen && io.exe_info.reg_waddr === io.src1.src_raddr) {
    src1_forward := io.exe_data.wdata(io.exe_info.fusel)
  }.elsewhen(io.mem_info.reg_wen && io.mem_info.reg_waddr === io.src1.src_raddr) {
    src1_forward := io.mem_data.wdata(io.mem_info.fusel)
  }.elsewhen(io.wb_info.reg_wen && io.wb_info.reg_waddr === io.src1.src_raddr) {
    src1_forward := io.wb_data.wdata(io.wb_info.fusel)
  }.otherwise {
    src1_forward := io.reg_src1_data
  }
  
  val src2_forward = Wire(UInt(XLEN.W))
  
  when(!io.src2.src_ren || io.src2.src_raddr === 0.U) {
    src2_forward := io.reg_src2_data
  }.elsewhen(io.exe_info.reg_wen && io.exe_info.reg_waddr === io.src2.src_raddr) {
    src2_forward := io.exe_data.wdata(io.exe_info.fusel)
  }.elsewhen(io.mem_info.reg_wen && io.mem_info.reg_waddr === io.src2.src_raddr) {
    src2_forward := io.mem_data.wdata(io.mem_info.fusel)
  }.elsewhen(io.wb_info.reg_wen && io.wb_info.reg_waddr === io.src2.src_raddr) {
    src2_forward := io.wb_data.wdata(io.wb_info.fusel)
  }.otherwise {
    src2_forward := io.reg_src2_data
  }
  
  io.final_src1_data := src1_forward
  io.final_src2_data := src2_forward
}