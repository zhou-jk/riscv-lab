package cpu.cache

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.defines.AXIParam._

class DCache extends Module {
  val io = IO(new Bundle {
    val cpu  = Flipped(new DCacheIO())
    val axi  = new AXIMaster()
  })
  
  // DCache状态机状态定义
  // 基于图9-63访存状态机定义状态
  val sIdle :: sReadAddr :: sReadData :: sWriteAddr :: sWriteData :: sWriteResp :: sWait :: Nil = Enum(7)
  val state = RegInit(sIdle)
  
  // 备份数据寄存器
  val rdata_reg = Reg(UInt(AXI_DATA_WIDTH.W))
  val addr_reg = RegEnable(io.cpu.addr, state === sIdle && io.cpu.en)
  val wdata_reg = RegEnable(io.cpu.wdata, state === sIdle && io.cpu.en && io.cpu.wen)
  val wstrb_reg = RegEnable(io.cpu.wstrb, state === sIdle && io.cpu.en && io.cpu.wen)
  
  // 写握手控制寄存器
  val awvalid_reg = RegInit(false.B)
  val wvalid_reg = RegInit(false.B)
  
  // AXI读请求通道信号默认值
  io.axi.arid    := AXI_ID_DCACHE // 数据访问ID为1
  io.axi.arlen   := 0.U           // 读取单个数据
  io.axi.arburst := AXI_BURST_INCR // 递增模式
  io.axi.arlock  := 0.U
  io.axi.arcache := 0.U
  io.axi.arprot  := 0.U
  io.axi.arvalid := state === sReadAddr
  io.axi.arsize  := io.cpu.size
  io.axi.araddr  := addr_reg
  
  // 读响应通道信号默认值
  io.axi.rready  := state === sReadData
  
  // 写请求通道信号默认值
  io.axi.awid    := AXI_ID_DCACHE // 数据访问ID为1
  io.axi.awlen   := 0.U           // 写入单个数据
  io.axi.awburst := AXI_BURST_INCR // 递增模式
  io.axi.awlock  := 0.U
  io.axi.awcache := 0.U
  io.axi.awprot  := 0.U
  io.axi.awvalid := awvalid_reg
  io.axi.awsize  := io.cpu.size
  io.axi.awaddr  := addr_reg
  
  // 写数据通道信号默认值
  io.axi.wid     := AXI_ID_DCACHE // 数据访问ID为1
  io.axi.wlast   := true.B        // 始终是最后一个数据
  io.axi.wvalid  := wvalid_reg
  io.axi.wdata   := wdata_reg
  io.axi.wstrb   := wstrb_reg
  
  // 写响应通道默认值
  io.axi.bready  := state === sWriteResp
  
  // CPU接口输出默认值
  io.cpu.rdata  := rdata_reg
  io.cpu.stall  := state =/= sIdle && state =/= sWait
  
  // 握手信号检测
  val aw_handshake = io.axi.awready && awvalid_reg
  val w_handshake = io.axi.wready && wvalid_reg
  
  // 根据状态控制AXI信号
  switch(state) {
    is(sIdle) {
      awvalid_reg := false.B
      wvalid_reg := false.B
      
      when(io.cpu.en) {
        when(io.cpu.wen) {
          // 写请求
          state := sWriteAddr
          awvalid_reg := true.B
          wvalid_reg := true.B
        }.otherwise {
          // 读请求
          state := sReadAddr
        }
      }
    }
    
    is(sReadAddr) {
      when(io.axi.arready) {
        state := sReadData
      }
    }
    
    is(sReadData) {
      when(io.axi.rvalid) {
        rdata_reg := io.axi.rdata
        when(io.cpu.finish_single_request) {
          state := sIdle
        }.otherwise {
          state := sWait
        }
      }
    }
    
    is(sWriteAddr) {
      // 检测握手信号并相应地设置下一个状态和寄存器
      when(aw_handshake) {
        awvalid_reg := false.B
      }
      
      when(w_handshake) {
        wvalid_reg := false.B
      }
      
      when(aw_handshake && w_handshake) {
        state := sWriteResp
      }.elsewhen(aw_handshake && !w_handshake) {
        state := sWriteData
      }.elsewhen(!aw_handshake && w_handshake) {
        state := sWriteAddr
      }
    }
    
    is(sWriteData) {
      wvalid_reg := true.B
      when(w_handshake) {
        wvalid_reg := false.B
        state := sWriteResp
      }
    }
    
    is(sWriteResp) {
      when(io.axi.bvalid) {
        when(io.cpu.finish_single_request) {
          state := sIdle
        }.otherwise {
          state := sWait
        }
      }
    }
    
    is(sWait) {
      when(io.cpu.finish_single_request) {
        state := sIdle
      }
    }
  }
} 