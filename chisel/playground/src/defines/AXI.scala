package cpu.defines

import chisel3._
import chisel3.util._
import cpu.defines.Const._

// AXI常量定义放在Object中而不是trait中
object AXIParam {
  // AXI ID宽度
  val AXI_ID_WIDTH = 4
  
  // AXI各类型宽度
  val AXI_ADDR_WIDTH = 32
  val AXI_DATA_WIDTH = 64
  val AXI_STRB_WIDTH = AXI_DATA_WIDTH / 8
  
  // AXI SIZE常量值
  val AXI_SIZE_1B    = "b000".U(3.W)
  val AXI_SIZE_2B    = "b001".U(3.W)
  val AXI_SIZE_4B    = "b010".U(3.W)
  val AXI_SIZE_8B    = "b011".U(3.W)
  
  // AXI BURST常量值
  val AXI_BURST_FIXED = "b00".U(2.W)
  val AXI_BURST_INCR  = "b01".U(2.W)
  val AXI_BURST_WRAP  = "b10".U(2.W)
  
  // AXI特定ID值
  val AXI_ID_ICACHE  = 0.U(AXI_ID_WIDTH.W)  // 取指ID为0
  val AXI_ID_DCACHE  = 1.U(AXI_ID_WIDTH.W)  // 取/存数ID为1
}

// AXI总线接口束，使用与top_axi_wrapper.v一致的命名格式
class AXIMaster extends Bundle {
  import AXIParam._
  
  // 全局信号
  val aclock  = Input(Clock())
  val areset  = Input(Bool())
  
  // 写请求通道（aw开头）
  val awid    = Output(UInt(AXI_ID_WIDTH.W))
  val awaddr  = Output(UInt(AXI_ADDR_WIDTH.W))
  val awlen   = Output(UInt(8.W))
  val awsize  = Output(UInt(3.W))
  val awburst = Output(UInt(2.W))
  val awvalid = Output(Bool())
  val awready = Input(Bool())
  val awlock  = Output(UInt(2.W))
  val awcache = Output(UInt(4.W))
  val awprot  = Output(UInt(3.W))
  
  // 写数据通道（w开头）
  val wdata   = Output(UInt(AXI_DATA_WIDTH.W))
  val wstrb   = Output(UInt(AXI_STRB_WIDTH.W))
  val wlast   = Output(Bool())
  val wvalid  = Output(Bool())
  val wready  = Input(Bool())
  val wid     = Output(UInt(AXI_ID_WIDTH.W))
  
  // 写响应通道（b开头）
  val bid     = Input(UInt(AXI_ID_WIDTH.W))
  val bresp   = Input(UInt(2.W))
  val bvalid  = Input(Bool())
  val bready  = Output(Bool())
  
  // 读请求通道（ar开头）
  val arid    = Output(UInt(AXI_ID_WIDTH.W))
  val araddr  = Output(UInt(AXI_ADDR_WIDTH.W))
  val arlen   = Output(UInt(8.W))
  val arsize  = Output(UInt(3.W))
  val arburst = Output(UInt(2.W))
  val arvalid = Output(Bool())
  val arready = Input(Bool())
  val arlock  = Output(UInt(2.W))
  val arcache = Output(UInt(4.W))
  val arprot  = Output(UInt(3.W))
  
  // 读响应通道（r开头）
  val rid     = Input(UInt(AXI_ID_WIDTH.W))
  val rdata   = Input(UInt(AXI_DATA_WIDTH.W))
  val rresp   = Input(UInt(2.W))
  val rlast   = Input(Bool())
  val rvalid  = Input(Bool())
  val rready  = Output(Bool())
}

// 定义Cache和CPU之间的接口
class ICacheIO extends Bundle {
  import AXIParam._
  
  val req     = Output(Bool())
  val addr    = Output(UInt(AXI_ADDR_WIDTH.W))
  val valid   = Input(Bool())
  val inst    = Input(UInt(INST_WID.W))
  val finish_single_request = Output(Bool())
  val stall   = Input(Bool())
}

class DCacheIO extends Bundle {
  import AXIParam._
  
  val en     = Output(Bool())
  val wen    = Output(Bool())
  val size   = Output(UInt(3.W))
  val addr   = Output(UInt(AXI_ADDR_WIDTH.W))
  val wdata  = Output(UInt(AXI_DATA_WIDTH.W))
  val wstrb  = Output(UInt(AXI_STRB_WIDTH.W))
  val rdata  = Input(UInt(AXI_DATA_WIDTH.W))
  val finish_single_request = Output(Bool())
  val stall  = Input(Bool())
} 