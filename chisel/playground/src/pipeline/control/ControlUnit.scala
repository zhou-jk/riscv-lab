package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._

class CtrlSignal extends Bundle {
  val allow_to_go = Bool()
  val do_flush = Bool()
}

class ControlUnit extends Module {
  val io = IO(new Bundle {
    val decodeUnitInfo = Input(new Info())
    val executeUnitInfo = Input(new Info())
    val memoryUnitInfo = Input(new Info())
    val writeBackUnitInfo = Input(new Info())
    val branch = Input(Bool())
    
    val fetchUnitCtrl = Output(new CtrlSignal())
    val decodeUnitCtrl = Output(new CtrlSignal())
    val executeUnitCtrl = Output(new CtrlSignal())
    val memoryUnitCtrl = Output(new CtrlSignal())
    val writeBackUnitCtrl = Output(new CtrlSignal())
  })

  val exe_conflict = io.executeUnitInfo.reg_wen && io.executeUnitInfo.reg_waddr.orR && 
                     ((io.decodeUnitInfo.src1_ren && (io.decodeUnitInfo.src1_raddr === io.executeUnitInfo.reg_waddr)) || 
                     (io.decodeUnitInfo.src2_ren && (io.decodeUnitInfo.src2_raddr === io.executeUnitInfo.reg_waddr)))

  val mem_conflict = io.memoryUnitInfo.reg_wen && io.memoryUnitInfo.reg_waddr.orR && 
                     ((io.decodeUnitInfo.src1_ren && (io.decodeUnitInfo.src1_raddr === io.memoryUnitInfo.reg_waddr)) || 
                     (io.decodeUnitInfo.src2_ren && (io.decodeUnitInfo.src2_raddr === io.memoryUnitInfo.reg_waddr)))
                     
  val wb_conflict = io.writeBackUnitInfo.reg_wen && io.writeBackUnitInfo.reg_waddr.orR && 
                    ((io.decodeUnitInfo.src1_ren && (io.decodeUnitInfo.src1_raddr === io.writeBackUnitInfo.reg_waddr)) || 
                    (io.decodeUnitInfo.src2_ren && (io.decodeUnitInfo.src2_raddr === io.writeBackUnitInfo.reg_waddr)))

  val data_conflict = exe_conflict || mem_conflict || wb_conflict

  io.fetchUnitCtrl.allow_to_go := !data_conflict
  io.fetchUnitCtrl.do_flush := io.branch

  io.decodeUnitCtrl.allow_to_go := !data_conflict
  io.decodeUnitCtrl.do_flush := io.branch

  io.executeUnitCtrl.allow_to_go := true.B
  io.executeUnitCtrl.do_flush := false.B

  io.memoryUnitCtrl.allow_to_go := true.B
  io.memoryUnitCtrl.do_flush := false.B

  io.writeBackUnitCtrl.allow_to_go := true.B
  io.writeBackUnitCtrl.do_flush := false.B
}