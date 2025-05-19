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
    val flush = Input(Bool())
    
    val icache_stall = Input(Bool())
    val dcache_stall = Input(Bool())
    
    val fetchUnitCtrl = Output(new CtrlSignal())
    val decodeUnitCtrl = Output(new CtrlSignal())
    val executeUnitCtrl = Output(new CtrlSignal())
    val memoryUnitCtrl = Output(new CtrlSignal())
    val writeBackUnitCtrl = Output(new CtrlSignal())
  })

  val exe_is_load = io.executeUnitInfo.valid && 
                    (io.executeUnitInfo.fusel === FuType.lsu) && 
                    LSUOpType.isLoad(io.executeUnitInfo.op(3, 0))
  
  val exe_conflict = exe_is_load && io.executeUnitInfo.reg_wen && io.executeUnitInfo.reg_waddr.orR && 
                    ((io.decodeUnitInfo.src1_ren && io.decodeUnitInfo.src1_raddr === io.executeUnitInfo.reg_waddr) || 
                     (io.decodeUnitInfo.src2_ren && io.decodeUnitInfo.src2_raddr === io.executeUnitInfo.reg_waddr))

  val cache_stall = io.icache_stall || io.dcache_stall
  val data_conflict = exe_conflict || cache_stall

  io.fetchUnitCtrl.allow_to_go := !data_conflict
  io.fetchUnitCtrl.do_flush := io.flush

  io.decodeUnitCtrl.allow_to_go := !data_conflict
  io.decodeUnitCtrl.do_flush := io.flush

  io.executeUnitCtrl.allow_to_go := !cache_stall
  io.executeUnitCtrl.do_flush := false.B

  io.memoryUnitCtrl.allow_to_go := !cache_stall
  io.memoryUnitCtrl.do_flush := false.B

  io.writeBackUnitCtrl.allow_to_go := !cache_stall
  io.writeBackUnitCtrl.do_flush := false.B
}