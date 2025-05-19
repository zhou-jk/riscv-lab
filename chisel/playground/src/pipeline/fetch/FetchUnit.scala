package cpu.pipeline

import chisel3._
import chisel3.util._
import cpu.defines.Const._
import cpu.CpuConfig
import cpu.defines._

class FetchUnit extends Module with HasExceptionNO {
  val io = IO(new Bundle {
    val decodeStage = new FetchUnitDecodeUnit()
    val icache      = new ICacheIO()
        
    val flush       = Input(Bool())
    val target      = Input(UInt(XLEN.W))
    val ctrl        = Input(new CtrlSignal())
  })

  val idle :: req :: wait_valid :: Nil = Enum(3)
  val state = RegInit(idle)
  val pc = RegInit(PC_INIT)

  // 计算下一个PC值
  val pc_next = Mux(io.flush, io.target, 
                Mux(io.ctrl.allow_to_go && state === wait_valid && io.icache.valid, pc + 4.U, pc))
  
  // PC更新逻辑 - 只在收到有效数据时更新
  when(state === wait_valid && io.icache.valid && io.ctrl.allow_to_go) {
    pc := pc_next
  }.elsewhen(io.flush) {
    pc := io.target
  }
  
  // 检查地址对齐
  val pc_misaligned = pc(1, 0) =/= 0.U
  io.icache.addr := pc
  
  // 只在req状态设置请求信号
  io.icache.req := !reset.asBool && !pc_misaligned && state === req
  
  // 只在wait_valid状态且收到有效数据时发送完成信号
  io.icache.finish_single_request := state === wait_valid && io.icache.valid && io.ctrl.allow_to_go
  
  // 数据有效条件
  io.decodeStage.data.valid := state === wait_valid && io.icache.valid && !io.ctrl.do_flush
  io.decodeStage.data.pc    := pc
  io.decodeStage.data.inst  := Mux(pc_misaligned, Instructions.NOP, io.icache.inst)
  
  // 状态机转换逻辑
  switch(state) {
    is(idle) {
      state := req
    }
    
    is(req) {
      when(io.icache.stall) {
        // ICache正在处理，保持req状态
        state := req
      }.otherwise {
        // ICache已接收请求，等待有效数据
        state := wait_valid
      }
    }
    
    is(wait_valid) {
      when(io.icache.valid && io.ctrl.allow_to_go) {
        // 收到有效数据且控制单元允许执行，返回req状态取下一条指令
        state := req
      }.elsewhen(io.flush) {
        // 如果遇到flush，立即回到req状态取新地址
        state := req
      }
    }
  }
}
