package cpu.cache

import chisel3._
import chisel3.util._
import cpu.defines._
import cpu.defines.Const._
import cpu.defines.AXIParam._

class ICache extends Module {
  val io = IO(new Bundle {
    val cpu  = Flipped(new ICacheIO())
    val axi  = new AXIMaster()
  })
  
  // 更清晰的状态命名
  val idle :: ar_req :: r_wait :: data_ready :: Nil = Enum(4)
  val state = RegInit(idle)
  
  // 备份数据寄存器
  val valid_reg = RegInit(false.B)
  val inst_reg = Reg(UInt(INST_WID.W))
  
  // 保存CPU请求的地址
  val addr_reg = RegEnable(io.cpu.addr, io.cpu.req && state === idle)
  
  // 添加请求和响应计数器用于调试
  val ar_cnt = RegInit(0.U(8.W))
  val r_cnt = RegInit(0.U(8.W))
  
  when(io.axi.arvalid && io.axi.arready) {
    ar_cnt := ar_cnt + 1.U
  }
  
  when(io.axi.rvalid && io.axi.rready) {
    r_cnt := r_cnt + 1.U
  }
  
  // AXI读请求通道信号默认值
  io.axi.arid    := AXI_ID_ICACHE // 取指ID为0
  io.axi.arlen   := 0.U           // 读取单个数据
  io.axi.arsize  := AXI_SIZE_4B    // 读取4字节
  io.axi.arburst := AXI_BURST_INCR // 递增模式
  io.axi.arlock  := 0.U
  io.axi.arcache := 0.U
  io.axi.arprot  := 0.U
  
  // 在ar_req状态断言arvalid
  io.axi.arvalid := state === ar_req
  io.axi.araddr  := addr_reg
  
  // 在r_wait状态断言rready，始终准备接收数据，即使ID不匹配
  // 这样可以避免总线卡死
  io.axi.rready  := state === r_wait
  
  // 写通道信号默认值（ICache不使用写通道）
  io.axi.awid    := 0.U
  io.axi.awaddr  := 0.U
  io.axi.awlen   := 0.U
  io.axi.awsize  := 0.U
  io.axi.awburst := 0.U
  io.axi.awlock  := 0.U
  io.axi.awcache := 0.U
  io.axi.awprot  := 0.U
  io.axi.awvalid := false.B
  io.axi.wid     := 0.U
  io.axi.wdata   := 0.U
  io.axi.wstrb   := 0.U
  io.axi.wlast   := false.B
  io.axi.wvalid  := false.B
  io.axi.bready  := false.B
  
  // 增加调试输出
  // printf(p"[Cycle ${Module.clock.asUInt}] PC=${io.cpu.addr}, AR: valid=${io.axi.arvalid} ready=${io.axi.arready} addr=${io.axi.araddr}, AR_CNT=${ar_cnt}\n")
  // printf(p"[Cycle ${Module.clock.asUInt}] R: valid=${io.axi.rvalid} ready=${io.axi.rready} data=${io.axi.rdata}, R_CNT=${r_cnt}, Commit=${io.cpu.finish_single_request}\n")
  
  // CPU接口输出 - stall状态更清晰
  io.cpu.stall := state =/= idle && state =/= data_ready
  
  // 只有当ID匹配时才认为数据有效，但无论如何都要接收数据以清除总线
  val data_valid = io.axi.rvalid && io.axi.rid === AXI_ID_ICACHE
  
  io.cpu.valid := state === data_ready || (state === r_wait && data_valid)
  io.cpu.inst  := Mux(state === r_wait && data_valid, io.axi.rdata(31, 0), inst_reg)
  
  // 状态机转换逻辑
  switch(state) {
    is(idle) {
      valid_reg := false.B
      when(io.cpu.req) {
        state := ar_req
      }
    }
    
    is(ar_req) {
      when(io.axi.arready) {
        state := r_wait
      }
    }
    
    is(r_wait) {
      // 无论ID是否匹配，都接收数据，但只有匹配时才处理数据
      when(io.axi.rvalid) {
        when(io.axi.rid === AXI_ID_ICACHE) {
          inst_reg := io.axi.rdata(31, 0)
          when(io.cpu.finish_single_request) {
            state := idle
          }.otherwise {
            state := data_ready
            valid_reg := true.B
          }
        }.otherwise {
          // 如果ID不匹配，仅接收数据但不改变状态
          // 这样可以消除总线上的数据，避免系统卡死
        }
      }
    }
    
    is(data_ready) {
      when(io.cpu.finish_single_request) {
        state := idle
        valid_reg := false.B
      }
    }
  }
} 